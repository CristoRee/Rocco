package com.cristoree.rocco.client.voice;

import com.cristoree.rocco.config.RoccoClientConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.registry.ModSounds;
import com.cristoree.rocco.voice.RoccoNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

// La voz de Rocco del lado del jugador: junta los pedazos que manda el servidor y la reproduce
// con el motor de sonido del juego, saliendo de la boca de Rocco y en la categoria "Voz/Habla"
// (asi respeta ese control de volumen). Las frases van en fila: nunca se pisan.
public final class VoicePlayback
{
    private record Pending(int entityId, int sampleRate, int count, Map<Integer, byte[]> parts, long started, int face) {}
    private record Clip(int entityId, int sampleRate, byte[] pcm, int face) {}

    private static final Map<Integer, Pending> PENDING = new HashMap<>();
    private static final Deque<Clip> QUEUE = new ArrayDeque<>();
    private static VoiceSound playing;

    // Su voz esta sonando ahora (para mover la boca con el audio de verdad y bajar la musica).
    // La cara de la frase que esta sonando (null si no suena ninguna o no trae): se muestra mientras
    // dura, aunque el servidor ya haya pasado a otra cara por una respuesta mas nueva.
    @org.jetbrains.annotations.Nullable
    public static com.cristoree.rocco.entity.RoccoExpression spokenFace(int entityId)
    {
        VoiceSound current = playing;
        if (current == null || current.face < 0 || !isSpeaking(entityId))
        {
            return null;
        }
        return com.cristoree.rocco.entity.RoccoExpression.byId(current.face);
    }

    public static boolean isSpeaking(int entityId)
    {
        VoiceSound current = playing;
        return current != null && current.rocco.getId() == entityId && !current.isStopped()
                && Minecraft.getInstance().getSoundManager().isActive(current);
    }

    public static void receive(RoccoNetwork.VoiceChunk chunk)
    {
        Pending pending = PENDING.computeIfAbsent(chunk.stream(), id ->
                new Pending(chunk.entityId(), chunk.sampleRate(), chunk.count(), new HashMap<>(), System.currentTimeMillis(), chunk.face()));
        pending.parts().put(chunk.index(), chunk.data());

        if (pending.parts().size() >= pending.count())
        {
            PENDING.remove(chunk.stream());
            ByteArrayOutputStream joined = new ByteArrayOutputStream();
            for (int i = 0; i < pending.count(); i++)
            {
                byte[] part = pending.parts().get(i);
                if (part == null)
                {
                    return;
                }
                joined.writeBytes(part);
            }
            QUEUE.add(new Clip(pending.entityId(), pending.sampleRate(), joined.toByteArray(), pending.face()));
        }

        // Pedazos que nunca terminaron de llegar no quedan colgados.
        long now = System.currentTimeMillis();
        PENDING.values().removeIf(other -> now - other.started() > 15_000L);
    }

    // Cada tick del cliente: si no esta sonando nada, arranca la proxima frase.
    public static void tick()
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null)
        {
            QUEUE.clear();
            PENDING.clear();
            playing = null;
            return;
        }

        if (playing != null && minecraft.getSoundManager().isActive(playing))
        {
            return;
        }
        playing = null;

        Clip next = QUEUE.poll();
        if (next == null)
        {
            return;
        }

        Entity entity = minecraft.level.getEntity(next.entityId());
        if (!(entity instanceof RoccoEntity rocco))
        {
            return;
        }

        playing = new VoiceSound(rocco, next.pcm(), next.sampleRate(), next.face());
        minecraft.getSoundManager().play(playing);
    }

    // Frases grabadas: se leen del mod (assets/rocco/voice_lines/<codigo>.ogg) y van a la misma fila
    // que la voz generada, asi suenan en orden y desde Rocco. Una que no este, se saltea.
    public static void playLines(int entityId, java.util.List<String> ids, int face)
    {
        for (String id : ids)
        {
            net.minecraft.resources.ResourceLocation location =
                    new net.minecraft.resources.ResourceLocation(com.cristoree.rocco.RoccoMod.MODID, "voice_lines/" + id + ".ogg");
            var resource = Minecraft.getInstance().getResourceManager().getResource(location);
            if (resource.isEmpty())
            {
                continue;
            }
            try (java.io.InputStream input = resource.get().open();
                 com.mojang.blaze3d.audio.OggAudioStream ogg = new com.mojang.blaze3d.audio.OggAudioStream(input))
            {
                AudioFormat format = ogg.getFormat();
                ByteBuffer all = ogg.readAll();
                byte[] pcm = new byte[all.remaining()];
                all.get(pcm);
                QUEUE.add(new Clip(entityId, (int) format.getSampleRate(), format.getChannels() > 1 ? toMono(pcm) : pcm, face));
            }
            catch (java.io.IOException | RuntimeException e)
            {
                com.cristoree.rocco.RoccoMod.LOGGER.warn("[Rocco] No pude leer la frase grabada {}: {}", id, e.toString());
            }
        }
    }

    // La voz sale desde Rocco, y el sonido con posicion tiene que ser mono: se promedian los canales.
    private static byte[] toMono(byte[] stereo)
    {
        ByteBuffer in = ByteBuffer.wrap(stereo).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer out = ByteBuffer.allocate(stereo.length / 2).order(ByteOrder.LITTLE_ENDIAN);
        while (in.remaining() >= 4)
        {
            int left = in.getShort();
            int right = in.getShort();
            out.putShort((short) ((left + right) / 2));
        }
        return out.array();
    }

    // Una frase de prueba (la pantalla de bienvenida): sin mundo ni Rocco, suena "en la cabeza".
    public static void playPreview(byte[] pcm, int sampleRate)
    {
        Minecraft.getInstance().getSoundManager().play(new PreviewSound(pcm, sampleRate));
    }

    private static final class PreviewSound extends net.minecraft.client.resources.sounds.AbstractSoundInstance
    {
        private final byte[] pcm;
        private final int sampleRate;

        PreviewSound(byte[] pcm, int sampleRate)
        {
            super(ModSounds.VOICE.get(), SoundSource.VOICE, RandomSource.create());
            this.pcm = pcm;
            this.sampleRate = sampleRate;
            this.volume = RoccoClientConfig.VOICE_VOLUME.get().floatValue();
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public boolean canStartSilent()
        {
            return true;
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping)
        {
            return CompletableFuture.completedFuture(new PcmStream(this.pcm, this.sampleRate));
        }
    }

    // Sigue a Rocco mientras habla; si se va del mundo, se corta.
    private static final class VoiceSound extends AbstractTickableSoundInstance
    {
        private final RoccoEntity rocco;
        private final byte[] pcm;
        private final int sampleRate;
        final int face;

        VoiceSound(RoccoEntity rocco, byte[] pcm, int sampleRate, int face)
        {
            super(ModSounds.VOICE.get(), SoundSource.VOICE, RandomSource.create());
            this.rocco = rocco;
            this.face = face;
            this.pcm = pcm;
            this.sampleRate = sampleRate;
            this.volume = RoccoClientConfig.VOICE_VOLUME.get().floatValue();
            this.pitch = 1.0F;
            this.looping = false;

            if (RoccoClientConfig.SPATIAL_AUDIO.get())
            {
                this.relative = false;
                this.attenuation = SoundInstance.Attenuation.LINEAR;
                follow();
            }
            else
            {
                // Sin espacio: suena "en la cabeza", igual de fuerte este donde este.
                this.relative = true;
                this.attenuation = SoundInstance.Attenuation.NONE;
                this.x = 0.0D;
                this.y = 0.0D;
                this.z = 0.0D;
            }
        }

        private void follow()
        {
            this.x = this.rocco.getX();
            this.y = this.rocco.getEyeY();
            this.z = this.rocco.getZ();
        }

        @Override
        public void tick()
        {
            if (!this.rocco.isAlive() || this.rocco.isRemoved())
            {
                stop();
                return;
            }
            if (!this.relative)
            {
                follow();
            }
        }

        @Override
        public boolean canStartSilent()
        {
            return true;
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping)
        {
            return CompletableFuture.completedFuture(new PcmStream(this.pcm, this.sampleRate));
        }
    }

    // El PCM ya decodificado, entregado de a pedazos como lo pide OpenAL.
    private static final class PcmStream implements AudioStream
    {
        private final byte[] pcm;
        private final AudioFormat format;
        private int position;

        PcmStream(byte[] pcm, int sampleRate)
        {
            this.pcm = pcm;
            this.format = new AudioFormat(sampleRate, 16, 1, true, false);
        }

        @Override
        public AudioFormat getFormat()
        {
            return this.format;
        }

        @Override
        public ByteBuffer read(int size)
        {
            if (this.position >= this.pcm.length)
            {
                return null;
            }
            int length = Math.min(size - (size % 2), this.pcm.length - this.position);
            ByteBuffer buffer = ByteBuffer.allocateDirect(length).order(ByteOrder.LITTLE_ENDIAN);
            buffer.put(this.pcm, this.position, length);
            buffer.flip();
            this.position += length;
            return buffer;
        }

        @Override
        public void close()
        {
        }
    }

    private VoicePlayback() {}
}

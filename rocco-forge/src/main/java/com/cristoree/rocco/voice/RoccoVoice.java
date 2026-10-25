package com.cristoree.rocco.voice;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.config.RoccoConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

// Hace hablar a Rocco en voz alta: cada frase que dice en el chat se convierte en audio con
// Fish Audio y se manda a los jugadores que lo escuchan. Un solo hilo para todas las frases,
// asi suenan en el mismo orden en que las dijo.
public final class RoccoVoice
{
    private static final AtomicInteger STREAMS = new AtomicInteger();
    private static ExecutorService executor;
    private static long lastProblemNote;

    public static void speak(RoccoEntity rocco, String text)
    {
        if (!RoccoConfig.voiceReady() || !(rocco.level() instanceof ServerLevel level))
        {
            return;
        }

        String line = speakable(text);
        if (line.isEmpty())
        {
            return;
        }

        MinecraftServer server = level.getServer();
        int entityId = rocco.getId();

        executor().execute(() ->
        {
            FishAudio.Result result;
            try
            {
                result = FishAudio.synthesize(line);
            }
            catch (RuntimeException e)
            {
                RoccoMod.LOGGER.error("[Rocco] Error inesperado generando la voz", e);
                return;
            }

            server.execute(() ->
            {
                if (result.pcm() == null)
                {
                    problem(level, result.error());
                    return;
                }
                if (level.getEntity(entityId) instanceof RoccoEntity speaker && speaker.isAlive())
                {
                    send(level, speaker, result.pcm());
                }
            });
        });
    }

    // La voz no lee etiquetas de cara ni cosas que no se pronuncian.
    static String speakable(String text)
    {
        String clean = text.replaceAll("\\[[a-z_]+]", " ")
                .replaceAll("[*_~`#<>]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        int max = RoccoCommonConfig.VOICE_MAX_CHARS.get();
        if (clean.length() > max)
        {
            int cut = clean.lastIndexOf(' ', max);
            clean = clean.substring(0, cut > max / 2 ? cut : max);
        }
        return clean;
    }

    private static void send(ServerLevel level, RoccoEntity rocco, Wav.Pcm pcm)
    {
        int stream = STREAMS.incrementAndGet();
        byte[] data = pcm.data();
        int count = Math.max(1, (data.length + RoccoNetwork.CHUNK_BYTES - 1) / RoccoNetwork.CHUNK_BYTES);
        double radius = RoccoCommonConfig.CHAT_HEARING_RADIUS.get() + 8.0D;

        // La boca se mueve lo que dura el audio.
        rocco.talkFor(Math.min(20 * 60, pcm.durationTicks()));

        for (ServerPlayer player : level.players())
        {
            if (player.distanceToSqr(rocco) > radius * radius)
            {
                continue;
            }
            for (int index = 0; index < count; index++)
            {
                int from = index * RoccoNetwork.CHUNK_BYTES;
                int to = Math.min(data.length, from + RoccoNetwork.CHUNK_BYTES);
                RoccoNetwork.sendVoice(player, new RoccoNetwork.VoiceChunk(stream, rocco.getId(), pcm.sampleRate(),
                        index, count, Arrays.copyOfRange(data, from, to)));
            }
        }
    }

    // Si la voz falla, se avisa en gris (una vez por minuto) y Rocco sigue escribiendo.
    private static void problem(ServerLevel level, String error)
    {
        RoccoMod.LOGGER.warn("[Rocco] La voz no anduvo: {}", error);
        long now = System.currentTimeMillis();
        if (now - lastProblemNote < 60_000L)
        {
            return;
        }
        lastProblemNote = now;
        Component note = Component.literal("[Rocco] La voz no anduvo: " + error + ". Sigo por escrito.")
                .withStyle(ChatFormatting.GRAY);
        level.players().forEach(player -> player.sendSystemMessage(note));
    }

    private static synchronized ExecutorService executor()
    {
        if (executor == null)
        {
            executor = Executors.newSingleThreadExecutor(runnable ->
            {
                Thread thread = new Thread(runnable, "Rocco-Voz");
                thread.setDaemon(true);
                return thread;
            });
        }
        return executor;
    }

    private RoccoVoice() {}
}

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

    // impression: a quien esta imitando en esta frase (null = es el). Si hay una voz para ese
    // nombre, la frase sale con esa voz; si no, con la de Rocco.
    public static void speak(RoccoEntity rocco, String text, @org.jetbrains.annotations.Nullable String impression)
    {
        if (!RoccoCommonConfig.ENABLED.get() || !RoccoCommonConfig.VOICE_ENABLED.get()
                || !(rocco.level() instanceof ServerLevel level))
        {
            return;
        }

        // Primero las frases grabadas: no necesitan clave ni gastan nada. Si la frase es de las de
        // siempre (o varias seguidas) y estan todas grabadas, suenan esas.
        if (impression == null && RoccoCommonConfig.VOICE_RECORDED_LINES.get())
        {
            java.util.List<String> recorded = recordedLines(text);
            if (recorded != null)
            {
                sendLines(level, rocco, recorded);
                return;
            }
        }

        if (!RoccoConfig.voiceReady())
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
            FishAudio.Result attempt;
            try
            {
                String voice = impression == null ? null : ImpressionVoices.voiceFor(impression);
                attempt = FishAudio.synthesize(line, voice);
                // Si la voz de la imitacion fallo, que lo diga con la suya antes que quedarse mudo.
                if (attempt.pcm() == null && voice != null)
                {
                    attempt = FishAudio.synthesize(line, null);
                }
            }
            catch (RuntimeException e)
            {
                RoccoMod.LOGGER.error("[Rocco] Error inesperado generando la voz", e);
                return;
            }

            FishAudio.Result result = attempt;
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

    // ---------- frases grabadas ----------

    private static final java.util.Map<String, Boolean> RECORDED = new java.util.concurrent.ConcurrentHashMap<>();
    private static java.util.List<String> recordable;

    // Los codigos de las frases grabadas que forman este texto, en orden ("Voy. Alguien tiene que
    // cuidarte." -> [follow_1]). Varias frases seguidas tambien valen. null si alguna parte no es
    // una frase fija o todavia no tiene su audio.
    @org.jetbrains.annotations.Nullable
    static java.util.List<String> recordedLines(String text)
    {
        String rest = text.trim();
        java.util.List<String> ids = new java.util.ArrayList<>();
        while (!rest.isEmpty())
        {
            String found = null;
            for (String line : recordable())
            {
                boolean whole = rest.length() == line.length() || Character.isWhitespace(rest.charAt(line.length()));
                if (rest.startsWith(line) && whole && (found == null || line.length() > found.length()))
                {
                    found = line;
                }
            }
            String id = found == null ? null : com.cristoree.rocco.ai.OfflineRocco.lineId(found);
            if (id == null || !hasRecording(id))
            {
                return null;
            }
            ids.add(id);
            rest = rest.substring(found.length()).trim();
        }
        return ids.isEmpty() ? null : ids;
    }

    private static synchronized java.util.List<String> recordable()
    {
        if (recordable == null)
        {
            java.util.List<String> lines = new java.util.ArrayList<>();
            for (String[] category : com.cristoree.rocco.ai.OfflineRocco.allLines().values())
            {
                for (String line : category)
                {
                    if (!line.contains("{"))
                    {
                        lines.add(line.trim());
                    }
                }
            }
            recordable = lines;
        }
        return recordable;
    }

    // El audio viene adentro del mod: se mira una sola vez si existe.
    public static boolean hasRecording(String id)
    {
        return RECORDED.computeIfAbsent(id, key ->
                RoccoVoice.class.getResource("/assets/" + RoccoMod.MODID + "/voice_lines/" + key + ".ogg") != null);
    }

    private static void sendLines(ServerLevel level, RoccoEntity rocco, java.util.List<String> ids)
    {
        double radius = RoccoCommonConfig.CHAT_HEARING_RADIUS.get() + 8.0D;
        RoccoNetwork.VoiceLines packet = new RoccoNetwork.VoiceLines(rocco.getId(), ids);
        for (ServerPlayer player : level.players())
        {
            if (player.distanceToSqr(rocco) <= radius * radius)
            {
                RoccoNetwork.sendLines(player, packet);
            }
        }
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

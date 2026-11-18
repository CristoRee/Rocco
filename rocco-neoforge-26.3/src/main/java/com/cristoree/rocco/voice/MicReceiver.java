package com.cristoree.rocco.voice;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.ai.RoccoChatHandler;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.config.RoccoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Junta lo que grabo cada jugador, lo pasa a texto y se lo dice a Rocco como si lo hubiera
// escrito en el chat (sin necesidad de nombrarlo: si apretaste la tecla, le hablas a el).
public final class MicReceiver
{
    public static final int SAMPLE_RATE = 16000;

    private record Recording(int session, ByteArrayOutputStream audio, int nextIndex) {}

    private static final Map<UUID, Recording> RECORDINGS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> NOTICES = new ConcurrentHashMap<>();
    private static ExecutorService executor;
    // El ultimo error del microfono (null si la ultima frase se entendio), para /rocco ia.
    private static volatile String lastError;

    static void receive(ServerPlayer player, RoccoNetwork.MicChunk chunk)
    {
        if (!RoccoConfig.sttReady())
        {
            if (chunk.last())
            {
                notice(player, "El microfono esta apagado o sin configurar: Mods > Rocco > Config > Microfono "
                        + "(Deepgram, que regala credito para empezar, o un proveedor propio como Groq).");
            }
            return;
        }

        Recording recording = RECORDINGS.get(player.getUUID());
        if (recording == null || recording.session() != chunk.session() || chunk.index() == 0)
        {
            recording = new Recording(chunk.session(), new ByteArrayOutputStream(), 0);
        }
        // Un pedazo fuera de orden (o repetido) arruina el audio: se descarta la grabacion.
        if (chunk.index() != recording.nextIndex())
        {
            RECORDINGS.remove(player.getUUID());
            return;
        }

        int maxBytes = RoccoCommonConfig.STT_MAX_SECONDS.get() * SAMPLE_RATE * 2 + RoccoNetwork.CHUNK_BYTES;
        if (recording.audio().size() + chunk.data().length > maxBytes)
        {
            RECORDINGS.remove(player.getUUID());
            notice(player, "La grabacion fue demasiado larga (maximo " + RoccoCommonConfig.STT_MAX_SECONDS.get() + " segundos).");
            return;
        }

        recording.audio().writeBytes(chunk.data());
        RECORDINGS.put(player.getUUID(), new Recording(recording.session(), recording.audio(), recording.nextIndex() + 1));

        if (chunk.last())
        {
            RECORDINGS.remove(player.getUUID());
            byte[] pcm = recording.audio().toByteArray();
            // Menos de un cuarto de segundo es un toque sin querer.
            if (pcm.length >= SAMPLE_RATE / 2)
            {
                transcribe(player, pcm);
            }
        }
    }

    private static void transcribe(ServerPlayer player, byte[] pcm)
    {
        byte[] wav = Wav.write(pcm, SAMPLE_RATE);
        var server = player.level().getServer();
        if (server == null)
        {
            return;
        }

        executor().execute(() ->
        {
            Deepgram.Result result = Deepgram.transcribe(wav);
            server.execute(() ->
            {
                if (result.text() == null)
                {
                    RoccoMod.LOGGER.warn("[Rocco] El microfono no anduvo: {}", result.error());
                    lastError = result.error();
                    notice(player, "No te pude escuchar: " + result.error() + ".");
                    return;
                }
                lastError = null;
                if (result.text().isBlank())
                {
                    player.sendOverlayMessage(Component.literal("Rocco no te entendio, proba de nuevo.")
                            .withStyle(ChatFormatting.GRAY));
                    return;
                }
                if (player.isAlive() && !player.hasDisconnected())
                {
                    RoccoChatHandler.onVoice(player, result.text());
                }
            });
        });
    }

    // Avisos al jugador, como mucho uno cada 20 segundos para no llenar el chat.
    private static void notice(ServerPlayer player, String text)
    {
        long now = System.currentTimeMillis();
        Long last = NOTICES.get(player.getUUID());
        if (last != null && now - last < 20_000L)
        {
            return;
        }
        NOTICES.put(player.getUUID(), now);
        player.sendSystemMessage(Component.literal("[Rocco] " + text).withStyle(ChatFormatting.GRAY));
    }

    // Para /rocco ia: con que escucha y, si fallo, por que.
    public static net.minecraft.network.chat.MutableComponent statusLine()
    {
        net.minecraft.network.chat.MutableComponent row = Component.literal(" Microfono: ");
        if (!RoccoCommonConfig.STT_ENABLED.get())
        {
            return row.append(Component.literal("apagado").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (!RoccoConfig.sttReady())
        {
            return row.append(Component.literal("sin configurar (Config > Microfono)").withStyle(ChatFormatting.DARK_GRAY));
        }
        boolean custom = RoccoCommonConfig.STT_PROVIDER.get() == RoccoCommonConfig.SttProvider.CUSTOM;
        row.append(Component.literal(custom
                ? "proveedor propio (" + RoccoCommonConfig.STT_CUSTOM_MODEL.get().trim() + ")"
                : "Deepgram (" + RoccoCommonConfig.DEEPGRAM_MODEL.get().trim() + ")").withStyle(ChatFormatting.WHITE));
        String error = lastError;
        if (error != null)
        {
            row.append(Component.literal("  ultimo error: " + error).withStyle(ChatFormatting.RED));
        }
        return row;
    }

    private static synchronized ExecutorService executor()
    {
        if (executor == null)
        {
            executor = Executors.newCachedThreadPool(runnable ->
            {
                Thread thread = new Thread(runnable, "Rocco-Oido");
                thread.setDaemon(true);
                return thread;
            });
        }
        return executor;
    }

    private MicReceiver() {}
}

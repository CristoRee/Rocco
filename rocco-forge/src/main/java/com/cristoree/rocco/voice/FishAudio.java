package com.cristoree.rocco.voice;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.config.RoccoConfig;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

// Texto a voz con Fish Audio (POST /v1/tts). Se pide WAV porque es lo que el juego reproduce
// sin librerias extra; MP3 y Opus necesitarian un decodificador.
// Las frases ya habladas quedan en config/rocco/voz_cache para no pagarlas dos veces.
public final class FishAudio
{
    public record Result(Wav.Pcm pcm, String error, boolean fatal) {}

    public static Result synthesize(String text)
    {
        String key = RoccoConfig.fishApiKey();
        String voice = RoccoCommonConfig.FISH_VOICE_ID.get().trim();
        if (key.isEmpty() || voice.isEmpty())
        {
            return new Result(null, "falta la clave o el ID de voz de Fish Audio", true);
        }

        int sampleRate = closestSampleRate(RoccoCommonConfig.VOICE_SAMPLE_RATE.get());
        String model = RoccoCommonConfig.FISH_MODEL.get().trim();
        String cacheKey = String.join("|", model, voice, String.valueOf(sampleRate),
                String.valueOf(RoccoCommonConfig.VOICE_SPEED.get()), String.valueOf(RoccoCommonConfig.VOICE_LOUDNESS.get()),
                RoccoCommonConfig.VOICE_LATENCY_MODE.get().name(), text);

        Wav.Pcm cached = readCache(cacheKey);
        if (cached != null)
        {
            return new Result(cached, null, false);
        }

        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        body.addProperty("reference_id", voice);
        body.addProperty("format", "wav");
        body.addProperty("sample_rate", sampleRate);
        body.addProperty("latency", RoccoCommonConfig.VOICE_LATENCY_MODE.get().name().toLowerCase(Locale.ROOT));
        body.addProperty("normalize", true);
        JsonObject prosody = new JsonObject();
        prosody.addProperty("speed", RoccoCommonConfig.VOICE_SPEED.get());
        // loudness 1.0 = sin cambio; Fish Audio lo pide en decibeles.
        prosody.addProperty("volume", Math.round(20.0D * Math.log10(RoccoCommonConfig.VOICE_LOUDNESS.get()) * 10.0D) / 10.0D);
        body.add("prosody", prosody);

        String endpoint = RoccoCommonConfig.FISH_ENDPOINT.get().trim().replaceAll("/+$", "");
        if (!endpoint.endsWith("/v1/tts"))
        {
            endpoint = endpoint + "/v1/tts";
        }

        try
        {
            Map<String, String> headers = model.isEmpty()
                    ? Map.of("Authorization", "Bearer " + key, "Content-Type", "application/json")
                    : Map.of("Authorization", "Bearer " + key, "Content-Type", "application/json", "model", model);

            VoiceHttp.Response response = VoiceHttp.post(endpoint, headers,
                    body.toString().getBytes(StandardCharsets.UTF_8), RoccoCommonConfig.VOICE_TIMEOUT_SECONDS.get());

            if (response.status() == 401 || response.status() == 403)
            {
                return new Result(null, "Fish Audio rechazo la clave (" + VoiceHttp.errorText(response) + ")", true);
            }
            if (response.status() == 402)
            {
                return new Result(null, "la cuenta de Fish Audio se quedo sin credito", true);
            }
            if (response.status() >= 400)
            {
                return new Result(null, "Fish Audio respondio " + response.status() + ": " + VoiceHttp.errorText(response), false);
            }

            Wav.Pcm pcm = Wav.read(response.body());
            if (pcm == null || pcm.data().length == 0)
            {
                return new Result(null, "Fish Audio mando un audio que no pude leer", false);
            }

            writeCache(cacheKey, response.body());
            return new Result(pcm, null, false);
        }
        catch (IOException e)
        {
            return new Result(null, "no pude conectar con Fish Audio (" + e.getMessage() + ")", false);
        }
    }

    // Fish acepta 8k, 16k, 24k, 32k y 44,1k para WAV.
    static int closestSampleRate(int wanted)
    {
        int best = 24000;
        for (int option : new int[]{8000, 16000, 24000, 32000, 44100})
        {
            if (Math.abs(option - wanted) < Math.abs(best - wanted))
            {
                best = option;
            }
        }
        return best;
    }

    // ---------- cache en disco ----------

    private static Path cacheDir()
    {
        return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("rocco").resolve("voz_cache");
    }

    private static Wav.Pcm readCache(String key)
    {
        if (!RoccoCommonConfig.VOICE_CACHE_ENABLED.get() || RoccoCommonConfig.VOICE_CACHE_SIZE_MB.get() <= 0)
        {
            return null;
        }
        Path file = cacheDir().resolve(hash(key) + ".wav");
        try
        {
            if (Files.exists(file))
            {
                Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
                return Wav.read(Files.readAllBytes(file));
            }
        }
        catch (IOException e)
        {
            RoccoMod.LOGGER.debug("[Rocco] No pude leer la voz guardada: {}", e.toString());
        }
        return null;
    }

    private static void writeCache(String key, byte[] wav)
    {
        long limit = RoccoCommonConfig.VOICE_CACHE_SIZE_MB.get() * 1024L * 1024L;
        if (!RoccoCommonConfig.VOICE_CACHE_ENABLED.get() || limit <= 0)
        {
            return;
        }
        try
        {
            Path dir = cacheDir();
            Files.createDirectories(dir);
            Files.write(dir.resolve(hash(key) + ".wav"), wav);
            trim(dir, limit);
        }
        catch (IOException e)
        {
            RoccoMod.LOGGER.debug("[Rocco] No pude guardar la voz: {}", e.toString());
        }
    }

    // Si se paso del tamaño, borra lo que hace mas que no se usa.
    private static void trim(Path dir, long limit) throws IOException
    {
        List<Path> files;
        try (Stream<Path> stream = Files.list(dir))
        {
            files = stream.filter(path -> path.toString().endsWith(".wav")).toList();
        }
        long total = 0L;
        for (Path file : files)
        {
            total += Files.size(file);
        }
        if (total <= limit)
        {
            return;
        }

        List<Path> oldestFirst = files.stream().sorted(Comparator.comparingLong(path ->
        {
            try
            {
                return Files.getLastModifiedTime(path).toMillis();
            }
            catch (IOException e)
            {
                return 0L;
            }
        })).toList();

        for (Path file : oldestFirst)
        {
            if (total <= limit * 9 / 10)
            {
                break;
            }
            long size = Files.size(file);
            Files.deleteIfExists(file);
            total -= size;
        }
    }

    private static String hash(String text)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : digest)
            {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        }
        catch (Exception e)
        {
            return Integer.toHexString(text.hashCode());
        }
    }

    private FishAudio() {}
}

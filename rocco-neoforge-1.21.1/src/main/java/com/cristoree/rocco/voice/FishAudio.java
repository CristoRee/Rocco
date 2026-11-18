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
    // notice: algo para avisarle al jugador una vez aunque la voz haya salido (null = nada).
    public record Result(Wav.Pcm pcm, String error, boolean fatal, @org.jetbrains.annotations.Nullable String notice)
    {
        Result(Wav.Pcm pcm, String error, boolean fatal)
        {
            this(pcm, error, fatal, null);
        }
    }

    // El modelo gratis de Fish Audio: anda con saldo de API en cero.
    public static final String FREE_MODEL = "s2.1-pro-free";
    // Si el modelo elegido dio 402 (sin credito de API para ese modelo), en esta sesion se usa el gratis.
    private static volatile String fallbackFor = null;

    // Las etiquetas que pide el prompt -> marcadores de emocion de Fish Audio. Los s2 entienden la
    // etiqueta tal cual, entre corchetes; s1 (viejo) tiene su lista fija, entre parentesis.
    private static final Map<String, String> S1_EMOTIONS = Map.ofEntries(
            Map.entry("deadpan", "indifferent"),
            Map.entry("sarcastic", "sarcastic"),
            Map.entry("calm", "relaxed"),
            Map.entry("relaxed", "relaxed"),
            Map.entry("whispering", "whispering"),
            Map.entry("sighing", "sighing"),
            Map.entry("laughing", "laughing"),
            Map.entry("evil laugh", "chuckling"),
            Map.entry("teasing", "amused"),
            Map.entry("playful", "amused"),
            Map.entry("proud", "proud"),
            Map.entry("scared", "scared"),
            Map.entry("worried", "worried"),
            Map.entry("low voice", "soft tone"),
            Map.entry("shouting", "shouting"),
            Map.entry("excited", "excited"),
            Map.entry("warm", "sincere"),
            Map.entry("offended", "upset"),
            Map.entry("angry", "angry"),
            Map.entry("sad", "sad"),
            Map.entry("suspicious", "disapproving"),
            Map.entry("serious", "serious"),
            Map.entry("confused", "confused"),
            Map.entry("happy", "joyful"),
            Map.entry("surprised", "surprised"),
            Map.entry("nervous", "nervous"));
    private static final java.util.regex.Pattern MARK = java.util.regex.Pattern.compile("\\{([a-z ]{2,24})}");

    // La voz sale de un proveedor propio (compatible con OpenAI) y no de Fish Audio.
    public static boolean customVoice()
    {
        return RoccoCommonConfig.VOICE_PROVIDER.get() == RoccoCommonConfig.VoiceProvider.CUSTOM;
    }

    // Una etiqueta que la voz sabe decir.
    public static boolean knowsEmotion(String tag)
    {
        return S1_EMOTIONS.containsKey(tag);
    }

    // El modelo que se va a usar de verdad (el de la config, o el gratis si ese se quedo sin credito).
    public static String model()
    {
        String configured = RoccoCommonConfig.FISH_MODEL.get().trim();
        return configured.equals(fallbackFor) ? FREE_MODEL : configured;
    }

    // Los modelos que entienden marcadores de emocion.
    public static boolean emotionsFor(String model)
    {
        String m = model.toLowerCase(Locale.ROOT);
        return m.startsWith("s1") || m.startsWith("s2") || m.startsWith("drama");
    }

    // Las marcas {emocion} del texto, en el formato de ese modelo (o afuera si no las entiende).
    static String withEmotions(String text, String model)
    {
        String m = model.toLowerCase(Locale.ROOT);
        java.util.regex.Matcher matcher = MARK.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find())
        {
            String tag = matcher.group(1);
            String marker = !emotionsFor(m) ? null : m.startsWith("s1") ? "(" + S1_EMOTIONS.get(tag) + ")" : "[" + tag + "]";
            matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(marker == null || marker.contains("null") ? " " : " " + marker + " "));
        }
        matcher.appendTail(out);
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    public static Result synthesize(String text)
    {
        return synthesize(text, null);
    }

    // voiceOverride: otra voz solo para esta frase (una imitacion). null = la de Rocco.
    public static Result synthesize(String text, @org.jetbrains.annotations.Nullable String voiceOverride)
    {
        // Con un proveedor propio habla con esa voz (las voces de imitacion son de Fish Audio).
        if (customVoice())
        {
            return OpenAiVoice.synthesize(text);
        }
        String key = RoccoConfig.fishApiKey();
        String voice = voiceOverride != null && !voiceOverride.isBlank()
                ? voiceOverride.trim()
                : RoccoCommonConfig.FISH_VOICE_ID.get().trim();
        if (key.isEmpty() || voice.isEmpty())
        {
            return new Result(null, "falta la clave o el ID de voz de Fish Audio", true);
        }

        int sampleRate = closestSampleRate(RoccoCommonConfig.VOICE_SAMPLE_RATE.get());
        Result result = synthesize(withEmotions(text, model()), key, voice, model(), sampleRate);
        // 402 = sin credito de API para ESE modelo (el credito del plan de la app no cuenta). Con el
        // gratis anda igual: se pasa a ese en vez de quedarse mudo, y se avisa una vez.
        String configured = RoccoCommonConfig.FISH_MODEL.get().trim();
        if (result.pcm() == null && result.fatal() && result.error().startsWith(NO_CREDIT) && !model().equals(FREE_MODEL))
        {
            fallbackFor = configured;
            RoccoMod.LOGGER.warn("[Rocco] Fish Audio: sin credito de API para el modelo '{}'; uso '{}'.", configured, FREE_MODEL);
            Result free = synthesize(withEmotions(text, FREE_MODEL), key, voice, FREE_MODEL, sampleRate);
            return free.pcm() == null ? free : new Result(free.pcm(), null, false,
                    "tu cuenta de Fish Audio no tiene credito de API para el modelo '" + (configured.isEmpty() ? "por defecto" : configured)
                            + "' (el credito del plan de la app es aparte), asi que hablo con el modelo gratis " + FREE_MODEL + ".");
        }
        return result;
    }

    private static final String NO_CREDIT = "Fish Audio no tiene credito";

    private static Result synthesize(String text, String key, String voice, String model, int sampleRate)
    {
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
                return new Result(null, NO_CREDIT + " de API para el modelo '" + model + "' (" + VoiceHttp.errorText(response)
                        + "). El credito de API se carga aparte del plan de la app, o usa el modelo " + FREE_MODEL, true);
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
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("rocco").resolve("voz_cache");
    }

    static Wav.Pcm readCache(String key)
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

    static void writeCache(String key, byte[] wav)
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

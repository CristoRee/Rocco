package com.cristoree.rocco.voice;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.config.RoccoConfig;
import com.cristoree.rocco.util.Text;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Que voz usar para una imitacion ("@voz:Vegeta|es"). En orden:
//  1. la lista de la config (impressionVoices: "Vegeta=id; Goku=id"), que completa el jugador;
//  2. si esta activado (lo esta por defecto), una voz publica de Fish Audio con ese nombre. Se
//     recuerda en config/rocco/voces_encontradas.json para no buscarla en cada frase;
//  3. ninguna: Rocco la dice con su propia voz, imitando solo con el texto. Nunca falla.
// El mod no trae ninguna voz de una persona real: las publicas las sube la comunidad de Fish Audio.
public final class ImpressionVoices
{
    private static final Gson GSON = new Gson();
    // Si una busqueda no encontro nada (o fallo la conexion), no se repite enseguida, pero
    // tampoco queda olvidada para siempre: antes un error de red dejaba al personaje sin voz.
    private static final long RETRY_MS = 30 * 60_000L;

    private static Map<String, String> found;
    private static final Map<String, Long> missed = new ConcurrentHashMap<>();

    @Nullable
    public static String voiceFor(String name)
    {
        return voiceFor(name, "es");
    }

    // Corre en el hilo de la voz: puede tardar si tiene que buscar. language: "es" o "pt", la
    // lengua de la imitacion; con eso se prefiere la voz que habla ese idioma.
    @Nullable
    public static String voiceFor(String name, @Nullable String language)
    {
        String key = Text.normalize(name);
        if (key.isEmpty())
        {
            return null;
        }

        String fromConfig = fromConfig(key);
        if (fromConfig != null)
        {
            return fromConfig;
        }
        if (!RoccoCommonConfig.IMPRESSION_VOICE_SEARCH.get())
        {
            return null;
        }

        synchronized (ImpressionVoices.class)
        {
            Map<String, String> cache = found();
            String known = cache.get(key);
            if (known != null && !known.isEmpty())
            {
                return known;
            }
            Long last = missed.get(key);
            if (last != null && System.currentTimeMillis() - last < RETRY_MS)
            {
                return null;
            }

            String id = search(name, language);
            if (id == null)
            {
                missed.put(key, System.currentTimeMillis());
                return null;
            }
            cache.put(key, id);
            save(cache);
            return id;
        }
    }

    @Nullable
    private static String fromConfig(String key)
    {
        for (String entry : RoccoCommonConfig.IMPRESSION_VOICES.get().split(";"))
        {
            int equals = entry.indexOf('=');
            if (equals <= 0)
            {
                continue;
            }
            String who = Text.normalize(entry.substring(0, equals));
            String id = entry.substring(equals + 1).trim();
            if (!id.isEmpty() && !who.isEmpty() && (who.equals(key) || key.contains(who) || who.contains(key)))
            {
                return id;
            }
        }
        return null;
    }

    // GET /model?title=... de Fish Audio. Antes se quedaba con el primer resultado tal cual venia;
    // ahora pide varios y elige: que el titulo sea de ese personaje, que hable el idioma de la
    // imitacion y, entre esos, la voz que mas gente usa (suele ser la mejor clonada).
    @Nullable
    private static String search(String name, @Nullable String language)
    {
        String key = RoccoConfig.fishApiKey();
        if (key.isEmpty())
        {
            return null;
        }
        String endpoint = RoccoCommonConfig.FISH_ENDPOINT.get().trim().replaceAll("/+$", "").replaceAll("/v1/tts$", "");
        String url = endpoint + "/model?page_size=20&sort_by=task_count&title=" + URLEncoder.encode(name.trim(), StandardCharsets.UTF_8);
        try
        {
            VoiceHttp.Response response = VoiceHttp.get(url, Map.of("Authorization", "Bearer " + key), 10);
            if (response.status() >= 400)
            {
                RoccoMod.LOGGER.warn("[Rocco] No pude buscar una voz para {}: Fish Audio respondio {}", name, response.status());
                return null;
            }
            JsonObject root = JsonParser.parseString(response.text()).getAsJsonObject();
            JsonArray items = root.has("items") && root.get("items").isJsonArray() ? root.getAsJsonArray("items") : new JsonArray();

            String wanted = Text.normalize(name);
            String best = null;
            String bestTitle = "";
            double bestScore = -1.0D;
            for (JsonElement element : items)
            {
                if (!element.isJsonObject())
                {
                    continue;
                }
                JsonObject model = element.getAsJsonObject();
                if (!model.has("_id") || (model.has("type") && !"tts".equalsIgnoreCase(model.get("type").getAsString())))
                {
                    continue;
                }
                String title = model.has("title") ? model.get("title").getAsString() : "";
                double score = score(wanted, Text.normalize(title), languagesOf(model), language, number(model, "task_count"));
                if (score > bestScore)
                {
                    bestScore = score;
                    best = model.get("_id").getAsString();
                    bestTitle = title;
                }
            }
            if (best != null)
            {
                RoccoMod.LOGGER.info("[Rocco] Voz para imitar a {}: {} ({})", name, best, bestTitle);
            }
            else
            {
                RoccoMod.LOGGER.info("[Rocco] Fish Audio no tiene una voz publica para {}: la digo con la voz de Rocco.", name);
            }
            return best;
        }
        catch (IOException | RuntimeException e)
        {
            RoccoMod.LOGGER.warn("[Rocco] No pude buscar una voz para {}: {}", name, e.toString());
        }
        return null;
    }

    // Pesa mas que el nombre coincida, despues el idioma, y por ultimo lo usada que es.
    static double score(String wanted, String title, String languages, @Nullable String language, double uses)
    {
        double score = 0.0D;
        if (title.equals(wanted))
        {
            score += 400.0D;
        }
        else if (title.contains(wanted))
        {
            score += 300.0D;
        }
        else
        {
            // Cuantas palabras del nombre aparecen en el titulo ("cristiano" en "CR7 Cristiano").
            String[] words = wanted.split(" ");
            int hits = 0;
            for (String word : words)
            {
                if (word.length() > 2 && title.contains(word))
                {
                    hits++;
                }
            }
            score += 200.0D * hits / Math.max(1, words.length);
        }
        if (language != null && languages.contains(language.toLowerCase(Locale.ROOT)))
        {
            score += 100.0D;
        }
        // Hasta 99 puntos por uso, en escala logaritmica, para desempatar sin pisar lo anterior.
        return score + Math.min(99.0D, Math.log10(1.0D + Math.max(0.0D, uses)) * 15.0D);
    }

    private static String languagesOf(JsonObject model)
    {
        if (!model.has("languages") || !model.get("languages").isJsonArray())
        {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (JsonElement language : model.getAsJsonArray("languages"))
        {
            out.append(language.getAsString().toLowerCase(Locale.ROOT)).append(' ');
        }
        return out.toString();
    }

    private static double number(JsonObject model, String field)
    {
        try
        {
            return model.has(field) ? model.get(field).getAsDouble() : 0.0D;
        }
        catch (RuntimeException e)
        {
            return 0.0D;
        }
    }

    private static Path file()
    {
        return FMLPaths.CONFIGDIR.get().resolve("rocco").resolve("voces_encontradas.json");
    }

    private static Map<String, String> found()
    {
        if (found == null)
        {
            found = new HashMap<>();
            try
            {
                if (Files.exists(file()))
                {
                    Map<String, String> loaded = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8),
                            new TypeToken<Map<String, String>>() {}.getType());
                    if (loaded != null)
                    {
                        // Las versiones anteriores tambien guardaban los "no encontre" (vacios): esos
                        // se descartan para volver a buscarlos.
                        loaded.forEach((who, id) ->
                        {
                            if (id != null && !id.isBlank())
                            {
                                found.put(who, id);
                            }
                        });
                    }
                }
            }
            catch (IOException | RuntimeException e)
            {
                RoccoMod.LOGGER.debug("[Rocco] No pude leer las voces encontradas: {}", e.toString());
            }
        }
        return found;
    }

    private static void save(Map<String, String> cache)
    {
        try
        {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(cache), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            RoccoMod.LOGGER.debug("[Rocco] No pude guardar las voces encontradas: {}", e.toString());
        }
    }

    private ImpressionVoices() {}
}

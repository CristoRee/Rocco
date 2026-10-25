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
import java.util.Map;

// Que voz usar para una imitacion ("@voz:Vegeta|es"). En orden:
//  1. la lista de la config (impressionVoices: "Vegeta=id; Goku=id"), que completa el jugador:
//     el mod no trae ninguna voz de una persona real;
//  2. si el jugador lo activo, una voz publica de Fish Audio con ese nombre (se recuerda en
//     config/rocco/voces_encontradas.json para no buscarla de nuevo);
//  3. ninguna: Rocco la dice con su propia voz, imitando solo con el texto. Nunca falla.
public final class ImpressionVoices
{
    private static final Gson GSON = new Gson();
    private static Map<String, String> found;

    // Corre en el hilo de la voz: puede tardar si tiene que buscar.
    @Nullable
    public static String voiceFor(String name)
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
            if (cache.containsKey(key))
            {
                String id = cache.get(key);
                return id == null || id.isEmpty() ? null : id;
            }
            String id = search(name);
            // Tambien se recuerda que no hubo: asi no se pregunta en cada respuesta.
            cache.put(key, id == null ? "" : id);
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
            if (!id.isEmpty() && (who.equals(key) || key.contains(who) || who.contains(key)))
            {
                return id;
            }
        }
        return null;
    }

    // GET /model?title=... de Fish Audio: la voz publica mas popular con ese nombre.
    @Nullable
    private static String search(String name)
    {
        String key = RoccoConfig.fishApiKey();
        if (key.isEmpty())
        {
            return null;
        }
        String endpoint = RoccoCommonConfig.FISH_ENDPOINT.get().trim().replaceAll("/+$", "").replaceAll("/v1/tts$", "");
        String url = endpoint + "/model?page_size=1&sort_by=score&title=" + URLEncoder.encode(name, StandardCharsets.UTF_8);
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
            for (JsonElement item : items)
            {
                JsonObject model = item.getAsJsonObject();
                if (model.has("_id"))
                {
                    String id = model.get("_id").getAsString();
                    RoccoMod.LOGGER.info("[Rocco] Voz para imitar a {}: {} ({})", name, id,
                            model.has("title") ? model.get("title").getAsString() : "?");
                    return id;
                }
            }
        }
        catch (IOException | RuntimeException e)
        {
            RoccoMod.LOGGER.warn("[Rocco] No pude buscar una voz para {}: {}", name, e.toString());
        }
        return null;
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
                        found.putAll(loaded);
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

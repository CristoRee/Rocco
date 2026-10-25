package com.cristoree.rocco.voice;

import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.config.RoccoConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

// Voz a texto con Deepgram (audio grabado, POST /v1/listen). Se manda la frase entera al soltar
// la tecla: es lo mas simple y alcanza para charlar.
public final class Deepgram
{
    public record Result(String text, String error) {}

    public static Result transcribe(byte[] wav)
    {
        String key = RoccoConfig.deepgramApiKey();
        if (key.isEmpty())
        {
            return new Result(null, "falta la clave de Deepgram");
        }

        String model = RoccoCommonConfig.DEEPGRAM_MODEL.get().trim();
        Result first = request(key, model, wav);

        // Si el modelo nuevo no tiene el idioma pedido, se prueba con el anterior.
        if (first.text() == null && first.error() != null && first.error().startsWith("400") && model.startsWith("nova-3"))
        {
            return request(key, "nova-2", wav);
        }
        return first;
    }

    private static Result request(String key, String model, byte[] wav)
    {
        String language = RoccoCommonConfig.STT_LANGUAGE.get().trim();
        StringBuilder url = new StringBuilder(RoccoCommonConfig.DEEPGRAM_ENDPOINT.get().trim());
        url.append(url.indexOf("?") >= 0 ? '&' : '?');
        url.append("model=").append(encode(model.isEmpty() ? "nova-3" : model));
        if (!language.isEmpty())
        {
            url.append("&language=").append(encode(language));
        }
        url.append("&smart_format=true");
        url.append("&punctuate=").append(RoccoCommonConfig.STT_PUNCTUATE.get());

        try
        {
            VoiceHttp.Response response = VoiceHttp.post(url.toString(),
                    Map.of("Authorization", "Token " + key, "Content-Type", "audio/wav"), wav, 30);

            if (response.status() == 401 || response.status() == 403)
            {
                return new Result(null, "Deepgram rechazo la clave");
            }
            if (response.status() >= 400)
            {
                return new Result(null, response.status() + ": " + VoiceHttp.errorText(response));
            }

            JsonObject root = JsonParser.parseString(response.text()).getAsJsonObject();
            JsonArray channels = root.getAsJsonObject("results").getAsJsonArray("channels");
            JsonArray alternatives = channels.get(0).getAsJsonObject().getAsJsonArray("alternatives");
            String text = alternatives.get(0).getAsJsonObject().get("transcript").getAsString().trim();
            return new Result(text, null);
        }
        catch (IOException e)
        {
            return new Result(null, "no pude conectar con Deepgram (" + e.getMessage() + ")");
        }
        catch (RuntimeException e)
        {
            return new Result(null, "Deepgram mando algo que no pude leer");
        }
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Deepgram() {}
}

package com.cristoree.rocco.voice;

import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.config.RoccoConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// Voz a texto con un proveedor propio compatible con OpenAI (POST <url>/audio/transcriptions):
// Groq (Whisper, con capa gratis), OpenAI o un Whisper en tu PC. Se manda la frase entera al
// soltar la tecla, igual que a Deepgram.
final class OpenAiTranscribe
{
    private static final String WHO = "el proveedor de microfono propio";

    static Deepgram.Result transcribe(byte[] wav)
    {
        String base = RoccoCommonConfig.STT_CUSTOM_URL.get().trim().replaceAll("/+$", "");
        if (base.isEmpty())
        {
            return new Deepgram.Result(null, "falta la direccion del proveedor de microfono propio");
        }
        String endpoint = base.endsWith("/audio/transcriptions") ? base : base + "/audio/transcriptions";
        String boundary = "----rocco" + Long.toHexString(System.nanoTime());

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        String model = RoccoCommonConfig.STT_CUSTOM_MODEL.get().trim();
        if (!model.isEmpty())
        {
            field(body, boundary, "model", model);
        }
        // Whisper pide el idioma en dos letras ("es"); "multi" o vacio = que lo adivine.
        String language = RoccoCommonConfig.STT_LANGUAGE.get().trim().toLowerCase(Locale.ROOT);
        if (language.length() > 2 && (language.charAt(2) == '-' || language.charAt(2) == '_'))
        {
            language = language.substring(0, 2);
        }
        if (!language.isEmpty() && !language.equals("multi"))
        {
            field(body, boundary, "language", language);
        }
        field(body, boundary, "response_format", "json");
        field(body, boundary, "temperature", "0");
        write(body, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"rocco.wav\"\r\n"
                + "Content-Type: audio/wav\r\n\r\n");
        body.writeBytes(wav);
        write(body, "\r\n--" + boundary + "--\r\n");

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "multipart/form-data; boundary=" + boundary);
        headers.put("Accept", "application/json");
        String key = RoccoConfig.customSttApiKey();
        if (!key.isEmpty())
        {
            headers.put("Authorization", "Bearer " + key);
        }

        try
        {
            VoiceHttp.Response response = VoiceHttp.post(endpoint, headers, body.toByteArray(), 30);
            if (response.status() == 401 || response.status() == 403)
            {
                return new Deepgram.Result(null, WHO + " rechazo la clave");
            }
            if (response.status() >= 400)
            {
                return new Deepgram.Result(null, response.status() + ": " + VoiceHttp.errorText(response));
            }
            JsonObject root = JsonParser.parseString(response.text()).getAsJsonObject();
            if (!root.has("text"))
            {
                return new Deepgram.Result(null, WHO + " no mando el texto");
            }
            return new Deepgram.Result(root.get("text").getAsString().trim(), null);
        }
        catch (IOException e)
        {
            return new Deepgram.Result(null, "no pude conectar con " + WHO + " (" + com.cristoree.rocco.config.RoccoConfig.redact(String.valueOf(e.getMessage())) + ")");
        }
        catch (RuntimeException e)
        {
            return new Deepgram.Result(null, WHO + " mando algo que no pude leer");
        }
    }

    private static void field(ByteArrayOutputStream body, String boundary, String name, String value)
    {
        write(body, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n");
    }

    private static void write(ByteArrayOutputStream body, String text)
    {
        body.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    private OpenAiTranscribe() {}
}

package com.cristoree.rocco.voice;

import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.config.RoccoConfig;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

// Texto a voz con un proveedor propio compatible con OpenAI (POST <url>/audio/speech): OpenAI, o
// un servidor en tu PC (Kokoro, openedai-speech, LocalAI...). Se pide WAV, igual que a Fish Audio,
// y las frases dichas se guardan en el mismo cache.
final class OpenAiVoice
{
    private static final String WHO = "el proveedor de voz propio";

    static FishAudio.Result synthesize(String text)
    {
        String base = RoccoCommonConfig.VOICE_CUSTOM_URL.get().trim().replaceAll("/+$", "");
        if (base.isEmpty())
        {
            return new FishAudio.Result(null, "falta la direccion del proveedor de voz propio", true);
        }
        String endpoint = base.endsWith("/audio/speech") ? base : base + "/audio/speech";
        String model = RoccoCommonConfig.VOICE_CUSTOM_MODEL.get().trim();
        String voice = RoccoCommonConfig.VOICE_CUSTOM_VOICE.get().trim();
        double speed = RoccoCommonConfig.VOICE_SPEED.get();
        // Sin las marcas de emocion de Fish Audio: este proveedor las leeria en voz alta.
        String line = FishAudio.withEmotions(text, "");
        if (line.isEmpty())
        {
            return new FishAudio.Result(null, "no quedo nada para decir", false);
        }

        String cacheKey = String.join("|", "propio", endpoint, model, voice, String.valueOf(speed), line);
        Wav.Pcm cached = FishAudio.readCache(cacheKey);
        if (cached != null)
        {
            return new FishAudio.Result(cached, null, false);
        }

        JsonObject body = new JsonObject();
        if (!model.isEmpty())
        {
            body.addProperty("model", model);
        }
        body.addProperty("input", line);
        if (!voice.isEmpty())
        {
            body.addProperty("voice", voice);
        }
        body.addProperty("response_format", "wav");
        if (speed != 1.0D)
        {
            body.addProperty("speed", speed);
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "audio/wav");
        String key = RoccoConfig.customVoiceApiKey();
        if (!key.isEmpty())
        {
            headers.put("Authorization", "Bearer " + key);
        }

        try
        {
            VoiceHttp.Response response = VoiceHttp.post(endpoint, headers, body.toString().getBytes(StandardCharsets.UTF_8),
                    RoccoCommonConfig.VOICE_TIMEOUT_SECONDS.get());
            if (response.status() == 401 || response.status() == 403)
            {
                return new FishAudio.Result(null, WHO + " rechazo la clave (" + VoiceHttp.errorText(response) + ")", true);
            }
            if (response.status() == 404)
            {
                return new FishAudio.Result(null, WHO + " no tiene " + endpoint + " (revisa la direccion)", true);
            }
            if (response.status() >= 400)
            {
                return new FishAudio.Result(null, WHO + " respondio " + response.status() + ": " + VoiceHttp.errorText(response), false);
            }

            Wav.Pcm pcm = Wav.read(response.body());
            if (pcm == null || pcm.data().length == 0)
            {
                return new FishAudio.Result(null, WHO + " mando un audio que no es WAV de 16 bits", false);
            }
            FishAudio.writeCache(cacheKey, response.body());
            return new FishAudio.Result(pcm, null, false);
        }
        catch (IOException e)
        {
            return new FishAudio.Result(null, "no pude conectar con " + WHO + " (" + e.getMessage() + ")", false);
        }
    }

    private OpenAiVoice() {}
}

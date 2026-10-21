package com.cristoree.rocco.ai;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.config.RoccoConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

// Usa HttpURLConnection a proposito: java.net.http vive en su propio modulo y no siempre
// esta disponible bajo ModLauncher.
public final class GeminiClient
{
    public record Turn(boolean fromPlayer, String text) {}

    public record Reply(boolean ok, String text, String error) {}

    public static Reply ask(String systemPrompt, List<Turn> history, String question)
    {
        String apiKey = RoccoConfig.geminiApiKey();
        if (apiKey.isEmpty())
        {
            return new Reply(false, null, "Falta la clave de Gemini. Ponela en la config del mod.");
        }

        int attempts = RoccoCommonConfig.AI_MAX_RETRIES.get() + 1;
        String lastError = "sin detalle";

        for (int attempt = 1; attempt <= attempts; attempt++)
        {
            try
            {
                return send(apiKey, systemPrompt, history, question);
            }
            catch (IOException e)
            {
                lastError = e.getMessage() == null ? e.toString() : e.getMessage();
                RoccoMod.LOGGER.warn("[Rocco] Gemini intento {}/{} fallo: {}", attempt, attempts, lastError);
            }
        }

        return new Reply(false, null, lastError);
    }

    private static Reply send(String apiKey, String systemPrompt, List<Turn> history, String question)
            throws IOException
    {
        String model = RoccoCommonConfig.GEMINI_MODEL.get().trim();
        String base = RoccoCommonConfig.GEMINI_ENDPOINT.get().trim();
        if (base.endsWith("/"))
        {
            base = base.substring(0, base.length() - 1);
        }

        URL url = new URL(base + "/models" + "/" + URLEncoder.encode(model, StandardCharsets.UTF_8)
                + ":generateContent");

        JsonObject body = buildBody(model, systemPrompt, history, question);
        if (RoccoCommonConfig.LOG_REQUEST_BODIES.get())
        {
            RoccoMod.LOGGER.info("[Rocco] Gemini peticion: {}", body);
        }

        int timeout = RoccoCommonConfig.AI_TIMEOUT_SECONDS.get() * 1000;

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        // La clave va por cabecera, no en la URL: asi no aparece en logs ni en errores.
        connection.setRequestProperty("x-goog-api-key", apiKey);

        try
        {
            try (OutputStream out = connection.getOutputStream())
            {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            String payload = read(status >= 400 ? connection.getErrorStream() : connection.getInputStream());

            if (status >= 400)
            {
                return new Reply(false, null, "HTTP " + status + ": " + shortenError(payload));
            }

            return parse(payload);
        }
        finally
        {
            connection.disconnect();
        }
    }

    private static JsonObject buildBody(String model, String systemPrompt, List<Turn> history, String question)
    {
        JsonObject body = new JsonObject();

        body.add("systemInstruction", textContent(null, systemPrompt));

        JsonArray contents = new JsonArray();
        for (Turn turn : history)
        {
            contents.add(textContent(turn.fromPlayer() ? "user" : "model", turn.text()));
        }
        contents.add(textContent("user", question));
        body.add("contents", contents);

        JsonObject generation = new JsonObject();
        generation.addProperty("temperature", RoccoCommonConfig.AI_TEMPERATURE.get());
        generation.addProperty("topP", RoccoCommonConfig.AI_TOP_P.get());
        if (RoccoCommonConfig.AI_TOP_K.get() > 0)
        {
            generation.addProperty("topK", RoccoCommonConfig.AI_TOP_K.get());
        }
        generation.addProperty("maxOutputTokens", RoccoCommonConfig.AI_MAX_OUTPUT_TOKENS.get());

        // thinkingConfig solo existe en los modelos 2.5; mandarlo a otros da error 400.
        if (model.contains("2.5"))
        {
            JsonObject thinking = new JsonObject();
            thinking.addProperty("thinkingBudget", RoccoCommonConfig.AI_THINKING_BUDGET.get());
            generation.add("thinkingConfig", thinking);
        }
        body.add("generationConfig", generation);

        String threshold = switch (RoccoCommonConfig.AI_SAFETY_LEVEL.get())
        {
            case OFF -> "BLOCK_NONE";
            case LOW -> "BLOCK_ONLY_HIGH";
            case MEDIUM -> "BLOCK_MEDIUM_AND_ABOVE";
            case HIGH -> "BLOCK_LOW_AND_ABOVE";
        };

        JsonArray safety = new JsonArray();
        for (String category : new String[]{
                "HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT"})
        {
            JsonObject setting = new JsonObject();
            setting.addProperty("category", category);
            setting.addProperty("threshold", threshold);
            safety.add(setting);
        }
        body.add("safetySettings", safety);

        return body;
    }

    private static JsonObject textContent(String role, String text)
    {
        JsonObject part = new JsonObject();
        part.addProperty("text", text);

        JsonArray parts = new JsonArray();
        parts.add(part);

        JsonObject content = new JsonObject();
        if (role != null)
        {
            content.addProperty("role", role);
        }
        content.add("parts", parts);
        return content;
    }

    private static Reply parse(String payload)
    {
        JsonObject root = JsonParser.parseString(payload).getAsJsonObject();

        if (!root.has("candidates") || root.getAsJsonArray("candidates").isEmpty())
        {
            String blocked = root.has("promptFeedback")
                    ? "la peticion fue bloqueada por los filtros de Gemini"
                    : "Gemini no devolvio ninguna respuesta";
            return new Reply(false, null, blocked);
        }

        JsonObject candidate = root.getAsJsonArray("candidates").get(0).getAsJsonObject();

        if (!candidate.has("content"))
        {
            String reason = candidate.has("finishReason")
                    ? candidate.get("finishReason").getAsString()
                    : "desconocido";
            return new Reply(false, null, "respuesta vacia (finishReason " + reason + ")");
        }

        StringBuilder text = new StringBuilder();
        for (var element : candidate.getAsJsonObject("content").getAsJsonArray("parts"))
        {
            JsonObject part = element.getAsJsonObject();
            if (part.has("text"))
            {
                text.append(part.get("text").getAsString());
            }
        }

        String result = text.toString().trim();
        return result.isEmpty()
                ? new Reply(false, null, "Gemini devolvio texto vacio")
                : new Reply(true, result, null);
    }

    private static String read(InputStream stream) throws IOException
    {
        if (stream == null)
        {
            return "";
        }

        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                out.append(line);
            }
        }
        return out.toString();
    }

    private static String shortenError(String payload)
    {
        try
        {
            JsonObject root = JsonParser.parseString(payload).getAsJsonObject();
            if (root.has("error"))
            {
                JsonObject error = root.getAsJsonObject("error");
                if (error.has("message"))
                {
                    return error.get("message").getAsString();
                }
            }
        }
        catch (RuntimeException ignored)
        {
            // Si no es JSON, se devuelve el crudo recortado.
        }

        return payload.length() > 200 ? payload.substring(0, 200) + "..." : payload;
    }

    private GeminiClient() {}
}

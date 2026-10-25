package com.cristoree.rocco.ai;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Cliente de Google Gemini, con su formato propio (generateContent).
public final class GeminiClient
{
    // "gemini-3.8-flash" -> grupo 1 = 3, grupo 2 = 8. El decimal es opcional (gemini-3-flash).
    private static final Pattern MODEL_VERSION = Pattern.compile("gemini-(\\d+)(?:\\.(\\d+))?");

    // Nivel de razonamiento que cada modelo acepto en esta sesion (ver raiseThinkingLevel).
    private static final Map<String, RoccoCommonConfig.ThinkingLevel> LEARNED_THINKING_LEVEL =
            new ConcurrentHashMap<>();

    // Variantes que no sirven para charlar: generan imagenes, audio, embeddings, etc.
    private static final String[] NOT_FOR_CHAT = {
            "image", "tts", "live", "audio", "embedding", "aqa", "learnlm", "computer-use",
            "robotics", "native", "veo", "imagen", "gemma"
    };

    public static ChatReply chat(String apiKey, String model, String systemPrompt, List<ChatTurn> history,
                                 String question) throws IOException
    {
        String url = AiProvider.GEMINI.baseUrl() + "/models/"
                + URLEncoder.encode(model, StandardCharsets.UTF_8) + ":generateContent";

        JsonObject body = buildBody(model, systemPrompt, history, question);
        if (RoccoCommonConfig.LOG_REQUEST_BODIES.get())
        {
            RoccoMod.LOGGER.info("[Rocco] Gemini peticion: {}", body);
        }

        // La clave va por cabecera, no en la URL: asi no aparece en logs ni en errores.
        AiHttp.Response response = AiHttp.post(url, Map.of("x-goog-api-key", apiKey), body);

        if (response.status() >= 400)
        {
            String detail = AiHttp.errorMessage(response.body());

            // Cada modelo 3.x acepta niveles distintos (gemini-3.8-flash rechaza MINIMAL).
            // Se sube un nivel y se reintenta; queda recordado por modelo. Como mucho 3 veces.
            if (response.status() == 400 && majorVersion(model) >= 3 && raiseThinkingLevel(model, detail))
            {
                return chat(apiKey, model, systemPrompt, history, question);
            }

            return classifyError(response.status(), detail, model);
        }

        return parse(response.body(), model);
    }

    // Modelos que Google ofrece hoy a esta clave y sirven para charlar.
    public static List<String> listModels(String apiKey) throws IOException
    {
        AiHttp.Response response = AiHttp.get(AiProvider.GEMINI.baseUrl() + "/models?pageSize=1000",
                Map.of("x-goog-api-key", apiKey));
        if (response.status() >= 400)
        {
            throw new IOException("HTTP " + response.status() + ": " + AiHttp.errorMessage(response.body()));
        }

        List<String> out = new ArrayList<>();
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!root.has("models"))
        {
            return out;
        }

        for (var element : root.getAsJsonArray("models"))
        {
            JsonObject model = element.getAsJsonObject();
            boolean generates = model.has("supportedGenerationMethods")
                    && model.getAsJsonArray("supportedGenerationMethods").toString().contains("generateContent");
            if (generates && model.has("name"))
            {
                out.add(model.get("name").getAsString().replaceFirst("^models/", ""));
            }
        }
        return out;
    }

    // Elige el mejor modelo para charlar: el flash mas nuevo y estable. Si no hay estable acepta
    // preview, y si no hay flash, cualquier gemini que sirva para texto.
    public static String chooseModel(List<String> available)
    {
        List<String> usable = available.stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .filter(name -> name.startsWith("gemini-"))
                .filter(name -> !containsAny(name, NOT_FOR_CHAT))
                .toList();

        Comparator<String> newestFirst = Comparator
                .comparingInt(GeminiClient::majorVersion)
                .thenComparingInt(GeminiClient::minorVersion)
                .reversed();

        String[][] tiers = {
                // flash estable, flash-lite estable, flash preview, cualquiera
                {"flash", "!lite", "!preview", "!exp"},
                {"flash", "!preview", "!exp"},
                {"flash"},
                {}
        };

        for (String[] tier : tiers)
        {
            List<String> matches = usable.stream().filter(name -> matchesTier(name, tier))
                    .sorted(newestFirst).toList();
            if (!matches.isEmpty())
            {
                return matches.get(0);
            }
        }
        return null;
    }

    private static boolean matchesTier(String name, String[] rules)
    {
        for (String rule : rules)
        {
            boolean negate = rule.startsWith("!");
            String word = negate ? rule.substring(1) : rule;
            if (name.contains(word) == negate)
            {
                return false;
            }
        }
        return true;
    }

    private static boolean containsAny(String text, String[] words)
    {
        for (String word : words)
        {
            if (text.contains(word))
            {
                return true;
            }
        }
        return false;
    }

    static JsonObject buildBody(String model, String systemPrompt, List<ChatTurn> history, String question)
    {
        JsonObject body = new JsonObject();

        body.add("systemInstruction", textContent(null, systemPrompt));

        JsonArray contents = new JsonArray();
        for (ChatTurn turn : history)
        {
            contents.add(textContent(turn.fromPlayer() ? "user" : "model", turn.text()));
        }
        contents.add(textContent("user", question));
        body.add("contents", contents);

        JsonObject generation = new JsonObject();
        generation.addProperty("maxOutputTokens", RoccoCommonConfig.AI_MAX_OUTPUT_TOKENS.get());

        int major = majorVersion(model);

        if (major >= 3)
        {
            // Gemini 3.x: el razonamiento se pide con thinkingLevel. Google indica no mandar
            // temperature, topP ni topK, y thinkingLevel junto con thinkingBudget da HTTP 400.
            JsonObject thinking = new JsonObject();
            thinking.addProperty("thinkingLevel", thinkingLevelFor(model).name().toLowerCase(Locale.ROOT));
            generation.add("thinkingConfig", thinking);
        }
        else
        {
            generation.addProperty("temperature", RoccoCommonConfig.AI_TEMPERATURE.get());
            generation.addProperty("topP", RoccoCommonConfig.AI_TOP_P.get());
            if (RoccoCommonConfig.AI_TOP_K.get() > 0)
            {
                generation.addProperty("topK", RoccoCommonConfig.AI_TOP_K.get());
            }

            // thinkingConfig solo existe desde el 2.5; en 2.0 y anteriores da error 400.
            if (major == 2 && minorVersion(model) >= 5)
            {
                JsonObject thinking = new JsonObject();
                thinking.addProperty("thinkingBudget", RoccoCommonConfig.AI_THINKING_BUDGET.get());
                generation.add("thinkingConfig", thinking);
            }
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

    static ChatReply parse(String payload, String model)
    {
        String provider = AiProvider.GEMINI.label();
        JsonObject root = JsonParser.parseString(payload).getAsJsonObject();

        if (!root.has("candidates") || root.getAsJsonArray("candidates").isEmpty())
        {
            if (root.has("promptFeedback") && root.get("promptFeedback").isJsonObject())
            {
                JsonObject feedback = root.getAsJsonObject("promptFeedback");
                String why = feedback.has("blockReason") ? feedback.get("blockReason").getAsString() : "?";
                return ChatReply.fail(ChatReply.Failure.BLOCKED,
                        "el filtro de Gemini bloqueo el mensaje antes de responder (" + why + ", "
                                + safetyDetail(feedback) + "). Baja safetyLevel en Mods > Rocco > Config, seccion ai.",
                        provider, model);
            }
            return ChatReply.fail(ChatReply.Failure.UNREADABLE, "Gemini no devolvio ninguna respuesta", provider, model);
        }

        JsonObject candidate = root.getAsJsonArray("candidates").get(0).getAsJsonObject();

        String reason = candidate.has("finishReason") && !candidate.get("finishReason").isJsonNull()
                ? candidate.get("finishReason").getAsString()
                : "desconocido";

        // Con Gemini 3.x un content sin "parts" es normal si el razonamiento se comio todo el tope.
        StringBuilder text = new StringBuilder();
        if (candidate.has("content") && candidate.get("content").isJsonObject()
                && candidate.getAsJsonObject("content").has("parts"))
        {
            for (var element : candidate.getAsJsonObject("content").getAsJsonArray("parts"))
            {
                JsonObject part = element.getAsJsonObject();

                // Las partes de razonamiento no son parte de lo que dice Rocco.
                if (part.has("thought") && part.get("thought").getAsBoolean())
                {
                    continue;
                }

                if (part.has("text"))
                {
                    text.append(part.get("text").getAsString());
                }
            }
        }

        String result = text.toString().trim();
        if (!result.isEmpty())
        {
            return ChatReply.success(result, provider, model);
        }

        if (reason.equals("SAFETY") || reason.equals("PROHIBITED_CONTENT"))
        {
            String detail = safetyDetail(candidate);
            RoccoMod.LOGGER.warn("[Rocco] Gemini bloqueo la respuesta por seguridad: {} | {}",
                    detail, candidate.get("safetyRatings"));
            return ChatReply.fail(ChatReply.Failure.BLOCKED, "el filtro de seguridad de Gemini bloqueo mi respuesta ("
                    + detail + "). Baja safetyLevel a OFF en Mods > Rocco > Config, seccion ai.", provider, model);
        }

        if (reason.equals("MAX_TOKENS"))
        {
            return ChatReply.fail(ChatReply.Failure.TRUNCATED, "Gemini se quedo sin tokens antes de escribir la "
                    + "respuesta (finishReason MAX_TOKENS). Sube maxOutputTokens en la config, seccion ai: con los "
                    + "modelos 3.x el razonamiento cuenta contra ese tope y hacen falta 400 o mas.", provider, model);
        }

        return ChatReply.fail(ChatReply.Failure.UNREADABLE,
                "Gemini devolvio una respuesta vacia (finishReason " + reason + ")", provider, model);
    }

    // Un 404 con "no existe / no disponible" casi siempre es el modelo, no la URL.
    static ChatReply classifyError(int status, String detail, String model)
    {
        String provider = AiProvider.GEMINI.label();
        String lower = detail.toLowerCase(Locale.ROOT);

        if (lower.contains("api key not valid") || lower.contains("api_key_invalid") || status == 401)
        {
            return ChatReply.fail(ChatReply.Failure.AUTH,
                    "la clave de Gemini no es valida. Revisala en Mods > Rocco > Config, seccion ai.", provider, model);
        }
        if (status == 403)
        {
            return ChatReply.fail(ChatReply.Failure.AUTH, "Gemini rechazo la clave (HTTP 403): " + detail, provider, model);
        }
        if (status == 429)
        {
            return ChatReply.fail(ChatReply.Failure.RATE_LIMIT,
                    "se acabo el cupo gratis de Gemini por ahora (HTTP 429).", provider, model);
        }

        boolean modelProblem = status == 404 && (lower.contains("not found")
                || lower.contains("no longer available")
                || lower.contains("is not supported")
                || lower.contains("does not exist")
                || lower.contains("models/"));
        if (modelProblem)
        {
            return ChatReply.fail(ChatReply.Failure.MODEL_MISSING, "el modelo '" + model
                    + "' no existe o ya no esta disponible para tu clave (HTTP 404). Pone 'auto' en geminiModel "
                    + "(Mods > Rocco > Config, seccion ai) y Rocco elige solo.", provider, model);
        }
        if (status >= 500)
        {
            return ChatReply.fail(ChatReply.Failure.SERVER, "Gemini fallo de su lado (HTTP " + status + ").", provider, model);
        }
        return ChatReply.fail(ChatReply.Failure.BAD_REQUEST, "HTTP " + status + ": " + detail, provider, model);
    }

    // El nivel que de verdad se manda: el de la config, o uno mas alto si el modelo ya rechazo
    // el configurado en esta sesion.
    static RoccoCommonConfig.ThinkingLevel thinkingLevelFor(String model)
    {
        RoccoCommonConfig.ThinkingLevel configured = RoccoCommonConfig.AI_THINKING_LEVEL.get();
        RoccoCommonConfig.ThinkingLevel learned = LEARNED_THINKING_LEVEL.get(modelKey(model));

        return learned != null && learned.ordinal() > configured.ordinal() ? learned : configured;
    }

    // Si el error es "Thinking level X is not supported", anota el siguiente nivel para ese
    // modelo y devuelve true para reintentar. False si no era ese error o ya no hay mas alto.
    static boolean raiseThinkingLevel(String model, String detail)
    {
        String lower = detail.toLowerCase(Locale.ROOT);
        if (!lower.contains("thinking level") || !lower.contains("not supported"))
        {
            return false;
        }

        RoccoCommonConfig.ThinkingLevel current = thinkingLevelFor(model);
        RoccoCommonConfig.ThinkingLevel[] levels = RoccoCommonConfig.ThinkingLevel.values();
        if (current.ordinal() >= levels.length - 1)
        {
            return false;
        }

        RoccoCommonConfig.ThinkingLevel next = levels[current.ordinal() + 1];
        LEARNED_THINKING_LEVEL.put(modelKey(model), next);
        RoccoMod.LOGGER.warn("[Rocco] {} no acepta thinkingLevel {}; pruebo con {}. "
                + "Para no hacer este reintento, pone ai.thinkingLevel en {}.", model.trim(), current, next, next);
        return true;
    }

    private static String modelKey(String model)
    {
        return model.trim().toLowerCase(Locale.ROOT);
    }

    // Traduce las categorias del filtro de Google para que el jugador sepa que lo disparo.
    private static String safetyDetail(JsonObject holder)
    {
        if (!holder.has("safetyRatings") || !holder.get("safetyRatings").isJsonArray())
        {
            return "sin detalle de categoria";
        }

        List<String> flagged = new ArrayList<>();
        for (var element : holder.getAsJsonArray("safetyRatings"))
        {
            JsonObject rating = element.getAsJsonObject();
            String probability = rating.has("probability") ? rating.get("probability").getAsString() : "";
            boolean blocked = rating.has("blocked") && rating.get("blocked").getAsBoolean();

            if (blocked || probability.equals("MEDIUM") || probability.equals("HIGH"))
            {
                String category = rating.has("category") ? rating.get("category").getAsString() : "?";
                flagged.add(switch (category)
                {
                    case "HARM_CATEGORY_HARASSMENT" -> "acoso";
                    case "HARM_CATEGORY_HATE_SPEECH" -> "discurso de odio";
                    case "HARM_CATEGORY_SEXUALLY_EXPLICIT" -> "contenido sexual";
                    case "HARM_CATEGORY_DANGEROUS_CONTENT" -> "contenido peligroso";
                    default -> category;
                } + " (" + probability.toLowerCase(Locale.ROOT) + ")");
            }
        }

        return flagged.isEmpty() ? "sin detalle de categoria" : String.join(", ", flagged);
    }

    // "gemini-3.8-flash" -> 3. Devuelve -1 si el nombre no sigue ese formato.
    public static int majorVersion(String model)
    {
        Matcher matcher = MODEL_VERSION.matcher(model.trim().toLowerCase(Locale.ROOT));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    // "gemini-2.5-flash" -> 5. Sin decimal (gemini-3-flash) cuenta como 0.
    public static int minorVersion(String model)
    {
        Matcher matcher = MODEL_VERSION.matcher(model.trim().toLowerCase(Locale.ROOT));
        if (!matcher.find() || matcher.group(2) == null)
        {
            return 0;
        }
        return Integer.parseInt(matcher.group(2));
    }

    public static boolean isRetiredModel(String model)
    {
        return model.trim().equalsIgnoreCase(RoccoCommonConfig.RETIRED_GEMINI_MODEL);
    }

    private GeminiClient() {}
}

package com.cristoree.rocco.ai;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

// Un solo cliente para todo lo que hable el formato de OpenAI (/chat/completions): Groq,
// OpenRouter, Ollama, OpenAI, DeepSeek, Mistral, xAI, LM Studio...
public final class OpenAiCompatClient
{
    // Lo que piensan algunos modelos (Qwen, DeepSeek) llega entre estas etiquetas.
    private static final Pattern THINK_BLOCK = Pattern.compile("(?is)<think>.*?</think>");

    private static final int MAX_TOKENS_CEILING = 4096;
    private static final int MAX_ADAPTATIONS = 4;

    // Parametros que cada proveedor+modelo rechazo: se dejan de mandar en esta sesion.
    private static final Map<String, Set<String>> DROPPED_PARAMS = new ConcurrentHashMap<>();
    // Los modelos nuevos de OpenAI piden max_completion_tokens en vez de max_tokens.
    private static final Set<String> USES_COMPLETION_TOKENS = ConcurrentHashMap.newKeySet();

    private static final String[] TUNABLE_PARAMS = {
            "max_completion_tokens", "max_tokens", "temperature", "top_p", "reasoning_effort"
    };

    // Lo que no sirve para charlar en una lista de modelos.
    private static final String[] NOT_FOR_CHAT = {
            "whisper", "tts", "guard", "safeguard", "safety", "orpheus", "playai", "compound",
            "embed", "moderation", "dall-e", "image", "audio", "realtime", "transcribe", "search",
            "code", "coder", "vision", "-vl"
    };

    public static ChatReply chat(AiProvider provider, String model, String systemPrompt, List<ChatTurn> history,
                                 String question) throws IOException
    {
        return chat(provider, model, systemPrompt, history, question,
                RoccoCommonConfig.AI_MAX_OUTPUT_TOKENS.get(), 0);
    }

    private static ChatReply chat(AiProvider provider, String model, String systemPrompt, List<ChatTurn> history,
                                  String question, int maxTokens, int depth) throws IOException
    {
        JsonObject body = buildBody(provider, model, systemPrompt, history, question, maxTokens);
        if (RoccoCommonConfig.LOG_REQUEST_BODIES.get())
        {
            RoccoMod.LOGGER.info("[Rocco] {} peticion: {}", provider.label(), body);
        }

        AiHttp.Response response = AiHttp.post(provider.baseUrl() + "/chat/completions", headers(provider), body);

        if (response.status() >= 400)
        {
            String detail = AiHttp.errorMessage(response.body());

            if (response.status() == 400 && depth < MAX_ADAPTATIONS && adaptToRejectedParam(provider, model, detail))
            {
                return chat(provider, model, systemPrompt, history, question, maxTokens, depth + 1);
            }

            return classifyError(provider, model, response.status(), detail);
        }

        ChatReply reply = parse(provider, model, response.body());

        // Los modelos que razonan (gpt-oss, Qwen3...) gastan tokens pensando antes de escribir.
        // Si no les alcanzo, se les da el doble una o dos veces antes de rendirse.
        if (reply.failure() == ChatReply.Failure.TRUNCATED && depth < MAX_ADAPTATIONS && maxTokens < MAX_TOKENS_CEILING)
        {
            int more = Math.min(MAX_TOKENS_CEILING, maxTokens * 2);
            RoccoMod.LOGGER.info("[Rocco] {} ({}) se quedo sin tokens con {}; reintento con {}.",
                    provider.label(), model, maxTokens, more);
            return chat(provider, model, systemPrompt, history, question, more, depth + 1);
        }

        return reply;
    }

    public static List<String> listModels(AiProvider provider) throws IOException
    {
        AiHttp.Response response = AiHttp.get(provider.baseUrl() + "/models", headers(provider));
        if (response.status() >= 400)
        {
            throw new IOException("HTTP " + response.status() + ": " + AiHttp.errorMessage(response.body()));
        }

        List<String> out = new ArrayList<>();
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        if (root.has("data") && root.get("data").isJsonArray())
        {
            for (JsonElement element : root.getAsJsonArray("data"))
            {
                JsonObject model = element.getAsJsonObject();
                if (model.has("id"))
                {
                    out.add(model.get("id").getAsString());
                }
            }
        }
        return out;
    }

    // Elige un modelo para charlar entre los que el proveedor ofrece hoy. Las preferencias son
    // solo un orden: si ninguna esta, se usa el primero que sirva, asi sigue andando aunque el
    // proveedor cambie todo su catalogo.
    public static String chooseModel(AiProvider provider, List<String> available)
    {
        String[] preferences = switch (provider)
        {
            case GROQ -> new String[]{"gpt-oss-120b", "qwen3", "qwen", "kimi", "llama-4", "llama-3.3-70b",
                    "gpt-oss-20b", "llama", "gemma"};
            case OPENROUTER -> new String[]{"openrouter/free", ":free"};
            default -> new String[]{};
        };

        List<String> usable = available.stream()
                .filter(id -> !containsAny(id.toLowerCase(Locale.ROOT), NOT_FOR_CHAT))
                .toList();

        for (String preference : preferences)
        {
            for (String id : usable)
            {
                if (id.toLowerCase(Locale.ROOT).contains(preference))
                {
                    return id;
                }
            }
        }

        return usable.isEmpty() ? null : usable.get(0);
    }

    static JsonObject buildBody(AiProvider provider, String model, String systemPrompt, List<ChatTurn> history,
                                String question, int maxTokens)
    {
        Set<String> dropped = DROPPED_PARAMS.getOrDefault(key(provider, model), Set.of());

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("stream", false);

        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt));
        for (ChatTurn turn : history)
        {
            messages.add(message(turn.fromPlayer() ? "user" : "assistant", turn.text()));
        }
        messages.add(message("user", question));
        body.add("messages", messages);

        String tokensParam = USES_COMPLETION_TOKENS.contains(key(provider, model)) ? "max_completion_tokens" : "max_tokens";
        if (!dropped.contains(tokensParam))
        {
            body.addProperty(tokensParam, maxTokens);
        }
        if (!dropped.contains("temperature"))
        {
            body.addProperty("temperature", RoccoCommonConfig.AI_TEMPERATURE.get());
        }
        if (!dropped.contains("top_p"))
        {
            body.addProperty("top_p", RoccoCommonConfig.AI_TOP_P.get());
        }

        // En Groq, gpt-oss razona mucho por defecto: con "low" responde rapido para charlar.
        if (provider == AiProvider.GROQ && model.contains("gpt-oss") && !dropped.contains("reasoning_effort"))
        {
            body.addProperty("reasoning_effort", "low");
        }

        return body;
    }

    private static JsonObject message(String role, String content)
    {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    static ChatReply parse(AiProvider provider, String model, String payload)
    {
        String label = provider.label();
        JsonObject root = JsonParser.parseString(payload).getAsJsonObject();

        // OpenRouter a veces responde 200 con un error adentro (el modelo de atras fallo).
        if (root.has("error") && !root.has("choices"))
        {
            String detail = AiHttp.errorMessage(payload);
            int code = 0;
            if (root.get("error").isJsonObject() && root.getAsJsonObject("error").has("code"))
            {
                try
                {
                    code = root.getAsJsonObject("error").get("code").getAsInt();
                }
                catch (RuntimeException ignored)
                {
                    // codigo no numerico: se clasifica por el texto
                }
            }
            return classifyError(provider, model, code, detail);
        }

        if (!root.has("choices") || root.getAsJsonArray("choices").isEmpty())
        {
            return ChatReply.fail(ChatReply.Failure.UNREADABLE, label + " no devolvio ninguna respuesta", label, model);
        }

        JsonObject choice = root.getAsJsonArray("choices").get(0).getAsJsonObject();
        String finish = choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()
                ? choice.get("finish_reason").getAsString()
                : "";

        String text = "";
        if (choice.has("message") && choice.get("message").isJsonObject())
        {
            text = contentText(choice.getAsJsonObject("message").get("content"));
        }

        text = THINK_BLOCK.matcher(text).replaceAll("").trim();
        if (!text.isEmpty())
        {
            return ChatReply.success(text, label, model);
        }

        if (finish.equals("length"))
        {
            return ChatReply.fail(ChatReply.Failure.TRUNCATED, label + " se quedo sin tokens antes de "
                    + "escribir la respuesta. Sube maxOutputTokens en Mods > Rocco > Config, seccion ai.", label, model);
        }
        if (finish.equals("content_filter"))
        {
            return ChatReply.fail(ChatReply.Failure.BLOCKED, "el filtro de " + label + " bloqueo la respuesta.",
                    label, model);
        }
        return ChatReply.fail(ChatReply.Failure.UNREADABLE,
                label + " devolvio una respuesta vacia" + (finish.isEmpty() ? "" : " (" + finish + ")"), label, model);
    }

    // El contenido puede venir como texto o como lista de partes.
    private static String contentText(JsonElement content)
    {
        if (content == null || content.isJsonNull())
        {
            return "";
        }
        if (content.isJsonPrimitive())
        {
            return content.getAsString();
        }
        if (content.isJsonArray())
        {
            StringBuilder out = new StringBuilder();
            for (JsonElement part : content.getAsJsonArray())
            {
                if (part.isJsonObject() && part.getAsJsonObject().has("text"))
                {
                    out.append(part.getAsJsonObject().get("text").getAsString());
                }
            }
            return out.toString();
        }
        return "";
    }

    static ChatReply classifyError(AiProvider provider, String model, int status, String detail)
    {
        String label = provider.label();
        String lower = detail.toLowerCase(Locale.ROOT);

        boolean modelProblem = lower.contains("model") && (lower.contains("not found")
                || lower.contains("does not exist") || lower.contains("decommissioned")
                || lower.contains("no longer") || lower.contains("not available")
                || lower.contains("invalid model") || lower.contains("unknown model")
                || lower.contains("no endpoints"));

        if (status == 401 || status == 403 || lower.contains("invalid api key") || lower.contains("incorrect api key"))
        {
            return ChatReply.fail(ChatReply.Failure.AUTH, "la clave de " + label
                    + " no es valida. Revisala en Mods > Rocco > Config.", label, model);
        }
        if (status == 402)
        {
            return ChatReply.fail(ChatReply.Failure.RATE_LIMIT, label + " no tiene credito para este modelo (HTTP 402).",
                    label, model);
        }
        if (status == 429 || lower.contains("rate limit") || lower.contains("quota"))
        {
            return ChatReply.fail(ChatReply.Failure.RATE_LIMIT, "se acabo el cupo de " + label + " por ahora.", label, model);
        }
        // Un 404 en /chat/completions es el modelo (o, en un proveedor propio, la URL).
        // OpenRouter lo dice como "No endpoints found for ...", sin la palabra "model".
        if (modelProblem || status == 404 || lower.contains("no endpoints found"))
        {
            String hint = provider == AiProvider.CUSTOM ? " (o la URL esta mal)" : "";
            return ChatReply.fail(ChatReply.Failure.MODEL_MISSING, "el modelo '" + model + "' no esta disponible en "
                    + label + hint + ". Pone 'auto' como modelo y Rocco elige solo.", label, model);
        }
        if (lower.contains("content") && (lower.contains("policy") || lower.contains("moderation") || lower.contains("flagged")))
        {
            return ChatReply.fail(ChatReply.Failure.BLOCKED, "el filtro de " + label + " bloqueo la respuesta.", label, model);
        }
        if (status >= 500)
        {
            return ChatReply.fail(ChatReply.Failure.SERVER, label + " fallo de su lado (HTTP " + status + ").", label, model);
        }
        return ChatReply.fail(ChatReply.Failure.BAD_REQUEST, label + " rechazo la consulta (HTTP " + status + "): "
                + detail, label, model);
    }

    // Si el 400 dice que un parametro no se acepta, se deja de mandar y se reintenta.
    static boolean adaptToRejectedParam(AiProvider provider, String model, String detail)
    {
        String lower = detail.toLowerCase(Locale.ROOT);
        boolean rejection = lower.contains("unsupported") || lower.contains("not supported")
                || lower.contains("does not support") || lower.contains("unrecognized")
                || lower.contains("unknown parameter") || lower.contains("extra inputs")
                || lower.contains("not permitted") || lower.contains("use 'max_completion_tokens'")
                || lower.contains("use max_completion_tokens");
        if (!rejection)
        {
            return false;
        }

        String key = key(provider, model);

        // OpenAI: "max_tokens is not supported with this model. Use max_completion_tokens instead."
        if (lower.contains("max_tokens") && lower.contains("max_completion_tokens") && !USES_COMPLETION_TOKENS.contains(key))
        {
            USES_COMPLETION_TOKENS.add(key);
            RoccoMod.LOGGER.info("[Rocco] {} ({}) pide max_completion_tokens; lo uso.", provider.label(), model);
            return true;
        }

        Set<String> dropped = DROPPED_PARAMS.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet());
        for (String param : TUNABLE_PARAMS)
        {
            if (lower.contains(param) && !dropped.contains(param))
            {
                dropped.add(param);
                RoccoMod.LOGGER.info("[Rocco] {} ({}) no acepta '{}'; lo saco y reintento.", provider.label(), model, param);
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> headers(AiProvider provider)
    {
        Map<String, String> headers = new HashMap<>();
        String apiKey = provider.apiKey();
        if (!apiKey.isEmpty())
        {
            headers.put("Authorization", "Bearer " + apiKey);
        }
        return headers;
    }

    private static String key(AiProvider provider, String model)
    {
        return provider.id() + "|" + model.toLowerCase(Locale.ROOT);
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

    private OpenAiCompatClient() {}
}

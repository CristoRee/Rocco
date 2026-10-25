package com.cristoree.rocco.ai;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoCommonConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Elige con que IA contestar. Prueba los proveedores configurados en orden y, si uno falla
// (sin cupo, clave mala, caido, modelo retirado), pasa al siguiente. Ningun proveedor gratis es
// confiable solo: entre varios, Rocco casi siempre tiene con quien pensar.
public final class AiRouter
{
    // Para las pruebas: permite reemplazar la llamada real por una simulada.
    interface Caller
    {
        ChatReply call(AiProvider provider, String model, String systemPrompt, List<ChatTurn> history,
                       String question) throws IOException;
    }

    static Caller caller = AiRouter::realCall;

    private static final Map<AiProvider, Long> PAUSED_UNTIL = new ConcurrentHashMap<>();
    private static final Map<AiProvider, String> LAST_ERROR = new ConcurrentHashMap<>();
    private static volatile AiProvider lastUsed;

    // Proveedores que se van a probar, en orden.
    public static List<AiProvider> candidates()
    {
        RoccoCommonConfig.AiProviderChoice choice = RoccoCommonConfig.AI_PROVIDER.get();

        if (choice != RoccoCommonConfig.AiProviderChoice.AUTO)
        {
            AiProvider forced = AiProvider.byId(choice.name());
            return forced != null && forced.isConfigured() ? List.of(forced) : List.of();
        }

        Set<AiProvider> ordered = new LinkedHashSet<>();
        for (String id : RoccoCommonConfig.AI_PROVIDER_ORDER.get().split(","))
        {
            AiProvider provider = AiProvider.byId(id);
            if (provider != null && provider.isConfigured())
            {
                ordered.add(provider);
            }
        }

        // Uno configurado que el jugador olvido poner en el orden igual se usa, al final.
        for (AiProvider provider : AiProvider.values())
        {
            if (provider.isConfigured())
            {
                ordered.add(provider);
            }
        }

        return new ArrayList<>(ordered);
    }

    public static ChatReply ask(String systemPrompt, List<ChatTurn> history, String question)
    {
        List<AiProvider> providers = candidates();
        if (providers.isEmpty())
        {
            return ChatReply.fail(ChatReply.Failure.NOT_CONFIGURED,
                    "no hay ninguna IA configurada", null, null);
        }

        List<String> problems = new ArrayList<>();
        ChatReply last = null;
        Set<AiProvider> tried = new LinkedHashSet<>();

        // Primera pasada: se saltean los que estan en pausa. Si estaban todos en pausa, se
        // prueban igual: mejor un intento que no contestar nada.
        for (int pass = 0; pass < 2; pass++)
        {
            for (AiProvider provider : providers)
            {
                if (tried.contains(provider) || (pass == 0 && isPaused(provider)))
                {
                    continue;
                }

                tried.add(provider);
                ChatReply reply = attempt(provider, systemPrompt, history, question);

                if (reply.ok())
                {
                    lastUsed = provider;
                    LAST_ERROR.remove(provider);
                    PAUSED_UNTIL.remove(provider);
                    return reply;
                }

                last = reply;
                LAST_ERROR.put(provider, reply.error());
                pause(provider, reply.failure());
                problems.add(provider.label() + ": " + reply.error());
                RoccoMod.LOGGER.warn("[Rocco] {} fallo ({}): {}", provider.label(), reply.failure(), reply.error());
            }

            if (!tried.isEmpty())
            {
                break;
            }
        }

        if (last == null)
        {
            return ChatReply.fail(ChatReply.Failure.NOT_CONFIGURED, "no hay ninguna IA disponible", null, null);
        }

        // Con un solo proveedor, su mensaje ya es claro; con varios, se resumen todos.
        String summary = problems.size() == 1 ? last.error() : String.join(" | ", problems);
        return ChatReply.fail(last.failure(), summary, last.provider(), last.model());
    }

    private static ChatReply attempt(AiProvider provider, String systemPrompt, List<ChatTurn> history, String question)
    {
        String model;
        try
        {
            model = ModelResolver.modelFor(provider);
        }
        catch (IOException | RuntimeException e)
        {
            return ChatReply.fail(networkFailure(provider, e), describe(provider, e), provider.label(), null);
        }

        ChatReply reply = callWithRetries(provider, model, systemPrompt, history, question);

        // Modelo retirado: se busca uno vigente en el catalogo y se reintenta una vez.
        if (reply.failure() == ChatReply.Failure.MODEL_MISSING)
        {
            String replacement = ModelResolver.replaceMissing(provider, model);
            if (replacement != null)
            {
                reply = callWithRetries(provider, replacement, systemPrompt, history, question);
            }
        }

        return reply;
    }

    private static ChatReply callWithRetries(AiProvider provider, String model, String systemPrompt,
                                             List<ChatTurn> history, String question)
    {
        int attempts = RoccoCommonConfig.AI_MAX_RETRIES.get() + 1;
        ChatReply reply = null;

        for (int attempt = 1; attempt <= attempts; attempt++)
        {
            try
            {
                reply = caller.call(provider, model, systemPrompt, history, question);
            }
            catch (IOException e)
            {
                reply = ChatReply.fail(networkFailure(provider, e), describe(provider, e), provider.label(), model);
            }
            catch (RuntimeException e)
            {
                // Una respuesta con forma rara no mejora reintentando, y si la excepcion se
                // escapa, Rocco se queda esperando para siempre.
                RoccoMod.LOGGER.error("[Rocco] {} devolvio algo que no se pudo leer", provider.label(), e);
                return ChatReply.fail(ChatReply.Failure.UNREADABLE,
                        "no pude leer la respuesta de " + provider.label() + " (" + e + ")", provider.label(), model);
            }

            // Solo vale la pena insistir con el mismo proveedor si el problema fue pasajero.
            boolean transient_ = reply.failure() == ChatReply.Failure.NETWORK || reply.failure() == ChatReply.Failure.SERVER;
            if (reply.ok() || !transient_)
            {
                return reply.withSource(provider.label(), model);
            }
        }

        return reply.withSource(provider.label(), model);
    }

    private static ChatReply realCall(AiProvider provider, String model, String systemPrompt, List<ChatTurn> history,
                                      String question) throws IOException
    {
        return provider == AiProvider.GEMINI
                ? GeminiClient.chat(provider.apiKey(), model, systemPrompt, history, question)
                : OpenAiCompatClient.chat(provider, model, systemPrompt, history, question);
    }

    private static ChatReply.Failure networkFailure(AiProvider provider, Exception e)
    {
        String text = String.valueOf(e.getMessage());
        if (text.startsWith("HTTP 401") || text.startsWith("HTTP 403") || text.contains("API key not valid"))
        {
            return ChatReply.Failure.AUTH;
        }
        if (text.startsWith("HTTP 429"))
        {
            return ChatReply.Failure.RATE_LIMIT;
        }
        return ChatReply.Failure.NETWORK;
    }

    private static String describe(AiProvider provider, Exception e)
    {
        String text = e.getMessage() == null ? e.toString() : e.getMessage();

        if (provider == AiProvider.OLLAMA && (e instanceof java.net.ConnectException || text.contains("Connection refused")))
        {
            return "no pude conectar con Ollama en " + provider.baseUrl() + ". Esta abierto?";
        }
        if (e instanceof java.net.SocketTimeoutException)
        {
            return provider.label() + " tardo demasiado en contestar.";
        }
        if (e instanceof java.net.UnknownHostException)
        {
            return "no hay conexion a internet (o no encuentro " + provider.label() + ").";
        }
        if (text.startsWith("HTTP 401") || text.startsWith("HTTP 403") || text.contains("API key not valid"))
        {
            return "la clave de " + provider.label() + " no es valida. Revisala en Mods > Rocco > Config.";
        }
        return provider.label() + ": " + text;
    }

    // Cuanto tiempo se deja descansar a un proveedor segun como fallo.
    private static void pause(AiProvider provider, ChatReply.Failure failure)
    {
        long seconds = switch (failure)
        {
            case RATE_LIMIT -> 60;
            case AUTH, MODEL_MISSING -> 300;
            case NETWORK, SERVER -> 30;
            case BAD_REQUEST -> 120;
            // Un bloqueo o un corte dependen de la frase: la proxima puede andar.
            default -> 0;
        };

        if (seconds > 0)
        {
            PAUSED_UNTIL.put(provider, System.currentTimeMillis() + seconds * 1000L);
        }
    }

    private static boolean isPaused(AiProvider provider)
    {
        Long until = PAUSED_UNTIL.get(provider);
        return until != null && until > System.currentTimeMillis();
    }

    // ---------- estado, para /rocco ia ----------

    public static AiProvider lastUsed()
    {
        return lastUsed;
    }

    public static String lastError(AiProvider provider)
    {
        return LAST_ERROR.get(provider);
    }

    public static long pausedSeconds(AiProvider provider)
    {
        Long until = PAUSED_UNTIL.get(provider);
        long left = until == null ? 0 : (until - System.currentTimeMillis()) / 1000L;
        return Math.max(0, left);
    }

    static void resetState()
    {
        PAUSED_UNTIL.clear();
        LAST_ERROR.clear();
        lastUsed = null;
    }

    private AiRouter() {}
}

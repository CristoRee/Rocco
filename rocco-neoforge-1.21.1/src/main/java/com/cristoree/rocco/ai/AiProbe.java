package com.cristoree.rocco.ai;

import com.cristoree.rocco.ai.personality.PersonalityProfile;
import com.cristoree.rocco.ai.personality.PersonalityPromptBuilder;
import com.cristoree.rocco.ai.personality.PersonalityTexts;
import com.cristoree.rocco.ai.personality.Traits;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Pruebas cortas contra la IA, sin entrar a un mundo: la de la pantalla de bienvenida ("anda esta
// clave?") y la de la personalidad ("presentate en una frase"). Corren en su propio hilo: nunca
// congelan el juego.
public final class AiProbe
{
    public record Result(boolean ok, Component message, String reply) {}

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable ->
    {
        Thread thread = new Thread(runnable, "Rocco-Prueba");
        thread.setDaemon(true);
        return thread;
    });

    // Rocco se presenta con la personalidad que esta elegida ahora (sin mundo: sin RIGHT NOW real).
    public static CompletableFuture<Result> personality()
    {
        PersonalityProfile profile = PersonalityProfile.current();
        Traits.Mode mode = PersonalityProfile.mode();
        return CompletableFuture.supplyAsync(() ->
        {
            String prompt = PersonalityPromptBuilder.build(profile, mode, new PersonalityPromptBuilder.Dynamic(
                    RoccoActions.describeForAi(), PersonalityPromptBuilder.faces(profile),
                    PersonalityTexts.trait("voice_tags.off"),
                    "- Mode: " + mode + "\n- Relationship stage: acquaintance\n- What you remember about this player: nothing yet",
                    false, "es_es"), null);
            return toResult(AiRouter.ask(prompt, List.of(), "Presentate en una frase."));
        }, EXECUTOR);
    }

    // Un proveedor solo, con la clave que tiene puesta ahora en la config (aunque no este guardada).
    public static CompletableFuture<Result> provider(AiProvider provider)
    {
        return CompletableFuture.supplyAsync(() ->
        {
            if (!provider.isConfigured())
            {
                return new Result(false, Component.translatable("rocco.test.not_configured"), "");
            }
            ChatReply reply;
            try
            {
                String model = ModelResolver.modelFor(provider);
                String prompt = "You are a test. Answer with exactly one short word.";
                reply = provider == AiProvider.GEMINI
                        ? GeminiClient.chat(provider.apiKey(), model, prompt, List.of(), "Hola")
                        : OpenAiCompatClient.chat(provider, model, prompt, List.of(), "Hola");
            }
            catch (IOException | RuntimeException e)
            {
                String text = String.valueOf(e.getMessage());
                ChatReply.Failure failure = text.startsWith("HTTP 401") || text.startsWith("HTTP 403") || text.contains("API key not valid")
                        ? ChatReply.Failure.AUTH
                        : text.startsWith("HTTP 429") ? ChatReply.Failure.RATE_LIMIT : ChatReply.Failure.NETWORK;
                reply = ChatReply.fail(failure, text, provider.label(), null);
            }
            return toResult(reply);
        }, EXECUTOR);
    }

    // Lo que paso, en palabras de todos los dias.
    static Result toResult(ChatReply reply)
    {
        if (reply.ok())
        {
            return new Result(true, Component.translatable("rocco.test.works"), reply.text() == null ? "" : reply.text());
        }
        String key = switch (reply.failure())
        {
            case AUTH -> "rocco.test.bad_key";
            case NETWORK, SERVER -> "rocco.test.offline";
            case RATE_LIMIT -> "rocco.test.rate_limit";
            case MODEL_MISSING -> "rocco.test.no_model";
            case NOT_CONFIGURED -> "rocco.test.not_configured";
            case BLOCKED -> "rocco.test.blocked";
            default -> "rocco.test.other";
        };
        return new Result(false, Component.translatable(key, String.valueOf(reply.error())), "");
    }

    private AiProbe() {}
}

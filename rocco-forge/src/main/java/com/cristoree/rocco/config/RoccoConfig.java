package com.cristoree.rocco.config;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.ai.GeminiClient;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.ArrayList;
import java.util.List;

public final class RoccoConfig
{
    public static final String ENV_GEMINI = "ROCCO_GEMINI_API_KEY";
    public static final String ENV_FISH = "ROCCO_FISH_API_KEY";
    public static final String ENV_DEEPGRAM = "ROCCO_DEEPGRAM_API_KEY";
    public static final String ENV_GROQ = "ROCCO_GROQ_API_KEY";
    public static final String ENV_OPENROUTER = "ROCCO_OPENROUTER_API_KEY";
    public static final String ENV_CUSTOM = "ROCCO_CUSTOM_API_KEY";

    // Forge nombra los archivos solo: config/rocco-common.toml y config/rocco-client.toml
    public static void register(FMLJavaModLoadingContext context)
    {
        context.registerConfig(ModConfig.Type.COMMON, RoccoCommonConfig.SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, RoccoClientConfig.SPEC);
    }

    public static String geminiApiKey()
    {
        return secret(RoccoCommonConfig.GEMINI_API_KEY, ENV_GEMINI);
    }

    public static String fishApiKey()
    {
        return secret(RoccoCommonConfig.FISH_API_KEY, ENV_FISH);
    }

    public static String deepgramApiKey()
    {
        return secret(RoccoCommonConfig.DEEPGRAM_API_KEY, ENV_DEEPGRAM);
    }

    public static String groqApiKey()
    {
        return secret(RoccoCommonConfig.GROQ_API_KEY, ENV_GROQ);
    }

    public static String openrouterApiKey()
    {
        return secret(RoccoCommonConfig.OPENROUTER_API_KEY, ENV_OPENROUTER);
    }

    public static String customApiKey()
    {
        return secret(RoccoCommonConfig.CUSTOM_API_KEY, ENV_CUSTOM);
    }

    // Rocco siempre puede contestar algo: sin IA usa sus frases propias (si no se apago).
    public static boolean chatReady()
    {
        return RoccoCommonConfig.ENABLED.get()
                && (aiConfigured() || RoccoCommonConfig.AI_OFFLINE_FALLBACK.get());
    }

    // Hay al menos un proveedor de IA con lo necesario para intentarlo.
    public static boolean aiConfigured()
    {
        return !com.cristoree.rocco.ai.AiRouter.candidates().isEmpty();
    }

    public static boolean voiceReady()
    {
        return RoccoCommonConfig.ENABLED.get()
                && RoccoCommonConfig.VOICE_ENABLED.get()
                && !fishApiKey().isEmpty()
                && !RoccoCommonConfig.FISH_VOICE_ID.get().isBlank();
    }

    public static boolean sttReady()
    {
        return RoccoCommonConfig.ENABLED.get()
                && RoccoCommonConfig.STT_ENABLED.get()
                && !deepgramApiKey().isEmpty();
    }

    // Forge valida cada opcion por separado, pero no las relaciones entre dos de ellas.
    // Sube cada vez que una migracion nueva haga falta.
    public static final int CURRENT_CONFIG_VERSION = 3;

    // Los valores por defecto viejos quedaron guardados en el .toml del jugador y cambiar el
    // default en el codigo no los toca. Se corrigen UNA vez, y solo si siguen exactamente en
    // el valor viejo: si el jugador eligio otro, se respeta. El modelo no se toca nunca (solo
    // se avisa, ver retiredModelWarning).
    public static void migrateOldDefaults()
    {
        int from = RoccoCommonConfig.CONFIG_VERSION.get();
        if (from >= CURRENT_CONFIG_VERSION)
        {
            return;
        }

        List<String> changes = new ArrayList<>();

        if (from < 2 && RoccoCommonConfig.AI_MAX_OUTPUT_TOKENS.get() == 220)
        {
            RoccoCommonConfig.AI_MAX_OUTPUT_TOKENS.set(400);
            changes.add("ai.maxOutputTokens 220 -> 400");
        }
        if (from < 2 && RoccoCommonConfig.AI_SAFETY_LEVEL.get() == RoccoCommonConfig.SafetyLevel.MEDIUM)
        {
            RoccoCommonConfig.AI_SAFETY_LEVEL.set(RoccoCommonConfig.SafetyLevel.LOW);
            changes.add("ai.safetyLevel MEDIUM -> LOW");
        }
        if (from < 2 && RoccoCommonConfig.AI_THINKING_LEVEL.get() == RoccoCommonConfig.ThinkingLevel.MINIMAL)
        {
            RoccoCommonConfig.AI_THINKING_LEVEL.set(RoccoCommonConfig.ThinkingLevel.LOW);
            changes.add("ai.thinkingLevel MINIMAL -> LOW");
        }

        // v3: LOW fue el default de la v2; con el los insultos en joda de Rocco se bloqueaban.
        if (from < 3 && RoccoCommonConfig.AI_SAFETY_LEVEL.get() == RoccoCommonConfig.SafetyLevel.LOW)
        {
            RoccoCommonConfig.AI_SAFETY_LEVEL.set(RoccoCommonConfig.SafetyLevel.OFF);
            changes.add("ai.safetyLevel LOW -> OFF");
        }

        RoccoCommonConfig.CONFIG_VERSION.set(CURRENT_CONFIG_VERSION);
        RoccoCommonConfig.SPEC.save();

        if (changes.isEmpty())
        {
            RoccoMod.LOGGER.info("[Rocco] Config al dia (version {}), no hubo que cambiar nada.", CURRENT_CONFIG_VERSION);
        }
        else
        {
            RoccoMod.LOGGER.warn("[Rocco] Actualice valores por defecto viejos de la config: {}", String.join(", ", changes));
        }
    }

    // Texto para el jugador si el modelo configurado es el retirado, o null si esta bien.
    // No se reescribe el valor del jugador: solo se avisa.
    public static String retiredModelWarning()
    {
        String model = RoccoCommonConfig.GEMINI_MODEL.get();
        if (!GeminiClient.isRetiredModel(model))
        {
            return null;
        }

        return "El modelo '" + model.trim() + "' ya no esta disponible (Google lo retiro para claves nuevas). "
                + "Mientras tanto uso otro modelo vigente. Para arreglarlo, pone 'auto' en geminiModel "
                + "(Mods > Rocco > Config, seccion ai) y Rocco elige solo el mejor disponible.";
    }

    public static void warnAboutConflicts()
    {
        String retired = retiredModelWarning();
        if (retired != null)
        {
            RoccoMod.LOGGER.warn("[Rocco] ai.geminiModel: {}", retired);
        }

        // Con Gemini 3.x el razonamiento cuenta contra maxOutputTokens: el 220 de las configs
        // viejas se quedaria sin tokens antes de escribir nada.
        String model = RoccoCommonConfig.GEMINI_MODEL.get();
        if (GeminiClient.majorVersion(model) >= 3 && RoccoCommonConfig.AI_MAX_OUTPUT_TOKENS.get() < 400)
        {
            RoccoMod.LOGGER.warn("[Rocco] ai.maxOutputTokens es {} y con {} conviene 400 o mas; "
                            + "las respuestas pueden cortarse sin texto.",
                    RoccoCommonConfig.AI_MAX_OUTPUT_TOKENS.get(), model);
        }

        if (RoccoCommonConfig.FOLLOW_STOP_DISTANCE.get() >= RoccoCommonConfig.FOLLOW_START_DISTANCE.get())
        {
            RoccoMod.LOGGER.warn("[Rocco] behavior.followStopDistance deberia ser menor que followStartDistance; Rocco va a quedar indeciso al seguirte.");
        }

        if (RoccoCommonConfig.CHAT_FULL_DETAIL_TURNS.get() > RoccoCommonConfig.CHAT_HISTORY_TURNS.get())
        {
            RoccoMod.LOGGER.warn("[Rocco] chat.fullDetailTurns es mayor que chat.historyTurns; se recortara al historial disponible.");
        }

        double teleport = RoccoCommonConfig.TELEPORT_DISTANCE.get();
        if (teleport > 0.0D && teleport <= RoccoCommonConfig.FOLLOW_START_DISTANCE.get())
        {
            RoccoMod.LOGGER.warn("[Rocco] behavior.teleportDistance es menor o igual que followStartDistance; Rocco va a teleportarse en vez de caminar.");
        }
    }

    private static String secret(ForgeConfigSpec.ConfigValue<String> value, String envVar)
    {
        String fromConfig = value.get();
        if (fromConfig != null && !fromConfig.isBlank())
        {
            return fromConfig.trim();
        }

        String fromEnv = System.getenv(envVar);
        return fromEnv == null ? "" : fromEnv.trim();
    }

    private RoccoConfig() {}
}

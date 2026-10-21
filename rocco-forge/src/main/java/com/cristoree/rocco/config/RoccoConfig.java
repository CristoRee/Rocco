package com.cristoree.rocco.config;

import com.cristoree.rocco.RoccoMod;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class RoccoConfig
{
    public static final String ENV_GEMINI = "ROCCO_GEMINI_API_KEY";
    public static final String ENV_FISH = "ROCCO_FISH_API_KEY";
    public static final String ENV_DEEPGRAM = "ROCCO_DEEPGRAM_API_KEY";

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

    public static boolean chatReady()
    {
        return RoccoCommonConfig.ENABLED.get() && !geminiApiKey().isEmpty();
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
    public static void warnAboutConflicts()
    {
        if (RoccoCommonConfig.FOLLOW_STOP_DISTANCE.get() >= RoccoCommonConfig.FOLLOW_START_DISTANCE.get())
        {
            RoccoMod.LOGGER.warn("[Rocco] behavior.followStopDistance deberia ser menor que followStartDistance; Rocco va a quedar indeciso al seguirte.");
        }

        if (RoccoCommonConfig.CHATTER_MIN_SECONDS.get() > RoccoCommonConfig.CHATTER_MAX_SECONDS.get())
        {
            RoccoMod.LOGGER.warn("[Rocco] personality.chatterMinSeconds es mayor que chatterMaxSeconds; se usara el minimo como unico intervalo.");
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

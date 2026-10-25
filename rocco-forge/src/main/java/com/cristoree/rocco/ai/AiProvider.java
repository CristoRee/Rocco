package com.cristoree.rocco.ai;

import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.config.RoccoConfig;

import java.util.Locale;

// Los proveedores que Rocco sabe usar. Gemini tiene su propio formato; el resto habla el
// formato de OpenAI, que es el estandar de la industria, asi que un solo cliente sirve para
// Groq, OpenRouter, Ollama y cualquier proveedor pago compatible.
public enum AiProvider
{
    GEMINI("Gemini", "https://aistudio.google.com/app/apikey", false),
    GROQ("Groq", "https://console.groq.com/keys", true),
    OPENROUTER("OpenRouter", "https://openrouter.ai/keys", true),
    OLLAMA("Ollama", "https://ollama.com/download", true),
    CUSTOM("Proveedor propio", "", true);

    private final String displayName;
    private final String signupUrl;
    private final boolean openAiFormat;

    AiProvider(String displayName, String signupUrl, boolean openAiFormat)
    {
        this.displayName = displayName;
        this.signupUrl = signupUrl;
        this.openAiFormat = openAiFormat;
    }

    public String id()
    {
        return name().toLowerCase(Locale.ROOT);
    }

    public String label()
    {
        if (this == CUSTOM)
        {
            String name = RoccoCommonConfig.CUSTOM_NAME.get().trim();
            return name.isEmpty() ? displayName : name;
        }
        return displayName;
    }

    public String signupUrl()
    {
        return this.signupUrl;
    }

    public boolean openAiFormat()
    {
        return this.openAiFormat;
    }

    // Gratis y sin instalar nada: los que se le ofrecen a un jugador que recien empieza.
    public boolean freeHosted()
    {
        return this == GEMINI || this == GROQ || this == OPENROUTER;
    }

    public String apiKey()
    {
        return switch (this)
        {
            case GEMINI -> RoccoConfig.geminiApiKey();
            case GROQ -> RoccoConfig.groqApiKey();
            case OPENROUTER -> RoccoConfig.openrouterApiKey();
            case CUSTOM -> RoccoConfig.customApiKey();
            case OLLAMA -> "";
        };
    }

    public String configuredModel()
    {
        String model = switch (this)
        {
            case GEMINI -> RoccoCommonConfig.GEMINI_MODEL.get();
            case GROQ -> RoccoCommonConfig.GROQ_MODEL.get();
            case OPENROUTER -> RoccoCommonConfig.OPENROUTER_MODEL.get();
            case OLLAMA -> RoccoCommonConfig.OLLAMA_MODEL.get();
            case CUSTOM -> RoccoCommonConfig.CUSTOM_MODEL.get();
        };
        return model.trim();
    }

    public String baseUrl()
    {
        String url = switch (this)
        {
            case GEMINI -> RoccoCommonConfig.GEMINI_ENDPOINT.get();
            case GROQ -> "https://api.groq.com/openai/v1";
            case OPENROUTER -> "https://openrouter.ai/api/v1";
            case OLLAMA -> RoccoCommonConfig.OLLAMA_URL.get();
            case CUSTOM -> RoccoCommonConfig.CUSTOM_URL.get();
        };

        url = url.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // Tiene lo minimo para intentarlo. Ollama y un proveedor propio local no necesitan clave.
    public boolean isConfigured()
    {
        return switch (this)
        {
            case GEMINI, GROQ, OPENROUTER -> !apiKey().isEmpty();
            case OLLAMA -> RoccoCommonConfig.OLLAMA_ENABLED.get() && !baseUrl().isEmpty();
            case CUSTOM -> !baseUrl().isEmpty();
        };
    }

    public static AiProvider byId(String id)
    {
        String wanted = id.trim().toUpperCase(Locale.ROOT);
        for (AiProvider provider : values())
        {
            if (provider.name().equals(wanted))
            {
                return provider;
            }
        }
        return null;
    }
}

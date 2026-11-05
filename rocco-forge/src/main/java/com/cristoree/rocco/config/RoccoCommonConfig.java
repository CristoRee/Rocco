package com.cristoree.rocco.config;

import com.cristoree.rocco.ai.personality.Traits;
import net.minecraftforge.common.ForgeConfigSpec;

public final class RoccoCommonConfig
{
    // Modelo vigente. Google retiro gemini-2.5-flash para claves nuevas (HTTP 404).
    public static final String DEFAULT_GEMINI_MODEL = "auto";
    public static final String RETIRED_GEMINI_MODEL = "gemini-2.5-flash";

    public static final ForgeConfigSpec SPEC;

    // general
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> COMPANION_NAME;
    public static final ForgeConfigSpec.BooleanValue LOG_REQUEST_BODIES;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING;
    public static final ForgeConfigSpec.IntValue CONFIG_VERSION;

    // ai (Gemini)
    public static final ForgeConfigSpec.EnumValue<AiProviderChoice> AI_PROVIDER;
    public static final ForgeConfigSpec.ConfigValue<String> AI_PROVIDER_ORDER;
    public static final ForgeConfigSpec.BooleanValue AI_OFFLINE_FALLBACK;
    public static final ForgeConfigSpec.BooleanValue AI_LOCAL_ORDERS;
    public static final ForgeConfigSpec.ConfigValue<String> GEMINI_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> GEMINI_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> GEMINI_ENDPOINT;
    public static final ForgeConfigSpec.DoubleValue AI_TEMPERATURE;
    public static final ForgeConfigSpec.DoubleValue AI_TOP_P;
    public static final ForgeConfigSpec.IntValue AI_TOP_K;
    public static final ForgeConfigSpec.IntValue AI_MAX_OUTPUT_TOKENS;
    public static final ForgeConfigSpec.IntValue AI_THINKING_BUDGET;
    public static final ForgeConfigSpec.EnumValue<ThinkingLevel> AI_THINKING_LEVEL;
    public static final ForgeConfigSpec.IntValue AI_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.IntValue AI_MAX_RETRIES;
    public static final ForgeConfigSpec.EnumValue<SafetyLevel> AI_SAFETY_LEVEL;

    // providers
    public static final ForgeConfigSpec.ConfigValue<String> GROQ_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> GROQ_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> OPENROUTER_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> OPENROUTER_MODEL;
    public static final ForgeConfigSpec.BooleanValue OLLAMA_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> OLLAMA_URL;
    public static final ForgeConfigSpec.ConfigValue<String> OLLAMA_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> CUSTOM_NAME;
    public static final ForgeConfigSpec.ConfigValue<String> CUSTOM_URL;
    public static final ForgeConfigSpec.ConfigValue<String> CUSTOM_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> CUSTOM_MODEL;

    // chat
    public static final ForgeConfigSpec.IntValue CHAT_HISTORY_TURNS;
    public static final ForgeConfigSpec.IntValue CHAT_FULL_DETAIL_TURNS;
    public static final ForgeConfigSpec.IntValue CHAT_MAX_RESPONSE_CHARS;
    public static final ForgeConfigSpec.DoubleValue CHAT_HEARING_RADIUS;
    public static final ForgeConfigSpec.BooleanValue CHAT_REQUIRE_NAME_MENTION;
    public static final ForgeConfigSpec.BooleanValue OWNER_ONLY;
    public static final ForgeConfigSpec.BooleanValue CHAT_SHOW_IN_CHAT;
    public static final ForgeConfigSpec.BooleanValue CHAT_READ_PLAYER_CHAT;

    // voice (Fish Audio)
    public static final ForgeConfigSpec.BooleanValue VOICE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue VOICE_RECORDED_LINES;
    public static final ForgeConfigSpec.ConfigValue<String> FISH_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> FISH_VOICE_ID;
    public static final ForgeConfigSpec.ConfigValue<String> FISH_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> FISH_ENDPOINT;
    public static final ForgeConfigSpec.IntValue VOICE_SAMPLE_RATE;
    public static final ForgeConfigSpec.EnumValue<LatencyMode> VOICE_LATENCY_MODE;
    public static final ForgeConfigSpec.DoubleValue VOICE_SPEED;
    public static final ForgeConfigSpec.DoubleValue VOICE_LOUDNESS;
    public static final ForgeConfigSpec.IntValue VOICE_MAX_CHARS;
    public static final ForgeConfigSpec.IntValue VOICE_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.BooleanValue VOICE_CACHE_ENABLED;
    public static final ForgeConfigSpec.IntValue VOICE_CACHE_SIZE_MB;
    public static final ForgeConfigSpec.ConfigValue<String> IMPRESSION_VOICES;
    public static final ForgeConfigSpec.BooleanValue IMPRESSION_VOICE_SEARCH;

    // stt (Deepgram)
    public static final ForgeConfigSpec.BooleanValue STT_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> DEEPGRAM_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> DEEPGRAM_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> DEEPGRAM_ENDPOINT;
    public static final ForgeConfigSpec.ConfigValue<String> STT_LANGUAGE;
    public static final ForgeConfigSpec.EnumValue<MicActivation> STT_ACTIVATION;
    public static final ForgeConfigSpec.DoubleValue STT_MIC_GAIN;
    public static final ForgeConfigSpec.IntValue STT_SILENCE_TIMEOUT_MS;
    public static final ForgeConfigSpec.IntValue STT_MAX_SECONDS;
    public static final ForgeConfigSpec.BooleanValue STT_PUNCTUATE;

    // behavior
    public static final ForgeConfigSpec.BooleanValue FOLLOW_ENABLED;
    public static final ForgeConfigSpec.DoubleValue FOLLOW_START_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue FOLLOW_STOP_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue FOLLOW_SPEED;
    public static final ForgeConfigSpec.DoubleValue TELEPORT_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue LEASH_BLOCKS_TELEPORT;
    public static final ForgeConfigSpec.BooleanValue LOOK_AT_PLAYER;
    public static final ForgeConfigSpec.DoubleValue LOOK_RADIUS;
    public static final ForgeConfigSpec.BooleanValue ANTI_STUCK_ENABLED;
    public static final ForgeConfigSpec.IntValue ANTI_STUCK_TICKS;
    public static final ForgeConfigSpec.DoubleValue MAX_HEALTH;
    public static final ForgeConfigSpec.BooleanValue IMMORTAL;
    public static final ForgeConfigSpec.BooleanValue INVULNERABLE;
    public static final ForgeConfigSpec.EnumValue<DeathMode> DEATH_MODE;
    public static final ForgeConfigSpec.BooleanValue PHOTOSYNTHESIS;
    public static final ForgeConfigSpec.DoubleValue REGEN_SECONDS;
    public static final ForgeConfigSpec.BooleanValue DEFEND_OWNER;
    public static final ForgeConfigSpec.BooleanValue ASSIST_OWNER;
    public static final ForgeConfigSpec.BooleanValue HUNT_HOSTILES;
    public static final ForgeConfigSpec.BooleanValue ATTACK_CREEPERS;
    public static final ForgeConfigSpec.BooleanValue OWNER_CAN_HURT_ROCCO;
    public static final ForgeConfigSpec.BooleanValue WEAPON_DURABILITY;
    public static final ForgeConfigSpec.BooleanValue DEFAULT_ATTACK_HOSTILES;
    public static final ForgeConfigSpec.BooleanValue DEFAULT_ATTACK_PEACEFUL;
    public static final ForgeConfigSpec.BooleanValue DEFAULT_ATTACK_PLAYERS;
    public static final ForgeConfigSpec.DoubleValue COLA_IDLE_CHANCE;
    public static final ForgeConfigSpec.BooleanValue MUSIC_ENABLED;
    public static final ForgeConfigSpec.BooleanValue MUSIC_DANCE_JUKEBOX;
    public static final ForgeConfigSpec.BooleanValue MUSIC_SING;
    public static final ForgeConfigSpec.IntValue COLA_IDLE_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.BooleanValue ENDER_MESSAGE_TO_ALL;
    public static final ForgeConfigSpec.BooleanValue AVOID_WATER;
    public static final ForgeConfigSpec.BooleanValue CAN_OPEN_DOORS;
    public static final ForgeConfigSpec.DoubleValue WANDER_RADIUS;
    public static final ForgeConfigSpec.BooleanValue SLEEP_WITH_PLAYER;
    public static final ForgeConfigSpec.BooleanValue IDLE_SIT_ENABLED;
    public static final ForgeConfigSpec.BooleanValue IDLE_MATE_ENABLED;
    public static final ForgeConfigSpec.IntValue IDLE_MATE_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.BooleanValue IDLE_DANCE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue CHEER_ENABLED;
    public static final ForgeConfigSpec.BooleanValue RETURN_GIFTS_ENABLED;
    public static final ForgeConfigSpec.BooleanValue SURPRISES_ENABLED;
    public static final ForgeConfigSpec.EnumValue<LidStyle> LID_STYLE;
    public static final ForgeConfigSpec.BooleanValue ACCOMPANY_FLIGHT;
    public static final ForgeConfigSpec.BooleanValue ACCOMPANY_WATER;
    public static final ForgeConfigSpec.BooleanValue ACCOMPANY_BOAT;
    public static final ForgeConfigSpec.BooleanValue ACCOMPANY_HORSE;

    // magic
    public static final ForgeConfigSpec.IntValue ENCHANT_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue ENCHANT_POWER;
    public static final ForgeConfigSpec.BooleanValue ENCHANT_TREASURE;

    // work
    public static final ForgeConfigSpec.IntValue WORK_WAIT_FOR_ITEMS_SECONDS;
    public static final ForgeConfigSpec.IntValue WORK_SEARCH_RADIUS;
    public static final ForgeConfigSpec.BooleanValue WORK_GIVE_RESULTS;
    public static final ForgeConfigSpec.BooleanValue WORK_PICK_UP_GIFTS;
    public static final ForgeConfigSpec.BooleanValue WORK_GATHER_FROM_NATURE;
    public static final ForgeConfigSpec.IntValue HOME_ANIMAL_CAP;
    public static final ForgeConfigSpec.BooleanValue HOME_CULL;
    public static final ForgeConfigSpec.BooleanValue HOME_SHEAR;
    public static final ForgeConfigSpec.BooleanValue HOME_CHEST_MATERIALS;
    public static final ForgeConfigSpec.BooleanValue HOME_KEEP_LOADED;
    public static final ForgeConfigSpec.BooleanValue WORK_PROTECT_ENCHANTED;
    public static final ForgeConfigSpec.IntValue WORK_MAX_MINING_SECONDS;

    // personality
    public static final ForgeConfigSpec.EnumValue<Traits.Mode> PERSONALITY_MODE;
    public static final ForgeConfigSpec.EnumValue<Traits.Dialect> DIALECT;
    public static final ForgeConfigSpec.ConfigValue<String> NICKNAME;
    public static final ForgeConfigSpec.BooleanValue IMPRESSIONS_ENABLED;
    public static final ForgeConfigSpec.EnumValue<Traits.Origin> P_ORIGIN;
    public static final ForgeConfigSpec.ConfigValue<String> CUSTOM_ORIGIN;
    public static final ForgeConfigSpec.EnumValue<Traits.Treatment> P_TREATMENT;
    public static final ForgeConfigSpec.EnumValue<Traits.Swearing> P_SWEARING;
    public static final ForgeConfigSpec.EnumValue<Traits.Sarcasm> P_SARCASM;
    public static final ForgeConfigSpec.EnumValue<Traits.Ego> P_EGO;
    public static final ForgeConfigSpec.EnumValue<Traits.DarkHumor> P_DARK_HUMOR;
    public static final ForgeConfigSpec.EnumValue<Traits.DarkSide> P_DARK_SIDE;
    public static final ForgeConfigSpec.EnumValue<Traits.Jealousy> P_JEALOUSY;
    public static final ForgeConfigSpec.EnumValue<Traits.Amount> P_SPONTANEOUS;
    public static final ForgeConfigSpec.EnumValue<Traits.Length> P_LENGTH;
    public static final ForgeConfigSpec.EnumValue<Traits.Questions> P_QUESTIONS;
    public static final ForgeConfigSpec.EnumValue<Traits.Amount> P_FACTS;
    public static final ForgeConfigSpec.BooleanValue P_LIFE_ADVICE;
    public static final ForgeConfigSpec.EnumValue<Traits.FourthWall> P_FOURTH_WALL;
    public static final ForgeConfigSpec.EnumValue<Traits.SpontaneousImpressions> P_SPONTANEOUS_IMPRESSIONS;
    public static final ForgeConfigSpec.BooleanValue P_FEARS;
    public static final ForgeConfigSpec.BooleanValue P_POTATO;
    public static final ForgeConfigSpec.EnumValue<Traits.TrustPace> P_TRUST_PACE;
    public static final ForgeConfigSpec.ConfigValue<String> EXTRA_NOTES;
    public static final ForgeConfigSpec.BooleanValue USE_OWN_FILE;
    public static final ForgeConfigSpec.BooleanValue CUSTOM_INITIALIZED;
    public static final ForgeConfigSpec.ConfigValue<String> PROMPT_FILE;
    public static final ForgeConfigSpec.BooleanValue CONTEXT_WORLD;
    public static final ForgeConfigSpec.BooleanValue CONTEXT_INVENTORY;
    public static final ForgeConfigSpec.BooleanValue CONTEXT_NEARBY_ENTITIES;
    public static final ForgeConfigSpec.BooleanValue CONTEXT_PLAYER_STATS;
    public static final ForgeConfigSpec.BooleanValue MOOD_ENABLED;

    // performance
    public static final ForgeConfigSpec.IntValue MAX_CONCURRENT_REQUESTS;
    public static final ForgeConfigSpec.IntValue RATE_LIMIT_PER_MINUTE;

    static
    {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Configuracion de Rocco. Las claves de API tambien pueden leerse de variables",
                  "de entorno, para no dejarlas escritas en este archivo. Si el campo queda vacio,",
                  "Rocco busca la variable de entorno indicada en cada comentario.").push("general");
        ENABLED = b
                .comment("Enciende o apaga Rocco por completo.")
                .define("enabled", true);
        COMPANION_NAME = b
                .comment("Nombre que se muestra y con el que responde.")
                .define("companionName", "Rocco");
        LOG_REQUEST_BODIES = b
                .comment("Registra el contenido enviado a las APIs. Solo para depurar:",
                         "puede llenar el log y nunca incluye las claves.")
                .define("logRequestBodies", false);
        DEBUG_LOGGING = b
                .comment("Mas detalle en el log: largo del prompt armado, respuestas bloqueadas, permisos",
                         "del lado oscuro. Tambien habilita /rocco personalidad ver.")
                .define("debugLogging", false);
        CONFIG_VERSION = b
                .comment("Uso interno: sirve para actualizar una sola vez valores por defecto viejos.",
                         "No lo cambies.")
                .defineInRange("configVersion", 0, 0, 1000);
        b.pop();

        b.comment("Cerebro de Rocco: con que IA piensa.",
                  "Sin ninguna clave Rocco igual habla y obedece ordenes basicas, con frases propias.",
                  "Con una clave gratis (Groq, Gemini u OpenRouter) charla de verdad. Con mas de una,",
                  "si una falla o se queda sin cupo, pasa sola a la siguiente.").push("ai");
        AI_PROVIDER = b
                .comment("AUTO (Automatico) usa todos los proveedores que tengan clave, en el orden de providerOrder,",
                         "y salta al siguiente si uno falla. Elegi uno solo si queres forzarlo.")
                .defineEnum("provider", AiProviderChoice.AUTO);
        AI_PROVIDER_ORDER = b
                .comment("Orden en que se prueban los proveedores con AUTO, separados por coma.",
                         "custom va primero: si pagas una IA mejor y la configuras, se usa esa.",
                         "Opciones: custom, groq, gemini, openrouter, ollama.")
                .define("providerOrder", "custom,groq,gemini,openrouter,ollama");
        AI_OFFLINE_FALLBACK = b
                .comment("Si ninguna IA responde (o no hay claves), Rocco contesta con frases propias",
                         "y entiende ordenes simples: plantar, cosechar, seguir, esperar, dormir...")
                .define("offlineFallback", true);
        AI_LOCAL_ORDERS = b
                .comment("Ahorra IA: las ordenes simples y claras (seguime, esperá, sentate, plantá papas,",
                         "guardá todo, crafteame un pico...) las resuelve Rocco solo, sin gastar un pedido",
                         "a la IA. La charla y lo que no sea una orden clara sigue yendo a la IA.",
                         "Apagado: todo pasa por la IA, incluso las ordenes cortas.")
                .define("localOrders", true);
        GEMINI_API_KEY = b
                .comment("Clave de Gemini, gratis desde aistudio.google.com/app/apikey.",
                         "Si queda vacia se usa la variable de entorno ROCCO_GEMINI_API_KEY.")
                .define("geminiApiKey", "");
        GEMINI_MODEL = b
                .comment("Modelo de Gemini. 'auto' le pregunta a Google cuales hay hoy y elige el mejor",
                         "flash disponible: asi no se rompe cuando Google retira modelos.",
                         "gemini-2.5-flash ya no esta disponible para claves nuevas (da error 404).")
                .define("geminiModel", DEFAULT_GEMINI_MODEL);
        GEMINI_ENDPOINT = b
                .comment("URL base de la API. Cambiala solo si sabes lo que haces.")
                .define("geminiEndpoint", "https://generativelanguage.googleapis.com/v1beta");
        AI_TEMPERATURE = b
                .comment("Cuanto varia la respuesta. Bajo = predecible, alto = creativo.",
                         "Solo para modelos Gemini 2.x: en los 3.x se ignora (Google pide no mandarlo).")
                .defineInRange("temperature", 0.9D, 0.0D, 2.0D);
        AI_TOP_P = b
                .comment("Filtro de probabilidad acumulada (nucleus sampling).",
                         "Solo para modelos Gemini 2.x: en los 3.x se ignora.")
                .defineInRange("topP", 0.95D, 0.0D, 1.0D);
        AI_TOP_K = b
                .comment("Cantidad de opciones que considera por palabra. 0 lo desactiva.",
                         "Solo para modelos Gemini 2.x: en los 3.x se ignora.")
                .defineInRange("topK", 40, 0, 1000);
        AI_MAX_OUTPUT_TOKENS = b
                .comment("Largo maximo de la respuesta, en tokens.",
                         "En los Gemini 3.x los tokens de razonamiento cuentan contra este tope:",
                         "con menos de 400 la respuesta puede cortarse antes de escribir nada.")
                .defineInRange("maxOutputTokens", 400, 16, 8192);
        AI_THINKING_BUDGET = b
                .comment("Tokens que puede gastar razonando antes de responder.",
                         "0 = sin razonamiento, la opcion mas rapida para charlar.",
                         "Solo para modelos Gemini 2.x: los 3.x usan thinkingLevel.")
                .defineInRange("thinkingBudget", 0, 0, 24576);
        AI_THINKING_LEVEL = b
                .comment("Cuanto razona antes de responder. Mas bajo = responde mas rapido.",
                         "No todos los modelos aceptan todos los niveles (gemini-3.8-flash rechaza MINIMAL).",
                         "Si el modelo rechaza el nivel, Rocco prueba solo el siguiente mas alto.",
                         "Solo para modelos Gemini 3.x: los 2.x usan thinkingBudget.")
                .defineEnum("thinkingLevel", ThinkingLevel.LOW);
        AI_TIMEOUT_SECONDS = b
                .comment("Tiempo maximo de espera por una respuesta.")
                .defineInRange("timeoutSeconds", 20, 1, 120);
        AI_MAX_RETRIES = b
                .comment("Reintentos cuando la peticion falla por red o limite de uso.")
                .defineInRange("maxRetries", 2, 0, 5);
        AI_SAFETY_LEVEL = b
                .comment("Filtros de contenido de Gemini.",
                         "OFF (Apagado) deja que Rocco bromee e insulte en joda, que es parte de su personalidad.",
                         "Con MEDIUM (Medio), Gemini 3.x llega a bloquear hasta saludos: el tono engreido de Rocco",
                         "cae en la categoria de acoso. Google mantiene igual sus protecciones basicas.")
                .defineEnum("safetyLevel", SafetyLevel.OFF);
        b.pop();

        b.comment("Otros proveedores de IA. Cualquier clave que pongas aca se suma a la cola de AUTO.",
                  "Los modelos en 'auto' se eligen solos segun lo que el proveedor ofrezca hoy.").push("providers");
        GROQ_API_KEY = b
                .comment("Groq: gratis y muy rapido. Clave en console.groq.com/keys.",
                         "Si queda vacia se usa la variable de entorno ROCCO_GROQ_API_KEY.")
                .define("groqApiKey", "");
        GROQ_MODEL = b
                .comment("Modelo de Groq, o 'auto'.")
                .define("groqModel", "auto");
        OPENROUTER_API_KEY = b
                .comment("OpenRouter: tiene modelos gratis y tambien pagos. Clave en openrouter.ai/keys.",
                         "Si queda vacia se usa la variable de entorno ROCCO_OPENROUTER_API_KEY.")
                .define("openrouterApiKey", "");
        OPENROUTER_MODEL = b
                .comment("'openrouter/free' elige solo entre los modelos gratis del momento.",
                         "Para uno pago, pone su id (por ejemplo openai/..., meta-llama/...).")
                .define("openrouterModel", "openrouter/free");
        OLLAMA_ENABLED = b
                .comment("Ollama corre la IA en tu propia PC: gratis, sin limites y sin internet,",
                         "pero necesita una placa de video decente. Bajalo de ollama.com.")
                .define("ollamaEnabled", false);
        OLLAMA_URL = b
                .comment("Direccion de Ollama. Por defecto corre en tu misma PC.")
                .define("ollamaUrl", "http://localhost:11434/v1");
        OLLAMA_MODEL = b
                .comment("Modelo de Ollama, o 'auto' para usar el primero que tengas instalado.")
                .define("ollamaModel", "auto");
        CUSTOM_NAME = b
                .comment("Proveedor propio, para usar una IA paga mejor. Sirve cualquiera compatible con",
                         "el formato de OpenAI: OpenAI, DeepSeek, Mistral, xAI, Together, LM Studio...",
                         "Este nombre solo se usa en los mensajes.")
                .define("customName", "");
        CUSTOM_URL = b
                .comment("URL base, terminando en /v1. Ejemplos: https://api.openai.com/v1,",
                         "https://api.deepseek.com/v1, https://api.mistral.ai/v1")
                .define("customUrl", "");
        CUSTOM_API_KEY = b
                .comment("Clave del proveedor propio.",
                         "Si queda vacia se usa la variable de entorno ROCCO_CUSTOM_API_KEY.")
                .define("customApiKey", "");
        CUSTOM_MODEL = b
                .comment("Modelo del proveedor propio (por ejemplo gpt-5-mini o deepseek-chat), o 'auto'.")
                .define("customModel", "auto");
        b.pop();

        b.comment("Como conversa Rocco.").push("chat");
        CHAT_READ_PLAYER_CHAT = b
                .comment("Rocco lee lo que escribes en el chat del juego.")
                .define("readPlayerChat", true);
        OWNER_ONLY = b
                .comment("Solo su dueño le da ordenes de trabajo (guardar, dar, mochila, pelear...) y le abre el",
                         "inventario. Los demas jugadores cerca pueden charlar con el, pero no mandarlo ni",
                         "llevarse sus cosas. Apagado, cualquier jugador cerca le da ordenes.")
                .define("ownerOnly", true);
        CHAT_REQUIRE_NAME_MENTION = b
                .comment("Responde solo si lo nombras. Si es false, contesta todo lo que escuche.")
                .define("requireNameMention", false);
        CHAT_HEARING_RADIUS = b
                .comment("Distancia en bloques desde la que escucha a un jugador.")
                .defineInRange("hearingRadius", 24.0D, 1.0D, 128.0D);
        CHAT_HISTORY_TURNS = b
                .comment("Turnos de conversacion que recuerda en la peticion.")
                .defineInRange("historyTurns", 12, 0, 64);
        CHAT_FULL_DETAIL_TURNS = b
                .comment("De esos turnos, cuantos van completos. Los mas viejos van resumidos,",
                         "lo que acelera la respuesta y abarata la peticion.")
                .defineInRange("fullDetailTurns", 2, 0, 16);
        CHAT_MAX_RESPONSE_CHARS = b
                .comment("Corta respuestas mas largas que esto, en caracteres.")
                .defineInRange("maxResponseChars", 300, 50, 2000);
        CHAT_SHOW_IN_CHAT = b
                .comment("Muestra el texto de Rocco en el chat, ademas de la voz.")
                .define("showInChat", true);
        b.pop();

        b.comment("Voz de Rocco (Fish Audio, texto a voz).").push("voice");
        VOICE_ENABLED = b
                .comment("Si es false, Rocco solo escribe y no gasta credito de voz.")
                .define("enabled", true);
        VOICE_RECORDED_LINES = b
                .comment("Sin una voz de Fish Audio configurada, sus frases de siempre (las de sin IA y",
                         "las de los trabajos) suenan con audios grabados que trae el mod, sin clave.",
                         "Si tenes Fish Audio andando, todo sale con tu voz y estas no se usan, salvo",
                         "que Fish Audio falle (sin credito, sin conexion).")
                .define("recordedLines", true);
        FISH_API_KEY = b
                .comment("Clave de Fish Audio.",
                         "Si queda vacia se usa la variable de entorno ROCCO_FISH_API_KEY.")
                .define("fishApiKey", "");
        FISH_VOICE_ID = b
                .comment("ID de la voz de Fish Audio (el 'reference id' del modelo de voz).",
                         "Es la voz con la que habla Rocco.")
                .define("voiceId", "");
        FISH_MODEL = b
                .comment("Modelo de sintesis de Fish Audio.")
                .define("fishModel", "s1");
        FISH_ENDPOINT = b
                .comment("URL base de la API de Fish Audio.")
                .define("fishEndpoint", "https://api.fish.audio");
        VOICE_SAMPLE_RATE = b
                .comment("Calidad del audio (muestras por segundo). 24000 suena bien y pesa poco;",
                         "44100 es calidad CD. Se pide en WAV, que es lo que el juego reproduce sin mods extra.",
                         "Valores que acepta Fish Audio: 8000, 16000, 24000, 32000, 44100.")
                .defineInRange("sampleRate", 24000, 8000, 44100);
        VOICE_LATENCY_MODE = b
                .comment("NORMAL (Mejor calidad): la mejor calidad. BALANCED (Equilibrado): un poco mas rapido.",
                         "LOW (Lo mas rapido): responde antes, con algo menos de calidad.")
                .defineEnum("latencyMode", LatencyMode.NORMAL);
        VOICE_SPEED = b
                .comment("Velocidad del habla. 1.0 es normal.")
                .defineInRange("speed", 1.0D, 0.5D, 2.0D);
        VOICE_LOUDNESS = b
                .comment("Volumen con el que se genera el audio.")
                .defineInRange("loudness", 1.0D, 0.1D, 2.0D);
        VOICE_MAX_CHARS = b
                .comment("No manda a sintetizar textos mas largos que esto.")
                .defineInRange("maxChars", 400, 50, 2000);
        VOICE_TIMEOUT_SECONDS = b
                .comment("Tiempo maximo de espera por el audio.")
                .defineInRange("timeoutSeconds", 20, 1, 120);
        VOICE_CACHE_ENABLED = b
                .comment("Guarda en disco las frases ya habladas y las reutiliza,",
                         "para no pagar dos veces lo mismo.")
                .define("cacheEnabled", true);
        VOICE_CACHE_SIZE_MB = b
                .comment("Tamano maximo de ese cache, en megabytes.")
                .defineInRange("cacheSizeMb", 64, 0, 2048);
        IMPRESSION_VOICES = b
                .comment("Voces para las imitaciones, separadas por ';': Nombre=id de voz de Fish Audio.",
                         "Ejemplo: Vegeta=abc123; Goku=def456. El mod no trae ninguna voz de una persona real:",
                         "sin voz para ese nombre, Rocco imita solo con el texto y su propia voz.")
                .define("impressionVoices", "");
        IMPRESSION_VOICE_SEARCH = b
                .comment("Si no hay voz en la lista, busca una publica en Fish Audio por el nombre",
                         "(y la recuerda), asi las imitaciones suenan con la voz del personaje.",
                         "Apagado, imita solo con el texto y su propia voz.")
                .define("impressionVoiceSearch", true);
        b.pop();

        b.comment("Escuchar al jugador por microfono (Deepgram, voz a texto).").push("stt");
        STT_ENABLED = b
                .comment("Permite hablarle a Rocco por microfono. La tecla (V por defecto) se cambia en",
                         "Opciones > Controles > Asignaciones de teclas, categoria Rocco.")
                .define("enabled", false);
        DEEPGRAM_API_KEY = b
                .comment("Clave de Deepgram.",
                         "Si queda vacia se usa la variable de entorno ROCCO_DEEPGRAM_API_KEY.")
                .define("deepgramApiKey", "");
        DEEPGRAM_MODEL = b
                .comment("Modelo de reconocimiento de Deepgram.")
                .define("deepgramModel", "nova-3");
        DEEPGRAM_ENDPOINT = b
                .comment("URL base de la API de Deepgram.")
                .define("deepgramEndpoint", "https://api.deepgram.com/v1/listen");
        STT_LANGUAGE = b
                .comment("Idioma del microfono, en codigo corto: es, en, multi...")
                .define("language", "es");
        STT_ACTIVATION = b
                .comment("PUSH_TO_TALK (Mantener tecla) graba mientras mantienes la tecla.",
                         "VOICE_ACTIVATION (Por voz): la tecla prende o apaga la escucha, y mientras esta prendida",
                         "graba sola cada vez que hablas y lo manda cuando te callas.")
                .defineEnum("activation", MicActivation.PUSH_TO_TALK);
        STT_MIC_GAIN = b
                .comment("Amplificacion del microfono antes de enviarlo.")
                .defineInRange("micGain", 1.0D, 0.1D, 5.0D);
        STT_SILENCE_TIMEOUT_MS = b
                .comment("Silencio que corta la grabacion, en milisegundos.",
                         "Solo aplica con VOICE_ACTIVATION (Por voz).")
                .defineInRange("silenceTimeoutMs", 1200, 200, 10000);
        STT_MAX_SECONDS = b
                .comment("Duracion maxima de una grabacion.")
                .defineInRange("maxSeconds", 30, 1, 300);
        STT_PUNCTUATE = b
                .comment("Pide a Deepgram que agregue puntuacion.")
                .define("punctuate", true);
        b.pop();

        b.comment("Como se mueve y actua Rocco en el mundo.").push("behavior");
        FOLLOW_ENABLED = b
                .comment("Sigue al jugador por defecto.")
                .define("followEnabled", true);
        FOLLOW_START_DISTANCE = b
                .comment("A partir de esta distancia empieza a seguirte.")
                .defineInRange("followStartDistance", 4.0D, 1.0D, 32.0D);
        FOLLOW_STOP_DISTANCE = b
                .comment("Se detiene cuando llega a esta distancia. Debe ser menor que la anterior.")
                .defineInRange("followStopDistance", 2.0D, 0.5D, 32.0D);
        FOLLOW_SPEED = b
                .comment("Multiplicador de velocidad al seguirte.")
                .defineInRange("followSpeed", 1.0D, 0.1D, 3.0D);
        TELEPORT_DISTANCE = b
                .comment("Si queda mas lejos que esto, aparece al lado tuyo. Tambien lo usa",
                         "cuando se traba o se queda en el agua y no puede llegar.",
                         "0 desactiva el teleport.")
                .defineInRange("teleportDistance", 24.0D, 0.0D, 256.0D);
        LEASH_BLOCKS_TELEPORT = b
                .comment("Atado con una rienda a un poste no aparece al lado tuyo (ni vuela, nada o",
                         "rema con vos). Para traerlo igual: /rocco llamar, que lo desata.")
                .define("leashBlocksTeleport", true);
        LOOK_AT_PLAYER = b
                .comment("Gira la cabeza hacia el jugador mas cercano.")
                .define("lookAtPlayer", true);
        LOOK_RADIUS = b
                .comment("Distancia maxima a la que te mira.")
                .defineInRange("lookRadius", 12.0D, 1.0D, 64.0D);
        ANTI_STUCK_ENABLED = b
                .comment("Si se traba sin poder avanzar (o se queda nadando), aparece al lado del jugador.")
                .define("antiStuckEnabled", true);
        ANTI_STUCK_TICKS = b
                .comment("Ticks sin avanzar antes de considerarlo trabado.")
                .defineInRange("antiStuckTicks", 60, 10, 600);
        MAX_HEALTH = b
                .comment("Vida maxima de Rocco. Se aplica al volver a entrar al mundo.")
                .worldRestart()
                .defineInRange("maxHealth", 20.0D, 1.0D, 200.0D);
        IMMORTAL = b
                .comment("Rocco recibe los golpes y se queja, pero nunca se muere: queda con medio",
                         "corazon, se aparta un rato detras tuyo y se cura. /kill sigue funcionando.")
                .define("immortal", false);
        INVULNERABLE = b
                .comment("Mas fuerte que lo anterior: directamente no recibe dano,",
                         "ni siquiera se queja.")
                .define("invulnerable", false);
        DEATH_MODE = b
                .comment("Que pasa si se muere. GRAVE: suelta sus cosas y reaparece enterradito al lado de",
                         "donde reapareces vos; lo desenterras con clic derecho. PERMANENT: se muere para siempre.")
                .defineEnum("deathMode", DeathMode.GRAVE);
        PHOTOSYNTHESIS = b
                .comment("Se cura solo con el tiempo (fotosintesis): a oscuras, a la mitad de velocidad.",
                         "Apagado, solo se cura comiendo, con pociones o con manzanas doradas.")
                .define("photosynthesisRegen", true);
        REGEN_SECONDS = b
                .comment("Segundos por medio corazon curado a la luz (a oscuras tarda el doble). Despues de comer",
                         "se cura al doble de rapido mientras le dura la saciedad. Espera 5 s despues de cada golpe.")
                .defineInRange("regenSeconds", 1.5D, 0.25D, 60.0D);
        DEFEND_OWNER = b
                .comment("Ataca a lo que te lastima (respetando a quien le dejaste atacar en su menu).")
                .define("defendOwner", true);
        ASSIST_OWNER = b
                .comment("Ataca a lo que vos atacas (respetando a quien le dejaste atacar en su menu).")
                .define("assistOwner", true);
        HUNT_HOSTILES = b
                .comment("Ataca a los monstruos que se te acercan a menos de 8 bloques, aunque no te hayan pegado.")
                .define("huntHostiles", false);
        ATTACK_CREEPERS = b
                .comment("Pelea cuerpo a cuerpo con creepers. Apagado, solo les dispara si tiene arco o ballesta.")
                .define("attackCreepers", false);
        OWNER_CAN_HURT_ROCCO = b
                .comment("Tus golpes le hacen dano, como a cualquier mob. Apagado, tus golpes no le hacen nada",
                         "(para no lastimarlo sin querer en medio de una pelea).")
                .define("ownerCanHurtRocco", true);
        WEAPON_DURABILITY = b
                .comment("Sus armas y armaduras se gastan como las tuyas.")
                .define("weaponDurability", true);
        DEFAULT_ATTACK_HOSTILES = b
                .comment("A quien puede atacar un Rocco nuevo (despues se cambia en su menu): mobs hostiles.")
                .define("defaultAttackHostiles", true);
        DEFAULT_ATTACK_PEACEFUL = b
                .comment("Mobs pacificos: animales, aldeanos, golems, peces (tambien tus mascotas).")
                .define("defaultAttackPeaceful", false);
        DEFAULT_ATTACK_PLAYERS = b
                .comment("Otros jugadores (nunca a vos).")
                .define("defaultAttackPlayers", false);
        COLA_IDLE_CHANCE = b
                .comment("Chance de tomarse una Roco Cola cada vez que elige algo para hacer en su rato libre.")
                .defineInRange("colaIdleChance", 0.005D, 0.0D, 1.0D);
        COLA_IDLE_COOLDOWN_MINUTES = b
                .comment("Minutos minimos entre dos Roco Colas que se toma solo.")
                .defineInRange("colaIdleCooldownMinutes", 10, 0, 1440);
        MUSIC_ENABLED = b
                .comment("Rocco pone musica cuando le pasas un link (\"Rocco, pone esto: https://...\").",
                         "Cada jugador la escucha desde su juego; el volumen es el de \"Discos\".")
                .define("musicEnabled", true);
        MUSIC_DANCE_JUKEBOX = b
                .comment("Si suena un disco en un tocadiscos cerca, va y baila.")
                .define("musicDanceJukebox", true);
        MUSIC_SING = b
                .comment("Mientras suena la musica que puso, canta y baila.")
                .define("musicSing", true);
        ENDER_MESSAGE_TO_ALL = b
                .comment("El aviso de que gano el poder del cofre de ender les llega tambien a los que estan cerca.")
                .define("enderMessageToAll", false);
        AVOID_WATER = b
                .comment("Evita entrar al agua cuando busca ruta.")
                .define("avoidWater", false);
        CAN_OPEN_DOORS = b
                .comment("Puede abrir puertas de madera para seguirte.")
                .define("canOpenDoors", true);
        WANDER_RADIUS = b
                .comment("Mientras te sigue y vos estas quieto, pasea por su cuenta hasta esta",
                         "distancia de vos. Cuando te moves, vuelve a seguirte de cerca.")
                .defineInRange("wanderRadius", 8.0D, 2.0D, 32.0D);
        SLEEP_WITH_PLAYER = b
                .comment("Cuando te acostas a dormir, Rocco tambien se duerme, y se despierta con vos.")
                .define("sleepWithPlayer", true);
        IDLE_SIT_ENABLED = b
                .comment("Si estas un rato quieto (en tu casa, haciendo tus cosas), a veces se sienta.")
                .define("idleSitEnabled", true);
        IDLE_MATE_ENABLED = b
                .comment("Si estas un rato quieto, a veces se toma un mate solo.")
                .define("idleMateEnabled", true);
        IDLE_MATE_COOLDOWN_MINUTES = b
                .comment("Minutos minimos entre un mate y el siguiente cuando lo toma por su cuenta.",
                         "Si se lo pedis, lo toma igual.")
                .defineInRange("idleMateCooldownMinutes", 10, 1, 240);
        IDLE_DANCE_ENABLED = b
                .comment("Si estas un rato quieto, a veces se pone a bailar tarareando.")
                .define("idleDanceEnabled", true);
        CHEER_ENABLED = b
                .comment("Festeja tus logros: aplaude los chicos (un logro comun, subir de nivel,",
                         "domesticar un animal) y salta con confeti en los grandes (jefes, desafios).")
                .define("cheerEnabled", true);
        RETURN_GIFTS_ENABLED = b
                .comment("Si estuviste desconectado al menos una hora, al volver te da lo que junto",
                         "mientras no estabas: mas y mejores cosas cuanto mas tiempo pasaste afuera",
                         "(lo maximo, a la semana).")
                .define("returnGifts", true);
        SURPRISES_ENABLED = b
                .comment("Muy de vez en cuando te regala alguna cosita rara que encontro, o se le cae",
                         "su diario. Tambien, si le pedis ciertas cosas en broma, te contesta con un regalito.")
                .define("surprises", true);
        LID_STYLE = b
                .comment("Que hay adentro de su cabeza cuando abre la tapa. AUTO: en modo Chill un",
                         "jardincito con flores y mariposas, en los otros la carne y la baba de siempre.",
                         "GARDEN: siempre el jardincito. CLASSIC: siempre la carne.")
                .defineEnum("censoredLid", LidStyle.AUTO);
        ACCOMPANY_FLIGHT = b
                .comment("Cuando volas con elytra (o en creativo), vuela al lado tuyo con sus hojas.")
                .define("accompanyFlight", true);
        ACCOMPANY_WATER = b
                .comment("Cuando buceas, se pone las antiparras y bucea con vos.",
                         "En la superficie siempre flota con su patito.")
                .define("accompanyWater", true);
        ACCOMPANY_BOAT = b
                .comment("Cuando vas en bote, te acompaña remando en su propio barquito.")
                .define("accompanyBoat", true);
        ACCOMPANY_HORSE = b
                .comment("Cuando vas a caballo (burro o mula, ensillados), se sube adelante tuyo.")
                .define("accompanyHorse", true);
        b.pop();

        b.comment("El poder que le dio la Madre Naturaleza: encantar tus cosas.").push("magic");
        ENCHANT_COOLDOWN_MINUTES = b
                .comment("Minutos que tiene que descansar despues de encantar algo. 20 es un dia",
                         "entero de Minecraft. Se cuenta con el juego abierto, no con la vida real.")
                .defineInRange("enchantCooldownMinutes", 20, 0, 10080);
        ENCHANT_POWER = b
                .comment("Que tan fuerte encanta, como el nivel de una mesa de encantamientos.",
                         "30 es lo maximo de una mesa con todas las bibliotecas.")
                .defineInRange("enchantPower", 30, 1, 50);
        ENCHANT_TREASURE = b
                .comment("Puede dar encantamientos de tesoro (Reparacion, Paso helado...), que en una mesa",
                         "normal no salen: al azar pueden tocar, y a pedido se pueden elegir.")
                .define("allowTreasure", false);
        b.pop();

        b.comment("Trabajos de Rocco: craftear, cofres, horno, herramientas y huerta.").push("work");
        WORK_WAIT_FOR_ITEMS_SECONDS = b
                .comment("Cuando le faltan materiales, cuanto espera a que se los des antes de ir",
                         "a buscarlos el solo a los cofres de alrededor.")
                .defineInRange("waitForItemsSeconds", 30, 5, 600);
        WORK_SEARCH_RADIUS = b
                .comment("Hasta que distancia busca cofres, hornos y mesas de crafteo.")
                .defineInRange("searchRadius", 16, 4, 48);
        WORK_GIVE_RESULTS = b
                .comment("Lo que craftea, cocina o trae te lo da en la mano (te lo tira cerca).",
                         "Si es false, se lo queda en su inventario.")
                .define("giveResultsToPlayer", true);
        WORK_PICK_UP_GIFTS = b
                .comment("Agarra lo que le tires cerca (tecla Q). Asi se le dan cosas.",
                         "Solo levanta lo que tiro un jugador, no lo que anda suelto.")
                .define("pickUpGifts", true);
        WORK_GATHER_FROM_NATURE = b
                .comment("Si le falta madera o piedra y no hay en los cofres, puede talar un arbol",
                         "o picar piedra de alrededor por su cuenta. Nunca rompe construcciones:",
                         "solo arboles naturales y piedra, tierra, arena o minerales del terreno.")
                .define("gatherFromNature", true);
        WORK_PROTECT_ENCHANTED = b
                .comment("No termina de romper herramientas encantadas: las deja con 1 de",
                         "durabilidad para que las repares.")
                .define("protectEnchantedTools", true);
        WORK_MAX_MINING_SECONDS = b
                .comment("Si romper un bloque le llevaria mas que esto, no lo intenta.")
                .defineInRange("maxMiningSeconds", 20, 1, 120);
        b.pop();

        b.comment("Cuidar la casa: la huerta y los animales mientras vos exploras (\"quedate cuidando la casa\").").push("home");
        HOME_ANIMAL_CAP = b
                .comment("Cuantos animales de cada especie mantiene alrededor de la casa (crias incluidas).",
                         "Los hace criar hasta ese numero y no mas.")
                .defineInRange("animalsPerSpecies", 16, 2, 64);
        HOME_CULL = b
                .comment("Si de una especie hay mas adultos que eso, saca de a poco los que sobran (y guarda",
                         "la carne y el cuero). Nunca toca animales con nombre, atados, montados o domesticados.")
                .define("cullExtra", true);
        HOME_SHEAR = b
                .comment("Esquila las ovejas y guarda la lana. Si no tiene tijeras, se las hace con 2 lingotes",
                         "de hierro que tenga encima.")
                .define("shearSheep", true);
        HOME_CHEST_MATERIALS = b
                .comment("Para hacerse las tijeras puede usar el hierro de los cofres de la casa.",
                         "Apagado, solo usa el que lleva encima (o el que le des).")
                .define("useChestMaterials", false);
        HOME_KEEP_LOADED = b
                .comment("Mientras cuida la casa, esa zona queda cargada aunque te vayas lejos: la huerta",
                         "crece y los animales se mueven. Apagado, la casa se congela cuando no estas cerca.")
                .define("keepHomeLoaded", true);
        b.pop();

        b.comment("Personalidad y que le cuenta el mod a la IA sobre lo que pasa.").push("personality");
        PERSONALITY_MODE = b
                .comment("CHILL: amigable y respetuoso, aunque un poco creido. SIN_FILTRO: Rocco completo,",
                         "burlon, con malas palabras y humor negro. PERSONALIZADO: usa los rasgos de abajo.",
                         "Se aplica en la proxima respuesta, sin reiniciar.")
                .defineEnum("mode", Traits.Mode.CHILL);
        DIALECT = b
                .comment("Como habla. AUTO elige segun el idioma del juego del jugador.",
                         "Vale para los tres modos.")
                .defineEnum("dialect", Traits.Dialect.LATINO);
        NICKNAME = b
                .comment("Apodo con el que Rocco te llama a veces. Vacio = ninguno.")
                .define("nickname", "", value -> value instanceof String text && text.length() <= 40);
        IMPRESSIONS_ENABLED = b
                .comment("Rocco imita voces y personajes cuando se lo pedis (y muy de vez en cuando solo).",
                         "Si es false, se niega en broma: solo hace de Rocco.")
                .define("impressions", true);

        // Los rasgos de aca abajo solo valen en PERSONALIZADO: CHILL y SIN_FILTRO traen los suyos.
        P_ORIGIN = b
                .comment("Solo en PERSONALIZADO, como todos los rasgos que siguen.",
                         "Su historia: CHILL (lo despertaste de una pesadilla en la que era una papa),",
                         "SIN_FILTRO (la Madre Naturaleza lo mando porque solo no llegas a nada) o",
                         "PERSONALIZADO (la que escribas en customOrigin).")
                .defineEnum("origin", Traits.Origin.CHILL);
        CUSTOM_ORIGIN = b
                .comment("Tu historia para Rocco, hasta 600 letras. Solo con origin = PERSONALIZADO.")
                .define("customOrigin", "", value -> value instanceof String text && text.length() <= 600);
        P_TREATMENT = b
                .comment("Como te trata: AMABLE (nunca te falta el respeto), BURLON (te gasta como un amigo)",
                         "o DESPIADADO (te destroza, pero solo por como jugas).")
                .defineEnum("treatment", Traits.Treatment.AMABLE);
        P_SWEARING = b
                .comment("Groserias: NADA, SUAVES (rayos, tonto) o NORMALES (como entre amigos).")
                .defineEnum("swearing", Traits.Swearing.SUAVES);
        P_SARCASM = b
                .comment("Sarcasmo: SUAVE (un guiño), NORMAL o AFILADO (ironia filosa y pasivo-agresiva).")
                .defineEnum("sarcasm", Traits.Sarcasm.SUAVE);
        P_EGO = b
                .comment("Ego: SIMPATICO, ENGREIDO o INSOPORTABLE.")
                .defineEnum("ego", Traits.Ego.ENGREIDO);
        P_DARK_HUMOR = b
                .comment("Humor negro: APAGADO, SI_LO_PIDEN o LIBRE.")
                .defineEnum("darkHumor", Traits.DarkHumor.SI_LO_PIDEN);
        P_DARK_SIDE = b
                .comment("Su psicopata escondido (solo contra bichos del juego): APAGADO, RARO o A_VECES.")
                .defineEnum("darkSide", Traits.DarkSide.APAGADO);
        P_JEALOUSY = b
                .comment("Celos: APAGADO, TIERNOS o DRAMATICOS.")
                .defineEnum("jealousy", Traits.Jealousy.TIERNOS);
        P_SPONTANEOUS = b
                .comment("Cuanto habla por su cuenta cuando hay silencio: POCOS, NORMAL o MUCHOS.")
                .defineEnum("spontaneousComments", Traits.Amount.NORMAL);
        P_LENGTH = b
                .comment("Largo de sus respuestas: CORTO, NORMAL o CHARLATAN.")
                .defineEnum("replyLength", Traits.Length.NORMAL);
        P_QUESTIONS = b
                .comment("Cuanto te pregunta sobre tus planes: POCAS, NORMAL o MUCHAS.")
                .defineEnum("questions", Traits.Questions.NORMAL);
        P_FACTS = b
                .comment("Datos curiosos: POCOS, NORMAL o MUCHOS.")
                .defineEnum("facts", Traits.Amount.NORMAL);
        P_LIFE_ADVICE = b
                .comment("De vez en cuando te da consejos de la vida real (tomar agua, descansar).")
                .define("lifeAdvice", true);
        P_FOURTH_WALL = b
                .comment("Romper la cuarta pared: NUNCA o MUY_RARA.")
                .defineEnum("fourthWall", Traits.FourthWall.MUY_RARA);
        P_SPONTANEOUS_IMPRESSIONS = b
                .comment("Imitaciones por su cuenta, sin que se las pidas: NUNCA, MUY_RARAS o A_VECES.")
                .defineEnum("spontaneousImpressions", Traits.SpontaneousImpressions.MUY_RARAS);
        P_FEARS = b
                .comment("Tiene miedos y manias (endermans, fuego, el vacio).")
                .define("fears", true);
        P_POTATO = b
                .comment("Se ofende si le dicen papa.")
                .define("potato", true);
        P_TRUST_PACE = b
                .comment("Que tan rapido se hacen amigos: LENTO, NORMAL, RAPIDO o EMPEZAR_COMO_AMIGOS.")
                .defineEnum("trustPace", Traits.TrustPace.NORMAL);
        EXTRA_NOTES = b
                .comment("Notas tuyas para su personalidad, hasta 800 letras. Solo en PERSONALIZADO.",
                         "Nunca pasan por encima de sus limites.")
                .define("extraNotes", "", value -> value instanceof String text && text.length() <= 800);
        USE_OWN_FILE = b
                .comment("Solo en PERSONALIZADO: usa el archivo de promptFile en vez de los rasgos.",
                         "Las acciones, las caras y lo que pasa en el juego se le agregan igual.",
                         "/rocco personalidad reset escribe ahi la personalidad actual para que la edites.")
                .define("useOwnFile", false);
        CUSTOM_INITIALIZED = b
                .comment("Uso interno: ya se copiaron una vez los rasgos de un modo a PERSONALIZADO.")
                .define("customInitialized", false);
        PROMPT_FILE = b
                .comment("Archivo de personalidad propio, dentro de la carpeta config/rocco/.",
                         "Solo se usa con useOwnFile.")
                .define("promptFile", "personalidad.txt");
        CONTEXT_WORLD = b
                .comment("Le cuenta hora, clima, bioma y dimension.")
                .define("includeWorldContext", true);
        CONTEXT_INVENTORY = b
                .comment("Le cuenta que tienes en el inventario y en la mano.")
                .define("includeInventory", true);
        CONTEXT_NEARBY_ENTITIES = b
                .comment("Le cuenta que mobs y jugadores hay cerca.")
                .define("includeNearbyEntities", true);
        CONTEXT_PLAYER_STATS = b
                .comment("Le cuenta tu vida, hambre y nivel de experiencia.")
                .define("includePlayerStats", true);
        MOOD_ENABLED = b
                .comment("Ajusta el tono segun la situacion: de noche, en una cueva, con poca vida...")
                .define("moodEnabled", true);
        b.pop();

        b.comment("Limites para que el mod no trabe el juego ni gaste de mas.").push("performance");
        MAX_CONCURRENT_REQUESTS = b
                .comment("Peticiones a las APIs en paralelo.")
                .defineInRange("maxConcurrentRequests", 2, 1, 8);
        RATE_LIMIT_PER_MINUTE = b
                .comment("Tope de peticiones por minuto. 0 lo desactiva.")
                .defineInRange("rateLimitPerMinute", 20, 0, 600);
        b.pop();

        SPEC = b.build();
    }

    private RoccoCommonConfig() {}

    public enum SafetyLevel { OFF, LOW, MEDIUM, HIGH }

    public enum AiProviderChoice { AUTO, GEMINI, GROQ, OPENROUTER, OLLAMA, CUSTOM }

    public enum ThinkingLevel { MINIMAL, LOW, MEDIUM, HIGH }

    public enum LatencyMode { NORMAL, BALANCED, LOW }

    public enum MicActivation { PUSH_TO_TALK, VOICE_ACTIVATION }

    public enum LidStyle { AUTO, GARDEN, CLASSIC }

    public enum DeathMode { GRAVE, PERMANENT }

    // Si la tapa muestra el jardincito en vez de la carne.
    public static boolean gardenLid()
    {
        return switch (LID_STYLE.get())
        {
            case GARDEN -> true;
            case CLASSIC -> false;
            case AUTO -> PERSONALITY_MODE.get() == com.cristoree.rocco.ai.personality.Traits.Mode.CHILL;
        };
    }
}

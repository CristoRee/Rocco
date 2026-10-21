package com.cristoree.rocco.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class RoccoCommonConfig
{
    public static final ForgeConfigSpec SPEC;

    // general
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> COMPANION_NAME;
    public static final ForgeConfigSpec.EnumValue<Language> LANGUAGE;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING;
    public static final ForgeConfigSpec.BooleanValue LOG_REQUEST_BODIES;

    // ai (Gemini)
    public static final ForgeConfigSpec.ConfigValue<String> GEMINI_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> GEMINI_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> GEMINI_ENDPOINT;
    public static final ForgeConfigSpec.DoubleValue AI_TEMPERATURE;
    public static final ForgeConfigSpec.DoubleValue AI_TOP_P;
    public static final ForgeConfigSpec.IntValue AI_TOP_K;
    public static final ForgeConfigSpec.IntValue AI_MAX_OUTPUT_TOKENS;
    public static final ForgeConfigSpec.IntValue AI_THINKING_BUDGET;
    public static final ForgeConfigSpec.IntValue AI_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.IntValue AI_MAX_RETRIES;
    public static final ForgeConfigSpec.EnumValue<SafetyLevel> AI_SAFETY_LEVEL;

    // chat
    public static final ForgeConfigSpec.IntValue CHAT_HISTORY_TURNS;
    public static final ForgeConfigSpec.IntValue CHAT_FULL_DETAIL_TURNS;
    public static final ForgeConfigSpec.IntValue CHAT_MAX_RESPONSE_CHARS;
    public static final ForgeConfigSpec.DoubleValue CHAT_HEARING_RADIUS;
    public static final ForgeConfigSpec.BooleanValue CHAT_REQUIRE_NAME_MENTION;
    public static final ForgeConfigSpec.BooleanValue CHAT_SHOW_IN_CHAT;
    public static final ForgeConfigSpec.IntValue CHAT_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.BooleanValue CHAT_READ_PLAYER_CHAT;

    // voice (Fish Audio)
    public static final ForgeConfigSpec.BooleanValue VOICE_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> FISH_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> FISH_VOICE_ID;
    public static final ForgeConfigSpec.ConfigValue<String> FISH_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> FISH_ENDPOINT;
    public static final ForgeConfigSpec.EnumValue<AudioFormat> VOICE_FORMAT;
    public static final ForgeConfigSpec.EnumValue<LatencyMode> VOICE_LATENCY_MODE;
    public static final ForgeConfigSpec.DoubleValue VOICE_SPEED;
    public static final ForgeConfigSpec.DoubleValue VOICE_LOUDNESS;
    public static final ForgeConfigSpec.IntValue VOICE_MAX_CHARS;
    public static final ForgeConfigSpec.IntValue VOICE_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.BooleanValue VOICE_CACHE_ENABLED;
    public static final ForgeConfigSpec.IntValue VOICE_CACHE_SIZE_MB;

    // stt (Deepgram)
    public static final ForgeConfigSpec.BooleanValue STT_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> DEEPGRAM_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> DEEPGRAM_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> DEEPGRAM_ENDPOINT;
    public static final ForgeConfigSpec.ConfigValue<String> STT_LANGUAGE;
    public static final ForgeConfigSpec.EnumValue<MicActivation> STT_ACTIVATION;
    public static final ForgeConfigSpec.DoubleValue STT_MIC_GAIN;
    public static final ForgeConfigSpec.IntValue STT_SILENCE_TIMEOUT_MS;
    public static final ForgeConfigSpec.IntValue STT_ENDPOINTING_MS;
    public static final ForgeConfigSpec.IntValue STT_MAX_SECONDS;
    public static final ForgeConfigSpec.BooleanValue STT_PUNCTUATE;
    public static final ForgeConfigSpec.BooleanValue STT_INTERIM_RESULTS;

    // behavior
    public static final ForgeConfigSpec.BooleanValue FOLLOW_ENABLED;
    public static final ForgeConfigSpec.DoubleValue FOLLOW_START_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue FOLLOW_STOP_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue FOLLOW_SPEED;
    public static final ForgeConfigSpec.DoubleValue TELEPORT_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue LOOK_AT_PLAYER;
    public static final ForgeConfigSpec.DoubleValue LOOK_RADIUS;
    public static final ForgeConfigSpec.BooleanValue IDLE_LOOK_ENABLED;
    public static final ForgeConfigSpec.BooleanValue ANTI_STUCK_ENABLED;
    public static final ForgeConfigSpec.IntValue ANTI_STUCK_TICKS;
    public static final ForgeConfigSpec.IntValue MAX_FALL_DROP;
    public static final ForgeConfigSpec.DoubleValue MAX_HEALTH;
    public static final ForgeConfigSpec.BooleanValue INVULNERABLE;
    public static final ForgeConfigSpec.BooleanValue DEFEND_PLAYER;
    public static final ForgeConfigSpec.BooleanValue AVOID_WATER;
    public static final ForgeConfigSpec.BooleanValue CAN_OPEN_DOORS;

    // memory
    public static final ForgeConfigSpec.BooleanValue MEMORY_ENABLED;
    public static final ForgeConfigSpec.IntValue MEMORY_MAX_FACTS;
    public static final ForgeConfigSpec.BooleanValue MEMORY_PER_WORLD;
    public static final ForgeConfigSpec.IntValue MEMORY_AUTOSAVE_SECONDS;
    public static final ForgeConfigSpec.BooleanValue FAMILIARITY_ENABLED;
    public static final ForgeConfigSpec.IntValue FAMILIARITY_HOURS_PER_STAGE;

    // personality
    public static final ForgeConfigSpec.ConfigValue<String> PROMPT_FILE;
    public static final ForgeConfigSpec.BooleanValue CONTEXT_WORLD;
    public static final ForgeConfigSpec.BooleanValue CONTEXT_INVENTORY;
    public static final ForgeConfigSpec.BooleanValue CONTEXT_NEARBY_ENTITIES;
    public static final ForgeConfigSpec.BooleanValue CONTEXT_PLAYER_STATS;
    public static final ForgeConfigSpec.BooleanValue MOOD_ENABLED;
    public static final ForgeConfigSpec.BooleanValue CHATTER_ENABLED;
    public static final ForgeConfigSpec.IntValue CHATTER_MIN_SECONDS;
    public static final ForgeConfigSpec.IntValue CHATTER_MAX_SECONDS;
    public static final ForgeConfigSpec.DoubleValue CHATTER_CHANCE;
    public static final ForgeConfigSpec.BooleanValue REACT_TO_EVENTS;

    // performance
    public static final ForgeConfigSpec.IntValue MAX_CONCURRENT_REQUESTS;
    public static final ForgeConfigSpec.IntValue RATE_LIMIT_PER_MINUTE;
    public static final ForgeConfigSpec.IntValue AI_TICK_INTERVAL;

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
        LANGUAGE = b
                .comment("Idioma en el que Rocco habla y escribe.")
                .defineEnum("language", Language.SPANISH);
        DEBUG_LOGGING = b
                .comment("Escribe informacion extra en logs/latest.log. Util para diagnosticar.")
                .define("debugLogging", false);
        LOG_REQUEST_BODIES = b
                .comment("Registra el contenido enviado a las APIs. Solo para depurar:",
                         "puede llenar el log y nunca incluye las claves.")
                .define("logRequestBodies", false);
        b.pop();

        b.comment("Cerebro de texto (Google Gemini).").push("ai");
        GEMINI_API_KEY = b
                .comment("Clave de Gemini, desde Google AI Studio.",
                         "Si queda vacia se usa la variable de entorno ROCCO_GEMINI_API_KEY.")
                .define("geminiApiKey", "");
        GEMINI_MODEL = b
                .comment("Modelo a usar. Los 'flash' responden mas rapido y gastan menos.")
                .define("geminiModel", "gemini-2.5-flash");
        GEMINI_ENDPOINT = b
                .comment("URL base de la API. Cambiala solo si sabes lo que haces.")
                .define("geminiEndpoint", "https://generativelanguage.googleapis.com/v1beta");
        AI_TEMPERATURE = b
                .comment("Cuanto varia la respuesta. Bajo = predecible, alto = creativo.")
                .defineInRange("temperature", 0.9D, 0.0D, 2.0D);
        AI_TOP_P = b
                .comment("Filtro de probabilidad acumulada (nucleus sampling).")
                .defineInRange("topP", 0.95D, 0.0D, 1.0D);
        AI_TOP_K = b
                .comment("Cantidad de opciones que considera por palabra. 0 lo desactiva.")
                .defineInRange("topK", 40, 0, 1000);
        AI_MAX_OUTPUT_TOKENS = b
                .comment("Largo maximo de la respuesta, en tokens.")
                .defineInRange("maxOutputTokens", 220, 16, 8192);
        AI_THINKING_BUDGET = b
                .comment("Tokens que puede gastar razonando antes de responder.",
                         "0 = sin razonamiento, la opcion mas rapida para charlar.")
                .defineInRange("thinkingBudget", 0, 0, 24576);
        AI_TIMEOUT_SECONDS = b
                .comment("Tiempo maximo de espera por una respuesta.")
                .defineInRange("timeoutSeconds", 20, 1, 120);
        AI_MAX_RETRIES = b
                .comment("Reintentos cuando la peticion falla por red o limite de uso.")
                .defineInRange("maxRetries", 2, 0, 5);
        AI_SAFETY_LEVEL = b
                .comment("Filtros de contenido de Gemini.")
                .defineEnum("safetyLevel", SafetyLevel.MEDIUM);
        b.pop();

        b.comment("Como conversa Rocco.").push("chat");
        CHAT_READ_PLAYER_CHAT = b
                .comment("Rocco lee lo que escribes en el chat del juego.")
                .define("readPlayerChat", true);
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
        CHAT_COOLDOWN_TICKS = b
                .comment("Espera minima entre respuestas, en ticks (20 ticks = 1 segundo).")
                .defineInRange("cooldownTicks", 20, 0, 1200);
        b.pop();

        b.comment("Voz de Rocco (Fish Audio, texto a voz).").push("voice");
        VOICE_ENABLED = b
                .comment("Si es false, Rocco solo escribe y no gasta credito de voz.")
                .define("enabled", true);
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
        VOICE_FORMAT = b
                .comment("Formato del audio que pedimos.")
                .defineEnum("format", AudioFormat.MP3);
        VOICE_LATENCY_MODE = b
                .comment("BALANCED prioriza calidad, NORMAL prioriza rapidez.")
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
        b.pop();

        b.comment("Escuchar al jugador por microfono (Deepgram, voz a texto).").push("stt");
        STT_ENABLED = b
                .comment("Permite hablarle a Rocco por microfono.")
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
                .comment("PUSH_TO_TALK graba mientras mantienes la tecla.",
                         "VOICE_ACTIVATION graba sola cuando detecta que hablas.")
                .defineEnum("activation", MicActivation.PUSH_TO_TALK);
        STT_MIC_GAIN = b
                .comment("Amplificacion del microfono antes de enviarlo.")
                .defineInRange("micGain", 1.0D, 0.1D, 5.0D);
        STT_SILENCE_TIMEOUT_MS = b
                .comment("Silencio que corta la grabacion, en milisegundos.",
                         "Solo aplica con VOICE_ACTIVATION.")
                .defineInRange("silenceTimeoutMs", 1200, 200, 10000);
        STT_ENDPOINTING_MS = b
                .comment("Pausa que Deepgram interpreta como fin de frase.")
                .defineInRange("endpointingMs", 300, 10, 5000);
        STT_MAX_SECONDS = b
                .comment("Duracion maxima de una grabacion.")
                .defineInRange("maxSeconds", 30, 1, 300);
        STT_PUNCTUATE = b
                .comment("Pide a Deepgram que agregue puntuacion.")
                .define("punctuate", true);
        STT_INTERIM_RESULTS = b
                .comment("Resultados parciales mientras hablas. Da sensacion de rapidez,",
                         "pero consume mas.")
                .define("interimResults", false);
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
                .comment("Si queda mas lejos que esto, aparece al lado tuyo.",
                         "0 desactiva el teleport.")
                .defineInRange("teleportDistance", 32.0D, 0.0D, 256.0D);
        LOOK_AT_PLAYER = b
                .comment("Gira la cabeza hacia el jugador mas cercano.")
                .define("lookAtPlayer", true);
        LOOK_RADIUS = b
                .comment("Distancia maxima a la que te mira.")
                .defineInRange("lookRadius", 12.0D, 1.0D, 64.0D);
        IDLE_LOOK_ENABLED = b
                .comment("Cuando esta quieto alterna entre mirarte, mirar alrededor y aburrirse.")
                .define("idleLookEnabled", true);
        ANTI_STUCK_ENABLED = b
                .comment("Si se traba, recalcula la ruta con un salto y, si sigue trabado,",
                         "aparece al lado del jugador.")
                .define("antiStuckEnabled", true);
        ANTI_STUCK_TICKS = b
                .comment("Ticks sin avanzar antes de considerarlo trabado.")
                .defineInRange("antiStuckTicks", 60, 10, 600);
        MAX_FALL_DROP = b
                .comment("Altura maxima en bloques que acepta bajar al buscar ruta.")
                .defineInRange("maxFallDrop", 4, 1, 16);
        MAX_HEALTH = b
                .comment("Vida maxima de Rocco. Se aplica al volver a entrar al mundo.")
                .worldRestart()
                .defineInRange("maxHealth", 20.0D, 1.0D, 200.0D);
        INVULNERABLE = b
                .comment("Rocco no recibe dano de ningun tipo.")
                .define("invulnerable", false);
        DEFEND_PLAYER = b
                .comment("Ataca a lo que te este atacando.")
                .define("defendPlayer", true);
        AVOID_WATER = b
                .comment("Evita entrar al agua cuando busca ruta.")
                .define("avoidWater", false);
        CAN_OPEN_DOORS = b
                .comment("Puede abrir puertas de madera para seguirte.")
                .define("canOpenDoors", true);
        b.pop();

        b.comment("Lo que Rocco recuerda entre sesiones.").push("memory");
        MEMORY_ENABLED = b
                .comment("Guarda cosas que aprende de vos y del mundo.")
                .define("enabled", true);
        MEMORY_MAX_FACTS = b
                .comment("Cantidad maxima de datos recordados. Al llenarse borra los mas viejos.")
                .defineInRange("maxFacts", 100, 0, 1000);
        MEMORY_PER_WORLD = b
                .comment("Memoria separada por mundo. Si es false, recuerda todo en todos lados.")
                .define("perWorld", true);
        MEMORY_AUTOSAVE_SECONDS = b
                .comment("Cada cuanto guarda la memoria a disco.")
                .defineInRange("autosaveSeconds", 60, 5, 3600);
        FAMILIARITY_ENABLED = b
                .comment("Rocco va teniendo mas confianza segun el tiempo que pasan juntos,",
                         "y eso cambia como te habla.")
                .define("familiarityEnabled", true);
        FAMILIARITY_HOURS_PER_STAGE = b
                .comment("Horas de juego juntos para pasar de una etapa de confianza a la siguiente.")
                .defineInRange("hoursPerStage", 2, 1, 100);
        b.pop();

        b.comment("Personalidad y de que habla por su cuenta.").push("personality");
        PROMPT_FILE = b
                .comment("Archivo con la personalidad, dentro de la carpeta config/rocco/.",
                         "Si no existe, Rocco crea uno con su personalidad por defecto.")
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
        CHATTER_ENABLED = b
                .comment("Habla solo de vez en cuando, sin que le preguntes.")
                .define("chatterEnabled", true);
        CHATTER_MIN_SECONDS = b
                .comment("Espera minima entre comentarios espontaneos.")
                .defineInRange("chatterMinSeconds", 120, 10, 3600);
        CHATTER_MAX_SECONDS = b
                .comment("Espera maxima entre comentarios espontaneos.")
                .defineInRange("chatterMaxSeconds", 420, 10, 7200);
        CHATTER_CHANCE = b
                .comment("Probabilidad de que efectivamente diga algo cuando le toca el turno.")
                .defineInRange("chatterChance", 0.6D, 0.0D, 1.0D);
        REACT_TO_EVENTS = b
                .comment("Comenta cosas que pasan: te lastimaste, encontraste diamantes,",
                         "se hizo de noche, murio un mob.")
                .define("reactToEvents", true);
        b.pop();

        b.comment("Limites para que el mod no trabe el juego ni gaste de mas.").push("performance");
        MAX_CONCURRENT_REQUESTS = b
                .comment("Peticiones a las APIs en paralelo.")
                .defineInRange("maxConcurrentRequests", 2, 1, 8);
        RATE_LIMIT_PER_MINUTE = b
                .comment("Tope de peticiones por minuto. 0 lo desactiva.")
                .defineInRange("rateLimitPerMinute", 20, 0, 600);
        AI_TICK_INTERVAL = b
                .comment("Cada cuantos ticks revisa si tiene algo que decir o hacer.",
                         "Mas alto = menos carga.")
                .defineInRange("tickInterval", 20, 1, 200);
        b.pop();

        SPEC = b.build();
    }

    private RoccoCommonConfig() {}

    public enum Language { SPANISH, ENGLISH }

    public enum SafetyLevel { OFF, LOW, MEDIUM, HIGH }

    public enum AudioFormat { MP3, WAV, OPUS }

    public enum LatencyMode { NORMAL, BALANCED }

    public enum MicActivation { PUSH_TO_TALK, VOICE_ACTIVATION }
}

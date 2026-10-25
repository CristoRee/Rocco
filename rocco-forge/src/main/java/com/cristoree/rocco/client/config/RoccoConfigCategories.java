package com.cristoree.rocco.client.config;

import com.cristoree.rocco.config.RoccoClientConfig;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.registry.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeConfigSpec;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

// Como se ve la configuracion en el juego: por temas, con nombres en castellano. El archivo .toml
// sigue igual (las secciones y claves de siempre); esto solo decide que opcion va en que pantalla.
// Una opcion nueva en RoccoCommonConfig o RoccoClientConfig tiene que sumarse aca para verse.
public final class RoccoConfigCategories
{
    // label: el nombre que se ve, o una clave de idioma ("rocco...."). tooltip: clave de idioma con
    // la ayuda; si es null se usa el comentario del .toml. maxLength: para los textos.
    public record Option(ForgeConfigSpec spec, ForgeConfigSpec.ConfigValue<?> value, String label,
                         @org.jetbrains.annotations.Nullable String tooltip, int maxLength)
    {
        public Option(ForgeConfigSpec spec, ForgeConfigSpec.ConfigValue<?> value, String label)
        {
            this(spec, value, label, null, 512);
        }

        public ForgeConfigSpec.ValueSpec valueSpec()
        {
            return this.spec.getSpec().get(this.value.getPath());
        }

        public boolean loaded()
        {
            return this.spec.isLoaded();
        }
    }

    // title null = sin subtitulo.
    public record Group(String title, List<Option> options) {}

    public record Category(String name, String description, Supplier<ItemStack> icon, List<Group> groups) {}

    private static List<Category> all;

    public static List<Category> all()
    {
        if (all == null)
        {
            all = build();
        }
        return all;
    }

    // Busca en el nombre, el grupo, la categoria y la explicacion de cada opcion, sin importar
    // tildes ni mayusculas. Devuelve un grupo por cada grupo con coincidencias.
    public static List<Group> search(String text)
    {
        String[] words = normalize(text).split("\\s+");
        List<Group> found = new ArrayList<>();

        for (Category category : all())
        {
            for (Group group : category.groups())
            {
                List<Option> matches = new ArrayList<>();
                for (Option option : group.options())
                {
                    if (!option.loaded())
                    {
                        continue;
                    }
                    String comment = option.tooltip() != null
                            ? RoccoOptionList.text(option.tooltip())
                            : option.valueSpec().getComment();
                    String haystack = normalize(RoccoOptionList.text(option.label()) + " " + category.name() + " "
                            + (group.title() == null ? "" : RoccoOptionList.text(group.title())) + " "
                            + (comment == null ? "" : comment) + " " + String.join(" ", option.value().getPath()));
                    boolean all = true;
                    for (String word : words)
                    {
                        if (!word.isEmpty() && !haystack.contains(word))
                        {
                            all = false;
                            break;
                        }
                    }
                    if (all)
                    {
                        matches.add(option);
                    }
                }
                if (!matches.isEmpty())
                {
                    String title = group.title() == null
                            ? category.name()
                            : category.name() + ": " + RoccoOptionList.text(group.title());
                    found.add(new Group(title, matches));
                }
            }
        }
        return found;
    }

    public static String normalize(String text)
    {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static List<Category> build()
    {
        ForgeConfigSpec common = RoccoCommonConfig.SPEC;
        ForgeConfigSpec client = RoccoClientConfig.SPEC;
        List<Category> list = new ArrayList<>();

        list.add(new Category("General", "Encender a Rocco, su nombre y como se ve.",
                () -> new ItemStack(ModItems.ROCCO_SPAWN_EGG.get()), List.of(
                group(null,
                        opt(common, RoccoCommonConfig.ENABLED, "Rocco activado"),
                        opt(common, RoccoCommonConfig.COMPANION_NAME, "Nombre")),
                group("Pantalla",
                        opt(client, RoccoClientConfig.SHOW_NAMEPLATE, "Nombre sobre la cabeza"),
                        opt(client, RoccoClientConfig.NAMEPLATE_DISTANCE, "Distancia del nombre"),
                        opt(client, RoccoClientConfig.MODEL_SCALE, "Tamaño del modelo")))));

        list.add(new Category("IA y claves", "Con que IA piensa Rocco y las claves de cada proveedor.",
                () -> new ItemStack(Items.KNOWLEDGE_BOOK), List.of(
                group("Cómo elige",
                        opt(common, RoccoCommonConfig.AI_PROVIDER, "Proveedor"),
                        opt(common, RoccoCommonConfig.AI_PROVIDER_ORDER, "Orden en automático"),
                        opt(common, RoccoCommonConfig.AI_OFFLINE_FALLBACK, "Frases propias sin IA")),
                group("Groq",
                        opt(common, RoccoCommonConfig.GROQ_API_KEY, "Clave"),
                        opt(common, RoccoCommonConfig.GROQ_MODEL, "Modelo")),
                group("Gemini",
                        opt(common, RoccoCommonConfig.GEMINI_API_KEY, "Clave"),
                        opt(common, RoccoCommonConfig.GEMINI_MODEL, "Modelo")),
                group("OpenRouter",
                        opt(common, RoccoCommonConfig.OPENROUTER_API_KEY, "Clave"),
                        opt(common, RoccoCommonConfig.OPENROUTER_MODEL, "Modelo")),
                group("Ollama (IA en tu PC)",
                        opt(common, RoccoCommonConfig.OLLAMA_ENABLED, "Usar Ollama"),
                        opt(common, RoccoCommonConfig.OLLAMA_URL, "Dirección"),
                        opt(common, RoccoCommonConfig.OLLAMA_MODEL, "Modelo")),
                group("Proveedor propio",
                        opt(common, RoccoCommonConfig.CUSTOM_NAME, "Nombre"),
                        opt(common, RoccoCommonConfig.CUSTOM_URL, "Dirección"),
                        opt(common, RoccoCommonConfig.CUSTOM_API_KEY, "Clave"),
                        opt(common, RoccoCommonConfig.CUSTOM_MODEL, "Modelo")))));

        list.add(new Category("IA avanzada", "Ajustes finos de las respuestas, la conexion y los limites.",
                () -> new ItemStack(Items.COMPARATOR), List.of(
                group("Respuestas",
                        opt(common, RoccoCommonConfig.AI_TEMPERATURE, "Creatividad"),
                        opt(common, RoccoCommonConfig.AI_TOP_P, "Top P"),
                        opt(common, RoccoCommonConfig.AI_TOP_K, "Top K"),
                        opt(common, RoccoCommonConfig.AI_MAX_OUTPUT_TOKENS, "Largo máximo (tokens)"),
                        opt(common, RoccoCommonConfig.AI_THINKING_LEVEL, "Razonamiento (Gemini 3)"),
                        opt(common, RoccoCommonConfig.AI_THINKING_BUDGET, "Razonamiento (Gemini 2)"),
                        opt(common, RoccoCommonConfig.AI_SAFETY_LEVEL, "Filtros de Gemini")),
                group("Conexión",
                        opt(common, RoccoCommonConfig.AI_TIMEOUT_SECONDS, "Espera máxima (s)"),
                        opt(common, RoccoCommonConfig.AI_MAX_RETRIES, "Reintentos"),
                        opt(common, RoccoCommonConfig.GEMINI_ENDPOINT, "Dirección de Gemini")),
                group("Límites",
                        opt(common, RoccoCommonConfig.MAX_CONCURRENT_REQUESTS, "Pedidos a la vez"),
                        opt(common, RoccoCommonConfig.RATE_LIMIT_PER_MINUTE, "Pedidos por minuto"),
                        opt(common, RoccoCommonConfig.LOG_REQUEST_BODIES, "Registrar pedidos (depurar)"),
                        opt(common, RoccoCommonConfig.DEBUG_LOGGING, "rocco.config.opt.debug_logging")))));

        // La personalidad (modos y rasgos) tiene su propia pantalla: RoccoPersonalityScreen. Aca
        // quedan solo las opciones que valen en cualquier modo, asi el buscador las encuentra.
        list.add(new Category("Charla", "Como te escucha y te contesta, como habla y que le cuenta a la IA.",
                () -> new ItemStack(Items.WRITABLE_BOOK), List.of(
                group("Escuchar y contestar",
                        opt(common, RoccoCommonConfig.CHAT_READ_PLAYER_CHAT, "Lee el chat"),
                        opt(common, RoccoCommonConfig.CHAT_REQUIRE_NAME_MENTION, "Solo si lo nombrás"),
                        opt(common, RoccoCommonConfig.CHAT_HEARING_RADIUS, "Distancia para oírte"),
                        opt(common, RoccoCommonConfig.CHAT_SHOW_IN_CHAT, "Respuestas en el chat"),
                        opt(common, RoccoCommonConfig.CHAT_MAX_RESPONSE_CHARS, "Largo de respuesta")),
                group("Memoria de la charla",
                        opt(common, RoccoCommonConfig.CHAT_HISTORY_TURNS, "Turnos que recuerda"),
                        opt(common, RoccoCommonConfig.CHAT_FULL_DETAIL_TURNS, "Turnos completos")),
                group("rocco.config.group.how_he_talks",
                        opt(common, RoccoCommonConfig.DIALECT, "rocco.config.trait.dialect"),
                        opt(common, RoccoCommonConfig.NICKNAME, "rocco.config.trait.nickname"),
                        opt(common, RoccoCommonConfig.MOOD_ENABLED, "Ánimo según la situación")),
                group("Qué le cuenta a la IA",
                        opt(common, RoccoCommonConfig.CONTEXT_WORLD, "Hora, clima y bioma"),
                        opt(common, RoccoCommonConfig.CONTEXT_INVENTORY, "Tu inventario"),
                        opt(common, RoccoCommonConfig.CONTEXT_NEARBY_ENTITIES, "Mobs y jugadores cerca"),
                        opt(common, RoccoCommonConfig.CONTEXT_PLAYER_STATS, "Tu vida y hambre")))));

        list.add(new Category("Voz", "La voz de Rocco con Fish Audio y como suena en tus parlantes.",
                () -> new ItemStack(Items.NOTE_BLOCK), List.of(
                group("Fish Audio",
                        opt(common, RoccoCommonConfig.VOICE_ENABLED, "Voz activada"),
                        opt(common, RoccoCommonConfig.FISH_API_KEY, "Clave"),
                        opt(common, RoccoCommonConfig.FISH_VOICE_ID, "ID de la voz"),
                        opt(common, RoccoCommonConfig.FISH_MODEL, "Modelo")),
                group("Cómo suena",
                        opt(client, RoccoClientConfig.VOICE_VOLUME, "Volumen"),
                        opt(client, RoccoClientConfig.SPATIAL_AUDIO, "Sale desde Rocco"),
                        opt(common, RoccoCommonConfig.VOICE_SPEED, "Velocidad"),
                        opt(common, RoccoCommonConfig.VOICE_LOUDNESS, "Volumen al generar"),
                        opt(common, RoccoCommonConfig.VOICE_SAMPLE_RATE, "Calidad (Hz)"),
                        opt(common, RoccoCommonConfig.VOICE_LATENCY_MODE, "Rapidez")),
                group("rocco.config.group.impressions",
                        opt(common, RoccoCommonConfig.IMPRESSIONS_ENABLED, "rocco.config.trait.impressions"),
                        new Option(common, RoccoCommonConfig.IMPRESSION_VOICES, "rocco.config.opt.impression_voices",
                                "rocco.config.tip.impression_voices", 2000),
                        new Option(common, RoccoCommonConfig.IMPRESSION_VOICE_SEARCH, "rocco.config.opt.impression_search",
                                "rocco.config.tip.impression_search", 512)),
                group("Avanzado",
                        opt(common, RoccoCommonConfig.VOICE_MAX_CHARS, "Largo máximo (letras)"),
                        opt(common, RoccoCommonConfig.VOICE_TIMEOUT_SECONDS, "Espera máxima (s)"),
                        opt(common, RoccoCommonConfig.VOICE_CACHE_ENABLED, "Guardar frases dichas"),
                        opt(common, RoccoCommonConfig.VOICE_CACHE_SIZE_MB, "Espacio para frases (MB)"),
                        opt(common, RoccoCommonConfig.FISH_ENDPOINT, "Dirección de Fish Audio")))));

        list.add(new Category("Micrófono", "Hablarle a Rocco con Deepgram. La tecla se cambia en Controles.",
                () -> new ItemStack(Items.SCULK_SENSOR), List.of(
                group("Deepgram",
                        opt(common, RoccoCommonConfig.STT_ENABLED, "Micrófono activado"),
                        opt(common, RoccoCommonConfig.DEEPGRAM_API_KEY, "Clave"),
                        opt(common, RoccoCommonConfig.DEEPGRAM_MODEL, "Modelo"),
                        opt(common, RoccoCommonConfig.STT_LANGUAGE, "Idioma")),
                group("Grabación",
                        opt(common, RoccoCommonConfig.STT_ACTIVATION, "Modo"),
                        opt(client, RoccoClientConfig.MIC_DEVICE, "Micrófono a usar"),
                        opt(common, RoccoCommonConfig.STT_MIC_GAIN, "Amplificación"),
                        opt(common, RoccoCommonConfig.STT_SILENCE_TIMEOUT_MS, "Silencio que corta (ms)"),
                        opt(common, RoccoCommonConfig.STT_MAX_SECONDS, "Duración máxima (s)"),
                        opt(common, RoccoCommonConfig.STT_PUNCTUATE, "Agregar puntuación")),
                group("Avisos",
                        opt(client, RoccoClientConfig.SHOW_MIC_INDICATOR, "Indicador en pantalla"),
                        opt(client, RoccoClientConfig.MIC_FEEDBACK_SOUND, "Clic al grabar")),
                group("Avanzado",
                        opt(common, RoccoCommonConfig.DEEPGRAM_ENDPOINT, "Dirección de Deepgram")))));

        list.add(new Category("Movimiento", "Como te sigue, cuanto se aleja y cuando aparece al lado tuyo.",
                () -> new ItemStack(Items.LEATHER_BOOTS), List.of(
                group("Seguirte",
                        opt(common, RoccoCommonConfig.FOLLOW_ENABLED, "Te sigue"),
                        opt(common, RoccoCommonConfig.FOLLOW_START_DISTANCE, "Empieza a seguir a"),
                        opt(common, RoccoCommonConfig.FOLLOW_STOP_DISTANCE, "Frena a"),
                        opt(common, RoccoCommonConfig.FOLLOW_SPEED, "Velocidad"),
                        opt(common, RoccoCommonConfig.WANDER_RADIUS, "Radio de paseo")),
                group("Teletransporte",
                        opt(common, RoccoCommonConfig.TELEPORT_DISTANCE, "Aparece si está a más de"),
                        opt(common, RoccoCommonConfig.ANTI_STUCK_ENABLED, "Se destraba solo"),
                        opt(common, RoccoCommonConfig.ANTI_STUCK_TICKS, "Ticks para destrabarse")),
                group("Camino",
                        opt(common, RoccoCommonConfig.AVOID_WATER, "Evita el agua"),
                        opt(common, RoccoCommonConfig.CAN_OPEN_DOORS, "Abre puertas")),
                group("Mirada",
                        opt(common, RoccoCommonConfig.LOOK_AT_PLAYER, "Te mira"),
                        opt(common, RoccoCommonConfig.LOOK_RADIUS, "Distancia para mirarte")))));

        list.add(new Category("Vida y rutina", "Su salud y lo que hace solo cuando estas tranquilo.",
                () -> new ItemStack(Items.RED_BED), List.of(
                group("Vida",
                        opt(common, RoccoCommonConfig.MAX_HEALTH, "Vida máxima"),
                        opt(common, RoccoCommonConfig.IMMORTAL, "Inmortal"),
                        opt(common, RoccoCommonConfig.INVULNERABLE, "Invulnerable")),
                group("Rato libre",
                        opt(common, RoccoCommonConfig.SLEEP_WITH_PLAYER, "Duerme con vos"),
                        opt(common, RoccoCommonConfig.IDLE_SIT_ENABLED, "Se sienta solo"),
                        opt(common, RoccoCommonConfig.IDLE_MATE_ENABLED, "Toma mate solo"),
                        opt(common, RoccoCommonConfig.IDLE_MATE_COOLDOWN_MINUTES, "Minutos entre mates")))));

        list.add(new Category("Trabajo", "Craftear, cofres, horno, herramientas y huerta.",
                () -> new ItemStack(Items.CRAFTING_TABLE), List.of(
                group(null,
                        opt(common, RoccoCommonConfig.WORK_SEARCH_RADIUS, "Radio de búsqueda"),
                        opt(common, RoccoCommonConfig.WORK_WAIT_FOR_ITEMS_SECONDS, "Espera materiales (s)"),
                        opt(common, RoccoCommonConfig.WORK_GIVE_RESULTS, "Te da lo que hace"),
                        opt(common, RoccoCommonConfig.WORK_PICK_UP_GIFTS, "Agarra lo que le tirás"),
                        opt(common, RoccoCommonConfig.WORK_GATHER_FROM_NATURE, "Junta de la naturaleza"),
                        opt(common, RoccoCommonConfig.WORK_PROTECT_ENCHANTED, "Cuida lo encantado"),
                        opt(common, RoccoCommonConfig.WORK_MAX_MINING_SECONDS, "Tiempo máximo picando (s)")))));

        list.add(new Category("Magia", "El poder de encantar tus cosas.",
                () -> new ItemStack(Items.ENCHANTING_TABLE), List.of(
                group(null,
                        opt(common, RoccoCommonConfig.ENCHANT_COOLDOWN_MINUTES, "Descanso (minutos)"),
                        opt(common, RoccoCommonConfig.ENCHANT_POWER, "Poder de encantar"),
                        opt(common, RoccoCommonConfig.ENCHANT_TREASURE, "Encantamientos de tesoro")))));

        return List.copyOf(list);
    }

    private static Group group(String title, Option... options)
    {
        return new Group(title, List.of(options));
    }

    private static Option opt(ForgeConfigSpec spec, ForgeConfigSpec.ConfigValue<?> value, String label)
    {
        return new Option(spec, value, label);
    }

    private RoccoConfigCategories() {}
}

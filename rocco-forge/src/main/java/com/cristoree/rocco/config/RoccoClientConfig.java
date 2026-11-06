package com.cristoree.rocco.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class RoccoClientConfig
{
    public static final ForgeConfigSpec SPEC;

    // display
    public static final ForgeConfigSpec.BooleanValue SHOW_NAMEPLATE;
    public static final ForgeConfigSpec.DoubleValue NAMEPLATE_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue SHOW_MIC_INDICATOR;
    public static final ForgeConfigSpec.DoubleValue MODEL_SCALE;
    public static final ForgeConfigSpec.BooleanValue SHOW_ARMOR;
    public static final ForgeConfigSpec.BooleanValue SHOW_HELD_ITEMS;
    public static final ForgeConfigSpec.BooleanValue SHOW_HEALTH;

    // audio
    public static final ForgeConfigSpec.DoubleValue VOICE_VOLUME;
    public static final ForgeConfigSpec.BooleanValue SPATIAL_AUDIO;
    public static final ForgeConfigSpec.BooleanValue MUSIC_LISTEN;
    public static final ForgeConfigSpec.DoubleValue MUSIC_VOLUME;
    public static final ForgeConfigSpec.BooleanValue MUSIC_BEAT_SYNC;
    public static final ForgeConfigSpec.BooleanValue DANCE_SONG;

    // input
    public static final ForgeConfigSpec.ConfigValue<String> MIC_DEVICE;
    public static final ForgeConfigSpec.BooleanValue MIC_FEEDBACK_SOUND;

    // setup
    public static final ForgeConfigSpec.BooleanValue SETUP_COMPLETED;
    public static final ForgeConfigSpec.IntValue SETUP_VERSION;
    public static final ForgeConfigSpec.BooleanValue TUTORIAL_SEEN;

    // Sube cuando una actualizacion trae algo que hay que configurar: la pantalla de bienvenida
    // vuelve a aparecer una vez, con lo nuevo.
    public static final int CURRENT_SETUP_VERSION = 1;

    static
    {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Opciones que solo afectan a tu pantalla y tus parlantes.",
                  "En un servidor, cada jugador tiene las suyas.").push("display");
        SHOW_NAMEPLATE = b
                .comment("Muestra el nombre de Rocco sobre su cabeza.")
                .define("showNameplate", true);
        SHOW_ARMOR = b
                .comment("Dibuja la armadura que tiene puesta Rocco.")
                .define("showArmor", true);
        SHOW_HELD_ITEMS = b
                .comment("Dibuja el arma, el escudo y lo que sostiene Rocco.")
                .define("showHeldItems", true);
        SHOW_HEALTH = b
                .comment("Muestra los corazones de Rocco sobre su cabeza mientras no tenga la vida llena.")
                .define("showHealth", true);
        NAMEPLATE_DISTANCE = b
                .comment("Distancia maxima a la que se ve ese nombre.")
                .defineInRange("nameplateDistance", 16.0D, 1.0D, 64.0D);
        SHOW_MIC_INDICATOR = b
                .comment("Muestra un indicador mientras el microfono esta grabando.")
                .define("showMicIndicator", true);
        MODEL_SCALE = b
                .comment("Tamano con el que se dibuja Rocco. 1.0 es el original.")
                .defineInRange("modelScale", 1.0D, 0.2D, 3.0D);
        b.pop();

        b.comment("Volumen y mezcla de la voz.").push("audio");
        VOICE_VOLUME = b
                .comment("Volumen de la voz de Rocco (ademas del control \"Voz/Habla\" de Opciones > Musica y sonidos).")
                .defineInRange("voiceVolume", 1.0D, 0.0D, 2.0D);
        SPATIAL_AUDIO = b
                .comment("La voz suena desde donde esta Rocco, no en el centro.")
                .define("spatialAudio", true);
        b.pop();

        b.comment("La musica que pone Rocco con un link.").push("music");
        MUSIC_LISTEN = b
                .comment("Escuchar la musica que pone Rocco (tambien la que le piden otros jugadores).",
                         "Para escucharla, tu juego abre el link: el sitio de ese link ve tu conexion.")
                .define("musicListen", true);
        MUSIC_VOLUME = b
                .comment("Volumen de esa musica (ademas del control \"Discos\" de Opciones > Musica y sonidos).")
                .defineInRange("musicVolume", 0.8D, 0.0D, 1.0D);
        MUSIC_BEAT_SYNC = b
                .comment("Rocco canta y baila al ritmo de la cancion (calcula el ritmo mientras suena).")
                .define("musicBeatSync", true);
        DANCE_SONG = b
                .comment("Cuando le pedis que baile, suena una cancioncita (el volumen es el de \"Discos\").")
                .define("danceSong", true);
        b.pop();

        b.comment("Microfono. La tecla para hablarle se cambia en Opciones > Controles.").push("input");
        MIC_DEVICE = b
                .comment("Nombre del microfono a usar. Vacio = el que use Windows por defecto.")
                .define("micDevice", "");
        MIC_FEEDBACK_SOUND = b
                .comment("Suena un clic al empezar y terminar de grabar.")
                .define("micFeedbackSound", true);
        b.pop();

        b.comment("Pantalla de bienvenida (claves, voz, microfono y personalidad).").push("setup");
        SETUP_COMPLETED = b
                .comment("Ya se vio la pantalla de bienvenida. En false vuelve a aparecer en el menu principal.",
                         "Se abre cuando quieras con /rocco setup o desde Mods > Rocco > Config.")
                .define("setupCompleted", false);
        TUTORIAL_SEEN = b
                .comment("Ya se vio la guia de Rocco, que aparece sola la primera vez que nace uno.",
                         "Se abre cuando quieras con /rocco tutorial o con el boton ? de su menu.")
                .define("tutorialSeen", false);
        SETUP_VERSION = b
                .comment("Uso interno: hasta que version de la bienvenida se vio.")
                .defineInRange("setupVersion", 0, 0, 1000);
        b.pop();

        SPEC = b.build();
    }

    private RoccoClientConfig() {}
}

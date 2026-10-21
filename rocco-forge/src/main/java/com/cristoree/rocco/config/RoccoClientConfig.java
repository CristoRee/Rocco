package com.cristoree.rocco.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class RoccoClientConfig
{
    public static final ForgeConfigSpec SPEC;

    // display
    public static final ForgeConfigSpec.BooleanValue SHOW_NAMEPLATE;
    public static final ForgeConfigSpec.DoubleValue NAMEPLATE_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue SUBTITLES_ENABLED;
    public static final ForgeConfigSpec.IntValue SUBTITLE_SECONDS;
    public static final ForgeConfigSpec.EnumValue<SubtitlePosition> SUBTITLE_POSITION;
    public static final ForgeConfigSpec.BooleanValue SHOW_THINKING_INDICATOR;
    public static final ForgeConfigSpec.BooleanValue SHOW_MIC_INDICATOR;
    public static final ForgeConfigSpec.DoubleValue MODEL_SCALE;

    // audio
    public static final ForgeConfigSpec.DoubleValue VOICE_VOLUME;
    public static final ForgeConfigSpec.BooleanValue SPATIAL_AUDIO;
    public static final ForgeConfigSpec.BooleanValue DUCK_MUSIC;
    public static final ForgeConfigSpec.DoubleValue DUCK_MUSIC_AMOUNT;

    // input
    public static final ForgeConfigSpec.ConfigValue<String> MIC_DEVICE;
    public static final ForgeConfigSpec.BooleanValue MIC_FEEDBACK_SOUND;

    static
    {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Opciones que solo afectan a tu pantalla y tus parlantes.",
                  "En un servidor, cada jugador tiene las suyas.").push("display");
        SHOW_NAMEPLATE = b
                .comment("Muestra el nombre de Rocco sobre su cabeza.")
                .define("showNameplate", true);
        NAMEPLATE_DISTANCE = b
                .comment("Distancia maxima a la que se ve ese nombre.")
                .defineInRange("nameplateDistance", 16.0D, 1.0D, 64.0D);
        SUBTITLES_ENABLED = b
                .comment("Muestra en pantalla lo que Rocco esta diciendo.")
                .define("subtitlesEnabled", true);
        SUBTITLE_SECONDS = b
                .comment("Cuanto queda el subtitulo en pantalla.")
                .defineInRange("subtitleSeconds", 6, 1, 60);
        SUBTITLE_POSITION = b
                .comment("Donde se dibuja el subtitulo.")
                .defineEnum("subtitlePosition", SubtitlePosition.BOTTOM);
        SHOW_THINKING_INDICATOR = b
                .comment("Avisa con una senal cuando Rocco esta pensando la respuesta.")
                .define("showThinkingIndicator", true);
        SHOW_MIC_INDICATOR = b
                .comment("Muestra un indicador mientras el microfono esta grabando.")
                .define("showMicIndicator", true);
        MODEL_SCALE = b
                .comment("Tamano con el que se dibuja Rocco. 1.0 es el original.")
                .defineInRange("modelScale", 1.0D, 0.2D, 3.0D);
        b.pop();

        b.comment("Volumen y mezcla de la voz.").push("audio");
        VOICE_VOLUME = b
                .comment("Volumen de la voz de Rocco.")
                .defineInRange("voiceVolume", 1.0D, 0.0D, 2.0D);
        SPATIAL_AUDIO = b
                .comment("La voz suena desde donde esta Rocco, no en el centro.")
                .define("spatialAudio", true);
        DUCK_MUSIC = b
                .comment("Baja la musica del juego mientras Rocco habla.")
                .define("duckMusic", true);
        DUCK_MUSIC_AMOUNT = b
                .comment("Cuanto se baja esa musica. 0.3 = queda al 30 por ciento.")
                .defineInRange("duckMusicAmount", 0.3D, 0.0D, 1.0D);
        b.pop();

        b.comment("Microfono. La tecla para hablarle se cambia en Opciones > Controles.").push("input");
        MIC_DEVICE = b
                .comment("Nombre del microfono a usar. Vacio = el que use Windows por defecto.")
                .define("micDevice", "");
        MIC_FEEDBACK_SOUND = b
                .comment("Suena un clic al empezar y terminar de grabar.")
                .define("micFeedbackSound", true);
        b.pop();

        SPEC = b.build();
    }

    private RoccoClientConfig() {}

    public enum SubtitlePosition { TOP, BOTTOM, ABOVE_HOTBAR }
}

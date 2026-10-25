package com.cristoree.rocco.ai.personality;

import java.util.Locale;

// Los rasgos ajustables de la personalidad. Cada valor es un nivel con un texto propio en
// traits.txt ("### rasgo.nivel"): al modelo se le pasa ese texto, nunca un numero. El nombre de
// cada constante es lo que queda guardado en el .toml, asi que no se renombran.
public final class Traits
{
    public enum Mode { CHILL, SIN_FILTRO, PERSONALIZADO }

    public enum Origin { CHILL, SIN_FILTRO, PERSONALIZADO }

    public enum Treatment { AMABLE, BURLON, DESPIADADO }

    public enum Swearing { NADA, SUAVES, NORMALES }

    public enum Sarcasm { SUAVE, NORMAL, AFILADO }

    public enum Ego { SIMPATICO, ENGREIDO, INSOPORTABLE }

    public enum DarkHumor { APAGADO, SI_LO_PIDEN, LIBRE }

    public enum DarkSide { APAGADO, RARO, A_VECES }

    public enum Jealousy { APAGADO, TIERNOS, DRAMATICOS }

    public enum Amount { POCOS, NORMAL, MUCHOS }

    public enum Questions { POCAS, NORMAL, MUCHAS }

    public enum Length { CORTO, NORMAL, CHARLATAN }

    public enum FourthWall { NUNCA, MUY_RARA }

    public enum SpontaneousImpressions { NUNCA, MUY_RARAS, A_VECES }

    public enum TrustPace { LENTO, NORMAL, RAPIDO, EMPEZAR_COMO_AMIGOS }

    public enum Dialect { AUTO, LATINO, RIOPLATENSE, ESPANA }

    // "SI_LO_PIDEN" -> "si_lo_piden", como aparece en traits.txt.
    static String key(Enum<?> level)
    {
        return level.name().toLowerCase(Locale.ROOT);
    }

    private Traits() {}
}

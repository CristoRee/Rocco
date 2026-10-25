package com.cristoree.rocco.ai.personality;

// Que tan amigos son Rocco y el jugador. Sube con el tiempo juntos y con las charlas (un punto
// por minuto, dos por charla); el ritmo de confianza elegido multiplica lo que suma.
// Con NORMAL: conocidos a los ~20 minutos, amigos a la hora y media, mejores amigos a las 5 horas.
public enum Relationship
{
    STRANGER(0.0D, "stranger"),
    ACQUAINTANCE(20.0D, "acquaintance"),
    FRIEND(90.0D, "friend"),
    BEST_FRIEND(300.0D, "best friend");

    private final double from;
    private final String label;

    Relationship(double from, String label)
    {
        this.from = from;
        this.label = label;
    }

    public String label()
    {
        return this.label;
    }

    public boolean atLeast(Relationship other)
    {
        return ordinal() >= other.ordinal();
    }

    public static Relationship of(double trust, Traits.TrustPace pace)
    {
        double points = pace == Traits.TrustPace.EMPEZAR_COMO_AMIGOS ? Math.max(trust, FRIEND.from) : trust;
        Relationship stage = STRANGER;
        for (Relationship candidate : values())
        {
            if (points >= candidate.from)
            {
                stage = candidate;
            }
        }
        return stage;
    }

    public static double multiplier(Traits.TrustPace pace)
    {
        return switch (pace)
        {
            case LENTO -> 0.5D;
            case NORMAL, EMPEZAR_COMO_AMIGOS -> 1.0D;
            case RAPIDO -> 2.0D;
        };
    }

    // Que tan atrevido esta ahora, segun como trata al jugador y cuanto se conocen.
    public String attitude(Traits.Treatment treatment)
    {
        return switch (treatment)
        {
            case AMABLE -> this.atLeast(FRIEND)
                    ? "warm and playful: you tease them like a close friend, always kindly"
                    : "warm and friendly: light, gentle teasing only";
            case BURLON -> switch (this)
            {
                case STRANGER -> "careful: you just met, light teasing only and no insults yet";
                case ACQUAINTANCE -> "growing bolder: tease them now and then, mild jabs";
                case FRIEND -> "bold: friendly insults and roasts are fine when earned";
                case BEST_FRIEND -> "fully bold: you roast them like a lifelong friend";
            };
            case DESPIADADO -> this == STRANGER
                    ? "already sharp, but still getting to know them"
                    : "merciless about their play";
        };
    }
}

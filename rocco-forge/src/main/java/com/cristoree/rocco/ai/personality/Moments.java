package com.cristoree.rocco.ai.personality;

import net.minecraft.util.RandomSource;

// Los modelos no saben llevar la cuenta de "muy de vez en cuando", asi que la frecuencia de los
// momentos raros la decide el codigo: en cada pedido se tira un dado, y el modelo solo recibe
// permiso o no en el bloque RIGHT NOW. Las esperas se cuentan en tiempo de juego.
public final class Moments
{
    public record Permissions(boolean darkSide, boolean impression, boolean jealousy) {}

    private static final long MINUTE = 1200L;

    private long lastDarkSide = Long.MIN_VALUE / 2;
    private long lastImpression = Long.MIN_VALUE / 2;
    private long lastJealousy = Long.MIN_VALUE / 2;

    // danger: vida baja o en pelea. Ahi nunca va una imitacion espontanea.
    public Permissions roll(PersonalityProfile profile, Relationship stage, long now, boolean danger, RandomSource random)
    {
        boolean darkSide = false;
        if (profile.darkSide() != Traits.DarkSide.APAGADO && stage.atLeast(Relationship.FRIEND))
        {
            boolean often = profile.darkSide() == Traits.DarkSide.A_VECES;
            long wait = (often ? 8 : 20) * MINUTE;
            darkSide = now - this.lastDarkSide >= wait && random.nextFloat() < (often ? 0.25F : 0.12F);
        }

        boolean impression = false;
        if (profile.spontaneousImpressions() != Traits.SpontaneousImpressions.NUNCA && !danger
                && stage.atLeast(Relationship.FRIEND))
        {
            boolean often = profile.spontaneousImpressions() == Traits.SpontaneousImpressions.A_VECES;
            long wait = (often ? 10 : 25) * MINUTE;
            impression = now - this.lastImpression >= wait && random.nextFloat() < (often ? 0.2F : 0.1F);
        }

        boolean jealousy = false;
        if (profile.jealousy() == Traits.Jealousy.DRAMATICOS && stage.atLeast(Relationship.FRIEND))
        {
            jealousy = now - this.lastJealousy >= 15 * MINUTE && random.nextFloat() < 0.25F;
        }

        return new Permissions(darkSide, impression, jealousy);
    }

    // Si uso lo que se le permitio, arranca la espera hasta la proxima vez. Los celos no tienen
    // una marca propia: se toma la cara de enojo en una respuesta con permiso como el episodio.
    public void used(Permissions given, boolean psychoFace, boolean spontaneousImpression, boolean angryFace, long now)
    {
        if (given.darkSide() && psychoFace)
        {
            this.lastDarkSide = now;
        }
        if (spontaneousImpression)
        {
            this.lastImpression = now;
        }
        if (given.jealousy() && angryFace)
        {
            this.lastJealousy = now;
        }
    }
}

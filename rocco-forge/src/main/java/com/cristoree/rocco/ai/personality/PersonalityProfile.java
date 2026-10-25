package com.cristoree.rocco.ai.personality;

import com.cristoree.rocco.ai.personality.Traits.Amount;
import com.cristoree.rocco.ai.personality.Traits.DarkHumor;
import com.cristoree.rocco.ai.personality.Traits.DarkSide;
import com.cristoree.rocco.ai.personality.Traits.Ego;
import com.cristoree.rocco.ai.personality.Traits.FourthWall;
import com.cristoree.rocco.ai.personality.Traits.Jealousy;
import com.cristoree.rocco.ai.personality.Traits.Length;
import com.cristoree.rocco.ai.personality.Traits.Mode;
import com.cristoree.rocco.ai.personality.Traits.Origin;
import com.cristoree.rocco.ai.personality.Traits.Questions;
import com.cristoree.rocco.ai.personality.Traits.Sarcasm;
import com.cristoree.rocco.ai.personality.Traits.SpontaneousImpressions;
import com.cristoree.rocco.ai.personality.Traits.Swearing;
import com.cristoree.rocco.ai.personality.Traits.Treatment;
import com.cristoree.rocco.ai.personality.Traits.TrustPace;
import com.cristoree.rocco.config.RoccoCommonConfig;

// Una combinacion de rasgos. Chill y Sin filtro son dos combinaciones fijas; Personalizado lee
// cada rasgo de la config. Asi los tres modos salen del mismo armador de prompt.
public record PersonalityProfile(
        Origin origin,
        Treatment treatment,
        Swearing swearing,
        Sarcasm sarcasm,
        Ego ego,
        DarkHumor darkHumor,
        DarkSide darkSide,
        Jealousy jealousy,
        Amount spontaneous,
        Length length,
        SpontaneousImpressions spontaneousImpressions,
        Amount facts,
        Questions questions,
        boolean lifeAdvice,
        FourthWall fourthWall,
        boolean fears,
        boolean potato,
        TrustPace trustPace)
{
    // Amigable y respetuoso, pero sigue siendo un poco creido y un milagro de la naturaleza.
    public static final PersonalityProfile CHILL = new PersonalityProfile(
            Origin.CHILL, Treatment.AMABLE, Swearing.SUAVES, Sarcasm.SUAVE, Ego.ENGREIDO,
            DarkHumor.SI_LO_PIDEN, DarkSide.APAGADO, Jealousy.TIERNOS, Amount.NORMAL, Length.NORMAL,
            SpontaneousImpressions.MUY_RARAS, Amount.NORMAL, Questions.NORMAL, true, FourthWall.MUY_RARA,
            true, true, TrustPace.NORMAL);

    // Rocco completo, sin restricciones de personalidad.
    public static final PersonalityProfile SIN_FILTRO = new PersonalityProfile(
            Origin.SIN_FILTRO, Treatment.BURLON, Swearing.NORMALES, Sarcasm.AFILADO, Ego.ENGREIDO,
            DarkHumor.LIBRE, DarkSide.RARO, Jealousy.DRAMATICOS, Amount.NORMAL, Length.NORMAL,
            SpontaneousImpressions.MUY_RARAS, Amount.NORMAL, Questions.NORMAL, true, FourthWall.MUY_RARA,
            true, true, TrustPace.NORMAL);

    public static Mode mode()
    {
        return RoccoCommonConfig.PERSONALITY_MODE.get();
    }

    // La que vale ahora, segun el modo elegido.
    public static PersonalityProfile current()
    {
        return of(mode());
    }

    public static PersonalityProfile of(Mode mode)
    {
        return switch (mode)
        {
            case CHILL -> CHILL;
            case SIN_FILTRO -> SIN_FILTRO;
            case PERSONALIZADO -> fromConfig();
        };
    }

    public static PersonalityProfile fromConfig()
    {
        return new PersonalityProfile(
                RoccoCommonConfig.P_ORIGIN.get(),
                RoccoCommonConfig.P_TREATMENT.get(),
                RoccoCommonConfig.P_SWEARING.get(),
                RoccoCommonConfig.P_SARCASM.get(),
                RoccoCommonConfig.P_EGO.get(),
                RoccoCommonConfig.P_DARK_HUMOR.get(),
                RoccoCommonConfig.P_DARK_SIDE.get(),
                RoccoCommonConfig.P_JEALOUSY.get(),
                RoccoCommonConfig.P_SPONTANEOUS.get(),
                RoccoCommonConfig.P_LENGTH.get(),
                RoccoCommonConfig.P_SPONTANEOUS_IMPRESSIONS.get(),
                RoccoCommonConfig.P_FACTS.get(),
                RoccoCommonConfig.P_QUESTIONS.get(),
                RoccoCommonConfig.P_LIFE_ADVICE.get(),
                RoccoCommonConfig.P_FOURTH_WALL.get(),
                RoccoCommonConfig.P_FEARS.get(),
                RoccoCommonConfig.P_POTATO.get(),
                RoccoCommonConfig.P_TRUST_PACE.get());
    }

    // Pasa esta combinacion a los rasgos editables de Personalizado.
    public void writeToConfig()
    {
        RoccoCommonConfig.P_ORIGIN.set(this.origin);
        RoccoCommonConfig.P_TREATMENT.set(this.treatment);
        RoccoCommonConfig.P_SWEARING.set(this.swearing);
        RoccoCommonConfig.P_SARCASM.set(this.sarcasm);
        RoccoCommonConfig.P_EGO.set(this.ego);
        RoccoCommonConfig.P_DARK_HUMOR.set(this.darkHumor);
        RoccoCommonConfig.P_DARK_SIDE.set(this.darkSide);
        RoccoCommonConfig.P_JEALOUSY.set(this.jealousy);
        RoccoCommonConfig.P_SPONTANEOUS.set(this.spontaneous);
        RoccoCommonConfig.P_LENGTH.set(this.length);
        RoccoCommonConfig.P_SPONTANEOUS_IMPRESSIONS.set(this.spontaneousImpressions);
        RoccoCommonConfig.P_FACTS.set(this.facts);
        RoccoCommonConfig.P_QUESTIONS.set(this.questions);
        RoccoCommonConfig.P_LIFE_ADVICE.set(this.lifeAdvice);
        RoccoCommonConfig.P_FOURTH_WALL.set(this.fourthWall);
        RoccoCommonConfig.P_FEARS.set(this.fears);
        RoccoCommonConfig.P_POTATO.set(this.potato);
        RoccoCommonConfig.P_TRUST_PACE.set(this.trustPace);
    }

    // Sin lado psicopata la cara psycho no se ofrece, y si el modelo igual la manda se cambia.
    public boolean allowsPsycho()
    {
        return this.darkSide != DarkSide.APAGADO;
    }

    // Con trato amable van los ejemplos de Chill; con burlon o despiadado, los de Sin filtro.
    public boolean usesChillExamples()
    {
        return this.treatment == Treatment.AMABLE;
    }
}

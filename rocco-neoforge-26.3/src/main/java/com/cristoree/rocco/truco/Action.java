package com.cristoree.rocco.truco;

/** Una jugada. card = índice (0..2/3) de la carta en la mano del jugador (solo para PLAY y DISCARD). */
public record Action(Type type, int card) {
    public enum Type {
        PLAY, DISCARD,
        TRUCO, RETRUCO, VALE_CUATRO,
        ENVIDO, REAL_ENVIDO, FALTA_ENVIDO,
        FLOR, CONTRAFLOR_AL_RESTO, CON_FLOR_ME_ACHICO,
        QUIERO, NO_QUIERO,
        MAZO
    }
    public static Action of(Type t) { return new Action(t, -1); }
    public static Action play(int i) { return new Action(Type.PLAY, i); }
    public static Action discard(int i) { return new Action(Type.DISCARD, i); }

    /** texto para el botón / el chat (lo que se dice en la mesa) */
    public String label() {
        return switch (type) {
            case PLAY -> "Jugar carta"; case DISCARD -> "Descartar";
            case TRUCO -> "¡Truco!"; case RETRUCO -> "¡Quiero retruco!"; case VALE_CUATRO -> "¡Quiero vale cuatro!";
            case ENVIDO -> "¡Envido!"; case REAL_ENVIDO -> "¡Real envido!"; case FALTA_ENVIDO -> "¡Falta envido!";
            case FLOR -> "¡Flor!"; case CONTRAFLOR_AL_RESTO -> "¡Contraflor al resto!"; case CON_FLOR_ME_ACHICO -> "Con flor me achico";
            case QUIERO -> "¡Quiero!"; case NO_QUIERO -> "No quiero"; case MAZO -> "Me voy al mazo";
        };
    }
}

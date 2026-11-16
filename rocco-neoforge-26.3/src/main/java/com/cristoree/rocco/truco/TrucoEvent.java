package com.cristoree.rocco.truco;

/**
 * Lo que pasó en la mesa, en orden, para que Minecraft lo muestre (animaciones, sonidos, chat, HUD).
 * seat = quién; team = equipo; value = número (puntos, tanto, índice de baza...); card = carta si corresponde.
 */
public record TrucoEvent(Kind kind, int seat, int team, int value, Card card, Action.Type call, String text) {
    public enum Kind {
        NEW_HAND,        // seat = el que reparte (dealer), value = número de mano
        MUESTRA,         // card = la muestra
        DEALT,           // seat recibió sus cartas (las ve solo él)
        DISCARDED,       // seat (el mano, a tres) descartó una carta boca abajo
        TURN,            // le toca a seat
        CARD_PLAYED,     // seat jugó card en la baza value (0..2)
        TRICK_WON,       // baza value: la ganó team (seat = el que la ganó) ; team = -1 si fue parda
        CALL,            // seat cantó call (TRUCO, ENVIDO, FLOR...)
        ANSWER,          // seat respondió call (QUIERO / NO_QUIERO / CON_FLOR_ME_ACHICO)
        AUTO_FLOR,       // seat tenía flor y la cantó solo (es obligatoria)
        TANTO_SAID,      // seat dijo su tanto: value (o -1 = "son buenas"); call = ENVIDO o FLOR
        POINTS,          // team suma value puntos (text = motivo)
        MAZO,            // seat (su equipo) se fue al mazo
        HAND_OVER,       // la mano la ganó team (value = puntos del truco)
        GAME_OVER        // ganó team
    }
    static TrucoEvent of(Kind k, int seat, int team, int value, Card card, Action.Type call, String text) {
        return new TrucoEvent(k, seat, team, value, card, call, text);
    }
}

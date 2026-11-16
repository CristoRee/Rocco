package com.cristoree.rocco.truco;

import java.util.List;

/**
 * Jerarquía de las cartas, envido y flor.
 * Uruguayo: el palo de la muestra tiene "piezas": 2 > 4 > 5 > 11 (caballo) > 10 (sota). Si la carta que se dio vuelta
 * como muestra ES una de esas, el 12 (rey) de ese palo ocupa su lugar (vale lo mismo y manda igual).
 * Después de las piezas, las matas: 1 de espadas > 1 de bastos > 7 de espadas > 7 de oros; después 3 > 2 > 1 (oro, copa)
 * > 12 > 11 > 10 > 7 (copa, basto) > 6 > 5 > 4.  Argentino: lo mismo sin piezas.
 * Envido de las piezas: 2 = 30, 4 = 29, 5 = 28, 11 y 10 = 27 (o sea 20 + 10/9/8/7/7) más la mejor de las otras cartas.
 */
public final class Ranks {
    private Ranks() {}
    static final int[] PIEZAS = {2, 4, 5, 11, 10};
    static final int[] PIEZA_VAL = {10, 9, 8, 7, 7};

    /** índice 0..4 de pieza (0 = la más alta) o -1 si no es pieza. */
    public static int piezaIndex(Card c, Card muestra, Rules r) {
        if (!r.muestra() || muestra == null || c.suit() != muestra.suit()) return -1;
        int m = muestra.number();
        for (int i = 0; i < PIEZAS.length; i++) {
            if (c.number() == PIEZAS[i]) return i;
            if (c.number() == 12 && m == PIEZAS[i]) return i;       // el rey reemplaza a la pieza que es la muestra
        }
        return -1;
    }
    public static boolean isPieza(Card c, Card muestra, Rules r) { return piezaIndex(c, muestra, r) >= 0; }

    /** fuerza para la baza: más alto gana. */
    public static int strength(Card c, Card muestra, Rules r) {
        int p = piezaIndex(c, muestra, r);
        if (p >= 0) return 100 - p;
        if (c.number() == 1 && c.suit() == Suit.ESPADA) return 90;
        if (c.number() == 1 && c.suit() == Suit.BASTO) return 89;
        if (c.number() == 7 && c.suit() == Suit.ESPADA) return 88;
        if (c.number() == 7 && c.suit() == Suit.ORO) return 87;
        return switch (c.number()) {
            case 3 -> 80; case 2 -> 79; case 1 -> 78; case 12 -> 77; case 11 -> 76; case 10 -> 75; case 7 -> 74;
            case 6 -> 73; case 5 -> 72; default -> 71;
        };
    }
    /** valor de una carta para envido y flor (piezas: 10/9/8/7/7). */
    public static int tantoValue(Card c, Card muestra, Rules r) {
        int p = piezaIndex(c, muestra, r);
        return p >= 0 ? PIEZA_VAL[p] : c.faceValue();
    }

    public static boolean hasFlor(List<Card> h, Card muestra, Rules r) {
        if (!r.conFlor || h.size() < 3) return false;
        int np = 0;
        for (Card c : h) if (isPieza(c, muestra, r)) np++;
        if (np >= 2) return true;
        if (np == 1) {
            Card a = null, b = null;
            for (Card c : h) if (!isPieza(c, muestra, r)) { if (a == null) a = c; else b = c; }
            return a.suit() == b.suit();
        }
        return h.get(0).suit() == h.get(1).suit() && h.get(1).suit() == h.get(2).suit();
    }
    public static int florValue(List<Card> h, Card muestra, Rules r) {
        int v = 20;
        for (Card c : h) v += tantoValue(c, muestra, r);
        return v;
    }
    /** puntos de envido de la mano (3 cartas). */
    public static int envido(List<Card> h, Card muestra, Rules r) {
        int best = 0;
        int pi = -1;
        int piezas = 0;
        for (Card c : h) if (isPieza(c, muestra, r)) piezas++;
        if (piezas >= 1) {
            // con pieza: 20 + la mejor pieza + la mejor de las otras (otra pieza cuenta su valor de tanto)
            int bestP = -1, bestPi = -1;
            for (int i = 0; i < h.size(); i++) {
                Card c = h.get(i);
                if (isPieza(c, muestra, r) && tantoValue(c, muestra, r) > bestP) { bestP = tantoValue(c, muestra, r); bestPi = i; }
            }
            int other = 0;
            for (int i = 0; i < h.size(); i++) if (i != bestPi) other = Math.max(other, tantoValue(h.get(i), muestra, r) % 10);
            return 20 + bestP + other;
        }
        for (int i = 0; i < h.size(); i++) {
            best = Math.max(best, h.get(i).faceValue());
            for (int j = i + 1; j < h.size(); j++)
                if (h.get(i).suit() == h.get(j).suit()) best = Math.max(best, 20 + h.get(i).faceValue() + h.get(j).faceValue());
        }
        return best;
    }
}

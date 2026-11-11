package com.cristoree.rocco.truco;

/** Opciones de la partida (las elige el que invita en el menú). */
public final class Rules {
    public enum Variant { URUGUAYO, ARGENTINO }
    public Variant variant = Variant.URUGUAYO;   // uruguayo: con muestra y piezas
    public int targetPoints = 30;                // 15 o 30 (malas y buenas = mitad cada una)
    public boolean conFlor = true;               // en el uruguayo la flor siempre se juega
    public boolean mazoEnPrimeraDaDos = true;    // irse al mazo en primera sin envido cantado: el rival suma 1 extra
    public boolean manoSoloDeATres = true;       // 3 jugadores: el mano juega solo contra los otros dos (variante uruguaya)

    public boolean muestra() { return variant == Variant.URUGUAYO; }
    public int malas() { return targetPoints / 2; }
    public Rules copy() {
        Rules r = new Rules(); r.variant = variant; r.targetPoints = targetPoints; r.conFlor = conFlor;
        r.mazoEnPrimeraDaDos = mazoEnPrimeraDaDos; r.manoSoloDeATres = manoSoloDeATres; return r;
    }
}

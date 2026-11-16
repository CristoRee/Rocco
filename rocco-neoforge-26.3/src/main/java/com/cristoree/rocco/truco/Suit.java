package com.cristoree.rocco.truco;

/** Palos de la baraja española. */
public enum Suit {
    ESPADA("E", "espadas"), BASTO("B", "bastos"), ORO("O", "oros"), COPA("C", "copas");
    public final String code, nombre;
    Suit(String code, String nombre) { this.code = code; this.nombre = nombre; }
}

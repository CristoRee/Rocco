package com.cristoree.rocco.truco;

import java.util.ArrayList;
import java.util.List;

/** Una carta de la baraja española de 40 (sin 8 ni 9). id 0..39 = suit * 10 + index(number). */
public record Card(int number, Suit suit) {
    public static final int[] NUMBERS = {1, 2, 3, 4, 5, 6, 7, 10, 11, 12};

    public int id() { return suit.ordinal() * 10 + indexOf(number); }
    public static Card byId(int id) { return new Card(NUMBERS[id % 10], Suit.values()[id / 10]); }
    public static int indexOf(int number) {
        for (int i = 0; i < NUMBERS.length; i++) if (NUMBERS[i] == number) return i;
        throw new IllegalArgumentException("no existe el " + number + " en la baraja de 40");
    }
    /** valor para el envido / la flor sin piezas: 1..7 su número, figuras 0 */
    public int faceValue() { return number <= 7 ? number : 0; }

    public static List<Card> deck() {
        List<Card> d = new ArrayList<>(40);
        for (int i = 0; i < 40; i++) d.add(byId(i));
        return d;
    }
    @Override public String toString() { return number + suit.code; }
    public String nombre() {
        String n = switch (number) { case 1 -> "ancho"; case 10 -> "sota"; case 11 -> "caballo"; case 12 -> "rey"; default -> String.valueOf(number); };
        return (number == 1 ? "el ancho de " : number == 10 ? "la sota de " : "el " + n + " de ") + suit.nombre;
    }
}

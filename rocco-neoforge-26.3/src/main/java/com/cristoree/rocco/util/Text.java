package com.cristoree.rocco.util;

import java.text.Normalizer;
import java.util.Locale;

// Texto sin tildes ni mayusculas para comparar lo que escribe el jugador: "Araña", "arana" y
// "ARANA" son lo mismo. No depende de nada de Minecraft.
public final class Text
{
    public static String normalize(String text)
    {
        String lower = text.toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9:_ ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Text() {}
}

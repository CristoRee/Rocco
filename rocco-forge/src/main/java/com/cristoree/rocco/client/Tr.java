package com.cristoree.rocco.client;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Los textos de las pantallas del mod. La clave es el propio texto en castellano: en castellano se
// ve tal cual (no hace falta una linea por texto), y en ingles y portugues el archivo de idioma
// trae su traduccion. Un texto sin traduccion se ve en castellano, nunca como una clave rara.
public final class Tr
{
    private static final Pattern MINUTES = Pattern.compile("^en (\\d+) min$");

    private Tr() {}

    public static MutableComponent t(String spanish, Object... args)
    {
        return Component.translatable(spanish, args);
    }

    public static String s(String spanish, Object... args)
    {
        return I18n.get(spanish, args);
    }

    // Lo que manda el servidor ya armado en castellano (estado, amistad, magia): lo que es fijo se
    // traduce, y "en 3 min" tambien. Lo demas (un nombre propio, un item) queda como vino.
    public static String server(String text)
    {
        Matcher minutes = MINUTES.matcher(text);
        if (minutes.matches())
        {
            return I18n.get("en %s min", minutes.group(1));
        }
        return I18n.get(text);
    }
}

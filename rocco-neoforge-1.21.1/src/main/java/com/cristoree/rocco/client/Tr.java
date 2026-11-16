package com.cristoree.rocco.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Los textos de las pantallas del mod. La clave es el propio texto en castellano: en castellano se
// ve tal cual, y en ingles y portugues el archivo de idioma trae su traduccion. Ojo: es_es.json
// tiene que traer tambien cada texto (clave = valor), porque Minecraft llena lo que le falta a un
// idioma con en_us: sin esa linea, en castellano se veria en ingles. El build lo controla.
public final class Tr
{
    private static final Pattern MINUTES = Pattern.compile("^en (\\d+) min$");

    private Tr() {}

    // El juego esta en alguna variante de castellano.
    public static boolean spanish()
    {
        return Minecraft.getInstance().getLanguageManager().getSelected().startsWith("es");
    }

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

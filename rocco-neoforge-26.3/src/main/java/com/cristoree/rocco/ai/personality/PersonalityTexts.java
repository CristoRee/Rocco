package com.cristoree.rocco.ai.personality;

import com.cristoree.rocco.RoccoMod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

// Los textos de la personalidad, que viven dentro del jar en rocco/personality/<version>/.
// Se leen una sola vez: no cambian mientras el juego esta abierto.
public final class PersonalityTexts
{
    // Si se cambian los textos, se sube esto (y se copia la carpeta con el numero nuevo).
    public static final String VERSION = "v3";
    private static final String ROOT = "/rocco/personality/" + VERSION + "/";

    // Comentario = "#" solo o seguido de un espacio. "#accion:" y "#recordar:" no lo son.
    private static final Pattern COMMENT = Pattern.compile("^[ \\t]*#(?:[ \\t].*)?$");

    private static String core;
    private static String impressions;
    private static String examplesChill;
    private static String examplesSinFiltro;
    private static Map<String, String> traits;
    private static Map<String, String> faces;
    private static Map<String, String> actions;
    private static Map<String, String> verbs;

    public static synchronized String core()
    {
        if (core == null)
        {
            core = read("core.txt");
        }
        return core;
    }

    public static synchronized String impressions()
    {
        if (impressions == null)
        {
            impressions = read("impressions.txt");
        }
        return impressions;
    }

    public static synchronized String examples(boolean chill)
    {
        if (examplesChill == null)
        {
            examplesChill = read("examples_chill.txt");
            examplesSinFiltro = read("examples_sin_filtro.txt");
        }
        return chill ? examplesChill : examplesSinFiltro;
    }

    // "" si ese nivel no agrega nada.
    public static synchronized String trait(String key)
    {
        if (traits == null)
        {
            traits = sections(read("traits.txt"));
        }
        return traits.getOrDefault(key, "");
    }

    // id de cara -> descripcion, en el orden del archivo.
    public static synchronized Map<String, String> faces()
    {
        if (faces == null)
        {
            faces = pairs(read("faces.txt"), "=");
        }
        return faces;
    }

    public static synchronized String actionSection(String name)
    {
        loadActions();
        return actions.getOrDefault(name, "");
    }

    private static Map<String, String> spontaneous;
    private static Map<String, String> ideas;

    // La nota de un momento espontaneo, con {{minutes}}, {{idea}} y {{activity}} sin completar.
    public static synchronized String spontaneousNote()
    {
        loadSpontaneous();
        return spontaneous.getOrDefault("note", "[SPONTANEOUS MOMENT]");
    }

    // clave -> idea para un comentario espontaneo.
    public static synchronized Map<String, String> spontaneousIdeas()
    {
        loadSpontaneous();
        return ideas;
    }

    private static void loadSpontaneous()
    {
        if (spontaneous == null)
        {
            spontaneous = sections(read("spontaneous.txt"));
            ideas = pairs(spontaneous.getOrDefault("ideas", ""), "=");
        }
    }

    // verbo -> "uso | explicacion".
    public static synchronized Map<String, String> verbs()
    {
        loadActions();
        return verbs;
    }

    private static void loadActions()
    {
        if (actions == null)
        {
            actions = sections(read("actions.txt"));
            verbs = pairs(actions.getOrDefault("verbs", ""), "=");
        }
    }

    // "### nombre" abre una seccion; todo hasta la siguiente es su texto.
    static Map<String, String> sections(String text)
    {
        Map<String, String> out = new LinkedHashMap<>();
        String name = null;
        StringBuilder body = new StringBuilder();

        for (String line : text.split("\n", -1))
        {
            if (line.startsWith("### "))
            {
                if (name != null)
                {
                    out.put(name, body.toString().trim());
                }
                name = line.substring(4).trim();
                body.setLength(0);
            }
            else if (name != null)
            {
                body.append(line).append('\n');
            }
        }
        if (name != null)
        {
            out.put(name, body.toString().trim());
        }
        return out;
    }

    private static Map<String, String> pairs(String text, String separator)
    {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : text.split("\n"))
        {
            int at = line.indexOf(separator);
            if (at > 0)
            {
                out.put(line.substring(0, at).trim(), line.substring(at + separator.length()).trim());
            }
        }
        return Collections.unmodifiableMap(out);
    }

    // Sin comentarios y con saltos de linea de Unix.
    private static String read(String file)
    {
        try (InputStream in = PersonalityTexts.class.getResourceAsStream(ROOT + file))
        {
            if (in == null)
            {
                RoccoMod.LOGGER.error("[Rocco] Falta el texto de personalidad {}{} dentro del jar", ROOT, file);
                return "";
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            StringBuilder out = new StringBuilder();
            for (String line : text.split("\n", -1))
            {
                if (!COMMENT.matcher(line).matches())
                {
                    out.append(line).append('\n');
                }
            }
            return out.toString().trim();
        }
        catch (IOException e)
        {
            RoccoMod.LOGGER.error("[Rocco] No pude leer {}{}: {}", ROOT, file, e.toString());
            return "";
        }
    }

    private PersonalityTexts() {}
}

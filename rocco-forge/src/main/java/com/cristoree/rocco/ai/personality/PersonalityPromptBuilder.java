package com.cristoree.rocco.ai.personality;

import com.cristoree.rocco.ai.personality.Traits.Dialect;
import com.cristoree.rocco.ai.personality.Traits.Mode;
import com.cristoree.rocco.ai.personality.Traits.Origin;
import com.cristoree.rocco.ai.personality.Traits.SpontaneousImpressions;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.entity.RoccoExpression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Arma el prompt de Rocco por bloques: el nucleo fijo, el texto de cada rasgo segun su nivel, y
// al final los bloques que genera el codigo en cada pedido (acciones, caras, AHORA MISMO). Lo
// fijo va primero y lo que cambia al final, asi los proveedores con cache de prompt lo
// aprovechan y cada respuesta sale mas rapida y barata.
public final class PersonalityPromptBuilder
{
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");
    // Lo que queda de una linea sin su marcador: viñeta, numero de lista o nada.
    private static final Pattern ONLY_PREFIX = Pattern.compile("^\\s*(?:-|\\d+\\.)?\\s*$");
    private static final Pattern VOICE_TAG = Pattern.compile("\\s*\\[[a-zA-Z ]{2,24}]");

    // Los bloques que no son de la personalidad: los arma el codigo en cada pedido.
    public static final List<String> DYNAMIC = List.of("actions", "faces", "voice_tags", "right_now");

    public record Dynamic(String actions, String faces, String voiceTags, String rightNow, boolean fullImpressions,
                          String playerLanguage) {}

    // El prompt completo para este pedido.
    public static String build(PersonalityProfile profile, Mode mode, Dynamic dynamic, String ownFile)
    {
        Map<String, String> values = traitValues(profile, mode, dynamic.playerLanguage(), dynamic.fullImpressions(),
                !dynamic.voiceTags().startsWith("Don't"));
        values.put("actions", dynamic.actions());
        values.put("faces", dynamic.faces());
        values.put("voice_tags", dynamic.voiceTags());
        values.put("right_now", dynamic.rightNow());

        if (ownFile != null)
        {
            return withOwnFile(ownFile, values);
        }

        String text = fill(PersonalityTexts.core(), values);
        return insertExtraNotes(text, mode);
    }

    // La personalidad sin los bloques del codigo: es lo que se escribe en el archivo propio para
    // editarlo. Los marcadores de los bloques dinamicos quedan, y se completan al usarlo.
    public static String staticPart(PersonalityProfile profile, Mode mode)
    {
        Map<String, String> values = traitValues(profile, mode, "es_es", true, true);
        for (String block : DYNAMIC)
        {
            values.put(block, "{{" + block + "}}");
        }
        return insertExtraNotes(fill(PersonalityTexts.core(), values), mode);
    }

    // Archivo propio: reemplaza el nucleo y los rasgos. Los bloques del codigo se agregan
    // siempre: donde el archivo tenga su marcador, o al final si no lo tiene.
    private static String withOwnFile(String file, Map<String, String> values)
    {
        String text = fill(file, values);
        StringBuilder out = new StringBuilder(text);
        if (!file.contains("{{actions}}"))
        {
            out.append("\n\nYOUR ACTIONS (what you can really do in the world)\n").append(values.get("actions"));
        }
        if (!file.contains("{{faces}}"))
        {
            out.append("\n\nFACES AND VOICE\nStart every reply with exactly one face tag. These are your faces:\n")
                    .append(values.get("faces"));
        }
        if (!file.contains("{{voice_tags}}"))
        {
            out.append('\n').append(values.get("voice_tags"));
        }
        if (!file.contains("{{right_now}}"))
        {
            out.append("\n\nRIGHT NOW (this changes constantly; follow it)\n").append(values.get("right_now"));
        }
        return out.toString().trim();
    }

    private static Map<String, String> traitValues(PersonalityProfile p, Mode mode, String language,
                                                   boolean fullImpressions, boolean voiceTags)
    {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("origin", origin(p));
        v.put("treatment", PersonalityTexts.trait("treatment." + Traits.key(p.treatment())));
        v.put("swearing", PersonalityTexts.trait("swearing." + Traits.key(p.swearing())));
        v.put("sarcasm", PersonalityTexts.trait("sarcasm." + Traits.key(p.sarcasm())));
        v.put("ego", PersonalityTexts.trait("ego." + Traits.key(p.ego())));
        v.put("dark_humor", PersonalityTexts.trait("dark_humor." + Traits.key(p.darkHumor())));
        // RARO y A_VECES comparten texto: lo que cambia es la frecuencia que permite el codigo.
        v.put("dark_side", p.allowsPsycho() ? PersonalityTexts.trait("dark_side.raro") : "");
        v.put("jealousy", PersonalityTexts.trait("jealousy." + Traits.key(p.jealousy())));
        v.put("length", PersonalityTexts.trait("length." + Traits.key(p.length())));
        v.put("questions", PersonalityTexts.trait("questions." + Traits.key(p.questions())));
        v.put("facts", PersonalityTexts.trait("facts." + Traits.key(p.facts())));
        v.put("life_advice", p.lifeAdvice() ? PersonalityTexts.trait("life_advice.on") : "");
        v.put("fourth_wall", PersonalityTexts.trait("fourth_wall." + Traits.key(p.fourthWall())));
        v.put("potato", p.potato() ? PersonalityTexts.trait("potato.on") : "");
        v.put("fears", p.fears() ? PersonalityTexts.trait("fears.on") : "");
        v.put("dialect", PersonalityTexts.trait("dialect." + dialectKey(RoccoCommonConfig.DIALECT.get(), language)));
        v.put("nickname", nickname());
        v.put("impressions", impressions(p, fullImpressions));
        v.put("examples", examples(p, voiceTags));
        return v;
    }

    private static String origin(PersonalityProfile p)
    {
        if (p.origin() == Origin.PERSONALIZADO)
        {
            String story = RoccoCommonConfig.CUSTOM_ORIGIN.get().trim();
            if (!story.isEmpty())
            {
                return PersonalityTexts.trait("origin.personalizado") + "\n" + limit(story, 600);
            }
            // Sin historia escrita, la de Chill: mejor una historia que ninguna.
            return PersonalityTexts.trait("origin.chill");
        }
        return PersonalityTexts.trait("origin." + Traits.key(p.origin()));
    }

    // AUTO: el mod no tiene un idioma propio, asi que se usa el del juego del jugador.
    static String dialectKey(Dialect dialect, String language)
    {
        String lang = language == null ? "" : language.toLowerCase(Locale.ROOT);
        return switch (dialect)
        {
            case LATINO -> "latino";
            case RIOPLATENSE -> "rioplatense";
            case ESPANA -> "espana";
            case AUTO -> lang.startsWith("pt") ? "portuguese" : lang.startsWith("en") ? "english" : "latino";
        };
    }

    private static String nickname()
    {
        String nick = RoccoCommonConfig.NICKNAME.get().trim().replace("\"", "");
        return nick.isEmpty() ? "" : PersonalityTexts.trait("nickname").replace("{{value}}", limit(nick, 40));
    }

    // La guia de estilo de las imitaciones es lo mas largo de todo el prompt. Va completa cuando
    // puede hacer falta (le pidieron una, esta haciendo una, o tiene permiso para una espontanea);
    // si no, van solo las reglas. Los planes gratis de algunos proveedores cuentan tokens por
    // minuto, y asi cada charla comun pesa bastante menos.
    private static String impressions(PersonalityProfile p, boolean full)
    {
        if (!RoccoCommonConfig.IMPRESSIONS_ENABLED.get())
        {
            return PersonalityTexts.trait("impressions.off");
        }
        String first = p.spontaneousImpressions() == SpontaneousImpressions.NUNCA
                ? PersonalityTexts.trait("impressions.nunca")
                : PersonalityTexts.trait("impressions.muy_raras");
        return first + "\n" + (full ? PersonalityTexts.impressions() : compactImpressions());
    }

    static String compactImpressions()
    {
        StringBuilder out = new StringBuilder();
        boolean inGuide = false;
        for (String line : PersonalityTexts.impressions().split("\n"))
        {
            if (line.startsWith("- Style guide"))
            {
                inGuide = true;
                out.append("- Style guide: play the character with what you know about them (attitude, way of talking, catchphrases).\n");
                continue;
            }
            if (inGuide && line.startsWith("  "))
            {
                continue;
            }
            inGuide = false;
            out.append(line).append('\n');
        }
        return out.toString().trim();
    }

    // Los ejemplos del trato elegido, sin los que contradicen lo que esta apagado.
    private static String examples(PersonalityProfile p, boolean voiceTags)
    {
        String[] lines = PersonalityTexts.examples(p.usesChillExamples()).split("\n");
        List<List<String>> pairs = new ArrayList<>();
        for (String line : lines)
        {
            if (line.startsWith("Player:") || pairs.isEmpty())
            {
                pairs.add(new ArrayList<>());
            }
            pairs.get(pairs.size() - 1).add(line);
        }

        StringBuilder out = new StringBuilder();
        for (List<String> pair : pairs)
        {
            String joined = String.join("\n", pair);
            if (!p.allowsPsycho() && joined.contains("@psycho"))
            {
                continue;
            }
            if (!RoccoCommonConfig.IMPRESSIONS_ENABLED.get() && joined.contains("@voz:"))
            {
                continue;
            }
            // Sin voz las etiquetas no sirven, y verlas en los ejemplos haria que las escriba igual.
            out.append(voiceTags ? joined : VOICE_TAG.matcher(joined).replaceAll("")).append('\n');
        }
        return out.toString().trim();
    }

    // La lista de caras que puede elegir, con lo que significa cada una.
    public static String faces(PersonalityProfile profile)
    {
        List<String> parts = new ArrayList<>();
        Map<String, String> meanings = PersonalityTexts.faces();
        for (RoccoExpression expression : RoccoExpression.selectable())
        {
            if (expression == RoccoExpression.PSYCHO && !profile.allowsPsycho())
            {
                continue;
            }
            String meaning = meanings.get(expression.id());
            parts.add("@" + expression.id() + (meaning == null ? "" : " (" + meaning + ")"));
        }
        return String.join(", ", parts);
    }

    // Las notas del jugador van despues de los rasgos y antes del cierre.
    private static String insertExtraNotes(String text, Mode mode)
    {
        String notes = RoccoCommonConfig.EXTRA_NOTES.get().trim();
        if (mode != Mode.PERSONALIZADO || notes.isEmpty())
        {
            return text;
        }
        String block = PersonalityTexts.trait("extra_notes") + "\n" + limit(notes, 800) + "\n\n";
        int at = text.indexOf("Stay in character.");
        return at < 0 ? text + "\n\n" + block.trim() : text.substring(0, at) + block + text.substring(at);
    }

    // Reemplaza los marcadores. Si uno queda vacio y la linea no tiene nada mas, la linea se
    // borra entera; si tiene mas texto ("Don't repeat the player's name... {{nickname}}"), solo
    // se saca el marcador, para no perder la instruccion que lo acompaña.
    static String fill(String template, Map<String, String> values)
    {
        StringBuilder out = new StringBuilder();
        for (String line : template.split("\n", -1))
        {
            Matcher matcher = PLACEHOLDER.matcher(line);
            if (!matcher.find())
            {
                out.append(line).append('\n');
                continue;
            }
            matcher.reset();

            StringBuilder replaced = new StringBuilder();
            boolean anyEmpty = false;
            while (matcher.find())
            {
                String value = values.getOrDefault(matcher.group(1), "");
                anyEmpty |= value.isBlank();
                matcher.appendReplacement(replaced, Matcher.quoteReplacement(value));
            }
            matcher.appendTail(replaced);

            String result = replaced.toString();
            if (anyEmpty && ONLY_PREFIX.matcher(result).matches())
            {
                continue;
            }
            out.append(result.stripTrailing()).append('\n');
        }
        // Una linea borrada puede dejar dos en blanco seguidas.
        return out.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    private static String limit(String text, int max)
    {
        return text.length() > max ? text.substring(0, max).trim() : text;
    }

    private PersonalityPromptBuilder() {}
}

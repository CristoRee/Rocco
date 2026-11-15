package com.cristoree.rocco.ai;

import com.cristoree.rocco.entity.RoccoExpression;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Lee las etiquetas que la IA pone en su respuesta y separa lo que va a cada lado:
//   @smug @voz:Vegeta|es [shouting] Que te crees, insecto?
// - la cara (@smug, o la vieja [smug] por compatibilidad),
// - la imitacion (@voz:Nombre|es o |pt), que dura solo esa respuesta,
// - las etiquetas de emocion de la voz ([shouting]), que no se muestran en el chat y a la voz se
//   le pasan en el formato que entiende.
public final class ReplyTags
{
    public record Parsed(@Nullable RoccoExpression face, @Nullable String impression, String language,
                         String chatText, String voiceText, String historyText) {}

    private static final Pattern LEADING_FACE = Pattern.compile("^\\s*[*_]*@([a-z_]+)\\b[*_]*");
    private static final Pattern LEADING_OLD_FACE = Pattern.compile("^\\s*\\[([a-z_]+)]");
    // Con lengua ("@voz:Bob Esponja|es") el nombre es todo lo de antes del "|", aunque tenga
    // espacios. Sin lengua no se sabe donde termina el nombre, asi que es solo la primera palabra.
    private static final Pattern IMPRESSION = Pattern.compile(
            "@voz\\s*:\\s*(?:([^|@\\[\\]\\n]+?)\\s*\\|\\s*(es|pt)\\b|([^\\s|@\\[\\]]+))", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_FACE = Pattern.compile(
            "(?i)(?:@|\\[)(happy|bored|serious|scared|sad|angry|smug|psycho|dizzy|mewing|cheer|wow)\\]?(?=\\W|$)");
    private static final Pattern VOICE_TAG = Pattern.compile("\\[([a-zA-Z ]{2,24})]");

    public static Parsed parse(String answer, boolean voiceEmotions)
    {
        String text = answer.trim();
        RoccoExpression face = null;
        String impression = null;
        String language = "es";

        // Las etiquetas del principio, en cualquier orden: cara e imitacion.
        for (int guard = 0; guard < 4; guard++)
        {
            Matcher voz = IMPRESSION.matcher(text);
            if (voz.lookingAt() || (voz.find() && voz.start() == leadingSpace(text)))
            {
                impression = (voz.group(1) != null ? voz.group(1) : voz.group(3)).trim();
                if (voz.group(2) != null)
                {
                    language = voz.group(2).toLowerCase(Locale.ROOT);
                }
                text = text.substring(voz.end()).trim();
                continue;
            }

            Matcher at = LEADING_FACE.matcher(text);
            if (at.find() && !text.substring(at.start()).toLowerCase(Locale.ROOT).trim().startsWith("@voz"))
            {
                RoccoExpression found = faceOf(at.group(1));
                if (found != null)
                {
                    face = face == null ? found : face;
                    text = text.substring(at.end()).trim();
                    continue;
                }
            }

            Matcher old = LEADING_OLD_FACE.matcher(text);
            if (old.find())
            {
                RoccoExpression found = faceOf(old.group(1));
                if (found != null)
                {
                    face = face == null ? found : face;
                    text = text.substring(old.end()).trim();
                    continue;
                }
            }
            break;
        }

        // Una imitacion o una cara que quedo en el medio tampoco se muestra.
        text = IMPRESSION.matcher(text).replaceAll(" ");
        text = ANY_FACE.matcher(text).replaceAll(" ");

        String history = (face == null ? "" : "@" + face.id() + " ")
                + (impression == null ? "" : "@voz:" + impression + "|" + language + " ")
                + text.replaceAll("\\s+", " ").trim();

        String chat = VOICE_TAG.matcher(text).replaceAll(" ").replaceAll("\\s+", " ").trim();
        String voice = voiceText(text, voiceEmotions);
        return new Parsed(face, impression == null || impression.isBlank() ? null : impression, language,
                chat, voice, history.trim());
    }

    private static int leadingSpace(String text)
    {
        int i = 0;
        while (i < text.length() && Character.isWhitespace(text.charAt(i)))
        {
            i++;
        }
        return i;
    }

    @Nullable
    private static RoccoExpression faceOf(String id)
    {
        String wanted = id.toLowerCase(Locale.ROOT);
        for (RoccoExpression expression : RoccoExpression.selectable())
        {
            if (expression.id().equals(wanted))
            {
                return expression;
            }
        }
        return null;
    }

    // Las etiquetas que la voz sabe decir quedan como {emocion}: FishAudio las pasa al formato del
    // modelo que use de verdad (que puede cambiar si uno se queda sin credito). El resto se saca.
    static String voiceText(String text, boolean emotions)
    {
        Matcher matcher = VOICE_TAG.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find())
        {
            String tag = matcher.group(1).toLowerCase(Locale.ROOT).trim();
            boolean known = emotions && com.cristoree.rocco.voice.FishAudio.knowsEmotion(tag);
            matcher.appendReplacement(out, known ? Matcher.quoteReplacement(" {" + tag + "} ") : " ");
        }
        matcher.appendTail(out);
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    private ReplyTags() {}
}

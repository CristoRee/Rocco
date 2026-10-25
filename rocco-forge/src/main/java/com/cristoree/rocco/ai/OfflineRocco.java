package com.cristoree.rocco.ai;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoExpression;
import net.minecraft.core.BlockPos;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Rocco sin IA. Contesta con frases propias que respetan su personalidad y entiende ordenes
// simples, asi cualquiera que baje el mod lo puede usar sin cuentas ni claves. Tambien es el
// respaldo cuando todas las IAs fallan o se quedan sin cupo.
public final class OfflineRocco
{
    public record Answer(String text, RoccoExpression face, String verb, String argument) {}

    private static final Map<String, Integer> LAST_LINE = new ConcurrentHashMap<>();

    // ---------- ordenes ----------

    private static final Pattern NOT_FOLLOW = Pattern.compile("\\b(no me sigas|deja de seguirme|dejame solo|no vengas)\\b");
    private static final Pattern STOP = Pattern.compile("\\b(basta|stop|cancela|para de|deja de|para ya)\\b|^\\s*para\\s*$");
    // Sin "aca" ni "vamos": "que haces aca?" o "vamos a plantar" no son una orden de seguir.
    private static final Pattern FOLLOW = Pattern.compile("\\b(seguime|segui|veni|veni aca|follow|follow me|come here)\\b");
    private static final Pattern PLANT = Pattern.compile("\\b(planta|plantar|plantame|plant|sembra|sembrar|siembra)\\w*");
    private static final Pattern HARVEST = Pattern.compile("\\b(cosecha|cosechar|harvest|recolecta|arranca)\\w*");
    private static final Pattern OPEN = Pattern.compile("\\b(abri|abrir|abrime|cerra|cerrar|open|close)\\b.*\\b(puerta|trampilla|porton|door|trapdoor|gate)\\b"
            + "|\\b(puerta|trampilla|porton|door)\\b.*\\b(abri|abrir|cerra|open|close)\\b");
    private static final Pattern EAT = Pattern.compile("\\b(come|comer|comete|comelo|comela|trag\\w*|eat)\\b(.*)");
    // Sin "sentado" ni "dormido": "estas sentado, levantate" pide lo contrario.
    private static final Pattern SIT = Pattern.compile("\\b(senta|sentate|sentarte|sentar|sentarse|sentame|sientate|sit|sit down)\\b");
    private static final Pattern SLEEP = Pattern.compile("\\b(dormi|dormite|dormir|dormirte|duermete|duerme|siesta|a mimir|sleep|nap)\\b");
    private static final Pattern WAKE = Pattern.compile("\\b(levanta\\w*|desperta\\w*|wake up|arriba)\\b");
    private static final Pattern WAIT = Pattern.compile("\\b(espera\\w*|quedate|quieto|stay|wait)\\b");

    // Trabajos. El argumento es el resto de la frase: RoccoActions lo traduce a items.
    private static final Pattern TEND = Pattern.compile("\\b(cuida|cuidar|cuidame|atende|atender)\\b.*\\b(huerta|huertas|cultivos?|granja|campo|plantacion)\\b");
    private static final Pattern HARVEST_CHEST = Pattern.compile("\\bcofre\\b.*\\bcosecha\\b|\\bcosecha\\b.*\\b(este|ese|aca|ahi) cofre\\b");
    private static final Pattern RECIPE = Pattern.compile("\\breceta (de |del |para )?(.+)|\\bque (necesito|hace falta|se necesita) para (hacer |craftear |fabricar )?(.+)"
            + "|\\bcomo (se hace|se craftea|hago|crafteo|fabrico) (un |una |el |la )?(.+)");
    private static final Pattern SEARCH = Pattern.compile("\\b(busca\\w*|fijate|mira|revisa)\\b(.*)\\b(cofres?|baul|barril)\\b");
    private static final Pattern TILL = Pattern.compile("\\b(ara|arar|arame|labra|labrar)\\b|\\bprepara(me)? la tierra\\b");
    private static final Pattern CHOP = Pattern.compile("\\b(tala\\w*|corta\\w*|hacha\\w*)\\b.*\\b(arbol\\w*|tronco\\w*|madera)\\b|\\btala(lo|me)?\\b");
    private static final Pattern LIGHT = Pattern.compile("\\b(ilumina\\w*|alumbra\\w*)\\b|\\bpone(me)? antorchas\\b");
    private static final Pattern COOK = Pattern.compile("\\b(cocina\\w*|asa|asar|asame|asalo|asala|hornea\\w*|funde|fundi|fundir|derreti\\w*)\\b(.*)");
    private static final Pattern CRAFT = Pattern.compile("\\b(craftea\\w*|craftear|fabrica\\w*|haceme|hace|hacete|arma|armame|construi|construime)\\b(.*)");
    private static final Pattern STORE = Pattern.compile("\\b(guarda\\w*|deja(lo|la|los|las)? en)\\b(.*)");
    private static final Pattern TAKE = Pattern.compile("\\b(saca\\w*|agarra\\w*)\\b(.*)\\b(del|de los|de el|en el)\\s+(cofres?|baul|barril)\\b");
    private static final Pattern BREAK = Pattern.compile("\\b(rompe\\w*|saca\\w*|quita\\w*|desarma\\w*)\\b(.*)");
    private static final Pattern PLACE = Pattern.compile("\\b(pone\\w*|pon|coloca\\w*)\\b(.*)");
    private static final Pattern FETCH = Pattern.compile("\\b(trae\\w*|alcanza\\w*)\\b(.*)");
    private static final Pattern GIVE = Pattern.compile("\\b(dame|damelo|damela|damelos|damelas|pasame|pasamelo|pasamelos)\\b(.*)");
    private static final Pattern DROP = Pattern.compile("\\b(tira|tiralo|tirala|tiralos|suelta\\w*|larga\\w*)\\b(.*)");
    private static final Pattern PICKUP = Pattern.compile("\\b(junta\\w*|levanta\\w*|recoge\\w*|agarra\\w*)\\b.*\\b(piso|suelo|tirad[oa]s?)\\b");
    private static final Pattern GATHER = Pattern.compile("\\b(consegui\\w*|consigue\\w*|busca\\w*|mina|minar|minas|pica|picar|picame|junta\\w*)\\b(.*)");

    // ---------- charla ----------

    private static final Pattern VEGGIE_INSULT = Pattern.compile("\\b(papa|patata|tuberculo|vegetal|verdura|pure|batata|potato)s?\\b");
    private static final Pattern RUDE = Pattern.compile("\\b(tonto|boludo|pelotudo|idiota|estupido|inutil|feo|salame|tarado|bobo|stupid|idiot|dumb)\\b");
    private static final Pattern DEATH = Pattern.compile("\\b(donde (me )?mori|donde quede|mis cosas|mis items|where did i die|donde fue que mori)\\b");
    private static final Pattern WHO = Pattern.compile("\\b(quien sos|que sos|como te llamas|who are you|what are you|de donde saliste)\\b");
    private static final Pattern HELP = Pattern.compile("\\b(ayuda|help|que podes hacer|que sabes hacer|comandos)\\b");
    private static final Pattern THANKS = Pattern.compile("\\b(gracias|thanks|genio|crack|groso|capo|bien hecho|te quiero|sos lo mas)\\b");
    private static final Pattern GREETING = Pattern.compile("\\b(hola|buenas|hey|hi|hello|que tal|como estas|como va|ey)\\b");

    public static Answer reply(RoccoEntity rocco, String message)
    {
        String text = normalize(message);

        // Primero las ordenes: son lo que el jugador espera que funcione si o si.
        if (NOT_FOLLOW.matcher(text).find())
        {
            return act("wait", RoccoExpression.BORED, "esperar", "");
        }
        if (STOP.matcher(text).find())
        {
            return act("stop", RoccoExpression.BORED, "parar", "");
        }

        Answer work = work(text, rocco);
        if (work != null)
        {
            return work;
        }
        if (PLANT.matcher(text).find())
        {
            return act("plant", RoccoExpression.SMUG, "plantar", cropIn(text));
        }
        if (HARVEST.matcher(text).find())
        {
            return act("harvest", RoccoExpression.SMUG, "cosechar", "");
        }
        if (OPEN.matcher(text).find())
        {
            return act("open", RoccoExpression.HAPPY, "abrir", "");
        }
        // Va antes que comer: "come here" es seguir, no comer.
        if (FOLLOW.matcher(text).find())
        {
            return act("follow", RoccoExpression.HAPPY, "seguir", "");
        }
        Matcher eat = EAT.matcher(text);
        if (eat.find())
        {
            return act("eat", RoccoExpression.HAPPY, "comer", eatTarget(eat.group(2)));
        }
        // Levantarse va primero: "estas sentado, levantate" no es una orden de sentarse.
        if (WAKE.matcher(text).find())
        {
            return act("wake", RoccoExpression.HAPPY, "levantarse", "");
        }
        if (SLEEP.matcher(text).find())
        {
            return act("sleep", RoccoExpression.BORED, "dormir", "");
        }
        if (SIT.matcher(text).find())
        {
            return act("sit", RoccoExpression.HAPPY, "sentarse", "");
        }
        if (WAIT.matcher(text).find())
        {
            return act("wait", RoccoExpression.SMUG, "esperar", "");
        }

        // Despues, la charla.
        if (DEATH.matcher(text).find())
        {
            return death(rocco);
        }
        if (VEGGIE_INSULT.matcher(text).find())
        {
            return say("veggie", RoccoExpression.ANGRY);
        }
        if (RUDE.matcher(text).find())
        {
            return say("rude", RoccoExpression.SMUG);
        }
        if (WHO.matcher(text).find())
        {
            return say("who", RoccoExpression.SMUG);
        }
        if (HELP.matcher(text).find())
        {
            return say("help", RoccoExpression.SERIOUS);
        }
        if (THANKS.matcher(text).find())
        {
            return say("thanks", RoccoExpression.SMUG);
        }
        if (GREETING.matcher(text).find())
        {
            return say("greeting", RoccoExpression.HAPPY);
        }
        if (text.contains("?"))
        {
            return say("question", RoccoExpression.SMUG);
        }
        return say("filler", RoccoExpression.BORED);
    }

    private static final Pattern CALL = Pattern.compile("\\b(teletransport\\w*|tepea\\w*|tpea\\w*|aparece\\w*|vente|veni (aca|ya|para aca|rapido|al toque))\\b");
    private static final Pattern FAR_CALL = Pattern.compile("\\b(veni\\w*|vente|teletransport\\w*|tepea\\w*|tpea\\w*|aparece\\w*|seguime|donde estas|volve\\w*)\\b");
    private static final Pattern MATE = Pattern.compile("\\b(toma\\w*|ceba\\w*|prepara\\w*|hacete|queres|vamos a tomar|un)\\b.*\\bmate(cito|s)?\\b|\\bmateamos\\b|\\bmatecito\\b");
    private static final Pattern ENCHANT = Pattern.compile("\\b(encanta\\w*|hechiza\\w*)\\b(.*)");
    private static final Pattern PLACE_SAVE = Pattern.compile("\\b(recorda\\w*|acordate|guarda\\w*|anota\\w*|marca\\w*)\\b.*\\b(este lugar|aca|esto|este sitio)\\b\\s*(como|es)?\\s*(.*)"
            + "|^(esta|este|aca) es (mi |la |el |nuestra |nuestro )(.+)$");
    private static final Pattern PLACE_FORGET = Pattern.compile("\\bolvida\\w* (de )?(el lugar |la |el |mi )?(.+)");
    private static final Pattern GUIDE = Pattern.compile("\\b(llevame|guiame|acompaname|vamos)\\b (a |al |hasta |para |a la |a mi )(la |el |mi )?(.+)");
    private static final Pattern BREED = Pattern.compile("\\b(cria|criar|criame|reproduc\\w*|aparea\\w*)\\b(.*)|\\bdales? de comer a (las |los )?(.+)");
    private static final Pattern FISH = Pattern.compile("\\b(pesca|pescar|pescame|pescate|pesquemos)\\b");
    private static final Pattern BUY = Pattern.compile("\\b(compra\\w*|comprame)\\b(.*)");
    private static final Pattern SELL = Pattern.compile("\\b(vende\\w*|vendele\\w*)\\b(.*)");
    private static final Pattern SORT = Pattern.compile("\\b(ordena\\w*|acomoda\\w*|organiza\\w*)\\b.*\\b(cofres?|baul\\w*|barril\\w*|cosas)\\b");

    // "Rocco, veni" dicho desde lejos: alcanza con eso para que aparezca.
    public static boolean isCall(String message)
    {
        return FAR_CALL.matcher(normalize(message)).find();
    }

    // Las ordenes de trabajo. null si la frase no es una (o nombra algo que no existe).
    private static Answer work(String text, RoccoEntity rocco)
    {
        if (CALL.matcher(text).find())
        {
            return act("teleport_ack", RoccoExpression.SMUG, "teletransportarse", "");
        }
        if (MATE.matcher(text).find())
        {
            return act("mate_ack", RoccoExpression.HAPPY, "mate", "");
        }

        Matcher enchant = ENCHANT.matcher(text);
        if (enchant.find())
        {
            return act("enchant_ack", RoccoExpression.SMUG, "encantar", enchant.group(2).trim());
        }

        // "este es el cofre de la cosecha" o "guarda esto en el cofre" no son lugares.
        Matcher save = PLACE_SAVE.matcher(text);
        if (!text.matches(".*\\b(cofres?|baul|barril)\\b.*") && save.find())
        {
            String name = save.group(4) != null ? save.group(4) : save.group(7);
            if (name != null && !name.isBlank())
            {
                return new Answer("", RoccoExpression.SMUG, "recordar_lugar", name.trim());
            }
        }
        Matcher forget = PLACE_FORGET.matcher(text);
        if (forget.find())
        {
            return new Answer("", RoccoExpression.SERIOUS, "olvidar_lugar", forget.group(3).trim());
        }
        Matcher guide = GUIDE.matcher(text);
        if (guide.find())
        {
            String where = guide.group(4).trim();
            // "vamos a plantar" no es un lugar, y "vamos a la cueva" solo lo es si la recuerda.
            boolean vamos = guide.group(1).equals("vamos");
            boolean known = rocco != null && rocco.places().containsKey(RoccoActions.placeName(where));
            if (!vamos || known)
            {
                return act("guide_ack", RoccoExpression.SMUG, "ir_a", where);
            }
        }

        if (SORT.matcher(text).find())
        {
            return act("sort_ack", RoccoExpression.SERIOUS, "ordenar", text.matches(".*\\b(este|ese)\\b.*") ? "este" : "");
        }
        Matcher breed = BREED.matcher(text);
        if (breed.find())
        {
            String animal = breed.group(2) != null ? breed.group(2) : breed.group(4);
            return act("breed_ack", RoccoExpression.HAPPY, "criar", animal == null ? "" : animal.trim());
        }
        if (FISH.matcher(text).find())
        {
            return act("fish_ack", RoccoExpression.HAPPY, "pescar", "");
        }
        Matcher buy = BUY.matcher(text);
        if (buy.find() && named(buy.group(2)))
        {
            return act("trade_ack", RoccoExpression.SMUG, "comprar", buy.group(2).trim());
        }
        Matcher sell = SELL.matcher(text);
        if (sell.find() && named(sell.group(2)))
        {
            return act("trade_ack", RoccoExpression.SMUG, "vender", sell.group(2).trim());
        }
        if (TEND.matcher(text).find())
        {
            return act("tend_ack", RoccoExpression.SMUG, "cuidar_huerta", "");
        }
        if (HARVEST_CHEST.matcher(text).find())
        {
            return new Answer("", RoccoExpression.SMUG, "cofre_cosecha", "");
        }

        Matcher recipe = RECIPE.matcher(text);
        if (recipe.find())
        {
            String subject = firstNonNull(recipe.group(2), recipe.group(5), recipe.group(8));
            if (subject != null && com.cristoree.rocco.work.Names.parse(subject).found())
            {
                return new Answer("", RoccoExpression.SMUG, "receta", subject);
            }
        }

        Matcher search = SEARCH.matcher(text);
        if (search.find())
        {
            String subject = search.group(2).replaceAll("\\b(en|el|los|la|las|del)\\b", " ").trim();
            boolean named = !subject.isBlank() && com.cristoree.rocco.work.Names.parse(subject).found();
            return named
                    ? act("fetch_ack", RoccoExpression.HAPPY, "agarrar", subject)
                    : act("craft_search_ok", RoccoExpression.HAPPY, "buscar", "");
        }

        if (TILL.matcher(text).find())
        {
            return act("till_ack", RoccoExpression.HAPPY, "arar", "");
        }
        if (CHOP.matcher(text).find())
        {
            return act("chop_ack", RoccoExpression.SMUG, "talar", "");
        }
        if (LIGHT.matcher(text).find())
        {
            return act("light_ack", RoccoExpression.HAPPY, "iluminar", "");
        }
        if (PICKUP.matcher(text).find())
        {
            return act("pickup_ack", RoccoExpression.SMUG, "juntar", "");
        }

        Matcher cook = COOK.matcher(text);
        if (cook.find())
        {
            return act("cook_ack", RoccoExpression.HAPPY, "cocinar", cook.group(2).trim());
        }

        Matcher take = TAKE.matcher(text);
        if (take.find() && named(take.group(2)))
        {
            return act("fetch_ack", RoccoExpression.HAPPY, "agarrar", take.group(2).trim());
        }

        Matcher store = STORE.matcher(text);
        if (store.find())
        {
            String rest = store.group(3);
            String what = rest.contains("cosecha") ? "cosecha"
                    : named(rest.replaceAll("\\b(en|el|este|ese|cofre|baul|aca|ahi)\\b", " ")) ? rest : "todo";
            return act("store_ack", RoccoExpression.HAPPY, "guardar", what);
        }

        Matcher craft = CRAFT.matcher(text);
        if (craft.find() && named(craft.group(2)))
        {
            return act("craft_ack", RoccoExpression.SMUG, "craftear", craft.group(2).trim());
        }

        Matcher breaking = BREAK.matcher(text);
        if (breaking.find())
        {
            String rest = breaking.group(2).trim();
            if (rest.matches("^(eso|esto|este|ese|esa|esta|lo|la)?\\s*(bloque)?\\s*(que (estoy )?mirando)?$"))
            {
                return act("break_ack", RoccoExpression.HAPPY, "romper", "mirando");
            }
            if (named(rest))
            {
                return act("break_ack", RoccoExpression.HAPPY, "romper", rest);
            }
        }

        Matcher place = PLACE.matcher(text);
        if (place.find() && named(place.group(2)))
        {
            return act("place_ack", RoccoExpression.HAPPY, "poner", place.group(2).trim());
        }

        Matcher fetch = FETCH.matcher(text);
        if (fetch.find() && named(fetch.group(2)))
        {
            return act("fetch_ack", RoccoExpression.HAPPY, "traer", fetch.group(2).trim());
        }

        Matcher give = GIVE.matcher(text);
        if (give.find())
        {
            String rest = give.group(2).trim();
            if (rest.matches(".*\\b(todo|todas? las cosas|lo que tengas)\\b.*"))
            {
                return act("give_ack", RoccoExpression.HAPPY, "dar", "todo");
            }
            if (named(rest))
            {
                return act("give_ack", RoccoExpression.HAPPY, "dar", rest);
            }
        }

        Matcher drop = DROP.matcher(text);
        if (drop.find())
        {
            String rest = drop.group(2).trim();
            if (rest.matches(".*\\btodo\\b.*"))
            {
                return act("drop_ack", RoccoExpression.BORED, "tirar", "todo");
            }
            if (named(rest))
            {
                return act("drop_ack", RoccoExpression.BORED, "tirar", rest);
            }
        }

        Matcher gather = GATHER.matcher(text);
        if (gather.find() && named(gather.group(2)))
        {
            return act("gather_ack", RoccoExpression.SMUG, "conseguir", gather.group(2).trim());
        }
        return null;
    }

    private static boolean named(String text)
    {
        return text != null && !text.isBlank() && com.cristoree.rocco.work.Names.parse(text).found();
    }

    private static String firstNonNull(String... values)
    {
        for (String value : values)
        {
            if (value != null)
            {
                return value;
            }
        }
        return null;
    }

    // Lo que dice al brotar de la tierra la primera vez.
    public static Answer greeting()
    {
        return say("awaken", RoccoExpression.SMUG);
    }

    private static Answer death(RoccoEntity rocco)
    {
        BlockPos pos = rocco.getLastDeathPos();
        if (pos == null)
        {
            return new Answer(pick("death_none"), RoccoExpression.SMUG, null, null);
        }

        String dimension = rocco.getLastDeathDimension().replace("minecraft:", "");
        String where = pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                + (dimension.equals("overworld") ? "" : " (en " + dimension + ")");
        return new Answer(pick("death").replace("{pos}", where), RoccoExpression.SMUG, null, null);
    }

    private static Answer act(String category, RoccoExpression face, String verb, String argument)
    {
        return new Answer(pick(category), face, verb, argument);
    }

    private static Answer say(String category, RoccoExpression face)
    {
        return new Answer(pick(category), face, null, null);
    }

    // "plantame papas" -> minecraft:potato. Sin cultivo reconocible, planta lo que tenga.
    private static String cropIn(String text)
    {
        if (text.matches(".*\\b(papa|patata|potato)s?\\b.*")) return "minecraft:potato";
        if (text.matches(".*\\b(zanahoria|carrot)s?\\b.*")) return "minecraft:carrot";
        if (text.matches(".*\\b(remolacha|beet\\w*)\\b.*")) return "minecraft:beetroot_seeds";
        if (text.matches(".*\\b(trigo|wheat)\\b.*")) return "minecraft:wheat_seeds";
        return "";
    }

    // "comete al aldeano" -> "aldeano". "come algo" -> "" (elige solo, sin tocar protegidos).
    private static String eatTarget(String rest)
    {
        String target = rest.replaceAll("[^a-z0-9 _]", " ")
                .replaceAll("\\b(a|al|el|la|los|las|un|una|ese|esa|eso|este|esta|esto|ahora|ya|por favor|porfa|algo|de|del)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return target;
    }

    private static String normalize(String message)
    {
        String lower = message.toLowerCase(Locale.ROOT);
        String noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        // El nombre de Rocco no aporta nada para entender la orden.
        return noAccents.replaceAll("\\brocco\\b[,:]?", " ").replaceAll("\\s+", " ").trim();
    }

    // Una frase de la categoria con sus datos puestos: line("craft_done", "item", "Pico").
    public static String line(String category, String... keyValues)
    {
        String text = pick(category);
        for (int i = 0; i + 1 < keyValues.length; i += 2)
        {
            text = text.replace("{" + keyValues[i] + "}", keyValues[i + 1]);
        }
        return text;
    }

    // La cara que va con cada tipo de frase de trabajo.
    public static RoccoExpression faceFor(String category)
    {
        if (category.endsWith("_done") || category.endsWith("_found_it") || category.endsWith("_got_it")
                || category.endsWith("_set") || category.endsWith("_arrived") || category.endsWith("_saved"))
        {
            return RoccoExpression.SMUG;
        }
        if (category.endsWith("_ack") || category.endsWith("_ok") || category.startsWith("craft_going")
                || category.startsWith("craft_making") || category.startsWith("cook_placing")
                || category.startsWith("cook_making"))
        {
            return RoccoExpression.HAPPY;
        }
        if (category.equals("tool_broke") || category.endsWith("_give_up"))
        {
            return RoccoExpression.SAD;
        }
        if (category.endsWith("_search_myself"))
        {
            return RoccoExpression.BORED;
        }
        return RoccoExpression.SERIOUS;
    }

    // Elige una frase al azar sin repetir la anterior de esa categoria.
    private static String pick(String category)
    {
        String[] lines = LINES.get(category);
        if (lines == null || lines.length == 0)
        {
            return "...";
        }
        if (lines.length == 1)
        {
            return lines[0];
        }

        int last = LAST_LINE.getOrDefault(category, -1);
        int index;
        do
        {
            index = ThreadLocalRandom.current().nextInt(lines.length);
        }
        while (index == last);

        LAST_LINE.put(category, index);
        return lines[index];
    }

    static String[] lines(String category)
    {
        return LINES.get(category);
    }

    private static final Map<String, String[]> LINES = Map.ofEntries(
            Map.entry("awaken", new String[]{
                    "Llegue. La Madre Tierra te mando lo mejor que tenia. De nada.",
                    "Ah, vos sos el que me toca cuidar. Se nota que me necesitabas.",
                    "Un milagro de la naturaleza acaba de brotar al lado tuyo. Aplaudi, dale.",
                    "Hola. Soy Rocco. A partir de ahora las cosas te van a salir bien, gracias a mi."}),
            Map.entry("greeting", new String[]{
                    "Hola. Si, soy yo, el milagro. Que necesitas?",
                    "Buenas. Te estaba esperando, como siempre.",
                    "Que tal? Yo espectacular, obvio.",
                    "Ey. Llegaste justo, ya me estaba aburriendo de ser perfecto solo.",
                    "Hola, campeon. Bah, campeon es mucho. Hola."}),
            Map.entry("veggie", new String[]{
                    "PAPA? Soy un milagro de la naturaleza. Un poco de respeto, por favor.",
                    "Decime papa otra vez y me ofendo en serio, eh.",
                    "Tuberculo no. Rocco. R, O, C, C, O.",
                    "Me hizo la Madre Tierra en persona y vos me tratas de verdura. Increible.",
                    "No soy ningun vegetal. Un poco de respeto para el que te mantiene vivo."}),
            Map.entry("rude", new String[]{
                    "Mira quien habla. Te moris con un creeper y el tonto soy yo.",
                    "Decime lo que quieras, igual sin mi no llegas ni a la esquina.",
                    "Que boca. La Madre Tierra se va a enterar.",
                    "Tomatelo con calma. Igual te quiero, eh. Un poquito.",
                    "Uh, que miedo. Segui, que me divierte."}),
            Map.entry("who", new String[]{
                    "Soy Rocco, un milagro de la naturaleza. La Madre Tierra me hizo para cuidarte, porque solo no podes.",
                    "Rocco. Unico, irrepetible, y bastante facherito.",
                    "Tu companero. Bah, tu salvador, para ser exactos."}),
            Map.entry("help", new String[]{
                    "Planto lo que sea sin semillas, cosecho y replanto, crafteo, cocino, busco en los cofres, guardo, pongo y saco bloques, talo arboles, pongo antorchas y te sigo. Clic derecho y te abro mi tapa.",
                    "Proba 'craftea un pico de piedra', 'cocina las papas', 'traeme carbon del cofre', 'guarda la cosecha en este cofre', 'cuida la huerta', 'ilumina' o 'tala un arbol'.",
                    "Tambien encanto tus cosas una vez por dia, me tomo unos mates, recuerdo lugares ('esta es mi casa', 'llevame a casa'), crio animales, pesco, comercio con aldeanos y ordeno tus cofres."}),
            Map.entry("thanks", new String[]{
                    "Ya se, soy lo mas.",
                    "De nada. Anotalo en la lista de veces que te salve.",
                    "Obvio. Para eso me mando la Madre Tierra.",
                    "Lo se, lo se. Segui, que me gusta."}),
            Map.entry("question", new String[]{
                    "Buena pregunta. La respuesta es que deberias hacerme caso a mi.",
                    "Ni idea, y eso que soy un milagro. Preguntame algo que valga la pena.",
                    "Mmm. Eso lo resolves vos, yo estoy ocupado siendo genial.",
                    "Te contestaria, pero me gusta verte pensar."}),
            Map.entry("filler", new String[]{
                    "Aha. Interesante. Bah, mas o menos.",
                    "Te escucho, eh. Mas o menos, pero te escucho.",
                    "Si, si. Segui que yo cuido que no te mueras.",
                    "Mira vos.",
                    "Anotado. No, mentira."}),
            Map.entry("plant", new String[]{
                    "Mira y aprende como planta un profesional.",
                    "Dale. Sembrar es lo mio, literalmente.",
                    "Aca va. Y semillas no me hacen falta: las llevo adentro."}),
            Map.entry("harvest", new String[]{
                    "A cosechar. Vos quedate ahi mirando, como siempre.",
                    "Dale, junto lo maduro. Lo verde lo dejo, no soy un improvisado.",
                    "Voy. Despues no digas que no te ayudo."}),
            Map.entry("open", new String[]{
                    "Ahi va. Las puertas no son lo tuyo, se nota.",
                    "Abro yo, tranqui."}),
            Map.entry("follow", new String[]{
                    "Voy. Alguien tiene que cuidarte.",
                    "Dale, adelante. Yo te cubro.",
                    "Te sigo, pero no te pierdas que despues lloras."}),
            Map.entry("wait", new String[]{
                    "Me quedo aca. No hagas macanas sin mi.",
                    "Espero. Pero no tardes, que me aburro.",
                    "Dale, me quedo. Si te moris, avisa."}),
            Map.entry("stop", new String[]{
                    "Paro. Igual lo estaba haciendo perfecto.",
                    "Listo, dejo. Vos te lo perdes."}),
            Map.entry("eat", new String[]{
                    "A comer. Esto me lo gane.",
                    "Dale, que tengo hambre de milagro."}),
            Map.entry("sit", new String[]{
                    "Me siento. Un descanso merecido.",
                    "Ok, me pongo comodo."}),
            Map.entry("sleep", new String[]{
                    "Siesta. Hasta los milagros descansan.",
                    "Me tiro un rato. No hagas ruido."}),
            Map.entry("wake", new String[]{
                    "Ya estoy. Que paso, me extranaste?",
                    "Arriba. A ver que desastre hiciste mientras dormia."}),
            Map.entry("death", new String[]{
                    "Te moriste en {pos}. Si me hubieras hecho caso, no pasaba.",
                    "Fue en {pos}. Tus cosas deben seguir ahi, si te apuras.",
                    "En {pos}. Clasico tuyo. Vamos a buscar tus cosas."}),
            Map.entry("death_none", new String[]{
                    "Que yo sepa no te moriste. Todavia. Gracias a mi.",
                    "No te moriste nunca desde que estoy yo. Casualidad? No creo."}),

            // ---------- trabajos ----------

            Map.entry("craft_ack", new String[]{
                    "Dale, a ver que tengo encima.",
                    "Fabricar es lo mio. Ya vas a ver.",
                    "Anotado. Dejame revisar los materiales."}),
            Map.entry("craft_missing", new String[]{
                    "Para {item} me faltan {missing}. Pasamelos o decime en que cofre busco.",
                    "Me faltan {missing} para {item}. Tirame las cosas o te las busco yo en los cofres.",
                    "Sin {missing} no hay {item}, y milagros de esos no hago. Me los das?"}),
            Map.entry("craft_got_it", new String[]{
                    "Ahi esta, ya tengo todo. Mira y aprende.",
                    "Perfecto, con esto alcanza. Manos a la obra."}),
            Map.entry("craft_still_need", new String[]{
                    "Bien, pero todavia me faltan {missing}.",
                    "Vamos bien. Me quedan {missing}."}),
            Map.entry("craft_search_myself", new String[]{
                    "Como no me das bola, voy a buscar yo a los cofres.",
                    "Bueno, ya fue. Lo busco yo en los cofres, como siempre."}),
            Map.entry("craft_search_ok", new String[]{
                    "Dale, voy a fijarme en los cofres.",
                    "Voy a los cofres. No te muevas."}),
            Map.entry("craft_found_it", new String[]{
                    "Listo, encontre lo que faltaba. Por suerte estoy yo.",
                    "Lo tenias en el cofre y ni sabias. Menos mal que me tenes."}),
            Map.entry("craft_still_missing", new String[]{
                    "En los cofres no estaba todo. Me siguen faltando {missing}.",
                    "Busque y nada. Me faltan {missing}, conseguimelos y lo hago."}),
            Map.entry("craft_give_up", new String[]{
                    "Bueno, cuando tengas lo de {item} me avisas. Yo no espero para siempre.",
                    "Me canse de esperar. Si queres {item}, pedimelo de nuevo cuando tengas todo."}),
            Map.entry("craft_going_to_chop", new String[]{
                    "Falta madera. Voy a talar un arbol, ya vuelvo.",
                    "No hay madera. Tranqui, la saco yo de un arbol."}),
            Map.entry("craft_going_to_mine", new String[]{
                    "Falta piedra. Voy a picar un poco por aca.",
                    "Me voy a picar piedra. Un milagro que trabaja, quien diria."}),
            Map.entry("craft_making_table", new String[]{
                    "No hay mesa de crafteo. Me hago una, que soy un profesional.",
                    "Sin mesa no se puede. Me fabrico una al toque."}),
            Map.entry("craft_failed", new String[]{
                    "Algo salio mal con {item}. No fue mi culpa, eh.",
                    "No me salio {item}. La receta se puso rara."}),
            Map.entry("craft_cant_reach_table", new String[]{
                    "No llego a la mesa de crafteo. Poneme una mas a mano.",
                    "La mesa esta en un lugar imposible. Asi no se puede trabajar."}),
            Map.entry("craft_done", new String[]{
                    "Listo, {item}. Impecable, como todo lo que hago.",
                    "Ahi esta {item}. De nada.",
                    "{item} terminado. Podes aplaudir."}),
            Map.entry("craft_unknown", new String[]{
                    "Eso no se fabrica en una mesa de crafteo. Y yo conozco todas las recetas.",
                    "{item} no se craftea. Se consigue de otra forma."}),
            Map.entry("recipe", new String[]{
                    "Para {item}: {recipe}.",
                    "{item} sale con {recipe}. Facil, si sabes."}),
            Map.entry("item_unknown", new String[]{
                    "No se que es eso. Decimelo de otra forma.",
                    "Eso no existe, o me lo estas diciendo raro."}),

            Map.entry("cook_ack", new String[]{
                    "Dale, prendo el horno.",
                    "A cocinar. Soy chef ademas de milagro."}),
            Map.entry("cook_dont_have", new String[]{
                    "No tengo {item} para cocinar. Pasame unos.",
                    "Sin {item} no cocino nada."}),
            Map.entry("cook_not_cookable", new String[]{
                    "{item} no se cocina. Vos queres comerte cualquier cosa."}),
            Map.entry("cook_nothing", new String[]{
                    "No tengo nada crudo para cocinar. Dame algo.",
                    "Que cocino, aire? Pasame comida cruda."}),
            Map.entry("cook_placing_furnace", new String[]{
                    "No hay horno cerca, pongo el mio."}),
            Map.entry("cook_making_furnace", new String[]{
                    "No hay horno. Me armo uno, esperame."}),
            Map.entry("cook_no_furnace", new String[]{
                    "No hay horno y no puedo hacer uno: me faltan 8 de roca."}),
            Map.entry("cook_no_fuel", new String[]{
                    "No tengo con que prender el horno. Pasame carbon o madera.",
                    "Sin combustible no hay fuego. Carbon, madera, lo que sea."}),
            Map.entry("cook_cant_reach", new String[]{
                    "No llego al horno. Asi no se puede."}),
            Map.entry("cook_furnace_busy", new String[]{
                    "El horno esta ocupado con otra cosa."}),
            Map.entry("cook_furnace_gone", new String[]{
                    "El horno desaparecio. Alguien lo rompio?"}),
            Map.entry("cook_out_of_fuel", new String[]{
                    "Se apago el horno, falta combustible. Saco lo que llego a salir."}),
            Map.entry("cook_ready_far", new String[]{
                    "Ya esta {item} en el horno de {pos}. Pasa a buscarlo."}),
            Map.entry("cook_done", new String[]{
                    "Salio {items}. Huele a exito.",
                    "Listo: {items}. Calentito."}),

            Map.entry("fetch_ack", new String[]{
                    "Voy a los cofres.",
                    "Dale, ya te lo busco."}),
            Map.entry("fetch_unreachable", new String[]{
                    "A ese cofre no llego."}),
            Map.entry("fetch_none", new String[]{
                    "En los cofres no hay {item}. Fijate vos, por las dudas.",
                    "Busque y no hay {item} en ningun cofre."}),
            Map.entry("fetch_partial", new String[]{
                    "Solo encontre {got} de {item}."}),
            Map.entry("fetch_done", new String[]{
                    "Tengo {amount}. Guardado en mi tapa.",
                    "Ya tengo {amount}."}),
            Map.entry("search_nothing", new String[]{
                    "Buscar que? Decime que necesitas."}),

            Map.entry("give_ack", new String[]{
                    "Toma.",
                    "Ahi va, agarralo."}),
            Map.entry("give_none", new String[]{
                    "No tengo eso encima.",
                    "Eso no lo tengo, revisa bien."}),
            Map.entry("give_no_player", new String[]{
                    "No te veo. Donde te metiste?"}),
            Map.entry("give_cant_reach", new String[]{
                    "No puedo llegar hasta vos. Veni vos."}),
            Map.entry("drop_ack", new String[]{
                    "Ahi lo dejo.",
                    "Lo suelto aca."}),

            Map.entry("pickup_ack", new String[]{
                    "Junto todo, como siempre."}),
            Map.entry("pickup_none", new String[]{
                    "No hay nada tirado por aca."}),
            Map.entry("pickup_full", new String[]{
                    "No pude agarrar nada: o no me entra o no llego."}),
            Map.entry("pickup_done", new String[]{
                    "Junte {count} cosas del piso. Ordenado, como debe ser."}),

            Map.entry("store_ack", new String[]{
                    "Lo guardo.",
                    "Al cofre va."}),
            Map.entry("deposit_nothing", new String[]{
                    "No tengo nada para guardar."}),
            Map.entry("deposit_no_place", new String[]{
                    "No se donde guardar {items}. Mira un cofre y decime que lo guarde ahi."}),
            Map.entry("deposit_full", new String[]{
                    "El cofre se lleno. Lo que no entro lo tengo yo."}),
            Map.entry("deposit_done", new String[]{
                    "Guarde {items}.",
                    "Listo, {items} en el cofre. Ordenadito."}),
            Map.entry("deposit_some_kept", new String[]{
                    "{items} me lo quedo, no habia cofre para eso."}),

            Map.entry("harvest_chest_set", new String[]{
                    "Perfecto, ese es el cofre de la cosecha. No me lo muevas.",
                    "Anotado: la cosecha va ahi."}),
            Map.entry("harvest_chest_none", new String[]{
                    "Mira el cofre que queres y decimelo de nuevo."}),
            Map.entry("harvest_none", new String[]{
                    "No hay nada maduro todavia. Paciencia, que la naturaleza no se apura."}),
            Map.entry("harvest_kept", new String[]{
                    "No hay cofre para la cosecha, la guardo yo. Si queres, mira uno y decime que es el de la cosecha."}),
            Map.entry("farm_full", new String[]{
                    "Ya no me entra nada mas. Vacia mi tapa o dame un cofre."}),
            Map.entry("tend_ack", new String[]{
                    "Me quedo cuidando la huerta. Vos anda tranquilo.",
                    "La huerta queda en buenas manos. Las mejores."}),
            Map.entry("plant_no_farmland", new String[]{
                    "No hay tierra arada libre por aca. Dame una azada y la preparo yo."}),
            Map.entry("plant_no_soul_sand", new String[]{
                    "La verruga va en arena de almas, y aca no hay."}),
            Map.entry("plant_no_ground", new String[]{
                    "No hay lugar con tierra para plantar un arbol aca."}),
            Map.entry("till_ack", new String[]{
                    "A preparar la tierra."}),
            Map.entry("till_need_hoe", new String[]{
                    "Para arar necesito una azada. Con las manos no, eh."}),
            Map.entry("till_no_spot", new String[]{
                    "No hay tierra con agua cerca para arar. Sin agua se seca."}),
            Map.entry("till_done", new String[]{
                    "Are {count} bloques. Listos para sembrar."}),

            Map.entry("place_ack", new String[]{
                    "Lo pongo.",
                    "Ahi va."}),
            Map.entry("place_not_block", new String[]{
                    "{item} no es algo que se pueda poner."}),
            Map.entry("place_dont_have", new String[]{
                    "No tengo {item} para poner."}),
            Map.entry("place_no_spot", new String[]{
                    "No hay lugar para ponerlo aca."}),
            Map.entry("place_cant_reach", new String[]{
                    "No llego hasta ahi."}),
            Map.entry("place_failed", new String[]{
                    "No pude poner {item} ahi."}),

            Map.entry("light_ack", new String[]{
                    "Voy a iluminar esto, que da miedo. A vos, digo.",
                    "Antorchas, ya mismo."}),
            Map.entry("light_done", new String[]{
                    "Puse {count} antorchas. Ahora si se ve algo."}),
            Map.entry("light_ran_out", new String[]{
                    "Me quede sin antorchas despues de poner {count}."}),
            Map.entry("light_no_torches", new String[]{
                    "No tengo antorchas ni con que hacerlas. Carbon y palos, por favor."}),
            Map.entry("light_already_lit", new String[]{
                    "Aca ya esta bien iluminado."}),

            Map.entry("break_ack", new String[]{
                    "Dale, lo saco."}),
            Map.entry("break_cant_reach", new String[]{
                    "No llego a ese bloque."}),
            Map.entry("break_nothing", new String[]{
                    "Lo rompi, pero no solto nada."}),
            Map.entry("break_done", new String[]{
                    "Listo, me quedo con {items}."}),
            Map.entry("break_not_found", new String[]{
                    "No veo {item} por aca."}),
            Map.entry("break_unbreakable", new String[]{
                    "Eso no se puede romper. Ni yo."}),
            Map.entry("break_ender_needs_silk", new String[]{
                    "El cofre de ender sin Toque de seda se hace obsidiana. Dame un pico con Toque de seda y lo saco."}),
            Map.entry("break_need_tool", new String[]{
                    "Para eso necesito {tool}. Con las manos no sale nada."}),
            Map.entry("break_too_slow", new String[]{
                    "Eso me llevaria una eternidad con lo que tengo. Dame una herramienta mejor."}),
            Map.entry("break_container_full", new String[]{
                    "Tiene cosas adentro y no me entran. Vacialo primero."}),

            Map.entry("chop_ack", new String[]{
                    "A talar. Tranqui, despues planto otro."}),
            Map.entry("chop_no_tree", new String[]{
                    "No hay ningun arbol cerca."}),
            Map.entry("chop_cant_reach", new String[]{
                    "No llego a ese arbol."}),
            Map.entry("chop_done", new String[]{
                    "Tale el arbol ({count} troncos) y plante otro. La Madre Naturaleza esta orgullosa."}),

            Map.entry("gather_ack", new String[]{
                    "Voy a conseguirlo."}),
            Map.entry("gather_need_tool", new String[]{
                    "Para sacar {item} necesito {tool}."}),
            Map.entry("gather_done", new String[]{
                    "Consegui {amount}."}),
            Map.entry("gather_partial", new String[]{
                    "Solo consegui {got} de {item}. Toma lo que hay."}),
            Map.entry("gather_not_found", new String[]{
                    "No encuentro {item} por ningun lado."}),

            Map.entry("tool_broke", new String[]{
                    "Se me rompio {tool}. Descanse en paz.",
                    "Chau {tool}. Se rompio trabajando, como un heroe."}),

            // ---------- mate, magia, lugares, animales, aldeanos ----------

            Map.entry("mate_ack", new String[]{
                    "Un mate, dale. Lo cebo yo, que me sale mejor.",
                    "Mate. La mejor idea que tuviste en todo el dia.",
                    "Esperame que pongo el agua. Sin hervir, eh."}),
            Map.entry("enchant_ack", new String[]{
                    "A ver, a ver. Esto es lo mio de verdad.",
                    "Preparate para ver magia de la buena."}),
            Map.entry("enchant_start", new String[]{
                    "Quieto, que me concentro con {item}.",
                    "Silencio. La Madre Naturaleza me presta su poder."}),
            Map.entry("enchant_done", new String[]{
                    "Listo: {item} con {enchants}. Ahora necesito descansar, eso cuesta.",
                    "Ahi tenes {item} con {enchants}. Me dejo fundido, me siento un rato."}),
            Map.entry("enchant_tired", new String[]{
                    "Hoy ya use mi poder. Necesito descansar, volve en {time}.",
                    "No me da el cuerpo. La magia vuelve en {time}."}),
            Map.entry("enchant_nothing", new String[]{
                    "Ponete en la mano lo que queres que encante y pedimelo de nuevo."}),
            Map.entry("enchant_dont_see", new String[]{
                    "No veo {item} en tu inventario."}),
            Map.entry("enchant_cant", new String[]{
                    "{item} no se puede encantar. Ni con mi poder, y mira que es mucho."}),
            Map.entry("enchant_already_enchanted", new String[]{
                    "{item} ya esta encantado. Si queres algo puntual, pedimelo: \"con irrompibilidad 3\", por ejemplo.",
                    "Encantar al azar algo que ya tiene magia no se puede. Decime que encantamiento queres."}),
            Map.entry("enchant_no_curses", new String[]{
                    "Maldiciones no hago. Soy un milagro, no una bruja."}),
            Map.entry("enchant_treasure", new String[]{
                    "{enchant} es de tesoro: ese no me sale. Se consigue pescando o en cofres de estructuras."}),
            Map.entry("enchant_wrong_item", new String[]{
                    "{enchant} no va en {item}. Ni con toda mi magia."}),
            Map.entry("enchant_level_capped", new String[]{
                    "Mas de {enchant} {level} no existe. Te lo pongo al maximo."}),
            Map.entry("enchant_already_has", new String[]{
                    "{item} ya tiene {enchant}. Mas no le puedo poner."}),
            Map.entry("enchant_conflict", new String[]{
                    "{enchant} no se lleva con {other}, que ya tiene. Es una o la otra."}),
            Map.entry("enchant_interrupted", new String[]{
                    "Me desconcentraste. Por suerte la magia no se gasto."}),
            Map.entry("enchant_lost", new String[]{
                    "Lo movieste y se me fue el hechizo. Probemos de nuevo."}),

            Map.entry("place_saved", new String[]{
                    "Anotado: esto es {place}. Yo no me olvido de nada.",
                    "Listo, {place} queda guardado aca."}),
            Map.entry("place_forgotten", new String[]{
                    "Ya me olvide de {place}. Como si nunca hubiera existido."}),
            Map.entry("place_unknown", new String[]{
                    "No conozco {place}. Decime \"esta es la {place}\" estando ahi."}),
            Map.entry("place_need_name", new String[]{
                    "Como se llama este lugar? Decime, por ejemplo, \"esta es mi casa\"."}),
            Map.entry("place_full", new String[]{
                    "Ya recuerdo demasiados lugares. Pedime que me olvide de alguno."}),
            Map.entry("guide_ack", new String[]{
                    "Seguime, que yo se el camino.",
                    "Vamos. No te pierdas, eh."}),
            Map.entry("guide_arrived", new String[]{
                    "Llegamos a {place}. Sano y salvo, gracias a mi.",
                    "Aca es {place}. De nada."}),
            Map.entry("guide_wait", new String[]{
                    "Dale, que te espero."}),
            Map.entry("guide_lost", new String[]{
                    "Te perdi. {place} queda {where}."}),
            Map.entry("guide_stuck", new String[]{
                    "Por aca no paso. {place} queda {where}."}),
            Map.entry("guide_other_dimension", new String[]{
                    "{place} esta en otra dimension. Desde aca no te llevo."}),

            Map.entry("teleport_ack", new String[]{
                    "Ya voy.",
                    "Aca estoy. Me extranabas, se nota."}),
            Map.entry("teleport_arrived", new String[]{
                    "Llegue. Ni se te ocurra perderte de nuevo.",
                    "Aca estoy. Que harias sin mi."}),
            Map.entry("teleport_no_spot", new String[]{
                    "No hay donde pararme al lado tuyo. Apenas pises firme, aparezco."}),

            Map.entry("breed_ack", new String[]{
                    "A ver si hay amor en el aire.",
                    "Dale, les doy de comer. Despues no me culpes si se llena de crias."}),
            Map.entry("breed_done", new String[]{
                    "Listo, {count} parejitas felices. Soy un casamentero.",
                    "Ya estan enamorados. Vienen crias."}),
            Map.entry("breed_failed", new String[]{
                    "No se dejaron. Animales maleducados."}),
            Map.entry("breed_no_animals", new String[]{
                    "No veo ningun par de animales listos para criar por aca."}),
            Map.entry("breed_no_pair", new String[]{
                    "No hay dos {animal} adultos y listos por aca."}),
            Map.entry("breed_need_food", new String[]{
                    "Para criarlos necesito {food}. Pasame un poco."}),

            Map.entry("fish_ack", new String[]{
                    "A pescar. Paciencia, que eso a mi me sobra."}),
            Map.entry("fish_need_rod", new String[]{
                    "Sin cana no pesco. Dame una o los materiales: 3 palos y 2 hilos."}),
            Map.entry("fish_no_water", new String[]{
                    "No hay agua cerca para pescar."}),
            Map.entry("fish_cant_reach", new String[]{
                    "No llego al agua desde aca."}),
            Map.entry("fish_nothing", new String[]{
                    "Nada. Los peces se escondieron de mi."}),
            Map.entry("fish_done", new String[]{
                    "Pesque {items}. Lo tengo en mi tapa.",
                    "Mira lo que saque: {items}. Pescador profesional."}),

            Map.entry("trade_ack", new String[]{
                    "Dejame a mi, que yo regateo mejor.",
                    "Voy a negociar con el aldeano."}),
            Map.entry("trade_done", new String[]{
                    "Trato hecho: {items}.",
                    "Listo, consegui {items}. Y ni pestaneo el aldeano."}),
            Map.entry("trade_failed", new String[]{
                    "El aldeano no quiso. Cosa de el."}),
            Map.entry("trade_nobody_sells", new String[]{
                    "Ningun aldeano de aca vende {item}."}),
            Map.entry("trade_nobody_buys", new String[]{
                    "Ningun aldeano de aca compra {item}."}),
            Map.entry("trade_cant_pay", new String[]{
                    "Para {item} me piden {price}, y no tengo. Pasame eso."}),

            Map.entry("sort_ack", new String[]{
                    "A ordenar. Que desastre tenias.",
                    "Dejame a mi, que lo ordeno como corresponde."}),
            Map.entry("sort_done", new String[]{
                    "Listo, ordene {count} cofres. Cada cosa en su lugar.",
                    "{count} cofres ordenados. Ahora si da gusto."}),
            Map.entry("sort_done_one", new String[]{
                    "Listo, ese cofre quedo ordenado."}),
            Map.entry("sort_none", new String[]{
                    "No veo cofres por aca para ordenar."}),
            Map.entry("sort_failed", new String[]{
                    "No pude ordenarlos. No toque nada, tranqui."})
    );

    private OfflineRocco() {}
}

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
    private static final Pattern STOP = Pattern.compile("\\b(basta|stop|cancela|para de|deja de|para ya)\\b|^\\s*(para|parar|frena|frenar)\\s*$");
    // Sin "aca" ni "vamos": "que haces aca?" o "vamos a plantar" no son una orden de seguir.
    private static final Pattern FOLLOW = Pattern.compile("\\b(seguime|segui|sigueme|veni|veni aca|ven aqui|ven aca|ven conmigo|follow|follow me|come here)\\b");
    private static final Pattern PLANT = Pattern.compile("\\b(planta|plantar|plantame|plant|sembra|sembrar|siembra)\\w*");
    private static final Pattern HARVEST = Pattern.compile("\\b(cosecha|cosechar|harvest|recolecta|arranca)\\w*");
    private static final Pattern OPEN = Pattern.compile("\\b(abri|abrir|abrime|cerra|cerrar|open|close)\\b.*\\b(puerta|trampilla|porton|door|trapdoor|gate)\\b"
            + "|\\b(puerta|trampilla|porton|door)\\b.*\\b(abri|abrir|cerra|open|close)\\b");
    private static final Pattern EAT = Pattern.compile("\\b(come|comer|comete|comelo|comela|trag\\w*|eat)\\b(.*)");
    // Sin "sentado" ni "dormido": "estas sentado, levantate" pide lo contrario.
    private static final Pattern SIT = Pattern.compile("\\b(senta|sentate|sentarte|sentar|sentarse|sentame|sientate|sit|sit down)\\b");
    private static final Pattern SLEEP = Pattern.compile("\\b(dormi|dormite|dormir|dormirte|duermete|duerme|siesta|a mimir|sleep|nap)\\b");
    private static final Pattern WAKE = Pattern.compile("\\b(levanta\\w*|desperta\\w*|despierta\\w*|wake up|arriba)\\b");
    private static final Pattern WAIT = Pattern.compile("\\b(espera\\w*|quedate|quieto|stay|wait)\\b");

    // Trabajos. El argumento es el resto de la frase: RoccoActions lo traduce a items.
    private static final Pattern TEND = Pattern.compile("\\b(cuida|cuidar|cuidame|cuidando|atende|atender)\\b.*\\b(huerta|huertas|cultivos?|campo|plantacion|cosechas?)\\b");
    // "Quedate cuidando la casa", "cuida todo", "cuida la granja": la huerta y los animales.
    private static final Pattern TEND_HOME = Pattern.compile("\\b(cuida|cuidar|cuidame|cuidando|atende|atender|vigila\\w*)\\b.*\\b(casa|base|todo|granja|chacra|estancia)\\b"
            + "|\\b(cuida|cuidar|cuidando|atende|atender)\\b.*\\b(huerta|cultivos?|cosechas?)\\b.*\\b(animales|bichos)\\b"
            + "|\\b(cuida|cuidar|cuidando|atende|atender)\\b.*\\b(animales|bichos)\\b.*\\b(huerta|cultivos?|cosechas?)\\b");
    private static final Pattern TEND_ANIMALS = Pattern.compile("\\b(cuida|cuidar|cuidame|cuidando|atende|atender)\\b.*\\b(animales|bichos|vacas|ovejas|gallinas|pollos|cerdos|chanchos|conejos|cabras|corral|corrales|establo)\\b");
    // "Esquila las ovejas", "cortales la lana", "sacales la lana a las ovejas".
    private static final Pattern SHEAR = Pattern.compile("\\b(esquila\\w*|trasquila\\w*)\\b|\\b(corta\\w*|saca\\w*)\\b.*\\blana\\b");
    // "Dame una Roco Cola": te la da a vos (tomarsela el es "tomate una").
    private static final Pattern COLA_GIVE = Pattern.compile("\\b(dame|damela|pasame|pasamela|traeme|convidame|regalame|invitame|quiero|queres darme)\\b.*\\b(roco ?cola|coca ?cola|cocacola|coca|colita)\\b");
    private static final Pattern BACKPACK_OFF = Pattern.compile("\\b(saca\\w*|quita\\w*|desequipa\\w*)\\b.*\\bmochila\\b");
    private static final Pattern WOLF_UP = Pattern.compile("\\b(subi\\w*|sube\\w*|monta\\w*)\\b.*\\b(lobo|lobito|perro|perrito)\\b");
    private static final Pattern RIDE_DOWN = Pattern.compile("\\bbajate\\b|\\bbaja\\w*\\b.*\\b(lobo|lobito|perro|perrito|caballo|burro|mula)\\b");
    // "Alimenta a las vacas", "dales de comer a los animales": criar (comen de a pares y tienen crias).
    private static final Pattern FEED = Pattern.compile("\\b(alimenta\\w*)\\b(.*)|\\b(dales?|darles?|dale) de comer a (las |los |mis )?(.+)");
    private static final Pattern HARVEST_CHEST = Pattern.compile("\\bcofre\\b.*\\bcosecha\\b|\\bcosecha\\b.*\\b(este|ese|aca|ahi) cofre\\b");
    private static final Pattern RECIPE = Pattern.compile("\\breceta (de |del |para )?(.+)|\\bque (necesito|hace falta|se necesita) para (hacer |craftear |fabricar )?(.+)"
            + "|\\bcomo (se hace|se craftea|hago|crafteo|fabrico) (un |una |el |la )?(.+)");
    private static final Pattern SEARCH = Pattern.compile("\\b(busca\\w*|fijate|mira|revisa)\\b(.*)\\b(cofres?|baul|barril)\\b");
    private static final Pattern TILL = Pattern.compile("\\b(ara|arar|arame|labra|labrar)\\b|\\bprepara(me)? la tierra\\b");
    private static final Pattern CHOP = Pattern.compile("\\b(tala\\w*|corta\\w*|hacha\\w*)\\b.*\\b(arbol\\w*|tronco\\w*|madera)\\b|\\btala(lo|me)?\\b");
    private static final Pattern LIGHT = Pattern.compile("\\b(ilumina\\w*|alumbra\\w*)\\b|\\bpone(me)? antorchas\\b");
    private static final Pattern COOK = Pattern.compile("\\b(cocina\\w*|asa|asar|asame|asalo|asala|hornea\\w*|funde|fundi|fundir|derreti\\w*)\\b(.*)");
    private static final Pattern CRAFT = Pattern.compile("\\b(craftea\\w*|craftear|fabrica\\w*|haceme|hace|hacete|haz|hazme|arma|armame|construi|construime|construye|construyeme)\\b(.*)");
    // "Donde guardaste?", "donde dejaste la cosecha?", "donde esta lo que cosechaste?".
    private static final Pattern WHERE_STORED = Pattern.compile("\\bdonde\\b.*\\b(guardaste|dejaste|pusiste|metiste)\\b"
            + "|\\bdonde (esta|estan|quedo|quedaron) (la cosecha|el trigo|las cosas|lo que (cosechaste|juntaste|guardaste))\\b");
    // Una pregunta por lo que ya hizo: el verbo en pasado ("guardaste", "hiciste") al principio.
    private static final Pattern PAST_QUESTION = Pattern.compile("^((donde|que|cuando|como|por que|cuanto|cuantos|cuantas|ya|y|che|vos)\\s+)*"
            + "((lo|la|los|las|le|les|me|te|se)\\s+)?[a-z]+(aste|iste|asteis|isteis)\\b");
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
    private static final Pattern ROCCO_DEATH = Pattern.compile("\\b(si te moris|perdes tus cosas|perdes las cosas|que pasa si te moris|si te mueres|pierdes tus cosas)\\b");
    private static final Pattern ROCCO_FOOD = Pattern.compile("\\b(que comes|tenes hambre|no comes|como te alimentas|que te gusta comer|comes algo)\\b");
    private static final Pattern DEATH = Pattern.compile("\\b(donde (me )?mori|donde quede|mis cosas|mis items|where did i die|donde fue que mori)\\b");
    private static final Pattern WHO = Pattern.compile("\\b(quien sos|que sos|como te llamas|who are you|what are you|de donde saliste)\\b");
    private static final Pattern HELP = Pattern.compile("\\b(ayuda|help|que podes hacer|que sabes hacer|comandos)\\b");
    private static final Pattern THANKS = Pattern.compile("\\b(gracias|thanks|genio|crack|groso|capo|bien hecho|te quiero|sos lo mas)\\b");
    private static final Pattern GREETING = Pattern.compile("\\b(hola|buenas|hey|hi|hello|que tal|como estas|como va|ey)\\b");
    private static final Pattern GOODNIGHT = Pattern.compile("\\b(buenas noches|hasta manana|que descanses|que duermas bien)\\b");
    private static final Pattern GOODMORNING = Pattern.compile("\\b(buen dia|buenos dias|buenas tardes)\\b");
    private static final Pattern FAREWELL = Pattern.compile("\\b(chau|chao|adios|nos vemos|hasta luego|me voy|bye|me desconecto|me tengo que ir)\\b");
    private static final Pattern HOW_ARE_YOU = Pattern.compile("\\b(como estas|como andas|como te va|como te sentis|todo bien|que onda|como va)\\b");
    private static final Pattern WHAT_DOING = Pattern.compile("\\b(que haces|que estas haciendo|en que andas)\\b");
    private static final Pattern JOKE = Pattern.compile("\\b(chiste|algo gracioso|hace(me)? reir)\\b");
    private static final Pattern FACT = Pattern.compile("\\b(contame algo|un dato|dato curioso|curiosidad|sabias que|ensename|un tip|un consejo|dame un consejo)\\b");
    private static final Pattern BORED_PLAYER = Pattern.compile("\\b(me aburro|estoy aburrid\\w*|que hago|que hacemos|no se que hacer|aburrido)\\b");
    private static final Pattern HUNGRY = Pattern.compile("\\b(tengo hambre|me muero de hambre|hambre)\\b");
    private static final Pattern TIRED = Pattern.compile("\\b(estoy cansad\\w*|tengo sueno|me caigo de sueno|agotad\\w*)\\b");
    private static final Pattern PLAYER_SAD = Pattern.compile("\\b(estoy triste|me siento mal|tuve un mal dia|estoy mal|triste|bajon)\\b");
    private static final Pattern PLAYER_HAPPY = Pattern.compile("\\b(estoy feliz|estoy contento|que buen dia|que lindo dia|re feliz)\\b");
    private static final Pattern PLAYER_SCARED = Pattern.compile("\\b(tengo miedo|que miedo|me da miedo|socorro|auxilio|ayudame)\\b");
    private static final Pattern COMPLIMENT = Pattern.compile(
            "\\b(sos|estas) (re |muy |tan |un |el )?(lindo|tierno|hermoso|genial|amor|divino|bonito|mejor|facherito)\\b"
            + "|\\bque (lindo|tierno|hermoso) (sos|que sos)\\b|\\bque tierno\\b|\\bte amo\\b|\\bque facha\\b");
    private static final Pattern SORRY = Pattern.compile("\\b(perdon|perdoname|disculpa\\w*|lo siento|sorry)\\b");
    private static final Pattern LAUGH = Pattern.compile("\\b(ja(ja)+|je(je)+|js(js)+|xd+|lol|lmao)\\b");
    private static final Pattern SHUSH = Pattern.compile("\\b(callate|calla|silencio|shh+|no hables|deja de hablar|hablas mucho)\\b");
    private static final Pattern CHATTY = Pattern.compile("\\b(habla mas|deci algo|di algo|estas muy callado|estas callado)\\b");
    private static final Pattern AGREE = Pattern.compile("^(si+|dale|ok|okey|oka|bueno|listo|joya|de una|obvio|claro)[.! ]*$");
    private static final Pattern DISAGREE = Pattern.compile("^(no|nop|nah|nunca|para nada|ni loco)[.! ]*$");
    private static final Pattern ORIGIN = Pattern.compile("\\b(cuantos anos tenes|que edad tenes|de donde sos|madre tierra|madre naturaleza|como naciste|quien te creo|quien te hizo)\\b");
    private static final Pattern LIKES = Pattern.compile("\\b(que te gusta|te gusta|tu favorit\\w*|lo que mas te gusta|que odias|que no te gusta)\\b");
    private static final Pattern MATE_TALK = Pattern.compile("\\bmate(s|cito)?\\b");
    private static final Pattern MOBS = Pattern.compile("\\b(creeper\\w*|zombi\\w*|esqueleto\\w*|enderman|aranas?|bruja\\w*|phantom\\w*|monstruos?|bichos)\\b");
    private static final Pattern ORES = Pattern.compile("\\b(diamante\\w*|minerales|esmeralda\\w*|netherita\\w*)\\b|\\bvamos a minar\\b");
    private static final Pattern DIMENSIONS = Pattern.compile("\\b(nether|el end|dragon|wither|portal)\\b");
    private static final Pattern WEATHER = Pattern.compile("\\b(llueve|lluvia|tormenta|el clima|esta lloviendo|truenos?)\\b");
    private static final Pattern TIME = Pattern.compile("\\b(que hora|hora es|es de noche|es de dia|se hace de noche)\\b");
    private static final Pattern ROCCO_OK = Pattern.compile("\\b(estas bien|te duele|te lastime|te pegue|te hice mal)\\b");

    public static Answer reply(RoccoEntity rocco, String message)
    {
        String text = normalize(message);

        // La musica va primero: "para la musica" no es "para" (dejar de hacer algo), y un link
        // tiene que llegar entero.
        Answer music = music(message, text);
        if (music != null)
        {
            return music;
        }

        // "Donde guardaste las cosas?": lo dice con el lugar.
        if (WHERE_STORED.matcher(text).find())
        {
            return storedWhere(rocco);
        }

        // Primero las ordenes: son lo que el jugador espera que funcione si o si. Una pregunta por
        // algo que ya hizo ("cosechaste?", "que hiciste?") no es una orden, aunque tenga el verbo.
        Answer order = PAST_QUESTION.matcher(text).find() ? null : order(rocco, text);
        if (order != null)
        {
            return order;
        }

        // Despues, la charla. De lo mas puntual a lo mas general.
        if (ROCCO_DEATH.matcher(text).find())
        {
            return say(rocco != null && rocco.life().hasEnderPocket() ? "death_info_safe" : "death_info", RoccoExpression.SERIOUS);
        }
        if (ROCCO_FOOD.matcher(text).find())
        {
            return say("photosynthesis", RoccoExpression.SMUG);
        }
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
        if (SORRY.matcher(text).find())
        {
            return say("sorry", RoccoExpression.SMUG);
        }
        if (ROCCO_OK.matcher(text).find())
        {
            return say("rocco_ok", RoccoExpression.HAPPY);
        }
        if (SHUSH.matcher(text).find())
        {
            return say("shush", RoccoExpression.BORED);
        }
        if (CHATTY.matcher(text).find())
        {
            return say("chatty", RoccoExpression.HAPPY);
        }
        if (WHO.matcher(text).find())
        {
            return say("who", RoccoExpression.SMUG);
        }
        if (ORIGIN.matcher(text).find())
        {
            return say("origin", RoccoExpression.SMUG);
        }
        if (HELP.matcher(text).find())
        {
            return say("help", RoccoExpression.SERIOUS);
        }
        if (COMPLIMENT.matcher(text).find())
        {
            return say("compliment", RoccoExpression.CHEER);
        }
        if (THANKS.matcher(text).find())
        {
            return say("thanks", RoccoExpression.SMUG);
        }
        if (PLAYER_SCARED.matcher(text).find())
        {
            return say("player_scared", RoccoExpression.SERIOUS);
        }
        if (PLAYER_SAD.matcher(text).find())
        {
            return say("player_sad", RoccoExpression.SAD);
        }
        if (PLAYER_HAPPY.matcher(text).find())
        {
            return say("player_happy", RoccoExpression.CHEER);
        }
        if (TIRED.matcher(text).find())
        {
            return say("player_tired", RoccoExpression.BORED);
        }
        if (HUNGRY.matcher(text).find())
        {
            return say("player_hungry", RoccoExpression.SERIOUS);
        }
        if (BORED_PLAYER.matcher(text).find())
        {
            return say("player_bored", RoccoExpression.SMUG);
        }
        if (JOKE.matcher(text).find())
        {
            return say("joke", RoccoExpression.SMUG);
        }
        if (FACT.matcher(text).find())
        {
            return say("fact", RoccoExpression.HAPPY);
        }
        if (WHAT_DOING.matcher(text).find())
        {
            return say(rocco != null && rocco.currentErrand() != null ? "what_doing_busy" : "what_doing", RoccoExpression.SMUG);
        }
        if (TIME.matcher(text).find())
        {
            boolean night = rocco != null && rocco.level().isNight();
            return say(night ? "time_night" : "time_day", RoccoExpression.HAPPY);
        }
        if (WEATHER.matcher(text).find())
        {
            return say("weather_talk", RoccoExpression.BORED);
        }
        if (MOBS.matcher(text).find())
        {
            return say("mobs_talk", RoccoExpression.SERIOUS);
        }
        if (ORES.matcher(text).find())
        {
            return say("ores_talk", RoccoExpression.SMUG);
        }
        if (DIMENSIONS.matcher(text).find())
        {
            return say("dimension_talk", RoccoExpression.SERIOUS);
        }
        if (MATE_TALK.matcher(text).find())
        {
            return say("mate_talk", RoccoExpression.HAPPY);
        }
        if (LIKES.matcher(text).find())
        {
            return say("likes", RoccoExpression.HAPPY);
        }
        if (GOODNIGHT.matcher(text).find())
        {
            return say("goodnight", RoccoExpression.HAPPY);
        }
        if (GOODMORNING.matcher(text).find())
        {
            return say("goodmorning", RoccoExpression.HAPPY);
        }
        if (FAREWELL.matcher(text).find())
        {
            return say("farewell", RoccoExpression.SAD);
        }
        if (HOW_ARE_YOU.matcher(text).find())
        {
            return say("how_are_you", RoccoExpression.HAPPY);
        }
        if (GREETING.matcher(text).find())
        {
            return say("greeting", RoccoExpression.HAPPY);
        }
        if (LAUGH.matcher(text).find())
        {
            return say("laugh", RoccoExpression.HAPPY);
        }
        if (AGREE.matcher(text).find())
        {
            return say("agree", RoccoExpression.HAPPY);
        }
        if (DISAGREE.matcher(text).find())
        {
            return say("disagree", RoccoExpression.BORED);
        }
        if (text.contains("?"))
        {
            return say("question", RoccoExpression.SMUG);
        }
        return say("filler", RoccoExpression.BORED);
    }

    // ---------- ahorro de IA: ordenes que se resuelven solas ----------

    // La frase tiene que empezar con la orden ("crafteame un pico", "porfa segui", "podes traerme
    // carbon"), no tenerla perdida en el medio: "me encanta como cocinas" no es una orden de cocinar.
    private static final Pattern LEADING_FILLER = Pattern.compile(
            "^((che|dale(?! de comer)|porfa|por favor|bueno|ahora|ya|y|ey|oye|eh|a ver|tipo|mira),?\\s+)+");
    private static final Pattern COMMAND_START = Pattern.compile("^(no me sigas|deja de|para|basta|stop|cancela\\w*"
            + "|segui\\w*|sigueme|veni\\w*|ven|vente|espera\\w*|quedate|quieto|senta\\w*|sientate|dormi\\w*|duerme\\w*|a mimir"
            + "|levanta\\w*|desperta\\w*|despierta\\w*|planta\\w*|sembra\\w*|siembra\\w*|cosecha\\w*|abri\\w*|cerra\\w*"
            + "|come\\w*|trag\\w*|craftea\\w*|fabrica\\w*|hace\\w*|haz\\w*|arma\\w*|construi\\w*|construye\\w*|cocina\\w*"
            + "|asa\\w*|hornea\\w*|funde|fundi\\w*|derreti\\w*|guarda\\w*|deja\\w*|saca\\w*|agarra\\w*|rompe\\w*|quita\\w*"
            + "|pone\\w*|pon|coloca\\w*|trae\\w*|alcanza\\w*|dame\\w*|pasame\\w*|tira\\w*|suelta\\w*|larga\\w*|junta\\w*"
            + "|recoge\\w*|consegui\\w*|consigue\\w*|busca\\w*|fijate|mina\\w*|pica\\w*|tala\\w*|corta\\w*|ilumina\\w*"
            + "|esquila\\w*|trasquila\\w*|alimenta\\w*|vigila\\w*|dales? de comer|darles? de comer|dale de comer"
            + "|convidame|regalame|invitame|quiero|subi\\w*|sube\\w*|monta\\w*|bajate|baja\\w*"
            + "|alumbra\\w*|ara\\w*|labra\\w*|prepara\\w*|cuida\\w*|atende\\w*|encanta\\w*|hechiza\\w*|toma\\w*|ceba\\w*|equipa\\w*|desequipa\\w*|bebe\\w*|ataca\\w*|mata\\w*|pegale\\w*|dispara\\w*|defende\\w*|usa\\w*"
            + "|teletransport\\w*|tepea\\w*|tpea\\w*|aparece\\w*|llevame|guiame|acompaname|cria\\w*|pesca\\w*|compra\\w*"
            + "|vende\\w*|ordena\\w*|acomoda\\w*|organiza\\w*|baila\\w*|bailemos|aplaud\\w*|festej\\w*|celebra\\w*"
            + "|anda|andate|podes|podrias|puedes|me podes|me (hace|haces|trae|traes|das|pasa|pasas|craftea|crafteas|cocina"
            + "|cocinas|guarda|guardas|busca|buscas|consigue|conseguis)\\w*|follow|come here|stay|wait|sit|sleep|wake up)\\b");
    // Preguntas sobre si sabe o puede: eso es charla, no una orden ("sabes cocinar?").
    private static final Pattern ABILITY_QUESTION = Pattern.compile("^(sabes|sabias|como|que|por que|cuando|donde|cual|te gusta)\\b");

    // Una orden corta y clara que Rocco hace solo, sin preguntarle a la IA. null si conviene que la
    // conteste la IA (charla, preguntas, frases largas o con matices).
    public static Answer localOrder(RoccoEntity rocco, String message)
    {
        // Un link o una orden de musica: la IA no puede abrir links, asi que la resuelve Rocco.
        Answer music = music(message, normalize(message));
        if (music != null)
        {
            return music;
        }
        String text = LEADING_FILLER.matcher(normalize(message)).replaceFirst("").trim();
        if (text.isEmpty() || text.split("\\s+").length > 10 || ABILITY_QUESTION.matcher(text).find()
                || PAST_QUESTION.matcher(text).find()
                || !COMMAND_START.matcher(text).find())
        {
            return null;
        }
        Answer answer = reply(rocco, text);
        return answer.verb() == null ? null : answer;
    }

    // ---------- lo que dice por su cuenta sin IA ----------

    private static final Map<java.util.UUID, String> LAST_SPONTANEOUS = new ConcurrentHashMap<>();

    // Que comentar segun lo que pasa: poca vida, hambre, bichos cerca, la noche, la lluvia, una
    // cueva, el agua, el jugador quieto... o algo que se le ocurra. Nunca dos veces seguidas lo mismo.
    public static String spontaneous(RoccoEntity rocco, net.minecraft.server.level.ServerPlayer owner,
                                     net.minecraft.util.RandomSource random)
    {
        java.util.List<String> pool = new java.util.ArrayList<>();
        net.minecraft.world.level.Level level = owner.level();
        boolean sky = level.canSeeSky(owner.blockPosition());
        if (owner.getHealth() < 7.0F)
        {
            add(pool, "spont_low_health", 4);
        }
        if (owner.getFoodData().getFoodLevel() < 6)
        {
            add(pool, "spont_hungry", 3);
        }
        if (!level.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, owner.getBoundingBox().inflate(12.0D),
                net.minecraft.world.entity.LivingEntity::isAlive).isEmpty())
        {
            add(pool, "spont_danger", 3);
        }
        if (level.isNight() && sky)
        {
            add(pool, "spont_night", 2);
        }
        if (level.isRaining() && sky)
        {
            add(pool, "spont_rain", 2);
        }
        if (!sky && owner.getY() < 40.0D)
        {
            add(pool, "spont_cave", 2);
        }
        if (owner.isInWater())
        {
            add(pool, "spont_water", 2);
        }
        if (rocco.ownerStillTicks() > 3 * 1200)
        {
            add(pool, "spont_idle_player", 2);
        }
        add(pool, "spont_musing", 3);
        add(pool, "spont_fact", 2);
        add(pool, "spont_question", 1);

        String last = LAST_SPONTANEOUS.get(owner.getUUID());
        if (pool.size() > 1 && last != null)
        {
            pool.removeIf(last::equals);
        }
        String chosen = pool.isEmpty() ? "spont_musing" : pool.get(random.nextInt(pool.size()));
        LAST_SPONTANEOUS.put(owner.getUUID(), chosen);
        return chosen;
    }

    private static void add(java.util.List<String> pool, String category, int weight)
    {
        for (int i = 0; i < weight; i++)
        {
            pool.add(category);
        }
    }

    // ---------- frases grabadas ----------

    private static Map<String, String> byText;

    // El codigo de una frase fija ("follow_2", la segunda de "follow"), para buscar su audio
    // grabado. null si no es una frase de la tabla o si tiene datos que cambian ({item}, {pos}).
    public static synchronized String lineId(String text)
    {
        if (byText == null)
        {
            Map<String, String> map = new java.util.HashMap<>();
            for (Map.Entry<String, String[]> entry : LINES.entrySet())
            {
                String[] lines = entry.getValue();
                for (int i = 0; i < lines.length; i++)
                {
                    if (!lines[i].contains("{"))
                    {
                        map.putIfAbsent(lines[i].trim(), entry.getKey() + "_" + (i + 1));
                    }
                }
            }
            byText = map;
        }
        return text == null ? null : byText.get(text.trim());
    }

    // Todas las frases, por categoria (para armar la lista de grabacion).
    public static Map<String, String[]> allLines()
    {
        return LINES;
    }

    private static final Pattern CALL = Pattern.compile("\\b(teletransport\\w*|tepea\\w*|tpea\\w*|aparece\\w*|vente|veni (aca|ya|para aca|rapido|al toque))\\b");
    private static final Pattern FAR_CALL = Pattern.compile("\\b(veni\\w*|vente|teletransport\\w*|tepea\\w*|tpea\\w*|aparece\\w*|seguime|donde estas|volve\\w*)\\b");
    // "Pasame un mate", "cebame uno", "tomemos unos mates": te ceba y te lo pasa a vos.
    private static final Pattern SHARE_MATE = Pattern.compile("\\b(dame|pasame|convidame|cebame|alcanzame|compartime|me das|me pasas|me convidas|me cebas"
            + "|me cebarias|me pasarias|tomemos|compartamos|matiemos)\\b(\\s+\\w+){0,3}?\\s+(mate|mates|matecito|matecitos|uno)\\b"
            + "|\\b(tomemos|compartamos) (unos )?mates\\b|\\bmate(amos|ar) juntos\\b");
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
    private static final Pattern DANCE = Pattern.compile("\\b(baila\\w*|bailemos|un bailecito|un baile)\\b");
    private static final Pattern CLAP = Pattern.compile("\\b(aplaud\\w*|unos aplausos)\\b");
    private static final Pattern CELEBRATE = Pattern.compile("\\b(festej\\w*|celebra\\w*)\\b");
    // Roco Cola (o una coca, que corrige): "tomate una coca", "te tomas una roco cola?".
    private static final Pattern COLA = Pattern.compile("\\b(roco ?cola|coca ?cola|cocacola|coca)\\b");
    private static final Pattern DRINK = Pattern.compile("\\b(toma|tomate|tomar|tomas|tomarte|tomemos|tomamos)\\b");
    private static final Pattern UNEQUIP = Pattern.compile("\\b(desequipa\\w*)\\b|\\b(sacate|quitate) (la |el |tu |todo |toda )?(armadura|casco|pechera|equipo|espada|escudo|todo)\\b");
    private static final Pattern EQUIP = Pattern.compile("\\b(equipa\\w*)\\b|\\bponete (la |el |tu |una |un )?(armadura|casco|pechera|equipo|espada|escudo|arma)\\b");
    // "Ataca al creeper", "disparale", "tirale flechas", "tira flechas", "usa el arco": pelear, no soltar.
    private static final Pattern ATTACK = Pattern.compile("\\b(ataca\\w*|atacalo|atacala|mata\\w*|pegale\\w*|disparale\\w*|dispara|disparar|tirale\\w*|flechal\\w*|defendeme)\\b(.*)"
            + "|\\b(tira\\w*|lanza\\w*) (unas |una |tus |las )?flechas?\\b(.*)|\\busa\\w* (el |la |tu )?(arco|ballesta|espada|hacha|tridente)\\b(.*)");
    private static final Pattern POTION = Pattern.compile("\\b(toma\\w*|bebe\\w*|usa\\w*)\\b.*\\bpocion\\w*\\b(.*)");
    private static final Pattern ENDER_CHEST = Pattern.compile("\\b(come\\w*|trag\\w*|absorb\\w*)\\b.*\\bcofre de(l)? ender\\b");
    private static final Pattern SORT = Pattern.compile("\\b(ordena\\w*|acomoda\\w*|organiza\\w*)\\b.*\\b(cofres?|baul\\w*|barril\\w*|cosas)\\b");

    // ---------- musica ----------

    private static final String MUSIC_WORD = "(musica|musiquita|cancion|canciones|tema|temazo|temita|cumbia|rock|reggaeton|trap|rap)";
    private static final Pattern MUSIC_STOP = Pattern.compile("\\b(para|pare|frena|corta|apaga|saca|quita|basta|deten)\\w*\\b.*\\b"
            + MUSIC_WORD + "\\b|\\b(basta de|sin) " + MUSIC_WORD + "\\b");
    private static final Pattern MUSIC_PAUSE = Pattern.compile("\\b(pausa\\w*|pone pausa|pone en pausa)\\b");
    private static final Pattern MUSIC_RESUME = Pattern.compile("\\b(reanuda\\w*|despausa\\w*|dale play|play)\\b"
            + "|\\b(segui|seguí|continua|volve a poner)\\w* (con )?(la |el )?" + MUSIC_WORD + "\\b");
    private static final Pattern MUSIC_VOLUME = Pattern.compile("\\b(subi\\w*|sube\\w*|baja\\w*|mas fuerte|mas bajo|mas despacio|mas alto)\\b"
            + ".*\\b(volumen|" + MUSIC_WORD.substring(1, MUSIC_WORD.length() - 1) + ")\\b|\\bvolumen\\b");
    private static final Pattern MUSIC_DOWN = Pattern.compile("\\b(baja\\w*|mas bajo|mas despacio|menos)\\b");
    // "Pone musica", "poneme la cancion despacito", "escuchemos algo": sin link, Rocco lo pide.
    private static final Pattern MUSIC_PLAY = Pattern.compile("\\b(pone\\w*|pon|reproduc\\w*|toca\\w*|escuchemos|mete\\w*|tirate\\w*|cantame|canta)\\b"
            + " (algo de |un poco de |la |el |una |un |unos |unas |tu |alguna )?" + MUSIC_WORD + "( de| que se llama| llamad[oa])?\\b(.*)");
    private static final Pattern MUSIC_VERB = Pattern.compile("\\b(pone\\w*|pon|reproduc\\w*|toca\\w*|escucha\\w*|mira|play|musica|cancion|tema)\\b");

    // null si no es nada de musica. 'raw' es lo que escribio (el link tiene que quedar entero).
    @org.jetbrains.annotations.Nullable
    private static Answer music(String raw, String text)
    {
        String url = com.cristoree.rocco.music.RoccoMusic.findUrl(raw);
        if (url != null)
        {
            // Un link solo, o con "pone esto" / "escucha esto": es para que lo ponga.
            String rest = text.replaceAll("https?://\\S+", " ").trim();
            if (rest.split("\\s+").length <= 5 || MUSIC_VERB.matcher(rest).find())
            {
                return new Answer("", RoccoExpression.HAPPY, "poner_musica", url);
            }
        }
        if (MUSIC_STOP.matcher(text).find())
        {
            return new Answer("", RoccoExpression.HAPPY, "parar_musica", "");
        }
        if (MUSIC_PAUSE.matcher(text).find())
        {
            return new Answer("", RoccoExpression.HAPPY, "pausar_musica", "");
        }
        if (MUSIC_RESUME.matcher(text).find())
        {
            return new Answer("", RoccoExpression.HAPPY, "seguir_musica", "");
        }
        if (MUSIC_VOLUME.matcher(text).find())
        {
            return new Answer("", RoccoExpression.HAPPY, "volumen", MUSIC_DOWN.matcher(text).find() ? "bajar" : "subir");
        }
        if (MUSIC_PLAY.matcher(text).find())
        {
            return new Answer("", RoccoExpression.HAPPY, "poner_musica", "");
        }
        return null;
    }

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
        if (COLA_GIVE.matcher(text).find())
        {
            return rocco == null || rocco.colaReady() ? act("cola_give", RoccoExpression.CHEER, "dar_cola", "")
                    : new Answer("", RoccoExpression.HAPPY, "dar_cola", "");
        }
        if (BACKPACK_OFF.matcher(text).find())
        {
            return new Answer("", RoccoExpression.HAPPY, "quitar_mochila", "");
        }
        if (WOLF_UP.matcher(text).find())
        {
            return new Answer("", RoccoExpression.CHEER, "montar_lobo", "");
        }
        if (RIDE_DOWN.matcher(text).find())
        {
            return new Answer("", RoccoExpression.HAPPY, "bajar", "");
        }
        // Cuidar la casa va antes que la huerta sola, y los animales antes que criar.
        if (TEND_HOME.matcher(text).find())
        {
            return act("home_ack", RoccoExpression.SMUG, "cuidar_casa", "");
        }
        if (TEND_ANIMALS.matcher(text).find())
        {
            return act("ranch_ack", RoccoExpression.HAPPY, "cuidar_animales", "");
        }
        if (SHEAR.matcher(text).find())
        {
            return act("shear_ack", RoccoExpression.HAPPY, "esquilar", "");
        }
        Matcher feed = FEED.matcher(text);
        if (feed.find())
        {
            String animal = feed.group(2) != null ? feed.group(2) : feed.group(5);
            return act("breed_ack", RoccoExpression.HAPPY, "criar", animal == null ? "" : animal.trim());
        }
        // Lo nuevo va antes que comer o poner: "comete ese cofre de ender", "ponete la armadura".
        if (COLA.matcher(text).find() && DRINK.matcher(text).find())
        {
            boolean coca = text.contains("coca") && !text.contains("roco");
            return new Answer("", RoccoExpression.HAPPY, "tomar_roco_cola", coca ? "coca" : "pedido");
        }
        if (ENDER_CHEST.matcher(text).find())
        {
            return new Answer("", RoccoExpression.WOW, "comer_cofre_ender", "");
        }
        Matcher potion = POTION.matcher(text);
        if (potion.find())
        {
            return act("potion_ack", RoccoExpression.HAPPY, "tomar_pocion", potion.group(2).trim());
        }
        Matcher attack = ATTACK.matcher(text);
        if (attack.find())
        {
            return act("attack_ack", RoccoExpression.ANGRY, "atacar", text);
        }
        if (UNEQUIP.matcher(text).find())
        {
            return new Answer("", RoccoExpression.HAPPY, "desequipar", "");
        }
        if (EQUIP.matcher(text).find())
        {
            return new Answer("", RoccoExpression.SMUG, "equipar", "");
        }
        // "Cebame uno" solo vale si ya estan tomando mate.
        if (SHARE_MATE.matcher(text).find() && (text.contains("mate") || (rocco != null && rocco.isSharingMate())))
        {
            return act("mate_share_ack", RoccoExpression.HAPPY, "cebar_mate", "");
        }
        if (MATE.matcher(text).find())
        {
            return act("mate_ack", RoccoExpression.HAPPY, "mate", "");
        }
        if (DANCE.matcher(text).find())
        {
            return act("dance_ack", RoccoExpression.HAPPY, "bailar", "");
        }
        if (CLAP.matcher(text).find())
        {
            return act("clap_ack", RoccoExpression.CHEER, "aplaudir", "");
        }
        if (CELEBRATE.matcher(text).find())
        {
            return act("celebrate_ack", RoccoExpression.CHEER, "festejar", "");
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

    // Las ordenes de la frase. null si no hay ninguna.
    @org.jetbrains.annotations.Nullable
    private static Answer order(RoccoEntity rocco, String text)
    {
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
        return null;
    }

    // Donde guardo lo ultimo que guardo en un cofre (lo recuerda la entidad).
    private static Answer storedWhere(RoccoEntity rocco)
    {
        RoccoEntity.Stored stored = rocco == null ? null : rocco.lastStored();
        if (stored == null)
        {
            return say("stored_where_none", RoccoExpression.HAPPY);
        }
        return new Answer(line("stored_where", "items", stored.items(), "where", rocco.describeStoredPlace(stored)),
                RoccoExpression.SMUG, null, null);
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
        switch (category)
        {
            case "cheer_small", "cheer_big", "react_fly", "react_boat" ->
            {
                return RoccoExpression.CHEER;
            }
            case "react_float", "react_dive", "spont_water", "spont_fact", "chatty" ->
            {
                return RoccoExpression.HAPPY;
            }
            case "spont_danger", "spont_low_health", "spont_cave", "low_health", "immortal_knock" ->
            {
                return RoccoExpression.SCARED;
            }
            case "combat_start", "combat_no_arrows", "combat_weapon_broke", "attack_ack" ->
            {
                return RoccoExpression.ANGRY;
            }
            case "combat_win", "armor_equip", "death_info_safe", "ender_already", "photosynthesis" ->
            {
                return RoccoExpression.SMUG;
            }
            case "music_start", "music_resume", "music_louder" ->
            {
                return RoccoExpression.CHEER;
            }
            case "music_fail", "music_site_fail", "music_page" ->
            {
                return RoccoExpression.SAD;
            }
            case "cola_request", "cola_after", "grave_back", "unequip_done" ->
            {
                return RoccoExpression.HAPPY;
            }
            case "cola_coca", "cola_cooldown", "cola_give_wait" ->
            {
                return RoccoExpression.SMUG;
            }
            case "ender_unlock", "ender_ack" ->
            {
                return RoccoExpression.WOW;
            }
            case "react_respawn", "left_behind" ->
            {
                return RoccoExpression.SAD;
            }
            case "spont_idle_player", "spont_rain", "celebrate_cant", "travel_busy" ->
            {
                return RoccoExpression.BORED;
            }
            case "spont_musing", "spont_question", "spont_night" ->
            {
                return RoccoExpression.SMUG;
            }
            default -> { }
        }
        if (category.endsWith("_done") || category.endsWith("_found_it") || category.endsWith("_got_it")
                || category.endsWith("_set") || category.endsWith("_arrived") || category.endsWith("_saved"))
        {
            return RoccoExpression.SMUG;
        }
        if (category.endsWith("_ack") || category.endsWith("_ok") || category.startsWith("craft_going")
                || category.equals("craft_fetching") || category.equals("craft_gathering")
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
    // Esa frase de la categoria: los regalos dicen las de una misma secuencia.
    public static String lineAt(String category, int index)
    {
        String[] lines = LINES.get(category);
        return lines == null || lines.length == 0 ? "..." : lines[Math.floorMod(index, lines.length)];
    }

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
                    "Planto, cosecho y replanto, crafteo, cocino, busco en los cofres, guardo, pongo y saco bloques, talo arboles, pongo antorchas y te sigo. Clic derecho y te abro mi tapa. Agachado y clic derecho, te abro mi menu.",
                    "Proba 'craftea un pico de piedra', 'cocina las papas', 'traeme carbon del cofre', 'guarda la cosecha en este cofre', 'cuida la huerta', 'ilumina' o 'tala un arbol'.",
                    "Tambien encanto tus cosas una vez por dia, me tomo unos mates, recuerdo lugares ('esta es mi casa', 'llevame a casa'), crio animales, pesco, comercio con aldeanos y ordeno tus cofres.",
                    "Si volas con elytra vuelo con vos, si nadas floto con mi patito, si buceas me pongo las antiparras y si vas en bote te sigo en mi barquito. Y si me pedis, bailo, aplaudo o festejo."}),
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
                    "Aca va. Si me faltan semillas, las busco en los cofres."}),
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
                    "Para {item} me faltan {missing}, y en los cofres no hay. Pasamelos y lo hago.",
                    "Me faltan {missing} para {item}. En los cofres no estan: tirame las cosas y arranco.",
                    "Sin {missing} no hay {item}, y en los cofres no queda. Me los das?"}),
            Map.entry("craft_fetching", new String[]{
                    "Para {item} me faltan {missing}, pero estan en los cofres. Voy a buscarlos y te lo hago.",
                    "Dale. Saco {missing} del cofre y te hago {item}.",
                    "{item}, sale. Busco {missing} en los cofres y vuelvo."}),
            Map.entry("craft_gathering", new String[]{
                    "Para {item} me faltan {missing}. Los consigo yo y te lo hago.",
                    "No hay {missing} en ningun lado, asi que voy a sacarlo de la naturaleza. Despues te hago {item}.",
                    "Dale. Junto {missing} por aca y te armo {item}."}),
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
                    "Los cofres de aca estan llenos: {items} me lo quedo yo. Pone otro cofre y lo guardo.",
                    "No entra mas nada en los cofres. {items} lo llevo en mi tapa hasta que pongas otro."}),
            Map.entry("deposit_no_chest", new String[]{
                    "No veo ningun cofre cerca. {items} lo llevo en mi tapa hasta que pongas uno."}),
            Map.entry("deposit_full", new String[]{
                    "El cofre se lleno. Lo que no entro lo tengo yo."}),
            Map.entry("deposit_done", new String[]{
                    "Guarde {items}.",
                    "Listo, {items} en el cofre. Ordenadito."}),
            Map.entry("stored_where", new String[]{
                    "Lo guarde {where}: {items}.",
                    "{where}. Ahi deje {items}, ordenadito."}),
            Map.entry("stored_where_none", new String[]{
                    "Todavia no guarde nada en ningun cofre. Lo que junto lo tengo en mi tapa."}),
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
            Map.entry("home_ack", new String[]{
                    "Me quedo cuidando la casa: la huerta, los animales, todo. Anda tranquilo a explorar.",
                    "Dale. Cosecho, les doy de comer a los animales y guardo todo en los cofres. Vos anda."}),
            Map.entry("ranch_ack", new String[]{
                    "Me quedo con los animales: comida, crias, lana y orden. Ni un pollo se me escapa.",
                    "Los animales quedan a mi cargo. Van a estar mejor atendidos que vos."}),
            Map.entry("shear_ack", new String[]{
                    "A esquilar. Las ovejas van a quedar fresquitas."}),
            Map.entry("shear_done", new String[]{
                    "Listo, esquile {count}. La lana la tengo yo: pedimela si la queres."}),
            Map.entry("shear_none", new String[]{
                    "No hay ovejas con lana por aca."}),
            Map.entry("home_need_shears", new String[]{
                    "Para esquilar me faltan tijeras. Dame unas, o dos lingotes de hierro y me las hago."}),
            Map.entry("home_cull_notice", new String[]{
                    "Hay demasiadas {animal}: mas de {cap}. Las que sobran las voy sacando de a poco, y lo que dejan va al cofre."}),
            Map.entry("home_report", new String[]{
                    "Volviste! Mientras no estabas {done}.",
                    "Te cuento: {done}. Todo en orden por aca."}),
            Map.entry("tend_ack", new String[]{
                    "Me quedo cuidando la huerta. Vos anda tranquilo.",
                    "La huerta queda en buenas manos. Las mejores."}),
            Map.entry("plant_no_farmland", new String[]{
                    "No hay tierra arada libre por aca. Dame una azada y la preparo yo."}),
            Map.entry("plant_no_soul_sand", new String[]{
                    "La verruga va en arena de almas, y aca no hay."}),
            Map.entry("plant_no_ground", new String[]{
                    "No hay lugar con tierra para plantar un arbol aca."}),
            Map.entry("plant_no_seeds", new String[]{
                    "No tengo {item} para plantar, ni encima ni en los cofres. Pasame unas y siembro.",
                    "Sin {item} no hay siembra. Soy un milagro, no un mago. Dame unas y listo."}),
            Map.entry("plant_seeds_ran_out", new String[]{
                    "Me quede sin {item}. Plante lo que pude.",
                    "Hasta ahi llegue: no me quedan mas {item}."}),
            Map.entry("farm_no_seeds", new String[]{
                    "Para replantar me faltan {item}. Dejame unas en un cofre y sigo.",
                    "Quedaron lugares vacios en la huerta: no tengo {item}. Tirame unas y las planto."}),
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
            Map.entry("chop_done_no_plant", new String[]{
                    "Tale el arbol ({count} troncos). Ahi no prende otro arbolito: plantalo vos en tierra, porfa."}),

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
            // Mate compartido: te ceba uno, te lo pasa y espera que se lo devuelvas.
            Map.entry("mate_share_ack", new String[]{
                    "Dale, te cebo uno. Del mio, que es el mejor.",
                    "Un mate para vos? Esperame que lo preparo.",
                    "Bueno, pero despues me lo devolves, eh."}),
            Map.entry("mate_share_offer", new String[]{
                    "Toma, esta justo.",
                    "Un matecito?",
                    "Aca tenes. Cuidado que quema."}),
            Map.entry("mate_share_thanks", new String[]{
                    "Gracias!",
                    "Gracias. Ahora me toca a mi.",
                    "Rico, no? Ahi te cebo otro."}),
            Map.entry("mate_share_alone", new String[]{
                    "Bueno... me lo tomo yo.",
                    "Nadie? Mejor, mas para mi.",
                    "Se me enfria. Me lo tomo yo y listo."}),
            Map.entry("mate_share_empty", new String[]{
                    "Ya esta, devolvemelo que te cebo otro.",
                    "Ese ya fue. Pasamelo, que lo vuelvo a llenar."}),
            Map.entry("mate_share_no_room", new String[]{
                    "No tenes ni un lugar libre. Hace espacio y te lo paso."}),
            Map.entry("mate_share_cant", new String[]{
                    "Ahora no puedo cebar. Dejame llegar a tierra firme.",
                    "Asi no se puede tomar mate. Despues, tranqui."}),
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
            Map.entry("teleport_leashed", new String[]{
                    "Estoy atado, genio. Desatame o usa /rocco llamar."}),

            // Roco Cola: siempre Roco Cola, nunca la otra.
            Map.entry("cola_request", new String[]{
                    "Una Roco Cola? Dale!",
                    "Uh, justo tenia ganas de una Roco Cola."}),
            Map.entry("cola_coca", new String[]{
                    "Coca Cola? Querras decir Roco Cola. Mira."}),
            Map.entry("cola_after", new String[]{
                    "Que rica! Bien fresquita.",
                    "Ahh... esto me cambio el dia."}),
            // La Roco Cola para el jugador, y lo que viene despues.
            Map.entry("cola_give", new String[]{
                    "Una Roco Cola bien helada, saliendo!",
                    "Toma, pero no te la tomes de un trago, eh?",
                    "La mejor bebida del mundo. La invente yo... bueno, casi."}),
            Map.entry("cola_give_wait", new String[]{
                    "Para, que se me tiene que enfriar otra."}),
            Map.entry("burp_react", new String[]{
                    "Alamaura, casi me dejas sordo. Todo eso lo tenias guardado?",
                    "Che, avisa antes... me volaron las hojitas.",
                    "Eso no fue un eructo. Fue un temporal.",
                    "Siguen mis hojitas en la cabeza? Decime que si."}),

            // Las mochilas, el lobo y el caballo.
            Map.entry("backpack_none", new String[]{
                    "No tengo ninguna mochila puesta."}),
            Map.entry("backpack_removed", new String[]{
                    "Toma, ahi esta. Con todo lo que tenia adentro."}),
            Map.entry("inventory_shrunk", new String[]{
                    "Se me achico la tapa: lo que no me entra te lo dejo aca. Una mochila me vendria barbara."}),
            Map.entry("wolf_none", new String[]{
                    "No hay ningun lobo tuyo cerca que me deje subir."}),
            Map.entry("wolf_ack", new String[]{
                    "Arre, lobito!"}),
            Map.entry("ride_down_none", new String[]{
                    "Si ya estoy en el piso."}),

            // Los regalos de amistad. Cada indice es una misma secuencia.
            Map.entry("gift_seed_start", new String[]{
                    "Ay... que es esto? Me hace cosquillas la cabeza.",
                    "Espera, espera... algo se mueve aca arriba.",
                    "Ay! Que fue eso?"}),
            Map.entry("gift_seed_give", new String[]{
                    "Mira, me crecio adentro. A mi no me sirve para nada, pero vos sos mi amigo. Toma, cuidala.",
                    "Una semillita. Salio de mi, asi que obvio que es especial. Te la regalo.",
                    "Bueno, no se de donde salio, pero si es mia es buena. Es tuya, amigo."}),
            Map.entry("gift_flower_1", new String[]{
                    "Te acordas cuando recien nos conociamos? No sabias ni para donde ir.",
                    "Che... sabes que estuve pensando? En todo lo que anduvimos.",
                    "Hoy es un dia lindo para decirte algo."}),
            Map.entry("gift_flower_2", new String[]{
                    "Pasamos un monton juntos... y en todo este tiempo algo me fue creciendo aca adentro, despacito.",
                    "Cada aventura me dejo algo. Y mira lo que salio de todo eso.",
                    "Desde que brotamos juntos en esta aventura, esto fue creciendo en mi sin que me diera cuenta."}),
            Map.entry("gift_flower_3", new String[]{
                    "Es para vos. Bueno... es de los dos. Gracias por dejarme acompañarte.",
                    "Obvio que sin mi no llegabas a ningun lado. Pero no lo hubiese querido con nadie mas. Toma.",
                    "Es una flor de la amistad. Nunca se marchita... como nosotros."}),

            Map.entry("cola_cooldown", new String[]{
                    "Recien me tome una, queres que explote?"}),

            // El poder del cofre de ender.
            Map.entry("ender_unlock", new String[]{
                    "Siento algo raro en la panza... ahora no pierdo mis cosas!"}),
            Map.entry("ender_already", new String[]{
                    "Ya tengo el poder del cofre de ender, guardalo para vos."}),
            Map.entry("ender_ack", new String[]{
                    "A ver que tiene de especial ese cofre..."}),
            Map.entry("ender_none", new String[]{
                    "No veo ningun cofre de ender cerca. Pone uno al lado mio y me lo como."}),
            Map.entry("death_info", new String[]{
                    "Si me muero, se me cae todo lo que llevo. Pero si me das un cofre de ender, o lo pones para que me lo coma, gano el poder de no perder nada, ni aunque me muera."}),
            Map.entry("death_info_safe", new String[]{
                    "Mis cosas estan a salvo: tengo el poder del cofre de ender."}),

            // Armadura y armas.
            Map.entry("armor_equip", new String[]{
                    "Mira que facha!",
                    "Me queda bien? Decime que si."}),
            Map.entry("equip_nothing", new String[]{
                    "No tengo nada mejor para ponerme. Dame algo y me lo pongo."}),
            Map.entry("unequip_done", new String[]{
                    "Listo, todo de vuelta a mi tapa."}),
            Map.entry("unequip_nothing", new String[]{
                    "No tengo nada puesto."}),

            // Musica con links.
            Map.entry("music_loading", new String[]{
                    "A ver que me pasaste...",
                    "Dame un segundo que lo busco."}),
            Map.entry("music_start", new String[]{
                    "Dale! Suena {title}.",
                    "Ahi va {title}. Buen gusto, eh.",
                    "{title}! Esta la canto yo."}),
            Map.entry("music_fail", new String[]{
                    "No pude abrir ese link. Seguro que es de una cancion?",
                    "Ese link no me da musica. Proba con otro."}),
            Map.entry("music_page", new String[]{
                    "Eso es una pagina, no la musica. Pasame el link del audio: el que termina en .mp3, .aac, .m3u o .pls."}),
            Map.entry("music_site_fail", new String[]{
                    "De ahi no puedo poner musica. Pasame el link de un MP3 o de una radio.",
                    "Esos links no los puedo abrir. Uno de un MP3 o de una radio si."}),
            Map.entry("music_need_link", new String[]{
                    "Pasame el link de la cancion y la pongo.",
                    "Que pongo? Pasame un link de un MP3 o de una radio."}),
            Map.entry("music_none", new String[]{
                    "No esta sonando nada."}),
            Map.entry("music_stop", new String[]{
                    "Listo, apagada.",
                    "Bueno, silencio."}),
            Map.entry("music_pause", new String[]{
                    "En pausa. Decime y sigo."}),
            Map.entry("music_resume", new String[]{
                    "Y seguimos!"}),
            Map.entry("music_end", new String[]{
                    "Que temazo. Pasame otra si queres.",
                    "Se termino. Otra?"}),
            Map.entry("music_off", new String[]{
                    "La musica esta apagada en la configuracion."}),
            Map.entry("music_louder", new String[]{
                    "Mas fuerte!"}),
            Map.entry("music_quieter", new String[]{
                    "Mas bajito."}),

            // Pelear porque se lo pediste.
            Map.entry("attack_ack", new String[]{
                    "Dejamelo a mi.",
                    "Voy. Que se vaya despidiendo."}),
            Map.entry("attack_none", new String[]{
                    "No veo a quien atacar. Senalamelo o decime quien."}),
            Map.entry("attack_owner", new String[]{
                    "A vos no te voy a pegar, por mas que me lo pidas."}),

            // Pociones.
            Map.entry("potion_ack", new String[]{
                    "Salud. Literalmente.",
                    "Glu glu. A ver que tal."}),
            Map.entry("potion_none", new String[]{
                    "No tengo ninguna pocion que sirva. Dame una y me la tomo."}),

            // Peleando.
            Map.entry("combat_start", new String[]{
                    "Nadie toca a mi amigo!",
                    "Alejate de el!"}),
            Map.entry("combat_win", new String[]{
                    "Toma! Y no vuelvas."}),
            Map.entry("combat_no_arrows", new String[]{
                    "No tengo flechas! Voy a las pinas."}),
            Map.entry("combat_weapon_broke", new String[]{
                    "Se me rompio el arma!"}),
            Map.entry("low_health", new String[]{
                    "Ay... me quedan pocas hojitas."}),
            Map.entry("immortal_knock", new String[]{
                    "Ay, ay... dame un segundito."}),

            // La vida: fotosintesis y la vuelta de la tumba.
            Map.entry("photosynthesis", new String[]{
                    "Me curo solo con un poquito de sol. Pero si me das algo rico, me curo mas rapido."}),
            Map.entry("grave_back", new String[]{
                    "Puaj! Tengo tierra hasta en las hojitas.",
                    "Volvi! Las plantas siempre rebrotan.",
                    "Nota mental: no volver a morirme.",
                    "Me extranaste? Yo extrane el sol."}),

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
                    "No pude ordenarlos. No toque nada, tranqui."}),

            // ---------- bailar, aplaudir, festejar ----------

            Map.entry("dance_ack", new String[]{
                    "Musica, maestro! Bueno, la tarareo yo.",
                    "A bailar! Mira y aprende estos pasos.",
                    "Un bailecito? Siempre."}),
            Map.entry("clap_ack", new String[]{
                    "Bravo! Bravisimo!",
                    "Aplausos para vos. Te los ganaste.",
                    "Eso merece un aplauso."}),
            Map.entry("celebrate_ack", new String[]{
                    "A festejar! Yupi!",
                    "Esto hay que celebrarlo como corresponde!",
                    "Fiesta! Confeti para todos!"}),
            Map.entry("celebrate_cant", new String[]{
                    "Ahora no puedo, estoy en pleno viaje. Despues festejamos.",
                    "Dejame llegar a tierra firme y festejamos."}),
            Map.entry("travel_busy", new String[]{
                    "Asi no puedo, estoy en el medio del viaje.",
                    "Aca no, que me caigo. Cuando lleguemos."}),
            Map.entry("cheer_small", new String[]{
                    "Bien ahi!",
                    "Eso! Asi se hace.",
                    "Muy bien. Algo aprendiste de mi.",
                    "Bravo! Te estas volviendo bueno."}),
            Map.entry("cheer_big", new String[]{
                    "Increible! Lo lograste!",
                    "Siii! Eso fue epico!",
                    "Que capo! Esto se lo cuento a todas las papas.",
                    "Lo hiciste! Bueno, lo hicimos."}),

            // ---------- acompañando: vuelo, agua, barco ----------

            Map.entry("left_behind", new String[]{
                    "Te fuiste muy lejos y deje lo que estaba haciendo. Ya voy.",
                    "Me dejaste solo! Dejo esto y te alcanzo."}),
            Map.entry("react_fly", new String[]{
                    "Alla vamos! Agarrate fuerte.",
                    "Volar es lo mio. Mira estas hojas!",
                    "Wiii! Esto es vida."}),
            Map.entry("react_float", new String[]{
                    "Agua! Menos mal que traje mi patito.",
                    "Al agua, patito!",
                    "Flotar es mi deporte favorito."}),
            Map.entry("react_dive", new String[]{
                    "Antiparras puestas. Vamos abajo!",
                    "A bucear! Yo respiro por el snorkel, vos arreglate."}),
            Map.entry("react_boat", new String[]{
                    "Yo tengo mi propio barquito. Mas lindo que el tuyo.",
                    "A remar! Te sigo de cerca.",
                    "Mi calabaza navega mejor que tu bote."}),
            Map.entry("react_respawn", new String[]{
                    "Te moriste... Pero tranqui, aca estoy. Vamos a buscar tus cosas.",
                    "Eso dolio. Te dije que tuvieras cuidado.",
                    "Volviste. Para la proxima, haceme caso."}),

            // ---------- charla sin IA ----------

            Map.entry("how_are_you", new String[]{
                    "Yo? Impecable, como siempre. Vos como andas?",
                    "Re bien. Brotado, regado y listo para salvarte la vida.",
                    "Tranqui. Un poco de sol, un poco de tierra, y soy feliz.",
                    "Bien, bien. Mejor que vos seguro, pero bien.",
                    "Todo joya por aca. Aunque un matecito no vendria mal."}),
            Map.entry("what_doing", new String[]{
                    "Aca, cuidandote. Es un trabajo de tiempo completo.",
                    "Nada, mirando como haces las cosas. Y tomando nota de los errores.",
                    "Esperando que me pidas algo. Dale, que me aburro."}),
            Map.entry("what_doing_busy", new String[]{
                    "Estoy en algo. Ya termino y soy todo tuyo.",
                    "Trabajando, como siempre. Alguien tiene que hacerlo.",
                    "Ocupadisimo. Pero para vos siempre tengo un ratito."}),
            Map.entry("joke", new String[]{
                    "Por que el creeper no tiene amigos? Porque cada vez que se emociona, explota.",
                    "Por que los esqueletos no se pelean entre ellos? Porque no tienen agallas.",
                    "Sabes por que el enderman se enoja si lo miras? Porque es muy timido.",
                    "Un zombie me pidio la receta del pan. Le dije que primero aprenda a no prenderse fuego.",
                    "Que es verde, explota y te arruina la casa? Exacto. Y no, no soy yo."}),
            Map.entry("fact", new String[]{
                    "Dato: con lluvia los peces pican mas rapido.",
                    "Los diamantes salen bien abajo, cerca de la capa menos 58. Lleva pico de hierro, por lo menos.",
                    "Si dormis en el Nether, la cama explota. No lo pruebes. O si, pero lejos mio.",
                    "Los gatos espantan a los creepers. Yo tambien, pero con mi carisma.",
                    "Con algo de oro puesto, los piglins no te atacan. Son medio fanaticos.",
                    "La miel cura el veneno. Y es rica. Dato de milagro.",
                    "Si cae una tormenta sobre un aldeano, se vuelve bruja. Mejor tenerlos bajo techo."}),
            Map.entry("player_bored", new String[]{
                    "Aburrido? Con todo el mundo por explorar? Vamos a buscar diamantes.",
                    "Podriamos hacer una huerta. Bah, la hago yo y vos miras, como siempre.",
                    "Un mate y una charla? O si queres, te bailo algo.",
                    "Construi algo lindo. Una casita para mi, por ejemplo. Con jardin.",
                    "Pescamos un rato? Es lo mas relajante que hay."}),
            Map.entry("player_hungry", new String[]{
                    "Comete algo, que con la panza vacia no le ganas ni a una gallina.",
                    "Si queres te cocino algo. Pasame carne cruda o papas.",
                    "Hambre? Hay pan, carne, papas... Mientras no me mires a mi, todo bien."}),
            Map.entry("player_tired", new String[]{
                    "Anda a dormir un rato. Yo cuido que no se te acerque nada.",
                    "Descansa, que manana seguimos. Yo me quedo de guardia.",
                    "Si estas cansado, una cama y listo. Hasta los milagros duermen."}),
            Map.entry("player_sad", new String[]{
                    "Ey, aca estoy. No estas solo, tenes al mejor companero del mundo.",
                    "Que paso? Contame. Y si no queres, nos quedamos tranquilos un rato.",
                    "Los dias feos pasan. Las papas tambien tienen dias malos y siguen brotando.",
                    "Veni que te cebo un mate. Eso arregla casi todo."}),
            Map.entry("player_happy", new String[]{
                    "Eso! Me encanta verte asi.",
                    "Que lindo. Se nota que tenerme cerca te hace bien.",
                    "Bien ahi! La buena onda se contagia."}),
            Map.entry("player_scared", new String[]{
                    "Tranqui, que estoy yo. Quedate cerca mio.",
                    "Respira. Si hay bichos, nos alejamos juntos. Yo te cubro.",
                    "No pasa nada. Pone antorchas y la mitad de los problemas desaparecen."}),
            Map.entry("compliment", new String[]{
                    "Ya lo se, pero gracias por decirlo.",
                    "Ay, basta. Bueno, no, segui.",
                    "Me vas a hacer sonrojar las hojitas.",
                    "Vos tampoco estas tan mal. Para ser humano."}),
            Map.entry("sorry", new String[]{
                    "Esta bien, te perdono. Pero lo anoto.",
                    "Tranqui, ya fue. Los milagros no guardamos rencor. Mucho.",
                    "Disculpas aceptadas. Hoy estoy generoso."}),
            Map.entry("rocco_ok", new String[]{
                    "Estoy bien. Soy un milagro, me curo solo.",
                    "Un raspon nada mas. La Madre Tierra me hizo resistente.",
                    "Si, si, tranqui. Pero no te acostumbres a pegarme, eh."}),
            Map.entry("shush", new String[]{
                    "Bueno, me callo un rato. Si me necesitas, aca estoy.",
                    "Ok, silencio. Igual vas a extranar mi voz.",
                    "Me callo. Pero que conste que tenia cosas interesantes para decir."}),
            Map.entry("chatty", new String[]{
                    "Ah, queres charla? Tengo para toda la tarde.",
                    "Por fin alguien que valora mi conversacion.",
                    "Dale, hablo mas. Despues no te quejes."}),
            Map.entry("origin", new String[]{
                    "Me hizo la Madre Tierra, con un poco de tierra, un poco de lluvia y mucho talento.",
                    "Edad? Brote hace poco, pero soy viejo de alma. Milenario, casi.",
                    "Soy de la tierra. Literalmente. Sali de un pozo al lado tuyo, te acordas?"}),
            Map.entry("likes", new String[]{
                    "Me gusta el mate, el sol de la manana y que me hagas caso.",
                    "Lo que mas me gusta es la tierra recien regada. Y ganarte las discusiones.",
                    "Odio que me digan papa. Y los creepers. En ese orden.",
                    "Me encantan las flores. Y bailar cuando nadie mira. Bah, cuando vos miras."}),
            Map.entry("mate_talk", new String[]{
                    "El mate es sagrado. Calentito y compartido.",
                    "Si queres, te cebo uno. Pedimelo y listo.",
                    "Sin mate no hay paraiso. Lo dijo alguien sabio. Yo, creo."}),
            Map.entry("mobs_talk", new String[]{
                    "Los creepers no avisan. Bueno, hacen ssss, pero ya es tarde.",
                    "A los zombies no les gusta el sol. A mi me encanta. Ganamos.",
                    "Al enderman no lo mires a los ojos. Es timido y se ofende.",
                    "Las aranas trepan paredes. De noche son un problema, de dia ni te miran."}),
            Map.entry("ores_talk", new String[]{
                    "Diamantes? Abajo, bien abajo. Y con pico de hierro, que si no se pierden.",
                    "Minar es lindo hasta que escuchas lava. Ahi, despacito.",
                    "La netherita es lo mejor que hay, pero hay que bajar al Nether. Yo te acompano."}),
            Map.entry("dimension_talk", new String[]{
                    "El Nether es caliente, feo y lleno de bichos raros. Ideal para un paseo, no?",
                    "Al End se va con ojos de ender. El dragon es grande, pero nosotros somos dos.",
                    "Al Wither ni lo invoques cerca de casa. Haceme caso."}),
            Map.entry("weather_talk", new String[]{
                    "La lluvia me encanta. Para las plantas, digo. A vos te moja.",
                    "Con tormenta salen mas bichos. Mejor nos quedamos bajo techo.",
                    "El clima? Ideal para que yo crezca. Para vos, ni idea."}),
            Map.entry("time_day", new String[]{
                    "Es de dia. Buen momento para explorar, sin bichos molestando.",
                    "Pleno dia. Aprovecha, que la noche llega rapido."}),
            Map.entry("time_night", new String[]{
                    "Es de noche. Ojo con los bichos, o mejor, anda a dormir.",
                    "Noche cerrada. Si salis, lleva antorchas. Y a mi, obvio."}),
            Map.entry("goodnight", new String[]{
                    "Buenas noches. Que suenes con milagros.",
                    "Que descanses. Yo cuido el fuerte.",
                    "Hasta manana. No te olvides de mi, eh."}),
            Map.entry("goodmorning", new String[]{
                    "Buen dia! Arriba, que el mundo no se explora solo.",
                    "Buenas! Hoy es un gran dia para hacerme caso.",
                    "Buen dia. Ya tome sol, ahora un matecito y arrancamos."}),
            Map.entry("farewell", new String[]{
                    "Chau. Te espero aca, como siempre.",
                    "Nos vemos. No tardes que me aburro.",
                    "Anda tranquilo. Cuando vuelvas, aca voy a estar."}),
            Map.entry("laugh", new String[]{
                    "Jaja, viste que soy gracioso?",
                    "Te reis, pero yo lo decia en serio.",
                    "Esa risa me la gane."}),
            Map.entry("agree", new String[]{
                    "Eso. Me gusta cuando estamos de acuerdo.",
                    "Dale, de una.",
                    "Perfecto. Ves que nos entendemos?"}),
            Map.entry("disagree", new String[]{
                    "Bueno, como quieras. Pero yo tenia razon.",
                    "No? Ok, ok. Despues no digas que no te avise.",
                    "Uh, que cortante. Bueno."}),

            // ---------- lo que dice por su cuenta sin IA ----------

            Map.entry("present_hours", new String[]{
                    "Volviste! Mientras no estabas di unas vueltas y te junte unas cositas.",
                    "Ah, aparecio. Me aburri tanto que sali a pasear, y mira lo que encontre.",
                    "Te fuiste un rato y no me quede de brazos cruzados. Mira todo lo que te traje."}),
            Map.entry("present_day", new String[]{
                    "Un dia entero sin vos! Por suerte me entretuve juntando cosas. Son todas tuyas.",
                    "Volviste! Pense que te habia comido un creeper. Mientras tanto te junte un monton de cosas.",
                    "Un dia afuera, eh. Yo trabajando, como siempre. Toma, te traje de todo."}),
            Map.entry("present_week", new String[]{
                    "UNA SEMANA! Pense que me habias abandonado. Igual te junte cosas buenisimas, por si volvias.",
                    "Siete dias. Siete. Recorri el mundo entero para no pensar en vos. Y mira todo lo que te traje.",
                    "Volviste! Ya estaba por salir a buscarte. Mientras no estabas junte lo mejor que encontre. Es todo tuyo."}),
            Map.entry("present_items", new String[]{
                    "Te lo deje en mi tapa: {items}. Abrila cuando quieras.",
                    "Te guarde {items} en mi tapa. De nada.",
                    "En mi tapa tenes {items}. No digas que nunca te doy nada."}),
            Map.entry("present_overflow", new String[]{
                    "No me entro todo en la tapa, asi que lo que sobra te lo tiro.",
                    "Mi tapa ya no da mas. El resto, agarralo vos."}),

            Map.entry("diary_drop", new String[]{
                    "Ay, se me cayo! No, no, devolvemelo. No lo leas!",
                    "Uy. Mi diario. Ni se te ocurra leerlo, eh!",
                    "NO. Eso es privado. Dame eso ya!"}),
            Map.entry("diary_picked", new String[]{
                    "Noooo! Bueno, ya fue. Leelo, pero no me juzgues.",
                    "Lo agarraste. Genial. Ahora vas a saber todo. Que verguenza.",
                    "Si lo lees, yo leo tus cofres. Estamos a mano."}),
            Map.entry("diary_picked_other", new String[]{
                    "Ey, eso es mio! Devolvelo, ladron de diarios!"}),
            Map.entry("diary_recovered", new String[]{
                    "Uf. Casi. Lo que hay ahi adentro queda entre mis hojas y yo.",
                    "Recuperado. Menos mal que sos lento."}),
            Map.entry("diary_returned", new String[]{
                    "Gracias por devolverlo. No lo leiste, no? No me contestes.",
                    "Mi diario! Gracias. Igual ya se que lo leiste."}),

            Map.entry("trinket_stick", new String[]{
                    "Mira lo que encontre! El palito perfecto. Es tuyo, cuidalo."}),
            Map.entry("trinket_stone", new String[]{
                    "Encontre una piedra con forma de corazon. Me hizo acordar a vos. Bah, mas o menos."}),
            Map.entry("trinket_flower", new String[]{
                    "Toma, una florcita. No es nada, eh. No te emociones."}),
            Map.entry("trinket_feather", new String[]{
                    "Esta pluma da suerte. Me lo dijo una gallina. Toma."}),
            Map.entry("trinket_drawing", new String[]{
                    "Te hice un dibujo. Sos vos. Se nota, no?"}),
            Map.entry("trinket_complaints", new String[]{
                    "Toma, te escribi algo. Leelo con atencion."}),
            Map.entry("trinket_bone", new String[]{
                    "Te traje un recuerdo de un esqueleto que conoci. Era buena gente."}),
            Map.entry("trinket_seeds", new String[]{
                    "Te regalo unas semillas magicas. Bueno, magicas no. Pero plantalas con fe."}),
            Map.entry("trinket_air", new String[]{
                    "Te junte aire puro del bosque en una botella. Respiralo de a poquito."}),

            Map.entry("joke_weed", new String[]{
                    "Toma, de la buena. Es un helecho, pero vos no le digas a nadie.",
                    "Aca tenes, cosecha propia. Bueno, es un helecho. Pero con mucho amor."}),
            Map.entry("joke_weed_chill", new String[]{
                    "Eso no tengo. Pero toma: yerba de la buena. Para el mate, que pensabas?",
                    "Marihuana no, pero yerba si. Toma, cebate unos mates."}),
            Map.entry("joke_money", new String[]{
                    "Plata no tengo, pero hice un billete. Vale por un abrazo.",
                    "Toma, un billete de Rocco. Los aldeanos no lo aceptan, pero yo si."}),
            Map.entry("joke_drink", new String[]{
                    "Toma, una birra. De abeja. Cero alcohol, puro amor.",
                    "Aca tenes, bien fria. Bueno, es miel. Salud!"}),
            Map.entry("joke_love", new String[]{
                    "No te consigo novia ni novio, pero toma esta flor. Dasela a quien te guste.",
                    "Una cita no te consigo. Pero con esta flor arrancas bien."}),
            Map.entry("joke_again", new String[]{
                    "Ya te di uno hace un rato. Que hiciste con el?",
                    "Otra vez? No soy un kiosco.",
                    "Ya te di. No abuses, que se me acaban."}),

            Map.entry("order_not_yours", new String[]{
                    "Eso solo me lo puede pedir mi dueño. Pero charlar, charlamos.",
                    "Las ordenes las da quien me cuida. Vos y yo podemos conversar, eso si."}),
            Map.entry("menu_not_yours", new String[]{
                    "Perdon, mi menu es para quien me cuida. Pero si queres, charlamos."}),
            Map.entry("menu_cant_open", new String[]{
                    "Ahora no puedo abrir la tapa. Dejame terminar lo que estoy haciendo."}),

            Map.entry("spont_low_health", new String[]{
                    "Ey, estas con poca vida. Comete algo y alejate de los bichos.",
                    "Ojo, que te veo medio muerto. Descansa un poco.",
                    "Tu vida esta bajita. No te hagas el heroe."}),
            Map.entry("spont_hungry", new String[]{
                    "Te escucho la panza desde aca. Comete algo.",
                    "Con hambre no se puede pelear. Pan, carne, lo que sea.",
                    "Che, comete algo antes de que te desmayes."}),
            Map.entry("spont_danger", new String[]{
                    "Hay bichos cerca. Ojo.",
                    "Siento algo feo por aca. Quedate atento.",
                    "Cuidado, que se acerca algo y no viene a saludar."}),
            Map.entry("spont_night", new String[]{
                    "Ya es de noche. Ojo, que salen los bichos.",
                    "Que linda la luna. Lastima los zombies.",
                    "Se hizo de noche. Una cama no vendria mal."}),
            Map.entry("spont_rain", new String[]{
                    "Llueve. Las plantas felices, vos mojado.",
                    "Que lluvia linda. Para mi, digo.",
                    "Con lluvia pican mas los peces. Dato."}),
            Map.entry("spont_cave", new String[]{
                    "Que oscuro esta aca. Pone antorchas, por favor.",
                    "No me gustan las cuevas. Pero por vos, lo que sea.",
                    "Escuchaste eso? Mejor no saber."}),
            Map.entry("spont_water", new String[]{
                    "El agua esta buenisima. Para flotar, digo.",
                    "Nadar un rato no viene mal. Y yo tengo patito."}),
            Map.entry("spont_idle_player", new String[]{
                    "Vas a estar ahi parado todo el dia?",
                    "Hace rato que no te moves. Estas bien?",
                    "Si te quedas quieto un rato mas, te planto."}),
            Map.entry("spont_musing", new String[]{
                    "A veces pienso que si fuera una papa comun, la vida seria mas facil. Pero mas aburrida.",
                    "Me pregunto si las vacas saben que son vacas.",
                    "Que lindo dia para ser un milagro.",
                    "Si alguna vez me pierdo, buscame donde haya sol.",
                    "Hoy me siento particularmente fotogenico.",
                    "Las abejas trabajan todo el dia. Yo te cuido todo el dia. Somos colegas."}),
            Map.entry("spont_fact", new String[]{
                    "Sabias que los ajolotes te ayudan a pelear bajo el agua?",
                    "Las tortugas siempre vuelven a la playa donde nacieron.",
                    "Con una calabaza en la cabeza, el enderman no se enoja si lo miras.",
                    "Con hielo y un bote se viaja rapidisimo.",
                    "Los zorros duermen de dia y se roban cosas de noche."}),
            Map.entry("spont_question", new String[]{
                    "Che, y que vamos a construir despues?",
                    "Cual es el plan para hoy?",
                    "Alguna vez pensaste en hacerme una casita?",
                    "Tenes una granja de papas? Pregunto por un amigo."})
    );

    private OfflineRocco() {}
}

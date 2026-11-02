package com.cristoree.rocco.ai;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.ai.personality.Moments;
import com.cristoree.rocco.ai.personality.PersonalityProfile;
import com.cristoree.rocco.ai.personality.PersonalityPromptBuilder;
import com.cristoree.rocco.ai.personality.PersonalityTexts;
import com.cristoree.rocco.ai.personality.Relationship;
import com.cristoree.rocco.ai.personality.Traits;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.config.RoccoConfig;
import com.cristoree.rocco.entity.RoccoExpression;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoState;
import com.cristoree.rocco.util.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RoccoBrain
{
    private static final int SHORT_HISTORY_CHARS = 140;
    private static final long STUCK_REQUEST_MS = 120_000L;

    // Lineas de memoria: van aparte de la #accion y puede haber varias en una respuesta.
    static final Pattern MEMORY_LINE = Pattern.compile(
            "(?im)^\\s*[*`]*#(recordar_siempre|recordar|olvidar)\\s*:\\s*(.+?)\\s*[*`]*\\s*$");
    // El jugador pide una imitacion: la guia de estilo completa va en el prompt.
    // Las ordenes de moverse o quedarse: si el jugador pidio una clara, manda esa.
    private static final java.util.Set<String> MOVEMENT_VERBS =
            java.util.Set.of("parar", "esperar", "seguir", "sentarse", "dormir", "levantarse");

    private static final Pattern IMPRESSION_REQUEST = Pattern.compile(
            "\\b(habla(me)? como|hablame como|imita(me)?|imitas|imitacion|imitar|hace de|has de|voz de|como si fueras|haceme a|hazme a|talk like|impression)\\b");
    private static final Pattern FRUSTRATED = Pattern.compile(
            "\\b(odio|me rindo|no puedo|no me sale|no sale|mierda|carajo|basta|me harte|harto|que bronca|otra vez no|no otra vez|ya fue|me mori otra vez|ugh)\\b");
    private static final Pattern HAPPY = Pattern.compile(
            "\\b(jaja\\w*|jsjs\\w*|xd+|genial|increible|al fin|por fin|lo logre|lo logramos|vamos|que bueno|me encanta|diamantes?|siii+)\\b");
    private static final Pattern SAD = Pattern.compile("\\b(triste|mal dia|me siento mal|deprimido|llore|llorando)\\b");
    private static final Pattern BORED = Pattern.compile("\\b(aburrido|me aburro|que hago|no se que hacer)\\b");

    private static final String GREET_PROMPT = "[FIRST MEETING: you just sprouted from the soil and you see the "
            + "player for the first time. Introduce yourself in one or two short sentences, true to your story.]";

    private static ExecutorService executor;
    private static final AtomicInteger IN_FLIGHT = new AtomicInteger();
    private static final Deque<Long> RECENT_CALLS = new ArrayDeque<>();
    // A quien ya se le explico como conseguir una clave gratis en esta sesion.
    private static final Set<UUID> ONBOARDED = ConcurrentHashMap.newKeySet();
    // Problemas de la IA ya contados en esta sesion: Rocco lo dice una sola vez por tipo.
    private static final Set<ChatReply.Failure> PROBLEMS_TOLD = ConcurrentHashMap.newKeySet();
    // Quien ya recibio el ofrecimiento de seguir usando su personalidad vieja editada.
    private static final Set<UUID> MIGRATION_OFFERED = ConcurrentHashMap.newKeySet();

    private final RoccoEntity rocco;
    private final List<ChatTurn> history = new ArrayList<>();
    private final Moments moments = new Moments();
    private final SpontaneousChatter chatter = new SpontaneousChatter();
    private final PhraseTracker phrases = new PhraseTracker();

    private boolean waiting;
    private long waitingSince;
    private boolean warnedRetiredModel;

    @Nullable
    private Traits.Mode lastMode;
    @Nullable
    private String pendingNote;
    @Nullable
    private String impression;
    private int impressionReplies;
    @Nullable
    private String playerMood;
    private long playerMoodAt;

    // ---------- una sola respuesta por orden ----------
    //
    // Antes una orden podia generar dos mensajes que se contradecian: la IA decia "dale, ya lo
    // hago" y un instante despues el trabajo decia "me faltan palos". Ahora la orden arranca
    // primero, lo que Rocco diria al empezar se junta (captured) en vez de ir al chat, y recien
    // ahi se contesta: si no hubo nada que avisar, va la respuesta de la IA; si hubo, se le pasa
    // eso a la IA para que lo diga una sola vez y en personaje (o se dice tal cual si no responde).

    // Lo que dijo una orden al arrancar. ack: lo que diria si no hubo nada que avisar.
    private record Started(String verb, String argument, List<String> said, String ack) {}

    // Un pedido a la IA. question es lo que dijo el jugador; prompt, lo que se le manda (con notas).
    // started: la orden ya se hizo antes (no se vuelve a hacer). followUp: es la segunda vuelta
    // para contar lo que paso al arrancar una orden.
    private record Turn(String question, String prompt, boolean spontaneous, @Nullable Started started, boolean followUp)
    {
        static Turn of(String question)
        {
            return new Turn(question, question, false, null, false);
        }
    }

    // Un mensaje que llego mientras pensaba la respuesta anterior: se contesta ese, el ultimo.
    private record Pending(ServerPlayer player, String question, @Nullable Started started, boolean isOrder) {}

    // Las ordenes de moverse se cumplen al toque aunque este pensando otra cosa.
    private static final Set<String> QUICK_VERBS = Set.of(
            "seguir", "esperar", "quedarse", "sentarse", "dormir", "levantarse", "despertarse", "parar", "cancelar",
            "teletransportarse", "venir", "aparecer");

    @Nullable
    private List<String> captured;
    @Nullable
    private Pending pending;
    @Nullable
    private String lastSaid;
    private long lastSaidAt;
    // Mientras esperaba a la IA llego una orden y la resolvio solo: esa respuesta ya no va.
    private boolean answeredMeanwhile;

    public RoccoBrain(RoccoEntity rocco)
    {
        this.rocco = rocco;
    }

    // Saludo al brotar: con IA lo inventa ella, sin IA usa una frase propia.
    public void greet(ServerPlayer player)
    {
        if (RoccoConfig.aiConfigured())
        {
            request(player, Turn.of(GREET_PROMPT));
        }
        else if (RoccoCommonConfig.AI_OFFLINE_FALLBACK.get())
        {
            deliverOffline(player, OfflineRocco.greeting());
            offerFreeAi(player);
        }
    }

    // El jugador le hablo (por chat o por microfono).
    public void ask(ServerPlayer player, String question)
    {
        long now = gameTime();
        PersonalityProfile profile = PersonalityProfile.current();
        this.chatter.heard(question, now);
        this.chatter.activity(now, profile, this.rocco.getRandom());
        notePlayerMood(question, now);
        offerMigration(player);

        // Si estaba bailando, frena para escucharte.
        if (this.rocco.getActiveTask() == com.cristoree.rocco.entity.RoccoTask.DANCING)
        {
            this.rocco.interruptActivity();
        }

        // Algunas cosas pedidas en broma ("dame marihuana", "prestame plata") tienen su regalito, con
        // o sin IA. Si estaba pensando otra respuesta, esa queda vieja.
        String joke = com.cristoree.rocco.RoccoSurprises.joke(this.rocco, player, question);
        if (joke != null)
        {
            if (this.waiting)
            {
                this.pending = null;
                this.answeredMeanwhile = true;
            }
            String said = clean(joke);
            speak(said, said, null);
            remember(question, said, said);
            this.chatter.activity(gameTime(), profile, this.rocco.getRandom());
            return;
        }

        // Ahorro de IA: una orden corta y clara la resuelve Rocco solo, sin gastar un pedido.
        OfflineRocco.Answer local = RoccoConfig.aiConfigured() && RoccoCommonConfig.AI_LOCAL_ORDERS.get()
                ? OfflineRocco.localOrder(this.rocco, question)
                : null;

        // Mientras piensa otra respuesta: se contesta el mensaje nuevo cuando llegue esa (la vieja
        // se descarta, con su orden, asi nada se hace dos veces). Una orden de moverse ("quedate",
        // "seguime") igual se cumple al toque, en silencio, y la respuesta despues lo cuenta.
        if (this.waiting && System.currentTimeMillis() - this.waitingSince < STUCK_REQUEST_MS)
        {
            if (local != null)
            {
                // Resuelta aca mismo: la respuesta que estaba esperando queda vieja y se descarta.
                this.pending = null;
                this.answeredMeanwhile = true;
                answerLocally(player, question, local);
                return;
            }
            OfflineRocco.Answer order = OfflineRocco.reply(this.rocco, question);
            Started started = null;
            if (order.verb() != null && QUICK_VERBS.contains(order.verb()))
            {
                this.rocco.setExpressionFromAi(order.face());
                started = start(player, order.verb(), order.argument(), order.text());
            }
            this.pending = new Pending(player, question, started, order.verb() != null);
            return;
        }
        if (this.waiting)
        {
            RoccoMod.LOGGER.warn("[Rocco] La respuesta anterior nunca llego; sigo escuchando.");
            this.waiting = false;
            this.rocco.endThinking();
        }

        if (local != null)
        {
            answerLocally(player, question, local);
            return;
        }
        answer(player, question, null);
    }

    // Lo que el jugador elige en el menu (agachado + clic derecho): una orden comun, que se hace al
    // toque y sin IA. Queda en la charla como si se la hubiera dicho (phrase), con la frase de "dale"
    // de esa orden (ack), o lo que haya pasado al arrancar.
    public void menuOrder(ServerPlayer player, String phrase, String verb, String argument, String ack)
    {
        if (this.waiting)
        {
            this.pending = null;
            this.answeredMeanwhile = true;
        }
        this.chatter.heard(phrase, gameTime());
        RoccoExpression face = ack.isEmpty() ? RoccoExpression.HAPPY : OfflineRocco.faceFor(ack);
        answerLocally(player, phrase, new OfflineRocco.Answer(ack.isEmpty() ? "" : OfflineRocco.line(ack), face, verb, argument));
    }

    // "Callate un rato" o "charla mas", desde el menu.
    public void menuQuiet(boolean quiet)
    {
        this.chatter.quiet(gameTime(), quiet);
        String category = quiet ? "shush" : "chatty";
        String said = clean(OfflineRocco.line(category));
        this.rocco.setExpressionFromAi(quiet ? RoccoExpression.BORED : RoccoExpression.HAPPY);
        speak(said, said, null);
        remember(quiet ? "Callate un rato." : "Charla un poco mas.", said, said);
    }

    public boolean isQuiet()
    {
        return this.chatter.isQuiet(gameTime());
    }

    // Una orden resuelta sin IA: se hace, se contesta una vez (lo que paso al arrancar o la frase
    // de "dale") y queda en la charla, asi la IA despues sabe que ya lo hizo.
    private void answerLocally(ServerPlayer player, String question, OfflineRocco.Answer order)
    {
        this.rocco.setExpressionFromAi(order.face());
        String said = sayStarted(start(player, order.verb(), order.argument(), order.text()));
        if (!said.isEmpty())
        {
            remember(question, said, said);
        }
    }

    private void answer(ServerPlayer player, String question, @Nullable Started started)
    {
        // Sin IA: contesta al toque con sus frases y entiende las ordenes simples.
        if (!RoccoConfig.aiConfigured())
        {
            if (started != null)
            {
                sayStarted(started);
            }
            else if (RoccoCommonConfig.AI_OFFLINE_FALLBACK.get())
            {
                deliverOffline(player, OfflineRocco.reply(this.rocco, question));
                offerFreeAi(player);
            }
            else
            {
                tell(Component.literal("no tengo ninguna IA configurada (Mods > Rocco > Config)."), true);
            }
            return;
        }

        Turn turn = started == null
                ? Turn.of(question)
                : new Turn(question, question + "\n\n" + alreadyDoneNote(started), false, started, false);
        if (!request(player, turn) && started != null)
        {
            sayStarted(started);
        }
    }

    // Hace la orden juntando lo que Rocco diria al arrancar, en vez de mandarlo al chat.
    private Started start(ServerPlayer player, String verb, @Nullable String argument, String ack)
    {
        List<String> said = new ArrayList<>();
        List<String> previous = this.captured;
        this.captured = said;
        try
        {
            RoccoActions.Outcome outcome = RoccoActions.run(this.rocco, player, verb, argument == null ? "" : argument);
            if (outcome != null && !outcome.ok() && outcome.note() != null)
            {
                this.rocco.setExpression(RoccoExpression.SAD);
                said.add(outcome.note() + ".");
            }
        }
        finally
        {
            this.captured = previous;
        }
        return new Started(verb, argument == null ? "" : argument, said, ack == null ? "" : ack);
    }

    // Lo que paso al arrancar la orden, tal cual (sin IA, o si la IA no contesto). Devuelve lo dicho.
    private String sayStarted(Started started)
    {
        String text = clean(started.said().isEmpty() ? started.ack() : String.join(" ", started.said()));
        if (!text.isEmpty())
        {
            speak(text, text, null);
        }
        this.chatter.activity(gameTime(), PersonalityProfile.current(), this.rocco.getRandom());
        return text;
    }

    private static String alreadyDoneNote(Started started)
    {
        return "[You already did what the player asked (\"" + (started.verb() + " " + started.argument()).trim() + "\")."
                + (started.said().isEmpty() ? " It went fine." : " Right away this happened: " + String.join(" ", started.said()))
                + " Answer in ONE short reply, in character, without contradicting this. Do not add an #accion line.]";
    }

    private static String resultNote(Started started)
    {
        return "[RESULT OF WHAT YOU JUST STARTED (\"" + (started.verb() + " " + started.argument()).trim() + "\"): right away "
                + "this happened: " + String.join(" ", started.said()) + " Answer the player above in ONE short reply that "
                + "tells them this, in character, without contradicting it. If something is missing, say exactly what. "
                + "Do not add an #accion line.]";
    }

    // Una vez por segundo desde la entidad: si hubo silencio un buen rato, dice algo solo.
    public void tickChatter()
    {
        boolean ai = RoccoConfig.aiConfigured();
        if (this.waiting || !RoccoCommonConfig.ENABLED.get() || (!ai && !RoccoCommonConfig.AI_OFFLINE_FALLBACK.get())
                || this.rocco.getRoccoState() != RoccoState.ACTIVE || this.rocco.getRoccoPose().isAsleep())
        {
            return;
        }

        ServerPlayer owner = this.rocco.owner();
        double radius = RoccoCommonConfig.CHAT_HEARING_RADIUS.get();
        if (owner == null || owner.level() != this.rocco.level() || owner.isSpectator()
                || owner.distanceToSqr(this.rocco) > radius * radius)
        {
            return;
        }

        long now = gameTime();
        PersonalityProfile profile = PersonalityProfile.current();
        if (!this.chatter.due(now, profile, this.rocco.getRandom()))
        {
            return;
        }

        // Sin IA tambien comenta: con una frase propia segun lo que esta pasando (de noche, en una
        // cueva, con poca vida, con bichos cerca, o lo que se le ocurra).
        if (!ai)
        {
            this.chatter.activity(now, profile, this.rocco.getRandom());
            this.rocco.sayLine(OfflineRocco.spontaneous(this.rocco, owner, this.rocco.getRandom()));
            return;
        }

        String note = this.chatter.note(this.rocco, owner, now, profile, !RoccoMemory.isEmpty(owner), this.rocco.getRandom());
        // Pase lo que pase con este pedido, el proximo silencio se cuenta desde ahora.
        this.chatter.activity(now, profile, this.rocco.getRandom());
        request(owner, new Turn(note, note, true, null, false));
    }

    // ---------- el pedido a la IA ----------

    // false si el pedido no salio (demasiados a la vez, o el limite por minuto).
    private boolean request(ServerPlayer player, Turn turn)
    {
        boolean spontaneous = turn.spontaneous();
        String question = turn.question();
        if (IN_FLIGHT.get() >= RoccoCommonConfig.MAX_CONCURRENT_REQUESTS.get())
        {
            RoccoMod.LOGGER.debug("[Rocco] Peticion descartada: ya hay {} en curso", IN_FLIGHT.get());
            return false;
        }

        if (!allowedByRateLimit())
        {
            if (!spontaneous && turn.started() == null)
            {
                tell(Component.literal("Estoy respondiendo demasiado seguido, espera un momento."), true);
            }
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null)
        {
            return false;
        }

        // Una vez por sesion: el modelo de Gemini configurado fue retirado.
        String retired = RoccoConfig.retiredModelWarning();
        if (retired != null && !this.warnedRetiredModel)
        {
            this.warnedRetiredModel = true;
            tell(Component.literal(retired), true);
        }

        Prepared prepared = prepare(player, question, spontaneous);
        PersonalityProfile profile = prepared.profile();
        Traits.Mode mode = prepared.mode();
        Moments.Permissions permissions = prepared.permissions();
        boolean asked = prepared.asked();
        boolean fullImpressions = prepared.dynamic().fullImpressions();
        List<ChatTurn> snapshot = trimmedHistory();

        this.waiting = true;
        this.waitingSince = System.currentTimeMillis();
        IN_FLIGHT.incrementAndGet();

        // Rocco "piensa" mientras espera: si tarda, se lleva la mano al menton (lo maneja la entidad).
        if (!spontaneous && turn.started() == null)
        {
            this.rocco.beginThinking();
        }

        executor().execute(() ->
        {
            ChatReply reply;
            try
            {
                String systemPrompt = prepared.build();
                if (RoccoCommonConfig.DEBUG_LOGGING.get())
                {
                    RoccoMod.LOGGER.info("[Rocco] Prompt armado: {} caracteres (~{} tokens), modo {}{}",
                            systemPrompt.length(), systemPrompt.length() / 4, mode,
                            fullImpressions ? ", con la guia de imitaciones completa" : "");
                }
                reply = AiRouter.ask(systemPrompt, snapshot, turn.prompt());
            }
            catch (Throwable e)
            {
                RoccoMod.LOGGER.error("[Rocco] Error inesperado consultando la IA", e);
                reply = ChatReply.fail(ChatReply.Failure.UNREADABLE, e.toString(), null, null);
            }

            ChatReply result = reply;

            // Volver al hilo del servidor antes de tocar la entidad o el chat.
            server.execute(() ->
            {
                IN_FLIGHT.decrementAndGet();
                this.waiting = false;
                this.rocco.endThinking();

                // Pudo morir o descargarse mientras esperabamos.
                if (!this.rocco.isAlive())
                {
                    this.pending = null;
                    return;
                }

                // Mientras tanto le diste una orden y la resolvio solo: esta respuesta quedo vieja.
                if (this.answeredMeanwhile)
                {
                    this.answeredMeanwhile = false;
                    if (turn.followUp() && turn.started() != null)
                    {
                        sayStarted(turn.started());
                    }
                    Pending later = this.pending;
                    this.pending = null;
                    if (later != null)
                    {
                        answer(later.player(), later.question(), later.started());
                    }
                    return;
                }

                // Mientras pensaba le dieron otra orden: esa manda, asi que esta respuesta (y su
                // orden) quedo vieja y se descarta. Si solo le siguieron charlando, se contesta esta
                // y despues lo nuevo.
                Pending next = this.pending;
                this.pending = null;
                if (next != null && next.isOrder())
                {
                    RoccoMod.LOGGER.debug("[Rocco] Descarto la respuesta a '{}': llego una orden nueva", question);
                    if (turn.followUp() && turn.started() != null)
                    {
                        // Lo que paso al arrancar la orden anterior igual se cuenta, tal cual.
                        sayStarted(turn.started());
                    }
                    answer(next.player(), next.question(), next.started());
                    return;
                }

                if (result.ok())
                {
                    this.lastMode = mode;
                    this.pendingNote = null;
                    deliver(player, turn, result.text(), asked, permissions, profile);
                }
                else
                {
                    failed(player, turn, result);
                }

                if (next != null)
                {
                    // Si esta respuesta abrio una segunda vuelta (contar lo que paso al arrancar),
                    // lo nuevo espera a esa.
                    if (this.waiting)
                    {
                        this.pending = next;
                    }
                    else
                    {
                        answer(next.player(), next.question(), next.started());
                    }
                }
            });
        });
        return true;
    }

    // Lo que hace falta para armar el prompt de un pedido. Se junta en el hilo del servidor (mira
    // el mundo) y el texto se arma despues con build(), en el hilo de la IA, para no frenar el juego.
    private record Prepared(PersonalityProfile profile, Traits.Mode mode, Moments.Permissions permissions, boolean asked,
                            PersonalityPromptBuilder.Dynamic dynamic, @Nullable String ownFile)
    {
        String build()
        {
            return PersonalityPromptBuilder.build(this.profile, this.mode, this.dynamic, this.ownFile);
        }
    }

    private Prepared prepare(ServerPlayer player, String question, boolean spontaneous)
    {
        long now = gameTime();
        PersonalityProfile profile = PersonalityProfile.current();
        Traits.Mode mode = PersonalityProfile.mode();
        Relationship stage = this.rocco.relationship();
        boolean danger = inDanger(player);
        Moments.Permissions permissions = this.moments.roll(profile, stage, now, danger, this.rocco.getRandom());
        boolean asked = !spontaneous && IMPRESSION_REQUEST.matcher(Text.normalize(question)).find();
        boolean fullImpressions = asked || this.impression != null || permissions.impression();
        boolean voiceTags = voiceEmotions();

        String rightNow = RoccoContextBuilder.build(this.rocco, player)
                + extraLines(player, profile, mode, stage, permissions, danger, now);
        PersonalityPromptBuilder.Dynamic dynamic = new PersonalityPromptBuilder.Dynamic(
                RoccoActions.describeForAi(),
                PersonalityPromptBuilder.faces(profile),
                PersonalityTexts.trait(voiceTags ? "voice_tags.on" : "voice_tags.off"),
                rightNow.trim(),
                fullImpressions,
                player.getLanguage());
        String ownFile = mode == Traits.Mode.PERSONALIZADO && RoccoCommonConfig.USE_OWN_FILE.get()
                ? RoccoPersonality.loadOwnFile()
                : null;
        return new Prepared(profile, mode, permissions, asked, dynamic, ownFile);
    }

    // Lo que se le mandaria a la IA ahora mismo, para /rocco personalidad ver. Tirar los dados no
    // gasta los permisos (eso pasa recien cuando Rocco los usa), y la nota de cambio de modo queda.
    public String debugPrompt(ServerPlayer player)
    {
        String note = this.pendingNote;
        Traits.Mode last = this.lastMode;
        String prompt = prepare(player, "", false).build();
        this.pendingNote = note;
        this.lastMode = last;
        return prompt;
    }

    // Lo que se le suma al bloque RIGHT NOW: el modo, la relacion, lo que recuerda, los permisos.
    private String extraLines(ServerPlayer player, PersonalityProfile profile, Traits.Mode mode, Relationship stage,
                              Moments.Permissions permissions, boolean danger, long now)
    {
        StringBuilder out = new StringBuilder();
        out.append("- Mode: ").append(modeName(mode)).append('\n');
        out.append("- Relationship stage: ").append(stage.label()).append('\n');
        out.append("- Attitude level: ").append(stage.attitude(profile.treatment())).append('\n');

        String memory = RoccoMemory.describe(player);
        out.append("- What you remember about this player: ").append(memory.isEmpty() ? "nothing yet" : memory).append('\n');

        String mood = playerMood(player, danger, now);
        if (mood != null)
        {
            out.append("- The player's mood: ").append(mood).append('\n');
        }

        if (profile.allowsPsycho())
        {
            out.append("- Dark side: ").append(permissions.darkSide() ? "allowed now" : "not now").append('\n');
        }
        if (RoccoCommonConfig.IMPRESSIONS_ENABLED.get() && profile.spontaneousImpressions() != Traits.SpontaneousImpressions.NUNCA)
        {
            out.append("- Spontaneous impression: ").append(permissions.impression() ? "allowed now" : "not now").append('\n');
        }
        if (profile.jealousy() == Traits.Jealousy.DRAMATICOS)
        {
            out.append("- Jealousy: ").append(permissions.jealousy() ? "allowed now" : "not now").append('\n');
        }
        if (this.impression != null)
        {
            out.append("- You are doing an impression of ").append(this.impression).append(" (reply ")
                    .append(this.impressionReplies + 1).append(" of about 5; keep the @voz tag until it ends)\n");
        }

        String around = othersAround(player);
        if (!around.isEmpty())
        {
            out.append("- Who is around: ").append(around).append('\n');
        }

        String overused = this.phrases.overused();
        if (!overused.isEmpty())
        {
            out.append("- Phrases you've been overusing lately (avoid them and their structure for now): ")
                    .append(overused).append('\n');
        }

        // Cambio de modo con el juego abierto: lo puede notar, una sola vez, sin nombrar nada tecnico.
        if (this.lastMode != null && this.lastMode != mode)
        {
            this.pendingNote = "One-time note: your mode just changed to " + modeName(mode) + ". You may react to it "
                    + "in character in one short line, without naming modes or settings.";
        }
        if (this.pendingNote != null)
        {
            out.append("- ").append(this.pendingNote).append('\n');
        }
        if (this.lastMode == null)
        {
            this.lastMode = mode;
        }

        // Algunas IAs contestan "dale, me quedo" y se olvidan la orden: sin ella Rocco no hace nada.
        out.append("\nREMINDER: if the player asks you to do something (move, stay, sit, get up, work...), the LAST ")
                .append("line of your reply must be \"#accion: <verb> <argument>\". Without that line you don't really do it.");
        return out.toString();
    }

    private static String modeName(Traits.Mode mode)
    {
        return switch (mode)
        {
            case CHILL -> "Chill";
            case SIN_FILTRO -> "Sin filtro";
            case PERSONALIZADO -> "Personalizado";
        };
    }

    // ---------- la respuesta ----------

    private void deliver(ServerPlayer player, Turn turn, String rawAnswer, boolean asked,
                         Moments.Permissions permissions, PersonalityProfile profile)
    {
        String question = turn.question();
        boolean spontaneous = turn.spontaneous();
        // Primero las lineas de memoria y la orden de trabajo, asi no aparecen en el chat.
        String withoutMemory = applyMemoryLines(player, rawAnswer);
        RoccoActions.Parsed parsed = RoccoActions.parse(withoutMemory);
        ReplyTags.Parsed tags = ReplyTags.parse(parsed.cleanText(), voiceEmotions());

        // La cara: si eligio psycho sin tener permiso (o con el lado psicopata apagado), smug.
        RoccoExpression face = tags.face() == null ? RoccoExpression.HAPPY : tags.face();
        boolean psycho = face == RoccoExpression.PSYCHO;
        if (psycho && !(profile.allowsPsycho() && permissions.darkSide()))
        {
            face = RoccoExpression.SMUG;
            psycho = false;
        }
        // Imitando a alguien pone su cara de "mewing" (mandibula afuera, la ceja arriba), elija
        // la IA la cara que elija.
        if (tags.impression() != null)
        {
            face = RoccoExpression.MEWING;
        }
        this.rocco.setExpressionFromAi(face);

        boolean spontaneousImpression = tags.impression() != null && !asked && this.impression == null;
        this.moments.used(permissions, psycho, spontaneousImpression, face == RoccoExpression.ANGRY, gameTime());
        trackImpression(tags.impression());
        this.chatter.activity(gameTime(), profile, this.rocco.getRandom());

        String chat = clean(tags.chatText());

        // Por su cuenta no se pone a trabajar, y una orden que ya se hizo no se vuelve a hacer.
        if (spontaneous || turn.started() != null || turn.followUp())
        {
            if (!spontaneous)
            {
                this.rocco.addTrust(2.0D);
            }
            if (!chat.isEmpty())
            {
                remember(spontaneous ? "[SPONTANEOUS MOMENT]" : question, tags.historyText(), chat);
                speak(chat, tags.voiceText(), tags.impression(), tags.language());
            }
            else if (turn.started() != null)
            {
                sayStarted(turn.started());
            }
            return;
        }
        this.rocco.addTrust(2.0D);

        String verb = parsed.verb();
        String argument = parsed.argument();

        // Las IAs a veces contestan "dale, me quedo quieto" y se olvidan la linea #accion (o
        // inventan un verbo). Si lo que pidio el jugador es una orden clara, se hace igual.
        if (!RoccoActions.knows(verb))
        {
            OfflineRocco.Answer understood = question.equals(GREET_PROMPT) ? null : OfflineRocco.reply(this.rocco, question);
            if (understood != null && understood.verb() != null)
            {
                RoccoMod.LOGGER.debug("[Rocco] La IA no puso la orden; uso la del jugador: {} {}",
                        understood.verb(), understood.argument());
                verb = understood.verb();
                argument = understood.argument() == null ? "" : understood.argument();
            }
            else
            {
                verb = null;
            }
        }

        // "Quedate quieto" es esperar, no solo parar: si la IA eligio otra orden de movimiento,
        // manda la que pidio el jugador (si no, con "parar" seguia siguiendolo despues de un mate).
        if (verb != null && MOVEMENT_VERBS.contains(verb) && !question.equals(GREET_PROMPT))
        {
            OfflineRocco.Answer own = OfflineRocco.reply(this.rocco, question);
            if (own != null && own.verb() != null && MOVEMENT_VERBS.contains(own.verb()) && !own.verb().equals(verb))
            {
                verb = own.verb();
                argument = own.argument() == null ? "" : own.argument();
            }
        }

        if (verb == null)
        {
            if (!chat.isEmpty())
            {
                remember(question, tags.historyText(), chat);
                speak(chat, tags.voiceText(), tags.impression(), tags.language());
            }
            return;
        }

        // Primero la orden, despues la palabra: asi lo que dice no contradice lo que paso.
        Started started = start(player, verb, argument, chat);
        if (started.said().isEmpty())
        {
            // Arranco sin nada que avisar: la respuesta de la IA es la que corresponde.
            if (!chat.isEmpty())
            {
                remember(question, tags.historyText(), chat);
                speak(chat, tags.voiceText(), tags.impression(), tags.language());
            }
            return;
        }

        // Al arrancar surgio algo (le falta algo, no se puede): la IA lo cuenta en una sola
        // respuesta. Si no se puede preguntar, se dice tal cual.
        Turn followUp = new Turn(question, question + "\n\n" + resultNote(started), false, started, true);
        if (!request(player, followUp))
        {
            sayStarted(started);
        }
    }

    // Lo que se dijo queda en la charla, para que la IA sepa lo que ya hablaron.
    private void remember(String playerText, String roccoText, String chat)
    {
        this.history.add(new ChatTurn(true, playerText));
        this.history.add(new ChatTurn(false, roccoText));
        this.phrases.add(chat);
    }

    // Guarda u olvida lo que la IA marco, y devuelve la respuesta sin esas lineas.
    private String applyMemoryLines(ServerPlayer player, String answer)
    {
        Matcher matcher = MEMORY_LINE.matcher(answer);
        StringBuilder rest = new StringBuilder();
        while (matcher.find())
        {
            String kind = matcher.group(1).toLowerCase(Locale.ROOT);
            String text = matcher.group(2).trim();
            switch (kind)
            {
                case "recordar" -> RoccoMemory.remember(player, text, false);
                case "recordar_siempre" -> RoccoMemory.remember(player, text, true);
                default -> RoccoMemory.forget(player, text);
            }
            matcher.appendReplacement(rest, "");
        }
        matcher.appendTail(rest);
        return rest.toString().trim();
    }

    // La imitacion dura lo que la IA siga poniendo la etiqueta (unas 5 respuestas).
    private void trackImpression(@Nullable String name)
    {
        if (name == null)
        {
            this.impression = null;
            this.impressionReplies = 0;
            return;
        }
        if (name.equalsIgnoreCase(this.impression))
        {
            this.impressionReplies++;
        }
        else
        {
            this.impression = name;
            this.impressionReplies = 1;
        }
    }

    // Ninguna IA contesto (o el filtro de un proveedor bloqueo la respuesta).
    private void failed(ServerPlayer player, Turn turn, ChatReply result)
    {
        String question = turn.question();
        if (turn.spontaneous())
        {
            // Si nadie le pregunto nada, mejor callado que explicando por que no pudo.
            RoccoMod.LOGGER.debug("[Rocco] El comentario espontaneo no salio: {}", result.error());
            this.rocco.setExpressionFromAi(RoccoExpression.HAPPY);
            return;
        }

        // La orden ya esta hecha: lo que paso se cuenta tal cual, sin volver a hacerla.
        if (turn.started() != null)
        {
            sayStarted(turn.started());
            if (result.failure() != ChatReply.Failure.BLOCKED)
            {
                noteProblem(player, result);
            }
            return;
        }

        if (result.failure() == ChatReply.Failure.BLOCKED)
        {
            // Nada de mostrar el error: lo dice en personaje, y el detalle queda en el log.
            if (RoccoCommonConfig.DEBUG_LOGGING.get())
            {
                RoccoMod.LOGGER.info("[Rocco] Respuesta bloqueada por el proveedor: {}", result.error());
            }
            OfflineRocco.Answer order = question.equals(GREET_PROMPT) ? null : OfflineRocco.reply(this.rocco, question);
            if (order != null && order.verb() != null)
            {
                // Si era una orden, se hace igual; lo que diga al arrancar es la respuesta.
                Started started = start(player, order.verb(), order.argument(), "");
                if (!started.said().isEmpty())
                {
                    sayStarted(started);
                    return;
                }
            }
            this.rocco.setExpressionFromAi(RoccoExpression.SMUG);
            tell(Component.translatable("rocco.blocked." + (1 + this.rocco.getRandom().nextInt(6))), false);
            return;
        }

        // Ninguna IA contesto: Rocco igual responde con lo suyo, y avisa por que (una vez).
        if (RoccoCommonConfig.AI_OFFLINE_FALLBACK.get())
        {
            OfflineRocco.Answer fallback = question.equals(GREET_PROMPT)
                    ? OfflineRocco.greeting()
                    : OfflineRocco.reply(this.rocco, question);
            deliverOffline(player, fallback);
            noteProblem(player, result);
        }
        else
        {
            this.rocco.setExpression(RoccoExpression.SAD);
            tell(Component.literal("no pude responder: " + result.error()), true);
        }
    }

    // Sin IA: si es una orden, se hace primero y se dice una sola cosa (lo que paso al arrancar,
    // o su frase de "dale" si arranco sin problemas). Si no, su frase y listo.
    private void deliverOffline(ServerPlayer player, OfflineRocco.Answer answer)
    {
        this.rocco.setExpressionFromAi(answer.face());
        if (answer.verb() != null)
        {
            sayStarted(start(player, answer.verb(), answer.argument(), answer.text()));
            return;
        }
        if (!answer.text().isBlank())
        {
            String text = clean(answer.text());
            speak(text, text, null);
        }
        this.chatter.activity(gameTime(), PersonalityProfile.current(), this.rocco.getRandom());
    }

    // Rocco habla por su cuenta mientras trabaja ("me faltan 3 de roca"). Lo escuchan los que
    // esten cerca, y queda en la charla para que la IA sepa que ya lo dijo.
    public void announce(String text, RoccoExpression face)
    {
        String message = clean(text);
        if (message.isEmpty())
        {
            return;
        }

        if (face != null)
        {
            this.rocco.setExpressionFromAi(face);
        }

        // Una orden recien arrancada: lo que diga al empezar se junta para la respuesta unica.
        if (this.captured != null)
        {
            this.captured.add(message);
            return;
        }

        // La misma frase dos veces seguidas en pocos segundos (dos encargos que avisan lo mismo) sobra.
        long now = gameTime();
        if (message.equals(this.lastSaid) && now - this.lastSaidAt < 100L)
        {
            return;
        }
        this.lastSaid = message;
        this.lastSaidAt = now;

        this.rocco.talkFor(Math.min(200, 20 + message.length()));
        this.chatter.activity(gameTime(), PersonalityProfile.current(), this.rocco.getRandom());

        // Se suma a la ultima respuesta suya: algunas IAs no aceptan dos turnos seguidos de Rocco.
        if (!this.history.isEmpty() && !this.history.get(this.history.size() - 1).fromPlayer())
        {
            ChatTurn last = this.history.remove(this.history.size() - 1);
            this.history.add(new ChatTurn(false, last.text() + " " + message));
        }

        broadcast(Component.literal(message), null, false);
        com.cristoree.rocco.voice.RoccoVoice.speak(this.rocco, message, null);
    }

    // "<Rocco> texto", o "<Rocco (como Vegeta)> texto" durante una imitacion.
    private void broadcast(Component message, @Nullable String impression, boolean problem)
    {
        if (!RoccoCommonConfig.CHAT_SHOW_IN_CHAT.get() && !problem)
        {
            return;
        }

        String name = RoccoCommonConfig.COMPANION_NAME.get();
        Component who = impression == null
                ? Component.literal(name)
                : Component.translatable("rocco.chat.impression", name, impression);
        Component line = Component.literal("<").append(who).append("> ")
                .withStyle(problem ? ChatFormatting.RED : ChatFormatting.GREEN)
                .append(message.copy().withStyle(ChatFormatting.WHITE));

        double radius = RoccoCommonConfig.CHAT_HEARING_RADIUS.get();
        if (!(this.rocco.level() instanceof ServerLevel level))
        {
            return;
        }
        for (ServerPlayer nearby : level.players())
        {
            if (nearby.distanceToSqr(this.rocco) <= radius * radius)
            {
                nearby.sendSystemMessage(line);
            }
        }
    }

    private void speak(String chat, String voice, @Nullable String impression)
    {
        speak(chat, voice, impression, "es");
    }

    private void speak(String chat, String voice, @Nullable String impression, String language)
    {
        // La boca se mueve un rato proporcional a lo que dijo.
        this.rocco.talkFor(Math.min(200, 20 + chat.length()));
        broadcast(Component.literal(chat), impression, false);
        // Y en voz alta, si la voz esta configurada.
        com.cristoree.rocco.voice.RoccoVoice.speak(this.rocco, voice, impression, language);
    }

    // Una sola linea, sin espacios dobles ni etiquetas sueltas: el chat no muestra saltos de linea.
    private String clean(String answer)
    {
        String out = answer.replaceAll("(?i)(?:@|\\[)(happy|bored|serious|scared|sad|angry|smug|psycho|dizzy|mewing|cheer|wow)]?(?=\\W|$)", " ")
                .replaceAll("\\[[a-zA-Z ]{2,24}]", " ")
                .replaceAll("\\s+", " ").trim();

        int limit = RoccoCommonConfig.CHAT_MAX_RESPONSE_CHARS.get();
        if (out.length() > limit)
        {
            out = out.substring(0, limit).trim() + "...";
        }
        return out;
    }

    // Si la IA no anda, Rocco lo dice en personaje una sola vez por tipo de problema, con una
    // pista para ver el detalle. Sus frases propias siguen contestando mientras tanto.
    private void noteProblem(ServerPlayer player, ChatReply result)
    {
        if (!PROBLEMS_TOLD.add(result.failure()))
        {
            return;
        }
        broadcast(Component.translatable("rocco.problem." + (1 + this.rocco.getRandom().nextInt(3))), null, false);
        player.sendSystemMessage(Component.translatable("rocco.problem.hint", String.valueOf(result.error()))
                .withStyle(ChatFormatting.GRAY));
    }

    // La primera vez que alguien le habla sin IA configurada, le explica como tener una gratis.
    private void offerFreeAi(ServerPlayer player)
    {
        if (!ONBOARDED.add(player.getUUID()))
        {
            return;
        }

        player.sendSystemMessage(freeAiMessage());
    }

    // Venia de la personalidad vieja y la habia editado: se le ofrece seguir usandola.
    private void offerMigration(ServerPlayer player)
    {
        if (RoccoCommonConfig.USE_OWN_FILE.get() || !MIGRATION_OFFERED.add(player.getUUID())
                || !RoccoPersonality.hasEditedOldFile())
        {
            return;
        }
        player.sendSystemMessage(Component.translatable("rocco.personality.migrate", RoccoPersonality.file().toString())
                .withStyle(ChatFormatting.GRAY));
    }

    public static Component freeAiMessage()
    {
        MutableComponent line = Component.literal("[Rocco] Esta usando sus frases de fabrica. Para que charle de verdad, "
                + "consegui una clave GRATIS (2 minutos) en ").withStyle(ChatFormatting.GRAY);

        boolean first = true;
        for (AiProvider provider : AiProvider.values())
        {
            if (!provider.freeHosted())
            {
                continue;
            }
            if (!first)
            {
                line.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            first = false;
            line.append(link(provider.label(), provider.signupUrl()));
        }

        return line.append(Component.literal(" y pegala en Mods > Rocco > Config. Con mas de una, si una se queda "
                + "sin cupo usa la otra.").withStyle(ChatFormatting.GRAY));
    }

    public static MutableComponent link(String text, String url)
    {
        return Component.literal(text).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(url))));
    }

    private void tell(Component message, boolean problem)
    {
        broadcast(message, null, problem);
    }

    // ---------- lo que sabe del momento ----------

    private long gameTime()
    {
        return this.rocco.level().getGameTime();
    }

    // Las etiquetas de emocion solo sirven si hay voz y el modelo de Fish Audio las entiende (s1).
    private static boolean voiceEmotions()
    {
        return RoccoConfig.voiceReady()
                && RoccoCommonConfig.FISH_MODEL.get().trim().toLowerCase(Locale.ROOT).startsWith("s1");
    }

    private boolean inDanger(ServerPlayer player)
    {
        if (player.getHealth() <= player.getMaxHealth() * 0.4F)
        {
            return true;
        }
        return !this.rocco.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(8.0D),
                entity -> entity instanceof Enemy && entity.isAlive()).isEmpty();
    }

    // Lo que se le nota al jugador por como escribe (dura unos minutos) o por como esta.
    private void notePlayerMood(String message, long now)
    {
        String text = Text.normalize(message);
        String mood = FRUSTRATED.matcher(text).find() ? "frustrated"
                : SAD.matcher(text).find() ? "sad or down"
                : BORED.matcher(text).find() ? "bored, looking for something to do"
                : HAPPY.matcher(text).find() ? "happy, joking around"
                : null;
        if (mood != null)
        {
            this.playerMood = mood;
            this.playerMoodAt = now;
        }
    }

    @Nullable
    private String playerMood(ServerPlayer player, boolean danger, long now)
    {
        if (player.getHealth() <= player.getMaxHealth() * 0.3F)
        {
            return "in danger, very low health";
        }
        if (this.playerMood != null && now - this.playerMoodAt < 6000L)
        {
            return this.playerMood;
        }
        return danger ? "in a tense moment, hostile mobs close" : null;
    }

    private String othersAround(ServerPlayer player)
    {
        double radius = RoccoCommonConfig.CHAT_HEARING_RADIUS.get();
        List<String> names = new ArrayList<>();
        for (ServerPlayer other : player.serverLevel().players())
        {
            if (other != player && !other.isSpectator() && other.distanceToSqr(this.rocco) <= radius * radius)
            {
                names.add(other.getGameProfile().getName());
            }
        }
        return names.isEmpty() ? "" : "other players: " + String.join(", ", names);
    }

    // Los turnos viejos van recortados y los ultimos completos: acelera y abarata la peticion.
    private List<ChatTurn> trimmedHistory()
    {
        int maxTurns = RoccoCommonConfig.CHAT_HISTORY_TURNS.get() * 2;
        int fullMessages = RoccoCommonConfig.CHAT_FULL_DETAIL_TURNS.get() * 2;

        List<ChatTurn> recent = this.history.size() > maxTurns
                ? this.history.subList(this.history.size() - maxTurns, this.history.size())
                : this.history;

        List<ChatTurn> out = new ArrayList<>(recent.size());
        int firstFull = Math.max(0, recent.size() - fullMessages);

        for (int i = 0; i < recent.size(); i++)
        {
            ChatTurn turn = recent.get(i);
            if (i >= firstFull || turn.text().length() <= SHORT_HISTORY_CHARS)
            {
                out.add(turn);
            }
            else
            {
                out.add(new ChatTurn(turn.fromPlayer(), turn.text().substring(0, SHORT_HISTORY_CHARS) + "..."));
            }
        }

        return out;
    }

    private static boolean allowedByRateLimit()
    {
        int limit = RoccoCommonConfig.RATE_LIMIT_PER_MINUTE.get();
        if (limit <= 0)
        {
            return true;
        }

        long now = System.currentTimeMillis();
        synchronized (RECENT_CALLS)
        {
            while (!RECENT_CALLS.isEmpty() && now - RECENT_CALLS.peekFirst() > 60_000L)
            {
                RECENT_CALLS.pollFirst();
            }

            if (RECENT_CALLS.size() >= limit)
            {
                return false;
            }

            RECENT_CALLS.addLast(now);
            return true;
        }
    }

    private static synchronized ExecutorService executor()
    {
        if (executor == null)
        {
            executor = Executors.newCachedThreadPool(runnable ->
            {
                Thread thread = new Thread(runnable, "Rocco-IA");
                thread.setDaemon(true);
                return thread;
            });
        }
        return executor;
    }
}

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

    public RoccoBrain(RoccoEntity rocco)
    {
        this.rocco = rocco;
    }

    // Saludo al brotar: con IA lo inventa ella, sin IA usa una frase propia.
    public void greet(ServerPlayer player)
    {
        if (RoccoConfig.aiConfigured())
        {
            request(player, GREET_PROMPT, false);
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

        // Si una respuesta anterior quedo colgada (una IA que nunca contesto), no lo deja sordo.
        if (this.waiting && System.currentTimeMillis() - this.waitingSince < STUCK_REQUEST_MS)
        {
            // Mientras piensa la respuesta anterior, una orden clara ("quedate quieto",
            // "seguime") se cumple igual, al toque, en vez de perderse.
            OfflineRocco.Answer order = OfflineRocco.reply(this.rocco, question);
            if (order.verb() != null)
            {
                deliverOffline(player, order);
            }
            return;
        }
        if (this.waiting)
        {
            RoccoMod.LOGGER.warn("[Rocco] La respuesta anterior nunca llego; sigo escuchando.");
            this.waiting = false;
        }

        // Sin IA: contesta al toque con sus frases y entiende las ordenes simples.
        if (!RoccoConfig.aiConfigured())
        {
            if (RoccoCommonConfig.AI_OFFLINE_FALLBACK.get())
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

        request(player, question, false);
    }

    // Una vez por segundo desde la entidad: si hubo silencio un buen rato, dice algo solo.
    public void tickChatter()
    {
        if (this.waiting || !RoccoCommonConfig.ENABLED.get() || !RoccoConfig.aiConfigured()
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

        String note = this.chatter.note(this.rocco, owner, now, profile, !RoccoMemory.isEmpty(owner), this.rocco.getRandom());
        // Pase lo que pase con este pedido, el proximo silencio se cuenta desde ahora.
        this.chatter.activity(now, profile, this.rocco.getRandom());
        request(owner, note, true);
    }

    // ---------- el pedido a la IA ----------

    private void request(ServerPlayer player, String question, boolean spontaneous)
    {
        if (IN_FLIGHT.get() >= RoccoCommonConfig.MAX_CONCURRENT_REQUESTS.get())
        {
            RoccoMod.LOGGER.debug("[Rocco] Peticion descartada: ya hay {} en curso", IN_FLIGHT.get());
            return;
        }

        if (!allowedByRateLimit())
        {
            if (!spontaneous)
            {
                tell(Component.literal("Estoy respondiendo demasiado seguido, espera un momento."), true);
            }
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null)
        {
            return;
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

        // Rocco "piensa" mientras espera.
        if (!spontaneous)
        {
            this.rocco.setExpression(RoccoExpression.SERIOUS);
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
                reply = AiRouter.ask(systemPrompt, snapshot, question);
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

                // Pudo morir o descargarse mientras esperabamos.
                if (!this.rocco.isAlive())
                {
                    return;
                }

                if (result.ok())
                {
                    this.lastMode = mode;
                    this.pendingNote = null;
                    deliver(player, question, result.text(), spontaneous, asked, permissions, profile);
                    return;
                }

                failed(player, question, result, spontaneous);
            });
        });
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

    private void deliver(ServerPlayer player, String question, String rawAnswer, boolean spontaneous, boolean asked,
                         Moments.Permissions permissions, PersonalityProfile profile)
    {
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
        this.rocco.setExpressionFromAi(face);

        boolean spontaneousImpression = tags.impression() != null && !asked && this.impression == null;
        this.moments.used(permissions, psycho, spontaneousImpression, face == RoccoExpression.ANGRY, gameTime());
        trackImpression(tags.impression());

        String chat = clean(tags.chatText());
        if (!chat.isEmpty())
        {
            this.history.add(new ChatTurn(true, spontaneous ? "[SPONTANEOUS MOMENT]" : question));
            this.history.add(new ChatTurn(false, tags.historyText()));
            this.phrases.add(chat);
            speak(chat, tags.voiceText(), tags.impression());
        }
        this.chatter.activity(gameTime(), profile, this.rocco.getRandom());

        if (spontaneous)
        {
            // Por su cuenta no se pone a trabajar ni a moverse: solo hablo.
            return;
        }
        this.rocco.addTrust(2.0D);

        // Despues de hablar: si la accion dice algo al toque (una receta), sale en orden.
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

        RoccoActions.Outcome outcome = verb == null ? null : RoccoActions.run(this.rocco, player, verb, argument);
        reportOutcome(outcome);
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
    private void failed(ServerPlayer player, String question, ChatReply result, boolean spontaneous)
    {
        if (spontaneous)
        {
            // Si nadie le pregunto nada, mejor callado que explicando por que no pudo.
            RoccoMod.LOGGER.debug("[Rocco] El comentario espontaneo no salio: {}", result.error());
            this.rocco.setExpressionFromAi(RoccoExpression.HAPPY);
            return;
        }

        if (result.failure() == ChatReply.Failure.BLOCKED)
        {
            // Nada de mostrar el error: lo dice en personaje, y el detalle queda en el log.
            if (RoccoCommonConfig.DEBUG_LOGGING.get())
            {
                RoccoMod.LOGGER.info("[Rocco] Respuesta bloqueada por el proveedor: {}", result.error());
            }
            this.rocco.setExpressionFromAi(RoccoExpression.SMUG);
            tell(Component.translatable("rocco.blocked." + (1 + this.rocco.getRandom().nextInt(6))), false);
            OfflineRocco.Answer order = question.equals(GREET_PROMPT) ? null : OfflineRocco.reply(this.rocco, question);
            if (order != null && order.verb() != null)
            {
                reportOutcome(RoccoActions.run(this.rocco, player, order.verb(), order.argument() == null ? "" : order.argument()));
            }
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

    private void deliverOffline(ServerPlayer player, OfflineRocco.Answer answer)
    {
        this.rocco.setExpressionFromAi(answer.face());
        // Algunas ordenes (la receta, el cofre de la cosecha) las contesta la accion misma.
        if (!answer.text().isBlank())
        {
            String text = clean(answer.text());
            speak(text, text, null);
        }
        this.chatter.activity(gameTime(), PersonalityProfile.current(), this.rocco.getRandom());

        RoccoActions.Outcome outcome = answer.verb() == null
                ? null
                : RoccoActions.run(this.rocco, player, answer.verb(), answer.argument() == null ? "" : answer.argument());
        reportOutcome(outcome);
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
        // La boca se mueve un rato proporcional a lo que dijo.
        this.rocco.talkFor(Math.min(200, 20 + chat.length()));
        broadcast(Component.literal(chat), impression, false);
        // Y en voz alta, si la voz esta configurada.
        com.cristoree.rocco.voice.RoccoVoice.speak(this.rocco, voice, impression);
    }

    // Si dijo que iba a hacer algo y no pudo, lo aclara en vez de quedarse quieto sin mas.
    private void reportOutcome(@Nullable RoccoActions.Outcome outcome)
    {
        if (outcome != null && !outcome.ok() && outcome.note() != null)
        {
            this.rocco.setExpression(RoccoExpression.SAD);
            tell(Component.literal(outcome.note() + "."), false);
        }
    }

    // Una sola linea, sin espacios dobles ni etiquetas sueltas: el chat no muestra saltos de linea.
    private String clean(String answer)
    {
        String out = answer.replaceAll("(?i)(?:@|\\[)(happy|bored|serious|scared|sad|angry|smug|psycho|dizzy)]?(?=\\W|$)", " ")
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

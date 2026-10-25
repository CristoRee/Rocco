package com.cristoree.rocco.ai;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.config.RoccoConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoExpression;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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
    private static final Pattern EXPRESSION_TAG = Pattern.compile("^\\s*\\[(\\w+)]\\s*");
    private static final int SHORT_HISTORY_CHARS = 140;
    private static final long PROBLEM_NOTE_EVERY_MS = 60_000L;
    private static final long STUCK_REQUEST_MS = 120_000L;

    private static final String GREET_PROMPT = "(Acabas de brotar de la tierra. Es la primera vez que ves al "
            + "jugador que la Madre Tierra te encargo cuidar. Presentate en una frase.)";

    private static ExecutorService executor;
    private static final AtomicInteger IN_FLIGHT = new AtomicInteger();
    private static final Deque<Long> RECENT_CALLS = new ArrayDeque<>();
    // A quien ya se le explico como conseguir una clave gratis en esta sesion.
    private static final Set<UUID> ONBOARDED = ConcurrentHashMap.newKeySet();

    private final RoccoEntity rocco;
    private final List<ChatTurn> history = new ArrayList<>();

    private boolean waiting;
    private long waitingSince;
    private boolean warnedRetiredModel;
    private long lastProblemNote;

    public RoccoBrain(RoccoEntity rocco)
    {
        this.rocco = rocco;
    }

    // Saludo al brotar: con IA lo inventa ella, sin IA usa una frase propia.
    public void greet(ServerPlayer player)
    {
        if (RoccoConfig.aiConfigured())
        {
            ask(player, GREET_PROMPT);
        }
        else if (RoccoCommonConfig.AI_OFFLINE_FALLBACK.get())
        {
            deliverOffline(player, OfflineRocco.greeting());
            offerFreeAi(player);
        }
    }

    public void ask(ServerPlayer player, String question)
    {
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
                tell(player, "no tengo ninguna IA configurada (Mods > Rocco > Config).", true);
            }
            return;
        }

        if (IN_FLIGHT.get() >= RoccoCommonConfig.MAX_CONCURRENT_REQUESTS.get())
        {
            RoccoMod.LOGGER.debug("[Rocco] Peticion descartada: ya hay {} en curso", IN_FLIGHT.get());
            return;
        }

        if (!allowedByRateLimit())
        {
            tell(player, "Estoy respondiendo demasiado seguido, espera un momento.", true);
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
            tell(player, retired, true);
        }

        String systemPrompt = RoccoPersonality.load() + RoccoContextBuilder.build(this.rocco, player);
        List<ChatTurn> snapshot = trimmedHistory();

        this.waiting = true;
        this.waitingSince = System.currentTimeMillis();
        IN_FLIGHT.incrementAndGet();

        // Rocco "piensa" mientras espera.
        this.rocco.setExpression(RoccoExpression.SERIOUS);

        executor().execute(() ->
        {
            ChatReply reply;
            try
            {
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
                    deliver(player, question, result.text());
                    return;
                }

                // Ninguna IA contesto: Rocco igual responde con lo suyo, y avisa por que.
                if (RoccoCommonConfig.AI_OFFLINE_FALLBACK.get())
                {
                    OfflineRocco.Answer fallback = question.equals(GREET_PROMPT)
                            ? OfflineRocco.greeting()
                            : OfflineRocco.reply(this.rocco, question);
                    deliverOffline(player, fallback);
                    noteProblem(player, result.error());
                }
                else
                {
                    this.rocco.setExpression(RoccoExpression.SAD);
                    tell(player, "no pude responder: " + result.error(), true);
                }
            });
        });
    }

    private void deliver(ServerPlayer player, String question, String rawAnswer)
    {
        // Primero se saca la orden de trabajo, asi no aparece en el chat.
        RoccoActions.Parsed parsed = RoccoActions.parse(rawAnswer);
        String answer = parsed.cleanText();
        boolean chose = false;

        Matcher tag = EXPRESSION_TAG.matcher(answer);
        if (tag.find())
        {
            String id = tag.group(1).toLowerCase(Locale.ROOT);
            // Solo las emocionales: hurt y sleep las pone el mod, no la IA.
            for (RoccoExpression expression : RoccoExpression.selectable())
            {
                if (expression.id().equals(id))
                {
                    this.rocco.setExpressionFromAi(expression);
                    answer = answer.substring(tag.end());
                    chose = true;
                    break;
                }
            }
        }

        // Si no eligio cara, se saca la de "pensando" que le puse al mandar la peticion.
        if (!chose)
        {
            this.rocco.setExpressionFromAi(RoccoExpression.HAPPY);
        }

        answer = clean(answer);
        if (!answer.isEmpty())
        {
            this.history.add(new ChatTurn(true, question));
            this.history.add(new ChatTurn(false, answer));
            speak(player, answer);
        }

        // Despues de hablar: si la accion dice algo al toque (una receta), sale en orden.
        String verb = parsed.verb();
        String argument = parsed.argument();

        // Las IAs a veces contestan "dale, me quedo quieto" y se olvidan la linea #accion (o
        // inventan un verbo). Si lo que pidio el jugador es una orden clara, se hace igual.
        if (!RoccoActions.knows(verb))
        {
            OfflineRocco.Answer understood = OfflineRocco.reply(this.rocco, question);
            if (understood.verb() != null)
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
        reportOutcome(player, outcome);
    }

    private void deliverOffline(ServerPlayer player, OfflineRocco.Answer answer)
    {
        this.rocco.setExpressionFromAi(answer.face());
        // Algunas ordenes (la receta, el cofre de la cosecha) las contesta la accion misma.
        if (!answer.text().isBlank())
        {
            speak(player, clean(answer.text()));
        }

        RoccoActions.Outcome outcome = answer.verb() == null
                ? null
                : RoccoActions.run(this.rocco, player, answer.verb(), answer.argument() == null ? "" : answer.argument());
        reportOutcome(player, outcome);
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

        // Se suma a la ultima respuesta suya: algunas IAs no aceptan dos turnos seguidos de Rocco.
        if (!this.history.isEmpty() && !this.history.get(this.history.size() - 1).fromPlayer())
        {
            ChatTurn last = this.history.remove(this.history.size() - 1);
            this.history.add(new ChatTurn(false, last.text() + " " + message));
        }

        broadcast(message, false);
        com.cristoree.rocco.voice.RoccoVoice.speak(this.rocco, message);
    }

    private void broadcast(String message, boolean problem)
    {
        if (!RoccoCommonConfig.CHAT_SHOW_IN_CHAT.get() && !problem)
        {
            return;
        }

        String name = RoccoCommonConfig.COMPANION_NAME.get();
        Component line = Component.literal("<" + name + "> ")
                .withStyle(problem ? ChatFormatting.RED : ChatFormatting.GREEN)
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE));

        double radius = RoccoCommonConfig.CHAT_HEARING_RADIUS.get();
        if (!(this.rocco.level() instanceof net.minecraft.server.level.ServerLevel level))
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

    private void speak(ServerPlayer player, String answer)
    {
        // La boca se mueve un rato proporcional a lo que dijo.
        this.rocco.talkFor(Math.min(200, 20 + answer.length()));
        tell(player, answer, false);
        // Y en voz alta, si Fish Audio esta configurado.
        com.cristoree.rocco.voice.RoccoVoice.speak(this.rocco, answer);
    }

    // Si dijo que iba a hacer algo y no pudo, lo aclara en vez de quedarse quieto sin mas.
    private void reportOutcome(ServerPlayer player, RoccoActions.Outcome outcome)
    {
        if (outcome != null && !outcome.ok() && outcome.note() != null)
        {
            this.rocco.setExpression(RoccoExpression.SAD);
            tell(player, outcome.note() + ".", false);
        }
    }

    private String clean(String answer)
    {
        // Una sola linea y sin espacios dobles: el chat no muestra saltos de linea. Las etiquetas
        // de cara que la IA deja en el medio del texto tampoco se muestran.
        String out = answer.replaceAll("(?i)\\[(happy|bored|serious|scared|sad|angry|smug|psycho|dizzy)]", " ")
                .replaceAll("\\s+", " ").trim();

        int limit = RoccoCommonConfig.CHAT_MAX_RESPONSE_CHARS.get();
        if (out.length() > limit)
        {
            out = out.substring(0, limit).trim() + "...";
        }
        return out;
    }

    // Avisa por que no hubo IA, como mucho una vez por minuto para no llenar el chat.
    private void noteProblem(ServerPlayer player, String error)
    {
        long now = System.currentTimeMillis();
        if (now - this.lastProblemNote < PROBLEM_NOTE_EVERY_MS)
        {
            return;
        }
        this.lastProblemNote = now;

        player.sendSystemMessage(Component.literal("[Rocco] Sin IA por ahora (" + error
                + "). Uso mis frases de fabrica. /rocco ia para ver el detalle.").withStyle(ChatFormatting.GRAY));
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

    private void tell(ServerPlayer player, String message, boolean problem)
    {
        broadcast(message, problem);
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

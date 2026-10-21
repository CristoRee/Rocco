package com.cristoree.rocco.ai;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoExpression;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RoccoBrain
{
    private static final Pattern EXPRESSION_TAG = Pattern.compile("^\\s*\\[(\\w+)]\\s*");
    private static final int SHORT_HISTORY_CHARS = 140;

    private static ExecutorService executor;
    private static final AtomicInteger IN_FLIGHT = new AtomicInteger();
    private static final Deque<Long> RECENT_CALLS = new ArrayDeque<>();

    private final RoccoEntity rocco;
    private final List<GeminiClient.Turn> history = new ArrayList<>();

    private boolean waiting;

    public RoccoBrain(RoccoEntity rocco)
    {
        this.rocco = rocco;
    }

    public void ask(ServerPlayer player, String question)
    {
        if (this.waiting)
        {
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

        String systemPrompt = RoccoPersonality.load()
                + RoccoContextBuilder.build(this.rocco, player);
        List<GeminiClient.Turn> snapshot = trimmedHistory();

        this.waiting = true;
        IN_FLIGHT.incrementAndGet();

        // Rocco "piensa" mientras espera: mueve la boca y despues dice lo que llego.
        this.rocco.setExpression(RoccoExpression.SERIOUS);

        executor().execute(() ->
        {
            GeminiClient.Reply reply = GeminiClient.ask(systemPrompt, snapshot, question);

            // Volver al hilo del servidor antes de tocar la entidad o el chat.
            server.execute(() ->
            {
                IN_FLIGHT.decrementAndGet();
                this.waiting = false;

                // Pudo morir o descargarse mientras esperabamos a Gemini.
                if (!this.rocco.isAlive())
                {
                    return;
                }

                if (!reply.ok())
                {
                    this.rocco.setExpression(RoccoExpression.SAD);
                    tell(player, "no pude responder: " + reply.error(), true);
                    return;
                }

                deliver(player, question, reply.text());
            });
        });
    }

    private void deliver(ServerPlayer player, String question, String rawAnswer)
    {
        String answer = rawAnswer;
        boolean chose = false;

        Matcher tag = EXPRESSION_TAG.matcher(answer);
        if (tag.find())
        {
            String id = tag.group(1).toLowerCase(Locale.ROOT);
            for (RoccoExpression expression : RoccoExpression.all())
            {
                if (expression.id().equals(id))
                {
                    this.rocco.setExpression(expression);
                    answer = answer.substring(tag.end());
                    chose = true;
                    break;
                }
            }
        }

        // Si no eligio cara, se saca la de "pensando" que le puse al mandar la peticion.
        // Una etiqueta desconocida se deja tal cual en el texto.
        if (!chose)
        {
            this.rocco.setExpression(RoccoExpression.HAPPY);
        }

        answer = answer.replace('\n', ' ').trim();

        int limit = RoccoCommonConfig.CHAT_MAX_RESPONSE_CHARS.get();
        if (answer.length() > limit)
        {
            answer = answer.substring(0, limit).trim() + "...";
        }

        if (answer.isEmpty())
        {
            return;
        }

        this.history.add(new GeminiClient.Turn(true, question));
        this.history.add(new GeminiClient.Turn(false, answer));

        // La boca se mueve un rato proporcional a lo que dijo.
        this.rocco.talkFor(Math.min(200, 20 + answer.length()));

        tell(player, answer, false);
    }

    private void tell(ServerPlayer player, String message, boolean problem)
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
        for (ServerPlayer nearby : player.serverLevel().players())
        {
            if (nearby.distanceToSqr(this.rocco) <= radius * radius)
            {
                nearby.sendSystemMessage(line);
            }
        }
    }

    // Los turnos viejos van recortados y los ultimos completos: acelera y abarata la peticion.
    private List<GeminiClient.Turn> trimmedHistory()
    {
        int maxTurns = RoccoCommonConfig.CHAT_HISTORY_TURNS.get() * 2;
        int fullMessages = RoccoCommonConfig.CHAT_FULL_DETAIL_TURNS.get() * 2;

        List<GeminiClient.Turn> recent = this.history.size() > maxTurns
                ? this.history.subList(this.history.size() - maxTurns, this.history.size())
                : this.history;

        List<GeminiClient.Turn> out = new ArrayList<>(recent.size());
        int firstFull = Math.max(0, recent.size() - fullMessages);

        for (int i = 0; i < recent.size(); i++)
        {
            GeminiClient.Turn turn = recent.get(i);
            if (i >= firstFull || turn.text().length() <= SHORT_HISTORY_CHARS)
            {
                out.add(turn);
            }
            else
            {
                out.add(new GeminiClient.Turn(turn.fromPlayer(),
                        turn.text().substring(0, SHORT_HISTORY_CHARS) + "..."));
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

package com.cristoree.rocco.truco;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoExpression;
import com.cristoree.rocco.entity.RoccoState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Antes de empezar (ROCCO-V14 7.1): el que pidio el truco elige las opciones y los demas a 12 bloques
// o menos se pueden sumar por chat durante 30 segundos. Un menu por Rocco.
public final class TrucoLobby
{
    private static final int JOIN_TICKS = 30 * 20;
    private static final int IDLE_TICKS = 3 * 60 * 20;
    private static final double INVITE_RANGE = 12.0D;
    static final int MAX_PLAYERS = 7;

    private static final Map<Integer, TrucoLobby> OPEN = new HashMap<>();

    final RoccoEntity rocco;
    final UUID host;
    final List<UUID> players = new ArrayList<>();
    // Variante, puntos, flor, nivel de Rocco, mate, equipos y que hacer con 2 jugadores.
    final int[] options = {0, 30, 1, 1, 0, 0, 0};
    private final long openedAt;

    private TrucoLobby(RoccoEntity rocco, ServerPlayer host)
    {
        this.rocco = rocco;
        this.host = host.getUUID();
        this.players.add(host.getUUID());
        this.openedAt = rocco.level().getGameTime();
    }

    // "Jugamos un truco?": abre el menu para el que lo pidio e invita a los de alrededor.
    public static boolean open(RoccoEntity rocco, ServerPlayer host)
    {
        if (rocco.getRoccoState() != RoccoState.ACTIVE || rocco.isTravelling() || rocco.isInWater() || rocco.isPassenger())
        {
            rocco.sayLine(RoccoExpression.THINK, "truco_not_here");
            return false;
        }
        if (rocco.truco() != null || TrucoSession.of(host) != null)
        {
            rocco.sayLine(RoccoExpression.SMUG, "truco_already");
            return false;
        }
        if (rocco.hostileNearby())
        {
            rocco.sayLine(RoccoExpression.SCARED, "cook_danger");
            return false;
        }
        TrucoLobby existing = OPEN.get(rocco.getId());
        if (existing != null && existing.alive())
        {
            existing.join(host);
            return true;
        }
        TrucoLobby lobby = new TrucoLobby(rocco, host);
        OPEN.put(rocco.getId(), lobby);
        rocco.cooking().stop(true);
        rocco.sayLine(RoccoExpression.CHEER, "truco_invite");
        lobby.invite(host);
        lobby.sync();
        return true;
    }

    // /rocco truco unirme: al menu abierto mas cercano (dentro de los 30 segundos).
    public static boolean joinNearest(ServerPlayer player)
    {
        TrucoLobby best = null;
        double bestDistance = Double.MAX_VALUE;
        for (TrucoLobby lobby : OPEN.values())
        {
            if (!lobby.alive() || lobby.rocco.level() != player.level())
            {
                continue;
            }
            double distance = lobby.rocco.distanceToSqr(player);
            if (distance < 24.0D * 24.0D && distance < bestDistance)
            {
                best = lobby;
                bestDistance = distance;
            }
        }
        if (best == null)
        {
            player.sendSystemMessage(Component.translatable("gui.rocco.truco.no_invite").withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (best.rocco.level().getGameTime() - best.openedAt > JOIN_TICKS && !best.players.contains(player.getUUID()))
        {
            player.sendSystemMessage(Component.translatable("gui.rocco.truco.too_late").withStyle(ChatFormatting.GRAY));
            return false;
        }
        return best.join(player);
    }

    @Nullable
    static TrucoLobby of(ServerPlayer player)
    {
        for (TrucoLobby lobby : OPEN.values())
        {
            if (lobby.players.contains(player.getUUID()))
            {
                return lobby;
            }
        }
        return null;
    }

    // Lo que toca el jugador en el menu.
    static void action(ServerPlayer player, int op, int value)
    {
        TrucoLobby lobby = of(player);
        if (lobby == null)
        {
            TrucoNetwork.send(player, new TrucoNetwork.LobbyState(-1, false, new int[TrucoNetwork.OPTIONS], List.of(), true));
            return;
        }
        boolean host = lobby.host.equals(player.getUUID());
        if (op == TrucoNetwork.OP_LEAVE || (op == TrucoNetwork.OP_CANCEL && !host))
        {
            lobby.leave(player);
            return;
        }
        if (!host)
        {
            return;
        }
        if (op == TrucoNetwork.OP_CANCEL)
        {
            lobby.rocco.sayLine(RoccoExpression.SAD, "truco_cancel");
            lobby.close();
            return;
        }
        if (op == TrucoNetwork.OP_START)
        {
            lobby.start(player);
            return;
        }
        if (op >= 0 && op < TrucoNetwork.OPTIONS)
        {
            lobby.options[op] = valid(op, value);
            // En el uruguayo la flor se juega siempre.
            if (lobby.options[TrucoNetwork.OPT_VARIANT] == 0)
            {
                lobby.options[TrucoNetwork.OPT_FLOR] = 1;
            }
            lobby.sync();
        }
    }

    private static int valid(int op, int value)
    {
        return switch (op)
        {
            case TrucoNetwork.OPT_POINTS -> value == 15 ? 15 : 30;
            case TrucoNetwork.OPT_LEVEL -> Math.max(0, Math.min(2, value));
            default -> value != 0 ? 1 : 0;
        };
    }

    private boolean alive()
    {
        long age = this.rocco.level().getGameTime() - this.openedAt;
        return this.rocco.isAlive() && !this.rocco.isRemoved() && age < IDLE_TICKS && host() != null;
    }

    @Nullable
    private ServerPlayer host()
    {
        return player(this.host);
    }

    @Nullable
    private ServerPlayer player(UUID id)
    {
        return this.rocco.level() instanceof ServerLevel level && level.getPlayerByUUID(id) instanceof ServerPlayer player
                ? player : null;
    }

    private void invite(ServerPlayer host)
    {
        Component button = Component.translatable("gui.rocco.truco.join").withStyle(Style.EMPTY
                .withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/rocco truco unirme"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("/rocco truco unirme"))));
        Component message = Component.translatable("gui.rocco.truco.invite").withStyle(ChatFormatting.YELLOW)
                .append(" ").append(button);
        for (ServerPlayer other : ((ServerLevel) this.rocco.level()).players())
        {
            if (other != host && other.distanceToSqr(this.rocco) <= INVITE_RANGE * INVITE_RANGE)
            {
                other.sendSystemMessage(message);
            }
        }
    }

    private boolean join(ServerPlayer player)
    {
        if (this.players.contains(player.getUUID()))
        {
            sync();
            return true;
        }
        if (this.players.size() >= MAX_PLAYERS)
        {
            player.sendSystemMessage(Component.translatable("gui.rocco.truco.full").withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (TrucoSession.of(player) != null || of(player) != null)
        {
            return false;
        }
        this.players.add(player.getUUID());
        Component joined = Component.translatable("gui.rocco.truco.joined", player.getDisplayName()).withStyle(ChatFormatting.GRAY);
        for (UUID id : this.players)
        {
            ServerPlayer member = player(id);
            if (member != null)
            {
                member.sendSystemMessage(joined);
            }
        }
        sync();
        return true;
    }

    private void leave(ServerPlayer player)
    {
        if (this.host.equals(player.getUUID()))
        {
            close();
            return;
        }
        this.players.remove(player.getUUID());
        TrucoNetwork.send(player, new TrucoNetwork.LobbyState(-1, false, this.options.clone(), List.of(), true));
        sync();
    }

    void close()
    {
        OPEN.remove(this.rocco.getId());
        for (UUID id : this.players)
        {
            ServerPlayer member = player(id);
            if (member != null)
            {
                TrucoNetwork.send(member, new TrucoNetwork.LobbyState(-1, false, this.options.clone(), List.of(), true));
            }
        }
    }

    private void sync()
    {
        List<String> names = new ArrayList<>();
        for (UUID id : this.players)
        {
            ServerPlayer member = player(id);
            names.add(member == null ? "?" : member.getGameProfile().name());
        }
        for (UUID id : this.players)
        {
            ServerPlayer member = player(id);
            if (member != null)
            {
                TrucoNetwork.send(member, new TrucoNetwork.LobbyState(this.rocco.getId(), id.equals(this.host),
                        this.options.clone(), names, false));
            }
        }
    }

    private void start(ServerPlayer host)
    {
        // Solo los que siguen conectados y cerca.
        List<ServerPlayer> seated = new ArrayList<>();
        for (UUID id : this.players)
        {
            ServerPlayer member = player(id);
            if (member != null && member.isAlive() && member.level() == this.rocco.level()
                    && member.distanceToSqr(this.rocco) < 32.0D * 32.0D && TrucoSession.of(member) == null)
            {
                seated.add(member);
            }
        }
        if (seated.isEmpty() || !seated.contains(host))
        {
            close();
            return;
        }
        if (this.rocco.truco() != null || this.rocco.getRoccoState() != RoccoState.ACTIVE)
        {
            close();
            return;
        }
        if (this.options[TrucoNetwork.OPT_TEAMS] == 1)
        {
            java.util.Collections.shuffle(seated, new java.util.Random(this.rocco.getRandom().nextLong()));
        }
        TrucoSession session = TrucoSession.create(this.rocco, host, seated, this.options.clone());
        if (session == null)
        {
            // No entra la mesa: el menu queda abierto para probar en otro lado.
            this.rocco.sayLine(RoccoExpression.THINK, "truco_no_room");
            return;
        }
        close();
    }

    // Limpia los menus de Rocco que ya no existen (lo llama el tick del servidor).
    static void tickAll()
    {
        OPEN.values().removeIf(lobby ->
        {
            if (lobby.alive())
            {
                return false;
            }
            for (UUID id : lobby.players)
            {
                ServerPlayer member = lobby.player(id);
                if (member != null)
                {
                    TrucoNetwork.send(member, new TrucoNetwork.LobbyState(-1, false, lobby.options.clone(), List.of(), true));
                }
            }
            return true;
        });
    }

    static void clearAll()
    {
        OPEN.clear();
    }
}

package com.cristoree.rocco.truco;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

// Los mensajes del truco (ROCCO-V14 7.6), en el mismo canal que el resto del mod.
public final class TrucoNetwork
{
    // Opciones del menu de la partida, en este orden.
    public static final int OPT_VARIANT = 0;
    public static final int OPT_POINTS = 1;
    public static final int OPT_FLOR = 2;
    public static final int OPT_LEVEL = 3;
    public static final int OPT_MATE = 4;
    public static final int OPT_TEAMS = 5;
    public static final int OPT_TWO = 6;
    public static final int OPTIONS = 7;

    public static final int OP_START = 10;
    public static final int OP_CANCEL = 11;
    public static final int OP_LEAVE = 12;

    private static SimpleChannel channel;

    // ---------- del servidor al jugador ----------

    // El menu antes de empezar: las opciones (solo las cambia el que invito) y quienes se sumaron.
    public record LobbyState(int rocco, boolean host, int[] options, List<String> players, boolean closed)
    {
        static void encode(LobbyState msg, FriendlyByteBuf buf)
        {
            buf.writeVarInt(msg.rocco);
            buf.writeBoolean(msg.host);
            buf.writeVarIntArray(msg.options);
            buf.writeCollection(msg.players, (out, name) -> out.writeUtf(name, 64));
            buf.writeBoolean(msg.closed);
        }

        static LobbyState decode(FriendlyByteBuf buf)
        {
            return new LobbyState(buf.readVarInt(), buf.readBoolean(), buf.readVarIntArray(16),
                    buf.readList(in -> in.readUtf(64)), buf.readBoolean());
        }
    }

    // Todo lo que ve un jugador sentado: lo publico de la mesa y sus propias cartas.
    public record TableState(int table, int n, int mySeat, int roccoSeat, boolean manoSolo, List<String> names,
                             int[] teams, int[] scores, int target, int muestra, int[] played, int[] tricks, int[] myCards,
                             int[] cardsLeft, int phase, int turn, int mano, int dealer, int pending, int caller,
                             int trucoLevel, int handValue, int hand, int[] legal, List<Component> log, boolean mate,
                             boolean thinking, Component banner, int bannerId, int bannerTeam, boolean blocked)
    {
        static void encode(TableState msg, FriendlyByteBuf buf)
        {
            buf.writeVarInt(msg.table);
            buf.writeVarInt(msg.n);
            buf.writeVarInt(msg.mySeat + 1);
            buf.writeVarInt(msg.roccoSeat + 1);
            buf.writeBoolean(msg.manoSolo);
            buf.writeCollection(msg.names, (out, name) -> out.writeUtf(name, 64));
            buf.writeVarIntArray(msg.teams);
            buf.writeVarIntArray(msg.scores);
            buf.writeVarInt(msg.target);
            buf.writeVarInt(msg.muestra + 1);
            writeCards(buf, msg.played);
            writeSigned(buf, msg.tricks);
            writeCards(buf, msg.myCards);
            buf.writeVarIntArray(msg.cardsLeft);
            buf.writeVarInt(msg.phase);
            buf.writeVarInt(msg.turn + 1);
            buf.writeVarInt(msg.mano);
            buf.writeVarInt(msg.dealer);
            buf.writeVarInt(msg.pending);
            buf.writeVarInt(msg.caller + 1);
            buf.writeVarInt(msg.trucoLevel);
            buf.writeVarInt(msg.handValue);
            buf.writeVarInt(msg.hand);
            writeSigned(buf, msg.legal);
            buf.writeCollection(msg.log, FriendlyByteBuf::writeComponent);
            buf.writeBoolean(msg.mate);
            buf.writeBoolean(msg.thinking);
            buf.writeComponent(msg.banner);
            buf.writeVarInt(msg.bannerId);
            buf.writeVarInt(msg.bannerTeam + 1);
            buf.writeBoolean(msg.blocked);
        }

        static TableState decode(FriendlyByteBuf buf)
        {
            int table = buf.readVarInt();
            int n = buf.readVarInt();
            int mySeat = buf.readVarInt() - 1;
            int roccoSeat = buf.readVarInt() - 1;
            boolean manoSolo = buf.readBoolean();
            List<String> names = buf.readList(in -> in.readUtf(64));
            int[] teams = buf.readVarIntArray(16);
            int[] scores = buf.readVarIntArray(16);
            int target = buf.readVarInt();
            int muestra = buf.readVarInt() - 1;
            int[] played = readCards(buf);
            int[] tricks = readSigned(buf);
            int[] myCards = readCards(buf);
            int[] cardsLeft = buf.readVarIntArray(16);
            int phase = buf.readVarInt();
            int turn = buf.readVarInt() - 1;
            int mano = buf.readVarInt();
            int dealer = buf.readVarInt();
            int pending = buf.readVarInt();
            int caller = buf.readVarInt() - 1;
            int trucoLevel = buf.readVarInt();
            int handValue = buf.readVarInt();
            int hand = buf.readVarInt();
            int[] legal = readSigned(buf);
            List<Component> log = buf.readList(FriendlyByteBuf::readComponent);
            boolean mate = buf.readBoolean();
            boolean thinking = buf.readBoolean();
            Component banner = buf.readComponent();
            int bannerId = buf.readVarInt();
            int bannerTeam = buf.readVarInt() - 1;
            boolean blocked = buf.readBoolean();
            return new TableState(table, n, mySeat, roccoSeat, manoSolo, names, teams, scores, target, muestra, played,
                    tricks, myCards, cardsLeft, phase, turn, mano, dealer, pending, caller, trucoLevel, handValue, hand,
                    legal, log, mate, thinking, banner, bannerId, bannerTeam, blocked);
        }
    }

    // Abrir la pantalla de la mesa (y mirar al centro de la mesa).
    public record Open(int table, double x, double y, double z)
    {
        static void encode(Open msg, FriendlyByteBuf buf)
        {
            buf.writeVarInt(msg.table);
            buf.writeDouble(msg.x);
            buf.writeDouble(msg.y);
            buf.writeDouble(msg.z);
        }

        static Open decode(FriendlyByteBuf buf)
        {
            return new Open(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble());
        }
    }

    public record Close()
    {
        static void encode(Close msg, FriendlyByteBuf buf)
        {
        }

        static Close decode(FriendlyByteBuf buf)
        {
            return new Close();
        }
    }

    // ---------- del jugador al servidor ----------

    public record LobbyAction(int op, int value)
    {
        static void encode(LobbyAction msg, FriendlyByteBuf buf)
        {
            buf.writeVarInt(msg.op);
            buf.writeVarInt(msg.value);
        }

        static LobbyAction decode(FriendlyByteBuf buf)
        {
            return new LobbyAction(buf.readVarInt(), buf.readVarInt());
        }
    }

    public record Play(int type, int card)
    {
        static void encode(Play msg, FriendlyByteBuf buf)
        {
            buf.writeVarInt(msg.type);
            buf.writeVarInt(msg.card + 1);
        }

        static Play decode(FriendlyByteBuf buf)
        {
            return new Play(buf.readVarInt(), buf.readVarInt() - 1);
        }
    }

    public record TakeMate()
    {
        static void encode(TakeMate msg, FriendlyByteBuf buf)
        {
        }

        static TakeMate decode(FriendlyByteBuf buf)
        {
            return new TakeMate();
        }
    }

    // ---------- registro ----------

    public static void register(SimpleChannel target, int first)
    {
        channel = target;
        int id = first;
        target.registerMessage(id++, LobbyState.class, LobbyState::encode, LobbyState::decode, TrucoNetwork::onLobbyState,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        target.registerMessage(id++, TableState.class, TableState::encode, TableState::decode, TrucoNetwork::onTableState,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        target.registerMessage(id++, Open.class, Open::encode, Open::decode, TrucoNetwork::onOpen,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        target.registerMessage(id++, Close.class, Close::encode, Close::decode, TrucoNetwork::onClose,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        target.registerMessage(id++, LobbyAction.class, LobbyAction::encode, LobbyAction::decode, TrucoNetwork::onLobbyAction,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        target.registerMessage(id++, Play.class, Play::encode, Play::decode, TrucoNetwork::onPlay,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        target.registerMessage(id, TakeMate.class, TakeMate::encode, TakeMate::decode, TrucoNetwork::onTakeMate,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void send(ServerPlayer player, Object msg)
    {
        if (channel != null)
        {
            channel.send(PacketDistributor.PLAYER.with(() -> player), msg);
        }
    }

    public static void toServer(Object msg)
    {
        if (channel != null)
        {
            channel.sendToServer(msg);
        }
    }

    private static void onLobbyState(LobbyState msg, Supplier<NetworkEvent.Context> context)
    {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.cristoree.rocco.client.truco.TrucoClient.lobby(msg)));
        context.get().setPacketHandled(true);
    }

    private static void onTableState(TableState msg, Supplier<NetworkEvent.Context> context)
    {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.cristoree.rocco.client.truco.TrucoClient.state(msg)));
        context.get().setPacketHandled(true);
    }

    private static void onOpen(Open msg, Supplier<NetworkEvent.Context> context)
    {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.cristoree.rocco.client.truco.TrucoClient.open(msg)));
        context.get().setPacketHandled(true);
    }

    private static void onClose(Close msg, Supplier<NetworkEvent.Context> context)
    {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.cristoree.rocco.client.truco.TrucoClient.close()));
        context.get().setPacketHandled(true);
    }

    private static void onLobbyAction(LobbyAction msg, Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer sender = context.get().getSender();
        context.get().enqueueWork(() ->
        {
            if (sender != null)
            {
                TrucoLobby.action(sender, msg.op, msg.value);
            }
        });
        context.get().setPacketHandled(true);
    }

    private static void onPlay(Play msg, Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer sender = context.get().getSender();
        context.get().enqueueWork(() ->
        {
            TrucoSession session = sender == null ? null : TrucoSession.of(sender);
            if (session != null)
            {
                session.humanAction(sender, msg.type, msg.card);
            }
        });
        context.get().setPacketHandled(true);
    }

    private static void onTakeMate(TakeMate msg, Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer sender = context.get().getSender();
        context.get().enqueueWork(() ->
        {
            TrucoSession session = sender == null ? null : TrucoSession.of(sender);
            if (session != null)
            {
                session.takeMate(sender);
            }
        });
        context.get().setPacketHandled(true);
    }

    // ---------- ayudas ----------

    // Ids de carta (0..39) o -1, en un solo byte cada uno.
    private static void writeCards(FriendlyByteBuf buf, int[] cards)
    {
        buf.writeVarInt(cards.length);
        for (int card : cards)
        {
            buf.writeByte(card + 1);
        }
    }

    private static int[] readCards(FriendlyByteBuf buf)
    {
        int length = Math.min(64, buf.readVarInt());
        int[] out = new int[length];
        for (int i = 0; i < length; i++)
        {
            out[i] = buf.readUnsignedByte() - 1;
        }
        return out;
    }

    private static void writeSigned(FriendlyByteBuf buf, int[] values)
    {
        buf.writeVarInt(values.length);
        for (int value : values)
        {
            buf.writeVarInt(value + 8);
        }
    }

    private static int[] readSigned(FriendlyByteBuf buf)
    {
        int length = Math.min(128, buf.readVarInt());
        int[] out = new int[length];
        for (int i = 0; i < length; i++)
        {
            out[i] = buf.readVarInt() - 8;
        }
        return out;
    }

    static List<Component> copy(List<Component> log)
    {
        return new ArrayList<>(log);
    }

    private TrucoNetwork() {}
}

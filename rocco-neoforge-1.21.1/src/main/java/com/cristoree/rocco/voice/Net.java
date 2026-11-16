package com.cristoree.rocco.voice;

import com.cristoree.rocco.RoccoMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

// Los mensajes del mod son records con encode/decode. NeoForge pide un "payload" por mensaje:
// esto envuelve cada record en uno, asi los mensajes y sus manejadores quedan como estaban.
public final class Net
{
    public enum Direction { PLAY_TO_CLIENT, PLAY_TO_SERVER }

    // El mensaje envuelto, con el tipo que lo identifica en la red.
    public record Packet<T>(CustomPacketPayload.Type<Packet<T>> type, T msg) implements CustomPacketPayload {}

    private record Entry<T>(Class<T> clazz, CustomPacketPayload.Type<Packet<T>> type, Direction direction,
                            BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder,
                            BiConsumer<T, Context> handler) {}

    // Lo que necesitan los manejadores: el jugador que lo mando y volver al hilo del juego.
    public static final class Context
    {
        private final IPayloadContext context;

        Context(IPayloadContext context)
        {
            this.context = context;
        }

        public void enqueueWork(Runnable work)
        {
            this.context.enqueueWork(work);
        }

        // El jugador que mando el mensaje (null si lo mando el servidor).
        public ServerPlayer getSender()
        {
            return this.context.player() instanceof ServerPlayer player ? player : null;
        }

        public void setPacketHandled(boolean handled) {}
    }

    // Un canal: junta los mensajes y los registra cuando NeoForge lo pide.
    public static final class Channel
    {
        private final String version;
        private final List<Entry<?>> entries = new ArrayList<>();
        private final Map<Class<?>, Entry<?>> byClass = new HashMap<>();

        public Channel(String version)
        {
            this.version = version;
        }

        public <T> Builder<T> messageBuilder(Class<T> clazz, int id, Direction direction)
        {
            return new Builder<>(this, clazz, id, direction);
        }

        public void register(RegisterPayloadHandlersEvent event)
        {
            // optional: un jugador con Rocco puede entrar a servidores que no lo tienen.
            PayloadRegistrar registrar = event.registrar(this.version).optional().executesOn(HandlerThread.NETWORK);
            for (Entry<?> entry : this.entries)
            {
                registerOne(registrar, entry);
            }
        }

        private static <T> void registerOne(PayloadRegistrar registrar, Entry<T> entry)
        {
            StreamCodec<RegistryFriendlyByteBuf, Packet<T>> codec = StreamCodec.of(
                    (buf, packet) -> entry.encoder().accept(packet.msg(), buf),
                    buf -> new Packet<>(entry.type(), entry.decoder().apply(buf)));
            if (entry.direction() == Direction.PLAY_TO_CLIENT)
            {
                registrar.playToClient(entry.type(), codec, (packet, context) -> entry.handler().accept(packet.msg(), new Context(context)));
            }
            else
            {
                registrar.playToServer(entry.type(), codec, (packet, context) -> entry.handler().accept(packet.msg(), new Context(context)));
            }
        }

        @SuppressWarnings("unchecked")
        private <T> Packet<T> wrap(T msg)
        {
            Entry<T> entry = (Entry<T>) this.byClass.get(msg.getClass());
            if (entry == null)
            {
                throw new IllegalArgumentException("Mensaje sin registrar: " + msg.getClass().getName());
            }
            return new Packet<>(entry.type(), msg);
        }

        // El otro lado tiene el canal de Rocco (un servidor sin el mod no lo tiene).
        public boolean isRemotePresent(net.neoforged.neoforge.common.extensions.ICommonPacketListener listener)
        {
            return !this.entries.isEmpty() && listener.hasChannel(this.entries.get(0).type());
        }

        public void send(Object msg, Target target)
        {
            target.send(wrap(msg));
        }
    }

    public static final class Builder<T>
    {
        private final Channel channel;
        private final Class<T> clazz;
        private final int id;
        private final Direction direction;
        private BiConsumer<T, FriendlyByteBuf> encoder;
        private Function<FriendlyByteBuf, T> decoder;
        private BiConsumer<T, Context> handler;

        Builder(Channel channel, Class<T> clazz, int id, Direction direction)
        {
            this.channel = channel;
            this.clazz = clazz;
            this.id = id;
            this.direction = direction;
        }

        public Builder<T> encoder(BiConsumer<T, FriendlyByteBuf> encoder) { this.encoder = encoder; return this; }
        public Builder<T> decoder(Function<FriendlyByteBuf, T> decoder) { this.decoder = decoder; return this; }
        public Builder<T> consumerNetworkThread(BiConsumer<T, Context> handler) { this.handler = handler; return this; }

        public void add()
        {
            CustomPacketPayload.Type<Packet<T>> type = new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(RoccoMod.MODID, "m" + this.id));
            Entry<T> entry = new Entry<>(this.clazz, type, this.direction, this.encoder, this.decoder, this.handler);
            this.channel.entries.add(entry);
            this.channel.byClass.put(this.clazz, entry);
        }
    }

    // A quien va un mensaje.
    @FunctionalInterface
    public interface Target
    {
        void send(CustomPacketPayload payload);
    }

    public static final class ToPlayer
    {
        public Target with(ServerPlayer player)
        {
            return payload -> PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static final class ToTracking
    {
        public Target with(Entity entity)
        {
            return payload -> PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
        }
    }

    public static final class ToServer
    {
        public Target noArg()
        {
            return payload -> PacketDistributor.sendToServer(payload);
        }
    }

    public static final ToPlayer PLAYER = new ToPlayer();
    public static final ToTracking TRACKING_ENTITY_AND_SELF = new ToTracking();
    public static final ToServer SERVER = new ToServer();

    // Corre algo solo en el cliente. La doble lambda evita que el servidor cargue clases de interfaz.
    public static void unsafeRunWhenOn(Dist dist, Supplier<Runnable> what)
    {
        if (FMLEnvironment.dist == dist)
        {
            what.get().run();
        }
    }

    private Net() {}
}

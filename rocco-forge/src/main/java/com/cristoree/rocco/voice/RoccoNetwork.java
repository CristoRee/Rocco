package com.cristoree.rocco.voice;

import com.cristoree.rocco.RoccoMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

// El canal del mod: la voz de Rocco viaja del servidor a los jugadores, y lo que dice el jugador
// por microfono viaja al servidor. El audio va en pedazos chicos porque Minecraft limita el
// tamaño de cada paquete (del jugador al servidor, a 32 KB).
public final class RoccoNetwork
{
    // Sube cuando cambian los mensajes: un cliente y un servidor con versiones distintas de Rocco
    // se avisan al conectar en vez de fallar a mitad de partida. 2: se agrego OpenSetup.
    // 3: se agrego VoiceLines (las frases grabadas). 4: el menu de Rocco (OpenMenu y MenuAction).
    // 5: CallRocco (traerlo desde la config o con su tecla). 6: la musica desde links.
    // 7: el eructo de la Roco Cola.
    private static final String PROTOCOL = "7";
    public static final int CHUNK_BYTES = 30_000;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RoccoMod.MODID, "main"), () -> PROTOCOL,
            // El jugador con el mod puede entrar a servidores que no lo tienen (ahi Rocco no existe y
            // el canal no se usa). Un servidor con Rocco si necesita el mod en el jugador.
            NetworkRegistry.acceptMissingOr(PROTOCOL), PROTOCOL::equals);

    // Un pedazo de la voz de Rocco: PCM de 16 bits mono, a 'sampleRate'.
    public record VoiceChunk(int stream, int entityId, int sampleRate, int index, int count, byte[] data)
    {
        static void encode(VoiceChunk msg, FriendlyByteBuf buf)
        {
            buf.writeVarInt(msg.stream);
            buf.writeVarInt(msg.entityId);
            buf.writeVarInt(msg.sampleRate);
            buf.writeVarInt(msg.index);
            buf.writeVarInt(msg.count);
            buf.writeByteArray(msg.data);
        }

        static VoiceChunk decode(FriendlyByteBuf buf)
        {
            return new VoiceChunk(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readByteArray(CHUNK_BYTES + 64));
        }
    }

    // Un pedazo de lo que grabo el microfono del jugador (PCM 16 bits mono, 16 kHz).
    public record MicChunk(int session, int index, boolean last, byte[] data)
    {
        static void encode(MicChunk msg, FriendlyByteBuf buf)
        {
            buf.writeVarInt(msg.session);
            buf.writeVarInt(msg.index);
            buf.writeBoolean(msg.last);
            buf.writeByteArray(msg.data);
        }

        static MicChunk decode(FriendlyByteBuf buf)
        {
            return new MicChunk(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readByteArray(CHUNK_BYTES + 64));
        }
    }

    // Frases grabadas que Rocco dice en voz alta, en orden: el audio ya esta en el mod de cada
    // jugador (assets/rocco/voice_lines/<codigo>.ogg), asi que solo viajan los codigos.
    public record VoiceLines(int entityId, java.util.List<String> ids)
    {
        static void encode(VoiceLines msg, FriendlyByteBuf buf)
        {
            buf.writeVarInt(msg.entityId);
            buf.writeCollection(msg.ids, (out, id) -> out.writeUtf(id, 96));
        }

        static VoiceLines decode(FriendlyByteBuf buf)
        {
            return new VoiceLines(buf.readVarInt(), buf.readList(in -> in.readUtf(96)));
        }
    }

    // Agachado + clic derecho: el menu de Rocco, con lo que esta haciendo para que cada boton diga
    // lo que corresponde ("Dejar de seguir" o "Seguirme"). flags: ver RoccoMenu.
    public record OpenMenu(int entityId, String status, int flags, String mode, String bond, float bondProgress,
                           String magic, java.util.List<String> places)
    {
        static void encode(OpenMenu msg, FriendlyByteBuf buf)
        {
            buf.writeVarInt(msg.entityId);
            buf.writeUtf(msg.status, 128);
            buf.writeVarInt(msg.flags);
            buf.writeUtf(msg.mode, 32);
            buf.writeUtf(msg.bond, 32);
            buf.writeFloat(msg.bondProgress);
            buf.writeUtf(msg.magic, 32);
            buf.writeCollection(msg.places, (out, place) -> out.writeUtf(place, 64));
        }

        static OpenMenu decode(FriendlyByteBuf buf)
        {
            return new OpenMenu(buf.readVarInt(), buf.readUtf(128), buf.readVarInt(), buf.readUtf(32), buf.readUtf(32),
                    buf.readFloat(), buf.readUtf(32), buf.readList(in -> in.readUtf(64)));
        }
    }

    // Lo que el jugador eligio en el menu ("seguir", "ir_a" + "casa"...).
    public record MenuAction(int entityId, String action, String argument)
    {
        static void encode(MenuAction msg, FriendlyByteBuf buf)
        {
            buf.writeVarInt(msg.entityId);
            buf.writeUtf(msg.action, 32);
            buf.writeUtf(msg.argument, 64);
        }

        static MenuAction decode(FriendlyByteBuf buf)
        {
            return new MenuAction(buf.readVarInt(), buf.readUtf(32), buf.readUtf(64));
        }
    }

    // "Traer a Rocco": el boton de la config o la tecla. Lo trae esté donde esté (como /rocco llamar).
    public record CallRocco()
    {
        static void encode(CallRocco msg, FriendlyByteBuf buf)
        {
        }

        static CallRocco decode(FriendlyByteBuf buf)
        {
            return new CallRocco();
        }
    }

    // El eructo de un jugador: el viento y las hojas los dibuja cada cliente (RoccoBurpFx).
    public record BurpFx(int entityId, float x, float y, float z)
    {
        static void encode(BurpFx msg, FriendlyByteBuf buf)
        {
            buf.writeVarInt(msg.entityId);
            buf.writeFloat(msg.x);
            buf.writeFloat(msg.y);
            buf.writeFloat(msg.z);
        }

        static BurpFx decode(FriendlyByteBuf buf)
        {
            return new BurpFx(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat());
        }
    }

    public static void sendBurp(ServerPlayer player, BurpFx msg)
    {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), msg);
    }

    private static void handleBurp(BurpFx msg, Supplier<NetworkEvent.Context> context)
    {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.cristoree.rocco.client.RoccoBurpFx.start(msg)));
        context.get().setPacketHandled(true);
    }

    // ---------- musica (ver RoccoMusic) ----------

    // Al jugador que pidio la cancion: "abri este link y decime si se puede escuchar".
    public record MusicResolve(int request, String url)
    {
        static void encode(MusicResolve msg, FriendlyByteBuf buf)
        {
            buf.writeVarInt(msg.request);
            buf.writeUtf(msg.url, 600);
        }

        static MusicResolve decode(FriendlyByteBuf buf)
        {
            return new MusicResolve(buf.readVarInt(), buf.readUtf(600));
        }
    }

    // La respuesta: si suena, el link final, el titulo y cuanto dura (0 si es una radio). Si no, por que.
    public record MusicResolved(int request, boolean ok, String url, String title, long durationMs, String error)
    {
        static void encode(MusicResolved msg, FriendlyByteBuf buf)
        {
            buf.writeVarInt(msg.request);
            buf.writeBoolean(msg.ok);
            buf.writeUtf(msg.url, 600);
            buf.writeUtf(msg.title, 200);
            buf.writeVarLong(Math.max(0L, msg.durationMs));
            buf.writeUtf(msg.error, 300);
        }

        static MusicResolved decode(FriendlyByteBuf buf)
        {
            return new MusicResolved(buf.readVarInt(), buf.readBoolean(), buf.readUtf(600), buf.readUtf(200),
                    buf.readVarLong(), buf.readUtf(300));
        }
    }

    // A todos los de cerca: "este Rocco esta tocando esto desde el tick tal".
    public record MusicPlay(int entityId, int session, String url, String title, long durationMs, long startTick)
    {
        static void encode(MusicPlay msg, FriendlyByteBuf buf)
        {
            buf.writeVarInt(msg.entityId);
            buf.writeVarInt(msg.session);
            buf.writeUtf(msg.url, 600);
            buf.writeUtf(msg.title, 200);
            buf.writeVarLong(Math.max(0L, msg.durationMs));
            buf.writeLong(msg.startTick);
        }

        static MusicPlay decode(FriendlyByteBuf buf)
        {
            return new MusicPlay(buf.readVarInt(), buf.readVarInt(), buf.readUtf(600), buf.readUtf(200),
                    buf.readVarLong(), buf.readLong());
        }
    }

    public record MusicStop(int entityId, int session)
    {
        static void encode(MusicStop msg, FriendlyByteBuf buf)
        {
            buf.writeVarInt(msg.entityId);
            buf.writeVarInt(msg.session);
        }

        static MusicStop decode(FriendlyByteBuf buf)
        {
            return new MusicStop(buf.readVarInt(), buf.readVarInt());
        }
    }

    // "Subí el volumen": cambia el volumen de la musica de Rocco en la config de ese jugador.
    public record MusicVolume(float delta)
    {
        static void encode(MusicVolume msg, FriendlyByteBuf buf)
        {
            buf.writeFloat(msg.delta);
        }

        static MusicVolume decode(FriendlyByteBuf buf)
        {
            return new MusicVolume(buf.readFloat());
        }
    }

    // "/rocco setup": que el jugador abra la pantalla de bienvenida (los comandos corren en el servidor).
    public record OpenSetup()
    {
        static void encode(OpenSetup msg, FriendlyByteBuf buf)
        {
        }

        static OpenSetup decode(FriendlyByteBuf buf)
        {
            return new OpenSetup();
        }
    }

    public static void register()
    {
        CHANNEL.registerMessage(0, VoiceChunk.class, VoiceChunk::encode, VoiceChunk::decode,
                RoccoNetwork::onVoiceChunk, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, MicChunk.class, MicChunk::encode, MicChunk::decode,
                RoccoNetwork::onMicChunk, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, OpenSetup.class, OpenSetup::encode, OpenSetup::decode,
                RoccoNetwork::onOpenSetup, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, VoiceLines.class, VoiceLines::encode, VoiceLines::decode,
                RoccoNetwork::onVoiceLines, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4, OpenMenu.class, OpenMenu::encode, OpenMenu::decode,
                RoccoNetwork::onOpenMenu, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(5, MenuAction.class, MenuAction::encode, MenuAction::decode,
                RoccoNetwork::onMenuAction, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(6, CallRocco.class, CallRocco::encode, CallRocco::decode,
                RoccoNetwork::onCallRocco, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(7, MusicResolve.class, MusicResolve::encode, MusicResolve::decode,
                RoccoNetwork::onMusicResolve, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(8, MusicResolved.class, MusicResolved::encode, MusicResolved::decode,
                RoccoNetwork::onMusicResolved, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(9, MusicPlay.class, MusicPlay::encode, MusicPlay::decode,
                RoccoNetwork::onMusicPlay, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(10, MusicStop.class, MusicStop::encode, MusicStop::decode,
                RoccoNetwork::onMusicStop, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(12, BurpFx.class, BurpFx::encode, BurpFx::decode, RoccoNetwork::handleBurp,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(11, MusicVolume.class, MusicVolume::encode, MusicVolume::decode,
                RoccoNetwork::onMusicVolume, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendMusicResolve(ServerPlayer player, MusicResolve msg)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendMusicResolved(MusicResolved msg)
    {
        CHANNEL.sendToServer(msg);
    }

    public static void sendMusicPlay(ServerPlayer player, MusicPlay msg)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendMusicStop(ServerPlayer player, MusicStop msg)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendMusicVolume(ServerPlayer player, MusicVolume msg)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    private static void onMusicResolve(MusicResolve msg, Supplier<NetworkEvent.Context> context)
    {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.cristoree.rocco.client.music.RoccoMusicClient.resolve(msg)));
        context.get().setPacketHandled(true);
    }

    private static void onMusicResolved(MusicResolved msg, Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer sender = context.get().getSender();
        context.get().enqueueWork(() ->
        {
            if (sender != null)
            {
                com.cristoree.rocco.music.RoccoMusic.onResolved(sender, msg);
            }
        });
        context.get().setPacketHandled(true);
    }

    private static void onMusicPlay(MusicPlay msg, Supplier<NetworkEvent.Context> context)
    {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.cristoree.rocco.client.music.RoccoMusicClient.play(msg)));
        context.get().setPacketHandled(true);
    }

    private static void onMusicStop(MusicStop msg, Supplier<NetworkEvent.Context> context)
    {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.cristoree.rocco.client.music.RoccoMusicClient.stop(msg)));
        context.get().setPacketHandled(true);
    }

    private static void onMusicVolume(MusicVolume msg, Supplier<NetworkEvent.Context> context)
    {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.cristoree.rocco.client.music.RoccoMusicClient.changeVolume(msg.delta())));
        context.get().setPacketHandled(true);
    }

    public static void callRocco()
    {
        CHANNEL.sendToServer(new CallRocco());
    }

    private static void onCallRocco(CallRocco msg, Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer sender = context.get().getSender();
        context.get().enqueueWork(() ->
        {
            if (sender != null)
            {
                com.cristoree.rocco.RoccoCompanionTracker.callFromButton(sender);
            }
        });
        context.get().setPacketHandled(true);
    }

    public static void openMenu(ServerPlayer player, OpenMenu menu)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), menu);
    }

    private static void onOpenMenu(OpenMenu msg, Supplier<NetworkEvent.Context> context)
    {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.cristoree.rocco.client.menu.RoccoMenuScreen.open(msg)));
        context.get().setPacketHandled(true);
    }

    public static void sendMenuAction(MenuAction action)
    {
        CHANNEL.sendToServer(action);
    }

    private static void onMenuAction(MenuAction msg, Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer sender = context.get().getSender();
        context.get().enqueueWork(() ->
        {
            if (sender != null)
            {
                com.cristoree.rocco.RoccoMenu.handle(sender, msg);
            }
        });
        context.get().setPacketHandled(true);
    }

    public static void sendLines(ServerPlayer player, VoiceLines lines)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), lines);
    }

    private static void onVoiceLines(VoiceLines msg, Supplier<NetworkEvent.Context> context)
    {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.cristoree.rocco.client.voice.VoicePlayback.playLines(msg.entityId(), msg.ids())));
        context.get().setPacketHandled(true);
    }

    public static void openSetup(ServerPlayer player)
    {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), new OpenSetup());
    }

    private static void onOpenSetup(OpenSetup msg, Supplier<NetworkEvent.Context> context)
    {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.cristoree.rocco.client.setup.RoccoSetup.open()));
        context.get().setPacketHandled(true);
    }

    private static void onVoiceChunk(VoiceChunk msg, Supplier<NetworkEvent.Context> context)
    {
        // El servidor dedicado no tiene clases de audio: solo se toca el cliente si existe.
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.cristoree.rocco.client.voice.VoicePlayback.receive(msg)));
        context.get().setPacketHandled(true);
    }

    private static void onMicChunk(MicChunk msg, Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer sender = context.get().getSender();
        context.get().enqueueWork(() ->
        {
            if (sender != null)
            {
                MicReceiver.receive(sender, msg);
            }
        });
        context.get().setPacketHandled(true);
    }

    public static void sendVoice(ServerPlayer player, VoiceChunk chunk)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), chunk);
    }

    public static void sendMic(MicChunk chunk)
    {
        CHANNEL.sendToServer(chunk);
    }

    private RoccoNetwork() {}
}

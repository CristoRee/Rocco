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
    private static final String PROTOCOL = "1";
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

    public static void register()
    {
        CHANNEL.registerMessage(0, VoiceChunk.class, VoiceChunk::encode, VoiceChunk::decode,
                RoccoNetwork::onVoiceChunk, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, MicChunk.class, MicChunk::encode, MicChunk::decode,
                RoccoNetwork::onMicChunk, Optional.of(NetworkDirection.PLAY_TO_SERVER));
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

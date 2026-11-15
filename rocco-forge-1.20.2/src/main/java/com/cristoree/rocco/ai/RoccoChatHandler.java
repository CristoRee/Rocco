package com.cristoree.rocco.ai;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.config.RoccoConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = RoccoMod.MODID)
public final class RoccoChatHandler
{
    private static final int REPLY_DELAY_TICKS = 2;

    private record Heard(java.util.UUID player, String message, int dueTick) {}

    private static final java.util.Queue<Heard> HEARD = new java.util.concurrent.ConcurrentLinkedQueue<>();

    @SubscribeEvent
    public static void onChat(ServerChatEvent event)
    {
        if (!RoccoCommonConfig.ENABLED.get() || !RoccoCommonConfig.CHAT_READ_PLAYER_CHAT.get())
        {
            return;
        }

        ServerPlayer player = event.getPlayer();
        String message = event.getRawText().trim();
        if (message.isEmpty())
        {
            return;
        }

        // Forge dispara este evento en un hilo de fondo, y ahi no se puede tocar el mundo: los cofres
        // se ven como si no existieran y puede romper cualquier cosa. Se atiende en el hilo del
        // servidor, un par de ticks despues, asi la respuesta sale despues del mensaje del jugador.
        HEARD.add(new Heard(player.getUUID(), message, player.server.getTickCount() + REPLY_DELAY_TICKS));
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event)
    {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END)
        {
            return;
        }
        net.minecraft.server.MinecraftServer server = event.getServer();
        Heard next;
        while ((next = HEARD.peek()) != null && next.dueTick() <= server.getTickCount())
        {
            HEARD.poll();
            // Si mientras tanto se fue o reaparecio, vale el jugador de ahora.
            ServerPlayer player = server.getPlayerList().getPlayer(next.player());
            if (player == null)
            {
                continue;
            }
            try
            {
                hear(player, next.message());
            }
            catch (RuntimeException e)
            {
                // Una frase rara no tiene que tirar el servidor.
                RoccoMod.LOGGER.error("[Rocco] Error atendiendo lo que dijo {}", player.getGameProfile().getName(), e);
            }
        }
    }

    // Lo que dijo un jugador en el chat, ya en el hilo del servidor.
    private static void hear(ServerPlayer player, String message)
    {
        String name = RoccoCommonConfig.COMPANION_NAME.get().toLowerCase(Locale.ROOT);
        boolean named = message.toLowerCase(Locale.ROOT).contains(name);

        RoccoEntity rocco = findListening(player);
        if (rocco == null)
        {
            // Lejos no te escucha, pero si lo llamas por su nombre para que venga, viene igual.
            if (named && OfflineRocco.isCall(message))
            {
                callFromAfar(player);
            }
            return;
        }

        if (RoccoCommonConfig.CHAT_REQUIRE_NAME_MENTION.get() && !named)
        {
            return;
        }
        rocco.adoptIfOwnerless(player);

        // Solo pasa si no hay ninguna IA y ademas se apagaron las frases propias.
        if (!RoccoConfig.chatReady())
        {
            // Solo avisa si le hablaron a el, para no spamear en cada mensaje del chat.
            if (named)
            {
                player.sendSystemMessage(Component.literal(
                        "[Rocco] No tengo IA configurada y las frases propias estan apagadas (ai.offlineFallback).")
                        .withStyle(ChatFormatting.RED));
            }
            return;
        }

        rocco.brain().ask(player, message);
    }

    // Lo que el jugador le dijo por microfono. Se muestra como chat (asi los demas lo ven) y le
    // llega a Rocco sin necesidad de nombrarlo.
    public static void onVoice(ServerPlayer player, String text)
    {
        Component line = Component.literal("<" + player.getGameProfile().getName() + "> ")
                .append(Component.literal(text))
                .append(Component.literal(" (voz)").withStyle(ChatFormatting.DARK_GRAY));
        double radius = RoccoCommonConfig.CHAT_HEARING_RADIUS.get();
        for (ServerPlayer nearby : player.serverLevel().players())
        {
            if (nearby.distanceToSqr(player) <= radius * radius * 4.0D)
            {
                nearby.sendSystemMessage(line);
            }
        }

        RoccoEntity rocco = findListening(player);
        if (rocco == null)
        {
            if (OfflineRocco.isCall(text))
            {
                callFromAfar(player);
            }
            else
            {
                player.displayClientMessage(Component.literal("Rocco esta muy lejos para escucharte. "
                        + "Decile \"Rocco, veni\" y aparece.").withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }

        if (RoccoConfig.chatReady())
        {
            rocco.brain().ask(player, text);
        }
    }

    // "Rocco, veni" desde lejos: lo trae aunque su zona del mundo este descargada.
    private static void callFromAfar(ServerPlayer player)
    {
        // Atado a un poste no viene solo (eso lo fuerza /rocco llamar); enterrado, te espera ahi.
        RoccoEntity known = com.cristoree.rocco.RoccoCompanionTracker.find(player);
        if (known != null && known.getRoccoState() == RoccoState.BURIED)
        {
            BlockPos at = known.blockPosition();
            player.displayClientMessage(Component.translatable("rocco.call.buried",
                    at.getX() + ", " + at.getY() + ", " + at.getZ()).withStyle(ChatFormatting.GRAY), false);
            return;
        }
        if (known != null && known.getRoccoState() == RoccoState.ACTIVE && !known.mayAutoTeleport())
        {
            player.displayClientMessage(Component.translatable("rocco.call.leashed").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        if (known != null && known.getRoccoState() != RoccoState.ACTIVE)
        {
            return;
        }
        boolean arrived = com.cristoree.rocco.RoccoCompanionTracker.recall(player, true);
        if (arrived)
        {
            RoccoEntity rocco = com.cristoree.rocco.RoccoCompanionTracker.find(player);
            if (rocco != null)
            {
                rocco.setFollowing(true);
                rocco.sayLine("teleport_arrived");
            }
        }
        else
        {
            player.displayClientMessage(Component.literal("Buscando a Rocco...").withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static RoccoEntity findListening(ServerPlayer player)
    {
        double radius = RoccoCommonConfig.CHAT_HEARING_RADIUS.get();
        AABB area = player.getBoundingBox().inflate(radius);

        List<RoccoEntity> found = player.serverLevel()
                .getEntitiesOfClass(RoccoEntity.class, area,
                        rocco -> rocco.isAlive() && rocco.getRoccoState() == RoccoState.ACTIVE);

        // Tu Rocco te escucha aunque el de otro jugador este mas cerca. Sin el tuyo, el mas cercano.
        RoccoEntity closest = null;
        double best = Double.MAX_VALUE;
        boolean foundMine = false;

        for (RoccoEntity candidate : found)
        {
            boolean mine = player.getUUID().equals(candidate.getOwnerId());
            double distance = candidate.distanceToSqr(player);
            if ((mine && !foundMine) || (mine == foundMine && distance < best))
            {
                best = distance;
                closest = candidate;
                foundMine = mine;
            }
        }

        return closest;
    }

    private RoccoChatHandler() {}
}

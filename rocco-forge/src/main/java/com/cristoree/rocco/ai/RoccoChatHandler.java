package com.cristoree.rocco.ai;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.config.RoccoConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoState;
import net.minecraft.ChatFormatting;
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

        RoccoEntity closest = null;
        double best = Double.MAX_VALUE;

        for (RoccoEntity candidate : found)
        {
            double distance = candidate.distanceToSqr(player);
            if (distance < best)
            {
                best = distance;
                closest = candidate;
            }
        }

        return closest;
    }

    private RoccoChatHandler() {}
}

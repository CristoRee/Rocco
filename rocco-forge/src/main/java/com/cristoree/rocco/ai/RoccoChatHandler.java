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

        RoccoEntity rocco = findListening(player);
        if (rocco == null)
        {
            return;
        }

        String name = RoccoCommonConfig.COMPANION_NAME.get().toLowerCase(Locale.ROOT);
        boolean named = message.toLowerCase(Locale.ROOT).contains(name);

        if (RoccoCommonConfig.CHAT_REQUIRE_NAME_MENTION.get() && !named)
        {
            return;
        }

        if (!RoccoConfig.chatReady())
        {
            // Solo avisa si le hablaron a el, para no spamear en cada mensaje del chat.
            if (named)
            {
                player.sendSystemMessage(Component.literal(
                        "[Rocco] Falta tu clave de Gemini. Ponela en Mods > Rocco > Config, en la seccion ai.")
                        .withStyle(ChatFormatting.RED));
            }
            return;
        }

        rocco.brain().ask(player, message);
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

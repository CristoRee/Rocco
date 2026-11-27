package com.cristoree.rocco.voice;

import com.cristoree.rocco.RoccoMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

// Le cuenta a cada jugador que tiene andando el servidor (microfono, voz, IA) al entrar, y a todos
// cuando se recarga la config comun. Solo viajan si/no (RoccoNetwork.ServerCaps), nunca claves.
@Mod.EventBusSubscriber(modid = RoccoMod.MODID)
public final class ServerCapsEvents
{
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            RoccoNetwork.sendCaps(player);
        }
    }

    // Desde el evento de config (hilo de Forge): se manda en el hilo del servidor, si hay uno andando.
    public static void configChanged()
    {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null)
        {
            server.execute(() -> RoccoNetwork.broadcastCaps(server));
        }
    }

    private ServerCapsEvents() {}
}

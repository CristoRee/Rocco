package com.cristoree.rocco.client;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.client.voice.RoccoKeys;
import com.cristoree.rocco.voice.RoccoNetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// La tecla "Traer a Rocco" (Controles > Rocco, sin asignar de fabrica): lo trae al lado tuyo.
@Mod.EventBusSubscriber(modid = RoccoMod.MODID, value = Dist.CLIENT)
public final class RoccoCallKey
{
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        boolean pressed = false;
        while (RoccoKeys.CALL.consumeClick())
        {
            pressed = true;
        }
        if (pressed && Minecraft.getInstance().player != null && serverHasRocco())
        {
            RoccoNetwork.callRocco();
        }
    }

    // El servidor tiene Rocco (si no, no hay a quien traer).
    public static boolean serverHasRocco()
    {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && RoccoNetwork.CHANNEL.isRemotePresent(connection.getConnection());
    }

    private RoccoCallKey() {}
}

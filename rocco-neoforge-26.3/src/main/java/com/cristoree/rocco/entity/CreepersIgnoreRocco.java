package com.cristoree.rocco.entity;

import com.cristoree.rocco.RoccoMod;
import net.minecraft.world.entity.monster.Creeper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

// Los creepers ignoran a Rocco: aunque les pegue, nunca lo eligen de objetivo y siguen yendo al
// jugador (con el, todo como siempre). Asi ningun creeper explota por culpa de Rocco.
@EventBusSubscriber(modid = RoccoMod.MODID)
public final class CreepersIgnoreRocco
{
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event)
    {
        if (event.getEntity() instanceof Creeper && event.getNewAboutToBeSetTarget() instanceof RoccoEntity)
        {
            event.setCanceled(true);
        }
    }

    private CreepersIgnoreRocco() {}
}

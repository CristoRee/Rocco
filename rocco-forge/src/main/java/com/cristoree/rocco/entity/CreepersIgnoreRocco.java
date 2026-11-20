package com.cristoree.rocco.entity;

import com.cristoree.rocco.RoccoMod;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Los creepers ignoran a Rocco: aunque les pegue, nunca lo eligen de objetivo y siguen yendo al
// jugador (con el, todo como siempre). Asi ningun creeper explota por culpa de Rocco.
@Mod.EventBusSubscriber(modid = RoccoMod.MODID)
public final class CreepersIgnoreRocco
{
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event)
    {
        // Se queda con el objetivo que ya tenia (el jugador), o con ninguno.
        if (event.getEntity() instanceof Creeper creeper && event.getNewTarget() instanceof RoccoEntity)
        {
            event.setNewTarget(creeper.getTarget() instanceof RoccoEntity ? null : creeper.getTarget());
        }
    }

    private CreepersIgnoreRocco() {}
}

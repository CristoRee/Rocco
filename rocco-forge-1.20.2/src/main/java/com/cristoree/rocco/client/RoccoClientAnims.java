package com.cristoree.rocco.client;

import com.cristoree.rocco.entity.RoccoAnimController;
import net.minecraft.client.Minecraft;
import software.bernie.geckolib.animatable.GeoEntity;

// El servidor avisa que corte una animacion disparada de una entidad (ver RoccoAnimController).
public final class RoccoClientAnims
{
    public static void stop(int entityId, String controller, String animation)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getEntity(entityId) instanceof GeoEntity animatable)
        {
            RoccoAnimController.stop(animatable, controller, animation);
        }
    }

    private RoccoClientAnims() {}
}

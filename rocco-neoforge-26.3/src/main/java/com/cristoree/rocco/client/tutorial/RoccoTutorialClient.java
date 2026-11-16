package com.cristoree.rocco.client.tutorial;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.entity.RoccoEntity;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

// La guia se abre sola cuando te nace tu primer Rocco en cada mundo: el servidor lleva la cuenta y le
// avisa a su dueño (el cliente no sabe de quien es cada Rocco). Se reabre con /rocco tutorial, con el boton "?" de su
// menu o con una tecla (Controles > Rocco, sin asignar de fabrica).
public final class RoccoTutorialClient
{
    public static final KeyMapping TUTORIAL_KEY = new KeyMapping("key.rocco.tutorial",
            InputConstants.UNKNOWN.getValue(), com.cristoree.rocco.client.RoccoKeyCategory.ROCCO);

    private static final double RANGE = 32.0D;
    // Cuanto esperar despues de que nace, para que se vea el saludo y escuches lo que dice.
    private static final int BIRTH_DELAY_TICKS = 90;

    private static int openIn = -1;
    private static int bornId = -1;

    // Del servidor. birth: acaba de nacer (con un rato de espera). Si no, se pidio.
    public static void open(int entityId, boolean birth)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (birth)
        {
            bornId = entityId;
            openIn = BIRTH_DELAY_TICKS;
            return;
        }
        minecraft.gui.setScreen(new RoccoTutorialScreen(rocco(entityId), null));
    }

    @Nullable
    private static RoccoEntity rocco(int entityId)
    {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(entityId);
        return entity instanceof RoccoEntity found ? found : minecraft.player == null ? null : nearest(minecraft.player);
    }

    @Nullable
    private static RoccoEntity nearest(LocalPlayer player)
    {
        RoccoEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (RoccoEntity rocco : player.level().getEntitiesOfClass(RoccoEntity.class, player.getBoundingBox().inflate(RANGE)))
        {
            double distance = rocco.distanceToSqr(player);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = rocco;
            }
        }
        return best;
    }

    @net.neoforged.fml.common.EventBusSubscriber(modid = RoccoMod.MODID, value = Dist.CLIENT)
    public static final class ModEvents
    {
        @SubscribeEvent
        static void onKeys(final RegisterKeyMappingsEvent event)
        {
            event.register(TUTORIAL_KEY);
        }
    }

    @net.neoforged.fml.common.EventBusSubscriber(modid = RoccoMod.MODID, value = Dist.CLIENT)
    public static final class Ticks
    {
        @SubscribeEvent
        static void onClientTick(final net.neoforged.neoforge.client.event.ClientTickEvent.Post event)
        {

            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (player == null || minecraft.level == null)
            {
                openIn = -1;
                return;
            }

            // La tecla para volver a verla cuando quieras.
            while (TUTORIAL_KEY.consumeClick())
            {
                if (minecraft.gui.screen() == null)
                {
                    minecraft.gui.setScreen(new RoccoTutorialScreen(nearest(player), null));
                }
            }

            if (openIn > 0)
            {
                openIn--;
            }
            // Si hay otra pantalla abierta (el chat, su menu), espera a que se cierre.
            else if (openIn == 0 && minecraft.gui.screen() == null)
            {
                openIn = -1;
                minecraft.gui.setScreen(new RoccoTutorialScreen(rocco(bornId), null));
            }
        }
    }

    private RoccoTutorialClient() {}
}

package com.cristoree.rocco.client.tutorial;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoClientConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

// La guia se abre sola una unica vez: cuando nace tu Rocco, el servidor le avisa a su dueño (el
// cliente no sabe de quien es cada Rocco). Se reabre con /rocco tutorial, con el boton "?" de su
// menu o con una tecla (Controles > Rocco, sin asignar de fabrica).
public final class RoccoTutorialClient
{
    public static final KeyMapping TUTORIAL_KEY = new KeyMapping("key.rocco.tutorial",
            InputConstants.UNKNOWN.getValue(), "key.categories.rocco");

    private static final double RANGE = 32.0D;
    // Cuanto esperar despues de que nace, para que se vea el saludo y escuches lo que dice.
    private static final int BIRTH_DELAY_TICKS = 90;

    private static int openIn = -1;
    private static int bornId = -1;

    // Del servidor. birth: acaba de nacer (una sola vez, con un rato de espera). Si no, se pidio.
    public static void open(int entityId, boolean birth)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (birth)
        {
            if (!RoccoClientConfig.TUTORIAL_SEEN.get())
            {
                bornId = entityId;
                openIn = BIRTH_DELAY_TICKS;
            }
            return;
        }
        minecraft.setScreen(new RoccoTutorialScreen(rocco(entityId), null));
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

    @Mod.EventBusSubscriber(modid = RoccoMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModEvents
    {
        @SubscribeEvent
        static void onKeys(final RegisterKeyMappingsEvent event)
        {
            event.register(TUTORIAL_KEY);
        }
    }

    @Mod.EventBusSubscriber(modid = RoccoMod.MODID, value = Dist.CLIENT)
    public static final class Ticks
    {
        @SubscribeEvent
        static void onClientTick(final TickEvent.ClientTickEvent event)
        {
            if (event.phase != TickEvent.Phase.END)
            {
                return;
            }

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
                if (minecraft.screen == null)
                {
                    minecraft.setScreen(new RoccoTutorialScreen(nearest(player), null));
                }
            }

            if (openIn > 0)
            {
                openIn--;
            }
            // Si hay otra pantalla abierta (el chat, su menu), espera a que se cierre.
            else if (openIn == 0 && minecraft.screen == null)
            {
                openIn = -1;
                RoccoClientConfig.TUTORIAL_SEEN.set(true);
                RoccoClientConfig.SPEC.save();
                minecraft.setScreen(new RoccoTutorialScreen(rocco(bornId), null));
            }
        }
    }

    private RoccoTutorialClient() {}
}

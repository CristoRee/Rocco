package com.cristoree.rocco;

import com.cristoree.rocco.client.RoccoClientHooks;
import com.cristoree.rocco.client.RoccoRenderer;
import com.cristoree.rocco.config.RoccoConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.registry.ModEntities;
import com.cristoree.rocco.registry.ModItems;
import com.cristoree.rocco.registry.ModParticles;
import com.cristoree.rocco.registry.ModSounds;
import com.cristoree.rocco.voice.RoccoNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(RoccoMod.MODID)
public class RoccoMod
{
    public static final String MODID = "rocco";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RoccoMod(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        RoccoConfig.register(context);
        ModEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModSounds.register(modEventBus);
        ModParticles.register(modEventBus);
        RoccoNetwork.register();
        RoccoCompanionTracker.registerChunkLoading();

        // Se registra aca y no en un evento de setup: ahi el contexto del mod puede no ser el
        // nuestro, porque Forge despacha esos eventos en paralelo.
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            RoccoClientHooks.registerConfigScreen(context);
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents
    {
        @SubscribeEvent
        static void onConfigChanged(final ModConfigEvent event)
        {
            if (event.getConfig().getType() == ModConfig.Type.COMMON)
            {
                // Primero se actualizan los defaults viejos, asi los avisos ya miran lo nuevo.
                RoccoConfig.migrateOldDefaults();
                RoccoConfig.warnAboutConflicts();
            }
        }

        @SubscribeEvent
        static void onAttributes(final EntityAttributeCreationEvent event)
        {
            event.put(ModEntities.ROCCO.get(), RoccoEntity.createAttributes().build());
        }

        @SubscribeEvent
        static void onBuildCreativeTabs(final BuildCreativeModeTabContentsEvent event)
        {
            if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS)
            {
                event.accept(ModItems.ROCCO_SPAWN_EGG);
            }
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ClientEvents
    {
        @SubscribeEvent
        static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event)
        {
            event.registerEntityRenderer(ModEntities.ROCCO.get(), RoccoRenderer::new);
        }

        @SubscribeEvent
        static void onClientSetup(final net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event)
        {
            com.cristoree.rocco.client.RoccoParticleFx.install();
        }

        @SubscribeEvent
        static void onParticles(final net.minecraftforge.client.event.RegisterParticleProvidersEvent event)
        {
            event.registerSpriteSet(ModParticles.BLISS_FLOWER.get(), com.cristoree.rocco.client.RoccoParticles.BlissFlowerProvider::new);
            event.registerSpriteSet(ModParticles.MATE_STEAM.get(), com.cristoree.rocco.client.RoccoParticles.MateSteamProvider::new);
            event.registerSpriteSet(ModParticles.MAGIC_RUNE.get(), com.cristoree.rocco.client.RoccoParticles.MagicRuneProvider::new);
            event.registerSpriteSet(ModParticles.MAGIC_SPARK.get(), com.cristoree.rocco.client.RoccoParticles.MagicSparkProvider::new);
            event.registerSpriteSet(ModParticles.MAGIC_BURST.get(), com.cristoree.rocco.client.RoccoParticles.MagicBurstProvider::new);
        }

        // La tecla del microfono queda en Opciones > Controles, categoria Rocco.
        @SubscribeEvent
        static void onKeys(final net.minecraftforge.client.event.RegisterKeyMappingsEvent event)
        {
            event.register(com.cristoree.rocco.client.voice.RoccoKeys.TALK);
        }

        @SubscribeEvent
        static void onOverlays(final net.minecraftforge.client.event.RegisterGuiOverlaysEvent event)
        {
            event.registerAboveAll("rocco_mic", new com.cristoree.rocco.client.voice.MicOverlay());
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static final class ClientTicks
    {
        @SubscribeEvent
        static void onClientTick(final net.minecraftforge.event.TickEvent.ClientTickEvent event)
        {
            if (event.phase == net.minecraftforge.event.TickEvent.Phase.END)
            {
                com.cristoree.rocco.client.voice.MicRecorder.tick();
                com.cristoree.rocco.client.voice.VoicePlayback.tick();
            }
        }
    }
}

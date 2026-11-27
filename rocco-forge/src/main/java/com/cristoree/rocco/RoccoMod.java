package com.cristoree.rocco;

import com.cristoree.rocco.client.RoccoClientHooks;
import com.cristoree.rocco.client.RoccoParticles.Look;
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

    // Sin parametros a proposito: los Forge 1.20.1 de antes de la 47.3 solo buscan este constructor
    // (con uno que recibe el contexto, fallaban con NoSuchMethodException). Por eso el bus sale de
    // FMLJavaModLoadingContext.get() y la config de ModLoadingContext.get(), que andan en todas las 47.
    @SuppressWarnings("removal")
    public RoccoMod()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        RoccoConfig.register();
        ModEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModSounds.register(modEventBus);
        ModParticles.register(modEventBus);
        com.cristoree.rocco.registry.ModMenus.register(modEventBus);
        RoccoNetwork.register();
        RoccoCompanionTracker.registerChunkLoading();

        // Se registra aca y no en un evento de setup: ahi el contexto del mod puede no ser el
        // nuestro, porque Forge despacha esos eventos en paralelo.
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            RoccoClientHooks.registerConfigScreen();
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
                // Si el dueño activo (o apago) el microfono o la voz, los jugadores conectados se enteran.
                com.cristoree.rocco.voice.ServerCapsEvents.configChanged();
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
            else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES)
            {
                event.accept(ModItems.MOCHILA_1);
                event.accept(ModItems.MOCHILA_2);
                event.accept(ModItems.MOCHILA_3);
                event.accept(ModItems.MOCHILA_4);
                event.accept(ModItems.MOCHILA_5);
                event.accept(ModItems.MOCHILA_6);
            }
            else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS)
            {
                event.accept(ModItems.SEMILLITA);
                event.accept(ModItems.FLOR_AMISTAD);
            }
            else if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS)
            {
                event.accept(ModItems.ROCO_COLA);
                event.accept(ModItems.ROCO_COLA_HALF);
                event.accept(ModItems.ROCO_COLA_EMPTY);
                event.accept(ModItems.TORTA_FRITA);
                event.accept(ModItems.ASADO_DE_TIRA);
                event.accept(ModItems.BONDIOLA_ASADA);
                event.accept(ModItems.COSTILLITAS_DE_CORDERO);
            }
            else if (event.getTabKey() == CreativeModeTabs.COMBAT)
            {
                event.accept(ModItems.BOTELLA_ROTA);
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
            event.registerEntityRenderer(ModEntities.BOTELLA_LANZADA.get(), com.cristoree.rocco.client.ThrownColaRenderer::new);
            event.registerEntityRenderer(ModEntities.CLICK_COCINA.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
            event.registerEntityRenderer(ModEntities.TRUCO_MESA.get(), com.cristoree.rocco.client.truco.TrucoTableRenderer::new);
            event.registerEntityRenderer(ModEntities.TRUCO_SILLA.get(), com.cristoree.rocco.client.truco.TrucoChairRenderer::new);
        }

        @SubscribeEvent
        static void onClientSetup(final net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event)
        {
            com.cristoree.rocco.client.RoccoParticleFx.install();
            // La pantalla del equipo de Rocco (armadura, arma y escudo).
            event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(
                    com.cristoree.rocco.registry.ModMenus.EQUIPMENT.get(), com.cristoree.rocco.client.menu.RoccoEquipmentScreen::new));
            // Su inventario, con una pagina por cada 6 filas.
            event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(
                    com.cristoree.rocco.registry.ModMenus.INVENTORY.get(), com.cristoree.rocco.client.menu.RoccoInventoryScreen::new));
            // El registro de propiedades de items no es seguro desde varios hilos.
            event.enqueueWork(com.cristoree.rocco.client.RoccoFishingLine::installRodModel);
            // El agua del mate baja con cada sorbo (el modelo trae lleno, 2/3, 1/3 y vacio).
            event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                    com.cristoree.rocco.registry.ModItems.MATE.get(),
                    new net.minecraft.resources.ResourceLocation(MODID, "sipped"),
                    (stack, level, entity, seed) -> com.cristoree.rocco.item.RoccoMateItem.sips(stack)
                            / (float) com.cristoree.rocco.item.RoccoMateItem.SIPS));
        }

        @SubscribeEvent
        static void onParticles(final net.minecraftforge.client.event.RegisterParticleProvidersEvent event)
        {
            event.registerSpriteSet(ModParticles.BLISS_FLOWER.get(), com.cristoree.rocco.client.RoccoParticles.BlissFlowerProvider::new);
            event.registerSpriteSet(ModParticles.MATE_STEAM.get(), com.cristoree.rocco.client.RoccoParticles.MateSteamProvider::new);
            event.registerSpriteSet(ModParticles.MAGIC_RUNE.get(), com.cristoree.rocco.client.RoccoParticles.MagicRuneProvider::new);
            event.registerSpriteSet(ModParticles.MAGIC_SPARK.get(), com.cristoree.rocco.client.RoccoParticles.MagicSparkProvider::new);
            event.registerSpriteSet(ModParticles.MAGIC_BURST.get(), com.cristoree.rocco.client.RoccoParticles.MagicBurstProvider::new);
            tuned(event, ModParticles.LEAF_SPARKLE.get(), Look.LEAF_SPARKLE);
            tuned(event, ModParticles.LEAF_BURST.get(), Look.LEAF_BURST);
            tuned(event, ModParticles.SPEED_LEAF.get(), Look.SPEED_LEAF);
            tuned(event, ModParticles.WIND_STREAK.get(), Look.WIND_STREAK);
            tuned(event, ModParticles.WIND_PUFF.get(), Look.WIND_PUFF);
            tuned(event, ModParticles.WIND_TEAR.get(), Look.WIND_TEAR);
            tuned(event, ModParticles.LAND_DUST.get(), Look.LAND_DUST);
            tuned(event, ModParticles.DUCK_POP.get(), Look.DUCK_POP);
            tuned(event, ModParticles.WATER_DROP.get(), Look.WATER_DROP);
            tuned(event, ModParticles.THINK_DOT.get(), Look.THINK_DOT);
            tuned(event, ModParticles.CLAP_SPARK.get(), Look.CLAP_SPARK);
            tuned(event, ModParticles.CONFETTI.get(), Look.CONFETTI);
            tuned(event, ModParticles.MUSIC_NOTE.get(), Look.MUSIC_NOTE);
            tuned(event, ModParticles.DANCE_SPARKLE.get(), Look.DANCE_SPARKLE);
            event.registerSpriteSet(ModParticles.BUTTERFLY.get(), com.cristoree.rocco.client.RoccoParticles.ButterflyProvider::new);
            tuned(event, ModParticles.PETAL.get(), Look.PETAL);
            tuned(event, ModParticles.COLA_FIZZ.get(), Look.COLA_FIZZ);
            tuned(event, ModParticles.COLA_POP.get(), Look.COLA_POP);
            tuned(event, ModParticles.REFRESH_SPARKLE.get(), Look.REFRESH_SPARKLE);
            tuned(event, ModParticles.HIC_BUBBLE.get(), Look.HIC_BUBBLE);
            tuned(event, ModParticles.SWING_SPARKLE.get(), Look.SWING_SPARKLE);
            tuned(event, ModParticles.PROUD_SPARKLE.get(), Look.PROUD_SPARKLE);
            tuned(event, ModParticles.ENDER_SPARK.get(), Look.ENDER_SPARK);
            tuned(event, ModParticles.ENDER_BURST.get(), Look.ENDER_BURST);
            tuned(event, ModParticles.ENDER_RING.get(), Look.ENDER_RING);
            event.registerSpriteSet(ModParticles.DIRT_CLOD.get(), com.cristoree.rocco.client.RoccoParticles.DirtClodProvider::new);
            tuned(event, ModParticles.BURP_WIND.get(), Look.BURP_WIND);
            tuned(event, ModParticles.BURP_LEAF.get(), Look.BURP_LEAF);
            tuned(event, ModParticles.BURP_PUFF.get(), Look.BURP_PUFF);
            tuned(event, ModParticles.FRIEND_GLOW.get(), Look.FRIEND_GLOW);
            tuned(event, ModParticles.HEART_POP.get(), Look.HEART_POP);
            tuned(event, ModParticles.JOY_TEAR.get(), Look.JOY_TEAR);
            tuned(event, ModParticles.SEED_POP.get(), Look.SEED_POP);
            tuned(event, ModParticles.GRILL_SMOKE.get(), Look.GRILL_SMOKE);
            tuned(event, ModParticles.HEAT_WAVE.get(), Look.HEAT_WAVE);
            tuned(event, ModParticles.EMBER.get(), Look.EMBER);
            tuned(event, ModParticles.FAT_SIZZLE.get(), Look.FAT_SIZZLE);
            tuned(event, ModParticles.FLAME_BURST.get(), Look.FLAME_BURST);
            tuned(event, ModParticles.OIL_BUBBLE.get(), Look.OIL_BUBBLE);
            tuned(event, ModParticles.OIL_SPLASH.get(), Look.OIL_SPLASH);
            tuned(event, ModParticles.OIL_DRIP.get(), Look.OIL_DRIP);
            tuned(event, ModParticles.FLOUR_PUFF.get(), Look.FLOUR_PUFF);
            event.registerSpriteSet(ModParticles.GLASS_SHARD.get(), com.cristoree.rocco.client.RoccoParticles.GlassShardProvider::new);
        }

        private static void tuned(net.minecraftforge.client.event.RegisterParticleProvidersEvent event,
                                  net.minecraft.core.particles.SimpleParticleType type, Look look)
        {
            event.registerSpriteSet(type, sprites -> new com.cristoree.rocco.client.RoccoParticles.TunedProvider(sprites, look));
        }

        // La tecla del microfono queda en Opciones > Controles, categoria Rocco.
        @SubscribeEvent
        static void onKeys(final net.minecraftforge.client.event.RegisterKeyMappingsEvent event)
        {
            event.register(com.cristoree.rocco.client.voice.RoccoKeys.TALK);
            event.register(com.cristoree.rocco.client.voice.RoccoKeys.CALL);
            event.register(com.cristoree.rocco.client.truco.TrucoClient.LOOK);
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
                com.cristoree.rocco.client.voice.VoicePlayback.tick();            }
        }
    }
}

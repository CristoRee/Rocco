package com.cristoree.rocco.registry;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.entity.CookClickEntity;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.ThrownColaBottle;
import com.cristoree.rocco.truco.TrucoChairEntity;
import com.cristoree.rocco.truco.TrucoTableEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities
{
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, RoccoMod.MODID);

    public static final RegistryObject<EntityType<RoccoEntity>> ROCCO = ENTITIES.register("rocco",
            () -> EntityType.Builder.of(RoccoEntity::new, MobCategory.CREATURE)
                    // El modelo mide 15,5 px hasta la punta de las hojitas: la caja de 1,3 le sobraba
                    // casi medio bloque. Con 1,0 encaja y pasa por huecos de un bloque.
                    .sized(0.6F, 1.0F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build(RoccoMod.MODID + ":rocco"));

    // La botella vacia de Roco Cola tirada (como una pocion arrojadiza).
    public static final RegistryObject<EntityType<ThrownColaBottle>> BOTELLA_LANZADA = ENTITIES.register("botella_lanzada",
            () -> EntityType.Builder.<ThrownColaBottle>of(ThrownColaBottle::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build(RoccoMod.MODID + ":botella_lanzada"));

    // La caja invisible sobre la parrilla o el pote, para hacerle clic derecho.
    public static final RegistryObject<EntityType<CookClickEntity>> CLICK_COCINA = ENTITIES.register("click_cocina",
            () -> EntityType.Builder.<CookClickEntity>of(CookClickEntity::new, MobCategory.MISC)
                    .sized(0.65F, 0.6F)
                    .noSave()
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .build(RoccoMod.MODID + ":click_cocina"));

    // La mesa y las sillas del truco.
    public static final RegistryObject<EntityType<TrucoTableEntity>> TRUCO_MESA = ENTITIES.register("truco_mesa",
            () -> EntityType.Builder.<TrucoTableEntity>of(TrucoTableEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.85F)
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build(RoccoMod.MODID + ":truco_mesa"));

    public static final RegistryObject<EntityType<TrucoChairEntity>> TRUCO_SILLA = ENTITIES.register("truco_silla",
            () -> EntityType.Builder.<TrucoChairEntity>of(TrucoChairEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build(RoccoMod.MODID + ":truco_silla"));

    public static void register(IEventBus modEventBus)
    {
        ENTITIES.register(modEventBus);
    }

    private ModEntities() {}
}

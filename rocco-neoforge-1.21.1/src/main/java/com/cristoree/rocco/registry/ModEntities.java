package com.cristoree.rocco.registry;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.entity.CookClickEntity;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.ThrownColaBottle;
import com.cristoree.rocco.truco.TrucoChairEntity;
import com.cristoree.rocco.truco.TrucoTableEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModEntities
{
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, RoccoMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<RoccoEntity>> ROCCO = ENTITIES.register("rocco",
            () -> EntityType.Builder.of(RoccoEntity::new, MobCategory.CREATURE)
                    // El modelo mide 15,5 px hasta la punta de las hojitas: la caja de 1,3 le sobraba
                    // casi medio bloque. Con 1,0 encaja y pasa por huecos de un bloque.
                    .sized(0.6F, 1.0F)
                    // Los ojos a tres cuartos de la altura (con la caja nueva, que ya no le sobra medio bloque).
                    .eyeHeight(0.75F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build(RoccoMod.MODID + ":rocco"));

    // La botella vacia de Roco Cola tirada (como una pocion arrojadiza).
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownColaBottle>> BOTELLA_LANZADA = ENTITIES.register("botella_lanzada",
            () -> EntityType.Builder.<ThrownColaBottle>of(ThrownColaBottle::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build(RoccoMod.MODID + ":botella_lanzada"));

    // La caja invisible sobre la parrilla o el pote, para hacerle clic derecho.
    public static final DeferredHolder<EntityType<?>, EntityType<CookClickEntity>> CLICK_COCINA = ENTITIES.register("click_cocina",
            () -> EntityType.Builder.<CookClickEntity>of(CookClickEntity::new, MobCategory.MISC)
                    .sized(0.65F, 0.6F)
                    .noSave()
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .build(RoccoMod.MODID + ":click_cocina"));

    // La mesa y las sillas del truco.
    public static final DeferredHolder<EntityType<?>, EntityType<TrucoTableEntity>> TRUCO_MESA = ENTITIES.register("truco_mesa",
            () -> EntityType.Builder.<TrucoTableEntity>of(TrucoTableEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.85F)
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build(RoccoMod.MODID + ":truco_mesa"));

    public static final DeferredHolder<EntityType<?>, EntityType<TrucoChairEntity>> TRUCO_SILLA = ENTITIES.register("truco_silla",
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

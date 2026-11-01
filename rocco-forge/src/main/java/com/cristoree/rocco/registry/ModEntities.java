package com.cristoree.rocco.registry;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.entity.RoccoEntity;
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

    public static void register(IEventBus modEventBus)
    {
        ENTITIES.register(modEventBus);
    }

    private ModEntities() {}
}

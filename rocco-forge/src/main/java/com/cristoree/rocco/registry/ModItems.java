package com.cristoree.rocco.registry;

import com.cristoree.rocco.RoccoMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems
{
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, RoccoMod.MODID);

    public static final RegistryObject<Item> ROCCO_SPAWN_EGG = ITEMS.register("rocco_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.ROCCO, 0xC8A165, 0x5EA832, new Item.Properties()));

    public static void register(IEventBus modEventBus)
    {
        ITEMS.register(modEventBus);
    }

    private ModItems() {}
}

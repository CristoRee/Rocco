package com.cristoree.rocco.registry;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.item.BackpackItem;
import com.cristoree.rocco.item.BackpackUpgradeRecipe;
import com.cristoree.rocco.item.RocoColaItem;
import com.cristoree.rocco.item.RoccoGiftItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
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

    // El mate que te pasa Rocco: no se fabrica ni aparece en el creativo.
    public static final RegistryObject<Item> MATE = ITEMS.register("mate",
            () -> new com.cristoree.rocco.item.RoccoMateItem(new Item.Properties().stacksTo(1)));

    // Las mochilas: cada una se fabrica desde la anterior (ver data/rocco/recipes).
    public static final RegistryObject<Item> MOCHILA_1 = backpack(1, Rarity.COMMON);
    public static final RegistryObject<Item> MOCHILA_2 = backpack(2, Rarity.COMMON);
    public static final RegistryObject<Item> MOCHILA_3 = backpack(3, Rarity.UNCOMMON);
    public static final RegistryObject<Item> MOCHILA_4 = backpack(4, Rarity.UNCOMMON);
    public static final RegistryObject<Item> MOCHILA_5 = backpack(5, Rarity.RARE);
    public static final RegistryObject<Item> MOCHILA_6 = backpack(6, Rarity.EPIC);

    // Los regalos de Rocco.
    public static final RegistryObject<Item> SEMILLITA = ITEMS.register("semillita",
            () -> new RoccoGiftItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), false, "tooltip.rocco.semillita"));
    public static final RegistryObject<Item> FLOR_AMISTAD = ITEMS.register("flor_amistad",
            () -> new RoccoGiftItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(), true,
                    "tooltip.rocco.flor_amistad", "tooltip.rocco.flor_amistad_2"));

    public static final RegistryObject<Item> ROCO_COLA_EMPTY = ITEMS.register("roco_cola_empty",
            () -> new RoccoGiftItem(new Item.Properties().stacksTo(16), false, "tooltip.rocco.roco_cola_empty"));
    public static final RegistryObject<Item> ROCO_COLA_HALF = ITEMS.register("roco_cola_half",
            () -> new RocoColaItem(new Item.Properties().stacksTo(16), ROCO_COLA_EMPTY,
                    "tooltip.rocco.roco_cola_flavor", "tooltip.rocco.roco_cola_half"));
    public static final RegistryObject<Item> ROCO_COLA = ITEMS.register("roco_cola",
            () -> new RocoColaItem(new Item.Properties().stacksTo(16), ROCO_COLA_HALF,
                    "tooltip.rocco.roco_cola_flavor", "tooltip.rocco.roco_cola"));

    public static final DeferredRegister<RecipeSerializer<?>> RECIPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, RoccoMod.MODID);

    public static final RegistryObject<RecipeSerializer<BackpackUpgradeRecipe>> BACKPACK_UPGRADE = RECIPES.register("backpack_upgrade",
            BackpackUpgradeRecipe.Serializer::new);

    private static RegistryObject<Item> backpack(int tier, Rarity rarity)
    {
        return ITEMS.register("mochila_" + tier, () ->
        {
            Item.Properties properties = new Item.Properties().stacksTo(1).rarity(rarity);
            return new BackpackItem(tier, tier >= 5 ? properties.fireResistant() : properties);
        });
    }

    public static void register(IEventBus modEventBus)
    {
        ITEMS.register(modEventBus);
        RECIPES.register(modEventBus);
    }

    private ModItems() {}
}

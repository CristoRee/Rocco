package com.cristoree.rocco.registry;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.item.BackpackItem;
import com.cristoree.rocco.item.BackpackUpgradeRecipe;
import com.cristoree.rocco.item.RocoColaItem;
import com.cristoree.rocco.item.RoccoGiftItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// Desde 1.21.2 cada item lleva su id en las propiedades: registerItem se las pasa ya armadas.
public final class ModItems
{
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RoccoMod.MODID);

    public static final DeferredItem<Item> ROCCO_SPAWN_EGG = ITEMS.registerItem("rocco_spawn_egg",
            properties -> new net.minecraft.world.item.SpawnEggItem(properties.spawnEgg(ModEntities.ROCCO.get())));

    // El mate que te pasa Rocco: no se fabrica ni aparece en el creativo.
    public static final DeferredItem<Item> MATE = ITEMS.registerItem("mate",
            properties -> new com.cristoree.rocco.item.RoccoMateItem(properties.stacksTo(1)));

    // Las mochilas: cada una se fabrica desde la anterior (ver data/rocco/recipe).
    public static final DeferredItem<Item> MOCHILA_1 = backpack(1, Rarity.COMMON);
    public static final DeferredItem<Item> MOCHILA_2 = backpack(2, Rarity.COMMON);
    public static final DeferredItem<Item> MOCHILA_3 = backpack(3, Rarity.UNCOMMON);
    public static final DeferredItem<Item> MOCHILA_4 = backpack(4, Rarity.UNCOMMON);
    public static final DeferredItem<Item> MOCHILA_5 = backpack(5, Rarity.RARE);
    public static final DeferredItem<Item> MOCHILA_6 = backpack(6, Rarity.EPIC);

    // Los regalos de Rocco.
    public static final DeferredItem<Item> SEMILLITA = ITEMS.registerItem("semillita",
            properties -> new RoccoGiftItem(properties.stacksTo(1).rarity(Rarity.UNCOMMON), false, "tooltip.rocco.semillita"));
    public static final DeferredItem<Item> FLOR_AMISTAD = ITEMS.registerItem("flor_amistad",
            properties -> new RoccoGiftItem(properties.stacksTo(1).rarity(Rarity.EPIC).fireResistant(), true,
                    "tooltip.rocco.flor_amistad", "tooltip.rocco.flor_amistad_2"));

    // Vacia: se tira, o agachado contra el piso se rompe y queda la botella rota.
    public static final DeferredItem<Item> ROCO_COLA_EMPTY = ITEMS.registerItem("roco_cola_empty",
            properties -> new com.cristoree.rocco.item.EmptyColaItem(properties.stacksTo(16)));
    public static final DeferredItem<Item> ROCO_COLA_HALF = ITEMS.registerItem("roco_cola_half",
            properties -> new RocoColaItem(properties.stacksTo(16), ROCO_COLA_EMPTY,
                    "tooltip.rocco.roco_cola_flavor", "tooltip.rocco.roco_cola_half"));
    public static final DeferredItem<Item> ROCO_COLA = ITEMS.registerItem("roco_cola",
            properties -> new RocoColaItem(properties.stacksTo(16), ROCO_COLA_HALF,
                    "tooltip.rocco.roco_cola_flavor", "tooltip.rocco.roco_cola"));

    // La botella rota: un arma corta de vidrio (3 de daño, 40 golpes).
    public static final DeferredItem<Item> BOTELLA_ROTA = ITEMS.registerItem("botella_rota",
            com.cristoree.rocco.item.BrokenBottleItem::new);

    // Lo que cocina Rocco: la torta frita y lo que sale de su parrilla.
    public static final DeferredItem<Item> TORTA_FRITA = ITEMS.registerItem("torta_frita",
            properties -> new com.cristoree.rocco.item.RoccoFoodItem(properties, 4, 0.5F, "tooltip.rocco.torta_frita"));
    public static final DeferredItem<Item> ASADO_DE_TIRA = ITEMS.registerItem("asado_de_tira",
            properties -> new com.cristoree.rocco.item.RoccoFoodItem(properties, 10, 0.8F, "tooltip.rocco.asado"));
    public static final DeferredItem<Item> BONDIOLA_ASADA = ITEMS.registerItem("bondiola_asada",
            properties -> new com.cristoree.rocco.item.RoccoFoodItem(properties, 10, 0.8F, "tooltip.rocco.asado"));
    public static final DeferredItem<Item> COSTILLITAS_DE_CORDERO = ITEMS.registerItem("costillitas_de_cordero",
            properties -> new com.cristoree.rocco.item.RoccoFoodItem(properties, 8, 0.8F, "tooltip.rocco.asado"));

    public static final DeferredRegister<RecipeSerializer<?>> RECIPES =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, RoccoMod.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BackpackUpgradeRecipe>> BACKPACK_UPGRADE = RECIPES.register("backpack_upgrade",
            () -> BackpackUpgradeRecipe.SERIALIZER);

    private static DeferredItem<Item> backpack(int tier, Rarity rarity)
    {
        return ITEMS.registerItem("mochila_" + tier, properties ->
        {
            Item.Properties built = properties.stacksTo(1).rarity(rarity);
            return new BackpackItem(tier, tier >= 5 ? built.fireResistant() : built);
        });
    }

    public static void register(IEventBus modEventBus)
    {
        ITEMS.register(modEventBus);
        RECIPES.register(modEventBus);
    }

    private ModItems() {}
}

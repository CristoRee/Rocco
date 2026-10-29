package com.cristoree.rocco.work;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.Tags;

import java.util.Set;

// De que "tipo" es una cosa, como la ordenaria una persona: los minerales con los minerales, la
// comida con la comida. Sirve para elegir a que cofre va cada cosa al guardar, y para que al
// ordenar cada cofre quede de un tema. El orden de las constantes es el orden dentro de un cofre.
// Usa las etiquetas de Forge, asi tambien entiende los items de otros mods que las usan.
public enum ItemCategory
{
    TOOLS,
    ORES,
    FOOD,
    FARMING,
    WOOD,
    STONE,
    MOB_DROPS,
    REDSTONE,
    BLOCKS,
    MISC;

    private static final Set<Item> FARMING_ITEMS = Set.of(
            Items.BONE_MEAL, Items.WHEAT, Items.SUGAR_CANE, Items.PUMPKIN, Items.MELON, Items.CACTUS, Items.BAMBOO,
            Items.COCOA_BEANS, Items.HAY_BLOCK, Items.KELP, Items.VINE, Items.LILY_PAD, Items.SWEET_BERRIES, Items.GLOW_BERRIES);
    private static final Set<Item> MOB_DROP_ITEMS = Set.of(
            Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.INK_SAC, Items.GLOW_INK_SAC, Items.PHANTOM_MEMBRANE,
            Items.GHAST_TEAR, Items.MAGMA_CREAM, Items.RABBIT_HIDE, Items.RABBIT_FOOT, Items.PRISMARINE_SHARD,
            Items.PRISMARINE_CRYSTALS, Items.SHULKER_SHELL, Items.ARROW, Items.SCUTE);
    private static final Set<Item> REDSTONE_ITEMS = Set.of(
            Items.REDSTONE, Items.REDSTONE_TORCH, Items.REPEATER, Items.COMPARATOR, Items.PISTON, Items.STICKY_PISTON,
            Items.OBSERVER, Items.HOPPER, Items.DROPPER, Items.DISPENSER, Items.LEVER, Items.REDSTONE_LAMP,
            Items.DAYLIGHT_DETECTOR, Items.TRIPWIRE_HOOK, Items.TARGET, Items.NOTE_BLOCK, Items.RAIL, Items.POWERED_RAIL,
            Items.DETECTOR_RAIL, Items.ACTIVATOR_RAIL, Items.TNT, Items.SLIME_BLOCK, Items.HONEY_BLOCK);
    private static final Set<Item> ORE_ITEMS = Set.of(
            Items.COAL, Items.CHARCOAL, Items.FLINT, Items.AMETHYST_SHARD, Items.NETHERITE_SCRAP, Items.ANCIENT_DEBRIS);

    public static ItemCategory of(ItemStack stack)
    {
        Item item = stack.getItem();
        if (stack.isDamageableItem() || stack.is(Tags.Items.TOOLS) || stack.is(Tags.Items.ARMORS) || stack.is(Tags.Items.SHEARS))
        {
            return TOOLS;
        }
        if (stack.is(Tags.Items.ORES) || stack.is(Tags.Items.RAW_MATERIALS) || stack.is(Tags.Items.INGOTS)
                || stack.is(Tags.Items.GEMS) || stack.is(Tags.Items.NUGGETS) || stack.is(Tags.Items.STORAGE_BLOCKS)
                || ORE_ITEMS.contains(item))
        {
            return ORES;
        }
        if (stack.is(Tags.Items.DUSTS_REDSTONE) || REDSTONE_ITEMS.contains(item))
        {
            return REDSTONE;
        }
        if (stack.is(Tags.Items.SEEDS) || stack.is(Tags.Items.CROPS) || stack.is(ItemTags.SAPLINGS)
                || stack.is(ItemTags.FLOWERS) || stack.is(Tags.Items.MUSHROOMS) || FARMING_ITEMS.contains(item))
        {
            return FARMING;
        }
        if (stack.isEdible())
        {
            return FOOD;
        }
        if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS) || stack.is(Tags.Items.RODS_WOODEN)
                || stack.is(ItemTags.WOODEN_SLABS) || stack.is(ItemTags.WOODEN_STAIRS) || stack.is(ItemTags.WOODEN_FENCES)
                || stack.is(ItemTags.WOODEN_DOORS) || stack.is(ItemTags.WOODEN_TRAPDOORS) || stack.is(ItemTags.LEAVES))
        {
            return WOOD;
        }
        if (stack.is(Tags.Items.STONE) || stack.is(Tags.Items.COBBLESTONE) || stack.is(Tags.Items.SAND)
                || stack.is(Tags.Items.GRAVEL) || stack.is(Tags.Items.NETHERRACK) || stack.is(Tags.Items.END_STONES)
                || stack.is(ItemTags.DIRT) || item == Items.CLAY_BALL || item == Items.CLAY || item == Items.DIORITE
                || item == Items.ANDESITE || item == Items.GRANITE || item == Items.TUFF || item == Items.CALCITE)
        {
            return STONE;
        }
        if (stack.is(Tags.Items.BONES) || stack.is(Tags.Items.STRING) || stack.is(Tags.Items.GUNPOWDER)
                || stack.is(Tags.Items.LEATHER) || stack.is(Tags.Items.FEATHERS) || stack.is(Tags.Items.SLIMEBALLS)
                || stack.is(Tags.Items.ENDER_PEARLS) || stack.is(Tags.Items.RODS_BLAZE) || stack.is(Tags.Items.EGGS)
                || stack.is(ItemTags.WOOL) || MOB_DROP_ITEMS.contains(item))
        {
            return MOB_DROPS;
        }
        if (item instanceof BlockItem)
        {
            return BLOCKS;
        }
        return MISC;
    }
}

package com.cristoree.rocco.item;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.List;

// Lo que lleva adentro una mochila. En 1.21 va en el componente de contenedor del juego (el mismo
// de los cofres de shulker), con cada cosa en su lugar. Una mochila traida de 1.20.1 lo tiene en
// sus datos viejos ("Items"): se lee de ahi y al guardar pasa al componente.
public final class BackpackContents
{
    private static final String LEGACY_KEY = "Items";

    // Las cosas por lugar (vacio donde no hay nada), hasta 'size' lugares.
    public static NonNullList<ItemStack> read(HolderLookup.Provider registries, ItemStack backpack, int size)
    {
        NonNullList<ItemStack> slots = NonNullList.withSize(size, ItemStack.EMPTY);
        ItemContainerContents contents = backpack.get(DataComponents.CONTAINER);
        if (contents != null)
        {
            contents.copyInto(slots);
        }
        ListTag legacy = legacy(backpack);
        for (int i = 0; i < legacy.size(); i++)
        {
            CompoundTag entry = legacy.getCompound(i);
            int slot = entry.getShort("Slot");
            ItemStack stack = LegacyItems.parse(registries, entry);
            if (slot >= 0 && slot < size && slots.get(slot).isEmpty())
            {
                slots.set(slot, stack);
            }
        }
        return slots;
    }

    // Guarda las cosas por lugar. Sin nada adentro, la mochila queda sin el componente.
    public static void write(ItemStack backpack, List<ItemStack> slots)
    {
        clearLegacy(backpack);
        if (slots.stream().allMatch(ItemStack::isEmpty))
        {
            backpack.remove(DataComponents.CONTAINER);
        }
        else
        {
            backpack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots));
        }
    }

    public static void clear(ItemStack backpack)
    {
        clearLegacy(backpack);
        backpack.remove(DataComponents.CONTAINER);
    }

    // Cuantas cosas distintas lleva adentro.
    public static int storedStacks(ItemStack backpack)
    {
        ItemContainerContents contents = backpack.get(DataComponents.CONTAINER);
        int count = contents == null ? 0 : (int) contents.nonEmptyStream().count();
        return count + legacy(backpack).size();
    }

    public static boolean hasContents(ItemStack backpack)
    {
        return storedStacks(backpack) > 0;
    }

    private static ListTag legacy(ItemStack backpack)
    {
        CustomData data = backpack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(LEGACY_KEY))
        {
            return new ListTag();
        }
        return data.copyTag().getList(LEGACY_KEY, 10);
    }

    private static void clearLegacy(ItemStack backpack)
    {
        CustomData data = backpack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.contains(LEGACY_KEY))
        {
            CustomData.update(DataComponents.CUSTOM_DATA, backpack, tag -> tag.remove(LEGACY_KEY));
        }
    }

    private BackpackContents() {}
}

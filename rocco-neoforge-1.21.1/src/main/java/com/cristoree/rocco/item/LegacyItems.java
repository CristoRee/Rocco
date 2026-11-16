package com.cristoree.rocco.item;

import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;

// Cosas guardadas por el mod: en 1.21 los items se guardan distinto. Lo que viene de un mundo de
// 1.20.1 (Rocco, su mochila, los regalos pendientes) el juego no lo convierte solo porque son
// datos del mod, asi que se convierte aca con los mismos conversores que usa Minecraft.
public final class LegacyItems
{
    // La version de datos de Minecraft 1.20.1.
    private static final int VERSION_1_20_1 = 3465;

    // Lee un item guardado, de esta version o de la 1.20.1 ("Count" con mayuscula). Vacio si no se puede.
    public static ItemStack parse(HolderLookup.Provider registries, CompoundTag tag)
    {
        if (tag.contains("Count"))
        {
            tag = upgrade(tag);
        }
        return ItemStack.parseOptional(registries, tag);
    }

    // Guarda un item (vacio da un compuesto vacio, que parse lee como vacio).
    public static Tag save(HolderLookup.Provider registries, ItemStack stack)
    {
        return stack.isEmpty() ? new CompoundTag() : stack.save(registries);
    }

    // Guarda un item agregando sus datos a 'into' (por ejemplo el lugar, "Slot").
    public static Tag save(HolderLookup.Provider registries, ItemStack stack, CompoundTag into)
    {
        return stack.isEmpty() ? into : stack.save(registries, into);
    }

    private static CompoundTag upgrade(CompoundTag old)
    {
        try
        {
            Dynamic<Tag> fixed = DataFixers.getDataFixer().update(References.ITEM_STACK,
                    new Dynamic<>(NbtOps.INSTANCE, old), VERSION_1_20_1,
                    SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            return fixed.getValue() instanceof CompoundTag tag ? tag : old;
        }
        catch (RuntimeException e)
        {
            com.cristoree.rocco.RoccoMod.LOGGER.warn("[Rocco] No pude convertir un item de 1.20.1: {}", e.toString());
            return old;
        }
    }

    private LegacyItems() {}
}

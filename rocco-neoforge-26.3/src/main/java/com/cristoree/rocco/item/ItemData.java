package com.cristoree.rocco.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

// Los datos propios del mod en un item (el mate de Rocco, el diario). En 1.21 van en el
// componente de datos libres del juego, con las mismas claves de antes.
public final class ItemData
{
    // Una copia de los datos, o null si el item no tiene.
    @Nullable
    public static CompoundTag read(ItemStack stack)
    {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    public static boolean has(ItemStack stack, String key)
    {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(key);
    }

    public static void edit(ItemStack stack, Consumer<CompoundTag> change)
    {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, change);
    }

    private ItemData() {}
}

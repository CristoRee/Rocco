package com.cristoree.rocco.work;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

// Encantamientos en 1.21: son datos del mundo (un registro por partida), no constantes.
public final class Ench
{
    public static Registry<Enchantment> registry(RegistryAccess access)
    {
        return access.lookupOrThrow(Registries.ENCHANTMENT);
    }

    // El nivel de un encantamiento en un item (0 si no lo tiene). No hace falta el mundo: se busca por id.
    public static int level(ItemStack stack, ResourceKey<Enchantment> key)
    {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet())
        {
            if (entry.getKey().is(key))
            {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    // Lo que tiene encantado (en un libro, lo que guarda).
    public static ItemEnchantments of(ItemStack stack)
    {
        return EnchantmentHelper.getEnchantmentsForCrafting(stack);
    }

    // Cuantos encantamientos tiene.
    public static int count(ItemStack stack)
    {
        return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).size();
    }

    // "Eficiencia II", "Toque de seda".
    public static String name(Holder<Enchantment> enchantment, int level)
    {
        return Enchantment.getFullname(enchantment, level).getString();
    }

    // Los nombres de lo que tiene, como en su descripcion.
    public static List<String> names(ItemStack stack)
    {
        List<String> out = new ArrayList<>();
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : of(stack).entrySet())
        {
            out.add(name(entry.getKey(), entry.getIntValue()));
        }
        return out;
    }

    public static Component description(Holder<Enchantment> enchantment)
    {
        return enchantment.value().description();
    }

    private Ench() {}
}

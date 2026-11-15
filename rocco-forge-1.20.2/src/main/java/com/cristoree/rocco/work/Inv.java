package com.cristoree.rocco.work;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

// Cuentas y movimientos de items sobre cualquier contenedor: el inventario de Rocco, un cofre,
// un horno. Todo respeta el tope de cada stack y lo que el contenedor acepta en cada lugar.
public final class Inv
{
    public static String id(Item item)
    {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        return key == null ? "minecraft:air" : key.toString();
    }

    public static String id(ItemStack stack)
    {
        return id(stack.getItem());
    }

    public static Item item(String id)
    {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null)
        {
            return Items.AIR;
        }
        Item item = ForgeRegistries.ITEMS.getValue(key);
        return item == null ? Items.AIR : item;
    }

    public static int count(Container container, Predicate<ItemStack> filter)
    {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++)
        {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && filter.test(stack))
            {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static int count(Container container, Item item)
    {
        return count(container, stack -> stack.is(item));
    }

    // Cuantos de cada id hay, para el planificador.
    public static Map<String, Integer> tally(Container container)
    {
        Map<String, Integer> out = new LinkedHashMap<>();
        addTally(out, container);
        return out;
    }

    public static void addTally(Map<String, Integer> into, Container container)
    {
        for (int slot = 0; slot < container.getContainerSize(); slot++)
        {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty())
            {
                into.merge(id(stack), stack.getCount(), Integer::sum);
            }
        }
    }

    // Saca hasta 'amount' de lo que cumpla el filtro. Devuelve lo sacado, separado por stacks.
    public static List<ItemStack> take(Container container, Predicate<ItemStack> filter, int amount)
    {
        List<ItemStack> out = new ArrayList<>();
        int left = amount;

        for (int slot = 0; slot < container.getContainerSize() && left > 0; slot++)
        {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty() || !filter.test(stack))
            {
                continue;
            }

            int moved = Math.min(left, stack.getCount());
            out.add(container.removeItem(slot, moved));
            left -= moved;
        }

        if (!out.isEmpty())
        {
            container.setChanged();
        }
        return out;
    }

    public static List<ItemStack> take(Container container, Item item, int amount)
    {
        return take(container, stack -> stack.is(item), amount);
    }

    // Mete el stack donde entre: primero junta con los que ya hay, despues usa lugares vacios.
    // Devuelve lo que no entro (vacio si entro todo). No modifica el stack que recibe.
    public static ItemStack insert(Container container, ItemStack incoming)
    {
        ItemStack rest = incoming.copy();
        if (rest.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        for (int slot = 0; slot < container.getContainerSize() && !rest.isEmpty(); slot++)
        {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, rest) && container.canPlaceItem(slot, rest))
            {
                int room = Math.min(stack.getMaxStackSize(), container.getMaxStackSize()) - stack.getCount();
                int moved = Math.min(room, rest.getCount());
                if (moved > 0)
                {
                    stack.grow(moved);
                    rest.shrink(moved);
                }
            }
        }

        for (int slot = 0; slot < container.getContainerSize() && !rest.isEmpty(); slot++)
        {
            if (container.getItem(slot).isEmpty() && container.canPlaceItem(slot, rest))
            {
                int moved = Math.min(rest.getCount(), Math.min(rest.getMaxStackSize(), container.getMaxStackSize()));
                container.setItem(slot, rest.split(moved));
            }
        }

        container.setChanged();
        return rest;
    }

    // Cuantos de este item entrarian, sin meter nada.
    public static int room(Container container, ItemStack like)
    {
        int room = 0;
        int max = Math.min(like.getMaxStackSize(), container.getMaxStackSize());

        for (int slot = 0; slot < container.getContainerSize(); slot++)
        {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty())
            {
                if (container.canPlaceItem(slot, like))
                {
                    room += max;
                }
            }
            else if (ItemStack.isSameItemSameTags(stack, like))
            {
                room += Math.max(0, max - stack.getCount());
            }
        }
        return room;
    }

    // Entra todo esto junto? Simula sobre una copia para contar bien stacks parecidos.
    public static boolean fitsAll(Container container, List<ItemStack> stacks)
    {
        net.minecraft.world.SimpleContainer copy = new net.minecraft.world.SimpleContainer(container.getContainerSize());
        for (int slot = 0; slot < container.getContainerSize(); slot++)
        {
            copy.setItem(slot, container.getItem(slot).copy());
        }
        for (ItemStack stack : stacks)
        {
            if (!insert(copy, stack).isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    public static boolean isEmpty(Container container)
    {
        for (int slot = 0; slot < container.getContainerSize(); slot++)
        {
            if (!container.getItem(slot).isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    public static List<ItemStack> contents(Container container)
    {
        List<ItemStack> out = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++)
        {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty())
            {
                out.add(stack);
            }
        }
        return out;
    }

    private Inv() {}
}

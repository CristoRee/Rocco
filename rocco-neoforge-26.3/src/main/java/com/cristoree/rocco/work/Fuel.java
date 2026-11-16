package com.cristoree.rocco.work;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

import java.util.Optional;

// Cuanto dura un combustible en el horno. Desde 26.x es un componente del item que se calcula
// con un contexto, como lo hace el horno; casi siempre es un numero fijo.
public final class Fuel
{
    public static int burnTicks(ServerLevel level, ItemStack stack)
    {
        if (stack.isEmpty() || !stack.has(DataComponents.COOKING_FUEL))
        {
            return 0;
        }
        try
        {
            LootContext context = new LootContext.Builder(new LootParams.Builder(level).create(LootContextParamSets.EMPTY))
                    .create(Optional.empty());
            return ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0);
        }
        catch (RuntimeException e)
        {
            // Un combustible que pide datos que no hay (de otro mod): lo de un tronco.
            return 300;
        }
    }

    private Fuel() {}
}

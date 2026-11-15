package com.cristoree.rocco.item;

import com.cristoree.rocco.registry.ModItems;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraftforge.registries.ForgeRegistries;

// Mejorar una mochila: una receta comun de mesa de crafteo, mas el campo "source" con la mochila
// anterior. Al armarla le copia el NBT, asi lo que llevaba adentro pasa a la mochila nueva.
public final class BackpackUpgradeRecipe extends ShapedRecipe
{
    private final Item source;

    public BackpackUpgradeRecipe(ShapedRecipe shape, Item source)
    {
        super(shape.getGroup(), shape.category(), shape.getWidth(), shape.getHeight(), shape.getIngredients(),
                shape.getResultItem(RegistryAccess.EMPTY), shape.showNotification());
        this.source = source;
    }

    @Override
    public ItemStack assemble(CraftingContainer grid, RegistryAccess access)
    {
        ItemStack result = super.assemble(grid, access);
        for (int slot = 0; slot < grid.getContainerSize(); slot++)
        {
            ItemStack stack = grid.getItem(slot);
            if (stack.is(this.source) && stack.hasTag())
            {
                result.setTag(stack.getTag().copy());
                break;
            }
        }
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer()
    {
        return ModItems.BACKPACK_UPGRADE.get();
    }

    public static final class Serializer implements RecipeSerializer<BackpackUpgradeRecipe>
    {
        // Desde 1.20.2 las recetas se leen con un codec. Este lee la receta comun con el codec del
        // juego y despues el campo "source" del mismo objeto.
        private static final Codec<ResourceLocation> SOURCE = ResourceLocation.CODEC.fieldOf("source").codec();
        private static final Codec<BackpackUpgradeRecipe> CODEC = new Codec<>()
        {
            @Override
            public <T> DataResult<Pair<BackpackUpgradeRecipe, T>> decode(DynamicOps<T> ops, T input)
            {
                return RecipeSerializer.SHAPED_RECIPE.codec().decode(ops, input).flatMap(shape ->
                        SOURCE.parse(ops, input).map(source -> Pair.of(
                                new BackpackUpgradeRecipe(shape.getFirst(), ForgeRegistries.ITEMS.getValue(source)),
                                shape.getSecond())));
            }

            @Override
            public <T> DataResult<T> encode(BackpackUpgradeRecipe recipe, DynamicOps<T> ops, T prefix)
            {
                return RecipeSerializer.SHAPED_RECIPE.codec().encode(recipe, ops, prefix).flatMap(shape ->
                        ops.mergeToMap(shape, ops.createString("source"),
                                ops.createString(ForgeRegistries.ITEMS.getKey(recipe.source).toString())));
            }
        };

        @Override
        public Codec<BackpackUpgradeRecipe> codec()
        {
            return CODEC;
        }

        @Override
        public BackpackUpgradeRecipe fromNetwork(FriendlyByteBuf buffer)
        {
            ShapedRecipe shape = RecipeSerializer.SHAPED_RECIPE.fromNetwork(buffer);
            return new BackpackUpgradeRecipe(shape, ForgeRegistries.ITEMS.getValue(buffer.readResourceLocation()));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, BackpackUpgradeRecipe recipe)
        {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buffer, recipe);
            buffer.writeResourceLocation(ForgeRegistries.ITEMS.getKey(recipe.source));
        }
    }
}

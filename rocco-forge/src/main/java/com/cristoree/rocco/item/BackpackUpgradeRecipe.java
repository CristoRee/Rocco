package com.cristoree.rocco.item;

import com.cristoree.rocco.registry.ModItems;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
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
        super(shape.getId(), shape.getGroup(), shape.category(), shape.getWidth(), shape.getHeight(), shape.getIngredients(),
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
        @Override
        public BackpackUpgradeRecipe fromJson(ResourceLocation id, JsonObject json)
        {
            ShapedRecipe shape = RecipeSerializer.SHAPED_RECIPE.fromJson(id, json);
            ResourceLocation source = new ResourceLocation(GsonHelper.getAsString(json, "source"));
            return new BackpackUpgradeRecipe(shape, ForgeRegistries.ITEMS.getValue(source));
        }

        @Override
        public BackpackUpgradeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer)
        {
            ShapedRecipe shape = RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buffer);
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

package com.cristoree.rocco.item;

import com.cristoree.rocco.registry.ModItems;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

// Mejorar una mochila: una receta comun de mesa de crafteo, mas el campo "source" con la mochila
// anterior. Al armarla le pasa lo que tenia la anterior (lo de adentro, el nombre), asi nada se pierde.
public final class BackpackUpgradeRecipe extends ShapedRecipe
{
    private final ShapedRecipePattern shape;
    private final ItemStack result;
    private final Item source;

    public BackpackUpgradeRecipe(String group, CraftingBookCategory category, ShapedRecipePattern shape, ItemStack result,
                                 boolean showNotification, Item source)
    {
        super(group, category, shape, result, showNotification);
        this.shape = shape;
        this.result = result;
        this.source = source;
    }

    @Override
    public ItemStack assemble(CraftingInput grid, HolderLookup.Provider registries)
    {
        ItemStack result = super.assemble(grid, registries);
        for (int slot = 0; slot < grid.size(); slot++)
        {
            ItemStack stack = grid.getItem(slot);
            if (stack.is(this.source) && !stack.getComponentsPatch().isEmpty())
            {
                result.applyComponents(stack.getComponentsPatch());
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
        // Los mismos campos que una receta con forma del juego, mas "source".
        private static final MapCodec<BackpackUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(ShapedRecipe::getGroup),
                CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(ShapedRecipe::category),
                ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.shape),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
                Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(ShapedRecipe::showNotification),
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("source").forGetter(recipe -> recipe.source)
        ).apply(instance, BackpackUpgradeRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, BackpackUpgradeRecipe> STREAM_CODEC = StreamCodec.of(
                (buffer, recipe) ->
                {
                    buffer.writeUtf(recipe.getGroup());
                    buffer.writeEnum(recipe.category());
                    ShapedRecipePattern.STREAM_CODEC.encode(buffer, recipe.shape);
                    ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
                    buffer.writeBoolean(recipe.showNotification());
                    buffer.writeResourceLocation(BuiltInRegistries.ITEM.getKey(recipe.source));
                },
                buffer -> new BackpackUpgradeRecipe(buffer.readUtf(), buffer.readEnum(CraftingBookCategory.class),
                        ShapedRecipePattern.STREAM_CODEC.decode(buffer), ItemStack.STREAM_CODEC.decode(buffer),
                        buffer.readBoolean(), BuiltInRegistries.ITEM.get(buffer.readResourceLocation())));

        @Override
        public MapCodec<BackpackUpgradeRecipe> codec()
        {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, BackpackUpgradeRecipe> streamCodec()
        {
            return STREAM_CODEC;
        }
    }
}

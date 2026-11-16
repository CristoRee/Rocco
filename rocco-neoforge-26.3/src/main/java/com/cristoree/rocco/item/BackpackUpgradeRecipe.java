package com.cristoree.rocco.item;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.NormalCraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;

import java.util.List;

// Mejorar una mochila: una receta comun de mesa de crafteo, mas el campo "source" con la mochila
// anterior. Al armarla le pasa lo que tenia la anterior (lo de adentro, el nombre), asi nada se pierde.
public final class BackpackUpgradeRecipe extends NormalCraftingRecipe
{
    private final ShapedRecipePattern pattern;
    private final ItemStackTemplate result;
    private final Item source;
    // La receta con forma del juego hace todo lo demas (reconocer la forma, el libro de recetas).
    private final ShapedRecipe shape;

    public BackpackUpgradeRecipe(Recipe.CommonInfo common, CraftingRecipe.CraftingBookInfo book, ShapedRecipePattern pattern,
                                 ItemStackTemplate result, Item source)
    {
        super(common, book);
        this.pattern = pattern;
        this.result = result;
        this.source = source;
        this.shape = new ShapedRecipe(common, book, pattern, result);
    }

    @Override
    public boolean matches(CraftingInput grid, Level level)
    {
        return this.shape.matches(grid, level);
    }

    @Override
    public ItemStack assemble(CraftingInput grid)
    {
        ItemStack result = this.shape.assemble(grid);
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
    protected PlacementInfo createPlacementInfo()
    {
        return this.shape.placementInfo();
    }

    @Override
    public List<RecipeDisplay> display()
    {
        return this.shape.display();
    }

    @Override
    public RecipeSerializer<BackpackUpgradeRecipe> getSerializer()
    {
        return SERIALIZER;
    }

    // Los mismos campos que una receta con forma del juego, mas "source".
    private static final MapCodec<BackpackUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
            CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(recipe -> recipe.bookInfo),
            ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("source").forGetter(recipe -> recipe.source)
    ).apply(instance, BackpackUpgradeRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, BackpackUpgradeRecipe> STREAM_CODEC = StreamCodec.of(
            (buffer, recipe) ->
            {
                Recipe.CommonInfo.STREAM_CODEC.encode(buffer, recipe.commonInfo);
                CraftingRecipe.CraftingBookInfo.STREAM_CODEC.encode(buffer, recipe.bookInfo);
                ShapedRecipePattern.STREAM_CODEC.encode(buffer, recipe.pattern);
                ItemStackTemplate.STREAM_CODEC.encode(buffer, recipe.result);
                buffer.writeIdentifier(BuiltInRegistries.ITEM.getKey(recipe.source));
            },
            buffer -> new BackpackUpgradeRecipe(Recipe.CommonInfo.STREAM_CODEC.decode(buffer),
                    CraftingRecipe.CraftingBookInfo.STREAM_CODEC.decode(buffer), ShapedRecipePattern.STREAM_CODEC.decode(buffer),
                    ItemStackTemplate.STREAM_CODEC.decode(buffer), BuiltInRegistries.ITEM.getValue(buffer.readIdentifier())));

    public static final RecipeSerializer<BackpackUpgradeRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);
}

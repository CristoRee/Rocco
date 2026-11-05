package com.cristoree.rocco.work;

import com.cristoree.rocco.entity.RoccoEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.crafting.IShapedRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Puente entre las recetas reales del juego (vanilla y de otros mods) y el planificador, y el
// que craftea de verdad: arma la grilla con lo que Rocco tiene y le pide el resultado a la receta,
// asi respeta todo lo que la receta quiera hacer (sobrantes como baldes, NBT, etc.).
public final class Recipes
{
    private static RecipeManager indexedFor;
    private static Map<String, List<CraftPlanner.Recipe>> byOutput = Map.of();
    private static Map<String, CraftingRecipe> byId = Map.of();

    public static CraftPlanner.Book book(ServerLevel level)
    {
        ensureIndex(level);
        Map<String, List<CraftPlanner.Recipe>> index = byOutput;
        return item -> index.getOrDefault(item, List.of());
    }

    public static List<CraftPlanner.Recipe> recipesFor(ServerLevel level, String item)
    {
        ensureIndex(level);
        return byOutput.getOrDefault(item, List.of());
    }

    // El indice se arma una vez por RecipeManager: con /reload el juego crea uno nuevo y se rehace.
    private static synchronized void ensureIndex(ServerLevel level)
    {
        RecipeManager manager = level.getRecipeManager();
        if (manager == indexedFor)
        {
            return;
        }

        Map<String, List<CraftPlanner.Recipe>> outputs = new HashMap<>();
        Map<String, CraftingRecipe> ids = new HashMap<>();

        for (CraftingRecipe recipe : manager.getAllRecipesFor(RecipeType.CRAFTING))
        {
            CraftPlanner.Recipe simple = simplify(recipe, level);
            if (simple != null)
            {
                outputs.computeIfAbsent(simple.output(), key -> new ArrayList<>()).add(simple);
                ids.put(simple.id(), recipe);
            }
        }

        // La receta "oficial" (misma id que el item) va primera: palo de tablas antes que de bambu.
        for (Map.Entry<String, List<CraftPlanner.Recipe>> entry : outputs.entrySet())
        {
            entry.getValue().sort((a, b) -> Boolean.compare(!a.id().equals(entry.getKey()), !b.id().equals(entry.getKey())));
        }

        byOutput = outputs;
        byId = ids;
        indexedFor = manager;
    }

    private static boolean plainTool(ItemStack result)
    {
        net.minecraft.nbt.CompoundTag tag = result.getTag();
        return tag != null && tag.size() == 1 && tag.contains("Damage") && result.getDamageValue() == 0;
    }

    private static CraftPlanner.Recipe simplify(CraftingRecipe recipe, ServerLevel level)
    {
        // Las mochilas las fabrica el jugador: la receta pasa lo que llevan adentro a la nueva.
        if (recipe.isSpecial() || recipe instanceof com.cristoree.rocco.item.BackpackUpgradeRecipe)
        {
            return null;
        }

        ItemStack result;
        try
        {
            result = recipe.getResultItem(level.registryAccess());
        }
        catch (RuntimeException e)
        {
            return null;
        }
        // Una herramienta nueva trae {Damage:0} (asi las arma el juego en 1.20.1): eso no la hace
        // especial. Antes se descartaban todas, y no podia craftear picos, hachas ni tijeras.
        if (result.isEmpty() || (result.hasTag() && !plainTool(result)))
        {
            return null;
        }

        Map<List<String>, Integer> grouped = new LinkedHashMap<>();
        for (Ingredient ingredient : recipe.getIngredients())
        {
            if (ingredient.isEmpty())
            {
                continue;
            }
            List<String> options = Arrays.stream(ingredient.getItems())
                    .filter(stack -> !stack.isEmpty())
                    .map(Inv::id)
                    .distinct()
                    .toList();
            if (options.isEmpty())
            {
                // Un tag vacio: la receta no se puede hacer en este modpack.
                return null;
            }
            grouped.merge(options, 1, Integer::sum);
        }

        if (grouped.isEmpty())
        {
            return null;
        }

        List<CraftPlanner.Ingredient> ingredients = new ArrayList<>();
        grouped.forEach((options, count) -> ingredients.add(new CraftPlanner.Ingredient(options, count)));

        return new CraftPlanner.Recipe(recipe.getId().toString(), Inv.id(result), result.getCount(),
                List.copyOf(ingredients), !recipe.canCraftInDimensions(2, 2));
    }

    // Craftea una vez la receta con lo que Rocco lleva. Devuelve false si no tenia lo necesario.
    public static boolean craftOnce(RoccoEntity rocco, ServerLevel level, String recipeId)
    {
        ensureIndex(level);
        CraftingRecipe recipe = byId.get(recipeId);
        if (recipe == null)
        {
            return false;
        }

        Container inventory = rocco.inventory();
        TransientCraftingContainer grid = new TransientCraftingContainer(new NoMenu(), 3, 3);
        int[] reserved = new int[inventory.getContainerSize()];

        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        int width = recipe instanceof IShapedRecipe<?> shaped ? shaped.getRecipeWidth() : 3;

        for (int i = 0; i < ingredients.size(); i++)
        {
            Ingredient ingredient = ingredients.get(i);
            if (ingredient.isEmpty())
            {
                continue;
            }

            int slot = findFor(inventory, ingredient, reserved);
            if (slot < 0)
            {
                return false;
            }
            reserved[slot]++;

            int gridSlot = recipe instanceof IShapedRecipe<?> ? (i % width) + (i / width) * 3 : i;
            grid.setItem(gridSlot, inventory.getItem(slot).copyWithCount(1));
        }

        if (!recipe.matches(grid, level))
        {
            return false;
        }

        ItemStack result = recipe.assemble(grid, level.registryAccess());
        NonNullList<ItemStack> leftovers = recipe.getRemainingItems(grid);

        for (int slot = 0; slot < reserved.length; slot++)
        {
            if (reserved[slot] > 0)
            {
                inventory.removeItem(slot, reserved[slot]);
            }
        }

        rocco.keep(result);
        for (ItemStack leftover : leftovers)
        {
            if (!leftover.isEmpty())
            {
                rocco.keep(leftover);
            }
        }
        inventory.setChanged();
        return true;
    }

    private static int findFor(Container inventory, Ingredient ingredient, int[] reserved)
    {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getCount() > reserved[slot] && ingredient.test(stack))
            {
                return slot;
            }
        }
        return -1;
    }

    // "3 de Roca y 2 de Palo, en una mesa de crafteo". null si no hay receta.
    public static String describe(ServerLevel level, String item)
    {
        List<CraftPlanner.Recipe> recipes = recipesFor(level, item);
        if (recipes.isEmpty())
        {
            return null;
        }

        CraftPlanner.Recipe recipe = recipes.get(0);
        List<CraftPlanner.Missing> parts = recipe.ingredients().stream()
                .map(ingredient -> new CraftPlanner.Missing(ingredient.options(), ingredient.count()))
                .toList();

        String text = Names.missing(parts);
        if (recipe.outputCount() > 1)
        {
            text += " (salen " + recipe.outputCount() + ")";
        }
        return text + (recipe.needsTable() ? ", en una mesa de crafteo" : ", se hace en la mano");
    }

    // ---------- horno ----------

    public static Optional<? extends AbstractCookingRecipe> cooking(ServerLevel level, RecipeType<? extends AbstractCookingRecipe> type, ItemStack input)
    {
        SimpleContainer probe = new SimpleContainer(input.copyWithCount(1));
        if (type == RecipeType.SMOKING)
        {
            return level.getRecipeManager().getRecipeFor(RecipeType.SMOKING, probe, level);
        }
        if (type == RecipeType.BLASTING)
        {
            return level.getRecipeManager().getRecipeFor(RecipeType.BLASTING, probe, level);
        }
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, probe, level);
    }

    public static ItemStack cookedResult(ServerLevel level, ItemStack input)
    {
        return cooking(level, RecipeType.SMELTING, input)
                .map(recipe -> recipe.getResultItem(level.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    // La grilla de crafteo pide un menu; este no muestra nada ni avisa a nadie.
    private static final class NoMenu extends AbstractContainerMenu
    {
        NoMenu()
        {
            super(null, -1);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index)
        {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player)
        {
            return true;
        }

        @Override
        public void slotsChanged(Container container)
        {
        }
    }

    private Recipes() {}
}

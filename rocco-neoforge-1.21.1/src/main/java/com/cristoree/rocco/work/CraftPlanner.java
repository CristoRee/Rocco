package com.cristoree.rocco.work;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Arma el plan para fabricar algo: que recetas usar, en que orden, que sale de lo que ya hay y
// que falta. No toca Minecraft (trabaja con ids en texto), asi se puede probar sin abrir el juego.
//
// Lo que falta se informa al nivel de la receta pedida: para un pico de piedra sin nada encima
// dice "3 de roca y 2 palos", no "3 de roca y 1 tronco". Es lo que entiende un jugador.
public final class CraftPlanner
{
    public static final String CRAFTING_TABLE = "minecraft:crafting_table";

    // Profundidad maxima de recetas encadenadas (tronco > tabla > palo > antorcha ya son 3).
    private static final int MAX_DEPTH = 6;

    // Un hueco de la receta agrupado: cualquiera de estas opciones, tantas veces.
    public record Ingredient(List<String> options, int count) {}

    public record Recipe(String id, String output, int outputCount, List<Ingredient> ingredients, boolean needsTable) {}

    public interface Book
    {
        List<Recipe> recipesFor(String item);
    }

    public record Step(Recipe recipe, int times) {}

    public record Missing(List<String> options, int count) {}

    // steps esta en orden de ejecucion. fromStock es lo que se saca de lo que ya habia.
    // placesTable: hace falta fabricar y poner una mesa antes de los pasos grandes.
    public record Plan(String target, int count, List<Step> steps, Map<String, Integer> fromStock,
                       List<Missing> missing, boolean craftable, boolean placesTable)
    {
        public boolean complete()
        {
            return craftable && missing.isEmpty();
        }

        public boolean needsTable()
        {
            return steps.stream().anyMatch(step -> step.recipe().needsTable());
        }

        public int missingTotal()
        {
            return missing.stream().mapToInt(Missing::count).sum();
        }
    }

    // tableAvailable: hay una mesa cerca o una en el inventario. Si no hay y la receta la
    // necesita, se agrega al plan fabricar una (y lo que falte para ella).
    public static Plan plan(Book book, Map<String, Integer> stock, String target, int count, boolean tableAvailable)
    {
        List<Recipe> recipes = usable(book, target);
        if (recipes.isEmpty())
        {
            return new Plan(target, count, List.of(), Map.of(),
                    List.of(new Missing(List.of(target), count)), false, false);
        }

        Plan best = null;
        for (Recipe recipe : recipes)
        {
            Plan candidate = planWith(book, stock, recipe, count, tableAvailable);
            if (best == null || candidate.missingTotal() < best.missingTotal())
            {
                best = candidate;
            }
            if (best.complete())
            {
                break;
            }
        }
        return best;
    }

    private static Plan planWith(Book book, Map<String, Integer> stock, Recipe recipe, int count, boolean tableAvailable)
    {
        Sim sim = new Sim(stock);
        int times = timesFor(recipe, count);
        List<Missing> missing = new ArrayList<>();

        Deque<String> path = new ArrayDeque<>();
        path.push(recipe.output());

        for (Ingredient ingredient : recipe.ingredients())
        {
            int left = obtain(book, sim, ingredient.options(), ingredient.count() * times, path, 1);
            if (left > 0)
            {
                missing.add(new Missing(ingredient.options(), left));
            }
        }

        sim.steps.add(new Step(recipe, times));
        sim.produce(recipe.output(), recipe.outputCount() * times);

        boolean placesTable = false;
        boolean anyBig = sim.steps.stream().anyMatch(step -> step.recipe().needsTable());

        if (anyBig && !tableAvailable)
        {
            // La mesa se hace antes que todo lo demas, con lo que sobro.
            Sim tableSim = sim.copy();
            tableSim.steps.clear();
            int left = obtainByCrafting(book, tableSim, CRAFTING_TABLE, 1, new ArrayDeque<>(), 1);
            if (left > 0)
            {
                missing.add(new Missing(List.of(CRAFTING_TABLE), 1));
            }
            else
            {
                List<Step> ordered = new ArrayList<>(tableSim.steps);
                ordered.addAll(sim.steps);
                tableSim.steps.clear();
                tableSim.steps.addAll(ordered);
                sim = tableSim;
            }
            placesTable = true;
        }

        return new Plan(recipe.output(), count, List.copyOf(sim.steps), Map.copyOf(sim.taken),
                List.copyOf(missing), true, placesTable);
    }

    // Saca lo que pueda del stock y lo que falte intenta fabricarlo. Devuelve cuanto no consiguio.
    private static int obtain(Book book, Sim sim, List<String> options, int amount, Deque<String> path, int depth)
    {
        int left = amount;

        // Primero lo que ya se fabrico en este plan (sobrantes), despues lo que habia.
        for (String option : options)
        {
            left -= sim.takeCrafted(option, left);
            if (left == 0)
            {
                return 0;
            }
        }
        for (String option : options)
        {
            left -= sim.takeStock(option, left);
            if (left == 0)
            {
                return 0;
            }
        }

        for (String option : options)
        {
            Sim attempt = sim.copy();
            if (obtainByCrafting(book, attempt, option, left, path, depth) == 0)
            {
                sim.adopt(attempt);
                return 0;
            }
        }
        return left;
    }

    // Fabrica 'amount' de 'item' entero o nada. Devuelve 0 si pudo, 'amount' si no.
    private static int obtainByCrafting(Book book, Sim sim, String item, int amount, Deque<String> path, int depth)
    {
        if (depth > MAX_DEPTH || path.contains(item))
        {
            return amount;
        }

        path.push(item);
        try
        {
            for (Recipe recipe : usable(book, item))
            {
                Sim attempt = sim.copy();
                int times = timesFor(recipe, amount);
                boolean ok = true;

                for (Ingredient ingredient : recipe.ingredients())
                {
                    if (obtain(book, attempt, ingredient.options(), ingredient.count() * times, path, depth + 1) > 0)
                    {
                        ok = false;
                        break;
                    }
                }

                if (ok)
                {
                    attempt.steps.add(new Step(recipe, times));
                    attempt.produce(item, recipe.outputCount() * times);
                    attempt.takeCrafted(item, amount);
                    sim.adopt(attempt);
                    return 0;
                }
            }
            return amount;
        }
        finally
        {
            path.pop();
        }
    }

    // Las recetas que se comen su propio resultado (teñir lana, por ejemplo) no sirven para
    // conseguir el item: solo lo transforman.
    private static List<Recipe> usable(Book book, String item)
    {
        List<Recipe> out = new ArrayList<>();
        for (Recipe recipe : book.recipesFor(item))
        {
            boolean selfConsuming = recipe.ingredients().stream()
                    .anyMatch(ingredient -> ingredient.options().contains(item));
            if (!selfConsuming && recipe.outputCount() > 0 && !recipe.ingredients().isEmpty())
            {
                out.add(recipe);
            }
        }
        return out;
    }

    private static int timesFor(Recipe recipe, int amount)
    {
        return Math.max(1, (amount + recipe.outputCount() - 1) / recipe.outputCount());
    }

    // Estado de la simulacion. Se copia en cada intento para poder deshacerlo barato.
    private static final class Sim
    {
        final Map<String, Integer> stock;
        final Map<String, Integer> crafted;
        final Map<String, Integer> taken;
        final List<Step> steps;

        Sim(Map<String, Integer> stock)
        {
            this(new HashMap<>(stock), new HashMap<>(), new LinkedHashMap<>(), new ArrayList<>());
        }

        private Sim(Map<String, Integer> stock, Map<String, Integer> crafted, Map<String, Integer> taken, List<Step> steps)
        {
            this.stock = stock;
            this.crafted = crafted;
            this.taken = taken;
            this.steps = steps;
        }

        Sim copy()
        {
            return new Sim(new HashMap<>(this.stock), new HashMap<>(this.crafted),
                    new LinkedHashMap<>(this.taken), new ArrayList<>(this.steps));
        }

        void adopt(Sim other)
        {
            replace(this.stock, other.stock);
            replace(this.crafted, other.crafted);
            replace(this.taken, other.taken);
            this.steps.clear();
            this.steps.addAll(other.steps);
        }

        int takeStock(String item, int wanted)
        {
            int got = take(this.stock, item, wanted);
            if (got > 0)
            {
                this.taken.merge(item, got, Integer::sum);
            }
            return got;
        }

        int takeCrafted(String item, int wanted)
        {
            return take(this.crafted, item, wanted);
        }

        void produce(String item, int amount)
        {
            this.crafted.merge(item, amount, Integer::sum);
        }

        private static int take(Map<String, Integer> from, String item, int wanted)
        {
            int have = from.getOrDefault(item, 0);
            int got = Math.min(have, wanted);
            if (got > 0)
            {
                from.put(item, have - got);
            }
            return got;
        }

        private static <K, V> void replace(Map<K, V> target, Map<K, V> source)
        {
            target.clear();
            target.putAll(source);
        }
    }

    private CraftPlanner() {}
}

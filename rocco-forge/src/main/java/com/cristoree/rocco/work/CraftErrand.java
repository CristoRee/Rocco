package com.cristoree.rocco.work;

import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Fabricar algo de punta a punta, como lo haria un jugador:
//  1. Arma el plan con lo que lleva. Si falta algo, dice exactamente que y espera a que se lo den.
//  2. Si no se lo dan (o le dicen "busca en el cofre"), va a los cofres y saca lo justo.
//  3. Si ni ahi hay, y se puede, tala un arbol o pica piedra.
//  4. Va a la mesa de crafteo (o pone una, o se la fabrica) y lo craftea con la receta real.
//  5. Se lo alcanza al jugador, salvo que sea para el.
public final class CraftErrand extends Errand
{
    private enum Phase { PLAN, WAIT, SEARCH, AFTER_SEARCH, GATHER, PREPARE, TABLE_IN_HAND, PLACE_TABLE, CRAFT, AFTER_CRAFT, END }

    private static final int GIVE_UP_TICKS = 20 * 180;

    private final List<Item> candidates;
    private final int wantedCount;
    private final boolean keep;
    private final boolean quiet;
    // Sin decir nada en todo el camino (cuidando la casa se hace las tijeras solo).
    private boolean silent;

    private Phase phase = Phase.PLAN;
    private Item target = Items.AIR;
    private int count;
    private CraftPlanner.Plan plan;

    private int waitStart;
    private int lastMissing = -1;
    private boolean searched;
    private boolean searchRequested;
    private boolean placedTable;
    private final List<Runnable> gatherQueue = new ArrayList<>();
    private boolean gatherPlanned;
    @Nullable
    private BlockPos table;
    private boolean crafted;
    // Ya conto al arrancar que iba a los cofres (o que ya los habia mirado): no lo repite despues.
    private boolean toldChests;
    private boolean toldGathering;

    // count 0 = lo que salga de una vez (4 antorchas, 1 pico).
    public CraftErrand(RoccoEntity rocco, @Nullable Player requester, List<Item> candidates, int count, boolean keep, boolean quiet)
    {
        super(rocco, requester);
        this.candidates = candidates.size() > 12 ? candidates.subList(0, 12) : candidates;
        this.wantedCount = count;
        this.keep = keep;
        this.quiet = quiet;
    }

    public CraftErrand silently()
    {
        this.silent = true;
        return this;
    }

    @Override
    protected void say(String category, String... keyValues)
    {
        if (!this.silent)
        {
            super.say(category, keyValues);
        }
    }

    @Override
    protected Status step()
    {
        return switch (this.phase)
        {
            case PLAN -> plan();
            case WAIT -> waitForItems();
            case SEARCH -> search();
            case AFTER_SEARCH -> afterSearch();
            case GATHER -> gather();
            case PREPARE -> prepare();
            case TABLE_IN_HAND -> tableInHand();
            case PLACE_TABLE -> placeTable();
            case CRAFT -> craft();
            case AFTER_CRAFT -> afterCraft();
            case END -> Status.DONE;
        };
    }

    // ---------- planear ----------

    // Que hacer primero, dicho una sola vez: si con lo que lleva alcanza, lo hace; si lo que falta
    // esta en los cofres, va a buscarlo; si es madera o piedra, la consigue; y solo si falta algo
    // que nadie mas que el jugador puede darle, dice exactamente que y espera.
    private Status plan()
    {
        if (!choose(Inv.tally(this.rocco.inventory())))
        {
            say("craft_unknown", "item", Names.name(this.candidates.isEmpty() ? Items.AIR : this.candidates.get(0)));
            return Status.FAILED;
        }

        if (this.plan.complete())
        {
            this.phase = Phase.PREPARE;
            return Status.RUNNING;
        }

        List<CraftPlanner.Missing> stillMissing = this.plan.missing();
        if (!this.searched)
        {
            CraftPlanner.Plan withChests = planWith(stockWithChests());
            if (withChests.complete())
            {
                say("craft_fetching", "item", Names.name(this.target), "missing", Names.missing(this.plan.missing()));
                this.toldChests = true;
                this.phase = Phase.SEARCH;
                return Status.RUNNING;
            }
            stillMissing = withChests.missing();
            this.toldChests = true;
            if (RoccoCommonConfig.WORK_GATHER_FROM_NATURE.get() && allGatherable(stillMissing))
            {
                say("craft_gathering", "item", Names.name(this.target), "missing", Names.missing(stillMissing));
                this.toldGathering = true;
                this.phase = Phase.SEARCH;
                return Status.RUNNING;
            }
        }

        say("craft_missing", "item", Names.name(this.target), "missing", Names.missing(stillMissing));
        this.lastMissing = this.plan.missingTotal();
        this.phase = Phase.WAIT;
        this.waitStart = this.age;
        return Status.RUNNING;
    }

    // Lo que lleva encima mas lo que hay en los cofres de alrededor.
    private Map<String, Integer> stockWithChests()
    {
        Map<String, Integer> combined = Inv.tally(this.rocco.inventory());
        for (BlockPos chest : Storage.findWide(this.level, this.rocco.blockPosition(), requester(), RoccoCommonConfig.WORK_SEARCH_RADIUS.get()))
        {
            Container container = Storage.open(this.level, chest, requester());
            if (container != null)
            {
                Inv.addTally(combined, container);
            }
        }
        return combined;
    }

    private CraftPlanner.Plan planWith(Map<String, Integer> stock)
    {
        boolean tableAvailable = tableNearby() != null || stock.getOrDefault(CraftPlanner.CRAFTING_TABLE, 0) > 0;
        return CraftPlanner.plan(Recipes.book(this.level), stock, Inv.id(this.target), this.count, tableAvailable);
    }

    // Madera (troncos, tablas, palos, la mesa) o roca: lo que sabe sacar de la naturaleza.
    private static boolean allGatherable(List<CraftPlanner.Missing> missing)
    {
        if (missing.isEmpty())
        {
            return false;
        }
        for (CraftPlanner.Missing need : missing)
        {
            boolean any = false;
            for (String option : need.options())
            {
                if (gatherable(Inv.item(option)))
                {
                    any = true;
                    break;
                }
            }
            if (!any)
            {
                return false;
            }
        }
        return true;
    }

    private static boolean gatherable(Item item)
    {
        ItemStack probe = new ItemStack(item);
        return probe.is(ItemTags.PLANKS) || probe.is(ItemTags.LOGS) || item == Items.STICK
                || item == Items.CRAFTING_TABLE || item == Items.COBBLESTONE;
    }

    // De las opciones (por ejemplo "tablas" de cualquier madera), la que se pueda hacer, o la
    // que menos cosas pida.
    private boolean choose(Map<String, Integer> stock)
    {
        CraftPlanner.Book book = Recipes.book(this.level);
        boolean tableAvailable = tableNearby() != null || stock.getOrDefault(CraftPlanner.CRAFTING_TABLE, 0) > 0;

        CraftPlanner.Plan best = null;
        Item bestItem = Items.AIR;
        int bestCount = 0;

        for (Item candidate : this.candidates)
        {
            String id = Inv.id(candidate);
            List<CraftPlanner.Recipe> recipes = book.recipesFor(id);
            if (recipes.isEmpty())
            {
                continue;
            }

            int amount = this.wantedCount > 0 ? this.wantedCount : recipes.get(0).outputCount();
            CraftPlanner.Plan candidatePlan = CraftPlanner.plan(book, stock, id, amount, tableAvailable);
            if (best == null || better(candidatePlan, best))
            {
                best = candidatePlan;
                bestItem = candidate;
                bestCount = amount;
            }
            if (best.complete())
            {
                break;
            }
        }

        if (best == null || !best.craftable())
        {
            return false;
        }
        this.plan = best;
        this.target = bestItem;
        this.count = bestCount;
        return true;
    }

    private static boolean better(CraftPlanner.Plan candidate, CraftPlanner.Plan current)
    {
        if (candidate.complete() != current.complete())
        {
            return candidate.complete();
        }
        return candidate.missingTotal() < current.missingTotal();
    }

    // ---------- esperar materiales ----------

    private Status waitForItems()
    {
        if (this.age % 20 == 0)
        {
            choose(Inv.tally(this.rocco.inventory()));
            if (this.plan.complete())
            {
                say("craft_got_it");
                this.phase = Phase.PREPARE;
                return Status.RUNNING;
            }

            // Le dieron una parte: avisa lo que queda, sin repetir si nada cambio.
            int missing = this.plan.missingTotal();
            if (missing < this.lastMissing)
            {
                say("craft_still_need", "missing", Names.missing(this.plan.missing()));
            }
            this.lastMissing = missing;
        }

        int waited = this.age - this.waitStart;
        int patience = RoccoCommonConfig.WORK_WAIT_FOR_ITEMS_SECONDS.get() * 20;

        if (this.searchRequested)
        {
            this.searchRequested = false;
            this.phase = Phase.SEARCH;
            return Status.RUNNING;
        }
        if (!this.searched && waited > patience)
        {
            if (!this.toldChests)
            {
                say("craft_search_myself");
            }
            this.phase = Phase.SEARCH;
            return Status.RUNNING;
        }
        // Ya busco en los cofres y no le diste lo que falta: si una parte es madera o piedra, la
        // consigue mientras tanto, asi despues solo falta lo tuyo.
        if (this.searched && !this.gatherPlanned && waited > patience && RoccoCommonConfig.WORK_GATHER_FROM_NATURE.get())
        {
            this.phase = Phase.GATHER;
            return Status.RUNNING;
        }
        if (this.searched && waited > GIVE_UP_TICKS)
        {
            say("craft_give_up", "item", Names.name(this.target));
            return Status.FAILED;
        }
        return Status.RUNNING;
    }

    @Override
    protected boolean waitingPassively()
    {
        return this.phase == Phase.WAIT;
    }

    @Override
    public boolean signal(String signal)
    {
        if (super.signal(signal))
        {
            return true;
        }
        if ("buscar".equals(signal) && this.phase == Phase.WAIT)
        {
            say("craft_search_ok");
            this.searchRequested = true;
            return true;
        }
        return false;
    }

    // ---------- buscar en los cofres ----------

    private Status search()
    {
        this.searched = true;

        List<BlockPos> chests = Storage.findWide(this.level, this.rocco.blockPosition(), requester(), RoccoCommonConfig.WORK_SEARCH_RADIUS.get());
        Map<BlockPos, Container> opened = new LinkedHashMap<>();
        Map<String, Integer> combined = Inv.tally(this.rocco.inventory());
        for (BlockPos chest : chests)
        {
            Container container = Storage.open(this.level, chest, requester());
            if (container != null)
            {
                opened.put(chest, container);
                Inv.addTally(combined, container);
            }
        }

        // El plan con todo lo que hay alrededor dice que sacar; lo que ya lleva no se busca.
        boolean tableAvailable = tableNearby() != null || combined.getOrDefault(CraftPlanner.CRAFTING_TABLE, 0) > 0;
        CraftPlanner.Plan withChests = CraftPlanner.plan(Recipes.book(this.level), combined,
                Inv.id(this.target), this.count, tableAvailable);

        Map<String, Integer> needed = new LinkedHashMap<>();
        withChests.fromStock().forEach((id, amount) ->
        {
            int missing = amount - Inv.count(this.rocco.inventory(), Inv.item(id));
            if (missing > 0)
            {
                needed.put(id, missing);
            }
        });

        List<FetchErrand.Pickup> pickups = new ArrayList<>();
        for (Map.Entry<BlockPos, Container> chest : opened.entrySet())
        {
            for (Map.Entry<String, Integer> need : needed.entrySet())
            {
                if (need.getValue() <= 0)
                {
                    continue;
                }
                Item item = Inv.item(need.getKey());
                int take = Math.min(need.getValue(), Inv.count(chest.getValue(), item));
                if (take > 0)
                {
                    pickups.add(new FetchErrand.Pickup(chest.getKey(), stack -> stack.is(item), take));
                    need.setValue(need.getValue() - take);
                }
            }
        }

        if (pickups.isEmpty())
        {
            this.phase = Phase.GATHER;
            return Status.RUNNING;
        }

        delegate(new FetchErrand(this.rocco, requester(), pickups, stack -> false, Names.name(this.target), 0, false, true));
        this.phase = Phase.AFTER_SEARCH;
        return Status.RUNNING;
    }

    @Override
    protected boolean childDone(Errand child, boolean success)
    {
        if (child instanceof PlaceErrand place)
        {
            this.table = place.placedAt();
            return success;
        }
        // Que no haya encontrado o juntado todo no corta: se vuelve a planear y se pide lo que falte.
        return true;
    }

    private Status afterSearch()
    {
        choose(Inv.tally(this.rocco.inventory()));
        if (this.plan.complete())
        {
            if (!this.toldChests)
            {
                say("craft_found_it");
            }
            this.phase = Phase.PREPARE;
            return Status.RUNNING;
        }
        this.phase = Phase.GATHER;
        return Status.RUNNING;
    }

    // ---------- conseguirlo de la naturaleza ----------

    private Status gather()
    {
        if (!this.gatherPlanned)
        {
            this.gatherPlanned = true;
            if (RoccoCommonConfig.WORK_GATHER_FROM_NATURE.get())
            {
                planGathering();
            }
        }

        if (!this.gatherQueue.isEmpty())
        {
            this.gatherQueue.remove(0).run();
            this.phase = Phase.AFTER_SEARCH;
            return Status.RUNNING;
        }

        choose(Inv.tally(this.rocco.inventory()));
        if (this.plan.complete())
        {
            this.phase = Phase.PREPARE;
            return Status.RUNNING;
        }

        say("craft_still_missing", "missing", Names.missing(this.plan.missing()));
        this.lastMissing = this.plan.missingTotal();
        this.phase = Phase.WAIT;
        this.waitStart = this.age;
        return Status.RUNNING;
    }

    // Madera (tablas, palos, troncos) sale de un arbol; roca, de la piedra de alrededor.
    private void planGathering()
    {
        boolean wood = false;
        int stone = 0;

        for (CraftPlanner.Missing missing : this.plan.missing())
        {
            for (String option : missing.options())
            {
                Item item = Inv.item(option);
                ItemStack probe = new ItemStack(item);
                if (probe.is(ItemTags.PLANKS) || probe.is(ItemTags.LOGS) || item == Items.STICK
                        || item == Items.CRAFTING_TABLE)
                {
                    wood = true;
                    break;
                }
                if (item == Items.COBBLESTONE)
                {
                    stone += missing.count();
                    break;
                }
            }
        }

        if (wood)
        {
            this.gatherQueue.add(() ->
            {
                if (!this.toldGathering)
                {
                    say("craft_going_to_chop");
                }
                delegate(new ChopErrand(this.rocco, requester(), true));
            });
        }
        if (stone > 0)
        {
            int amount = stone;
            this.gatherQueue.add(() ->
            {
                if (!this.toldGathering)
                {
                    say("craft_going_to_mine");
                }
                delegate(new GatherErrand(this.rocco, requester(), List.of(Items.COBBLESTONE), amount, true, true));
            });
        }
    }

    // ---------- mesa de crafteo ----------

    private Status prepare()
    {
        choose(Inv.tally(this.rocco.inventory()));
        if (!this.plan.complete())
        {
            this.phase = Phase.PLAN;
            return Status.RUNNING;
        }

        if (!this.plan.needsTable())
        {
            this.table = null;
            this.phase = Phase.CRAFT;
            return Status.RUNNING;
        }

        this.table = tableNearby();
        if (this.table != null)
        {
            this.phase = Phase.CRAFT;
            return Status.RUNNING;
        }

        if (Inv.count(this.rocco.inventory(), Items.CRAFTING_TABLE) > 0)
        {
            this.phase = Phase.PLACE_TABLE;
            return Status.RUNNING;
        }

        if (this.plan.placesTable())
        {
            // La mesa se hace en la mano (es de 2x2) y despues se pone.
            List<CraftPlanner.Step> tableSteps = stepsUntilTable();
            say("craft_making_table");
            work(RoccoTask.INTERACTING, this.rocco.blockPosition(), ItemStack.EMPTY, level -> runSteps(tableSteps));
            this.phase = Phase.TABLE_IN_HAND;
            return Status.RUNNING;
        }

        this.phase = Phase.PLAN;
        return Status.RUNNING;
    }

    private List<CraftPlanner.Step> stepsUntilTable()
    {
        List<CraftPlanner.Step> out = new ArrayList<>();
        for (CraftPlanner.Step step : this.plan.steps())
        {
            out.add(step);
            if (step.recipe().output().equals(CraftPlanner.CRAFTING_TABLE))
            {
                break;
            }
        }
        return out;
    }

    private Status tableInHand()
    {
        if (Inv.count(this.rocco.inventory(), Items.CRAFTING_TABLE) == 0)
        {
            say("craft_failed", "item", Names.name(Items.CRAFTING_TABLE));
            return Status.FAILED;
        }
        this.phase = Phase.PLACE_TABLE;
        return Status.RUNNING;
    }

    private Status placeTable()
    {
        if (!this.placedTable)
        {
            this.placedTable = true;
            delegate(new PlaceErrand(this.rocco, requester(), Items.CRAFTING_TABLE, null));
            return Status.RUNNING;
        }

        if (this.table == null)
        {
            return Status.FAILED;
        }
        this.phase = Phase.CRAFT;
        return Status.RUNNING;
    }

    @Nullable
    private BlockPos tableNearby()
    {
        int radius = Math.min(12, RoccoCommonConfig.WORK_SEARCH_RADIUS.get());
        return Storage.findBlock(this.level, this.rocco.blockPosition(), radius, 4,
                state -> state.is(Blocks.CRAFTING_TABLE));
    }

    // ---------- craftear ----------

    private Status craft()
    {
        choose(Inv.tally(this.rocco.inventory()));
        if (!this.plan.complete())
        {
            this.phase = Phase.PLAN;
            return Status.RUNNING;
        }

        List<CraftPlanner.Step> steps = this.plan.steps();
        BlockPos where = this.table != null ? this.table : this.rocco.blockPosition();
        RoccoEntity.Job job = RoccoEntity.Job.custom(RoccoTask.INTERACTING, where, ItemStack.EMPTY, level ->
        {
            this.crafted = runSteps(steps);
            if (this.crafted)
            {
                this.rocco.showInHand(new ItemStack(this.target));
                level.playSound(null, where, net.minecraft.sounds.SoundEvents.WOOD_HIT,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
                level.playSound(null, this.rocco.blockPosition(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
                        net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F, 1.2F);
            }
        }, null).reaching(3.0D);
        work(job);
        this.phase = Phase.AFTER_CRAFT;
        return Status.RUNNING;
    }

    private boolean runSteps(List<CraftPlanner.Step> steps)
    {
        for (CraftPlanner.Step step : steps)
        {
            for (int i = 0; i < step.times(); i++)
            {
                if (!Recipes.craftOnce(this.rocco, this.level, step.recipe().id()))
                {
                    return false;
                }
            }
        }
        return true;
    }

    private Status afterCraft()
    {
        if (jobFailed())
        {
            say("craft_cant_reach_table");
            return Status.FAILED;
        }
        if (!this.crafted)
        {
            say("craft_failed", "item", Names.name(this.target));
            return Status.FAILED;
        }

        if (!this.quiet)
        {
            say("craft_done", "item", Names.name(this.target));
        }

        this.phase = Phase.END;
        if (!this.keep && RoccoCommonConfig.WORK_GIVE_RESULTS.get() && requester() != null)
        {
            Item made = this.target;
            delegate(new GiveErrand(this.rocco, requester(), stack -> stack.is(made), this.count, false));
            return Status.RUNNING;
        }
        return Status.DONE;
    }

    @Override
    public String describe()
    {
        String what = "crafteando " + Names.name(this.target == Items.AIR && !this.candidates.isEmpty()
                ? this.candidates.get(0) : this.target);
        if (this.phase == Phase.WAIT && this.plan != null && !this.plan.missing().isEmpty())
        {
            return what + " (esperando que le den: " + Names.missing(this.plan.missing()) + ")";
        }
        return what;
    }
}

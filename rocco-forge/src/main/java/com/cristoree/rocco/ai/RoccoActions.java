package com.cristoree.rocco.ai;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoFollowGoal;
import com.cristoree.rocco.entity.RoccoTask;
import com.cristoree.rocco.work.BreedErrand;
import com.cristoree.rocco.work.EnchantErrand;
import com.cristoree.rocco.work.EnchantRequest;
import com.cristoree.rocco.work.FishErrand;
import com.cristoree.rocco.work.GuideErrand;
import com.cristoree.rocco.work.SortErrand;
import com.cristoree.rocco.work.TradeErrand;
import com.cristoree.rocco.entity.RoccoWork;
import com.cristoree.rocco.work.BreakErrand;
import com.cristoree.rocco.work.ChopErrand;
import com.cristoree.rocco.work.CookErrand;
import com.cristoree.rocco.work.CraftErrand;
import com.cristoree.rocco.work.Crops;
import com.cristoree.rocco.work.DepositErrand;
import com.cristoree.rocco.work.FarmErrand;
import com.cristoree.rocco.work.FetchErrand;
import com.cristoree.rocco.work.GatherErrand;
import com.cristoree.rocco.work.GiveErrand;
import com.cristoree.rocco.work.Inv;
import com.cristoree.rocco.work.LightErrand;
import com.cristoree.rocco.work.Names;
import com.cristoree.rocco.work.PickupErrand;
import com.cristoree.rocco.work.PlaceErrand;
import com.cristoree.rocco.work.PlantErrand;
import com.cristoree.rocco.work.Recipes;
import com.cristoree.rocco.work.Storage;
import com.cristoree.rocco.work.TillErrand;
import com.cristoree.rocco.work.Tools;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Interpreta la linea "#accion: ..." que la IA puede poner al final de su respuesta (o la orden
// que entendio el modo sin IA) y la convierte en trabajos concretos. Si algo no se puede, lo
// dice Rocco con sus palabras o devuelve el motivo en una frase.
public final class RoccoActions
{
    private static final Pattern ACTION_LINE =
            Pattern.compile("(?im)^\\s*#accion\\s*:\\s*(.+?)\\s*$");
    private static final Pattern FOR_ME = Pattern.compile("\\b(mio|mia|para mi|para vos|quedatelo|quedatela|tuyo)\\b");

    public record Parsed(String cleanText, String verb, String argument) {}

    public record Outcome(boolean ok, String note) {}

    private static final Outcome OK = new Outcome(true, null);
    private static final Outcome SAID = new Outcome(false, null);

    // Las IAs no siempre escriben la orden prolija: "#acción: seguir", "**#accion: seguir**",
    // "[accion: seguir]" o pegada al final de una frase. Se aceptan todas.
    private static final Pattern LOOSE_ACTION =
            Pattern.compile("(?i)[#\\[*`]+\\s*acci[oó]n\\s*[:=]\\s*([^\\n\\]*`]+)[\\]*`]*");

    public static Parsed parse(String reply)
    {
        Matcher matcher = ACTION_LINE.matcher(reply);
        if (!matcher.find())
        {
            matcher = LOOSE_ACTION.matcher(reply);
            if (!matcher.find())
            {
                return new Parsed(reply, null, null);
            }
        }

        String command = matcher.group(1).trim();
        String clean = reply.substring(0, matcher.start()) + reply.substring(matcher.end());

        String[] pieces = command.split("\\s+", 2);
        String verb = java.text.Normalizer.normalize(pieces[0].toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z_]", "");
        String argument = pieces.length > 1 ? pieces[1].trim() : "";

        return new Parsed(clean, verb, argument);
    }

    // Verbo que existe de verdad. Si la IA invento uno, se toma como si no hubiera puesto orden.
    public static boolean knows(String verb)
    {
        return verb != null && KNOWN_VERBS.contains(verb);
    }

    // Los verbos que se le ofrecen a la IA, en el orden en que se listan en YOUR ACTIONS. Los
    // alias (sembrar, fabricar, venir...) se entienden igual pero no se ofrecen. La explicacion de
    // cada uno esta en rocco/personality/<version>/actions.txt; un verbo nuevo agregado aca
    // aparece solo en el prompt (y avisa en el log si le falta la explicacion).
    public static final List<String> OFFERED_VERBS = List.of(
            "plantar", "cosechar", "arar", "cuidar_huerta", "cofre_cosecha", "craftear", "buscar", "receta",
            "cocinar", "agarrar", "traer", "guardar", "dar", "tirar", "juntar", "poner", "iluminar", "romper",
            "talar", "conseguir", "encantar", "mate", "recordar_lugar", "olvidar_lugar", "ir_a", "criar", "pescar",
            "comprar", "vender", "ordenar", "teletransportarse", "abrir", "comer", "seguir", "esperar",
            "sentarse", "dormir", "levantarse", "parar");

    private static final java.util.Set<String> UNDOCUMENTED_WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // El bloque YOUR ACTIONS del prompt: formato, cada verbo con su explicacion y las reglas.
    public static String describeForAi()
    {
        StringBuilder out = new StringBuilder(com.cristoree.rocco.ai.personality.PersonalityTexts.actionSection("header"));
        java.util.Map<String, String> docs = com.cristoree.rocco.ai.personality.PersonalityTexts.verbs();
        for (String verb : OFFERED_VERBS)
        {
            String doc = docs.get(verb);
            if (doc == null)
            {
                if (UNDOCUMENTED_WARNED.add(verb))
                {
                    RoccoMod.LOGGER.warn("[Rocco] El verbo '{}' no tiene explicacion en actions.txt", verb);
                }
                out.append("\n- ").append(verb);
                continue;
            }
            // " | " separa el uso de la explicacion; un "|" pegado es parte del uso (<id|todo>).
            int bar = doc.indexOf(" | ");
            String usage = bar < 0 ? verb : doc.substring(0, bar).trim();
            String what = bar < 0 ? doc : doc.substring(bar + 3).trim();
            out.append("\n- ").append(usage).append(" -> ").append(what);
        }
        out.append('\n').append(com.cristoree.rocco.ai.personality.PersonalityTexts.actionSection("rules"));
        return out.toString();
    }

    private static final java.util.Set<String> KNOWN_VERBS = java.util.Set.of(
            "mate", "tomar_mate", "encantar", "recordar_lugar", "recordar", "olvidar_lugar", "olvidar", "ir_a", "llevar",
            "criar", "pescar", "comprar", "comerciar", "vender", "ordenar", "teletransportarse", "venir", "aparecer",
            "plantar", "sembrar", "cosechar", "cuidar_huerta", "cuidar", "arar", "cofre_cosecha", "craftear", "fabricar",
            "buscar", "receta", "cocinar", "agarrar", "sacar", "traer", "guardar", "dar", "tirar", "soltar", "juntar",
            "poner", "colocar", "iluminar", "romper", "talar", "conseguir", "abrir", "comer", "tragar", "seguir",
            "esperar", "quedarse", "sentarse", "dormir", "levantarse", "despertarse", "parar", "cancelar");

    public static Outcome run(RoccoEntity rocco, @Nullable ServerPlayer player, String verb, String argument)
    {
        if (!(rocco.level() instanceof ServerLevel level))
        {
            return new Outcome(false, "no estoy en el mundo");
        }

        // Una orden nueva corta el mate o el encantamiento que estuviera haciendo.
        if (!verb.equals("mate") || rocco.getActiveTask() != RoccoTask.MATE)
        {
            rocco.interruptActivity();
        }

        return switch (verb)
        {
            case "mate", "tomar_mate" -> mate(rocco);
            case "encantar" -> enchant(rocco, player, argument);
            case "recordar_lugar" -> rememberPlace(rocco, player, argument);
            case "olvidar_lugar" -> forgetPlace(rocco, argument);
            // Memoria de lo que dijo o hizo el jugador. Lo normal es que la IA use las lineas
            // "#recordar:" aparte (asi no gasta su unica #accion), pero como verbo tambien sirve.
            case "recordar" -> remember(player, argument, false);
            case "olvidar" -> forget(player, argument);
            case "ir_a", "llevar" -> guide(rocco, player, argument);
            case "criar" -> start(rocco, new BreedErrand(rocco, player, argument));
            case "pescar" -> start(rocco, new FishErrand(rocco, player, Names.parse(argument).count()));
            case "comprar", "comerciar" -> trade(rocco, player, argument, false);
            case "vender" -> trade(rocco, player, argument, true);
            case "ordenar" -> sort(rocco, player, argument);
            case "teletransportarse", "venir", "aparecer" -> teleport(rocco, player);
            case "plantar", "sembrar" -> plant(rocco, player, level, argument);
            case "cosechar" -> start(rocco, new FarmErrand(rocco, player, false, rocco.blockPosition()));
            case "cuidar_huerta", "cuidar" -> start(rocco, new FarmErrand(rocco, player, true, rocco.blockPosition()));
            case "arar" -> start(rocco, new TillErrand(rocco, player, Names.parse(argument).count()));
            case "cofre_cosecha" -> harvestChest(rocco, player);
            case "craftear", "fabricar" -> craft(rocco, player, argument);
            case "buscar" -> search(rocco, player, argument);
            case "receta" -> recipe(rocco, level, argument);
            case "cocinar" -> cook(rocco, player, argument);
            case "agarrar", "sacar" -> fetch(rocco, player, argument, false);
            case "traer" -> fetch(rocco, player, argument, true);
            case "guardar" -> store(rocco, player, argument);
            case "dar" -> give(rocco, player, argument, false);
            case "tirar", "soltar" -> give(rocco, player, argument, true);
            case "juntar" -> start(rocco, new PickupErrand(rocco, player));
            case "poner", "colocar" -> place(rocco, player, argument);
            case "iluminar" -> start(rocco, new LightErrand(rocco, player, Names.parse(argument).count()));
            case "romper" -> breakBlock(rocco, player, argument);
            case "talar" -> start(rocco, new ChopErrand(rocco, player, false));
            case "conseguir" -> gather(rocco, player, argument);
            case "abrir" -> open(rocco, level);
            case "comer", "tragar" -> eat(rocco, level, argument);
            case "seguir" -> follow(rocco, true);
            case "esperar", "quedarse" -> follow(rocco, false);
            case "sentarse" -> sit(rocco);
            case "dormir" -> sleep(rocco);
            case "levantarse", "despertarse" -> wake(rocco);
            case "parar", "cancelar" -> stop(rocco);
            default ->
            {
                RoccoMod.LOGGER.debug("[Rocco] Accion desconocida de la IA: {}", verb);
                yield new Outcome(false, null);
            }
        };
    }

    private static Outcome start(RoccoEntity rocco, com.cristoree.rocco.work.Errand errand)
    {
        rocco.startErrand(errand);
        return OK;
    }

    // Lo que pidieron, o null si no se entiende: en ese caso Rocco ya lo dijo.
    @Nullable
    private static Names.Query understood(RoccoEntity rocco, String argument)
    {
        Names.Query query = Names.parse(argument);
        if (!query.found())
        {
            rocco.sayLine("item_unknown");
            return null;
        }
        return query;
    }

    // ---------- mate, magia y lugares ----------

    private static Outcome mate(RoccoEntity rocco)
    {
        if (rocco.getActiveTask() == RoccoTask.MATE)
        {
            return OK;
        }
        rocco.cancelErrand();
        rocco.startMate();
        return OK;
    }

    private static Outcome enchant(RoccoEntity rocco, @Nullable ServerPlayer player, String argument)
    {
        // "el pico con eficiencia 5" -> item "el pico" + Eficiencia V. Sin encantamiento: al azar.
        EnchantRequest.Parsed request = EnchantRequest.parse(argument);
        String itemText = request.item();
        boolean generic = itemText.isBlank()
                || itemText.matches(".*\\b(mano|esto|eso|este|esta|lo que tengo|armadura|armor)\\b.*");
        Names.Query query = generic ? null : Names.parse(itemText);
        return start(rocco, new EnchantErrand(rocco, player, query, itemText, request.enchantment(), request.level()));
    }

    private static Outcome remember(@Nullable ServerPlayer player, String note, boolean person)
    {
        if (player != null && !note.isBlank())
        {
            RoccoMemory.remember(player, note, person);
        }
        return OK;
    }

    private static Outcome forget(@Nullable ServerPlayer player, String words)
    {
        if (player != null && !words.isBlank())
        {
            RoccoMemory.forget(player, words);
        }
        return OK;
    }

    // "Esta es mi casa": guarda donde esta parado el jugador.
    private static Outcome rememberPlace(RoccoEntity rocco, @Nullable ServerPlayer player, String argument)
    {
        String name = placeName(argument);
        if (player == null || name.isEmpty())
        {
            rocco.sayLine("place_need_name");
            return SAID;
        }
        if (!rocco.places().containsKey(name) && rocco.places().size() >= 32)
        {
            rocco.sayLine("place_full");
            return SAID;
        }
        rocco.rememberPlace(name, player.blockPosition(), player.level().dimension().location().toString());
        rocco.sayLine("place_saved", "place", name);
        return OK;
    }

    private static Outcome forgetPlace(RoccoEntity rocco, String argument)
    {
        String name = placeName(argument);
        if (rocco.places().remove(name) == null)
        {
            rocco.sayLine("place_unknown", "place", name.isEmpty() ? "ese lugar" : name);
            return SAID;
        }
        rocco.sayLine("place_forgotten", "place", name);
        return OK;
    }

    private static Outcome guide(RoccoEntity rocco, @Nullable ServerPlayer player, String argument)
    {
        String name = placeName(argument);
        RoccoEntity.Place place = rocco.places().get(name);
        if (place == null)
        {
            // Tolera "la casita" por "casa" y parecidos.
            for (var entry : rocco.places().entrySet())
            {
                if (!name.isEmpty() && (entry.getKey().contains(name) || name.contains(entry.getKey())))
                {
                    name = entry.getKey();
                    place = entry.getValue();
                    break;
                }
            }
        }
        if (place == null)
        {
            rocco.sayLine("place_unknown", "place", name.isEmpty() ? "ese lugar" : name);
            return SAID;
        }
        rocco.setFollowing(true);
        return start(rocco, new GuideErrand(rocco, player, name, place));
    }

    // "la casa", "mi casa" y "casa" son el mismo lugar.
    public static String placeName(String argument)
    {
        String name = Names.normalize(argument)
                .replaceAll("^(este lugar (como|es)|aca (es|como)|esto es|este es|esta es)\\s+", "")
                .replaceAll("^(mi|mis|la|el|los|las|nuestra|nuestro|tu|a la|al|a)\\s+", "")
                .trim();
        return name.length() > 30 ? name.substring(0, 30).trim() : name;
    }

    // ---------- animales, aldeanos, cofres ----------

    private static Outcome trade(RoccoEntity rocco, @Nullable ServerPlayer player, String argument, boolean selling)
    {
        Names.Query query = understood(rocco, argument);
        if (query == null)
        {
            return SAID;
        }
        return start(rocco, new TradeErrand(rocco, player, query, selling));
    }

    private static Outcome sort(RoccoEntity rocco, @Nullable ServerPlayer player, String argument)
    {
        String normalized = Names.normalize(argument);
        BlockPos only = null;
        if (player != null && normalized.matches(".*\\b(este|ese|esto|mirando)\\b.*"))
        {
            only = Storage.lookedAtStorage(player);
        }
        return start(rocco, new SortErrand(rocco, player, only));
    }

    // "Veni": aparece al lado tuyo y te vuelve a seguir, este donde este.
    private static Outcome teleport(RoccoEntity rocco, @Nullable ServerPlayer player)
    {
        rocco.cancelErrand();
        rocco.setFollowing(true);
        rocco.standUp();
        if (player == null)
        {
            return OK;
        }
        if (rocco.distanceTo(player) < 3.0D)
        {
            return OK;
        }
        if (!RoccoFollowGoal.teleportNear(rocco, player))
        {
            rocco.sayLine("teleport_no_spot");
            return SAID;
        }
        return OK;
    }

    // ---------- huerta ----------

    // No gasta semillas: el poder de las semillas lo lleva adentro.
    private static Outcome plant(RoccoEntity rocco, @Nullable ServerPlayer player, ServerLevel level, String argument)
    {
        Names.Query query = Names.parse(argument);
        Crops.Plantable plant = null;

        if (query.found())
        {
            for (net.minecraft.world.item.Item item : query.items())
            {
                plant = Crops.plantableFor(item);
                if (plant != null)
                {
                    break;
                }
            }
            if (plant == null)
            {
                return new Outcome(false, "eso no se planta, y mira que yo planto cualquier cosa");
            }
        }
        else
        {
            // Sin decir que: lo que mas hay en la huerta, o trigo.
            plant = Crops.mostCommon(level, rocco.blockPosition(), 8);
            if (plant == null)
            {
                plant = Crops.plantableFor(Items.WHEAT_SEEDS);
            }
        }

        int count = query.count() > 0 ? query.count() : plant.soil() == Crops.Soil.GROUND ? 1 : 32;
        return start(rocco, new PlantErrand(rocco, player, plant, count));
    }

    // "Este es el cofre de la cosecha": el que esta mirando el jugador.
    private static Outcome harvestChest(RoccoEntity rocco, @Nullable ServerPlayer player)
    {
        BlockPos chest = player == null ? null : Storage.lookedAtStorage(player);
        if (chest == null)
        {
            rocco.sayLine("harvest_chest_none");
            return SAID;
        }

        rocco.setHarvestChest(chest);
        rocco.sayLine("harvest_chest_set");

        boolean hasHarvest = Inv.count(rocco.inventory(), stack -> rocco.harvestTypes().contains(stack.getItem())) > 0;
        if (hasHarvest)
        {
            rocco.startErrand(new DepositErrand(rocco, player, DepositErrand.Mode.HARVEST, null, chest, false));
        }
        return OK;
    }

    // ---------- crafteo ----------

    private static Outcome craft(RoccoEntity rocco, @Nullable ServerPlayer player, String argument)
    {
        Names.Query query = understood(rocco, argument);
        if (query == null)
        {
            return SAID;
        }
        boolean forMe = FOR_ME.matcher(Names.normalize(argument)).find();
        return start(rocco, new CraftErrand(rocco, player, query.items(), query.count(), forMe, false));
    }

    // "Busca en el cofre": si estaba esperando materiales, va a buscarlos. Si no, es buscar algo.
    private static Outcome search(RoccoEntity rocco, @Nullable ServerPlayer player, String argument)
    {
        if (rocco.signalErrand("buscar"))
        {
            return OK;
        }
        if (!argument.isBlank() && Names.parse(argument).found())
        {
            return fetch(rocco, player, argument, false);
        }
        rocco.sayLine("search_nothing");
        return SAID;
    }

    private static Outcome recipe(RoccoEntity rocco, ServerLevel level, String argument)
    {
        Names.Query query = understood(rocco, argument);
        if (query == null)
        {
            return SAID;
        }

        for (net.minecraft.world.item.Item item : query.items())
        {
            String text = Recipes.describe(level, Inv.id(item));
            if (text != null)
            {
                rocco.sayLine("recipe", "item", Names.name(item), "recipe", text);
                return OK;
            }
        }
        rocco.sayLine("craft_unknown", "item", Names.name(query.first()));
        return SAID;
    }

    // ---------- horno ----------

    private static Outcome cook(RoccoEntity rocco, @Nullable ServerPlayer player, String argument)
    {
        Names.Query query = Names.parse(argument);
        return start(rocco, new CookErrand(rocco, player, query.found() ? query : null, query.count()));
    }

    // ---------- cofres e inventario ----------

    private static Outcome fetch(RoccoEntity rocco, @Nullable ServerPlayer player, String argument, boolean give)
    {
        Names.Query query = understood(rocco, argument);
        if (query == null)
        {
            return SAID;
        }

        Predicate<ItemStack> filter = query.filter();
        String what = Names.name(query.first());

        // Si ya lo lleva encima, para traerlo no hace falta ir a ningun cofre.
        int have = Inv.count(rocco.inventory(), filter);
        int wanted = query.count() > 0 ? query.count() : 64;
        if (give && have > 0 && (query.count() == 0 || have >= query.count()))
        {
            return start(rocco, new GiveErrand(rocco, player, filter, query.count() > 0 ? query.count() : have, false));
        }

        BlockPos looked = player == null ? null : Storage.lookedAtStorage(player);
        FetchErrand errand = FetchErrand.plan(rocco, player, filter, what, Math.max(1, wanted - (give ? have : 0)), looked, give, false);
        if (errand == null)
        {
            rocco.sayLine("fetch_none", "item", what);
            return SAID;
        }
        return start(rocco, errand);
    }

    private static Outcome store(RoccoEntity rocco, @Nullable ServerPlayer player, String argument)
    {
        String normalized = Names.normalize(argument);
        BlockPos looked = player == null ? null : Storage.lookedAtStorage(player);

        if (normalized.isBlank() || normalized.matches(".*\\b(todo|todas|todos)\\b.*"))
        {
            return start(rocco, new DepositErrand(rocco, player, DepositErrand.Mode.ALL, null, looked, false));
        }
        if (normalized.contains("cosecha"))
        {
            return start(rocco, new DepositErrand(rocco, player, DepositErrand.Mode.HARVEST, null, looked, false));
        }

        Names.Query query = understood(rocco, argument);
        if (query == null)
        {
            return SAID;
        }
        return start(rocco, new DepositErrand(rocco, player, DepositErrand.Mode.MATCHING, query.filter(), looked, false));
    }

    private static Outcome give(RoccoEntity rocco, @Nullable ServerPlayer player, String argument, boolean drop)
    {
        Names.Query query = Names.parse(argument);
        Predicate<ItemStack> filter;
        int amount;

        if (query.all() && !query.found() || Names.normalize(argument).matches("^(todo|todas|todos)?$"))
        {
            // "Todo" son sus cosas: las herramientas con las que trabaja se las queda.
            filter = stack -> !Tools.isTool(stack);
            amount = 0;
        }
        else if (query.found())
        {
            filter = query.filter();
            amount = query.all() ? 0 : query.count();
        }
        else
        {
            rocco.sayLine("item_unknown");
            return SAID;
        }

        if (Inv.count(rocco.inventory(), filter) == 0)
        {
            rocco.sayLine("give_none");
            return SAID;
        }
        return start(rocco, new GiveErrand(rocco, player, filter, amount, drop));
    }

    // ---------- bloques ----------

    private static Outcome place(RoccoEntity rocco, @Nullable ServerPlayer player, String argument)
    {
        Names.Query query = understood(rocco, argument);
        if (query == null)
        {
            return SAID;
        }

        // De las opciones, la que tenga encima (si dijo "tablas", las que haya).
        net.minecraft.world.item.Item item = query.items().stream()
                .filter(candidate -> Inv.count(rocco.inventory(), candidate) > 0)
                .findFirst()
                .orElse(query.first());

        BlockHitResult hint = player == null ? null : Storage.lookedAt(player, 6.0D);
        return start(rocco, new PlaceErrand(rocco, player, item, hint));
    }

    private static Outcome breakBlock(RoccoEntity rocco, @Nullable ServerPlayer player, String argument)
    {
        String normalized = Names.normalize(argument);
        BlockPos target;

        if (normalized.isBlank() || normalized.matches(".*\\b(mirando|eso|esto|este|ese|aca)\\b.*"))
        {
            BlockHitResult hit = player == null ? null : Storage.lookedAt(player, 8.0D);
            if (hit == null)
            {
                rocco.sayLine("break_not_found", "item", "lo que estas mirando");
                return SAID;
            }
            target = hit.getBlockPos();
        }
        else
        {
            Names.Query query = understood(rocco, argument);
            if (query == null)
            {
                return SAID;
            }
            target = BreakErrand.nearest(rocco, query);
            if (target == null)
            {
                rocco.sayLine("break_not_found", "item", Names.name(query.first()));
                return SAID;
            }
        }
        return start(rocco, new BreakErrand(rocco, player, target));
    }

    private static Outcome gather(RoccoEntity rocco, @Nullable ServerPlayer player, String argument)
    {
        Names.Query query = understood(rocco, argument);
        if (query == null)
        {
            return SAID;
        }
        int amount = query.count() > 0 ? query.count() : new ItemStack(query.first()).getMaxStackSize() > 1 ? 16 : 1;
        return start(rocco, new GatherErrand(rocco, player, query.items(), amount, false, false));
    }

    private static Outcome open(RoccoEntity rocco, ServerLevel level)
    {
        List<BlockPos> doors = RoccoWork.findOpenables(level, rocco.blockPosition(), 1);
        if (doors.isEmpty())
        {
            return new Outcome(false, "no veo ninguna puerta cerca");
        }

        rocco.cancelErrand();
        rocco.queueJob(RoccoEntity.Job.onBlock(RoccoTask.INTERACTING, doors.get(0), ItemStack.EMPTY));
        return OK;
    }

    // ---------- comer ----------

    // Comer solo pasa si se lo piden. Nunca elige por su cuenta un aldeano ni un bicho con
    // nombre puesto: para esos hace falta que el argumento los nombre explicitamente.
    private static Outcome eat(RoccoEntity rocco, ServerLevel level, String argument)
    {
        String wanted = Names.normalize(argument)
                .replaceAll("\\b(a|al|el|la|los|las|ese|esa|esos|esas|este|esta|estos|estas|un|una|de|del)\\b", " ")
                .replaceAll("\\s+", " ").trim();

        AABB area = rocco.getBoundingBox().inflate(10.0D);
        List<Entity> around = level.getEntities(rocco, area, candidate -> candidate.isAlive());

        // "Comete todos los monstruos": se los va tragando de a uno (solo los hostiles).
        if (wanted.matches(".*\\b(todos|todas|mobs?|monstruos?|bichos? malos|enemigos?|hostiles?)\\b.*"))
        {
            List<Entity> hostiles = around.stream()
                    .filter(entity -> entity instanceof net.minecraft.world.entity.monster.Enemy && edible(entity) && !isProtected(entity))
                    .sorted(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(rocco)))
                    .limit(6)
                    .toList();
            if (!hostiles.isEmpty())
            {
                rocco.cancelErrand();
                hostiles.forEach(hostile -> rocco.queueJob(RoccoEntity.Job.onEntity(RoccoTask.SWALLOWING, hostile)));
                return OK;
            }
            if (wanted.matches(".*\\b(mobs?|monstruos?|enemigos?|hostiles?)\\b.*"))
            {
                return new Outcome(false, "no hay ningun monstruo cerca para comerme");
            }
        }

        // 1) Comida propia del inventario.
        ItemStack food = findFood(rocco, wanted);
        if (!food.isEmpty())
        {
            rocco.cancelErrand();
            rocco.queueJob(RoccoEntity.Job.onBlock(RoccoTask.EATING, rocco.blockPosition(), food));
            return OK;
        }

        // 2) Algo tirado en el piso o un bicho cerca.

        Entity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity candidate : around)
        {
            if (!edible(candidate))
            {
                continue;
            }

            boolean named = isProtected(candidate);
            boolean askedForIt = !wanted.isBlank() && matches(candidate, wanted);

            // Aldeanos y mascotas con nombre: solo si lo pidieron por nombre.
            if (named && !askedForIt)
            {
                continue;
            }

            // Si nombraron algo concreto, lo demas no cuenta.
            if (!wanted.isBlank() && !askedForIt)
            {
                continue;
            }

            double distance = candidate.distanceToSqr(rocco);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = candidate;
            }
        }

        if (best == null)
        {
            boolean protectedNearby = around.stream().anyMatch(e -> edible(e) && isProtected(e));
            if (protectedNearby && wanted.isBlank())
            {
                return new Outcome(false, "no me voy a comer a alguien que tiene nombre, pedimelo claro y por su nombre");
            }
            return new Outcome(false, "no veo nada decente para comer por aca");
        }

        rocco.cancelErrand();
        rocco.queueJob(RoccoEntity.Job.onEntity(
                best instanceof ItemEntity ? RoccoTask.EATING : RoccoTask.SWALLOWING, best));
        return OK;
    }

    private static boolean edible(Entity candidate)
    {
        if (candidate instanceof Player || candidate instanceof RoccoEntity)
        {
            return false;
        }
        return candidate instanceof ItemEntity || candidate instanceof LivingEntity;
    }

    // Aldeanos, bichos con etiqueta de nombre y mascotas domesticadas: todos son de alguien.
    private static boolean isProtected(Entity candidate)
    {
        if (candidate.hasCustomName())
        {
            return true;
        }
        if (candidate instanceof Villager || candidate instanceof AbstractVillager)
        {
            return true;
        }
        return candidate instanceof TamableAnimal tamed && tamed.isTame();
    }

    // Todo sin tildes ni mayusculas: "araña", "Arana" y "spider" son el mismo bicho.
    private static boolean matches(Entity candidate, String wanted)
    {
        if (candidate.hasCustomName())
        {
            String name = Names.normalize(candidate.getCustomName().getString());
            if (!name.isEmpty() && (name.contains(wanted) || wanted.contains(name)))
            {
                return true;
            }
        }

        String typeName = Names.normalize(candidate.getType().getDescription().getString());
        if (!typeName.isEmpty() && (typeName.contains(wanted) || wanted.contains(typeName)))
        {
            return true;
        }

        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(candidate.getType());
        if (id == null)
        {
            return false;
        }
        String path = id.getPath();
        if (wanted.contains(path.replace('_', ' ')) || path.contains(wanted.replace(' ', '_')))
        {
            return true;
        }
        for (String word : wanted.split(" "))
        {
            if (path.equals(Names.entityFor(word)))
            {
                return true;
            }
        }
        return false;
    }

    private static ItemStack findFood(RoccoEntity rocco, String wanted)
    {
        for (int slot = 0; slot < rocco.inventory().getContainerSize(); slot++)
        {
            ItemStack stack = rocco.inventory().getItem(slot);
            if (stack.isEmpty() || !stack.isEdible())
            {
                continue;
            }

            if (wanted.isBlank())
            {
                return stack.copy();
            }

            String name = Names.normalize(stack.getHoverName().getString());
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());

            if (name.contains(wanted)
                    || (id != null && (wanted.contains(id.getPath()) || id.getPath().contains(wanted))))
            {
                return stack.copy();
            }
        }
        return ItemStack.EMPTY;
    }

    // ---------- moverse ----------

    private static Outcome follow(RoccoEntity rocco, boolean following)
    {
        rocco.clearAutoRest();
        rocco.setFollowing(following);

        if (following)
        {
            // Si solo estaba esperando algo (materiales, que se cocine), lo sigue esperando
            // mientras camina con vos. Si estaba en medio de un trabajo, lo deja.
            if (rocco.currentErrand() != null && !rocco.currentErrand().allowsFollowing())
            {
                rocco.cancelErrand();
            }
            // Para seguirte primero tiene que levantarse. Y viene al lado tuyo aunque estes quieto
            // (si no, con vos quieto lo dejaria pasear lejos y pareceria que no obedece).
            rocco.standUp();
            rocco.comeClose();
        }
        else
        {
            rocco.cancelErrand();
            rocco.getNavigation().stop();
        }

        return OK;
    }

    private static Outcome sit(RoccoEntity rocco)
    {
        rocco.clearAutoRest();
        rocco.cancelErrand();
        rocco.setFollowing(false);
        rocco.sitDown();
        return OK;
    }

    private static Outcome sleep(RoccoEntity rocco)
    {
        rocco.clearAutoRest();
        rocco.cancelErrand();
        rocco.setFollowing(false);
        rocco.goToSleep();
        return OK;
    }

    // "Levantate" es tambien "veni conmigo": se para, deja lo que lo frenaba y te sigue.
    private static Outcome wake(RoccoEntity rocco)
    {
        return follow(rocco, true);
    }

    private static Outcome stop(RoccoEntity rocco)
    {
        rocco.cancelErrand();
        rocco.getNavigation().stop();
        return OK;
    }

    private RoccoActions() {}
}

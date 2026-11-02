package com.cristoree.rocco.ai;

import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoWork;
import com.cristoree.rocco.work.Crops;
import com.cristoree.rocco.work.Errand;
import com.cristoree.rocco.work.Inv;
import com.cristoree.rocco.work.Names;
import com.cristoree.rocco.work.Storage;
import com.cristoree.rocco.work.Tools;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Lo que pasa en el juego ahora, como lineas "- Clave: valor" para el bloque RIGHT NOW del
// prompt (que va al final, despues de la parte fija). Tiene que correr en el hilo del servidor:
// mira entidades y cofres. RoccoBrain le suma las lineas de la relacion, la memoria y los permisos.
public final class RoccoContextBuilder
{
    public static String build(RoccoEntity rocco, Player player)
    {
        if (!(rocco.level() instanceof ServerLevel level))
        {
            return "";
        }

        Map<String, String> facts = new LinkedHashMap<>();

        if (RoccoCommonConfig.CONTEXT_WORLD.get())
        {
            facts.put("Time of day", timeOfDay(level));
            facts.put("Weather", level.isThundering() ? "thunderstorm" : level.isRaining() ? "rain" : "clear");
            facts.put("Dimension", level.dimension().location().getPath());
            facts.put("Biome", biome(level, rocco));
            facts.put("World day", String.valueOf(level.getDayTime() / 24000L + 1));
        }

        if (RoccoCommonConfig.CONTEXT_PLAYER_STATS.get())
        {
            facts.put("Player health", Math.round(player.getHealth()) + " of " + Math.round(player.getMaxHealth()));
            facts.put("Player hunger", player.getFoodData().getFoodLevel() + " of 20");
            facts.put("Player level", String.valueOf(player.experienceLevel));
            facts.put("Player height (Y)", String.valueOf(player.blockPosition().getY()));
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
            {
                int deaths = serverPlayer.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS));
                facts.put("Player deaths in this world", String.valueOf(deaths));
            }
        }

        if (RoccoCommonConfig.CONTEXT_INVENTORY.get())
        {
            ItemStack held = player.getMainHandItem();
            facts.put("Player is holding", held.isEmpty() ? "nothing" : describeHeld(held));
            facts.put("Player inventory highlights", playerHighlights(player));
        }

        if (RoccoCommonConfig.CONTEXT_NEARBY_ENTITIES.get())
        {
            String mobs = nearbyHostiles(level, rocco);
            if (!mobs.isEmpty())
            {
                facts.put("Hostile mobs nearby", mobs);
            }
        }

        // Lo que sigue decide si puede aceptar una orden de trabajo, asi que va siempre:
        // con esto la IA contesta bien de una sin tener que fallar primero.
        Errand errand = rocco.currentErrand();
        facts.put("What you are doing", errand == null ? "nothing in particular" : errand.describe());
        facts.put("Your inventory", inventoryOf(rocco));
        facts.put("Free farmland nearby", String.valueOf(freeFarmland(level, rocco)));
        facts.put("Ripe crops nearby", String.valueOf(Crops.findRipe(level, rocco.blockPosition(), 8, 64).size()));
        facts.put("Chests nearby", chestsNear(level, rocco, player));
        facts.put("Crafting table nearby", Storage.findBlock(level, rocco.blockPosition(), 12, 4,
                state -> state.is(Blocks.CRAFTING_TABLE)) != null ? "yes" : "no");
        facts.put("Furnace nearby", Storage.findBlockEntities(level, rocco.blockPosition(), 16,
                entity -> entity instanceof AbstractFurnaceBlockEntity).isEmpty() ? "no" : "yes");

        BlockPos harvestChest = rocco.getHarvestChest();
        facts.put("Harvest chest", harvestChest == null
                ? "none chosen yet"
                : harvestChest.getX() + ", " + harvestChest.getY() + ", " + harvestChest.getZ());

        String looking = lookingAt(level, player);
        if (looking != null)
        {
            facts.put("The player is looking at", looking);
        }

        long enchantLeft = rocco.enchantCooldownLeft();
        facts.put("Your enchanting power", enchantLeft <= 0
                ? "ready to use"
                : "resting, ready again in " + Math.max(1, (enchantLeft + 1199) / 1200) + " minutes");

        facts.put("Places you remember", placesOf(rocco, player));

        String animals = breedable(level, rocco);
        if (!animals.isEmpty())
        {
            facts.put("Adult animals nearby", animals);
        }
        String villagers = villagersNear(level, rocco);
        if (!villagers.isEmpty())
        {
            facts.put("Villagers nearby", villagers);
        }

        facts.put("Doors or trapdoors nearby",
                String.valueOf(RoccoWork.findOpenables(level, rocco.blockPosition(), 8).size()));
        facts.put("You are following the player", rocco.isFollowing() ? "yes" : "no, they asked you to wait");
        facts.put("Your health", Math.round(rocco.getHealth()) + " of " + Math.round(rocco.getMaxHealth())
                + (rocco.life().saturation() > 0.0F ? " (you ate recently: healing faster)" : " (you heal by yourself little by little)"));
        facts.put("You may use on your own", usesOf(rocco));
        facts.put("What you are wearing", gearOf(rocco));
        facts.put("You may attack", targetsOf(rocco));
        facts.put("Your ender chest power", rocco.life().hasEnderPocket()
                ? "yes: you keep everything if you die"
                : "no: if you die you drop everything (an ender chest would give you the power)");
        facts.put("Your posture", postureOf(rocco));

        // Si el jugador pregunta donde murio, Rocco tiene el dato a mano.
        BlockPos death = rocco.getLastDeathPos();
        if (death != null)
        {
            String place = death.getX() + ", " + death.getY() + ", " + death.getZ();
            String dimension = rocco.getLastDeathDimension().replace("minecraft:", "");
            long day = rocco.getLastDeathDay();

            facts.put("Where the player last died",
                    place + " (in " + dimension + (day >= 0 ? ", day " + day : "") + ")");
        }

        if (RoccoCommonConfig.MOOD_ENABLED.get())
        {
            facts.put("Your current mood", mood(level, player, rocco));
        }

        StringBuilder out = new StringBuilder();
        facts.forEach((key, value) -> out.append("- ").append(key).append(": ").append(value).append('\n'));
        return out.toString();
    }

    private static String describeHeld(ItemStack held)
    {
        String name = held.getHoverName().getString();
        if (held.isDamageableItem())
        {
            return name + " (" + Tools.describe(held) + ")";
        }
        return held.getCount() > 1 ? name + " x" + held.getCount() : name;
    }

    // Lo que vale la pena comentar del inventario del jugador, sin listar los 36 lugares: lo mas
    // valioso, la comida, las antorchas y si le falta algo basico.
    private static String playerHighlights(Player player)
    {
        Map<String, Integer> totals = new LinkedHashMap<>();
        int food = 0;
        int torches = 0;
        boolean bed = false;
        for (ItemStack stack : player.getInventory().items)
        {
            if (stack.isEmpty())
            {
                continue;
            }
            if (stack.isEdible())
            {
                food += stack.getCount();
            }
            if (stack.is(net.minecraft.world.item.Items.TORCH))
            {
                torches += stack.getCount();
            }
            if (stack.is(net.minecraft.tags.ItemTags.BEDS))
            {
                bed = true;
            }
            totals.merge(stack.getHoverName().getString(), stack.getCount(), Integer::sum);
        }
        if (totals.isEmpty())
        {
            return "empty";
        }

        String top = totals.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(8)
                .map(entry -> entry.getKey() + " x" + entry.getValue())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return top + "; food items: " + food + "; torches: " + torches + "; carries a bed: " + (bed ? "yes" : "no");
    }

    private static String postureOf(RoccoEntity rocco)
    {
        return switch (rocco.getRoccoPose())
        {
            case STANDING -> "standing";
            case SITTING_DOWN, SITTING -> "sitting";
            case STANDING_UP -> "getting up";
            case FALLING_ASLEEP -> "lying down to sleep";
            case SLEEPING -> "asleep";
            case WAKING_UP -> "waking up";
        };
    }

    // Con el id exacto, para que la IA pueda mandarlo tal cual en la accion. Las herramientas
    // van con sus encantamientos y la durabilidad que les queda.
    private static String inventoryOf(RoccoEntity rocco)
    {
        Map<String, Integer> totals = new LinkedHashMap<>();
        List<String> tools = new java.util.ArrayList<>();

        for (ItemStack stack : Inv.contents(rocco.inventory()))
        {
            if (stack.isDamageableItem())
            {
                tools.add(Inv.id(stack) + " = " + Tools.describe(stack));
            }
            else
            {
                totals.merge(Inv.id(stack), stack.getCount(), Integer::sum);
            }
        }

        if (totals.isEmpty() && tools.isEmpty())
        {
            return "empty";
        }

        List<String> parts = new java.util.ArrayList<>(tools);
        totals.forEach((id, count) -> parts.add(id + " x" + count));
        return String.join(", ", parts);
    }

    private static int freeFarmland(ServerLevel level, RoccoEntity rocco)
    {
        Crops.Plantable wheat = Crops.plantableFor(net.minecraft.world.item.Items.WHEAT_SEEDS);
        return wheat == null ? 0 : Crops.freeSpots(level, rocco.blockPosition(), wheat, 8, 64).size();
    }

    // La armadura, el arma y el escudo que tiene puestos, o "nothing".
    private static String gearOf(RoccoEntity rocco)
    {
        List<String> worn = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values())
        {
            ItemStack stack = rocco.gear(slot);
            if (!stack.isEmpty())
            {
                worn.add(Inv.id(stack) + (stack.isEnchanted() ? " (enchanted)" : ""));
            }
        }
        return worn.isEmpty() ? "nothing" : String.join(", ", worn);
    }

    private static String usesOf(RoccoEntity rocco)
    {
        List<String> uses = new java.util.ArrayList<>();
        if (rocco.life().mayUse(com.cristoree.rocco.entity.RoccoFood.FOOD)) uses.add("food when hurt");
        if (rocco.life().mayUse(com.cristoree.rocco.entity.RoccoFood.POTIONS)) uses.add("potions");
        if (rocco.life().mayUse(com.cristoree.rocco.entity.RoccoFood.GOLDEN)) uses.add("golden apples in trouble");
        return uses.isEmpty() ? "nothing (only if the player asks)" : String.join(", ", uses);
    }

    private static String targetsOf(RoccoEntity rocco)
    {
        int targets = rocco.attackTargets();
        List<String> who = new java.util.ArrayList<>();
        if ((targets & com.cristoree.rocco.entity.RoccoCombat.HOSTILE) != 0) who.add("hostile mobs");
        if ((targets & com.cristoree.rocco.entity.RoccoCombat.PEACEFUL) != 0) who.add("peaceful mobs");
        if ((targets & com.cristoree.rocco.entity.RoccoCombat.PLAYERS) != 0) who.add("other players");
        return who.isEmpty() ? "nobody (set in your menu)" : String.join(", ", who) + " (set in your menu; never your owner)";
    }

    // Cuantos cofres hay y que tienen, resumido: lo justo para que sepa si conviene ir a buscar.
    private static String chestsNear(ServerLevel level, RoccoEntity rocco, Player player)
    {
        List<BlockPos> chests = Storage.findAround(level, rocco.blockPosition(), player, RoccoCommonConfig.WORK_SEARCH_RADIUS.get());
        if (chests.isEmpty())
        {
            return "none";
        }

        Map<String, Integer> totals = new LinkedHashMap<>();
        for (BlockPos chest : chests)
        {
            Container container = Storage.open(level, chest, player);
            if (container != null)
            {
                Inv.addTally(totals, container);
            }
        }

        String contents = totals.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(15)
                .map(entry -> entry.getKey() + " x" + entry.getValue())
                .reduce((a, b) -> a + ", " + b)
                .orElse("empty");
        return chests.size() + " (with: " + contents + ")";
    }

    // "casa (a 120 bloques al norte), mina (en el nether)".
    private static String placesOf(RoccoEntity rocco, Player player)
    {
        if (rocco.places().isEmpty())
        {
            return "none yet";
        }
        String here = player.level().dimension().location().toString();
        List<String> parts = new java.util.ArrayList<>();
        rocco.places().forEach((name, place) -> parts.add(name + " (" + (place.dimension().equals(here)
                ? com.cristoree.rocco.work.GuideErrand.directions(player, place.pos())
                : "en " + place.dimension().replace("minecraft:", "")) + ")"));
        return String.join(", ", parts);
    }

    private static String breedable(ServerLevel level, RoccoEntity rocco)
    {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (net.minecraft.world.entity.animal.Animal animal : level.getEntitiesOfClass(
                net.minecraft.world.entity.animal.Animal.class, rocco.getBoundingBox().inflate(14.0D),
                animal -> animal.isAlive() && !animal.isBaby()))
        {
            counts.merge(String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(animal.getType())), 1, Integer::sum);
        }
        return counts.entrySet().stream().limit(8).map(entry -> entry.getKey() + " x" + entry.getValue())
                .reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static String villagersNear(ServerLevel level, RoccoEntity rocco)
    {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (net.minecraft.world.entity.npc.Villager villager : level.getEntitiesOfClass(
                net.minecraft.world.entity.npc.Villager.class, rocco.getBoundingBox().inflate(20.0D),
                villager -> villager.isAlive() && !villager.isBaby()))
        {
            counts.merge(villager.getVillagerData().getProfession().name(), 1, Integer::sum);
        }
        return counts.entrySet().stream().map(entry -> entry.getKey() + " x" + entry.getValue())
                .reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static String lookingAt(ServerLevel level, Player player)
    {
        BlockHitResult hit = Storage.lookedAt(player, 6.0D);
        if (hit == null)
        {
            return null;
        }
        BlockState state = level.getBlockState(hit.getBlockPos());
        String id = String.valueOf(ForgeRegistries.BLOCKS.getKey(state.getBlock()));
        String extra = Storage.isStorage(level, hit.getBlockPos()) ? " (it is a chest)" : "";
        return Names.name(state.getBlock().asItem()) + " (" + id + ")" + extra;
    }

    private static String timeOfDay(ServerLevel level)
    {
        long time = level.getDayTime() % 24000L;

        if (time < 5000L) return "morning";
        if (time < 11000L) return "midday";
        if (time < 13000L) return "sunset";
        if (time < 22000L) return "night";
        return "dawn";
    }

    private static String biome(ServerLevel level, RoccoEntity rocco)
    {
        ResourceLocation key = level.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getKey(level.getBiome(rocco.blockPosition()).value());

        return key == null ? "unknown" : key.getPath();
    }

    private static String nearbyHostiles(ServerLevel level, RoccoEntity rocco)
    {
        AABB area = rocco.getBoundingBox().inflate(12.0D);
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity instanceof Enemy && entity.isAlive());

        if (found.isEmpty())
        {
            return "";
        }

        return found.stream()
                .map(entity -> entity.getType().getDescription().getString())
                .distinct()
                .limit(4)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private static String mood(ServerLevel level, Player player, RoccoEntity rocco)
    {
        if (player.getHealth() <= player.getMaxHealth() * 0.3F)
        {
            return "worried, the player is badly hurt";
        }
        if (rocco.getHealth() < rocco.getMaxHealth() * 0.5F)
        {
            return "sore and a bit offended, someone has been hitting you";
        }
        if (player.blockPosition().getY() < 30)
        {
            return "a little uneasy, you are underground";
        }
        if (level.isThundering())
        {
            return "impressed by the storm";
        }
        if (!level.isDay())
        {
            return "a bit alert, it is night";
        }
        return "relaxed and in a good mood";
    }

    private RoccoContextBuilder() {}
}

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

// El bloque variable va al final del prompt, despues de la parte fija: asi la parte estable
// se puede cachear del lado de Gemini y la respuesta llega antes.
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
            facts.put("Hora", timeOfDay(level));
            facts.put("Clima", level.isThundering() ? "tormenta" : level.isRaining() ? "lluvia" : "despejado");
            facts.put("Dimension", level.dimension().location().getPath());
            facts.put("Bioma", biome(level, rocco));
        }

        if (RoccoCommonConfig.CONTEXT_PLAYER_STATS.get())
        {
            facts.put("Vida del jugador", Math.round(player.getHealth()) + " de "
                    + Math.round(player.getMaxHealth()));
            facts.put("Hambre", player.getFoodData().getFoodLevel() + " de 20");
            facts.put("Nivel", String.valueOf(player.experienceLevel));
        }

        if (RoccoCommonConfig.CONTEXT_INVENTORY.get())
        {
            ItemStack held = player.getMainHandItem();
            facts.put("En la mano", held.isEmpty() ? "nada" : held.getHoverName().getString());
        }

        if (RoccoCommonConfig.CONTEXT_NEARBY_ENTITIES.get())
        {
            String mobs = nearbyHostiles(level, rocco);
            if (!mobs.isEmpty())
            {
                facts.put("Mobs hostiles cerca", mobs);
            }
        }

        // Lo que sigue decide si puede aceptar una orden de trabajo, asi que va siempre:
        // con esto la IA contesta bien de una sin tener que fallar primero.
        Errand errand = rocco.currentErrand();
        facts.put("Lo que estas haciendo", errand == null ? "nada en particular" : errand.describe());
        facts.put("Tu inventario", inventoryOf(rocco));
        facts.put("Tierra de cultivo libre cerca", String.valueOf(freeFarmland(level, rocco)));
        facts.put("Cultivos maduros cerca",
                String.valueOf(Crops.findRipe(level, rocco.blockPosition(), 8, 64).size()));
        facts.put("Cofres cerca", chestsNear(level, rocco, player));
        facts.put("Mesa de crafteo cerca", Storage.findBlock(level, rocco.blockPosition(), 12, 4,
                state -> state.is(Blocks.CRAFTING_TABLE)) != null ? "si" : "no");
        facts.put("Horno cerca", Storage.findBlockEntities(level, rocco.blockPosition(), 16,
                entity -> entity instanceof AbstractFurnaceBlockEntity).isEmpty() ? "no" : "si");

        BlockPos harvestChest = rocco.getHarvestChest();
        facts.put("Cofre de la cosecha", harvestChest == null
                ? "ninguno elegido todavia"
                : harvestChest.getX() + ", " + harvestChest.getY() + ", " + harvestChest.getZ());

        String looking = lookingAt(level, player);
        if (looking != null)
        {
            facts.put("El jugador esta mirando", looking);
        }

        long enchantLeft = rocco.enchantCooldownLeft();
        facts.put("Tu poder de encantar", enchantLeft <= 0
                ? "listo para usar"
                : "descansando, vuelve en " + Math.max(1, (enchantLeft + 1199) / 1200) + " minutos");

        facts.put("Lugares que recordas", placesOf(rocco, player));

        String animals = breedable(level, rocco);
        if (!animals.isEmpty())
        {
            facts.put("Animales adultos cerca", animals);
        }
        String villagers = villagersNear(level, rocco);
        if (!villagers.isEmpty())
        {
            facts.put("Aldeanos cerca", villagers);
        }

        facts.put("Puertas o trampillas cerca",
                String.valueOf(RoccoWork.findOpenables(level, rocco.blockPosition(), 8).size()));
        facts.put("Lo estas siguiendo", rocco.isFollowing() ? "si" : "no, te pidio que esperes");
        facts.put("Tu postura", postureOf(rocco));

        // Si el jugador pregunta donde murio, Rocco tiene el dato a mano.
        BlockPos death = rocco.getLastDeathPos();
        if (death != null)
        {
            String place = death.getX() + ", " + death.getY() + ", " + death.getZ();
            String dimension = rocco.getLastDeathDimension().replace("minecraft:", "");
            long day = rocco.getLastDeathDay();

            facts.put("Donde murio el jugador la ultima vez",
                    place + " (en " + dimension + (day >= 0 ? ", dia " + day : "") + ")");
        }

        if (facts.isEmpty())
        {
            return "";
        }

        StringBuilder out = new StringBuilder("\n\nAHORA MISMO:\n");
        facts.forEach((key, value) -> out.append("- ").append(key).append(": ").append(value).append('\n'));

        if (RoccoCommonConfig.MOOD_ENABLED.get())
        {
            String mood = mood(level, player);
            if (mood != null)
            {
                out.append("- Tono sugerido: ").append(mood).append('\n');
            }
        }

        // Algunas IAs contestan "dale, me quedo" y se olvidan la orden: sin ella Rocco no hace nada.
        out.append("\nRECORDATORIO: si el jugador te pide que hagas algo (moverte, quedarte, sentarte, ")
                .append("levantarte, trabajar...), la ULTIMA linea de tu respuesta tiene que ser ")
                .append("\"#accion: <verbo> <argumento>\". Sin esa linea no lo haces de verdad.\n");

        return out.toString();
    }

    private static String postureOf(RoccoEntity rocco)
    {
        return switch (rocco.getRoccoPose())
        {
            case STANDING -> "de pie";
            case SITTING_DOWN, SITTING -> "sentado";
            case STANDING_UP -> "levantandote";
            case FALLING_ASLEEP -> "acostandote a dormir";
            case SLEEPING -> "dormido";
            case WAKING_UP -> "despertandote";
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
            return "vacio";
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

    // Cuantos cofres hay y que tienen, resumido: lo justo para que sepa si conviene ir a buscar.
    private static String chestsNear(ServerLevel level, RoccoEntity rocco, Player player)
    {
        List<BlockPos> chests = Storage.find(level, rocco.blockPosition(), RoccoCommonConfig.WORK_SEARCH_RADIUS.get());
        if (chests.isEmpty())
        {
            return "ninguno";
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
                .orElse("vacios");
        return chests.size() + " (con: " + contents + ")";
    }

    // "casa (a 120 bloques al norte), mina (en el nether)".
    private static String placesOf(RoccoEntity rocco, Player player)
    {
        if (rocco.places().isEmpty())
        {
            return "ninguno todavia";
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
        String extra = Storage.isStorage(level, hit.getBlockPos()) ? " (es un cofre)" : "";
        return Names.name(state.getBlock().asItem()) + " (" + id + ")" + extra;
    }

    private static String timeOfDay(ServerLevel level)
    {
        long time = level.getDayTime() % 24000L;

        if (time < 5000L) return "manana";
        if (time < 11000L) return "mediodia";
        if (time < 13000L) return "atardecer";
        if (time < 22000L) return "noche";
        return "amanecer";
    }

    private static String biome(ServerLevel level, RoccoEntity rocco)
    {
        ResourceLocation key = level.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getKey(level.getBiome(rocco.blockPosition()).value());

        return key == null ? "desconocido" : key.getPath();
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

    private static String mood(ServerLevel level, Player player)
    {
        if (player.getHealth() <= player.getMaxHealth() * 0.3F)
        {
            return "preocupado, el jugador esta muy lastimado";
        }
        if (player.blockPosition().getY() < 30)
        {
            return "algo inquieto, estan bajo tierra";
        }
        if (level.isThundering())
        {
            return "impresionado por la tormenta";
        }
        if (!level.isDay())
        {
            return "un poco alerta, es de noche";
        }
        return null;
    }

    private RoccoContextBuilder() {}
}

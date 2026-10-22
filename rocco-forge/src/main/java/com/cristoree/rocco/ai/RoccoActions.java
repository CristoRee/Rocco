package com.cristoree.rocco.ai;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import com.cristoree.rocco.entity.RoccoWork;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Interpreta la linea "#accion: ..." que Gemini puede poner al final de su respuesta y la
// convierte en trabajos concretos. Si algo no se puede, devuelve el motivo en una frase.
public final class RoccoActions
{
    private static final Pattern ACTION_LINE =
            Pattern.compile("(?im)^\\s*#accion\\s*:\\s*(.+?)\\s*$");

    private static final int MAX_JOBS = 12;

    public record Parsed(String cleanText, String verb, String argument) {}

    public record Outcome(boolean ok, String note) {}

    public static Parsed parse(String reply)
    {
        Matcher matcher = ACTION_LINE.matcher(reply);
        if (!matcher.find())
        {
            return new Parsed(reply, null, null);
        }

        String command = matcher.group(1).trim();
        String clean = reply.substring(0, matcher.start()) + reply.substring(matcher.end());

        String[] pieces = command.split("\\s+", 2);
        String verb = pieces[0].toLowerCase(Locale.ROOT);
        String argument = pieces.length > 1 ? pieces[1].trim() : "";

        return new Parsed(clean, verb, argument);
    }

    public static Outcome run(RoccoEntity rocco, String verb, String argument)
    {
        if (!(rocco.level() instanceof ServerLevel level))
        {
            return new Outcome(false, "no estoy en el mundo");
        }

        return switch (verb)
        {
            case "plantar" -> plant(rocco, level, argument);
            case "cosechar" -> harvest(rocco, level);
            case "abrir" -> open(rocco, level);
            case "comer", "tragar" -> eat(rocco, level, argument);
            case "seguir" -> follow(rocco, true);
            case "esperar", "quedarse" -> follow(rocco, false);
            case "parar", "cancelar" -> stop(rocco);
            default ->
            {
                RoccoMod.LOGGER.debug("[Rocco] Accion desconocida de la IA: {}", verb);
                yield new Outcome(false, null);
            }
        };
    }

    private static Outcome plant(RoccoEntity rocco, ServerLevel level, String argument)
    {
        ItemStack seed = resolveSeed(rocco, argument);
        if (seed.isEmpty())
        {
            return new Outcome(false, "no tengo esa semilla encima");
        }

        List<BlockPos> spots = RoccoWork.findFarmland(level, rocco.blockPosition(), MAX_JOBS);
        if (spots.isEmpty())
        {
            return new Outcome(false, "no veo tierra de cultivo lista por aca");
        }

        int available = rocco.countInInventory(seed);
        int count = Math.min(available, spots.size());

        rocco.clearJobs();
        for (int i = 0; i < count; i++)
        {
            rocco.queueJob(RoccoEntity.Job.onBlock(RoccoTask.PLANTING, spots.get(i), seed.copy()));
        }

        return new Outcome(true, null);
    }

    private static Outcome harvest(RoccoEntity rocco, ServerLevel level)
    {
        List<BlockPos> crops = RoccoWork.findRipeCrops(level, rocco.blockPosition(), MAX_JOBS);
        if (crops.isEmpty())
        {
            return new Outcome(false, "no hay nada maduro cerca");
        }

        rocco.clearJobs();
        for (BlockPos crop : crops)
        {
            rocco.queueJob(RoccoEntity.Job.onBlock(RoccoTask.HARVESTING, crop, ItemStack.EMPTY));
        }

        return new Outcome(true, null);
    }

    private static Outcome open(RoccoEntity rocco, ServerLevel level)
    {
        List<BlockPos> doors = RoccoWork.findOpenables(level, rocco.blockPosition(), 1);
        if (doors.isEmpty())
        {
            return new Outcome(false, "no veo ninguna puerta cerca");
        }

        rocco.clearJobs();
        rocco.queueJob(RoccoEntity.Job.onBlock(RoccoTask.INTERACTING, doors.get(0), ItemStack.EMPTY));
        return new Outcome(true, null);
    }

    // Comer solo pasa si se lo piden. Nunca elige por su cuenta un aldeano ni un bicho con
    // nombre puesto: para esos hace falta que el argumento los nombre explicitamente.
    private static Outcome eat(RoccoEntity rocco, ServerLevel level, String argument)
    {
        String wanted = argument.toLowerCase(Locale.ROOT).trim();

        // 1) Comida propia del inventario.
        ItemStack food = findFood(rocco, wanted);
        if (!food.isEmpty())
        {
            rocco.clearJobs();
            rocco.queueJob(RoccoEntity.Job.onBlock(RoccoTask.EATING, rocco.blockPosition(), food));
            return new Outcome(true, null);
        }

        // 2) Algo tirado en el piso o un bicho cerca.
        AABB area = rocco.getBoundingBox().inflate(10.0D);
        List<Entity> around = level.getEntities(rocco, area, candidate -> candidate.isAlive());

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
                return new Outcome(false, "no me voy a comer a alguien que tiene nombre, pedimelo claro si querés");
            }
            return new Outcome(false, "no veo nada para comer por aca");
        }

        rocco.clearJobs();
        rocco.queueJob(RoccoEntity.Job.onEntity(
                best instanceof ItemEntity ? RoccoTask.EATING : RoccoTask.SWALLOWING, best));
        return new Outcome(true, null);
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

    private static boolean matches(Entity candidate, String wanted)
    {
        if (candidate.hasCustomName()
                && candidate.getCustomName().getString().toLowerCase(Locale.ROOT).contains(wanted))
        {
            return true;
        }

        String typeName = candidate.getType().getDescription().getString().toLowerCase(Locale.ROOT);
        if (typeName.contains(wanted) || wanted.contains(typeName))
        {
            return true;
        }

        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(candidate.getType());
        return id != null && (wanted.contains(id.getPath()) || id.getPath().contains(wanted));
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

            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());

            if (name.contains(wanted)
                    || (id != null && (wanted.contains(id.getPath()) || id.getPath().contains(wanted))))
            {
                return stack.copy();
            }
        }
        return ItemStack.EMPTY;
    }

    private static Outcome follow(RoccoEntity rocco, boolean following)
    {
        rocco.setFollowing(following);
        if (!following)
        {
            rocco.clearJobs();
            rocco.getNavigation().stop();
        }
        return new Outcome(true, null);
    }

    private static Outcome stop(RoccoEntity rocco)
    {
        rocco.clearJobs();
        rocco.getNavigation().stop();
        return new Outcome(true, null);
    }

    // La IA recibe los ids reales del inventario, asi que lo normal es que mande "minecraft:potato".
    // Igual se acepta un nombre sueltoasi no falla por un detalle de formato.
    private static ItemStack resolveSeed(RoccoEntity rocco, String argument)
    {
        if (argument.isBlank())
        {
            List<ItemStack> plantable = rocco.plantableStacks();
            return plantable.isEmpty() ? ItemStack.EMPTY : plantable.get(0);
        }

        String wanted = argument.toLowerCase(Locale.ROOT).trim();
        ResourceLocation id = ResourceLocation.tryParse(wanted.contains(":") ? wanted : "minecraft:" + wanted);

        if (id != null)
        {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null)
            {
                ItemStack candidate = new ItemStack(item);
                if (RoccoWork.cropFor(candidate) != null && rocco.countInInventory(candidate) > 0)
                {
                    return candidate;
                }
            }
        }

        // Ultimo recurso: buscar por nombre visible entre lo que tiene.
        for (ItemStack stack : rocco.plantableStacks())
        {
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (name.contains(wanted) || wanted.contains(name))
            {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    private RoccoActions() {}
}

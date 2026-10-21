package com.cristoree.rocco.ai;

import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

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

        return out.toString();
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

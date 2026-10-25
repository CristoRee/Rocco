package com.cristoree.rocco.client;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

// Las animaciones traen, en cada momento justo, que particula sale y de que hueso. El renderer
// anota donde quedo cada hueso en el mundo y aca se crean las particulas ahi mismo.
public final class RoccoParticleFx
{
    private record Located(Vec3 position, long gameTime) {}

    private static final Map<RoccoEntity, Map<String, Located>> LOCATORS = new WeakHashMap<>();

    // Si el hueso no se dibujo hace poco (fuera de camara), se usa una posicion aproximada.
    private static final long STALE_TICKS = 5L;

    public static void install()
    {
        RoccoEntity.PARTICLE_HOOK = RoccoParticleFx::spawn;
    }

    static void rememberLocator(RoccoEntity rocco, String name, Vec3 position)
    {
        Level level = rocco.level();
        LOCATORS.computeIfAbsent(rocco, key -> new HashMap<>()).put(name, new Located(position, level.getGameTime()));
    }

    private static void spawn(RoccoEntity rocco, String effect, String locator)
    {
        Level level = rocco.level();
        if (!level.isClientSide || Minecraft.getInstance().options.particles().get().getId() == 2)
        {
            // "Particulas: minimas" en las opciones del jugador: se respeta.
            return;
        }

        Vec3 at = positionOf(rocco, locator);
        Vec3 forward = Vec3.directionFromRotation(0.0F, rocco.yBodyRot);
        var random = level.random;

        switch (effect)
        {
            case "rocco:bliss_flower" ->
            {
                int count = 3 + random.nextInt(3);
                for (int i = 0; i < count; i++)
                {
                    add(level, ModParticles.BLISS_FLOWER.get(), at.add(spread(random, 0.25D), random.nextDouble() * 0.1D, spread(random, 0.25D)), Vec3.ZERO);
                }
            }
            case "rocco:mate_steam" ->
            {
                int count = 2 + random.nextInt(2);
                for (int i = 0; i < count; i++)
                {
                    add(level, ModParticles.MATE_STEAM.get(), at.add(spread(random, 0.05D), 0.02D, spread(random, 0.05D)), Vec3.ZERO);
                }
            }
            case "rocco:magic_rune" ->
                    add(level, ModParticles.MAGIC_RUNE.get(), at, forward.scale(0.04D));
            case "rocco:magic_spark" ->
            {
                for (int i = 0; i < 2; i++)
                {
                    add(level, ModParticles.MAGIC_SPARK.get(), at, forward.scale(0.02D));
                }
            }
            case "rocco:magic_burst" ->
                    add(level, ModParticles.MAGIC_BURST.get(), at.add(forward.scale(0.3D)), Vec3.ZERO);
            default -> { }
        }
    }

    private static void add(Level level, SimpleParticleType type, Vec3 at, Vec3 speed)
    {
        level.addParticle(type, at.x, at.y, at.z, speed.x, speed.y, speed.z);
    }

    private static double spread(net.minecraft.util.RandomSource random, double amount)
    {
        return (random.nextDouble() - 0.5D) * 2.0D * amount;
    }

    private static Vec3 positionOf(RoccoEntity rocco, String locator)
    {
        Map<String, Located> known = LOCATORS.get(rocco);
        Located located = known == null ? null : known.get(locator);
        if (located != null && rocco.level().getGameTime() - located.gameTime() <= STALE_TICKS)
        {
            return located.position();
        }

        // Aproximado, en bloques, girado segun hacia donde mira el cuerpo (la guia trae estos valores).
        double side;
        double up;
        double ahead;
        switch (locator)
        {
            case "fx_head" -> { side = 0.0D; up = 1.05D; ahead = 0.0D; }
            case "fx_mate", "fx_spout" -> { side = 0.2D; up = 0.55D; ahead = 0.3D; }
            case "fx_magic_right" -> { side = -0.2D; up = 0.5D; ahead = 0.45D; }
            case "fx_magic_left" -> { side = 0.2D; up = 0.5D; ahead = 0.45D; }
            default -> { side = 0.0D; up = 0.6D; ahead = 0.2D; }
        }

        float yaw = rocco.yBodyRot * Mth.DEG_TO_RAD;
        double sin = Mth.sin(yaw);
        double cos = Mth.cos(yaw);
        double x = -sin * ahead + cos * side;
        double z = cos * ahead + sin * side;
        return rocco.position().add(x, up, z);
    }

    private RoccoParticleFx() {}
}

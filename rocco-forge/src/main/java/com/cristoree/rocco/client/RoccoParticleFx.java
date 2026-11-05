package com.cristoree.rocco.client;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

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
        // Y el audio: la boca con su voz de verdad, y el ritmo de su musica para cantar a tiempo.
        RoccoEntity.SPEAKING_HOOK = rocco -> com.cristoree.rocco.client.voice.VoicePlayback.isSpeaking(rocco.getId());
        RoccoEntity.BEAT_HOOK = rocco -> com.cristoree.rocco.client.music.RoccoMusicClient.bpm(rocco.getId());
    }

    static void rememberLocator(RoccoEntity rocco, String name, Vec3 position)
    {
        Level level = rocco.level();
        LOCATORS.computeIfAbsent(rocco, key -> new HashMap<>()).put(name, new Located(position, level.getGameTime()));
    }

    private static void spawn(RoccoEntity rocco, String effect, @Nullable String rawLocator)
    {
        // land_dust viene sin localizador (sale de los pies).
        String locator = rawLocator == null ? "" : rawLocator;
        Level level = rocco.level();
        if (effect == null)
        {
            return;
        }
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

            // Vuelo: "hacia atras" es contra el sentido en que va volando.
            case "rocco:leaf_sparkle" -> add(level, ModParticles.LEAF_SPARKLE.get(), at, new Vec3(spread(random, 0.01D), 0.02D, spread(random, 0.01D)));
            case "rocco:leaf_burst" ->
            {
                int count = 4 + random.nextInt(3);
                for (int i = 0; i < count; i++)
                {
                    add(level, ModParticles.LEAF_BURST.get(), at, new Vec3(spread(random, 0.12D), 0.05D + random.nextDouble() * 0.1D, spread(random, 0.12D)));
                }
            }
            case "rocco:speed_leaf" -> add(level, ModParticles.SPEED_LEAF.get(), at.add(spread(random, 0.2D), spread(random, 0.2D), spread(random, 0.2D)), heading(rocco).scale(-0.45D));
            case "rocco:wind_streak" ->
            {
                Vec3 side = sideOf(rocco).scale(random.nextBoolean() ? 0.6D : -0.6D);
                add(level, ModParticles.WIND_STREAK.get(), at.add(side).add(heading(rocco).scale(0.6D)), heading(rocco).scale(-0.5D));
            }
            case "rocco:wind_puff" -> add(level, ModParticles.WIND_PUFF.get(), at, new Vec3(spread(random, 0.03D), spread(random, 0.02D), spread(random, 0.03D)));
            case "rocco:wind_tear" ->
            {
                Vec3 out = sideOf(rocco).scale(locator.endsWith("left") ? 0.04D : -0.04D);
                add(level, ModParticles.WIND_TEAR.get(), at, heading(rocco).scale(-0.25D).add(out));
            }
            case "rocco:land_dust" ->
            {
                for (int i = 0; i < 6; i++)
                {
                    double angle = i * Mth.TWO_PI / 6.0D + random.nextDouble() * 0.5D;
                    add(level, ModParticles.LAND_DUST.get(), rocco.position().add(0.0D, 0.05D, 0.0D),
                            new Vec3(Math.cos(angle) * 0.06D, 0.01D, Math.sin(angle) * 0.06D));
                }
            }

            // Agua y barco.
            case "rocco:duck_pop" ->
            {
                for (int i = 0; i < 6; i++)
                {
                    add(level, ModParticles.DUCK_POP.get(), at, new Vec3(spread(random, 0.12D), spread(random, 0.1D), spread(random, 0.12D)));
                }
            }
            case "rocco:water_drop" ->
            {
                int count = 2 + random.nextInt(2);
                for (int i = 0; i < count; i++)
                {
                    add(level, ModParticles.WATER_DROP.get(), at, new Vec3(spread(random, 0.14D), 0.06D + random.nextDouble() * 0.06D, spread(random, 0.14D)));
                }
            }
            case "minecraft:splash" ->
            {
                for (int i = 0; i < 8; i++)
                {
                    add(level, ParticleTypes.SPLASH, at.add(spread(random, 0.4D), 0.0D, spread(random, 0.4D)), new Vec3(0.0D, 0.1D, 0.0D));
                }
            }
            case "minecraft:bubble" ->
            {
                for (int i = 0; i < 3; i++)
                {
                    add(level, ParticleTypes.BUBBLE, at.add(spread(random, 0.05D), 0.0D, spread(random, 0.05D)), new Vec3(0.0D, 0.06D, 0.0D));
                }
            }

            // Pensar, aplaudir, festejar y bailar.
            case "rocco:think_dot" -> add(level, ModParticles.THINK_DOT.get(), at, new Vec3(spread(random, 0.005D), 0.02D, spread(random, 0.005D)));
            case "rocco:clap_spark" ->
            {
                int count = 3 + random.nextInt(2);
                for (int i = 0; i < count; i++)
                {
                    add(level, ModParticles.CLAP_SPARK.get(), at, new Vec3(spread(random, 0.06D), spread(random, 0.04D), spread(random, 0.06D)));
                }
            }
            case "rocco:confetti" ->
            {
                for (int i = 0; i < 14; i++)
                {
                    add(level, ModParticles.CONFETTI.get(), at, new Vec3(spread(random, 0.12D), 0.18D + random.nextDouble() * 0.12D, spread(random, 0.12D)));
                }
            }
            case "rocco:music_note" -> add(level, ModParticles.MUSIC_NOTE.get(), at, new Vec3(0.0D, 0.03D, 0.0D));
            case "rocco:dance_sparkle" ->
            {
                for (int i = 0; i < 2; i++)
                {
                    add(level, ModParticles.DANCE_SPARKLE.get(), at.add(spread(random, 0.1D), spread(random, 0.1D), spread(random, 0.1D)), new Vec3(0.0D, 0.005D, 0.0D));
                }
            }

            // El jardincito de la tapa en modo Chill.
            // Roco Cola: espuma al destaparla, burbujitas en el pico, brillitos del "¡aaah!" y el hipo.
            case "rocco:cola_fizz" ->
            {
                int count = 2 + random.nextInt(3);
                for (int i = 0; i < count; i++)
                {
                    add(level, ModParticles.COLA_FIZZ.get(), at.add(spread(random, 0.04D), 0.0D, spread(random, 0.04D)),
                            new Vec3(spread(random, 0.005D), 0.04D, spread(random, 0.005D)));
                }
            }
            case "rocco:cola_pop" ->
            {
                int count = 6 + random.nextInt(3);
                for (int i = 0; i < count; i++)
                {
                    add(level, ModParticles.COLA_POP.get(), at, new Vec3(spread(random, 0.08D), 0.06D + random.nextDouble() * 0.05D, spread(random, 0.08D)));
                }
            }
            case "rocco:refresh_sparkle" ->
            {
                int count = 3 + random.nextInt(3);
                for (int i = 0; i < count; i++)
                {
                    Vec3 out = new Vec3(spread(random, 1.0D), spread(random, 1.0D), spread(random, 1.0D)).normalize().scale(0.04D);
                    add(level, ModParticles.REFRESH_SPARKLE.get(), at, out);
                }
            }
            case "rocco:hic_bubble" -> add(level, ModParticles.HIC_BUBBLE.get(), at, new Vec3(0.0D, 0.015D, 0.0D));
            // El destello del golpe, quieto donde quedo el arma.
            case "rocco:swing_sparkle" -> add(level, ModParticles.SWING_SPARKLE.get(), at, Vec3.ZERO);
            case "rocco:proud_sparkle" ->
            {
                int count = 4 + random.nextInt(3);
                for (int i = 0; i < count; i++)
                {
                    add(level, ModParticles.PROUD_SPARKLE.get(), at, new Vec3(spread(random, 0.05D), 0.12D + random.nextDouble() * 0.05D, spread(random, 0.05D)));
                }
            }
            // Cofre de ender: chispitas que se arremolinan, el destello y el aro de luz.
            case "rocco:ender_spark" ->
            {
                int count = 3 + random.nextInt(3);
                for (int i = 0; i < count; i++)
                {
                    double angle = random.nextDouble() * Mth.TWO_PI;
                    Vec3 around = new Vec3(Math.cos(angle) * 0.3D, spread(random, 0.15D), Math.sin(angle) * 0.3D);
                    Vec3 swirl = new Vec3(-Math.sin(angle) * 0.04D, 0.02D, Math.cos(angle) * 0.04D);
                    add(level, ModParticles.ENDER_SPARK.get(), at.add(around), swirl);
                }
            }
            case "rocco:ender_burst" -> add(level, ModParticles.ENDER_BURST.get(), at, Vec3.ZERO);
            case "rocco:ender_ring" ->
            {
                int count = 8 + random.nextInt(5);
                for (int i = 0; i < count; i++)
                {
                    double angle = Mth.TWO_PI * i / count;
                    add(level, ModParticles.ENDER_RING.get(), at, new Vec3(Math.cos(angle) * 0.12D, 0.0D, Math.sin(angle) * 0.12D));
                }
            }
            // El cofre de ender entrando a la panza: las del portal vienen de afuera hacia el punto.
            case "minecraft:portal" ->
            {
                for (int i = 0; i < 4; i++)
                {
                    add(level, ParticleTypes.PORTAL, at, new Vec3(spread(random, 0.6D), spread(random, 0.4D), spread(random, 0.6D)));
                }
            }
            // La tumba y el nacimiento: terroncitos que saltan.
            case "rocco:dirt_clod" ->
            {
                int count = 4 + random.nextInt(4);
                for (int i = 0; i < count; i++)
                {
                    add(level, ModParticles.DIRT_CLOD.get(), at, new Vec3(spread(random, 0.1D), 0.1D + random.nextDouble() * 0.05D, spread(random, 0.1D)));
                }
            }
            // Los regalos de amistad.
            case "rocco:heart_pop" ->
            {
                int count = 2 + random.nextInt(3);
                for (int i = 0; i < count; i++)
                {
                    add(level, ModParticles.HEART_POP.get(), at.add(spread(random, 0.2D), 0.0D, spread(random, 0.2D)),
                            new Vec3(spread(random, 0.01D), 0.04D, spread(random, 0.01D)));
                }
            }
            case "rocco:friend_glow" ->
            {
                int count = 3 + random.nextInt(3);
                for (int i = 0; i < count; i++)
                {
                    add(level, ModParticles.FRIEND_GLOW.get(), at.add(spread(random, 0.2D), spread(random, 0.2D), spread(random, 0.2D)),
                            new Vec3(spread(random, 0.015D), 0.02D, spread(random, 0.015D)));
                }
            }
            case "rocco:joy_tear" -> add(level, ModParticles.JOY_TEAR.get(), at, new Vec3(spread(random, 0.02D), 0.03D, spread(random, 0.02D)));
            case "rocco:seed_pop" -> add(level, ModParticles.SEED_POP.get(), at, new Vec3(spread(random, 0.04D), 0.1D, spread(random, 0.04D)));
            case "rocco:butterfly" -> add(level, ModParticles.BUTTERFLY.get(), at, Vec3.ZERO);
            case "rocco:petal" ->
            {
                int count = 3 + random.nextInt(3);
                for (int i = 0; i < count; i++)
                {
                    add(level, ModParticles.PETAL.get(), at, new Vec3(spread(random, 0.04D), 0.05D + random.nextDouble() * 0.04D, spread(random, 0.04D)));
                }
            }
            default -> { }
        }
    }

    // Para donde va: mientras vuela es hacia donde mira (con la inclinacion), si no, el frente.
    private static Vec3 heading(RoccoEntity rocco)
    {
        return Vec3.directionFromRotation(rocco.getXRot(), rocco.getYRot());
    }

    private static Vec3 sideOf(RoccoEntity rocco)
    {
        return Vec3.directionFromRotation(0.0F, rocco.getYRot() + 90.0F);
    }

    private static void add(Level level, ParticleOptions type, Vec3 at, Vec3 speed)
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
            case "fx_head", "fx_garden" -> { side = 0.0D; up = 1.05D; ahead = 0.0D; }
            case "fx_mate", "fx_spout" -> { side = 0.2D; up = 0.55D; ahead = 0.3D; }
            case "fx_magic_right" -> { side = -0.2D; up = 0.5D; ahead = 0.45D; }
            case "fx_magic_left" -> { side = 0.2D; up = 0.5D; ahead = 0.45D; }
            case "fx_wing_right" -> { side = -0.8D; up = 0.6D; ahead = 0.0D; }
            case "fx_wing_left" -> { side = 0.8D; up = 0.6D; ahead = 0.0D; }
            case "fx_tear_right" -> { side = -0.2D; up = 0.85D; ahead = 0.25D; }
            case "fx_tear_left" -> { side = 0.2D; up = 0.85D; ahead = 0.25D; }
            case "fx_snorkel" -> { side = 0.15D; up = 1.15D; ahead = 0.1D; }
            case "fx_ring", "fx_boat" -> { side = 0.0D; up = 0.3D; ahead = 0.0D; }
            case "fx_paddle_right" -> { side = -0.7D; up = 0.2D; ahead = 0.2D; }
            case "fx_paddle_left" -> { side = 0.7D; up = 0.2D; ahead = 0.2D; }
            case "fx_cola" -> { side = 0.2D; up = 0.75D; ahead = 0.25D; }
            case "fx_mic" -> { side = -0.2D; up = 0.7D; ahead = 0.25D; }
            case "fx_belly" -> { side = 0.0D; up = 0.35D; ahead = 0.2D; }
            case "fx_body" -> { side = 0.0D; up = 0.45D; ahead = 0.0D; }
            case "fx_held" -> { side = 0.0D; up = 0.5D; ahead = 0.3D; }
            case "fx_backpack" -> { side = 0.0D; up = 0.55D; ahead = -0.35D; }
            case "fx_grave", "fx_soil" -> { side = 0.0D; up = 0.1D; ahead = 0.0D; }
            case "item_right" -> { side = -0.35D; up = 0.45D; ahead = 0.35D; }
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

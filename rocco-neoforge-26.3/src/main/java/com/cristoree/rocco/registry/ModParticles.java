package com.cristoree.rocco.registry;

import com.cristoree.rocco.RoccoMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

// Las particulas de Rocco. Las disparan las propias animaciones (cada una trae sus momentos como
// "particle_effects"), del lado del cliente.
public final class ModParticles
{
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, RoccoMod.MODID);

    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> BLISS_FLOWER = register("bliss_flower");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> MATE_STEAM = register("mate_steam");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> MAGIC_RUNE = register("magic_rune");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> MAGIC_SPARK = register("magic_spark");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> MAGIC_BURST = register("magic_burst");

    // Vuelo, agua y barco.
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> LEAF_SPARKLE = register("leaf_sparkle");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> LEAF_BURST = register("leaf_burst");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> SPEED_LEAF = register("speed_leaf");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> WIND_STREAK = register("wind_streak");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> WIND_PUFF = register("wind_puff");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> WIND_TEAR = register("wind_tear");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> LAND_DUST = register("land_dust");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> DUCK_POP = register("duck_pop");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> WATER_DROP = register("water_drop");

    // Pensar, aplaudir, festejar y bailar.
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> THINK_DOT = register("think_dot");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> CLAP_SPARK = register("clap_spark");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> CONFETTI = register("confetti");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> MUSIC_NOTE = register("music_note");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> DANCE_SPARKLE = register("dance_sparkle");

    // El jardincito de la tapa en modo Chill.
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> BUTTERFLY = register("butterfly");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> PETAL = register("petal");

    // Roco Cola, combate, equipo, cofre de ender y tumba.
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> COLA_FIZZ = register("cola_fizz");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> COLA_POP = register("cola_pop");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> REFRESH_SPARKLE = register("refresh_sparkle");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> HIC_BUBBLE = register("hic_bubble");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> SWING_SPARKLE = register("swing_sparkle");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> PROUD_SPARKLE = register("proud_sparkle");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> ENDER_SPARK = register("ender_spark");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> ENDER_BURST = register("ender_burst");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> ENDER_RING = register("ender_ring");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> DIRT_CLOD = register("dirt_clod");

    // El eructo de la Roco Cola y los regalos de amistad.
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> BURP_WIND = register("burp_wind");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> BURP_LEAF = register("burp_leaf");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> BURP_PUFF = register("burp_puff");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> FRIEND_GLOW = register("friend_glow");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> HEART_POP = register("heart_pop");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> JOY_TEAR = register("joy_tear");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> SEED_POP = register("seed_pop");

    // La parrilla, la olla de las tortas fritas y el vidrio de la botella (v14).
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> GRILL_SMOKE = register("grill_smoke");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> HEAT_WAVE = register("heat_wave");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> EMBER = register("ember");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> FAT_SIZZLE = register("fat_sizzle");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> FLAME_BURST = register("flame_burst");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> OIL_BUBBLE = register("oil_bubble");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> OIL_SPLASH = register("oil_splash");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> OIL_DRIP = register("oil_drip");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> FLOUR_PUFF = register("flour_puff");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> GLASS_SHARD = register("glass_shard");

    public static void register(IEventBus modEventBus)
    {
        PARTICLES.register(modEventBus);
    }

    private static DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> register(String name)
    {
        return PARTICLES.register(name, () -> new SimpleParticleType(false));
    }

    private ModParticles() {}
}

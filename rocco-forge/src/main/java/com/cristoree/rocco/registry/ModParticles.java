package com.cristoree.rocco.registry;

import com.cristoree.rocco.RoccoMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// Las particulas de Rocco. Las disparan las propias animaciones (cada una trae sus momentos como
// "particle_effects"), del lado del cliente.
public final class ModParticles
{
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, RoccoMod.MODID);

    public static final RegistryObject<SimpleParticleType> BLISS_FLOWER = register("bliss_flower");
    public static final RegistryObject<SimpleParticleType> MATE_STEAM = register("mate_steam");
    public static final RegistryObject<SimpleParticleType> MAGIC_RUNE = register("magic_rune");
    public static final RegistryObject<SimpleParticleType> MAGIC_SPARK = register("magic_spark");
    public static final RegistryObject<SimpleParticleType> MAGIC_BURST = register("magic_burst");

    // Vuelo, agua y barco.
    public static final RegistryObject<SimpleParticleType> LEAF_SPARKLE = register("leaf_sparkle");
    public static final RegistryObject<SimpleParticleType> LEAF_BURST = register("leaf_burst");
    public static final RegistryObject<SimpleParticleType> SPEED_LEAF = register("speed_leaf");
    public static final RegistryObject<SimpleParticleType> WIND_STREAK = register("wind_streak");
    public static final RegistryObject<SimpleParticleType> WIND_PUFF = register("wind_puff");
    public static final RegistryObject<SimpleParticleType> WIND_TEAR = register("wind_tear");
    public static final RegistryObject<SimpleParticleType> LAND_DUST = register("land_dust");
    public static final RegistryObject<SimpleParticleType> DUCK_POP = register("duck_pop");
    public static final RegistryObject<SimpleParticleType> WATER_DROP = register("water_drop");

    // Pensar, aplaudir, festejar y bailar.
    public static final RegistryObject<SimpleParticleType> THINK_DOT = register("think_dot");
    public static final RegistryObject<SimpleParticleType> CLAP_SPARK = register("clap_spark");
    public static final RegistryObject<SimpleParticleType> CONFETTI = register("confetti");
    public static final RegistryObject<SimpleParticleType> MUSIC_NOTE = register("music_note");
    public static final RegistryObject<SimpleParticleType> DANCE_SPARKLE = register("dance_sparkle");

    // El jardincito de la tapa en modo Chill.
    public static final RegistryObject<SimpleParticleType> BUTTERFLY = register("butterfly");
    public static final RegistryObject<SimpleParticleType> PETAL = register("petal");

    public static void register(IEventBus modEventBus)
    {
        PARTICLES.register(modEventBus);
    }

    private static RegistryObject<SimpleParticleType> register(String name)
    {
        return PARTICLES.register(name, () -> new SimpleParticleType(false));
    }

    private ModParticles() {}
}

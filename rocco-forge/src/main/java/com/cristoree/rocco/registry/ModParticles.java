package com.cristoree.rocco.registry;

import com.cristoree.rocco.RoccoMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// Las particulas del mate y del encantamiento. Las disparan las propias animaciones (cada una
// trae sus momentos como "particle_effects"), del lado del cliente.
public final class ModParticles
{
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, RoccoMod.MODID);

    public static final RegistryObject<SimpleParticleType> BLISS_FLOWER = register("bliss_flower");
    public static final RegistryObject<SimpleParticleType> MATE_STEAM = register("mate_steam");
    public static final RegistryObject<SimpleParticleType> MAGIC_RUNE = register("magic_rune");
    public static final RegistryObject<SimpleParticleType> MAGIC_SPARK = register("magic_spark");
    public static final RegistryObject<SimpleParticleType> MAGIC_BURST = register("magic_burst");

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

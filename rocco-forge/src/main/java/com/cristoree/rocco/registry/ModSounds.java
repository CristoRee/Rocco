package com.cristoree.rocco.registry;

import com.cristoree.rocco.RoccoMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// Un SoundEvent por evento de sounds.json. Cada uno tiene varias versiones y el juego elige
// una al azar, asi que aca no hay que hacer nada para eso.
public final class ModSounds
{
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, RoccoMod.MODID);

    public static final RegistryObject<SoundEvent> LID_OPEN = register("lid_open");
    public static final RegistryObject<SoundEvent> LID_CLOSE = register("lid_close");
    public static final RegistryObject<SoundEvent> GOO = register("goo");
    public static final RegistryObject<SoundEvent> STEP = register("step");
    public static final RegistryObject<SoundEvent> CHEW = register("chew");
    public static final RegistryObject<SoundEvent> GULP = register("gulp");
    public static final RegistryObject<SoundEvent> GULP_BIG = register("gulp_big");
    public static final RegistryObject<SoundEvent> DIRT = register("dirt");
    public static final RegistryObject<SoundEvent> HARVEST_POP = register("harvest_pop");
    public static final RegistryObject<SoundEvent> HURT = register("hurt");
    public static final RegistryObject<SoundEvent> LEAVES = register("leaves");
    public static final RegistryObject<SoundEvent> AWAKEN_POP = register("awaken_pop");

    public static final RegistryObject<SoundEvent> SNORE = register("snore");
    public static final RegistryObject<SoundEvent> BUBBLE_POP = register("bubble_pop");
    public static final RegistryObject<SoundEvent> SIT_PLOP = register("sit_plop");

    // Mate y encantar.
    public static final RegistryObject<SoundEvent> MATE_POUR = register("mate_pour");
    public static final RegistryObject<SoundEvent> MATE_SIP = register("mate_sip");
    public static final RegistryObject<SoundEvent> MATE_SLURP = register("mate_slurp");
    public static final RegistryObject<SoundEvent> MAGIC_HUM = register("magic_hum");
    public static final RegistryObject<SoundEvent> MAGIC_BURST = register("magic_burst");
    public static final RegistryObject<SoundEvent> MATE_SIGH = register("mate_sigh");

    // Vuelo, agua y barco.
    public static final RegistryObject<SoundEvent> LEAF_FWIP = register("leaf_fwip");
    public static final RegistryObject<SoundEvent> LEAF_GROW = register("leaf_grow");
    public static final RegistryObject<SoundEvent> LEAF_FLAP = register("leaf_flap");
    public static final RegistryObject<SoundEvent> DUCK_SQUEAK = register("duck_squeak");
    public static final RegistryObject<SoundEvent> DUCK_POP = register("duck_pop");

    // Pensar, aplaudir, festejar y bailar.
    public static final RegistryObject<SoundEvent> THINK = register("think");
    public static final RegistryObject<SoundEvent> CLAP = register("clap");
    public static final RegistryObject<SoundEvent> GIGGLE = register("giggle");
    public static final RegistryObject<SoundEvent> CHEER = register("cheer");
    public static final RegistryObject<SoundEvent> HUM = register("hum");

    // La voz de Fish Audio: el audio llega por red y se reproduce con este evento como canal
    // (categoria "Voz/Habla" del juego). El archivo del json nunca suena: se reemplaza al vuelo.
    public static final RegistryObject<SoundEvent> VOICE = register("voice");

    public static void register(IEventBus modEventBus)
    {
        SOUNDS.register(modEventBus);
    }

    private static RegistryObject<SoundEvent> register(String name)
    {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(RoccoMod.MODID, name)));
    }

    private ModSounds() {}
}

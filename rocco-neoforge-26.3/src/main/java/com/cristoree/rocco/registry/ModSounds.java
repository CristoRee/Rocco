package com.cristoree.rocco.registry;

import com.cristoree.rocco.RoccoMod;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

// Un SoundEvent por evento de sounds.json. Cada uno tiene varias versiones y el juego elige
// una al azar, asi que aca no hay que hacer nada para eso.
public final class ModSounds
{
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, RoccoMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> LID_OPEN = register("lid_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> LID_CLOSE = register("lid_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOO = register("goo");
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP = register("step");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHEW = register("chew");
    public static final DeferredHolder<SoundEvent, SoundEvent> GULP = register("gulp");
    public static final DeferredHolder<SoundEvent, SoundEvent> GULP_BIG = register("gulp_big");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIRT = register("dirt");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARVEST_POP = register("harvest_pop");
    public static final DeferredHolder<SoundEvent, SoundEvent> HURT = register("hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEAVES = register("leaves");
    public static final DeferredHolder<SoundEvent, SoundEvent> AWAKEN_POP = register("awaken_pop");

    public static final DeferredHolder<SoundEvent, SoundEvent> SNORE = register("snore");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUBBLE_POP = register("bubble_pop");
    public static final DeferredHolder<SoundEvent, SoundEvent> SIT_PLOP = register("sit_plop");

    // Mate y encantar.
    public static final DeferredHolder<SoundEvent, SoundEvent> MATE_POUR = register("mate_pour");
    public static final DeferredHolder<SoundEvent, SoundEvent> MATE_SIP = register("mate_sip");
    public static final DeferredHolder<SoundEvent, SoundEvent> MATE_SLURP = register("mate_slurp");
    public static final DeferredHolder<SoundEvent, SoundEvent> MAGIC_HUM = register("magic_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> MAGIC_BURST = register("magic_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> MATE_SIGH = register("mate_sigh");
    // Tus sorbos del mate que te pasa, en orden.
    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_SIP_1 = register("player_sip1");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_SIP_2 = register("player_sip2");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_SIP_3 = register("player_sip3");

    // Vuelo, agua y barco.
    public static final DeferredHolder<SoundEvent, SoundEvent> LEAF_FWIP = register("leaf_fwip");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEAF_GROW = register("leaf_grow");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEAF_FLAP = register("leaf_flap");
    public static final DeferredHolder<SoundEvent, SoundEvent> DUCK_SQUEAK = register("duck_squeak");
    public static final DeferredHolder<SoundEvent, SoundEvent> DUCK_POP = register("duck_pop");

    // Pensar, aplaudir, festejar y bailar.
    public static final DeferredHolder<SoundEvent, SoundEvent> THINK = register("think");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLAP = register("clap");
    public static final DeferredHolder<SoundEvent, SoundEvent> GIGGLE = register("giggle");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHEER = register("cheer");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUM = register("hum");

    // La Roco Cola.
    public static final DeferredHolder<SoundEvent, SoundEvent> COLA_CLINK = register("cola_clink");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLA_OPEN = register("cola_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLA_CAP = register("cola_cap");
    // Un trago y un "aaah" por cada vez que se lleva la botella a la boca (cada uno mas grande).
    public static final DeferredHolder<SoundEvent, SoundEvent> COLA_DRINK_1 = register("cola_drink_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLA_DRINK_2 = register("cola_drink_2");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLA_DRINK_3 = register("cola_drink_3");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLA_AHH_1 = register("cola_ahh_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLA_AHH_2 = register("cola_ahh_2");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLA_AHH_3 = register("cola_ahh_3");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLA_HIC = register("cola_hic");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLA_FIZZ = register("cola_fizz");

    // Comer y tomar pociones usan los sonidos del juego (ROCCO-V12).

    // El poder del cofre de ender.
    public static final DeferredHolder<SoundEvent, SoundEvent> ENDER_ABSORB = register("ender_absorb");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENDER_AWAKEN = register("ender_awaken");

    // La tumba y el nacimiento.
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAVE_TWITCH = register("grave_twitch");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAVE_BURST = register("grave_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> CROSS_FALL = register("cross_fall");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHAKE_OFF = register("shake_off");
    public static final DeferredHolder<SoundEvent, SoundEvent> DUST_PAT = register("dust_pat");

    // La voz de Fish Audio: el audio llega por red y se reproduce con este evento como canal
    // (categoria "Voz/Habla" del juego). El archivo del json nunca suena: se reemplaza al vuelo.
    public static final DeferredHolder<SoundEvent, SoundEvent> VOICE = register("voice");
    // La musica de un link: igual que la voz, el audio lo pone el mod (categoria "Discos").
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC = register("music");

    // v13: el eructo, la cola refrescante, las mochilas y los regalos.
    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_BURP = register("player_burp");
    public static final DeferredHolder<SoundEvent, SoundEvent> BURP_GUST = register("burp_gust");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLA_REFRESH = register("cola_refresh");
    public static final DeferredHolder<SoundEvent, SoundEvent> BACKPACK_EQUIP = register("backpack_equip");
    public static final DeferredHolder<SoundEvent, SoundEvent> BACKPACK_OPEN = register("backpack_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> BACKPACK_CLOSE = register("backpack_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> GIFT_SEED = register("gift_seed");
    public static final DeferredHolder<SoundEvent, SoundEvent> GIFT_FLOWER = register("gift_flower");
    // Las cancioncitas del baile pedido: un evento con tres, el juego elige una cada vez.
    public static final DeferredHolder<SoundEvent, SoundEvent> DANCE_MUSIC = register("dance_music");

    // La v14: la parrilla, las tortas fritas y el truco. Las animaciones los piden por su nombre
    // (sound_effects); los dos ultimos suenan en bucle mientras cocina.
    public static final DeferredHolder<SoundEvent, SoundEvent> TONGS_CLACK = register("tongs_clack");
    public static final DeferredHolder<SoundEvent, SoundEvent> ASADO_PLACE = register("asado_place");
    public static final DeferredHolder<SoundEvent, SoundEvent> ASADO_FLIP = register("asado_flip");
    public static final DeferredHolder<SoundEvent, SoundEvent> ASADO_PLATE = register("asado_plate");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRILL_LID_OPEN = register("grill_lid_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRILL_LID_CLOSE = register("grill_lid_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_WHOOSH = register("fire_whoosh");
    public static final DeferredHolder<SoundEvent, SoundEvent> APRON_ON = register("apron_on");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOUGH_SQUISH = register("dough_squish");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOUGH_SLAP = register("dough_slap");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOUR_PAT = register("flour_pat");
    public static final DeferredHolder<SoundEvent, SoundEvent> OIL_DROP = register("oil_drop");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKIMMER_CLINK = register("skimmer_clink");
    public static final DeferredHolder<SoundEvent, SoundEvent> TORTA_BOWL = register("torta_bowl");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLOW_OUT = register("blow_out");
    public static final DeferredHolder<SoundEvent, SoundEvent> TABLE_SLAM = register("table_slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> TABLE_KNOCK = register("table_knock");
    public static final DeferredHolder<SoundEvent, SoundEvent> CARD_TAP = register("card_tap");
    public static final DeferredHolder<SoundEvent, SoundEvent> CARD_PICK = register("card_pick");
    public static final DeferredHolder<SoundEvent, SoundEvent> CARD_SLAP = register("card_slap");
    public static final DeferredHolder<SoundEvent, SoundEvent> CARD_SWEEP = register("card_sweep");
    public static final DeferredHolder<SoundEvent, SoundEvent> CARD_RIFFLE = register("card_riffle");
    public static final DeferredHolder<SoundEvent, SoundEvent> CARD_FLICK = register("card_flick");
    public static final DeferredHolder<SoundEvent, SoundEvent> ASADO_SIZZLE_LOOP = register("asado_sizzle_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> OIL_FRY_LOOP = register("oil_fry_loop");

    public static void register(IEventBus modEventBus)
    {
        SOUNDS.register(modEventBus);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name)
    {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(RoccoMod.MODID, name)));
    }

    private ModSounds() {}
}

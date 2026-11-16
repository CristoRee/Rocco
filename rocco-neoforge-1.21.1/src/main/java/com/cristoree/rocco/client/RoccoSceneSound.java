package com.cristoree.rocco.client;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoScene;
import com.cristoree.rocco.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;

import java.util.HashMap;
import java.util.Map;

// La carne chisporroteando en la parrilla y el aceite hirviendo: un bucle que sigue a Rocco mientras
// cocina (en los ciclos, no al sacar ni al guardar las cosas) y se apaga con un fundido corto.
public final class RoccoSceneSound extends AbstractTickableSoundInstance
{
    private static final int FADE_TICKS = 10;
    private static final Map<Integer, RoccoSceneSound> PLAYING = new HashMap<>();

    private final RoccoEntity rocco;
    private final boolean grill;
    private final float full;
    private int fade;
    private boolean ending;

    // Cada vez que cambia la escena de un Rocco: arranca el bucle que corresponde (si no estaba).
    public static void update(RoccoEntity rocco)
    {
        Boolean grill = loopFor(rocco.getScene());
        if (grill == null)
        {
            return;
        }
        RoccoSceneSound current = PLAYING.get(rocco.getId());
        if (current != null && current.rocco == rocco && current.grill == grill && !current.ending
                && Minecraft.getInstance().getSoundManager().isActive(current))
        {
            return;
        }
        RoccoSceneSound loop = new RoccoSceneSound(rocco, grill);
        PLAYING.put(rocco.getId(), loop);
        Minecraft.getInstance().getSoundManager().play(loop);
    }

    // true = parrilla, false = aceite, null = nada.
    private static Boolean loopFor(RoccoScene scene)
    {
        return switch (scene)
        {
            case ASADO_A1, ASADO_A2, ASADO_A3, ASADO_A4, ASADO_B1, ASADO_B2, ASADO_B3, ASADO_B4 -> Boolean.TRUE;
            case TORTAS_A, TORTAS_B -> Boolean.FALSE;
            default -> null;
        };
    }

    private RoccoSceneSound(RoccoEntity rocco, boolean grill)
    {
        super(sound(grill), rocco.getSoundSource(), SoundInstance.createUnseededRandom());
        this.rocco = rocco;
        this.grill = grill;
        this.full = grill ? 0.7F : 0.55F;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01F;
        this.pitch = 1.0F;
        this.attenuation = Attenuation.LINEAR;
        follow();
    }

    private static SoundEvent sound(boolean grill)
    {
        return grill ? ModSounds.ASADO_SIZZLE_LOOP.get() : ModSounds.OIL_FRY_LOOP.get();
    }

    private void follow()
    {
        this.x = this.rocco.getX();
        this.y = this.rocco.getY() + 0.4D;
        this.z = this.rocco.getZ();
    }

    @Override
    public void tick()
    {
        if (this.rocco.isRemoved())
        {
            stop();
            PLAYING.remove(this.rocco.getId(), this);
            return;
        }
        follow();
        Boolean wanted = loopFor(this.rocco.getScene());
        this.ending = wanted == null || wanted != this.grill;
        this.fade = Math.max(0, Math.min(FADE_TICKS, this.fade + (this.ending ? -1 : 1)));
        this.volume = Math.max(0.01F, this.full * this.fade / FADE_TICKS);
        if (this.ending && this.fade == 0)
        {
            stop();
            PLAYING.remove(this.rocco.getId(), this);
        }
    }
}

package com.cristoree.rocco.client.music;

import com.cristoree.rocco.config.RoccoClientConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;

// La cancioncita del baile pedido: sigue a Rocco, se repite mientras baile y se apaga con un fundido
// corto. El juego elige una de las tres al arrancar. Suena con el volumen de "Discos", como su
// musica. La nota del tono no se toca: todas van a 120 bpm, igual que el baile.
public final class RoccoDanceMusic extends AbstractTickableSoundInstance
{
    private static final float VOLUME = 0.8F;
    private static final int FADE_TICKS = 8;
    private static final Map<Integer, RoccoDanceMusic> PLAYING = new HashMap<>();

    private final RoccoEntity rocco;
    private int fade = -1;

    public static void start(RoccoEntity rocco)
    {
        if (!RoccoClientConfig.DANCE_SONG.get())
        {
            return;
        }
        RoccoDanceMusic current = PLAYING.get(rocco.getId());
        // Si saliste del mundo con la cancion sonando, la vieja ya no existe aunque siga anotada.
        if (current != null && current.rocco == rocco && current.fade < 0
                && Minecraft.getInstance().getSoundManager().isActive(current))
        {
            return;
        }
        RoccoDanceMusic song = new RoccoDanceMusic(rocco);
        PLAYING.put(rocco.getId(), song);
        Minecraft.getInstance().getSoundManager().play(song);
    }

    private RoccoDanceMusic(RoccoEntity rocco)
    {
        super(ModSounds.DANCE_MUSIC.get(), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.rocco = rocco;
        this.looping = true;
        this.delay = 0;
        this.volume = VOLUME;
        this.pitch = 1.0F;
        this.attenuation = Attenuation.LINEAR;
        follow();
    }

    private void follow()
    {
        this.x = this.rocco.getX();
        this.y = this.rocco.getY() + 0.5D;
        this.z = this.rocco.getZ();
    }

    @Override
    public void tick()
    {
        if (this.rocco.isRemoved() || !RoccoClientConfig.DANCE_SONG.get())
        {
            finish();
            return;
        }
        follow();
        if (this.fade < 0 && !this.rocco.danceSongOn())
        {
            this.fade = FADE_TICKS;
        }
        if (this.fade >= 0)
        {
            this.volume = VOLUME * this.fade / FADE_TICKS;
            if (this.fade-- == 0)
            {
                finish();
            }
        }
    }

    private void finish()
    {
        stop();
        PLAYING.remove(this.rocco.getId(), this);
    }
}

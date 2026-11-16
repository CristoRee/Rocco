package com.cristoree.rocco.client.music;

import com.cristoree.rocco.RoccoMod;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

// Decodifica la pista en su propio hilo y deja listos unos segundos de audio mono (48 kHz, 16 bits),
// cada pedazo con el momento de la cancion al que corresponde. Si el que escucha va mas lento (o el
// juego esta en pausa), espera: nunca adelanta mas de lo que entra en la cola.
final class MusicDecoder implements AutoCloseable
{
    // Un pedazo de 20 ms: desde que milisegundo de la cancion arranca y sus muestras.
    record Chunk(long startMs, short[] samples)
    {
    }

    // Unos 12 segundos.
    private static final int QUEUE_FRAMES = 600;

    private final AudioPlayer player;
    private final LinkedBlockingDeque<Chunk> queue = new LinkedBlockingDeque<>(QUEUE_FRAMES);
    private final BeatTracker beats = new BeatTracker();
    private final Thread feeder;
    private volatile boolean closed;
    private volatile boolean finished;
    @Nullable
    private volatile String error;
    private volatile boolean gotAudio;

    MusicDecoder(AudioTrack track, long startMs)
    {
        this.player = MusicEngine.manager().createPlayer();
        this.player.addListener(new AudioEventAdapter()
        {
            @Override
            public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception)
            {
                MusicDecoder.this.error = MusicEngine.describe(exception);
            }
        });
        if (startMs > 0 && track.isSeekable())
        {
            track.setPosition(startMs);
        }
        this.player.playTrack(track);
        this.feeder = new Thread(this::run, "Rocco-Musica");
        this.feeder.setDaemon(true);
        this.feeder.start();
    }

    private void run()
    {
        try
        {
            while (!this.closed)
            {
                AudioFrame frame = this.player.provide(100, TimeUnit.MILLISECONDS);
                if (frame == null)
                {
                    if (this.player.getPlayingTrack() == null)
                    {
                        break;
                    }
                    continue;
                }
                short[] mono = toMono(frame.getData());
                this.beats.feed(mono);
                this.gotAudio = true;
                this.queue.putLast(new Chunk(frame.getTimecode(), mono));
            }
        }
        catch (InterruptedException ignored)
        {
            Thread.currentThread().interrupt();
        }
        catch (Exception e)
        {
            this.error = e.toString();
            RoccoMod.LOGGER.warn("[Rocco] La musica se corto: {}", e.toString());
        }
        finally
        {
            this.finished = true;
        }
    }

    // Estereo de 16 bits little-endian -> mono (el promedio de los dos canales).
    private static short[] toMono(byte[] data)
    {
        short[] mono = new short[data.length / 4];
        for (int i = 0, j = 0; j < mono.length; i += 4, j++)
        {
            int left = (short) ((data[i] & 0xFF) | (data[i + 1] << 8));
            int right = (short) ((data[i + 2] & 0xFF) | (data[i + 3] << 8));
            mono[j] = (short) ((left + right) / 2);
        }
        return mono;
    }

    @Nullable
    Chunk poll()
    {
        return this.queue.pollFirst();
    }

    // Vuelve a poner adelante lo que sobro de un pedazo.
    void pushBack(Chunk chunk)
    {
        this.queue.offerFirst(chunk);
    }

    int buffered()
    {
        return this.queue.size();
    }

    boolean hasAudio()
    {
        return this.gotAudio;
    }

    // Ya no va a llegar mas audio (termino o fallo) y no queda nada en la cola.
    boolean drained()
    {
        return this.finished && this.queue.isEmpty();
    }

    boolean finished()
    {
        return this.finished;
    }

    @Nullable
    String error()
    {
        return this.error;
    }

    double bpm()
    {
        return this.beats.bpm();
    }

    @Override
    public void close()
    {
        this.closed = true;
        this.feeder.interrupt();
        this.queue.clear();
        try
        {
            this.player.destroy();
        }
        catch (RuntimeException ignored)
        {
            // Ya estaba cerrado.
        }
    }
}

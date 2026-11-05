package com.cristoree.rocco.client.music;

import net.minecraft.client.sounds.AudioStream;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.LongSupplier;

// Lo que Minecraft le pide a la musica: pedazos de un segundo de audio mono. Va pegado al reloj del
// mundo: cada muestra que entrega es la que corresponde a ese momento de la cancion, asi todos los
// jugadores escuchan lo mismo a la vez. Si el audio todavia no llego, pone silencio y despues salta
// lo que se perdio; si sobra (empezo antes), lo descarta. Una radio en vivo no tiene "momento": ahi
// solo se reproduce lo que llega.
final class MusicStream implements AudioStream
{
    static final int RATE = 48000;
    private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false);
    private static final long SAMPLES_PER_MS = RATE / 1000;

    private final MusicDecoder decoder;
    private final LongSupplier timelineMs;
    private final boolean live;
    private boolean started;
    // La proxima muestra a entregar, contada desde el principio de la cancion.
    private long cursor;
    @Nullable
    private MusicDecoder.Chunk current;
    private int offset;

    MusicStream(MusicDecoder decoder, LongSupplier timelineMs, boolean live)
    {
        this.decoder = decoder;
        this.timelineMs = timelineMs;
        this.live = live;
    }

    @Override
    public AudioFormat getFormat()
    {
        return FORMAT;
    }

    @Override
    @Nullable
    public ByteBuffer read(int size)
    {
        int wanted = Math.max(1, size / 2);
        if (!this.started)
        {
            this.started = true;
            this.cursor = this.live ? Long.MIN_VALUE : this.timelineMs.getAsLong() * SAMPLES_PER_MS;
        }
        ByteBuffer out = BufferUtils.createByteBuffer(wanted * 2).order(ByteOrder.LITTLE_ENDIAN);
        int written = 0;
        while (written < wanted)
        {
            if (this.current == null)
            {
                this.current = this.decoder.poll();
                this.offset = 0;
                if (this.current == null)
                {
                    break;
                }
            }
            long chunkStart = this.current.startMs() * SAMPLES_PER_MS;
            if (this.cursor == Long.MIN_VALUE)
            {
                this.cursor = chunkStart;
            }
            long chunkEnd = chunkStart + this.current.samples().length;
            if (chunkEnd <= this.cursor)
            {
                // Ya paso: se descarta.
                this.current = null;
                continue;
            }
            if (chunkStart + this.offset > this.cursor)
            {
                // Todavia no le toca (la cancion arranca en un ratito): silencio hasta ahi.
                int gap = (int) Math.min(wanted - written, chunkStart + this.offset - this.cursor);
                silence(out, gap);
                written += gap;
                this.cursor += gap;
                continue;
            }
            this.offset = (int) Math.max(this.offset, this.cursor - chunkStart);
            short[] samples = this.current.samples();
            int count = Math.min(wanted - written, samples.length - this.offset);
            for (int i = 0; i < count; i++)
            {
                out.putShort(samples[this.offset + i]);
            }
            written += count;
            this.cursor += count;
            this.offset += count;
            if (this.offset >= samples.length)
            {
                this.current = null;
            }
        }

        if (written < wanted)
        {
            if (this.current == null && this.decoder.drained())
            {
                // Termino la cancion.
                if (written == 0)
                {
                    return null;
                }
                out.flip();
                return out;
            }
            // El audio viene atrasado: silencio ahora, y despues se salta lo que no se escucho.
            int missing = wanted - written;
            silence(out, missing);
            if (this.cursor != Long.MIN_VALUE)
            {
                this.cursor += missing;
            }
        }
        out.flip();
        return out;
    }

    private static void silence(ByteBuffer out, int samples)
    {
        for (int i = 0; i < samples; i++)
        {
            out.putShort((short) 0);
        }
    }

    @Override
    public void close()
    {
        // El decodificador lo cierra la sesion.
    }
}

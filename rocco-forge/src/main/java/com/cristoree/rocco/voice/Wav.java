package com.cristoree.rocco.voice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

// Lo minimo de WAV: sacar el PCM de lo que manda Fish Audio y armar uno para Deepgram.
public final class Wav
{
    // PCM de 16 bits, mono, little endian, a sampleRate.
    public record Pcm(byte[] data, int sampleRate)
    {
        public int durationTicks()
        {
            return (int) Math.ceil(this.data.length / 2.0D / this.sampleRate * 20.0D);
        }
    }

    // Lee un WAV y lo deja en 16 bits mono (si viene estereo se mezcla). null si no es un WAV
    // que se pueda leer.
    public static Pcm read(byte[] file)
    {
        if (file.length < 44 || !ascii(file, 0, "RIFF") || !ascii(file, 8, "WAVE"))
        {
            return null;
        }

        ByteBuffer buffer = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        int channels = 1;
        int sampleRate = 0;
        int bits = 16;
        int format = 1;
        int position = 12;

        while (position + 8 <= file.length)
        {
            String id = new String(file, position, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int size = buffer.getInt(position + 4);
            int body = position + 8;

            if (id.equals("fmt ") && body + 16 <= file.length)
            {
                format = buffer.getShort(body) & 0xFFFF;
                channels = buffer.getShort(body + 2) & 0xFFFF;
                sampleRate = buffer.getInt(body + 4);
                bits = buffer.getShort(body + 14) & 0xFFFF;
            }
            else if (id.equals("data"))
            {
                // Las respuestas en streaming a veces traen el tamaño en 0 o en "maximo".
                int end = size <= 0 || body + size > file.length ? file.length : body + size;
                if ((format != 1 && format != 0xFFFE) || bits != 16 || sampleRate <= 0 || channels < 1)
                {
                    return null;
                }
                byte[] data = Arrays.copyOfRange(file, body, end - ((end - body) % (2 * channels)));
                return new Pcm(channels == 1 ? data : toMono(data, channels), sampleRate);
            }

            position = body + Math.max(0, size) + (size & 1);
        }
        return null;
    }

    private static byte[] toMono(byte[] data, int channels)
    {
        ByteBuffer in = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int frames = data.length / (2 * channels);
        ByteBuffer out = ByteBuffer.allocate(frames * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int frame = 0; frame < frames; frame++)
        {
            int sum = 0;
            for (int channel = 0; channel < channels; channel++)
            {
                sum += in.getShort((frame * channels + channel) * 2);
            }
            out.putShort((short) (sum / channels));
        }
        return out.array();
    }

    // Arma un WAV con su encabezado alrededor de PCM 16 bits mono.
    public static byte[] write(byte[] pcm, int sampleRate)
    {
        ByteBuffer out = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        out.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.putInt(36 + pcm.length);
        out.put("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.put("fmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.putInt(16);
        out.putShort((short) 1);
        out.putShort((short) 1);
        out.putInt(sampleRate);
        out.putInt(sampleRate * 2);
        out.putShort((short) 2);
        out.putShort((short) 16);
        out.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.putInt(pcm.length);
        out.put(pcm);
        return out.array();
    }

    private static boolean ascii(byte[] data, int offset, String text)
    {
        for (int i = 0; i < text.length(); i++)
        {
            if (data[offset + i] != text.charAt(i))
            {
                return false;
            }
        }
        return true;
    }

    private Wav() {}
}

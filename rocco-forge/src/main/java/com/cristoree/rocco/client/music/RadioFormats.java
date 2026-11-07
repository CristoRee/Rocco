package com.cristoree.rocco.client.music;

import com.sedmelluq.discord.lavaplayer.container.MediaContainer;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerHints;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerProbe;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.container.adts.AdtsAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.playlists.HlsStreamTrack;
import com.sedmelluq.discord.lavaplayer.container.playlists.M3uPlaylistContainerProbe;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.List;

// Lo que las radios mandan y el reproductor no reconocia solo:
// - AAC suelto (ADTS): sus primeros bytes se parecen a los de un MP3 y se abria como MP3, sin sonido.
// - Listas HLS (.m3u8) con pedacitos de AAC en vez de video MPEG-TS, cada uno con una etiqueta ID3
//   adelante: es lo que usan muchas radios de Argentina.
// Todo sigue siendo abrir un link de audio: no se saca nada de ninguna pagina.
final class RadioFormats
{
    static MediaContainerRegistry containers()
    {
        List<MediaContainerProbe> probes = new ArrayList<>();
        probes.add(new AdtsFirstProbe());
        for (MediaContainerProbe probe : MediaContainer.asList())
        {
            probes.add(probe == MediaContainer.M3U.probe ? new RadioM3uProbe() : probe);
        }
        return new MediaContainerRegistry(probes);
    }

    // Se fija en los primeros bytes (despues de una etiqueta ID3, si hay): 12 bits de sincronia y
    // la "capa" en 00. Un MP3 nunca tiene la capa en 00, asi que no confunde ninguno.
    private static final class AdtsFirstProbe implements MediaContainerProbe
    {
        private final MediaContainerProbe adts = MediaContainer.ADTS.probe;

        @Override
        public String getName()
        {
            return "rocco-adts";
        }

        @Override
        public boolean matchesHints(MediaContainerHints hints)
        {
            return true;
        }

        @Override
        public MediaContainerDetectionResult probe(AudioReference reference, SeekableInputStream stream) throws IOException
        {
            long start = stream.getPosition();
            boolean adtsAhead;
            try
            {
                byte[] head = new byte[10];
                int read = stream.readNBytes(head, 0, head.length);
                if (read == 10 && head[0] == 'I' && head[1] == 'D' && head[2] == '3')
                {
                    long tag = 10L + syncsafe(head, 6) + ((head[5] & 0x10) != 0 ? 10 : 0);
                    stream.seek(start + tag);
                    read = stream.readNBytes(head, 0, 2);
                }
                adtsAhead = read >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xF6) == 0xF0;
            }
            catch (IOException e)
            {
                adtsAhead = false;
            }
            stream.seek(start);
            return adtsAhead ? this.adts.probe(reference, stream) : null;
        }

        @Override
        public AudioTrack createTrack(String parameters, AudioTrackInfo info, SeekableInputStream stream)
        {
            return this.adts.createTrack(parameters, info, stream);
        }
    }

    // El de siempre para las listas M3U, pero las HLS las abre con la pista de abajo y con la misma
    // identificacion y paciencia que los demas links.
    private static final class RadioM3uProbe extends M3uPlaylistContainerProbe
    {
        private final HttpInterfaceManager http = MusicEngine.configure(HttpClientTools.createDefaultThreadLocalManager());

        // Una lista que se anuncia como tal se mira primero: si no, el de MP3 la tomaba por un MP3
        // (".mp3.m3u", "audio/mpegurl") y sonaba el texto de la lista, o sea nada.
        @Override
        public boolean matchesHints(MediaContainerHints hints)
        {
            String mime = hints.mimeType == null ? "" : hints.mimeType.toLowerCase(java.util.Locale.ROOT);
            String extension = hints.fileExtension == null ? "" : hints.fileExtension.toLowerCase(java.util.Locale.ROOT);
            return mime.contains("mpegurl") || extension.equals("m3u") || extension.equals("m3u8");
        }

        // Una lista comun (un link y su nombre, como la de muchas radios) no es HLS aunque el de siempre
        // la tome por una: se abre el link que trae.
        @Override
        public MediaContainerDetectionResult probe(AudioReference reference, SeekableInputStream stream) throws IOException
        {
            long start = stream.getPosition();
            MediaContainerDetectionResult result = super.probe(reference, stream);
            if (result == null || result.isReference() || !result.isContainerDetected() || !result.isSupportedFile())
            {
                return result;
            }
            stream.seek(start);
            String[] lines = com.sedmelluq.discord.lavaplayer.tools.DataFormatTools.streamToLines(stream, java.nio.charset.StandardCharsets.UTF_8);
            for (String line : lines)
            {
                if (line.startsWith("#EXT-X-"))
                {
                    return result;
                }
            }
            for (String line : lines)
            {
                String link = line.trim();
                if (!link.startsWith("#") && (link.startsWith("http://") || link.startsWith("https://")))
                {
                    return MediaContainerDetectionResult.refer(this, new AudioReference(link, null));
                }
            }
            return result;
        }

        @Override
        public AudioTrack createTrack(String parameters, AudioTrackInfo info, SeekableInputStream stream)
        {
            if (parameters.equals("hls-inner") || parameters.equals("hls-outer"))
            {
                return new RadioHlsTrack(info, this.http, parameters.equals("hls-inner"));
            }
            return super.createTrack(parameters, info, stream);
        }
    }

    // Una radio HLS: si los pedacitos son MPEG-TS sigue como siempre; si son AAC suelto, los une
    // sacando las etiquetas ID3 del principio de cada uno.
    private static final class RadioHlsTrack extends HlsStreamTrack
    {
        private final HttpInterfaceManager http;
        private final boolean inner;

        RadioHlsTrack(AudioTrackInfo info, HttpInterfaceManager http, boolean inner)
        {
            super(info, info.identifier, http, inner);
            this.http = http;
            this.inner = inner;
        }

        @Override
        protected void processJoinedStream(LocalAudioTrackExecutor executor, InputStream stream) throws Exception
        {
            PushbackInputStream joined = new PushbackInputStream(stream, 1);
            int first = joined.read();
            if (first < 0)
            {
                return;
            }
            joined.unread(first);
            if (first == 0x47)
            {
                super.processJoinedStream(executor, joined);
            }
            else
            {
                processDelegate(new AdtsAudioTrack(this.trackInfo, new AdtsFrames(joined)), executor);
            }
        }

        @Override
        protected AudioTrack makeShallowClone()
        {
            return new RadioHlsTrack(this.trackInfo, this.http, this.inner);
        }
    }

    // Deja pasar solo cuadros ADTS enteros: salta las etiquetas ID3 y cualquier cosa entre cuadros.
    static final class AdtsFrames extends InputStream
    {
        private final PushbackInputStream in;
        private byte[] frame = new byte[0];
        private int position;

        AdtsFrames(InputStream in)
        {
            this.in = new PushbackInputStream(in, 16);
        }

        @Override
        public int read() throws IOException
        {
            if (this.position >= this.frame.length && !nextFrame())
            {
                return -1;
            }
            return this.frame[this.position++] & 0xFF;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException
        {
            if (length == 0)
            {
                return 0;
            }
            if (this.position >= this.frame.length && !nextFrame())
            {
                return -1;
            }
            int count = Math.min(length, this.frame.length - this.position);
            System.arraycopy(this.frame, this.position, buffer, offset, count);
            this.position += count;
            return count;
        }

        private boolean nextFrame() throws IOException
        {
            while (true)
            {
                int b = this.in.read();
                if (b < 0)
                {
                    return false;
                }
                if (b == 'I')
                {
                    byte[] rest = new byte[9];
                    int got = this.in.readNBytes(rest, 0, rest.length);
                    if (got == 9 && rest[0] == 'D' && rest[1] == '3')
                    {
                        long size = syncsafe(rest, 5) + ((rest[4] & 0x10) != 0 ? 10 : 0);
                        this.in.skipNBytes(size);
                        continue;
                    }
                    this.in.unread(rest, 0, got);
                    continue;
                }
                if (b != 0xFF)
                {
                    continue;
                }
                byte[] header = new byte[7];
                header[0] = (byte) 0xFF;
                int got = this.in.readNBytes(header, 1, 6);
                if (got < 6)
                {
                    return false;
                }
                int length = ((header[3] & 0x03) << 11) | ((header[4] & 0xFF) << 3) | ((header[5] & 0xE0) >> 5);
                if ((header[1] & 0xF6) != 0xF0 || length < 7)
                {
                    // No era un cuadro: se sigue buscando desde el byte siguiente.
                    this.in.unread(header, 1, 6);
                    continue;
                }
                byte[] full = new byte[length];
                System.arraycopy(header, 0, full, 0, 7);
                int body = this.in.readNBytes(full, 7, length - 7);
                if (body < length - 7)
                {
                    return false;
                }
                this.frame = full;
                this.position = 0;
                return true;
            }
        }
    }

    // Los tamaños de ID3 van en 4 bytes de 7 bits.
    private static long syncsafe(byte[] bytes, int at)
    {
        return ((bytes[at] & 0x7FL) << 21) | ((bytes[at + 1] & 0x7FL) << 14) | ((bytes[at + 2] & 0x7FL) << 7) | (bytes[at + 3] & 0x7FL);
    }

    private RadioFormats() {}
}

package com.cristoree.rocco.client.music;

import com.sedmelluq.discord.lavaplayer.format.Pcm16AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

// Abre links de musica con lavaplayer: archivos de audio (MP3, OGG, M4A, FLAC, WAV) y radios por
// internet. Solo links que ya son audio: no saca el sonido de ninguna pagina ni entra a la API
// privada de ningun sitio. Todo corre en el juego de cada jugador, con su conexion.
public final class MusicEngine
{
    // Lo que sale de abrir un link: la pista lista para sonar, el titulo y el link final.
    public record Opened(AudioTrack track, String title, String url, long durationMs)
    {
    }

    private static final String USER_AGENT = "RoccoMod (Minecraft mod; +https://github.com/CristoRee/Rocco)";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    @Nullable
    private static AudioPlayerManager manager;

    static synchronized AudioPlayerManager manager()
    {
        if (manager == null)
        {
            AudioPlayerManager created = new DefaultAudioPlayerManager();
            // 48 kHz estereo de 16 bits: despues se mezcla a mono para que suene desde Rocco.
            created.getConfiguration().setOutputFormat(new Pcm16AudioDataFormat(2, 48000, 960, false));
            created.setFrameBufferDuration(5000);
            created.registerSourceManager(configure(new HttpAudioSourceManager(RadioFormats.containers())));
            manager = created;
        }
        return manager;
    }

    // Se presenta como lo que es (hay sitios, como Wikimedia, que lo exigen) y le da tiempo a los
    // servidores lentos: de fabrica corta a los 3 segundos.
    static <T extends com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable> T configure(T http)
    {
        http.configureBuilder(builder -> builder.setUserAgent(USER_AGENT));
        http.configureRequests(config -> org.apache.http.client.config.RequestConfig.copy(config)
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(READ_TIMEOUT_MS)
                .build());
        return http;
    }

    public static synchronized void shutdown()
    {
        if (manager != null)
        {
            manager.shutdown();
            manager = null;
        }
    }

    // Abre el link y da la pista. Nunca en el hilo del juego.
    public static Opened open(String url) throws Exception
    {
        AudioTrack track;
        try
        {
            track = load(url);
        }
        catch (Exception e)
        {
            // Los sitios a veces tropiezan: un error pasajero se prueba una vez mas.
            if (!passing(e))
            {
                throw e;
            }
            Thread.sleep(1000L);
            track = load(url);
        }
        return new Opened(track, title(track), track.getInfo().uri == null ? url : track.getInfo().uri,
                track.getInfo().isStream ? 0L : track.getDuration());
    }

    // El servidor tardo de mas o fallo de su lado (un error 5xx). Un 404 o un archivo raro no cambian.
    private static boolean passing(Throwable error)
    {
        for (Throwable cause = error; cause != null; cause = cause.getCause() == cause ? null : cause.getCause())
        {
            if (cause instanceof java.net.SocketTimeoutException
                    || (cause instanceof IllegalStateException && String.valueOf(cause.getMessage()).startsWith("Status code 5")))
            {
                return true;
            }
        }
        return false;
    }

    private static AudioTrack load(String item) throws Exception
    {
        CompletableFuture<AudioTrack> result = new CompletableFuture<>();
        manager().loadItem(item, new AudioLoadResultHandler()
        {
            @Override
            public void trackLoaded(AudioTrack track)
            {
                result.complete(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist)
            {
                // Una lista (un disco de Bandcamp, por ejemplo): la elegida, o la primera.
                AudioTrack track = playlist.getSelectedTrack() != null ? playlist.getSelectedTrack()
                        : playlist.getTracks().isEmpty() ? null : playlist.getTracks().get(0);
                if (track == null)
                {
                    result.completeExceptionally(new IllegalStateException("lista vacia"));
                }
                else
                {
                    result.complete(track);
                }
            }

            @Override
            public void noMatches()
            {
                result.completeExceptionally(new IllegalStateException("no encontre nada en ese link"));
            }

            @Override
            public void loadFailed(FriendlyException exception)
            {
                result.completeExceptionally(exception);
            }
        });
        return result.get(30, TimeUnit.SECONDS);
    }

    // Lo que no dice nada: "playlist", "stream", "live"... de una radio se usa otra parte del link.
    private static final java.util.regex.Pattern GENERIC = java.util.regex.Pattern.compile(
            "(?i)(playlist|chunks?|index|stream\\w*|live|listen|play|radio|master|audio|hls|icecast|mount|aac|mp3|ogg|\\d+|;|)");

    // De "https://popradio.stweb.tv/popradio/live/playlist.m3u8" sale "popradio": la ultima parte del
    // camino que diga algo, o si no el nombre del sitio.
    private static String nameFromLink(String uri)
    {
        String clean = uri.replaceAll("[?#].*$", "");
        String[] path = clean.replaceFirst("^[a-zA-Z]+://[^/]*", "").split("/");
        for (int i = path.length - 1; i >= 0; i--)
        {
            String part = java.net.URLDecoder.decode(path[i].replaceAll("\\.[A-Za-z0-9]{2,4}$", ""), java.nio.charset.StandardCharsets.UTF_8)
                    .replace('_', ' ').replace(';', ' ').trim();
            if (!GENERIC.matcher(part).matches())
            {
                return part;
            }
        }
        String host = clean.replaceFirst("^[a-zA-Z]+://", "").replaceAll("[/:].*$", "");
        for (String label : host.split("\\."))
        {
            if (!label.matches("(?i)www\\d*|stream\\w*|server\\d*|ice\\d*|cast\\d*|live|radio|play|edge.*|cdn.*|s\\d+") && !label.isBlank())
            {
                return label;
            }
        }
        return "";
    }

    private static String title(AudioTrack track)
    {
        String title = track.getInfo().title;
        if (title == null || title.isBlank() || title.equals("Unknown title") || GENERIC.matcher(title.trim()).matches())
        {
            title = nameFromLink(track.getInfo().uri == null ? "" : track.getInfo().uri);
        }
        // El artista adelante solo si el titulo no lo trae ("Artista - Cancion").
        String author = track.getInfo().author;
        if (author != null && !author.isBlank() && !author.equals("Unknown artist") && !title.contains(" - ")
                && !title.toLowerCase(Locale.ROOT).contains(author.toLowerCase(Locale.ROOT)))
        {
            title = author + " - " + title;
        }
        return title.isBlank() ? "una cancion" : title;
    }

    // Un link a esta misma computadora o a su red: el link que puso otro jugador no tiene por que
    // hacer que tu juego toque tu router o lo que tengas en tu casa. Hace una consulta de DNS: nunca
    // en el hilo del juego. Si no se puede saber, no cuenta como local: el link va a fallar solo.
    static boolean pointsToLocalNetwork(String url)
    {
        try
        {
            String host = new java.net.URI(url).getHost();
            if (host == null)
            {
                return false;
            }
            String name = host.toLowerCase(Locale.ROOT);
            if (name.equals("localhost") || name.endsWith(".localhost") || name.endsWith(".local") || name.endsWith(".lan"))
            {
                return true;
            }
            for (java.net.InetAddress address : java.net.InetAddress.getAllByName(host))
            {
                byte first = address.getAddress()[0];
                if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                        || address.isAnyLocalAddress() || (address instanceof java.net.Inet6Address && (first & 0xFE) == 0xFC))
                {
                    return true;
                }
            }
            return false;
        }
        catch (java.net.URISyntaxException | java.net.UnknownHostException e)
        {
            return false;
        }
    }

    // Por que fallo, en pocas palabras (para el log).
    static String describe(Throwable error)
    {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root)
        {
            root = root.getCause();
        }
        String message = String.valueOf(error.getMessage()) + " / " + root;
        return message.length() > 250 ? message.substring(0, 250) : message;
    }

    private MusicEngine() {}
}

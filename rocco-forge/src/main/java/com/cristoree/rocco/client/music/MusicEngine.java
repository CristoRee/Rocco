package com.cristoree.rocco.client.music;

import com.sedmelluq.discord.lavaplayer.format.Pcm16AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

// Abre links de musica con lavaplayer: archivos de audio (MP3, OGG, M4A, FLAC, WAV), radios por
// internet, Bandcamp y Twitch. Todo corre en el juego de cada jugador, con su conexion.
public final class MusicEngine
{
    // Lo que sale de abrir un link: la pista lista para sonar, el titulo y el link final.
    public record Opened(AudioTrack track, String title, String url, long durationMs)
    {
    }

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
            // Solo estas: el link directo va al final porque acepta cualquier cosa.
            created.registerSourceManager(new BandcampAudioSourceManager());
            created.registerSourceManager(new TwitchStreamAudioSourceManager());
            created.registerSourceManager(new HttpAudioSourceManager());
            manager = created;
        }
        return manager;
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
        AudioTrack track = load(url);
        return new Opened(track, title(track), track.getInfo().uri == null ? url : track.getInfo().uri,
                track.getInfo().isStream ? 0L : track.getDuration());
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

    private static String title(AudioTrack track)
    {
        String title = track.getInfo().title;
        if (title == null || title.isBlank() || title.equals("Unknown title"))
        {
            String uri = track.getInfo().uri == null ? "" : track.getInfo().uri;
            String file = uri.replaceAll("[?#].*$", "");
            file = file.substring(file.lastIndexOf('/') + 1).replaceAll("\\.[A-Za-z0-9]{2,4}$", "");
            title = java.net.URLDecoder.decode(file, java.nio.charset.StandardCharsets.UTF_8).replace('_', ' ').trim();
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

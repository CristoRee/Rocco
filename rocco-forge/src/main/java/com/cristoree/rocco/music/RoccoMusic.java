package com.cristoree.rocco.music;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoState;
import com.cristoree.rocco.entity.RoccoTask;
import com.cristoree.rocco.voice.RoccoNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// La musica de Rocco, del lado del servidor. El servidor no baja ni decodifica nada: el jugador que
// pide la cancion la abre en su juego (su conexion, su IP), avisa si se puede escuchar y con que
// titulo, y recien ahi arranca la "sesion": un link y el tick en que empezo. Cada jugador cerca la
// reproduce por su cuenta desde ese tick, asi todos escuchan lo mismo al mismo tiempo, y el que
// llega tarde entra en el punto justo. Rocco, mientras suena, canta o baila.
@Mod.EventBusSubscriber(modid = RoccoMod.MODID)
public final class RoccoMusic
{
    // Hasta donde llegan los avisos de "esta sonando esto" (el sonido se apaga a los 48 bloques).
    private static final double ANNOUNCE_RADIUS = 96.0D;
    // Le da al jugador este tiempo para abrir el link y contestar.
    private static final int RESOLVE_TIMEOUT_TICKS = 30 * 20;
    // Arranca un segundo despues de confirmado, para que todos lleguen a cargar el principio.
    private static final int START_DELAY_TICKS = 20;

    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"']{4,500}", Pattern.CASE_INSENSITIVE);
    // Sitios de videos y apps de musica: sus condiciones no dejan sacar el audio, asi que ni se prueban.
    private static final Pattern STREAMING_SITE = Pattern.compile("^https?://([a-z0-9-]+\\.)*(youtube\\.com|youtu\\.be"
            + "|youtube-nocookie\\.com|spotify\\.com|spotify\\.link|soundcloud\\.com|deezer\\.com|deezer\\.page\\.link|music\\.apple\\.com"
            + "|tidal\\.com|music\\.amazon\\.[a-z.]+|tiktok\\.com|vimeo\\.com|instagram\\.com|facebook\\.com|x\\.com|twitter\\.com"
            + "|bandcamp\\.com|twitch\\.tv)(/|$|\\?)",
            Pattern.CASE_INSENSITIVE);

    private static final AtomicInteger IDS = new AtomicInteger();
    private static final Map<Integer, Pending> PENDING = new HashMap<>();

    // Lo que esta sonando: el link (o la busqueda), el titulo, cuanto dura (0 si es una radio o no
    // se sabe) y el tick del mundo en que empezo. En pausa, 'pausedAtMs' guarda por donde iba.
    public record Session(int id, String url, String title, long durationMs, long startTick, long pausedAtMs, Style style)
    {
        public boolean paused()
        {
            return this.pausedAtMs >= 0;
        }

        public long positionMs(long gameTime)
        {
            return paused() ? this.pausedAtMs : (gameTime - this.startTick) * 50L;
        }
    }

    // Como canta segun la cancion (el titulo lo dice casi siempre).
    public enum Style
    {
        SOFT, GROOVE, RAP;

        static Style guess(String title)
        {
            String t = title.toLowerCase(Locale.ROOT);
            if (t.matches(".*\\b(rap|trap|hip ?hop|freestyle|bzrp|drill|cypher|tiraera)\\b.*"))
            {
                return RAP;
            }
            if (t.matches(".*\\b(balada|lento|lenta|acustic[oa]|acoustic|piano|lofi|lo-fi|sad|triste|bolero|"
                    + "cancion de cuna|lullaby|romantic[oa]|unplugged|ballad|chill|relax|zamba)\\b.*"))
            {
                return SOFT;
            }
            return GROOVE;
        }
    }

    private record Pending(UUID rocco, UUID player, String url, long expiresAt)
    {
    }

    // ---------- pedir una cancion ----------

    // El primer link del mensaje, si hay uno.
    @Nullable
    public static String findUrl(String text)
    {
        Matcher matcher = URL.matcher(text);
        if (!matcher.find())
        {
            return null;
        }
        // La puntuacion pegada al final ("mira esto: https://...!") no es parte del link.
        return matcher.group().replaceAll("[).,;:!?¡¿]+$", "");
    }

    // "Poné esto" con un link: el jugador que lo pidio lo abre. Sin link, Rocco lo pide.
    public static void request(RoccoEntity rocco, @Nullable ServerPlayer player, String what)
    {
        String query = what == null ? "" : what.trim();
        if (!RoccoCommonConfig.MUSIC_ENABLED.get())
        {
            rocco.sayLine("music_off");
            return;
        }
        if (player == null)
        {
            return;
        }
        String item = findUrl(query);
        if (item == null)
        {
            rocco.sayLine("music_need_link");
            return;
        }
        // Los videos y las apps de musica no se pueden abrir: se pide un archivo de audio o una radio.
        if (STREAMING_SITE.matcher(item).find())
        {
            rocco.sayLine("music_site_fail");
            return;
        }

        int id = IDS.incrementAndGet();
        PENDING.put(id, new Pending(rocco.getUUID(), player.getUUID(), item, rocco.level().getGameTime() + RESOLVE_TIMEOUT_TICKS));
        RoccoNetwork.sendMusicResolve(player, new RoccoNetwork.MusicResolve(id, item));
        rocco.sayLineOnce("music_loading", 60);
    }

    // El jugador ya abrio el link: si se puede escuchar, arranca para todos.
    public static void onResolved(ServerPlayer sender, RoccoNetwork.MusicResolved msg)
    {
        Pending pending = PENDING.remove(msg.request());
        if (pending == null || !pending.player().equals(sender.getUUID()))
        {
            return;
        }
        if (!(sender.serverLevel().getEntity(pending.rocco()) instanceof RoccoEntity rocco) || !rocco.isAlive())
        {
            return;
        }
        if (!msg.ok())
        {
            RoccoMod.LOGGER.info("[Rocco] No se pudo abrir la musica {}: {}", pending.url(), msg.error());
            rocco.sayLine("music_fail");
            return;
        }
        String url = msg.url().isBlank() ? pending.url() : msg.url();
        // Este link lo van a abrir todos los que esten cerca: no se confia en lo que manda un juego.
        if (!URL.matcher(url).matches() || STREAMING_SITE.matcher(url).find())
        {
            rocco.sayLine("music_site_fail");
            return;
        }
        String title = msg.title().isBlank() ? "esa cancion" : msg.title();
        play(rocco, url, title, Math.max(0L, msg.durationMs()), 0L);
        rocco.sayLine("music_start", "title", shortTitle(title));
    }

    private static String shortTitle(String title)
    {
        String clean = title.replaceAll("(?i)\\s*[(\\[](official|oficial|video|lyric|letra|audio|4k|hd|remaster)[^)\\]]*[)\\]]", "").trim();
        return clean.length() > 60 ? clean.substring(0, 57) + "..." : clean;
    }

    // ---------- reproducir, pausar, parar ----------

    private static void play(RoccoEntity rocco, String url, String title, long durationMs, long fromMs)
    {
        stop(rocco, false);
        long start = rocco.level().getGameTime() + START_DELAY_TICKS - fromMs / 50L;
        Session session = new Session(IDS.incrementAndGet(), url, title, durationMs, start, -1L, Style.guess(title));
        rocco.setMusic(session);
        announce(rocco, session);
    }

    public static boolean stop(RoccoEntity rocco, boolean say)
    {
        Session session = rocco.music();
        if (session == null)
        {
            if (say)
            {
                rocco.sayLine("music_none");
            }
            return false;
        }
        rocco.setMusic(null);
        broadcast(rocco, new RoccoNetwork.MusicStop(rocco.getId(), session.id()));
        if (say)
        {
            rocco.sayLine("music_stop");
        }
        rocco.stopSinging();
        return true;
    }

    public static void pause(RoccoEntity rocco)
    {
        Session session = rocco.music();
        if (session == null || session.paused())
        {
            rocco.sayLine("music_none");
            return;
        }
        long at = session.positionMs(rocco.level().getGameTime());
        rocco.setMusic(new Session(session.id(), session.url(), session.title(), session.durationMs(), session.startTick(),
                Math.max(0L, at), session.style()));
        broadcast(rocco, new RoccoNetwork.MusicStop(rocco.getId(), session.id()));
        rocco.stopSinging();
        rocco.sayLine("music_pause");
    }

    public static void resume(RoccoEntity rocco)
    {
        Session session = rocco.music();
        if (session == null || !session.paused())
        {
            rocco.sayLine(session == null ? "music_none" : "music_resume");
            return;
        }
        play(rocco, session.url(), session.title(), session.durationMs(), session.pausedAtMs());
        rocco.sayLine("music_resume");
    }

    // "Subí el volumen": es de cada jugador (su config), asi que se le avisa a su juego.
    public static void volume(RoccoEntity rocco, @Nullable ServerPlayer player, boolean up)
    {
        if (player != null)
        {
            RoccoNetwork.sendMusicVolume(player, new RoccoNetwork.MusicVolume(up ? 0.15F : -0.15F));
        }
        rocco.sayLine(up ? "music_louder" : "music_quieter");
    }

    // ---------- cada tick ----------

    // Termino la cancion, se murio o se fue lejos de todos: se corta.
    public static void tick(RoccoEntity rocco)
    {
        Session session = rocco.music();
        if (session == null)
        {
            return;
        }
        if (rocco.getRoccoState() != RoccoState.ACTIVE || !rocco.isAlive())
        {
            stop(rocco, false);
            return;
        }
        long now = rocco.level().getGameTime();
        if (!session.paused() && session.durationMs() > 0 && session.positionMs(now) > session.durationMs() + 1500L)
        {
            stop(rocco, false);
            rocco.sayLineOnce("music_end", 1200);
        }
    }

    // Los pedidos que nadie contesto (el jugador se fue, o su juego no tiene el mod).
    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event)
    {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || PENDING.isEmpty())
        {
            return;
        }
        long now = event.getServer().overworld().getGameTime();
        PENDING.entrySet().removeIf(entry ->
        {
            if (now < entry.getValue().expiresAt())
            {
                return false;
            }
            for (ServerLevel level : event.getServer().getAllLevels())
            {
                if (level.getEntity(entry.getValue().rocco()) instanceof RoccoEntity rocco)
                {
                    rocco.sayLine("music_fail");
                }
            }
            return true;
        });
    }

    // El que llega cerca mientras suena algo empieza a escucharlo desde donde va.
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event)
    {
        if (event.getTarget() instanceof RoccoEntity rocco && event.getEntity() instanceof ServerPlayer player)
        {
            Session session = rocco.music();
            if (session != null && !session.paused())
            {
                RoccoNetwork.sendMusicPlay(player, playPacket(rocco, session));
            }
        }
    }

    private static void announce(RoccoEntity rocco, Session session)
    {
        broadcast(rocco, playPacket(rocco, session));
    }

    private static RoccoNetwork.MusicPlay playPacket(RoccoEntity rocco, Session session)
    {
        return new RoccoNetwork.MusicPlay(rocco.getId(), session.id(), session.url(), session.title(),
                session.durationMs(), session.startTick());
    }

    private static void broadcast(RoccoEntity rocco, Object packet)
    {
        if (!(rocco.level() instanceof ServerLevel level))
        {
            return;
        }
        for (ServerPlayer player : level.players())
        {
            if (player.distanceToSqr(rocco) <= ANNOUNCE_RADIUS * ANNOUNCE_RADIUS)
            {
                if (packet instanceof RoccoNetwork.MusicPlay play)
                {
                    RoccoNetwork.sendMusicPlay(player, play);
                }
                else if (packet instanceof RoccoNetwork.MusicStop stop)
                {
                    RoccoNetwork.sendMusicStop(player, stop);
                }
            }
        }
    }

    // ---------- tocadiscos ----------

    // Un tocadiscos con un disco sonando a menos de 'radius' bloques (el mas cercano).
    @Nullable
    public static BlockPos playingJukebox(ServerLevel level, BlockPos around, double radius)
    {
        BlockPos best = null;
        double bestDistance = radius * radius;
        int chunks = (int) Math.ceil(radius / 16.0D);
        int cx = around.getX() >> 4;
        int cz = around.getZ() >> 4;
        for (int x = cx - chunks; x <= cx + chunks; x++)
        {
            for (int z = cz - chunks; z <= cz + chunks; z++)
            {
                if (!level.hasChunk(x, z))
                {
                    continue;
                }
                LevelChunk chunk = level.getChunk(x, z);
                for (BlockEntity entity : chunk.getBlockEntities().values())
                {
                    if (entity instanceof JukeboxBlockEntity jukebox
                            && entity.getBlockState().hasProperty(JukeboxBlock.HAS_RECORD)
                            && entity.getBlockState().getValue(JukeboxBlock.HAS_RECORD)
                            && jukebox.isRecordPlaying())
                    {
                        double distance = entity.getBlockPos().distSqr(around);
                        if (distance < bestDistance)
                        {
                            bestDistance = distance;
                            best = entity.getBlockPos();
                        }
                    }
                }
            }
        }
        return best;
    }

    // ---------- que baila o canta ----------

    // La proxima vuelta mientras suena algo: con su musica canta dos vueltas y baila una (las
    // canciones lentas, solo cantando); con un disco, un baile al azar que cambia cada dos vueltas.
    public static RoccoTask nextPerformance(@Nullable RoccoTask last, @Nullable Session own, net.minecraft.util.RandomSource random)
    {
        if (own != null)
        {
            RoccoTask sing = switch (own.style())
            {
                case SOFT -> RoccoTask.SING_SOFT;
                case RAP -> RoccoTask.SING_RAP;
                case GROOVE -> RoccoTask.SING_GROOVE;
            };
            if (own.style() == Style.SOFT || last == null || last.musical() && !last.singing())
            {
                return sing;
            }
            // Despues de cantar, a veces baila un rato (una de cada tres vueltas).
            return random.nextInt(3) == 0 ? randomDance(random, false) : sing;
        }
        return randomDance(random, true);
    }

    public static RoccoTask randomDance(net.minecraft.util.RandomSource random, boolean withClassic)
    {
        int pick = random.nextInt(withClassic ? 3 : 2);
        return pick == 0 ? RoccoTask.DANCE_FIESTA : pick == 1 ? RoccoTask.DANCE_MENEITO : RoccoTask.DANCING;
    }

    private RoccoMusic() {}
}

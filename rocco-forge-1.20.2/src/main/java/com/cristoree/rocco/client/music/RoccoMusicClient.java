package com.cristoree.rocco.client.music;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoClientConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.registry.ModSounds;
import com.cristoree.rocco.voice.RoccoNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// La musica de Rocco en el juego de cada jugador: abre el link, la decodifica y la hace sonar desde
// Rocco (con el volumen de "Discos", la distancia y la pausa del juego, como un tocadiscos que
// camina). Va pegada al tick del mundo en que empezo, asi todos escuchan lo mismo a la vez.
@Mod.EventBusSubscriber(modid = RoccoMod.MODID, value = Dist.CLIENT)
public final class RoccoMusicClient
{
    // Antes de arrancar espera tener un poco de audio, para no empezar cortado.
    private static final int PREBUFFER_FRAMES = 75;
    private static final long PREBUFFER_TIMEOUT_MS = 8000L;

    private static final ExecutorService WORKERS = Executors.newCachedThreadPool(runnable ->
    {
        Thread thread = new Thread(runnable, "Rocco-Musica-Links");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<Integer, Session> SESSIONS = new HashMap<>();
    // Los links que este jugador pidio y abrio: los suyos pueden ser de su propia red.
    private static final java.util.Set<String> MINE = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // El reloj del mundo, para el hilo del sonido: el tick y cuando llego.
    private static volatile long clockTick;
    private static volatile long clockNanos;

    private static final class Session
    {
        final RoccoNetwork.MusicPlay info;
        @Nullable
        volatile MusicDecoder decoder;
        @Nullable
        MusicSound sound;
        volatile boolean closed;

        Session(RoccoNetwork.MusicPlay info)
        {
            this.info = info;
        }

        long timelineMs()
        {
            long elapsed = Math.min(50L, (System.nanoTime() - clockNanos) / 1_000_000L);
            return (clockTick - this.info.startTick()) * 50L + elapsed;
        }

        void close()
        {
            this.closed = true;
            if (this.sound != null)
            {
                this.sound.finish();
                this.sound = null;
            }
            MusicDecoder current = this.decoder;
            if (current != null)
            {
                current.close();
                this.decoder = null;
            }
        }
    }

    // ---------- lo que manda el servidor ----------

    // "Abri este link y decime si se puede escuchar": lo abre y prueba que de audio de verdad (hay
    // links que cargan el titulo y despues no dan sonido).
    public static void resolve(RoccoNetwork.MusicResolve msg)
    {
        WORKERS.execute(() ->
        {
            RoccoNetwork.MusicResolved answer;
            try
            {
                MusicEngine.Opened opened = MusicEngine.open(msg.url());
                String failure = probe(opened);
                if (failure != null)
                {
                    throw new IllegalStateException(failure);
                }
                MINE.add(opened.url());
                answer = new RoccoNetwork.MusicResolved(msg.request(), true, opened.url(), opened.title(), opened.durationMs(), "");
            }
            catch (Exception e)
            {
                String error = MusicEngine.describe(e);
                RoccoMod.LOGGER.info("[Rocco] No pude abrir {}: {}", msg.url(), error);
                answer = new RoccoNetwork.MusicResolved(msg.request(), false, "", "", 0L, error);
            }
            RoccoNetwork.MusicResolved reply = answer;
            Minecraft.getInstance().execute(() -> RoccoNetwork.sendMusicResolved(reply));
        });
    }

    // null si la pista da audio; si no, por que.
    @Nullable
    private static String probe(MusicEngine.Opened opened)
    {
        try (MusicDecoder test = new MusicDecoder(opened.track().makeClone(), 0L))
        {
            long until = System.currentTimeMillis() + 15_000L;
            while (System.currentTimeMillis() < until)
            {
                if (test.hasAudio())
                {
                    return null;
                }
                if (test.finished())
                {
                    break;
                }
                Thread.sleep(50L);
            }
            String error = test.error();
            return error != null ? error : "sin audio";
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return "cancelado";
        }
    }

    public static void play(RoccoNetwork.MusicPlay msg)
    {
        Session old = SESSIONS.remove(msg.entityId());
        if (old != null)
        {
            old.close();
        }
        if (!RoccoClientConfig.MUSIC_LISTEN.get())
        {
            return;
        }
        Session session = new Session(msg);
        SESSIONS.put(msg.entityId(), session);
        WORKERS.execute(() -> open(session));
    }

    public static void stop(RoccoNetwork.MusicStop msg)
    {
        Session session = SESSIONS.get(msg.entityId());
        if (session != null && session.info.session() == msg.session())
        {
            SESSIONS.remove(msg.entityId());
            session.close();
        }
    }

    // "Subí el volumen" / "bajá el volumen".
    public static void changeVolume(float delta)
    {
        double now = Math.max(0.0D, Math.min(1.0D, RoccoClientConfig.MUSIC_VOLUME.get() + delta));
        RoccoClientConfig.MUSIC_VOLUME.set(now);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null)
        {
            minecraft.player.displayClientMessage(Component.translatable("rocco.music.volume", Math.round(now * 100.0D))
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    // ---------- abrir y arrancar ----------

    private static void open(Session session)
    {
        String url = session.info.url();
        try
        {
            if (!MINE.contains(url) && MusicEngine.pointsToLocalNetwork(url))
            {
                RoccoMod.LOGGER.info("[Rocco] No abro un link de otro jugador que apunta a tu red: {}", url);
                return;
            }
            MusicEngine.Opened opened = MusicEngine.open(url);
            start(session, opened);
        }
        catch (Exception e)
        {
            RoccoMod.LOGGER.info("[Rocco] No pude abrir la musica {}: {}", url, MusicEngine.describe(e));
        }
    }

    private static void start(Session session, MusicEngine.Opened opened)
    {
        if (session.closed)
        {
            return;
        }
        boolean live = opened.durationMs() <= 0 || !opened.track().isSeekable();
        // El que llega tarde (o cuando ya arranco) empieza un poco adelante: lo que cuesta cargar.
        long now = session.timelineMs();
        long from = !live && now > 3000L ? now + 1500L : 0L;
        MusicDecoder decoder = new MusicDecoder(opened.track(), from);
        session.decoder = decoder;

        long until = System.currentTimeMillis() + PREBUFFER_TIMEOUT_MS;
        while (!session.closed && decoder.buffered() < PREBUFFER_FRAMES && !decoder.finished()
                && System.currentTimeMillis() < until)
        {
            try
            {
                Thread.sleep(40L);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (session.closed)
        {
            decoder.close();
            return;
        }
        if (!decoder.hasAudio())
        {
            String error = decoder.error();
            decoder.close();
            session.decoder = null;
            RoccoMod.LOGGER.info("[Rocco] La musica {} no dio audio: {}", session.info.url(), error);
            return;
        }
        MusicStream stream = new MusicStream(decoder, session::timelineMs, live);
        Minecraft.getInstance().execute(() ->
        {
            if (session.closed || SESSIONS.get(session.info.entityId()) != session || Minecraft.getInstance().level == null)
            {
                return;
            }
            Entity entity = Minecraft.getInstance().level.getEntity(session.info.entityId());
            MusicSound sound = new MusicSound(session, stream, entity);
            session.sound = sound;
            Minecraft.getInstance().getSoundManager().play(sound);
        });
    }

    // ---------- cada tick ----------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null)
        {
            if (!SESSIONS.isEmpty())
            {
                closeAll();
            }
            return;
        }
        if (minecraft.isPaused())
        {
            // En pausa (un jugador) el mundo no avanza: el reloj tampoco.
            clockNanos = System.nanoTime();
            return;
        }
        clockTick = minecraft.level.getGameTime();
        clockNanos = System.nanoTime();

        if (!RoccoClientConfig.MUSIC_LISTEN.get() && !SESSIONS.isEmpty())
        {
            closeAll();
            return;
        }
        // Las que terminaron se sacan.
        SESSIONS.values().removeIf(session ->
        {
            MusicDecoder decoder = session.decoder;
            boolean over = session.sound != null && !minecraft.getSoundManager().isActive(session.sound)
                    && session.sound.isStopped();
            boolean dead = decoder != null && decoder.drained() && session.sound == null;
            if (over || dead)
            {
                session.close();
                return true;
            }
            return false;
        });
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event)
    {
        closeAll();
    }

    private static void closeAll()
    {
        SESSIONS.values().forEach(Session::close);
        SESSIONS.clear();
        MINE.clear();
    }

    // ---------- para Rocco (animaciones) ----------

    // El ritmo de lo que esta sonando en este Rocco (0 si no se sabe todavia).
    public static double bpm(int entityId)
    {
        if (!RoccoClientConfig.MUSIC_BEAT_SYNC.get())
        {
            return 0.0D;
        }
        Session session = SESSIONS.get(entityId);
        MusicDecoder decoder = session == null ? null : session.decoder;
        return decoder == null ? 0.0D : decoder.bpm();
    }

    public static boolean isPlaying(int entityId)
    {
        Session session = SESSIONS.get(entityId);
        return session != null && session.sound != null;
    }

    // ---------- el sonido ----------

    // Suena desde Rocco y lo sigue. Mientras el habla, la musica baja un poco para que se le entienda.
    private static final class MusicSound extends AbstractTickableSoundInstance
    {
        private final Session session;
        private final MusicStream stream;
        private int missingTicks;
        private float duck = 1.0F;

        MusicSound(Session session, MusicStream stream, @Nullable Entity entity)
        {
            super(ModSounds.MUSIC.get(), SoundSource.RECORDS, RandomSource.create());
            this.session = session;
            this.stream = stream;
            this.looping = false;
            this.delay = 0;
            this.relative = false;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.volume = RoccoClientConfig.MUSIC_VOLUME.get().floatValue();
            this.pitch = 1.0F;
            if (entity != null)
            {
                this.x = entity.getX();
                this.y = entity.getEyeY();
                this.z = entity.getZ();
            }
        }

        void finish()
        {
            stop();
        }

        @Override
        public void tick()
        {
            if (this.session.closed)
            {
                stop();
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(this.session.info.entityId());
            if (entity == null || entity.isRemoved())
            {
                // Un rato sin verlo (se descargo, se fue lejos): se corta.
                if (++this.missingTicks > 60)
                {
                    stop();
                }
                return;
            }
            this.missingTicks = 0;
            this.x = entity.getX();
            this.y = entity.getEyeY();
            this.z = entity.getZ();
            boolean speaking = entity instanceof RoccoEntity rocco
                    && com.cristoree.rocco.client.voice.VoicePlayback.isSpeaking(rocco.getId());
            float target = speaking ? 0.4F : 1.0F;
            this.duck += (target - this.duck) * 0.25F;
            this.volume = Math.max(0.001F, RoccoClientConfig.MUSIC_VOLUME.get().floatValue() * this.duck);
        }

        @Override
        public boolean canStartSilent()
        {
            return true;
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping)
        {
            return CompletableFuture.completedFuture(this.stream);
        }
    }

    private RoccoMusicClient() {}
}

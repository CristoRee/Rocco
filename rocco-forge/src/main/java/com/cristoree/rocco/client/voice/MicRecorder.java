package com.cristoree.rocco.client.voice;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoClientConfig;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.voice.MicReceiver;
import com.cristoree.rocco.voice.RoccoNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

// El microfono para hablarle a Rocco. Dos modos (stt.activation en la config):
//  - PUSH_TO_TALK: graba mientras mantenes la tecla y lo manda al soltarla.
//  - VOICE_ACTIVATION: la tecla prende y apaga la escucha; mientras esta prendida, cada vez que
//    hablas graba la frase sola y la manda cuando te quedas callado.
// Graba a 16 kHz mono: es lo que necesita Deepgram y pesa poco.
public final class MicRecorder
{
    public enum State { IDLE, LISTENING, RECORDING }

    private static final int PREROLL_FRAMES = 3;

    private static volatile State state = State.IDLE;
    private static Thread worker;
    private static volatile boolean keyHeld;
    private static volatile boolean listening;
    private static int session;
    private static boolean lastKeyDown;
    private static long errorShownAt;

    public static State state()
    {
        return state;
    }

    // Cada tick del cliente: lee la tecla y prende o apaga el microfono.
    public static void tick()
    {
        Minecraft minecraft = Minecraft.getInstance();
        boolean inGame = minecraft.player != null && minecraft.screen == null;
        boolean down = inGame && RoccoKeys.TALK.isDown();
        boolean pushToTalk = RoccoCommonConfig.STT_ACTIVATION.get() == RoccoCommonConfig.MicActivation.PUSH_TO_TALK;

        if (minecraft.player == null)
        {
            stopEverything();
            lastKeyDown = false;
            return;
        }

        if (pushToTalk)
        {
            listening = false;
            if (down && !lastKeyDown)
            {
                keyHeld = true;
                start(true);
            }
            else if (!down && lastKeyDown)
            {
                keyHeld = false;
            }
        }
        else if (down && !lastKeyDown)
        {
            // En modo por voz la tecla es un interruptor.
            listening = !listening;
            if (listening)
            {
                start(false);
            }
            feedback(listening);
            minecraft.player.displayClientMessage(Component.literal(listening
                    ? "Rocco te esta escuchando. Apreta la tecla de nuevo para apagar."
                    : "Microfono apagado.").withStyle(ChatFormatting.GRAY), true);
        }
        else if (listening)
        {
            // Si se apago y prendio muy rapido, el hilo anterior pudo no haber terminado.
            start(false);
        }
        lastKeyDown = down;
    }

    private static synchronized void start(boolean pushToTalk)
    {
        if (worker != null && worker.isAlive())
        {
            return;
        }
        worker = new Thread(() -> run(pushToTalk), "Rocco-Microfono");
        worker.setDaemon(true);
        worker.start();
    }

    private static void stopEverything()
    {
        keyHeld = false;
        listening = false;
    }

    private static void run(boolean pushToTalk)
    {
        MicSource source;
        try
        {
            source = openSource();
        }
        catch (LineUnavailableException | IllegalArgumentException | SecurityException e)
        {
            RoccoMod.LOGGER.warn("[Rocco] No pude abrir el microfono: {}", e.toString());
            showError("No pude abrir el microfono (" + e.getMessage() + "). Revisa client.input.micDevice "
                    + "o los permisos de microfono de Windows.");
            state = State.IDLE;
            listening = false;
            return;
        }

        try
        {
            if (pushToTalk)
            {
                recordWhileHeld(source);
            }
            else
            {
                listenForSpeech(source);
            }
        }
        finally
        {
            source.line().stop();
            source.line().close();
            state = State.IDLE;
        }
    }

    private static void recordWhileHeld(MicSource source)
    {
        state = State.RECORDING;
        feedback(true);
        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        long maxBytes = RoccoCommonConfig.STT_MAX_SECONDS.get() * (long) MicReceiver.SAMPLE_RATE * 2;

        while (keyHeld && audio.size() < maxBytes)
        {
            byte[] frame = source.read();
            if (frame.length > 0)
            {
                amplify(frame, frame.length);
                audio.writeBytes(frame);
            }
        }
        feedback(false);
        send(audio.toByteArray());
    }

    // Escucha hasta que se apaga: arranca a grabar cuando hay voz y corta con el silencio.
    private static void listenForSpeech(MicSource source)
    {
        Deque<byte[]> preroll = new ArrayDeque<>();
        ByteArrayOutputStream audio = null;
        double noise = 300.0D;
        int loudFrames = 0;
        int silentMs = 0;
        long maxBytes = RoccoCommonConfig.STT_MAX_SECONDS.get() * (long) MicReceiver.SAMPLE_RATE * 2;

        state = State.LISTENING;
        while (listening)
        {
            byte[] frame = source.read();
            int read = frame.length;
            if (read <= 0)
            {
                continue;
            }
            amplify(frame, read);
            double level = rms(frame, read);
            double threshold = Math.max(700.0D, noise * 3.0D);

            if (audio == null)
            {
                // Ruido de fondo: se va adaptando mientras no hablas.
                noise = noise * 0.95D + level * 0.05D;
                preroll.addLast(Arrays.copyOf(frame, read));
                if (preroll.size() > PREROLL_FRAMES)
                {
                    preroll.removeFirst();
                }

                loudFrames = level > threshold ? loudFrames + 1 : 0;
                if (loudFrames >= 2)
                {
                    audio = new ByteArrayOutputStream();
                    for (byte[] old : preroll)
                    {
                        audio.writeBytes(old);
                    }
                    preroll.clear();
                    silentMs = 0;
                    state = State.RECORDING;
                }
                continue;
            }

            audio.write(frame, 0, read);
            silentMs = level > threshold * 0.6D ? 0 : silentMs + 100;

            if (silentMs >= RoccoCommonConfig.STT_SILENCE_TIMEOUT_MS.get() || audio.size() >= maxBytes)
            {
                send(audio.toByteArray());
                audio = null;
                loudFrames = 0;
                state = State.LISTENING;
            }
        }
    }

    private static void send(byte[] pcm)
    {
        if (pcm.length < MicReceiver.SAMPLE_RATE / 2)
        {
            return;
        }
        int id = ++session;
        Minecraft.getInstance().execute(() ->
        {
            // En un servidor sin Rocco no hay a quien mandarselo.
            var connection = Minecraft.getInstance().getConnection();
            if (connection == null || !RoccoNetwork.CHANNEL.isRemotePresent(connection.getConnection()))
            {
                showError("Este servidor no tiene a Rocco instalado.");
                return;
            }
            int count = (pcm.length + RoccoNetwork.CHUNK_BYTES - 1) / RoccoNetwork.CHUNK_BYTES;
            for (int index = 0; index < count; index++)
            {
                int from = index * RoccoNetwork.CHUNK_BYTES;
                int to = Math.min(pcm.length, from + RoccoNetwork.CHUNK_BYTES);
                RoccoNetwork.sendMic(new RoccoNetwork.MicChunk(id, index, index == count - 1, Arrays.copyOfRange(pcm, from, to)));
            }
        });
    }

    // No todos los microfonos graban a 16 kHz mono: se prueba eso primero y despues lo que suelen
    // aceptar todos (48 o 44,1 kHz, mono o estereo). Lo que se grabe se pasa a 16 kHz mono.
    private static final float[] RATES = {MicReceiver.SAMPLE_RATE, 48000.0F, 44100.0F, 32000.0F, 22050.0F};

    private static MicSource openSource() throws LineUnavailableException
    {
        String wanted = RoccoClientConfig.MIC_DEVICE.get().trim().toLowerCase(java.util.Locale.ROOT);
        Mixer chosen = null;
        if (!wanted.isEmpty())
        {
            for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo())
            {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                if (mixerInfo.getName().toLowerCase(java.util.Locale.ROOT).contains(wanted)
                        && mixer.getTargetLineInfo().length > 0)
                {
                    chosen = mixer;
                    break;
                }
            }
            if (chosen == null)
            {
                showError("No encontre el microfono \"" + RoccoClientConfig.MIC_DEVICE.get() + "\"; uso el de Windows por defecto.");
            }
        }

        LineUnavailableException last = null;
        for (int channels = 1; channels <= 2; channels++)
        {
            for (float rate : RATES)
            {
                AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                try
                {
                    TargetDataLine line;
                    if (chosen != null && chosen.isLineSupported(info))
                    {
                        line = (TargetDataLine) chosen.getLine(info);
                    }
                    else if (AudioSystem.isLineSupported(info))
                    {
                        line = (TargetDataLine) AudioSystem.getLine(info);
                    }
                    else
                    {
                        continue;
                    }
                    line.open(format, (int) (rate / 10) * format.getFrameSize() * 4);
                    line.start();
                    if (rate != MicReceiver.SAMPLE_RATE || channels != 1)
                    {
                        RoccoMod.LOGGER.info("[Rocco] El microfono graba a {} Hz y {} canal(es); lo paso a 16 kHz mono.", (int) rate, channels);
                    }
                    return new MicSource(line, format);
                }
                catch (LineUnavailableException | IllegalArgumentException e)
                {
                    last = e instanceof LineUnavailableException unavailable ? unavailable : new LineUnavailableException(e.getMessage());
                }
            }
        }
        throw last != null ? last : new LineUnavailableException("no hay ningun microfono disponible");
    }

    // Lo que graba el microfono, convertido de a pedazos de 100 ms a 16 kHz mono.
    private static final class MicSource
    {
        private final TargetDataLine line;
        private final int channels;
        private final double step;
        private final byte[] raw;
        private double position;

        MicSource(TargetDataLine line, AudioFormat format)
        {
            this.line = line;
            this.channels = format.getChannels();
            this.step = format.getSampleRate() / MicReceiver.SAMPLE_RATE;
            this.raw = new byte[(int) (format.getSampleRate() / 10) * format.getFrameSize()];
        }

        TargetDataLine line()
        {
            return this.line;
        }

        byte[] read()
        {
            int read = this.line.read(this.raw, 0, this.raw.length);
            int frames = read / (2 * this.channels);
            if (frames <= 0)
            {
                return new byte[0];
            }

            // A mono, promediando los canales.
            short[] mono = new short[frames];
            for (int frame = 0; frame < frames; frame++)
            {
                int sum = 0;
                for (int channel = 0; channel < this.channels; channel++)
                {
                    int at = (frame * this.channels + channel) * 2;
                    sum += (short) ((this.raw[at] & 0xFF) | (this.raw[at + 1] << 8));
                }
                mono[frame] = (short) (sum / this.channels);
            }

            if (Math.abs(this.step - 1.0D) < 1e-6)
            {
                return toBytes(mono, frames);
            }

            // A 16 kHz: cada muestra nueva es el promedio de las que caen en su ventana.
            short[] out = new short[(int) (frames / this.step) + 2];
            int written = 0;
            while (this.position < frames && written < out.length)
            {
                int from = (int) this.position;
                int to = Math.min(frames, Math.max(from + 1, (int) (this.position + this.step)));
                int sum = 0;
                for (int i = from; i < to; i++)
                {
                    sum += mono[i];
                }
                out[written++] = (short) (sum / (to - from));
                this.position += this.step;
            }
            this.position -= frames;
            return toBytes(out, written);
        }

        private static byte[] toBytes(short[] samples, int count)
        {
            byte[] bytes = new byte[count * 2];
            for (int i = 0; i < count; i++)
            {
                bytes[i * 2] = (byte) samples[i];
                bytes[i * 2 + 1] = (byte) (samples[i] >> 8);
            }
            return bytes;
        }
    }

    private static void amplify(byte[] frame, int length)
    {
        double gain = RoccoCommonConfig.STT_MIC_GAIN.get();
        if (Math.abs(gain - 1.0D) < 0.01D)
        {
            return;
        }
        for (int i = 0; i + 1 < length; i += 2)
        {
            int sample = (short) ((frame[i] & 0xFF) | (frame[i + 1] << 8));
            int boosted = (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample * gain));
            frame[i] = (byte) boosted;
            frame[i + 1] = (byte) (boosted >> 8);
        }
    }

    private static double rms(byte[] frame, int length)
    {
        double sum = 0.0D;
        int samples = 0;
        for (int i = 0; i + 1 < length; i += 2)
        {
            int sample = (short) ((frame[i] & 0xFF) | (frame[i + 1] << 8));
            sum += (double) sample * sample;
            samples++;
        }
        return samples == 0 ? 0.0D : Math.sqrt(sum / samples);
    }

    private static void feedback(boolean starting)
    {
        if (!RoccoClientConfig.MIC_FEEDBACK_SOUND.get())
        {
            return;
        }
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), starting ? 1.6F : 1.1F, 0.35F)));
    }

    private static void showError(String text)
    {
        long now = System.currentTimeMillis();
        if (now - errorShownAt < 10_000L)
        {
            return;
        }
        errorShownAt = now;
        Minecraft.getInstance().execute(() ->
        {
            if (Minecraft.getInstance().player != null)
            {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("[Rocco] " + text).withStyle(ChatFormatting.GRAY));
            }
        });
    }

    private MicRecorder() {}
}

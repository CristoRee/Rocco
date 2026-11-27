package com.cristoree.rocco.client.voice;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.config.RoccoConfig;
import com.cristoree.rocco.voice.RoccoNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Lo que el servidor tiene andando (llega en ServerCaps al entrar y al recargar su config) y, con
// eso, que hace la tecla del microfono y con que se escucha a Rocco:
//  - el servidor tiene microfono o voz: se usa lo del servidor, como siempre;
//  - si no, y el jugador puso su propia clave: se usa la suya, en su PC;
//  - si no: el aviso que corresponde, sin grabar nada.
// En el mundo propio (el servidor corre en esta PC) se mira la config directamente.
@Mod.EventBusSubscriber(modid = RoccoMod.MODID, value = Dist.CLIENT)
public final class ClientCaps
{
    public enum Mic { SERVER, OWN, OFF_LOCAL, OFF_SERVER, OFF_DISABLED }

    public enum Voice { SERVER, OWN, NONE }

    @org.jetbrains.annotations.Nullable
    private static volatile RoccoNetwork.ServerCaps caps;

    public static void set(RoccoNetwork.ServerCaps received)
    {
        caps = received;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event)
    {
        caps = null;
    }

    // El servidor es este mismo juego: un mundo propio (o uno abierto a LAN por este jugador).
    public static boolean local()
    {
        return Minecraft.getInstance().hasSingleplayerServer();
    }

    // En un servidor ajeno: que el servidor sea dedicado (para la pantalla de config).
    public static boolean dedicated()
    {
        RoccoNetwork.ServerCaps known = caps;
        return !local() && known != null && known.dedicated();
    }

    // Jugando en un servidor que no es este juego.
    public static boolean remote()
    {
        return Minecraft.getInstance().level != null && !local();
    }

    public static boolean serverStt()
    {
        if (local())
        {
            return RoccoConfig.sttReady();
        }
        RoccoNetwork.ServerCaps known = caps;
        return known != null && known.sttReady();
    }

    public static boolean serverVoice()
    {
        if (local())
        {
            return RoccoConfig.voiceReady();
        }
        RoccoNetwork.ServerCaps known = caps;
        return known != null && known.voiceReady();
    }

    private static boolean allowOwnStt()
    {
        RoccoNetwork.ServerCaps known = caps;
        return known == null || known.allowClientStt();
    }

    private static boolean allowOwnVoice()
    {
        if (local())
        {
            // El texto lo manda el servidor de esta PC solo si voice.allowClientVoice esta prendido.
            return RoccoCommonConfig.ALLOW_CLIENT_VOICE.get();
        }
        RoccoNetwork.ServerCaps known = caps;
        return known == null || known.allowClientVoice();
    }

    // Que hace la tecla del microfono ahora.
    public static Mic mic()
    {
        if (serverStt())
        {
            return Mic.SERVER;
        }
        if (local())
        {
            // En su propio mundo, su clave propia vale aunque la comun este vacia.
            return RoccoConfig.ownSttReady() && RoccoCommonConfig.ALLOW_CLIENT_TRANSCRIPTION.get() ? Mic.OWN : Mic.OFF_LOCAL;
        }
        if (!allowOwnStt())
        {
            return Mic.OFF_DISABLED;
        }
        return RoccoConfig.ownSttReady() ? Mic.OWN : Mic.OFF_SERVER;
    }

    // Con que se escucha a Rocco ahora.
    public static Voice voice()
    {
        if (serverVoice())
        {
            return Voice.SERVER;
        }
        return allowOwnVoice() && RoccoConfig.ownVoiceReady() ? Voice.OWN : Voice.NONE;
    }

    // El aviso de por que la tecla no graba (null si graba).
    @org.jetbrains.annotations.Nullable
    public static Component micOffNotice()
    {
        return switch (mic())
        {
            case OFF_LOCAL -> Component.translatable("rocco.mic.off.local");
            case OFF_SERVER -> Component.translatable("rocco.mic.off.server");
            case OFF_DISABLED -> Component.translatable("rocco.mic.off.disabled");
            default -> null;
        };
    }

    // Para la pantalla de config en multijugador: de donde sale cada cosa.
    public static Component micStatus()
    {
        return switch (mic())
        {
            case SERVER -> Component.translatable("rocco.caps.mic.server");
            case OWN -> Component.translatable("rocco.caps.mic.own");
            case OFF_DISABLED -> Component.translatable("rocco.caps.mic.disabled");
            default -> Component.translatable("rocco.caps.mic.none");
        };
    }

    public static Component voiceStatus()
    {
        return switch (voice())
        {
            case SERVER -> Component.translatable("rocco.caps.voice.server");
            case OWN -> Component.translatable("rocco.caps.voice.own");
            default -> Component.translatable(allowOwnVoice() ? "rocco.caps.voice.none" : "rocco.caps.voice.disabled");
        };
    }

    private ClientCaps() {}
}

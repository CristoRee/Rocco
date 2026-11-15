package com.cristoree.rocco.client.setup;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoClientConfig;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.config.RoccoConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Cuando aparece la bienvenida: la primera vez que se llega al menu principal (o cuando una
// actualizacion trae algo nuevo para configurar), con /rocco setup y desde la config. Y el aviso
// chico al entrar a un mundo si Rocco todavia no tiene con que pensar, una vez por sesion.
@Mod.EventBusSubscriber(modid = RoccoMod.MODID, value = Dist.CLIENT)
public final class RoccoSetup
{
    private static boolean shownThisSession;
    private static boolean toastShown;

    public static void open()
    {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new RoccoSetupScreen(minecraft.screen));
    }

    @SubscribeEvent
    static void onScreenOpening(ScreenEvent.Opening event)
    {
        if (shownThisSession || !(event.getNewScreen() instanceof TitleScreen title)
                || !RoccoClientConfig.SPEC.isLoaded() || !RoccoCommonConfig.SPEC.isLoaded())
        {
            return;
        }
        if (RoccoClientConfig.SETUP_COMPLETED.get()
                && RoccoClientConfig.SETUP_VERSION.get() >= RoccoClientConfig.CURRENT_SETUP_VERSION)
        {
            return;
        }
        shownThisSession = true;
        event.setNewScreen(new RoccoSetupScreen(title));
    }

    @SubscribeEvent
    static void onLogin(ClientPlayerNetworkEvent.LoggingIn event)
    {
        Minecraft minecraft = Minecraft.getInstance();
        // En un servidor ajeno la IA la configura el dueño: lo de este jugador no cuenta.
        if (toastShown || !minecraft.isLocalServer() || !RoccoCommonConfig.SPEC.isLoaded() || RoccoConfig.aiConfigured())
        {
            return;
        }
        toastShown = true;
        SystemToast.add(minecraft.getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                Component.translatable("rocco.toast.no_ai.title"), Component.translatable("rocco.toast.no_ai.text"));
    }

    private RoccoSetup() {}
}

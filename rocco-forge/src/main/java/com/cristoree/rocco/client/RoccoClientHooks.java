package com.cristoree.rocco.client;

import com.cristoree.rocco.client.config.RoccoConfigScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// Esta clase solo se carga en el cliente: el servidor dedicado no tiene las clases de interfaz.
public final class RoccoClientHooks
{
    // Habilita el boton "Config" de la lista de mods. Forge lo deja gris si el mod no
    // registra este punto de extension.
    public static void registerConfigScreen(FMLJavaModLoadingContext context)
    {
        context.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new RoccoConfigScreen(parent)));
    }

    private RoccoClientHooks() {}
}

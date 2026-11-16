package com.cristoree.rocco.client;

import com.cristoree.rocco.client.config.RoccoConfigScreen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// Esta clase solo se carga en el cliente: el servidor dedicado no tiene las clases de interfaz.
public final class RoccoClientHooks
{
    // Habilita el boton "Config" de la lista de mods. NeoForge lo deja gris si el mod no
    // registra este punto de extension.
    public static void registerConfigScreen(ModContainer container)
    {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (mod, parent) -> new RoccoConfigScreen(parent));
    }

    private RoccoClientHooks() {}
}

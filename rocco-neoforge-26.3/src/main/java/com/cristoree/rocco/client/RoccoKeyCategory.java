package com.cristoree.rocco.client;

import com.cristoree.rocco.RoccoMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

// La categoria "Rocco" de Opciones > Controles. Desde 1.21.9 es un objeto que se registra.
public final class RoccoKeyCategory
{
    public static final KeyMapping.Category ROCCO = new KeyMapping.Category(Identifier.fromNamespaceAndPath(RoccoMod.MODID, "rocco"));

    private RoccoKeyCategory() {}
}

package com.cristoree.rocco.client.voice;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

// La tecla del microfono. Aparece en Opciones > Controles > Asignaciones de teclas, en la
// categoria "Rocco", y se puede cambiar ahi como cualquier otra. Por defecto, la V.
public final class RoccoKeys
{
    public static final KeyMapping TALK = new KeyMapping("key.rocco.talk", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.rocco");

    private RoccoKeys() {}
}

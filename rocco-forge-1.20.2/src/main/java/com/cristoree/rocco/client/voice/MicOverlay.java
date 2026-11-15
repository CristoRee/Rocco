package com.cristoree.rocco.client.voice;

import com.cristoree.rocco.config.RoccoClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

// El cartelito de arriba a la izquierda: rojo mientras graba, verde mientras escucha.
public final class MicOverlay implements IGuiOverlay
{
    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height)
    {
        MicRecorder.State state = MicRecorder.state();
        Minecraft minecraft = Minecraft.getInstance();
        if (state == MicRecorder.State.IDLE || !RoccoClientConfig.SHOW_MIC_INDICATOR.get() || minecraft.options.hideGui)
        {
            return;
        }

        boolean recording = state == MicRecorder.State.RECORDING;
        String text = recording ? "Grabando para Rocco..." : "Rocco te escucha";
        int color = recording ? 0xFFFF5555 : 0xFF55FF55;

        // El punto late mientras graba.
        boolean on = !recording || (System.currentTimeMillis() / 400L) % 2L == 0L;
        int x = 6;
        int y = 6;
        graphics.fill(x - 2, y - 2, x + 14 + minecraft.font.width(text), y + 10, 0x88000000);
        if (on)
        {
            graphics.fill(x, y + 1, x + 6, y + 7, color);
        }
        graphics.drawString(minecraft.font, text, x + 10, y, 0xFFFFFFFF, false);
    }
}

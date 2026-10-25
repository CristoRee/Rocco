package com.cristoree.rocco.client.setup;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// Las filas de la pantalla de bienvenida: titulos, renglones de texto (los largos se parten en
// varias filas) y filas con un nombre a la izquierda y controles a la derecha.
public class SetupList extends ContainerObjectSelectionList<SetupList.Entry>
{
    private static final int ROW_HEIGHT = 22;
    private static final int MAX_ROW_WIDTH = 360;

    public SetupList(Minecraft minecraft, int width, int height, int top, int bottom)
    {
        super(minecraft, width, height, top, bottom, ROW_HEIGHT);
    }

    @Override
    public int getRowWidth()
    {
        return Math.min(MAX_ROW_WIDTH, this.width - 24);
    }

    @Override
    protected int getScrollbarPosition()
    {
        return this.width / 2 + getRowWidth() / 2 + 6;
    }

    public void heading(Component text)
    {
        addEntry(new Heading(text));
    }

    // Un texto que se parte en renglones del ancho de la fila; cada renglon es una fila.
    public void text(Component text)
    {
        Font font = Minecraft.getInstance().font;
        for (FormattedCharSequence line : font.split(text, getRowWidth() - 8))
        {
            addEntry(new Line(() -> line));
        }
    }

    // Un renglon que cambia mientras la pantalla esta abierta (el resultado de una prueba).
    public void live(Supplier<Component> text)
    {
        addEntry(new Line(() ->
        {
            Font font = Minecraft.getInstance().font;
            List<FormattedCharSequence> lines = font.split(text.get(), getRowWidth() - 8);
            return lines.isEmpty() ? FormattedCharSequence.EMPTY : lines.get(0);
        }));
    }

    // Un nombre a la izquierda y los controles a la derecha, en el orden dado.
    public void row(Component label, AbstractWidget... widgets)
    {
        addEntry(new Controls(label, List.of(widgets)));
    }

    public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry>
    {
    }

    static final class Heading extends Entry
    {
        private final Component text;

        Heading(Component text)
        {
            this.text = text.copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick)
        {
            Font font = Minecraft.getInstance().font;
            graphics.drawString(font, this.text, left + 2, top + height - 12, 0xFFFFFF);
            graphics.fill(left + 2, top + height - 2, left + width - 2, top + height - 1, 0x40FFFFFF);
        }

        @Override
        public List<? extends GuiEventListener> children()
        {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables()
        {
            return List.of();
        }
    }

    static final class Line extends Entry
    {
        private final Supplier<FormattedCharSequence> text;

        Line(Supplier<FormattedCharSequence> text)
        {
            this.text = text;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick)
        {
            graphics.drawString(Minecraft.getInstance().font, this.text.get(), left + 4, top + 7, 0xD0D0D0);
        }

        @Override
        public List<? extends GuiEventListener> children()
        {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables()
        {
            return List.of();
        }
    }

    static final class Controls extends Entry
    {
        private final Component label;
        private final List<AbstractWidget> widgets;

        Controls(Component label, List<AbstractWidget> widgets)
        {
            this.label = label;
            this.widgets = new ArrayList<>(widgets);
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick)
        {
            Font font = Minecraft.getInstance().font;
            int x = left + width - 2;
            for (int i = this.widgets.size() - 1; i >= 0; i--)
            {
                AbstractWidget widget = this.widgets.get(i);
                x -= widget.getWidth();
                widget.setX(x);
                widget.setY(top + 1);
                widget.render(graphics, mouseX, mouseY, partialTick);
                x -= 4;
            }
            int room = x - left - 6;
            String text = this.label.getString();
            if (font.width(text) > room)
            {
                text = font.plainSubstrByWidth(text, Math.max(0, room - font.width("..."))) + "...";
            }
            graphics.drawString(font, text, left + 4, top + 6, 0xFFFFFF);
        }

        @Override
        public List<? extends GuiEventListener> children()
        {
            return this.widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables()
        {
            return this.widgets;
        }
    }
}

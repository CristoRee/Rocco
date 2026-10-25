package com.cristoree.rocco.client.config;

import com.cristoree.rocco.client.config.RoccoConfigCategories.Category;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// Las opciones de un solo tema. Al volver (boton o Esc) se aplican y se guardan.
public class RoccoCategoryScreen extends Screen
{
    private final Screen parent;
    private final Category category;
    private RoccoOptionList list;

    public RoccoCategoryScreen(Screen parent, Category category)
    {
        super(Component.literal(category.name()));
        this.parent = parent;
        this.category = category;
    }

    @Override
    protected void init()
    {
        // Si la ventana cambia de tamaño, init vuelve a correr: lo tocado no se pierde.
        if (this.list != null)
        {
            this.list.applyAll();
        }

        this.list = new RoccoOptionList(this.minecraft, this.width, this.height, 40, this.height - 36);
        this.list.show(this.category.groups(), Component.literal("Estas opciones todavía no están cargadas"));
        addRenderableWidget(this.list);

        addRenderableWidget(Button.builder(Component.literal("Volver"), b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), this.width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal(this.category.description()).withStyle(ChatFormatting.GRAY),
                this.width / 2, 26, 0xFFFFFF);
    }

    @Override
    public void onClose()
    {
        this.list.applyAll();
        RoccoConfigScreen.saveAll();
        this.minecraft.setScreen(this.parent);
    }
}

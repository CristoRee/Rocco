package com.cristoree.rocco.client.config;

import com.cristoree.rocco.config.RoccoClientConfig;
import com.cristoree.rocco.config.RoccoCommonConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RoccoConfigScreen extends Screen
{
    private final Screen parent;
    private RoccoOptionList list;

    public RoccoConfigScreen(Screen parent)
    {
        super(Component.literal("Rocco"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        this.list = new RoccoOptionList(this.minecraft, this.width, this.height, 44, this.height - 36);

        if (RoccoCommonConfig.SPEC.isLoaded())
        {
            this.list.addSpec(Component.literal("Juego, IA, voz y microfono"), RoccoCommonConfig.SPEC);
        }

        if (RoccoClientConfig.SPEC.isLoaded())
        {
            this.list.addSpec(Component.literal("Pantalla y audio (solo tu)"), RoccoClientConfig.SPEC);
        }

        addRenderableWidget(this.list);

        addRenderableWidget(Button.builder(Component.literal("Guardar"), b -> saveAndClose())
                .bounds(this.width / 2 - 154, this.height - 28, 150, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("Cancelar"), b -> onClose())
                .bounds(this.width / 2 + 4, this.height - 28, 150, 20)
                .build());
    }

    private void saveAndClose()
    {
        this.list.applyAll();

        if (RoccoCommonConfig.SPEC.isLoaded())
        {
            RoccoCommonConfig.SPEC.save();
        }
        if (RoccoClientConfig.SPEC.isLoaded())
        {
            RoccoClientConfig.SPEC.save();
        }

        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        boolean loaded = RoccoCommonConfig.SPEC.isLoaded();
        Component hint = loaded
                ? Component.literal("Pasa el mouse por una opcion para ver que hace")
                        .withStyle(ChatFormatting.GRAY)
                : Component.literal("La configuracion todavia no esta cargada")
                        .withStyle(ChatFormatting.RED);

        graphics.drawCenteredString(this.font, hint, this.width / 2, 28, 0xFFFFFF);
    }

    @Override
    public void onClose()
    {
        this.minecraft.setScreen(this.parent);
    }
}

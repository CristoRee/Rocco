package com.cristoree.rocco.client.config;

import com.cristoree.rocco.client.config.RoccoConfigCategories.Category;
import com.cristoree.rocco.client.voice.MicRecorder;
import com.cristoree.rocco.voice.Deepgram;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// Las opciones de un solo tema. Al volver (boton o Esc) se aplican y se guardan.
public class RoccoCategoryScreen extends Screen
{
    private static final String MIC_CATEGORY = "Micrófono";

    private final Screen parent;
    private final Category category;
    private RoccoOptionList list;
    private Component micResult = Component.empty();

    public RoccoCategoryScreen(Screen parent, Category category)
    {
        super(com.cristoree.rocco.client.Tr.t(category.name()));
        this.parent = parent;
        this.category = category;
    }

    private boolean mic()
    {
        return MIC_CATEGORY.equals(this.category.name());
    }

    @Override
    protected void init()
    {
        // Si la ventana cambia de tamaño, init vuelve a correr: lo tocado no se pierde.
        if (this.list != null)
        {
            this.list.applyAll();
        }

        // El tema del microfono tiene ademas su prueba, con el resultado arriba de los botones.
        this.list = new RoccoOptionList(this.minecraft, this.width, this.height, 40, this.height - (mic() ? 48 : 36));
        this.list.show(this.category.groups(), com.cristoree.rocco.client.Tr.t("Estas opciones todavía no están cargadas"));
        addRenderableWidget(this.list);

        Component back = com.cristoree.rocco.client.Tr.t("Volver");
        if (mic())
        {
            addRenderableWidget(Button.builder(Component.translatable("rocco.setup.mic.test"), b -> testMic())
                    .bounds(this.width / 2 - 152, this.height - 28, 150, 20)
                    .build());
            addRenderableWidget(Button.builder(back, b -> onClose())
                    .bounds(this.width / 2 + 2, this.height - 28, 150, 20)
                    .build());
        }
        else
        {
            addRenderableWidget(Button.builder(back, b -> onClose())
                    .bounds(this.width / 2 - 100, this.height - 28, 200, 20)
                    .build());
        }
    }

    // Graba 4 segundos y muestra lo que entendio. En segundo plano: no congela el juego.
    private void testMic()
    {
        this.list.applyAll();
        this.micResult = Component.translatable("rocco.setup.mic.recording").withStyle(ChatFormatting.GOLD);
        MicRecorder.recordSample(4)
                .thenApply(wav ->
                {
                    this.minecraft.execute(() -> this.micResult = Component.translatable("rocco.setup.testing").withStyle(ChatFormatting.GRAY));
                    return Deepgram.transcribe(wav);
                })
                .whenComplete((result, error) -> this.minecraft.execute(() ->
                {
                    if (error != null)
                    {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        this.micResult = Component.translatable("rocco.setup.mic.no_mic", String.valueOf(cause.getMessage()))
                                .withStyle(ChatFormatting.RED);
                    }
                    else if (result.text() == null)
                    {
                        this.micResult = Component.literal(String.valueOf(result.error())).withStyle(ChatFormatting.RED);
                    }
                    else if (result.text().isBlank())
                    {
                        this.micResult = Component.translatable("rocco.setup.mic.silence").withStyle(ChatFormatting.GOLD);
                    }
                    else
                    {
                        this.micResult = Component.translatable("rocco.setup.mic.heard", result.text()).withStyle(ChatFormatting.GREEN);
                    }
                }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), this.width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font, com.cristoree.rocco.client.Tr.t(this.category.description()).withStyle(ChatFormatting.GRAY),
                this.width / 2, 26, 0xFFFFFF);
        if (mic())
        {
            graphics.drawCenteredString(this.font, this.micResult, this.width / 2, this.height - 42, 0xFFFFFF);
        }
    }

    @Override
    public void onClose()
    {
        this.list.applyAll();
        RoccoConfigScreen.saveAll();
        this.minecraft.setScreen(this.parent);
    }
}

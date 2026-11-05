package com.cristoree.rocco.client.menu;

import com.cristoree.rocco.voice.RoccoNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

// Los lugares que Rocco recuerda ("esta es mi casa"): uno por fila, con "Llevame" y "Olvidar".
// Abajo, para guardar donde estas parado con un nombre. De a seis por pagina.
public class RoccoPlacesScreen extends Screen
{
    private static final int PAD = RoccoMenuScreen.PAD;
    private static final int GAP = RoccoMenuScreen.GAP;
    private static final int ROW = RoccoMenuScreen.ROW;
    private static final int PER_PAGE = 6;
    private static final int WIDTH = 260;

    private final Screen parent;
    private final int entityId;
    private final List<String> places;
    private int page;
    private int left;
    private int top;
    private int panelHeight;
    private EditBox name;
    private String typed = "";

    RoccoPlacesScreen(Screen parent, int entityId, List<String> places)
    {
        super(com.cristoree.rocco.client.Tr.t("Lugares"));
        this.parent = parent;
        this.entityId = entityId;
        this.places = new ArrayList<>(places);
    }

    private int pages()
    {
        return Math.max(1, (this.places.size() + PER_PAGE - 1) / PER_PAGE);
    }

    @Override
    protected void init()
    {
        this.page = Math.min(this.page, pages() - 1);
        this.panelHeight = 30 + PER_PAGE * (ROW + GAP) + 12 + ROW + GAP + ROW + PAD;
        this.left = (this.width - WIDTH) / 2;
        this.top = Math.max(4, (this.height - this.panelHeight) / 2);

        int rowsTop = this.top + 30;
        int right = this.left + WIDTH - PAD;
        int from = this.page * PER_PAGE;
        for (int i = from; i < Math.min(this.places.size(), from + PER_PAGE); i++)
        {
            String place = this.places.get(i);
            int y = rowsTop + (i - from) * (ROW + GAP);
            addRenderableWidget(Button.builder(com.cristoree.rocco.client.Tr.t("Olvidar"), b -> forget(place))
                    .bounds(right - 54, y, 54, ROW)
                    .tooltip(Tooltip.create(com.cristoree.rocco.client.Tr.t("Se olvida de \"%s\".", place)))
                    .build());
            addRenderableWidget(Button.builder(com.cristoree.rocco.client.Tr.t("Llevame"), b -> send("ir_a", place))
                    .bounds(right - 54 - GAP - 64, y, 64, ROW)
                    .tooltip(Tooltip.create(com.cristoree.rocco.client.Tr.t("Te guía hasta \"%s\".", place)))
                    .build());
        }

        int pagerY = rowsTop + PER_PAGE * (ROW + GAP);
        if (pages() > 1)
        {
            Button previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1))
                    .bounds(this.left + WIDTH / 2 - 44, pagerY, 20, 12).build());
            Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1))
                    .bounds(this.left + WIDTH / 2 + 24, pagerY, 20, 12).build());
            previous.active = this.page > 0;
            next.active = this.page < pages() - 1;
        }

        int saveY = pagerY + 12 + GAP;
        this.name = new EditBox(this.font, this.left + PAD + 1, saveY + 1, WIDTH - 2 * PAD - 92 - GAP - 2, ROW - 2,
                com.cristoree.rocco.client.Tr.t("Nombre del lugar"));
        this.name.setMaxLength(30);
        this.name.setHint(com.cristoree.rocco.client.Tr.t("Nombre (casa, mina...)").withStyle(ChatFormatting.DARK_GRAY));
        this.name.setValue(this.typed);
        this.name.setResponder(text -> this.typed = text);
        addRenderableWidget(this.name);
        Button save = addRenderableWidget(Button.builder(com.cristoree.rocco.client.Tr.t("Guardar acá"), b -> save())
                .bounds(right - 92, saveY, 92, ROW)
                .tooltip(Tooltip.create(com.cristoree.rocco.client.Tr.t("Recuerda el lugar donde estás parado con ese nombre.")))
                .build());
        save.active = this.places.size() < 32;

        addRenderableWidget(Button.builder(com.cristoree.rocco.client.Tr.t("Volver"), b -> onClose())
                .bounds(this.left + WIDTH / 2 - 50, saveY + ROW + GAP, 100, ROW)
                .build());
    }

    private void turn(int delta)
    {
        this.page = Math.max(0, Math.min(pages() - 1, this.page + delta));
        rebuildWidgets();
    }

    private void forget(String place)
    {
        RoccoNetwork.sendMenuAction(new RoccoNetwork.MenuAction(this.entityId, "olvidar_lugar", place));
        this.places.remove(place);
        rebuildWidgets();
    }

    private void save()
    {
        String place = this.typed.trim();
        if (!place.isEmpty())
        {
            send("recordar_lugar", place);
        }
    }

    private void send(String action, String place)
    {
        RoccoNetwork.sendMenuAction(new RoccoNetwork.MenuAction(this.entityId, action, place));
        this.minecraft.setScreen(null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);
        RoccoMenuScreen.panel(graphics, this.left, this.top, WIDTH, this.panelHeight);
        graphics.drawString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), this.left + PAD, this.top + 10, 0xFFFFFF);
        Component count = Component.literal(this.places.size() + " de 32").withStyle(ChatFormatting.GRAY);
        graphics.drawString(this.font, count, this.left + WIDTH - PAD - this.font.width(count), this.top + 10, 0xFFFFFF);

        int rowsTop = this.top + 30;
        if (this.places.isEmpty())
        {
            graphics.drawCenteredString(this.font, com.cristoree.rocco.client.Tr.t("Todavía no recuerda ningún lugar.").withStyle(ChatFormatting.GRAY),
                    this.left + WIDTH / 2, rowsTop + 8, 0xFFFFFF);
            graphics.drawCenteredString(this.font, com.cristoree.rocco.client.Tr.t("Escribí un nombre abajo y guardá donde estás.")
                    .withStyle(ChatFormatting.DARK_GRAY), this.left + WIDTH / 2, rowsTop + 20, 0xFFFFFF);
        }
        int from = this.page * PER_PAGE;
        int room = WIDTH - 2 * PAD - 54 - GAP - 64 - GAP;
        for (int i = from; i < Math.min(this.places.size(), from + PER_PAGE); i++)
        {
            String place = this.places.get(i);
            if (this.font.width(place) > room)
            {
                place = this.font.plainSubstrByWidth(place, room - this.font.width("...")) + "...";
            }
            graphics.drawString(this.font, place, this.left + PAD + 2, rowsTop + (i - from) * (ROW + GAP) + 6, 0xFFFFFF);
        }
        if (pages() > 1)
        {
            graphics.drawCenteredString(this.font, Component.literal((this.page + 1) + "/" + pages()).withStyle(ChatFormatting.GRAY),
                    this.left + WIDTH / 2, rowsTop + PER_PAGE * (ROW + GAP) + 2, 0xFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // Enter en el nombre guarda el lugar.
    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers)
    {
        if ((key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) && this.name.isFocused())
        {
            save();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    @Override
    public void onClose()
    {
        this.minecraft.setScreen(this.parent);
    }
}

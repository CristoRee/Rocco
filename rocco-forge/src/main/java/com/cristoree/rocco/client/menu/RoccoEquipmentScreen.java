package com.cristoree.rocco.client.menu;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.menu.RoccoEquipmentMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

// La pantalla del equipo de Rocco, dibujada a mano con el estilo de los inventarios del juego:
// sus lugares a la izquierda, el mismo Rocco en el medio (te sigue con la mirada) y tu inventario.
public class RoccoEquipmentScreen extends AbstractContainerScreen<RoccoEquipmentMenu>
{
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;

    // Que puede usar solo: comida comun, pociones y manzanas doradas (los bits de RoccoFood).
    private static final String[] PERM_LABELS = {"Comer solo", "Pociones", "Manzanas"};
    private static final String[] PERM_TIPS = {
            "Come solo de su inventario cuando le falta vida (la comida que más lo sacia).",
            "Toma pociones solo: curación si le falta mucha vida (también peleando), regeneración si está tranquilo y resistencia al fuego si se quema.",
            "Se come manzanas doradas en apuros, y la encantada solo si no le queda otra."};
    private final Button[] perms = new Button[3];

    public RoccoEquipmentScreen(RoccoEquipmentMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 98;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init()
    {
        super.init();
        for (int i = 0; i < this.perms.length; i++)
        {
            int id = i;
            this.perms[i] = addRenderableWidget(Button.builder(Component.empty(),
                            b -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id))
                    .bounds(this.leftPos + 98, this.topPos + 18 + i * 19, 72, 17)
                    .tooltip(Tooltip.create(Component.literal(PERM_TIPS[i] + " Lo que le pidas por chat lo hace igual.")))
                    .build());
        }
        // Que se vean con su texto desde el primer cuadro.
        containerTick();
    }

    @Override
    protected void containerTick()
    {
        super.containerTick();
        int allowed = this.menu.consumePerms();
        for (int i = 0; i < this.perms.length; i++)
        {
            boolean on = (allowed & (1 << i)) != 0;
            this.perms[i].setMessage(Component.literal((on ? "✔ " : "✘ ") + PERM_LABELS[i])
                    .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        int x = this.leftPos;
        int y = this.topPos;

        // El panel con el borde biselado de los inventarios.
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
        graphics.fill(x, y, x + this.imageWidth - 1, y + 1, LIGHT);
        graphics.fill(x, y, x + 1, y + this.imageHeight - 1, LIGHT);
        graphics.fill(x + 1, y + this.imageHeight - 1, x + this.imageWidth, y + this.imageHeight, SHADOW);
        graphics.fill(x + this.imageWidth - 1, y + 1, x + this.imageWidth, y + this.imageHeight, SHADOW);

        for (Slot slot : this.menu.slots)
        {
            drawSlot(graphics, x + slot.x - 1, y + slot.y - 1);
        }

        // La ventanita con Rocco, como la del jugador en su inventario.
        int boxLeft = x + 26;
        int boxTop = y + 8;
        graphics.fill(boxLeft, boxTop, boxLeft + 49, boxTop + 70, 0xFF000000);
        graphics.fill(boxLeft, boxTop, boxLeft + 48, boxTop + 1, SLOT_DARK);
        graphics.fill(boxLeft, boxTop, boxLeft + 1, boxTop + 69, SLOT_DARK);
        RoccoEntity rocco = this.menu.rocco();
        if (rocco != null)
        {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, boxLeft + 25, boxTop + 62, 42,
                    (float) (boxLeft + 25 - mouseX), (float) (boxTop + 25 - mouseY), rocco);
        }
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y)
    {
        graphics.fill(x, y, x + 18, y + 18, SLOT_FILL);
        graphics.fill(x, y, x + 17, y + 1, SLOT_DARK);
        graphics.fill(x, y, x + 1, y + 17, SLOT_DARK);
        graphics.fill(x + 1, y + 17, x + 18, y + 18, LIGHT);
        graphics.fill(x + 17, y + 1, x + 18, y + 18, LIGHT);
    }
}

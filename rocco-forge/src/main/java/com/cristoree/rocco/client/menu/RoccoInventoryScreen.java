package com.cristoree.rocco.client.menu;

import com.cristoree.rocco.item.BackpackItem;
import com.cristoree.rocco.menu.RoccoInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

// Su inventario, con la misma ventana de los cofres. Si tiene mas de una pagina, arriba van los
// botones para pasar de una a otra. Los lugares que esa pagina todavia no tiene se ven apagados.
public class RoccoInventoryScreen extends AbstractContainerScreen<RoccoInventoryMenu>
{
    private static final ResourceLocation WINDOW = new ResourceLocation("textures/gui/container/generic_54.png");
    private static final int TAB_WIDTH = 22;
    private static final int TAB_HEIGHT = 16;
    private static final int LOCKED = 0xC8302A24;

    private final List<Button> tabs = new ArrayList<>();

    public RoccoInventoryScreen(RoccoInventoryMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        this.imageHeight = 114 + menu.rows() * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init()
    {
        super.init();
        this.tabs.clear();
        int pages = this.menu.pages();
        if (pages < 2)
        {
            return;
        }
        for (int i = 0; i < pages; i++)
        {
            int page = i;
            Button tab = Button.builder(Component.literal(String.valueOf(i + 1)),
                            b -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, page))
                    .bounds(this.leftPos + this.imageWidth - (pages - i) * (TAB_WIDTH + 1) - 3, this.topPos - TAB_HEIGHT - 1, TAB_WIDTH, TAB_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable("rocco.inventory.page", i + 1, pages)))
                    .build();
            this.tabs.add(addRenderableWidget(tab));
        }
    }

    @Override
    protected void containerTick()
    {
        super.containerTick();
        // El boton de la pagina que se esta viendo queda apretado.
        int page = this.menu.page();
        for (int i = 0; i < this.tabs.size(); i++)
        {
            this.tabs.get(i).active = i != page;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        // La mochila abierta queda oscurecida: no se puede mover mientras este abierta.
        net.minecraft.world.inventory.Slot held = this.menu.heldSlot();
        if (held != null)
        {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 300.0F);
            int slotX = this.leftPos + held.x;
            int slotY = this.topPos + held.y;
            graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x80000000);
            graphics.pose().popPose();
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        int x = this.leftPos;
        int y = this.topPos;
        int rows = this.menu.rows();
        graphics.blit(WINDOW, x, y, 0, 0, this.imageWidth, rows * 18 + 17);
        graphics.blit(WINDOW, x, y + rows * 18 + 17, 0, 126, this.imageWidth, 96);

        // Lo que esta pagina no tiene: esos lugares todavia no existen.
        int first = this.menu.page() * BackpackItem.PAGE_SLOTS;
        for (int i = 0; i < rows * 9; i++)
        {
            if (first + i >= this.menu.capacity())
            {
                int slotX = x + 7 + (i % 9) * 18;
                int slotY = y + 17 + (i / 9) * 18;
                graphics.fill(slotX, slotY, slotX + 18, slotY + 18, LOCKED);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY)
    {
        super.renderLabels(graphics, mouseX, mouseY);
        int pages = this.menu.pages();
        if (pages > 1)
        {
            Component page = Component.translatable("rocco.inventory.page", this.menu.page() + 1, pages);
            graphics.drawString(this.font, page, this.imageWidth - 8 - this.font.width(page), this.titleLabelY, 0x404040, false);
        }
    }
}

package com.cristoree.rocco.client.menu;

import com.cristoree.rocco.client.Tr;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.item.BackpackItem;
import com.cristoree.rocco.menu.RoccoEquipmentMenu;
import com.cristoree.rocco.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

import java.util.List;

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

    // El panel de apariencia de la mochila: se abre al costado con el boton de arriba de las manos.
    private static final int LOOK_ROW = 18;
    private static final int LOOK_TOP = 18;
    private static final int LOOK_FOOT = 24;
    private static final int LOOK_PAD = 5;
    private boolean lookOpen;
    private boolean swallowRelease;
    private int lookX;
    private int lookY;
    private int lookWidth;
    private int lookHeight;

    public RoccoEquipmentScreen(RoccoEquipmentMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init()
    {
        super.init();
        layoutLook();
        for (int i = 0; i < this.perms.length; i++)
        {
            int id = i;
            this.perms[i] = addRenderableWidget(Button.builder(Component.empty(),
                            b -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id))
                    .bounds(this.leftPos + 98, this.topPos + 18 + i * 19, 72, 17)
                    .tooltip(Tooltip.create(com.cristoree.rocco.client.Tr.t(PERM_TIPS[i]).append(" ").append(com.cristoree.rocco.client.Tr.t("Lo que le pidas por chat lo hace igual."))))
                    .build());
        }
        addRenderableWidget(new LookButton(this.leftPos + 76, this.topPos + 7));
        // Que se vean con su texto desde el primer cuadro.
        containerTick();
    }

    // Al costado de la ventana, como el libro de recetas: si al abrirlo no hay lugar, la ventana se
    // corre. Solo en la pantalla mas angosta del juego se encima, y aun asi entra entero.
    private void layoutLook()
    {
        int textWidth = this.font.width(Tr.t("Su mochila real"));
        for (int tier = 1; tier <= BackpackItem.MAX_TIER; tier++)
        {
            textWidth = Math.max(textWidth, this.font.width(lookStack(tier).getHoverName()));
        }
        this.lookWidth = Math.max(textWidth + 16 + 12 + 2 * LOOK_PAD, this.font.width(Tr.t("Apariencia de la mochila")) + 2 * LOOK_PAD + 2);
        this.lookHeight = LOOK_TOP + (BackpackItem.MAX_TIER + 1) * LOOK_ROW + LOOK_FOOT;
        if (this.lookOpen && this.leftPos + this.imageWidth + 3 + this.lookWidth + 2 > this.width)
        {
            this.leftPos = Math.max(2, (this.width - this.imageWidth - 3 - this.lookWidth) / 2);
        }
        this.lookX = Math.min(this.leftPos + this.imageWidth + 3, this.width - this.lookWidth - 2);
        this.lookY = Math.max(2, Math.min(this.topPos, this.height - this.lookHeight - 2));
    }

    private static ItemStack lookStack(int tier)
    {
        return new ItemStack(switch (tier)
        {
            case 1 -> ModItems.MOCHILA_1.get();
            case 2 -> ModItems.MOCHILA_2.get();
            case 3 -> ModItems.MOCHILA_3.get();
            case 4 -> ModItems.MOCHILA_4.get();
            case 5 -> ModItems.MOCHILA_5.get();
            default -> ModItems.MOCHILA_6.get();
        });
    }

    private boolean insideLook(double mouseX, double mouseY)
    {
        return this.lookOpen && mouseX >= this.lookX && mouseX < this.lookX + this.lookWidth
                && mouseY >= this.lookY && mouseY < this.lookY + this.lookHeight;
    }

    // La fila bajo el mouse: 0 es la real, 1 a 6 las apariencias, -1 ninguna.
    private int lookRowAt(double mouseX, double mouseY)
    {
        if (!insideLook(mouseX, mouseY))
        {
            return -1;
        }
        int row = (int) Math.floor((mouseY - this.lookY - LOOK_TOP) / LOOK_ROW);
        return row >= 0 && row <= BackpackItem.MAX_TIER ? row : -1;
    }

    private void pickLook(int look)
    {
        RoccoEntity rocco = this.menu.rocco();
        // Tocar la que ya esta elegida la desmarca: vuelve a verse la real.
        int chosen = rocco != null && rocco.backpackLook() == look ? 0 : look;
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, RoccoEquipmentMenu.LOOK_BUTTON + chosen);
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (insideLook(mouseX, mouseY))
        {
            int row = lookRowAt(mouseX, mouseY);
            if (row >= 0 && button == 0)
            {
                pickLook(row);
            }
            this.swallowRelease = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // El clic ya se uso en el panel: soltar el boton encima no tiene que dejar caer lo que tengas agarrado.
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        if (this.swallowRelease)
        {
            this.swallowRelease = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY)
    {
        return this.swallowRelease || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    // Esc cierra primero el panel, despues la ventana.
    @Override
    public boolean keyPressed(int key, int scan, int modifiers)
    {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && this.lookOpen)
        {
            toggleLook(false);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private void toggleLook(boolean open)
    {
        this.lookOpen = open;
        // La ventana puede correrse para dejarle lugar: los botones se acomodan con ella.
        rebuildWidgets();
    }

    private void renderLook(GuiGraphics graphics, int mouseX, int mouseY)
    {
        RoccoEntity rocco = this.menu.rocco();
        int look = rocco == null ? 0 : rocco.backpackLook();
        int real = rocco == null ? 0 : rocco.backpackTier();
        int hover = lookRowAt(mouseX, mouseY);
        int x = this.lookX;
        int y = this.lookY;

        // Por encima de los objetos de la ventana, por si en una pantalla angosta queda encimado.
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        drawPanel(graphics, x, y, this.lookWidth, this.lookHeight);
        graphics.drawString(this.font, Tr.t("Apariencia de la mochila"), x + LOOK_PAD + 1, y + 6, 0x404040, false);

        for (int row = 0; row <= BackpackItem.MAX_TIER; row++)
        {
            int rowY = y + LOOK_TOP + row * LOOK_ROW;
            boolean selected = row == look;
            if (selected)
            {
                graphics.fill(x + 3, rowY, x + this.lookWidth - 3, rowY + LOOK_ROW, 0xFF9FB48A);
            }
            else if (row == hover)
            {
                graphics.fill(x + 3, rowY, x + this.lookWidth - 3, rowY + LOOK_ROW, 0xFFB4B4B4);
            }
            ItemStack icon = row > 0 ? lookStack(row) : real > 0 ? lookStack(real) : ItemStack.EMPTY;
            if (!icon.isEmpty())
            {
                graphics.renderItem(icon, x + LOOK_PAD, rowY + 1);
            }
            Component name = row > 0 ? lookStack(row).getHoverName() : Tr.t("Su mochila real");
            graphics.drawString(this.font, name, x + LOOK_PAD + 19, rowY + 5, selected ? 0x1E3A12 : 0x404040, false);
            if (selected)
            {
                graphics.drawString(this.font, "✔", x + this.lookWidth - LOOK_PAD - 7, rowY + 5, 0x1E3A12, false);
            }
        }

        // Lo unico que hay que saber, en dos renglones.
        Component note = Tr.t(real > 0 ? "Solo cambia cómo se ve, no su capacidad."
                : "Se va a ver cuando se ponga una mochila.");
        List<FormattedCharSequence> lines = this.font.split(note, this.lookWidth - 2 * LOOK_PAD);
        int noteY = y + this.lookHeight - LOOK_FOOT + 3;
        for (int i = 0; i < Math.min(2, lines.size()); i++)
        {
            graphics.drawString(this.font, lines.get(i), x + LOOK_PAD, noteY + i * 10, 0x555555, false);
        }
        graphics.pose().popPose();
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height)
    {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.fill(x, y, x + width - 1, y + 1, LIGHT);
        graphics.fill(x, y, x + 1, y + height - 1, LIGHT);
        graphics.fill(x + 1, y + height - 1, x + width, y + height, SHADOW);
        graphics.fill(x + width - 1, y + 1, x + width, y + height, SHADOW);
    }

    // El boton: muestra la mochila que se esta viendo en Rocco.
    private final class LookButton extends AbstractButton
    {
        LookButton(int x, int y)
        {
            super(x, y, 18, 18, Tr.t("Apariencia de la mochila"));
            setTooltip(Tooltip.create(Tr.t("Apariencia de la mochila").append("\n")
                    .append(Tr.t("Elegí cómo se ve en Rocco, sin cambiar la mochila que tiene.").withStyle(ChatFormatting.GRAY))));
        }

        @Override
        public void onPress()
        {
            toggleLook(!RoccoEquipmentScreen.this.lookOpen);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
        {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            RoccoEntity rocco = RoccoEquipmentScreen.this.menu.rocco();
            int shown = rocco == null ? 0 : rocco.backpackLook() > 0 ? rocco.backpackLook() : rocco.backpackTier();
            graphics.renderItem(lookStack(shown == 0 ? 1 : shown), getX() + 1, getY() + 1);
            // Sin mochila ni apariencia: el icono queda apagado.
            if (shown == 0)
            {
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 200.0F);
                graphics.fill(getX() + 1, getY() + 1, getX() + 17, getY() + 17, 0x88000000);
                graphics.pose().popPose();
            }
        }

        // El texto no va: es solo el icono.
        @Override
        public void renderString(GuiGraphics graphics, net.minecraft.client.gui.Font font, int color)
        {
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output)
        {
            defaultButtonNarrationText(output);
        }
    }

    @Override
    protected void containerTick()
    {
        super.containerTick();
        int allowed = this.menu.consumePerms();
        for (int i = 0; i < this.perms.length; i++)
        {
            boolean on = (allowed & (1 << i)) != 0;
            this.perms[i].setMessage(Component.literal(on ? "✔ " : "✘ ").append(com.cristoree.rocco.client.Tr.t(PERM_LABELS[i]))
                    .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.lookOpen)
        {
            renderLook(graphics, mouseX, mouseY);
        }
        if (!insideLook(mouseX, mouseY))
        {
            renderTooltip(graphics, mouseX, mouseY);
        }
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
            if (this.lookOpen)
            {
                renderTurning(graphics, boxLeft + 25, boxTop + 62, rocco, partialTick);
            }
            else
            {
                InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, boxLeft + 25, boxTop + 62, 42,
                        (float) (boxLeft + 25 - mouseX), (float) (boxTop + 25 - mouseY), rocco);
            }
        }
    }

    // Con el panel abierto gira despacio, para que se vea la mochila en su espalda.
    private static void renderTurning(GuiGraphics graphics, int x, int y, RoccoEntity rocco, float partialTick)
    {
        float body = rocco.yBodyRot;
        float bodyOld = rocco.yBodyRotO;
        float yaw = rocco.getYRot();
        float yawOld = rocco.yRotO;
        float pitch = rocco.getXRot();
        float pitchOld = rocco.xRotO;
        float head = rocco.yHeadRot;
        float headOld = rocco.yHeadRotO;
        float angle = ((rocco.tickCount + partialTick) * 2.0F) % 360.0F;
        rocco.yBodyRot = angle;
        rocco.yBodyRotO = angle;
        rocco.setYRot(angle);
        rocco.yRotO = angle;
        rocco.setXRot(0.0F);
        rocco.xRotO = 0.0F;
        rocco.yHeadRot = angle;
        rocco.yHeadRotO = angle;
        InventoryScreen.renderEntityInInventory(graphics, x, y, 42,
                new Quaternionf().rotateZ((float) Math.PI), new Quaternionf(), rocco);
        rocco.yBodyRot = body;
        rocco.yBodyRotO = bodyOld;
        rocco.setYRot(yaw);
        rocco.yRotO = yawOld;
        rocco.setXRot(pitch);
        rocco.xRotO = pitchOld;
        rocco.yHeadRot = head;
        rocco.yHeadRotO = headOld;
    }

    // Corto y sin el rotulo "Inventario": no hay lugar, y el nombre ya se lee arriba de Rocco.
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY)
    {
        graphics.drawString(this.font, Component.translatable("rocco.menu.equipment.short"), 98, 6, 0x404040, false);
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

package com.cristoree.rocco.client.tutorial;

import com.cristoree.rocco.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

// La guia de Rocco: siete paginas cortas con lo mas importante (dos ideas y un consejo por pagina).
// Estilo: piedra oscura de Minecraft con un solo acento verde musgo (Rocco es planta).
// Todo el texto sale del idioma (rocco.tutorial.*), asi sirve en castellano e ingles.
public class RoccoTutorialScreen extends Screen
{
    private static final int PAGES = 7;
    private static final int LINES = 2;

    private static final int WIDTH = 360;
    private static final int HEIGHT = 214;
    private static final int BOX_WIDTH = 96;
    private static final int ROCCO_SCALE = 78;

    // Paleta: piedra oscura + musgo. Nada de colores saturados.
    private static final int PANEL = 0xF01C1E1F;
    private static final int PANEL_INNER = 0xFF232526;
    private static final int BEVEL_LIGHT = 0xFF4B4E50;
    private static final int BEVEL_DARK = 0xFF0C0D0D;
    private static final int INSET = 0xFF151617;
    private static final int FLOOR = 0xFF1A1C1B;
    private static final int MOSS = 0xFF8DB562;
    private static final int MOSS_DIM = 0xFF4E6637;
    private static final int DOT = 0xFF3A3D3E;
    private static final int TEXT = 0xFFE4E4DE;
    private static final int TEXT_SOFT = 0xFFA9ABA3;

    // Rocco dibujado en vivo, si esta cerca. Si no, la vitrina muestra el icono de la pagina en grande.
    @Nullable
    private final LivingEntity rocco;
    // A donde vuelve al cerrar: su menu, si se abrio desde ahi.
    @Nullable
    private final Screen parent;
    private int page;
    private Button prev;
    private Button next;

    public RoccoTutorialScreen(@Nullable LivingEntity rocco, @Nullable Screen parent)
    {
        super(Component.translatable("rocco.tutorial.title"));
        this.rocco = rocco;
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;
        int by = top + HEIGHT - 28;

        this.prev = addRenderableWidget(Button.builder(Component.translatable("rocco.tutorial.prev"), b -> go(-1))
                .bounds(left + 12, by, 80, 20).build());
        this.next = addRenderableWidget(Button.builder(Component.translatable("rocco.tutorial.next"), b -> go(1))
                .bounds(left + WIDTH - 92, by, 80, 20).build());
        refreshButtons();
    }

    private void go(int delta)
    {
        int target = this.page + delta;
        if (target >= PAGES)
        {
            onClose();
            return;
        }
        this.page = Math.max(0, target);
        refreshButtons();
    }

    private void refreshButtons()
    {
        this.prev.active = this.page > 0;
        this.next.setMessage(Component.translatable(this.page == PAGES - 1 ? "rocco.tutorial.done" : "rocco.tutorial.next"));
    }

    @Override
    public void onClose()
    {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods)
    {
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D)
        {
            go(1);
            return true;
        }
        if ((key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) && this.page > 0)
        {
            go(-1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // No pausa: Rocco sigue moviendose en la vitrina.
    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial)
    {
        renderBackground(g);

        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        // Marco biselado, como los de Minecraft.
        g.fill(left - 2, top - 2, left + WIDTH + 2, top + HEIGHT + 2, BEVEL_DARK);
        g.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, BEVEL_LIGHT);
        g.fill(left, top, left + WIDTH, top + HEIGHT, PANEL);
        g.fill(left + 1, top + 1, left + WIDTH - 1, top + HEIGHT - 1, PANEL_INNER);

        // Titulo con una lineita musgo.
        g.drawString(this.font, this.title, left + 14, top + 11, MOSS, false);
        String counter = (this.page + 1) + " / " + PAGES;
        g.drawString(this.font, counter, left + WIDTH - 14 - this.font.width(counter), top + 11, TEXT_SOFT, false);
        g.fill(left + 12, top + 24, left + WIDTH - 12, top + 25, MOSS_DIM);

        int boxX = left + 14;
        int boxY = top + 34;
        int boxH = HEIGHT - 34 - 36;
        renderShowcase(g, boxX, boxY, boxH, mouseX, mouseY);

        // El texto de la pagina, a la derecha.
        int tx = boxX + BOX_WIDTH + 14;
        int tw = left + WIDTH - 14 - tx;
        int ty = boxY + 2;
        String prefix = "rocco.tutorial.page." + (this.page + 1);

        Component pageTitle = Component.translatable(prefix + ".title");
        g.drawString(this.font, pageTitle, tx, ty, MOSS, false);
        g.fill(tx, ty + 11, tx + Math.min(tw, this.font.width(pageTitle)), ty + 12, MOSS_DIM);
        ty += 20;

        for (int i = 1; i <= LINES; i++)
        {
            // Un cuadradito musgo como viñeta.
            g.fill(tx, ty + 3, tx + 3, ty + 6, MOSS);
            for (FormattedCharSequence part : this.font.split(Component.translatable(prefix + ".line." + i), tw - 9))
            {
                g.drawString(this.font, part, tx + 9, ty, TEXT, false);
                ty += 10;
            }
            ty += 8;
        }

        // El consejo, apoyado abajo de todo: asi nunca se pisa con el texto de arriba.
        List<FormattedCharSequence> tip = this.font.split(Component.translatable(prefix + ".tip"), tw);
        int tipY = boxY + boxH - tip.size() * 10 + 1;
        g.fill(tx, tipY - 5, tx + tw, tipY - 4, MOSS_DIM);
        for (FormattedCharSequence part : tip)
        {
            g.drawString(this.font, part, tx, tipY, TEXT_SOFT, false);
            tipY += 10;
        }

        // Puntitos de pagina, al centro de abajo.
        int dotsW = PAGES * 10 - 4;
        int dx = left + (WIDTH - dotsW) / 2;
        int dy = top + HEIGHT - 21;
        for (int i = 0; i < PAGES; i++)
        {
            g.fill(dx + i * 10, dy, dx + i * 10 + 6, dy + 6, i == this.page ? MOSS : DOT);
        }

        super.render(g, mouseX, mouseY, partial);
    }

    private void renderShowcase(GuiGraphics g, int boxX, int boxY, int boxH, int mouseX, int mouseY)
    {
        g.fill(boxX - 1, boxY - 1, boxX + BOX_WIDTH + 1, boxY + boxH + 1, BEVEL_DARK);
        g.fill(boxX, boxY, boxX + BOX_WIDTH, boxY + boxH, INSET);
        g.fill(boxX, boxY + boxH - 14, boxX + BOX_WIDTH, boxY + boxH, FLOOR);

        ItemStack icon = iconFor(this.page);
        int centerX = boxX + BOX_WIDTH / 2;
        if (this.rocco != null && this.rocco.isAlive())
        {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, centerX, boxY + boxH - 18, ROCCO_SCALE,
                    centerX - mouseX, (boxY + 40) - mouseY, this.rocco);
            g.renderItem(icon, boxX + BOX_WIDTH - 20, boxY + 4);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(centerX - 24.0F, boxY + (boxH - 48) / 2.0F, 0.0F);
        g.pose().scale(3.0F, 3.0F, 1.0F);
        g.renderItem(icon, 0, 0);
        g.pose().popPose();
    }

    private static ItemStack iconFor(int page)
    {
        return switch (page)
        {
            case 0 -> new ItemStack(ModItems.ROCCO_SPAWN_EGG.get());
            case 1 -> new ItemStack(Items.WRITABLE_BOOK);
            case 2 -> new ItemStack(Items.COMPASS);
            case 3 -> new ItemStack(Items.CHEST);
            case 4 -> new ItemStack(ModItems.MOCHILA_1.get());
            case 5 -> new ItemStack(Items.WHEAT);
            default -> new ItemStack(ModItems.ROCO_COLA.get());
        };
    }
}

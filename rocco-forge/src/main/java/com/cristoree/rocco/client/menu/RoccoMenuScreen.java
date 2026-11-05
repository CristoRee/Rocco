package com.cristoree.rocco.client.menu;

import com.cristoree.rocco.RoccoMenu;
import com.cristoree.rocco.client.config.RoccoConfigScreen;
import com.cristoree.rocco.client.config.RoccoPersonalityScreen;
import com.cristoree.rocco.client.voice.RoccoKeys;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.config.RoccoConfig;
import com.cristoree.rocco.voice.RoccoNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// El menu de Rocco (agachado + clic derecho). Arriba, Rocco con lo que esta haciendo, que tan
// amigos son y si tiene la magia lista. Abajo, tres columnas con lo de todos los dias:
// moverse, tareas y rato libre. Los botones que van y vienen (seguir / dejar de seguir) dicen lo
// que va a pasar ahora. No pausa el juego: Rocco contesta mientras tanto.
public class RoccoMenuScreen extends Screen
{
    static final int PAD = 10;
    static final int GAP = 4;
    static final int ROW = 20;
    private static final int HEADER = 56;
    private static final int LABEL = 12;

    static final int PANEL = 0xE0101418;
    static final int BORDER = 0xFF4A5560;
    static final int LINE = 0x40FFFFFF;

    private final RoccoNetwork.OpenMenu state;
    // Las casillas de a quien puede atacar: cambian sin cerrar el menu.
    private int targets;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int columnWidth;
    private int gridTop;

    public static void open(RoccoNetwork.OpenMenu state)
    {
        Minecraft.getInstance().setScreen(new RoccoMenuScreen(state));
    }

    private RoccoMenuScreen(RoccoNetwork.OpenMenu state)
    {
        super(Component.literal(RoccoCommonConfig.COMPANION_NAME.get()));
        this.state = state;
        this.targets = (has(RoccoMenu.TARGET_HOSTILE) ? com.cristoree.rocco.entity.RoccoCombat.HOSTILE : 0)
                | (has(RoccoMenu.TARGET_PEACEFUL) ? com.cristoree.rocco.entity.RoccoCombat.PEACEFUL : 0)
                | (has(RoccoMenu.TARGET_PLAYERS) ? com.cristoree.rocco.entity.RoccoCombat.PLAYERS : 0);
    }

    private boolean has(int flag)
    {
        return (this.state.flags() & flag) != 0;
    }

    private record Entry(String label, Item icon, String tip, @Nullable String action, boolean enabled, @Nullable Runnable open) {}

    private static Entry order(String label, Item icon, String tip, String action)
    {
        return new Entry(label, icon, tip, action, true, null);
    }

    @Override
    protected void init()
    {
        this.columnWidth = Mth.clamp((this.width - 2 * PAD - 2 * GAP - 16) / 3, 92, 118);
        this.panelWidth = 3 * this.columnWidth + 2 * GAP + 2 * PAD;
        this.panelHeight = HEADER + LABEL + 5 * ROW + 4 * GAP + 6 + LABEL + ROW + 10 + ROW + 18 + PAD;
        this.left = (this.width - this.panelWidth) / 2;
        this.top = Math.max(4, (this.height - this.panelHeight) / 2);
        this.gridTop = this.top + HEADER + LABEL;

        boolean travelling = has(RoccoMenu.TRAVELLING);
        String away = "Ahora no: está viajando con vos.";

        column(0, List.of(
                has(RoccoMenu.FOLLOWING)
                        ? order("Dejar de seguir", Items.LEAD, "Se queda esperando acá hasta que le digas que te siga.", "esperar")
                        : order("Seguime", Items.LEAD, "Vuelve a seguirte a todos lados.", "seguir"),
                order("Vení acá", Items.ENDER_PEARL, "Aparece al lado tuyo, esté donde esté, y te sigue.", "venir"),
                has(RoccoMenu.SITTING)
                        ? order("Levantate", Items.OAK_STAIRS, "Se para y te sigue.", "levantarse")
                        : new Entry("Sentate", Items.OAK_STAIRS, travelling ? away : "Se sienta acá y te espera.", "sentarse", !travelling, null),
                has(RoccoMenu.SLEEPING)
                        ? order("Despertate", Items.RED_BED, "Se despierta y te sigue.", "levantarse")
                        : new Entry("A dormir", Items.RED_BED, travelling ? away : "Se echa una siesta acá.", "dormir", !travelling, null),
                has(RoccoMenu.RIDING)
                        ? order("Bajate", Items.SADDLE, "Se baja de donde está montado.", "bajar")
                        : new Entry("Subite al lobo", Items.BONE, has(RoccoMenu.WOLF)
                                ? "Se sube a un lobo tuyo que tenga permiso."
                                : "Para darle permiso: agachado + clic derecho con la mano vacía a tu lobo, con Rocco cerca.",
                                "montar_lobo", has(RoccoMenu.WOLF) && !travelling, null)));

        column(1, List.of(
                new Entry("Su inventario", Items.CHEST, travelling ? away : "Abre su tapa. Con una mochila tiene más lugar (clic derecho con la mochila en la mano).",
                        "inventario", !travelling, null),
                order("Guardar todo", Items.BARREL, "Guarda lo que lleva encima en los cofres de alrededor, cada cosa en su lugar.", "guardar"),
                order("Ordenar cofres", Items.HOPPER, "Acomoda los cofres de alrededor: cada uno de un tema y todo agrupado.", "ordenar"),
                has(RoccoMenu.TENDING)
                        ? order("Dejar de cuidar", Items.WHEAT, "Deja de cuidar la casa y te sigue.", "seguir")
                        : order("Cuidar la casa", Items.WHEAT, "Se queda acá mientras explorás: cosecha y replanta, esquila, hace criar a los animales (sin pasarse del tope), junta lo que dejan y guarda todo en los cofres. Al volver te cuenta qué hizo.", "casa"),
                new Entry("Quitar mochila", com.cristoree.rocco.registry.ModItems.MOCHILA_1.get(), has(RoccoMenu.BACKPACK)
                        ? "Te la devuelve con lo que lleva adentro. Lo que va en sus lugares viaja con ella."
                        : "No tiene ninguna puesta.", "quitar_mochila", has(RoccoMenu.BACKPACK) && !travelling, null)));

        column(2, List.of(
                new Entry("Cebame un mate", Items.FLOWER_POT, travelling ? away : "Ceba y te pasa el mate. Lo tomás y se lo devolvés con clic derecho.", "cebar_mate", !travelling, null),
                new Entry("Bailá", Items.JUKEBOX, travelling ? away : "Se pone a bailar tarareando.", "bailar", !travelling, null),
                has(RoccoMenu.QUIET)
                        ? order("Charlá más", Items.BELL, "Vuelve a comentar cosas por su cuenta, y más seguido.", "charlar")
                        : order("Callate un rato", Items.BELL, "No habla por su cuenta por un buen rato. Si le hablás, contesta igual.", "callar"),
                new Entry("Lugares", Items.COMPASS, "Los lugares que recuerda: te lleva, guarda uno nuevo o se olvida de uno.",
                        null, true, () -> this.minecraft.setScreen(new RoccoPlacesScreen(this, this.state.entityId(), this.state.places()))),
                new Entry("Roco Cola", com.cristoree.rocco.registry.ModItems.ROCO_COLA.get(), travelling ? away : "Te da una Roco Cola bien helada. Cuidado con lo que viene después de tomarla.",
                        "dar_cola", !travelling, null)));

        // Pelea: su equipo y a quien puede atacar (tres casillas que se guardan en este Rocco).
        int fightTop = this.gridTop + 5 * ROW + 4 * GAP + 6 + LABEL;
        int quarter = (this.panelWidth - 2 * PAD - 3 * GAP) / 4;
        MenuButton gear = new MenuButton(this.left + PAD, fightTop, quarter, Component.literal("Equipo"),
                new ItemStack(Items.IRON_CHESTPLATE), b -> send("equipo"));
        gear.setTooltip(Tooltip.create(Component.literal(travelling ? away : "Su armadura, su arma y su escudo. Arrastrá las cosas desde tu inventario.")));
        gear.active = !travelling;
        addRenderableWidget(gear);
        target(this.left + PAD + quarter + GAP, fightTop, quarter, "Hostiles", Items.ZOMBIE_HEAD,
                com.cristoree.rocco.entity.RoccoCombat.HOSTILE, "Zombis, esqueletos, arañas y demás monstruos.");
        target(this.left + PAD + 2 * (quarter + GAP), fightTop, quarter, "Pacíficos", Items.WHEAT,
                com.cristoree.rocco.entity.RoccoCombat.PEACEFUL, "Animales, aldeanos, gólems y peces (también tus mascotas).");
        target(this.left + PAD + 3 * (quarter + GAP), fightTop, quarter, "Jugadores", Items.PLAYER_HEAD,
                com.cristoree.rocco.entity.RoccoCombat.PLAYERS, "Otros jugadores. A vos nunca te ataca.");

        // Si esta en medio de algo, al lado de lo que esta haciendo va el boton para cortarlo.
        if (has(RoccoMenu.BUSY))
        {
            Button cancel = Button.builder(Component.literal("Cancelar"), b -> send("parar"))
                    .bounds(this.left + this.panelWidth - PAD - 64, this.top + 22, 64, 16)
                    .tooltip(Tooltip.create(Component.literal("Deja lo que está haciendo.")))
                    .build();
            addRenderableWidget(cancel);
        }

        // La guia, en la esquina.
        net.minecraft.world.entity.Entity shown = this.minecraft.level == null ? null : this.minecraft.level.getEntity(this.state.entityId());
        addRenderableWidget(Button.builder(Component.literal("?"), b -> this.minecraft.setScreen(new com.cristoree.rocco.client.tutorial.RoccoTutorialScreen(
                        shown instanceof net.minecraft.world.entity.LivingEntity living ? living : null, this)))
                .bounds(this.left + this.panelWidth - PAD - 16, this.top + 4, 16, 16)
                .tooltip(Tooltip.create(Component.translatable("rocco.tutorial.title")))
                .build());

        int footer = this.top + this.panelHeight - PAD - 14 - ROW;
        int third = (this.panelWidth - 2 * PAD - 2 * GAP) / 3;
        addRenderableWidget(new MenuButton(this.left + PAD, footer, third, Component.literal("Personalidad"),
                new ItemStack(Items.POPPY), b -> this.minecraft.setScreen(new RoccoPersonalityScreen(this))));
        addRenderableWidget(new MenuButton(this.left + PAD + third + GAP, footer, third, Component.literal("Configuración"),
                new ItemStack(Items.COMPARATOR), b -> this.minecraft.setScreen(new RoccoConfigScreen(this))));
        addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> onClose())
                .bounds(this.left + PAD + 2 * (third + GAP), footer, third, ROW)
                .build());
    }

    private void column(int index, List<Entry> entries)
    {
        int x = this.left + PAD + index * (this.columnWidth + GAP);
        for (int i = 0; i < entries.size(); i++)
        {
            Entry entry = entries.get(i);
            Button button = new MenuButton(x, this.gridTop + i * (ROW + GAP), this.columnWidth, Component.literal(entry.label()),
                    new ItemStack(entry.icon()), b ->
            {
                if (entry.open() != null)
                {
                    entry.open().run();
                }
                else if (entry.action() != null)
                {
                    send(entry.action());
                }
            });
            button.setTooltip(Tooltip.create(Component.literal(entry.tip())));
            button.active = entry.enabled();
            addRenderableWidget(button);
        }
    }

    // Una casilla de "puede atacar a": verde si esta permitido. Se manda al servidor al tocarla.
    private void target(int x, int y, int width, String label, Item icon, int bit, String tip)
    {
        boolean on = (this.targets & bit) != 0;
        MenuButton button = new MenuButton(x, y, width,
                Component.literal((on ? "✔ " : "✘ ") + label).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                new ItemStack(icon), b ->
        {
            this.targets ^= bit;
            RoccoNetwork.sendMenuAction(new RoccoNetwork.MenuAction(this.state.entityId(), "objetivos", String.valueOf(this.targets)));
            rebuildWidgets();
        });
        button.setTooltip(Tooltip.create(Component.literal("Puede atacar a: " + tip + (on ? " (sí)" : " (no)"))));
        addRenderableWidget(button);
    }

    private void send(String action)
    {
        RoccoNetwork.sendMenuAction(new RoccoNetwork.MenuAction(this.state.entityId(), action, ""));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);
        panel(graphics, this.left, this.top, this.panelWidth, this.panelHeight);

        // Rocco en chiquito, mirando el mouse.
        if (this.minecraft.level != null && this.minecraft.level.getEntity(this.state.entityId()) instanceof LivingEntity rocco)
        {
            int x = this.left + PAD + 16;
            int y = this.top + 50;
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x, y, 22, x - mouseX, y - 30 - mouseY, rocco);
        }

        int textLeft = this.left + PAD + 40;
        int right = this.left + this.panelWidth - PAD;
        graphics.drawString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), textLeft, this.top + 10, 0xFFFFFF);
        Component mode = Component.literal(this.state.mode()).withStyle(ChatFormatting.AQUA);
        graphics.drawString(this.font, mode, right - 20 - this.font.width(mode), this.top + 10, 0xFFFFFF);

        graphics.drawString(this.font, Component.literal("● ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(this.state.status()).withStyle(ChatFormatting.WHITE)), textLeft, this.top + 26, 0xFFFFFF);

        // La amistad, con una barrita hasta la etapa siguiente, y la magia.
        int bondY = this.top + 40;
        Component bond = Component.literal(this.state.bond()).withStyle(ChatFormatting.GOLD);
        graphics.drawString(this.font, bond, textLeft, bondY, 0xFFFFFF);
        int barLeft = textLeft + this.font.width(bond) + 6;
        int barWidth = 48;
        graphics.fill(barLeft, bondY + 2, barLeft + barWidth, bondY + 6, 0xFF2A2F35);
        graphics.fill(barLeft, bondY + 2, barLeft + Math.round(barWidth * this.state.bondProgress()), bondY + 6, 0xFFE0B040);
        Component magic = Component.literal("Magia: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(this.state.magic()).withStyle(
                        this.state.magic().equals("lista") ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GRAY));
        graphics.drawString(this.font, magic, right - this.font.width(magic), bondY, 0xFFFFFF);

        // Su vida (corazones de hoja) y si sus cosas estan a salvo con el poder del cofre de ender.
        if (this.minecraft.level != null && this.minecraft.level.getEntity(this.state.entityId()) instanceof LivingEntity living)
        {
            Component life = Component.literal("♥ " + Math.round(living.getHealth()) + "/" + Math.round(living.getMaxHealth()))
                    .withStyle(ChatFormatting.GREEN);
            if (has(RoccoMenu.ENDER_POCKET))
            {
                life = life.copy().append(Component.literal("  ✦ cosas a salvo").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            int afterTitle = textLeft + this.font.width(this.title.copy().withStyle(ChatFormatting.BOLD)) + 8;
            graphics.drawString(this.font, life, afterTitle, this.top + 10, 0xFFFFFF);
        }

        int lineY = this.top + HEADER - 2;
        graphics.fill(this.left + PAD, lineY, right, lineY + 1, LINE);
        String[] sections = {"Movimiento", "Tareas", "Rato libre"};
        for (int i = 0; i < sections.length; i++)
        {
            graphics.drawString(this.font, Component.literal(sections[i]).withStyle(ChatFormatting.YELLOW),
                    this.left + PAD + i * (this.columnWidth + GAP) + 1, this.gridTop - LABEL + 1, 0xFFFFFF);
        }
        int fightLabel = this.gridTop + 5 * ROW + 4 * GAP + 6;
        graphics.drawString(this.font, Component.literal("Pelea").withStyle(ChatFormatting.YELLOW),
                this.left + PAD + 1, fightLabel + 1, 0xFFFFFF);
        Component canAttack = Component.literal("Puede atacar a:").withStyle(ChatFormatting.GRAY);
        int quarter = (this.panelWidth - 2 * PAD - 3 * GAP) / 4;
        graphics.drawString(this.font, canAttack, this.left + PAD + quarter + GAP + 1, fightLabel + 1, 0xFFFFFF);

        int footerLine = this.top + this.panelHeight - PAD - 14 - ROW - 6;
        graphics.fill(this.left + PAD, footerLine, right, footerLine + 1, LINE);
        graphics.drawCenteredString(this.font, talkHint(), this.left + this.panelWidth / 2,
                this.top + this.panelHeight - PAD - 9, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static Component talkHint()
    {
        String text = RoccoConfig.sttReady()
                ? "Para hablarle: escribí en el chat o mantené " + RoccoKeys.TALK.getTranslatedKeyMessage().getString() + "."
                : "Para hablarle: escribí en el chat.";
        return Component.literal(text).withStyle(ChatFormatting.DARK_GRAY);
    }

    static void panel(GuiGraphics graphics, int left, int top, int width, int height)
    {
        graphics.fill(left - 1, top - 1, left + width + 1, top + height + 1, BORDER);
        graphics.fill(left, top, left + width, top + height, PANEL);
    }

    // Con el juego andando: Rocco contesta y se mueve mientras el menu esta abierto.
    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    // Boton con el icono de un item a la izquierda y el texto al lado (recortado si no entra).
    static class MenuButton extends Button
    {
        private final ItemStack icon;

        MenuButton(int x, int y, int width, Component label, ItemStack icon, OnPress onPress)
        {
            super(x, y, width, ROW, label, onPress, DEFAULT_NARRATION);
            this.icon = icon;
        }

        @Override
        public void renderString(GuiGraphics graphics, Font font, int color)
        {
            graphics.renderItem(this.icon, getX() + 3, getY() + 2);
            int room = getWidth() - 26;
            String text = getMessage().getString();
            if (font.width(text) > room)
            {
                text = font.plainSubstrByWidth(text, room - font.width("...")) + "...";
            }
            graphics.drawString(font, text, getX() + 22, getY() + (getHeight() - 8) / 2, color);
        }
    }
}

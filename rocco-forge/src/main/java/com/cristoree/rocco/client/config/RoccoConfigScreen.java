package com.cristoree.rocco.client.config;

import com.cristoree.rocco.client.config.RoccoConfigCategories.Category;
import com.cristoree.rocco.client.config.RoccoConfigCategories.Group;
import com.cristoree.rocco.client.config.RoccoConfigCategories.Option;
import com.cristoree.rocco.client.setup.RoccoSetupScreen;
import com.cristoree.rocco.config.RoccoClientConfig;
import com.cristoree.rocco.config.RoccoCommonConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// La entrada a la configuracion: un boton por tema y un buscador que encuentra cualquier opcion
// de cualquier tema. Los cambios se guardan solos al salir, como en las opciones del juego.
public class RoccoConfigScreen extends Screen
{
    private static final int BUTTON_WIDTH = 124;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;
    private static final int SEARCH_TOP = 40;
    private static final int GRID_TOP = 66;
    private static final int SECTION_LABEL = 12;
    private static final int SECTION_GAP = 10;

    private int connectionsTop;
    private int settingsTop;
    private int sectionLeft;

    private final Screen parent;
    private final List<Button> categoryButtons = new ArrayList<>();
    private EditBox search;
    private RoccoOptionList results;
    private boolean showingResults;
    private String lastQuery = "";

    public RoccoConfigScreen(Screen parent)
    {
        super(Component.literal("Rocco"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        // Si la ventana cambia de tamaño, init vuelve a correr: lo tocado en la busqueda no se pierde.
        if (this.results != null && this.showingResults)
        {
            this.results.applyAll();
        }
        this.categoryButtons.clear();
        this.showingResults = false;

        this.search = new EditBox(this.font, this.width / 2 - 110, SEARCH_TOP, 220, 18, Component.literal("Buscar"));
        this.search.setHint(Component.literal("Buscar una opción... (clave, voz, seguir...)").withStyle(ChatFormatting.DARK_GRAY));
        this.search.setValue(this.lastQuery);
        this.search.setResponder(this::onSearch);
        addRenderableWidget(this.search);

        this.results = new RoccoOptionList(this.minecraft, this.width, this.height, GRID_TOP - 4, this.height - 36);
        // Sin las franjas de arriba y abajo: taparian el buscador y el boton Listo.
        this.results.setRenderTopAndBottom(false);

        // Dos bloques. Arriba "Conectar a Rocco": la IA, la voz y el microfono, juntos y con una
        // lucecita que dice si ya estan listos (es lo primero que hay que configurar). Abajo el
        // resto de los ajustes, con Personalidad despues de General, y al final las teclas y el
        // asistente de bienvenida.
        List<Entry> connections = new ArrayList<>();
        List<Entry> settings = new ArrayList<>();
        for (Category category : RoccoConfigCategories.all())
        {
            boolean available = category.groups().stream()
                    .flatMap(group -> group.options().stream())
                    .anyMatch(Option::loaded);
            Entry entry = new Entry(Component.literal(category.name()), Component.literal(category.description()),
                    category.icon(), () -> this.minecraft.setScreen(new RoccoCategoryScreen(this, category)), available,
                    category.ready());
            if (category.isConnection())
            {
                connections.add(entry);
                continue;
            }
            settings.add(entry);
            if (category.name().equals("General"))
            {
                settings.add(new Entry(Component.translatable("rocco.personality.title"),
                        Component.translatable("rocco.personality.button.tip"), () -> new ItemStack(Items.POPPY),
                        () -> this.minecraft.setScreen(new RoccoPersonalityScreen(this)), RoccoCommonConfig.SPEC.isLoaded(), null));
            }
        }
        settings.add(new Entry(Component.literal("Teclas"),
                Component.literal("La tecla para hablarle por micrófono (V por defecto) y las demás teclas del juego."),
                () -> new ItemStack(Items.LEVER),
                () -> this.minecraft.setScreen(new KeyBindsScreen(this, this.minecraft.options)), true, null));
        settings.add(new Entry(Component.translatable("rocco.setup.button"), Component.translatable("rocco.setup.button.tip"),
                () -> new ItemStack(Items.WHEAT_SEEDS),
                () -> this.minecraft.setScreen(new RoccoSetupScreen(this)), RoccoCommonConfig.SPEC.isLoaded(), null));
        // Por si se perdio: lo trae al lado tuyo esté donde esté (tambien hay una tecla en Teclas).
        boolean inWorld = this.minecraft.level != null && com.cristoree.rocco.client.RoccoCallKey.serverHasRocco();
        settings.add(new Entry(Component.translatable("rocco.call.button"), Component.translatable("rocco.call.button.tip"),
                () -> new ItemStack(Items.ENDER_PEARL),
                () ->
                {
                    com.cristoree.rocco.voice.RoccoNetwork.callRocco();
                    this.minecraft.setScreen(null);
                }, inWorld, null));

        int columns = this.width >= 3 * BUTTON_WIDTH + 2 * GAP + 20 ? 3 : 2;
        int rowWidth = columns * BUTTON_WIDTH + (columns - 1) * GAP;
        int left = (this.width - rowWidth) / 2;

        this.connectionsTop = GRID_TOP + SECTION_LABEL;
        int afterConnections = placeGrid(connections, left, this.connectionsTop, columns);
        this.settingsTop = afterConnections + SECTION_GAP + SECTION_LABEL;
        placeGrid(settings, left, this.settingsTop, columns);
        this.sectionLeft = left;

        addRenderableWidget(Button.builder(Component.literal("Listo"), b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20)
                .build());

        if (!this.lastQuery.isBlank())
        {
            onSearch(this.lastQuery);
        }
        setInitialFocus(this.search);
    }

    // Pone los botones en filas y devuelve donde termina la ultima.
    private int placeGrid(List<Entry> entries, int left, int top, int columns)
    {
        for (int i = 0; i < entries.size(); i++)
        {
            Entry entry = entries.get(i);
            int x = left + (i % columns) * (BUTTON_WIDTH + GAP);
            int y = top + (i / columns) * (BUTTON_HEIGHT + GAP);
            Button button = entry.ready() != null
                    ? new StatusButton(x, y, entry.name(), entry.icon(), entry.ready(), b -> entry.open().run())
                    : new IconButton(x, y, entry.name(), entry.icon(), b -> entry.open().run());
            button.setTooltip(Tooltip.create(entry.ready() == null ? entry.tip() : entry.tip().copy()
                    .append("\n\n").append(entry.ready().get()
                            ? Component.literal("Listo para usar.").withStyle(ChatFormatting.GREEN)
                            : Component.literal("Todavía sin configurar.").withStyle(ChatFormatting.GOLD))));
            button.active = entry.active();
            this.categoryButtons.add(addRenderableWidget(button));
        }
        int rows = (entries.size() + columns - 1) / columns;
        return top + rows * (BUTTON_HEIGHT + GAP) - GAP;
    }

    // Con texto en el buscador se esconden los temas y aparece la lista de lo que coincide.
    private void onSearch(String query)
    {
        this.lastQuery = query;
        boolean searching = !query.isBlank();

        if (searching)
        {
            List<Group> found = RoccoConfigCategories.search(query);
            this.results.show(found, Component.literal("Ninguna opción coincide con \"" + query.trim() + "\""));
            if (!this.showingResults)
            {
                addRenderableWidget(this.results);
                this.showingResults = true;
            }
        }
        else if (this.showingResults)
        {
            this.results.applyAll();
            removeWidget(this.results);
            this.showingResults = false;
        }

        for (Button button : this.categoryButtons)
        {
            button.visible = !searching;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), this.width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font, hint(), this.width / 2, 26, 0xFFFFFF);

        if (!this.showingResults)
        {
            graphics.drawString(this.font, Component.literal("Conectar a Rocco").withStyle(ChatFormatting.YELLOW),
                    this.sectionLeft, this.connectionsTop - SECTION_LABEL + 1, 0xFFFFFF);
            graphics.drawString(this.font, Component.literal("Ajustes").withStyle(ChatFormatting.GRAY),
                    this.sectionLeft, this.settingsTop - SECTION_LABEL + 1, 0xFFFFFF);
        }
    }

    private Component hint()
    {
        if (!RoccoCommonConfig.SPEC.isLoaded())
        {
            return Component.literal("La configuración todavía no está cargada").withStyle(ChatFormatting.RED);
        }
        return Component.literal("Elegí un tema o buscá una opción. Los cambios se guardan al salir.")
                .withStyle(ChatFormatting.GRAY);
    }

    @Override
    public void onClose()
    {
        if (this.showingResults)
        {
            this.results.applyAll();
        }
        saveAll();
        this.minecraft.setScreen(this.parent);
    }

    public static void saveAll()
    {
        if (RoccoCommonConfig.SPEC.isLoaded())
        {
            RoccoCommonConfig.SPEC.save();
        }
        if (RoccoClientConfig.SPEC.isLoaded())
        {
            RoccoClientConfig.SPEC.save();
        }
    }

    private record Entry(Component name, Component tip, Supplier<ItemStack> icon, Runnable open, boolean active,
                         @org.jetbrains.annotations.Nullable Supplier<Boolean> ready) {}

    // Un tema de conexion: ademas del icono, una lucecita a la derecha (verde = listo, ambar = falta).
    static class StatusButton extends IconButton
    {
        private final Supplier<Boolean> ready;

        StatusButton(int x, int y, Component label, Supplier<ItemStack> icon, Supplier<Boolean> ready, OnPress onPress)
        {
            super(x, y, label, icon, onPress);
            this.ready = ready;
        }

        @Override
        public void renderString(GuiGraphics graphics, Font font, int color)
        {
            super.renderString(graphics, font, color);
            int size = 6;
            int x = getX() + getWidth() - size - 5;
            int y = getY() + (getHeight() - size) / 2;
            boolean ok = this.ready.get();
            graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF202020);
            graphics.fill(x, y, x + size, y + size, ok ? 0xFF55DD55 : 0xFFE0A030);
        }
    }

    // Boton con el icono de un item a la izquierda y el texto al lado.
    static class IconButton extends Button
    {
        private final Supplier<ItemStack> icon;

        IconButton(int x, int y, Component label, Supplier<ItemStack> icon, OnPress onPress)
        {
            super(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, label, onPress, DEFAULT_NARRATION);
            this.icon = icon;
        }

        @Override
        public void renderString(GuiGraphics graphics, Font font, int color)
        {
            graphics.renderItem(this.icon.get(), getX() + 3, getY() + 2);
            int textX = getX() + 24;
            int room = getWidth() - 28;
            String text = getMessage().getString();
            if (font.width(text) > room)
            {
                text = font.plainSubstrByWidth(text, room - font.width("...")) + "...";
            }
            graphics.drawString(font, text, textX, getY() + (getHeight() - 8) / 2, color);
        }
    }
}

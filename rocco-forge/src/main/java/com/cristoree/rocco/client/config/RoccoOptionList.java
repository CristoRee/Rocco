package com.cristoree.rocco.client.config;

import com.cristoree.rocco.client.config.RoccoConfigCategories.Group;
import com.cristoree.rocco.client.config.RoccoConfigCategories.Option;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// La lista de opciones de una pantalla de la configuracion. Cada fila edita una copia del valor:
// recien al aplicar (al salir de la pantalla o al cambiar la busqueda) se escribe en la config.
// Una fila bloqueada (los rasgos de un modo listo) se ve en gris y no se aplica nunca.
public class RoccoOptionList extends ContainerObjectSelectionList<RoccoOptionList.Row>
{
    private static final int WIDGET_WIDTH = 110;
    private static final int RESET_WIDTH = 20;
    private static final int ROW_HEIGHT = 24;
    private static final int MAX_ROW_WIDTH = 340;

    // Los valores de las listas desplegables, en castellano. El .toml guarda el nombre de siempre.
    // Los enums nuevos usan claves de idioma ("rocco.value.Treatment.AMABLE") en vez de esto.
    private static final Map<String, String> ENUM_NAMES = Map.ofEntries(
            Map.entry("AUTO", "Automático"),
            Map.entry("GEMINI", "Gemini"),
            Map.entry("GROQ", "Groq"),
            Map.entry("OPENROUTER", "OpenRouter"),
            Map.entry("OLLAMA", "Ollama"),
            Map.entry("CUSTOM", "Propio"),
            Map.entry("OFF", "Apagado"),
            Map.entry("MINIMAL", "Mínimo"),
            Map.entry("LOW", "Bajo"),
            Map.entry("MEDIUM", "Medio"),
            Map.entry("HIGH", "Alto"),
            Map.entry("LatencyMode.NORMAL", "Mejor calidad"),
            Map.entry("LatencyMode.BALANCED", "Equilibrado"),
            Map.entry("LatencyMode.LOW", "Lo más rápido"),
            Map.entry("PUSH_TO_TALK", "Mantener tecla"),
            Map.entry("VOICE_ACTIVATION", "Por voz"));

    private final List<WidgetRow> rows = new ArrayList<>();

    public RoccoOptionList(Minecraft minecraft, int width, int height, int top, int bottom)
    {
        super(minecraft, width, height, top, bottom, ROW_HEIGHT);
    }

    // Cambia lo que muestra. Lo que se habia tocado en la vista anterior queda aplicado antes.
    public void show(List<Group> groups, Component emptyText)
    {
        applyAll();
        this.rows.clear();
        clearEntries();
        setScrollAmount(0.0D);

        for (Group group : groups)
        {
            List<WidgetRow> built = new ArrayList<>();
            for (Option option : group.options())
            {
                WidgetRow row = option.loaded() ? buildRow(option) : null;
                if (row != null)
                {
                    built.add(row);
                }
            }
            if (built.isEmpty())
            {
                continue;
            }
            if (group.title() != null)
            {
                addEntry(new HeadingRow(Component.literal(text(group.title()))));
            }
            built.forEach(this::addEntry);
            this.rows.addAll(built);
        }

        if (children().isEmpty())
        {
            addEntry(new HeadingRow(emptyText.copy().withStyle(ChatFormatting.GRAY)));
        }
    }

    public void applyAll()
    {
        for (WidgetRow row : this.rows)
        {
            if (!row.locked)
            {
                row.apply();
            }
        }
    }

    // La fila de esa opcion, si esta en la lista.
    @Nullable
    public WidgetRow rowFor(ForgeConfigSpec.ConfigValue<?> value)
    {
        for (WidgetRow row : this.rows)
        {
            if (row.value == value)
            {
                return row;
            }
        }
        return null;
    }

    @Override
    public int getRowWidth()
    {
        return Math.min(MAX_ROW_WIDTH, this.width - 24);
    }

    @Override
    protected int getScrollbarPosition()
    {
        return this.width / 2 + getRowWidth() / 2 + 6;
    }

    // El nombre de una opcion: una clave de idioma ("rocco....") o el propio texto en castellano
    // (ver Tr), que se traduce si hay traduccion.
    static String text(String labelOrKey)
    {
        return I18n.get(labelOrKey);
    }

    // La ayuda de la opcion: su traduccion ("rocco.tip.<ruta>") si hay, y si no el comentario del .toml.
    @Nullable
    private static String tooltipOf(Option option, ForgeConfigSpec.ValueSpec spec)
    {
        String key = "rocco.tip." + String.join(".", option.value().getPath());
        return I18n.exists(key) ? I18n.get(key) : spec.getComment();
    }

    @Nullable
    private WidgetRow buildRow(Option option)
    {
        ForgeConfigSpec.ValueSpec spec = option.valueSpec();
        ForgeConfigSpec.ConfigValue<?> value = option.value();
        Component label = com.cristoree.rocco.client.Tr.t(option.label());
        Class<?> type = resolveType(spec);

        if (type == Boolean.class)
        {
            return new BooleanRow(label, option, spec, cast(value));
        }
        if (type.isEnum())
        {
            return new EnumRow(label, option, spec, cast(value), type);
        }
        if (type == Integer.class || type == Long.class || type == Double.class)
        {
            return new NumberRow(label, option, spec, value, type);
        }
        if (CharSequence.class.isAssignableFrom(type))
        {
            return new TextRow(label, option, spec, cast(value), isSecret(value.getPath().get(value.getPath().size() - 1)));
        }

        // Tipo que esta pantalla no sabe editar (una lista, por ejemplo): mejor no mostrar
        // una fila rota. Se edita a mano en el .toml.
        return null;
    }

    // Un define() normal de Forge registra la clase como Object.class (ver ForgeConfigSpec,
    // define(path, supplier, validator) -> define(..., Object.class)), asi que para los textos
    // hay que mirar el tipo del valor por defecto. Sin esto las claves de API se dibujaban como
    // campos numericos: todo en rojo y el guardado las descartaba por invalidas.
    private static Class<?> resolveType(ForgeConfigSpec.ValueSpec typeSpec)
    {
        Class<?> declared = typeSpec.getClazz();
        if (declared != null && declared != Object.class)
        {
            return declared;
        }

        Object fallback = typeSpec.getDefault();
        return fallback != null ? fallback.getClass() : Object.class;
    }

    private static boolean isSecret(String key)
    {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("apikey") || lower.contains("token") || lower.contains("secret");
    }

    @SuppressWarnings("unchecked")
    private static <T> ForgeConfigSpec.ConfigValue<T> cast(ForgeConfigSpec.ConfigValue<?> value)
    {
        return (ForgeConfigSpec.ConfigValue<T>) value;
    }

    static String enumName(Object value)
    {
        Enum<?> constant = (Enum<?>) value;
        String key = "rocco.value." + constant.getDeclaringClass().getSimpleName() + "." + constant.name();
        if (I18n.exists(key))
        {
            return I18n.get(key);
        }
        String specific = ENUM_NAMES.get(constant.getDeclaringClass().getSimpleName() + "." + constant.name());
        if (specific != null)
        {
            return I18n.get(specific);
        }
        return I18n.get(ENUM_NAMES.getOrDefault(constant.name(), constant.name()));
    }

    public abstract static class Row extends ContainerObjectSelectionList.Entry<Row>
    {
    }

    public static class HeadingRow extends Row
    {
        private final Component text;

        HeadingRow(Component text)
        {
            this.text = text;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick)
        {
            Minecraft mc = Minecraft.getInstance();
            Component shown = this.text.getStyle().getColor() == null
                    ? this.text.copy().withStyle(ChatFormatting.YELLOW)
                    : this.text;
            int y = top + height - 14;
            graphics.drawCenteredString(mc.font, shown, left + width / 2, y, 0xFFFFFF);

            // Una rayita a cada lado del titulo, como separador.
            int half = mc.font.width(shown) / 2 + 6;
            int mid = y + 4;
            graphics.fill(left + 4, mid, left + width / 2 - half, mid + 1, 0x40FFFFFF);
            graphics.fill(left + width / 2 + half, mid, left + width - 4, mid + 1, 0x40FFFFFF);
        }

        @Override
        public List<? extends GuiEventListener> children()
        {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables()
        {
            return List.of();
        }
    }

    // Una fila con el nombre a la izquierda, el control a la derecha y el boton para volver al
    // valor por defecto. Pasar el mouse por el nombre o el control muestra que hace la opcion.
    public abstract static class WidgetRow extends Row
    {
        private final Component label;
        private final Tooltip help;
        private final Button reset;
        protected final ForgeConfigSpec.ValueSpec spec;
        final ForgeConfigSpec.ConfigValue<?> value;
        boolean locked;

        WidgetRow(Component label, Option option, ForgeConfigSpec.ValueSpec spec)
        {
            this.label = label;
            this.spec = spec;
            this.value = option.value();
            this.help = Tooltip.create(helpText(label, option, spec));
            this.reset = Button.builder(Component.literal("↺"), b -> resetToDefault())
                    .bounds(0, 0, RESET_WIDTH, 20)
                    .tooltip(Tooltip.create(Component.translatable("rocco.config.reset")))
                    .build();
        }

        protected abstract AbstractWidget widget();

        protected abstract boolean isDefault();

        protected abstract void resetToDefault();

        protected abstract String describe(Object value);

        // Escribe en la config lo que muestra la fila.
        protected abstract void apply();

        // Muestra otro valor (copiar un modo, mostrar el de un modo listo). No toca la config.
        public abstract void setValue(Object value);

        // Bloqueada: en gris, sin poder tocarla, y nunca se aplica.
        public void setLocked(boolean locked)
        {
            this.locked = locked;
            widget().active = !locked;
            if (widget() instanceof EditBox box)
            {
                box.setEditable(!locked);
            }
        }

        protected void attachTooltip()
        {
            widget().setTooltip(this.help);
        }

        private Component helpText(Component label, Option option, ForgeConfigSpec.ValueSpec spec)
        {
            MutableComponent text = label.copy().withStyle(ChatFormatting.YELLOW);
            String comment = option.tooltip() != null ? I18n.get(option.tooltip()) : tooltipOf(option, spec);
            if (comment != null && !comment.isBlank())
            {
                // Los comentarios del .toml vienen cortados en renglones; aca se acomodan solos.
                text.append(Component.literal("\n" + comment.replace('\n', ' ')).withStyle(ChatFormatting.WHITE));
            }
            ForgeConfigSpec.Range<?> range = spec.getRange();
            if (range != null)
            {
                text.append(com.cristoree.rocco.client.Tr.t("\n").append(Component.translatable("rocco.config.range",
                        String.valueOf(range.getMin()), String.valueOf(range.getMax()))).withStyle(ChatFormatting.GRAY));
            }
            text.append(com.cristoree.rocco.client.Tr.t("\n").append(Component.translatable("rocco.config.default", describe(spec.getDefault())))
                    .withStyle(ChatFormatting.GRAY));
            if (spec.needsWorldRestart())
            {
                text.append(com.cristoree.rocco.client.Tr.t("\n").append(Component.translatable("rocco.config.restart")).withStyle(ChatFormatting.GOLD));
            }
            return text;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick)
        {
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;
            AbstractWidget widget = widget();

            int resetX = left + width - RESET_WIDTH - 2;
            int widgetX = resetX - WIDGET_WIDTH - 4;
            int labelSpace = widgetX - left - 10;

            String text = this.label.getString();
            if (font.width(text) > labelSpace)
            {
                text = font.plainSubstrByWidth(text, labelSpace - font.width("...")) + "...";
            }
            graphics.drawString(font, text, left + 4, top + 6, this.locked ? 0x9A9A9A : 0xFFFFFF);

            widget.setX(widgetX);
            widget.setY(top + 1);
            widget.render(graphics, mouseX, mouseY, partialTick);

            this.reset.visible = !this.locked;
            this.reset.active = !isDefault();
            this.reset.setX(resetX);
            this.reset.setY(top + 1);
            this.reset.render(graphics, mouseX, mouseY, partialTick);

            if (hovering && mouseX < widgetX && mc.screen != null)
            {
                mc.screen.setTooltipForNextRenderPass(this.help.toCharSequence(mc));
            }
        }

        @Override
        public List<? extends GuiEventListener> children()
        {
            return List.of(widget(), this.reset);
        }

        @Override
        public List<? extends NarratableEntry> narratables()
        {
            return List.of(widget(), this.reset);
        }
    }

    public static class BooleanRow extends WidgetRow
    {
        private final Button button;
        private final ForgeConfigSpec.ConfigValue<Boolean> configValue;
        private boolean current;

        BooleanRow(Component label, Option option, ForgeConfigSpec.ValueSpec spec, ForgeConfigSpec.ConfigValue<Boolean> configValue)
        {
            super(label, option, spec);
            this.configValue = configValue;
            this.current = configValue.get();
            this.button = Button.builder(text(this.current), b -> set(!this.current))
                    .bounds(0, 0, WIDGET_WIDTH, 20).build();
            attachTooltip();
        }

        private void set(boolean on)
        {
            this.current = on;
            this.button.setMessage(text(on));
        }

        private static Component text(boolean on)
        {
            return on
                    ? Component.translatable("rocco.config.yes").withStyle(ChatFormatting.GREEN)
                    : Component.translatable("rocco.config.no").withStyle(ChatFormatting.RED);
        }

        @Override
        protected AbstractWidget widget()
        {
            return this.button;
        }

        @Override
        protected boolean isDefault()
        {
            return Boolean.valueOf(this.current).equals(this.spec.getDefault());
        }

        @Override
        protected void resetToDefault()
        {
            set(Boolean.TRUE.equals(this.spec.getDefault()));
        }

        @Override
        protected String describe(Object value)
        {
            return I18n.get(Boolean.TRUE.equals(value) ? "rocco.config.yes" : "rocco.config.no");
        }

        @Override
        protected void apply()
        {
            this.configValue.set(this.current);
        }

        @Override
        public void setValue(Object value)
        {
            set(Boolean.TRUE.equals(value));
        }
    }

    public static class EnumRow extends WidgetRow
    {
        private final Button button;
        private final Object[] options;
        private final ForgeConfigSpec.ConfigValue<Object> configValue;
        private int index;

        EnumRow(Component label, Option option, ForgeConfigSpec.ValueSpec spec,
                ForgeConfigSpec.ConfigValue<Object> configValue, Class<?> enumType)
        {
            super(label, option, spec);
            this.configValue = configValue;
            this.options = enumType.getEnumConstants();
            this.index = indexOf(configValue.get());

            this.button = Button.builder(Component.literal(enumName(this.options[this.index])), b ->
                    select((this.index + 1) % this.options.length))
                    .bounds(0, 0, WIDGET_WIDTH, 20).build();
            attachTooltip();
        }

        private int indexOf(Object value)
        {
            for (int i = 0; i < this.options.length; i++)
            {
                if (this.options[i] == value)
                {
                    return i;
                }
            }
            return 0;
        }

        private void select(int index)
        {
            this.index = index;
            this.button.setMessage(Component.literal(enumName(this.options[index])));
        }

        @Override
        protected AbstractWidget widget()
        {
            return this.button;
        }

        @Override
        protected boolean isDefault()
        {
            return this.options[this.index] == this.spec.getDefault();
        }

        @Override
        protected void resetToDefault()
        {
            select(indexOf(this.spec.getDefault()));
        }

        @Override
        protected String describe(Object value)
        {
            return value instanceof Enum<?> ? enumName(value) : String.valueOf(value);
        }

        @Override
        protected void apply()
        {
            this.configValue.set(this.options[this.index]);
        }

        @Override
        public void setValue(Object value)
        {
            select(indexOf(value));
        }
    }

    public static class TextRow extends WidgetRow
    {
        private final EditBox box;
        private final ForgeConfigSpec.ConfigValue<String> configValue;

        TextRow(Component label, Option option, ForgeConfigSpec.ValueSpec spec,
                ForgeConfigSpec.ConfigValue<String> configValue, boolean secret)
        {
            super(label, option, spec);
            this.configValue = configValue;

            this.box = new EditBox(Minecraft.getInstance().font, 0, 0, WIDGET_WIDTH, 20, label);
            this.box.setMaxLength(option.maxLength());
            this.box.setValue(configValue.get());

            // Las claves no se muestran en claro salvo que la estes escribiendo.
            if (secret)
            {
                this.box.setFormatter((text, cursor) -> this.box.isFocused()
                        ? FormattedCharSequence.forward(text, Style.EMPTY)
                        : FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
                this.box.setHint(Component.translatable("rocco.config.paste_key").withStyle(ChatFormatting.DARK_GRAY));
            }
            attachTooltip();
        }

        @Override
        protected AbstractWidget widget()
        {
            return this.box;
        }

        @Override
        protected boolean isDefault()
        {
            return this.box.getValue().trim().equals(this.spec.getDefault());
        }

        @Override
        protected void resetToDefault()
        {
            this.box.setValue(String.valueOf(this.spec.getDefault()));
        }

        @Override
        protected String describe(Object value)
        {
            String text = String.valueOf(value);
            return text.isEmpty() ? I18n.get("rocco.config.empty") : text;
        }

        @Override
        protected void apply()
        {
            String text = this.box.getValue().trim();
            // Un texto que la config no acepta (mas largo de la cuenta) se descarta.
            if (this.spec.test(text))
            {
                this.configValue.set(text);
            }
        }

        @Override
        public void setValue(Object value)
        {
            this.box.setValue(String.valueOf(value));
        }
    }

    public static class NumberRow extends WidgetRow
    {
        private final EditBox box;
        private final Class<?> type;
        private final ForgeConfigSpec.ConfigValue<?> configValue;

        NumberRow(Component label, Option option, ForgeConfigSpec.ValueSpec spec,
                  ForgeConfigSpec.ConfigValue<?> configValue, Class<?> type)
        {
            super(label, option, spec);
            this.type = type;
            this.configValue = configValue;

            this.box = new EditBox(Minecraft.getInstance().font, 0, 0, WIDGET_WIDTH, 20, label);
            this.box.setResponder(text ->
            {
                Object parsed = parse(text, type);
                boolean valid = parsed != null && spec.test(parsed);
                this.box.setTextColor(valid ? 0xE0E0E0 : 0xFF5555);
            });
            this.box.setValue(String.valueOf(configValue.get()));
            attachTooltip();
        }

        private static Object parse(String text, Class<?> type)
        {
            try
            {
                String trimmed = text.trim().replace(',', '.');
                if (type == Integer.class)
                {
                    return Integer.valueOf(trimmed);
                }
                if (type == Long.class)
                {
                    return Long.valueOf(trimmed);
                }
                if (type == Double.class)
                {
                    return Double.valueOf(trimmed);
                }
                return null;
            }
            catch (NumberFormatException e)
            {
                return null;
            }
        }

        @Override
        protected AbstractWidget widget()
        {
            return this.box;
        }

        @Override
        protected boolean isDefault()
        {
            Object parsed = parse(this.box.getValue(), this.type);
            return parsed instanceof Number number && this.spec.getDefault() instanceof Number fallback
                    && Double.compare(number.doubleValue(), fallback.doubleValue()) == 0;
        }

        @Override
        protected void resetToDefault()
        {
            this.box.setValue(String.valueOf(this.spec.getDefault()));
        }

        @Override
        protected String describe(Object value)
        {
            return String.valueOf(value);
        }

        @Override
        protected void apply()
        {
            Object parsed = parse(this.box.getValue(), this.type);
            // Un valor invalido o fuera de rango se descarta: queda el que ya estaba.
            if (parsed != null && this.spec.test(parsed))
            {
                cast(this.configValue).set(parsed);
            }
        }

        @Override
        public void setValue(Object value)
        {
            this.box.setValue(String.valueOf(value));
        }
    }
}

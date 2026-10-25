package com.cristoree.rocco.client.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RoccoOptionList extends ContainerObjectSelectionList<RoccoOptionList.Row>
{
    private static final int WIDGET_WIDTH = 120;
    private static final int ROW_HEIGHT = 24;

    private final List<Runnable> applyActions = new ArrayList<>();

    public RoccoOptionList(Minecraft minecraft, int width, int height, int top, int bottom)
    {
        super(minecraft, width, height, top, bottom, ROW_HEIGHT);
    }

    public void addSpec(Component heading, ForgeConfigSpec spec)
    {
        addEntry(new HeadingRow(heading, true));
        walk(spec.getValues(), spec.getSpec());
    }

    public void applyAll()
    {
        this.applyActions.forEach(Runnable::run);
    }

    @Override
    public int getRowWidth()
    {
        return 310;
    }

    @Override
    protected int getScrollbarPosition()
    {
        return this.width / 2 + 160;
    }

    private void walk(UnmodifiableConfig values, UnmodifiableConfig specs)
    {
        Map<String, Object> specMap = specs.valueMap();

        for (Map.Entry<String, Object> entry : values.valueMap().entrySet())
        {
            Object value = entry.getValue();
            Object valueSpec = specMap.get(entry.getKey());

            if (value instanceof UnmodifiableConfig childValues && valueSpec instanceof UnmodifiableConfig childSpecs)
            {
                addEntry(new HeadingRow(Component.literal(prettify(entry.getKey())), false));
                walk(childValues, childSpecs);
            }
            else if (value instanceof ForgeConfigSpec.ConfigValue<?> configValue
                    && valueSpec instanceof ForgeConfigSpec.ValueSpec typeSpec)
            {
                // Es de uso interno: editarlo a mano romperia las migraciones.
                if (entry.getKey().equals("configVersion"))
                {
                    continue;
                }

                Row row = buildRow(configValue, typeSpec, entry.getKey());
                if (row != null)
                {
                    addEntry(row);
                }
            }
        }
    }

    private Row buildRow(ForgeConfigSpec.ConfigValue<?> configValue, ForgeConfigSpec.ValueSpec typeSpec, String key)
    {
        Component label = Component.literal(prettify(key));
        Class<?> type = resolveType(typeSpec);

        if (type == Boolean.class)
        {
            return new BooleanRow(label, typeSpec, cast(configValue));
        }
        if (type.isEnum())
        {
            return new EnumRow(label, typeSpec, cast(configValue), type);
        }
        if (type == Integer.class || type == Long.class || type == Double.class || type == Float.class)
        {
            return new NumberRow(label, typeSpec, configValue, type);
        }
        if (CharSequence.class.isAssignableFrom(type))
        {
            return new TextRow(label, typeSpec, cast(configValue), isSecret(key));
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

    // thinkingBudget -> "Thinking budget"; followStartDistance -> "Follow start distance"
    private static String prettify(String key)
    {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < key.length(); i++)
        {
            char c = key.charAt(i);
            if (i == 0)
            {
                out.append(Character.toUpperCase(c));
            }
            else if (Character.isUpperCase(c))
            {
                out.append(' ').append(Character.toLowerCase(c));
            }
            else
            {
                out.append(c);
            }
        }
        return out.toString();
    }

    private void onApply(Runnable action)
    {
        this.applyActions.add(action);
    }

    public abstract static class Row extends ContainerObjectSelectionList.Entry<Row>
    {
    }

    public static class HeadingRow extends Row
    {
        private final Component text;
        private final boolean major;

        HeadingRow(Component text, boolean major)
        {
            this.text = text;
            this.major = major;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick)
        {
            Minecraft mc = Minecraft.getInstance();
            Component shown = this.major
                    ? this.text.copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                    : this.text.copy().withStyle(ChatFormatting.YELLOW);

            graphics.drawCenteredString(mc.font, shown, left + width / 2, top + height - 14, 0xFFFFFF);
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

    public abstract class WidgetRow extends Row
    {
        private final Component label;
        private final String comment;

        WidgetRow(Component label, ForgeConfigSpec.ValueSpec typeSpec)
        {
            this.label = label;
            this.comment = typeSpec.getComment();
        }

        protected abstract AbstractWidget widget();

        protected void attachTooltip()
        {
            if (this.comment != null && !this.comment.isBlank())
            {
                widget().setTooltip(Tooltip.create(Component.literal(this.comment)));
            }
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick)
        {
            Minecraft mc = Minecraft.getInstance();
            AbstractWidget widget = widget();

            graphics.drawString(mc.font, this.label, left + 4, top + 6, 0xFFFFFF);

            widget.setX(left + width - WIDGET_WIDTH - 4);
            widget.setY(top + 1);
            widget.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children()
        {
            return List.of(widget());
        }

        @Override
        public List<? extends NarratableEntry> narratables()
        {
            return List.of(widget());
        }
    }

    public class BooleanRow extends WidgetRow
    {
        private final Button button;
        private boolean value;

        BooleanRow(Component label, ForgeConfigSpec.ValueSpec typeSpec, ForgeConfigSpec.ConfigValue<Boolean> configValue)
        {
            super(label, typeSpec);
            this.value = configValue.get();
            this.button = Button.builder(text(this.value), b ->
            {
                this.value = !this.value;
                b.setMessage(text(this.value));
            }).bounds(0, 0, WIDGET_WIDTH, 20).build();

            attachTooltip();
            onApply(() -> configValue.set(this.value));
        }

        private static Component text(boolean on)
        {
            return on
                    ? Component.literal("Si").withStyle(ChatFormatting.GREEN)
                    : Component.literal("No").withStyle(ChatFormatting.RED);
        }

        @Override
        protected AbstractWidget widget()
        {
            return this.button;
        }
    }

    public class EnumRow extends WidgetRow
    {
        private final Button button;
        private final Object[] options;
        private int index;

        EnumRow(Component label, ForgeConfigSpec.ValueSpec typeSpec,
                ForgeConfigSpec.ConfigValue<Object> configValue, Class<?> enumType)
        {
            super(label, typeSpec);
            this.options = enumType.getEnumConstants();

            Object current = configValue.get();
            for (int i = 0; i < this.options.length; i++)
            {
                if (this.options[i] == current)
                {
                    this.index = i;
                    break;
                }
            }

            this.button = Button.builder(Component.literal(name(this.options[this.index])), b ->
            {
                this.index = (this.index + 1) % this.options.length;
                b.setMessage(Component.literal(name(this.options[this.index])));
            }).bounds(0, 0, WIDGET_WIDTH, 20).build();

            attachTooltip();
            onApply(() -> configValue.set(this.options[this.index]));
        }

        private static String name(Object value)
        {
            return ((Enum<?>) value).name();
        }

        @Override
        protected AbstractWidget widget()
        {
            return this.button;
        }
    }

    public class TextRow extends WidgetRow
    {
        private final EditBox box;

        TextRow(Component label, ForgeConfigSpec.ValueSpec typeSpec,
                ForgeConfigSpec.ConfigValue<String> configValue, boolean secret)
        {
            super(label, typeSpec);

            this.box = new EditBox(Minecraft.getInstance().font, 0, 0, WIDGET_WIDTH, 20, label);
            this.box.setMaxLength(512);
            this.box.setValue(configValue.get());

            // Las claves no se muestran en claro salvo que la estes escribiendo.
            if (secret)
            {
                this.box.setFormatter((text, cursor) -> this.box.isFocused()
                        ? FormattedCharSequence.forward(text, Style.EMPTY)
                        : FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
            }

            attachTooltip();
            onApply(() -> configValue.set(this.box.getValue().trim()));
        }

        @Override
        protected AbstractWidget widget()
        {
            return this.box;
        }
    }

    public class NumberRow extends WidgetRow
    {
        private final EditBox box;

        NumberRow(Component label, ForgeConfigSpec.ValueSpec typeSpec,
                  ForgeConfigSpec.ConfigValue<?> configValue, Class<?> type)
        {
            super(label, typeSpec);

            this.box = new EditBox(Minecraft.getInstance().font, 0, 0, WIDGET_WIDTH, 20, label);
            this.box.setValue(String.valueOf(configValue.get()));
            this.box.setResponder(text -> this.box.setTextColor(parse(text, type) == null ? 0xFF5555 : 0xE0E0E0));

            attachTooltip();
            onApply(() ->
            {
                Object parsed = parse(this.box.getValue(), type);
                // Un valor invalido o fuera de rango se descarta: queda el que ya estaba.
                if (parsed != null && typeSpec.test(parsed))
                {
                    cast(configValue).set(parsed);
                }
            });
        }

        private static Object parse(String text, Class<?> type)
        {
            try
            {
                String trimmed = text.trim();
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
    }
}

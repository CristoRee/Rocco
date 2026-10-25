package com.cristoree.rocco.client.config;

import com.cristoree.rocco.ai.AiProbe;
import com.cristoree.rocco.ai.ReplyTags;
import com.cristoree.rocco.ai.personality.PersonalityProfile;
import com.cristoree.rocco.ai.personality.Traits;
import com.cristoree.rocco.client.config.RoccoConfigCategories.Group;
import com.cristoree.rocco.client.config.RoccoConfigCategories.Option;
import com.cristoree.rocco.config.RoccoCommonConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// La personalidad de Rocco: los modos Chill, Sin filtro y Personalizado, y cada rasgo. En los
// modos listos los rasgos se ven en gris (para que se vea como es cada uno) y no se pueden tocar;
// en Personalizado se editan. Los cambios valen desde la proxima respuesta, sin reiniciar.
public class RoccoPersonalityScreen extends Screen
{
    private static final int BUTTON_WIDTH = 104;

    private final Screen parent;
    private Traits.Mode mode;
    private RoccoOptionList list;
    private final Map<Traits.Mode, Button> modeButtons = new LinkedHashMap<>();
    private Button copyChill;
    private Button copySinFiltro;
    private Button test;
    private List<FormattedCharSequence> testLines = List.of();
    private boolean initializeCustom;

    // Los rasgos que traen los modos listos, y donde va cada uno en un perfil.
    private static final Map<ForgeConfigSpec.ConfigValue<?>, Function<PersonalityProfile, Object>> TRAITS = new LinkedHashMap<>();

    static
    {
        TRAITS.put(RoccoCommonConfig.P_ORIGIN, PersonalityProfile::origin);
        TRAITS.put(RoccoCommonConfig.P_TREATMENT, PersonalityProfile::treatment);
        TRAITS.put(RoccoCommonConfig.P_SWEARING, PersonalityProfile::swearing);
        TRAITS.put(RoccoCommonConfig.P_SARCASM, PersonalityProfile::sarcasm);
        TRAITS.put(RoccoCommonConfig.P_EGO, PersonalityProfile::ego);
        TRAITS.put(RoccoCommonConfig.P_DARK_HUMOR, PersonalityProfile::darkHumor);
        TRAITS.put(RoccoCommonConfig.P_DARK_SIDE, PersonalityProfile::darkSide);
        TRAITS.put(RoccoCommonConfig.P_JEALOUSY, PersonalityProfile::jealousy);
        TRAITS.put(RoccoCommonConfig.P_SPONTANEOUS, PersonalityProfile::spontaneous);
        TRAITS.put(RoccoCommonConfig.P_LENGTH, PersonalityProfile::length);
        TRAITS.put(RoccoCommonConfig.P_QUESTIONS, PersonalityProfile::questions);
        TRAITS.put(RoccoCommonConfig.P_FACTS, PersonalityProfile::facts);
        TRAITS.put(RoccoCommonConfig.P_LIFE_ADVICE, PersonalityProfile::lifeAdvice);
        TRAITS.put(RoccoCommonConfig.P_FOURTH_WALL, PersonalityProfile::fourthWall);
        TRAITS.put(RoccoCommonConfig.P_SPONTANEOUS_IMPRESSIONS, PersonalityProfile::spontaneousImpressions);
        TRAITS.put(RoccoCommonConfig.P_FEARS, PersonalityProfile::fears);
        TRAITS.put(RoccoCommonConfig.P_POTATO, PersonalityProfile::potato);
        TRAITS.put(RoccoCommonConfig.P_TRUST_PACE, PersonalityProfile::trustPace);
    }

    // Solo tienen sentido en Personalizado: en los modos listos tambien quedan en gris.
    private static final List<ForgeConfigSpec.ConfigValue<?>> CUSTOM_ONLY = List.of(
            RoccoCommonConfig.CUSTOM_ORIGIN, RoccoCommonConfig.EXTRA_NOTES,
            RoccoCommonConfig.USE_OWN_FILE, RoccoCommonConfig.PROMPT_FILE);

    public RoccoPersonalityScreen(Screen parent)
    {
        super(Component.translatable("rocco.personality.title"));
        this.parent = parent;
        this.mode = RoccoCommonConfig.PERSONALITY_MODE.get();
    }

    @Override
    protected void init()
    {
        // Si la ventana cambia de tamaño, init vuelve a correr: lo tocado no se pierde.
        if (this.list != null)
        {
            this.list.applyAll();
        }
        this.modeButtons.clear();

        // La lista va primero: pinta el fondo de arriba y de abajo, y los botones van encima.
        this.list = new RoccoOptionList(this.minecraft, this.width, this.height, 84, this.height - 62);
        this.list.show(groups(), Component.translatable("rocco.config.not_loaded"));
        addRenderableWidget(this.list);

        int left = this.width / 2 - (3 * BUTTON_WIDTH + 8) / 2;
        int x = left;
        for (Traits.Mode option : Traits.Mode.values())
        {
            Button button = Button.builder(modeLabel(option), b -> selectMode(option))
                    .bounds(x, 36, BUTTON_WIDTH, 20)
                    .tooltip(Tooltip.create(Component.translatable("rocco.personality.mode." + key(option) + ".tip")))
                    .build();
            this.modeButtons.put(option, addRenderableWidget(button));
            x += BUTTON_WIDTH + 4;
        }

        this.copyChill = addRenderableWidget(Button.builder(Component.translatable("rocco.personality.copy_chill"),
                        b -> copy(PersonalityProfile.CHILL))
                .bounds(left, 60, BUTTON_WIDTH, 20).build());
        this.copySinFiltro = addRenderableWidget(Button.builder(Component.translatable("rocco.personality.copy_sin_filtro"),
                        b -> copy(PersonalityProfile.SIN_FILTRO))
                .bounds(left + BUTTON_WIDTH + 4, 60, BUTTON_WIDTH, 20).build());
        this.test = addRenderableWidget(Button.builder(Component.translatable("rocco.personality.test"), b -> runTest())
                .bounds(left + 2 * (BUTTON_WIDTH + 4), 60, BUTTON_WIDTH, 20)
                .tooltip(Tooltip.create(Component.translatable("rocco.personality.test.tip")))
                .build());
        refreshMode();

        addRenderableWidget(Button.builder(Component.translatable("rocco.config.back"), b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20)
                .build());
    }

    private static String key(Traits.Mode mode)
    {
        return mode.name().toLowerCase(java.util.Locale.ROOT);
    }

    private Component modeLabel(Traits.Mode option)
    {
        Component name = Component.translatable("rocco.personality.mode." + key(option));
        return option == this.mode
                ? Component.literal("» ").append(name).append(" «").withStyle(ChatFormatting.GREEN)
                : name;
    }

    private void selectMode(Traits.Mode next)
    {
        if (next == this.mode)
        {
            return;
        }
        // Lo que habia tocado en Personalizado se guarda antes de mostrar los rasgos de otro modo.
        if (this.mode == Traits.Mode.PERSONALIZADO)
        {
            this.list.applyAll();
        }

        Traits.Mode previous = this.mode;
        this.mode = next;

        if (next == Traits.Mode.PERSONALIZADO)
        {
            if (!RoccoCommonConfig.CUSTOM_INITIALIZED.get() && !this.initializeCustom)
            {
                // La primera vez arranca copiado del modo que tenia, en vez de con valores sueltos.
                this.initializeCustom = true;
                showValues(PersonalityProfile.of(previous));
            }
            else
            {
                showValues(PersonalityProfile.fromConfig());
            }
        }
        refreshMode();
    }

    private void copy(PersonalityProfile profile)
    {
        showValues(profile);
    }

    // Pone en las filas los valores de ese perfil (sin tocar la config hasta aplicar).
    private void showValues(PersonalityProfile profile)
    {
        TRAITS.forEach((value, getter) ->
        {
            RoccoOptionList.WidgetRow row = this.list.rowFor(value);
            if (row != null)
            {
                row.setValue(getter.apply(profile));
            }
        });
    }

    // En los modos listos: los rasgos de ese modo, en gris. En Personalizado: editables.
    private void refreshMode()
    {
        boolean custom = this.mode == Traits.Mode.PERSONALIZADO;
        if (!custom)
        {
            showValues(PersonalityProfile.of(this.mode));
        }
        for (ForgeConfigSpec.ConfigValue<?> value : TRAITS.keySet())
        {
            RoccoOptionList.WidgetRow row = this.list.rowFor(value);
            if (row != null)
            {
                row.setLocked(!custom);
            }
        }
        for (ForgeConfigSpec.ConfigValue<?> value : CUSTOM_ONLY)
        {
            RoccoOptionList.WidgetRow row = this.list.rowFor(value);
            if (row != null)
            {
                row.setLocked(!custom);
            }
        }
        this.copyChill.active = custom;
        this.copySinFiltro.active = custom;
        this.modeButtons.forEach((option, button) -> button.setMessage(modeLabel(option)));
    }

    // Guarda lo de la pantalla en la config (en memoria) y el modo elegido.
    private void applyAll()
    {
        this.list.applyAll();
        RoccoCommonConfig.PERSONALITY_MODE.set(this.mode);
        if (this.initializeCustom && this.mode == Traits.Mode.PERSONALIZADO)
        {
            RoccoCommonConfig.CUSTOM_INITIALIZED.set(true);
        }
    }

    private void runTest()
    {
        applyAll();
        this.test.active = false;
        this.testLines = this.font.split(Component.translatable("rocco.personality.testing").withStyle(ChatFormatting.GRAY), 330);
        AiProbe.personality().thenAccept(result -> this.minecraft.execute(() ->
        {
            this.test.active = true;
            Component shown = result.ok()
                    ? Component.literal("Rocco: ").withStyle(ChatFormatting.GREEN)
                            .append(Component.literal(ReplyTags.parse(result.reply(), false).chatText()).withStyle(ChatFormatting.WHITE))
                    : result.message().copy().withStyle(ChatFormatting.RED);
            this.testLines = this.font.split(shown, Math.min(330, this.width - 20));
        }));
    }

    private List<Group> groups()
    {
        ForgeConfigSpec common = RoccoCommonConfig.SPEC;
        return List.of(
                new Group("rocco.personality.group.story", List.of(
                        trait(common, RoccoCommonConfig.P_ORIGIN, "origin"),
                        new Option(common, RoccoCommonConfig.CUSTOM_ORIGIN, "rocco.config.trait.custom_origin",
                                "rocco.config.tip.custom_origin", 600))),
                new Group("rocco.personality.group.treatment", List.of(
                        trait(common, RoccoCommonConfig.P_TREATMENT, "treatment"),
                        trait(common, RoccoCommonConfig.P_SWEARING, "swearing"),
                        trait(common, RoccoCommonConfig.P_SARCASM, "sarcasm"),
                        trait(common, RoccoCommonConfig.P_EGO, "ego"))),
                new Group("rocco.personality.group.dark", List.of(
                        trait(common, RoccoCommonConfig.P_DARK_HUMOR, "dark_humor"),
                        trait(common, RoccoCommonConfig.P_DARK_SIDE, "dark_side"),
                        trait(common, RoccoCommonConfig.P_JEALOUSY, "jealousy"))),
                new Group("rocco.personality.group.chat", List.of(
                        trait(common, RoccoCommonConfig.P_SPONTANEOUS, "spontaneous"),
                        trait(common, RoccoCommonConfig.P_LENGTH, "length"),
                        trait(common, RoccoCommonConfig.P_QUESTIONS, "questions"),
                        trait(common, RoccoCommonConfig.P_FACTS, "facts"),
                        trait(common, RoccoCommonConfig.P_LIFE_ADVICE, "life_advice"),
                        trait(common, RoccoCommonConfig.P_FOURTH_WALL, "fourth_wall"))),
                new Group("rocco.config.group.impressions", List.of(
                        trait(common, RoccoCommonConfig.IMPRESSIONS_ENABLED, "impressions"),
                        trait(common, RoccoCommonConfig.P_SPONTANEOUS_IMPRESSIONS, "spontaneous_impressions"))),
                new Group("rocco.personality.group.character", List.of(
                        trait(common, RoccoCommonConfig.P_FEARS, "fears"),
                        trait(common, RoccoCommonConfig.P_POTATO, "potato"))),
                new Group("rocco.personality.group.relationship", List.of(
                        trait(common, RoccoCommonConfig.P_TRUST_PACE, "trust_pace"))),
                new Group("rocco.personality.group.language", List.of(
                        trait(common, RoccoCommonConfig.DIALECT, "dialect"))),
                new Group("rocco.personality.group.extra", List.of(
                        trait(common, RoccoCommonConfig.NICKNAME, "nickname"),
                        new Option(common, RoccoCommonConfig.EXTRA_NOTES, "rocco.config.trait.extra_notes",
                                "rocco.config.tip.extra_notes", 800))),
                new Group("rocco.personality.group.advanced", List.of(
                        trait(common, RoccoCommonConfig.USE_OWN_FILE, "own_file"),
                        trait(common, RoccoCommonConfig.PROMPT_FILE, "prompt_file"))));
    }

    private static Option trait(ForgeConfigSpec spec, ForgeConfigSpec.ConfigValue<?> value, String key)
    {
        return new Option(spec, value, "rocco.config.trait." + key, "rocco.config.tip." + key, 512);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), this.width / 2, 10, 0xFFFFFF);
        Component hint = Component.translatable(this.mode == Traits.Mode.PERSONALIZADO
                ? "rocco.personality.hint.custom" : "rocco.personality.hint.preset").withStyle(ChatFormatting.GRAY);
        graphics.drawCenteredString(this.font, hint, this.width / 2, 23, 0xFFFFFF);

        int y = this.height - 58;
        for (int i = 0; i < Math.min(3, this.testLines.size()); i++)
        {
            graphics.drawCenteredString(this.font, this.testLines.get(i), this.width / 2, y + i * 10, 0xFFFFFF);
        }
    }

    @Override
    public void onClose()
    {
        applyAll();
        RoccoConfigScreen.saveAll();
        this.minecraft.setScreen(this.parent);
    }
}

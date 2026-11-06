package com.cristoree.rocco.client.setup;

import com.cristoree.rocco.ai.AiProbe;
import com.cristoree.rocco.ai.AiProvider;
import com.cristoree.rocco.ai.personality.Traits;
import com.cristoree.rocco.client.config.RoccoConfigScreen;
import com.cristoree.rocco.client.voice.VoicePlayback;
import com.cristoree.rocco.config.RoccoClientConfig;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.voice.FishAudio;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// La bienvenida: una sola pantalla, sin barra para bajar, con lo que un tutorial dentro del juego no
// puede resolver: la clave de la IA, la voz y la personalidad. Lo demas esta en "Mas opciones".
// Aparece la primera vez en el menu principal y se abre cuando quieras con /rocco setup o desde la
// config. Nunca traba: Rocco ya funciona sin nada de esto. "Listo" y Esc guardan, como las opciones
// del juego.
public class RoccoSetupScreen extends Screen
{
    private static final String FISH_SIGNUP = "https://fish.audio/app/api-keys/";

    private static final int MAX_WIDTH = 440;
    private static final int COLUMN_GAP = 6;
    private static final int PAD = 6;
    private static final int ROW = 20;
    private static final int ROW_GAP = 2;
    private static final int LINE = 10;
    private static final int FOOTER_HEIGHT = 20;
    private static final int SHOW_WIDTH = 38;
    private static final int CARD_FILL = 0xB0101010;
    private static final int CARD_EDGE = 0x50FFFFFF;

    private enum Check { UNKNOWN, WORKS, FAILED }

    private final Screen parent;

    // Lo que se va escribiendo, por proveedor: cambiar de proveedor no borra lo escrito.
    private final Map<AiProvider, String> keys = new EnumMap<>(AiProvider.class);
    private AiProvider provider;
    private String fishKeyText;
    private String fishVoiceText;
    private boolean hadFishKey;
    private Traits.Mode mode;
    private boolean reloadOnReturn;

    private EditBox aiKey;
    private EditBox fishKey;
    private EditBox fishVoice;
    private Button aiGet;
    private Button aiTest;
    private Button voiceTest;
    private Button chillCard;
    private Button sinFiltroCard;
    private final Set<EditBox> shown = new HashSet<>();

    private Check aiCheck = Check.UNKNOWN;
    private Check voiceCheck = Check.UNKNOWN;
    private Component aiResult = Component.empty();
    private Component voiceResult = Component.empty();

    // Donde quedo cada cosa, para dibujarla (init lo recalcula si cambia la ventana).
    private int left;
    private int contentWidth;
    private int gap;
    private int titleY;
    private boolean bigTitle;
    private List<FormattedCharSequence> subtitle = List.of();
    private int subtitleY;
    private int cardsTop;
    private int cardHeight;
    private int cardWidth;
    private int rightCard;
    private int personalityTop;
    private int personalityHeight;
    private int privacyY;
    private List<FormattedCharSequence> privacy = List.of();

    public RoccoSetupScreen(Screen parent)
    {
        super(Component.translatable("rocco.setup.title"));
        this.parent = parent;
        load();
    }

    private void load()
    {
        for (AiProvider candidate : AiProvider.values())
        {
            ForgeConfigSpec.ConfigValue<String> key = keyOf(candidate);
            this.keys.put(candidate, key == null ? "" : key.get());
        }
        this.provider = firstConfigured();
        this.fishKeyText = RoccoCommonConfig.FISH_API_KEY.get();
        this.fishVoiceText = RoccoCommonConfig.FISH_VOICE_ID.get();
        this.hadFishKey = !this.fishKeyText.isBlank();
        this.mode = RoccoCommonConfig.PERSONALITY_MODE.get();
    }

    // El que ya tiene clave; si no hay ninguno, Groq.
    private static AiProvider firstConfigured()
    {
        for (AiProvider candidate : AiProvider.values())
        {
            if (candidate.isConfigured())
            {
                return candidate;
            }
        }
        return AiProvider.GROQ;
    }

    @Override
    protected void init()
    {
        if (this.reloadOnReturn)
        {
            // Volviendo de "Mas opciones": lo de ahi manda.
            this.reloadOnReturn = false;
            load();
        }
        else
        {
            rememberFields();
        }
        layout();
        buildAi();
        buildVoice();
        buildPersonality();
        buildFooter();
        refreshButtons();
        setInitialFocus(this.provider == AiProvider.OLLAMA ? this.fishKey : this.aiKey);
    }

    // Todo tiene que entrar sin barra, hasta en la pantalla mas chica del juego (320x240).
    private void layout()
    {
        this.contentWidth = Math.min(this.width - 16, MAX_WIDTH);
        this.left = (this.width - this.contentWidth) / 2;
        this.cardWidth = (this.contentWidth - COLUMN_GAP) / 2;
        this.rightCard = this.left + this.cardWidth + COLUMN_GAP;

        Component sub = onRemoteServer()
                ? Component.translatable("rocco.setup.remote").withStyle(ChatFormatting.GOLD)
                : Component.translatable("rocco.setup.subtitle").withStyle(ChatFormatting.GRAY);
        this.subtitle = limit(this.font.split(sub, this.contentWidth), 2);
        this.privacy = limit(this.font.split(Component.translatable("rocco.setup.privacy.text").withStyle(ChatFormatting.GRAY),
                this.contentWidth), 2);

        this.gap = this.height < 260 ? 2 : 4;
        this.cardHeight = PAD + 2 * LINE + 2 + 3 * ROW + 2 * ROW_GAP + 5;
        this.personalityHeight = PAD + LINE + 2 + ROW + 2 + LINE + 3;
        this.bigTitle = this.height >= 280;
        int titleHeight = this.bigTitle ? 20 : 11;
        int subtitleHeight = this.subtitle.size() * LINE;
        int privacyHeight = this.privacy.size() * LINE;
        int total = titleHeight + subtitleHeight + this.gap + this.cardHeight + this.gap + this.personalityHeight
                + this.gap + privacyHeight + this.gap + FOOTER_HEIGHT;

        int y = Math.max(1, (this.height - total) / 2);
        this.titleY = y;
        y += titleHeight;
        this.subtitleY = y;
        y += subtitleHeight + this.gap;
        this.cardsTop = y;
        y += this.cardHeight + this.gap;
        this.personalityTop = y;
        y += this.personalityHeight + this.gap;
        this.privacyY = y;
    }

    private static List<FormattedCharSequence> limit(List<FormattedCharSequence> lines, int max)
    {
        return lines.size() <= max ? lines : new ArrayList<>(lines.subList(0, max));
    }

    private int firstRowY()
    {
        return this.cardsTop + PAD + 2 * LINE + 2;
    }

    private void buildAi()
    {
        int x = this.left + PAD;
        int inner = this.cardWidth - 2 * PAD;
        int y = firstRowY();

        // Proveedor propio solo si ya lo tenia armado: se configura en "Mas opciones".
        List<AiProvider> offered = Arrays.stream(AiProvider.values())
                .filter(candidate -> candidate != AiProvider.CUSTOM || this.provider == AiProvider.CUSTOM)
                .toList();
        addRenderableWidget(CycleButton.builder(RoccoSetupScreen::providerName)
                .withValues(offered)
                .withInitialValue(this.provider)
                .displayOnlyValue()
                .withTooltip(value -> Tooltip.create(Component.translatable("rocco.setup.brain.provider.tip")))
                .create(x, y, inner, ROW, Component.translatable("rocco.setup.brain.title"), (button, value) -> switchProvider(value)));
        y += ROW + ROW_GAP;

        this.aiKey = secretBox(x, y, inner - SHOW_WIDTH - 2, this.keys.getOrDefault(this.provider, ""));
        this.aiKey.setEditable(this.provider != AiProvider.OLLAMA);
        this.aiKey.setResponder(text ->
        {
            this.aiCheck = Check.UNKNOWN;
            this.aiResult = Component.empty();
            refreshButtons();
        });
        addRenderableWidget(this.aiKey);
        addRenderableWidget(showButton(x + inner - SHOW_WIDTH, y, this.aiKey));
        y += ROW + ROW_GAP;

        int half = (inner - 2) / 2;
        this.aiGet = addRenderableWidget(Button.builder(aiGetLabel(), b -> openLink(this.provider.signupUrl()))
                .bounds(x, y, inner - half - 2, ROW).build());
        this.aiTest = addRenderableWidget(Button.builder(Component.translatable("rocco.setup.test"), b -> testAi())
                .bounds(x + inner - half, y, half, ROW).build());
    }

    private void buildVoice()
    {
        int x = this.rightCard + PAD;
        int inner = this.cardWidth - 2 * PAD;
        int y = firstRowY();

        this.fishKey = secretBox(x, y, inner - SHOW_WIDTH - 2, this.fishKeyText);
        this.fishKey.setResponder(text -> voiceEdited());
        addRenderableWidget(this.fishKey);
        addRenderableWidget(showButton(x + inner - SHOW_WIDTH, y, this.fishKey));
        y += ROW + ROW_GAP;

        this.fishVoice = new EditBox(this.font, x + 1, y + 1, inner - 2, ROW - 2, Component.translatable("rocco.setup.voice.id"));
        this.fishVoice.setMaxLength(512);
        this.fishVoice.setValue(this.fishVoiceText == null ? "" : this.fishVoiceText);
        this.fishVoice.setHint(Component.translatable("rocco.setup.voice.id").withStyle(ChatFormatting.DARK_GRAY));
        this.fishVoice.setTooltip(Tooltip.create(Component.translatable("rocco.setup.voice.id.tip")));
        this.fishVoice.setResponder(text -> voiceEdited());
        addRenderableWidget(this.fishVoice);
        y += ROW + ROW_GAP;

        int half = (inner - 2) / 2;
        addRenderableWidget(Button.builder(Component.translatable("rocco.setup.get_key"), b -> openLink(FISH_SIGNUP))
                .bounds(x, y, inner - half - 2, ROW).build());
        this.voiceTest = addRenderableWidget(Button.builder(Component.translatable("rocco.setup.voice.sample"), b -> testVoice())
                .bounds(x + inner - half, y, half, ROW).build());
    }

    // Ollama no tiene clave: el boton lleva a bajarlo.
    private Component aiGetLabel()
    {
        return Component.translatable(this.provider == AiProvider.OLLAMA ? "rocco.setup.download" : "rocco.setup.get_key");
    }

    private void voiceEdited()
    {
        this.voiceCheck = Check.UNKNOWN;
        this.voiceResult = Component.empty();
        refreshButtons();
    }

    private void buildPersonality()
    {
        int x = this.left + PAD;
        int inner = this.contentWidth - 2 * PAD;
        int half = (inner - 2) / 2;
        int y = this.personalityTop + PAD + LINE + 2;
        this.chillCard = addRenderableWidget(Button.builder(card(Traits.Mode.CHILL), b -> pickMode(Traits.Mode.CHILL))
                .bounds(x, y, inner - half - 2, ROW)
                .tooltip(Tooltip.create(Component.translatable("rocco.setup.personality.chill.example").withStyle(ChatFormatting.ITALIC)))
                .build());
        this.sinFiltroCard = addRenderableWidget(Button.builder(card(Traits.Mode.SIN_FILTRO), b -> pickMode(Traits.Mode.SIN_FILTRO))
                .bounds(x + inner - half, y, half, ROW)
                .tooltip(Tooltip.create(Component.translatable("rocco.setup.personality.sin_filtro.example").withStyle(ChatFormatting.ITALIC)))
                .build());
    }

    private void buildFooter()
    {
        int y = this.privacyY + this.privacy.size() * LINE + this.gap;
        int width = Math.min(150, (this.contentWidth - COLUMN_GAP) / 2);
        addRenderableWidget(Button.builder(Component.translatable("rocco.setup.advanced"), b -> openAdvanced())
                .bounds(this.width / 2 - width - COLUMN_GAP / 2, y, width, FOOTER_HEIGHT).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> finish())
                .bounds(this.width / 2 + COLUMN_GAP / 2, y, width, FOOTER_HEIGHT).build());
    }

    // ---------- piezas ----------

    // Las claves se ven con puntitos salvo que se apriete "Ver". Se pegan con Ctrl+V.
    private EditBox secretBox(int x, int y, int width, String value)
    {
        EditBox box = new EditBox(this.font, x + 1, y + 1, width - 2, ROW - 2, Component.translatable("rocco.setup.key"));
        box.setMaxLength(512);
        box.setValue(value == null ? "" : value);
        box.setFormatter((text, cursor) -> FormattedCharSequence.forward(this.shown.contains(box) ? text : "•".repeat(text.length()), Style.EMPTY));
        box.setHint(Component.translatable("rocco.config.paste_key").withStyle(ChatFormatting.DARK_GRAY));
        return box;
    }

    private Button showButton(int x, int y, EditBox box)
    {
        return Button.builder(Component.translatable(this.shown.contains(box) ? "rocco.setup.hide" : "rocco.setup.show"), b ->
        {
            if (!this.shown.remove(box))
            {
                this.shown.add(box);
            }
            b.setMessage(Component.translatable(this.shown.contains(box) ? "rocco.setup.hide" : "rocco.setup.show"));
        }).bounds(x, y, SHOW_WIDTH, ROW).build();
    }

    private static Component providerName(AiProvider option)
    {
        return option.freeHosted()
                ? Component.translatable("rocco.setup.brain.free", option.label())
                : Component.literal(option.label());
    }

    private Component card(Traits.Mode option)
    {
        Component name = Component.translatable("rocco.personality.mode." + option.name().toLowerCase(Locale.ROOT));
        return option == this.mode ? Component.literal("✔ ").append(name).withStyle(ChatFormatting.GREEN) : name;
    }

    private void pickMode(Traits.Mode option)
    {
        this.mode = option;
        this.chillCard.setMessage(card(Traits.Mode.CHILL));
        this.sinFiltroCard.setMessage(card(Traits.Mode.SIN_FILTRO));
    }

    private Component modeDescription()
    {
        return switch (this.mode)
        {
            case CHILL -> Component.translatable("rocco.setup.personality.chill");
            case SIN_FILTRO -> Component.translatable("rocco.setup.personality.sin_filtro");
            default -> Component.translatable("rocco.setup.personality.custom");
        };
    }

    private void refreshButtons()
    {
        if (this.aiTest != null && this.aiKey != null)
        {
            this.aiTest.active = this.provider == AiProvider.OLLAMA || !this.aiKey.getValue().isBlank();
        }
        if (this.voiceTest != null && this.fishKey != null && this.fishVoice != null)
        {
            this.voiceTest.active = !this.fishKey.getValue().isBlank() && !this.fishVoice.getValue().isBlank();
        }
    }

    private void openLink(String url)
    {
        if (url != null && !url.isBlank())
        {
            rememberFields();
            ConfirmLinkScreen.confirmLinkNow(url, this, true);
        }
    }

    private void openAdvanced()
    {
        applyToConfig();
        RoccoConfigScreen.saveAll();
        this.reloadOnReturn = true;
        this.minecraft.setScreen(new RoccoConfigScreen(this));
    }

    private static boolean onRemoteServer()
    {
        Minecraft mc = Minecraft.getInstance();
        return mc.getCurrentServer() != null && !mc.isLocalServer();
    }

    // ---------- proveedor de IA ----------

    private void switchProvider(AiProvider next)
    {
        rememberAiKey();
        this.provider = next;
        this.aiKey.setValue(this.keys.getOrDefault(next, ""));
        // Ollama corre en tu PC: no tiene clave.
        this.aiKey.setEditable(next != AiProvider.OLLAMA);
        this.aiGet.setMessage(aiGetLabel());
        this.aiCheck = Check.UNKNOWN;
        this.aiResult = next == AiProvider.OLLAMA
                ? Component.translatable("rocco.setup.brain.ollama").withStyle(ChatFormatting.GRAY)
                : Component.empty();
        refreshButtons();
    }

    private void rememberAiKey()
    {
        if (this.aiKey != null)
        {
            this.keys.put(this.provider, this.aiKey.getValue().trim());
        }
    }

    // Si la ventana cambia de tamaño, init arma todo de nuevo: lo escrito no se pierde.
    private void rememberFields()
    {
        if (this.aiKey == null)
        {
            return;
        }
        rememberAiKey();
        this.fishKeyText = this.fishKey.getValue();
        this.fishVoiceText = this.fishVoice.getValue();
    }

    @Nullable
    private static ForgeConfigSpec.ConfigValue<String> keyOf(AiProvider provider)
    {
        return switch (provider)
        {
            case GEMINI -> RoccoCommonConfig.GEMINI_API_KEY;
            case GROQ -> RoccoCommonConfig.GROQ_API_KEY;
            case OPENROUTER -> RoccoCommonConfig.OPENROUTER_API_KEY;
            case CUSTOM -> RoccoCommonConfig.CUSTOM_API_KEY;
            case OLLAMA -> null;
        };
    }

    // ---------- pruebas (en segundo plano: nunca congelan el juego) ----------

    private void testAi()
    {
        applyAiKeys();
        if (this.provider == AiProvider.OLLAMA)
        {
            RoccoCommonConfig.OLLAMA_ENABLED.set(true);
        }
        this.aiCheck = Check.UNKNOWN;
        this.aiResult = Component.translatable("rocco.setup.testing").withStyle(ChatFormatting.GRAY);
        AiProbe.provider(this.provider).thenAccept(result -> this.minecraft.execute(() ->
        {
            this.aiCheck = result.ok() ? Check.WORKS : Check.FAILED;
            this.aiResult = result.message().copy().withStyle(result.ok() ? ChatFormatting.GREEN : ChatFormatting.RED);
        }));
    }

    private void testVoice()
    {
        RoccoCommonConfig.FISH_API_KEY.set(this.fishKey.getValue().trim());
        RoccoCommonConfig.FISH_VOICE_ID.set(this.fishVoice.getValue().trim());
        this.voiceCheck = Check.UNKNOWN;
        this.voiceResult = Component.translatable("rocco.setup.testing").withStyle(ChatFormatting.GRAY);
        String sample = net.minecraft.client.resources.language.I18n.get("rocco.setup.voice.sample_line");
        java.util.concurrent.CompletableFuture.supplyAsync(() -> FishAudio.synthesize(sample))
                .thenAccept(result -> this.minecraft.execute(() ->
                {
                    if (result.pcm() == null)
                    {
                        this.voiceCheck = Check.FAILED;
                        this.voiceResult = Component.literal(result.error()).withStyle(ChatFormatting.RED);
                        return;
                    }
                    this.voiceCheck = Check.WORKS;
                    this.voiceResult = Component.translatable("rocco.setup.voice.ok").withStyle(ChatFormatting.GREEN);
                    VoicePlayback.playPreview(result.pcm().data(), result.pcm().sampleRate());
                }));
    }

    // ---------- guardar ----------

    private void applyAiKeys()
    {
        rememberAiKey();
        for (AiProvider candidate : AiProvider.values())
        {
            ForgeConfigSpec.ConfigValue<String> key = keyOf(candidate);
            if (key != null)
            {
                key.set(this.keys.getOrDefault(candidate, ""));
            }
        }
    }

    private void applyToConfig()
    {
        applyAiKeys();
        if (this.provider == AiProvider.OLLAMA)
        {
            RoccoCommonConfig.OLLAMA_ENABLED.set(true);
        }
        // Si elegiste un proveedor fijo en "Mas opciones", pasa a ser este; en automatico sigue asi.
        if (RoccoCommonConfig.AI_PROVIDER.get() != RoccoCommonConfig.AiProviderChoice.AUTO)
        {
            RoccoCommonConfig.AI_PROVIDER.set(RoccoCommonConfig.AiProviderChoice.valueOf(this.provider.name()));
        }
        // El elegido va primero en la cola; los demas quedan de respaldo en el orden que tenian.
        String order = this.provider.id() + "," + RoccoCommonConfig.AI_PROVIDER_ORDER.get()
                .replaceAll("(^|,)\\s*" + this.provider.id() + "\\s*(?=,|$)", "");
        RoccoCommonConfig.AI_PROVIDER_ORDER.set(order.replaceAll(",+", ",").replaceAll(",$", ""));

        String fish = this.fishKey.getValue().trim();
        RoccoCommonConfig.FISH_API_KEY.set(fish);
        RoccoCommonConfig.FISH_VOICE_ID.set(this.fishVoice.getValue().trim());
        // Pegar una clave nueva prende la voz; si ya la tenias y la apagaste en la config, no se toca.
        if (!this.hadFishKey && !fish.isEmpty())
        {
            RoccoCommonConfig.VOICE_ENABLED.set(true);
        }
        RoccoCommonConfig.PERSONALITY_MODE.set(this.mode);
        RoccoClientConfig.SETUP_COMPLETED.set(true);
        RoccoClientConfig.SETUP_VERSION.set(RoccoClientConfig.CURRENT_SETUP_VERSION);
    }

    private void finish()
    {
        applyToConfig();
        RoccoConfigScreen.saveAll();
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void onClose()
    {
        finish();
    }

    // ---------- dibujo ----------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);
        drawTitle(graphics);

        int y = this.subtitleY;
        for (FormattedCharSequence line : this.subtitle)
        {
            graphics.drawCenteredString(this.font, line, this.width / 2, y, 0xFFFFFF);
            y += LINE;
        }

        // El resultado de una prueba reemplaza a la descripcion de la tarjeta.
        drawCard(graphics, this.left, this.cardsTop, this.cardWidth, this.cardHeight, Component.translatable("rocco.setup.brain.title"),
                this.aiResult.getString().isEmpty() ? Component.translatable("rocco.setup.brain.text") : this.aiResult, aiBadge());
        drawCard(graphics, this.rightCard, this.cardsTop, this.cardWidth, this.cardHeight, Component.translatable("rocco.setup.voice.title"),
                this.voiceResult.getString().isEmpty() ? Component.translatable("rocco.setup.voice.text") : this.voiceResult, voiceBadge());
        drawCard(graphics, this.left, this.personalityTop, this.contentWidth, this.personalityHeight,
                Component.translatable("rocco.setup.personality.title"), null, null);

        graphics.drawString(this.font, modeDescription(), this.left + PAD, this.personalityTop + this.personalityHeight - LINE - 2, 0xAAAAAA);

        y = this.privacyY;
        for (FormattedCharSequence line : this.privacy)
        {
            graphics.drawCenteredString(this.font, line, this.width / 2, y, 0xFFFFFF);
            y += LINE;
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        int resultY = this.cardsTop + PAD + LINE;
        resultTooltip(graphics, this.aiResult, this.left + PAD, resultY, mouseX, mouseY);
        resultTooltip(graphics, this.voiceResult, this.rightCard + PAD, resultY, mouseX, mouseY);
    }

    private void drawTitle(GuiGraphics graphics)
    {
        Component title = this.title.copy().withStyle(ChatFormatting.BOLD);
        if (!this.bigTitle)
        {
            graphics.drawCenteredString(this.font, title, this.width / 2, this.titleY, 0xFFFFFF);
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(this.width / 2.0F, this.titleY, 0.0F);
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        graphics.drawCenteredString(this.font, title, 0, 0, 0xFFFFFF);
        graphics.pose().popPose();
    }

    private void drawCard(GuiGraphics graphics, int x, int y, int width, int height, Component title,
                          @Nullable Component text, @Nullable Component badge)
    {
        graphics.fill(x, y, x + width, y + height, CARD_FILL);
        graphics.fill(x, y, x + width, y + 1, CARD_EDGE);
        graphics.fill(x, y + height - 1, x + width, y + height, CARD_EDGE);
        graphics.fill(x, y + 1, x + 1, y + height - 1, CARD_EDGE);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, CARD_EDGE);

        graphics.drawString(this.font, title.copy().withStyle(ChatFormatting.BOLD), x + PAD, y + PAD, 0xFFFFFF);
        if (badge != null)
        {
            graphics.drawString(this.font, badge, x + width - PAD - this.font.width(badge), y + PAD, 0xFFFFFF);
        }
        if (text != null)
        {
            graphics.drawString(this.font, clip(text, width - 2 * PAD), x + PAD, y + PAD + LINE, 0xAAAAAA);
        }
    }

    private FormattedCharSequence clip(Component text, int width)
    {
        if (this.font.width(text) <= width)
        {
            return text.getVisualOrderText();
        }
        String cut = this.font.plainSubstrByWidth(text.getString(), width - this.font.width("...")) + "...";
        return Component.literal(cut).withStyle(text.getStyle()).getVisualOrderText();
    }

    // Un error largo se corta en la tarjeta: entero, al pasar el mouse.
    private void resultTooltip(GuiGraphics graphics, Component result, int x, int y, int mouseX, int mouseY)
    {
        int width = this.cardWidth - 2 * PAD;
        if (this.font.width(result) > width && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + LINE)
        {
            graphics.renderTooltip(this.font, this.font.split(result, 220), mouseX, mouseY);
        }
    }

    private Component aiBadge()
    {
        boolean hasKey = this.provider == AiProvider.OLLAMA || (this.aiKey != null && !this.aiKey.getValue().isBlank());
        if (this.aiCheck == Check.WORKS)
        {
            return badge("✔ ", "rocco.setup.badge.works", ChatFormatting.GREEN);
        }
        if (this.aiCheck == Check.FAILED)
        {
            return badge("✘ ", "rocco.setup.badge.failed", ChatFormatting.RED);
        }
        return hasKey && this.provider != AiProvider.OLLAMA
                ? badge("✔ ", "rocco.setup.badge.has_key", ChatFormatting.GREEN)
                : badge("", "rocco.setup.badge.optional", ChatFormatting.GRAY);
    }

    private Component voiceBadge()
    {
        if (this.voiceCheck == Check.WORKS)
        {
            return badge("✔ ", "rocco.setup.badge.works", ChatFormatting.GREEN);
        }
        if (this.voiceCheck == Check.FAILED)
        {
            return badge("✘ ", "rocco.setup.badge.failed", ChatFormatting.RED);
        }
        boolean key = this.fishKey != null && !this.fishKey.getValue().isBlank();
        boolean id = this.fishVoice != null && !this.fishVoice.getValue().isBlank();
        if (key && !id)
        {
            return badge("", "rocco.setup.badge.needs_id", ChatFormatting.GOLD);
        }
        return key
                ? badge("✔ ", "rocco.setup.badge.has_key", ChatFormatting.GREEN)
                : badge("", "rocco.setup.badge.optional", ChatFormatting.GRAY);
    }

    private static Component badge(String mark, String key, ChatFormatting color)
    {
        return Component.literal(mark).append(Component.translatable(key)).withStyle(color);
    }
}

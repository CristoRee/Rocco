package com.cristoree.rocco.client.setup;

import com.cristoree.rocco.ai.AiProbe;
import com.cristoree.rocco.ai.AiProvider;
import com.cristoree.rocco.ai.personality.Traits;
import com.cristoree.rocco.client.config.RoccoConfigScreen;
import com.cristoree.rocco.client.voice.MicRecorder;
import com.cristoree.rocco.client.voice.RoccoKeys;
import com.cristoree.rocco.client.voice.VoicePlayback;
import com.cristoree.rocco.config.RoccoClientConfig;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.voice.Deepgram;
import com.cristoree.rocco.voice.FishAudio;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

// La bienvenida: lo minimo para que Rocco piense, hable y escuche, cada cosa con su boton para
// probarla, y la eleccion entre Chill y Sin filtro. Aparece la primera vez en el menu principal,
// se abre cuando quieras con /rocco setup o desde la config, y nunca traba el juego: "Mas tarde"
// la cierra. Todo lo que se toca queda recien al apretar "Guardar y jugar".
public class RoccoSetupScreen extends Screen
{
    private static final int FIELD_WIDTH = 150;
    private static final String FISH_SIGNUP = "https://fish.audio/app/api-keys/";
    private static final String DEEPGRAM_SIGNUP = "https://console.deepgram.com/signup";

    private final Screen parent;
    private SetupList list;

    // Lo que el jugador va escribiendo, por proveedor: cambiar de proveedor no borra lo escrito.
    private final Map<AiProvider, String> keys = new EnumMap<>(AiProvider.class);
    private final Map<AiProvider, String> models = new EnumMap<>(AiProvider.class);
    private AiProvider provider;
    private boolean fallback;
    private boolean voiceOn;
    private boolean micOn;
    private boolean requireName;
    private Traits.Mode mode;
    private Traits.Dialect dialect;

    private EditBox aiKey;
    private EditBox aiModel;
    private EditBox fishKey;
    private EditBox fishVoice;
    private EditBox deepgramKey;
    private EditBox micLanguage;
    private EditBox micDevice;
    private Button chillCard;
    private Button sinFiltroCard;

    private Component aiResult = Component.empty();
    private Component voiceResult = Component.empty();
    private Component micResult = Component.empty();
    private boolean aiTested;

    // Lo escrito en los demas campos: si la ventana cambia de tamaño, init arma todo de nuevo.
    private String fishKeyText;
    private String fishVoiceText;
    private String deepgramText;
    private String languageText;
    private String deviceText;

    public RoccoSetupScreen(Screen parent)
    {
        super(Component.translatable("rocco.setup.title"));
        this.parent = parent;

        for (AiProvider candidate : AiProvider.values())
        {
            ForgeConfigSpec.ConfigValue<String> key = keyOf(candidate);
            this.keys.put(candidate, key == null ? "" : key.get());
            this.models.put(candidate, modelOf(candidate).get());
        }
        this.provider = firstConfigured();
        this.fallback = RoccoCommonConfig.AI_PROVIDER.get() == RoccoCommonConfig.AiProviderChoice.AUTO;
        this.voiceOn = RoccoCommonConfig.VOICE_ENABLED.get();
        this.micOn = RoccoCommonConfig.STT_ENABLED.get();
        this.requireName = RoccoCommonConfig.CHAT_REQUIRE_NAME_MENTION.get();
        this.mode = RoccoCommonConfig.PERSONALITY_MODE.get();
        this.dialect = RoccoCommonConfig.DIALECT.get();
        this.fishKeyText = RoccoCommonConfig.FISH_API_KEY.get();
        this.fishVoiceText = RoccoCommonConfig.FISH_VOICE_ID.get();
        this.deepgramText = RoccoCommonConfig.DEEPGRAM_API_KEY.get();
        this.languageText = RoccoCommonConfig.STT_LANGUAGE.get();
        this.deviceText = RoccoClientConfig.MIC_DEVICE.get();
    }

    // El que ya tiene clave; si no hay ninguno, Groq (gratis y rapido).
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
        rememberFields();
        this.list = new SetupList(this.minecraft, this.width, this.height, 32, this.height - 34);
        build();
        addRenderableWidget(this.list);

        int buttonWidth = Math.min(120, (this.width - 24) / 3);
        int left = this.width / 2 - (3 * buttonWidth + 8) / 2;
        addRenderableWidget(Button.builder(Component.translatable("rocco.setup.save"), b -> save())
                .bounds(left, this.height - 28, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("rocco.setup.later"), b -> later())
                .bounds(left + buttonWidth + 4, this.height - 28, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("rocco.setup.advanced"),
                        b -> this.minecraft.setScreen(new RoccoConfigScreen(this)))
                .bounds(left + 2 * (buttonWidth + 4), this.height - 28, buttonWidth, 20).build());
    }

    private void build()
    {
        SetupList l = this.list;

        // 1. Bienvenida.
        l.heading(Component.translatable("rocco.setup.welcome.title"));
        l.text(Component.translatable("rocco.setup.welcome.text"));
        if (onRemoteServer())
        {
            l.text(Component.translatable("rocco.setup.remote").withStyle(ChatFormatting.GOLD));
        }

        // 2. Cerebro: con que IA piensa. El selector sale de la lista de proveedores del mod.
        l.heading(Component.translatable("rocco.setup.brain.title"));
        l.text(Component.translatable("rocco.setup.brain.text"));
        CycleButton<AiProvider> providers = CycleButton.builder((AiProvider p) -> Component.literal(p.label()))
                .withValues(AiProvider.values())
                .withInitialValue(this.provider)
                .displayOnlyValue()
                .create(0, 0, FIELD_WIDTH, 20, Component.translatable("rocco.setup.brain.provider"), (button, value) -> switchProvider(value));
        l.row(Component.translatable("rocco.setup.brain.provider"), providers);

        this.aiKey = secretBox(this.keys.getOrDefault(this.provider, ""));
        l.row(Component.translatable("rocco.setup.key"), this.aiKey, showButton(this.aiKey));
        this.aiModel = box(this.models.getOrDefault(this.provider, "auto"), 120);
        this.aiModel.setTooltip(Tooltip.create(Component.translatable("rocco.setup.brain.model.tip")));
        l.row(Component.translatable("rocco.setup.brain.model"), this.aiModel);
        l.row(Component.empty(),
                Button.builder(Component.translatable("rocco.setup.get_key"), b -> openLink(this.provider.signupUrl()))
                        .bounds(0, 0, 110, 20).build(),
                Button.builder(Component.translatable("rocco.setup.test"), b -> testAi()).bounds(0, 0, 80, 20).build());
        l.live(() -> this.aiResult);
        Button fallbackButton = toggle(this.fallback, value -> this.fallback = value);
        fallbackButton.setTooltip(Tooltip.create(Component.translatable("rocco.setup.brain.fallback.tip")));
        l.row(Component.translatable("rocco.setup.brain.fallback"), fallbackButton);

        // 3. Voz.
        l.heading(Component.translatable("rocco.setup.voice.title"));
        l.text(Component.translatable("rocco.setup.voice.text"));
        CycleButton<Boolean> voice = CycleButton.booleanBuilder(Component.literal("Fish Audio"),
                        Component.translatable("rocco.setup.voice.none"))
                .withInitialValue(this.voiceOn)
                .displayOnlyValue()
                .create(0, 0, FIELD_WIDTH, 20, Component.translatable("rocco.setup.voice.provider"), (button, value) -> this.voiceOn = value);
        l.row(Component.translatable("rocco.setup.voice.provider"), voice);
        this.fishKey = secretBox(this.fishKeyText);
        l.row(Component.translatable("rocco.setup.key"), this.fishKey, showButton(this.fishKey));
        this.fishVoice = box(this.fishVoiceText, FIELD_WIDTH);
        this.fishVoice.setTooltip(Tooltip.create(Component.translatable("rocco.setup.voice.id.tip")));
        l.row(Component.translatable("rocco.setup.voice.id"), this.fishVoice);
        l.row(Component.empty(),
                Button.builder(Component.translatable("rocco.setup.get_key"), b -> openLink(FISH_SIGNUP)).bounds(0, 0, 110, 20).build(),
                Button.builder(Component.translatable("rocco.setup.voice.sample"), b -> testVoice()).bounds(0, 0, 110, 20).build());
        l.live(() -> this.voiceResult);

        // 4. Hablarle.
        l.heading(Component.translatable("rocco.setup.talk.title"));
        l.text(Component.translatable("rocco.setup.talk.text"));
        l.row(Component.translatable("rocco.setup.talk.name"), toggle(this.requireName, value -> this.requireName = value));
        l.row(Component.translatable("rocco.setup.mic.enabled"), toggle(this.micOn, value -> this.micOn = value));
        this.deepgramKey = secretBox(this.deepgramText);
        l.row(Component.translatable("rocco.setup.key"), this.deepgramKey, showButton(this.deepgramKey));
        this.micLanguage = box(this.languageText, 60);
        this.micLanguage.setTooltip(Tooltip.create(Component.translatable("rocco.setup.mic.language.tip")));
        l.row(Component.translatable("rocco.setup.mic.language"), this.micLanguage);
        l.row(Component.translatable("rocco.setup.mic.key"), Button.builder(RoccoKeys.TALK.getTranslatedKeyMessage(),
                b -> this.minecraft.setScreen(new KeyBindsScreen(this, this.minecraft.options))).bounds(0, 0, 110, 20)
                .tooltip(Tooltip.create(Component.translatable("rocco.setup.mic.key.tip"))).build());
        this.micDevice = box(this.deviceText, FIELD_WIDTH);
        this.micDevice.setHint(Component.translatable("rocco.setup.mic.device.hint").withStyle(ChatFormatting.DARK_GRAY));
        l.row(Component.translatable("rocco.setup.mic.device"), this.micDevice);
        l.row(Component.empty(),
                Button.builder(Component.translatable("rocco.setup.get_key"), b -> openLink(DEEPGRAM_SIGNUP)).bounds(0, 0, 110, 20).build(),
                Button.builder(Component.translatable("rocco.setup.mic.test"), b -> testMic()).bounds(0, 0, 110, 20).build());
        l.live(() -> this.micResult);

        // 5. Personalidad.
        l.heading(Component.translatable("rocco.setup.personality.title"));
        int cardWidth = Math.min(160, (this.list.getRowWidth() - 12) / 2);
        this.chillCard = Button.builder(card(Traits.Mode.CHILL), b -> pickMode(Traits.Mode.CHILL))
                .bounds(0, 0, cardWidth, 20).build();
        this.sinFiltroCard = Button.builder(card(Traits.Mode.SIN_FILTRO), b -> pickMode(Traits.Mode.SIN_FILTRO))
                .bounds(0, 0, cardWidth, 20).build();
        l.row(Component.empty(), this.chillCard, this.sinFiltroCard);
        l.text(Component.translatable("rocco.setup.personality.chill").withStyle(ChatFormatting.GREEN));
        l.text(Component.translatable("rocco.setup.personality.chill.example").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        l.text(Component.translatable("rocco.setup.personality.sin_filtro").withStyle(ChatFormatting.RED));
        l.text(Component.translatable("rocco.setup.personality.sin_filtro.example").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        l.text(Component.translatable("rocco.setup.personality.later"));

        // 6. Idioma.
        l.heading(Component.translatable("rocco.setup.language.title"));
        CycleButton<Traits.Dialect> dialects = CycleButton.builder((Traits.Dialect d) ->
                        Component.translatable("rocco.value.Dialect." + d.name()))
                .withValues(Traits.Dialect.values())
                .withInitialValue(this.dialect)
                .displayOnlyValue()
                .create(0, 0, FIELD_WIDTH, 20, Component.translatable("rocco.config.trait.dialect"), (button, value) -> this.dialect = value);
        l.row(Component.translatable("rocco.config.trait.dialect"), dialects);

        // 7. Resumen, en vivo.
        l.heading(Component.translatable("rocco.setup.summary.title"));
        l.live(() -> status(brainReady(), "rocco.setup.summary.brain").copy().append(this.aiTested
                ? Component.translatable("rocco.setup.summary.tested").withStyle(ChatFormatting.GRAY)
                : Component.empty()));
        l.live(() -> status(this.voiceOn && !this.fishKey.getValue().isBlank() && !this.fishVoice.getValue().isBlank(),
                "rocco.setup.summary.voice"));
        l.live(() -> status(this.micOn && !this.deepgramKey.getValue().isBlank(), "rocco.setup.summary.mic"));

        // 8. Privacidad.
        l.heading(Component.translatable("rocco.setup.privacy.title"));
        l.text(Component.translatable("rocco.setup.privacy.text"));
    }

    // ---------- piezas ----------

    private EditBox box(String value, int width)
    {
        EditBox box = new EditBox(this.font, 0, 0, width, 20, Component.empty());
        box.setMaxLength(512);
        box.setValue(value == null ? "" : value);
        return box;
    }

    // Las claves se ven con puntitos salvo que se apriete "Mostrar". Se pueden pegar con Ctrl+V.
    private EditBox secretBox(String value)
    {
        EditBox box = box(value, FIELD_WIDTH - 54);
        box.setFormatter((text, cursor) -> FormattedCharSequence.forward(revealed(box) ? text : "•".repeat(text.length()), Style.EMPTY));
        box.setHint(Component.translatable("rocco.config.paste_key").withStyle(ChatFormatting.DARK_GRAY));
        return box;
    }

    private final java.util.Set<EditBox> shown = new java.util.HashSet<>();

    private boolean revealed(EditBox box)
    {
        return this.shown.contains(box);
    }

    private Button showButton(EditBox box)
    {
        return Button.builder(Component.translatable("rocco.setup.show"), b ->
        {
            if (!this.shown.remove(box))
            {
                this.shown.add(box);
            }
            b.setMessage(Component.translatable(revealed(box) ? "rocco.setup.hide" : "rocco.setup.show"));
        }).bounds(0, 0, 50, 20).build();
    }

    private Button toggle(boolean initial, java.util.function.Consumer<Boolean> onChange)
    {
        boolean[] value = {initial};
        Button button = Button.builder(yesNo(initial), b ->
        {
            value[0] = !value[0];
            b.setMessage(yesNo(value[0]));
            onChange.accept(value[0]);
        }).bounds(0, 0, 60, 20).build();
        return button;
    }

    private static Component yesNo(boolean on)
    {
        return on
                ? Component.translatable("rocco.config.yes").withStyle(ChatFormatting.GREEN)
                : Component.translatable("rocco.config.no").withStyle(ChatFormatting.RED);
    }

    private Component card(Traits.Mode option)
    {
        Component name = Component.translatable("rocco.personality.mode." + option.name().toLowerCase(Locale.ROOT));
        return option == this.mode ? Component.literal("» ").append(name).append(" «").withStyle(ChatFormatting.GREEN) : name;
    }

    private void pickMode(Traits.Mode option)
    {
        this.mode = option;
        this.chillCard.setMessage(card(Traits.Mode.CHILL));
        this.sinFiltroCard.setMessage(card(Traits.Mode.SIN_FILTRO));
    }

    private static Component status(boolean ok, String key)
    {
        return Component.literal(ok ? "✔ " : "✘ ").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(Component.translatable(key).withStyle(ChatFormatting.WHITE));
    }

    private boolean brainReady()
    {
        rememberAiFields();
        for (AiProvider candidate : AiProvider.values())
        {
            boolean hasKey = !this.keys.getOrDefault(candidate, "").isBlank();
            if (hasKey && candidate != AiProvider.OLLAMA && candidate != AiProvider.CUSTOM)
            {
                return true;
            }
        }
        return this.provider == AiProvider.OLLAMA || (this.provider == AiProvider.CUSTOM && !RoccoCommonConfig.CUSTOM_URL.get().isBlank());
    }

    private void openLink(String url)
    {
        if (url != null && !url.isBlank())
        {
            ConfirmLinkScreen.confirmLinkNow(url, this, true);
        }
    }

    private static boolean onRemoteServer()
    {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        return mc.getCurrentServer() != null && !mc.isLocalServer();
    }

    // ---------- proveedor de IA ----------

    private void switchProvider(AiProvider next)
    {
        rememberAiFields();
        this.provider = next;
        this.aiKey.setValue(this.keys.getOrDefault(next, ""));
        this.aiModel.setValue(this.models.getOrDefault(next, "auto"));
        // Ollama corre en tu PC: no tiene clave.
        this.aiKey.setEditable(next != AiProvider.OLLAMA);
        this.aiResult = next == AiProvider.OLLAMA
                ? Component.translatable("rocco.setup.brain.ollama").withStyle(ChatFormatting.GRAY)
                : Component.empty();
    }

    private void rememberAiFields()
    {
        if (this.aiKey != null)
        {
            this.keys.put(this.provider, this.aiKey.getValue().trim());
            this.models.put(this.provider, this.aiModel.getValue().trim().isEmpty() ? "auto" : this.aiModel.getValue().trim());
        }
    }

    // Si la ventana cambia de tamaño, init arma todo de nuevo: lo escrito no se pierde.
    private void rememberFields()
    {
        if (this.aiKey == null)
        {
            return;
        }
        rememberAiFields();
        this.fishKeyText = this.fishKey.getValue();
        this.fishVoiceText = this.fishVoice.getValue();
        this.deepgramText = this.deepgramKey.getValue();
        this.languageText = this.micLanguage.getValue();
        this.deviceText = this.micDevice.getValue();
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

    private static ForgeConfigSpec.ConfigValue<String> modelOf(AiProvider provider)
    {
        return switch (provider)
        {
            case GEMINI -> RoccoCommonConfig.GEMINI_MODEL;
            case GROQ -> RoccoCommonConfig.GROQ_MODEL;
            case OPENROUTER -> RoccoCommonConfig.OPENROUTER_MODEL;
            case OLLAMA -> RoccoCommonConfig.OLLAMA_MODEL;
            case CUSTOM -> RoccoCommonConfig.CUSTOM_MODEL;
        };
    }

    // ---------- pruebas (en segundo plano: nunca congelan el juego) ----------

    private void applyAiToConfig()
    {
        rememberAiFields();
        for (AiProvider candidate : AiProvider.values())
        {
            ForgeConfigSpec.ConfigValue<String> key = keyOf(candidate);
            if (key != null)
            {
                key.set(this.keys.getOrDefault(candidate, ""));
            }
            modelOf(candidate).set(this.models.getOrDefault(candidate, "auto"));
        }
        if (this.provider == AiProvider.OLLAMA)
        {
            RoccoCommonConfig.OLLAMA_ENABLED.set(true);
        }
    }

    private void testAi()
    {
        applyAiToConfig();
        this.aiResult = Component.translatable("rocco.setup.testing").withStyle(ChatFormatting.GRAY);
        AiProbe.provider(this.provider).thenAccept(result -> this.minecraft.execute(() ->
        {
            this.aiTested = result.ok();
            this.aiResult = result.message().copy().withStyle(result.ok() ? ChatFormatting.GREEN : ChatFormatting.RED);
        }));
    }

    private void testVoice()
    {
        RoccoCommonConfig.FISH_API_KEY.set(this.fishKey.getValue().trim());
        RoccoCommonConfig.FISH_VOICE_ID.set(this.fishVoice.getValue().trim());
        this.voiceResult = Component.translatable("rocco.setup.testing").withStyle(ChatFormatting.GRAY);
        String sample = net.minecraft.client.resources.language.I18n.get("rocco.setup.voice.sample_line");
        java.util.concurrent.CompletableFuture.supplyAsync(() -> FishAudio.synthesize(sample))
                .thenAccept(result -> this.minecraft.execute(() ->
                {
                    if (result.pcm() == null)
                    {
                        this.voiceResult = Component.literal(result.error()).withStyle(ChatFormatting.RED);
                        return;
                    }
                    this.voiceResult = Component.translatable("rocco.setup.voice.ok").withStyle(ChatFormatting.GREEN);
                    VoicePlayback.playPreview(result.pcm().data(), result.pcm().sampleRate());
                }));
    }

    private void testMic()
    {
        RoccoCommonConfig.DEEPGRAM_API_KEY.set(this.deepgramKey.getValue().trim());
        RoccoCommonConfig.STT_LANGUAGE.set(this.micLanguage.getValue().trim());
        RoccoClientConfig.MIC_DEVICE.set(this.micDevice.getValue().trim());
        this.micResult = Component.translatable("rocco.setup.mic.recording").withStyle(ChatFormatting.GOLD);
        MicRecorder.recordSample(4)
                .thenApply(wav ->
                {
                    this.minecraft.execute(() -> this.micResult = Component.translatable("rocco.setup.testing").withStyle(ChatFormatting.GRAY));
                    return Deepgram.transcribe(wav);
                })
                .whenComplete((result, error) -> this.minecraft.execute(() ->
                {
                    if (error != null)
                    {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        this.micResult = Component.translatable("rocco.setup.mic.no_mic", String.valueOf(cause.getMessage()))
                                .withStyle(ChatFormatting.RED);
                    }
                    else if (result.text() == null)
                    {
                        this.micResult = Component.literal(String.valueOf(result.error())).withStyle(ChatFormatting.RED);
                    }
                    else if (result.text().isBlank())
                    {
                        this.micResult = Component.translatable("rocco.setup.mic.silence").withStyle(ChatFormatting.GOLD);
                    }
                    else
                    {
                        this.micResult = Component.translatable("rocco.setup.mic.heard", result.text()).withStyle(ChatFormatting.GREEN);
                    }
                }));
    }

    // ---------- guardar ----------

    private void save()
    {
        applyAiToConfig();
        RoccoCommonConfig.AI_PROVIDER.set(this.fallback
                ? RoccoCommonConfig.AiProviderChoice.AUTO
                : RoccoCommonConfig.AiProviderChoice.valueOf(this.provider.name()));
        // El elegido va primero en la cola; los demas quedan de respaldo en el orden que tenian.
        String order = this.provider.id() + "," + RoccoCommonConfig.AI_PROVIDER_ORDER.get()
                .replaceAll("(^|,)\\s*" + this.provider.id() + "\\s*(?=,|$)", "");
        RoccoCommonConfig.AI_PROVIDER_ORDER.set(order.replaceAll(",+", ",").replaceAll(",$", ""));

        RoccoCommonConfig.VOICE_ENABLED.set(this.voiceOn);
        RoccoCommonConfig.FISH_API_KEY.set(this.fishKey.getValue().trim());
        RoccoCommonConfig.FISH_VOICE_ID.set(this.fishVoice.getValue().trim());
        RoccoCommonConfig.CHAT_REQUIRE_NAME_MENTION.set(this.requireName);
        RoccoCommonConfig.STT_ENABLED.set(this.micOn);
        RoccoCommonConfig.DEEPGRAM_API_KEY.set(this.deepgramKey.getValue().trim());
        RoccoCommonConfig.STT_LANGUAGE.set(this.micLanguage.getValue().trim().isEmpty() ? "es" : this.micLanguage.getValue().trim());
        RoccoClientConfig.MIC_DEVICE.set(this.micDevice.getValue().trim());
        RoccoCommonConfig.PERSONALITY_MODE.set(this.mode);
        RoccoCommonConfig.DIALECT.set(this.dialect);

        markSeen();
        RoccoConfigScreen.saveAll();
        this.minecraft.setScreen(this.parent);
    }

    // "Mas tarde": no vuelve a aparecer sola hasta que se use /rocco setup.
    private void later()
    {
        markSeen();
        RoccoClientConfig.SPEC.save();
        this.minecraft.setScreen(this.parent);
    }

    private static void markSeen()
    {
        RoccoClientConfig.SETUP_COMPLETED.set(true);
        RoccoClientConfig.SETUP_VERSION.set(RoccoClientConfig.CURRENT_SETUP_VERSION);
    }

    @Override
    public void onClose()
    {
        later();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), this.width / 2, 8, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("rocco.setup.subtitle").withStyle(ChatFormatting.GRAY),
                this.width / 2, 20, 0xFFFFFF);
    }
}

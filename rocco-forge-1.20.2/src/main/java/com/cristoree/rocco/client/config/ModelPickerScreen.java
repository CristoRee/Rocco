package com.cristoree.rocco.client.config;

import com.cristoree.rocco.ai.AiProvider;
import com.cristoree.rocco.ai.ModelResolver;
import com.cristoree.rocco.client.Tr;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

// Elegir el modelo de un proveedor sin salir a buscarlo por internet: primero los que se sabe que
// andan gratis (con una nota de que es cada uno) y, con un boton, los que ofrece hoy tu cuenta.
public class ModelPickerScreen extends Screen
{
    private static final int ROW = 22;
    private static final int WIDTH = 300;

    // Los recomendados de cada proveedor: modelo -> nota (texto en castellano, se traduce).
    private static final Map<String, Map<String, String>> SUGGESTED = Map.of(
            "gemini", ordered(
                    "gemma-4-26b-a4b-it", "Gratis, rápido y con mucho cupo por día. Recomendado.",
                    "gemini-2.5-flash-lite", "Gratis y muy rápido.",
                    "auto", "Elige solo el mejor Gemini flash que haya hoy."),
            "groq", ordered(
                    "openai/gpt-oss-120b", "El más inteligente de los gratis de Groq. Recomendado.",
                    "openai/gpt-oss-20b", "Más liviano y más rápido.",
                    "llama-3.3-70b-versatile", "Bueno para charlar, sin razonar antes.",
                    "auto", "Elige solo el mejor que haya hoy."),
            "openrouter", ordered(
                    "openrouter/free", "Elige solo entre los modelos gratis del momento. Recomendado."),
            "ollama", ordered(
                    "auto", "Usa el primero que tengas instalado."),
            "fish", ordered(
                    "s2.1-pro-free", "Gratis: anda con el saldo de API en cero. Recomendado.",
                    "s2.1-pro", "Pago: usa el crédito de API de Fish Audio.",
                    "s2-pro", "Pago, la versión anterior."));

    private final Screen parent;
    private final String provider;
    private final String current;
    private final Consumer<String> onPick;
    private final Map<String, String> entries = new LinkedHashMap<>();
    private int page;
    private Component status = Component.empty();
    private boolean loading;

    // provider: "gemini", "groq", "openrouter", "ollama" o "fish".
    public ModelPickerScreen(Screen parent, String provider, String current, Consumer<String> onPick)
    {
        super(Tr.t("Elegir modelo"));
        this.parent = parent;
        this.provider = provider;
        this.current = current;
        this.onPick = onPick;
        this.entries.putAll(SUGGESTED.getOrDefault(provider, Map.of()));
    }

    private static Map<String, String> ordered(String... pairs)
    {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2)
        {
            out.put(pairs[i], pairs[i + 1]);
        }
        return out;
    }

    @Nullable
    private AiProvider aiProvider()
    {
        return AiProvider.byId(this.provider);
    }

    private int perPage()
    {
        return Math.max(3, (this.height - 112) / ROW);
    }

    @Override
    protected void init()
    {
        int left = (this.width - WIDTH) / 2;
        int top = 44;
        List<String> names = new ArrayList<>(this.entries.keySet());
        int pages = Math.max(1, (names.size() + perPage() - 1) / perPage());
        this.page = Math.min(this.page, pages - 1);

        int from = this.page * perPage();
        for (int i = from; i < Math.min(names.size(), from + perPage()); i++)
        {
            String name = names.get(i);
            String note = this.entries.get(name);
            Component label = Component.literal(name.equals(this.current) ? "✔ " + name : name)
                    .withStyle(name.equals(this.current) ? ChatFormatting.GREEN : ChatFormatting.WHITE);
            Button button = Button.builder(label, b -> pick(name))
                    .bounds(left, top + (i - from) * ROW, WIDTH, 20)
                    .build();
            if (note != null && !note.isEmpty())
            {
                button.setTooltip(Tooltip.create(Tr.t(note)));
            }
            addRenderableWidget(button);
        }

        int bottom = this.height - 52;
        if (pages > 1)
        {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { this.page--; rebuildWidgets(); })
                    .bounds(left, bottom, 20, 20).build()).active = this.page > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { this.page++; rebuildWidgets(); })
                    .bounds(left + WIDTH - 20, bottom, 20, 20).build()).active = this.page < pages - 1;
        }
        if (aiProvider() != null)
        {
            Button fetch = Button.builder(Tr.t("Ver los modelos de mi cuenta"), b -> fetch())
                    .bounds(left + 24, bottom, WIDTH - 48, 20)
                    .tooltip(Tooltip.create(Tr.t("Le pregunta al proveedor, con tu clave, qué modelos podés usar hoy.")))
                    .build();
            fetch.active = !this.loading;
            addRenderableWidget(fetch);
        }
        addRenderableWidget(Button.builder(Tr.t("Cancelar"), b -> onClose())
                .bounds(left + WIDTH / 2 - 60, this.height - 28, 120, 20).build());
    }

    private void pick(String model)
    {
        this.onPick.accept(model);
        onClose();
    }

    // En segundo plano: la lista llega en un segundo o dos y se suma abajo de los recomendados.
    private void fetch()
    {
        AiProvider ai = aiProvider();
        if (ai == null)
        {
            return;
        }
        if (ai != AiProvider.OLLAMA && ai.apiKey().isEmpty())
        {
            this.status = Tr.t("Primero pegá tu clave de %s.", ai.label()).withStyle(ChatFormatting.GOLD);
            return;
        }
        this.loading = true;
        this.status = Tr.t("Buscando...").withStyle(ChatFormatting.GRAY);
        rebuildWidgets();
        CompletableFuture.supplyAsync(() ->
        {
            try
            {
                return ModelResolver.chatModels(ai);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).whenComplete((models, error) -> this.minecraft.execute(() ->
        {
            this.loading = false;
            if (error != null)
            {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                this.status = Tr.t("No pude traer la lista: %s", String.valueOf(cause.getMessage())).withStyle(ChatFormatting.RED);
            }
            else
            {
                int before = this.entries.size();
                for (String model : models)
                {
                    this.entries.putIfAbsent(model, "");
                }
                this.status = Tr.t("Tu cuenta tiene %s modelos para charlar.", String.valueOf(models.size()))
                        .withStyle(ChatFormatting.GREEN);
                if (this.entries.size() > before)
                {
                    this.page = before / perPage();
                }
            }
            rebuildWidgets();
        }));
    }

    // Desde 1.20.2 Screen.render pinta el fondo encima de todo lo anterior: aca se pinta una sola
    // vez, al principio de render, y lo propio queda arriba.
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), this.width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Tr.t("Los recomendados primero. Pasá el mouse para ver qué es cada uno.")
                .withStyle(ChatFormatting.GRAY), this.width / 2, 26, 0xFFFFFF);
        graphics.drawCenteredString(this.font, this.status, this.width / 2, this.height - 64, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose()
    {
        this.minecraft.setScreen(this.parent);
    }
}

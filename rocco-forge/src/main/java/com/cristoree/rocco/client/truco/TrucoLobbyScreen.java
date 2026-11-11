package com.cristoree.rocco.client.truco;

import com.cristoree.rocco.truco.TrucoNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

// El menu antes de la partida (ROCCO-V14 7.1): el que invito cambia las opciones tocando cada boton
// (va pasando de valor) y ve quienes se sumaron; los demas ven lo mismo sin poder tocar.
public final class TrucoLobbyScreen extends Screen
{
    private static final int PANEL_W = 300;
    private final List<Button> optionButtons = new ArrayList<>();
    private Button start;

    public TrucoLobbyScreen()
    {
        super(Component.translatable("gui.rocco.truco.lobby.title"));
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    void refresh()
    {
        rebuildWidgets();
    }

    @Override
    protected void init()
    {
        TrucoNetwork.LobbyState lobby = TrucoClient.lobby;
        this.optionButtons.clear();
        if (lobby == null)
        {
            return;
        }
        int buttonWidth = buttonWidth(lobby);
        int left = this.width / 2 - buttonWidth / 2;
        int y = top(lobby);
        for (int option = 0; option < TrucoNetwork.OPTIONS; option++)
        {
            int index = option;
            Button button = Button.builder(label(lobby, option), b -> cycle(index))
                    .bounds(left, y, buttonWidth, 20).build();
            button.active = lobby.host() && !(option == TrucoNetwork.OPT_FLOR && lobby.options()[TrucoNetwork.OPT_VARIANT] == 0);
            this.optionButtons.add(addRenderableWidget(button));
            y += 22;
        }
        int bottom = Math.min(this.height - 28, y + 8);
        if (lobby.host())
        {
            this.start = addRenderableWidget(Button.builder(Component.translatable("gui.rocco.truco.start").withStyle(ChatFormatting.GREEN),
                    b -> TrucoNetwork.toServer(new TrucoNetwork.LobbyAction(TrucoNetwork.OP_START, 0)))
                    .bounds(this.width / 2 - 104, bottom, 100, 20).build());
            this.start.active = !lobby.players().isEmpty();
            addRenderableWidget(Button.builder(Component.translatable("gui.rocco.truco.cancel"),
                    b -> TrucoNetwork.toServer(new TrucoNetwork.LobbyAction(TrucoNetwork.OP_CANCEL, 0)))
                    .bounds(this.width / 2 + 4, bottom, 100, 20).build());
        }
        else
        {
            addRenderableWidget(Button.builder(Component.translatable("gui.rocco.truco.leave"),
                    b -> TrucoNetwork.toServer(new TrucoNetwork.LobbyAction(TrucoNetwork.OP_LEAVE, 0)))
                    .bounds(this.width / 2 - 50, bottom, 100, 20).build());
        }
    }

    // Tan ancho como el texto mas largo de cualquier valor, sin pasarse de la pantalla.
    private int buttonWidth(TrucoNetwork.LobbyState lobby)
    {
        int widest = 160;
        int[] saved = lobby.options().clone();
        for (int option = 0; option < TrucoNetwork.OPTIONS; option++)
        {
            int[] values = option == TrucoNetwork.OPT_POINTS ? new int[]{15, 30} : option == TrucoNetwork.OPT_LEVEL ? new int[]{0, 1, 2} : new int[]{0, 1};
            for (int value : values)
            {
                lobby.options()[option] = value;
                widest = Math.max(widest, this.font.width(label(lobby, option)) + 16);
            }
            lobby.options()[option] = saved[option];
        }
        return Math.min(widest, this.width - 20);
    }

    // Arriba: el titulo, quienes se sumaron y (si no lo armaste vos) quien elige.
    private int top(TrucoNetwork.LobbyState lobby)
    {
        return lobby.host() ? 40 : 52;
    }

    // Cada boton pasa al siguiente valor.
    private void cycle(int option)
    {
        TrucoNetwork.LobbyState lobby = TrucoClient.lobby;
        if (lobby == null || !lobby.host())
        {
            return;
        }
        int value = lobby.options()[option];
        int next = switch (option)
        {
            case TrucoNetwork.OPT_POINTS -> value == 30 ? 15 : 30;
            case TrucoNetwork.OPT_LEVEL -> (value + 1) % 3;
            default -> value == 0 ? 1 : 0;
        };
        TrucoNetwork.toServer(new TrucoNetwork.LobbyAction(option, next));
    }

    private static Component label(TrucoNetwork.LobbyState lobby, int option)
    {
        int value = lobby.options()[option];
        return switch (option)
        {
            case TrucoNetwork.OPT_VARIANT -> Component.translatable("gui.rocco.truco.option.variant",
                    Component.translatable(value == 0 ? "gui.rocco.truco.variant.uruguayo" : "gui.rocco.truco.variant.argentino"));
            case TrucoNetwork.OPT_POINTS -> Component.translatable("gui.rocco.truco.option.points", String.valueOf(value));
            case TrucoNetwork.OPT_FLOR -> Component.translatable("gui.rocco.truco.option.flor",
                    Component.translatable(value == 1 ? "gui.rocco.truco.yes" : "gui.rocco.truco.no"));
            case TrucoNetwork.OPT_LEVEL -> Component.translatable("gui.rocco.truco.option.level",
                    Component.translatable(switch (value)
                    {
                        case 0 -> "gui.rocco.truco.level.tranqui";
                        case 2 -> "gui.rocco.truco.level.picaro";
                        default -> "gui.rocco.truco.level.normal";
                    }));
            case TrucoNetwork.OPT_MATE -> Component.translatable("gui.rocco.truco.option.mate",
                    Component.translatable(value == 0 ? "gui.rocco.truco.mate.entre_manos" : "gui.rocco.truco.mate.nunca"));
            case TrucoNetwork.OPT_TEAMS -> Component.translatable("gui.rocco.truco.option.teams",
                    Component.translatable(value == 0 ? "gui.rocco.truco.teams.llegada" : "gui.rocco.truco.teams.mezclados"));
            default -> Component.translatable("gui.rocco.truco.option.two",
                    Component.translatable(value == 0 ? "gui.rocco.truco.two.de_a_tres" : "gui.rocco.truco.two.mano_a_mano"));
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);
        TrucoNetwork.LobbyState lobby = TrucoClient.lobby;
        int panel = lobby == null ? PANEL_W : Math.max(PANEL_W, buttonWidth(lobby) + 20);
        int left = this.width / 2 - panel / 2;
        int bottom = lobby == null ? 120 : Math.min(this.height - 2, top(lobby) + TrucoNetwork.OPTIONS * 22 + 34);
        graphics.fill(left, 6, left + panel, bottom, 0xAA1E1A14);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFE9B0);
        if (lobby != null)
        {
            // Los que se sumaron, en una linea (si no entra, se corta con puntos suspensivos).
            String names = String.join(", ", lobby.players());
            Component players = Component.translatable("gui.rocco.truco.players", lobby.players().size()).append(": ");
            int room = panel - 16 - this.font.width(players);
            String shown = this.font.width(names) <= room ? names : this.font.plainSubstrByWidth(names, room - 8) + "…";
            graphics.drawCenteredString(this.font, players.copy().withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(shown).withStyle(ChatFormatting.WHITE)), this.width / 2, 25, 0xFFFFFF);
            if (!lobby.host())
            {
                graphics.drawCenteredString(this.font, Component.translatable("gui.rocco.truco.host_chooses").withStyle(ChatFormatting.GRAY),
                        this.width / 2, 38, 0xAAAAAA);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose()
    {
        // Cerrar el menu es como cancelar (o salir, si no lo armaste vos).
        TrucoNetwork.LobbyState lobby = TrucoClient.lobby;
        if (lobby != null)
        {
            TrucoNetwork.toServer(new TrucoNetwork.LobbyAction(lobby.host() ? TrucoNetwork.OP_CANCEL : TrucoNetwork.OP_LEAVE, 0));
        }
        TrucoClient.lobby = null;
        super.onClose();
    }
}

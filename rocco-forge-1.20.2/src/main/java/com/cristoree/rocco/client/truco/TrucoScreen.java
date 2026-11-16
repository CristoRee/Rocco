package com.cristoree.rocco.client.truco;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.registry.ModItems;
import com.cristoree.rocco.truco.Action;
import com.cristoree.rocco.truco.TrucoNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// La mesa del truco vista por el jugador (ROCCO-V14 7.7): sus cartas abajo, los botones de lo que
// puede cantar a la derecha, el tanteador con fosforos arriba a la izquierda, la muestra arriba a
// la derecha y en el medio de quien es el turno. No oscurece el fondo: atras se ve la mesa.
public final class TrucoScreen extends Screen
{
    static final ResourceLocation CARDS = new ResourceLocation(RoccoMod.MODID, "textures/gui/truco/cartas.png");
    static final ResourceLocation GUI = new ResourceLocation(RoccoMod.MODID, "textures/gui/truco/truco_gui.png");

    private static final int CARD_W = 40;
    private static final int CARD_H = 60;
    private static final int BUTTON_W = 80;
    private static final int BUTTON_H = 20;

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();
    private int shownBanner = -1;
    private int bannerTicks;
    private int ticks;
    // Donde termina el tanteador (cambia con el idioma): el estado de arriba se centra sin pisarlo.
    private int scoreRight = 126;

    public TrucoScreen()
    {
        super(Component.translatable("gui.rocco.truco.lobby.title"));
    }

    void refresh()
    {
        TrucoNetwork.TableState state = TrucoClient.state;
        if (state != null && state.bannerId() != this.shownBanner)
        {
            this.shownBanner = state.bannerId();
            this.bannerTicks = state.banner().getString().isEmpty() ? 0 : 40;
        }
    }

    @Override
    protected void init()
    {
        refresh();
    }

    // La mesa esta en el mundo: sin el fondo oscuro (ni el desenfoque) que el juego pone detras de las pantallas.
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    @Override
    public void tick()
    {
        this.ticks++;
        if (this.bannerTicks > 0)
        {
            this.bannerTicks--;
        }
        // Ya no esta sentado: la pantalla no tiene sentido.
        if (!TrucoClient.seated() && this.ticks > 40)
        {
            onClose();
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers)
    {
        if (TrucoClient.LOOK.matches(key, scan))
        {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    static int legalCount(TrucoNetwork.TableState state)
    {
        return state.legal().length / 2;
    }

    // ---------- dibujar ----------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.hits.clear();
        TrucoNetwork.TableState state = TrucoClient.state;
        if (state == null)
        {
            Component wait = Component.translatable("gui.rocco.truco.waiting");
            graphics.drawCenteredString(this.font, wait, this.width / 2, 20, 0xFFFFFF);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        int myTeam = state.mySeat() >= 0 && state.mySeat() < state.teams().length ? state.teams()[state.mySeat()] : 0;
        drawScore(graphics, state, myTeam);
        drawLog(graphics, state);
        drawMuestra(graphics, state);
        drawStatus(graphics, state, myTeam);
        drawHand(graphics, state, mouseX, mouseY);
        drawButtons(graphics, state, mouseX, mouseY);
        drawBanner(graphics, state, myTeam);
        drawMateOverlay(graphics, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawScore(GuiGraphics graphics, TrucoNetwork.TableState state, int myTeam)
    {
        int x = 6;
        int y = 6;
        // Cada columna con el nombre de los suyos ("CristoRee", "Rocco", "Ana y Rocco") y sus puntos,
        // sin pisar la otra ni pasar de un tercio de la pantalla.
        int maxColumn = Math.max(56, (this.width / 3 - 12) / 2);
        String ourName = state.manoSolo() ? "" : teamName(state, myTeam, maxColumn - this.font.width(" 30") - 6);
        String theirName = state.manoSolo() ? "" : teamName(state, 1 - myTeam, maxColumn - this.font.width(" 30") - 6);
        int column = Math.min(maxColumn, Math.max(56, Math.max(this.font.width(ourName + " 30"), this.font.width(theirName + " 30")) + 6));
        int w = Math.max(120, column * 2 + 12);
        int h = 78;
        this.scoreRight = x + w;
        panel(graphics, x, y, w, h);
        if (state.manoSolo())
        {
            // De a tres: cada uno con sus puntos.
            for (int seat = 0; seat < state.n(); seat++)
            {
                String name = state.names().get(seat);
                Component line = Component.literal(name + ": " + state.scores()[seat])
                        .withStyle(seat == state.mySeat() ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_GRAY);
                graphics.drawString(this.font, line, x + 8, y + 8 + seat * 11, 0xFFFFFF, false);
            }
            graphics.drawString(this.font, Component.literal("/" + state.target()), x + 8, y + h - 14, 0x666666, false);
            return;
        }
        int ours = teamScore(state, myTeam);
        int theirs = teamScore(state, 1 - myTeam);
        Component us = Component.literal(ourName + " " + ours);
        Component them = Component.literal(theirName + " " + theirs);
        // Cada nombre centrado sobre sus fosforos.
        int left = x + 6 + column / 2;
        int right = x + w - 6 - column / 2;
        graphics.drawString(this.font, us, left - this.font.width(us) / 2, y + 6, 0x2E6B1F, false);
        graphics.drawString(this.font, them, right - this.font.width(them) / 2, y + 6, 0x8A2B22, false);
        matches(graphics, left - 4, y + 18, ours, state.target());
        matches(graphics, right - 4, y + 18, theirs, state.target());
        // La linea entre las malas y las buenas.
        if (state.target() == 30)
        {
            int line = y + 18 + 3 * 9 + 1;
            graphics.fill(x + 8, line, x + w - 8, line + 1, 0x88553311);
        }
    }

    // Los de un equipo por su nombre: "Rocco", "Ana y Rocco", "Ana, Juan y Rocco". Si no entra, el
    // primero y cuantos mas ("Ana +2"); y si tampoco, "Nosotros" o "Ellos".
    private String teamName(TrucoNetwork.TableState state, int team, int room)
    {
        List<String> names = new ArrayList<>();
        // Primero vos, en tu equipo.
        if (state.mySeat() >= 0 && state.mySeat() < state.n() && state.teams()[state.mySeat()] == team)
        {
            names.add(state.names().get(state.mySeat()));
        }
        for (int seat = 0; seat < state.n(); seat++)
        {
            if (state.teams()[seat] == team && seat != state.mySeat())
            {
                names.add(state.names().get(seat));
            }
        }
        if (names.isEmpty())
        {
            return Component.translatable(team == 0 ? "gui.rocco.truco.us" : "gui.rocco.truco.them").getString();
        }
        String and = " " + Component.translatable("gui.rocco.truco.and").getString() + " ";
        String all = names.size() == 1 ? names.get(0)
                : String.join(", ", names.subList(0, names.size() - 1)) + and + names.get(names.size() - 1);
        if (this.font.width(all) <= room)
        {
            return all;
        }
        String first = names.get(0);
        String more = names.size() > 1 ? " +" + (names.size() - 1) : "";
        if (this.font.width(first + more) <= room)
        {
            return first + more;
        }
        return this.font.plainSubstrByWidth(first, Math.max(10, room - this.font.width(more + "…"))) + "…" + more;
    }

    // Los fosforos: cada 5 puntos un cuadradito, de arriba hacia abajo.
    private static void matches(GuiGraphics graphics, int x, int y, int score, int target)
    {
        int groups = Math.max(1, target / 5);
        int left = Math.min(score, target);
        for (int group = 0; group < groups && left > 0; group++)
        {
            int k = Math.min(5, left);
            left -= k;
            int gy = y + group * 9 + (target == 30 && group >= 3 ? 3 : 0);
            graphics.pose().pushPose();
            graphics.pose().translate(x, gy, 0.0F);
            graphics.pose().scale(9.0F / 16.0F, 9.0F / 16.0F, 1.0F);
            graphics.blit(GUI, 0, 0, (k - 1) * 16, 128, 16, 16, 256, 256);
            graphics.pose().popPose();
        }
    }

    private static int teamScore(TrucoNetwork.TableState state, int team)
    {
        for (int seat = 0; seat < state.n(); seat++)
        {
            if (state.teams()[seat] == team)
            {
                return state.scores()[seat];
            }
        }
        return 0;
    }

    private void drawLog(GuiGraphics graphics, TrucoNetwork.TableState state)
    {
        int y = 90;
        int max = Math.max(60, this.width / 2 - 30);
        // Las lineas largas se parten en dos; si son muchas, quedan las ultimas.
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
        for (Component line : state.log())
        {
            lines.addAll(this.font.split(line, max));
        }
        int room = Math.max(1, (this.height - 72 - y) / 12);
        for (int i = Math.max(0, lines.size() - room); i < lines.size(); i++)
        {
            net.minecraft.util.FormattedCharSequence line = lines.get(i);
            int w = this.font.width(line);
            graphics.fill(4, y - 2, 10 + w, y + 9, 0x66000000);
            graphics.drawString(this.font, line, 7, y, 0xFFFFFF, true);
            y += 12;
        }
    }

    private void drawMuestra(GuiGraphics graphics, TrucoNetwork.TableState state)
    {
        if (state.muestra() < 0)
        {
            return;
        }
        // A la derecha, debajo de donde aparecen los avisos del juego.
        int x = this.width - 6 - 30;
        int y = 52;
        Component label = Component.translatable("gui.rocco.truco.muestra");
        graphics.drawString(this.font, label, Math.min(x + 15 - this.font.width(label) / 2, this.width - 4 - this.font.width(label)), y - 11, 0xFFFFFF, true);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(0.75F, 0.75F, 1.0F);
        card(graphics, 0, 0, state.muestra());
        graphics.pose().popPose();
    }

    private void drawStatus(GuiGraphics graphics, TrucoNetwork.TableState state, int myTeam)
    {
        Component status = status(state);
        // En el lugar libre entre el tanteador y la muestra: grande si entra; si no, en renglones.
        int left = this.scoreRight + 6;
        int right = this.width - 52;
        int room = Math.max(60, right - left);
        int center = (left + right) / 2;
        float scale = Math.min(1.5F, room / (float) Math.max(1, this.font.width(status)));
        java.util.List<net.minecraft.util.FormattedCharSequence> lines;
        if (scale >= 1.0F)
        {
            lines = java.util.List.of(status.getVisualOrderText());
        }
        else
        {
            scale = 1.0F;
            lines = this.font.split(status, room);
        }
        // Debajo de la fila de avisos del juego ("nuevas recetas"...), que se dibujan encima de todo.
        int lineY = 36;
        for (net.minecraft.util.FormattedCharSequence line : lines)
        {
            graphics.pose().pushPose();
            graphics.pose().translate(center, lineY, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawCenteredString(this.font, line, 0, 0, 0xFFFFFF);
            graphics.pose().popPose();
            lineY += (int) Math.ceil(10 * scale);
        }
        // Los tres puntitos de las bazas, debajo.
        int x = center - 26;
        int y = lineY + 2;
        for (int trick = 0; trick < 3; trick++)
        {
            int result = trick < state.tricks().length ? state.tricks()[trick] : -2;
            int u = result == -2 ? -1 : result == -1 ? 144 : result == myTeam ? 112 : 128;
            if (u < 0)
            {
                graphics.fill(x + trick * 18 + 5, y + 5, x + trick * 18 + 11, y + 11, 0x55FFFFFF);
            }
            else
            {
                graphics.blit(GUI, x + trick * 18, y, u, 128, 16, 16, 256, 256);
            }
        }
    }

    private Component status(TrucoNetwork.TableState state)
    {
        TrucoNetwork.TableState s = state;
        if (s.phase() == 4)
        {
            return s.banner();
        }
        boolean mine = legalCount(s) > 0 && !s.blocked();
        if (s.pending() != 0 && s.caller() >= 0 && s.caller() < s.names().size())
        {
            Component call = Component.translatable("truco.rocco.call." + pendingName(s));
            Component called = Component.translatable("gui.rocco.truco.called", s.names().get(s.caller()), call);
            return mine ? called.copy().append(" ").append(Component.translatable("gui.rocco.truco.answer").withStyle(ChatFormatting.GREEN)) : called;
        }
        if (s.phase() == 0 && mine)
        {
            return Component.translatable("gui.rocco.truco.discard").withStyle(ChatFormatting.YELLOW);
        }
        if (mine)
        {
            return Component.translatable("gui.rocco.truco.your_turn").withStyle(ChatFormatting.GREEN);
        }
        if (s.thinking())
        {
            return Component.translatable("gui.rocco.truco.thinking");
        }
        if (s.phase() == 3)
        {
            return Component.translatable("gui.rocco.truco.hand_end");
        }
        if (s.turn() >= 0 && s.turn() < s.names().size())
        {
            return Component.translatable("gui.rocco.truco.turn_of", s.names().get(s.turn()));
        }
        return Component.empty();
    }

    private static String pendingName(TrucoNetwork.TableState s)
    {
        return switch (s.pending())
        {
            case 1 -> switch (s.trucoLevel())
            {
                case 0 -> "truco";
                case 1 -> "retruco";
                default -> "vale_cuatro";
            };
            case 2 -> "envido";
            case 3 -> "flor";
            default -> "contraflor_al_resto";
        };
    }

    private void drawHand(GuiGraphics graphics, TrucoNetwork.TableState state, int mouseX, int mouseY)
    {
        int[] cards = state.myCards();
        boolean discard = state.phase() == 0;
        int count = cards.length;
        int startX = this.width / 2 - (count * 46 - 6) / 2;
        for (int i = 0; i < count; i++)
        {
            int x = startX + i * 46;
            int y = this.height - 66;
            boolean playable = !state.blocked() && hasAction(state, discard ? Action.Type.DISCARD : Action.Type.PLAY, i);
            boolean hover = playable && mouseX >= x && mouseX < x + CARD_W && mouseY >= y - 6 && mouseY < y + CARD_H;
            if (hover)
            {
                y -= 6;
            }
            card(graphics, x, y, cards[i]);
            if (!playable)
            {
                graphics.fill(x, y, x + CARD_W, y + CARD_H, 0x66000000);
            }
            if (playable)
            {
                int index = i;
                Action.Type type = discard ? Action.Type.DISCARD : Action.Type.PLAY;
                this.hits.add(new Hit(x, y, CARD_W, CARD_H + 6, () -> send(type, index)));
            }
        }
    }

    private void drawButtons(GuiGraphics graphics, TrucoNetwork.TableState state, int mouseX, int mouseY)
    {
        int[] legal = state.legal();
        Action.Type[] types = Action.Type.values();
        // Todos los botones del ancho del texto mas largo (sin cortar), sin pasar de un tercio de pantalla.
        int widest = BUTTON_W;
        for (int i = 0; i + 1 < legal.length; i += 2)
        {
            if (legal[i] >= 0 && legal[i] < types.length && types[legal[i]] != Action.Type.PLAY && types[legal[i]] != Action.Type.DISCARD)
            {
                Component label = Component.translatable("truco.rocco.action." + types[legal[i]].name().toLowerCase(Locale.ROOT));
                widest = Math.max(widest, this.font.width(label) + 14);
            }
        }
        int buttonWidth = Math.min(widest, Math.max(BUTTON_W, this.width / 3));
        int x = this.width - buttonWidth - 8;
        int y = this.height - 30;
        for (int i = 0; i + 1 < legal.length; i += 2)
        {
            int ordinal = legal[i];
            if (ordinal < 0 || ordinal >= types.length)
            {
                continue;
            }
            Action.Type type = types[ordinal];
            if (type == Action.Type.PLAY || type == Action.Type.DISCARD)
            {
                continue;
            }
            boolean red = type == Action.Type.NO_QUIERO || type == Action.Type.MAZO || type == Action.Type.CON_FLOR_ME_ACHICO;
            Component label = Component.translatable("truco.rocco.action." + type.name().toLowerCase(Locale.ROOT));
            boolean enabled = !state.blocked();
            button(graphics, x, y, buttonWidth, label, red, enabled, mouseX, mouseY, () -> send(type, -1));
            y -= 22;
        }
        // Tomar el mate que te pasaron.
        if (state.mate())
        {
            Component mate = Component.translatable("gui.rocco.truco.mate_button");
            int mw = Math.max(100, this.font.width(mate) + 40);
            int mx = this.width / 2 - mw / 2;
            int my = this.height - 92;
            button(graphics, mx, my, mw, mate, false, true, mouseX, mouseY,
                    () -> TrucoNetwork.toServer(new TrucoNetwork.TakeMate()));
            graphics.renderItem(new ItemStack(ModItems.MATE.get()), mx + 2, my + 2);
        }
        // Mirar alrededor (la R vuelve a abrir esto).
        Component look = Component.translatable("gui.rocco.truco.look");
        button(graphics, 8, this.height - 28, Math.max(70, this.font.width(look) + 14), look, false, true, mouseX, mouseY, this::onClose);
    }

    private void drawBanner(GuiGraphics graphics, TrucoNetwork.TableState state, int myTeam)
    {
        if (this.bannerTicks <= 0)
        {
            return;
        }
        Component banner = state.banner();
        int color = state.bannerTeam() < 0 ? 0xFFFFFF : state.bannerTeam() == myTeam ? 0x7CFC5A : 0xFF6A50;
        if (state.manoSolo())
        {
            color = 0xFFE066;
        }
        float alpha = Math.min(1.0F, this.bannerTicks / 8.0F);
        int a = Mth.clamp((int) (alpha * 255.0F), 8, 255);
        graphics.pose().pushPose();
        float scale = Math.max(1.0F, Math.min(2.5F, (this.width - 24) / (float) Math.max(1, this.font.width(banner))));
        // Una franja oscura detras, para que se lea sobre la mesa y el registro.
        int textWidth = (int) (this.font.width(banner) * scale);
        int bannerY = this.height / 2 - 24;
        graphics.fill(this.width / 2 - textWidth / 2 - 10, bannerY - 5, this.width / 2 + textWidth / 2 + 10,
                bannerY + (int) (8 * scale) + 5, ((int) (a * 0.6F) << 24));
        graphics.pose().translate(this.width / 2.0F, bannerY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawCenteredString(this.font, banner, 0, 0, (a << 24) | color);
        graphics.pose().popPose();
    }

    // El mate que te tomaste, subiendo por abajo un momento.
    private void drawMateOverlay(GuiGraphics graphics, float partialTick)
    {
        if (TrucoClient.mateOverlay <= 0)
        {
            return;
        }
        float progress = (40 - TrucoClient.mateOverlay + partialTick) / 40.0F;
        float lift = (float) Math.sin(Math.min(1.0F, progress) * Math.PI);
        graphics.pose().pushPose();
        graphics.pose().translate(this.width / 2.0F - 24.0F, this.height - 10.0F - lift * 70.0F, 200.0F);
        graphics.pose().scale(3.0F, 3.0F, 1.0F);
        graphics.renderItem(new ItemStack(ModItems.MATE.get()), 0, 0);
        graphics.pose().popPose();
    }

    // ---------- piezas ----------

    static void card(GuiGraphics graphics, int x, int y, int id)
    {
        int u = id < 0 ? 0 : (id % 10) * CARD_W;
        int v = id < 0 ? 240 : (id / 10) * CARD_H;
        graphics.blit(CARDS, x, y, u, v, CARD_W, CARD_H, 512, 512);
    }

    private static void panel(GuiGraphics graphics, int x, int y, int w, int h)
    {
        // 9 partes de 16 px: las esquinas quedan iguales y los bordes se estiran.
        int c = 16;
        graphics.blit(GUI, x, y, 0, 0, c, c, 256, 256);
        graphics.blit(GUI, x + w - c, y, 32, 0, c, c, 256, 256);
        graphics.blit(GUI, x, y + h - c, 0, 32, c, c, 256, 256);
        graphics.blit(GUI, x + w - c, y + h - c, 32, 32, c, c, 256, 256);
        graphics.blit(GUI, x + c, y, w - 2 * c, c, 16, 0, 16, 16, 256, 256);
        graphics.blit(GUI, x + c, y + h - c, w - 2 * c, c, 16, 32, 16, 16, 256, 256);
        graphics.blit(GUI, x, y + c, c, h - 2 * c, 0, 16, 16, 16, 256, 256);
        graphics.blit(GUI, x + w - c, y + c, c, h - 2 * c, 32, 16, 16, 16, 256, 256);
        graphics.blit(GUI, x + c, y + c, w - 2 * c, h - 2 * c, 16, 16, 16, 16, 256, 256);
    }

    private void button(GuiGraphics graphics, int x, int y, int w, Component label, boolean red, boolean enabled, int mouseX,
                        int mouseY, Runnable action)
    {
        boolean hover = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + BUTTON_H;
        int u = red ? 80 : 0;
        int v = !enabled ? 108 : hover ? 68 : 48;
        if (w == BUTTON_W)
        {
            graphics.blit(GUI, x, y, u, v, BUTTON_W, BUTTON_H, 256, 256);
        }
        else
        {
            // Mas ancho o mas angosto: las puntas quedan y el medio se estira.
            int edge = 4;
            graphics.blit(GUI, x, y, u, v, edge, BUTTON_H, 256, 256);
            graphics.blit(GUI, x + w - edge, y, u + BUTTON_W - edge, v, edge, BUTTON_H, 256, 256);
            graphics.blit(GUI, x + edge, y, w - 2 * edge, BUTTON_H, u + edge, v, BUTTON_W - 2 * edge, BUTTON_H, 256, 256);
        }
        Font font = this.font;
        Component shown = label;
        if (font.width(label) > w - 6)
        {
            shown = Component.literal(font.plainSubstrByWidth(label.getString(), w - 10) + "…");
        }
        graphics.drawCenteredString(font, shown, x + w / 2, y + 6, enabled ? 0xFFFFFF : 0xAAAAAA);
        if (enabled)
        {
            this.hits.add(new Hit(x, y, w, BUTTON_H, action));
        }
    }

    private static boolean hasAction(TrucoNetwork.TableState state, Action.Type type, int card)
    {
        int[] legal = state.legal();
        for (int i = 0; i + 1 < legal.length; i += 2)
        {
            if (legal[i] == type.ordinal() && legal[i + 1] == card)
            {
                return true;
            }
        }
        return false;
    }

    // ---------- clics ----------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0)
        {
            for (int i = this.hits.size() - 1; i >= 0; i--)
            {
                Hit hit = this.hits.get(i);
                if (mouseX >= hit.x && mouseX < hit.x + hit.w && mouseY >= hit.y && mouseY < hit.y + hit.h)
                {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    hit.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static void send(Action.Type type, int card)
    {
        TrucoNetwork.toServer(new TrucoNetwork.Play(type.ordinal(), card));
    }

    @Nullable
    static TrucoNetwork.TableState current()
    {
        return TrucoClient.state;
    }
}

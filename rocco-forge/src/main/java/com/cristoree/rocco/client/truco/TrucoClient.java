package com.cristoree.rocco.client.truco;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.truco.TrucoChairEntity;
import com.cristoree.rocco.truco.TrucoNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

// Lo que el jugador sabe de su partida de truco: el ultimo estado de la mesa, el menu antes de
// empezar y la tecla "Mirar" (R): cierra la pantalla para mirar alrededor y la vuelve a abrir.
@Mod.EventBusSubscriber(modid = RoccoMod.MODID, value = Dist.CLIENT)
public final class TrucoClient
{
    public static final KeyMapping LOOK = new KeyMapping("key.rocco.truco_look", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.rocco");

    @Nullable
    static TrucoNetwork.TableState state;
    @Nullable
    static TrucoNetwork.LobbyState lobby;
    // El mate que se acaba de tomar: se ve subiendo por abajo de la pantalla un ratito.
    static int mateOverlay;
    private static boolean hadMate;
    static int tableId = -1;
    @Nullable
    private static Vec3 tableCenter;

    public static void lobby(TrucoNetwork.LobbyState msg)
    {
        Minecraft mc = Minecraft.getInstance();
        if (msg.closed())
        {
            lobby = null;
            if (mc.screen instanceof TrucoLobbyScreen)
            {
                mc.setScreen(null);
            }
            return;
        }
        lobby = msg;
        if (mc.screen instanceof TrucoLobbyScreen screen)
        {
            screen.refresh();
        }
        else if (mc.screen == null || !(mc.screen instanceof TrucoScreen))
        {
            mc.setScreen(new TrucoLobbyScreen());
        }
    }

    public static void state(TrucoNetwork.TableState msg)
    {
        if (hadMate && !msg.mate())
        {
            mateOverlay = 40;
        }
        hadMate = msg.mate();
        state = msg;
        tableId = msg.table();
        if (Minecraft.getInstance().screen instanceof TrucoScreen screen)
        {
            screen.refresh();
        }
    }

    public static void open(TrucoNetwork.Open msg)
    {
        Minecraft mc = Minecraft.getInstance();
        tableId = msg.table();
        tableCenter = new Vec3(msg.x(), msg.y(), msg.z());
        lobby = null;
        aimAtTable();
        mc.setScreen(new TrucoScreen());
    }

    public static void close()
    {
        Minecraft mc = Minecraft.getInstance();
        state = null;
        tableId = -1;
        tableCenter = null;
        hadMate = false;
        if (mc.screen instanceof TrucoScreen || mc.screen instanceof TrucoLobbyScreen)
        {
            mc.setScreen(null);
        }
    }

    // La camara mira al centro de la mesa.
    static void aimAtTable()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || tableCenter == null)
        {
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        Vec3 to = tableCenter.subtract(eye);
        double flat = Math.sqrt(to.x * to.x + to.z * to.z);
        float yaw = (float) (Mth.atan2(to.z, to.x) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) -(Mth.atan2(to.y, flat) * (180.0D / Math.PI));
        mc.player.setYRot(yaw);
        mc.player.yRotO = yaw;
        mc.player.setXRot(Mth.clamp(pitch, -10.0F, 75.0F));
        mc.player.xRotO = mc.player.getXRot();
    }

    // Sentado en una silla del truco, con una partida en curso.
    static boolean seated()
    {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && state != null && mc.player.getVehicle() instanceof TrucoChairEntity;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        if (mateOverlay > 0)
        {
            mateOverlay--;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean pressed = false;
        while (LOOK.consumeClick())
        {
            pressed = true;
        }
        if (mc.player == null)
        {
            state = null;
            lobby = null;
            return;
        }
        if (pressed && mc.screen == null && seated())
        {
            aimAtTable();
            mc.setScreen(new TrucoScreen());
        }
        // Se bajo de la silla (o termino la partida sin aviso): se olvida de la mesa.
        if (state != null && !(mc.player.getVehicle() instanceof TrucoChairEntity) && mc.player.tickCount % 20 == 0)
        {
            if (mc.level != null && mc.level.getEntity(tableId) == null)
            {
                close();
            }
        }
    }

    // Con la mesa abierta, la barra de items, la vida y la mira no se dibujan: quedarian arriba de las cartas.
    private static final java.util.Set<net.minecraft.resources.ResourceLocation> HIDDEN_WITH_TABLE = java.util.Set.of(
            net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.id(),
            net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CROSSHAIR.id(),
            net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.PLAYER_HEALTH.id(),
            net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.ARMOR_LEVEL.id(),
            net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.FOOD_LEVEL.id(),
            net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.AIR_LEVEL.id(),
            net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.MOUNT_HEALTH.id(),
            net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.JUMP_BAR.id(),
            net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.EXPERIENCE_BAR.id(),
            net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.ITEM_NAME.id());

    @SubscribeEvent
    public static void onOverlayPre(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre event)
    {
        if (Minecraft.getInstance().screen instanceof TrucoScreen && HIDDEN_WITH_TABLE.contains(event.getOverlay().id()))
        {
            event.setCanceled(true);
        }
    }

    // Con la pantalla cerrada: un recordatorio de como volver a la mesa, y si te toca.
    @SubscribeEvent
    public static void onOverlay(RenderGuiEvent.Post event)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || !seated() || mc.options.hideGui || state == null)
        {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        boolean myTurn = TrucoScreen.legalCount(state) > 0;
        Component hint = Component.translatable("gui.rocco.truco.back", LOOK.getTranslatedKeyMessage());
        Component line = myTurn ? Component.translatable("gui.rocco.truco.your_turn").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY)).append(hint) : hint;
        int width = mc.font.width(line);
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - 72;
        graphics.fill(x - 4, y - 3, x + width + 4, y + 11, 0x88000000);
        graphics.drawString(mc.font, line, x, y, 0xFFFFFF, true);
    }

    private TrucoClient() {}
}

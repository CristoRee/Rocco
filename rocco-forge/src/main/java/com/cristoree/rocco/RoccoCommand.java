package com.cristoree.rocco;

import com.cristoree.rocco.ai.RoccoPersonality;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.config.RoccoConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoState;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

// /rocco estado  -> dice si las claves estan puestas y como esta Rocco
// /rocco despertar -> lo despierta sin tener que buscarlo y hacerle clic
@Mod.EventBusSubscriber(modid = RoccoMod.MODID)
public final class RoccoCommand
{
    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event)
    {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("rocco")
                .then(Commands.literal("estado").executes(context ->
                {
                    report(context.getSource());
                    return 1;
                }))
                .then(Commands.literal("despertar").executes(context ->
                {
                    return awaken(context.getSource());
                }));

        event.getDispatcher().register(root);
    }

    private static void report(CommandSourceStack source)
    {
        source.sendSuccess(() -> Component.literal("--- Rocco ---").withStyle(ChatFormatting.AQUA), false);

        line(source, "Mod encendido", RoccoCommonConfig.ENABLED.get());
        line(source, "Clave de Gemini", !RoccoConfig.geminiApiKey().isEmpty());
        line(source, "Chat con IA listo", RoccoConfig.chatReady());
        line(source, "Clave de Fish Audio", !RoccoConfig.fishApiKey().isEmpty());
        line(source, "ID de voz", !RoccoCommonConfig.FISH_VOICE_ID.get().isBlank());
        line(source, "Clave de Deepgram", !RoccoConfig.deepgramApiKey().isEmpty());

        source.sendSuccess(() -> Component.literal("Modelo: " + RoccoCommonConfig.GEMINI_MODEL.get())
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("Personalidad: " + RoccoPersonality.file())
                .withStyle(ChatFormatting.GRAY), false);

        if (source.getEntity() instanceof ServerPlayer player)
        {
            RoccoEntity rocco = nearest(player);
            if (rocco == null)
            {
                source.sendSuccess(() -> Component.literal("Rocco cerca: no hay ninguno")
                        .withStyle(ChatFormatting.GRAY), false);
                return;
            }

            source.sendSuccess(() -> Component.literal("Estado: " + rocco.getRoccoState().name()
                    + " | cara: " + rocco.getExpression().id()
                    + " | tapa: " + (rocco.isLidOpen() ? "abierta" : "cerrada"))
                    .withStyle(ChatFormatting.GRAY), false);

            String seeds = rocco.plantableStacks().isEmpty()
                    ? "ninguna"
                    : rocco.plantableStacks().stream()
                            .map(stack -> stack.getHoverName().getString()
                                    + " x" + rocco.countInInventory(stack))
                            .reduce((a, b) -> a + ", " + b).orElse("ninguna");

            source.sendSuccess(() -> Component.literal("Semillas: " + seeds)
                    .withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("Trabajos en cola: " + rocco.queueSize()
                    + " | siguiendo: " + (rocco.isFollowing() ? "si" : "no"))
                    .withStyle(ChatFormatting.GRAY), false);
        }
    }

    private static int awaken(CommandSourceStack source)
    {
        if (!(source.getEntity() instanceof ServerPlayer player))
        {
            source.sendFailure(Component.literal("Este comando lo tiene que usar un jugador."));
            return 0;
        }

        RoccoEntity rocco = nearest(player);
        if (rocco == null)
        {
            source.sendFailure(Component.literal("No hay ningun Rocco en 32 bloques."));
            return 0;
        }

        if (rocco.getRoccoState() != RoccoState.PLANT)
        {
            source.sendFailure(Component.literal("Ese Rocco ya esta despierto."));
            return 0;
        }

        rocco.beginAwakening();
        source.sendSuccess(() -> Component.literal("Despertando a Rocco...").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static void line(CommandSourceStack source, String label, boolean ok)
    {
        source.sendSuccess(() -> Component.literal(label + ": ")
                .append(Component.literal(ok ? "si" : "no")
                        .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
    }

    private static RoccoEntity nearest(ServerPlayer player)
    {
        AABB area = player.getBoundingBox().inflate(32.0D);
        List<RoccoEntity> found = player.serverLevel()
                .getEntitiesOfClass(RoccoEntity.class, area, RoccoEntity::isAlive);

        RoccoEntity closest = null;
        double best = Double.MAX_VALUE;
        for (RoccoEntity candidate : found)
        {
            double distance = candidate.distanceToSqr(player);
            if (distance < best)
            {
                best = distance;
                closest = candidate;
            }
        }
        return closest;
    }

    private RoccoCommand() {}
}

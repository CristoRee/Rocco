package com.cristoree.rocco;

import com.cristoree.rocco.ai.AiProvider;
import com.cristoree.rocco.ai.AiRouter;
import com.cristoree.rocco.ai.ModelResolver;
import com.cristoree.rocco.ai.RoccoBrain;
import com.cristoree.rocco.ai.RoccoPersonality;
import com.cristoree.rocco.ai.personality.Traits;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.config.RoccoConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoState;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
                }))
                .then(Commands.literal("llamar").executes(context ->
                {
                    return call(context.getSource());
                }))
                .then(Commands.literal("lugares").executes(context ->
                {
                    return places(context.getSource());
                }))
                .then(Commands.literal("ia").executes(context ->
                {
                    reportAi(context.getSource());
                    return 1;
                }))
                .then(Commands.literal("personalidad")
                        .executes(context ->
                        {
                            showPersonality(context.getSource());
                            return 1;
                        })
                        .then(Commands.literal("chill").requires(RoccoCommand::mayConfigure)
                                .executes(context -> setMode(context.getSource(), Traits.Mode.CHILL)))
                        .then(Commands.literal("sin_filtro").requires(RoccoCommand::mayConfigure)
                                .executes(context -> setMode(context.getSource(), Traits.Mode.SIN_FILTRO)))
                        .then(Commands.literal("personalizado").requires(RoccoCommand::mayConfigure)
                                .executes(context -> setMode(context.getSource(), Traits.Mode.PERSONALIZADO)))
                        .then(Commands.literal("ver").executes(context -> dumpPrompt(context.getSource())))
                        .then(Commands.literal("reset").executes(context ->
                        {
                            return resetPersonality(context.getSource());
                        })))
                .then(Commands.literal("setup").executes(context -> openSetup(context.getSource())));

        event.getDispatcher().register(root);
    }

    private static void report(CommandSourceStack source)
    {
        source.sendSuccess(() -> Component.literal("--- Rocco ---").withStyle(ChatFormatting.AQUA), false);

        line(source, "Mod encendido", RoccoCommonConfig.ENABLED.get());

        List<AiProvider> ready = AiRouter.candidates();
        String ai = ready.isEmpty()
                ? "sin IA: usa sus frases de fabrica (/rocco ia para conseguir una gratis)"
                : ready.stream().map(AiProvider::label).reduce((a, b) -> a + " > " + b).orElse("");
        source.sendSuccess(() -> Component.literal("IA: ").append(Component.literal(ai)
                .withStyle(ready.isEmpty() ? ChatFormatting.YELLOW : ChatFormatting.GREEN)), false);

        line(source, "Clave de Fish Audio", !RoccoConfig.fishApiKey().isEmpty());
        line(source, "ID de voz", !RoccoCommonConfig.FISH_VOICE_ID.get().isBlank());
        line(source, "Clave de Deepgram", !RoccoConfig.deepgramApiKey().isEmpty());
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

            var errand = rocco.currentErrand();
            source.sendSuccess(() -> Component.literal("Haciendo: " + (errand == null ? "nada" : errand.describe()))
                    .withStyle(ChatFormatting.GRAY), false);

            long enchantLeft = rocco.enchantCooldownLeft();
            source.sendSuccess(() -> Component.literal("Poder de encantar: " + (enchantLeft <= 0
                    ? "listo" : "descansando (" + Math.max(1, (enchantLeft + 1199) / 1200) + " min)"))
                    .withStyle(ChatFormatting.GRAY), false);

            var harvestChest = rocco.getHarvestChest();
            source.sendSuccess(() -> Component.literal("Cofre de la cosecha: "
                    + (harvestChest == null ? "ninguno" : harvestChest.toShortString()))
                    .withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("Trabajos en cola: " + rocco.queueSize()
                    + " | siguiendo: " + (rocco.isFollowing() ? "si" : "no")
                    + " | postura: " + rocco.getRoccoPose().name())
                    .withStyle(ChatFormatting.GRAY), false);

            var death = rocco.getLastDeathPos();
            source.sendSuccess(() -> Component.literal(death == null
                    ? "Ultima muerte recordada: ninguna"
                    : "Ultima muerte recordada: " + death.toShortString()
                            + " en " + rocco.getLastDeathDimension())
                    .withStyle(ChatFormatting.GRAY), false);
        }
    }

    // Estado de cada proveedor, en el orden en que se prueban, con links para los que faltan.
    private static void reportAi(CommandSourceStack source)
    {
        source.sendSuccess(() -> Component.literal("--- IA de Rocco ---").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("Modo: " + RoccoCommonConfig.AI_PROVIDER.get()
                + " | orden: " + RoccoCommonConfig.AI_PROVIDER_ORDER.get()).withStyle(ChatFormatting.GRAY), false);

        AiProvider last = AiRouter.lastUsed();

        for (AiProvider provider : AiProvider.values())
        {
            MutableComponent row = Component.literal(" " + provider.label() + ": ");

            if (!provider.isConfigured())
            {
                row.append(Component.literal("sin configurar").withStyle(ChatFormatting.DARK_GRAY));
                if (!provider.signupUrl().isEmpty())
                {
                    row.append(Component.literal("  ")).append(RoccoBrain.link(
                            provider.freeHosted() ? "[clave gratis]" : "[descargar]", provider.signupUrl()));
                }
            }
            else
            {
                long paused = AiRouter.pausedSeconds(provider);
                String error = AiRouter.lastError(provider);

                row.append(Component.literal(ModelResolver.lastKnown(provider)).withStyle(ChatFormatting.WHITE));
                if (provider == last)
                {
                    row.append(Component.literal("  (contesto la ultima vez)").withStyle(ChatFormatting.GREEN));
                }
                if (paused > 0)
                {
                    row.append(Component.literal("  en pausa " + paused + "s").withStyle(ChatFormatting.YELLOW));
                }
                if (error != null)
                {
                    row.append(Component.literal("  ultimo error: " + error).withStyle(ChatFormatting.RED));
                }
            }

            source.sendSuccess(() -> row, false);
        }

        if (AiRouter.candidates().isEmpty())
        {
            source.sendSuccess(RoccoBrain::freeAiMessage, false);
        }
    }

    private static void showPersonality(CommandSourceStack source)
    {
        Traits.Mode mode = RoccoCommonConfig.PERSONALITY_MODE.get();
        source.sendSuccess(() -> Component.translatable("rocco.command.personality.mode",
                Component.translatable("rocco.personality.mode." + mode.name().toLowerCase(java.util.Locale.ROOT)))
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.translatable("rocco.command.personality.modes").withStyle(ChatFormatting.GRAY), false);

        if (mode == Traits.Mode.PERSONALIZADO && RoccoCommonConfig.USE_OWN_FILE.get())
        {
            source.sendSuccess(() -> Component.translatable("rocco.command.personality.file", RoccoPersonality.file().toString())
                    .withStyle(ChatFormatting.GRAY), false);
            if (RoccoPersonality.isOutdated())
            {
                source.sendSuccess(() -> Component.translatable("rocco.command.personality.outdated")
                        .withStyle(ChatFormatting.YELLOW), false);
            }
        }
    }

    // En un mundo propio, cualquiera; en un servidor, solo un operador (cambia la config de todos).
    private static boolean mayConfigure(CommandSourceStack source)
    {
        return source.hasPermission(2) || source.getServer().isSingleplayer();
    }

    private static int setMode(CommandSourceStack source, Traits.Mode mode)
    {
        RoccoCommonConfig.PERSONALITY_MODE.set(mode);
        RoccoCommonConfig.SPEC.save();
        source.sendSuccess(() -> Component.translatable("rocco.command.personality.changed",
                Component.translatable("rocco.personality.mode." + mode.name().toLowerCase(java.util.Locale.ROOT)))
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    // Guarda el prompt armado completo, tal como se le mandaria a la IA, para revisarlo.
    private static int dumpPrompt(CommandSourceStack source)
    {
        if (!RoccoCommonConfig.DEBUG_LOGGING.get())
        {
            source.sendFailure(Component.translatable("rocco.command.personality.needs_debug"));
            return 0;
        }
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

        Path file = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("rocco").resolve("prompt-generado.txt");
        try
        {
            String prompt = rocco.brain().debugPrompt(player);
            Files.createDirectories(file.getParent());
            Files.writeString(file, prompt, StandardCharsets.UTF_8);
            source.sendSuccess(() -> Component.translatable("rocco.command.personality.dumped", file.toString(),
                    String.valueOf(prompt.length()), String.valueOf(prompt.length() / 4)).withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        catch (IOException e)
        {
            source.sendFailure(Component.literal("No se pudo escribir el archivo: " + e.getMessage()));
            return 0;
        }
    }

    private static int openSetup(CommandSourceStack source)
    {
        if (!(source.getEntity() instanceof ServerPlayer player))
        {
            source.sendFailure(Component.literal("Este comando lo tiene que usar un jugador."));
            return 0;
        }
        com.cristoree.rocco.voice.RoccoNetwork.openSetup(player);
        return 1;
    }

    private static int resetPersonality(CommandSourceStack source)
    {
        try
        {
            RoccoPersonality.reset();
            source.sendSuccess(() -> Component.translatable("rocco.command.personality.reset", RoccoPersonality.file().toString())
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        catch (IOException e)
        {
            source.sendFailure(Component.literal("No se pudo escribir el archivo: " + e.getMessage()));
            return 0;
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

    // Lo trae al lado tuyo este donde este (otra dimension, zona descargada, lejisimo).
    private static int call(CommandSourceStack source)
    {
        if (!(source.getEntity() instanceof ServerPlayer player))
        {
            source.sendFailure(Component.literal("Este comando lo tiene que usar un jugador."));
            return 0;
        }
        if (RoccoCompanionTracker.recall(player, true))
        {
            source.sendSuccess(() -> Component.literal("Rocco ya esta al lado tuyo.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Buscando a Rocco donde lo viste por ultima vez...")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int places(CommandSourceStack source)
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
        if (rocco.places().isEmpty())
        {
            source.sendSuccess(() -> Component.literal("Rocco no recuerda ningun lugar. Decile \"esta es mi casa\" estando ahi.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        String here = player.level().dimension().location().toString();
        rocco.places().forEach((name, place) -> source.sendSuccess(() -> Component.literal(" " + name + ": "
                + (place.dimension().equals(here)
                        ? com.cristoree.rocco.work.GuideErrand.directions(player, place.pos())
                        : place.pos().toShortString() + " en " + place.dimension().replace("minecraft:", "")))
                .withStyle(ChatFormatting.GRAY), false));
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

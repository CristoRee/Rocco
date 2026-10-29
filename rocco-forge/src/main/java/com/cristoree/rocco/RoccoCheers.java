package com.cristoree.rocco;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.FrameType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraftforge.event.entity.living.AnimalTameEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Rocco festeja lo bueno que le pasa al jugador (ROCCO-PENSAR-FESTEJAR-BAILAR.md):
//  - aplaude lo chico: un logro comun, cada 5 niveles, domesticar un animal, un buen trueque;
//  - salta con confeti lo grande: logros de meta o desafio, y matar a un jefe.
// Solo si esta cerca (16 bloques), libre y en tierra, y como mucho uno cada 10 segundos.
@Mod.EventBusSubscriber(modid = RoccoMod.MODID)
public final class RoccoCheers
{
    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event)
    {
        DisplayInfo display = event.getAdvancement().getDisplay();
        // Las recetas y los logros escondidos no tienen cartel: no se festejan.
        if (display == null || !display.shouldAnnounceChat() || !(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        cheer(player, display.getFrame() == FrameType.TASK ? RoccoTask.CLAPPING : RoccoTask.CELEBRATING);
    }

    @SubscribeEvent
    public static void onLevelUp(PlayerXpEvent.LevelChange event)
    {
        if (event.getLevels() <= 0 || !(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        int reached = player.experienceLevel + event.getLevels();
        if (reached / 5 > player.experienceLevel / 5)
        {
            cheer(player, RoccoTask.CLAPPING);
        }
    }

    @SubscribeEvent
    public static void onTame(AnimalTameEvent event)
    {
        if (event.getTamer() instanceof ServerPlayer player)
        {
            cheer(player, RoccoTask.CLAPPING);
        }
    }

    // No cada trueque: alguno que otro, si no aplaudiria toda la tarde en la aldea.
    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player && player.getRandom().nextInt(4) == 0)
        {
            cheer(player, RoccoTask.CLAPPING);
        }
    }

    // Un jefe menos: festejan todos los Roccos de los jugadores que estaban ahi.
    @SubscribeEvent
    public static void onBossDeath(LivingDeathEvent event)
    {
        LivingEntity boss = event.getEntity();
        if (!(boss.level() instanceof ServerLevel level)
                || !(boss instanceof EnderDragon || boss instanceof WitherBoss || boss instanceof Warden
                || boss instanceof ElderGuardian))
        {
            return;
        }
        for (ServerPlayer player : level.players())
        {
            if (player.distanceToSqr(boss) < 128.0D * 128.0D)
            {
                cheer(player, RoccoTask.CELEBRATING);
            }
        }
    }

    private static void cheer(ServerPlayer player, RoccoTask kind)
    {
        RoccoEntity rocco = RoccoCompanionTracker.find(player);
        if (rocco == null || !rocco.canCheer(player))
        {
            return;
        }
        rocco.perform(kind, player);
        // Lo grande siempre se dice; lo chico a veces, para no hablar de mas.
        if (kind == RoccoTask.CELEBRATING)
        {
            rocco.sayLine("cheer_big");
        }
        else if (player.getRandom().nextBoolean())
        {
            rocco.sayLine("cheer_small");
        }
    }

    private RoccoCheers() {}
}

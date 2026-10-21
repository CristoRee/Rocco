package com.cristoree.rocco.entity;

import com.cristoree.rocco.config.RoccoCommonConfig;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class RoccoFollowGoal extends Goal
{
    // Hasta donde busca a quien seguir. Mas alla de esto deja de ser "su" jugador.
    private static final double SEARCH_RADIUS = 32.0D;

    private final RoccoEntity rocco;
    private Player target;
    private int repathCooldown;

    public RoccoFollowGoal(RoccoEntity rocco)
    {
        this.rocco = rocco;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        if (rocco.getRoccoState() != RoccoState.ACTIVE || !RoccoCommonConfig.FOLLOW_ENABLED.get())
        {
            return false;
        }

        Player nearest = rocco.level().getNearestPlayer(rocco, SEARCH_RADIUS);
        if (nearest == null || nearest.isSpectator())
        {
            return false;
        }

        if (rocco.distanceToSqr(nearest) < sqr(RoccoCommonConfig.FOLLOW_START_DISTANCE.get()))
        {
            return false;
        }

        this.target = nearest;
        return true;
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.target != null
                && this.target.isAlive()
                && rocco.getRoccoState() == RoccoState.ACTIVE
                && rocco.distanceToSqr(this.target) > sqr(RoccoCommonConfig.FOLLOW_STOP_DISTANCE.get());
    }

    @Override
    public void start()
    {
        this.repathCooldown = 0;
    }

    @Override
    public void stop()
    {
        this.target = null;
        rocco.getNavigation().stop();
    }

    @Override
    public void tick()
    {
        if (this.target == null)
        {
            return;
        }

        double teleportAt = RoccoCommonConfig.TELEPORT_DISTANCE.get();
        if (teleportAt > 0.0D && rocco.distanceToSqr(this.target) > sqr(teleportAt))
        {
            rocco.teleportTo(this.target.getX(), this.target.getY(), this.target.getZ());
            rocco.getNavigation().stop();
            return;
        }

        if (--this.repathCooldown <= 0)
        {
            this.repathCooldown = 10;
            rocco.getNavigation().moveTo(this.target, RoccoCommonConfig.FOLLOW_SPEED.get());
        }
    }

    private static double sqr(double value)
    {
        return value * value;
    }
}

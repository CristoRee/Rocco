package com.cristoree.rocco.entity;

import com.cristoree.rocco.config.RoccoCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumSet;

public class RoccoFollowGoal extends Goal
{
    // Tiene que ser mas grande que teleportDistance: si no, el jugador se aleja, el goal deja de
    // encontrarlo y el teleport nunca llega a ejecutarse. Ese era el motivo de que no funcionara.
    private static final double SEARCH_RADIUS = 160.0D;

    // Atascado: tantos ticks intentando caminar sin avanzar de verdad.
    private static final int STUCK_TICKS = 50;
    private static final double STUCK_STEP = 0.02D;

    private final RoccoEntity rocco;
    private Player target;
    private int repathCooldown;
    private int stuckTicks;
    private double lastX;
    private double lastZ;

    public RoccoFollowGoal(RoccoEntity rocco)
    {
        this.rocco = rocco;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        if (rocco.getRoccoState() != RoccoState.ACTIVE
                || !RoccoCommonConfig.FOLLOW_ENABLED.get()
                || !rocco.isFollowing())
        {
            return false;
        }

        // Mientras tiene trabajo pendiente, seguir al jugador lo sacaria del cultivo.
        if (rocco.isWorking() || rocco.queueSize() > 0 || rocco.isLidOpen())
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
                && rocco.isFollowing()
                && !rocco.isWorking()
                && rocco.queueSize() == 0
                && rocco.distanceToSqr(this.target) > sqr(RoccoCommonConfig.FOLLOW_STOP_DISTANCE.get());
    }

    @Override
    public void start()
    {
        this.repathCooldown = 0;
        this.stuckTicks = 0;
        this.lastX = rocco.getX();
        this.lastZ = rocco.getZ();
    }

    @Override
    public void stop()
    {
        this.target = null;
        this.stuckTicks = 0;
        rocco.getNavigation().stop();
    }

    @Override
    public void tick()
    {
        if (this.target == null)
        {
            return;
        }

        double distanceSqr = rocco.distanceToSqr(this.target);
        double teleportAt = RoccoCommonConfig.TELEPORT_DISTANCE.get();
        boolean teleportAllowed = teleportAt > 0.0D;

        // Demasiado lejos: ya no tiene sentido caminar.
        if (teleportAllowed && distanceSqr > sqr(teleportAt))
        {
            teleportToPlayer();
            return;
        }

        trackProgress();

        // Atascado o nadando sin poder salir: aparece al lado del jugador aunque no este lejisimo.
        boolean trapped = this.stuckTicks > STUCK_TICKS || (rocco.isInWater() && this.stuckTicks > STUCK_TICKS / 2);
        if (teleportAllowed && trapped && distanceSqr > sqr(RoccoCommonConfig.FOLLOW_START_DISTANCE.get()))
        {
            teleportToPlayer();
            return;
        }

        if (--this.repathCooldown <= 0)
        {
            this.repathCooldown = 10;
            rocco.getNavigation().moveTo(this.target, RoccoCommonConfig.FOLLOW_SPEED.get());
        }
    }

    private void trackProgress()
    {
        double moved = Math.abs(rocco.getX() - this.lastX) + Math.abs(rocco.getZ() - this.lastZ);
        this.lastX = rocco.getX();
        this.lastZ = rocco.getZ();

        if (moved < STUCK_STEP)
        {
            this.stuckTicks++;
        }
        else
        {
            this.stuckTicks = 0;
        }
    }

    private void teleportToPlayer()
    {
        this.stuckTicks = 0;
        rocco.getNavigation().stop();

        BlockPos spot = findSpotNear(this.target);
        if (spot == null)
        {
            return;
        }

        rocco.teleportTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D);
        rocco.getNavigation().stop();
    }

    // Busca un lugar firme y con aire al lado del jugador, para no dejarlo dentro de una pared.
    private BlockPos findSpotNear(Player player)
    {
        if (!(rocco.level() instanceof ServerLevel level))
        {
            return null;
        }

        BlockPos base = player.blockPosition();

        for (int attempt = 0; attempt < 16; attempt++)
        {
            int dx = rocco.getRandom().nextInt(5) - 2;
            int dz = rocco.getRandom().nextInt(5) - 2;
            int dy = rocco.getRandom().nextInt(3) - 1;

            BlockPos candidate = base.offset(dx, dy, dz);

            if (isSafe(level, candidate))
            {
                return candidate;
            }
        }

        // Si no hay nada mejor, el lugar donde esta parado el jugador sirve.
        return isSafe(level, base) ? base : null;
    }

    private static boolean isSafe(ServerLevel level, BlockPos pos)
    {
        BlockPos below = pos.below();

        return level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)
                && !level.getBlockState(below).is(Blocks.WATER)
                && level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir();
    }

    private static double sqr(double value)
    {
        return value * value;
    }
}

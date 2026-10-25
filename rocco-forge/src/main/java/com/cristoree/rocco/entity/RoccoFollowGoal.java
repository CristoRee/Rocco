package com.cristoree.rocco.entity;

import com.cristoree.rocco.config.RoccoCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

// Sigue a su dueño. Si te quedas quieto, lo deja pasear hasta wanderRadius; en cuanto te moves,
// te sigue de cerca. Si quedo muy lejos (teleportDistance) o se traba, aparece al lado tuyo:
// si en ese momento no hay donde pararse (vas volando, nadando), lo vuelve a intentar.
public class RoccoFollowGoal extends Goal
{
    // Sin dueño conocido, sigue al jugador mas cercano dentro de esto.
    private static final double SEARCH_RADIUS = 160.0D;

    // Atascado: se mueve menos que esto por tick (los ticks salen de behavior.antiStuckTicks).
    private static final double STUCK_STEP = 0.02D;

    private final RoccoEntity rocco;
    private Player target;
    private int repathCooldown;
    private int stuckTicks;
    private double lastX;
    private double lastZ;
    private int teleportRetry;

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
        // Sentado o dormido tampoco se mueve.
        if (rocco.isBusy() || rocco.isLidOpen() || rocco.isResting())
        {
            return false;
        }

        Player player = leader();
        if (player == null)
        {
            return false;
        }

        double distanceSqr = rocco.distanceToSqr(player);
        double start = RoccoCommonConfig.FOLLOW_START_DISTANCE.get();
        double wander = Math.max(start, RoccoCommonConfig.WANDER_RADIUS.get());

        // Con el jugador quieto, lo deja pasear hasta wanderRadius; si se mueve, lo sigue de cerca.
        double leash = rocco.isOwnerSettled() ? wander : start;
        if (distanceSqr < sqr(leash))
        {
            return false;
        }

        this.target = player;
        return true;
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.target != null
                && this.target.isAlive()
                && this.target.level() == rocco.level()
                && rocco.getRoccoState() == RoccoState.ACTIVE
                && rocco.isFollowing()
                && !rocco.isBusy()
                && !rocco.isResting()
                && rocco.distanceToSqr(this.target) > sqr(RoccoCommonConfig.FOLLOW_STOP_DISTANCE.get());
    }

    @Override
    public void start()
    {
        this.repathCooldown = 0;
        this.stuckTicks = 0;
        this.teleportRetry = 0;
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

        rocco.getLookControl().setLookAt(this.target, 10.0F, rocco.getMaxHeadXRot());

        double distanceSqr = rocco.distanceToSqr(this.target);
        double teleportAt = RoccoCommonConfig.TELEPORT_DISTANCE.get();
        boolean teleportAllowed = teleportAt > 0.0D;

        trackProgress();

        // Demasiado lejos, o atascado / nadando sin poder salir: aparece al lado del jugador.
        boolean tooFar = teleportAllowed && distanceSqr > sqr(teleportAt);
        int stuckLimit = RoccoCommonConfig.ANTI_STUCK_TICKS.get();
        boolean trapped = RoccoCommonConfig.ANTI_STUCK_ENABLED.get()
                && (this.stuckTicks > stuckLimit || (rocco.isInWater() && this.stuckTicks > stuckLimit / 2));
        if (tooFar || (teleportAllowed && trapped && distanceSqr > sqr(RoccoCommonConfig.FOLLOW_START_DISTANCE.get())))
        {
            // Si ahora no hay donde pararse (va volando, nadando), prueba de nuevo en un rato.
            if (this.teleportRetry-- <= 0)
            {
                this.teleportRetry = teleportNear(rocco, this.target) ? 0 : 10;
                this.stuckTicks = 0;
            }
            if (tooFar)
            {
                return;
            }
        }

        if (--this.repathCooldown <= 0)
        {
            this.repathCooldown = 10;
            double speed = RoccoCommonConfig.FOLLOW_SPEED.get();
            // Si quedo un poco lejos, se apura.
            rocco.getNavigation().moveTo(this.target, distanceSqr > 100.0D ? speed * 1.25D : speed);
        }
    }

    // El dueño si esta en este mundo; si no, el jugador mas cercano.
    @Nullable
    private Player leader()
    {
        Player owner = rocco.getOwnerId() == null ? null : rocco.level().getPlayerByUUID(rocco.getOwnerId());
        if (owner != null && owner.isAlive() && !owner.isSpectator())
        {
            return owner;
        }
        if (rocco.getOwnerId() != null && owner == null)
        {
            // El dueño esta en otra dimension o desconectado: no persigue a otro jugador.
            return null;
        }
        Player nearest = rocco.level().getNearestPlayer(rocco, SEARCH_RADIUS);
        return nearest == null || nearest.isSpectator() ? null : nearest;
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

    // Lo pone al lado del jugador si hay un lugar firme y seguro. false si por ahora no hay.
    public static boolean teleportNear(RoccoEntity rocco, Player player)
    {
        if (!(rocco.level() instanceof ServerLevel level) || player.level() != level)
        {
            return false;
        }

        BlockPos spot = findSpotNear(level, player);
        if (spot == null)
        {
            return false;
        }

        rocco.getNavigation().stop();
        rocco.teleportTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D);
        rocco.getNavigation().stop();
        level.playSound(null, spot, net.minecraft.sounds.SoundEvents.CHORUS_FRUIT_TELEPORT,
                net.minecraft.sounds.SoundSource.NEUTRAL, 0.4F, 1.4F);
        return true;
    }

    // De lo mas cerca a lo mas lejos alrededor del jugador, primero a su altura. Nunca adentro de
    // una pared, en el aire, en lava o en fuego.
    @Nullable
    private static BlockPos findSpotNear(ServerLevel level, Player player)
    {
        BlockPos base = player.blockPosition();
        Vec3 look = player.getLookAngle();

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3})
        {
            for (int dx = -3; dx <= 3; dx++)
            {
                for (int dz = -3; dz <= 3; dz++)
                {
                    if (dx == 0 && dz == 0)
                    {
                        continue;
                    }
                    BlockPos candidate = base.offset(dx, dy, dz);
                    if (!isSafe(level, candidate))
                    {
                        continue;
                    }
                    // Mejor cerca, a la misma altura y a un costado (no justo adelante tapando la vista).
                    double ahead = dx * look.x + dz * look.z;
                    double score = dx * dx + dz * dz + Math.abs(dy) * 3.0D + (ahead > 0.5D ? 2.0D : 0.0D);
                    if (score < bestScore)
                    {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
            if (best != null)
            {
                return best;
            }
        }
        return null;
    }

    private static boolean isSafe(ServerLevel level, BlockPos pos)
    {
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());

        return ground.isFaceSturdy(level, below, Direction.UP)
                && ground.getFluidState().isEmpty()
                && !ground.is(BlockTags.FIRE)
                && !ground.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                && feet.getCollisionShape(level, pos).isEmpty() && feet.getFluidState().isEmpty()
                && !feet.is(BlockTags.FIRE) && !feet.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                && head.getCollisionShape(level, pos.above()).isEmpty() && head.getFluidState().isEmpty();
    }

    private static double sqr(double value)
    {
        return value * value;
    }
}

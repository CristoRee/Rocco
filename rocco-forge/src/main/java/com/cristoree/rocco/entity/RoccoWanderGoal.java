package com.cristoree.rocco.entity;

import com.cristoree.rocco.config.RoccoCommonConfig;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

// Pasea un poco por su cuenta. Si te esta siguiendo, elige siempre lugares a no mas de
// wanderRadius de vos, asi da vueltas por la casa sin irse lejos. Si le dijiste que espere,
// no se mueve de donde lo dejaste.
public class RoccoWanderGoal extends Goal
{
    private static final int CHANCE = 100;

    private final RoccoEntity rocco;
    private Vec3 destination;

    public RoccoWanderGoal(RoccoEntity rocco)
    {
        this.rocco = rocco;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        // Si le dijiste que espere, se queda donde esta: no se va paseando de a poco.
        if (!rocco.canWander() || !rocco.isFollowing() || rocco.getRandom().nextInt(CHANCE) != 0)
        {
            return false;
        }

        double radius = RoccoCommonConfig.WANDER_RADIUS.get();
        Player owner = rocco.getOwnerId() == null ? null : rocco.level().getPlayerByUUID(rocco.getOwnerId());
        Player anchor = owner != null ? owner : rocco.level().getNearestPlayer(rocco, 32.0D);

        // Siguiendo a alguien que se esta moviendo, el que manda es el seguimiento.
        if (anchor == null || !rocco.isOwnerSettled())
        {
            return false;
        }

        for (int attempt = 0; attempt < 10; attempt++)
        {
            Vec3 candidate = DefaultRandomPos.getPos(rocco, (int) Math.ceil(radius), 4);
            if (candidate == null)
            {
                continue;
            }
            if (candidate.distanceToSqr(anchor.position()) <= radius * radius)
            {
                this.destination = candidate;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse()
    {
        return !rocco.getNavigation().isDone() && rocco.canWander();
    }

    @Override
    public void start()
    {
        net.minecraft.world.level.pathfinder.Path path = rocco.getNavigation().createPath(this.destination.x, this.destination.y, this.destination.z, 0);
        if (path != null && !RoccoGates.crossesClosedGate(rocco.level(), path))
        {
            rocco.getNavigation().moveTo(path, 0.6D);
        }
    }

    @Override
    public void stop()
    {
        this.destination = null;
    }
}

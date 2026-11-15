package com.cristoree.rocco.work;

import com.cristoree.rocco.entity.RoccoEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// "Llevame a casa": Rocco va adelante hasta un lugar que le pediste recordar, de a tramos, y te
// espera si te quedas atras. Si el camino se complica (agua, montaña), te dice hacia donde y
// cuanto falta para que sigas solo.
public final class GuideErrand extends Errand
{
    private static final int STEP = 28;
    private static final int MAX_TICKS = 20 * 60 * 10;

    private final String name;
    private final RoccoEntity.Place place;

    private int stuckTicks;
    private Vec3 lastPos;
    private int waitTicks;
    private boolean waitedNoticed;

    public GuideErrand(RoccoEntity rocco, @Nullable Player requester, String name, RoccoEntity.Place place)
    {
        super(rocco, requester);
        this.name = name;
        this.place = place;
    }

    @Override
    protected Status step()
    {
        if (!this.place.dimension().equals(this.level.dimension().location().toString()))
        {
            say("guide_other_dimension", "place", this.name);
            return Status.FAILED;
        }

        BlockPos goal = this.place.pos();
        double toGoal = Math.sqrt(this.rocco.blockPosition().distSqr(goal));
        ServerPlayer player = requester();

        if (toGoal < 4.0D)
        {
            say("guide_arrived", "place", this.name);
            return Status.DONE;
        }
        if (this.age > MAX_TICKS)
        {
            say("guide_stuck", "place", this.name, "where", directions(player, goal));
            return Status.DONE;
        }

        // Si te quedaste atras, te espera mirandote.
        if (player != null && this.rocco.distanceTo(player) > 14.0D)
        {
            this.rocco.getNavigation().stop();
            this.rocco.getLookControl().setLookAt(player);
            if (++this.waitTicks > 20 * 40)
            {
                say("guide_lost", "place", this.name, "where", directions(player, goal));
                return Status.FAILED;
            }
            if (!this.waitedNoticed && this.waitTicks > 60)
            {
                this.waitedNoticed = true;
                say("guide_wait");
            }
            return Status.RUNNING;
        }
        this.waitTicks = 0;

        // Trabado: el camino no da, mejor le explica hacia donde ir.
        Vec3 here = this.rocco.position();
        if (this.lastPos != null && here.distanceToSqr(this.lastPos) < 0.01D)
        {
            this.stuckTicks++;
        }
        else
        {
            this.stuckTicks = 0;
        }
        this.lastPos = here;
        if (this.stuckTicks > 20 * 12)
        {
            say("guide_stuck", "place", this.name, "where", directions(player, goal));
            return Status.DONE;
        }

        if (this.age % 20 == 1 || this.rocco.getNavigation().isDone())
        {
            BlockPos next = waypoint(goal, toGoal);
            this.rocco.getNavigation().moveTo(next.getX() + 0.5D, next.getY(), next.getZ() + 0.5D, 1.05D);
        }
        return Status.RUNNING;
    }

    // Lejos, un punto a STEP bloques en la direccion correcta, apoyado en el suelo.
    private BlockPos waypoint(BlockPos goal, double distance)
    {
        if (distance <= STEP * 1.5D)
        {
            return goal;
        }
        Vec3 from = this.rocco.position();
        Vec3 direction = new Vec3(goal.getX() + 0.5D - from.x, 0.0D, goal.getZ() + 0.5D - from.z).normalize();
        int x = (int) Math.floor(from.x + direction.x * STEP);
        int z = (int) Math.floor(from.z + direction.z * STEP);
        int y = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    // "a 230 bloques al noreste (x 120, z -340)".
    public static String directions(@Nullable Player from, BlockPos goal)
    {
        if (from == null)
        {
            return "en " + goal.getX() + ", " + goal.getY() + ", " + goal.getZ();
        }
        double dx = goal.getX() + 0.5D - from.getX();
        double dz = goal.getZ() + 0.5D - from.getZ();
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        return "a " + distance + " bloques " + compass(dx, dz) + " (" + goal.getX() + ", " + goal.getY() + ", " + goal.getZ() + ")";
    }

    // El norte en Minecraft es Z negativo.
    public static String compass(double dx, double dz)
    {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        if (angle < 0)
        {
            angle += 360.0D;
        }
        String[] names = {"al norte", "al noreste", "al este", "al sureste", "al sur", "al suroeste", "al oeste", "al noroeste"};
        return names[(int) Math.round(angle / 45.0D) % 8];
    }

    @Override
    public String describe()
    {
        return "llevandote a " + this.name;
    }
}

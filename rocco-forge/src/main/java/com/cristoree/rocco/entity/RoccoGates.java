package com.cristoree.rocco.entity;

import com.cristoree.rocco.config.RoccoCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

// Puertas de valla y trampillas. Las puertas comunes las abre OpenDoorGoal, del juego; estas el
// juego no sabe usarlas. Mira los proximos pasos de su camino: abre la puerta de valla (o la
// trampilla que le queda a la altura de la cabeza), cierra la trampilla abierta que le corta el paso
// o que es un agujero en el piso, y al pasar la deja como estaba.
final class RoccoGates
{
    // El camino puede pasar por las puertas de valla cerradas: las abre al llegar.
    static final class Nodes extends WalkNodeEvaluator
    {
        @Override
        public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z)
        {
            BlockPathTypes type = super.getBlockPathType(level, x, y, z);
            if (type == BlockPathTypes.FENCE && canOpenDoors() && level.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof FenceGateBlock)
            {
                return BlockPathTypes.WALKABLE_DOOR;
            }
            return type;
        }
    }

    private static final int LOOK_AHEAD = 3;
    private static final double REACH = 2.2D;
    // Ya paso: lejos de la puerta y sin nadie adentro.
    private static final double PASSED = 1.6D;
    private static final int GIVE_UP_TICKS = 20 * 8;

    private static final class Touched
    {
        final BlockPos pos;
        final boolean wasOpen;
        int age;

        Touched(BlockPos pos, boolean wasOpen)
        {
            this.pos = pos;
            this.wasOpen = wasOpen;
        }
    }

    private final RoccoEntity rocco;
    private final List<Touched> touched = new ArrayList<>();

    RoccoGates(RoccoEntity rocco)
    {
        this.rocco = rocco;
    }

    void tick(ServerLevel level)
    {
        putBack(level, false);
        if (!RoccoCommonConfig.CAN_OPEN_DOORS.get())
        {
            return;
        }
        Path path = this.rocco.getNavigation().getPath();
        if (path == null || path.isDone())
        {
            return;
        }
        int next = path.getNextNodeIndex();
        BlockPos previous = this.rocco.blockPosition();
        for (int i = next; i < Math.min(next + LOOK_AHEAD, path.getNodeCount()); i++)
        {
            BlockPos node = path.getNodePos(i);
            if (node.distSqr(this.rocco.blockPosition()) > REACH * REACH)
            {
                break;
            }
            look(level, node, previous);
            previous = node;
        }
    }

    private void look(ServerLevel level, BlockPos node, BlockPos from)
    {
        BlockState state = level.getBlockState(node);
        if (state.getBlock() instanceof FenceGateBlock)
        {
            if (!state.getValue(BlockStateProperties.OPEN))
            {
                toggle(level, node);
            }
        }
        else if (isWoodenTrapdoor(state))
        {
            boolean open = state.getValue(BlockStateProperties.OPEN);
            // Abierta es una pared finita: corta el paso si la cruza de frente.
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            boolean across = facing.getAxis() == Direction.getNearest(node.getX() - from.getX(), 0, node.getZ() - from.getZ()).getAxis();
            // Cerrada arriba del todo le da en la cabeza; cerrada abajo se la pisa como un escalon.
            boolean atHead = !open && state.getValue(BlockStateProperties.HALF) == Half.TOP;
            if ((open && across && !node.equals(from)) || atHead)
            {
                toggle(level, node);
            }
        }
        // Una trampilla abierta en el piso: para el camino es piso, pero se caeria.
        BlockPos below = node.below();
        BlockState floor = level.getBlockState(below);
        if (isWoodenTrapdoor(floor) && floor.getValue(BlockStateProperties.OPEN) && level.getBlockState(below.below()).isAir())
        {
            toggle(level, below);
        }
    }

    private void toggle(ServerLevel level, BlockPos pos)
    {
        boolean wasOpen = level.getBlockState(pos).getValue(BlockStateProperties.OPEN);
        if (RoccoWork.toggleOpenable(level, pos, this.rocco))
        {
            this.touched.add(new Touched(pos.immutable(), wasOpen));
        }
    }

    // Lo que toco lo deja como estaba en cuanto paso. all: se va del mundo, sin esperar.
    void putBack(ServerLevel level, boolean all)
    {
        this.touched.removeIf(gate ->
        {
            gate.age++;
            if (!level.isLoaded(gate.pos))
            {
                return true;
            }
            BlockState state = level.getBlockState(gate.pos);
            // Ya no esta, o alguien la dejo como estaba.
            if (!RoccoWork.isOpenable(state) || state.getValue(BlockStateProperties.OPEN) == gate.wasOpen)
            {
                return true;
            }
            double dx = this.rocco.getX() - (gate.pos.getX() + 0.5D);
            double dz = this.rocco.getZ() - (gate.pos.getZ() + 0.5D);
            boolean away = dx * dx + dz * dz > PASSED * PASSED;
            if (!all && !away && gate.age < GIVE_UP_TICKS)
            {
                return false;
            }
            // Con alguien parado adentro no se cierra (te dejaria encerrado o empujado).
            if (!all && !level.getEntitiesOfClass(LivingEntity.class, new AABB(gate.pos)).isEmpty())
            {
                return false;
            }
            RoccoWork.toggleOpenable(level, gate.pos, this.rocco);
            return true;
        });
    }

    // Paseando solo no abre tus corrales: ese camino lo descarta.
    static boolean crossesClosedGate(net.minecraft.world.level.Level level, Path path)
    {
        for (int i = 0; i < path.getNodeCount(); i++)
        {
            BlockState state = level.getBlockState(path.getNodePos(i));
            if (state.getBlock() instanceof FenceGateBlock && !state.getValue(BlockStateProperties.OPEN))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean isWoodenTrapdoor(BlockState state)
    {
        return state.getBlock() instanceof TrapDoorBlock && state.is(BlockTags.WOODEN_TRAPDOORS);
    }
}

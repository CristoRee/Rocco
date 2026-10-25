package com.cristoree.rocco.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Puertas, trampillas y portones: encontrarlos y abrirlos o cerrarlos. La huerta y el resto de
// los trabajos viven en el paquete work.
public final class RoccoWork
{
    public static final int SEARCH_RADIUS = 8;

    // Puertas, trampillas y portones que se pueden abrir o cerrar, de la mas cercana a la mas lejana.
    public static List<BlockPos> findOpenables(ServerLevel level, BlockPos center, int limit)
    {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++)
        {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++)
            {
                for (int dy = -3; dy <= 3; dy++)
                {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (isOpenable(level.getBlockState(cursor)))
                    {
                        found.add(cursor.immutable());
                    }
                }
            }
        }

        return found.stream()
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(center)))
                .limit(limit)
                .toList();
    }

    public static boolean isOpenable(BlockState state)
    {
        return (state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock)
                && state.hasProperty(BlockStateProperties.OPEN);
    }

    public static boolean toggleOpenable(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        if (!isOpenable(state))
        {
            return false;
        }

        boolean nowOpen = !state.getValue(BlockStateProperties.OPEN);
        level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, nowOpen), Block.UPDATE_ALL);

        // Las puertas de madera y las de hierro suenan distinto; este es el generico de madera.
        level.playSound(null, pos,
                nowOpen ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE,
                SoundSource.BLOCKS, 0.9F, 1.0F);
        return true;
    }

    private RoccoWork() {}
}

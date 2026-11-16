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
        // Las de hierro solo con redstone, como para el jugador.
        boolean byHand = state.getBlock() instanceof DoorBlock door ? door.type().canOpenByHand()
                : state.getBlock() instanceof TrapDoorBlock ? state.is(net.minecraft.tags.BlockTags.WOODEN_TRAPDOORS)
                : state.getBlock() instanceof FenceGateBlock;
        return byHand && state.hasProperty(BlockStateProperties.OPEN);
    }

    public static boolean toggleOpenable(ServerLevel level, BlockPos pos)
    {
        return toggleOpenable(level, pos, null);
    }

    public static boolean toggleOpenable(ServerLevel level, BlockPos pos, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.Entity who)
    {
        BlockState state = level.getBlockState(pos);
        if (!isOpenable(state))
        {
            return false;
        }

        boolean nowOpen = !state.getValue(BlockStateProperties.OPEN);
        BlockState changed = state.setValue(BlockStateProperties.OPEN, nowOpen);
        // La puerta de valla se abre para el lado contrario de quien pasa, como con el jugador.
        if (nowOpen && who != null && state.getBlock() instanceof FenceGateBlock
                && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == who.getDirection().getOpposite())
        {
            changed = changed.setValue(BlockStateProperties.HORIZONTAL_FACING, who.getDirection());
        }
        level.setBlock(pos, changed, Block.UPDATE_ALL);

        // Cada una con su sonido.
        net.minecraft.sounds.SoundEvent sound = state.getBlock() instanceof FenceGateBlock
                ? (nowOpen ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE)
                : state.getBlock() instanceof TrapDoorBlock
                ? (nowOpen ? SoundEvents.WOODEN_TRAPDOOR_OPEN : SoundEvents.WOODEN_TRAPDOOR_CLOSE)
                : (nowOpen ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE);
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.9F, 1.0F);
        level.gameEvent(who, nowOpen ? net.minecraft.world.level.gameevent.GameEvent.BLOCK_OPEN : net.minecraft.world.level.gameevent.GameEvent.BLOCK_CLOSE, pos);
        return true;
    }

    private RoccoWork() {}
}

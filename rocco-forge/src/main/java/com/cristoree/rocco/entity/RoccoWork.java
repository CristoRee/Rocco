package com.cristoree.rocco.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Busca en que trabajar y aplica el efecto real. La animacion la maneja la entidad.
public final class RoccoWork
{
    public static final int SEARCH_RADIUS = 8;

    // Tierra de cultivo libre donde se puede sembrar.
    public static List<BlockPos> findFarmland(ServerLevel level, BlockPos center, int limit)
    {
        List<BlockPos> found = new ArrayList<>();

        forEachNearby(center, pos ->
        {
            // El cursor se reutiliza, asi que lo que se guarda tiene que ser una copia.
            if (level.getBlockState(pos).is(Blocks.FARMLAND) && level.getBlockState(pos.above()).isAir())
            {
                found.add(pos.above().immutable());
            }
        });

        return closest(found, center, limit);
    }

    // Cultivos que ya terminaron de crecer.
    public static List<BlockPos> findRipeCrops(ServerLevel level, BlockPos center, int limit)
    {
        List<BlockPos> found = new ArrayList<>();

        forEachNearby(center, pos ->
        {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state))
            {
                found.add(pos.immutable());
            }
        });

        return closest(found, center, limit);
    }

    // Puertas, trampillas y portones que se pueden abrir o cerrar.
    public static List<BlockPos> findOpenables(ServerLevel level, BlockPos center, int limit)
    {
        List<BlockPos> found = new ArrayList<>();

        forEachNearby(center, pos ->
        {
            if (isOpenable(level.getBlockState(pos)))
            {
                found.add(pos.immutable());
            }
        });

        return closest(found, center, limit);
    }

    public static boolean isOpenable(BlockState state)
    {
        return (state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock)
                && state.hasProperty(BlockStateProperties.OPEN);
    }

    // Devuelve el bloque de cultivo que corresponde a una semilla o tuberculo del inventario.
    public static Block cropFor(ItemStack stack)
    {
        if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CropBlock)
        {
            return item.getBlock();
        }
        return null;
    }

    public static boolean plant(ServerLevel level, BlockPos pos, Block crop)
    {
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).is(Blocks.FARMLAND))
        {
            return false;
        }

        level.setBlock(pos, crop.defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.8F, 1.0F);
        return true;
    }

    // Rompe el cultivo y devuelve lo que suelta, para que Rocco lo guarde.
    public static List<ItemStack> harvest(ServerLevel level, BlockPos pos, RoccoEntity rocco)
    {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state))
        {
            return List.of();
        }

        List<ItemStack> drops = Block.getDrops(state, level, pos, null, rocco, ItemStack.EMPTY);

        level.destroyBlock(pos, false, rocco);
        level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);

        return drops;
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

    private static void forEachNearby(BlockPos center, java.util.function.Consumer<BlockPos> action)
    {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++)
        {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++)
            {
                for (int dy = -3; dy <= 3; dy++)
                {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    action.accept(cursor);
                }
            }
        }
    }

    private static List<BlockPos> closest(List<BlockPos> positions, BlockPos center, int limit)
    {
        return positions.stream()
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(center)))
                .limit(limit)
                .toList();
    }

    private RoccoWork() {}
}

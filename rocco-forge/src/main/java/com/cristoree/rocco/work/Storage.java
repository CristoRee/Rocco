package com.cristoree.rocco.work;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Cofres, barriles y cajas de shulker: encontrarlos, abrirlos (con la tapa moviendose de verdad)
// y saber que hay adentro. El cofre de ender es el del dueño de Rocco, como pasa en el juego.
public final class Storage
{
    // Cofres de alrededor, del mas cercano al mas lejano. Un cofre doble aparece una sola vez.
    public static List<BlockPos> find(ServerLevel level, BlockPos center, int radius)
    {
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        double maxSqr = (double) radius * radius;

        int minChunkX = SectionPos.blockToSectionCoord(center.getX() - radius);
        int maxChunkX = SectionPos.blockToSectionCoord(center.getX() + radius);
        int minChunkZ = SectionPos.blockToSectionCoord(center.getZ() - radius);
        int maxChunkZ = SectionPos.blockToSectionCoord(center.getZ() + radius);

        // Recorrer los bloques con entidad de cada chunk es mucho mas barato que mirar bloque por bloque.
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++)
        {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++)
            {
                if (!level.hasChunk(chunkX, chunkZ))
                {
                    continue;
                }

                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                for (BlockEntity entity : chunk.getBlockEntities().values())
                {
                    BlockPos pos = entity.getBlockPos();
                    if (pos.distSqr(center) > maxSqr || Math.abs(pos.getY() - center.getY()) > 6 || !isStorage(entity))
                    {
                        continue;
                    }

                    BlockPos key = canonical(level, pos);
                    if (seen.add(key))
                    {
                        found.add(key);
                    }
                }
            }
        }

        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        return found;
    }

    // Los cofres para un encargo: alrededor de Rocco y alrededor del jugador, del mas cercano a Rocco
    // al mas lejano. Si no hay ninguno a 'radius', busca al doble (hasta 48): para vos la casa de al
    // lado esta "ahi nomas" aunque quede a 20 bloques, y antes decia que no habia ningun cofre.
    public static List<BlockPos> findAround(ServerLevel level, BlockPos center, @Nullable Player player, int radius)
    {
        List<BlockPos> found = findBoth(level, center, player, radius);
        int wider = Math.min(radius * 2, 48);
        return found.isEmpty() && wider > radius ? findBoth(level, center, player, wider) : found;
    }

    private static List<BlockPos> findBoth(ServerLevel level, BlockPos center, @Nullable Player player, int radius)
    {
        Set<BlockPos> found = new java.util.LinkedHashSet<>(find(level, center, radius));
        if (player != null && player.level() == level)
        {
            found.addAll(find(level, player.blockPosition(), radius));
        }
        List<BlockPos> sorted = new ArrayList<>(found);
        sorted.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        return sorted;
    }

    // Bloques con entidad que cumplan algo (hornos, por ejemplo), del mas cercano al mas lejano.
    public static List<BlockPos> findBlockEntities(ServerLevel level, BlockPos center, int radius,
                                                   java.util.function.Predicate<BlockEntity> filter)
    {
        List<BlockPos> found = new ArrayList<>();
        double maxSqr = (double) radius * radius;

        for (int chunkX = SectionPos.blockToSectionCoord(center.getX() - radius);
             chunkX <= SectionPos.blockToSectionCoord(center.getX() + radius); chunkX++)
        {
            for (int chunkZ = SectionPos.blockToSectionCoord(center.getZ() - radius);
                 chunkZ <= SectionPos.blockToSectionCoord(center.getZ() + radius); chunkZ++)
            {
                if (!level.hasChunk(chunkX, chunkZ))
                {
                    continue;
                }
                for (BlockEntity entity : level.getChunk(chunkX, chunkZ).getBlockEntities().values())
                {
                    BlockPos pos = entity.getBlockPos();
                    if (pos.distSqr(center) <= maxSqr && Math.abs(pos.getY() - center.getY()) <= 6 && filter.test(entity))
                    {
                        found.add(pos.immutable());
                    }
                }
            }
        }

        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        return found;
    }

    // El bloque mas cercano que cumpla algo (una mesa de crafteo, por ejemplo). null si no hay.
    @Nullable
    public static BlockPos findBlock(ServerLevel level, BlockPos center, int radius, int height,
                                     java.util.function.Predicate<BlockState> filter)
    {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dz = -radius; dz <= radius; dz++)
            {
                for (int dy = -height; dy <= height; dy++)
                {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    double distance = cursor.distSqr(center);
                    if (distance < bestDistance && filter.test(level.getBlockState(cursor)))
                    {
                        best = cursor.immutable();
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private static boolean isStorage(BlockEntity entity)
    {
        return entity instanceof ChestBlockEntity
                || entity instanceof BarrelBlockEntity
                || entity instanceof ShulkerBoxBlockEntity;
    }

    public static boolean isStorage(ServerLevel level, BlockPos pos)
    {
        BlockEntity entity = level.getBlockEntity(pos);
        return (entity != null && isStorage(entity)) || level.getBlockState(pos).getBlock() instanceof EnderChestBlock;
    }

    // De un cofre doble se queda con una mitad fija, asi no se cuenta dos veces.
    private static BlockPos canonical(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE)
        {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            return other.asLong() < pos.asLong() ? other.immutable() : pos.immutable();
        }
        return pos.immutable();
    }

    // El contenido entero (las dos mitades de un cofre doble). null si ahi no hay nada que abrir.
    @Nullable
    public static Container open(ServerLevel level, BlockPos pos, @Nullable Player owner)
    {
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof ChestBlock chest)
        {
            return ChestBlock.getContainer(chest, state, level, pos, true);
        }
        if (state.getBlock() instanceof EnderChestBlock)
        {
            return owner == null ? null : owner.getEnderChestInventory();
        }

        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof BarrelBlockEntity || entity instanceof ShulkerBoxBlockEntity)
        {
            return (Container) entity;
        }
        return null;
    }

    // Abre o cierra la tapa como si lo usara un jugador, con su sonido.
    public static void animate(ServerLevel level, BlockPos pos, boolean open)
    {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof ChestBlock)
        {
            level.blockEvent(pos, block, 1, open ? 1 : 0);
            if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE)
            {
                level.blockEvent(pos.relative(ChestBlock.getConnectedDirection(state)), block, 1, open ? 1 : 0);
            }
            level.playSound(null, pos, open ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE,
                    SoundSource.BLOCKS, 0.5F, 0.9F + level.random.nextFloat() * 0.1F);
        }
        else if (block instanceof EnderChestBlock)
        {
            level.blockEvent(pos, block, 1, open ? 1 : 0);
            level.playSound(null, pos, open ? SoundEvents.ENDER_CHEST_OPEN : SoundEvents.ENDER_CHEST_CLOSE,
                    SoundSource.BLOCKS, 0.5F, 0.9F + level.random.nextFloat() * 0.1F);
        }
        else if (block instanceof BarrelBlock)
        {
            level.setBlock(pos, state.setValue(BarrelBlock.OPEN, open), Block.UPDATE_ALL);
            level.playSound(null, pos, open ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE,
                    SoundSource.BLOCKS, 0.5F, 0.9F + level.random.nextFloat() * 0.1F);
        }
        else if (block instanceof ShulkerBoxBlock)
        {
            level.blockEvent(pos, block, 1, open ? 1 : 0);
            level.playSound(null, pos, open ? SoundEvents.SHULKER_BOX_OPEN : SoundEvents.SHULKER_BOX_CLOSE,
                    SoundSource.BLOCKS, 0.5F, 0.9F + level.random.nextFloat() * 0.1F);
        }
    }

    // El bloque al que apunta el jugador, hasta 'reach' bloques. null si mira al aire.
    @Nullable
    public static BlockHitResult lookedAt(Player player, double reach)
    {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(reach));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    // El cofre que mira el jugador, si mira uno.
    @Nullable
    public static BlockPos lookedAtStorage(Player player)
    {
        if (!(player.level() instanceof ServerLevel level))
        {
            return null;
        }
        BlockHitResult hit = lookedAt(player, 6.0D);
        if (hit == null || !isStorage(level, hit.getBlockPos()))
        {
            return null;
        }
        return canonical(level, hit.getBlockPos());
    }

    private Storage() {}
}

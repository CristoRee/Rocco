package com.cristoree.rocco.work;

import com.cristoree.rocco.entity.RoccoEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.StemGrownBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Todo lo de la huerta: que esta maduro, como se cosecha cada cosa y como se replanta.
//
// Plantar gasta semillas, como al jugador: una por planta. Al cosechar, lo replantado sale de lo
// que solto la planta (el trigo siempre suelta al menos una semilla) o de su inventario.
public final class Crops
{
    public enum Soil { FARMLAND, SOUL_SAND, GROUND }

    // Lo que se planta: el bloque recien sembrado, donde va y el item que se muestra en la mano.
    public record Plantable(BlockState state, Soil soil, Item shown) {}

    // Lo que se cosecha y se guarda en la mano para que se vea, junto con lo que solto.
    public record Harvest(List<ItemStack> drops) {}

    // ---------- que se puede plantar ----------

    // El trigo se planta con semillas, la sandia con las suyas, etc. Si piden el fruto, se
    // traduce a lo que se siembra.
    @Nullable
    public static Plantable plantableFor(Item item)
    {
        Item seed = item;
        if (item == Items.WHEAT) seed = Items.WHEAT_SEEDS;
        else if (item == Items.BEETROOT) seed = Items.BEETROOT_SEEDS;
        else if (item == Items.MELON_SLICE || item == Items.MELON) seed = Items.MELON_SEEDS;
        else if (item == Items.PUMPKIN || item == Items.CARVED_PUMPKIN) seed = Items.PUMPKIN_SEEDS;
        else if (item == Items.TORCHFLOWER) seed = Items.TORCHFLOWER_SEEDS;

        if (!(seed instanceof BlockItem blockItem))
        {
            return null;
        }

        Block block = blockItem.getBlock();
        if (block instanceof CropBlock crop)
        {
            return new Plantable(crop.getStateForAge(0), Soil.FARMLAND, seed);
        }
        if (block instanceof StemBlock)
        {
            return new Plantable(block.defaultBlockState(), Soil.FARMLAND, seed);
        }
        if (block instanceof NetherWartBlock)
        {
            return new Plantable(block.defaultBlockState(), Soil.SOUL_SAND, seed);
        }
        if (block instanceof SaplingBlock)
        {
            return new Plantable(block.defaultBlockState(), Soil.GROUND, seed);
        }
        return null;
    }

    // Lugares libres donde va esa planta, de los mas cercanos a los mas lejanos.
    public static List<BlockPos> freeSpots(ServerLevel level, BlockPos center, Plantable plant, int radius, int limit)
    {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dz = -radius; dz <= radius; dz++)
            {
                for (int dy = -3; dy <= 3; dy++)
                {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (fits(level, cursor, plant) && spacedOut(level, cursor, plant, found))
                    {
                        found.add(cursor.immutable());
                    }
                }
            }
        }

        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        return found.size() > limit ? found.subList(0, limit) : found;
    }

    public static boolean fits(ServerLevel level, BlockPos pos, Plantable plant)
    {
        if (!level.getBlockState(pos).isAir())
        {
            return false;
        }

        BlockState below = level.getBlockState(pos.below());
        boolean soilOk = switch (plant.soil())
        {
            case FARMLAND -> below.is(Blocks.FARMLAND);
            case SOUL_SAND -> below.is(Blocks.SOUL_SAND);
            case GROUND -> below.is(BlockTags.DIRT) && level.getBlockState(pos.above()).isAir();
        };
        return soilOk && plant.state().canSurvive(level, pos);
    }

    // Los arbolitos necesitan lugar para crecer: no los pega uno al lado del otro.
    private static boolean spacedOut(ServerLevel level, BlockPos pos, Plantable plant, List<BlockPos> chosen)
    {
        if (plant.soil() != Soil.GROUND)
        {
            return true;
        }

        for (BlockPos other : chosen)
        {
            if (other.distManhattan(pos) < 4)
            {
                return false;
            }
        }
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 2, 2)))
        {
            BlockState state = level.getBlockState(near);
            if (state.getBlock() instanceof SaplingBlock || state.is(BlockTags.LOGS))
            {
                return false;
            }
        }
        return true;
    }

    // Planta gastando una semilla de su inventario. false si no habia lugar o no le quedaban.
    public static boolean plant(ServerLevel level, BlockPos pos, Plantable plant, RoccoEntity rocco)
    {
        if (!fits(level, pos, plant) || Inv.take(rocco.inventory(), plant.shown(), 1).isEmpty())
        {
            return false;
        }
        level.setBlock(pos, plant.state(), Block.UPDATE_ALL);
        level.playSound(null, pos, plant.soil() == Soil.GROUND ? SoundEvents.GRASS_PLACE : SoundEvents.CROP_PLANTED,
                SoundSource.BLOCKS, 0.5F, 1.0F);
        return true;
    }

    public static int seeds(RoccoEntity rocco, Plantable plant)
    {
        return Inv.count(rocco.inventory(), plant.shown());
    }

    // Lo primero que tenga para sembrar en tierra arada (semillas, papas, zanahorias...).
    @Nullable
    public static Plantable firstInInventory(RoccoEntity rocco)
    {
        for (ItemStack stack : Inv.contents(rocco.inventory()))
        {
            Plantable plant = plantableFor(stack.getItem());
            if (plant != null && plant.soil() == Soil.FARMLAND)
            {
                return plant;
            }
        }
        return null;
    }

    // Las semillas de lo que hay plantado alrededor: lo que se guarda para replantar.
    public static java.util.Set<Item> seedsAround(ServerLevel level, BlockPos center, int radius)
    {
        java.util.Set<Item> seeds = new java.util.LinkedHashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -3, -radius), center.offset(radius, 3, radius)))
        {
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            if (block instanceof CropBlock || block instanceof StemBlock || block instanceof AttachedStemBlock
                    || block instanceof NetherWartBlock)
            {
                seeds.add(seedOf(block instanceof AttachedStemBlock ? stemOf(block) : block));
            }
        }
        return seeds;
    }

    // El cultivo que mas hay en la huerta, para replantar lo mismo donde quedo libre.
    @Nullable
    public static Plantable mostCommon(ServerLevel level, BlockPos center, int radius)
    {
        Map<Block, Integer> counts = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -3, -radius), center.offset(radius, 3, radius)))
        {
            Block block = level.getBlockState(pos).getBlock();
            if (block instanceof CropBlock || block instanceof StemBlock || block instanceof AttachedStemBlock)
            {
                counts.merge(block instanceof AttachedStemBlock ? stemOf(block) : block, 1, Integer::sum);
            }
        }

        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> plantableFor(seedOf(entry.getKey())))
                .orElse(null);
    }

    private static Block stemOf(Block attached)
    {
        if (attached == Blocks.ATTACHED_MELON_STEM) return Blocks.MELON_STEM;
        if (attached == Blocks.ATTACHED_PUMPKIN_STEM) return Blocks.PUMPKIN_STEM;
        return attached;
    }

    private static Item seedOf(Block block)
    {
        ItemStack clone = block.getCloneItemStack(null, BlockPos.ZERO, block.defaultBlockState());
        return clone.isEmpty() ? Items.WHEAT_SEEDS : clone.getItem();
    }

    // ---------- que esta maduro ----------

    public static boolean isRipe(ServerLevel level, BlockPos pos, BlockState state)
    {
        Block block = state.getBlock();

        if (block instanceof CropBlock crop)
        {
            return crop.isMaxAge(state);
        }
        if (block instanceof NetherWartBlock)
        {
            return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        }
        if (block instanceof CocoaBlock)
        {
            return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
        }
        if (block instanceof SweetBerryBushBlock)
        {
            return state.getValue(SweetBerryBushBlock.AGE) >= 2;
        }
        if (block instanceof StemGrownBlock)
        {
            return attachedStemNear(level, pos);
        }
        // La caña se corta dejando la de abajo, asi vuelve a crecer.
        if (block instanceof SugarCaneBlock)
        {
            return level.getBlockState(pos.below()).is(Blocks.SUGAR_CANE)
                    && !level.getBlockState(pos.below(2)).is(Blocks.SUGAR_CANE);
        }
        return false;
    }

    private static boolean attachedStemNear(ServerLevel level, BlockPos fruit)
    {
        for (Direction direction : Direction.Plane.HORIZONTAL)
        {
            BlockState side = level.getBlockState(fruit.relative(direction));
            if (side.getBlock() instanceof AttachedStemBlock
                    && side.getValue(AttachedStemBlock.FACING) == direction.getOpposite())
            {
                return true;
            }
        }
        return false;
    }

    public static List<BlockPos> findRipe(ServerLevel level, BlockPos center, int radius, int limit)
    {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dz = -radius; dz <= radius; dz++)
            {
                for (int dy = -3; dy <= 3; dy++)
                {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (isRipe(level, cursor, level.getBlockState(cursor)))
                    {
                        found.add(cursor.immutable());
                    }
                }
            }
        }

        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        return found.size() > limit ? found.subList(0, limit) : found;
    }

    // ---------- cosechar ----------

    // Cosecha y deja replantado lo que se replanta, con una semilla de lo que solto (o de las
    // suyas). Si no hay ninguna, queda el lugar libre.
    public static List<ItemStack> harvest(ServerLevel level, BlockPos pos, RoccoEntity rocco)
    {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (!isRipe(level, pos, state))
        {
            return List.of();
        }

        List<ItemStack> drops = new ArrayList<>();

        if (block instanceof SweetBerryBushBlock)
        {
            int age = state.getValue(SweetBerryBushBlock.AGE);
            int berries = 1 + level.random.nextInt(2) + (age >= 3 ? 1 : 0);
            drops.add(new ItemStack(Items.SWEET_BERRIES, berries));
            level.setBlock(pos, state.setValue(SweetBerryBushBlock.AGE, 1), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F,
                    0.8F + level.random.nextFloat() * 0.4F);
            return drops;
        }

        if (block instanceof SugarCaneBlock)
        {
            // Esta y todas las de arriba.
            BlockPos cursor = pos;
            List<BlockPos> canes = new ArrayList<>();
            while (level.getBlockState(cursor).is(Blocks.SUGAR_CANE))
            {
                canes.add(cursor);
                cursor = cursor.above();
            }
            for (int i = canes.size() - 1; i >= 0; i--)
            {
                drops.addAll(Block.getDrops(level.getBlockState(canes.get(i)), level, canes.get(i), null, rocco, ItemStack.EMPTY));
                level.destroyBlock(canes.get(i), false, rocco);
            }
            return drops;
        }

        // Sandia y calabaza salen mejor con hacha (Fortuna en la sandia), como al jugador.
        ItemStack tool = ItemStack.EMPTY;
        if (block instanceof StemGrownBlock)
        {
            Tools.Choice choice = Tools.best(rocco.inventory(), state, level, pos);
            if (choice != null && !choice.byHand())
            {
                tool = rocco.inventory().getItem(choice.slot());
            }
        }

        drops.addAll(Block.getDrops(state, level, pos, null, rocco, tool));
        Item seed = seedOf(block);
        level.destroyBlock(pos, false, rocco);

        BlockState replant = replantState(state);
        if (replant != null && replant.canSurvive(level, pos) && takeSeed(drops, seed, rocco))
        {
            level.setBlock(pos, replant, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.4F, 1.0F);
        }
        drops.removeIf(ItemStack::isEmpty);
        return drops;
    }

    // Una semilla para replantar: de lo que acaba de soltar la planta, o si no, de las suyas.
    private static boolean takeSeed(List<ItemStack> drops, Item seed, RoccoEntity rocco)
    {
        for (ItemStack drop : drops)
        {
            if (drop.is(seed) && !drop.isEmpty())
            {
                drop.shrink(1);
                return true;
            }
        }
        return !Inv.take(rocco.inventory(), seed, 1).isEmpty();
    }

    @Nullable
    private static BlockState replantState(BlockState ripe)
    {
        Block block = ripe.getBlock();
        if (block instanceof CropBlock crop)
        {
            return crop.getStateForAge(0);
        }
        if (block instanceof NetherWartBlock)
        {
            return ripe.setValue(NetherWartBlock.AGE, 0);
        }
        if (block instanceof CocoaBlock)
        {
            return ripe.setValue(CocoaBlock.AGE, 0);
        }
        return null;
    }

    // Lo que se ve en la mano mientras cosecha.
    public static ItemStack preview(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof SweetBerryBushBlock)
        {
            return new ItemStack(Items.SWEET_BERRIES);
        }
        return state.getBlock().getCloneItemStack(level, pos, state);
    }

    private Crops() {}
}

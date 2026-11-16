package com.cristoree.rocco.work;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Coloca un bloque que lleva encima: donde mira el jugador, o al lado de Rocco. Si no lo tiene
// pero se puede fabricar, lo craftea primero (y si faltan materiales, los pide).
public final class PlaceErrand extends Errand
{
    private enum Phase { CHECK, PLACING, DONE }

    private final Item item;
    @Nullable
    private final BlockHitResult hint;


    private Phase phase = Phase.CHECK;
    private boolean craftTried;
    @Nullable
    private BlockPos placedAt;

    public PlaceErrand(RoccoEntity rocco, @Nullable Player requester, Item item, @Nullable BlockHitResult hint)
    {
        super(rocco, requester);
        this.item = item;
        this.hint = hint;
    }

    @Nullable
    public BlockPos placedAt()
    {
        return this.placedAt;
    }

    @Override
    protected Status step()
    {
        switch (this.phase)
        {
            case CHECK ->
            {
                if (!(this.item instanceof BlockItem))
                {
                    say("place_not_block", "item", Names.name(this.item));
                    return Status.FAILED;
                }

                if (Inv.count(this.rocco.inventory(), this.item) == 0)
                {
                    if (!this.craftTried && !Recipes.recipesFor(this.level, Inv.id(this.item)).isEmpty())
                    {
                        this.craftTried = true;
                        delegate(new CraftErrand(this.rocco, requester(), java.util.List.of(this.item), 1, true, true));
                        return Status.RUNNING;
                    }
                    say("place_dont_have", "item", Names.name(this.item));
                    return Status.FAILED;
                }

                BlockHitResult hit = this.hint != null && usable(this.hint) ? this.hint : spotNearRocco();
                if (hit == null)
                {
                    say("place_no_spot");
                    return Status.FAILED;
                }

                BlockPos where = hit.getBlockPos().relative(hit.getDirection());
                work(RoccoTask.INTERACTING, where, new ItemStack(this.item), level -> place(level, hit));
                this.phase = Phase.PLACING;
                return Status.RUNNING;
            }
            case PLACING ->
            {
                this.phase = Phase.DONE;
                if (jobFailed())
                {
                    say("place_cant_reach");
                    return Status.FAILED;
                }
                if (this.placedAt == null)
                {
                    say("place_failed", "item", Names.name(this.item));
                    return Status.FAILED;
                }
                return Status.DONE;
            }
            default ->
            {
                return Status.DONE;
            }
        }
    }

    // El hueco al lado de la cara que apunta el jugador tiene que estar libre.
    private boolean usable(BlockHitResult hit)
    {
        BlockPos where = hit.getBlockPos().relative(hit.getDirection());
        return this.level.getBlockState(where).canBeReplaced() && !occupied(where)
                && where.distSqr(this.rocco.blockPosition()) < 24 * 24;
    }

    // Un lugar firme, libre y a mano, que no sea donde esta parado nadie ni la huerta.
    @Nullable
    private BlockHitResult spotNearRocco()
    {
        BlockPos base = this.rocco.blockPosition();
        BlockHitResult best = null;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, -2), base.offset(2, 1, 2)))
        {
            BlockPos below = pos.below();
            BlockState ground = this.level.getBlockState(below);
            if (!this.level.getBlockState(pos).canBeReplaced()
                    || !this.level.getBlockState(pos).getFluidState().isEmpty()
                    || !ground.isFaceSturdy(this.level, below, Direction.UP)
                    || ground.is(Blocks.FARMLAND)
                    || occupied(pos))
            {
                continue;
            }

            double distance = pos.distSqr(base);
            if (distance >= 1.0D && distance < bestDistance)
            {
                bestDistance = distance;
                best = new BlockHitResult(Vec3.atCenterOf(below).add(0.0D, 0.5D, 0.0D), Direction.UP, below.immutable(), false);
            }
        }
        return best;
    }

    private boolean occupied(BlockPos pos)
    {
        return !this.level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), Entity::isAlive).isEmpty();
    }

    private void place(ServerLevel level, BlockHitResult hit)
    {
        int slot = slotOf(this.item);
        if (slot < 0 || !(this.item instanceof BlockItem blockItem))
        {
            return;
        }

        ItemStack stack = this.rocco.inventory().getItem(slot);
        Direction facing = facingFor(hit.getBlockPos().relative(hit.getDirection()));
        PlacingContext context = new PlacingContext(level, stack, hit, facing);

        InteractionResult result = blockItem.place(context);
        if (result.consumesAction())
        {
            this.placedAt = context.getClickedPos().immutable();
            if (stack.isEmpty())
            {
                this.rocco.inventory().setItem(slot, ItemStack.EMPTY);
            }
            this.rocco.inventory().setChanged();
        }
    }

    // Hacia donde "mira" el que coloca: el bloque queda de frente al jugador (un cofre, un horno).
    private Direction facingFor(BlockPos where)
    {
        Player player = requester();
        Vec3 from = player != null && player.distanceToSqr(Vec3.atCenterOf(where)) < 16 * 16
                ? player.position()
                : this.rocco.position();
        Vec3 delta = Vec3.atCenterOf(where).subtract(from);
        return Direction.getNearest(delta.x, 0.0D, delta.z);
    }

    private int slotOf(Item wanted)
    {
        for (int slot = 0; slot < this.rocco.inventory().getContainerSize(); slot++)
        {
            if (this.rocco.inventory().getItem(slot).is(wanted))
            {
                return slot;
            }
        }
        return -1;
    }

    @Override
    public String describe()
    {
        return "poniendo " + Names.name(this.item);
    }

    // Lo que el juego le preguntaria al jugador (hacia donde mira) lo contesta Rocco. Sin esto
    // el juego busca la mirada de un jugador que no existe.
    private static final class PlacingContext extends BlockPlaceContext
    {
        private final Direction facing;

        PlacingContext(ServerLevel level, ItemStack stack, BlockHitResult hit, Direction facing)
        {
            super(level, null, InteractionHand.MAIN_HAND, stack, hit);
            this.facing = facing;
        }

        @Override
        public Direction getHorizontalDirection()
        {
            return this.facing;
        }

        @Override
        public Direction getNearestLookingDirection()
        {
            return getNearestLookingDirections()[0];
        }

        @Override
        public Direction getNearestLookingVerticalDirection()
        {
            return getClickedFace() == Direction.DOWN ? Direction.UP : Direction.DOWN;
        }

        // Primero contra la cara que se toco (antorcha en la pared o en el piso), despues el resto.
        @Override
        public Direction[] getNearestLookingDirections()
        {
            Direction first = getClickedFace().getOpposite();
            Direction[] out = new Direction[6];
            out[0] = first;
            int i = 1;
            for (Direction direction : new Direction[]{this.facing, Direction.DOWN, Direction.UP,
                    this.facing.getClockWise(), this.facing.getCounterClockWise(), this.facing.getOpposite()})
            {
                boolean seen = false;
                for (int j = 0; j < i; j++)
                {
                    seen |= out[j] == direction;
                }
                if (!seen && i < 6)
                {
                    out[i++] = direction;
                }
            }
            return out;
        }

        @Override
        public float getRotation()
        {
            return this.facing.toYRot();
        }
    }
}

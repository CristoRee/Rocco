package com.cristoree.rocco.work;

import com.cristoree.rocco.entity.RoccoEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// Levanta lo que este tirado cerca, de lo mas cercano a lo mas lejano. Cuidando la casa junta
// solo lo de los animales (lana, huevos, plumas...) alrededor de la casa, y sin hablar.
public final class PickupErrand extends Errand
{
    private static final double RADIUS = 10.0D;
    private static final int MAX_ITEMS = 40;
    private static final int TICKS_PER_ITEM = 120;

    private final Set<UUID> skipped = new HashSet<>();
    @Nullable
    private ItemEntity target;
    private int targetTicks;
    private int picked;
    private int stacks;
    @Nullable
    private net.minecraft.core.BlockPos center;
    private double radius = RADIUS;
    private java.util.function.Predicate<ItemStack> filter = stack -> true;
    private boolean quiet;

    public PickupErrand(RoccoEntity rocco, @Nullable Player requester)
    {
        super(rocco, requester);
    }

    // Solo lo que pase el filtro, alrededor de center, sin avisar nada.
    public PickupErrand around(net.minecraft.core.BlockPos center, double radius, java.util.function.Predicate<ItemStack> filter)
    {
        this.center = center.immutable();
        this.radius = radius;
        this.filter = filter;
        this.quiet = true;
        return this;
    }

    public int picked()
    {
        return this.picked;
    }

    @Override
    protected Status step()
    {
        if (this.target == null || !this.target.isAlive())
        {
            this.target = nextTarget();
            this.targetTicks = 0;
        }

        if (this.target == null || this.stacks >= MAX_ITEMS)
        {
            if (this.quiet)
            {
                return Status.DONE;
            }
            if (this.picked == 0)
            {
                say(this.skipped.isEmpty() ? "pickup_none" : "pickup_full");
                return Status.FAILED;
            }
            say("pickup_done", "count", String.valueOf(this.picked));
            return Status.DONE;
        }

        if (this.rocco.distanceTo(this.target) < 1.6D)
        {
            grab(this.target);
            this.target = null;
            return Status.RUNNING;
        }

        if (++this.targetTicks > TICKS_PER_ITEM)
        {
            this.skipped.add(this.target.getUUID());
            this.target = null;
            return Status.RUNNING;
        }

        if (this.targetTicks % 10 == 1)
        {
            this.rocco.getNavigation().moveTo(this.target, 1.1D);
        }
        return Status.RUNNING;
    }

    @Nullable
    private ItemEntity nextTarget()
    {
        net.minecraft.world.phys.AABB area = this.center == null ? this.rocco.getBoundingBox().inflate(this.radius)
                : new net.minecraft.world.phys.AABB(this.center).inflate(this.radius, 8.0D, this.radius);
        return this.level.getEntitiesOfClass(ItemEntity.class, area,
                        item -> item.isAlive() && !item.hasPickUpDelay() && !this.skipped.contains(item.getUUID())
                                && this.filter.test(item.getItem())
                                && Inv.room(this.rocco.inventory(), item.getItem()) > 0)
                .stream()
                .min(Comparator.comparingDouble(item -> item.distanceToSqr(this.rocco)))
                .orElse(null);
    }

    private void grab(ItemEntity item)
    {
        ItemStack stack = item.getItem();
        int before = stack.getCount();
        ItemStack leftover = Inv.insert(this.rocco.inventory(), stack);
        int taken = before - leftover.getCount();

        if (taken <= 0)
        {
            this.skipped.add(item.getUUID());
            return;
        }

        this.rocco.take(item, taken);
        if (leftover.isEmpty())
        {
            item.discard();
        }
        else
        {
            item.setItem(leftover);
            this.skipped.add(item.getUUID());
        }

        this.picked += taken;
        this.stacks++;
        this.rocco.playSound(SoundEvents.ITEM_PICKUP, 0.4F, 1.4F + this.level.random.nextFloat() * 0.4F);
    }

    @Override
    public String describe()
    {
        return "juntando cosas del piso";
    }
}

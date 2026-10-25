package com.cristoree.rocco.work;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

// Le alcanza cosas al jugador: se acerca y se las tira a las manos, como el allay. Con 'drop'
// las suelta ahi mismo ("tira eso").
public final class GiveErrand extends Errand
{
    private static final double HAND_DISTANCE = 2.8D;

    private final Predicate<ItemStack> filter;
    private final int amount;
    private final boolean drop;

    private int walkTicks;
    private boolean tossed;

    // amount 0 = todo lo que tenga de eso.
    public GiveErrand(RoccoEntity rocco, @Nullable Player requester, Predicate<ItemStack> filter, int amount, boolean drop)
    {
        super(rocco, requester);
        this.filter = filter;
        this.amount = amount;
        this.drop = drop;
    }

    @Override
    protected Status step()
    {
        if (this.tossed)
        {
            return jobFailed() ? Status.FAILED : Status.DONE;
        }

        if (Inv.count(this.rocco.inventory(), this.filter) == 0)
        {
            say("give_none");
            return Status.FAILED;
        }

        if (this.drop)
        {
            this.tossed = true;
            work(RoccoTask.INTERACTING, this.rocco.blockPosition(), ItemStack.EMPTY, level -> toss(null));
            return Status.RUNNING;
        }

        ServerPlayer player = requester();
        if (player == null || player.level() != this.level)
        {
            say("give_no_player");
            return Status.FAILED;
        }

        double distance = this.rocco.distanceTo(player);
        if (distance <= HAND_DISTANCE || (this.walkTicks > 200 && distance < 8.0D))
        {
            this.rocco.getNavigation().stop();
            this.tossed = true;
            work(RoccoEntity.Job.custom(RoccoTask.INTERACTING, player.blockPosition(), ItemStack.EMPTY,
                    level -> toss(player), null).reaching(10.0D));
            return Status.RUNNING;
        }

        if (this.walkTicks > 400)
        {
            say("give_cant_reach");
            return Status.FAILED;
        }

        if (this.walkTicks++ % 10 == 0)
        {
            this.rocco.getNavigation().moveTo(player, 1.1D);
        }
        return Status.RUNNING;
    }

    private void toss(@Nullable ServerPlayer player)
    {
        List<ItemStack> stacks = Inv.take(this.rocco.inventory(), this.filter,
                this.amount > 0 ? this.amount : Integer.MAX_VALUE);
        if (stacks.isEmpty())
        {
            return;
        }

        this.rocco.showInHand(stacks.get(0));
        Vec3 from = new Vec3(this.rocco.getX(), this.rocco.getEyeY() - 0.3D, this.rocco.getZ());
        Vec3 to = player == null
                ? from.add(this.rocco.getLookAngle().scale(1.5D))
                : player.position().add(0.0D, 1.0D, 0.0D);

        for (ItemStack stack : stacks)
        {
            ItemEntity entity = new ItemEntity(this.level, from.x, from.y, from.z, stack);
            Vec3 push = to.subtract(from).normalize().scale(0.3D);
            entity.setDeltaMovement(push.x, push.y + 0.1D, push.z);
            entity.setDefaultPickUpDelay();
            // Marcado como suyo: asi no lo vuelve a levantar como si fuera un regalo.
            entity.setThrower(this.rocco.getUUID());
            this.level.addFreshEntity(entity);
        }

        this.level.playSound(null, this.rocco.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL,
                0.5F, 0.7F + this.level.random.nextFloat() * 0.2F);
    }

    @Override
    public String describe()
    {
        return this.drop ? "soltando cosas" : "alcanzandole cosas al jugador";
    }
}

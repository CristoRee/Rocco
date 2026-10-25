package com.cristoree.rocco.work;

import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

// Va a uno o varios cofres, abre la tapa, saca lo justo y lo guarda en su inventario.
// Con 'give' despues se lo alcanza al jugador ("traeme carbon").
public final class FetchErrand extends Errand
{
    public record Pickup(BlockPos chest, Predicate<ItemStack> filter, int amount) {}

    private final Deque<Pickup> pickups;
    private final Predicate<ItemStack> wantedFilter;
    private final String what;
    private final int wanted;
    private final boolean give;
    private final boolean quiet;

    private int got;
    private boolean visiting;
    private boolean handedOver;

    public FetchErrand(RoccoEntity rocco, @Nullable Player requester, List<Pickup> pickups,
                       Predicate<ItemStack> wantedFilter, String what, int wanted, boolean give, boolean quiet)
    {
        super(rocco, requester);
        this.pickups = new ArrayDeque<>(pickups);
        this.wantedFilter = wantedFilter;
        this.what = what;
        this.wanted = wanted;
        this.give = give;
        this.quiet = quiet;
    }

    // Arma la recorrida para juntar 'amount' de lo pedido entre los cofres de alrededor (o solo
    // en 'onlyChest'). null si en ningun cofre hay.
    @Nullable
    public static FetchErrand plan(RoccoEntity rocco, @Nullable Player requester, Predicate<ItemStack> filter,
                                   String what, int amount, @Nullable BlockPos onlyChest, boolean give, boolean quiet)
    {
        List<BlockPos> chests = onlyChest != null
                ? List.of(onlyChest)
                : Storage.find((net.minecraft.server.level.ServerLevel) rocco.level(), rocco.blockPosition(),
                        RoccoCommonConfig.WORK_SEARCH_RADIUS.get());

        List<Pickup> pickups = new ArrayList<>();
        int left = amount;
        for (BlockPos chest : chests)
        {
            Container container = Storage.open((net.minecraft.server.level.ServerLevel) rocco.level(), chest, requester);
            if (container == null)
            {
                continue;
            }
            int available = Inv.count(container, filter);
            if (available <= 0)
            {
                continue;
            }
            int take = Math.min(available, left);
            pickups.add(new Pickup(chest, filter, take));
            left -= take;
            if (left <= 0)
            {
                break;
            }
        }

        if (pickups.isEmpty())
        {
            return null;
        }
        return new FetchErrand(rocco, requester, pickups, filter, what, amount, give, quiet);
    }

    @Override
    protected Status step()
    {
        if (this.handedOver)
        {
            return Status.DONE;
        }

        if (this.visiting)
        {
            this.visiting = false;
            if (jobFailed() && !this.quiet)
            {
                say("fetch_unreachable");
            }
        }

        Pickup next = this.pickups.poll();
        if (next != null)
        {
            work(RoccoTask.INTERACTING, next.chest(), ItemStack.EMPTY, level -> takeFrom(next));
            this.visiting = true;
            return Status.RUNNING;
        }

        if (this.got == 0)
        {
            if (!this.quiet)
            {
                say("fetch_none", "item", this.what);
            }
            return Status.FAILED;
        }

        if (!this.quiet && this.got < this.wanted)
        {
            say("fetch_partial", "got", String.valueOf(this.got), "item", this.what);
        }

        if (this.give)
        {
            this.handedOver = true;
            delegate(new GiveErrand(this.rocco, requester(), this.wantedFilter, this.got, false));
            return Status.RUNNING;
        }

        if (!this.quiet)
        {
            say("fetch_done", "amount", this.got + " de " + this.what);
        }
        return Status.DONE;
    }

    private void takeFrom(Pickup pickup)
    {
        Container container = Storage.open(this.level, pickup.chest(), requester());
        if (container == null)
        {
            return;
        }

        Storage.animate(this.level, pickup.chest(), true);
        BlockPos chest = pickup.chest();
        this.rocco.schedule(16, () -> Storage.animate(this.level, chest, false));

        for (ItemStack taken : Inv.take(container, pickup.filter(), pickup.amount()))
        {
            int before = taken.getCount();
            ItemStack leftover = Inv.insert(this.rocco.inventory(), taken);
            this.got += before - leftover.getCount();

            // Si no le entra, lo devuelve al cofre en vez de tirarlo.
            if (!leftover.isEmpty())
            {
                ItemStack back = Inv.insert(container, leftover);
                if (!back.isEmpty())
                {
                    this.rocco.keep(back);
                }
            }
        }

        if (this.got > 0)
        {
            ItemStack shown = this.rocco.inventory().getItem(firstMatching());
            this.rocco.showInHand(shown);
        }
    }

    private int firstMatching()
    {
        for (int slot = 0; slot < this.rocco.inventory().getContainerSize(); slot++)
        {
            if (this.wantedFilter.test(this.rocco.inventory().getItem(slot)))
            {
                return slot;
            }
        }
        return 0;
    }

    @Override
    public String describe()
    {
        return "buscando " + this.what + " en los cofres";
    }
}

package com.cristoree.rocco.work;

import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// Comercia con los aldeanos de alrededor. "Comprar" busca quien te da lo que pediste y le paga
// con lo que Rocco tenga (esmeraldas, trigo...); "vender" busca quien te compra eso por
// esmeraldas. Respeta los precios, el stock y la experiencia del aldeano como un trato normal.
public final class TradeErrand extends Errand
{
    private static final double RADIUS = 20.0D;

    private record Deal(Villager villager, MerchantOffer offer) {}

    private final Names.Query query;
    private final boolean selling;
    private final int times;

    private boolean queued;
    private boolean handedOver;
    private boolean fetchTried;
    private int done;
    private final List<ItemStack> got = new ArrayList<>();

    public TradeErrand(RoccoEntity rocco, @Nullable Player requester, Names.Query query, boolean selling)
    {
        super(rocco, requester);
        this.query = query;
        this.selling = selling;
        this.times = query.count() > 0 ? Math.min(query.count(), 16) : 1;
    }

    @Override
    protected Status step()
    {
        if (this.handedOver)
        {
            return Status.DONE;
        }
        if (this.queued)
        {
            if (this.done == 0)
            {
                say("trade_failed");
                return Status.FAILED;
            }
            say("trade_done", "items", Names.stacks(this.got), "count", String.valueOf(this.done));
            if (RoccoCommonConfig.WORK_GIVE_RESULTS.get() && requester() != null && !this.got.isEmpty())
            {
                List<ItemStack> results = List.copyOf(this.got);
                this.handedOver = true;
                delegate(new GiveErrand(this.rocco, requester(),
                        stack -> results.stream().anyMatch(result -> ItemStack.isSameItem(result, stack)), 0, false));
                return Status.RUNNING;
            }
            return Status.DONE;
        }

        List<Deal> deals = matchingDeals();
        if (deals.isEmpty())
        {
            say(this.selling ? "trade_nobody_buys" : "trade_nobody_sells", "item", Names.name(this.query.first()));
            return Status.FAILED;
        }

        Deal affordable = deals.stream().filter(this::canPay).findFirst().orElse(null);
        if (affordable == null)
        {
            Deal cheapest = deals.get(0);
            if (!this.fetchTried)
            {
                // Lo que falta para pagar, capaz esta en los cofres.
                this.fetchTried = true;
                ItemStack cost = cheapest.offer().getCostA();
                FetchErrand fetch = FetchErrand.plan(this.rocco, requester(), stack -> ItemStack.isSameItem(stack, cost),
                        Names.name(cost), cost.getCount() * this.times, null, false, true);
                if (fetch != null)
                {
                    delegate(fetch);
                    return Status.RUNNING;
                }
            }
            say("trade_cant_pay", "price", price(cheapest.offer()), "item", Names.name(this.query.first()));
            return Status.FAILED;
        }

        Villager villager = affordable.villager();
        work(new RoccoEntity.Job(RoccoTask.INTERACTING, villager.blockPosition(), affordable.offer().getCostA().copy(),
                villager.getId(), level -> trade(affordable), null, 0, 2.8D));
        this.queued = true;
        return Status.RUNNING;
    }

    @Override
    protected boolean childDone(Errand child, boolean success)
    {
        return true;
    }

    private void trade(Deal deal)
    {
        Villager villager = deal.villager();
        MerchantOffer offer = deal.offer();
        ServerPlayer player = requester();
        if (!villager.isAlive() || villager.distanceTo(this.rocco) > 5.0F || villager.isSleeping())
        {
            return;
        }
        // Si otro jugador esta comerciando con el, no se mete en el medio.
        if (villager.isTrading() && villager.getTradingPlayer() != player)
        {
            villager.playSound(SoundEvents.VILLAGER_NO, 1.0F, villager.getVoicePitch());
            return;
        }

        for (int i = 0; i < this.times && !offer.isOutOfStock() && canPay(deal); i++)
        {
            pay(offer.getCostA());
            pay(offer.getCostB());
            ItemStack result = offer.assemble();
            this.got.add(result.copy());
            this.rocco.keep(result);
            this.done++;

            reward(villager, offer, player);
        }

        if (this.done > 0)
        {
            villager.playSound(SoundEvents.VILLAGER_YES, 1.0F, villager.getVoicePitch());
            this.level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.8F, 1.0F);
        }
        else
        {
            villager.playSound(SoundEvents.VILLAGER_NO, 1.0F, villager.getVoicePitch());
        }
    }

    // Lo mismo que cuando comercia un jugador: gasta un uso, el aldeano gana experiencia (y sube de
    // nivel cuando le toca), suelta su bolita de experiencia y recuerda al jugador que se lo pidio.
    // notifyTrade avisa a otros mods con un evento que espera un jugador, asi que se lo pone como
    // comprador solo durante el trato. Sin jugador se hace a mano, sin subir de nivel.
    private void reward(Villager villager, MerchantOffer offer, @Nullable ServerPlayer player)
    {
        if (player == null)
        {
            offer.increaseUses();
            villager.setVillagerXp(villager.getVillagerXp() + offer.getXp());
            if (offer.shouldRewardExp())
            {
                this.level.addFreshEntity(new ExperienceOrb(this.level, villager.getX(), villager.getY() + 0.5D,
                        villager.getZ(), 3 + this.rocco.getRandom().nextInt(4)));
            }
            return;
        }

        boolean borrowed = !villager.isTrading();
        if (borrowed)
        {
            villager.setTradingPlayer(player);
        }
        villager.notifyTrade(offer);
        if (borrowed)
        {
            villager.setTradingPlayer(null);
        }
    }

    // Los tratos que sirven, de los aldeanos mas cercanos primero.
    private List<Deal> matchingDeals()
    {
        List<Villager> villagers = this.level.getEntitiesOfClass(Villager.class, this.rocco.getBoundingBox().inflate(RADIUS),
                villager -> villager.isAlive() && !villager.isBaby() && !villager.isSleeping()
                        && villager.getVillagerData().getProfession() != VillagerProfession.NONE
                        && villager.getVillagerData().getProfession() != VillagerProfession.NITWIT);
        villagers.sort(java.util.Comparator.comparingDouble(villager -> villager.distanceToSqr(this.rocco)));

        List<Deal> deals = new ArrayList<>();
        for (Villager villager : villagers)
        {
            for (MerchantOffer offer : villager.getOffers())
            {
                if (offer.isOutOfStock())
                {
                    continue;
                }
                boolean fits = this.selling
                        ? this.query.items().contains(offer.getCostA().getItem()) && offer.getCostB().isEmpty()
                        : this.query.items().contains(offer.getResult().getItem());
                if (fits)
                {
                    deals.add(new Deal(villager, offer));
                }
            }
        }
        return deals;
    }

    private boolean canPay(Deal deal)
    {
        return has(deal.offer().getCostA()) && has(deal.offer().getCostB());
    }

    private boolean has(ItemStack cost)
    {
        return cost.isEmpty() || Inv.count(this.rocco.inventory(), stack -> ItemStack.isSameItem(stack, cost)) >= cost.getCount();
    }

    private void pay(ItemStack cost)
    {
        if (!cost.isEmpty())
        {
            Inv.take(this.rocco.inventory(), stack -> ItemStack.isSameItem(stack, cost), cost.getCount());
        }
    }

    // "5 de Esmeralda" o "20 de Trigo y 1 de Libro".
    static String price(MerchantOffer offer)
    {
        String text = offer.getCostA().getCount() + " de " + Names.name(offer.getCostA());
        if (!offer.getCostB().isEmpty())
        {
            text += " y " + offer.getCostB().getCount() + " de " + Names.name(offer.getCostB());
        }
        return text;
    }

    @Override
    public String describe()
    {
        return (this.selling ? "vendiendo " : "comprando ") + Names.name(this.query.first()) + " a un aldeano";
    }
}

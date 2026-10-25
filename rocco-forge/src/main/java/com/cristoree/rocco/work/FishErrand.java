package com.cristoree.rocco.work;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// Pesca con caña en el agua mas cercana: tira, espera a que pique (Atraccion lo acorta, Suerte
// marina mejora lo que sale) y saca lo que diga la tabla de pesca del juego. Lo guarda en su tapa.
// Sin el anzuelo de verdad no salen los tesoros de mar abierto: solo peces y cosas sueltas.
public final class FishErrand extends Errand
{
    private enum Phase { FIND, CAST, WAIT, REEL, DONE }

    private static final int RADIUS = 12;

    private final int wanted;
    private Phase phase = Phase.FIND;
    @Nullable
    private BlockPos water;
    private int waitLeft;
    private int caught;
    private boolean craftTried;
    private final List<ItemStack> haul = new ArrayList<>();

    public FishErrand(RoccoEntity rocco, @Nullable Player requester, int wanted)
    {
        super(rocco, requester);
        this.wanted = wanted > 0 ? Math.min(wanted, 32) : 5;
    }

    @Override
    protected Status step()
    {
        switch (this.phase)
        {
            case FIND ->
            {
                if (rodSlot() < 0)
                {
                    if (!this.craftTried)
                    {
                        this.craftTried = true;
                        delegate(new CraftErrand(this.rocco, requester(), List.of(Items.FISHING_ROD), 1, true, true));
                        return Status.RUNNING;
                    }
                    say("fish_need_rod");
                    return Status.FAILED;
                }
                this.water = findWater();
                if (this.water == null)
                {
                    say("fish_no_water");
                    return Status.FAILED;
                }
                this.phase = Phase.CAST;
                return Status.RUNNING;
            }
            case CAST ->
            {
                if (jobFailed())
                {
                    say("fish_cant_reach");
                    return Status.FAILED;
                }
                int slot = rodSlot();
                if (slot < 0)
                {
                    return finish();
                }
                ItemStack rod = this.rocco.inventory().getItem(slot);
                BlockPos at = this.water;
                work(RoccoEntity.Job.custom(RoccoTask.INTERACTING, at, rod.copy(), level -> splash(level, at, true), null)
                        .reaching(5.0D));
                int lure = rod.getEnchantmentLevel(Enchantments.FISHING_SPEED);
                this.waitLeft = Math.max(40, 100 + this.rocco.getRandom().nextInt(500) - lure * 100);
                this.phase = Phase.WAIT;
                return Status.RUNNING;
            }
            case WAIT ->
            {
                if (jobFailed())
                {
                    say("fish_cant_reach");
                    return Status.FAILED;
                }
                int slot = rodSlot();
                if (slot < 0)
                {
                    return finish();
                }
                this.rocco.showInHand(this.rocco.inventory().getItem(slot));
                this.rocco.getLookControl().setLookAt(Vec3.atCenterOf(this.water));
                // Alguna burbujita mientras espera.
                if (this.waitLeft % 25 == 0)
                {
                    this.level.sendParticles(ParticleTypes.BUBBLE, this.water.getX() + 0.5D, this.water.getY() + 0.9D,
                            this.water.getZ() + 0.5D, 2, 0.2D, 0.0D, 0.2D, 0.01D);
                }
                if (--this.waitLeft > 0)
                {
                    return Status.RUNNING;
                }
                BlockPos at = this.water;
                work(RoccoEntity.Job.custom(RoccoTask.INTERACTING, at, this.rocco.inventory().getItem(slot).copy(),
                        level -> reel(level, at), null).reaching(5.0D));
                this.phase = Phase.REEL;
                return Status.RUNNING;
            }
            case REEL ->
            {
                if (this.caught >= this.wanted)
                {
                    return finish();
                }
                this.phase = Phase.CAST;
                return Status.RUNNING;
            }
            default ->
            {
                return Status.DONE;
            }
        }
    }

    @Override
    protected boolean childDone(Errand child, boolean success)
    {
        return true;
    }

    private Status finish()
    {
        this.phase = Phase.DONE;
        if (this.haul.isEmpty())
        {
            say("fish_nothing");
            return Status.FAILED;
        }
        say("fish_done", "items", Names.stacks(this.haul));
        return Status.DONE;
    }

    // Saca lo que pico, como la caña del jugador: la tabla de pesca del juego, con la suerte de la caña.
    private void reel(ServerLevel level, BlockPos at)
    {
        int slot = rodSlot();
        if (slot < 0)
        {
            return;
        }
        ItemStack rod = this.rocco.inventory().getItem(slot);
        int luck = rod.getEnchantmentLevel(Enchantments.FISHING_LUCK);

        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(at))
                .withParameter(LootContextParams.TOOL, rod)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, this.rocco)
                .withLuck(luck)
                .create(LootContextParamSets.FISHING);
        LootTable table = level.getServer().getLootData().getLootTable(BuiltInLootTables.FISHING);
        table.getRandomItems(params, stack ->
        {
            this.haul.add(stack.copy());
            this.rocco.keep(stack);
        });

        this.caught++;
        splash(level, at, false);
        level.addFreshEntity(new ExperienceOrb(level, this.rocco.getX(), this.rocco.getY() + 0.5D, this.rocco.getZ(),
                1 + this.rocco.getRandom().nextInt(6)));
        Tools.wear(this.rocco, slot);
    }

    private void splash(ServerLevel level, BlockPos at, boolean cast)
    {
        level.playSound(null, at, cast ? SoundEvents.FISHING_BOBBER_THROW : SoundEvents.FISHING_BOBBER_RETRIEVE,
                SoundSource.NEUTRAL, 0.6F, 0.9F + level.random.nextFloat() * 0.2F);
        level.playSound(null, at, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.3F, 1.0F);
        level.sendParticles(ParticleTypes.SPLASH, at.getX() + 0.5D, at.getY() + 1.0D, at.getZ() + 0.5D,
                12, 0.2D, 0.0D, 0.2D, 0.1D);
    }

    private int rodSlot()
    {
        for (int slot = 0; slot < this.rocco.inventory().getContainerSize(); slot++)
        {
            if (this.rocco.inventory().getItem(slot).getItem() instanceof FishingRodItem)
            {
                return slot;
            }
        }
        return -1;
    }

    // Agua con aire arriba (la superficie), lo mas cerca posible.
    @Nullable
    private BlockPos findWater()
    {
        BlockPos center = this.rocco.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RADIUS, -4, -RADIUS), center.offset(RADIUS, 3, RADIUS)))
        {
            if (!this.level.getFluidState(pos).is(FluidTags.WATER) || !this.level.getBlockState(pos.above()).isAir())
            {
                continue;
            }
            double distance = pos.distSqr(center);
            if (distance < bestDistance)
            {
                best = pos.immutable();
                bestDistance = distance;
            }
        }
        return best;
    }

    @Override
    public String describe()
    {
        return "pescando";
    }
}

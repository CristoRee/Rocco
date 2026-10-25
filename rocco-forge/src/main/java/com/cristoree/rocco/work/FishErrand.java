package com.cristoree.rocco.work;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
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
// La boya y el hilo los dibuja el cliente (RoccoFishingLine) a partir de castBobber/setBobberBite.
public final class FishErrand extends Errand
{
    private enum Phase { FIND, CAST, WAIT, REEL, DONE }

    private static final int RADIUS = 12;
    // Ticks antes de recoger en que el pez muerde y hunde la boya, y cuanto antes se ve venir.
    private static final int BITE_AT = 15;
    private static final int TRAIL_TICKS = 40;
    // Si lo alejaron del agua (un teleport, un empujon), vuelve a tirar.
    private static final double MAX_LINE = 6.0D;

    private final int wanted;
    private Phase phase = Phase.FIND;
    @Nullable
    private BlockPos water;
    private int waitLeft;
    private int caught;
    // De donde viene nadando el pez en esta tirada.
    private float fishAngle;
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
                work(RoccoEntity.Job.custom(RoccoTask.INTERACTING, at, rod.copy(), level -> cast(level, at), null)
                        .reaching(5.0D));
                int lure = rod.getEnchantmentLevel(Enchantments.FISHING_SPEED);
                this.waitLeft = Math.max(BITE_AT + TRAIL_TICKS, 100 + this.rocco.getRandom().nextInt(500) - lure * 100);
                this.fishAngle = this.rocco.getRandom().nextFloat() * Mth.TWO_PI;
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
                // Se seco, se congelo o lo alejaron: recoge y vuelve a buscar donde tirar.
                if (!this.level.getFluidState(this.water).is(FluidTags.WATER))
                {
                    this.rocco.reelInBobber();
                    this.phase = Phase.FIND;
                    return Status.RUNNING;
                }
                if (this.rocco.distanceToSqr(Vec3.atCenterOf(this.water)) > MAX_LINE * MAX_LINE)
                {
                    this.rocco.reelInBobber();
                    this.phase = Phase.CAST;
                    return Status.RUNNING;
                }
                this.rocco.showInHand(this.rocco.inventory().getItem(slot));
                // De frente al agua, asi la caña y el hilo apuntan a la boya.
                this.rocco.lookAtBlock(this.water);
                this.rocco.getLookControl().setLookAt(Vec3.atCenterOf(this.water));
                tickNibble();
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

    @Override
    public void cancelled()
    {
        super.cancelled();
        this.rocco.reelInBobber();
    }

    private Status finish()
    {
        this.phase = Phase.DONE;
        this.rocco.reelInBobber();
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
        List<ItemStack> catchNow = new ArrayList<>();
        table.getRandomItems(params, stack ->
        {
            catchNow.add(stack.copy());
            this.haul.add(stack.copy());
            this.rocco.keep(stack);
        });

        this.caught++;
        this.rocco.reelInBobber();
        level.playSound(null, this.rocco.getX(), this.rocco.getY(), this.rocco.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE,
                SoundSource.NEUTRAL, 1.0F, 0.4F / (level.random.nextFloat() * 0.4F + 0.8F));
        splash(level, at, 0.4F);
        level.addFreshEntity(new ExperienceOrb(level, this.rocco.getX(), this.rocco.getY() + 0.5D, this.rocco.getZ(),
                1 + this.rocco.getRandom().nextInt(6)));
        Tools.wear(this.rocco, slot);

        // Lo que salio se ve en la mano el resto del tiron (es solo para mostrar: ya esta en la tapa).
        if (!catchNow.isEmpty())
        {
            this.rocco.showInHand(catchNow.get(0));
        }
    }

    // La caña tira la boya: vuela sola hasta el agua y recien ahi chapotea.
    private void cast(ServerLevel level, BlockPos at)
    {
        this.rocco.castBobber(at);
        level.playSound(null, this.rocco.getX(), this.rocco.getY(), this.rocco.getZ(), SoundEvents.FISHING_BOBBER_THROW,
                SoundSource.NEUTRAL, 0.5F, 0.4F / (level.random.nextFloat() * 0.4F + 0.8F));
        this.rocco.schedule(RoccoEntity.BOBBER_FLIGHT_TICKS, () ->
        {
            if (at.equals(this.rocco.getBobber()))
            {
                splash(level, at, 0.3F);
            }
        });
    }

    // Antes de picar se ve la estela del pez acercandose a la boya; al morder la hunde.
    private void tickNibble()
    {
        double surface = surfaceY(this.water);
        double x = this.water.getX() + 0.5D;
        double z = this.water.getZ() + 0.5D;

        if (this.waitLeft > BITE_AT && this.waitLeft <= BITE_AT + TRAIL_TICKS && this.waitLeft % 2 == 0)
        {
            double away = (this.waitLeft - BITE_AT) * 0.1D;
            this.level.sendParticles(ParticleTypes.FISHING, x + Mth.cos(this.fishAngle) * away, surface,
                    z + Mth.sin(this.fishAngle) * away, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        else if (this.waitLeft == BITE_AT)
        {
            this.rocco.setBobberBite(true);
            this.level.playSound(null, x, surface, z, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL,
                    0.25F, 1.0F + (this.level.random.nextFloat() - this.level.random.nextFloat()) * 0.4F);
            this.level.sendParticles(ParticleTypes.BUBBLE, x, surface, z, 8, 0.15D, 0.0D, 0.15D, 0.2D);
            this.level.sendParticles(ParticleTypes.FISHING, x, surface, z, 8, 0.15D, 0.0D, 0.15D, 0.2D);
        }
        else if (this.waitLeft > BITE_AT + TRAIL_TICKS && this.waitLeft % 25 == 0)
        {
            // Alguna burbujita mientras espera.
            this.level.sendParticles(ParticleTypes.BUBBLE, x, surface - 0.1D, z, 2, 0.2D, 0.0D, 0.2D, 0.01D);
        }
    }

    private void splash(ServerLevel level, BlockPos at, float volume)
    {
        double surface = surfaceY(at);
        level.playSound(null, at.getX() + 0.5D, surface, at.getZ() + 0.5D, SoundEvents.FISHING_BOBBER_SPLASH,
                SoundSource.NEUTRAL, volume, 1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.4F);
        level.sendParticles(ParticleTypes.SPLASH, at.getX() + 0.5D, surface, at.getZ() + 0.5D,
                12, 0.2D, 0.0D, 0.2D, 0.1D);
    }

    private double surfaceY(BlockPos at)
    {
        return at.getY() + this.level.getFluidState(at).getHeight(this.level, at);
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

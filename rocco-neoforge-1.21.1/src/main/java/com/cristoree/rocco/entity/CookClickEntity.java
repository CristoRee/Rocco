package com.cristoree.rocco.entity;

import com.cristoree.rocco.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Una caja invisible sobre la parrilla (o el pote de las tortas) para hacerle clic derecho: no se
// ve, no choca y no se guarda. Vive mientras Rocco cocina.
public final class CookClickEntity extends Entity
{
    private static final EntityDataAccessor<Boolean> DATA_SMALL =
            SynchedEntityData.defineId(CookClickEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDimensions SMALL = EntityDimensions.fixed(0.35F, 0.3F);

    private int roccoId = -1;

    public CookClickEntity(EntityType<? extends CookClickEntity> type, Level level)
    {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    // La caja apoyada en ese punto (la base, como dice el modelo).
    static CookClickEntity spawn(RoccoEntity rocco, Vec3 base, boolean small)
    {
        CookClickEntity click = new CookClickEntity(ModEntities.CLICK_COCINA.get(), rocco.level());
        click.roccoId = rocco.getId();
        click.entityData.set(DATA_SMALL, small);
        click.refreshDimensions();
        click.moveTo(base.x, base.y, base.z, rocco.getYRot(), 0.0F);
        rocco.level().addFreshEntity(click);
        return click;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder)
    {
        builder.define(DATA_SMALL, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key)
    {
        super.onSyncedDataUpdated(key);
        if (DATA_SMALL.equals(key))
        {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose)
    {
        return this.entityData.get(DATA_SMALL) ? SMALL : super.getDimensions(pose);
    }

    @Override
    public void tick()
    {
        super.tick();
        // Rocco ya no cocina (o no existe): no queda una caja suelta.
        if (!level().isClientSide && (this.tickCount > 5 && rocco() == null))
        {
            discard();
        }
    }

    @Nullable
    private RoccoEntity rocco()
    {
        return level().getEntity(this.roccoId) instanceof RoccoEntity rocco && rocco.isAlive() && rocco.cooking().active()
                ? rocco : null;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand)
    {
        if (hand != InteractionHand.MAIN_HAND)
        {
            return InteractionResult.PASS;
        }
        if (level().isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        RoccoEntity rocco = rocco();
        if (rocco != null && player instanceof ServerPlayer serverPlayer)
        {
            rocco.cooking().clicked(serverPlayer);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isPickable()
    {
        return !isRemoved();
    }

    @Override
    public boolean isAttackable()
    {
        return false;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker)
    {
        return true;
    }

    @Override
    public boolean canBeHitByProjectile()
    {
        return false;
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    public boolean canBeCollidedWith()
    {
        return false;
    }

    @Override
    public boolean shouldBeSaved()
    {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
    }

}

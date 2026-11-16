package com.cristoree.rocco.truco;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

// La mesa del truco: aparece con Rocco, se rompe sola cuando termina la partida y no se guarda. Lo que
// esta arriba (las cartas jugadas, el mazo, la muestra) viaja en DATA_VIEW y lo dibuja su renderer.
public final class TrucoTableEntity extends Entity implements GeoEntity
{
    private static final EntityDataAccessor<Integer> DATA_SIZE =
            SynchedEntityData.defineId(TrucoTableEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_LEAVING =
            SynchedEntityData.defineId(TrucoTableEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<CompoundTag> DATA_VIEW =
            SynchedEntityData.defineId(TrucoTableEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private static final RawAnimation APPEAR = RawAnimation.begin().thenPlayAndHold("animation.truco_mesa.aparecer");
    private static final RawAnimation LEAVE = RawAnimation.begin().thenPlayAndHold("animation.truco_mesa.desaparecer");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    // Del lado del servidor: la partida que la usa (si no hay, la mesa se va sola).
    private boolean claimed;

    public TrucoTableEntity(EntityType<? extends TrucoTableEntity> type, Level level)
    {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder)
    {
        builder.define(DATA_SIZE, 0);
        builder.define(DATA_LEAVING, false);
        builder.define(DATA_VIEW, new CompoundTag());
    }

    public TrucoLayout.Size size()
    {
        return TrucoLayout.Size.byId(this.entityData.get(DATA_SIZE));
    }

    void setup(TrucoLayout.Size size, float yaw)
    {
        this.claimed = true;
        this.entityData.set(DATA_SIZE, size.ordinal());
        setYRot(yaw);
        this.yRotO = yaw;
        refreshDimensions();
    }

    public CompoundTag view()
    {
        return this.entityData.get(DATA_VIEW);
    }

    void setView(CompoundTag view)
    {
        this.entityData.set(DATA_VIEW, view);
    }

    public boolean leaving()
    {
        return this.entityData.get(DATA_LEAVING);
    }

    void leave()
    {
        this.entityData.set(DATA_LEAVING, true);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key)
    {
        super.onSyncedDataUpdated(key);
        if (DATA_SIZE.equals(key))
        {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose)
    {
        TrucoLayout.Size size = size();
        return EntityDimensions.fixed((float) Math.max(size.width, size.depth) / 16.0F, 0.85F);
    }

    // La tapa es un rectangulo y la mesa siempre queda mirando a un punto cardinal: la caja de choque
    // es justo la tapa.
    @Override
    protected AABB makeBoundingBox()
    {
        TrucoLayout.Size size = size();
        double halfWidth = size.width / 32.0D;
        double halfDepth = size.depth / 32.0D;
        float yaw = Mth.wrapDegrees(getYRot());
        boolean alongZ = Math.abs(yaw) < 45.0F || Math.abs(yaw) > 135.0F;
        double hx = alongZ ? halfWidth : halfDepth;
        double hz = alongZ ? halfDepth : halfWidth;
        return new AABB(getX() - hx, getY(), getZ() - hz, getX() + hx, getY() + 0.8D, getZ() + hz);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!level().isClientSide && (!this.claimed || this.tickCount > 20 * 60 * 120))
        {
            discard();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        return false;
    }

    @Override
    public boolean isPickable()
    {
        return false;
    }

    @Override
    public boolean canBeCollidedWith()
    {
        return !leaving();
    }

    @Override
    public boolean isPushable()
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


    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(leaving() ? LEAVE : APPEAR)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.cache;
    }
}

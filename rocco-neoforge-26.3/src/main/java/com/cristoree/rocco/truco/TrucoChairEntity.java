package com.cristoree.rocco.truco;

import com.cristoree.rocco.entity.RoccoEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;

// Una silla de la mesa del truco: la de los jugadores (asiento a 7 px) o la alta de Rocco (con
// escalon y almohadon, 9 px). Mira hacia -z, como Rocco: su frente apunta a la mesa.
// Se guarda con el mundo a proposito: Minecraft guarda a los pasajeros adentro de su asiento, asi que
// una silla sin guardar se llevaba a Rocco. Al volver a cargar no tiene partida: lo baja y se va.
public final class TrucoChairEntity extends Entity implements GeoEntity
{
    private static final EntityDataAccessor<Boolean> DATA_ROCCO =
            SynchedEntityData.defineId(TrucoChairEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_LEAVING =
            SynchedEntityData.defineId(TrucoChairEntity.class, EntityDataSerializers.BOOLEAN);

    // El jugador sentado apoya los muslos 0,625 por encima de su posicion y el juego le resta 0,35.
    static final double PLAYER_SEAT = 7.0D / 16.0D - 0.275D;
    static final double ROCCO_SEAT = 9.0D / 16.0D;

    private static final RawAnimation APPEAR = RawAnimation.begin().thenPlayAndHold("animation.truco_silla.aparecer");
    private static final RawAnimation LEAVE = RawAnimation.begin().thenPlayAndHold("animation.truco_silla.desaparecer");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean claimed;

    public TrucoChairEntity(EntityType<? extends TrucoChairEntity> type, Level level)
    {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder)
    {
        builder.define(DATA_ROCCO, false);
        builder.define(DATA_LEAVING, false);
    }

    void setup(boolean rocco, float yaw)
    {
        this.claimed = true;
        this.entityData.set(DATA_ROCCO, rocco);
        setYRot(yaw);
        this.yRotO = yaw;
    }

    public boolean forRocco()
    {
        return this.entityData.get(DATA_ROCCO);
    }

    public boolean leaving()
    {
        return this.entityData.get(DATA_LEAVING);
    }

    void leave()
    {
        this.entityData.set(DATA_LEAVING, true);
    }

    // Desde 1.20.2 el pasajero se sienta en este punto mas su propio ajuste: el del jugador paso de
    // -0.35 a -0.6, asi que se le suman 0.25 para que quede igual que antes. El de Rocco es 0.
    @Override
    protected net.minecraft.world.phys.Vec3 getPassengerAttachmentPoint(Entity passenger, net.minecraft.world.entity.EntityDimensions dimensions, float scale)
    {
        return new net.minecraft.world.phys.Vec3(0.0D, forRocco() ? ROCCO_SEAT : PLAYER_SEAT + PLAYER_OFFSET_CHANGE, 0.0D);
    }

    private static final double PLAYER_OFFSET_CHANGE = 0.6D - 0.35D;

    // Rocco queda mirando la mesa, quieto; los jugadores miran donde quieran.
    @Override
    protected void positionRider(Entity passenger, MoveFunction move)
    {
        super.positionRider(passenger, move);
        if (passenger instanceof RoccoEntity rocco)
        {
            rocco.setYRot(getYRot());
            rocco.yBodyRot = getYRot();
            rocco.yBodyRotO = getYRot();
            rocco.yHeadRot = getYRot();
            rocco.yHeadRotO = getYRot();
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger)
    {
        return getPassengers().isEmpty() && !leaving();
    }

    @Override
    public boolean shouldRiderSit()
    {
        return true;
    }

    // Al pararse queda detras de la silla (no arriba de la mesa).
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger)
    {
        Vec3 back = Vec3.directionFromRotation(0.0F, getYRot()).scale(-0.7D);
        Vec3 behind = position().add(back);
        if (level().noCollision(passenger, passenger.getBoundingBox().move(behind.subtract(passenger.position()))))
        {
            return behind;
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!level().isClientSide() && (!this.claimed || this.tickCount > 20 * 60 * 120))
        {
            ejectPassengers();
            discard();
        }
    }

    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount)
    {
        return false;
    }

    @Override
    public boolean isPickable()
    {
        return false;
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input)
    {
        CompoundTag tag = com.cristoree.rocco.entity.Nbt.of(input);
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output)
    {
    }


    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new AnimationController<>("main", 0, state -> state.setAndContinue(leaving() ? LEAVE : APPEAR)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.cache;
    }
}

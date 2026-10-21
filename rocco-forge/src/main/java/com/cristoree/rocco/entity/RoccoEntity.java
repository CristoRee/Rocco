package com.cristoree.rocco.entity;

import com.cristoree.rocco.ai.RoccoBrain;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.config.RoccoConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class RoccoEntity extends PathfinderMob implements GeoEntity
{
    // animation.rocco.awaken dura 3,3 s
    public static final int AWAKEN_TICKS = 66;

    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(RoccoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_EXPRESSION =
            SynchedEntityData.defineId(RoccoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_TALKING =
            SynchedEntityData.defineId(RoccoEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation PLANT_IDLE = RawAnimation.begin().thenLoop("animation.rocco.plant_idle");
    private static final RawAnimation AWAKEN = RawAnimation.begin().thenPlayAndHold("animation.rocco.awaken");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.rocco.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.rocco.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.rocco.run");
    private static final RawAnimation WAVE = RawAnimation.begin().thenPlay("animation.rocco.wave");
    private static final RawAnimation TALK = RawAnimation.begin().thenLoop("animation.rocco.talk");
    private static final RawAnimation BLINK = RawAnimation.begin().thenLoop("animation.rocco.blink");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private RoccoBrain brain;
    private int awakenTicks;
    private int talkTicks;

    public RoccoEntity(EntityType<? extends RoccoEntity> type, Level level)
    {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new RoccoFollowGoal(this));

        // Sin el filtro de estado, la planta se pondria a pasear.
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.6D)
        {
            @Override
            public boolean canUse()
            {
                return getRoccoState() == RoccoState.ACTIVE && super.canUse();
            }
        });
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_STATE, RoccoState.PLANT.ordinal());
        this.entityData.define(DATA_EXPRESSION, RoccoExpression.HAPPY.ordinal());
        this.entityData.define(DATA_TALKING, false);
    }

    public RoccoState getRoccoState()
    {
        return RoccoState.byId(this.entityData.get(DATA_STATE));
    }

    public void setRoccoState(RoccoState state)
    {
        this.entityData.set(DATA_STATE, state.ordinal());
    }

    public RoccoExpression getExpression()
    {
        return RoccoExpression.byId(this.entityData.get(DATA_EXPRESSION));
    }

    public void setExpression(RoccoExpression expression)
    {
        this.entityData.set(DATA_EXPRESSION, expression.ordinal());
    }

    public boolean isTalking()
    {
        return this.entityData.get(DATA_TALKING);
    }

    public void setTalking(boolean talking)
    {
        this.entityData.set(DATA_TALKING, talking);
    }

    public boolean isPlantVisible()
    {
        return getRoccoState().isPlantVisible();
    }

    public void talkFor(int ticks)
    {
        this.talkTicks = ticks;
        setTalking(true);
    }

    public void beginAwakening()
    {
        setRoccoState(RoccoState.AWAKENING);
        this.awakenTicks = 0;
        playSound(SoundEvents.GRASS_BREAK, 0.8F, 1.2F);
    }

    // Solo existe del lado del servidor: es donde se habla con Gemini.
    public RoccoBrain brain()
    {
        if (this.brain == null)
        {
            this.brain = new RoccoBrain(this);
        }
        return this.brain;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        if (hand != InteractionHand.MAIN_HAND)
        {
            return InteractionResult.PASS;
        }

        if (level().isClientSide)
        {
            return getRoccoState() == RoccoState.AWAKENING ? InteractionResult.PASS : InteractionResult.SUCCESS;
        }

        switch (getRoccoState())
        {
            case PLANT ->
            {
                beginAwakening();
                return InteractionResult.CONSUME;
            }
            case AWAKENING -> {
                return InteractionResult.PASS;
            }
            case ACTIVE ->
            {
                triggerAnim("main", "wave");
                setExpression(RoccoExpression.HAPPY);
                talkFor(50);
                lookAtPlayer(player);
                return InteractionResult.CONSUME;
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public void tick()
    {
        super.tick();

        if (level().isClientSide)
        {
            return;
        }

        if (getRoccoState() == RoccoState.AWAKENING)
        {
            this.awakenTicks++;
            if (this.awakenTicks >= AWAKEN_TICKS)
            {
                setRoccoState(RoccoState.ACTIVE);
                setExpression(RoccoExpression.HAPPY);
                talkFor(80);

                Player nearest = level().getNearestPlayer(this, 16.0D);
                if (nearest != null)
                {
                    lookAtPlayer(nearest);
                    greet(nearest);
                }
            }
        }

        if (this.talkTicks > 0)
        {
            this.talkTicks--;
            if (this.talkTicks == 0)
            {
                setTalking(false);
            }
        }

        // Un solo lugar gira la cabeza, siempre en el servidor: dos sistemas peleandose la trabarian.
        // Mientras camina manda la navegacion, asi que aca solo mira cuando esta quieto.
        if (getRoccoState() == RoccoState.ACTIVE
                && RoccoCommonConfig.LOOK_AT_PLAYER.get()
                && getNavigation().isDone())
        {
            Player nearest = level().getNearestPlayer(this, RoccoCommonConfig.LOOK_RADIUS.get());
            if (nearest != null)
            {
                lookAtPlayer(nearest);
            }
        }
    }

    private void lookAtPlayer(Player player)
    {
        lookAt(player, 30.0F, 30.0F);
        this.yBodyRot = this.getYRot();
    }

    private void greet(Player player)
    {
        if (player instanceof ServerPlayer serverPlayer && RoccoConfig.chatReady())
        {
            brain().ask(serverPlayer,
                    "(Acabas de despertar de la tierra y ves a este jugador por primera vez. Saludalo en una frase.)");
        }
    }

    // En planta no se lo puede empujar ni mover.
    @Override
    protected boolean isImmobile()
    {
        return isPlantVisible() || super.isImmobile();
    }

    @Override
    public boolean isPushable()
    {
        return !isPlantVisible() && super.isPushable();
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        if (isPlantVisible() && !source.isCreativePlayer())
        {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean removeWhenFarAway(double distance)
    {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putInt("RoccoState", getRoccoState().ordinal());
        tag.putInt("RoccoExpression", getExpression().ordinal());
        tag.putInt("AwakenTicks", this.awakenTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        setRoccoState(RoccoState.byId(tag.getInt("RoccoState")));
        setExpression(RoccoExpression.byId(tag.getInt("RoccoExpression")));
        this.awakenTicks = tag.getInt("AwakenTicks");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new AnimationController<>(this, "main", 3, this::mainAnimation)
                .triggerableAnim("wave", WAVE));

        controllers.add(new AnimationController<>(this, "mouth", 0, state ->
                isTalking() ? state.setAndContinue(TALK) : PlayState.STOP));

        controllers.add(new AnimationController<>(this, "blink", 0, state ->
                isPlantVisible() ? PlayState.STOP : state.setAndContinue(BLINK)));
    }

    private PlayState mainAnimation(AnimationState<RoccoEntity> state)
    {
        return switch (getRoccoState())
        {
            case PLANT -> state.setAndContinue(PLANT_IDLE);
            case AWAKENING -> state.setAndContinue(AWAKEN);
            case ACTIVE -> state.setAndContinue(activeAnimation(state));
        };
    }

    private RawAnimation activeAnimation(AnimationState<RoccoEntity> state)
    {
        if (!state.isMoving())
        {
            return IDLE;
        }
        // RUN queda para cuando Rocco tenga una razon para apurarse. Hoy nada lo hace esprintar,
        // y un umbral sobre walkAnimation.speed() lo mostraba corriendo al caminar normal.
        return isSprinting() ? RUN : WALK;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.cache;
    }
}

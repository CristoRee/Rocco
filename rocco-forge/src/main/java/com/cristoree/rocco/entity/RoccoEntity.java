package com.cristoree.rocco.entity;

import com.cristoree.rocco.ai.RoccoBrain;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.config.RoccoConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class RoccoEntity extends PathfinderMob implements GeoEntity, MenuProvider
{
    // animation.rocco.awaken dura 3,3 s
    public static final int AWAKEN_TICKS = 66;
    // animation.rocco.lid_close dura 1,3 s; a los 23 ticks termina de sacudir la cabeza
    public static final int LID_CLOSE_TICKS = 26;
    public static final int LID_FACE_RESTORE_TICK = 23;

    public static final int INVENTORY_SIZE = 27;

    private static final int LID_CLOSED = 0;
    private static final int LID_OPENED = 1;
    private static final int LID_CLOSING = 2;

    private static final double WORK_REACH = 2.2D;
    private static final int WORK_TIMEOUT_TICKS = 160;

    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(RoccoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_EXPRESSION =
            SynchedEntityData.defineId(RoccoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_TALKING =
            SynchedEntityData.defineId(RoccoEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_LID =
            SynchedEntityData.defineId(RoccoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> DATA_WORK_TARGET =
            SynchedEntityData.defineId(RoccoEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> DATA_WORKING =
            SynchedEntityData.defineId(RoccoEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation PLANT_IDLE = RawAnimation.begin().thenLoop("animation.rocco.plant_idle");
    private static final RawAnimation AWAKEN = RawAnimation.begin().thenPlayAndHold("animation.rocco.awaken");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.rocco.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.rocco.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.rocco.run");
    private static final RawAnimation TALK = RawAnimation.begin().thenLoop("animation.rocco.talk");
    private static final RawAnimation BLINK = RawAnimation.begin().thenLoop("animation.rocco.blink");
    private static final RawAnimation LID_OPEN_LOOP = RawAnimation.begin()
            .thenPlay("animation.rocco.lid_open")
            .thenLoop("animation.rocco.lid_dizzy");
    private static final RawAnimation LID_CLOSE = RawAnimation.begin().thenPlay("animation.rocco.lid_close");

    // Las de una sola vez se disparan desde el servidor para que se puedan repetir.
    private static final RawAnimation WAVE = RawAnimation.begin().thenPlay("animation.rocco.wave");
    private static final RawAnimation PLANT = RawAnimation.begin().thenPlay("animation.rocco.plant");
    private static final RawAnimation HARVEST = RawAnimation.begin().thenPlay("animation.rocco.harvest");
    private static final RawAnimation INTERACT = RawAnimation.begin().thenPlay("animation.rocco.interact");
    private static final RawAnimation EAT = RawAnimation.begin().thenPlay("animation.rocco.eat");
    private static final RawAnimation SWALLOW = RawAnimation.begin().thenPlay("animation.rocco.swallowterror");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final RoccoContainer inventory = new RoccoContainer();
    private final Deque<Job> jobs = new ArrayDeque<>();

    private RoccoBrain brain;
    private int awakenTicks;
    private int talkTicks;
    private int lidCloseTicks;
    private int viewers;

    // Se puede apagar por orden del jugador ("quedate ahi"), aparte del interruptor de la config.
    private boolean following = true;
    private boolean configApplied;

    private RoccoTask task = RoccoTask.NONE;
    private int taskTicks;
    private Job currentJob;
    private int approachTicks;
    private RoccoExpression faceBeforeLid = RoccoExpression.HAPPY;
    private RoccoExpression faceBeforeSwallow = RoccoExpression.HAPPY;

    public RoccoEntity(EntityType<? extends RoccoEntity> type, Level level)
    {
        super(type, level);
        setPersistenceRequired();

        // Lo que tiene en la mano es solo lo que esta mostrando durante un trabajo; la copia
        // de verdad vive en su inventario. Sin esto, morirse a mitad de plantar lo duplicaria.
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        setDropChance(EquipmentSlot.OFFHAND, 0.0F);
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
                return getRoccoState() == RoccoState.ACTIVE && isIdleForWandering() && super.canUse();
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
        this.entityData.define(DATA_LID, LID_CLOSED);
        this.entityData.define(DATA_WORK_TARGET, BlockPos.ZERO);
        this.entityData.define(DATA_WORKING, false);
    }

    // ---------- estado sincronizado ----------

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

    public boolean isLidOpen()
    {
        return this.entityData.get(DATA_LID) == LID_OPENED;
    }

    public boolean isWorking()
    {
        return this.entityData.get(DATA_WORKING);
    }

    // El cliente lo usa para girarle la cabeza hacia donde esta trabajando.
    @Nullable
    public BlockPos getWorkTarget()
    {
        BlockPos pos = this.entityData.get(DATA_WORK_TARGET);
        return pos.equals(BlockPos.ZERO) ? null : pos;
    }

    public void talkFor(int ticks)
    {
        // Regla 4 de la spec: con la boca grande abierta no puede hablar, o se encimarian
        // mouth_talk y la boca de la animacion.
        if (this.task == RoccoTask.EATING || this.task == RoccoTask.SWALLOWING)
        {
            return;
        }

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

    public Container inventory()
    {
        return this.inventory;
    }

    public boolean isFollowing()
    {
        return this.following;
    }

    public void setFollowing(boolean following)
    {
        this.following = following;
    }

    private boolean isIdleForWandering()
    {
        return !isWorking() && this.jobs.isEmpty() && this.entityData.get(DATA_LID) == LID_CLOSED;
    }

    // ---------- trabajos ----------

    // entityId -1 cuando el trabajo es sobre un bloque y no sobre un bicho.
    public record Job(RoccoTask task, BlockPos target, ItemStack shown, int entityId)
    {
        public static Job onBlock(RoccoTask task, BlockPos target, ItemStack shown)
        {
            return new Job(task, target, shown, -1);
        }

        public static Job onEntity(RoccoTask task, Entity entity)
        {
            return new Job(task, entity.blockPosition(), ItemStack.EMPTY, entity.getId());
        }
    }

    public int queueSize()
    {
        return this.jobs.size();
    }

    public void clearJobs()
    {
        this.jobs.clear();
    }

    public void queueJob(Job job)
    {
        if (this.jobs.size() < 32)
        {
            this.jobs.add(job);
        }
    }

    private void startJob(Job job)
    {
        this.currentJob = job;
        this.task = job.task();
        this.taskTicks = 0;
        this.approachTicks = 0;

        this.entityData.set(DATA_WORKING, true);
        this.entityData.set(DATA_WORK_TARGET, job.target());

        // La spec pide que el cultivo se vea en la mano desde el arranque de harvest, no solo
        // cuando lo arranca, asi que para cosechar se averigua antes que va a levantar.
        ItemStack shown = job.task() == RoccoTask.HARVESTING
                ? previewOf(job.target())
                : job.shown().copy();

        setItemSlot(EquipmentSlot.MAINHAND, shown);
        getNavigation().stop();

        switch (job.task())
        {
            case PLANTING -> triggerAnim("main", "plant");
            case HARVESTING -> triggerAnim("main", "harvest");
            case INTERACTING -> triggerAnim("main", "interact");
            case EATING -> triggerAnim("main", "eat");
            case SWALLOWING ->
            {
                // La spec pide psycho solo mientras traga, y recuperar la cara al terminar.
                this.faceBeforeSwallow = getExpression();
                setExpression(RoccoExpression.PSYCHO);
                triggerAnim("main", "swallowterror");
            }
            default -> { }
        }

        // Con la boca llena no habla: asi mouth_talk queda escondido y no se encima con la
        // boca grande de la animacion.
        if (job.task() == RoccoTask.EATING || job.task() == RoccoTask.SWALLOWING)
        {
            setTalking(false);
            this.talkTicks = 0;
        }
    }

    private ItemStack previewOf(BlockPos pos)
    {
        BlockState state = level().getBlockState(pos);
        return state.getBlock().getCloneItemStack(level(), pos, state);
    }

    private void finishJob()
    {
        if (this.task == RoccoTask.SWALLOWING)
        {
            setExpression(this.faceBeforeSwallow);
        }

        this.task = RoccoTask.NONE;
        this.currentJob = null;
        this.taskTicks = 0;
        this.entityData.set(DATA_WORKING, false);
        this.entityData.set(DATA_WORK_TARGET, BlockPos.ZERO);
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    private void applyJobEffect()
    {
        if (this.currentJob == null || !(level() instanceof ServerLevel serverLevel))
        {
            return;
        }

        BlockPos target = this.currentJob.target();

        switch (this.currentJob.task())
        {
            case PLANTING ->
            {
                Block crop = RoccoWork.cropFor(this.currentJob.shown());
                if (crop != null && RoccoWork.plant(serverLevel, target, crop))
                {
                    // La semilla recien se descuenta cuando quedo plantada de verdad.
                    consumeFromInventory(this.currentJob.shown());
                }
            }
            case HARVESTING ->
            {
                List<ItemStack> drops = RoccoWork.harvest(serverLevel, target, this);
                for (ItemStack drop : drops)
                {
                    ItemStack leftover = this.inventory.addItem(drop);
                    if (!leftover.isEmpty())
                    {
                        spawnAtLocation(leftover);
                    }
                }
                // Sostiene lo cosechado hasta que termina la animacion.
                if (!drops.isEmpty())
                {
                    setItemSlot(EquipmentSlot.MAINHAND, drops.get(0).copy());
                }
            }
            case INTERACTING -> RoccoWork.toggleOpenable(serverLevel, target);
            // Comer puede ser del propio inventario o algo tirado en el piso.
            case EATING ->
            {
                if (this.currentJob.entityId() >= 0)
                {
                    swallowTarget(serverLevel);
                }
                else
                {
                    eatFromInventory();
                }
            }
            case SWALLOWING -> swallowTarget(serverLevel);
            default -> { }
        }
    }

    private void eatFromInventory()
    {
        ItemStack food = this.currentJob.shown();
        if (food.isEmpty())
        {
            return;
        }

        consumeFromInventory(food);
        playSound(SoundEvents.GENERIC_EAT, 0.9F, 1.0F);

        // Comer le hace bien, aunque sea inmortal.
        heal(2.0F);
    }

    private void swallowTarget(ServerLevel level)
    {
        Entity victim = level.getEntity(this.currentJob.entityId());
        if (victim == null || !victim.isAlive())
        {
            return;
        }

        playSound(SoundEvents.GENERIC_EAT, 1.0F, 0.6F);

        if (victim instanceof ItemEntity item)
        {
            ItemStack leftover = this.inventory.addItem(item.getItem().copy());
            if (leftover.isEmpty())
            {
                item.discard();
            }
            else
            {
                item.setItem(leftover);
            }
            return;
        }

        if (victim instanceof LivingEntity living)
        {
            living.hurt(damageSources().mobAttack(this), living.getMaxHealth() * 4.0F);
        }
    }

    private void consumeFromInventory(ItemStack like)
    {
        for (int slot = 0; slot < this.inventory.getContainerSize(); slot++)
        {
            ItemStack stack = this.inventory.getItem(slot);
            if (!stack.isEmpty() && ItemStack.isSameItem(stack, like))
            {
                stack.shrink(1);
                if (stack.isEmpty())
                {
                    this.inventory.setItem(slot, ItemStack.EMPTY);
                }
                this.inventory.setChanged();
                return;
            }
        }
    }

    public int countInInventory(ItemStack like)
    {
        int total = 0;
        for (int slot = 0; slot < this.inventory.getContainerSize(); slot++)
        {
            ItemStack stack = this.inventory.getItem(slot);
            if (!stack.isEmpty() && ItemStack.isSameItem(stack, like))
            {
                total += stack.getCount();
            }
        }
        return total;
    }

    // Todo lo plantable que tenga encima, para que la IA sepa con que puede trabajar.
    public List<ItemStack> plantableStacks()
    {
        List<ItemStack> out = new java.util.ArrayList<>();
        for (int slot = 0; slot < this.inventory.getContainerSize(); slot++)
        {
            ItemStack stack = this.inventory.getItem(slot);
            if (!stack.isEmpty() && RoccoWork.cropFor(stack) != null)
            {
                boolean already = out.stream().anyMatch(other -> ItemStack.isSameItem(other, stack));
                if (!already)
                {
                    out.add(stack.copy());
                }
            }
        }
        return out;
    }

    private void tickWork()
    {
        if (this.task != RoccoTask.NONE)
        {
            this.taskTicks++;

            if (this.taskTicks == this.task.impactTick())
            {
                applyJobEffect();
            }

            if (this.taskTicks >= this.task.totalTicks())
            {
                finishJob();
            }
            return;
        }

        Job next = this.jobs.peek();
        if (next == null)
        {
            return;
        }

        if (this.entityData.get(DATA_LID) != LID_CLOSED)
        {
            return;
        }

        // Primero se acerca; la animacion arranca cuando la mano llega al bloque.
        double distance = Math.sqrt(distanceToSqr(
                next.target().getX() + 0.5D, next.target().getY(), next.target().getZ() + 0.5D));

        if (distance <= WORK_REACH)
        {
            lookAtBlock(next.target());
            this.jobs.poll();
            startJob(next);
            return;
        }

        this.approachTicks++;
        if (this.approachTicks > WORK_TIMEOUT_TICKS)
        {
            // No pudo llegar: se descarta y sigue con el proximo.
            this.jobs.poll();
            this.approachTicks = 0;
            return;
        }

        if (this.approachTicks % 10 == 1)
        {
            getNavigation().moveTo(next.target().getX() + 0.5D, next.target().getY(),
                    next.target().getZ() + 0.5D, 1.0D);
        }
    }

    private void lookAtBlock(BlockPos pos)
    {
        double dx = pos.getX() + 0.5D - getX();
        double dz = pos.getZ() + 0.5D - getZ();
        float yaw = (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;

        setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    // ---------- tapa / cofre ----------

    private void openLid(ServerPlayer player)
    {
        if (this.entityData.get(DATA_LID) == LID_CLOSED)
        {
            this.faceBeforeLid = getExpression();
        }

        this.entityData.set(DATA_LID, LID_OPENED);
        this.lidCloseTicks = 0;
        setExpression(RoccoExpression.DIZZY);
        getNavigation().stop();
        setTalking(false);

        playSound(SoundEvents.CHEST_OPEN, 0.7F, 1.1F);
        player.openMenu(this);
    }

    private void beginLidClose()
    {
        this.entityData.set(DATA_LID, LID_CLOSING);
        this.lidCloseTicks = 0;
        playSound(SoundEvents.CHEST_CLOSE, 0.7F, 1.0F);
    }

    private void tickLid()
    {
        if (this.entityData.get(DATA_LID) != LID_CLOSING)
        {
            return;
        }

        this.lidCloseTicks++;

        if (this.lidCloseTicks == LID_FACE_RESTORE_TICK)
        {
            setExpression(this.faceBeforeLid);
        }

        if (this.lidCloseTicks >= LID_CLOSE_TICKS)
        {
            this.entityData.set(DATA_LID, LID_CLOSED);
            this.lidCloseTicks = 0;
        }
    }

    @Override
    public Component getDisplayName()
    {
        return Component.literal(RoccoCommonConfig.COMPANION_NAME.get());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player)
    {
        // threeRows es el cofre chico de 27 espacios.
        return ChestMenu.threeRows(containerId, playerInventory, this.inventory);
    }

    // ---------- interaccion ----------

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        if (hand != InteractionHand.MAIN_HAND)
        {
            return InteractionResult.PASS;
        }

        // El cliente decide con los mismos datos sincronizados que usa el servidor, asi no
        // mueve el brazo cuando en realidad no va a pasar nada.
        if (level().isClientSide)
        {
            return switch (getRoccoState())
            {
                case PLANT -> InteractionResult.SUCCESS;
                case AWAKENING -> InteractionResult.PASS;
                case ACTIVE -> isWorking() ? InteractionResult.PASS : InteractionResult.SUCCESS;
            };
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
                if (player instanceof ServerPlayer serverPlayer && !isWorking())
                {
                    openLid(serverPlayer);
                    return InteractionResult.CONSUME;
                }
                return InteractionResult.PASS;
            }
        }

        return InteractionResult.PASS;
    }

    // maxHealth, canOpenDoors y avoidWater estaban en la config sin hacer nada. Se aplican una
    // sola vez, cuando el mundo ya cargo la configuracion.
    private void applyConfigOnce()
    {
        if (this.configApplied)
        {
            return;
        }
        this.configApplied = true;

        AttributeInstance maxHealth = getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null)
        {
            double wanted = RoccoCommonConfig.MAX_HEALTH.get();
            if (Math.abs(maxHealth.getBaseValue() - wanted) > 0.001D)
            {
                maxHealth.setBaseValue(wanted);
                setHealth(getMaxHealth());
            }
        }

        if (getNavigation() instanceof GroundPathNavigation ground)
        {
            ground.setCanOpenDoors(RoccoCommonConfig.CAN_OPEN_DOORS.get());
        }

        setPathfindingMalus(BlockPathTypes.WATER, RoccoCommonConfig.AVOID_WATER.get() ? 8.0F : 0.0F);
    }

    @Override
    public void tick()
    {
        super.tick();

        if (level().isClientSide)
        {
            return;
        }

        applyConfigOnce();

        if (getRoccoState() == RoccoState.AWAKENING)
        {
            this.awakenTicks++;
            if (this.awakenTicks >= AWAKEN_TICKS)
            {
                setRoccoState(RoccoState.ACTIVE);
                setExpression(RoccoExpression.HAPPY);

                Player nearest = level().getNearestPlayer(this, 16.0D);
                if (nearest != null)
                {
                    // Recien nacido: se da vuelta hacia el jugador, lo saluda con la mano y
                    // despues le habla. El saludo no depende de que haya clave de IA.
                    lookAtPlayer(nearest);
                    triggerAnim("main", "wave");
                    talkFor(80);
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

        // Se cura despacio: siendo inmortal, sin esto quedaria en 1 de vida para siempre
        // despues de una paliza.
        if (this.tickCount % 100 == 0 && getHealth() < getMaxHealth())
        {
            heal(1.0F);
        }

        tickLid();

        if (getRoccoState() == RoccoState.ACTIVE)
        {
            tickWork();
        }

        // Un solo lugar gira la cabeza, siempre en el servidor: dos sistemas peleandose la trabarian.
        // Mientras camina manda la navegacion, y mientras trabaja manda el bloque.
        if (getRoccoState() == RoccoState.ACTIVE
                && RoccoCommonConfig.LOOK_AT_PLAYER.get()
                && !isWorking()
                && this.jobs.isEmpty()
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

    // En planta, trabajando o con la tapa abierta no se mueve ni lo empujan.
    @Override
    protected boolean isImmobile()
    {
        return isPlantVisible() || isWorking() || this.entityData.get(DATA_LID) != LID_CLOSED || super.isImmobile();
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

        if (RoccoCommonConfig.INVULNERABLE.get() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            return false;
        }

        return super.hurt(source, amount);
    }

    // Inmortal pero no insensible: el golpe se nota (sonido, parpadeo rojo, empujon) y la vida
    // baja, pero se frena en 1. Asi se le puede pegar y no se muere.
    @Override
    protected void actuallyHurt(DamageSource source, float amount)
    {
        // /kill y el vacio tienen que seguir funcionando, si no no habria forma de sacarlo.
        if (!RoccoCommonConfig.IMMORTAL.get() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            super.actuallyHurt(source, amount);
            return;
        }

        float allowed = Math.max(0.0F, Math.min(amount, getHealth() - 1.0F));
        super.actuallyHurt(source, allowed);
    }

    @Override
    public boolean removeWhenFarAway(double distance)
    {
        return false;
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit)
    {
        super.dropCustomDeathLoot(source, looting, recentlyHit);

        // Lo que juntó no se pierde con él.
        for (int slot = 0; slot < this.inventory.getContainerSize(); slot++)
        {
            ItemStack stack = this.inventory.getItem(slot);
            if (!stack.isEmpty())
            {
                spawnAtLocation(stack);
            }
        }
        this.inventory.clearContent();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putInt("RoccoState", getRoccoState().ordinal());
        tag.putInt("RoccoExpression", getExpression().ordinal());
        tag.putInt("AwakenTicks", this.awakenTicks);
        tag.put("Inventory", this.inventory.createTag());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        setRoccoState(RoccoState.byId(tag.getInt("RoccoState")));
        setExpression(RoccoExpression.byId(tag.getInt("RoccoExpression")));
        this.awakenTicks = tag.getInt("AwakenTicks");

        if (tag.contains("Inventory"))
        {
            this.inventory.fromTag(tag.getList("Inventory", 10));
        }
    }

    // ---------- animaciones ----------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new AnimationController<>(this, "main", 3, this::mainAnimation)
                .triggerableAnim("wave", WAVE)
                .triggerableAnim("plant", PLANT)
                .triggerableAnim("harvest", HARVEST)
                .triggerableAnim("interact", INTERACT)
                .triggerableAnim("eat", EAT)
                .triggerableAnim("swallowterror", SWALLOW));

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
        int lid = this.entityData.get(DATA_LID);
        if (lid == LID_OPENED)
        {
            return LID_OPEN_LOOP;
        }
        if (lid == LID_CLOSING)
        {
            return LID_CLOSE;
        }

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

    // Cuenta los jugadores que lo tienen abierto, como un cofre: la tapa baja con el ultimo.
    private class RoccoContainer extends SimpleContainer
    {
        RoccoContainer()
        {
            super(INVENTORY_SIZE);
        }

        @Override
        public void startOpen(Player player)
        {
            if (!player.isSpectator())
            {
                RoccoEntity.this.viewers++;
            }
        }

        @Override
        public void stopOpen(Player player)
        {
            if (player.isSpectator())
            {
                return;
            }

            RoccoEntity.this.viewers = Math.max(0, RoccoEntity.this.viewers - 1);

            if (RoccoEntity.this.viewers == 0 && RoccoEntity.this.isLidOpen())
            {
                RoccoEntity.this.beginLidClose();
            }
        }

        @Override
        public boolean stillValid(Player player)
        {
            return RoccoEntity.this.isAlive() && player.distanceToSqr(RoccoEntity.this) < 64.0D;
        }
    }
}

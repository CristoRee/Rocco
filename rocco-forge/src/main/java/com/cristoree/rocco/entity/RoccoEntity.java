package com.cristoree.rocco.entity;

import com.cristoree.rocco.ai.OfflineRocco;
import com.cristoree.rocco.ai.RoccoBrain;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.config.RoccoConfig;
import com.cristoree.rocco.registry.ModSounds;
import com.cristoree.rocco.work.Crops;
import com.cristoree.rocco.work.Errand;
import com.cristoree.rocco.work.FarmErrand;
import com.cristoree.rocco.work.Inv;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
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
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class RoccoEntity extends PathfinderMob implements GeoEntity, MenuProvider
{
    // animation.rocco.awaken dura 3,3 s
    public static final int AWAKEN_TICKS = 66;
    // animation.rocco.lid_close dura 1,3 s; a los 23 ticks termina de sacudir la cabeza
    public static final int LID_CLOSE_TICKS = 26;
    public static final int LID_FACE_RESTORE_TICK = 23;

    // 4 filas de 9, como un cofre y un tercio.
    public static final int INVENTORY_SIZE = 36;

    private static final int LID_CLOSED = 0;
    private static final int LID_OPENED = 1;
    private static final int LID_CLOSING = 2;

    private static final double WORK_REACH = 2.2D;
    // GeckoLib tarda esto en pasar de una animacion a la siguiente (la transicion del
    // controlador "main"). Los sonidos y efectos salen del servidor, asi que se corren estos
    // ticks para caer justo con el fotograma y no un poquito antes.
    private static final int ANIM_LAG = 3;
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
    private static final EntityDataAccessor<Integer> DATA_POSE =
            SynchedEntityData.defineId(RoccoEntity.class, EntityDataSerializers.INT);
    // Que trabajo esta haciendo: el cliente lo necesita para mostrar el mate o la magia.
    private static final EntityDataAccessor<Integer> DATA_TASK =
            SynchedEntityData.defineId(RoccoEntity.class, EntityDataSerializers.INT);
    // El agua donde cayo la boya mientras pesca (ZERO = caña recogida) y si ya pico algo. El
    // anzuelo del juego solo existe para jugadores, asi que la boya y el hilo los dibuja el cliente.
    private static final EntityDataAccessor<BlockPos> DATA_BOBBER =
            SynchedEntityData.defineId(RoccoEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> DATA_BOBBER_BITE =
            SynchedEntityData.defineId(RoccoEntity.class, EntityDataSerializers.BOOLEAN);

    // Lo que tarda la boya en volar de la caña al agua.
    public static final int BOBBER_FLIGHT_TICKS = 10;

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
    // Bucle: se repite mientras pica y se corta al terminar el trabajo.
    private static final RawAnimation TOOL_SWING = RawAnimation.begin().thenLoop("animation.rocco.tool_swing");
    private static final RawAnimation EAT = RawAnimation.begin().thenPlay("animation.rocco.eat");
    private static final RawAnimation SWALLOW = RawAnimation.begin().thenPlay("animation.rocco.swallowterror");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay("animation.rocco.hurt");
    private static final RawAnimation HURT_LIGHT = RawAnimation.begin().thenPlay("animation.rocco.hurt_light");
    private static final RawAnimation SIT_DOWN = RawAnimation.begin().thenPlay("animation.rocco.sit_down");
    private static final RawAnimation STAND_UP = RawAnimation.begin().thenPlay("animation.rocco.stand_up");
    private static final RawAnimation SLEEP_START = RawAnimation.begin().thenPlay("animation.rocco.sleep_start");
    private static final RawAnimation WAKE_UP = RawAnimation.begin().thenPlay("animation.rocco.wake_up");
    private static final RawAnimation MATE = RawAnimation.begin().thenPlay("animation.rocco.mate");
    private static final RawAnimation ENCHANT = RawAnimation.begin().thenPlay("animation.rocco.enchant");
    private static final RawAnimation ENCHANT_LOOP = RawAnimation.begin().thenLoop("animation.rocco.enchant_loop");

    // El servidor no tiene particulas: el cliente cambia este gancho al arrancar.
    public interface ParticleHook
    {
        void spawn(RoccoEntity rocco, String effect, String locator);
    }

    public static ParticleHook PARTICLE_HOOK = (rocco, effect, locator) -> { };

    // Las estables van por estado, no disparadas, porque tienen que mantenerse en bucle.
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop("animation.rocco.sit");
    private static final RawAnimation SLEEP = RawAnimation.begin().thenLoop("animation.rocco.sleep");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final RoccoContainer inventory = new RoccoContainer();
    private final Deque<Job> jobs = new ArrayDeque<>();

    private RoccoBrain brain;
    private int awakenTicks;
    private int talkTicks;
    private int lidCloseTicks;
    private int viewers;
    private int gooTicks;

    // Se puede apagar por orden del jugador ("quedate ahi"), aparte del interruptor de la config.
    private boolean following = true;
    private boolean configApplied;

    private RoccoTask task = RoccoTask.NONE;
    private int taskTicks;
    private Job currentJob;
    private int approachTicks;
    private int pathFailures;
    private int crackStage = -1;

    @Nullable
    private Errand errand;
    private final List<Scheduled> scheduled = new ArrayList<>();

    // El cofre de la cosecha que eligio el jugador, y que tipos de cosas salieron de la huerta.
    @Nullable
    private BlockPos harvestChest;
    private final Set<net.minecraft.world.item.Item> harvestTypes = new HashSet<>();

    // Si quedo cuidando la huerta al cerrar el mundo, vuelve a hacerlo al cargar.
    @Nullable
    private BlockPos tendCenter;
    private RoccoExpression faceBeforeLid = RoccoExpression.HAPPY;
    private RoccoExpression faceBeforeSwallow = RoccoExpression.HAPPY;

    @Nullable
    private UUID ownerId;

    // Donde murio el jugador la ultima vez, para cuando pregunte.
    @Nullable
    private BlockPos lastDeathPos;
    private String lastDeathDimension = "";
    private long lastDeathDay = -1L;

    private int poseTicks;
    private int snoreTicks;

    private RoccoExpression faceBeforeOverride = RoccoExpression.HAPPY;
    private int faceOverrideTicks;

    // Cuanto dura una cara elegida por la IA antes de que el animo automatico pueda cambiarla.
    private static final int AI_FACE_TICKS = 600;
    private int aiFaceTicks;

    // Golpes recientes: uno da pena, varios dan bronca.
    private int recentHits;
    private int hitDecayTicks;
    private int boredTicks;

    // Rato libre con el jugador quieto (en la casa, haciendo sus cosas): a veces se sienta o
    // se toma un mate. autoRestTicks > 0 = se sento solo y se levanta solo.
    private int autoRestTicks;
    private int comeCloseTicks;
    private boolean autoSlept;
    @Nullable
    private net.minecraft.world.phys.Vec3 ownerAnchor;
    private int ownerStillTicks;
    private long lastMateTick = -1_000_000L;

    // El poder de encantar: una vez por "dia" (configurable). Se cuenta en tiempo de juego.
    private long lastEnchantTick = Long.MIN_VALUE / 2;

    // Confianza con el dueño: un punto por minuto juntos y dos por charla (por el ritmo elegido).
    private double trust;

    // Lugares que el jugador le pidio recordar ("esta es mi casa").
    public record Place(BlockPos pos, String dimension) {}
    private final java.util.Map<String, Place> places = new java.util.LinkedHashMap<>();

    // El chunk que mantiene cargado mientras te sigue, para no quedar congelado atras.
    @Nullable
    private net.minecraft.world.level.ChunkPos forcedChunk;

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
        // Pasea cerca del jugador aunque lo este siguiendo (dentro de behavior.wanderRadius).
        this.goalSelector.addGoal(3, new RoccoWanderGoal(this));
    }

    public boolean canWander()
    {
        return getRoccoState() == RoccoState.ACTIVE && isIdleForWandering();
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
        this.entityData.define(DATA_POSE, RoccoPose.STANDING.ordinal());
        this.entityData.define(DATA_TASK, RoccoTask.NONE.ordinal());
        this.entityData.define(DATA_BOBBER, BlockPos.ZERO);
        this.entityData.define(DATA_BOBBER_BITE, false);
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

    // La usa la IA: su eleccion manda un rato antes de que el animo automatico pueda cambiarla.
    public void setExpressionFromAi(RoccoExpression expression)
    {
        setExpression(expression);
        this.aiFaceTicks = AI_FACE_TICKS;
    }

    // Cara automatica por un rato (golpe, sueño). Guarda la que tenia para devolverla despues.
    private void overrideFace(RoccoExpression face, int ticks)
    {
        RoccoExpression current = getExpression();
        if (current.selectableByAi())
        {
            this.faceBeforeOverride = current;
        }

        setExpression(face);
        this.faceOverrideTicks = ticks;
    }

    private void restoreFace()
    {
        this.faceOverrideTicks = 0;
        if (!getExpression().selectableByAi())
        {
            setExpression(this.faceBeforeOverride);
        }
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

    // ---------- la boya de pescar ----------

    @Nullable
    public BlockPos getBobber()
    {
        BlockPos pos = this.entityData.get(DATA_BOBBER);
        return pos.equals(BlockPos.ZERO) ? null : pos;
    }

    public boolean isBobberBiting()
    {
        return this.entityData.get(DATA_BOBBER_BITE);
    }

    public void castBobber(BlockPos water)
    {
        this.entityData.set(DATA_BOBBER_BITE, false);
        this.entityData.set(DATA_BOBBER, water.immutable());
    }

    public void setBobberBite(boolean biting)
    {
        this.entityData.set(DATA_BOBBER_BITE, biting);
    }

    public void reelInBobber()
    {
        this.entityData.set(DATA_BOBBER_BITE, false);
        this.entityData.set(DATA_BOBBER, BlockPos.ZERO);
    }

    public void talkFor(int ticks)
    {
        // Regla 4 de la spec: con la boca grande abierta no puede hablar, o se encimarian
        // mouth_talk y la boca de la animacion.
        if (this.task == RoccoTask.EATING || this.task == RoccoTask.SWALLOWING || this.task == RoccoTask.MATE)
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
        // No suena nada aca: el "dirt" del tick 6 llega 0,3 s despues y ya se siente inmediato.
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

    // ---------- postura ----------

    public RoccoPose getRoccoPose()
    {
        return RoccoPose.byId(this.entityData.get(DATA_POSE));
    }

    private void setRoccoPose(RoccoPose pose)
    {
        this.entityData.set(DATA_POSE, pose.ordinal());
        this.poseTicks = 0;
        this.snoreTicks = 0;

        switch (pose)
        {
            case SITTING_DOWN -> triggerAnim("main", "sit_down");
            case STANDING_UP -> triggerAnim("main", "stand_up");
            case FALLING_ASLEEP ->
            {
                triggerAnim("main", "sleep_start");
                // 0 ticks: la cara de dormido dura hasta que se despierte, no un rato fijo.
                overrideFace(RoccoExpression.SLEEP, 0);
            }
            case WAKING_UP ->
            {
                triggerAnim("main", "wake_up");
                restoreFace();
            }
            default -> { }
        }

        if (pose.isResting())
        {
            getNavigation().stop();
        }
    }

    public void sitDown()
    {
        if (getRoccoPose() == RoccoPose.STANDING)
        {
            setRoccoPose(RoccoPose.SITTING_DOWN);
        }
    }

    public void goToSleep()
    {
        RoccoPose pose = getRoccoPose();
        if (pose == RoccoPose.STANDING || pose == RoccoPose.SITTING)
        {
            setRoccoPose(RoccoPose.FALLING_ASLEEP);
        }
    }

    // Se levanta de donde este. Devuelve false si ya estaba de pie.
    public boolean standUp()
    {
        RoccoPose pose = getRoccoPose();

        if (pose == RoccoPose.SITTING || pose == RoccoPose.SITTING_DOWN)
        {
            setRoccoPose(RoccoPose.STANDING_UP);
            return true;
        }
        if (pose.isAsleep())
        {
            setRoccoPose(RoccoPose.WAKING_UP);
            return true;
        }
        return false;
    }

    // ---------- dueño y memoria de muerte ----------

    public void setOwnerId(@Nullable UUID ownerId)
    {
        this.ownerId = ownerId;
    }

    @Nullable
    public UUID getOwnerId()
    {
        return this.ownerId;
    }

    public void rememberDeath(BlockPos pos, String dimension, long day)
    {
        this.lastDeathPos = pos.immutable();
        this.lastDeathDimension = dimension;
        this.lastDeathDay = day;
    }

    @Nullable
    public BlockPos getLastDeathPos()
    {
        return this.lastDeathPos;
    }

    public String getLastDeathDimension()
    {
        return this.lastDeathDimension;
    }

    public long getLastDeathDay()
    {
        return this.lastDeathDay;
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
        return !isWorking()
                && this.jobs.isEmpty()
                && this.errand == null
                && this.entityData.get(DATA_LID) == LID_CLOSED
                && !getRoccoPose().isResting();
    }

    public boolean isResting()
    {
        return getRoccoPose().isResting();
    }

    // ---------- trabajos ----------

    // entityId -1 cuando el trabajo es sobre un bloque y no sobre un bicho.
    // effect: lo que pasa en el tick de impacto; si es null, lo decide la tarea (plantar, comer...).
    // onFail: si no llega al lugar. duration y reach en 0 usan lo de la tarea y WORK_REACH.
    public record Job(RoccoTask task, BlockPos target, ItemStack shown, int entityId,
                      @Nullable Consumer<ServerLevel> effect, @Nullable Runnable onFail, int duration, double reach)
    {
        public static Job onBlock(RoccoTask task, BlockPos target, ItemStack shown)
        {
            return new Job(task, target, shown, -1, null, null, 0, 0.0D);
        }

        public static Job onEntity(RoccoTask task, Entity entity)
        {
            return new Job(task, entity.blockPosition(), ItemStack.EMPTY, entity.getId(), null, null, 0, 0.0D);
        }

        public static Job custom(RoccoTask task, BlockPos target, ItemStack shown,
                                 Consumer<ServerLevel> effect, @Nullable Runnable onFail)
        {
            return new Job(task, target.immutable(), shown, -1, effect, onFail, 0, 0.0D);
        }

        public Job lasting(int ticks)
        {
            return new Job(task, target, shown, entityId, effect, onFail, ticks, reach);
        }

        public Job reaching(double blocks)
        {
            return new Job(task, target, shown, entityId, effect, onFail, duration, blocks);
        }

        public Job failingWith(Runnable callback)
        {
            return new Job(task, target, shown, entityId, effect, callback, duration, reach);
        }

        int totalTicks()
        {
            return this.duration > 0 ? this.duration : this.task.totalTicks();
        }

        int impactTick()
        {
            if (this.task == RoccoTask.MINING)
            {
                return totalTicks();
            }
            return Math.min(this.task.impactTick(), totalTicks());
        }

        double reachOr(double fallback)
        {
            return this.reach > 0.0D ? this.reach : fallback;
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

    // ---------- encargos de varios pasos ----------

    @Nullable
    public Errand currentErrand()
    {
        return this.errand;
    }

    // Uno a la vez: el nuevo reemplaza al que hubiera.
    public void startErrand(Errand next)
    {
        cancelErrand();
        standUp();
        this.errand = next;
    }

    public void cancelErrand()
    {
        if (this.errand != null)
        {
            Errand old = this.errand;
            this.errand = null;
            old.cancelled();
        }
        this.jobs.clear();
    }

    // Lo que el jugador dice mientras hay un encargo ("busca en el cofre"). true si lo uso.
    public boolean signalErrand(String signal)
    {
        return this.errand != null && this.errand.signal(signal);
    }

    // Ocupado de verdad: el seguir al jugador lo sacaria de lo que esta haciendo.
    public boolean isBusy()
    {
        return isWorking()
                || !this.jobs.isEmpty()
                || (this.errand != null && !this.errand.allowsFollowing());
    }

    // Guarda algo en su inventario; lo que no entra queda en el piso al lado suyo.
    public void keep(ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return;
        }
        ItemStack leftover = Inv.insert(this.inventory, stack);
        if (!leftover.isEmpty())
        {
            ItemEntity dropped = spawnAtLocation(leftover);
            if (dropped != null)
            {
                dropped.setThrower(getUUID());
            }
        }
    }

    // Lo que se ve en la mano hasta que termina el trabajo actual (es solo para mostrar).
    public void showInHand(ItemStack stack)
    {
        setItemSlot(EquipmentSlot.MAINHAND, stack.copyWithCount(1));
    }

    // Habla por su cuenta (sin que nadie le pregunte), con una frase de su repertorio.
    public void sayLine(String category, String... keyValues)
    {
        sayLine(OfflineRocco.faceFor(category), category, keyValues);
    }

    public void sayLine(RoccoExpression face, String category, String... keyValues)
    {
        say(OfflineRocco.line(category, keyValues), face);
    }

    public void say(String text, @Nullable RoccoExpression face)
    {
        if (!(level() instanceof ServerLevel))
        {
            return;
        }
        brain().announce(text, face);
    }

    // Tareas cortas para dentro de un rato (cerrar la tapa del cofre, por ejemplo).
    public void schedule(int delayTicks, Runnable task)
    {
        this.scheduled.add(new Scheduled(this.tickCount + Math.max(1, delayTicks), task));
    }

    private record Scheduled(int at, Runnable task) {}

    private void tickScheduled()
    {
        if (this.scheduled.isEmpty())
        {
            return;
        }
        List<Scheduled> due = new java.util.ArrayList<>();
        this.scheduled.removeIf(entry ->
        {
            if (entry.at() <= this.tickCount)
            {
                due.add(entry);
                return true;
            }
            return false;
        });
        due.forEach(entry -> entry.task().run());
    }

    // El dueño si esta conectado; si no, el jugador mas cercano.
    @Nullable
    public ServerPlayer owner()
    {
        if (!(level() instanceof ServerLevel serverLevel))
        {
            return null;
        }
        if (this.ownerId != null && serverLevel.getPlayerByUUID(this.ownerId) instanceof ServerPlayer player)
        {
            return player;
        }
        return level().getNearestPlayer(this, 32.0D) instanceof ServerPlayer player ? player : null;
    }

    @Nullable
    public BlockPos getHarvestChest()
    {
        return this.harvestChest;
    }

    public void setHarvestChest(@Nullable BlockPos pos)
    {
        this.harvestChest = pos == null ? null : pos.immutable();
    }

    // Que tipos de items salieron de la huerta, para saber que guardar como "cosecha".
    public java.util.Set<net.minecraft.world.item.Item> harvestTypes()
    {
        return this.harvestTypes;
    }

    public void queueJob(Job job)
    {
        if (this.jobs.size() < 32)
        {
            this.jobs.add(job);
            // Sentado o dormido no puede trabajar: se levanta solo (antes habia que levantarlo
            // a mano para que plantara o cosechara).
            this.autoRestTicks = 0;
            standUp();
        }
    }

    // Lo que esta haciendo en este momento (tambien del lado del cliente, para el modelo).
    public RoccoTask getActiveTask()
    {
        return RoccoTask.byId(this.entityData.get(DATA_TASK));
    }

    // Corta el mate o el encantamiento a la mitad (un golpe, una orden nueva). Avisa al que lo
    // habia pedido como si no hubiera llegado, asi el encantamiento no gasta el poder del dia.
    public void interruptActivity()
    {
        if (this.task != RoccoTask.MATE && this.task != RoccoTask.ENCHANTING)
        {
            return;
        }
        Job interrupted = this.currentJob;
        stopTriggeredAnimation("main", this.task == RoccoTask.MATE ? "mate" : "enchant");
        finishJob();
        if (interrupted != null && interrupted.onFail() != null)
        {
            interrupted.onFail().run();
        }
    }

    private void startJob(Job job)
    {
        this.currentJob = job;
        this.task = job.task();
        this.taskTicks = 0;
        this.approachTicks = 0;

        this.entityData.set(DATA_WORKING, true);
        this.entityData.set(DATA_TASK, job.task().ordinal());
        this.entityData.set(DATA_WORK_TARGET, job.target());

        // La spec pide que el cultivo se vea en la mano desde el arranque de harvest, no solo
        // cuando lo arranca, asi que para cosechar se averigua antes que va a levantar.
        ItemStack shown = job.task() == RoccoTask.HARVESTING && job.shown().isEmpty()
                ? previewOf(job.target())
                : job.shown().copy();

        setItemSlot(EquipmentSlot.MAINHAND, shown);
        getNavigation().stop();

        switch (job.task())
        {
            case PLANTING -> triggerAnim("main", "plant");
            case HARVESTING -> triggerAnim("main", "harvest");
            case INTERACTING -> triggerAnim("main", "interact");
            case MINING -> triggerAnim("main", "tool_swing");
            case EATING -> triggerAnim("main", "eat");
            case SWALLOWING ->
            {
                // La spec pide psycho solo mientras traga, y recuperar la cara al terminar.
                this.faceBeforeSwallow = getExpression();
                setExpression(RoccoExpression.PSYCHO);
                triggerAnim("main", "swallowterror");
            }
            case MATE ->
            {
                this.faceBeforeSwallow = calmFace();
                setExpression(RoccoExpression.HAPPY);
                triggerAnim("main", "mate");
            }
            case ENCHANTING ->
            {
                this.faceBeforeSwallow = calmFace();
                triggerAnim("main", "enchant");
            }
            default -> { }
        }

        // Con la boca llena (o con la bombilla) no habla: asi mouth_talk queda escondido y no se
        // encima con la boca de la animacion.
        if (job.task() == RoccoTask.EATING || job.task() == RoccoTask.SWALLOWING || job.task() == RoccoTask.MATE)
        {
            setTalking(false);
            this.talkTicks = 0;
        }
    }

    // La cara a la que vuelve despues: si tenia una automatica (golpe, sueño), vuelve a happy.
    private RoccoExpression calmFace()
    {
        RoccoExpression current = getExpression();
        return current.selectableByAi() ? current : RoccoExpression.HAPPY;
    }

    // Las caras que la guia de animacion pide en cada tramo del mate y del encantamiento.
    private void tickActivityFace()
    {
        int t = this.taskTicks - ANIM_LAG;
        if (this.task == RoccoTask.MATE)
        {
            boolean sipping = (t >= 121 && t <= 327) || (t >= 410 && t <= 567);
            RoccoExpression wanted = sipping ? RoccoExpression.BLISS : RoccoExpression.HAPPY;
            if (getExpression() != wanted)
            {
                setExpression(wanted);
            }
        }
        else if (this.task == RoccoTask.ENCHANTING)
        {
            if (t == 4)
            {
                setExpression(RoccoExpression.FOCUS);
            }
            else if (t == 96)
            {
                setExpression(RoccoExpression.SMUG);
            }

            // Apunta al jugador todo el rato, como pide la animacion.
            Entity target = this.currentJob.entityId() >= 0 ? level().getEntity(this.currentJob.entityId()) : null;
            if (target != null)
            {
                getLookControl().setLookAt(target, 30.0F, 30.0F);
                lookAtBlock(target.blockPosition());
            }
        }
    }

    private ItemStack previewOf(BlockPos pos)
    {
        return level() instanceof ServerLevel serverLevel ? Crops.preview(serverLevel, pos) : ItemStack.EMPTY;
    }

    private void finishJob()
    {
        if (this.task == RoccoTask.SWALLOWING || this.task == RoccoTask.MATE || this.task == RoccoTask.ENCHANTING)
        {
            setExpression(this.faceBeforeSwallow);
        }
        if (this.task == RoccoTask.MATE && level() != null)
        {
            this.lastMateTick = level().getGameTime();
        }
        if (this.task == RoccoTask.MINING)
        {
            stopTriggeredAnimation("main", "tool_swing");
            clearCrack();
        }

        this.task = RoccoTask.NONE;
        this.currentJob = null;
        this.taskTicks = 0;
        this.entityData.set(DATA_WORKING, false);
        this.entityData.set(DATA_TASK, RoccoTask.NONE.ordinal());
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

        if (this.currentJob.effect() != null)
        {
            this.currentJob.effect().accept(serverLevel);
            return;
        }

        // Plantar, cosechar y el resto de los encargos traen su propio efecto; aca quedan los
        // trabajos sueltos de siempre: puertas y comer.
        switch (this.currentJob.task())
        {
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
        // El sonido no va aca: chew y gulp ya suenan en sus ticks dentro de la animacion.

        // Comer le hace bien, aunque sea inmortal.
        heal(2.0F);
    }

    private void swallowTarget(ServerLevel level)
    {
        Entity victim = level.getEntity(this.currentJob.entityId());
        // Si se escapo lejos mientras se acercaba, no lo traga a distancia.
        if (victim == null || !victim.isAlive() || victim.distanceToSqr(this) > 5.0D * 5.0D)
        {
            return;
        }

        // gulp_big ya suena en el tick 44 de swallowterror.

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

    private void tickWork()
    {
        if (this.task != RoccoTask.NONE && this.currentJob != null)
        {
            this.taskTicks++;
            playTaskSound(this.taskTicks - ANIM_LAG);

            if (this.task == RoccoTask.MINING)
            {
                tickMining();
            }
            else if (this.task == RoccoTask.MATE || this.task == RoccoTask.ENCHANTING)
            {
                tickActivityFace();
            }

            if (this.currentJob != null && this.taskTicks == this.currentJob.impactTick() + ANIM_LAG)
            {
                applyJobEffect();
            }

            if (this.currentJob != null && this.taskTicks >= this.currentJob.totalTicks() + ANIM_LAG)
            {
                finishJob();
            }
            return;
        }

        Job next = this.jobs.peek();
        if (next == null)
        {
            tickErrand();
            return;
        }

        // Primero termina de pararse: sentado o dormido no camina.
        if (getRoccoPose() != RoccoPose.STANDING)
        {
            if (!getRoccoPose().isTransition())
            {
                standUp();
            }
            return;
        }

        if (this.entityData.get(DATA_LID) != LID_CLOSED)
        {
            return;
        }

        // Primero se acerca; la animacion arranca cuando la mano llega al bloque.
        double distance = Math.sqrt(distanceToSqr(
                next.target().getX() + 0.5D, next.target().getY(), next.target().getZ() + 0.5D));

        if (distance <= next.reachOr(WORK_REACH))
        {
            lookAtBlock(next.target());
            this.jobs.poll();
            this.pathFailures = 0;
            startJob(next);
            return;
        }

        this.approachTicks++;

        // Sin camino, o llego a donde podia y le sigue faltando: no tiene sentido esperar el timeout.
        boolean stuckShort = this.approachTicks > 40 && getNavigation().isDone();
        if (this.approachTicks > WORK_TIMEOUT_TICKS || this.pathFailures >= 3 || stuckShort)
        {
            // No pudo llegar: se descarta, avisa a quien lo pidio y sigue con el proximo.
            this.jobs.poll();
            this.approachTicks = 0;
            this.pathFailures = 0;
            getNavigation().stop();
            if (next.onFail() != null)
            {
                next.onFail().run();
            }
            return;
        }

        if (this.approachTicks % 10 == 1)
        {
            boolean moving = getNavigation().moveTo(next.target().getX() + 0.5D, next.target().getY(),
                    next.target().getZ() + 0.5D, 1.0D);
            this.pathFailures = moving ? 0 : this.pathFailures + 1;
        }
    }

    private void tickErrand()
    {
        if (this.errand == null
                || this.entityData.get(DATA_LID) != LID_CLOSED
                || getRoccoPose().isResting())
        {
            return;
        }

        Errand running = this.errand;
        Errand.Status status;
        try
        {
            status = running.tick();
        }
        catch (RuntimeException e)
        {
            // Un encargo roto no puede tirar abajo el mundo: se corta y queda en el log.
            com.cristoree.rocco.RoccoMod.LOGGER.error("[Rocco] El encargo '{}' fallo", running.describe(), e);
            status = Errand.Status.FAILED;
        }

        if (status != Errand.Status.RUNNING && this.errand == running)
        {
            this.errand = null;
        }
    }

    // La grieta avanza con el trabajo y cada golpe del bucle suena contra el bloque.
    private void tickMining()
    {
        BlockPos target = this.currentJob.target();
        int total = Math.max(1, this.currentJob.totalTicks());
        int stage = Math.min(9, this.taskTicks * 10 / total);

        if (stage != this.crackStage)
        {
            this.crackStage = stage;
            level().destroyBlockProgress(getId(), target, stage);
        }

        if (this.taskTicks >= ANIM_LAG && (this.taskTicks - ANIM_LAG) % 12 == 5)
        {
            BlockState state = level().getBlockState(target);
            if (!state.isAir())
            {
                var sound = state.getSoundType();
                level().playSound(null, target, sound.getHitSound(), net.minecraft.sounds.SoundSource.NEUTRAL,
                        (sound.getVolume() + 1.0F) / 4.0F, sound.getPitch() * 0.5F);
            }
        }
    }

    private void clearCrack()
    {
        if (this.currentJob != null)
        {
            level().destroyBlockProgress(getId(), this.currentJob.target(), -1);
        }
        this.crackStage = -1;
    }

    // Tres capas, de mas a menos prioridad: cara forzada (golpe, sueño), cara que eligio la IA,
    // y animo automatico. Asi la IA manda un rato pero Rocco no queda con la misma cara para
    // siempre, y cada expresion tiene su momento.
    private void tickFace()
    {
        if (this.faceOverrideTicks > 0 && --this.faceOverrideTicks == 0)
        {
            restoreFace();

            // Despues del golpe queda dolido: pena la primera vez, bronca si insisten.
            if (this.recentHits > 0)
            {
                setExpression(this.recentHits >= 3 ? RoccoExpression.ANGRY : RoccoExpression.SAD);
                this.aiFaceTicks = 300;
            }
        }

        if (this.hitDecayTicks > 0 && --this.hitDecayTicks == 0)
        {
            this.recentHits = 0;
        }

        if (this.aiFaceTicks > 0)
        {
            this.aiFaceTicks--;
        }

        // El animo se revisa una vez por segundo: no hace falta mas y evita parpadeos.
        if (this.tickCount % 20 != 0
                || this.faceOverrideTicks > 0
                || this.aiFaceTicks > 0
                || getRoccoState() != RoccoState.ACTIVE
                || getRoccoPose().isAsleep()
                || this.entityData.get(DATA_LID) != LID_CLOSED)
        {
            return;
        }

        RoccoExpression ambient = ambientFace();
        if (ambient != null && getExpression() != ambient)
        {
            setExpression(ambient);
        }
    }

    @Nullable
    private RoccoExpression ambientFace()
    {
        if (!RoccoCommonConfig.MOOD_ENABLED.get())
        {
            return null;
        }

        // Miedo: algo hostil cerca o poca vida propia.
        if (getHealth() < getMaxHealth() * 0.35F || hostileNearby())
        {
            this.boredTicks = 0;
            return RoccoExpression.SCARED;
        }

        // Aburrimiento: un rato largo sin trabajo ni movimiento. Se cuenta una vez por segundo,
        // asi que 120 son dos minutos quieto.
        if (!isWorking() && this.jobs.isEmpty() && getNavigation().isDone())
        {
            this.boredTicks++;
        }
        else
        {
            this.boredTicks = 0;
        }

        if (this.boredTicks > 120)
        {
            return RoccoExpression.BORED;
        }

        return this.boredTicks == 0 ? RoccoExpression.HAPPY : null;
    }

    private boolean hostileNearby()
    {
        return !level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8.0D),
                entity -> entity instanceof net.minecraft.world.entity.monster.Enemy && entity.isAlive())
                .isEmpty();
    }

    private void tickPose()
    {
        RoccoPose pose = getRoccoPose();
        this.poseTicks++;

        // Sonidos de las transiciones, en los ticks de ROCCO-SONIDOS.md.
        if (pose == RoccoPose.SITTING_DOWN && this.poseTicks == 9 + ANIM_LAG)
        {
            playRoccoSound(ModSounds.SIT_PLOP.get(), 0.6F);
        }
        else if (pose == RoccoPose.FALLING_ASLEEP)
        {
            if (this.poseTicks == 25 + ANIM_LAG)
            {
                playRoccoSound(ModSounds.SIT_PLOP.get(), 0.6F);
            }
            // Mas bajito: aca cae de espaldas, no se sienta.
            else if (this.poseTicks == 36 + ANIM_LAG)
            {
                playRoccoSound(ModSounds.SIT_PLOP.get(), 0.4F);
            }
        }

        // Dormido: una respiracion cada 40 ticks y la burbujita a los 70 de cada vuelta de 80.
        if (pose == RoccoPose.SLEEPING)
        {
            if (this.snoreTicks >= ANIM_LAG && (this.snoreTicks - ANIM_LAG) % 40 == 0)
            {
                playRoccoSound(ModSounds.SNORE.get(), 0.4F);
            }
            if (this.snoreTicks >= ANIM_LAG && (this.snoreTicks - ANIM_LAG) % 80 == 70)
            {
                playRoccoSound(ModSounds.BUBBLE_POP.get(), 0.5F);
            }
            this.snoreTicks++;
        }

        if (pose.isTransition() && this.poseTicks >= pose.ticks() + ANIM_LAG)
        {
            RoccoPose next = pose.next();
            if (next != null)
            {
                setRoccoPose(next);
            }
        }
    }

    // Los ticks salen de ROCCO-SONIDOS.md: cada golpe cae junto al fotograma que le corresponde.
    private void playTaskSound(int tick)
    {
        switch (this.task)
        {
            case PLANTING ->
            {
                if (tick == 11 || tick == 16 || tick == 22)
                {
                    playRoccoSound(ModSounds.DIRT.get(), 0.5F);
                }
            }
            case HARVESTING ->
            {
                if (tick == 11)
                {
                    playRoccoSound(ModSounds.DIRT.get(), 0.5F);
                }
                // El archivo trae el crujido antes y el "pop" cae solo en el tick 22.
                if (tick == 16)
                {
                    playRoccoSound(ModSounds.HARVEST_POP.get(), 0.7F);
                }
            }
            case EATING ->
            {
                if (tick == 34 || tick == 40 || tick == 46)
                {
                    playRoccoSound(ModSounds.CHEW.get(), 0.6F);
                }
                if (tick == 52)
                {
                    playRoccoSound(ModSounds.GULP.get(), 0.7F);
                }
            }
            case SWALLOWING ->
            {
                if (tick == 44)
                {
                    playRoccoSound(ModSounds.GULP_BIG.get(), 1.0F);
                }
            }
            // Ticks de ROCCO-MATE-Y-ENCANTAR.md.
            case MATE ->
            {
                if (tick == 35)
                {
                    playRoccoSound(ModSounds.LEAVES.get(), 0.4F);
                }
                else if (tick == 64 || tick == 353)
                {
                    playRoccoSound(ModSounds.MATE_POUR.get(), 0.6F);
                }
                else if (tick == 130 || tick == 204 || tick == 278 || tick == 419 || tick == 493)
                {
                    playRoccoSound(ModSounds.MATE_SIP.get(), 0.5F);
                }
                else if (tick == 295 || tick == 510)
                {
                    playRoccoSound(ModSounds.MATE_SLURP.get(), 0.6F);
                }
            }
            case ENCHANTING ->
            {
                if (tick == 13)
                {
                    playRoccoSound(ModSounds.MAGIC_HUM.get(), 0.6F);
                }
                else if (tick == 89)
                {
                    playRoccoSound(ModSounds.MAGIC_BURST.get(), 0.8F);
                }
            }
            // interact usa el sonido del propio bloque, que ya pone RoccoWork.
            default -> { }
        }
    }

    // Tono variado como hace Minecraft, para que no suene calcado.
    private void playRoccoSound(SoundEvent sound, float volume)
    {
        playSound(sound, volume, 0.9F + this.random.nextFloat() * 0.2F);
    }

    // Gira todo el cuerpo (no solo la cabeza) hacia ese bloque, de una.
    public void lookAtBlock(BlockPos pos)
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

        // El archivo ya trae el pop, el roce, la baba y el golpecito al apoyarse, sincronizados.
        schedule(ANIM_LAG, () -> playRoccoSound(ModSounds.LID_OPEN.get(), 0.8F));
        this.gooTicks = 40 + this.random.nextInt(40);
        player.openMenu(this);
    }

    private void beginLidClose()
    {
        this.entityData.set(DATA_LID, LID_CLOSING);
        this.lidCloseTicks = 0;
        // El "clonc" del archivo cae a los 0,58 s, igual que en la animacion.
        schedule(ANIM_LAG, () -> playRoccoSound(ModSounds.LID_CLOSE.get(), 0.8F));
    }

    private void tickLid()
    {
        // Con la tapa abierta la baba chapotea cada tanto.
        if (this.entityData.get(DATA_LID) == LID_OPENED && --this.gooTicks <= 0)
        {
            this.gooTicks = 40 + this.random.nextInt(40);
            playRoccoSound(ModSounds.GOO.get(), 0.3F);
        }

        if (this.entityData.get(DATA_LID) != LID_CLOSING)
        {
            return;
        }

        this.lidCloseTicks++;

        if (this.lidCloseTicks == LID_FACE_RESTORE_TICK + ANIM_LAG)
        {
            setExpression(this.faceBeforeLid);
        }

        if (this.lidCloseTicks >= LID_CLOSE_TICKS + ANIM_LAG)
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
        // fourRows: 4 filas de 9 (36 lugares).
        return new ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x4, containerId, playerInventory, this.inventory, 4);
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

            // Se sacude, salta fuera de la tierra, y le brotan las hojitas.
            switch (this.awakenTicks - ANIM_LAG)
            {
                case 6 -> playRoccoSound(ModSounds.DIRT.get(), 0.5F);
                case 40 -> playRoccoSound(ModSounds.AWAKEN_POP.get(), 0.8F);
                case 42 -> playRoccoSound(ModSounds.LEAVES.get(), 0.4F);
                default -> { }
            }

            if (this.awakenTicks >= AWAKEN_TICKS + ANIM_LAG)
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

        tickFace();
        tickLid();
        tickPose();
        tickScheduled();

        if (getRoccoState() == RoccoState.ACTIVE)
        {
            resumeTending();
            tickWork();
            tickGifts();
            tickCompanionship();
        }

        // Si dejo de pescar por cualquier lado (otra orden, un teleport), la boya no queda colgando.
        BlockPos bobber = getBobber();
        if (bobber != null && (!(this.errand instanceof com.cristoree.rocco.work.FishErrand)
                || distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(bobber)) > 12.0D * 12.0D))
        {
            reelInBobber();
        }

        // Un solo lugar gira la cabeza, siempre en el servidor: dos sistemas peleandose la trabarian.
        // Mientras camina manda la navegacion, mientras trabaja manda el bloque, y en un encargo
        // que lo tiene ocupado (pescando, por ejemplo) manda el encargo.
        if (getRoccoState() == RoccoState.ACTIVE
                && RoccoCommonConfig.LOOK_AT_PLAYER.get()
                && !isWorking()
                && this.jobs.isEmpty()
                && (this.errand == null || this.errand.allowsFollowing())
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

    // Lo que un jugador le tira (Q) cerca de los pies, lo agarra: es la forma de darle cosas.
    // Solo lo que tiro un jugador, para no barrer el piso con todo lo que ande suelto.
    private void tickGifts()
    {
        if (this.tickCount % 10 != 0 || !RoccoCommonConfig.WORK_PICK_UP_GIFTS.get() || isLidOpen())
        {
            return;
        }

        List<ItemEntity> nearby = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(1.5D, 0.5D, 1.5D),
                item -> item.isAlive() && item.getAge() > 15 && item.getOwner() instanceof Player);

        for (ItemEntity item : nearby)
        {
            ItemStack stack = item.getItem();
            int before = stack.getCount();
            ItemStack leftover = Inv.insert(this.inventory, stack);
            int taken = before - leftover.getCount();
            if (taken <= 0)
            {
                continue;
            }

            take(item, taken);
            if (leftover.isEmpty())
            {
                item.discard();
            }
            else
            {
                item.setItem(leftover);
            }
            playSound(SoundEvents.ITEM_PICKUP, 0.4F, 1.4F + this.random.nextFloat() * 0.4F);
        }
    }

    // ---------- rato libre con el jugador ----------

    // Una vez por segundo: si el jugador anda quieto (en la casa, haciendo sus cosas), Rocco a
    // veces se sienta o se toma un mate; si el jugador se acuesta, el tambien.
    private void tickCompanionship()
    {
        if (this.comeCloseTicks > 0)
        {
            this.comeCloseTicks--;
        }
        if (this.tickCount % 20 != 0 || getRoccoState() != RoccoState.ACTIVE)
        {
            return;
        }

        ServerPlayer owner = owner();
        rememberWhereIAm(owner);
        if (owner == null || owner.level() != level())
        {
            return;
        }

        trackOwnerStillness(owner);

        // Cada minuto juntos (cerca) suma confianza; tambien suma cada charla (RoccoBrain).
        if (distanceToSqr(owner) < 32 * 32)
        {
            addTrust(1.0D / 60.0D);
        }

        // Si hubo silencio un buen rato, dice algo por su cuenta.
        brain().tickChatter();

        // Si se puso a tomar mate y te fuiste, lo deja para seguirte.
        if (this.task == RoccoTask.MATE && this.following
                && distanceToSqr(owner) > sqr(RoccoCommonConfig.WANDER_RADIUS.get() + 8.0D))
        {
            interruptActivity();
        }

        // Dormir juntos: cuando el jugador se acuesta, Rocco tambien; y se despierta con el.
        if (RoccoCommonConfig.SLEEP_WITH_PLAYER.get())
        {
            if (owner.isSleeping() && !getRoccoPose().isAsleep() && isFreeTime() && distanceToSqr(owner) < 48 * 48)
            {
                this.autoRestTicks = 0;
                goToSleep();
                this.autoSlept = getRoccoPose().isAsleep();
            }
            else if (!owner.isSleeping() && this.autoSlept)
            {
                this.autoSlept = false;
                standUp();
            }
        }

        // Sentado solo: se levanta cuando se cansa o cuando el jugador se va.
        if (this.autoRestTicks > 0)
        {
            this.autoRestTicks -= 20;
            boolean ownerLeft = distanceToSqr(owner) > sqr(RoccoCommonConfig.WANDER_RADIUS.get());
            if (this.autoRestTicks <= 0 || ownerLeft)
            {
                this.autoRestTicks = 0;
                if (getRoccoPose() == RoccoPose.SITTING)
                {
                    standUp();
                }
            }
            return;
        }

        if (!isFreeTime() || !this.following || this.ownerStillTicks < 20 * 20
                || distanceToSqr(owner) > sqr(RoccoCommonConfig.WANDER_RADIUS.get() + 4.0D) || hostileNearby())
        {
            return;
        }

        float roll = this.random.nextFloat();
        long sinceMate = level().getGameTime() - this.lastMateTick;
        boolean mateReady = RoccoCommonConfig.IDLE_MATE_ENABLED.get()
                && sinceMate > RoccoCommonConfig.IDLE_MATE_COOLDOWN_MINUTES.get() * 1200L;

        if (mateReady && roll < 0.012F)
        {
            startMate();
        }
        else if (RoccoCommonConfig.IDLE_SIT_ENABLED.get() && roll > 1.0F - 0.008F)
        {
            sitDown();
            this.autoRestTicks = (30 + this.random.nextInt(60)) * 20;
        }
    }

    // Libre de verdad: sin encargos, sin trabajos, de pie, con la tapa cerrada.
    private boolean isFreeTime()
    {
        return this.errand == null && this.jobs.isEmpty() && this.task == RoccoTask.NONE
                && this.entityData.get(DATA_LID) == LID_CLOSED && getRoccoPose() == RoccoPose.STANDING;
    }

    // Quieto = no se alejo mas de 6 bloques de donde estaba. Asi cuenta "en la casa".
    private void trackOwnerStillness(ServerPlayer owner)
    {
        net.minecraft.world.phys.Vec3 now = owner.position();
        if (this.ownerAnchor == null || this.ownerAnchor.distanceToSqr(now) > 36.0D)
        {
            this.ownerAnchor = now;
            this.ownerStillTicks = 0;
        }
        else
        {
            this.ownerStillTicks += 20;
        }
    }

    // Si se lo ordenaste vos (sentate, a dormir, levantate), ya no se para ni se acuesta solo.
    public void clearAutoRest()
    {
        this.autoRestTicks = 0;
        this.autoSlept = false;
    }

    public boolean isOwnerSettled()
    {
        return this.comeCloseTicks <= 0 && this.ownerStillTicks >= 20 * 20;
    }

    // "Seguime" / "veni": durante un rato viene pegado a vos aunque estes quieto.
    public void comeClose()
    {
        this.comeCloseTicks = 20 * 30;
        this.ownerStillTicks = 0;
    }

    // Se toma un mate: la animacion entera, ahi donde esta.
    public void startMate()
    {
        this.autoRestTicks = 0;
        queueJob(Job.custom(RoccoTask.MATE, blockPosition(), ItemStack.EMPTY, level -> { }, null).reaching(64.0D));
    }

    // ---------- la confianza con el dueño ----------

    public void addTrust(double points)
    {
        this.trust += points * com.cristoree.rocco.ai.personality.Relationship.multiplier(
                com.cristoree.rocco.ai.personality.PersonalityProfile.current().trustPace());
    }

    public com.cristoree.rocco.ai.personality.Relationship relationship()
    {
        return com.cristoree.rocco.ai.personality.Relationship.of(this.trust,
                com.cristoree.rocco.ai.personality.PersonalityProfile.current().trustPace());
    }

    // Cuantos ticks lleva el dueño sin alejarse de donde estaba (en la casa, haciendo sus cosas).
    public int ownerStillTicks()
    {
        return this.ownerStillTicks;
    }

    // ---------- el poder de encantar ----------

    // Ticks que le faltan para poder volver a encantar. 0 = listo.
    public long enchantCooldownLeft()
    {
        long cooldown = RoccoCommonConfig.ENCHANT_COOLDOWN_MINUTES.get() * 1200L;
        long since = level().getGameTime() - this.lastEnchantTick;
        return Math.max(0L, cooldown - since);
    }

    public void markEnchantUsed()
    {
        this.lastEnchantTick = level().getGameTime();
    }

    // Despues de encantar queda fundido: se sienta a descansar un rato.
    public void restAfterMagic()
    {
        sitDown();
        this.autoRestTicks = 30 * 20;
    }

    // ---------- lugares ----------

    public java.util.Map<String, Place> places()
    {
        return this.places;
    }

    public void rememberPlace(String name, BlockPos pos, String dimension)
    {
        if (!this.places.containsKey(name) && this.places.size() >= 32)
        {
            return;
        }
        this.places.put(name, new Place(pos.immutable(), dimension));
    }

    // ---------- que no quede atras ----------

    // Le anota al dueño donde esta, para poder ir a buscarlo aunque su zona se descargue.
    private void rememberWhereIAm(@Nullable ServerPlayer owner)
    {
        if (owner != null && getOwnerId() != null && owner.getUUID().equals(getOwnerId()))
        {
            var data = owner.getPersistentData();
            data.putLong(com.cristoree.rocco.RoccoCompanionTracker.TAG_ROCCO_POS, blockPosition().asLong());
            data.putString(com.cristoree.rocco.RoccoCompanionTracker.TAG_ROCCO_DIM, level().dimension().location().toString());
            data.putBoolean(com.cristoree.rocco.RoccoCompanionTracker.TAG_ROCCO_FOLLOWING, this.following);
        }
        updateChunkTicket();
    }

    // Mientras te sigue, su chunk queda cargado: si te alejas rapido (elytra, caballo, un tp)
    // sigue "despierto" y se teletransporta en cuanto pueda, en vez de quedar congelado.
    private void updateChunkTicket()
    {
        if (!(level() instanceof ServerLevel serverLevel))
        {
            return;
        }
        boolean wanted = this.following && getRoccoState() == RoccoState.ACTIVE && isAlive();
        net.minecraft.world.level.ChunkPos current = chunkPosition();

        if (this.forcedChunk != null && (!wanted || !this.forcedChunk.equals(current)))
        {
            net.minecraftforge.common.world.ForgeChunkManager.forceChunk(serverLevel, com.cristoree.rocco.RoccoMod.MODID,
                    this, this.forcedChunk.x, this.forcedChunk.z, false, true);
            this.forcedChunk = null;
        }
        if (wanted && this.forcedChunk == null)
        {
            net.minecraftforge.common.world.ForgeChunkManager.forceChunk(serverLevel, com.cristoree.rocco.RoccoMod.MODID,
                    this, current.x, current.z, true, true);
            this.forcedChunk = current;
        }
    }

    @Override
    public void remove(RemovalReason reason)
    {
        // Si se va del mundo de verdad (o cambia de dimension) suelta el chunk que tenia agarrado.
        // Al cerrar el mundo no hace falta: al abrirlo se sueltan todos solos.
        boolean leaving = reason.shouldDestroy() || reason == RemovalReason.CHANGED_DIMENSION;
        if (leaving && this.forcedChunk != null && level() instanceof ServerLevel serverLevel)
        {
            try
            {
                net.minecraftforge.common.world.ForgeChunkManager.forceChunk(serverLevel, com.cristoree.rocco.RoccoMod.MODID,
                        this, this.forcedChunk.x, this.forcedChunk.z, false, true);
            }
            catch (RuntimeException e)
            {
                com.cristoree.rocco.RoccoMod.LOGGER.debug("[Rocco] No pude soltar el chunk: {}", e.toString());
            }
            this.forcedChunk = null;
        }
        super.remove(reason);
    }

    private static double sqr(double value)
    {
        return value * value;
    }

    // Al cargar el mundo vuelve a cuidar la huerta si eso estaba haciendo.
    private void resumeTending()
    {
        if (this.tendCenter == null || this.errand != null)
        {
            return;
        }
        BlockPos center = this.tendCenter;
        this.tendCenter = null;
        this.errand = new FarmErrand(this, null, true, center);
    }

    private void greet(Player player)
    {
        // Con IA el saludo lo inventa ella; sin IA usa una frase propia. Siempre dice algo.
        if (player instanceof ServerPlayer serverPlayer && RoccoConfig.chatReady())
        {
            brain().greet(serverPlayer);
        }
    }

    // En planta, trabajando o con la tapa abierta no se mueve ni lo empujan.
    @Override
    protected boolean isImmobile()
    {
        return isPlantVisible()
                || isWorking()
                || this.entityData.get(DATA_LID) != LID_CLOSED
                || getRoccoPose().isResting()
                || super.isImmobile();
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

        boolean hit = super.hurt(source, amount);

        if (hit && !level().isClientSide && getRoccoState() == RoccoState.ACTIVE)
        {
            // Primero se corta lo que estaba haciendo (mate, magia), asi la animacion del golpe
            // no queda pisada.
            interruptActivity();

            // Un golpecito lo sacude; uno fuerte lo dobla. La cara acompaña a la animacion y
            // dura lo mismo, despues vuelve la que tenia.
            boolean heavy = amount >= 4.0F;
            triggerAnim("main", heavy ? "hurt" : "hurt_light");
            overrideFace(heavy ? RoccoExpression.HURT : RoccoExpression.HURT_LIGHT, heavy ? 22 : 10);

            // Se olvida del maltrato despues de un rato tranquilo.
            this.recentHits++;
            this.hitDecayTicks = 600;

            // Si dormia o estaba sentado, se levanta de un salto.
            this.autoRestTicks = 0;
            this.autoSlept = false;
            standUp();
        }

        return hit;
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
    protected void playStepSound(BlockPos pos, BlockState state)
    {
        playRoccoSound(ModSounds.STEP.get(), 0.15F);
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.HURT.get();
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound()
    {
        // Siendo inmortal casi nunca suena, pero si alguien apaga la opcion conviene tenerlo.
        return ModSounds.HURT.get();
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

        // La postura se guarda ya resuelta: si lo apagaste mientras se sentaba, vuelve sentado.
        RoccoPose pose = getRoccoPose();
        tag.putInt("Pose", (pose.isTransition() && pose.next() != null ? pose.next() : pose).ordinal());

        tag.putBoolean("Following", this.following);

        if (this.harvestChest != null)
        {
            tag.putLong("HarvestChest", this.harvestChest.asLong());
        }
        BlockPos tending = this.errand instanceof FarmErrand farm && farm.persistent() ? farm.center() : this.tendCenter;
        if (tending != null)
        {
            tag.putLong("TendCenter", tending.asLong());
        }

        tag.putBoolean("AutoSlept", this.autoSlept);
        tag.putInt("AutoRest", this.autoRestTicks);
        tag.putLong("LastMate", this.lastMateTick);
        tag.putLong("LastEnchant", this.lastEnchantTick);
        tag.putDouble("Trust", this.trust);

        net.minecraft.nbt.ListTag placeList = new net.minecraft.nbt.ListTag();
        this.places.forEach((name, place) ->
        {
            CompoundTag entry = new CompoundTag();
            entry.putString("Name", name);
            entry.putLong("Pos", place.pos().asLong());
            entry.putString("Dim", place.dimension());
            placeList.add(entry);
        });
        tag.put("Places", placeList);

        if (this.ownerId != null)
        {
            tag.putUUID("Owner", this.ownerId);
        }

        if (this.lastDeathPos != null)
        {
            tag.putInt("DeathX", this.lastDeathPos.getX());
            tag.putInt("DeathY", this.lastDeathPos.getY());
            tag.putInt("DeathZ", this.lastDeathPos.getZ());
            tag.putString("DeathDim", this.lastDeathDimension);
            tag.putLong("DeathDay", this.lastDeathDay);
        }
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

        this.entityData.set(DATA_POSE, RoccoPose.byId(tag.getInt("Pose")).ordinal());

        // Si no esta en el NBT es un Rocco de una version anterior: por defecto sigue al jugador.
        this.following = !tag.contains("Following") || tag.getBoolean("Following");

        this.harvestChest = tag.contains("HarvestChest") ? BlockPos.of(tag.getLong("HarvestChest")) : null;
        this.tendCenter = tag.contains("TendCenter") ? BlockPos.of(tag.getLong("TendCenter")) : null;

        this.autoSlept = tag.getBoolean("AutoSlept");
        this.autoRestTicks = tag.getInt("AutoRest");
        if (tag.contains("LastMate"))
        {
            this.lastMateTick = tag.getLong("LastMate");
        }
        if (tag.contains("LastEnchant"))
        {
            this.lastEnchantTick = tag.getLong("LastEnchant");
        }
        this.trust = tag.getDouble("Trust");

        this.places.clear();
        for (net.minecraft.nbt.Tag raw : tag.getList("Places", 10))
        {
            CompoundTag entry = (CompoundTag) raw;
            this.places.put(entry.getString("Name"), new Place(BlockPos.of(entry.getLong("Pos")), entry.getString("Dim")));
        }

        // El mate y la magia no se guardan a la mitad: si quedo con esa cara, vuelve a la normal.
        if (getExpression() == RoccoExpression.BLISS || getExpression() == RoccoExpression.FOCUS)
        {
            setExpression(RoccoExpression.HAPPY);
        }

        if (tag.hasUUID("Owner"))
        {
            this.ownerId = tag.getUUID("Owner");
        }

        if (tag.contains("DeathDim"))
        {
            this.lastDeathPos = new BlockPos(tag.getInt("DeathX"), tag.getInt("DeathY"), tag.getInt("DeathZ"));
            this.lastDeathDimension = tag.getString("DeathDim");
            this.lastDeathDay = tag.getLong("DeathDay");
        }
    }

    // ---------- animaciones ----------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new AnimationController<>(this, "main", ANIM_LAG, this::mainAnimation)
                .triggerableAnim("wave", WAVE)
                .triggerableAnim("plant", PLANT)
                .triggerableAnim("harvest", HARVEST)
                .triggerableAnim("interact", INTERACT)
                .triggerableAnim("tool_swing", TOOL_SWING)
                .triggerableAnim("eat", EAT)
                .triggerableAnim("swallowterror", SWALLOW)
                .triggerableAnim("hurt", HURT)
                .triggerableAnim("hurt_light", HURT_LIGHT)
                .triggerableAnim("sit_down", SIT_DOWN)
                .triggerableAnim("stand_up", STAND_UP)
                .triggerableAnim("sleep_start", SLEEP_START)
                .triggerableAnim("wake_up", WAKE_UP)
                .triggerableAnim("mate", MATE)
                .triggerableAnim("enchant", ENCHANT)
                .triggerableAnim("enchant_loop", ENCHANT_LOOP)
                // Las animaciones traen sus particulas (florcitas, vapor, runas): las dibuja el cliente.
                .setParticleKeyframeHandler(event -> PARTICLE_HOOK.spawn(this,
                        event.getKeyframeData().getEffect(), event.getKeyframeData().getLocator())));

        controllers.add(new AnimationController<>(this, "mouth", 0, state ->
                isTalking() ? state.setAndContinue(TALK) : PlayState.STOP));

        controllers.add(new AnimationController<>(this, "blink", 0, state ->
                isPlantVisible() || getExpression().eyesClosed() ? PlayState.STOP : state.setAndContinue(BLINK)));
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

        // Sentado y dormido son bucles que tienen que sostenerse; las transiciones las toca
        // triggerAnim y pisan esto mientras duran.
        RoccoPose pose = getRoccoPose();
        if (pose == RoccoPose.SITTING)
        {
            return SIT;
        }
        if (pose == RoccoPose.SLEEPING)
        {
            return SLEEP;
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

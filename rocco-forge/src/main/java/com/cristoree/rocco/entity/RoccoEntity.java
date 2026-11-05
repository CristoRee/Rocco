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
    // animation.rocco.awaken dura 5,8 s (el nacimiento nuevo: bosteza, mira, hace fuerza y sale)
    public static final int AWAKEN_TICKS = 116;
    // animation.rocco.lid_close dura 1,3 s; a los 23 ticks termina de sacudir la cabeza
    public static final int LID_CLOSE_TICKS = 26;
    public static final int LID_FACE_RESTORE_TICK = 23;

    // 4 filas de 9, como un cofre y un tercio.
    public static final int INVENTORY_SIZE = 36;

    private static final int LID_CLOSED = 0;
    private static final int LID_OPENED = 1;
    private static final int LID_CLOSING = 2;

    private static final double WORK_REACH = 2.2D;
    // Un cofre lo alcanza como una persona: parado al lado, hasta una columna de cinco cofres
    // (el de arriba de todo a 4 bloques del piso) y un par para abajo.
    private static final double STORAGE_REACH_SIDE = 2.6D;
    private static final double STORAGE_REACH_UP = 4.5D;
    private static final double STORAGE_REACH_DOWN = 2.5D;
    // GeckoLib tarda esto en pasar de una animacion a la siguiente (la transicion del
    // controlador "main"). Los sonidos y efectos salen del servidor, asi que se corren estos
    // ticks para caer justo con el fotograma y no un poquito antes.
    static final int ANIM_LAG = 3;
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
    // Volando, en el agua o en el barquito: que animacion de viaje tiene que verse.
    private static final EntityDataAccessor<Integer> DATA_MOTION =
            SynchedEntityData.defineId(RoccoEntity.class, EntityDataSerializers.INT);
    // Pensando la respuesta de la IA (solo despues de un ratito, para que no parpadee).
    private static final EntityDataAccessor<Boolean> DATA_THINKING =
            SynchedEntityData.defineId(RoccoEntity.class, EntityDataSerializers.BOOLEAN);
    // La tapa con el jardincito (modo Chill) en vez de la carne: lo decide la config del servidor.
    private static final EntityDataAccessor<Boolean> DATA_GARDEN_LID =
            SynchedEntityData.defineId(RoccoEntity.class, EntityDataSerializers.BOOLEAN);
    // Que animacion del mate compartido esta haciendo (0 = ninguna). La elige el servidor.
    private static final EntityDataAccessor<Integer> DATA_MATE_STEP =
            SynchedEntityData.defineId(RoccoEntity.class, EntityDataSerializers.INT);
    // La postura de pelea que ve el cliente (guardia, apuntando, escudo): RoccoCombat.STANCE_*.
    private static final EntityDataAccessor<Integer> DATA_COMBAT =
            SynchedEntityData.defineId(RoccoEntity.class, EntityDataSerializers.INT);
    // Cual de las tres poses de la tumba: 0 piecitos, 1 manito, 2 cabecita.
    private static final EntityDataAccessor<Integer> DATA_GRAVE_POSE =
            SynchedEntityData.defineId(RoccoEntity.class, EntityDataSerializers.INT);
    // Lo que flota en el hueso grab: el cofre de ender que se traga o que absorbe.
    private static final EntityDataAccessor<ItemStack> DATA_GRAB =
            SynchedEntityData.defineId(RoccoEntity.class, EntityDataSerializers.ITEM_STACK);

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
    // Las mismas, con el jardincito en vez de la carne (modo Chill).
    private static final RawAnimation LID_OPEN_LOOP_GARDEN = RawAnimation.begin()
            .thenPlay("animation.rocco.lid_open_chill")
            .thenLoop("animation.rocco.lid_dizzy_chill");
    private static final RawAnimation LID_CLOSE_GARDEN = RawAnimation.begin().thenPlay("animation.rocco.lid_close_chill");

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
    private static final RawAnimation DANCE = RawAnimation.begin().thenPlay("animation.rocco.dance");
    private static final RawAnimation CLAP = RawAnimation.begin().thenPlay("animation.rocco.clap");
    private static final RawAnimation CELEBRATE = RawAnimation.begin().thenPlay("animation.rocco.celebrate");
    // Pensar va por estado: dura lo que tarde la IA y se corta cuando llega la respuesta.
    private static final RawAnimation THINK = RawAnimation.begin().thenLoop("animation.rocco.think");

    // Roco Cola, equipo nuevo y el poder del cofre de ender: se disparan como el mate.
    private static final RawAnimation COLA_DRINK = RawAnimation.begin().thenPlay("animation.rocco.cola_drink");
    private static final RawAnimation EQUIP_PROUD = RawAnimation.begin().thenPlay("animation.rocco.equip_proud");
    private static final RawAnimation ENDER_ABSORB = RawAnimation.begin().thenPlay("animation.rocco.ender_absorb");
    private static final RawAnimation ENDER_AWAKEN = RawAnimation.begin().thenPlay("animation.rocco.ender_awaken");
    // Comer y tomar con la cosa en la mano (rico o feo, buena o mala).
    private static final RawAnimation FOOD_EAT = RawAnimation.begin().thenPlay("animation.rocco.food_eat");
    private static final RawAnimation FOOD_EAT_BAD = RawAnimation.begin().thenPlay("animation.rocco.food_eat_bad");
    private static final RawAnimation POTION_DRINK = RawAnimation.begin().thenPlay("animation.rocco.potion_drink");
    private static final RawAnimation POTION_DRINK_BAD = RawAnimation.begin().thenPlay("animation.rocco.potion_drink_bad");
    // Bailes y canto de la musica (ROCCO-V12 y el canto de la v10): bucles por estado, mientras
    // dure la tarea; si la tarea sigue igual, el bucle sigue sin cortes.
    private static final RawAnimation DANCE_FIESTA = RawAnimation.begin().thenLoop("animation.rocco.dance_fiesta");
    private static final RawAnimation DANCE_MENEITO = RawAnimation.begin().thenLoop("animation.rocco.dance_meneito");
    private static final RawAnimation SING_SOFT = RawAnimation.begin().thenLoop("animation.rocco.sing_soft");
    private static final RawAnimation SING_GROOVE = RawAnimation.begin().thenLoop("animation.rocco.sing_groove");
    private static final RawAnimation SING_RAP = RawAnimation.begin().thenLoop("animation.rocco.sing_rap");

    // Pelea: los golpes y los disparos se disparan; la guardia, apuntar y el escudo van por estado.
    private static final RawAnimation ATTACK_PUNCH = RawAnimation.begin().thenPlay("animation.rocco.attack_punch");
    private static final RawAnimation ATTACK_SWING = RawAnimation.begin().thenPlay("animation.rocco.attack_swing");
    private static final RawAnimation ATTACK_CHOP = RawAnimation.begin().thenPlay("animation.rocco.attack_chop");
    private static final RawAnimation ATTACK_THRUST = RawAnimation.begin().thenPlay("animation.rocco.attack_thrust");
    private static final RawAnimation BOW_DRAW = RawAnimation.begin().thenPlay("animation.rocco.bow_draw");
    private static final RawAnimation BOW_RELEASE = RawAnimation.begin().thenPlay("animation.rocco.bow_release");
    private static final RawAnimation CROSSBOW_CHARGE = RawAnimation.begin().thenPlay("animation.rocco.crossbow_charge");
    private static final RawAnimation CROSSBOW_SHOOT = RawAnimation.begin().thenPlay("animation.rocco.crossbow_shoot");
    private static final RawAnimation COMBAT_IDLE = RawAnimation.begin().thenLoop("animation.rocco.combat_idle");
    private static final RawAnimation COMBAT_RUN = RawAnimation.begin().thenLoop("animation.rocco.combat_run");
    private static final RawAnimation BOW_AIM = RawAnimation.begin().thenLoop("animation.rocco.bow_aim");
    private static final RawAnimation CROSSBOW_AIM = RawAnimation.begin().thenLoop("animation.rocco.crossbow_aim");
    private static final RawAnimation SHIELD_BLOCK = RawAnimation.begin().thenLoop("animation.rocco.shield_block");

    // Morirse y la tumba: van por estado (DYING, BURIED, RISING).
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.rocco.death");
    private static final RawAnimation[] GRAVE = {
            RawAnimation.begin().thenLoop("animation.rocco.grave_feet"),
            RawAnimation.begin().thenLoop("animation.rocco.grave_hand"),
            RawAnimation.begin().thenLoop("animation.rocco.grave_head")};
    private static final RawAnimation[] GRAVE_RISE = {
            RawAnimation.begin().thenPlayAndHold("animation.rocco.grave_rise_feet"),
            RawAnimation.begin().thenPlayAndHold("animation.rocco.grave_rise_hand"),
            RawAnimation.begin().thenPlayAndHold("animation.rocco.grave_rise_head")};

    // Enterrado, la caja es chiquita y a ras del monticulo, para poder hacerle clic.
    private static final net.minecraft.world.entity.EntityDimensions BURIED_SIZE =
            net.minecraft.world.entity.EntityDimensions.scalable(1.0F, 0.6F);

    // El servidor no tiene particulas: el cliente cambia este gancho al arrancar.
    public interface ParticleHook
    {
        void spawn(RoccoEntity rocco, String effect, String locator);
    }

    public static ParticleHook PARTICLE_HOOK = (rocco, effect, locator) -> { };
    // Lo mismo para el audio, que solo existe en el cliente: si su voz esta sonando (la boca se
    // mueve con el audio de verdad) y el ritmo de la musica que esta tocando (0 si no se sabe).
    public static java.util.function.Predicate<RoccoEntity> SPEAKING_HOOK = rocco -> false;
    public static java.util.function.ToDoubleFunction<RoccoEntity> BEAT_HOOK = rocco -> 0.0D;

    // Lo que esta sonando desde Rocco (solo en el servidor; ver RoccoMusic).
    @Nullable
    private com.cristoree.rocco.music.RoccoMusic.Session music;
    // Para no buscar tocadiscos todos los ticks.
    @Nullable
    private BlockPos jukebox;
    @Nullable
    private BlockPos danceFacing;
    private int musicLoops;

    // Las estables van por estado, no disparadas, porque tienen que mantenerse en bucle.
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop("animation.rocco.sit");
    private static final RawAnimation SLEEP = RawAnimation.begin().thenLoop("animation.rocco.sleep");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final RoccoContainer inventory = new RoccoContainer();
    private final RoccoTravel journey = new RoccoTravel(this);
    private final MateShare share = new MateShare(this);
    private final RoccoLife life = new RoccoLife(this);
    private final com.cristoree.rocco.RoccoSurprises.State surprises = new com.cristoree.rocco.RoccoSurprises.State();
    private final Deque<Job> jobs = new ArrayDeque<>();

    private RoccoBrain brain;
    private int awakenTicks;

    // El arma de verdad mientras la mano muestra otra cosa (la herramienta con la que trabaja).
    @Nullable
    private ItemStack stashedWeapon;
    // A quien puede atacar (RoccoCombat.HOSTILE | PEACEFUL | PLAYERS). -1 = todavia no se eligio.
    private int attackTargets = -1;
    private boolean fighting;
    private int combatCalmTicks;
    // Inmortal: despues de un golpe que lo habria matado, se aparta un rato sin pelear.
    private int immortalRetreatTicks;
    private boolean colaRequested;
    // Lo que le ordenaste atacar ("ataca al creeper"), aunque no este en sus casillas.
    @Nullable
    private UUID orderedTarget;
    private final java.util.Map<String, Long> onceLines = new java.util.HashMap<>();
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
    private int approachLimit = WORK_TIMEOUT_TICKS;
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
        // La armadura y el arma las suelta el mismo (dropEverything), para que no se dupliquen.
        for (EquipmentSlot slot : RoccoGear.ARMOR_SLOTS)
        {
            setDropChance(slot, 0.0F);
        }
        setCanPickUpLoot(false);
    }

    private static final double PUNCH_DAMAGE = 2.0D;

    public static AttributeSupplier.Builder createAttributes()
    {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                // A piñas pega 2 (el jugador 1, un lobo 4) y las armas suman lo suyo (con sus encantamientos).
                .add(Attributes.ATTACK_DAMAGE, PUNCH_DAMAGE)
                .add(Attributes.ATTACK_SPEED, 4.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0D)
                // Caminando por el agua (para salir, o hasta que se pone el patito) no se arrastra.
                .add(net.minecraftforge.common.ForgeMod.SWIM_SPEED.get(), 2.5D);
    }

    @Override
    protected void registerGoals()
    {
        // Sin el patito no se hunde (salta para flotar); con el patito puesto lo sostiene RoccoTravel.
        this.goalSelector.addGoal(1, new FloatGoal(this)
        {
            @Override
            public boolean canUse()
            {
                return !isTravelling() && super.canUse();
            }

            @Override
            public boolean canContinueToUse()
            {
                return !isTravelling() && super.canContinueToUse();
            }
        });
        // El navegador ya sabe pasar por puertas de madera (setCanOpenDoors), pero quien las abre
        // de verdad es este objetivo; sin el, se quedaba empujando la puerta cerrada hasta
        // teletransportarse. La vuelve a cerrar al pasar, como los aldeanos.
        this.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.OpenDoorGoal(this, true));
        // Pelear le gana a seguirte y a pasear; a quien atacar lo decide PickTarget.
        this.goalSelector.addGoal(1, new RoccoCombat.Fight(this));
        this.targetSelector.addGoal(1, new RoccoCombat.PickTarget(this));
        this.goalSelector.addGoal(2, new RoccoFollowGoal(this));
        // Pasea cerca del jugador aunque lo este siguiendo (dentro de behavior.wanderRadius).
        this.goalSelector.addGoal(3, new RoccoWanderGoal(this));
    }

    // Atado a un poste no aparece solo al lado tuyo: lo traes vos (/rocco llamar lo desata).
    public boolean mayAutoTeleport()
    {
        return getRoccoState() == RoccoState.ACTIVE && (!isLeashed() || !RoccoCommonConfig.LEASH_BLOCKS_TELEPORT.get());
    }

    public boolean canWander()
    {
        return getRoccoState() == RoccoState.ACTIVE && isIdleForWandering() && !isGivingSpace() && !isTravelling();
    }

    // ---------- dar espacio mientras el jugador construye ----------

    private int giveSpaceTicks;

    // Un rato sin pasear cerca y siguiendo un poco mas lejos, para no meterse en la construccion.
    public void giveSpace(int ticks)
    {
        this.giveSpaceTicks = Math.max(this.giveSpaceTicks, ticks);
    }

    public boolean isGivingSpace()
    {
        return this.giveSpaceTicks > 0;
    }

    // El jugador quiere poner un bloque justo donde esta Rocco: se corre a un lugar libre de al
    // lado (lejos del bloque y sin taparle la vista) antes de que el juego compruebe el lugar, asi
    // el bloque entra en el mismo clic. Devuelve false si no encontro donde correrse.
    public boolean stepAsideFrom(BlockPos blocked, Player player)
    {
        if (!(level() instanceof ServerLevel serverLevel) || isPlantVisible())
        {
            return false;
        }
        net.minecraft.world.phys.AABB forbidden = new net.minecraft.world.phys.AABB(blocked);
        net.minecraft.world.phys.Vec3 look = player.getLookAngle();
        BlockPos here = blockPosition();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (BlockPos candidate : BlockPos.betweenClosed(here.offset(-2, -1, -2), here.offset(2, 1, 2)))
        {
            net.minecraft.world.phys.Vec3 center = new net.minecraft.world.phys.Vec3(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
            if (getDimensions(getPose()).makeBoundingBox(center).intersects(forbidden)
                    || !RoccoFollowGoal.isSafe(serverLevel, candidate))
            {
                continue;
            }
            // Cerca de donde estaba, a la misma altura, y no adelante del jugador (ahi esta construyendo).
            double dx = center.x - player.getX();
            double dz = center.z - player.getZ();
            double ahead = (dx * look.x + dz * look.z) / Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
            double score = candidate.distSqr(here) + Math.abs(candidate.getY() - here.getY()) * 2.0D + (ahead > 0.3D ? 3.0D : 0.0D);
            if (score < bestScore)
            {
                bestScore = score;
                best = candidate.immutable();
            }
        }
        if (best == null)
        {
            return false;
        }
        getNavigation().stop();
        moveTo(best.getX() + 0.5D, best.getY(), best.getZ() + 0.5D, getYRot(), getXRot());
        giveSpace(300);
        return true;
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
        this.entityData.define(DATA_MOTION, RoccoMotion.NONE.ordinal());
        this.entityData.define(DATA_THINKING, false);
        this.entityData.define(DATA_GARDEN_LID, false);
        this.entityData.define(DATA_MATE_STEP, 0);
        this.entityData.define(DATA_COMBAT, RoccoCombat.STANCE_NONE);
        this.entityData.define(DATA_GRAVE_POSE, 0);
        this.entityData.define(DATA_GRAB, ItemStack.EMPTY);
    }

    // ---------- volando, en el agua y en el barquito (RoccoTravel) ----------

    public RoccoMotion getMotion()
    {
        return RoccoMotion.byId(this.entityData.get(DATA_MOTION));
    }

    void setMotion(RoccoMotion motion)
    {
        this.entityData.set(DATA_MOTION, motion.ordinal());
    }

    // Esta acompañandote de otra forma que caminando (volando, nadando, en su barquito).
    public boolean isTravelling()
    {
        return getMotion() != RoccoMotion.NONE;
    }

    // Puede seguirte caminando (o nadando con el patito, que usa la misma navegacion).
    public boolean canWalkFollow()
    {
        RoccoMotion motion = getMotion();
        return motion == RoccoMotion.NONE || motion == RoccoMotion.FLOAT_IDLE || motion == RoccoMotion.FLOAT_SWIM;
    }

    // Vuelve a caminar ya mismo: lo usan los teletransportes y el cambio de dimension.
    public void stopTravelling()
    {
        this.journey.reset();
    }

    // Las caras que marcan las animaciones de viaje. Si la IA eligio una hace un momento, manda.
    void setMotionFace(RoccoExpression face)
    {
        if (this.faceOverrideTicks <= 0 && !getRoccoPose().isAsleep())
        {
            setExpression(face);
        }
    }

    // Antes de despegar, bucear o subirse al barquito: deja lo que estaba haciendo y se para.
    void prepareForTravel()
    {
        this.share.stop(true);
        interruptActivity();
        if (this.task != RoccoTask.NONE)
        {
            stopTriggeredAnimation("main", null);
            finishJob();
        }
        if (this.errand != null && !staysForWork())
        {
            cancelErrand();
        }
        this.jobs.clear();
        if (getRoccoPose() != RoccoPose.STANDING)
        {
            if (getRoccoPose().isAsleep())
            {
                restoreFace();
            }
            setRoccoPose(RoccoPose.STANDING);
        }
        clearAutoRest();
        getNavigation().stop();
    }

    // Quedo cuidando la huerta: ahi se queda aunque te vayas volando.
    boolean staysForWork()
    {
        return this.errand instanceof FarmErrand farm && farm.persistent();
    }

    // Despues de volar o de un rescate, si cae no se lastima.
    private int safeFallTicks;

    void allowSafeFall()
    {
        this.safeFallTicks = 200;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source)
    {
        if (this.safeFallTicks > 0 || isTravelling())
        {
            return false;
        }
        return super.causeFallDamage(distance, multiplier, source);
    }

    ServerLevel serverLevel()
    {
        return (ServerLevel) level();
    }

    // Volando, buceando o en el barquito lo mueve RoccoTravel: la fisica de caminar no toca nada.
    @Override
    public void travel(net.minecraft.world.phys.Vec3 input)
    {
        if (getMotion().steered())
        {
            return;
        }
        super.travel(input);
    }

    // ---------- pensando la respuesta ----------

    // Lo que tarda en mostrarse (si la respuesta llega antes, no se ve nada) y cuando suena el "mmm".
    private static final int THINK_SHOW_TICKS = 8;
    private static final int THINK_SOUND_TICKS = 20;
    private static final int THINK_GIVE_UP_TICKS = 20 * 90;
    private int thinkTicks = -1;
    private boolean thinkShown;
    private RoccoExpression faceBeforeThinking = RoccoExpression.HAPPY;

    public void beginThinking()
    {
        if (this.thinkTicks < 0)
        {
            this.thinkTicks = 0;
            this.thinkShown = false;
        }
    }

    public void endThinking()
    {
        if (this.thinkShown && getExpression() == RoccoExpression.THINK)
        {
            setExpression(this.faceBeforeThinking);
        }
        this.entityData.set(DATA_THINKING, false);
        this.thinkTicks = -1;
        this.thinkShown = false;
    }

    public boolean isThinking()
    {
        return this.entityData.get(DATA_THINKING);
    }

    private void tickThinking()
    {
        if (this.thinkTicks < 0)
        {
            return;
        }
        this.thinkTicks++;
        if (this.thinkTicks == THINK_SHOW_TICKS)
        {
            // Con algo en marcha (volando, tomando mate, durmiendo) no se pone a pensar con la
            // mano en el menton: como mucho la cara, y dormido ni eso.
            boolean faceAllowed = getRoccoPose() == RoccoPose.STANDING && this.task == RoccoTask.NONE
                    && this.entityData.get(DATA_LID) == LID_CLOSED && !getMotion().scriptedFace();
            if (faceAllowed)
            {
                this.faceBeforeThinking = calmFace();
                setExpression(RoccoExpression.THINK);
                this.thinkShown = true;
                boolean free = !isTravelling() && this.errand == null && this.jobs.isEmpty();
                this.entityData.set(DATA_THINKING, free);
            }
        }
        if (this.thinkTicks == THINK_SOUND_TICKS && this.thinkShown)
        {
            playRoccoSound(ModSounds.THINK.get(), 0.3F);
        }
        if (this.thinkTicks > THINK_GIVE_UP_TICKS)
        {
            endThinking();
        }
    }

    // ---------- aplaudir, festejar y bailar ----------

    // Uno cada tanto, aunque pasen varias cosas buenas juntas.
    private static final int CHEER_COOLDOWN_TICKS = 200;
    private long lastCheerTick = -1_000_000L;
    private long lastDanceTick = -1_000_000L;

    // Aplaude, festeja o baila ahi donde esta, mirando al jugador.
    public void perform(RoccoTask kind, @Nullable Player toward)
    {
        interruptActivity();
        if (toward != null)
        {
            lookAtPlayer(toward);
        }
        this.autoRestTicks = 0;
        queueJob(Job.custom(kind, blockPosition(), ItemStack.EMPTY, level -> { }, null).reaching(64.0D));
        if (kind == RoccoTask.DANCING)
        {
            this.lastDanceTick = level().getGameTime();
        }
        else
        {
            this.lastCheerTick = level().getGameTime();
        }
    }

    // Para los logros del jugador: cerca, libre y sin haber festejado recien.
    public boolean canCheer(Player player)
    {
        return RoccoCommonConfig.CHEER_ENABLED.get()
                && getRoccoState() == RoccoState.ACTIVE
                && player.level() == level()
                && distanceToSqr(player) < 16.0D * 16.0D
                && !isTravelling()
                && !getRoccoPose().isAsleep()
                && this.task == RoccoTask.NONE
                && this.jobs.isEmpty()
                && this.entityData.get(DATA_LID) == LID_CLOSED
                && level().getGameTime() - this.lastCheerTick > CHEER_COOLDOWN_TICKS;
    }

    // ---------- musica: cantar y bailar ----------

    @Nullable
    public com.cristoree.rocco.music.RoccoMusic.Session music()
    {
        return this.music;
    }

    public void setMusic(@Nullable com.cristoree.rocco.music.RoccoMusic.Session session)
    {
        this.music = session;
    }

    // Se corto su musica: deja de cantar en el acto (cantar sin musica queda raro). Los bailes
    // terminan la vuelta que estaban haciendo.
    public void stopSinging()
    {
        if (this.task.singing())
        {
            interruptActivity();
        }
    }

    // "Bailá": uno de los dos bailes nuevos, dos vueltas (16 s), o hasta que le digas que pare.
    public void danceOnRequest(@Nullable Player toward)
    {
        this.danceFacing = null;
        this.musicLoops = 0;
        perform(com.cristoree.rocco.music.RoccoMusic.randomDance(this.random, false), toward);
    }

    private boolean ownMusicPlaying()
    {
        return this.music != null && !this.music.paused() && level().getGameTime() >= this.music.startTick()
                && RoccoCommonConfig.MUSIC_SING.get();
    }

    private boolean musicNearby()
    {
        return ownMusicPlaying() || this.jukebox != null;
    }

    // Puede ponerse a cantar o bailar: en tierra, de pie, sin pelear ni bichos cerca, y si te sigue,
    // con vos cerca (si te estas yendo, primero te alcanza).
    private boolean canPerformMusic()
    {
        if (getRoccoState() != RoccoState.ACTIVE || isTravelling() || isInWater() || getRoccoPose() != RoccoPose.STANDING
                || this.entityData.get(DATA_LID) != LID_CLOSED || this.entityData.get(DATA_COMBAT) != RoccoCombat.STANCE_NONE
                || this.share.active() || hostileNearby(10.0D) || this.errand != null)
        {
            return false;
        }
        ServerPlayer owner = owner();
        return !this.following || owner == null || owner.level() != level() || distanceToSqr(owner) < 10.0D * 10.0D;
    }

    // Cada medio segundo: con su musica sonando canta (y a veces baila); con un disco sonando cerca,
    // va al tocadiscos y baila.
    private void tickMusic()
    {
        if (this.tickCount % 10 != 0 || !(level() instanceof ServerLevel serverLevel))
        {
            return;
        }
        if (this.tickCount % 20 == 0)
        {
            this.jukebox = RoccoCommonConfig.MUSIC_DANCE_JUKEBOX.get()
                    ? com.cristoree.rocco.music.RoccoMusic.playingJukebox(serverLevel, blockPosition(), 12.0D) : null;
        }

        // Se termino el disco (o lo sacaron) a mitad de un baile: termina la vuelta y para.
        if ((this.task == RoccoTask.DANCE_FIESTA || this.task == RoccoTask.DANCE_MENEITO) && this.musicLoops > 0
                && !musicNearby() && Math.floorMod(this.taskTicks - ANIM_LAG, 160) < 10)
        {
            interruptActivity();
            return;
        }

        if (this.task != RoccoTask.NONE || !this.jobs.isEmpty() || !isFreeTime() || !canPerformMusic())
        {
            return;
        }
        if (ownMusicPlaying())
        {
            this.danceFacing = null;
            this.musicLoops = 1;
            startMusicLoop(com.cristoree.rocco.music.RoccoMusic.nextPerformance(null, this.music, this.random),
                    blockPosition(), 64.0D);
        }
        else if (this.jukebox != null)
        {
            // Te sigue: va hasta el tocadiscos. Le dijiste que espere: baila ahi donde esta.
            this.danceFacing = this.jukebox;
            this.musicLoops = 1;
            startMusicLoop(com.cristoree.rocco.music.RoccoMusic.randomDance(this.random, true),
                    this.following ? this.jukebox : blockPosition(), this.following ? 3.0D : 64.0D);
        }
    }

    private void startMusicLoop(RoccoTask kind, BlockPos target, double reach)
    {
        this.autoRestTicks = 0;
        queueJob(Job.custom(kind, target, ItemStack.EMPTY, level -> { }, null).reaching(reach));
        this.lastDanceTick = level().getGameTime();
    }

    // Al terminar una vuelta: si sigue sonando algo, la proxima (la misma tarea sigue el bucle sin
    // cortes; con un disco cambia de baile cada dos vueltas).
    @Nullable
    private RoccoTask nextMusicLoop(RoccoTask done)
    {
        if (this.musicLoops <= 0 || !musicNearby() || !canPerformMusic())
        {
            this.musicLoops = 0;
            return null;
        }
        this.musicLoops++;
        if (ownMusicPlaying())
        {
            return com.cristoree.rocco.music.RoccoMusic.nextPerformance(done, this.music, this.random);
        }
        return com.cristoree.rocco.music.RoccoMusic.randomDance(this.random, true);
    }

    // ---------- reacciones sin IA ----------

    private final java.util.Map<String, Long> lastReaction = new java.util.HashMap<>();

    // Sin IA, comenta lo que pasa (despegar, meterse al agua, subirse al barquito, que te mueras).
    // Con IA no: los comentarios los inventa ella. Cada cosa como mucho cada dos minutos, y no
    // siempre, para que no se vuelva pesado.
    public void react(String category)
    {
        if (!(level() instanceof ServerLevel) || RoccoConfig.aiConfigured() || !RoccoConfig.chatReady())
        {
            return;
        }
        long now = level().getGameTime();
        Long last = this.lastReaction.get(category);
        if ((last != null && now - last < 2400L) || this.random.nextFloat() > 0.6F)
        {
            return;
        }
        this.lastReaction.put(category, now);
        sayLine(category);
    }

    // Corta el tarareo del baile para los que lo estaban escuchando.
    private void stopHumming()
    {
        if (level() instanceof ServerLevel serverLevel)
        {
            var packet = new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                    ModSounds.HUM.getId(), getSoundSource());
            for (ServerPlayer player : serverLevel.players())
            {
                if (player.distanceToSqr(this) < 48.0D * 48.0D)
                {
                    player.connection.send(packet);
                }
            }
        }
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

    // El jardincito de la tapa (modo Chill) solo existe mientras la tapa esta abierta o cerrandose.
    public boolean showsGarden()
    {
        return this.entityData.get(DATA_GARDEN_LID) && this.entityData.get(DATA_LID) != LID_CLOSED;
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

    // Regla 4 de la spec: con la boca grande abierta no puede hablar, o se encimarian mouth_talk y
    // la boca de la animacion (comiendo, tragando, con la bombilla o la botella). Vale en los dos
    // lados: el cliente lo mira para mover la boca con el audio de su voz.
    public boolean mouthBusy()
    {
        RoccoTask active = getActiveTask();
        return active == RoccoTask.EATING || active == RoccoTask.SWALLOWING || active == RoccoTask.MATE
                || active == RoccoTask.COLA || active.consuming();
    }

    public void talkFor(int ticks)
    {
        if (mouthBusy() || this.share.drinking())
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
                // Guarda la cara que tenia; el bostezo y la de dormido las pone tickPose en su
                // momento. La de dormido dura hasta que se despierte, no un rato fijo.
                RoccoExpression current = getExpression();
                if (current.selectableByAi())
                {
                    this.faceBeforeOverride = current;
                }
                this.faceOverrideTicks = 0;
            }
            case WAKING_UP -> triggerAnim("main", "wake_up");
            default -> { }
        }

        if (pose.isResting())
        {
            getNavigation().stop();
        }
    }

    public void sitDown()
    {
        if (getRoccoPose() == RoccoPose.STANDING && !isTravelling())
        {
            setRoccoPose(RoccoPose.SITTING_DOWN);
        }
    }

    public void goToSleep()
    {
        RoccoPose pose = getRoccoPose();
        if ((pose == RoccoPose.STANDING || pose == RoccoPose.SITTING) && !isTravelling())
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
        return !this.share.active()
                && !isWorking()
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

    // Los primeros pasos del encargo, ya mismo y no en el proximo tick: asi lo que tiene para decir
    // al arrancar ("me faltan palos", "voy al cofre") se sabe antes de contestarle al jugador, y
    // sale en una sola respuesta. Para cuando el encargo pone un trabajo en la cola o termina.
    public void primeErrand()
    {
        for (int i = 0; i < 3 && this.errand != null && this.jobs.isEmpty() && this.task == RoccoTask.NONE; i++)
        {
            Errand running = this.errand;
            Errand.Status status;
            try
            {
                status = running.tick();
            }
            catch (RuntimeException e)
            {
                com.cristoree.rocco.RoccoMod.LOGGER.error("[Rocco] El encargo '{}' fallo", running.describe(), e);
                status = Errand.Status.FAILED;
            }
            if (status != Errand.Status.RUNNING && this.errand == running)
            {
                this.errand = null;
            }
        }
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
        if (this.task == RoccoTask.NONE)
        {
            restoreWeapon();
        }
    }

    // Lo que el jugador dice mientras hay un encargo ("busca en el cofre"). true si lo uso.
    public boolean signalErrand(String signal)
    {
        return this.errand != null && this.errand.signal(signal);
    }

    // Ocupado de verdad: el seguir al jugador lo sacaria de lo que esta haciendo.
    public boolean isBusy()
    {
        return this.share.active()
                || isWorking()
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
        stashWeapon();
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
        this.share.stop(false);
        String animation = switch (this.task)
        {
            case MATE -> "mate";
            case ENCHANTING -> "enchant";
            case DANCING -> "dance";
            case CLAPPING -> "clap";
            case CELEBRATING -> "celebrate";
            case COLA -> "cola_drink";
            case EQUIP_PROUD -> "equip_proud";
            case ENDER_ABSORB -> "ender_absorb";
            case ENDER_AWAKEN -> "ender_awaken";
            case FOOD_EAT -> "food_eat";
            case FOOD_EAT_BAD -> "food_eat_bad";
            case POTION_DRINK -> "potion_drink";
            case POTION_DRINK_BAD -> "potion_drink_bad";
            // Van por estado: al cambiar la tarea, la animacion se va sola.
            case DANCE_FIESTA, DANCE_MENEITO, SING_SOFT, SING_GROOVE, SING_RAP -> "music";
            default -> null;
        };
        if (animation == null)
        {
            return;
        }
        this.musicLoops = 0;
        // El cofre de ender ya entro: aunque se corte la animacion, el poder se activa igual.
        if (this.task == RoccoTask.ENDER_ABSORB || this.task == RoccoTask.ENDER_AWAKEN)
        {
            this.jobs.removeIf(job -> job.task() == RoccoTask.ENDER_AWAKEN);
            this.life.grantEnderPocket();
        }
        Job interrupted = this.currentJob;
        stopTriggeredAnimation("main", animation);
        if (this.task == RoccoTask.DANCING || this.task == RoccoTask.DANCE_FIESTA || this.task == RoccoTask.DANCE_MENEITO)
        {
            stopHumming();
        }
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

        if (job.task() == RoccoTask.SWALLOWING || job.task() == RoccoTask.ENDER_ABSORB || job.task().consuming())
        {
            // Lo que traga o absorbe flota en el hueso grab. La comida y la pocion se dibujan en el
            // hueso held (ROCCO-V12): el arma queda en su mano, sin dibujarse mientras come.
            this.entityData.set(DATA_GRAB, job.shown().copy());
        }
        else
        {
            stashWeapon();
            setItemSlot(EquipmentSlot.MAINHAND, shown);
        }
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
            case DANCE_FIESTA, DANCE_MENEITO, SING_SOFT, SING_GROOVE, SING_RAP ->
            {
                this.faceBeforeSwallow = calmFace();
                setExpression(job.task().singing() ? RoccoExpression.SING
                        : job.task() == RoccoTask.DANCE_MENEITO ? RoccoExpression.SMUG : RoccoExpression.HAPPY);
                // Bailando para un disco: mirando al tocadiscos.
                if (this.danceFacing != null)
                {
                    lookAtBlock(this.danceFacing);
                }
            }
            case DANCING ->
            {
                this.faceBeforeSwallow = calmFace();
                setExpression(RoccoExpression.HAPPY);
                if (this.danceFacing != null && this.musicLoops > 0)
                {
                    lookAtBlock(this.danceFacing);
                }
                triggerAnim("main", "dance");
            }
            case CLAPPING ->
            {
                this.faceBeforeSwallow = calmFace();
                setExpression(RoccoExpression.HAPPY);
                triggerAnim("main", "clap");
            }
            case CELEBRATING ->
            {
                this.faceBeforeSwallow = calmFace();
                setExpression(RoccoExpression.CHEER);
                triggerAnim("main", "celebrate");
            }
            case COLA ->
            {
                this.faceBeforeSwallow = calmFace();
                setExpression(RoccoExpression.HAPPY);
                triggerAnim("main", "cola_drink");
                this.life.drankCola();
            }
            case EQUIP_PROUD ->
            {
                this.faceBeforeSwallow = calmFace();
                setExpression(RoccoExpression.WOW);
                triggerAnim("main", "equip_proud");
            }
            case ENDER_ABSORB ->
            {
                this.faceBeforeSwallow = calmFace();
                setExpression(RoccoExpression.THINK);
                triggerAnim("main", "ender_absorb");
            }
            case ENDER_AWAKEN ->
            {
                this.faceBeforeSwallow = calmFace();
                setExpression(RoccoExpression.WOW);
                triggerAnim("main", "ender_awaken");
                this.life.startAwakening();
            }
            case FOOD_EAT, FOOD_EAT_BAD, POTION_DRINK, POTION_DRINK_BAD ->
            {
                this.faceBeforeSwallow = calmFace();
                boolean potion = job.task() == RoccoTask.POTION_DRINK || job.task() == RoccoTask.POTION_DRINK_BAD;
                setExpression(potion ? RoccoExpression.FOCUS : RoccoExpression.HAPPY);
                triggerAnim("main", switch (job.task())
                {
                    case FOOD_EAT -> "food_eat";
                    case FOOD_EAT_BAD -> "food_eat_bad";
                    case POTION_DRINK -> "potion_drink";
                    default -> "potion_drink_bad";
                });
            }
            default -> { }
        }

        // Con la boca llena (o con la bombilla) no habla: asi mouth_talk queda escondido y no se
        // encima con la boca de la animacion.
        if (job.task() == RoccoTask.EATING || job.task() == RoccoTask.SWALLOWING || job.task() == RoccoTask.MATE
                || job.task() == RoccoTask.COLA || job.task().consuming())
        {
            setTalking(false);
            this.talkTicks = 0;
        }
    }

    private static boolean isPerformance(RoccoTask task)
    {
        return task == RoccoTask.MATE || task == RoccoTask.DANCING || task == RoccoTask.CLAPPING
                || task == RoccoTask.CELEBRATING || task == RoccoTask.COLA || task == RoccoTask.EQUIP_PROUD
                || task == RoccoTask.ENDER_ABSORB || task == RoccoTask.ENDER_AWAKEN || task.consuming() || task.musical();
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
        if (this.task.musical())
        {
            // Los bailes de la v12 (segundos x 20, en cada vuelta de 8 s). Cantando: la cara de cantar.
            int beat = Math.floorMod(t, 160);
            RoccoExpression face = switch (this.task)
            {
                case DANCE_FIESTA -> beat == 0 ? RoccoExpression.HAPPY : beat == 40 ? RoccoExpression.BLISS
                        : beat == 80 ? RoccoExpression.WOW : beat == 100 ? RoccoExpression.CHEER
                        : beat == 120 ? RoccoExpression.HUM : beat == 150 ? RoccoExpression.HAPPY : null;
                case DANCE_MENEITO -> beat == 0 ? RoccoExpression.SMUG : beat == 40 ? RoccoExpression.BLISS
                        : beat == 80 ? RoccoExpression.CHEER : beat == 120 ? RoccoExpression.HAPPY
                        : beat == 150 ? RoccoExpression.SMUG : null;
                default -> getExpression() != RoccoExpression.SING ? RoccoExpression.SING : null;
            };
            if (face != null && t >= 0)
            {
                setExpression(face);
            }
            return;
        }
        if (this.task == RoccoTask.MATE)
        {
            boolean sipping = (t >= 121 && t <= 327) || (t >= 410 && t <= 567);
            RoccoExpression wanted = sipping ? RoccoExpression.BLISS : RoccoExpression.HAPPY;
            if (getExpression() != wanted)
            {
                setExpression(wanted);
            }
        }
        else if (this.task == RoccoTask.DANCING || this.task == RoccoTask.CLAPPING || this.task == RoccoTask.CELEBRATING)
        {
            // Ticks de ROCCO-PENSAR-FESTEJAR-BAILAR.md.
            RoccoExpression face = switch (this.task)
            {
                case DANCING -> t == 7 || t == 57 ? RoccoExpression.HUM : t == 51 || t == 117 ? RoccoExpression.HAPPY : null;
                case CLAPPING -> t == 2 ? RoccoExpression.CHEER : t == 25 ? RoccoExpression.HAPPY : null;
                default -> t == 47 ? RoccoExpression.HAPPY : null;
            };
            if (face != null)
            {
                setExpression(face);
            }
        }
        else if (this.task == RoccoTask.COLA)
        {
            RoccoExpression face = faceAt(t, COLA_FACE_TICKS, COLA_FACES);
            if (face != null)
            {
                setExpression(face);
            }
        }
        else if (this.task.consuming())
        {
            // ROCCO-V12-COMER-BAILES-SNORKEL.md (segundos x 20).
            RoccoExpression face = switch (this.task)
            {
                case FOOD_EAT -> t == 6 ? RoccoExpression.WOW : t == 9 ? RoccoExpression.BLISS : t == 40 ? RoccoExpression.HAPPY
                        : t == 43 ? RoccoExpression.BLISS : null;
                case FOOD_EAT_BAD -> t == 6 ? RoccoExpression.WOW : t == 9 ? RoccoExpression.BLISS : t == 32 ? RoccoExpression.FOCUS
                        : t == 41 ? RoccoExpression.WINDY : t == 49 ? RoccoExpression.SAD : t == 59 ? RoccoExpression.HAPPY : null;
                case POTION_DRINK -> t == 12 ? RoccoExpression.BLISS : t == 32 ? RoccoExpression.HAPPY : t == 38 ? RoccoExpression.WOW
                        : t == 44 ? RoccoExpression.CHEER : t == 49 ? RoccoExpression.HAPPY : null;
                default -> t == 12 ? RoccoExpression.BLISS : t == 32 ? RoccoExpression.FOCUS : t == 38 ? RoccoExpression.WINDY
                        : t == 45 ? RoccoExpression.SAD : t == 55 ? RoccoExpression.HAPPY : null;
            };
            if (face != null)
            {
                setExpression(face);
            }
        }
        else if (this.task == RoccoTask.EQUIP_PROUD)
        {
            RoccoExpression face = t == 15 ? RoccoExpression.HAPPY : t == 25 ? RoccoExpression.SMUG
                    : t == 36 ? RoccoExpression.HAPPY : null;
            if (face != null)
            {
                setExpression(face);
            }
        }
        else if (this.task == RoccoTask.ENDER_ABSORB)
        {
            if (t == 7)
            {
                setExpression(RoccoExpression.WOW);
            }
        }
        else if (this.task == RoccoTask.ENDER_AWAKEN)
        {
            RoccoExpression face = switch (t)
            {
                case 31 -> RoccoExpression.FOCUS;
                case 40 -> RoccoExpression.CHEER;
                case 62 -> RoccoExpression.HAPPY;
                case 67 -> RoccoExpression.CHEER;
                case 80 -> RoccoExpression.HAPPY;
                default -> null;
            };
            if (face != null)
            {
                setExpression(face);
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

    // Las migas en cada mordida (como el jugador), la comida que desaparece cuando ya se la comio
    // (la animacion la achica) y la pocion que se vuelve un frasco vacio.
    private void tickConsumingHand(int t)
    {
        // Solo al comer o tomar una pocion (con el mate o la cola la botella aparecia en la mano).
        if (this.currentJob == null || !this.task.consuming() || !(level() instanceof ServerLevel serverLevel))
        {
            return;
        }
        boolean food = this.task == RoccoTask.FOOD_EAT || this.task == RoccoTask.FOOD_EAT_BAD;
        if (food && (t == 10 || t == 18 || t == 26))
        {
            net.minecraft.world.phys.Vec3 look = net.minecraft.world.phys.Vec3.directionFromRotation(0.0F, this.yBodyRot);
            serverLevel.sendParticles(new net.minecraft.core.particles.ItemParticleOption(
                            net.minecraft.core.particles.ParticleTypes.ITEM, this.currentJob.shown()),
                    getX() + look.x * 0.3D, getY() + getBbHeight() * 0.62D, getZ() + look.z * 0.3D,
                    5 + this.random.nextInt(2), 0.06D, 0.04D, 0.06D, 0.05D);
        }
        else if (food && t == 27)
        {
            // Ya se la comio: la animacion la achico hasta 0.
            this.entityData.set(DATA_GRAB, ItemStack.EMPTY);
        }
        else if (!food && t == 36)
        {
            // Le queda el frasco vacio en la mano hasta el final.
            this.entityData.set(DATA_GRAB, new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE));
        }
    }

    // La linea de tiempo de caras de cola_drink (ROCCO-COLA-COMBATE-ARMADURAS-CANTO.md).
    private static final int[] COLA_FACE_TICKS = {35, 47, 52, 59, 72, 89, 101, 109, 119, 126, 152, 159, 173, 181, 224,
            229, 250, 252, 256, 266};
    private static final RoccoExpression[] COLA_FACES = {RoccoExpression.CHEER, RoccoExpression.HAPPY,
            RoccoExpression.SERIOUS, RoccoExpression.WOW, RoccoExpression.HAPPY, RoccoExpression.BLISS, RoccoExpression.HAPPY,
            RoccoExpression.BLISS, RoccoExpression.HAPPY, RoccoExpression.BLISS, RoccoExpression.HAPPY, RoccoExpression.REFRESH,
            RoccoExpression.HAPPY, RoccoExpression.BLISS, RoccoExpression.HAPPY, RoccoExpression.REFRESH, RoccoExpression.HAPPY,
            RoccoExpression.WOW, RoccoExpression.SMUG, RoccoExpression.HAPPY};

    @Nullable
    private static RoccoExpression faceAt(int tick, int[] ticks, RoccoExpression[] faces)
    {
        for (int i = 0; i < ticks.length; i++)
        {
            if (ticks[i] == tick)
            {
                return faces[i];
            }
        }
        return null;
    }

    private ItemStack previewOf(BlockPos pos)
    {
        return level() instanceof ServerLevel serverLevel ? Crops.preview(serverLevel, pos) : ItemStack.EMPTY;
    }

    private void finishJob()
    {
        if (this.task == RoccoTask.SWALLOWING || this.task == RoccoTask.MATE || this.task == RoccoTask.ENCHANTING
                || this.task == RoccoTask.DANCING || this.task == RoccoTask.CLAPPING || this.task == RoccoTask.CELEBRATING
                || this.task == RoccoTask.COLA || this.task == RoccoTask.EQUIP_PROUD || this.task == RoccoTask.ENDER_ABSORB
                || this.task == RoccoTask.ENDER_AWAKEN || this.task.consuming() || this.task.musical())
        {
            setExpression(this.faceBeforeSwallow);
        }
        this.entityData.set(DATA_GRAB, ItemStack.EMPTY);
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
        restoreWeapon();
    }

    // Termino la animacion entera (no se corto): lo que dice despues.
    private void jobCompleted(RoccoTask done)
    {
        if (done == RoccoTask.COLA && (this.colaRequested || this.random.nextFloat() < 0.4F))
        {
            sayLine("cola_after");
        }
        else if (done == RoccoTask.ENDER_AWAKEN)
        {
            this.life.grantEnderPocket();
            sayLine("ender_unlock");
        }
        this.colaRequested = false;
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

    // La comida le da saciedad (se cura mas rapido un rato) y la manzana dorada o la pocion, su
    // efecto. Si mientras masticaba le sacaste eso del inventario, no pasa nada.
    // El sonido no va aca: chew y gulp (o el trago) ya suenan en sus ticks de la animacion.
    private void eatFromInventory()
    {
        ItemStack food = this.currentJob.shown();
        if (food.isEmpty() || !consumeFromInventory(food))
        {
            return;
        }
        this.life.consume(food);
    }

    // Come o toma algo de su inventario, con la animacion de comer (la usa la vida, cuando le hace
    // falta, y las ordenes de comer y tomar una pocion).
    public void useConsumable(ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return;
        }
        ItemStack one = stack.copyWithCount(1);
        queueJob(Job.custom(RoccoFood.taskFor(one), blockPosition(), one, level ->
        {
            if (consumeFromInventory(one))
            {
                this.life.consume(one);
            }
        }, null).reaching(64.0D));
    }

    // Se lo diste en la mano (clic derecho con comida o una pocion): va a su inventario y lo usa ya.
    public void feedFromHand(Player player, ItemStack held)
    {
        ItemStack one = held.copyWithCount(1);
        if (!player.getAbilities().instabuild)
        {
            held.shrink(1);
        }
        keep(one);
        interruptActivity();
        cancelErrand();
        standUp();
        lookAtPlayer(player);
        useConsumable(one);
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

        // Comida tirada en el piso que le pediste comer: se come una (con su saciedad) y el resto queda.
        if (victim instanceof ItemEntity item && this.task == RoccoTask.EATING && item.getItem().isEdible())
        {
            ItemStack one = item.getItem().split(1);
            if (item.getItem().isEmpty())
            {
                item.discard();
            }
            this.life.consume(one);
            return;
        }

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

    // Saca uno igual (las pociones se distinguen por su efecto). false si ya no lo tiene.
    private boolean consumeFromInventory(ItemStack like)
    {
        for (int slot = 0; slot < this.inventory.getContainerSize(); slot++)
        {
            ItemStack stack = this.inventory.getItem(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, like))
            {
                stack.shrink(1);
                if (stack.isEmpty())
                {
                    this.inventory.setItem(slot, ItemStack.EMPTY);
                }
                this.inventory.setChanged();
                return true;
            }
        }
        return false;
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
            else if (this.task == RoccoTask.MATE || this.task == RoccoTask.ENCHANTING || this.task == RoccoTask.DANCING
                    || this.task == RoccoTask.CLAPPING || this.task == RoccoTask.CELEBRATING || this.task == RoccoTask.COLA
                    || this.task == RoccoTask.EQUIP_PROUD || this.task == RoccoTask.ENDER_ABSORB
                    || this.task == RoccoTask.ENDER_AWAKEN || this.task.consuming() || this.task.musical())
            {
                tickActivityFace();
                tickConsumingHand(this.taskTicks - ANIM_LAG);
            }

            // La Roco Cola es de tierra firme: si se mete al agua o despega, la guarda.
            if (this.task == RoccoTask.COLA && (isInWater() || isTravelling()))
            {
                interruptActivity();
                return;
            }

            if (this.currentJob != null && this.taskTicks == this.currentJob.impactTick() + ANIM_LAG)
            {
                applyJobEffect();
            }

            if (this.currentJob != null && this.taskTicks >= this.currentJob.totalTicks() + ANIM_LAG)
            {
                RoccoTask done = this.task;
                RoccoTask next = done.musical() || done == RoccoTask.DANCING ? nextMusicLoop(done) : null;
                finishJob();
                jobCompleted(done);
                // La vuelta siguiente arranca en el mismo tick: el cliente nunca ve la tarea vacia,
                // y si es la misma, el bucle sigue sin saltos.
                if (next != null && this.jobs.isEmpty())
                {
                    startJob(Job.custom(next, blockPosition(), ItemStack.EMPTY, level -> { }, null).reaching(64.0D));
                }
            }
            return;
        }

        // Volando, buceando o en el barquito no trabaja (y al despegar dejo todo).
        if (getMotion().steered())
        {
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

        if (distance <= next.reachOr(WORK_REACH) || reachesStorage(next))
        {
            // El mate y los festejos son ahi donde esta: en vez de girar hacia su propio bloque
            // (para cualquier lado), mira al jugador.
            ServerPlayer watcher = isPerformance(next.task()) ? owner() : null;
            if (watcher != null)
            {
                lookAtPlayer(watcher);
            }
            else if (!isPerformance(next.task()))
            {
                lookAtBlock(next.target());
            }
            this.jobs.poll();
            this.pathFailures = 0;
            startJob(next);
            return;
        }

        // Para un cofre lejos le da tiempo de caminar hasta ahi (antes eran 8 segundos para todo).
        if (this.approachTicks == 0)
        {
            this.approachLimit = WORK_TIMEOUT_TICKS + (int) (distance * 8.0D);
        }
        this.approachTicks++;

        // Sin camino, o llego a donde podia y le sigue faltando: no tiene sentido esperar el timeout.
        boolean stuckShort = this.approachTicks > 40 && getNavigation().isDone();
        if (this.approachTicks > this.approachLimit || this.pathFailures >= 3 || stuckShort)
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

    // Antes tenia que llegar a 2,2 bloques del cofre: uno puesto arriba de otro ya no lo alcanzaba,
    // y lo que iba ahi se quedaba sin guardar (o sin sacar).
    private boolean reachesStorage(Job job)
    {
        if (job.task() != RoccoTask.INTERACTING || job.entityId() >= 0
                || !(level() instanceof ServerLevel server) || !com.cristoree.rocco.work.Storage.isStorage(server, job.target()))
        {
            return false;
        }
        double dx = getX() - (job.target().getX() + 0.5D);
        double dz = getZ() - (job.target().getZ() + 0.5D);
        double rise = job.target().getY() - getY();
        return dx * dx + dz * dz <= STORAGE_REACH_SIDE * STORAGE_REACH_SIDE
                && rise <= STORAGE_REACH_UP
                && rise >= -STORAGE_REACH_DOWN;
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
            if (this.task == RoccoTask.NONE && this.jobs.isEmpty())
            {
                restoreWeapon();
            }
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

        // El animo se revisa una vez por segundo: no hace falta mas y evita parpadeos. No pisa las
        // caras que marcan las animaciones (mate, baile, despegue, bostezo) ni la de pensar.
        if (this.tickCount % 20 != 0
                || this.faceOverrideTicks > 0
                || this.aiFaceTicks > 0
                || getRoccoState() != RoccoState.ACTIVE
                || getRoccoPose().isAsleep()
                || getRoccoPose() == RoccoPose.WAKING_UP
                || this.entityData.get(DATA_LID) != LID_CLOSED
                || this.task != RoccoTask.NONE
                || this.thinkShown
                || getMotion().scriptedFace()
                || this.entityData.get(DATA_COMBAT) != RoccoCombat.STANCE_NONE)
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

        // Volando, nadando o remando no se aburre: deja la cara que tenga.
        if (isTravelling())
        {
            this.boredTicks = 0;
            return null;
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

    public boolean hostileNearby()
    {
        return hostileNearby(8.0D);
    }

    private boolean hostileNearby(double radius)
    {
        return !level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius),
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
            // El bostezo (boquita en "O") de los ticks 2 a 17, y despues la cara de dormido.
            if (this.poseTicks == 2 + ANIM_LAG)
            {
                setExpression(RoccoExpression.YAWN);
            }
            else if (this.poseTicks == 18 + ANIM_LAG)
            {
                setExpression(RoccoExpression.SLEEP);
            }

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

        // Al despertarse: sigue dormido hasta el tick 15, bosteza hasta el 20 y vuelve a su cara.
        else if (pose == RoccoPose.WAKING_UP)
        {
            if (this.poseTicks == 15 + ANIM_LAG)
            {
                setExpression(RoccoExpression.YAWN);
            }
            else if (this.poseTicks == 21 + ANIM_LAG)
            {
                restoreFace();
            }
        }

        // Dormido: una respiracion cada 40 ticks y la burbujita a los 70 de cada vuelta de 80.
        if (pose == RoccoPose.SLEEPING)
        {
            // Por si se durmio sin pasar por el bostezo (al cargar el mundo, por ejemplo).
            if (getExpression() != RoccoExpression.SLEEP && this.faceOverrideTicks <= 0)
            {
                setExpression(RoccoExpression.SLEEP);
            }
            if (this.snoreTicks >= ANIM_LAG && (this.snoreTicks - ANIM_LAG) % 40 == 0)
            {
                playRoccoSound(ModSounds.SNORE.get(), 0.3F);
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
                // Una pocion o la miel no se mastica: se toma.
                boolean drink = this.currentJob != null && RoccoFood.isDrink(this.currentJob.shown());
                if (drink && (tick == 34 || tick == 44))
                {
                    playSound(SoundEvents.GENERIC_DRINK, 0.6F, 0.9F + this.random.nextFloat() * 0.2F);
                }
                else if (!drink && (tick == 34 || tick == 40 || tick == 46))
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
                // El "aaah" de despues de cada sorbo, cuando le salen las florcitas, y el grande
                // del final.
                else if (tick == 161 || tick == 235 || tick == 310 || tick == 450 || tick == 524)
                {
                    playRoccoSound(ModSounds.MATE_SIGH.get(), 0.5F);
                }
            }
            // Ticks de ROCCO-PENSAR-FESTEJAR-BAILAR.md.
            // Con un disco o su musica sonando no tararea: ya hay musica.
            case DANCING ->
            {
                if (tick == 8 && !musicNearby())
                {
                    playRoccoSound(ModSounds.HUM.get(), 0.5F);
                }
            }
            case DANCE_FIESTA, DANCE_MENEITO ->
            {
                if (Math.floorMod(tick, 160) == 8 && !musicNearby())
                {
                    playRoccoSound(ModSounds.HUM.get(), 0.5F);
                }
            }
            case CLAPPING ->
            {
                if (tick == 3)
                {
                    playRoccoSound(ModSounds.GIGGLE.get(), 0.4F);
                }
                else if (tick == 7 || tick == 12 || tick == 17)
                {
                    playRoccoSound(ModSounds.CLAP.get(), 0.25F);
                }
            }
            case CELEBRATING ->
            {
                if (tick == 6)
                {
                    playRoccoSound(ModSounds.CHEER.get(), 0.6F);
                }
                else if (tick == 14 || tick == 26 || tick == 37)
                {
                    playRoccoSound(ModSounds.STEP.get(), 0.15F);
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
            // Ticks de ROCCO-COLA-COMBATE-ARMADURAS-CANTO.md.
            case COLA ->
            {
                switch (tick)
                {
                    // v11: un trago y un "aaah" por cada vez que toma, sincronizados con la animacion (tono 1).
                    case 21 -> playSound(ModSounds.COLA_CLINK.get(), 0.5F, 1.0F);
                    case 59 -> playSound(ModSounds.COLA_OPEN.get(), 0.8F, 1.0F);
                    case 62, 140 -> playRoccoSound(ModSounds.COLA_FIZZ.get(), 0.25F);
                    case 79 -> playSound(ModSounds.COLA_CAP.get(), 0.5F, 1.0F);
                    case 89 -> playSound(ModSounds.COLA_DRINK_1.get(), 0.8F, 1.0F);
                    case 109 -> playSound(ModSounds.COLA_AHH_1.get(), 0.75F, 1.0F);
                    case 126 -> playSound(ModSounds.COLA_DRINK_2.get(), 0.85F, 1.0F);
                    case 159 -> playSound(ModSounds.COLA_AHH_2.get(), 0.75F, 1.0F);
                    case 181 -> playSound(ModSounds.COLA_DRINK_3.get(), 0.9F, 1.0F);
                    case 229 -> playSound(ModSounds.COLA_AHH_3.get(), 0.8F, 1.0F);
                    case 252 -> playSound(ModSounds.COLA_HIC.get(), 0.6F, 1.0F);
                    default -> { }
                }
            }
            case ENDER_ABSORB ->
            {
                if (tick == 0)
                {
                    playRoccoSound(ModSounds.ENDER_ABSORB.get(), 0.8F);
                }
            }
            case FOOD_EAT, FOOD_EAT_BAD ->
            {
                // Los sonidos de comer del juego (ROCCO-V12): mordidas, masticar y un eructito tierno.
                switch (tick)
                {
                    case 10, 18, 26 -> playSound(SoundEvents.GENERIC_EAT, 0.5F, 0.9F + this.random.nextFloat() * 0.2F);
                    case 14, 22 -> playSound(SoundEvents.GENERIC_EAT, 0.3F, 0.9F + this.random.nextFloat() * 0.2F);
                    case 40 -> playSound(SoundEvents.PLAYER_BURP, 0.4F, 1.3F);
                    default -> { }
                }
            }
            case POTION_DRINK, POTION_DRINK_BAD ->
            {
                switch (tick)
                {
                    case 12, 16, 21, 26 -> playSound(SoundEvents.GENERIC_DRINK, 0.5F, 0.9F + this.random.nextFloat() * 0.2F);
                    default -> { }
                }
            }
            case ENDER_AWAKEN ->
            {
                if (tick == 0)
                {
                    playSound(ModSounds.ENDER_AWAKEN.get(), 0.9F, 1.0F);
                }
            }
            // interact usa el sonido del propio bloque, que ya pone RoccoWork.
            default -> { }
        }
    }

    // Tono variado como hace Minecraft, para que no suene calcado.
    void playRoccoSound(SoundEvent sound, float volume)
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

        // Se decide al abrir, asi no cambia de carne a flores con la tapa abierta.
        boolean garden = RoccoCommonConfig.gardenLid();
        if (this.entityData.get(DATA_LID) == LID_CLOSED)
        {
            this.entityData.set(DATA_GARDEN_LID, garden);
        }
        this.entityData.set(DATA_LID, LID_OPENED);
        this.lidCloseTicks = 0;
        setExpression(RoccoExpression.DIZZY);
        getNavigation().stop();
        setTalking(false);

        // El archivo ya trae el pop, el roce, la baba y el golpecito al apoyarse, sincronizados.
        schedule(ANIM_LAG, () -> playRoccoSound(ModSounds.LID_OPEN.get(), 0.8F));
        if (this.entityData.get(DATA_GARDEN_LID))
        {
            // Las flores brotando.
            schedule(ANIM_LAG + 8, () -> playRoccoSound(ModSounds.LEAF_GROW.get(), 0.4F));
        }
        this.gooTicks = 40 + this.random.nextInt(40);
        player.openMenu(this);
    }

    // Desde el menu: le abre la tapa si puede (no en medio de un trabajo ni mientras viaja).
    public boolean openInventory(ServerPlayer player)
    {
        if (getRoccoState() != RoccoState.ACTIVE || isWorking() || isTravelling())
        {
            return false;
        }
        openLid(player);
        return true;
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
        if (this.entityData.get(DATA_LID) == LID_OPENED && !this.entityData.get(DATA_GARDEN_LID) && --this.gooTicks <= 0)
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
                case PLANT, BURIED -> InteractionResult.SUCCESS;
                case AWAKENING, DYING, RISING -> InteractionResult.PASS;
                case ACTIVE -> !player.isShiftKeyDown() && !isSharingMate() && (isWorking() || isTravelling())
                        && !(RoccoFood.isConsumable(player.getItemInHand(hand)) && !isTravelling())
                        ? InteractionResult.PASS
                        : InteractionResult.SUCCESS;
            };
        }

        switch (getRoccoState())
        {
            case PLANT ->
            {
                // Un Rocco sin dueño (del huevo generador) es de quien lo despierta.
                if (this.ownerId == null && player instanceof ServerPlayer serverPlayer)
                {
                    com.cristoree.rocco.RoccoCompanionTracker.remember(serverPlayer, this);
                }
                beginAwakening();
                return InteractionResult.CONSUME;
            }
            case AWAKENING, DYING, RISING -> {
                return InteractionResult.PASS;
            }
            // Clic derecho al monticulo: su dueño lo desentierra.
            case BURIED ->
            {
                this.life.interactGrave(player);
                return InteractionResult.CONSUME;
            }
            case ACTIVE ->
            {
                adoptIfOwnerless(player);
                // Agachado: su menu, aunque este trabajando o viajando (sirve justamente para pararlo).
                if (player instanceof ServerPlayer serverPlayer && player.isShiftKeyDown())
                {
                    com.cristoree.rocco.RoccoMenu.open(serverPlayer, this);
                    return InteractionResult.CONSUME;
                }
                // Tomando mate con vos: agarras el que te ofrece, o se lo devolves.
                if (player instanceof ServerPlayer serverPlayer && this.share.active())
                {
                    this.share.interact(serverPlayer);
                    return InteractionResult.CONSUME;
                }
                // Con comida o una pocion en la mano: se la das y la usa enseguida.
                ItemStack held = player.getItemInHand(hand);
                if (RoccoFood.isConsumable(held) && !isTravelling())
                {
                    feedFromHand(player, held);
                    return InteractionResult.CONSUME;
                }
                // Volando, nadando o en el barquito no abre la tapa (se le mojaria todo).
                if (player instanceof ServerPlayer serverPlayer && !isWorking() && !isTravelling())
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

        // Los que se guardaron con la piña de antes (1) pegan como ahora.
        AttributeInstance damage = getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null && damage.getBaseValue() < PUNCH_DAMAGE)
        {
            damage.setBaseValue(PUNCH_DAMAGE);
        }

        if (getNavigation() instanceof GroundPathNavigation ground)
        {
            ground.setCanOpenDoors(RoccoCommonConfig.CAN_OPEN_DOORS.get());
        }

        applyWaterMalus(false);
    }

    // "Evita el agua" es para cuando pasea solo: si te sigue y vos estas en el agua, se mete igual.
    private void applyWaterMalus(boolean ownerSwimming)
    {
        setPathfindingMalus(BlockPathTypes.WATER, RoccoCommonConfig.AVOID_WATER.get() && !ownerSwimming ? 8.0F : 0.0F);
    }

    // Lo esta llevando nadando al lado tuyo: el seguir de siempre no lo tiene que mover.
    public boolean isSwimmingAlongside(Player player)
    {
        return player instanceof ServerPlayer swimmer && this.journey.swimsWith(swimmer);
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
            tickAwakening(this.awakenTicks - ANIM_LAG);

            if (this.awakenTicks >= AWAKEN_TICKS + ANIM_LAG)
            {
                setRoccoState(RoccoState.ACTIVE);
                setExpression(RoccoExpression.HAPPY);

                Player nearest = level().getNearestPlayer(this, 16.0D);
                if (nearest != null)
                {
                    // Recien nacido: el saludo con la mano ya viene al final de awaken; se da
                    // vuelta hacia el jugador y le habla. No depende de que haya clave de IA.
                    lookAtPlayer(nearest);
                    talkFor(80);
                    greet(nearest);
                }
            }
        }
        // Su musica: se corta si termina la cancion, si se muere o si se entierra.
        com.cristoree.rocco.music.RoccoMusic.tick(this);

        if (getRoccoState() == RoccoState.PLANT)
        {
            // Plantado duerme con la carita afuera: respira y de vez en cuando ronca bajito.
            if (getExpression() != RoccoExpression.SLEEP)
            {
                setExpression(RoccoExpression.SLEEP);
            }
            if (this.tickCount % 80 == 0)
            {
                playRoccoSound(ModSounds.SNORE.get(), 0.2F);
            }
        }
        else if (getRoccoState() == RoccoState.DYING)
        {
            this.life.tickDying();
        }
        else if (getRoccoState() == RoccoState.BURIED)
        {
            this.life.tickBuried();
        }
        else if (getRoccoState() == RoccoState.RISING)
        {
            this.life.tickRising();
        }

        if (this.talkTicks > 0)
        {
            this.talkTicks--;
            if (this.talkTicks == 0)
            {
                setTalking(false);
            }
        }

        // Se cura solo (a oscuras, mas lento) y mas rapido despues de comer.
        if (getRoccoState() == RoccoState.ACTIVE)
        {
            this.life.tickRegen();
        }
        tickCombatCalm();

        if (this.giveSpaceTicks > 0)
        {
            this.giveSpaceTicks--;
        }
        if (this.safeFallTicks > 0)
        {
            this.safeFallTicks--;
        }

        rescueFromVoid();
        tickFace();
        tickLid();
        tickPose();
        tickScheduled();
        tickThinking();

        if (getRoccoState() == RoccoState.ACTIVE)
        {
            resumeTending();
            this.journey.tick();
            this.share.tick();
            if (this.tickCount % 20 == 0)
            {
                ServerPlayer leader = owner();
                applyWaterMalus(this.following && leader != null && leader.isInWater());
            }
            tickWork();
            tickMusic();
            tickGifts();
            tickCompanionship();
            if (this.tickCount % 20 == 0)
            {
                this.life.tickEnder();
                // Come, toma una pocion o una manzana dorada si le hace falta (y lo dejaste).
                this.life.tickConsumables();
            }
            // El cofre de ender ya entro pero la animacion del poder se perdio (otra orden borro
            // la cola): el poder se activa igual.
            if (this.life.awakeningPending() && this.task != RoccoTask.ENDER_AWAKEN && this.task != RoccoTask.ENDER_ABSORB
                    && this.task != RoccoTask.SWALLOWING && this.jobs.stream().noneMatch(job -> job.task() == RoccoTask.ENDER_AWAKEN))
            {
                this.life.grantEnderPocket();
            }
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
                && (getMotion() == RoccoMotion.NONE || getMotion() == RoccoMotion.FLOAT_IDLE)
                && this.entityData.get(DATA_COMBAT) == RoccoCombat.STANCE_NONE
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

    // Si se cae al vacio no se pierde: aparece al lado tuyo (o en el spawn si no estas). Solo
    // /kill lo puede sacar del mundo.
    private void rescueFromVoid()
    {
        if (getY() > level().getMinBuildHeight() - 16 || !(level() instanceof ServerLevel serverLevel))
        {
            return;
        }
        stopTravelling();
        allowSafeFall();
        setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        ServerPlayer owner = owner();
        if (owner != null && owner.level() == serverLevel && RoccoFollowGoal.teleportNear(this, owner))
        {
            return;
        }
        BlockPos spawn = com.cristoree.rocco.RoccoSpawner.findGround(serverLevel, serverLevel.getSharedSpawnPos());
        teleportTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D);
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
            boolean diary = com.cristoree.rocco.RoccoSurprises.isDiary(stack);
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
            // Le devolviste el diario.
            if (diary)
            {
                sayLine(RoccoExpression.SMUG, "diary_returned");
            }
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

        // Lo que junto mientras no estabas, y las sorpresas (un regalito raro, su diario).
        com.cristoree.rocco.RoccoPresents.tick(this, owner);
        com.cristoree.rocco.RoccoSurprises.tick(this, owner);

        // Cada minuto juntos (cerca) suma confianza; tambien suma cada charla (RoccoBrain).
        if (distanceToSqr(owner) < 32 * 32)
        {
            addTrust(1.0D / 60.0D);
        }

        // Si hubo silencio un buen rato, dice algo por su cuenta.
        brain().tickChatter();

        // Si se puso a tomar mate (o a bailar) y te fuiste, lo deja para seguirte.
        if ((this.task == RoccoTask.MATE || this.task == RoccoTask.DANCING || this.task.musical()) && this.following
                && distanceToSqr(owner) > sqr(RoccoCommonConfig.WANDER_RADIUS.get() + 8.0D))
        {
            interruptActivity();
        }

        catchUpIfLeftBehind(owner);

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

        long sinceDance = level().getGameTime() - this.lastDanceTick;
        boolean danceReady = RoccoCommonConfig.IDLE_DANCE_ENABLED.get() && sinceDance > 5 * 1200L
                && !isTalking() && !isInWater() && !hostileNearby(12.0D) && !level().isNight();

        // Cada vez que elige algo para hacer, muy de vez en cuando en vez de eso se toma una Roco Cola.
        if (mateReady && roll < 0.012F)
        {
            if (!idleCola(owner))
            {
                startMate();
            }
        }
        else if (danceReady && roll >= 0.012F && roll < 0.018F)
        {
            if (!idleCola(owner))
            {
                perform(RoccoTask.DANCING, owner);
            }
        }
        else if (RoccoCommonConfig.IDLE_SIT_ENABLED.get() && roll > 1.0F - 0.008F)
        {
            if (!idleCola(owner))
            {
                sitDown();
                this.autoRestTicks = (30 + this.random.nextInt(60)) * 20;
            }
        }
    }

    // Libre de verdad: sin encargos, sin trabajos, de pie, en tierra y con la tapa cerrada.
    // ---------- el mate compartido ----------

    public MateShare mateShare()
    {
        return this.share;
    }

    void setMateStep(MateShare.Step step)
    {
        this.entityData.set(DATA_MATE_STEP, step.ordinal());
    }

    // Tambien del lado del cliente: muestra el mate y el termo, y no le gira la cabeza.
    public boolean isSharingMate()
    {
        return this.entityData.get(DATA_MATE_STEP) != 0;
    }

    // Gira de a poco hacia el jugador (todo el cuerpo: la cabeza la mueve la animacion).
    void faceToward(Player player)
    {
        lookAt(player, 12.0F, 30.0F);
        this.yBodyRot = getYRot();
        this.yHeadRot = getYRot();
    }

    public boolean isFreeTime()
    {
        return !this.share.active() && this.errand == null && this.jobs.isEmpty() && this.task == RoccoTask.NONE
                && this.entityData.get(DATA_LID) == LID_CLOSED && getRoccoPose() == RoccoPose.STANDING
                && !isTravelling();
    }

    // Libre para darte algo en la mano: despierto, en tierra, sin un trabajo a la mitad y con la
    // tapa cerrada. Puede estar sentado, esperando o cuidando la huerta.
    public boolean canHandOver()
    {
        return getRoccoState() == RoccoState.ACTIVE && this.jobs.isEmpty() && this.task == RoccoTask.NONE
                && this.entityData.get(DATA_LID) == LID_CLOSED && !getRoccoPose().isAsleep() && !isTravelling();
    }

    public com.cristoree.rocco.RoccoSurprises.State surprises()
    {
        return this.surprises;
    }

    // Que nunca quede perdido: si te alejaste mucho mientras estaba sentado, dormido o con un
    // encargo (no si le dijiste que espere, ni si quedo cuidando la huerta), deja eso y te sigue.
    // El seguimiento despues lo trae al lado tuyo con el teletransporte.
    private void catchUpIfLeftBehind(ServerPlayer owner)
    {
        if (!this.following || !RoccoCommonConfig.FOLLOW_ENABLED.get() || staysForWork() || isTravelling())
        {
            return;
        }
        double teleport = RoccoCommonConfig.TELEPORT_DISTANCE.get();
        double distance = Math.sqrt(distanceToSqr(owner));

        // Sentado o dormido por su cuenta ya se levanta solo; por orden, recien si te fuiste lejos.
        if (getRoccoPose().isResting() && distance > Math.max(48.0D, teleport * 2.0D))
        {
            clearAutoRest();
            standUp();
            return;
        }

        // Con un encargo trabaja tranquilo aunque te alejes un poco; si te fuiste muy lejos, lo
        // deja y te alcanza.
        if ((this.errand != null || !this.jobs.isEmpty() || isWorking()) && distance > Math.max(96.0D, teleport * 3.0D))
        {
            interruptActivity();
            if (this.task != RoccoTask.NONE)
            {
                stopTriggeredAnimation("main", null);
                finishJob();
            }
            cancelErrand();
            sayLine("left_behind");
        }
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

    public double trustPoints()
    {
        return this.trust;
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
        // Su musica se va con el (a los que la escuchaban se les corta).
        if (this.music != null && !level().isClientSide)
        {
            com.cristoree.rocco.music.RoccoMusic.stop(this, false);
        }
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
        return getRoccoState() != RoccoState.ACTIVE
                || isWorking()
                || this.entityData.get(DATA_LID) != LID_CLOSED
                || getRoccoPose().isResting()
                || getMotion() == RoccoMotion.FLOAT_END
                || super.isImmobile();
    }

    @Override
    public boolean isPushable()
    {
        return getRoccoState() == RoccoState.ACTIVE && !getMotion().steered() && super.isPushable();
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

        // Muriendose, enterrado o saliendo de la tumba no se lo lastima (solo /kill y el vacio).
        if (getRoccoState().isInert() && !isPlantVisible() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            return false;
        }

        // Tus golpes no lo lastiman (para no pegarle sin querer en medio de una pelea), salvo que
        // lo actives en la config.
        if (!RoccoCommonConfig.OWNER_CAN_HURT_ROCCO.get() && this.ownerId != null
                && source.getEntity() instanceof Player hitter && this.ownerId.equals(hitter.getUUID())
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            return false;
        }

        // El vacio no se lo lleva: lo rescata (rescueFromVoid). /kill si.
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD) && !level().isClientSide)
        {
            rescueFromVoid();
            return false;
        }

        boolean hit = super.hurt(source, amount);

        if (hit && !level().isClientSide)
        {
            this.life.noteHurt();
        }

        if (hit && !level().isClientSide && getRoccoState() == RoccoState.ACTIVE)
        {
            // Primero se corta lo que estaba haciendo (mate, magia), asi la animacion del golpe
            // no queda pisada.
            this.share.stop(true);
            interruptActivity();

            // Un golpecito lo sacude; uno fuerte lo dobla. La cara acompaña a la animacion y
            // dura lo mismo, despues vuelve la que tenia. Volando o en el barquito sigue en su
            // pose (la animacion del golpe es de pie): solo se queja con la cara.
            boolean heavy = amount >= 4.0F;
            if (!getMotion().steered())
            {
                triggerAnim("main", heavy ? "hurt" : "hurt_light");
            }
            overrideFace(heavy ? RoccoExpression.HURT : RoccoExpression.HURT_LIGHT, heavy ? 22 : 10);

            // Se olvida del maltrato despues de un rato tranquilo.
            this.recentHits++;
            this.hitDecayTicks = 600;

            if (getHealth() < getMaxHealth() * 0.3F && getHealth() > 0.0F)
            {
                sayLineOnce("low_health", 1200);
            }

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
        boolean wouldDie = amount >= getHealth();
        super.actuallyHurt(source, allowed);
        if (wouldDie && !level().isClientSide)
        {
            immortalKnock();
        }
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

    // Se muere para siempre (modo PERMANENT, /kill o el vacio): lo que llevaba no se pierde con el.
    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit)
    {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        dropEverything();
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
        this.surprises.save(tag);
        // El arma de verdad: en HandItems puede quedar la herramienta que mostraba trabajando.
        tag.put("Weapon", weapon().save(new CompoundTag()));
        tag.putInt("AttackTargets", attackTargets());
        tag.putInt("GravePose", getGravePose());
        this.life.save(tag);

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
        this.surprises.load(tag);
        // Las versiones anteriores solo usaban la mano para mostrar cosas: sin "Weapon", va vacia.
        this.stashedWeapon = null;
        setItemSlot(EquipmentSlot.MAINHAND, tag.contains("Weapon") ? ItemStack.of(tag.getCompound("Weapon")) : ItemStack.EMPTY);
        this.attackTargets = tag.contains("AttackTargets") ? tag.getInt("AttackTargets") : -1;
        this.entityData.set(DATA_GRAVE_POSE, Math.floorMod(tag.getInt("GravePose"), 3));
        this.life.load(tag);
        // Saliendo de la tumba no se guarda a la mitad: al cargar ya esta afuera.
        if (getRoccoState() == RoccoState.RISING)
        {
            setRoccoState(RoccoState.ACTIVE);
        }

        this.places.clear();
        for (net.minecraft.nbt.Tag raw : tag.getList("Places", 10))
        {
            CompoundTag entry = (CompoundTag) raw;
            this.places.put(entry.getString("Name"), new Place(BlockPos.of(entry.getLong("Pos")), entry.getString("Dim")));
        }

        // El mate, la magia, el vuelo y el baile no se guardan a la mitad: si quedo con una cara
        // automatica (menos la de dormido, que va con la postura), vuelve a la normal.
        RoccoExpression saved = getExpression();
        if (!saved.selectableByAi() && saved != RoccoExpression.SLEEP)
        {
            setExpression(RoccoExpression.HAPPY);
        }
        if (getRoccoState() == RoccoState.PLANT)
        {
            setExpression(RoccoExpression.SLEEP);
        }
        else if (getRoccoState().isInert())
        {
            setExpression(RoccoExpression.DIZZY);
        }

        // Volando o en el barquito no se guarda: al cargar camina (y si esta en el agua, flota).
        // NoGravity si se guarda en el NBT, y sin esto quedaba colgado en el aire para siempre.
        setNoGravity(false);
        this.noPhysics = false;
        allowSafeFall();

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

    // ---------- nacer ----------

    // awaken (5,8 s): tiembla la tierra, abre los ojitos, bosteza, mira para los costados, hace
    // fuerza, sale de un salto, le brotan las hojitas, se sacude, se limpia y saluda.
    private void tickAwakening(int t)
    {
        switch (t)
        {
            case 5, 10, 14, 48, 55, 59 -> playRoccoSound(ModSounds.DIRT.get(), 0.35F);
            case 35, 40 -> playRoccoSound(ModSounds.LEAVES.get(), 0.4F);
            case 63 -> playRoccoSound(ModSounds.AWAKEN_POP.get(), 0.8F);
            case 68 -> playRoccoSound(ModSounds.LEAF_GROW.get(), 0.5F);
            case 75 -> playRoccoSound(ModSounds.STEP.get(), 0.4F);
            case 78 -> playRoccoSound(ModSounds.SHAKE_OFF.get(), 0.6F);
            case 94 -> playRoccoSound(ModSounds.DUST_PAT.get(), 0.5F);
            case 102 -> playRoccoSound(ModSounds.CHEER.get(), 0.6F);
            case 109 -> playRoccoSound(ModSounds.STEP.get(), 0.15F);
            default -> { }
        }
        RoccoExpression face = switch (t)
        {
            case 0 -> RoccoExpression.SLEEP;
            case 20 -> RoccoExpression.BORED;
            case 30 -> RoccoExpression.YAWN;
            case 40 -> RoccoExpression.THINK;
            case 47 -> RoccoExpression.SERIOUS;
            case 63, 102 -> RoccoExpression.CHEER;
            case 75, 92 -> RoccoExpression.HAPPY;
            case 78 -> RoccoExpression.WINDY;
            default -> null;
        };
        if (face != null)
        {
            setExpression(face);
        }
    }

    // ---------- el equipo: armadura, arma y escudo ----------

    // El arma que tiene de verdad (aunque ahora la mano muestre una herramienta).
    public ItemStack weapon()
    {
        return this.stashedWeapon != null ? this.stashedWeapon : getItemBySlot(EquipmentSlot.MAINHAND);
    }

    public void setWeapon(ItemStack stack)
    {
        if (this.stashedWeapon != null)
        {
            this.stashedWeapon = stack;
        }
        else
        {
            setItemSlot(EquipmentSlot.MAINHAND, stack);
        }
    }

    // Lo que tiene puesto en ese lugar (para el menu de equipo y #equipar).
    public ItemStack gear(EquipmentSlot slot)
    {
        return slot == EquipmentSlot.MAINHAND ? weapon() : getItemBySlot(slot);
    }

    public void setGear(EquipmentSlot slot, ItemStack stack)
    {
        if (slot == EquipmentSlot.MAINHAND)
        {
            setWeapon(stack);
        }
        else
        {
            setItemSlot(slot, stack);
        }
    }

    // Antes de mostrar otra cosa en la mano, el arma se guarda; al terminar vuelve.
    private void stashWeapon()
    {
        if (this.stashedWeapon == null)
        {
            this.stashedWeapon = getItemBySlot(EquipmentSlot.MAINHAND);
        }
    }

    private void restoreWeapon()
    {
        if (this.stashedWeapon != null)
        {
            ItemStack weapon = this.stashedWeapon;
            this.stashedWeapon = null;
            setItemSlot(EquipmentSlot.MAINHAND, weapon);
        }
        else if (!RoccoGear.isWeapon(getItemBySlot(EquipmentSlot.MAINHAND)))
        {
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    public int countInInventory(net.minecraft.world.item.Item item)
    {
        return Inv.count(this.inventory, item);
    }

    // #equipar: lo mejor de su inventario a cada lugar (lo que tenia puesto vuelve al inventario).
    public boolean equipBest()
    {
        boolean changed = false;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
        {
            int best = -1;
            double bestValue = valueFor(slot, gear(slot));
            for (int i = 0; i < this.inventory.getContainerSize(); i++)
            {
                ItemStack candidate = this.inventory.getItem(i);
                if (candidate.isEmpty() || !RoccoGear.fits(slot, candidate))
                {
                    continue;
                }
                double value = valueFor(slot, candidate);
                if (value > bestValue)
                {
                    bestValue = value;
                    best = i;
                }
            }
            if (best >= 0)
            {
                ItemStack chosen = this.inventory.removeItem(best, 1);
                ItemStack old = gear(slot);
                setGear(slot, chosen);
                if (!old.isEmpty())
                {
                    keep(old);
                }
                changed = true;
            }
        }
        return changed;
    }

    private static double valueFor(EquipmentSlot slot, ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return -1.0D;
        }
        return switch (slot)
        {
            case MAINHAND -> RoccoGear.weaponValue(stack);
            case OFFHAND -> RoccoGear.isShield(stack) ? 1.0D + stack.getEnchantmentTags().size() : -1.0D;
            default -> RoccoGear.armorValue(stack);
        };
    }

    // #desequipar: todo lo que tiene puesto vuelve a su inventario.
    public boolean unequipAll()
    {
        boolean changed = false;
        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            ItemStack worn = gear(slot);
            if (!worn.isEmpty())
            {
                setGear(slot, ItemStack.EMPTY);
                keep(worn);
                changed = true;
            }
        }
        return changed;
    }

    // Se puso algo nuevo (por el menu o con #equipar): se mira, se da palmaditas y posa.
    public void celebrateGear(@Nullable Player toward)
    {
        // La frase, como mucho cada 20 segundos (probandose varias cosas seguidas no la repite).
        if (getRoccoState() != RoccoState.ACTIVE || isTravelling() || !isFreeTime())
        {
            sayLineOnce("armor_equip", 400);
            return;
        }
        perform(RoccoTask.EQUIP_PROUD, toward);
        sayLineOnce("armor_equip", 400);
    }

    // El arma se gasta al pegar, como la del jugador (se puede apagar en la config).
    public void wearWeapon()
    {
        ItemStack weapon = weapon();
        if (!RoccoCommonConfig.WEAPON_DURABILITY.get() || weapon.isEmpty() || !weapon.isDamageableItem())
        {
            return;
        }
        weapon.hurtAndBreak(1, this, rocco -> rocco.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        if (weapon.isEmpty())
        {
            setWeapon(ItemStack.EMPTY);
            sayLine("combat_weapon_broke");
        }
    }

    // La armadura se gasta con los golpes, como la del jugador.
    @Override
    protected void hurtArmor(DamageSource source, float amount)
    {
        if (!RoccoCommonConfig.WEAPON_DURABILITY.get() || amount <= 0.0F)
        {
            return;
        }
        int damage = Math.max(1, (int) (amount / 4.0F));
        for (EquipmentSlot slot : RoccoGear.ARMOR_SLOTS)
        {
            ItemStack stack = getItemBySlot(slot);
            if (stack.getItem() instanceof net.minecraft.world.item.ArmorItem
                    && !(source.is(DamageTypeTags.IS_FIRE) && stack.getItem().isFireResistant()))
            {
                stack.hurtAndBreak(damage, this, rocco -> rocco.broadcastBreakEvent(slot));
            }
        }
    }

    @Override
    protected void hurtHelmet(DamageSource source, float amount)
    {
        ItemStack helmet = getItemBySlot(EquipmentSlot.HEAD);
        if (RoccoCommonConfig.WEAPON_DURABILITY.get() && amount > 0.0F && helmet.getItem() instanceof net.minecraft.world.item.ArmorItem)
        {
            helmet.hurtAndBreak(Math.max(1, (int) (amount / 4.0F)), this, rocco -> rocco.broadcastBreakEvent(EquipmentSlot.HEAD));
        }
    }

    // Lo que flota en el hueso grab (del lado del cliente lo dibuja el renderer).
    public ItemStack grabbedItem()
    {
        return this.entityData.get(DATA_GRAB);
    }

    // ---------- peleando ----------

    public int attackTargets()
    {
        if (this.attackTargets < 0)
        {
            this.attackTargets = RoccoCombat.defaultTargets();
        }
        return this.attackTargets;
    }

    public void setAttackTargets(int targets)
    {
        this.attackTargets = targets & (RoccoCombat.HOSTILE | RoccoCombat.PEACEFUL | RoccoCombat.PLAYERS);
        LivingEntity target = getTarget();
        if (target != null && !RoccoCombat.mayAttack(this, target))
        {
            setTarget(null);
        }
    }

    // Puede pelear: despierto, en tierra, de pie, sin la tapa abierta y sin un trabajo a la mitad.
    // Un encargo que solo espera (materiales, el horno) no lo frena.
    public boolean canFight()
    {
        return getRoccoState() == RoccoState.ACTIVE
                && !isTravelling()
                && !isLidOpen()
                && getRoccoPose() == RoccoPose.STANDING
                && this.task == RoccoTask.NONE
                && this.jobs.isEmpty()
                && (this.errand == null || this.errand.allowsFollowing())
                && !this.share.active()
                && this.immortalRetreatTicks <= 0;
    }

    // Lo que puede dejar para defenderte: sentado o en sus cosas de tiempo libre (mate, baile,
    // aplausos, la Roco Cola). No lo despierta ni le corta un trabajo, comida o el cofre de ender.
    public boolean canDropForFight()
    {
        boolean idleTask = this.task == RoccoTask.NONE
                || isPerformance(this.task) && !this.task.consuming()
                && this.task != RoccoTask.ENDER_ABSORB && this.task != RoccoTask.ENDER_AWAKEN;
        RoccoPose pose = getRoccoPose();
        return getRoccoState() == RoccoState.ACTIVE
                && !isTravelling()
                && !isLidOpen()
                && (pose == RoccoPose.STANDING || pose == RoccoPose.SITTING || pose == RoccoPose.SITTING_DOWN)
                && idleTask
                && (this.task != RoccoTask.NONE || this.jobs.isEmpty())
                && (this.errand == null || this.errand.allowsFollowing())
                && this.immortalRetreatTicks <= 0;
    }

    // Deja lo que estaba haciendo para pelear (la meta de pelea arranca cuando termina de pararse).
    public void dropForFight()
    {
        if (this.task != RoccoTask.NONE)
        {
            interruptActivity();
            this.jobs.clear();
        }
        this.share.stop(false);
        clearAutoRest();
        standUp();
    }

    // Su dueño, si esta conectado y en este mundo (nunca el jugador que tenga mas cerca).
    @Nullable
    public ServerPlayer trueOwner()
    {
        if (this.ownerId == null || !(level() instanceof ServerLevel serverLevel))
        {
            return null;
        }
        return serverLevel.getPlayerByUUID(this.ownerId) instanceof ServerPlayer player && player.isAlive() ? player : null;
    }

    // Su dueño este donde este (otra dimension incluida), o muerto esperando reaparecer.
    @Nullable
    public ServerPlayer trueOwnerAnywhere()
    {
        net.minecraft.server.MinecraftServer server = getServer();
        return this.ownerId == null || server == null ? null : server.getPlayerList().getPlayer(this.ownerId);
    }

    // "Ataca a ese": deja lo que estaba haciendo y va. Las reglas fijas siguen (nunca a su dueño).
    public boolean orderAttack(LivingEntity target)
    {
        if (target == this || target instanceof RoccoEntity || (this.ownerId != null && this.ownerId.equals(target.getUUID())))
        {
            return false;
        }
        interruptActivity();
        cancelErrand();
        clearAutoRest();
        standUp();
        this.orderedTarget = target.getUUID();
        setTarget(target);
        enterCombat();
        return true;
    }

    public boolean isOrderedTarget(@Nullable LivingEntity target)
    {
        return target != null && this.orderedTarget != null && this.orderedTarget.equals(target.getUUID());
    }

    public boolean hasOrderedTarget()
    {
        LivingEntity target = getTarget();
        return target != null && target.isAlive() && isOrderedTarget(target);
    }

    public void clearOrderedTarget()
    {
        this.orderedTarget = null;
    }

    // Un Rocco sin dueño (de un huevo generador, o de una version vieja) adopta al primero que le
    // habla o lo toca: sin dueño no sabe a quien defender ni a quien ayudar.
    public void adoptIfOwnerless(Player player)
    {
        if (this.ownerId == null && player instanceof ServerPlayer serverPlayer && !player.isSpectator())
        {
            com.cristoree.rocco.RoccoCompanionTracker.remember(serverPlayer, this);
        }
    }

    public void enterCombat()
    {
        this.fighting = true;
        this.combatCalmTicks = 0;
        if (this.entityData.get(DATA_COMBAT) == RoccoCombat.STANCE_NONE)
        {
            setCombatStance(RoccoCombat.STANCE_GUARD);
        }
    }

    // Termino la pelea: queda en guardia un ratito y despues vuelve a lo de siempre.
    public void leaveCombat(int calmTicks)
    {
        this.fighting = false;
        this.combatCalmTicks = Math.max(1, calmTicks);
        if (this.entityData.get(DATA_COMBAT) != RoccoCombat.STANCE_NONE)
        {
            setCombatStance(RoccoCombat.STANCE_GUARD);
        }
    }

    private void tickCombatCalm()
    {
        if (this.immortalRetreatTicks > 0)
        {
            this.immortalRetreatTicks--;
        }
        if (this.fighting || this.combatCalmTicks <= 0 || --this.combatCalmTicks > 0)
        {
            return;
        }
        if (this.entityData.get(DATA_COMBAT) != RoccoCombat.STANCE_NONE)
        {
            this.entityData.set(DATA_COMBAT, RoccoCombat.STANCE_NONE);
            if (getExpression() == RoccoExpression.SERIOUS || getExpression() == RoccoExpression.ANGRY)
            {
                setExpression(RoccoExpression.HAPPY);
            }
        }
    }

    public void setCombatStance(int stance)
    {
        if (this.entityData.get(DATA_COMBAT) == stance)
        {
            return;
        }
        this.entityData.set(DATA_COMBAT, stance);
        if (stance != RoccoCombat.STANCE_NONE && this.faceOverrideTicks <= 0)
        {
            setExpression(RoccoExpression.SERIOUS);
        }
    }

    public int combatStance()
    {
        return this.entityData.get(DATA_COMBAT);
    }

    public void playAttack(RoccoGear.Attack attack)
    {
        triggerAnim("main", attack.animation());
        overrideFace(RoccoExpression.ANGRY, attack.ticks());
    }

    public void triggerRoccoAnim(String animation)
    {
        triggerAnim("main", animation);
    }

    // Una frase de esa categoria como mucho cada tantos ticks (las de pelea, por ejemplo).
    public void sayLineOnce(String category, int cooldownTicks)
    {
        long now = level().getGameTime();
        Long last = this.onceLines.get(category);
        if (last != null && now - last < cooldownTicks)
        {
            return;
        }
        this.onceLines.put(category, now);
        sayLine(category);
    }

    // Inmortal: el golpe que lo habria matado lo deja en medio corazon, invulnerable 3 segundos y
    // un rato sin pelear, detras tuyo, curandose.
    private void immortalKnock()
    {
        this.invulnerableTime = 60;
        this.immortalRetreatTicks = 200;
        setTarget(null);
        stopUsingItem();
        leaveCombat(20);
        sayLineOnce("immortal_knock", 600);
    }

    // ---------- la vida y la tumba ----------

    public RoccoLife life()
    {
        return this.life;
    }

    public int getGravePose()
    {
        return Math.floorMod(this.entityData.get(DATA_GRAVE_POSE), 3);
    }

    void setGravePose(int pose)
    {
        this.entityData.set(DATA_GRAVE_POSE, Math.floorMod(pose, 3));
    }

    // En modo tumba no se muere de verdad (sin dueño, la tumba queda en el spawn del mundo): hace la caida, suelta sus cosas y se va a
    // su tumba. /kill, el vacio y el modo PERMANENT si lo sacan del mundo.
    @Override
    public void die(DamageSource source)
    {
        if (!level().isClientSide && getRoccoState() == RoccoState.ACTIVE
                && RoccoCommonConfig.DEATH_MODE.get() == RoccoCommonConfig.DeathMode.GRAVE
                && !source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)
                && !source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD))
        {
            if (net.minecraftforge.common.ForgeHooks.onLivingDeath(this, source))
            {
                return;
            }
            this.life.beginDying();
            return;
        }
        if (!level().isClientSide)
        {
            ServerPlayer owner = trueOwnerAnywhere();
            if (owner != null)
            {
                owner.sendSystemMessage(Component.translatable("rocco.death.permanent", getDisplayName())
                        .withStyle(net.minecraft.ChatFormatting.RED));
            }
        }
        super.die(source);
    }

    // La muerte de verdad dura lo que la animacion (1,8 s) y no los 20 ticks del juego.
    @Override
    protected void tickDeath()
    {
        ++this.deathTime;
        if (this.deathTime >= RoccoLife.DEATH_TICKS && !level().isClientSide() && !isRemoved())
        {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    // Antes de la caida: deja todo lo que estaba haciendo y queda con 1 de vida (no se muere).
    void prepareForDeath()
    {
        this.share.stop(true);
        interruptActivity();
        if (this.task != RoccoTask.NONE)
        {
            stopTriggeredAnimation("main", null);
            finishJob();
        }
        cancelErrand();
        stopTravelling();
        getNavigation().stop();
        setTarget(null);
        this.fighting = false;
        this.combatCalmTicks = 0;
        this.entityData.set(DATA_COMBAT, RoccoCombat.STANCE_NONE);
        stopUsingItem();
        setTalking(false);
        this.talkTicks = 0;
        this.entityData.set(DATA_POSE, RoccoPose.STANDING.ordinal());
        this.entityData.set(DATA_LID, LID_CLOSED);
        clearFire();
        setHealth(1.0F);
        this.deathTime = 0;
    }

    // Todo lo que lleva, al piso: el inventario, la armadura, el arma y el escudo.
    void dropEverything()
    {
        for (int slot = 0; slot < this.inventory.getContainerSize(); slot++)
        {
            ItemStack stack = this.inventory.getItem(slot);
            if (!stack.isEmpty())
            {
                spawnAtLocation(stack);
            }
        }
        this.inventory.clearContent();
        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            ItemStack worn = gear(slot);
            if (!worn.isEmpty())
            {
                spawnAtLocation(worn.copy());
            }
        }
        this.stashedWeapon = null;
        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    public void lookAtEntity(Player player)
    {
        lookAtPlayer(player);
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose)
    {
        return getRoccoState() == RoccoState.BURIED ? BURIED_SIZE : super.getDimensions(pose);
    }

    // Los ojos a tres cuartos de la altura (con la caja nueva, que ya no le sobra medio bloque).
    @Override
    protected float getStandingEyeHeight(net.minecraft.world.entity.Pose pose, net.minecraft.world.entity.EntityDimensions size)
    {
        return size.height * 0.75F;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key)
    {
        super.onSyncedDataUpdated(key);
        if (DATA_STATE.equals(key))
        {
            refreshDimensions();
        }
    }

    // Muerto (haciendo la caida o invisible esperando para reaparecer) no se lo puede tocar.
    @Override
    public boolean isPickable()
    {
        return getRoccoState() != RoccoState.DYING && super.isPickable();
    }

    // En la tumba, plantado o saliendo de la tierra no es un objetivo para los monstruos.
    @Override
    public boolean canBeSeenAsEnemy()
    {
        return getRoccoState() == RoccoState.ACTIVE && super.canBeSeenAsEnemy();
    }

    // ---------- Roco Cola ----------

    // Se la toma cuando se la piden (o muy de vez en cuando, sola). Siempre es Roco Cola.
    public boolean drinkCola(@Nullable Player toward, boolean requested)
    {
        if (getRoccoState() != RoccoState.ACTIVE || isTravelling() || isInWater())
        {
            return false;
        }
        if (requested && this.life.colaRequestTooSoon())
        {
            sayLine("cola_cooldown");
            return false;
        }
        cancelErrand();
        standUp();
        this.colaRequested = requested;
        perform(RoccoTask.COLA, toward);
        return true;
    }

    // Pedirle otra recien tomada: "¿queres que explote?".
    public boolean colaTooSoon()
    {
        return this.life.colaRequestTooSoon();
    }

    // En vez de lo que eligio para su rato libre, 0,5 % de las veces una Roco Cola.
    private boolean idleCola(Player owner)
    {
        if (!this.life.colaIdleReady() || this.random.nextDouble() >= RoccoCommonConfig.COLA_IDLE_CHANCE.get())
        {
            return false;
        }
        return drinkCola(owner, false);
    }

    // ---------- el poder del cofre de ender ----------

    // Camina hasta el cofre de ender, se lo traga (el bloque desaparece sin soltar nada: lo que
    // hay adentro es tuyo, no del bloque) y siente el poder.
    void swallowEnderChest(BlockPos target)
    {
        ItemStack chest = new ItemStack(net.minecraft.world.item.Items.ENDER_CHEST);
        queueJob(Job.custom(RoccoTask.SWALLOWING, target, chest, level ->
        {
            if (level.getBlockState(target).getBlock() instanceof net.minecraft.world.level.block.EnderChestBlock)
            {
                level.removeBlock(target, false);
                this.life.startAwakening();
                queueEnderAwaken();
            }
        }, null).reaching(2.5D));
    }

    // Uno que tiene en el inventario: lo hace flotar entre las manitos y le entra en la panza.
    void absorbEnderChest()
    {
        for (int slot = 0; slot < this.inventory.getContainerSize(); slot++)
        {
            ItemStack stack = this.inventory.getItem(slot);
            if (stack.is(net.minecraft.world.item.Items.ENDER_CHEST))
            {
                stack.shrink(1);
                this.inventory.setChanged();
                break;
            }
        }
        this.life.startAwakening();
        ServerPlayer owner = trueOwner();
        if (owner != null)
        {
            lookAtPlayer(owner);
        }
        queueJob(Job.custom(RoccoTask.ENDER_ABSORB, blockPosition(), new ItemStack(net.minecraft.world.item.Items.ENDER_CHEST),
                level -> { }, null).reaching(64.0D));
        queueEnderAwaken();
    }

    private void queueEnderAwaken()
    {
        queueJob(Job.custom(RoccoTask.ENDER_AWAKEN, blockPosition(), ItemStack.EMPTY,
                level -> this.life.grantEnderPocket(), null).reaching(64.0D));
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
                .triggerableAnim("dance", DANCE)
                .triggerableAnim("clap", CLAP)
                .triggerableAnim("celebrate", CELEBRATE)
                .triggerableAnim("cola_drink", COLA_DRINK)
                .triggerableAnim("equip_proud", EQUIP_PROUD)
                .triggerableAnim("ender_absorb", ENDER_ABSORB)
                .triggerableAnim("ender_awaken", ENDER_AWAKEN)
                .triggerableAnim("attack_punch", ATTACK_PUNCH)
                .triggerableAnim("attack_swing", ATTACK_SWING)
                .triggerableAnim("attack_chop", ATTACK_CHOP)
                .triggerableAnim("attack_thrust", ATTACK_THRUST)
                .triggerableAnim("bow_draw", BOW_DRAW)
                .triggerableAnim("bow_release", BOW_RELEASE)
                .triggerableAnim("crossbow_charge", CROSSBOW_CHARGE)
                .triggerableAnim("crossbow_shoot", CROSSBOW_SHOOT)
                .triggerableAnim("food_eat", FOOD_EAT)
                .triggerableAnim("food_eat_bad", FOOD_EAT_BAD)
                .triggerableAnim("potion_drink", POTION_DRINK)
                .triggerableAnim("potion_drink_bad", POTION_DRINK_BAD)
                // Las animaciones traen sus particulas (florcitas, vapor, runas): las dibuja el cliente.
                .setParticleKeyframeHandler(event -> PARTICLE_HOOK.spawn(this,
                        event.getKeyframeData().getEffect(), event.getKeyframeData().getLocator())));

        // La boca se mueve mientras habla (lo que dice el servidor, o el audio de su voz sonando en
        // este juego) y mientras canta. Nunca con la boca ocupada comiendo o tomando.
        controllers.add(new AnimationController<>(this, "mouth", 0, state ->
                (isTalking() || SPEAKING_HOOK.test(this) || getActiveTask().singing()) && !mouthBusy()
                        ? state.setAndContinue(TALK) : PlayState.STOP));

        controllers.add(new AnimationController<>(this, "blink", 0, state ->
                getRoccoState().isInert() || getExpression().eyesClosed() || getActiveTask().consuming()
                        ? PlayState.STOP : state.setAndContinue(BLINK)));
    }

    private PlayState mainAnimation(AnimationState<RoccoEntity> state)
    {
        // Cantando, al ritmo de la cancion: sing_groove esta hecho a 96 pulsos, sing_rap a 92 y
        // sing_soft a 50 (ROCCO-COLA-COMBATE-ARMADURAS-CANTO.md). Los bailes quedan a su ritmo,
        // asi sus caras siguen a tiempo.
        state.getController().setAnimationSpeed(singSpeed());

        // Muerte para siempre (modo PERMANENT o /kill): la caida, en vez del giro rojo del juego.
        if (isDeadOrDying())
        {
            return state.setAndContinue(DEATH);
        }
        return switch (getRoccoState())
        {
            case PLANT -> state.setAndContinue(PLANT_IDLE);
            case AWAKENING -> state.setAndContinue(AWAKEN);
            case ACTIVE -> state.setAndContinue(activeAnimation(state));
            case DYING -> state.setAndContinue(DEATH);
            case BURIED -> state.setAndContinue(GRAVE[getGravePose()]);
            case RISING -> state.setAndContinue(GRAVE_RISE[getGravePose()]);
        };
    }

    private double singSpeed()
    {
        RoccoTask active = getActiveTask();
        if (!active.singing())
        {
            return 1.0D;
        }
        double bpm = BEAT_HOOK.applyAsDouble(this);
        if (bpm <= 0.0D)
        {
            return 1.0D;
        }
        double base = switch (active)
        {
            case SING_SOFT -> 50.0D;
            case SING_RAP -> 92.0D;
            default -> 96.0D;
        };
        // Una balada que el contador lee al doble (los golpes de la bateria) va a la mitad.
        if (active == RoccoTask.SING_SOFT && bpm > 100.0D)
        {
            bpm /= 2.0D;
        }
        return net.minecraft.util.Mth.clamp(bpm / base, 0.6D, 1.6D);
    }

    @Nullable
    private RawAnimation musicAnimation()
    {
        return switch (getActiveTask())
        {
            case DANCE_FIESTA -> DANCE_FIESTA;
            case DANCE_MENEITO -> DANCE_MENEITO;
            case SING_SOFT -> SING_SOFT;
            case SING_GROOVE -> SING_GROOVE;
            case SING_RAP -> SING_RAP;
            default -> null;
        };
    }

    private RawAnimation activeAnimation(AnimationState<RoccoEntity> state)
    {
        int lid = this.entityData.get(DATA_LID);
        boolean garden = this.entityData.get(DATA_GARDEN_LID);
        if (lid == LID_OPENED)
        {
            return garden ? LID_OPEN_LOOP_GARDEN : LID_OPEN_LOOP;
        }
        if (lid == LID_CLOSING)
        {
            return garden ? LID_CLOSE_GARDEN : LID_CLOSE;
        }

        RawAnimation sharing = MateShare.Step.byId(this.entityData.get(DATA_MATE_STEP)).animation();
        if (sharing != null)
        {
            return sharing;
        }

        // Volando, flotando, buceando o en el barquito: la animacion la decide el servidor.
        RawAnimation travelling = getMotion().animation();
        if (travelling != null)
        {
            return travelling;
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

        // Peleando: en guardia (o corriendo con el arma), apuntando o detras del escudo.
        switch (this.entityData.get(DATA_COMBAT))
        {
            case RoccoCombat.STANCE_GUARD -> { return state.isMoving() ? COMBAT_RUN : COMBAT_IDLE; }
            case RoccoCombat.STANCE_BOW -> { return BOW_AIM; }
            case RoccoCombat.STANCE_CROSSBOW -> { return CROSSBOW_AIM; }
            case RoccoCombat.STANCE_SHIELD -> { return SHIELD_BLOCK; }
            default -> { }
        }

        // Bailando o cantando con la musica: el bucle sigue mientras dure la tarea.
        RawAnimation music = musicAnimation();
        if (music != null)
        {
            return music;
        }

        if (!state.isMoving())
        {
            // Con la mano en el menton mientras espera a la IA (solo si esta quieto y libre).
            return isThinking() ? THINK : IDLE;
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

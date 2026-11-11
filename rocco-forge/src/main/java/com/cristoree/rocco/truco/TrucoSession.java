package com.cristoree.rocco.truco;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoExpression;
import com.cristoree.rocco.entity.RoccoScene;
import com.cristoree.rocco.entity.RoccoState;
import com.cristoree.rocco.registry.ModEntities;
import com.cristoree.rocco.registry.ModParticles;
import com.cristoree.rocco.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntFunction;

// Una partida de truco con Rocco (ROCCO-V14 7.1 a 7.9): arma la mesa, sienta a todos, lleva el motor
// (TrucoGame) y traduce lo que pasa a animaciones de Rocco, sonidos, chat y la pantalla de cada uno.
// Rocco decide con RoccoAI en otro hilo (a veces tarda mas de un tick); mientras piensa, las jugadas de
// los demas esperan en una cola.
@Mod.EventBusSubscriber(modid = RoccoMod.MODID)
public final class TrucoSession
{
    public enum End { FINISHED, LEFT, DANGER }

    private enum Stage { WALK, SUMMON, SETTLE, PLAY, CLOSING, DONE }

    private enum MateStep { NONE, CEBAR, WAIT, BACK, MATEAR }

    private static final List<TrucoSession> ALL = new ArrayList<>();
    private static final ExecutorService AI = Executors.newSingleThreadExecutor(runnable ->
    {
        Thread thread = new Thread(runnable, "Rocco truco");
        thread.setDaemon(true);
        return thread;
    });

    private static final int WALK_TICKS = 100;
    private static final int SPAWN_TICK = 20;
    private static final int SEAT_TICK = 27;
    private static final int HAND_PAUSE = 60;
    private static final int GAME_OVER_TICKS = 80;
    private static final int CLOSE_TICKS = 10;
    private static final int MATE_AUTO_TICKS = 160;
    private static final int RUEDA_COOLDOWN = 240;
    // Los tiempos dentro de las animaciones (en ticks desde que arrancan).
    private static final int PLAY_FAN_TICK = 6;
    private static final int PLAY_REVEAL_TICK = 17;
    private static final int DEAL_HANDS_TICK = 60;
    private static final int DEAL_MUESTRA_TICK = 66;
    private static final int CEBAR_GIVE_TICK = 88;
    private static final int BACK_TAKE_TICK = 11;
    private static final int MAZO_FAN_TICK = 12;
    private static final int SWEEP_TICK = 8;

    private final RoccoEntity rocco;
    private final ServerLevel level;
    private final TrucoLayout.Plan plan;
    private final TrucoLayout.Spot spot;
    private final int[] options;
    // Por asiento de la partida: el lugar de la mesa y el jugador (null = Rocco).
    private final int[] seatPlace;
    private final UUID[] seatPlayer;
    private final int roccoSeat;
    private final Rules rules;
    private final RoccoAI ai;
    @Nullable
    private TrucoGame game;
    @Nullable
    private TrucoTableEntity table;
    private final Map<Integer, TrucoChairEntity> chairs = new HashMap<>();

    private Stage stage = Stage.WALK;
    private int stageTicks;
    private End end = End.FINISHED;

    // La animacion de Rocco y las que esperan.
    private RoccoScene anim = RoccoScene.NONE;
    private int animTicks;
    private final Deque<RoccoScene> animQueue = new ArrayDeque<>();
    private int fan;

    // Rocco pensando su jugada.
    private int waitTicks;
    private int thinkDelay;
    private int waitVersion = -1;
    private int version;
    @Nullable
    private CompletableFuture<RoccoAI.Decision> deciding;
    private boolean thinking;

    private record Queued(int seat, Action action) {}

    private final Deque<Queued> queued = new ArrayDeque<>();

    // Lo que se ve en la mesa y en la pantalla.
    private int maskedTrick = -1;
    private long dealAt = -1;
    private long sweepAt = -1;
    private boolean handsShown;
    private boolean muestraShown;
    private final Deque<IntFunction<Component>> log = new ArrayDeque<>();
    private IntFunction<Component> banner = seat -> Component.empty();
    private int bannerId;
    private int bannerTeam = -1;
    private boolean dirty = true;
    // Lo ultimo que se le mando a la pantalla sobre si se puede jugar (para avisar cuando cambia).
    private boolean sentBlocked = true;

    // Fin de la mano y de la partida.
    private int handOverTicks = -1;
    private int gameOverTicks = -1;
    private boolean senaDone;

    // El mate.
    private MateStep mateStep = MateStep.NONE;
    private int mateTicks;
    private int mateSeat = -1;
    private int mateDir;
    private boolean mateInHand;
    private boolean mateBetweenHands;
    private boolean mateServed;
    private int ruedaIndex;
    private int ruedaServed;
    private int ruedaCooldown = RUEDA_COOLDOWN;

    private final Map<UUID, Long> leavePrompt = new HashMap<>();

    private TrucoSession(RoccoEntity rocco, TrucoLayout.Plan plan, TrucoLayout.Spot spot, List<ServerPlayer> humans, int[] options)
    {
        this.rocco = rocco;
        this.level = (ServerLevel) rocco.level();
        this.plan = plan;
        this.spot = spot;
        this.options = options;
        int n = plan.gamePlayers();
        this.seatPlace = new int[n];
        this.seatPlayer = new UUID[n];
        int[] places = plan.places();
        if (plan.roccoPlays())
        {
            this.roccoSeat = 0;
            this.seatPlace[0] = places[0];
            for (int seat = 1; seat < n; seat++)
            {
                this.seatPlace[seat] = places[seat];
                this.seatPlayer[seat] = humans.get(seat - 1).getUUID();
            }
        }
        else
        {
            this.roccoSeat = -1;
            for (int seat = 0; seat < n; seat++)
            {
                this.seatPlace[seat] = places[seat + 1];
                this.seatPlayer[seat] = humans.get(seat).getUUID();
            }
        }
        this.rules = new Rules();
        this.rules.variant = options[TrucoNetwork.OPT_VARIANT] == 0 ? Rules.Variant.URUGUAYO : Rules.Variant.ARGENTINO;
        this.rules.targetPoints = options[TrucoNetwork.OPT_POINTS] == 15 ? 15 : 30;
        this.rules.conFlor = this.rules.variant == Rules.Variant.URUGUAYO || options[TrucoNetwork.OPT_FLOR] == 1;
        this.rules.manoSoloDeATres = true;
        RoccoAI.Level level = switch (options[TrucoNetwork.OPT_LEVEL])
        {
            case 0 -> RoccoAI.Level.TRANQUI;
            case 2 -> RoccoAI.Level.PICARO;
            default -> RoccoAI.Level.NORMAL;
        };
        this.ai = new RoccoAI(level, rocco.getRandom().nextLong());
        this.mateBetweenHands = options[TrucoNetwork.OPT_MATE] == 0 && this.roccoSeat >= 0 && n == 2;
    }

    // ---------- crear ----------

    @Nullable
    static TrucoSession create(RoccoEntity rocco, ServerPlayer host, List<ServerPlayer> humans, int[] options)
    {
        boolean manoAMano = options[TrucoNetwork.OPT_TWO] == 1;
        TrucoLayout.Plan plan = TrucoLayout.plan(Math.min(TrucoLobby.MAX_PLAYERS, humans.size()), manoAMano);
        List<ServerPlayer> playing = humans.subList(0, Math.min(TrucoLobby.MAX_PLAYERS, humans.size()));
        // Rocco mira hacia el que lo invito.
        double dx = host.getX() - rocco.getX();
        double dz = host.getZ() - rocco.getZ();
        float facing = dx * dx + dz * dz < 0.01D ? rocco.getYRot() : TrucoLayout.yawOf(new Vec3(dx, 0.0D, dz));
        TrucoLayout.Spot spot = TrucoLayout.find(rocco.level(), rocco.position(), facing, plan.size(), plan.places(), 6);
        if (spot == null)
        {
            return null;
        }
        TrucoSession session = new TrucoSession(rocco, plan, spot, playing, options);
        rocco.interruptActivity();
        rocco.cancelErrand();
        rocco.cooking().stop(true);
        rocco.mateShare().stop(true);
        rocco.standUp();
        rocco.setTruco(session);
        rocco.setTarget(null);
        ALL.add(session);
        rocco.sayLine(RoccoExpression.HAPPY, "truco_start");
        rocco.getNavigation().moveTo(spot.rocco().x, spot.rocco().y, spot.rocco().z, 1.0D);
        return session;
    }

    @Nullable
    public static TrucoSession of(Player player)
    {
        for (TrucoSession session : ALL)
        {
            if (session.isSeated(player))
            {
                return session;
            }
        }
        return null;
    }

    public boolean isSeated(Player player)
    {
        for (UUID id : this.seatPlayer)
        {
            if (player.getUUID().equals(id))
            {
                return true;
            }
        }
        return false;
    }

    // Rocco camina hasta su lugar (todavia no esta quieto).
    public boolean walking()
    {
        return this.stage == Stage.WALK;
    }

    // ---------- el tick ----------

    private void tick()
    {
        this.stageTicks++;
        if (!roccoOk())
        {
            close(End.DANGER, true);
        }
        switch (this.stage)
        {
            case WALK -> tickWalk();
            case SUMMON -> tickSummon();
            case SETTLE -> tickSettle();
            case PLAY -> tickPlay();
            case CLOSING -> tickClosing();
            case DONE -> { }
        }
    }

    private boolean roccoOk()
    {
        return this.stage == Stage.CLOSING || this.stage == Stage.DONE
                || (this.rocco.isAlive() && !this.rocco.isRemoved() && this.rocco.getRoccoState() == RoccoState.ACTIVE
                && this.rocco.level() == this.level);
    }

    private void tickWalk()
    {
        Vec3 target = this.spot.rocco();
        double dx = this.rocco.getX() - target.x;
        double dz = this.rocco.getZ() - target.z;
        boolean there = dx * dx + dz * dz < 0.3D * 0.3D && Math.abs(this.rocco.getY() - target.y) < 0.6D;
        if (!there && this.stageTicks < WALK_TICKS)
        {
            if (this.stageTicks % 20 == 0 || this.rocco.getNavigation().isDone())
            {
                if (!this.rocco.getNavigation().moveTo(target.x, target.y, target.z, 1.0D) && this.stageTicks > 20)
                {
                    this.stageTicks = WALK_TICKS;
                }
            }
            return;
        }
        // Llego (o no encontro el camino y aparece ahi, con un poco de magia).
        if (!there)
        {
            puff(this.rocco.position());
            puff(target);
        }
        this.rocco.getNavigation().stop();
        this.rocco.moveTo(target.x, target.y, target.z, this.spot.yaw(), 0.0F);
        this.rocco.holdStill(this.spot.yaw());
        setStage(Stage.SUMMON);
        playAnim(RoccoScene.TRUCO_SUMMON);
    }

    private void tickSummon()
    {
        this.rocco.holdStill(this.spot.yaw());
        tickAnim();
        if (this.stageTicks == SPAWN_TICK + RoccoEntity.ANIM_LAG)
        {
            spawnFurniture();
        }
        if (this.stageTicks == SEAT_TICK + RoccoEntity.ANIM_LAG)
        {
            seatPlayers();
        }
        if (this.stageTicks >= RoccoScene.TRUCO_SUMMON.ticks + RoccoEntity.ANIM_LAG)
        {
            TrucoChairEntity chair = this.chairs.get(0);
            if (chair == null || this.table == null)
            {
                close(End.DANGER, true);
                return;
            }
            this.rocco.startRiding(chair, true);
            setStage(Stage.SETTLE);
            playAnim(RoccoScene.TRUCO_SETTLE);
        }
    }

    private void tickSettle()
    {
        tickAnim();
        if (this.stageTicks >= RoccoScene.TRUCO_SETTLE.ticks + RoccoEntity.ANIM_LAG)
        {
            int n = this.plan.gamePlayers();
            int dealer = this.roccoSeat >= 0 ? this.roccoSeat : n - 1;
            this.game = new TrucoGame(this.rules, n, this.rocco.getRandom().nextLong(), dealer);
            setStage(Stage.PLAY);
            Vec3 center = this.spot.center().add(0.0D, TrucoLayout.TOP / 16.0D, 0.0D);
            for (ServerPlayer player : humans())
            {
                TrucoNetwork.send(player, new TrucoNetwork.Open(this.table == null ? -1 : this.table.getId(), center.x, center.y, center.z));
            }
            drain();
            this.dirty = true;
        }
    }

    private void tickPlay()
    {
        TrucoGame game = this.game;
        if (game == null)
        {
            close(End.DANGER, true);
            return;
        }
        if (!checkPlayers())
        {
            return;
        }
        tickAnim();
        tickMate();

        // Las jugadas que esperaban a que Rocco terminara su animacion o su decision.
        while (!blocked() && !this.queued.isEmpty())
        {
            Queued next = this.queued.poll();
            if (game.apply(next.seat, next.action))
            {
                this.version++;
                drain();
            }
        }

        tickRocco(game);
        tickHandOver(game);

        if (this.gameOverTicks >= 0 && ++this.gameOverTicks >= GAME_OVER_TICKS)
        {
            close(End.FINISHED, false);
            return;
        }
        // Termino una animacion de Rocco o su decision: los botones vuelven a andar.
        if (blocked() != this.sentBlocked)
        {
            this.dirty = true;
        }
        if (this.dirty)
        {
            this.dirty = false;
            this.sentBlocked = blocked();
            sendState();
        }
    }

    private void tickClosing()
    {
        if (this.stageTicks == CLOSE_TICKS)
        {
            if (this.table != null)
            {
                this.table.discard();
            }
            for (TrucoChairEntity chair : this.chairs.values())
            {
                chair.ejectPassengers();
                chair.discard();
            }
            setStage(Stage.DONE);
        }
    }

    private void setStage(Stage stage)
    {
        this.stage = stage;
        this.stageTicks = 0;
    }

    // ---------- armar y desarmar ----------

    private void spawnFurniture()
    {
        TrucoTableEntity table = new TrucoTableEntity(ModEntities.TRUCO_MESA.get(), this.level);
        table.setup(this.plan.size(), this.spot.yaw());
        Vec3 center = this.spot.center();
        table.moveTo(center.x, center.y, center.z, this.spot.yaw(), 0.0F);
        table.setup(this.plan.size(), this.spot.yaw());
        this.level.addFreshEntity(table);
        this.table = table;
        puff(center.add(0.0D, 0.5D, 0.0D));
        for (int place : this.plan.places())
        {
            TrucoChairEntity chair = new TrucoChairEntity(ModEntities.TRUCO_SILLA.get(), this.level);
            Vec3 at = TrucoLayout.chairPosition(center, this.spot.yaw(), this.plan.size(), place);
            float yaw = TrucoLayout.chairYaw(this.spot.yaw(), this.plan.size(), place);
            chair.setup(place == 0, yaw);
            chair.moveTo(at.x, at.y, at.z, yaw, 0.0F);
            this.level.addFreshEntity(chair);
            this.chairs.put(place, chair);
            if (place != 0)
            {
                puff(at.add(0.0D, 0.4D, 0.0D));
            }
        }
        updateView();
    }

    private void seatPlayers()
    {
        for (int seat = 0; seat < this.seatPlayer.length; seat++)
        {
            ServerPlayer player = player(seat);
            TrucoChairEntity chair = this.chairs.get(this.seatPlace[seat]);
            if (player == null || chair == null)
            {
                continue;
            }
            player.stopRiding();
            player.stopSleeping();
            if (player.isFallFlying())
            {
                player.stopFallFlying();
            }
            player.startRiding(chair, true);
        }
    }

    // Termina la partida. now: sin animacion (un peligro, Rocco no esta). Si no, Rocco hace la suya.
    public void abandon(End why)
    {
        if (this.stage == Stage.CLOSING || this.stage == Stage.DONE)
        {
            return;
        }
        // Ya se esta terminando (la partida termino o Rocco esta haciendo la de perder): no se repite.
        if (this.gameOverTicks >= 0 && why != End.DANGER)
        {
            return;
        }
        if (why == End.DANGER)
        {
            this.rocco.sayLine(RoccoExpression.SCARED, "truco_danger");
            close(End.DANGER, true);
            return;
        }
        // Alguien se levanto: Rocco hace la de perder y lo dice.
        for (ServerPlayer player : humans())
        {
            player.sendSystemMessage(Component.translatable("gui.rocco.truco.left").withStyle(ChatFormatting.GRAY));
        }
        this.rocco.sayLine(RoccoExpression.SAD, "truco_abandon");
        if (this.stage == Stage.PLAY)
        {
            this.animQueue.clear();
            startAnim(RoccoScene.TRUCO_LOSE_HAND);
            this.gameOverTicks = GAME_OVER_TICKS - RoccoScene.TRUCO_LOSE_HAND.ticks - RoccoEntity.ANIM_LAG;
            this.handOverTicks = -1;
            this.end = End.LEFT;
            this.queued.clear();
            return;
        }
        close(End.LEFT, true);
    }

    private void close(End why, boolean now)
    {
        if (this.stage == Stage.CLOSING || this.stage == Stage.DONE)
        {
            return;
        }
        this.end = why;
        // Primero se marca que termina: asi las sillas dejan bajar a todos sin preguntar.
        setStage(Stage.CLOSING);
        if (this.deciding != null)
        {
            this.deciding.cancel(true);
            this.deciding = null;
        }
        for (ServerPlayer player : humans())
        {
            TrucoNetwork.send(player, new TrucoNetwork.Close());
        }
        // Todos se paran y los muebles se van.
        for (TrucoChairEntity chair : this.chairs.values())
        {
            chair.ejectPassengers();
            chair.leave();
        }
        if (this.table != null)
        {
            this.table.leave();
        }
        if (this.rocco.isPassenger())
        {
            this.rocco.stopRiding();
        }
        if (this.rocco.truco() == this)
        {
            this.rocco.setTruco(null);
        }
        this.rocco.playScene(RoccoScene.NONE);
        this.rocco.setSceneBones(0);
        this.rocco.getNavigation().stop();
        if (now && this.table == null)
        {
            setStage(Stage.DONE);
        }
    }

    private void puff(Vec3 at)
    {
        this.level.sendParticles(ModParticles.MAGIC_BURST.get(), at.x, at.y, at.z, 6, 0.25D, 0.2D, 0.25D, 0.02D);
    }

    // Que nadie se haya ido: desconectado, muerto, en otro mundo o bajado de su silla.
    private boolean checkPlayers()
    {
        for (int seat = 0; seat < this.seatPlayer.length; seat++)
        {
            if (this.seatPlayer[seat] == null)
            {
                continue;
            }
            ServerPlayer player = player(seat);
            TrucoChairEntity chair = this.chairs.get(this.seatPlace[seat]);
            if (player == null || !player.isAlive() || player.level() != this.level || chair == null || player.getVehicle() != chair)
            {
                if (this.gameOverTicks < 0)
                {
                    abandon(End.LEFT);
                }
                return this.stage == Stage.PLAY;
            }
        }
        TrucoChairEntity mine = this.chairs.get(0);
        if (mine != null && this.rocco.getVehicle() != mine)
        {
            this.rocco.startRiding(mine, true);
        }
        return true;
    }

    // ---------- las animaciones de Rocco ----------

    private void playAnim(RoccoScene scene)
    {
        if (this.anim == RoccoScene.NONE || this.anim.loops)
        {
            startAnim(scene);
        }
        else
        {
            this.animQueue.add(scene);
        }
    }

    private void startAnim(RoccoScene scene)
    {
        this.anim = scene;
        this.animTicks = 0;
        this.rocco.playScene(scene);
        if (scene == RoccoScene.TRUCO_DEAL)
        {
            this.dealAt = this.level.getGameTime() + RoccoEntity.ANIM_LAG;
            this.handsShown = false;
            this.muestraShown = false;
            this.fan = 0;
            this.dirty = true;
        }
        if (scene == RoccoScene.TRUCO_WIN_HAND)
        {
            this.sweepAt = this.level.getGameTime() + RoccoEntity.ANIM_LAG + SWEEP_TICK;
        }
        updateFan();
        updateView();
    }

    private void tickAnim()
    {
        if (this.anim == RoccoScene.NONE)
        {
            return;
        }
        this.animTicks++;
        int t = this.animTicks - RoccoEntity.ANIM_LAG;
        TrucoGame game = this.game;
        switch (this.anim)
        {
            case TRUCO_PLAY_1, TRUCO_PLAY_2, TRUCO_PLAY_3 ->
            {
                if (t == PLAY_FAN_TICK && game != null && this.roccoSeat >= 0)
                {
                    this.fan = game.cardsLeft(this.roccoSeat);
                    updateFan();
                }
                if (t == PLAY_REVEAL_TICK)
                {
                    this.maskedTrick = -1;
                    sound(ModSounds.CARD_SLAP.get(), 0.8F);
                    this.dirty = true;
                    updateView();
                }
            }
            case TRUCO_DEAL ->
            {
                if (t == DEAL_HANDS_TICK)
                {
                    this.handsShown = true;
                    if (game != null && this.roccoSeat >= 0)
                    {
                        this.fan = game.cardsLeft(this.roccoSeat);
                    }
                    this.dirty = true;
                    sena();
                }
                if (t == DEAL_MUESTRA_TICK)
                {
                    this.muestraShown = true;
                    this.dirty = true;
                    updateView();
                }
            }
            case TRUCO_MAZO ->
            {
                if (t == MAZO_FAN_TICK)
                {
                    this.fan = 0;
                    updateFan();
                }
            }
            default -> { }
        }
        if (!this.anim.loops && this.animTicks >= this.anim.ticks + RoccoEntity.ANIM_LAG)
        {
            RoccoScene next = this.animQueue.poll();
            if (this.anim == RoccoScene.TRUCO_DEAL)
            {
                // Por si la animacion se corto antes: las cartas y la muestra se ven igual.
                this.handsShown = true;
                this.muestraShown = true;
                this.dirty = true;
                updateView();
            }
            startAnim(next != null ? next : idleAnim());
        }
    }

    private boolean animIdle()
    {
        return this.anim == RoccoScene.TRUCO_IDLE || this.anim == RoccoScene.TRUCO_IDLE_NOCARDS
                || this.anim == RoccoScene.TRUCO_THINK || this.anim == RoccoScene.NONE;
    }

    private RoccoScene idleAnim()
    {
        if (this.mateStep == MateStep.WAIT)
        {
            return mateWait();
        }
        TrucoGame game = this.game;
        if (this.roccoSeat < 0 || game == null || game.cardsLeft(this.roccoSeat) == 0 || this.handOverTicks >= 0
                || game.phase() == TrucoGame.Phase.GAME_OVER)
        {
            return RoccoScene.TRUCO_IDLE_NOCARDS;
        }
        return RoccoScene.TRUCO_IDLE;
    }

    private void updateFan()
    {
        int shown = this.roccoSeat < 0 ? 0 : Math.min(3, this.fan);
        this.rocco.setSceneBones(RoccoScene.trucoBits(shown));
    }

    // Mientras Rocco tira una carta, canta o reparte, nadie juega (las jugadas esperan).
    private boolean blocked()
    {
        if (this.deciding != null)
        {
            return true;
        }
        return switch (this.anim)
        {
            case TRUCO_PLAY_1, TRUCO_PLAY_2, TRUCO_PLAY_3, TRUCO_CALL_TRUCO, TRUCO_CALL_ENVIDO, TRUCO_CALL_FLOR,
                 TRUCO_QUIERO, TRUCO_NO_QUIERO, TRUCO_DEAL -> true;
            default -> !this.animQueue.isEmpty() && this.animQueue.stream().anyMatch(scene -> scene == RoccoScene.TRUCO_DEAL);
        };
    }

    // ---------- el turno de Rocco ----------

    private void tickRocco(TrucoGame game)
    {
        if (this.roccoSeat < 0 || this.gameOverTicks >= 0)
        {
            return;
        }
        if (this.deciding != null)
        {
            if (!this.deciding.isDone())
            {
                return;
            }
            RoccoAI.Decision decision = null;
            try
            {
                decision = this.deciding.join();
            }
            catch (RuntimeException ignored)
            {
                // Si algo fallo, juega lo primero que pueda.
            }
            this.deciding = null;
            List<Action> legal = game.legalActions(this.roccoSeat);
            if (legal.isEmpty())
            {
                return;
            }
            if (decision == null || decision.action() == null || !game.isLegal(this.roccoSeat, decision.action()))
            {
                decision = new RoccoAI.Decision(firstPlay(legal), "");
            }
            applyRocco(game, decision);
            return;
        }

        List<Action> legal = game.legalActions(this.roccoSeat);
        if (legal.isEmpty() || this.handOverTicks >= 0 || !this.handsShown)
        {
            this.waitTicks = 0;
            this.waitVersion = -1;
            setThinking(false);
            return;
        }
        // Termina primero lo que esta haciendo (una carta, un canto, cebar un mate).
        if (!animIdle() || !this.animQueue.isEmpty() || (this.mateStep != MateStep.NONE && this.mateStep != MateStep.WAIT))
        {
            return;
        }
        if (this.mateStep == MateStep.WAIT)
        {
            return;
        }
        if (this.waitVersion != this.version)
        {
            // Algo cambio (otra jugada, un canto): vuelve a pensar desde cero.
            this.waitVersion = this.version;
            this.waitTicks = 0;
            boolean response = game.phase() == TrucoGame.Phase.RESPONSE;
            this.thinkDelay = response ? 15 + this.rocco.getRandom().nextInt(16) : 20 + this.rocco.getRandom().nextInt(26);
            // En equipo espera un poco por si contesta un compañero.
            if (response && humanTeammateCanAnswer(game))
            {
                this.thinkDelay = Math.max(this.thinkDelay, 40);
            }
        }
        this.waitTicks++;
        if (this.waitTicks > 30 && game.phase() != TrucoGame.Phase.RESPONSE && this.anim != RoccoScene.TRUCO_THINK)
        {
            startAnim(RoccoScene.TRUCO_THINK);
            setThinking(true);
        }
        if (this.waitTicks >= this.thinkDelay)
        {
            TrucoGame frozen = game;
            int seat = this.roccoSeat;
            RoccoAI brain = this.ai;
            this.deciding = CompletableFuture.supplyAsync(() -> brain.decide(frozen, seat), AI);
        }
    }

    private boolean humanTeammateCanAnswer(TrucoGame game)
    {
        for (int seat = 0; seat < game.n; seat++)
        {
            if (seat != this.roccoSeat && this.seatPlayer[seat] != null && game.mustRespond(seat))
            {
                return true;
            }
        }
        return false;
    }

    private static Action firstPlay(List<Action> legal)
    {
        for (Action action : legal)
        {
            if (action.type() == Action.Type.PLAY || action.type() == Action.Type.QUIERO || action.type() == Action.Type.DISCARD)
            {
                return action;
            }
        }
        return legal.get(0);
    }

    private void setThinking(boolean thinking)
    {
        if (this.thinking != thinking)
        {
            this.thinking = thinking;
            this.dirty = true;
        }
    }

    private void applyRocco(TrucoGame game, RoccoAI.Decision decision)
    {
        setThinking(false);
        this.waitTicks = 0;
        if (this.anim == RoccoScene.TRUCO_THINK)
        {
            startAnim(idleAnim());
        }
        say(decision);
        if (game.apply(this.roccoSeat, decision.action()))
        {
            this.version++;
            drain();
        }
    }

    // Lo que dice Rocco segun lo que decidio (7.9).
    private void say(RoccoAI.Decision decision)
    {
        String mood = decision.mood() == null ? "" : decision.mood();
        switch (decision.action().type())
        {
            case TRUCO, RETRUCO, VALE_CUATRO -> this.rocco.sayLine(mood.equals("farol") ? RoccoExpression.SERIOUS : RoccoExpression.SMUG,
                    mood.equals("farol") ? "truco_bluff" : "truco_call");
            case QUIERO -> this.rocco.sayLine(RoccoExpression.SERIOUS, "truco_quiero");
            case NO_QUIERO, CON_FLOR_ME_ACHICO -> this.rocco.sayLine(RoccoExpression.BORED, "truco_no_quiero");
            case ENVIDO, REAL_ENVIDO -> this.rocco.sayLine(RoccoExpression.SMUG, "truco_envido");
            case FALTA_ENVIDO -> this.rocco.sayLine(RoccoExpression.SMUG, "truco_falta");
            case FLOR, CONTRAFLOR_AL_RESTO -> this.rocco.sayLine(RoccoExpression.CHEER, "truco_flor");
            case MAZO -> this.rocco.sayLine(RoccoExpression.SAD, "truco_mazo");
            default -> { }
        }
    }

    // ---------- las jugadas de los jugadores ----------

    void humanAction(ServerPlayer player, int type, int card)
    {
        TrucoGame game = this.game;
        if (game == null || this.stage != Stage.PLAY || this.gameOverTicks >= 0)
        {
            return;
        }
        int seat = seatOf(player);
        Action.Type[] types = Action.Type.values();
        if (seat < 0 || type < 0 || type >= types.length)
        {
            return;
        }
        Action action = new Action(types[type], card);
        if (blocked())
        {
            if (this.queued.size() < 4 && this.queued.stream().noneMatch(q -> q.seat == seat))
            {
                this.queued.add(new Queued(seat, action));
            }
            return;
        }
        if (game.apply(seat, action))
        {
            this.version++;
            drain();
        }
        this.dirty = true;
    }

    // ---------- lo que paso en la mesa ----------

    private void drain()
    {
        TrucoGame game = this.game;
        if (game == null)
        {
            return;
        }
        for (TrucoEvent event : game.drainEvents())
        {
            handle(game, event);
        }
        this.dirty = true;
        updateView();
    }

    private void handle(TrucoGame game, TrucoEvent e)
    {
        boolean mine = e.seat() >= 0 && e.seat() == this.roccoSeat;
        switch (e.kind())
        {
            case NEW_HAND ->
            {
                this.maskedTrick = -1;
                this.sweepAt = -1;
                this.senaDone = false;
                this.mateServed = false;
                this.handOverTicks = -1;
                int hand = e.value();
                int dealer = e.seat();
                addLog(seat -> Component.translatable("gui.rocco.truco.hand", hand, name(dealer)).withStyle(ChatFormatting.DARK_GRAY));
                playAnim(RoccoScene.TRUCO_DEAL);
            }
            case DISCARDED ->
            {
                int who = e.seat();
                addLog(seat -> Component.translatable("gui.rocco.truco.discarded", name(who)).withStyle(ChatFormatting.GRAY));
                if (mine)
                {
                    this.fan = game.cardsLeft(this.roccoSeat);
                    updateFan();
                }
            }
            case CARD_PLAYED ->
            {
                if (mine)
                {
                    this.maskedTrick = e.value();
                    this.fan = game.cardsLeft(this.roccoSeat) + 1;
                    playAnim(switch (e.value())
                    {
                        case 0 -> RoccoScene.TRUCO_PLAY_1;
                        case 1 -> RoccoScene.TRUCO_PLAY_2;
                        default -> RoccoScene.TRUCO_PLAY_3;
                    });
                }
                else
                {
                    sound(ModSounds.CARD_SLAP.get(), 0.8F);
                }
            }
            case TRICK_WON ->
            {
                // Una baza que pesa (la primera o la que define), ganada por su equipo: a veces lo festeja.
                if (this.roccoSeat >= 0 && e.team() == game.team(this.roccoSeat) && e.value() >= 1
                        && this.rocco.getRandom().nextFloat() < 0.35F)
                {
                    this.rocco.sayLine(RoccoExpression.CHEER, "truco_trick_win");
                }
            }
            case CALL, AUTO_FLOR ->
            {
                Action.Type call = e.call();
                int who = e.seat();
                int team = e.team();
                if (e.kind() == TrucoEvent.Kind.AUTO_FLOR)
                {
                    addLog(seat -> Component.translatable("gui.rocco.truco.had_flor", name(who)).withStyle(ChatFormatting.LIGHT_PURPLE));
                }
                setBanner(seat -> Component.translatable("gui.rocco.truco.called", name(who), callName(call)), team);
                addLog(seat -> Component.translatable("gui.rocco.truco.called", name(who), callName(call)).withStyle(ChatFormatting.GOLD));
                sound(ModSounds.TABLE_KNOCK.get(), 0.7F);
                if (mine)
                {
                    playAnim(switch (call)
                    {
                        case TRUCO, RETRUCO, VALE_CUATRO -> RoccoScene.TRUCO_CALL_TRUCO;
                        case FLOR, CONTRAFLOR_AL_RESTO -> RoccoScene.TRUCO_CALL_FLOR;
                        default -> RoccoScene.TRUCO_CALL_ENVIDO;
                    });
                }
            }
            case ANSWER ->
            {
                Action.Type call = e.call();
                int who = e.seat();
                setBanner(seat -> Component.translatable("gui.rocco.truco.answered", name(who), callName(call)), e.team());
                addLog(seat -> Component.translatable("gui.rocco.truco.answered", name(who), callName(call)).withStyle(ChatFormatting.YELLOW));
                if (mine)
                {
                    playAnim(call == Action.Type.QUIERO ? RoccoScene.TRUCO_QUIERO : RoccoScene.TRUCO_NO_QUIERO);
                }
            }
            case TANTO_SAID ->
            {
                int who = e.seat();
                int value = e.value();
                if (value >= 0)
                {
                    addLog(seat -> Component.translatable("gui.rocco.truco.tanto", name(who), value).withStyle(ChatFormatting.AQUA));
                    setBanner(seat -> Component.translatable("gui.rocco.truco.tanto", name(who), value), e.team());
                }
                else
                {
                    addLog(seat -> Component.translatable("gui.rocco.truco.son_buenas", name(who)).withStyle(ChatFormatting.AQUA));
                }
                if (mine)
                {
                    if (value >= 0)
                    {
                        this.rocco.sayLine(RoccoExpression.SMUG, "truco_tanto", "n", String.valueOf(value));
                    }
                    else
                    {
                        this.rocco.sayLine(RoccoExpression.BORED, "truco_son_buenas");
                    }
                }
            }
            case POINTS ->
            {
                int team = e.team();
                int points = e.value();
                String why = e.text();
                addLog(seat -> Component.translatable("gui.rocco.truco.points", points, teamName(seat, team), reason(why))
                        .withStyle(ChatFormatting.GREEN));
            }
            case MAZO ->
            {
                int who = e.seat();
                addLog(seat -> Component.translatable("gui.rocco.truco.mazo", name(who)).withStyle(ChatFormatting.GRAY));
                if (this.roccoSeat >= 0 && e.team() == game.team(this.roccoSeat))
                {
                    playAnim(RoccoScene.TRUCO_MAZO);
                }
            }
            case HAND_OVER ->
            {
                int team = e.team();
                int points = e.value();
                addLog(seat -> Component.translatable("gui.rocco.truco.hand_won", teamName(seat, team), points)
                        .withStyle(ChatFormatting.DARK_GREEN));
                this.handOverTicks = 0;
                this.ruedaCooldown = Math.max(this.ruedaCooldown, 100);
                if (this.roccoSeat >= 0)
                {
                    boolean won = team == game.team(this.roccoSeat);
                    playAnim(won ? RoccoScene.TRUCO_WIN_HAND : RoccoScene.TRUCO_LOSE_HAND);
                    if (!won && this.rocco.getRandom().nextFloat() < 0.5F)
                    {
                        this.rocco.sayLine(RoccoExpression.SAD, "truco_hand_lost");
                    }
                }
            }
            case GAME_OVER ->
            {
                int team = e.team();
                int us = game.teamScore(team);
                int them = game.teamScore(1 - team);
                addLog(seat -> Component.translatable("gui.rocco.truco.won", teamName(seat, team), us, them).withStyle(ChatFormatting.GOLD));
                setBanner(seat -> Component.translatable("gui.rocco.truco.won", teamName(seat, team), us, them), team);
                this.gameOverTicks = 0;
                this.handOverTicks = -1;
                this.end = End.FINISHED;
                if (this.roccoSeat >= 0)
                {
                    boolean won = team == game.team(this.roccoSeat);
                    this.animQueue.clear();
                    playAnim(won ? RoccoScene.TRUCO_WIN_GAME : RoccoScene.TRUCO_LOSE_GAME);
                    this.rocco.sayLine(won ? RoccoExpression.CHEER : RoccoExpression.SAD, won ? "truco_game_win" : "truco_game_lost");
                }
                else
                {
                    this.rocco.sayLine(RoccoExpression.CHEER, "truco_game_referee");
                }
            }
            default -> { }
        }
    }

    // ---------- fin de la mano ----------

    private void tickHandOver(TrucoGame game)
    {
        if (this.handOverTicks < 0 || game.phase() != TrucoGame.Phase.HAND_OVER)
        {
            return;
        }
        // Primero termina de festejar (o de lamentarse).
        if (!animIdle() || !this.animQueue.isEmpty())
        {
            return;
        }
        // Mano a mano, entre mano y mano, un mate.
        if (this.mateBetweenHands && this.handOverTicks == 0 && this.options[TrucoNetwork.OPT_MATE] == 0)
        {
            if (this.mateStep == MateStep.NONE && !this.mateServed)
            {
                this.mateServed = true;
                int human = firstHumanSeat();
                if (human >= 0)
                {
                    startMate(human);
                }
            }
            if (this.mateStep != MateStep.NONE)
            {
                return;
            }
        }
        if (this.mateStep != MateStep.NONE)
        {
            return;
        }
        this.handOverTicks++;
        if (this.handOverTicks >= HAND_PAUSE)
        {
            this.handOverTicks = -1;
            game.startNextHand();
            this.version++;
            drain();
        }
    }

    // ---------- el mate en la mesa (7.8) ----------

    private void tickMate()
    {
        TrucoGame game = this.game;
        if (game == null || this.options[TrucoNetwork.OPT_MATE] != 0 || this.gameOverTicks >= 0)
        {
            return;
        }
        switch (this.mateStep)
        {
            case NONE ->
            {
                // La rueda: con mas de un jugador (o si Rocco arbitra), cuando esta tranquilo.
                if (this.mateBetweenHands)
                {
                    return;
                }
                if (this.ruedaCooldown > 0)
                {
                    this.ruedaCooldown--;
                    return;
                }
                boolean calm = game.phase() == TrucoGame.Phase.PLAY && this.handsShown && animIdle() && this.animQueue.isEmpty()
                        && (this.roccoSeat < 0 || game.turn() != this.roccoSeat) && this.deciding == null && this.handOverTicks < 0;
                if (!calm)
                {
                    return;
                }
                if (this.ruedaServed >= humanCount())
                {
                    this.ruedaServed = 0;
                    this.mateStep = MateStep.MATEAR;
                    this.mateTicks = 0;
                    startAnim(RoccoScene.TRUCO_MATEAR);
                    return;
                }
                int seat = nextRuedaSeat();
                if (seat >= 0)
                {
                    startMate(seat);
                }
            }
            case CEBAR ->
            {
                if (this.anim != RoccoScene.TRUCO_CEBAR_IZQ && this.anim != RoccoScene.TRUCO_CEBAR_FRENTE && this.anim != RoccoScene.TRUCO_CEBAR_DER)
                {
                    return;
                }
                if (this.animTicks - RoccoEntity.ANIM_LAG == CEBAR_GIVE_TICK)
                {
                    this.mateInHand = true;
                    this.mateStep = MateStep.WAIT;
                    this.mateTicks = 0;
                    this.dirty = true;
                    // La animacion de espera es un bucle: queda ahi hasta que lo tomes.
                    this.animQueue.clear();
                    this.animQueue.add(mateWait());
                }
            }
            case WAIT ->
            {
                ServerPlayer player = player(this.mateSeat);
                if (player == null)
                {
                    finishMateRound();
                    return;
                }
                if (++this.mateTicks >= MATE_AUTO_TICKS && this.mateInHand)
                {
                    drink(player);
                }
            }
            case BACK ->
            {
                if (this.animTicks - RoccoEntity.ANIM_LAG == BACK_TAKE_TICK)
                {
                    this.mateSeat = -1;
                    this.dirty = true;
                }
                if (this.anim != RoccoScene.TRUCO_MATE_BACK_IZQ && this.anim != RoccoScene.TRUCO_MATE_BACK_FRENTE
                        && this.anim != RoccoScene.TRUCO_MATE_BACK_DER)
                {
                    finishMateRound();
                }
            }
            case MATEAR ->
            {
                if (this.anim != RoccoScene.TRUCO_MATEAR)
                {
                    this.mateStep = MateStep.NONE;
                    this.ruedaCooldown = RUEDA_COOLDOWN;
                    if (this.rocco.getRandom().nextFloat() < 0.4F)
                    {
                        this.rocco.sayLine(RoccoExpression.BLISS, "truco_mate_good");
                    }
                }
            }
        }
    }

    private void startMate(int seat)
    {
        this.mateSeat = seat;
        this.mateInHand = false;
        this.mateDir = mateDirection(seat);
        this.mateStep = MateStep.CEBAR;
        this.mateTicks = 0;
        startAnim(switch (this.mateDir)
        {
            case -1 -> RoccoScene.TRUCO_CEBAR_IZQ;
            case 1 -> RoccoScene.TRUCO_CEBAR_DER;
            default -> RoccoScene.TRUCO_CEBAR_FRENTE;
        });
        if (this.rocco.getRandom().nextFloat() < 0.5F)
        {
            this.rocco.sayLine(RoccoExpression.HAPPY, "truco_mate_offer");
        }
    }

    private RoccoScene mateWait()
    {
        return switch (this.mateDir)
        {
            case -1 -> RoccoScene.TRUCO_MATE_WAIT_IZQ;
            case 1 -> RoccoScene.TRUCO_MATE_WAIT_DER;
            default -> RoccoScene.TRUCO_MATE_WAIT_FRENTE;
        };
    }

    // -1 a su izquierda, 1 a su derecha, 0 de frente (con 35 grados de margen).
    private int mateDirection(int seat)
    {
        Vec3 chair = TrucoLayout.chairPosition(this.spot.center(), this.spot.yaw(), this.plan.size(), this.seatPlace[seat]);
        Vec3 from = TrucoLayout.chairPosition(this.spot.center(), this.spot.yaw(), this.plan.size(), 0);
        float toward = TrucoLayout.yawOf(chair.subtract(from));
        float delta = Mth.wrapDegrees(toward - this.spot.yaw());
        return delta < -35.0F ? -1 : delta > 35.0F ? 1 : 0;
    }

    // El jugador toma el mate (boton o solo, a los 8 segundos).
    void takeMate(ServerPlayer player)
    {
        if (this.mateStep == MateStep.WAIT && this.mateInHand && seatOf(player) == this.mateSeat)
        {
            drink(player);
        }
    }

    private void drink(ServerPlayer player)
    {
        this.mateInHand = false;
        this.level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.MATE_SIP.get(), SoundSource.PLAYERS, 0.7F, 1.0F);
        this.level.sendParticles(ModParticles.MATE_STEAM.get(), player.getX(), player.getEyeY() + 0.1D, player.getZ(), 4, 0.1D, 0.05D, 0.1D, 0.01D);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0, false, true));
        this.mateStep = MateStep.BACK;
        this.mateTicks = 0;
        this.animQueue.clear();
        startAnim(switch (this.mateDir)
        {
            case -1 -> RoccoScene.TRUCO_MATE_BACK_IZQ;
            case 1 -> RoccoScene.TRUCO_MATE_BACK_DER;
            default -> RoccoScene.TRUCO_MATE_BACK_FRENTE;
        });
        this.dirty = true;
    }

    private void finishMateRound()
    {
        this.mateSeat = -1;
        this.mateInHand = false;
        this.dirty = true;
        if (this.mateBetweenHands)
        {
            // Mano a mano: se ceba uno el y recien ahi se reparte.
            this.mateStep = MateStep.MATEAR;
            this.animQueue.clear();
            startAnim(RoccoScene.TRUCO_MATEAR);
            return;
        }
        this.ruedaServed++;
        this.mateStep = MateStep.NONE;
        this.ruedaCooldown = 60;
        if (!animIdle())
        {
            return;
        }
        startAnim(idleAnim());
    }

    private int nextRuedaSeat()
    {
        int n = this.seatPlayer.length;
        for (int i = 0; i < n; i++)
        {
            int seat = (this.ruedaIndex + i) % n;
            if (this.seatPlayer[seat] != null && player(seat) != null)
            {
                this.ruedaIndex = (seat + 1) % n;
                return seat;
            }
        }
        return -1;
    }

    private int firstHumanSeat()
    {
        for (int seat = 0; seat < this.seatPlayer.length; seat++)
        {
            if (this.seatPlayer[seat] != null)
            {
                return seat;
            }
        }
        return -1;
    }

    private int humanCount()
    {
        int count = 0;
        for (UUID id : this.seatPlayer)
        {
            if (id != null)
            {
                count++;
            }
        }
        return count;
    }

    // ---------- las señas a su compañero (una por mano) ----------

    private void sena()
    {
        TrucoGame game = this.game;
        if (this.senaDone || game == null || this.roccoSeat < 0 || game.manoSolo() || game.n < 4)
        {
            return;
        }
        this.senaDone = true;
        if (this.rocco.getRandom().nextFloat() > 0.6F)
        {
            return;
        }
        Card best = null;
        int strength = -1;
        for (Card card : game.hand(this.roccoSeat))
        {
            int value = Ranks.strength(card, game.muestra(), this.rules);
            if (value > strength)
            {
                strength = value;
                best = card;
            }
        }
        if (best == null)
        {
            return;
        }
        String key = senaKey(best, game);
        Component message = Component.translatable("gui.rocco.truco.sena", Component.translatable("gui.rocco.truco.sena." + key))
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        int team = game.team(this.roccoSeat);
        for (int seat = 0; seat < game.n; seat++)
        {
            ServerPlayer player = player(seat);
            if (player != null && game.team(seat) == team)
            {
                player.sendSystemMessage(message);
            }
        }
    }

    private String senaKey(Card card, TrucoGame game)
    {
        int pieza = Ranks.piezaIndex(card, game.muestra(), this.rules);
        if (pieza >= 0)
        {
            return switch (pieza)
            {
                case 0 -> "dos";
                case 1 -> "cuatro";
                case 2 -> "cinco";
                case 3 -> "once";
                default -> "diez";
            };
        }
        if (card.number() == 1 && (card.suit() == Suit.ESPADA || card.suit() == Suit.BASTO))
        {
            return "ancho";
        }
        if (card.number() == 7 && (card.suit() == Suit.ESPADA || card.suit() == Suit.ORO))
        {
            return "siete";
        }
        if (card.number() == 3)
        {
            return "tres";
        }
        return "malas";
    }

    // ---------- lo que ve cada uno ----------

    private void sendState()
    {
        TrucoGame game = this.game;
        if (game == null)
        {
            return;
        }
        int n = game.n;
        List<String> names = new ArrayList<>();
        int[] teams = new int[n];
        int[] scores = new int[n];
        int[] left = new int[n];
        for (int seat = 0; seat < n; seat++)
        {
            names.add(name(seat).getString());
            teams[seat] = game.team(seat);
            scores[seat] = game.score(seat);
            left[seat] = this.handsShown ? game.cardsLeft(seat) : 0;
        }
        int[] played = playedView(game);
        int[] tricks = {game.trickResult(0), game.trickResult(1), game.trickResult(2)};
        int muestra = this.muestraShown && game.muestra() != null ? game.muestra().id() : -1;
        boolean blocked = blocked() || this.gameOverTicks >= 0;
        for (int seat = 0; seat < n; seat++)
        {
            ServerPlayer player = player(seat);
            if (player == null)
            {
                continue;
            }
            int viewer = seat;
            int[] mine = this.handsShown ? game.hand(seat).stream().mapToInt(Card::id).toArray() : new int[0];
            List<Action> legal = this.handsShown && this.gameOverTicks < 0 ? game.legalActions(seat) : List.of();
            int[] flat = new int[legal.size() * 2];
            for (int i = 0; i < legal.size(); i++)
            {
                flat[i * 2] = legal.get(i).type().ordinal();
                flat[i * 2 + 1] = legal.get(i).card();
            }
            List<Component> lines = new ArrayList<>();
            for (IntFunction<Component> entry : this.log)
            {
                lines.add(entry.apply(viewer));
            }
            boolean mate = this.mateStep == MateStep.WAIT && this.mateInHand && this.mateSeat == seat;
            TrucoNetwork.send(player, new TrucoNetwork.TableState(this.table == null ? -1 : this.table.getId(), n, seat,
                    this.roccoSeat, game.manoSolo(), names, teams, scores, this.rules.targetPoints, muestra, played, tricks,
                    mine, left, game.phase().ordinal(), game.turn(), game.mano(), game.dealer(), game.pending().ordinal(),
                    game.callerSeat(), game.trucoLevel(), game.handValue(), game.handNumber(), flat, lines, mate,
                    this.thinking, this.banner.apply(viewer), this.bannerId, this.bannerTeam, blocked));
        }
    }

    // Las cartas jugadas (la que Rocco esta tirando aparece recien cuando la apoya).
    private int[] playedView(TrucoGame game)
    {
        int n = game.n;
        int[] played = new int[3 * n];
        for (int trick = 0; trick < 3; trick++)
        {
            for (int seat = 0; seat < n; seat++)
            {
                Card card = game.played(trick, seat);
                boolean hidden = seat == this.roccoSeat && trick == this.maskedTrick;
                played[trick * n + seat] = card == null || hidden ? -1 : card.id();
            }
        }
        return played;
    }

    private void updateView()
    {
        TrucoTableEntity table = this.table;
        if (table == null)
        {
            return;
        }
        CompoundTag view = new CompoundTag();
        TrucoGame game = this.game;
        view.putInt("n", this.seatPlace.length);
        view.putIntArray("places", this.seatPlace);
        view.putInt("rseat", this.roccoSeat);
        view.putIntArray("cards", game == null ? new int[0] : playedView(game));
        view.putInt("muestra", game != null && this.muestraShown && game.muestra() != null ? game.muestra().id() : -1);
        view.putLong("deal", this.dealAt);
        view.putLong("sweep", this.sweepAt);
        view.putBoolean("deck", game != null);
        table.setView(view);
    }

    private void addLog(IntFunction<Component> entry)
    {
        this.log.addLast(entry);
        while (this.log.size() > 6)
        {
            this.log.removeFirst();
        }
        this.dirty = true;
    }

    private void setBanner(IntFunction<Component> text, int team)
    {
        this.banner = text;
        this.bannerTeam = team;
        this.bannerId++;
        this.dirty = true;
    }

    private Component name(int seat)
    {
        if (seat < 0 || seat >= this.seatPlayer.length)
        {
            return Component.literal("?");
        }
        if (this.seatPlayer[seat] == null)
        {
            return this.rocco.getName();
        }
        ServerPlayer player = player(seat);
        return player == null ? Component.literal("?") : Component.literal(player.getGameProfile().getName());
    }

    private Component teamName(int viewer, int team)
    {
        TrucoGame game = this.game;
        if (game != null && game.manoSolo())
        {
            return team == 0 ? name(game.mano()) : Component.translatable("gui.rocco.truco.others");
        }
        if (game != null && viewer >= 0 && viewer < game.n && game.team(viewer) == team)
        {
            return Component.translatable("gui.rocco.truco.us");
        }
        return Component.translatable("gui.rocco.truco.them");
    }

    private static Component callName(Action.Type type)
    {
        return Component.translatable("truco.rocco.call." + type.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component reason(String why)
    {
        String key = switch (why)
        {
            case "flor (se achicaron)" -> "flor_achico";
            case "contraflor al resto" -> "contraflor";
            case "envido no querido" -> "envido_no";
            case "contraflor no querida" -> "contraflor_no";
            case "se fueron al mazo" -> "mazo";
            case "envido" -> "envido";
            case "flor contra flor" -> "flor_flor";
            case "flor" -> "flor";
            case "truco" -> "truco";
            case "no quiso el truco" -> "truco_no";
            default -> "";
        };
        return key.isEmpty() ? Component.literal(why) : Component.translatable("gui.rocco.truco.why." + key);
    }

    private void sound(SoundEvent sound, float volume)
    {
        Vec3 at = this.spot.center();
        this.level.playSound(null, at.x, at.y + 0.8D, at.z, sound, SoundSource.NEUTRAL, volume, 0.95F + this.rocco.getRandom().nextFloat() * 0.1F);
    }

    private int seatOf(Player player)
    {
        for (int seat = 0; seat < this.seatPlayer.length; seat++)
        {
            if (player.getUUID().equals(this.seatPlayer[seat]))
            {
                return seat;
            }
        }
        return -1;
    }

    @Nullable
    private ServerPlayer player(int seat)
    {
        if (seat < 0 || seat >= this.seatPlayer.length || this.seatPlayer[seat] == null)
        {
            return null;
        }
        return this.level.getServer().getPlayerList().getPlayer(this.seatPlayer[seat]);
    }

    private List<ServerPlayer> humans()
    {
        List<ServerPlayer> out = new ArrayList<>();
        for (int seat = 0; seat < this.seatPlayer.length; seat++)
        {
            ServerPlayer player = player(seat);
            if (player != null)
            {
                out.add(player);
            }
        }
        return out;
    }

    // ---------- pararse de la silla ----------

    // Agachado en la silla: primero pregunta.
    private boolean askToLeave(ServerPlayer player)
    {
        if (this.stage != Stage.PLAY || this.gameOverTicks >= 0)
        {
            return false;
        }
        long now = this.level.getGameTime();
        Long last = this.leavePrompt.get(player.getUUID());
        if (last == null || now - last > 60)
        {
            this.leavePrompt.put(player.getUUID(), now);
            MutableComponent yes = Component.translatable("gui.rocco.truco.yes").withStyle(Style.EMPTY.withColor(ChatFormatting.RED)
                    .withBold(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rocco truco abandonar")));
            MutableComponent no = Component.translatable("gui.rocco.truco.no").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                    .withBold(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rocco truco seguir")));
            player.sendSystemMessage(Component.translatable("gui.rocco.truco.leave_q").withStyle(ChatFormatting.YELLOW)
                    .append(" [").append(yes).append("] [").append(no).append("]"));
        }
        return true;
    }

    // /rocco truco abandonar
    public static boolean leave(ServerPlayer player)
    {
        TrucoSession session = of(player);
        if (session == null)
        {
            return false;
        }
        session.abandon(End.LEFT);
        return true;
    }

    // /rocco truco seguir: vuelve a la mesa.
    public static boolean resume(ServerPlayer player)
    {
        TrucoSession session = of(player);
        if (session == null || session.table == null)
        {
            return false;
        }
        Vec3 center = session.spot.center().add(0.0D, TrucoLayout.TOP / 16.0D, 0.0D);
        TrucoNetwork.send(player, new TrucoNetwork.Open(session.table.getId(), center.x, center.y, center.z));
        session.dirty = true;
        return true;
    }

    // ---------- eventos del juego ----------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        if (!ALL.isEmpty())
        {
            for (TrucoSession session : new ArrayList<>(ALL))
            {
                try
                {
                    session.tick();
                }
                catch (RuntimeException failure)
                {
                    RoccoMod.LOGGER.error("[Rocco] La partida de truco fallo y se cierra", failure);
                    session.close(End.DANGER, true);
                }
            }
            ALL.removeIf(session -> session.stage == Stage.DONE);
        }
        TrucoLobby.tickAll();
    }

    @SubscribeEvent
    public static void onMount(EntityMountEvent event)
    {
        if (!event.isDismounting() || event.getLevel().isClientSide() || !(event.getEntityBeingMounted() instanceof TrucoChairEntity))
        {
            return;
        }
        if (event.getEntityMounting() instanceof ServerPlayer player)
        {
            TrucoSession session = of(player);
            if (session != null && session.askToLeave(player))
            {
                event.setCanceled(true);
            }
        }
        else if (event.getEntityMounting() instanceof RoccoEntity rocco && rocco.truco() != null
                && rocco.truco().stage == Stage.PLAY && rocco.isAlive())
        {
            // Rocco no se levanta solo en medio de la partida.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            TrucoSession session = of(player);
            if (session != null)
            {
                session.abandon(End.LEFT);
            }
            TrucoLobby lobby = TrucoLobby.of(player);
            if (lobby != null)
            {
                TrucoLobby.action(player, TrucoNetwork.OP_LEAVE, 0);
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            TrucoSession session = of(player);
            if (session != null)
            {
                session.abandon(End.DANGER);
            }
        }
    }

    // Un bicho le pega a alguien de la mesa: se levantan todos.
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player && event.getSource().getEntity() instanceof Mob)
        {
            TrucoSession session = of(player);
            if (session != null)
            {
                session.abandon(End.DANGER);
            }
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event)
    {
        for (TrucoSession session : ALL)
        {
            session.close(End.DANGER, true);
        }
        ALL.clear();
        TrucoLobby.clearAll();
    }

    @Override
    public String toString()
    {
        return "TrucoSession" + Arrays.toString(this.seatPlayer);
    }
}

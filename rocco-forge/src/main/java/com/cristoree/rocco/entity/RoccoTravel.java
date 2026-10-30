package com.cristoree.rocco.entity;

import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Rocco acompaña al jugador cuando no va caminando (ROCCO-VUELO-AGUA-BARCO.md):
//  - volando: si el jugador planea con elytra (o vuela en creativo), despega con sus hojas-ala y
//    vuela a su derecha, un poco atras, inclinandose con el;
//  - en el agua: flota con su patito; si el jugador bucea, se pone las antiparras y bucea con el;
//  - en bote: no se sube al del jugador, tiene el suyo y rema al costado.
// Corre solo en el servidor, una vez por tick, despues de la fisica normal de la entidad.
public final class RoccoTravel
{
    // A su derecha y un poco atras, volando y buceando.
    private static final double FLY_SIDE = 1.5D;
    private static final double FLY_BACK = 1.0D;
    private static final double SWIM_SIDE = 1.2D;
    private static final double SWIM_BACK = 0.8D;
    private static final double BOAT_SIDE = 1.6D;

    // Mas lejos que esto, en vez de alcanzarlo de a poco aparece en su lugar.
    private static final double FLY_SNAP = 24.0D;
    private static final double SWIM_SNAP = 20.0D;
    // Nadando en la superficie: hasta donde te acompaña nadando (mas lejos, el seguir de siempre
    // lo hace aparecer al lado tuyo) y lo mas rapido que nada por tick (nadando rapido vas a ~0,28).
    private static final double SURFACE_RANGE = 24.0D;
    private static final double SURFACE_MAX_STEP = 0.4D;
    // Con vos quieto en el agua: mas cerca que SWIM_NEAR se aparta, mas lejos que SWIM_FAR se
    // acerca hasta SWIM_REST, y en el medio no se mueve. Nunca a menos de SWIM_MIN_GAP (te empuja).
    // Moverse menos que SWIM_STILL por tick cuenta como quieto (flotando, el agua te mece un poco).
    private static final double SWIM_NEAR = 1.4D;
    private static final double SWIM_FAR = 3.2D;
    private static final double SWIM_REST = 2.2D;
    private static final double SWIM_MIN_GAP = 1.1D;
    private static final double SWIM_STILL = 0.03D;
    private static final double BOAT_SNAP = 12.0D;

    // Solo despega o se mete al bote si el jugador no esta lejisimo (si no, primero lo alcanza).
    private static final double ACCOMPANY_RANGE = 64.0D;

    // El agua le queda a la altura del flotador: 4,9 pixeles por encima de los pies.
    private static final double FLOAT_DEPTH = 4.9D / 16.0D;

    private final RoccoEntity rocco;
    private int ticks;
    @Nullable
    private Vec3 ownerLast;
    @Nullable
    private Vec3 boatLast;
    private int notFlyingTicks;
    private int calmTicks;
    private int outOfWaterTicks;
    private Vec3 ownerStep = Vec3.ZERO;
    private boolean swimmingAlongside;
    private int surfacedTicks;
    private int boatLostTicks;
    private int boatSide;
    private int bubbleTicks;
    @Nullable
    private Vec3 landing;

    RoccoTravel(RoccoEntity rocco)
    {
        this.rocco = rocco;
    }

    // ---------- el tick ----------

    void tick()
    {
        ServerPlayer owner = leader();
        Vec3 ownerVelocity = ownerVelocity(owner);
        this.ownerStep = ownerVelocity;
        RoccoMotion motion = this.rocco.getMotion();
        this.ticks++;

        sounds(motion, this.ticks - RoccoEntity.ANIM_LAG);
        faces(motion, this.ticks - RoccoEntity.ANIM_LAG);

        switch (motion.kind())
        {
            case GROUND -> ground(owner);
            case AIR -> air(owner, ownerVelocity, motion);
            case SURFACE -> surface(owner, motion);
            case UNDERWATER -> underwater(owner, ownerVelocity, motion);
            case BOAT -> boat(owner, motion);
        }

        // Tiene snorkel: nunca se ahoga.
        if (this.rocco.isInWater())
        {
            this.rocco.setAirSupply(this.rocco.getMaxAirSupply());
        }
    }

    // A quien acompaña: su dueño. Si el dueño no esta conectado no se va volando atras de otro
    // jugador; solo un Rocco sin dueño todavia acompaña al que tenga cerca.
    @Nullable
    private ServerPlayer leader()
    {
        java.util.UUID id = this.rocco.getOwnerId();
        if (id != null)
        {
            return this.rocco.level().getPlayerByUUID(id) instanceof ServerPlayer player ? player : null;
        }
        return this.rocco.owner();
    }

    // Vuelve a caminar ya mismo (un teleport, un cambio de dimension, una orden de parar).
    void reset()
    {
        if (this.rocco.getMotion() != RoccoMotion.NONE)
        {
            set(RoccoMotion.NONE);
        }
        this.landing = null;
        this.ownerLast = null;
        this.boatLast = null;
    }

    private void set(RoccoMotion next)
    {
        this.rocco.setMotion(next);
        this.ticks = 0;
        this.calmTicks = 0;
        this.outOfWaterTicks = 0;
        this.surfacedTicks = 0;
        this.notFlyingTicks = 0;
        this.boatLostTicks = 0;

        // Volando y en el barquito no choca con nada (lo lleva el mod); buceando si, para no
        // atravesar paredes. Flotando usa el agua de verdad, sin gravedad: el flotador lo sostiene.
        RoccoMotion.Kind kind = next.kind();
        this.rocco.setNoGravity(kind != RoccoMotion.Kind.GROUND);
        this.rocco.noPhysics = kind == RoccoMotion.Kind.AIR || kind == RoccoMotion.Kind.BOAT;
        this.rocco.resetFallDistance();
        if (kind == RoccoMotion.Kind.GROUND)
        {
            this.rocco.setXRot(0.0F);
            this.rocco.allowSafeFall();
        }
    }

    // ---------- caminando: ve si tiene que despegar, meterse al bote o flotar ----------

    private void ground(@Nullable ServerPlayer owner)
    {
        boolean near = accompanies(owner) && this.rocco.distanceToSqr(owner) < ACCOMPANY_RANGE * ACCOMPANY_RANGE;
        if (near && RoccoCommonConfig.ACCOMPANY_FLIGHT.get() && isFlying(owner))
        {
            takeOff();
            return;
        }
        if (near && RoccoCommonConfig.ACCOMPANY_BOAT.get() && enterBoat(owner))
        {
            return;
        }
        if (canFloat())
        {
            set(RoccoMotion.FLOAT_START);
            if (owner != null && owner.isInWater())
            {
                this.rocco.react("react_float");
            }
        }
    }

    private void takeOff()
    {
        this.rocco.prepareForTravel();
        set(RoccoMotion.FLY_TAKEOFF);
        this.rocco.react("react_fly");
    }

    // Solo acompaña si te esta siguiendo y estas en su mundo. Si quedo cuidando la huerta, se queda
    // ahi. Para empezar (despegar, subirse al barquito) ademas tenes que estar cerca; ya en viaje no
    // te suelta aunque te adelantes: si queda lejos, aparece a tu lado.
    private boolean accompanies(@Nullable ServerPlayer owner)
    {
        return owner != null
                && owner.isAlive()
                && !owner.isSpectator()
                && owner.level() == this.rocco.level()
                && this.rocco.getRoccoState() == RoccoState.ACTIVE
                && RoccoCommonConfig.FOLLOW_ENABLED.get()
                && this.rocco.isFollowing()
                && !this.rocco.isLidOpen()
                && !this.rocco.staysForWork();
    }

    private static boolean isFlying(ServerPlayer owner)
    {
        return owner.isFallFlying()
                || (owner.getAbilities().flying && !owner.onGround() && !owner.isPassenger());
    }

    private boolean canFloat()
    {
        return this.rocco.getRoccoState() == RoccoState.ACTIVE
                && this.rocco.isInWater()
                && waterDepth() > 0.5D;
    }

    // ---------- volando ----------

    private void air(@Nullable ServerPlayer owner, Vec3 ownerVelocity, RoccoMotion motion)
    {
        if (motion == RoccoMotion.FLY_LAND)
        {
            land();
            return;
        }

        // Te fuiste a otro lado, te moriste o le dijiste que espere: baja donde esta.
        if (!accompanies(owner))
        {
            set(this.rocco.isInWater() ? RoccoMotion.FLOAT_START : RoccoMotion.NONE);
            return;
        }

        this.notFlyingTicks = isFlying(owner) ? 0 : this.notFlyingTicks + 1;
        boolean touchedDown = owner.onGround() || owner.isInWater() || owner.isPassenger();
        if (this.notFlyingTicks >= 3 && touchedDown)
        {
            beginLanding(owner);
            return;
        }

        if (motion == RoccoMotion.FLY_TAKEOFF)
        {
            // Mientras despega se va acomodando a tu lado, sin apuro.
            steerFlight(owner, ownerVelocity, 0.18D);
            if (this.ticks >= motion.ticks() + RoccoEntity.ANIM_LAG)
            {
                set(RoccoMotion.FLY_GLIDE);
            }
            return;
        }

        RoccoMotion next = flightStyle(owner, ownerVelocity, motion);
        if (next != motion)
        {
            set(next);
        }
        steerFlight(owner, ownerVelocity, 0.3D);
    }

    // Cohete, picada o planeo, con un poco de margen para que no titile entre una y otra.
    private RoccoMotion flightStyle(ServerPlayer owner, Vec3 velocity, RoccoMotion current)
    {
        if (boosting(owner, velocity))
        {
            return RoccoMotion.FLY_BOOST;
        }
        if (current == RoccoMotion.FLY_BOOST)
        {
            return RoccoMotion.FLY_BOOST_END;
        }
        if (current == RoccoMotion.FLY_BOOST_END && this.ticks < current.ticks() + RoccoEntity.ANIM_LAG)
        {
            return current;
        }
        boolean diving = current == RoccoMotion.FLY_DIVE
                ? velocity.y < -0.35D || owner.getXRot() > 25.0F
                : velocity.y < -0.5D || owner.getXRot() > 35.0F;
        return diving ? RoccoMotion.FLY_DIVE : RoccoMotion.FLY_GLIDE;
    }

    // Un cohete enganchado al jugador (o una velocidad que solo da un cohete). El cohete del elytra
    // es suyo y viaja pegado a el; uno de ballesta sale disparado lejos enseguida.
    private boolean boosting(ServerPlayer owner, Vec3 velocity)
    {
        // En picada sin cohete el elytra tambien va muy rapido: ahi la velocidad no cuenta.
        if (velocity.y > -0.3D && velocity.lengthSqr() > 1.9D * 1.9D)
        {
            return true;
        }
        return !owner.level().getEntitiesOfClass(FireworkRocketEntity.class, owner.getBoundingBox().inflate(2.0D),
                rocket -> rocket.isAlive() && rocket.getOwner() == owner && rocket.distanceToSqr(owner) < 4.0D).isEmpty();
    }

    // A tu derecha y un poco atras, a tu altura, mirando para donde miras. Se suma tu velocidad
    // para no quedarse atras con el elytra y se corrige de a poco hacia su lugar.
    private void steerFlight(ServerPlayer owner, Vec3 ownerVelocity, double catchUp)
    {
        float yaw = owner.getYRot();
        Vec3 target = owner.position()
                .add(right(yaw).scale(FLY_SIDE))
                .subtract(owner.getLookAngle().scale(FLY_BACK));
        glideTo(target, ownerVelocity, catchUp, FLY_SNAP);

        boolean tilted = this.rocco.getMotion().tiltsWithPlayer();
        face(yaw, tilted ? Mth.clamp(owner.getXRot(), -60.0F, 80.0F) : 0.0F);
    }

    private void beginLanding(ServerPlayer owner)
    {
        if (owner.isInWater())
        {
            // Cae al agua al lado tuyo y se infla el patito.
            Vec3 water = waterSpotNear(this.rocco.serverLevel(), owner);
            if (water != null)
            {
                this.rocco.setPos(water);
            }
            set(RoccoMotion.FLOAT_START);
            return;
        }

        BlockPos spot = RoccoFollowGoal.findSpotNear(this.rocco.serverLevel(), owner);
        this.landing = spot != null
                ? new Vec3(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D)
                : owner.position();
        set(RoccoMotion.FLY_LAND);
    }

    // Se endereza y baja hasta el lugar elegido en lo que dura la animacion.
    private void land()
    {
        Vec3 target = this.landing != null ? this.landing : this.rocco.position();
        int total = RoccoMotion.FLY_LAND.ticks();
        int left = Math.max(1, total + RoccoEntity.ANIM_LAG - this.ticks);
        Vec3 here = this.rocco.position();
        Vec3 step = target.subtract(here).scale(1.0D / left);
        this.rocco.setPos(here.add(step));
        this.rocco.setDeltaMovement(step);
        face(this.rocco.getYRot(), 0.0F);

        if (this.ticks >= total + RoccoEntity.ANIM_LAG)
        {
            this.rocco.setPos(target);
            this.rocco.setDeltaMovement(Vec3.ZERO);
            this.landing = null;
            set(this.rocco.isInWater() ? RoccoMotion.FLOAT_START : RoccoMotion.NONE);
        }
    }

    // ---------- flotando en la superficie ----------

    private void surface(@Nullable ServerPlayer owner, RoccoMotion motion)
    {
        if (motion == RoccoMotion.FLOAT_END)
        {
            // Se sacude el agua como un perrito, quieto en el lugar.
            if (this.ticks >= motion.ticks() + RoccoEntity.ANIM_LAG)
            {
                set(RoccoMotion.NONE);
            }
            return;
        }

        buoy();

        // Salio del agua (o hace pie): se desinfla el patito.
        double depth = waterDepth();
        boolean out = !this.rocco.isInWater() || (this.rocco.onGround() && depth < 0.35D);
        this.outOfWaterTicks = out ? this.outOfWaterTicks + 1 : 0;
        if (this.outOfWaterTicks >= 4)
        {
            set(motion == RoccoMotion.FLOAT_START ? RoccoMotion.NONE : RoccoMotion.FLOAT_END);
            return;
        }

        if (motion == RoccoMotion.FLOAT_START)
        {
            if (this.ticks >= motion.ticks() + RoccoEntity.ANIM_LAG)
            {
                set(RoccoMotion.FLOAT_IDLE);
            }
            return;
        }

        if (accompanies(owner) && this.rocco.distanceToSqr(owner) < ACCOMPANY_RANGE * ACCOMPANY_RANGE)
        {
            if (RoccoCommonConfig.ACCOMPANY_FLIGHT.get() && isFlying(owner))
            {
                takeOff();
                return;
            }
            if (RoccoCommonConfig.ACCOMPANY_BOAT.get() && enterBoat(owner))
            {
                return;
            }
            if (RoccoCommonConfig.ACCOMPANY_WATER.get() && owner.isUnderWater()
                    && this.rocco.distanceToSqr(owner) < 16.0D * 16.0D)
            {
                this.rocco.prepareForTravel();
                set(RoccoMotion.SWIM_DIVE);
                this.rocco.react("react_dive");
                return;
            }
        }

        swimAlongside(owner);

        // Nada estilo perrito cuando avanza, y flota tranquilo cuando no.
        RoccoMotion wanted = keepMoving(horizontalSpeed() > 0.025D) ? RoccoMotion.FLOAT_SWIM : RoccoMotion.FLOAT_IDLE;
        if (wanted != motion)
        {
            set(wanted);
        }
    }

    // Nadando con vos en la superficie va a tu ritmo, a un costado. Antes lo llevaba la
    // navegacion de tierra, que en el agua avanza a menos de un bloque por segundo: nadabas un
    // poco y se quedaba atras (o en la orilla).
    private void swimAlongside(@Nullable ServerPlayer owner)
    {
        if (!swimsWith(owner))
        {
            if (this.swimmingAlongside)
            {
                // Dejo de llevarlo: frena en vez de seguir de largo con el envion.
                this.swimmingAlongside = false;
                Vec3 movement = this.rocco.getDeltaMovement();
                this.rocco.setDeltaMovement(0.0D, movement.y, 0.0D);
            }
            return;
        }
        this.swimmingAlongside = true;

        Vec3 here = this.rocco.position();
        Vec3 flat = new Vec3(this.ownerStep.x, 0.0D, this.ownerStep.z);
        Vec3 fromOwner = new Vec3(here.x - owner.getX(), 0.0D, here.z - owner.getZ());
        double distance = fromOwner.length();
        Vec3 away = distance > 0.01D ? fromOwner.scale(1.0D / distance) : right(owner.getYRot());

        Vec3 step;
        if (flat.lengthSqr() > SWIM_STILL * SWIM_STILL)
        {
            // Nadando: a tu costado y un poco atras, segun para donde vas.
            float yaw = (float) (Mth.atan2(flat.z, flat.x) * Mth.RAD_TO_DEG) - 90.0F;
            Vec3 target = owner.position()
                    .add(right(yaw).scale(SWIM_SIDE))
                    .subtract(forward(yaw).scale(SWIM_BACK));
            Vec3 gap = new Vec3(target.x - here.x - flat.x, 0.0D, target.z - here.z - flat.z);
            step = flat.add(gap.scale(0.15D));
        }
        else if (distance < SWIM_NEAR)
        {
            // Quieto y pegado a vos: se hace a un lado.
            step = away.scale((SWIM_NEAR - distance) * 0.2D);
        }
        else if (distance > SWIM_FAR)
        {
            step = away.scale(-(distance - SWIM_REST) * 0.12D);
        }
        else
        {
            // Quieto: se queda donde esta. Girar la camara para mirarlo ya no lo hace nadar
            // alrededor tuyo (antes su lugar dependia de para donde mirabas).
            step = Vec3.ZERO;
        }

        // Nunca se te mete encima: si con este paso quedaria pegado, lo corre para afuera.
        Vec3 after = new Vec3(here.x + step.x - owner.getX() - flat.x, 0.0D, here.z + step.z - owner.getZ() - flat.z);
        double afterDistance = after.length();
        if (afterDistance < SWIM_MIN_GAP)
        {
            Vec3 out = afterDistance > 0.01D ? after.scale(1.0D / afterDistance) : away;
            step = step.add(out.scale(SWIM_MIN_GAP - afterDistance));
        }
        if (step.lengthSqr() > SURFACE_MAX_STEP * SURFACE_MAX_STEP)
        {
            step = step.normalize().scale(SURFACE_MAX_STEP);
        }

        this.rocco.getNavigation().stop();
        this.rocco.setDeltaMovement(step.x, this.rocco.getDeltaMovement().y, step.z);
        float wanted = step.lengthSqr() > 0.0025D
                ? (float) (Mth.atan2(step.z, step.x) * Mth.RAD_TO_DEG) - 90.0F
                : (float) (Mth.atan2(-fromOwner.z, -fromOwner.x) * Mth.RAD_TO_DEG) - 90.0F;
        face(Mth.approachDegrees(this.rocco.getYRot(), wanted, 12.0F), 0.0F);
    }

    // Si lo esta llevando nadando al lado tuyo (el seguir de siempre no tiene que pisarlo).
    boolean swimsWith(@Nullable ServerPlayer owner)
    {
        return accompanies(owner)
                && owner.isInWater()
                // Parado en la orilla con los pies en el agua no cuenta: ahi lo trae el seguir de siempre.
                && !(owner.onGround() && owner.getFluidTypeHeight(net.minecraftforge.common.ForgeMod.WATER_TYPE.get()) < 1.0D)
                && !owner.isPassenger()
                && this.rocco.getMotion().kind() == RoccoMotion.Kind.SURFACE
                && this.rocco.getMotion() != RoccoMotion.FLOAT_START
                && this.rocco.getMotion() != RoccoMotion.FLOAT_END
                && this.rocco.distanceToSqr(owner) < SURFACE_RANGE * SURFACE_RANGE;
    }

    // El flotador lo sostiene con el agua a la altura de la cintura: si se hunde de mas sube, si
    // queda alto baja. Lo de los costados lo hace la navegacion de siempre.
    private void buoy()
    {
        double depth = waterDepth();
        Vec3 movement = this.rocco.getDeltaMovement();
        double vertical = this.rocco.isInWater()
                ? Mth.clamp((depth - FLOAT_DEPTH) * 0.25D, -0.08D, 0.08D)
                : -0.08D;
        this.rocco.setDeltaMovement(movement.x, vertical, movement.z);
        this.rocco.resetFallDistance();
    }

    // ---------- buceando ----------

    private void underwater(@Nullable ServerPlayer owner, Vec3 ownerVelocity, RoccoMotion motion)
    {
        if (motion == RoccoMotion.SWIM_DIVE)
        {
            // Primero toma aire y se pone las antiparras; recien despues se sumerge.
            if (this.ticks < 12 + RoccoEntity.ANIM_LAG || owner == null)
            {
                buoy();
            }
            else
            {
                steerUnderwater(owner, ownerVelocity, 0.1D);
            }
            if (this.ticks >= motion.ticks() + RoccoEntity.ANIM_LAG)
            {
                set(RoccoMotion.SWIM_UNDER_IDLE);
            }
            return;
        }

        if (motion == RoccoMotion.SWIM_SURFACE)
        {
            rise();
            if (this.ticks >= motion.ticks() + RoccoEntity.ANIM_LAG)
            {
                set(this.rocco.isInWater() ? RoccoMotion.FLOAT_IDLE : RoccoMotion.NONE);
            }
            return;
        }

        if (!this.rocco.isInWater())
        {
            set(RoccoMotion.NONE);
            return;
        }

        // Saliste a respirar (o del agua): sube con vos, sin apurarse por medio segundo.
        boolean stillDiving = accompanies(owner) && owner.isInWater() && owner.isUnderWater();
        this.surfacedTicks = stillDiving ? 0 : this.surfacedTicks + 1;
        if (this.surfacedTicks >= 10 || owner == null)
        {
            set(RoccoMotion.SWIM_SURFACE);
            return;
        }

        steerUnderwater(owner, ownerVelocity, 0.15D);
        RoccoMotion wanted = keepMoving(horizontalSpeed() > 0.04D) ? RoccoMotion.SWIM_UNDER : RoccoMotion.SWIM_UNDER_IDLE;
        if (wanted != motion)
        {
            set(wanted);
        }
    }

    private void steerUnderwater(ServerPlayer owner, Vec3 ownerVelocity, double catchUp)
    {
        float yaw = owner.getYRot();
        Vec3 target = owner.position()
                .add(right(yaw).scale(SWIM_SIDE))
                .subtract(forward(yaw).scale(SWIM_BACK));
        Vec3 here = this.rocco.position();

        if (here.distanceToSqr(target) > SWIM_SNAP * SWIM_SNAP && fits(target))
        {
            this.rocco.setPos(target);
            this.rocco.setDeltaMovement(Vec3.ZERO);
        }
        else
        {
            // Choca con las paredes: se mueve con la fisica de la entidad, no a traves de todo.
            Vec3 step = ownerVelocity.add(target.subtract(here.add(ownerVelocity)).scale(catchUp));
            if (step.lengthSqr() > 0.45D * 0.45D)
            {
                step = step.normalize().scale(0.45D);
            }
            this.rocco.move(MoverType.SELF, step);
            this.rocco.setDeltaMovement(step);
        }
        face(yaw, 0.0F);
    }

    // Sube derecho hasta que el agua le quede en la cintura.
    private void rise()
    {
        Double surface = surfaceAbove();
        Vec3 here = this.rocco.position();
        double goal = surface == null ? here.y + 0.1D : surface - FLOAT_DEPTH;
        double step = Mth.clamp(goal - here.y, -0.12D, 0.12D);
        this.rocco.move(MoverType.SELF, new Vec3(0.0D, step, 0.0D));
        this.rocco.setDeltaMovement(0.0D, step, 0.0D);
    }

    @Nullable
    private Double surfaceAbove()
    {
        BlockPos.MutableBlockPos pos = this.rocco.blockPosition().mutable();
        ServerLevel level = this.rocco.serverLevel();
        for (int i = 0; i < 24; i++)
        {
            FluidState fluid = level.getFluidState(pos);
            if (!fluid.is(FluidTags.WATER))
            {
                return i == 0 ? null : (double) pos.getY();
            }
            if (!level.getFluidState(pos.above()).is(FluidTags.WATER))
            {
                return pos.getY() + (double) fluid.getHeight(level, pos);
            }
            pos.move(0, 1, 0);
        }
        return null;
    }

    // ---------- en el barquito ----------

    // Si vas en bote por el agua, aparece en su barquito al costado. false si no corresponde.
    private boolean enterBoat(ServerPlayer owner)
    {
        if (!(owner.getVehicle() instanceof Boat boat))
        {
            return false;
        }
        Vec3 spot = boatSpot(boat);
        if (spot == null)
        {
            return false;
        }
        this.rocco.prepareForTravel();
        // El barquito aparece de golpe debajo suyo: si estaba en la orilla, salta directo a el.
        if (this.rocco.position().distanceToSqr(spot) > 0.25D)
        {
            this.rocco.setPos(spot);
        }
        this.boatLast = boat.position();
        set(RoccoMotion.BOAT_ENTER);
        face(boat.getYRot(), 0.0F);
        this.rocco.react("react_boat");
        return true;
    }

    private void boat(@Nullable ServerPlayer owner, RoccoMotion motion)
    {
        if (motion == RoccoMotion.BOAT_EXIT)
        {
            // Guarda el remo, salta afuera y el barquito desaparece: despues nada o camina.
            if (this.ticks >= motion.ticks() + RoccoEntity.ANIM_LAG)
            {
                set(RoccoMotion.NONE);
            }
            return;
        }

        Boat boat = owner != null && owner.getVehicle() instanceof Boat ridden ? ridden : null;
        if (boat == null || !accompanies(owner) || !RoccoCommonConfig.ACCOMPANY_BOAT.get())
        {
            set(RoccoMotion.BOAT_EXIT);
            return;
        }

        Vec3 spot = boatSpot(boat);
        Vec3 boatVelocity = this.boatLast == null ? Vec3.ZERO : boat.position().subtract(this.boatLast);
        this.boatLast = boat.position();
        if (spot == null)
        {
            // El bote salio del agua (lo arrastras por tierra o hielo): se baja.
            if (++this.boatLostTicks >= 10)
            {
                set(RoccoMotion.BOAT_EXIT);
            }
            return;
        }
        this.boatLostTicks = 0;

        glideTo(spot, boatVelocity, 0.35D, BOAT_SNAP);
        face(boat.getYRot(), 0.0F);

        if (motion == RoccoMotion.BOAT_ENTER)
        {
            if (this.ticks >= motion.ticks() + RoccoEntity.ANIM_LAG)
            {
                set(RoccoMotion.BOAT_IDLE);
            }
            return;
        }

        double speed = Math.sqrt(boatVelocity.x * boatVelocity.x + boatVelocity.z * boatVelocity.z);
        RoccoMotion wanted = keepMoving(speed > 0.04D) ? RoccoMotion.BOAT_ROW : RoccoMotion.BOAT_IDLE;
        if (wanted != motion)
        {
            set(wanted);
        }
    }

    // Al costado del bote, flotando en el agua (la superficie a la altura de sus pies). Prueba la
    // derecha, la izquierda y atras, quedandose del lado que venia para no cambiar a cada rato.
    @Nullable
    private Vec3 boatSpot(Boat boat)
    {
        float yaw = boat.getYRot();
        Vec3 side = right(yaw).scale(BOAT_SIDE);
        Vec3[] options = {
                boat.position().add(side),
                boat.position().subtract(side),
                boat.position().subtract(forward(yaw).scale(2.4D))};

        for (int i = 0; i < options.length; i++)
        {
            int index = (this.boatSide + i) % options.length;
            Vec3 option = options[index];
            Double surface = waterSurface(this.rocco.serverLevel(), option.x, option.z, boat.getY());
            if (surface != null)
            {
                this.boatSide = index;
                return new Vec3(option.x, surface, option.z);
            }
        }
        return null;
    }

    // La altura del agua en esa columna, cerca de la altura dada. null si ahi no hay agua abierta.
    @Nullable
    static Double waterSurface(ServerLevel level, double x, double z, double nearY)
    {
        int top = Mth.floor(nearY) + 1;
        for (int y = top; y >= top - 3; y--)
        {
            BlockPos pos = BlockPos.containing(x, y, z);
            FluidState fluid = level.getFluidState(pos);
            if (fluid.is(FluidTags.WATER))
            {
                BlockPos above = pos.above();
                boolean open = !level.getFluidState(above).is(FluidTags.WATER)
                        && level.getBlockState(above).getCollisionShape(level, above).isEmpty();
                return open ? pos.getY() + (double) fluid.getHeight(level, pos) : null;
            }
        }
        return null;
    }

    // Agua abierta al lado del jugador, para caer flotando ahi. Sirve tambien para que el
    // teletransporte no falle cuando estas nadando lejos de la costa.
    @Nullable
    public static Vec3 waterSpotNear(ServerLevel level, ServerPlayer player)
    {
        BlockPos base = player.blockPosition();
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++)
        {
            for (int dz = -3; dz <= 3; dz++)
            {
                if (dx * dx + dz * dz < 2)
                {
                    continue;
                }
                double x = base.getX() + dx + 0.5D;
                double z = base.getZ() + dz + 0.5D;
                Double surface = waterSurface(level, x, z, player.getY() + 1.0D);
                if (surface == null)
                {
                    continue;
                }
                double score = dx * dx + dz * dz + Math.abs(surface - player.getY());
                if (score < bestScore)
                {
                    bestScore = score;
                    best = new Vec3(x, surface - FLOAT_DEPTH, z);
                }
            }
        }
        return best;
    }

    // ---------- ayudas ----------

    // Se suma lo que se movio quien lo lleva (asi no queda atras) y se corrige hacia su lugar.
    private void glideTo(Vec3 target, Vec3 carried, double catchUp, double snap)
    {
        Vec3 here = this.rocco.position();
        Vec3 next = here.distanceToSqr(target) > snap * snap
                ? target
                : here.add(carried).lerp(target, catchUp);
        this.rocco.setPos(next);
        this.rocco.setDeltaMovement(next.subtract(here));
        this.rocco.resetFallDistance();
    }

    private void face(float yaw, float pitch)
    {
        this.rocco.setYRot(yaw);
        this.rocco.setYHeadRot(yaw);
        this.rocco.setYBodyRot(yaw);
        this.rocco.setXRot(pitch);
    }

    // Un rato de margen antes de pasar de moverse a quieto, para que la animacion no titile.
    private boolean keepMoving(boolean moving)
    {
        if (moving)
        {
            this.calmTicks = 8;
            return true;
        }
        return --this.calmTicks > 0;
    }

    // Cuanta agua tiene encima de los pies.
    private double waterDepth()
    {
        return this.rocco.getFluidTypeHeight(net.minecraftforge.common.ForgeMod.WATER_TYPE.get());
    }

    private double horizontalSpeed()
    {
        Vec3 movement = this.rocco.getDeltaMovement();
        return Math.sqrt(movement.x * movement.x + movement.z * movement.z);
    }

    private boolean fits(Vec3 at)
    {
        return this.rocco.level().noCollision(this.rocco, this.rocco.getDimensions(this.rocco.getPose()).makeBoundingBox(at));
    }

    // Lo que se movio el jugador desde el tick anterior (en el servidor la velocidad del jugador
    // no siempre esta al dia). Un teletransporte no cuenta como velocidad.
    private Vec3 ownerVelocity(@Nullable ServerPlayer owner)
    {
        if (owner == null)
        {
            this.ownerLast = null;
            return Vec3.ZERO;
        }
        Vec3 now = owner.position();
        Vec3 velocity = this.ownerLast == null ? Vec3.ZERO : now.subtract(this.ownerLast);
        this.ownerLast = now;
        return velocity.lengthSqr() > 6.0D * 6.0D ? Vec3.ZERO : velocity;
    }

    private static Vec3 forward(float yaw)
    {
        float radians = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(radians), 0.0D, Mth.cos(radians));
    }

    // Mirando al sur (+Z), la derecha es el oeste (-X).
    private static Vec3 right(float yaw)
    {
        float radians = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.cos(radians), 0.0D, -Mth.sin(radians));
    }

    // ---------- sonidos y caras de cada animacion ----------

    private void sounds(RoccoMotion motion, int t)
    {
        if (t < 0)
        {
            return;
        }
        switch (motion)
        {
            case FLY_TAKEOFF ->
            {
                if (t == 4) play(ModSounds.LEAF_FWIP.get(), 0.7F);
                else if (t == 8) play(ModSounds.LEAF_GROW.get(), 0.7F);
                else if (t == 10 || t == 13) play(ModSounds.LEAF_FLAP.get(), 0.5F);
            }
            case FLY_GLIDE ->
            {
                int loop = t % 80;
                if (loop == 22 || loop == 58) play(ModSounds.LEAF_FLAP.get(), 0.25F);
            }
            case FLY_LAND ->
            {
                if (t == 2) play(ModSounds.LEAF_FLAP.get(), 0.5F);
                else if (t == 10) play(ModSounds.LEAF_FWIP.get(), 0.5F);
            }
            case FLOAT_START ->
            {
                if (t == 0) play(SoundEvents.GENERIC_SPLASH, 0.5F);
                else if (t == 4) play(ModSounds.DUCK_SQUEAK.get(), 0.6F);
            }
            case FLOAT_SWIM ->
            {
                if (t % 8 == 0) play(SoundEvents.PLAYER_SWIM, 0.3F);
            }
            case FLOAT_END ->
            {
                if (t == 5) play(ModSounds.DUCK_POP.get(), 0.6F);
            }
            case SWIM_DIVE ->
            {
                if (t == 5) play(ModSounds.DUCK_POP.get(), 0.6F);
                else if (t == 17) play(SoundEvents.GENERIC_SPLASH, 0.4F);
            }
            case SWIM_UNDER_IDLE, SWIM_UNDER ->
            {
                // Burbujas del snorkel cada tanto.
                if (--this.bubbleTicks <= 0)
                {
                    this.bubbleTicks = 30 + this.rocco.getRandom().nextInt(40);
                    play(SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.2F);
                }
            }
            case SWIM_SURFACE ->
            {
                if (t == 5) play(SoundEvents.GENERIC_SPLASH, 0.4F);
                else if (t == 10) play(ModSounds.DUCK_POP.get(), 0.5F);
                else if (t == 12) play(ModSounds.DUCK_SQUEAK.get(), 0.6F);
            }
            case BOAT_ENTER ->
            {
                if (t == 3)
                {
                    play(ModSounds.DUCK_POP.get(), 0.6F);
                    play(SoundEvents.GENERIC_SPLASH, 0.4F);
                }
            }
            case BOAT_ROW ->
            {
                int loop = t % 28;
                if (loop == 2 || loop == 16) play(SoundEvents.BOAT_PADDLE_WATER, 0.5F);
            }
            case BOAT_EXIT ->
            {
                if (t == 8) play(SoundEvents.GENERIC_SPLASH, 0.4F);
                else if (t == 12) play(ModSounds.DUCK_POP.get(), 0.6F);
            }
            default -> { }
        }
    }

    // Solo en los momentos que marca la guia: entre medio la cara puede cambiar (la IA, un susto).
    private void faces(RoccoMotion motion, int t)
    {
        RoccoExpression face = switch (motion)
        {
            case FLY_TAKEOFF, FLY_GLIDE, FLY_DIVE, FLY_LAND, FLOAT_IDLE, SWIM_UNDER_IDLE, SWIM_SURFACE,
                 BOAT_ENTER, BOAT_EXIT -> t == 0 ? RoccoExpression.HAPPY : null;
            case FLY_BOOST -> t == 0 ? RoccoExpression.WINDY : null;
            case FLY_BOOST_END -> t == 0 ? RoccoExpression.WINDY : t == 11 ? RoccoExpression.HAPPY : null;
            case FLOAT_START -> t == 0 ? RoccoExpression.SCARED : t == 6 ? RoccoExpression.HAPPY : null;
            case FLOAT_END -> t == 0 ? RoccoExpression.HAPPY : t == 8 ? RoccoExpression.DIZZY : t == 18 ? RoccoExpression.HAPPY : null;
            case SWIM_DIVE -> t == 0 ? RoccoExpression.HAPPY : t == 3 ? RoccoExpression.SMUG : null;
            default -> null;
        };
        if (face != null)
        {
            this.rocco.setMotionFace(face);
        }
    }

    private void play(net.minecraft.sounds.SoundEvent sound, float volume)
    {
        this.rocco.playRoccoSound(sound, volume);
    }
}

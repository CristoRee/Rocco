package com.cristoree.rocco.entity;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

// La vida de Rocco: se cura con la luz (fotosintesis), se muere con su animacion, reaparece
// enterradito al lado de donde reaparece su dueño y lo desentierran con clic derecho. Tambien el
// poder del cofre de ender (no perder sus cosas al morir) y los tiempos de la Roco Cola.
// Corre solo en el servidor; la entidad le avisa en cada tick y al guardar.
public final class RoccoLife
{
    // death dura 1,8 s; grave_rise_* 3,8 s.
    static final int DEATH_TICKS = 36;
    static final int RISE_TICKS = 76;
    // Despues de un golpe espera esto para empezar a curarse.
    private static final int REGEN_DELAY_TICKS = 100;
    // Pedirle otra Roco Cola: 30 segundos entre una y otra.
    private static final long COLA_REQUEST_COOLDOWN = 600L;
    // El cofre de ender que pusiste cerca: se lo come si fue hace menos de un minuto.
    private static final long ENDER_NOTICE_TICKS = 1200L;

    private final RoccoEntity rocco;

    private int deathTicks;
    // Murieron los dos: espera a que reaparezcas para enterrarse al lado tuyo.
    private boolean waitingForOwner;
    private int graveTicks;
    private int riseTicks;
    // Cuantas veces se murio: la explicacion del clic derecho va solo la primera.
    private int graves;

    private boolean enderPocket;
    private boolean enderAwakening;
    private int enderChestsSeen;
    @Nullable
    private BlockPos enderTarget;
    private long enderTargetUntil;

    private long lastHurtTick = -1_000_000L;
    private long lastCola = -1_000_000L;

    RoccoLife(RoccoEntity rocco)
    {
        this.rocco = rocco;
    }

    // ---------- curarse con la luz ----------

    void noteHurt()
    {
        this.lastHurtTick = this.rocco.level().getGameTime();
    }

    // Medio corazon cada photosynthesisSeconds a la luz (cielo de dia o una antorcha cerca); a
    // oscuras, cinco veces mas lento. No come: hace fotosintesis.
    void tickPhotosynthesis()
    {
        if (!RoccoCommonConfig.PHOTOSYNTHESIS.get() || this.rocco.getHealth() >= this.rocco.getMaxHealth())
        {
            return;
        }
        long now = this.rocco.level().getGameTime();
        if (now - this.lastHurtTick < REGEN_DELAY_TICKS)
        {
            return;
        }
        boolean lit = this.rocco.level().getMaxLocalRawBrightness(this.rocco.blockPosition()) >= 9;
        int every = RoccoCommonConfig.PHOTOSYNTHESIS_SECONDS.get() * 20 * (lit ? 1 : 5);
        if (now % Math.max(1, every) == 0)
        {
            this.rocco.heal(1.0F);
        }
    }

    // ---------- morirse ----------

    // En vez de desaparecer: queda quieto haciendo la animacion de la caida, y al terminarla
    // suelta sus cosas y se va a su tumba.
    void beginDying()
    {
        this.rocco.prepareForDeath();
        this.deathTicks = 0;
        this.waitingForOwner = false;
        this.rocco.setRoccoState(RoccoState.DYING);
        this.rocco.setExpression(RoccoExpression.HURT);
        this.rocco.playSound(ModSounds.HURT.get(), 1.0F, 0.7F);
        notifyOwner(Component.translatable("rocco.death.message", this.rocco.getDisplayName()).withStyle(ChatFormatting.RED));
    }

    void tickDying()
    {
        this.deathTicks++;
        int t = this.deathTicks - RoccoEntity.ANIM_LAG;
        if (t == 8)
        {
            this.rocco.setExpression(RoccoExpression.DIZZY);
        }
        if (t < DEATH_TICKS)
        {
            return;
        }
        if (this.waitingForOwner)
        {
            // Por si el aviso de que reapareciste no llego (su zona estaba descargada): apenas
            // te ve vivo, o si te desconectaste, se va a su tumba igual.
            ServerPlayer owner = this.rocco.trueOwnerAnywhere();
            if (owner == null || !owner.isDeadOrDying())
            {
                this.waitingForOwner = false;
                goToGrave(owner, owner == null ? null : owner.blockPosition());
            }
            return;
        }

        // Suelta lo que lleva, salvo que tenga el poder del cofre de ender.
        if (!this.enderPocket)
        {
            this.rocco.dropEverything();
        }

        ServerPlayer owner = this.rocco.trueOwnerAnywhere();
        if (owner != null && owner.isDeadOrDying())
        {
            // Murieron los dos: se entierra al lado tuyo cuando reaparezcas.
            this.waitingForOwner = true;
            return;
        }
        goToGrave(owner, null);
    }

    public boolean waitingForOwner()
    {
        return this.rocco.getRoccoState() == RoccoState.DYING && this.waitingForOwner;
    }

    // Reapareciste: Rocco se entierra al lado de donde apareciste.
    public void ownerRespawned(ServerPlayer owner)
    {
        if (waitingForOwner())
        {
            this.waitingForOwner = false;
            goToGrave(owner, owner.blockPosition());
        }
    }

    // A 2 o 3 bloques de donde reaparece el dueño (su cama, su ancla o el spawn del mundo), en un
    // bloque firme y entero, sin agua ni lava, y nunca en el lugar exacto donde aparece el jugador.
    private void goToGrave(@Nullable ServerPlayer owner, @Nullable BlockPos respawnedAt)
    {
        MinecraftServer server = this.rocco.getServer();
        if (server == null)
        {
            return;
        }
        ServerLevel level = server.overworld();
        BlockPos base = null;

        if (respawnedAt != null && owner != null)
        {
            level = owner.serverLevel();
            base = respawnedAt;
        }
        else if (owner != null && owner.getRespawnPosition() != null)
        {
            ServerLevel respawnLevel = server.getLevel(owner.getRespawnDimension());
            if (respawnLevel != null)
            {
                Optional<Vec3> stand = Player.findRespawnPositionAndUseSpawnBlock(respawnLevel, owner.getRespawnPosition(),
                        owner.getRespawnAngle(), owner.isRespawnForced(), false);
                if (stand.isPresent())
                {
                    level = respawnLevel;
                    base = BlockPos.containing(stand.get());
                }
            }
        }
        if (base == null)
        {
            BlockPos spawn = level.getSharedSpawnPos();
            base = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
        }

        BlockPos spot = graveSpot(level, base);
        int pose = this.rocco.getRandom().nextInt(3);
        this.graves++;
        this.graveTicks = 0;
        this.rocco.setGravePose(pose);
        this.rocco.setRoccoState(RoccoState.BURIED);
        this.rocco.setHealth(this.rocco.getMaxHealth());
        this.rocco.setExpression(RoccoExpression.DIZZY);
        this.rocco.setPersistenceRequired();

        Component where = Component.literal(spot.getX() + ", " + spot.getY() + ", " + spot.getZ());
        Component message = Component.translatable("rocco.grave.message", this.rocco.getDisplayName(), where).withStyle(ChatFormatting.GRAY);
        if (this.graves == 1)
        {
            message = message.copy().append(" ").append(Component.translatable("rocco.grave.hint").withStyle(ChatFormatting.YELLOW));
        }
        notifyOwner(message);
        RoccoMod.LOGGER.info("[Rocco] Enterrado en {} ({})", spot, level.dimension().location());

        double x = spot.getX() + 0.5D;
        double z = spot.getZ() + 0.5D;
        if (level == this.rocco.level())
        {
            this.rocco.teleportTo(x, spot.getY(), z);
            this.rocco.refreshDimensions();
        }
        else
        {
            // Cambiar de dimension crea otro Rocco a partir del NBT: el estado ya quedo guardado.
            this.rocco.teleportTo(level, x, spot.getY(), z, Set.<RelativeMovement>of(), this.rocco.getYRot(), 0.0F);
        }
    }

    private static BlockPos graveSpot(ServerLevel level, BlockPos base)
    {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dy : new int[]{0, 1, -1, 2, -2})
        {
            for (int dx = -4; dx <= 4; dx++)
            {
                for (int dz = -4; dz <= 4; dz++)
                {
                    int flat = dx * dx + dz * dz;
                    if (flat < 2)
                    {
                        continue;
                    }
                    BlockPos candidate = base.offset(dx, dy, dz);
                    if (!fitsGrave(level, candidate))
                    {
                        continue;
                    }
                    // Mejor a 2 o 3 bloques, a la misma altura, y sobre tierra o pasto.
                    BlockState ground = level.getBlockState(candidate.below());
                    double score = Math.abs(flat - 6) + Math.abs(dy) * 4.0D
                            + (ground.is(net.minecraft.tags.BlockTags.DIRT) ? 0.0D : 2.0D);
                    if (score < bestScore)
                    {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
        }
        return best != null ? best : base;
    }

    // Bloque entero y firme abajo (losas, nieve o tierra arada dejarian ver la mitad enterrada),
    // y aire (o pasto alto, flores) a la altura de los pies y de la cabeza.
    private static boolean fitsGrave(ServerLevel level, BlockPos pos)
    {
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        return ground.isFaceSturdy(level, below, Direction.UP)
                && ground.isCollisionShapeFullBlock(level, below)
                && ground.getFluidState().isEmpty()
                && !ground.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                && feet.getCollisionShape(level, pos).isEmpty() && feet.getFluidState().isEmpty()
                && !feet.is(net.minecraft.tags.BlockTags.FIRE)
                && head.getCollisionShape(level, pos.above()).isEmpty() && head.getFluidState().isEmpty();
    }

    // ---------- la tumba ----------

    // El monticulo se mueve de vez en cuando (Rocco se acomoda adentro): ticks 11 y 38 de cada
    // vuelta de 3 segundos.
    void tickBuried()
    {
        this.graveTicks++;
        int t = this.graveTicks % 60;
        if (t == 11 || t == 38)
        {
            this.rocco.playRoccoSound(ModSounds.GRAVE_TWITCH.get(), 0.35F);
        }
        if (this.rocco.getHealth() < this.rocco.getMaxHealth())
        {
            this.rocco.setHealth(this.rocco.getMaxHealth());
        }
    }

    // Clic derecho sobre el monticulo: el dueño lo desentierra; otro jugador solo lo hace moverse.
    void interactGrave(Player player)
    {
        boolean owner = this.rocco.getOwnerId() == null || this.rocco.getOwnerId().equals(player.getUUID());
        if (!owner)
        {
            this.rocco.playRoccoSound(ModSounds.GRAVE_TWITCH.get(), 0.5F);
            return;
        }
        this.riseTicks = 0;
        this.rocco.setRoccoState(RoccoState.RISING);
        this.rocco.refreshDimensions();
        this.rocco.lookAtEntity(player);
    }

    // grave_rise_*: sale disparado, aterriza mareado, se sacude, se limpia el polvo y saluda.
    void tickRising()
    {
        this.riseTicks++;
        int t = this.riseTicks - RoccoEntity.ANIM_LAG;
        int pose = this.rocco.getGravePose();
        int burst = pose == 1 ? 11 : pose == 2 ? 9 : 10;
        int cross = burst + 4;

        if (t == 0)
        {
            this.rocco.playRoccoSound(ModSounds.GRAVE_TWITCH.get(), 0.35F);
        }
        else if (t == burst)
        {
            this.rocco.playSound(ModSounds.GRAVE_BURST.get(), 0.9F, 1.0F);
        }
        else if (t == cross)
        {
            this.rocco.playRoccoSound(ModSounds.CROSS_FALL.get(), 0.6F);
        }
        else if (t == 23)
        {
            this.rocco.playRoccoSound(ModSounds.STEP.get(), 0.4F);
        }
        else if (t == 36)
        {
            this.rocco.playRoccoSound(ModSounds.SHAKE_OFF.get(), 0.6F);
        }
        else if (t == 52)
        {
            this.rocco.playRoccoSound(ModSounds.DUST_PAT.get(), 0.5F);
        }
        else if (t == 60)
        {
            this.rocco.playRoccoSound(ModSounds.CHEER.get(), 0.6F);
        }
        else if (t == 67)
        {
            this.rocco.playRoccoSound(ModSounds.STEP.get(), 0.15F);
        }

        RoccoExpression face = switch (t)
        {
            case 0 -> RoccoExpression.DIZZY;
            case 9 -> RoccoExpression.WOW;
            case 23 -> RoccoExpression.DIZZY;
            case 36 -> RoccoExpression.WINDY;
            case 50 -> RoccoExpression.HAPPY;
            case 60 -> RoccoExpression.CHEER;
            default -> null;
        };
        if (face != null)
        {
            this.rocco.setExpression(face);
        }

        if (t >= RISE_TICKS)
        {
            this.rocco.setRoccoState(RoccoState.ACTIVE);
            this.rocco.setExpression(RoccoExpression.HAPPY);
            this.rocco.setFollowing(true);
            this.rocco.refreshDimensions();
            this.rocco.sayLine("grave_back");
        }
    }

    // ---------- el poder del cofre de ender ----------

    public boolean hasEnderPocket()
    {
        return this.enderPocket;
    }

    // Pusiste un cofre de ender cerca: si todavia no tiene el poder, va y se lo come.
    public void noticeEnderChest(BlockPos pos)
    {
        this.enderTarget = pos.immutable();
        this.enderTargetUntil = this.rocco.level().getGameTime() + ENDER_NOTICE_TICKS;
    }

    // "Comete ese cofre de ender": sin apuro.
    public boolean orderEnderChest(BlockPos pos)
    {
        if (this.enderPocket)
        {
            this.rocco.sayLine("ender_already");
            return false;
        }
        this.enderTarget = pos.immutable();
        this.enderTargetUntil = this.rocco.level().getGameTime() + ENDER_NOTICE_TICKS * 5;
        return true;
    }

    // Una vez por segundo, si esta libre: el cofre que le pusiste cerca, o uno que tenga encima.
    void tickEnder()
    {
        int carried = this.rocco.countInInventory(Items.ENDER_CHEST);
        boolean gotOne = carried > this.enderChestsSeen;
        this.enderChestsSeen = carried;

        if (this.enderTarget != null && this.rocco.level().getGameTime() > this.enderTargetUntil)
        {
            this.enderTarget = null;
        }
        if (this.enderPocket)
        {
            if (gotOne || this.enderTarget != null)
            {
                this.enderTarget = null;
                this.rocco.sayLine("ender_already");
            }
            return;
        }
        if (!this.rocco.isFreeTime() || this.enderAwakening)
        {
            return;
        }

        if (this.enderTarget != null)
        {
            BlockPos target = this.enderTarget;
            this.enderTarget = null;
            if (this.rocco.level().getBlockState(target).getBlock() instanceof EnderChestBlock)
            {
                this.rocco.swallowEnderChest(target);
                return;
            }
        }
        if (carried > 0)
        {
            this.rocco.absorbEnderChest();
        }
    }

    void startAwakening()
    {
        this.enderAwakening = true;
    }

    // El tick 41 de ender_awaken (la explosion de luz). Si la animacion se corta antes, el poder
    // igual se activa en ese momento: nunca se avisa sin activarlo.
    void grantEnderPocket()
    {
        this.enderAwakening = false;
        if (this.enderPocket)
        {
            return;
        }
        this.enderPocket = true;
        Component message = Component.translatable("rocco.ender.unlocked", this.rocco.getDisplayName())
                .withStyle(ChatFormatting.LIGHT_PURPLE);
        if (RoccoCommonConfig.ENDER_MESSAGE_TO_ALL.get() && this.rocco.level() instanceof ServerLevel level)
        {
            for (ServerPlayer nearby : level.players())
            {
                if (nearby.distanceToSqr(this.rocco) < 32.0D * 32.0D)
                {
                    nearby.sendSystemMessage(message);
                }
            }
            ServerPlayer owner = this.rocco.trueOwnerAnywhere();
            if (owner != null && (owner.level() != level || owner.distanceToSqr(this.rocco) >= 32.0D * 32.0D))
            {
                owner.sendSystemMessage(message);
            }
        }
        else
        {
            notifyOwner(message);
        }
    }

    boolean awakeningPending()
    {
        return this.enderAwakening;
    }

    // ---------- Roco Cola ----------

    boolean colaRecently(long ticks)
    {
        return this.rocco.level().getGameTime() - this.lastCola < ticks;
    }

    boolean colaRequestTooSoon()
    {
        return colaRecently(COLA_REQUEST_COOLDOWN);
    }

    boolean colaIdleReady()
    {
        return !colaRecently(RoccoCommonConfig.COLA_IDLE_COOLDOWN_MINUTES.get() * 1200L);
    }

    void drankCola()
    {
        this.lastCola = this.rocco.level().getGameTime();
    }

    // ---------- avisos y guardado ----------

    // Al dueño, aunque este lejos o en otra dimension.
    private void notifyOwner(Component message)
    {
        ServerPlayer owner = this.rocco.trueOwnerAnywhere();
        if (owner != null)
        {
            owner.sendSystemMessage(message);
        }
    }

    void save(CompoundTag tag)
    {
        tag.putBoolean("EnderPocket", this.enderPocket);
        tag.putInt("Graves", this.graves);
        tag.putLong("LastCola", this.lastCola);
        tag.putBoolean("WaitingForOwner", this.waitingForOwner);
    }

    void load(CompoundTag tag)
    {
        this.enderPocket = tag.getBoolean("EnderPocket");
        this.graves = tag.getInt("Graves");
        if (tag.contains("LastCola"))
        {
            this.lastCola = tag.getLong("LastCola");
        }
        this.waitingForOwner = tag.getBoolean("WaitingForOwner");
        // Se cerro el mundo a mitad de la caida: al cargar termina enseguida.
        this.deathTicks = DEATH_TICKS + RoccoEntity.ANIM_LAG;
    }
}

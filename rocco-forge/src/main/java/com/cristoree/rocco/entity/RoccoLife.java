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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

// La vida de Rocco: se cura solo (mas lento a oscuras) y mas rapido despues de comer, come y toma
// pociones o manzanas doradas si lo dejaste, se muere con su animacion, a los 15 segundos reaparece
// enterradito cerca tuyo y a los 5 minutos su tumba se muda a tu punto de reaparicion (y lo sigue
// si lo cambias). Tambien el poder del cofre de ender y los tiempos de la Roco Cola.
// Corre solo en el servidor; la entidad le avisa en cada tick y al guardar.
public final class RoccoLife
{
    // death dura 1,8 s; grave_rise_* 3,8 s.
    static final int DEATH_TICKS = 36;
    static final int RISE_TICKS = 76;
    // Desde que se murio hasta que aparece enterrado cerca tuyo: 15 segundos.
    static final int REAPPEAR_TICKS = 300;
    // Enterrado 5 minutos, la tumba se muda a tu punto de reaparicion.
    private static final int MOVE_GRAVE_TICKS = 6000;
    // Despues de un golpe espera esto para empezar a curarse (no se cura en medio de una pelea).
    private static final int REGEN_DELAY_TICKS = 100;
    // Cada medio corazon curado con la saciedad gasta esto (como el jugador: 1,5 por punto).
    private static final float SATURATION_PER_HEAL = 1.5F;
    // Entre una cosa y otra que come o toma solo.
    private static final int AUTO_FOOD_COOLDOWN = 200;
    private static final int AUTO_POTION_COOLDOWN = 100;
    private static final int AUTO_GOLDEN_COOLDOWN = 600;
    // Pedirle otra Roco Cola: 30 segundos entre una y otra.
    private static final long COLA_REQUEST_COOLDOWN = 600L;
    // El cofre de ender que pusiste cerca: se lo come si fue hace menos de un minuto.
    private static final long ENDER_NOTICE_TICKS = 1200L;

    private final RoccoEntity rocco;

    private int deathTicks;
    private int graveTicks;
    private int riseTicks;
    // Cuantas veces se murio: la explicacion del clic derecho va solo la primera.
    private int graves;
    // Ya se mudo a tu punto de reaparicion, y cual era (para seguirlo si lo cambias).
    private boolean graveMoved;
    private String graveRespawn = "";

    private boolean enderPocket;
    private boolean enderAwakening;
    private int enderChestsSeen;
    @Nullable
    private BlockPos enderTarget;
    private long enderTargetUntil;

    private long lastHurtTick = -1_000_000L;
    private long lastCola = -1_000_000L;

    // Saciedad: mientras le queda, se cura el doble de rapido.
    private float saturation;
    private float regenProgress;
    // Lo que puede comer o tomar solo (RoccoFood.FOOD | POTIONS | GOLDEN).
    private int consumePerms = RoccoFood.FOOD;
    private long nextAutoFood;
    private long nextAutoPotion;
    private long nextAutoGolden;

    RoccoLife(RoccoEntity rocco)
    {
        this.rocco = rocco;
    }

    // ---------- curarse ----------

    void noteHurt()
    {
        this.lastHurtTick = this.rocco.level().getGameTime();
    }

    boolean recentlyHurt(long ticks)
    {
        return this.rocco.level().getGameTime() - this.lastHurtTick < ticks;
    }

    // Medio corazon cada regenSeconds (1,5 s) a la luz y el doble a oscuras: hace fotosintesis.
    // Con saciedad (despues de comer) va al doble de rapido, y cada medio corazon la gasta. Despues
    // de un golpe espera 5 segundos, asi no se cura en medio de una pelea.
    void tickRegen()
    {
        if (this.rocco.getHealth() >= this.rocco.getMaxHealth())
        {
            this.regenProgress = 0.0F;
            return;
        }
        if (recentlyHurt(REGEN_DELAY_TICKS))
        {
            return;
        }
        boolean natural = RoccoCommonConfig.PHOTOSYNTHESIS.get();
        boolean fed = this.saturation > 0.0F;
        if (!natural && !fed)
        {
            return;
        }
        boolean lit = this.rocco.level().getMaxLocalRawBrightness(this.rocco.blockPosition()) >= 9;
        double every = RoccoCommonConfig.REGEN_SECONDS.get() * 20.0D * (lit || fed ? 1.0D : 2.0D) * (fed ? 0.5D : 1.0D);
        this.regenProgress += 1.0F;
        if (this.regenProgress >= every)
        {
            this.regenProgress = 0.0F;
            this.rocco.heal(1.0F);
            if (fed)
            {
                this.saturation = Math.max(0.0F, this.saturation - SATURATION_PER_HEAL);
            }
        }
    }

    public float saturation()
    {
        return this.saturation;
    }

    // ---------- comer y tomar ----------

    public int consumePerms()
    {
        return this.consumePerms;
    }

    public void setConsumePerms(int perms)
    {
        this.consumePerms = perms & (RoccoFood.FOOD | RoccoFood.POTIONS | RoccoFood.GOLDEN);
    }

    public boolean mayUse(int perm)
    {
        return (this.consumePerms & perm) != 0;
    }

    // Lo que le hace comer o tomar algo: la comida le da saciedad y sus efectos (la manzana dorada,
    // absorcion y regeneracion), la pocion hace lo suyo, y lo que sobra (el bol, la botella)
    // vuelve a su inventario.
    void consume(ItemStack stack)
    {
        if (stack.isEmpty() || !(this.rocco.level() instanceof ServerLevel level))
        {
            return;
        }
        ItemStack one = stack.copyWithCount(1);
        if (one.getItem() instanceof net.minecraft.world.item.PotionItem)
        {
            for (MobEffectInstance effect : PotionUtils.getMobEffects(one))
            {
                if (effect.getEffect().isInstantenous())
                {
                    effect.getEffect().applyInstantenousEffect(null, null, this.rocco, effect.getAmplifier(), 1.0D);
                }
                else
                {
                    this.rocco.addEffect(new MobEffectInstance(effect));
                }
            }
        }
        else if (one.isEdible())
        {
            this.saturation = Math.min(RoccoFood.MAX_SATURATION, this.saturation + RoccoFood.saturationOf(one));
            net.minecraft.world.food.FoodProperties food = one.getFoodProperties(this.rocco);
            if (food != null)
            {
                for (com.mojang.datafixers.util.Pair<MobEffectInstance, Float> effect : food.getEffects())
                {
                    if (effect.getFirst() != null && this.rocco.getRandom().nextFloat() < effect.getSecond())
                    {
                        this.rocco.addEffect(new MobEffectInstance(effect.getFirst()));
                    }
                }
            }
            // La miel cura el veneno, como al jugador.
            if (one.getItem() instanceof net.minecraft.world.item.HoneyBottleItem)
            {
                this.rocco.removeEffect(MobEffects.POISON);
            }
        }
        ItemStack leftover = RoccoFood.leftoverOf(one);
        if (!leftover.isEmpty())
        {
            this.rocco.keep(leftover);
        }
    }

    // Una vez por segundo, si esta libre: come, toma una pocion o una manzana dorada cuando le hace
    // falta, segun lo que le permitiste. Equilibrado para que no gaste todo de una:
    //  - comida comun: con menos del 60 % de vida y sin saciedad, cada 10 segundos;
    //  - pocion de resistencia al fuego si se esta quemando; de curacion con menos del 40 %, o de
    //    regeneracion con menos del 50 % (si no tiene de curacion y no esta peleando);
    //  - en una pelea no se pone a comer: solo la pocion de curacion o de fuego, o la manzana;
    //  - manzana dorada en apuros (menos del 35 % y peleando); la encantada solo si no tiene otra
    //    y le queda menos del 20 %. Una cada 30 segundos.
    void tickConsumables()
    {
        if (!this.rocco.isFreeTime())
        {
            return;
        }
        float ratio = this.rocco.getHealth() / this.rocco.getMaxHealth();
        long now = this.rocco.level().getGameTime();
        java.util.List<ItemStack> carried = com.cristoree.rocco.work.Inv.contents(this.rocco.inventory());
        boolean fighting = recentlyHurt(REGEN_DELAY_TICKS) || this.rocco.combatStance() != RoccoCombat.STANCE_NONE;

        if (mayUse(RoccoFood.POTIONS) && now >= this.nextAutoPotion)
        {
            ItemStack potion = ItemStack.EMPTY;
            if (this.rocco.isOnFire() && !this.rocco.hasEffect(MobEffects.FIRE_RESISTANCE))
            {
                potion = RoccoFood.find(carried, stack -> RoccoFood.potionFor(stack, RoccoFood.Need.FIRE));
            }
            if (potion.isEmpty() && ratio < 0.4F)
            {
                potion = RoccoFood.find(carried, stack -> RoccoFood.potionFor(stack, RoccoFood.Need.HEAL));
            }
            if (potion.isEmpty() && !fighting && ratio < 0.5F && !this.rocco.hasEffect(MobEffects.REGENERATION))
            {
                potion = RoccoFood.find(carried, stack -> RoccoFood.potionFor(stack, RoccoFood.Need.REGEN));
            }
            if (!potion.isEmpty())
            {
                this.nextAutoPotion = now + AUTO_POTION_COOLDOWN;
                this.rocco.useConsumable(potion);
                return;
            }
        }

        if (mayUse(RoccoFood.GOLDEN) && now >= this.nextAutoGolden && fighting && ratio < 0.35F)
        {
            ItemStack apple = RoccoFood.find(carried, stack -> stack.is(Items.GOLDEN_APPLE));
            if (apple.isEmpty() && ratio < 0.2F)
            {
                apple = RoccoFood.find(carried, stack -> stack.is(Items.ENCHANTED_GOLDEN_APPLE));
            }
            if (!apple.isEmpty())
            {
                this.nextAutoGolden = now + AUTO_GOLDEN_COOLDOWN;
                this.rocco.useConsumable(apple);
                return;
            }
        }

        if (mayUse(RoccoFood.FOOD) && !fighting && now >= this.nextAutoFood && ratio < 0.6F && this.saturation < 4.0F)
        {
            ItemStack food = RoccoFood.bestFood(carried);
            if (!food.isEmpty())
            {
                this.nextAutoFood = now + AUTO_FOOD_COOLDOWN;
                this.rocco.useConsumable(food);
            }
        }
    }

    // ---------- morirse ----------

    // En vez de desaparecer: hace la animacion de la caida, suelta sus cosas, se lo lleva la
    // tierra (una nubecita) y a los 15 segundos aparece enterradito cerca tuyo.
    void beginDying()
    {
        this.rocco.prepareForDeath();
        this.deathTicks = 0;
        this.saturation = 0.0F;
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
        if (t == DEATH_TICKS)
        {
            vanish();
        }
        if (t < REAPPEAR_TICKS)
        {
            return;
        }

        // Si estas muerto, espera a que reaparezcas para enterrarse al lado tuyo. Si no estas
        // conectado, se entierra ahi donde se murio.
        ServerPlayer owner = this.rocco.trueOwnerAnywhere();
        if (owner != null && owner.isDeadOrDying())
        {
            return;
        }
        if (owner != null)
        {
            bury(owner.serverLevel(), owner.blockPosition(), "rocco.grave.message");
        }
        else if (this.rocco.level() instanceof ServerLevel level)
        {
            bury(level, this.rocco.blockPosition(), "rocco.grave.message");
        }
    }

    // Termino la caida: suelta lo que lleva (salvo con el poder del cofre de ender) y desaparece.
    private void vanish()
    {
        if (!this.enderPocket)
        {
            this.rocco.dropEverything();
        }
        this.rocco.setInvisible(true);
        this.rocco.setNoGravity(true);
        this.rocco.setDeltaMovement(Vec3.ZERO);
        // La nubecita del juego al morir.
        this.rocco.level().broadcastEntityEvent(this.rocco, (byte) 60);
    }

    public boolean waitingForOwner()
    {
        return this.rocco.getRoccoState() == RoccoState.DYING;
    }

    // Reapareciste: si ya pasaron los 15 segundos, se entierra al lado tuyo enseguida (si no,
    // cuando se cumplan, tambien al lado tuyo).
    public void ownerRespawned(ServerPlayer owner)
    {
        if (waitingForOwner() && this.deathTicks - RoccoEntity.ANIM_LAG >= REAPPEAR_TICKS)
        {
            bury(owner.serverLevel(), owner.blockPosition(), "rocco.grave.message");
        }
    }

    // Lo llamaste (con el boton, la tecla o /rocco llamar): muerto o enterrado, su tumba aparece al
    // lado tuyo. Despues hay que desenterrarlo igual.
    public void summonTo(ServerPlayer player)
    {
        if (this.rocco.getRoccoState() == RoccoState.DYING && this.deathTicks - RoccoEntity.ANIM_LAG < DEATH_TICKS)
        {
            vanish();
        }
        bury(player.serverLevel(), player.blockPosition(), "rocco.grave.message");
    }

    // A 2 o 3 bloques de ese lugar, en un bloque firme y entero, sin agua ni lava, y nunca en el
    // lugar exacto del jugador. Cambiar de dimension crea otro Rocco a partir del NBT: el estado
    // ya queda guardado antes.
    private void bury(ServerLevel level, BlockPos near, String messageKey)
    {
        boolean fresh = this.rocco.getRoccoState() != RoccoState.BURIED;
        BlockPos spot = graveSpot(level, near);
        if (fresh)
        {
            this.graves++;
            this.rocco.setGravePose(this.rocco.getRandom().nextInt(3));
        }
        this.graveTicks = 0;
        this.graveMoved = false;
        this.graveRespawn = "";
        this.rocco.setInvisible(false);
        this.rocco.setNoGravity(false);
        this.rocco.setRoccoState(RoccoState.BURIED);
        this.rocco.setHealth(this.rocco.getMaxHealth());
        this.rocco.setExpression(RoccoExpression.DIZZY);
        this.rocco.setPersistenceRequired();

        Component message = Component.translatable(messageKey, this.rocco.getDisplayName(), coords(spot)).withStyle(ChatFormatting.GRAY);
        if (fresh && this.graves == 1)
        {
            message = message.copy().append(" ").append(Component.translatable("rocco.grave.hint").withStyle(ChatFormatting.YELLOW));
        }
        notifyOwner(message);
        moveTo(level, spot);
    }

    // A los 5 minutos la tumba se muda a tu punto de reaparicion (tu cama, tu ancla o el spawn del
    // mundo), y desde ahi lo sigue si lo cambias. Sin dueño conectado, espera.
    private void tickGraveMoves()
    {
        if (this.graveTicks % 20 != 0)
        {
            return;
        }
        ServerPlayer owner = this.rocco.trueOwnerAnywhere();
        if (owner == null)
        {
            return;
        }
        String respawn = respawnKey(owner);
        boolean due = !this.graveMoved && this.graveTicks >= MOVE_GRAVE_TICKS;
        boolean changed = this.graveMoved && !respawn.equals(this.graveRespawn);
        if (!due && !changed)
        {
            return;
        }
        MinecraftServer server = this.rocco.getServer();
        if (server == null)
        {
            return;
        }
        ServerLevel level = server.overworld();
        BlockPos base = null;
        if (owner.getRespawnPosition() != null)
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
            base = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level.getSharedSpawnPos());
        }
        this.graveMoved = true;
        this.graveRespawn = respawn;
        BlockPos spot = graveSpot(level, base);
        notifyOwner(Component.translatable("rocco.grave.moved", this.rocco.getDisplayName(), coords(spot)).withStyle(ChatFormatting.GRAY));
        moveTo(level, spot);
    }

    // "dimension|x,y,z" del punto de reaparicion, o "dimension|spawn" si no tiene cama ni ancla.
    private static String respawnKey(ServerPlayer owner)
    {
        BlockPos pos = owner.getRespawnPosition();
        return owner.getRespawnDimension().location() + "|" + (pos == null ? "spawn" : pos.getX() + "," + pos.getY() + "," + pos.getZ());
    }

    private void moveTo(ServerLevel level, BlockPos spot)
    {
        RoccoMod.LOGGER.info("[Rocco] Tumba en {} ({})", spot, level.dimension().location());
        double x = spot.getX() + 0.5D;
        double z = spot.getZ() + 0.5D;
        if (level == this.rocco.level())
        {
            this.rocco.teleportTo(x, spot.getY(), z);
            this.rocco.setDeltaMovement(Vec3.ZERO);
            this.rocco.refreshDimensions();
        }
        else
        {
            this.rocco.teleportTo(level, x, spot.getY(), z, Set.<RelativeMovement>of(), this.rocco.getYRot(), 0.0F);
        }
    }

    private static Component coords(BlockPos pos)
    {
        return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
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
        tickGraveMoves();
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
        tag.putInt("DeathTicks", this.deathTicks);
        tag.putInt("GraveTicks", this.graveTicks);
        tag.putBoolean("GraveMoved", this.graveMoved);
        tag.putString("GraveRespawn", this.graveRespawn);
        tag.putFloat("Saturation", this.saturation);
        tag.putInt("Consume", this.consumePerms);
    }

    void load(CompoundTag tag)
    {
        this.enderPocket = tag.getBoolean("EnderPocket");
        this.graves = tag.getInt("Graves");
        if (tag.contains("LastCola"))
        {
            this.lastCola = tag.getLong("LastCola");
        }
        this.deathTicks = tag.getInt("DeathTicks");
        this.graveTicks = tag.getInt("GraveTicks");
        this.graveMoved = tag.getBoolean("GraveMoved");
        this.graveRespawn = tag.getString("GraveRespawn");
        this.saturation = tag.getFloat("Saturation");
        this.consumePerms = tag.contains("Consume") ? tag.getInt("Consume") : RoccoFood.FOOD;
        // Se cerro el mundo con Rocco muerto: sigue invisible esperando para reaparecer.
        if (this.rocco.getRoccoState() == RoccoState.DYING && this.deathTicks - RoccoEntity.ANIM_LAG >= DEATH_TICKS)
        {
            this.rocco.setInvisible(true);
            this.rocco.setNoGravity(true);
        }
    }
}

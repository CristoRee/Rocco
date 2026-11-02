package com.cristoree.rocco.entity;

import com.cristoree.rocco.config.RoccoCommonConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

// Rocco peleando: a quien puede atacar (las tres casillas de su menu y las reglas que nunca se
// rompen), como elige el objetivo (defenderte, ayudarte, defenderse, y si se lo activaste, cazar
// monstruos cerca tuyo) y como pega: el daño cae en el tick del golpe de cada animacion, con el
// arco y la ballesta usa las flechas de su inventario, y con escudo se cubre como el jugador.
public final class RoccoCombat
{
    public static final int HOSTILE = 1;
    public static final int PEACEFUL = 1 << 1;
    public static final int PLAYERS = 1 << 2;

    // La postura que ve el cliente (DATA_COMBAT): de ahi salen los bucles de guardia y apuntar.
    public static final int STANCE_NONE = 0;
    public static final int STANCE_GUARD = 1;
    public static final int STANCE_BOW = 2;
    public static final int STANCE_CROSSBOW = 3;
    public static final int STANCE_SHIELD = 4;

    // Distancias: con arco o ballesta se queda entre 6 y 12 bloques; a mas de 24 deja de perseguir.
    private static final double RANGED_MIN = 6.0D;
    private static final double RANGED_MAX = 12.0D;
    private static final double GIVE_UP = 24.0D;
    // Despues de la pelea queda en guardia un ratito antes de volver a lo de siempre.
    private static final int CALM_DOWN_TICKS = 60;

    public static int defaultTargets()
    {
        return (RoccoCommonConfig.DEFAULT_ATTACK_HOSTILES.get() ? HOSTILE : 0)
                | (RoccoCommonConfig.DEFAULT_ATTACK_PEACEFUL.get() ? PEACEFUL : 0)
                | (RoccoCommonConfig.DEFAULT_ATTACK_PLAYERS.get() ? PLAYERS : 0);
    }

    // ---------- a quien puede atacar ----------

    // Las reglas fijas primero (nunca a su dueño, ni a otro Rocco, ni a un jugador en creativo o
    // espectador; en Pacifico no hay monstruos) y despues las casillas del menu.
    public static boolean mayAttack(RoccoEntity rocco, @Nullable LivingEntity target)
    {
        if (target == null || !target.isAlive() || target == rocco || target instanceof RoccoEntity
                || target.level() != rocco.level() || !target.canBeSeenAsEnemy())
        {
            return false;
        }
        if (rocco.getOwnerId() != null && rocco.getOwnerId().equals(target.getUUID()))
        {
            return false;
        }
        // Se lo ordenaste vos ("ataca al golem"): vale aunque no este en las casillas del menu.
        if (rocco.isOrderedTarget(target))
        {
            return !(target instanceof Player player) || (!player.isCreative() && !player.isSpectator());
        }
        int allowed = rocco.attackTargets();
        if (target instanceof Player player)
        {
            return !player.isCreative() && !player.isSpectator() && (allowed & PLAYERS) != 0;
        }
        if (target instanceof Enemy)
        {
            if (rocco.level().getDifficulty() == Difficulty.PEACEFUL || (allowed & HOSTILE) == 0)
            {
                return false;
            }
            // Le explotan en la cara: solo con arco o ballesta, salvo que lo actives en la config.
            return !(target instanceof Creeper) || RoccoCommonConfig.ATTACK_CREEPERS.get()
                    || RoccoGear.attackFor(rocco.weapon()).ranged() && hasArrows(rocco);
        }
        return (allowed & PEACEFUL) != 0;
    }

    // ---------- elegir objetivo ----------

    // Defenderte (lo que te pega), ayudarte (lo que vos atacas), defenderse y, si esta prendido,
    // cazar monstruos que se te acercan. Una sola meta que revisa todo cada medio segundo.
    static final class PickTarget extends Goal
    {
        private final RoccoEntity rocco;
        private int ownerHurtStamp;
        private int ownerHitStamp;
        private int selfHurtStamp;
        private int cooldown;

        PickTarget(RoccoEntity rocco)
        {
            this.rocco = rocco;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse()
        {
            // Sentado o tomando mate tambien te defiende: deja eso y va.
            if (--this.cooldown > 0 || !this.rocco.canDropForFight() || this.rocco.hasOrderedTarget())
            {
                return false;
            }
            this.cooldown = 10;
            LivingEntity found = choose();
            if (found == null || found == this.rocco.getTarget())
            {
                return false;
            }
            if (!this.rocco.canFight())
            {
                this.rocco.dropForFight();
            }
            this.rocco.setTarget(found);
            return false;
        }

        @Nullable
        private LivingEntity choose()
        {
            // Defenderse: lo que le pego a el (los golpes de su dueño no cuentan).
            LivingEntity attacker = this.rocco.getLastHurtByMob();
            int selfStamp = this.rocco.getLastHurtByMobTimestamp();
            if (attacker != null && selfStamp != this.selfHurtStamp)
            {
                this.selfHurtStamp = selfStamp;
                if (mayAttack(this.rocco, attacker))
                {
                    return attacker;
                }
            }

            ServerPlayer owner = this.rocco.trueOwner();
            if (owner == null || owner.distanceToSqr(this.rocco) > GIVE_UP * GIVE_UP)
            {
                return null;
            }
            if (RoccoCommonConfig.DEFEND_OWNER.get())
            {
                // Antes de que te pegue: el monstruo (o lo que sea) que te tiene de objetivo, o a el.
                LivingEntity threat = this.rocco.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                                owner.getBoundingBox().inflate(16.0D),
                                mob -> (mob.getTarget() == owner || mob.getTarget() == this.rocco) && mayAttack(this.rocco, mob))
                        .stream()
                        .min(java.util.Comparator.comparingDouble(owner::distanceToSqr))
                        .orElse(null);
                if (threat != null && threat != this.rocco.getTarget())
                {
                    return threat;
                }
                LivingEntity hurtOwner = owner.getLastHurtByMob();
                int stamp = owner.getLastHurtByMobTimestamp();
                if (hurtOwner != null && stamp != this.ownerHurtStamp)
                {
                    this.ownerHurtStamp = stamp;
                    if (mayAttack(this.rocco, hurtOwner))
                    {
                        return hurtOwner;
                    }
                }
            }
            if (RoccoCommonConfig.ASSIST_OWNER.get())
            {
                LivingEntity ownerTarget = owner.getLastHurtMob();
                int stamp = owner.getLastHurtMobTimestamp();
                if (ownerTarget != null && stamp != this.ownerHitStamp)
                {
                    this.ownerHitStamp = stamp;
                    // Si le pegaste sin querer a tu propia mascota, no la ataca (salvo que le
                    // hayas permitido los pacificos).
                    boolean ownPet = ownerTarget instanceof TamableAnimal pet && pet.isOwnedBy(owner);
                    if (mayAttack(this.rocco, ownerTarget) && (!ownPet || (this.rocco.attackTargets() & PEACEFUL) != 0))
                    {
                        return ownerTarget;
                    }
                }
            }
            if (RoccoCommonConfig.HUNT_HOSTILES.get() && this.rocco.getTarget() == null)
            {
                return this.rocco.level().getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(8.0D),
                                entity -> entity instanceof Enemy && mayAttack(this.rocco, entity))
                        .stream()
                        .min(java.util.Comparator.comparingDouble(owner::distanceToSqr))
                        .orElse(null);
            }
            return null;
        }
    }

    // ---------- pelear ----------

    static final class Fight extends Goal
    {
        private final RoccoEntity rocco;
        private int cooldown;
        private int swingTicks = -1;
        private RoccoGear.Attack swing = RoccoGear.Attack.PUNCH;
        private int drawTicks = -1;
        private int repath;
        private boolean warnedArrows;

        Fight(RoccoEntity rocco)
        {
            this.rocco = rocco;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            LivingEntity target = this.rocco.getTarget();
            return this.rocco.canFight() && valid(target);
        }

        @Override
        public boolean canContinueToUse()
        {
            return canUse() || this.swingTicks >= 0;
        }

        private boolean valid(@Nullable LivingEntity target)
        {
            if (!mayAttack(this.rocco, target))
            {
                return false;
            }
            ServerPlayer owner = this.rocco.trueOwner();
            // No se va lejisimo detras de algo: si vos te alejaste, vuelve.
            double far = GIVE_UP * GIVE_UP;
            return this.rocco.distanceToSqr(target) < far && (owner == null || owner.distanceToSqr(target) < far);
        }

        @Override
        public void start()
        {
            this.cooldown = 4;
            this.swingTicks = -1;
            this.drawTicks = -1;
            this.repath = 0;
            this.rocco.enterCombat();
            this.rocco.sayLineOnce("combat_start", 400);
        }

        @Override
        public void stop()
        {
            LivingEntity target = this.rocco.getTarget();
            if (target != null && !target.isAlive())
            {
                this.rocco.sayLineOnce("combat_win", 600);
            }
            this.rocco.setTarget(null);
            this.rocco.clearOrderedTarget();
            this.rocco.getNavigation().stop();
            this.rocco.stopUsingItem();
            this.rocco.leaveCombat(CALM_DOWN_TICKS);
            this.swingTicks = -1;
            this.drawTicks = -1;
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void tick()
        {
            LivingEntity target = this.rocco.getTarget();
            if (target == null)
            {
                return;
            }
            this.rocco.getLookControl().setLookAt(target, 30.0F, 30.0F);
            this.rocco.enterCombat();

            // La espera entre golpes corre tambien durante la animacion (si no, pegaba la mitad de seguido).
            if (this.cooldown > 0)
            {
                this.cooldown--;
            }

            // Un golpe a la mitad: el daño cae en su tick, aunque el objetivo se haya movido un poco.
            if (this.swingTicks >= 0)
            {
                this.swingTicks++;
                if (this.swingTicks == this.swing.impactTick() + RoccoEntity.ANIM_LAG)
                {
                    land(target);
                }
                if (this.swingTicks >= this.swing.ticks() + RoccoEntity.ANIM_LAG)
                {
                    this.swingTicks = -1;
                }
                return;
            }

            RoccoGear.Attack attack = RoccoGear.attackFor(this.rocco.weapon());
            if (attack.ranged() && !hasArrows(this.rocco))
            {
                if (!this.warnedArrows)
                {
                    this.warnedArrows = true;
                    this.rocco.sayLineOnce("combat_no_arrows", 1200);
                }
                attack = RoccoGear.Attack.PUNCH;
            }

            if (attack.ranged())
            {
                shootAt(target, attack);
            }
            else
            {
                meleeAt(target, attack);
            }
        }

        // ---------- cuerpo a cuerpo ----------

        private void meleeAt(LivingEntity target, RoccoGear.Attack attack)
        {
            double distance = this.rocco.distanceToSqr(target);
            double reach = reachSqr(target);
            boolean close = distance <= reach;

            // Con escudo se cubre mientras espera para pegar, si el otro esta encima.
            boolean shield = RoccoGear.isShield(this.rocco.getOffhandItem());
            if (shield && this.cooldown > 0 && distance < 9.0D
                    && target instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == this.rocco)
            {
                if (!this.rocco.isUsingItem())
                {
                    this.rocco.startUsingItem(InteractionHand.OFF_HAND);
                }
                this.rocco.setCombatStance(STANCE_SHIELD);
            }
            else
            {
                this.rocco.stopUsingItem();
                this.rocco.setCombatStance(STANCE_GUARD);
            }

            if (!close)
            {
                if (--this.repath <= 0)
                {
                    this.repath = 8;
                    this.rocco.getNavigation().moveTo(target, 1.25D);
                }
                return;
            }
            this.rocco.getNavigation().stop();
            if (this.cooldown <= 0 && this.rocco.hasLineOfSight(target))
            {
                this.rocco.stopUsingItem();
                this.swing = attack;
                this.swingTicks = 0;
                this.cooldown = Math.max(attack.ticks(), cooldownFromSpeed());
                this.rocco.playAttack(attack);
            }
        }

        private double reachSqr(LivingEntity target)
        {
            double reach = this.rocco.getBbWidth() * 2.0D;
            return reach * reach + target.getBbWidth() + 0.6D;
        }

        // 20 / velocidad de ataque: con una espada (1,6) unos 12 ticks; a piñas (4) 5.
        private int cooldownFromSpeed()
        {
            double speed = this.rocco.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
            return speed <= 0.0D ? 20 : (int) Math.ceil(20.0D / speed);
        }

        // El golpe: credito al dueño (asi suelta experiencia y el botin de "matado por un jugador"),
        // daño con el arma de verdad (encantamientos incluidos) y el desgaste.
        private void land(LivingEntity target)
        {
            if (!target.isAlive() || this.rocco.distanceToSqr(target) > reachSqr(target) + 2.5D)
            {
                return;
            }
            if (this.swing.ranged())
            {
                return;
            }
            creditOwner(target);
            this.rocco.playSound(this.swing == RoccoGear.Attack.PUNCH ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_SWEEP,
                    0.7F, 0.9F + this.rocco.getRandom().nextFloat() * 0.2F);
            if (this.rocco.doHurtTarget(target))
            {
                this.rocco.wearWeapon();
            }
        }

        // ---------- arco y ballesta ----------

        private void shootAt(LivingEntity target, RoccoGear.Attack attack)
        {
            double distance = Math.sqrt(this.rocco.distanceToSqr(target));
            boolean sees = this.rocco.hasLineOfSight(target);

            // Se acerca o se aleja para quedar a buena distancia.
            if (--this.repath <= 0)
            {
                this.repath = 10;
                if (distance > RANGED_MAX || !sees)
                {
                    this.rocco.getNavigation().moveTo(target, 1.15D);
                }
                else if (distance < RANGED_MIN)
                {
                    Vec3 away = this.rocco.position().subtract(target.position()).normalize().scale(4.0D);
                    Vec3 to = this.rocco.position().add(away);
                    this.rocco.getNavigation().moveTo(to.x, to.y, to.z, 1.2D);
                }
                else
                {
                    this.rocco.getNavigation().stop();
                }
            }

            boolean bow = attack == RoccoGear.Attack.BOW;
            int drawLength = bow ? 20 : 25;
            int stance = bow ? STANCE_BOW : STANCE_CROSSBOW;

            if (this.drawTicks < 0)
            {
                this.rocco.setCombatStance(STANCE_GUARD);
                if (this.cooldown <= 0 && sees && distance <= RANGED_MAX + 4.0D)
                {
                    this.drawTicks = 0;
                    this.rocco.triggerRoccoAnim(bow ? "bow_draw" : "crossbow_charge");
                    if (!bow)
                    {
                        this.rocco.playSound(SoundEvents.CROSSBOW_LOADING_START, 0.6F, 1.0F);
                    }
                }
                return;
            }

            this.drawTicks++;
            if (!bow && this.drawTicks == drawLength - 2)
            {
                this.rocco.playSound(SoundEvents.CROSSBOW_LOADING_END, 0.6F, 1.0F);
            }
            if (this.drawTicks >= drawLength)
            {
                this.rocco.setCombatStance(stance);
            }
            // Apunta un momento y suelta (si lo ve).
            if (this.drawTicks >= drawLength + 8 && sees)
            {
                this.swing = attack;
                this.swingTicks = 0;
                this.drawTicks = -1;
                this.cooldown = bow ? 12 : 18;
                this.rocco.playAttack(attack);
                shoot(target, bow);
            }
            else if (this.drawTicks > drawLength + 60)
            {
                // Lo perdio de vista mucho rato: baja el arco y vuelve a intentar.
                this.drawTicks = -1;
            }
        }

        private void shoot(LivingEntity target, boolean bow)
        {
            ItemStack weapon = this.rocco.weapon();
            ItemStack ammo = findArrows(this.rocco);
            if (ammo.isEmpty())
            {
                return;
            }
            creditOwner(target);
            AbstractArrow arrow = ProjectileUtil.getMobArrow(this.rocco, ammo.copyWithCount(1), bow ? 1.0F : 1.2F);
            if (weapon.getItem() instanceof BowItem bowItem)
            {
                arrow = bowItem.customArrow(arrow);
            }
            double dx = target.getX() - this.rocco.getX();
            double dy = target.getY(0.3333D) - arrow.getY();
            double dz = target.getZ() - this.rocco.getZ();
            double flat = Math.sqrt(dx * dx + dz * dz);
            arrow.shoot(dx, dy + flat * 0.2D, dz, bow ? 1.6F : 3.15F, 2.0F);

            boolean infinite = bow && ammo.is(Items.ARROW)
                    && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, weapon) > 0;
            if (infinite)
            {
                arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            }
            else
            {
                arrow.pickup = AbstractArrow.Pickup.ALLOWED;
                ammo.shrink(1);
                this.rocco.inventory().setChanged();
            }
            this.rocco.playSound(bow ? SoundEvents.ARROW_SHOOT : SoundEvents.CROSSBOW_SHOOT, 1.0F,
                    1.0F / (this.rocco.getRandom().nextFloat() * 0.4F + 0.8F));
            this.rocco.level().addFreshEntity(arrow);
            this.rocco.wearWeapon();
        }

        private void creditOwner(LivingEntity target)
        {
            ServerPlayer owner = this.rocco.trueOwner();
            if (owner != null && !(target instanceof Player))
            {
                target.setLastHurtByPlayer(owner);
            }
        }
    }

    static boolean hasArrows(RoccoEntity rocco)
    {
        return !findArrows(rocco).isEmpty();
    }

    // Las flechas que lleva en su inventario (las de tipos raros tambien sirven).
    static ItemStack findArrows(RoccoEntity rocco)
    {
        for (int slot = 0; slot < rocco.inventory().getContainerSize(); slot++)
        {
            ItemStack stack = rocco.inventory().getItem(slot);
            if (stack.getItem() instanceof ArrowItem)
            {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private RoccoCombat() {}
}

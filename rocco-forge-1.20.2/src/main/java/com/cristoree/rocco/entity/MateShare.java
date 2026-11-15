package com.cristoree.rocco.entity;

import com.cristoree.rocco.item.RoccoMateItem;
import com.cristoree.rocco.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.RawAnimation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

// Tomar mate con el jugador (ROCCO-MATE-COMPARTIDO-Y-CHILL.md). Rocco ceba, te ofrece el mate y
// espera con su termo mientras lo tomas; se lo devolves con clic derecho, se toma uno el y te
// ofrece otro. Si en 10 s no lo agarras, se lo toma solo y guarda todo.
//
// Todas las animaciones empiezan y terminan en la misma pose, asi que se encadenan: el servidor
// decide cual va (y cuando termina cada una) y el cliente solo la muestra.
public final class MateShare
{
    public enum Step
    {
        NONE(null, 0, false),
        START("mate_share_start", 125, false),
        OFFER("mate_offer", 40, true),
        GIVE("mate_give", 16, false),
        WAIT("mate_wait", 80, true),
        TAKE("mate_take", 20, false),
        REFILL("mate_refill", 95, false),
        SIP_SLOW("mate_sip_slow", 302, false),
        EXTEND("mate_extend", 16, false),
        RETRACT("mate_retract", 12, false),
        SIP("mate_sip", 206, false),
        PUT_AWAY("mate_put_away", 82, false);

        private static final Step[] VALUES = values();

        final int ticks;
        final boolean loops;
        @Nullable
        private final RawAnimation animation;

        Step(@Nullable String name, int ticks, boolean loops)
        {
            this.ticks = ticks;
            this.loops = loops;
            this.animation = name == null ? null : loops
                    ? RawAnimation.begin().thenLoop("animation.rocco." + name)
                    : RawAnimation.begin().thenPlayAndHold("animation.rocco." + name);
        }

        @Nullable
        public RawAnimation animation()
        {
            return this.animation;
        }

        public static Step byId(int id)
        {
            return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
        }
    }

    // Lo que espera para agarrarlo; despues se lo toma el.
    private static final int OFFER_TICKS = 200;
    private static final double MAX_DISTANCE = 12.0D;
    // Si el mate no esta en tu inventario (lo guardaste en un cofre, por ejemplo), un rato y vuelve.
    private static final int MISSING_TICKS = 40;

    private final RoccoEntity rocco;
    private final Deque<Step> next = new ArrayDeque<>();
    private Step step = Step.NONE;
    private int stepTicks;
    @Nullable
    private UUID partner;
    private int offerTicks;
    private int missingTicks;
    // Lo pediste mientras se lo tomaba solo: al terminar ese paso te ofrece uno.
    private boolean askedAgain;
    // Ya te dijo que lo devuelvas (el mate quedo vacio).
    private boolean askedBack;

    MateShare(RoccoEntity rocco)
    {
        this.rocco = rocco;
    }

    public boolean active()
    {
        return this.step != Step.NONE;
    }

    // Con la bombilla en la boca no puede hablar.
    public boolean drinking()
    {
        return this.step == Step.SIP || this.step == Step.SIP_SLOW;
    }

    // Con el mate en tu mano y el esperandolo (tambien mientras te lo pasa o te lo saca).
    public boolean isWaitingFor(Player player)
    {
        return (this.step == Step.WAIT || this.step == Step.GIVE || this.step == Step.TAKE)
                && player.getUUID().equals(this.partner);
    }

    // ---------- pedidos ----------

    // "Pasame un mate": empieza a cebar, o si ya esta en eso, te ofrece el proximo.
    public boolean request(ServerPlayer player)
    {
        if (!canShare())
        {
            return false;
        }
        if (active())
        {
            if (!player.getUUID().equals(this.partner))
            {
                return false;
            }
            switch (this.step)
            {
                // Ya te lo esta ofreciendo o lo tenes vos.
                case START, OFFER, GIVE, WAIT, TAKE, EXTEND -> { }
                // Ya lo esta guardando: arranca de nuevo al terminar.
                case PUT_AWAY -> this.askedAgain = true;
                default ->
                {
                    // Se lo estaba tomando solo: termina el paso y te lo ofrece. Si es la vuelta
                    // en que se toma uno despues del tuyo, ya te iba a ofrecer otro.
                    if (!this.next.contains(Step.OFFER))
                    {
                        this.askedAgain = true;
                    }
                }
            }
            return true;
        }

        this.rocco.interruptActivity();
        this.rocco.cancelErrand();
        this.partner = player.getUUID();
        this.askedAgain = false;
        this.askedBack = false;
        this.next.clear();
        go(Step.START);
        this.rocco.setExpression(RoccoExpression.HAPPY);
        return true;
    }

    // Clic derecho sobre Rocco: agarras el mate que te ofrece, o se lo devolves.
    public boolean interact(ServerPlayer player)
    {
        if (!active() || !player.getUUID().equals(this.partner))
        {
            return false;
        }
        if (this.step == Step.OFFER)
        {
            if (player.getInventory().getFreeSlot() < 0 && !player.getMainHandItem().isEmpty())
            {
                this.rocco.sayLine(RoccoExpression.SMUG, "mate_share_no_room");
                return true;
            }
            go(Step.GIVE);
            return true;
        }
        if (this.step == Step.WAIT && holdsMate(player))
        {
            go(Step.TAKE);
            return true;
        }
        // En el medio de otra animacion: no hace nada, pero tampoco abre la tapa.
        return true;
    }

    public void sipped(ServerPlayer player, int sips)
    {
        if (sips >= RoccoMateItem.SIPS && !this.askedBack && isWaitingFor(player))
        {
            this.askedBack = true;
            this.rocco.sayLine(RoccoExpression.HAPPY, "mate_share_empty");
        }
    }

    // Lo tiraste al piso: vuelve con el.
    public void mateDropped()
    {
        if (this.step == Step.WAIT)
        {
            stop(false);
        }
    }

    // ---------- cortar ----------

    // danger: un golpe, un bicho cerca, se va volando o al agua. Ahi corta sin animacion (la
    // siguiente ya esconde el mate y el termo). Si no, guarda sus cosas tranquilo.
    public void stop(boolean danger)
    {
        if (!active())
        {
            return;
        }
        ServerPlayer player = partner();
        boolean playerHadIt = this.step == Step.WAIT || this.step == Step.GIVE;
        if (player != null)
        {
            removeMate(player);
        }
        this.askedAgain = false;
        this.next.clear();

        if (danger)
        {
            finish();
            return;
        }
        if (this.step == Step.PUT_AWAY)
        {
            return;
        }
        if (playerHadIt)
        {
            // El mate le vuelve a la mano y lo guarda.
            this.next.add(Step.PUT_AWAY);
            go(Step.TAKE);
        }
        else
        {
            go(Step.PUT_AWAY);
        }
    }

    // ---------- el tick ----------

    void tick()
    {
        if (!active())
        {
            return;
        }
        this.stepTicks++;

        if (!canShare())
        {
            stop(true);
            return;
        }
        ServerPlayer player = partner();
        boolean leaving = this.step == Step.PUT_AWAY || (this.step == Step.TAKE && this.next.contains(Step.PUT_AWAY));
        boolean gone = player == null || player.isRemoved() || !player.isAlive() || player.level() != this.rocco.level()
                || this.rocco.distanceToSqr(player) > MAX_DISTANCE * MAX_DISTANCE;
        if (gone && !leaving)
        {
            stop(false);
        }

        // Quieto, mirandote.
        this.rocco.getNavigation().stop();
        if (player != null && player.level() == this.rocco.level())
        {
            this.rocco.faceToward(player);
        }

        int t = this.stepTicks - RoccoEntity.ANIM_LAG;
        sounds(t);
        face(t);
        events(player, t);

        if (!active())
        {
            return;
        }
        if (this.step.loops)
        {
            loopTick(player);
        }
        else if (this.stepTicks >= this.step.ticks + RoccoEntity.ANIM_LAG)
        {
            advance();
        }
    }

    private void loopTick(@Nullable ServerPlayer player)
    {
        if (this.step == Step.OFFER && ++this.offerTicks >= OFFER_TICKS)
        {
            // Nadie lo agarro: se lo toma el.
            this.rocco.sayLine(RoccoExpression.SMUG, "mate_share_alone");
            this.next.clear();
            this.next.add(Step.SIP);
            this.next.add(Step.REFILL);
            this.next.add(Step.SIP);
            this.next.add(Step.PUT_AWAY);
            go(Step.RETRACT);
            return;
        }
        if (this.step == Step.WAIT && player != null)
        {
            this.missingTicks = hasMate(player) ? 0 : this.missingTicks + 1;
            if (this.missingTicks >= MISSING_TICKS)
            {
                stop(false);
            }
        }
    }

    // Termino un paso que no es bucle: sigue el que toca.
    private void advance()
    {
        Step done = this.step;
        if (done == Step.PUT_AWAY)
        {
            UUID who = this.partner;
            boolean again = this.askedAgain;
            finish();
            if (again && who != null && this.rocco.level().getPlayerByUUID(who) instanceof ServerPlayer player)
            {
                request(player);
            }
            return;
        }

        if (this.askedAgain && (done == Step.RETRACT || done == Step.SIP || done == Step.SIP_SLOW || done == Step.REFILL))
        {
            // Recien guardado el brazo (o recien cebado) el mate esta lleno: no hace falta volver
            // a cebar. Si se lo acaba de tomar, si.
            this.askedAgain = false;
            this.next.clear();
            if (done == Step.SIP || done == Step.SIP_SLOW)
            {
                this.next.add(Step.REFILL);
            }
            this.next.add(Step.EXTEND);
            this.next.add(Step.OFFER);
            this.rocco.sayLine(RoccoExpression.HAPPY, "mate_share_offer");
        }

        Step following = this.next.poll();
        if (following == null)
        {
            following = switch (done)
            {
                case START, EXTEND -> Step.OFFER;
                case GIVE -> Step.WAIT;
                // Te lo saco: se toma uno el y te ofrece otro.
                case TAKE -> Step.REFILL;
                case REFILL -> Step.SIP_SLOW;
                case SIP_SLOW -> Step.REFILL;
                default -> Step.PUT_AWAY;
            };
            if (done == Step.TAKE)
            {
                this.next.add(Step.SIP_SLOW);
                this.next.add(Step.REFILL);
                this.next.add(Step.EXTEND);
                this.next.add(Step.OFFER);
            }
        }
        go(following);
    }

    private void go(Step step)
    {
        if (step == Step.OFFER)
        {
            this.offerTicks = 0;
        }
        if (step == Step.WAIT)
        {
            this.missingTicks = 0;
            this.askedBack = false;
        }
        this.step = step;
        this.stepTicks = 0;
        this.rocco.setMateStep(step);
    }

    private void finish()
    {
        this.step = Step.NONE;
        this.stepTicks = 0;
        this.next.clear();
        this.partner = null;
        this.rocco.setMateStep(Step.NONE);
        this.rocco.setExpression(RoccoExpression.HAPPY);
    }

    // ---------- lo que pasa en cada tick de cada animacion ----------

    private void events(@Nullable ServerPlayer player, int t)
    {
        if (this.step == Step.START && t == 100)
        {
            this.rocco.sayLine(RoccoExpression.HAPPY, "mate_share_offer");
        }
        // El mate desaparece de su mano en el tick 2: ahi te llega a vos.
        if (this.step == Step.GIVE && t == 2 && player != null)
        {
            giveMate(player);
        }
        // Le aparece en la mano en el tick 7: ahi se te va.
        if (this.step == Step.TAKE && t == 7 && player != null)
        {
            if (removeMate(player) && !this.next.contains(Step.PUT_AWAY))
            {
                this.rocco.sayLine(RoccoExpression.HAPPY, "mate_share_thanks");
            }
        }
    }

    private void sounds(int t)
    {
        switch (this.step)
        {
            case START -> { if (t == 64) sound(ModSounds.MATE_POUR.get(), 0.6F); }
            case REFILL -> { if (t == 38) sound(ModSounds.MATE_POUR.get(), 0.6F); }
            case SIP_SLOW ->
            {
                if (t == 16 || t == 114 || t == 212) sound(ModSounds.MATE_SIP.get(), 0.5F);
                else if (t == 235) sound(ModSounds.MATE_SLURP.get(), 0.6F);
                else if (t == 64 || t == 162 || t == 260) sound(ModSounds.MATE_SIGH.get(), 0.5F);
            }
            case SIP ->
            {
                if (t == 9 || t == 83 || t == 157) sound(ModSounds.MATE_SIP.get(), 0.5F);
                else if (t == 174) sound(ModSounds.MATE_SLURP.get(), 0.6F);
                else if (t == 46 || t == 120 || t == 194) sound(ModSounds.MATE_SIGH.get(), 0.5F);
            }
            case PUT_AWAY -> { if (t == 12) sound(ModSounds.MATE_SIGH.get(), 0.5F); }
            default -> { }
        }
    }

    private void face(int t)
    {
        if (t < 0)
        {
            return;
        }
        boolean bliss = switch (this.step)
        {
            case WAIT -> t % 80 < 36;
            case SIP_SLOW -> (t >= 12 && t < 88) || (t >= 110 && t < 186) || (t >= 208 && t < 284);
            case SIP -> true;
            case PUT_AWAY -> t < 40;
            default -> false;
        };
        RoccoExpression wanted = bliss ? RoccoExpression.BLISS : RoccoExpression.HAPPY;
        if (this.rocco.getExpression() != wanted)
        {
            this.rocco.setExpression(wanted);
        }
    }

    private void sound(net.minecraft.sounds.SoundEvent sound, float volume)
    {
        this.rocco.playRoccoSound(sound, volume);
    }

    // ---------- el mate del jugador ----------

    private void giveMate(ServerPlayer player)
    {
        ItemStack mate = RoccoMateItem.create(this.rocco);
        if (player.getMainHandItem().isEmpty())
        {
            player.setItemInHand(InteractionHand.MAIN_HAND, mate);
        }
        else if (!player.getInventory().add(mate))
        {
            // Se le lleno el inventario justo: que no quede tirado.
            this.next.clear();
            this.next.add(Step.TAKE);
            this.next.add(Step.PUT_AWAY);
        }
    }

    private boolean holdsMate(ServerPlayer player)
    {
        return RoccoMateItem.isMateOf(player.getMainHandItem(), this.rocco)
                || RoccoMateItem.isMateOf(player.getOffhandItem(), this.rocco);
    }

    private boolean hasMate(ServerPlayer player)
    {
        if (RoccoMateItem.isMateOf(player.containerMenu.getCarried(), this.rocco))
        {
            return true;
        }
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++)
        {
            if (RoccoMateItem.isMateOf(inventory.getItem(i), this.rocco))
            {
                return true;
            }
        }
        return false;
    }

    // Saca de tu inventario todos los mates de este Rocco. true si habia alguno.
    private boolean removeMate(ServerPlayer player)
    {
        boolean removed = false;
        if (RoccoMateItem.isMateOf(player.containerMenu.getCarried(), this.rocco))
        {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            removed = true;
        }
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++)
        {
            if (RoccoMateItem.isMateOf(inventory.getItem(i), this.rocco))
            {
                inventory.setItem(i, ItemStack.EMPTY);
                removed = true;
            }
        }
        return removed;
    }

    // ---------- condiciones ----------

    // Parado, despierto, en tierra firme y sin bichos cerca.
    private boolean canShare()
    {
        return this.rocco.getRoccoState() == RoccoState.ACTIVE
                && this.rocco.getRoccoPose() == RoccoPose.STANDING
                && !this.rocco.isTravelling()
                && !this.rocco.isInWater()
                && !this.rocco.isPassenger()
                && !this.rocco.isLidOpen()
                && !this.rocco.hostileNearby();
    }

    @Nullable
    private ServerPlayer partner()
    {
        return this.partner != null && this.rocco.level().getPlayerByUUID(this.partner) instanceof ServerPlayer player
                ? player
                : null;
    }
}

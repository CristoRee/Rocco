package com.cristoree.rocco.entity;

import com.cristoree.rocco.registry.ModItems;
import com.cristoree.rocco.registry.ModSounds;
import com.cristoree.rocco.work.Inv;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// El asado y las tortas fritas (ROCCO-V14). Rocco se planta, saca sus cosas de la cabeza y cocina
// de a una pieza lo que tiene en su inventario; lo hecho va a su inventario y se puede agarrar con
// clic derecho a la parrilla o al pote. Igual que el mate compartido, el servidor elige cada
// animacion y cuando termina, y el cliente solo la muestra.
public final class RoccoCooking
{
    public enum Kind { NONE, ASADO, TORTAS }

    // Cada carbon alcanza para 16 piezas (el doble que en el ahumador).
    private static final int PIECES_PER_COAL = 16;
    // En el ciclo del asado: agarra la cruda del estante (0,6 s) y deja la hecha al costado (4,45 s).
    private static final int ASADO_GRAB_TICK = 12;
    private static final int ASADO_DONE_TICK = 89;
    // En el ciclo de las tortas, la torta cae al pote a los 15,35 s.
    private static final int TORTAS_DONE_TICK = 307;
    // A los 2,4 s de terminar dice "a comer" (con la tapa cerrandose).
    private static final int END_LINE_TICK = 20;
    // Hasta donde puede quedar el que pidio la comida antes de que Rocco guarde todo.
    private static final double MAX_DISTANCE = 32.0D;

    private static final RoccoScene[] ASADO_A = {RoccoScene.ASADO_A1, RoccoScene.ASADO_A2, RoccoScene.ASADO_A3, RoccoScene.ASADO_A4};
    private static final RoccoScene[] ASADO_B = {RoccoScene.ASADO_B1, RoccoScene.ASADO_B2, RoccoScene.ASADO_B3, RoccoScene.ASADO_B4};

    private final RoccoEntity rocco;
    private Kind kind = Kind.NONE;
    private RoccoScene step = RoccoScene.NONE;
    private int stepTicks;
    private float yaw;
    private Vec3 spot = Vec3.ZERO;
    // Le pidieron que pare: termina la pieza que tiene en la mano y guarda todo.
    private boolean wrapUp;
    private boolean alternate;
    @Nullable
    private CookClickEntity click;
    @Nullable
    private java.util.UUID cook;

    // El asado: lo que queda del carbon prendido, la pieza en la parrilla, la que sigue y las hechas
    // del costado (las que se ven son las primeras cuatro).
    private int coalLeft;
    private int grilling;
    private int following;
    private final List<Integer> ready = new ArrayList<>();
    // Las tortas que estan en el pote.
    private int inBowl;
    private int made;

    RoccoCooking(RoccoEntity rocco)
    {
        this.rocco = rocco;
    }

    public boolean active()
    {
        return this.kind != Kind.NONE;
    }

    public Kind kind()
    {
        return this.kind;
    }

    // ---------- pedidos ----------

    public boolean startAsado(ServerPlayer player)
    {
        if (busyWith(Kind.ASADO))
        {
            return true;
        }
        if (!prepare(player))
        {
            return false;
        }
        Container inventory = this.rocco.inventory();
        boolean meat = rawCount(inventory) > 0;
        boolean coal = Inv.count(inventory, RoccoCooking::isCoal) > 0;
        if (!coal)
        {
            this.rocco.sayLine(RoccoExpression.THINK, "asado_no_coal");
            return false;
        }
        if (!meat)
        {
            this.rocco.sayLine(RoccoExpression.SAD, "asado_no_meat");
            return false;
        }
        begin(player, Kind.ASADO);
        takeCoal();
        this.ready.clear();
        this.grilling = RoccoScene.MEAT_NONE;
        this.following = nextRaw(0);
        this.click = CookClickEntity.spawn(this.rocco, modelToWorld(0.0D, 4.0D, -7.5D), false);
        go(RoccoScene.ASADO_START);
        this.rocco.sayLine(RoccoExpression.HAPPY, "asado_start");
        return true;
    }

    public boolean startTortas(ServerPlayer player)
    {
        if (busyWith(Kind.TORTAS))
        {
            return true;
        }
        if (!prepare(player))
        {
            return false;
        }
        // El trigo que tengas en la mano pasa a su inventario.
        ItemStack held = player.getMainHandItem();
        if (held.is(Items.WHEAT))
        {
            ItemStack leftover = Inv.insert(this.rocco.inventory(), held);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, leftover);
            this.rocco.inventory().setChanged();
        }
        if (Inv.count(this.rocco.inventory(), Items.WHEAT) <= 0)
        {
            this.rocco.sayLine(RoccoExpression.THINK, "tortas_no_wheat");
            return false;
        }
        begin(player, Kind.TORTAS);
        this.inBowl = 0;
        this.made = 0;
        this.click = CookClickEntity.spawn(this.rocco, modelToWorld(-9.0D, 7.0D, 4.35D), true);
        go(RoccoScene.TORTAS_START);
        this.rocco.sayLine(RoccoExpression.HAPPY, "tortas_start");
        return true;
    }

    // "Para de cocinar": termina lo que tiene en la mano y guarda todo.
    public boolean requestStop()
    {
        if (!active())
        {
            return false;
        }
        stop(false);
        return true;
    }

    private boolean busyWith(Kind wanted)
    {
        if (!active())
        {
            return false;
        }
        this.rocco.sayLine(RoccoExpression.SMUG, this.kind == wanted ? "cook_already" : "cook_other");
        return true;
    }

    // Parado en tierra firme, despierto y sin bichos cerca. Si estaba sentado se levanta y empieza
    // apenas queda de pie.
    private boolean prepare(ServerPlayer player)
    {
        if (this.rocco.getRoccoState() != RoccoState.ACTIVE || this.rocco.isTravelling() || this.rocco.isInWater()
                || this.rocco.isPassenger() || !this.rocco.onGround() || this.rocco.truco() != null)
        {
            this.rocco.sayLine(RoccoExpression.THINK, "cook_not_here");
            return false;
        }
        if (this.rocco.hostileNearby())
        {
            this.rocco.sayLine(RoccoExpression.SCARED, "cook_danger");
            return false;
        }
        return true;
    }

    private void begin(ServerPlayer player, Kind kind)
    {
        this.rocco.interruptActivity();
        this.rocco.cancelErrand();
        this.rocco.mateShare().stop(true);
        this.rocco.standUp();
        this.kind = kind;
        this.cook = player.getUUID();
        this.wrapUp = false;
        this.alternate = false;
        this.spot = this.rocco.position();
        // Mirando al que le pidio la comida: asi ve todo de frente.
        double dx = player.getX() - this.rocco.getX();
        double dz = player.getZ() - this.rocco.getZ();
        this.yaw = dx * dx + dz * dz < 0.01D ? this.rocco.getYRot()
                : (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        this.rocco.holdStill(this.yaw);
    }

    // ---------- cortar ----------

    // danger: un golpe, un bicho, otra orden o se lo llevaron de lugar. Ahi guarda todo de una
    // (la proxima animacion ya esconde la parrilla). Si no, termina tranquilo con su animacion.
    public void stop(boolean danger)
    {
        if (!active())
        {
            return;
        }
        if (danger)
        {
            finish();
            return;
        }
        this.wrapUp = true;
        if (this.step == RoccoScene.ASADO_END || this.step == RoccoScene.TORTAS_END)
        {
            return;
        }
        int t = this.stepTicks - RoccoEntity.ANIM_LAG;
        // Con una carne en la pinza la termina; si no, cierra ya.
        boolean holdingMeat = this.kind == Kind.ASADO && this.step != RoccoScene.ASADO_START && t >= ASADO_GRAB_TICK;
        if (!holdingMeat)
        {
            go(this.kind == Kind.ASADO ? RoccoScene.ASADO_END : RoccoScene.TORTAS_END);
        }
    }

    private void finish()
    {
        if (this.click != null)
        {
            this.click.discard();
            this.click = null;
        }
        this.kind = Kind.NONE;
        this.step = RoccoScene.NONE;
        this.stepTicks = 0;
        this.ready.clear();
        this.cook = null;
        this.rocco.playScene(RoccoScene.NONE);
        this.rocco.setSceneBones(0);
    }

    // ---------- el tick ----------

    void tick()
    {
        if (!active())
        {
            return;
        }
        this.stepTicks++;

        if (!canKeepCooking())
        {
            stop(true);
            return;
        }
        this.rocco.holdStill(this.yaw);
        if (this.click != null && (this.click.isRemoved() || this.click.level() != this.rocco.level()))
        {
            this.click = null;
        }

        int t = this.stepTicks - RoccoEntity.ANIM_LAG;
        if (this.kind == Kind.ASADO)
        {
            asadoEvents(t);
        }
        else
        {
            tortasEvents(t);
        }
        if (active() && this.stepTicks >= this.step.ticks + RoccoEntity.ANIM_LAG)
        {
            advance();
        }
    }

    private boolean canKeepCooking()
    {
        if (this.rocco.getRoccoState() != RoccoState.ACTIVE || this.rocco.isTravelling() || this.rocco.isInWater()
                || this.rocco.isPassenger() || this.rocco.isDeadOrDying() || this.rocco.hostileNearby())
        {
            return false;
        }
        // Lo movieron (un teleport, un piston): la parrilla quedaria flotando.
        if (this.rocco.position().distanceToSqr(this.spot) > 1.0D)
        {
            return false;
        }
        ServerPlayer player = cook();
        // El que pidio se fue lejisimo o se desconecto: guarda todo tranquilo.
        if ((player == null || player.level() != this.rocco.level() || player.distanceToSqr(this.rocco) > MAX_DISTANCE * MAX_DISTANCE)
                && !this.wrapUp)
        {
            stop(false);
        }
        return true;
    }

    private void advance()
    {
        RoccoScene done = this.step;
        if (done == RoccoScene.ASADO_END || done == RoccoScene.TORTAS_END)
        {
            finish();
            return;
        }
        if (this.kind == Kind.ASADO)
        {
            nextAsado();
        }
        else
        {
            nextTortas();
        }
    }

    private void go(RoccoScene scene)
    {
        this.step = scene;
        this.stepTicks = 0;
        this.rocco.playScene(scene);
        updateBones();
    }

    // ---------- el asado ----------

    private void nextAsado()
    {
        Container inventory = this.rocco.inventory();
        if (this.wrapUp || rawCount(inventory) <= 0)
        {
            go(RoccoScene.ASADO_END);
            return;
        }
        if (this.coalLeft <= 0 && !takeCoal())
        {
            this.rocco.sayLine(RoccoExpression.THINK, "asado_out_of_coal");
            go(RoccoScene.ASADO_END);
            return;
        }
        this.grilling = nextRaw(0);
        // En el estante esta la que va a agarrar; cuando la agarra aparece la siguiente.
        this.following = this.grilling;
        int slot = Math.min(4, this.ready.size() + 1);
        RoccoScene cycle = (this.alternate ? ASADO_B : ASADO_A)[slot - 1];
        this.alternate = !this.alternate;
        go(cycle);
    }

    private void asadoEvents(int t)
    {
        boolean cycle = this.step != RoccoScene.ASADO_START && this.step != RoccoScene.ASADO_END;
        if (cycle && t == ASADO_GRAB_TICK)
        {
            this.following = nextRaw(1);
            updateBones();
        }
        if (cycle && t == ASADO_DONE_TICK)
        {
            grilled();
        }
        if (this.step == RoccoScene.ASADO_END && t == END_LINE_TICK)
        {
            this.rocco.sayLine(RoccoExpression.CHEER, "asado_end");
        }
    }

    // La deja al costado: sale una cruda de su inventario y entra la asada.
    private void grilled()
    {
        Container inventory = this.rocco.inventory();
        int type = this.grilling;
        Item raw = rawItem(type);
        List<ItemStack> taken = raw == null ? List.of() : Inv.take(inventory, raw, 1);
        if (taken.isEmpty())
        {
            // Se la sacaste del cofre mientras la asaba: esa no sale.
            updateBones();
            return;
        }
        this.coalLeft--;
        store(new ItemStack(cookedItem(type)), modelToWorld(0.0D, 4.0D, -7.5D));
        this.ready.add(type);
        this.grilling = RoccoScene.MEAT_NONE;
        updateBones();
    }

    // La proxima cruda, salteando 'skip' piezas (0 = la primera que agarraria).
    private int nextRaw(int skip)
    {
        Container inventory = this.rocco.inventory();
        int seen = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            int type = rawType(stack);
            if (type == RoccoScene.MEAT_NONE)
            {
                continue;
            }
            if (seen + stack.getCount() > skip)
            {
                return type;
            }
            seen += stack.getCount();
        }
        return RoccoScene.MEAT_NONE;
    }

    private boolean takeCoal()
    {
        if (Inv.take(this.rocco.inventory(), RoccoCooking::isCoal, 1).isEmpty())
        {
            return false;
        }
        this.coalLeft = PIECES_PER_COAL;
        return true;
    }

    // ---------- las tortas fritas ----------

    private void nextTortas()
    {
        if (this.wrapUp || Inv.count(this.rocco.inventory(), Items.WHEAT) <= 0)
        {
            go(RoccoScene.TORTAS_END);
            return;
        }
        RoccoScene cycle = this.alternate ? RoccoScene.TORTAS_B : RoccoScene.TORTAS_A;
        this.alternate = !this.alternate;
        go(cycle);
    }

    private void tortasEvents(int t)
    {
        boolean cycle = this.step == RoccoScene.TORTAS_A || this.step == RoccoScene.TORTAS_B;
        if (cycle && t == TORTAS_DONE_TICK)
        {
            if (!Inv.take(this.rocco.inventory(), Items.WHEAT, 1).isEmpty())
            {
                store(new ItemStack(ModItems.TORTA_FRITA.get()), modelToWorld(-9.0D, 7.0D, 4.35D));
                this.inBowl++;
                this.made++;
            }
            updateBones();
        }
        if (this.step == RoccoScene.TORTAS_END && t == END_LINE_TICK)
        {
            this.rocco.sayLine(RoccoExpression.CHEER, this.made > 0 ? "tortas_end" : "cook_stop");
        }
    }

    // ---------- clic derecho a la parrilla, al pote o a Rocco ----------

    public void clicked(ServerPlayer player)
    {
        if (!active())
        {
            return;
        }
        if (this.kind == Kind.ASADO)
        {
            takeMeat(player);
        }
        else
        {
            takeTorta(player);
        }
    }

    private void takeMeat(ServerPlayer player)
    {
        if (this.ready.isEmpty())
        {
            this.rocco.sayLineOnce("asado_wait", 200);
            return;
        }
        int type = this.ready.remove(this.ready.size() - 1);
        List<ItemStack> taken = Inv.take(this.rocco.inventory(), cookedItem(type), 1);
        updateBones();
        if (taken.isEmpty())
        {
            // Ya la habias sacado de su cofre: solo se va del costado.
            return;
        }
        hand(player, taken.get(0));
        this.rocco.playRoccoSound(ModSounds.ASADO_PLATE.get(), 0.7F);
    }

    private void takeTorta(ServerPlayer player)
    {
        if (this.inBowl <= 0)
        {
            this.rocco.sayLineOnce("tortas_wait", 200);
            return;
        }
        List<ItemStack> taken = Inv.take(this.rocco.inventory(), ModItems.TORTA_FRITA.get(), 1);
        this.inBowl--;
        updateBones();
        if (taken.isEmpty())
        {
            return;
        }
        hand(player, taken.get(0));
        this.rocco.playRoccoSound(ModSounds.TORTA_BOWL.get(), 0.7F);
    }

    private static void hand(ServerPlayer player, ItemStack stack)
    {
        if (!player.getInventory().add(stack))
        {
            player.drop(stack, false);
        }
    }

    // ---------- lo que se ve ----------

    private void updateBones()
    {
        if (this.kind == Kind.ASADO)
        {
            int[] warm = new int[4];
            for (int i = 0; i < Math.min(4, this.ready.size()); i++)
            {
                warm[i] = this.ready.get(i);
            }
            this.rocco.setSceneBones(RoccoScene.asadoBits(this.grilling, this.following, warm, this.ready.size()));
        }
        else if (this.kind == Kind.TORTAS)
        {
            int wheat = Inv.count(this.rocco.inventory(), Items.WHEAT);
            int dough = wheat >= 6 ? 1 : wheat >= 3 ? 2 : wheat >= 1 ? 3 : 0;
            int bowl = Math.min(4, (this.inBowl + 1) / 2);
            this.rocco.setSceneBones(RoccoScene.tortasBits(dough, bowl));
        }
    }

    // ---------- ayudas ----------

    // Lo hecho va a su inventario; si esta lleno, al piso delante de sus cosas.
    private void store(ItemStack stack, Vec3 at)
    {
        ItemStack leftover = Inv.insert(this.rocco.inventory(), stack);
        this.rocco.inventory().setChanged();
        if (!leftover.isEmpty() && this.rocco.level() instanceof ServerLevel level)
        {
            ItemEntity item = new ItemEntity(level, at.x, at.y + 0.2D, at.z, leftover);
            item.setDefaultPickUpDelay();
            item.setThrower(this.rocco);
            level.addFreshEntity(item);
        }
    }

    // Un punto del modelo de Rocco (en px; mira hacia -z y su derecha es +x) en el mundo.
    Vec3 modelToWorld(double x, double y, double z)
    {
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.yaw);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        return this.spot.add(right.scale(x / 16.0D)).add(0.0D, y / 16.0D, 0.0D).add(forward.scale(-z / 16.0D));
    }

    @Nullable
    private ServerPlayer cook()
    {
        return this.cook != null && this.rocco.level().getPlayerByUUID(this.cook) instanceof ServerPlayer player ? player : null;
    }

    private static boolean isCoal(ItemStack stack)
    {
        return stack.is(Items.COAL) || stack.is(Items.CHARCOAL);
    }

    private static int rawCount(Container inventory)
    {
        return Inv.count(inventory, stack -> rawType(stack) != RoccoScene.MEAT_NONE);
    }

    private static int rawType(ItemStack stack)
    {
        if (stack.is(Items.BEEF))
        {
            return RoccoScene.MEAT_BEEF;
        }
        if (stack.is(Items.PORKCHOP))
        {
            return RoccoScene.MEAT_PORK;
        }
        if (stack.is(Items.MUTTON))
        {
            return RoccoScene.MEAT_LAMB;
        }
        return RoccoScene.MEAT_NONE;
    }

    @Nullable
    private static Item rawItem(int type)
    {
        return switch (type)
        {
            case RoccoScene.MEAT_BEEF -> Items.BEEF;
            case RoccoScene.MEAT_PORK -> Items.PORKCHOP;
            case RoccoScene.MEAT_LAMB -> Items.MUTTON;
            default -> null;
        };
    }

    private static Item cookedItem(int type)
    {
        return switch (type)
        {
            case RoccoScene.MEAT_PORK -> ModItems.BONDIOLA_ASADA.get();
            case RoccoScene.MEAT_LAMB -> ModItems.COSTILLITAS_DE_CORDERO.get();
            default -> ModItems.ASADO_DE_TIRA.get();
        };
    }
}

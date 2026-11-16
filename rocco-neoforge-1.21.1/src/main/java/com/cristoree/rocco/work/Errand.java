package com.cristoree.rocco.work;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoExpression;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

// Un encargo de varios pasos: craftear, cocinar, ir a un cofre... La entidad lo llama cada tick
// en que esta libre (sin trabajo animado en curso) y el encargo decide el paso siguiente: pone
// un trabajo en la cola, espera, o termina. Puede delegar en otro encargo (child) y seguir
// cuando ese termina, asi "poner una antorcha sin tener" craftea una primero.
public abstract class Errand
{
    public enum Status { RUNNING, DONE, FAILED }

    protected final RoccoEntity rocco;
    protected final ServerLevel level;
    @Nullable
    protected final UUID requester;

    @Nullable
    private Errand child;
    private boolean jobFailed;
    protected int age;

    protected Errand(RoccoEntity rocco, @Nullable Player requester)
    {
        this.rocco = rocco;
        this.level = (ServerLevel) rocco.level();
        this.requester = requester == null ? rocco.getOwnerId() : requester.getUUID();
    }

    public final Status tick()
    {
        this.age++;

        if (this.child != null)
        {
            Status status = this.child.tick();
            if (status == Status.RUNNING)
            {
                return Status.RUNNING;
            }

            Errand finished = this.child;
            this.child = null;
            if (!childDone(finished, status == Status.DONE))
            {
                return Status.FAILED;
            }
        }

        return step();
    }

    // El paso siguiente. Se llama solo cuando Rocco no esta en medio de un trabajo animado.
    protected abstract Status step();

    // Que esta haciendo, para la IA y /rocco estado: "crafteando Pico de piedra".
    public abstract String describe();

    // Devuelve false para abortar este encargo cuando el hijo fallo. Por defecto aborta: el
    // hijo ya explico que paso.
    protected boolean childDone(Errand child, boolean success)
    {
        return success;
    }

    protected void delegate(Errand child)
    {
        this.child = child;
    }

    // Mientras solo espera algo (materiales, que se cocine) puede seguir al jugador.
    public boolean allowsFollowing()
    {
        return this.child != null ? this.child.allowsFollowing() : waitingPassively();
    }

    protected boolean waitingPassively()
    {
        return false;
    }

    // Lo que el jugador dice mientras el encargo corre ("busca en el cofre").
    public boolean signal(String signal)
    {
        return this.child != null && this.child.signal(signal);
    }

    // Al cancelarlo: cerrar lo que haya quedado abierto, etc.
    public void cancelled()
    {
        if (this.child != null)
        {
            this.child.cancelled();
        }
    }

    // Para que un encargo de "cuidar huerta" sobreviva a cerrar el mundo.
    public boolean persistent()
    {
        return false;
    }

    // Si un bicho lo ataca puede dejar el encargo un momento para defenderse, y despues sigue.
    // Lo que solo espera (y cuidar la casa, que dura horas) lo permite.
    public boolean allowsFighting()
    {
        return allowsFollowing();
    }

    // ---------- ayudas para los pasos ----------

    @Nullable
    protected ServerPlayer requester()
    {
        if (this.requester == null)
        {
            return null;
        }
        Player player = this.level.getPlayerByUUID(this.requester);
        return player instanceof ServerPlayer serverPlayer && serverPlayer.isAlive() ? serverPlayer : null;
    }

    protected void say(String category, String... keyValues)
    {
        this.rocco.sayLine(category, keyValues);
    }

    protected void say(RoccoExpression face, String category, String... keyValues)
    {
        this.rocco.sayLine(face, category, keyValues);
    }

    // Trabajo animado sobre un bloque, con el efecto real en el momento justo de la animacion.
    protected void work(RoccoTask task, BlockPos target, ItemStack shown, Consumer<ServerLevel> effect)
    {
        this.rocco.queueJob(RoccoEntity.Job.custom(task, target, shown, effect, this::markJobFailed));
    }

    protected void work(RoccoEntity.Job job)
    {
        this.rocco.queueJob(job.failingWith(this::markJobFailed));
    }

    // Picar un bloque con la herramienta elegida. Lo que suelta queda en el inventario de Rocco.
    protected void mine(BlockPos pos, Tools.Choice choice, double reach, @Nullable Consumer<List<ItemStack>> after)
    {
        ItemStack shown = choice.byHand() ? ItemStack.EMPTY : choice.tool().copy();
        RoccoEntity.Job job = RoccoEntity.Job.custom(RoccoTask.MINING, pos, shown, level ->
        {
            List<ItemStack> drops = Tools.breakBlock(this.rocco, level, pos, choice);
            for (ItemStack drop : drops)
            {
                this.rocco.keep(drop);
            }
            if (after != null)
            {
                after.accept(drops);
            }
        }, this::markJobFailed).lasting(Math.max(6, choice.ticks())).reaching(reach);
        this.rocco.queueJob(job);
    }

    private void markJobFailed()
    {
        this.jobFailed = true;
    }

    // Si el ultimo trabajo no se pudo hacer (no llego), lo dice una sola vez.
    protected boolean jobFailed()
    {
        boolean failed = this.jobFailed;
        this.jobFailed = false;
        return failed;
    }
}

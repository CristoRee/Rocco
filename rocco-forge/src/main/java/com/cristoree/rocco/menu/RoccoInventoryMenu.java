package com.cristoree.rocco.menu;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.item.BackpackItem;
import com.cristoree.rocco.registry.ModMenus;
import com.cristoree.rocco.registry.ModSounds;
import com.cristoree.rocco.work.Inv;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// El inventario de Rocco. Se ve de a una pagina (hasta 6x9) y con los botones de arriba se pasa a
// la siguiente, sin soltar lo que tengas en el cursor. Los lugares que todavia no existen en una
// pagina quedan apagados. El alto de la ventana se fija al abrir: 3 filas sin mochila, 4 con el
// morralito y 6 de ahi en adelante. La misma ventana abre una mochila en la mano del jugador: ahi
// los lugares son los de la mochila y la que tenes en la mano queda trabada mientras esta abierta.
public final class RoccoInventoryMenu extends AbstractContainerMenu
{
    private final Container view;
    private final Container whole;
    private final int rows;
    private final int openedWith;
    // El lugar de tu inventario donde esta la mochila que abriste (-1: es el inventario de Rocco).
    private final int heldSlot;
    private final DataSlot page = DataSlot.standalone();
    private final DataSlot capacity = DataSlot.standalone();

    // Del lado del servidor: una ventana sobre el inventario de verdad.
    public RoccoInventoryMenu(int id, Inventory inventory, RoccoEntity rocco)
    {
        this(id, inventory, rocco.inventory(), rocco.inventory().getContainerSize(), -1);
    }

    // Del lado del cliente el contenido lo manda el servidor: solo hace falta donde ponerlo.
    public static RoccoInventoryMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buf)
    {
        int capacity = buf.readVarInt();
        return new RoccoInventoryMenu(id, inventory, null, capacity, buf.readVarInt());
    }

    // La mochila que tenes en la mano.
    public static void openHeld(ServerPlayer player, InteractionHand hand, ItemStack stack, int capacity)
    {
        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BACKPACK_OPEN.get(),
                SoundSource.PLAYERS, 0.7F, 1.0F);
        net.minecraftforge.network.NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (id, inventory, who) -> new RoccoInventoryMenu(id, inventory, new HeldBackpack(stack, hand, capacity), capacity, slot),
                stack.getHoverName()), buf ->
        {
            buf.writeVarInt(capacity);
            buf.writeVarInt(slot);
        });
    }

    private RoccoInventoryMenu(int id, Inventory inventory, Container whole, int capacity, int heldSlot)
    {
        super(ModMenus.INVENTORY.get(), id);
        this.rows = rowsFor(capacity);
        this.whole = whole;
        this.openedWith = capacity;
        this.heldSlot = heldSlot;
        this.view = whole == null ? new SimpleContainer(this.rows * 9) : new PageView(whole, this.rows * 9);
        this.capacity.set(capacity);
        addDataSlot(this.page);
        addDataSlot(this.capacity);
        this.view.startOpen(inventory.player);

        for (int row = 0; row < this.rows; row++)
        {
            for (int column = 0; column < 9; column++)
            {
                addSlot(new PageSlot(this.view, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }
        int shift = (this.rows - 4) * 18;
        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 9; column++)
            {
                addSlot(playerSlot(inventory, column + row * 9 + 9, 8 + column * 18, 103 + row * 18 + shift));
            }
        }
        for (int column = 0; column < 9; column++)
        {
            addSlot(playerSlot(inventory, column, 8 + column * 18, 161 + shift));
        }
    }

    private Slot playerSlot(Inventory inventory, int index, int x, int y)
    {
        return index == this.heldSlot ? new HeldSlot(inventory, index, x, y) : new Slot(inventory, index, x, y);
    }

    // El lugar en pantalla de la mochila abierta (null si esta en la otra mano o es Rocco).
    @org.jetbrains.annotations.Nullable
    public Slot heldSlot()
    {
        for (Slot slot : this.slots)
        {
            if (slot instanceof HeldSlot)
            {
                return slot;
            }
        }
        return null;
    }

    // Con los numeros o la F se podria mover la mochila abierta desde otro lugar: eso no.
    @Override
    public void clicked(int slotId, int button, ClickType type, Player player)
    {
        if (type == ClickType.SWAP && button == this.heldSlot)
        {
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    public static int rowsFor(int capacity)
    {
        return Math.min(BackpackItem.PAGE_SLOTS, capacity + 8) / 9;
    }

    public int rows()
    {
        return this.rows;
    }

    public int page()
    {
        return this.page.get();
    }

    public int pages()
    {
        return BackpackItem.pagesFor(this.capacity.get());
    }

    public int capacity()
    {
        return this.capacity.get();
    }

    // Ese lugar de la ventana existe en la pagina que se esta viendo.
    boolean slotExists(int index)
    {
        return page() * BackpackItem.PAGE_SLOTS + index < capacity();
    }

    // Los botones de arriba: el numero es la pagina.
    @Override
    public boolean clickMenuButton(Player player, int id)
    {
        if (id < 0 || id >= pages() || !stillValid(player))
        {
            return false;
        }
        this.page.set(id);
        if (this.view instanceof PageView pages)
        {
            pages.page = id;
        }
        return true;
    }

    // Si le cambian la mochila con la ventana abierta (otro jugador), se cierra.
    @Override
    public boolean stillValid(Player player)
    {
        return this.view.stillValid(player) && (this.whole == null || this.whole.getContainerSize() == this.openedWith);
    }

    @Override
    public void removed(Player player)
    {
        super.removed(player);
        this.view.stopOpen(player);
    }

    // Shift + clic: de Rocco a tu inventario; de tu inventario a esta pagina, y si no entra, a las otras.
    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        Slot slot = index >= 0 && index < this.slots.size() ? this.slots.get(index) : null;
        if (slot == null || !slot.hasItem() || !slot.mayPickup(player))
        {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        int mine = this.rows * 9;
        if (index < mine)
        {
            if (!moveItemStackTo(stack, mine, this.slots.size(), true))
            {
                return ItemStack.EMPTY;
            }
        }
        else
        {
            boolean moved = moveItemStackTo(stack, 0, mine, false);
            if (!stack.isEmpty() && this.whole != null && this.whole.canPlaceItem(0, stack))
            {
                ItemStack left = Inv.insert(this.whole, stack.copy());
                moved |= left.getCount() < stack.getCount();
                stack.setCount(left.getCount());
            }
            if (!moved)
            {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty())
        {
            slot.setByPlayer(ItemStack.EMPTY);
        }
        else
        {
            slot.setChanged();
        }
        return before;
    }

    // La mochila que esta abierta no se puede sacar, tirar ni reemplazar.
    private static final class HeldSlot extends Slot
    {
        HeldSlot(Container container, int index, int x, int y)
        {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player)
        {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack)
        {
            return false;
        }
    }

    // Lo que hay adentro de la mochila en tu mano. Se escribe en ella con cada cambio, asi que nunca
    // hay dos copias: si se cae o te moris con la ventana abierta, lo que lleva viaja con ella.
    private static final class HeldBackpack extends SimpleContainer
    {
        private final ItemStack stack;
        private final InteractionHand hand;
        private boolean loaded;

        HeldBackpack(ItemStack stack, InteractionHand hand, int capacity)
        {
            super(capacity);
            this.stack = stack;
            this.hand = hand;
            CompoundTag tag = stack.getTag();
            ListTag stored = tag == null ? new ListTag() : tag.getList("Items", 10);
            for (int i = 0; i < stored.size(); i++)
            {
                CompoundTag entry = stored.getCompound(i);
                int slot = entry.getShort("Slot");
                if (slot >= 0 && slot < capacity)
                {
                    super.setItem(slot, ItemStack.of(entry));
                }
            }
            this.loaded = true;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack candidate)
        {
            return !(candidate.getItem() instanceof BackpackItem);
        }

        @Override
        public void setChanged()
        {
            super.setChanged();
            // Mientras se carga no hace falta escribir nada: es lo que ya tiene.
            if (!this.loaded)
            {
                return;
            }
            ListTag stored = new ListTag();
            for (int slot = 0; slot < getContainerSize(); slot++)
            {
                ItemStack item = getItem(slot);
                if (!item.isEmpty())
                {
                    CompoundTag entry = new CompoundTag();
                    entry.putShort("Slot", (short) slot);
                    stored.add(item.save(entry));
                }
            }
            if (stored.isEmpty())
            {
                this.stack.removeTagKey("Items");
            }
            else
            {
                this.stack.getOrCreateTag().put("Items", stored);
            }
        }

        @Override
        public boolean stillValid(Player player)
        {
            return player.isAlive() && player.getItemInHand(this.hand) == this.stack;
        }

        @Override
        public void stopOpen(Player player)
        {
            setChanged();
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BACKPACK_CLOSE.get(),
                    SoundSource.PLAYERS, 0.7F, 1.0F);
        }
    }

    private final class PageSlot extends Slot
    {
        private final int index;

        PageSlot(Container container, int index, int x, int y)
        {
            super(container, index, x, y);
            this.index = index;
        }

        @Override
        public boolean isActive()
        {
            return slotExists(this.index);
        }

        @Override
        public boolean mayPlace(ItemStack stack)
        {
            return slotExists(this.index) && !(stack.getItem() instanceof BackpackItem);
        }
    }

    // Una pagina del inventario entero, del tamaño de la ventana.
    private static final class PageView implements Container
    {
        private final Container whole;
        private final int size;
        int page;

        PageView(Container whole, int size)
        {
            this.whole = whole;
            this.size = size;
        }

        private int real(int slot)
        {
            int index = this.page * BackpackItem.PAGE_SLOTS + slot;
            return index < this.whole.getContainerSize() ? index : -1;
        }

        @Override
        public int getContainerSize()
        {
            return this.size;
        }

        @Override
        public boolean isEmpty()
        {
            return this.whole.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot)
        {
            int index = real(slot);
            return index < 0 ? ItemStack.EMPTY : this.whole.getItem(index);
        }

        @Override
        public ItemStack removeItem(int slot, int amount)
        {
            int index = real(slot);
            return index < 0 ? ItemStack.EMPTY : this.whole.removeItem(index, amount);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot)
        {
            int index = real(slot);
            return index < 0 ? ItemStack.EMPTY : this.whole.removeItemNoUpdate(index);
        }

        @Override
        public void setItem(int slot, ItemStack stack)
        {
            int index = real(slot);
            if (index >= 0)
            {
                this.whole.setItem(index, stack);
            }
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack)
        {
            int index = real(slot);
            return index >= 0 && this.whole.canPlaceItem(index, stack);
        }

        @Override
        public void setChanged()
        {
            this.whole.setChanged();
        }

        @Override
        public boolean stillValid(Player player)
        {
            return this.whole.stillValid(player);
        }

        @Override
        public void startOpen(Player player)
        {
            this.whole.startOpen(player);
        }

        @Override
        public void stopOpen(Player player)
        {
            this.whole.stopOpen(player);
        }

        @Override
        public void clearContent()
        {
        }
    }
}

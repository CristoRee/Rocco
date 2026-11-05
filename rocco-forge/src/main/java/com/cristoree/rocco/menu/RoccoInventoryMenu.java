package com.cristoree.rocco.menu;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.item.BackpackItem;
import com.cristoree.rocco.registry.ModMenus;
import com.cristoree.rocco.work.Inv;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// El inventario de Rocco. Se ve de a una pagina (hasta 6x9) y con los botones de arriba se pasa a
// la siguiente, sin soltar lo que tengas en el cursor. Los lugares que todavia no existen en una
// pagina quedan apagados. El alto de la ventana se fija al abrir: 3 filas sin mochila, 4 con el
// morralito y 6 de ahi en adelante.
public final class RoccoInventoryMenu extends AbstractContainerMenu
{
    private final Container view;
    private final Container whole;
    private final int rows;
    private final int openedWith;
    private final DataSlot page = DataSlot.standalone();
    private final DataSlot capacity = DataSlot.standalone();

    // Del lado del servidor: una ventana sobre el inventario de verdad.
    public RoccoInventoryMenu(int id, Inventory inventory, RoccoEntity rocco)
    {
        this(id, inventory, rocco.inventory(), rocco.inventory().getContainerSize());
    }

    // Del lado del cliente el contenido lo manda el servidor: solo hace falta donde ponerlo.
    public static RoccoInventoryMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buf)
    {
        return new RoccoInventoryMenu(id, inventory, null, buf.readVarInt());
    }

    private RoccoInventoryMenu(int id, Inventory inventory, Container whole, int capacity)
    {
        super(ModMenus.INVENTORY.get(), id);
        this.rows = rowsFor(capacity);
        this.whole = whole;
        this.openedWith = capacity;
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
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 103 + row * 18 + shift));
            }
        }
        for (int column = 0; column < 9; column++)
        {
            addSlot(new Slot(inventory, column, 8 + column * 18, 161 + shift));
        }
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
        if (slot == null || !slot.hasItem())
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

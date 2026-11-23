// Inventarios: el de Rocco (su componente minecraft:inventory) y el del jugador.
import { ItemStack } from "@minecraft/server";
import { safe } from "./util.js";

export function inventoryOf(entity) {
  return safe("inventario", () => entity.getComponent("minecraft:inventory")?.container);
}

export function countItem(container, typeId) {
  if (!container) return 0;
  let n = 0;
  for (let i = 0; i < container.size; i++) {
    const s = container.getItem(i);
    if (s && s.typeId === typeId) n += s.amount;
  }
  return n;
}

/** Saca hasta 'amount' de ese item. Devuelve cuantos saco. */
export function takeItem(container, typeId, amount) {
  if (!container) return 0;
  let left = amount;
  for (let i = 0; i < container.size && left > 0; i++) {
    const s = container.getItem(i);
    if (!s || s.typeId !== typeId) continue;
    const used = Math.min(left, s.amount);
    left -= used;
    if (s.amount - used <= 0) container.setItem(i, undefined);
    else {
      s.amount -= used;
      container.setItem(i, s);
    }
  }
  return amount - left;
}

/** Mete el item; lo que no entra lo devuelve (o undefined si entro todo). */
export function addItem(container, stack) {
  if (!container) return stack;
  return safe("guardar item", () => container.addItem(stack)) ?? undefined;
}

/** Se lo da al jugador; si tiene el inventario lleno, queda a sus pies. */
export function giveToPlayer(player, stack) {
  const left = addItem(inventoryOf(player), stack);
  if (left) safe("soltar", () => player.dimension.spawnItem(left, player.location));
}

export function heldItem(player) {
  return safe("mano", () => player.getComponent("minecraft:equippable")?.getEquipment("Mainhand"));
}

export function setHeldItem(player, stack) {
  safe("mano", () => player.getComponent("minecraft:equippable")?.setEquipment("Mainhand", stack));
}

/** Gasta uno del item que tiene en la mano. */
export function consumeHeld(player) {
  const held = heldItem(player);
  if (!held) return;
  if (held.amount <= 1) setHeldItem(player, undefined);
  else {
    held.amount -= 1;
    setHeldItem(player, held);
  }
}

export function stackOf(id, amount = 1) {
  return new ItemStack(id, amount);
}

/** Todo lo que lleva, item por item (para el formulario del inventario). */
export function listItems(container) {
  const out = [];
  if (!container) return out;
  for (let i = 0; i < container.size; i++) {
    const s = container.getItem(i);
    if (s) out.push({ slot: i, stack: s });
  }
  return out;
}

export function isFood(stack) {
  return !!safe("comida", () => stack.getComponent("minecraft:food"));
}

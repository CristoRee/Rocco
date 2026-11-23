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

// Las comidas de Minecraft: en Bedrock los items de fabrica no muestran su componente de comida
// al script (solo los de los paquetes), asi que van por nombre.
const VANILLA_FOOD = new Set([
  "apple", "golden_apple", "enchanted_golden_apple", "bread", "cookie", "cake", "pumpkin_pie", "melon_slice",
  "glistering_melon_slice", "sweet_berries", "glow_berries", "carrot", "golden_carrot", "potato", "baked_potato",
  "poisonous_potato", "beetroot", "beetroot_soup", "mushroom_stew", "rabbit_stew", "suspicious_stew", "dried_kelp",
  "beef", "cooked_beef", "porkchop", "cooked_porkchop", "chicken", "cooked_chicken", "mutton", "cooked_mutton",
  "rabbit", "cooked_rabbit", "cod", "cooked_cod", "salmon", "cooked_salmon", "tropical_fish", "pufferfish",
  "rotten_flesh", "spider_eye", "chorus_fruit", "honey_bottle",
]);

export function isFood(stack) {
  if (!stack) return false;
  if (stack.typeId.startsWith("minecraft:") && VANILLA_FOOD.has(stack.typeId.slice(10))) return true;
  return !!safe("comida", () => stack.getComponent("minecraft:food"));
}

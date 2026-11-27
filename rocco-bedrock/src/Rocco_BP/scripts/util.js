// Ayudas chicas que usan todos los modulos.
import { world, system } from "@minecraft/server";

export const TICKS = 20;

export function normalize(message) {
  return String(message)
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/\brocco\b[,:]?/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

export function randInt(min, max) {
  return min + Math.floor(Math.random() * (max - min + 1));
}

export function chance(p) {
  return Math.random() < p;
}

export function choice(list) {
  return list[Math.floor(Math.random() * list.length)];
}

export function dist2(a, b) {
  const dx = a.x - b.x;
  const dy = a.y - b.y;
  const dz = a.z - b.z;
  return dx * dx + dy * dy + dz * dz;
}

export function now() {
  return system.currentTick;
}

export function playerById(id) {
  if (!id) return undefined;
  for (const p of world.getPlayers()) {
    if (p.id === id) return p;
  }
  return undefined;
}

export function readJson(holder, key, fallback) {
  try {
    const raw = holder.getDynamicProperty(key);
    if (typeof raw !== "string" || raw.length === 0) return fallback;
    return JSON.parse(raw);
  } catch {
    return fallback;
  }
}

export function writeJson(holder, key, value) {
  try {
    holder.setDynamicProperty(key, JSON.stringify(value));
  } catch (e) {
    console.warn("[Rocco] no pude guardar " + key + ": " + e);
  }
}

// Un texto traducible (las claves estan en texts/*.lang del paquete de recursos).
export function tr(key, ...with_) {
  return with_.length ? { translate: key, with: with_.map(String) } : { translate: key };
}

/**
 * Corre fn y, si falla, lo anota en el registro de contenido en vez de cortar todo.
 * @template T
 * @param {string} label
 * @param {() => T} fn
 * @returns {T | undefined}
 */
export function safe(label, fn) {
  try {
    return fn();
  } catch (e) {
    console.warn("[Rocco] " + label + ": " + e);
    return undefined;
  }
}

// Block.isSolid no existe en la API estable: "frena el agua" es lo mas parecido a un bloque macizo.
function blocksWater(block) {
  try {
    return block.isLiquidBlocking("Water");
  } catch {
    return !block.isAir && !block.isLiquid;
  }
}

// Las cercas, paredes, puertas y rejas no frenan el agua pero si chocan.
const THIN_WALLS = /fence|wall|door|pane|bars|chain|lantern|bell/;

/** Se puede pararse adentro: aire o algo que no choca (pasto alto, flores, nieve fina). */
export function passable(block) {
  if (!block) return false;
  if (block.isAir) return true;
  return !block.isLiquid && !block.isWaterlogged && !blocksWater(block) && !THIN_WALLS.test(block.typeId);
}

/** Se puede pisar encima. */
export function standable(block) {
  return !!block && !block.isAir && !block.isLiquid && blocksWater(block);
}

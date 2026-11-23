// Pelear, el equipo (armadura y armas que se ven en el modelo) y la tumba.
//
// El equipo se guarda en Rocco como ids de items. Lo que se ve lo eligen las propiedades
// rocco:armor_*, rocco:weapon y rocco:shield (los controladores de dibujo usan la textura de
// cada material, como RoccoGearLayer de Java).
import { system, ItemStack } from "@minecraft/server";
import { ARMOR_MATERIALS, WEAPON_KINDS, WEAPON_MATERIALS } from "./gen/anims.js";
import { rt, play, say, setFace, json, setJson, owner, ownerId, state, setState, sound, particle, cutAnim } from "./rocco.js";
import { line } from "./offline.js";
import { now, choice, safe } from "./util.js";
import { giveToPlayer } from "./items.js";
import { clearTask } from "./activities.js";

const SLOTS = ["head", "chest", "legs", "feet"];
const SLOT_SUFFIX = { head: "_helmet", chest: "_chestplate", legs: "_leggings", feet: "_boots" };
const ARMOR_POINTS = {
  leather: [1, 3, 2, 1], chainmail: [2, 5, 4, 1], iron: [2, 6, 5, 2], gold: [2, 5, 3, 1],
  diamond: [3, 8, 6, 3], netherite: [3, 8, 6, 3],
};
const WEAPON_DAMAGE = {
  weapon_sword: { wood: 4, stone: 5, iron: 6, gold: 4, diamond: 7, netherite: 8 },
  weapon_axe: { wood: 7, stone: 9, iron: 9, gold: 7, diamond: 9, netherite: 10 },
};
const BASE_DAMAGE = 4;

function materialOf(id) {
  const name = id.replace("minecraft:", "");
  if (name.startsWith("chainmail")) return "chainmail";
  if (name.startsWith("golden")) return "gold";
  if (name.startsWith("wooden")) return "wood";
  for (const m of ["leather", "iron", "diamond", "netherite", "stone", "turtle"]) if (name.startsWith(m)) return m === "turtle" ? "iron" : m;
  return null;
}

/** Para que hueco sirve ese item (o null si no es equipo de Rocco). */
export function gearSlot(id) {
  if (!id) return null;
  for (const slot of SLOTS) if (id.endsWith(SLOT_SUFFIX[slot]) && materialOf(id)) return slot;
  if (id === "minecraft:turtle_helmet") return "head";
  if (id === "minecraft:shield") return "off";
  if (/_(sword|axe)$/.test(id) && materialOf(id)) return "main";
  if (id === "minecraft:trident" || id === "minecraft:bow" || id === "minecraft:crossbow" || id === "rocco:botella_rota") return "main";
  return null;
}

export function gear(rocco) {
  return json(rocco, "gear", {});
}

function weaponLook(id) {
  if (!id) return null;
  if (id === "minecraft:trident") return { kind: "weapon_trident", mat: "special" };
  if (id === "minecraft:bow") return { kind: "weapon_bow", mat: "special" };
  if (id === "minecraft:crossbow") return { kind: "weapon_crossbow", mat: "special" };
  if (id.endsWith("_sword")) return { kind: "weapon_sword", mat: materialOf(id) };
  if (id.endsWith("_axe")) return { kind: "weapon_axe", mat: materialOf(id) };
  return null;
}

/** Pone en el modelo lo que tiene puesto. */
export function showGear(rocco) {
  const g = gear(rocco);
  safe("equipo", () => {
    for (const slot of SLOTS) {
      const mat = g[slot] ? materialOf(g[slot]) : null;
      const index = mat ? ARMOR_MATERIALS.indexOf(mat) + 1 : 0;
      rocco.setProperty("rocco:armor_" + slot, Math.max(0, index));
    }
    const look = weaponLook(g.main);
    rocco.setProperty("rocco:weapon", look ? WEAPON_KINDS.indexOf(look.kind) + 1 : 0);
    rocco.setProperty("rocco:weapon_mat", look ? Math.max(0, WEAPON_MATERIALS.indexOf(look.mat)) : 0);
    rocco.setProperty("rocco:shield", g.off === "minecraft:shield");
  });
}

/** Le da a Rocco lo que el jugador tiene en la mano. Devuelve true si lo tomo. */
export function equipFrom(rocco, player, stack) {
  const slot = gearSlot(stack?.typeId);
  if (!slot) return false;
  const g = gear(rocco);
  if (g[slot]) giveToPlayer(player, new ItemStack(g[slot], 1));
  g[slot] = stack.typeId;
  setJson(rocco, "gear", g);
  showGear(rocco);
  play(rocco, "equip_proud");
  setFace(rocco, "smug", 60);
  sound(rocco, "rocco.backpack_equip", 0.8);
  return true;
}

/** Le devuelve todo el equipo al jugador. */
export function unequipAll(rocco, player) {
  const g = gear(rocco);
  for (const id of Object.values(g)) if (id) giveToPlayer(player, new ItemStack(id, 1));
  setJson(rocco, "gear", {});
  showGear(rocco);
}

function armorPoints(rocco) {
  const g = gear(rocco);
  let points = 0;
  SLOTS.forEach((slot, i) => {
    const mat = g[slot] ? materialOf(g[slot]) : null;
    if (mat && ARMOR_POINTS[mat]) points += ARMOR_POINTS[mat][i];
  });
  return points;
}

function weaponDamage(rocco) {
  const g = gear(rocco);
  const look = weaponLook(g.main);
  if (g.main === "rocco:botella_rota") return 3;
  if (!look) return BASE_DAMAGE;
  const table = WEAPON_DAMAGE[look.kind];
  if (table && table[look.mat]) return table[look.mat];
  if (look.kind === "weapon_trident") return 9;
  return BASE_DAMAGE;
}

function attackAnim(rocco) {
  const look = weaponLook(gear(rocco).main);
  if (!look) return "attack_punch";
  if (look.kind === "weapon_axe") return "attack_chop";
  if (look.kind === "weapon_trident") return "attack_thrust";
  return "attack_swing";
}

let extraHit = false;

/** Rocco le pego a algo: su animacion y el daño extra del arma. */
export function onRoccoHit(rocco, target) {
  const r = rt(rocco);
  if (!r.task && !r.scene) play(rocco, attackAnim(rocco));
  setFace(rocco, "angry", 40);
  if (!r.inFight) {
    r.inFight = true;
    if (!r.lastFightLine || now() - r.lastFightLine > 400) {
      r.lastFightLine = now();
      say(rocco, line("combat_start"), "angry");
    }
  }
  const extra = weaponDamage(rocco) - BASE_DAMAGE;
  if (extra > 0 && !extraHit) {
    extraHit = true;
    safe("daño extra", () => target.applyDamage(extra, { cause: "entityAttack", damagingEntity: rocco }));
    extraHit = false;
  }
}

/** A Rocco le pegaron: la armadura le devuelve parte, y los golpes del dueño no cuentan. */
export function onRoccoHurt(rocco, amount, source) {
  const health = rocco.getComponent("minecraft:health");
  if (!health) return;
  const attacker = source?.damagingEntity;
  let refund = 0;
  if (attacker && attacker.id === ownerId(rocco)) refund = amount;
  else refund = amount * Math.min(0.8, armorPoints(rocco) * 0.04);
  if (refund > 0) safe("vida", () => health.setCurrentValue(Math.min(health.effectiveMax, health.currentValue + refund)));
  if (!attacker || attacker.id !== ownerId(rocco)) {
    const r = rt(rocco);
    if (!r.task && !r.scene && r.pose === "stand") play(rocco, amount >= 4 ? "hurt" : "hurt_light");
    setFace(rocco, amount >= 4 ? "hurt" : "hurt_light", 30);
    sound(rocco, "rocco.hurt", 0.8);
  }
}

export function onFightEnded(rocco) {
  const r = rt(rocco);
  if (r.inFight) {
    r.inFight = false;
    say(rocco, line("combat_win"), "smug");
  }
}

// ---------------------------------------------------------------- la tumba

const GRAVE_POSES = ["feet", "hand", "head"];

export function onDied(rocco) {
  if (state(rocco) === "grave") return;
  const r = rt(rocco);
  clearTask(rocco);
  r.scene = null;
  r.pose = "stand";
  setState(rocco, "grave");
  const pose = choice(GRAVE_POSES);
  r.gravePose = pose;
  safe("pose", () => rocco.setDynamicProperty("rocco:grave_pose", pose));
  setFace(rocco, "dizzy", 0);
  const ticks = play(rocco, "death");
  sound(rocco, "rocco.grave_burst", 0.8);
  system.runTimeout(() => {
    if (!rocco.isValid || state(rocco) !== "grave") return;
    play(rocco, "grave_" + pose);
    rt(rocco).animUntil = Number.MAX_SAFE_INTEGER;
    setFace(rocco, "sleep", 0);
    const o = owner(rocco);
    if (o) o.sendMessage({ translate: "rocco.grave.notice" });
  }, ticks);
}

/** El dueño lo desentierra. */
export function rise(rocco) {
  if (state(rocco) !== "grave") return;
  const pose = rocco.getDynamicProperty("rocco:grave_pose") || "feet";
  setState(rocco, "rising");
  cutAnim(rocco);
  const ticks = play(rocco, "grave_rise_" + pose);
  sound(rocco, "rocco.grave_burst", 0.8);
  particle(rocco, "rocco:dirt_clod", { x: 0, y: 0.2, z: 0 });
  system.runTimeout(() => {
    if (!rocco.isValid) return;
    setState(rocco, "active");
    safe("vida", () => {
      const h = rocco.getComponent("minecraft:health");
      if (h) h.resetToMaxValue();
    });
    rocco.triggerEvent("rocco:wake");
    cutAnim(rocco);
    setFace(rocco, "happy", 0);
    say(rocco, line("grave_back"), "cheer");
  }, ticks);
}

export function restoreGrave(rocco) {
  if (state(rocco) === "grave") {
    const pose = rocco.getDynamicProperty("rocco:grave_pose") || "feet";
    play(rocco, "grave_" + pose);
    rt(rocco).animUntil = Number.MAX_SAFE_INTEGER;
    setFace(rocco, "sleep", 0);
  }
}

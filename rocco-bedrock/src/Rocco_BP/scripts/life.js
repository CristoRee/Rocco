// La vida de cada Rocco: brotar al lado tuyo la primera vez, despertarse, lo que hace cuando
// no le pediste nada, la charla espontanea y el regalo cuando volves despues de un tiempo.
import { world, system, ItemStack } from "@minecraft/server";
import {
  ROCCO, rt, allRoccos, state, setState, owner, ownerId, play, hold, say, setFace, tickLooks, sound,
  particle, data, setData, animBusy, calmFace, roccoOf,
} from "./rocco.js";
import { line } from "./offline.js";
import { now, chance, randInt, choice, dist2, safe } from "./util.js";
import { busy, poseAnim, drinkMate, drinkCola, tickShare, wave, sleep, standUp } from "./activities.js";
import { tickCooking } from "./cooking.js";
import { showGear, restoreGrave, onFightEnded } from "./combat.js";
import { giveToPlayer } from "./items.js";
import { tickTruco } from "./truco/session.js";

const MINUTE = 1200;
const AWAKEN_TICKS = 116;

// ---------------------------------------------------------------- brotar

const OFFSETS = [[2, 0], [0, 2], [-2, 0], [0, -2], [3, 2], [-3, 2], [3, -2], [-3, -2]];

function spotNear(player) {
  const dim = player.dimension;
  const base = player.location;
  for (const [dx, dz] of OFFSETS) {
    const x = Math.floor(base.x) + dx;
    const z = Math.floor(base.z) + dz;
    for (let dy = 2; dy >= -3; dy--) {
      const y = Math.floor(base.y) + dy;
      const ground = safe("bloque", () => dim.getBlock({ x, y: y - 1, z }));
      const feet = safe("bloque", () => dim.getBlock({ x, y, z }));
      const head = safe("bloque", () => dim.getBlock({ x, y: y + 1, z }));
      if (ground && feet && head && ground.isSolid && feet.isAir && head.isAir) return { x: x + 0.5, y, z: z + 0.5 };
    }
  }
  return { x: base.x + 1.5, y: base.y, z: base.z };
}

export function spawnFor(player) {
  const at = spotNear(player);
  const rocco = safe("brotar", () => player.dimension.spawnEntity(ROCCO, at));
  if (!rocco) return undefined;
  setData(rocco, "owner", player.id);
  setData(rocco, "owner_name", player.name);
  setState(rocco, "plant");
  setData(rocco, "follow", true);
  setFace(rocco, "sleep", 0);
  safe("mirar", () => rocco.setRotation({ x: 0, y: (Math.atan2(player.location.x - at.x, at.z - player.location.z) * 180) / Math.PI }));
  console.warn("[Rocco] brotado para " + player.name);
  return rocco;
}

/** La primera vez que entras al mundo, Rocco brota dormido al lado tuyo. */
export function onFirstJoin(player) {
  const key = "rocco:spawned:" + player.id;
  if (world.getDynamicProperty(key)) return;
  if (roccoOf(player)) {
    world.setDynamicProperty(key, true);
    return;
  }
  if (spawnFor(player)) world.setDynamicProperty(key, true);
}

// ---------------------------------------------------------------- despertarse

export function beginAwakening(rocco, player) {
  if (state(rocco) !== "plant") return;
  if (!ownerId(rocco)) {
    setData(rocco, "owner", player.id);
    setData(rocco, "owner_name", player.name);
  }
  setState(rocco, "awakening");
  const r = rt(rocco);
  r.awakenStart = now();
  play(rocco, "awaken");
  r.animUntil = now() + AWAKEN_TICKS;
}

const AWAKEN_SOUNDS = {
  5: ["rocco.dirt", 0.35], 10: ["rocco.dirt", 0.35], 14: ["rocco.dirt", 0.35], 48: ["rocco.dirt", 0.35],
  55: ["rocco.dirt", 0.35], 59: ["rocco.dirt", 0.35], 35: ["rocco.leaves", 0.4], 40: ["rocco.leaves", 0.4],
  63: ["rocco.awaken_pop", 0.8], 68: ["rocco.leaf_grow", 0.5], 75: ["rocco.step", 0.4], 78: ["rocco.shake_off", 0.6],
  94: ["rocco.dust_pat", 0.5], 102: ["rocco.cheer", 0.6], 109: ["rocco.step", 0.15],
};
const AWAKEN_FACES = { 0: "sleep", 20: "bored", 30: "yawn", 40: "think", 47: "serious", 63: "cheer", 102: "cheer", 75: "happy", 92: "happy", 78: "windy" };

function tickAwakening(rocco) {
  const r = rt(rocco);
  const t = now() - (r.awakenStart ?? now());
  if (AWAKEN_SOUNDS[t]) sound(rocco, AWAKEN_SOUNDS[t][0], AWAKEN_SOUNDS[t][1]);
  if (AWAKEN_FACES[t]) setFace(rocco, AWAKEN_FACES[t], 0);
  if (t === 63) particle(rocco, "rocco:dirt_clod", { x: 0, y: 0.3, z: 0 });
  if (t < AWAKEN_TICKS) return;
  setState(rocco, "active");
  setFace(rocco, "happy", 0);
  const o = owner(rocco);
  safe("domar", () => {
    if (o) rocco.getComponent("minecraft:tameable")?.tame(o);
  });
  rocco.triggerEvent("rocco:wake");
  r.animUntil = 0;
  say(rocco, line("awaken"), "smug");
  if (o) {
    system.runTimeout(() => {
      if (o.isValid) o.sendMessage({ translate: "rocco.welcome.hint" });
    }, 60);
  }
}

// ---------------------------------------------------------------- el rato libre

function idleChoice(rocco) {
  const r = rt(rocco);
  const t = now();
  if (t - r.lastIdle < MINUTE * 2) return;
  r.lastIdle = t + randInt(0, MINUTE);
  if (busy(rocco) || animBusy(rocco)) return;
  // Como en Java: el mate es lo de siempre; el 3 % de las veces, una Roco Cola (con 10 minutos
  // como minimo entre una y otra).
  if (chance(0.03) && t - r.lastCola > MINUTE * 10) {
    drinkCola(rocco);
    return;
  }
  const roll = Math.random();
  if (roll < 0.5) drinkMate(rocco);
  else if (roll < 0.65) wave(rocco);
}

// ---------------------------------------------------------------- charla espontanea

function spontaneous(rocco) {
  const r = rt(rocco);
  const t = now();
  if (r.nextSpont === undefined) {
    r.nextSpont = t + randInt(2 * MINUTE, 4 * MINUTE);
    return;
  }
  if (t < r.nextSpont) return;
  const quietUntil = data(rocco, "quiet_until", 0);
  if (Date.now() < quietUntil) {
    r.nextSpont = t + MINUTE;
    return;
  }
  const chatty = Date.now() < data(rocco, "chatty_until", 0);
  r.nextSpont = t + Math.round(randInt(2 * MINUTE, 4 * MINUTE) / (chatty ? 2 : 1));
  if (busy(rocco) || t - r.lastSaid < MINUTE) return;
  const o = owner(rocco);
  if (!o || o.dimension.id !== rocco.dimension.id || dist2(o.location, rocco.location) > 24 * 24) return;
  const health = o.getComponent("minecraft:health");
  const time = world.getTimeOfDay();
  let category;
  if (health && health.currentValue < 6) category = "spont_low_health";
  else if (time >= 13000 && time < 23000 && chance(0.4)) category = "spont_night";
  else if (o.location.y < 40 && o.dimension.id === "minecraft:overworld" && chance(0.4)) category = "spont_cave";
  else category = choice(["spont_musing", "spont_fact", "spont_question", "spont_idle_player"]);
  say(rocco, line(category), choice(["happy", "smug", "bored"]));
}

// ---------------------------------------------------------------- dormir con vos

function followOwnerSleep(rocco) {
  const r = rt(rocco);
  const o = owner(rocco);
  const ownerSleeping = !!o && o.isSleeping && o.dimension.id === rocco.dimension.id && dist2(o.location, rocco.location) < 16 * 16;
  if (ownerSleeping && r.pose === "stand" && !busy(rocco)) {
    sleep(rocco);
    r.sleptWithOwner = true;
  } else if (!ownerSleeping && r.pose === "sleep" && r.sleptWithOwner) {
    r.sleptWithOwner = false;
    standUp(rocco);
  }
}

// ---------------------------------------------------------------- cada tick

let restored = false;

export function tickAll() {
  const t = now();
  for (const rocco of allRoccos()) {
    safe("tick de Rocco", () => tickOne(rocco, t));
  }
}

function tickOne(rocco, t) {
  const r = rt(rocco);
  if (!r.restored) {
    r.restored = true;
    showGear(rocco);
    const s = state(rocco);
    safe("planta", () => rocco.setProperty("rocco:plant", s === "plant" || s === "awakening"));
    safe("mochila", () => rocco.setProperty("rocco:pack", data(rocco, "pack", 0)));
    if (s === "awakening") setState(rocco, "plant");
    if (s === "rising") setState(rocco, "active");
    restoreGrave(rocco);
    setFace(rocco, state(rocco) === "plant" ? "sleep" : calmFace(rocco), 0);
  }
  tickLooks(rocco);
  const s = state(rocco);
  if (s === "plant") {
    hold(rocco, "plant_idle");
    return;
  }
  if (s === "awakening") {
    tickAwakening(rocco);
    return;
  }
  if (s === "grave" || s === "rising") return;

  tickCooking(rocco);
  tickShare(rocco);
  tickTruco(rocco);
  if (r.task && r.taskUntil <= t) {
    r.task = null;
    r.taskData = null;
  }
  if (r.inFight && t - (r.lastHitAt ?? 0) > 120) onFightEnded(rocco);

  if (t % 20 === 0) {
    followOwnerSleep(rocco);
    idleChoice(rocco);
    spontaneous(rocco);
  }
  // Sin otra cosa que mostrar: sentado, dormido, pensando o caminar/quieto (lo elige el cliente).
  // Las tareas (cebar mate, bailar...) y las escenas manejan sus propias animaciones.
  if (!animBusy(rocco) && !r.scene && !r.task) hold(rocco, poseAnim(rocco));
}

// ---------------------------------------------------------------- el regalo al volver

const PRESENTS = [
  ["minecraft:coal", 2, 12, 0], ["minecraft:iron_ingot", 1, 6, 0], ["minecraft:copper_ingot", 2, 10, 0],
  ["minecraft:lapis_lazuli", 2, 8, 0], ["minecraft:redstone", 2, 12, 0], ["minecraft:amethyst_shard", 1, 4, 0],
  ["minecraft:gold_ingot", 1, 4, 3], ["minecraft:emerald", 1, 4, 4], ["minecraft:diamond", 1, 2, 8],
  ["minecraft:bread", 2, 6, 0], ["minecraft:cooked_beef", 2, 6, 0], ["minecraft:baked_potato", 2, 8, 0],
  ["minecraft:apple", 1, 5, 0], ["minecraft:golden_carrot", 1, 4, 4], ["minecraft:golden_apple", 1, 1, 8],
  ["minecraft:bone", 2, 8, 0], ["minecraft:string", 2, 8, 0], ["minecraft:arrow", 6, 20, 0],
  ["minecraft:ender_pearl", 1, 2, 6], ["minecraft:experience_bottle", 1, 6, 3], ["minecraft:name_tag", 1, 1, 10],
];

function presentItems(hours) {
  const count = Math.min(6, 1 + Math.floor(Math.min(hours, 48) / 8));
  const pool = PRESENTS.filter(([, , , minHours]) => hours >= minHours);
  const out = [];
  const used = new Set();
  for (let i = 0; i < count && used.size < pool.length; i++) {
    let p;
    do p = choice(pool);
    while (used.has(p[0]));
    used.add(p[0]);
    const scale = Math.min(1, hours / 24);
    out.push(new ItemStack(p[0], Math.max(p[1], Math.round(p[1] + (p[2] - p[1]) * scale * Math.random()))));
  }
  return out;
}

export function rememberLeaving(player) {
  safe("hora", () => world.setDynamicProperty("rocco:left:" + player.id, Date.now()));
}

/** Al entrar: si estuviste afuera mas de una hora, Rocco te tiene algo. */
export function onReturn(player) {
  const left = world.getDynamicProperty("rocco:left:" + player.id);
  rememberLeaving(player);
  if (typeof left !== "number") return;
  const hours = (Date.now() - left) / 3600000;
  if (hours < 1) return;
  system.runTimeout(() => {
    const rocco = roccoOf(player);
    if (!rocco || state(rocco) !== "active" || !player.isValid) return;
    const items = presentItems(Math.min(hours, 24 * 7));
    const category = hours >= 24 * 7 ? "present_week" : hours >= 24 ? "present_day" : "present_hours";
    say(rocco, line(category, { hours: Math.round(hours) }), "cheer");
    play(rocco, "give_seed");
    for (const stack of items) giveToPlayer(player, stack);
    sound(rocco, "rocco.gift_seed", 0.8);
  }, 100);
}

/** Cada minuto: la hora de "se fue" queda al dia aunque el juego se cierre de golpe. */
export function tickPresence() {
  for (const p of world.getPlayers()) rememberLeaving(p);
}

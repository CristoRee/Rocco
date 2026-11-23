// El asado y las tortas fritas (como RoccoCooking de Java): Rocco se planta con su parrilla o su
// olla, que viven adentro del modelo, y va pidiendo cada paso de la escena. Lo que hace lo saca
// de su inventario o, si no tiene, del tuyo.
import { ItemStack } from "@minecraft/server";
import { rt, play, say, setFace, owner, sound, data } from "./rocco.js";
import { line } from "./offline.js";
import { now, dist2, safe } from "./util.js";
import { inventoryOf, countItem, takeItem, giveToPlayer } from "./items.js";

const ASADO_GRAB_TICK = 12;
const ASADO_DONE_TICK = 89;
const TORTAS_DONE_TICK = 307;
const END_LINE_TICK = 20;
const PIECES_PER_COAL = 16;
const MAX_DISTANCE = 32;

const RAW = [
  { item: "minecraft:beef", meat: 1, cooked: "rocco:asado_de_tira" },
  { item: "minecraft:porkchop", meat: 2, cooked: "rocco:bondiola_asada" },
  { item: "minecraft:mutton", meat: 3, cooked: "rocco:costillitas_de_cordero" },
];
const COALS = ["minecraft:coal", "minecraft:charcoal"];

export function cooking(rocco) {
  return rt(rocco).scene && rt(rocco).scene.kind !== "truco" ? rt(rocco).scene : null;
}

function sources(rocco) {
  const list = [inventoryOf(rocco)];
  const o = owner(rocco);
  if (o) list.push(inventoryOf(o));
  return list.filter(Boolean);
}

function count(rocco, ids) {
  let n = 0;
  for (const inv of sources(rocco)) for (const id of ids) n += countItem(inv, id);
  return n;
}

function take(rocco, ids) {
  for (const inv of sources(rocco)) {
    for (const id of ids) {
      if (takeItem(inv, id, 1) > 0) return id;
    }
  }
  return null;
}

function nextRaw(rocco, skip) {
  let seen = 0;
  for (const raw of RAW) {
    const n = count(rocco, [raw.item]);
    if (n > skip - seen) return raw.meat;
    seen += n;
  }
  return 0;
}

function bits(scene) {
  if (scene.kind === "asado") {
    let b = (scene.grilling & 3) | ((scene.following & 3) << 2);
    for (let i = 0; i < Math.min(4, scene.ready.length); i++) b |= (scene.ready[i] & 3) << (4 + i * 2);
    return b;
  }
  return ((scene.dough & 3) << 12) | ((Math.min(4, scene.bowl) & 7) << 14);
}

function updateBits(rocco, scene) {
  safe("huesos", () => rocco.setProperty("rocco:bits", bits(scene)));
}

function go(rocco, scene, anim) {
  scene.step = anim;
  scene.stepStart = now();
  scene.stepTicks = play(rocco, anim);
  updateBits(rocco, scene);
}

export function startAsado(rocco, player) {
  const r = rt(rocco);
  if (r.scene) {
    say(rocco, line("cook_already"), "smug");
    return;
  }
  if (count(rocco, COALS) <= 0) {
    say(rocco, line("asado_no_coal"), "think");
    return;
  }
  if (count(rocco, RAW.map((x) => x.item)) <= 0) {
    say(rocco, line("asado_no_meat"), "sad");
    return;
  }
  const scene = { kind: "asado", cook: player ? player.id : null, grilling: 0, following: 0, ready: [], coalLeft: 0, alternate: false, wrapUp: false, made: 0 };
  r.scene = scene;
  rocco.triggerEvent("rocco:busy");
  go(rocco, scene, "asado_start");
  say(rocco, line("asado_start"), "happy");
}

export function startTortas(rocco, player) {
  const r = rt(rocco);
  if (r.scene) {
    say(rocco, line("cook_already"), "smug");
    return;
  }
  if (count(rocco, ["minecraft:wheat"]) <= 0) {
    say(rocco, line("tortas_no_wheat"), "think");
    return;
  }
  const scene = { kind: "tortas", cook: player ? player.id : null, dough: 0, bowl: 0, alternate: false, wrapUp: false, made: 0 };
  r.scene = scene;
  rocco.triggerEvent("rocco:busy");
  setData(rocco, "mate_out", true);
  go(rocco, scene, "tortas_start");
  say(rocco, line("tortas_start"), "happy");
}

function setData(rocco, key, value) {
  safe("dato", () => rocco.setDynamicProperty("rocco:" + key, value));
}

/** Termina: cuando acaba el paso que esta haciendo pasa al cierre. */
export function stopCooking(rocco) {
  const scene = cooking(rocco);
  if (!scene) return false;
  scene.wrapUp = true;
  return true;
}

function finish(rocco, scene) {
  const r = rt(rocco);
  r.scene = null;
  r.animUntil = 0;
  safe("huesos", () => rocco.setProperty("rocco:bits", 0));
  safe("libre", () => rocco.triggerEvent("rocco:free"));
  // Lo que cocino te lo da (o queda a sus pies si no estas).
  const o = owner(rocco);
  for (const [id, amount] of Object.entries(scene.cookedItems || {})) {
    const stack = new ItemStack(id, amount);
    if (o) giveToPlayer(o, stack);
    else safe("soltar", () => rocco.dimension.spawnItem(stack, rocco.location));
  }
}

function addCooked(scene, id) {
  scene.cookedItems = scene.cookedItems || {};
  scene.cookedItems[id] = (scene.cookedItems[id] || 0) + 1;
}

function nextAsado(rocco, scene) {
  if (scene.wrapUp || count(rocco, RAW.map((x) => x.item)) <= 0) {
    scene.grilling = 0;
    scene.following = 0;
    go(rocco, scene, "asado_end");
    return;
  }
  if (scene.coalLeft <= 0) {
    if (!take(rocco, COALS)) {
      say(rocco, line("asado_out_of_coal"), "think");
      go(rocco, scene, "asado_end");
      return;
    }
    scene.coalLeft = PIECES_PER_COAL;
  }
  scene.grilling = nextRaw(rocco, 0);
  scene.following = scene.grilling;
  const slot = Math.min(4, scene.ready.length + 1);
  const anim = (scene.alternate ? "asado_cycle_b" : "asado_cycle_a") + slot;
  scene.alternate = !scene.alternate;
  go(rocco, scene, anim);
}

function grilled(rocco, scene) {
  const raw = RAW.find((x) => x.meat === scene.grilling);
  if (!raw || !take(rocco, [raw.item])) return;
  scene.coalLeft--;
  scene.ready.push(raw.meat);
  if (scene.ready.length > 4) scene.ready.shift();
  addCooked(scene, raw.cooked);
  scene.made++;
  sound(rocco, "rocco.asado_plate", 0.7);
  updateBits(rocco, scene);
}

function nextTortas(rocco, scene) {
  if (scene.wrapUp || count(rocco, ["minecraft:wheat"]) <= 0) {
    scene.dough = 0;
    go(rocco, scene, "tortas_end");
    return;
  }
  scene.dough = 1;
  go(rocco, scene, scene.alternate ? "tortas_cycle_b" : "tortas_cycle_a");
  scene.alternate = !scene.alternate;
}

export function tickCooking(rocco) {
  const scene = cooking(rocco);
  if (!scene) return;
  const t = now() - scene.stepStart;
  // El que pidio se fue lejisimo: guarda todo tranquilo.
  const o = owner(rocco);
  if (!scene.wrapUp && (!o || o.dimension.id !== rocco.dimension.id || dist2(o.location, rocco.location) > MAX_DISTANCE * MAX_DISTANCE)) {
    scene.wrapUp = true;
  }
  const cycle = scene.step !== scene.kind + "_start" && scene.step !== scene.kind + "_end";
  if (scene.kind === "asado") {
    if (cycle && t === ASADO_GRAB_TICK) {
      scene.following = nextRaw(rocco, 1);
      updateBits(rocco, scene);
    }
    if (cycle && t === ASADO_DONE_TICK) grilled(rocco, scene);
    if (scene.step === "asado_end" && t === END_LINE_TICK) {
      say(rocco, line(scene.made > 0 ? "asado_end" : "cook_stop"), "cheer");
    }
  } else {
    if (cycle) {
      const dough = t < 100 ? 1 : t < 200 ? 2 : 3;
      if (dough !== scene.dough) {
        scene.dough = dough;
        updateBits(rocco, scene);
      }
    }
    if (cycle && t === TORTAS_DONE_TICK) {
      if (take(rocco, ["minecraft:wheat"])) {
        addCooked(scene, "rocco:torta_frita");
        scene.made++;
        scene.bowl = Math.min(4, scene.bowl + 1);
      }
      scene.dough = 0;
      updateBits(rocco, scene);
    }
    if (scene.step === "tortas_end" && t === END_LINE_TICK) {
      say(rocco, line(scene.made > 0 ? "tortas_end" : "cook_stop"), "cheer");
      setFace(rocco, "cheer", 60);
    }
  }
  if (t >= scene.stepTicks) {
    if (scene.step === "asado_end" || scene.step === "tortas_end") {
      finish(rocco, scene);
      setData(rocco, "mate_out", false);
      return;
    }
    if (scene.kind === "asado") nextAsado(rocco, scene);
    else nextTortas(rocco, scene);
  }
}

export function hasMateOut(rocco) {
  return data(rocco, "mate_out", false);
}

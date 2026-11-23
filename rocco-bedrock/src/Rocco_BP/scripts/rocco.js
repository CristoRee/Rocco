// El estado de cada Rocco y lo que se ve de el: la animacion principal, la cara y la boca.
//
// Lo que tiene que sobrevivir a cerrar el mundo va en propiedades dinamicas de la entidad
// (dueño, si te sigue, lugares, equipo, amistad). Lo del momento (que animacion esta haciendo,
// hasta cuando habla) vive en memoria: al volver a abrir el mundo arranca de cero, como en Java.
import { world, system } from "@minecraft/server";
import { ANIMS, FACES } from "./gen/anims.js";
import { now, playerById, readJson, writeJson, dist2, safe } from "./util.js";

export const ROCCO = "rocco:rocco";
const DIMENSIONS = ["minecraft:overworld", "minecraft:nether", "minecraft:the_end"];

const runtime = new Map();

/** Lo que Rocco esta haciendo ahora (no se guarda). */
export function rt(rocco) {
  let r = runtime.get(rocco.id);
  if (!r) {
    r = {
      animName: null,
      animUntil: 0,
      baseAnim: null,
      faceUntil: 0,
      talkUntil: 0,
      thinkUntil: 0,
      task: null,
      taskUntil: 0,
      taskStep: 0,
      taskData: null,
      pose: "stand",
      lastSaid: 0,
      lastIdle: now(),
      lastCola: -1e9,
      lastLine: {},
      scene: null,
    };
    runtime.set(rocco.id, r);
  }
  return r;
}

export function forget(id) {
  runtime.delete(id);
}

export function isRocco(entity) {
  return !!entity && entity.typeId === ROCCO && entity.isValid;
}

export function allRoccos() {
  const out = [];
  for (const id of DIMENSIONS) {
    safe("buscar Roccos", () => {
      for (const e of world.getDimension(id).getEntities({ type: ROCCO })) out.push(e);
    });
  }
  return out;
}

// ---------------------------------------------------------------- datos guardados

export function data(rocco, key, fallback) {
  const v = rocco.getDynamicProperty("rocco:" + key);
  return v === undefined ? fallback : v;
}

export function setData(rocco, key, value) {
  safe("guardar " + key, () => rocco.setDynamicProperty("rocco:" + key, value));
}

export function json(rocco, key, fallback) {
  return readJson(rocco, "rocco:" + key, fallback);
}

export function setJson(rocco, key, value) {
  writeJson(rocco, "rocco:" + key, value);
}

export function state(rocco) {
  return data(rocco, "state", "plant");
}

export function setState(rocco, value) {
  setData(rocco, "state", value);
  safe("planta", () => rocco.setProperty("rocco:plant", value === "plant" || value === "awakening"));
}

export function ownerId(rocco) {
  return data(rocco, "owner", undefined);
}

export function owner(rocco) {
  return playerById(ownerId(rocco));
}

export function ownerName(rocco) {
  return data(rocco, "owner_name", "");
}

export function roccoOf(player) {
  for (const r of allRoccos()) {
    if (ownerId(r) === player.id) return r;
  }
  return undefined;
}

export function displayName(rocco) {
  return rocco.nameTag && rocco.nameTag.length ? rocco.nameTag : "Rocco";
}

// ---------------------------------------------------------------- animaciones

/** Pide una animacion de la tabla. Devuelve cuantos ticks dura. */
export function play(rocco, name) {
  const anim = ANIMS[name];
  if (!anim) {
    console.warn("[Rocco] no existe la animacion " + name);
    return 0;
  }
  const seq = ((rocco.getProperty("rocco:seq") ?? 0) + 1) % 1000;
  safe("animar", () => {
    rocco.setProperty("rocco:anim", anim.id);
    rocco.setProperty("rocco:seq", seq);
  });
  const ticks = Math.max(1, Math.round(anim.length * 20));
  const r = rt(rocco);
  r.animName = name;
  r.animUntil = now() + ticks;
  // Cuando termine, el proximo hold() tiene que volver a pedir lo suyo (aunque sea "nada").
  r.baseAnim = undefined;
  return ticks;
}

/** Un bucle que se sostiene (sentado, dormido, pensando...): solo se pide si cambio. */
export function hold(rocco, name) {
  const r = rt(rocco);
  if (r.baseAnim === name && r.animUntil <= now()) return;
  if (r.animUntil > now()) return;
  r.baseAnim = name;
  r.animName = name;
  if (name === null) {
    safe("animar", () => {
      if (rocco.getProperty("rocco:anim") !== 0) {
        rocco.setProperty("rocco:anim", 0);
        rocco.setProperty("rocco:seq", ((rocco.getProperty("rocco:seq") ?? 0) + 1) % 1000);
      }
    });
    return;
  }
  const anim = ANIMS[name];
  if (!anim) return;
  safe("animar", () => {
    if (rocco.getProperty("rocco:anim") !== anim.id) {
      rocco.setProperty("rocco:anim", anim.id);
      rocco.setProperty("rocco:seq", ((rocco.getProperty("rocco:seq") ?? 0) + 1) % 1000);
    }
  });
}

/** Corta lo que este haciendo (la proxima vuelta elige de nuevo). */
export function cutAnim(rocco) {
  const r = rt(rocco);
  r.animUntil = 0;
  r.animName = null;
  r.baseAnim = undefined;
}

export function animBusy(rocco) {
  return rt(rocco).animUntil > now();
}

// ---------------------------------------------------------------- cara y boca

export function faceIndex(face) {
  const i = FACES.indexOf(face);
  return i < 0 ? 0 : i;
}

/** La cara de fondo (la que queda) o una por un rato (ticks > 0). */
export function setFace(rocco, face, ticks = 0) {
  safe("cara", () => rocco.setProperty("rocco:face", faceIndex(face)));
  const r = rt(rocco);
  if (ticks > 0) r.faceUntil = now() + ticks;
  else setData(rocco, "face", face);
}

export function calmFace(rocco) {
  return data(rocco, "face", "happy");
}

export function talkFor(rocco, ticks) {
  const r = rt(rocco);
  r.talkUntil = Math.max(r.talkUntil, now() + ticks);
  safe("boca", () => rocco.setProperty("rocco:talk", true));
}

/** Rocco dice algo: le llega al dueño y a los que estan cerca, como en el chat de Java. */
export function say(rocco, text, face) {
  if (!text) return;
  const r = rt(rocco);
  r.lastSaid = now();
  const line = "<" + displayName(rocco) + "> " + text;
  const near = new Set();
  for (const p of world.getPlayers()) {
    if (p.dimension.id === rocco.dimension.id && dist2(p.location, rocco.location) < 48 * 48) near.add(p);
  }
  const o = owner(rocco);
  if (o) near.add(o);
  for (const p of near) safe("mensaje", () => p.sendMessage(line));
  talkFor(rocco, Math.min(160, Math.max(20, Math.round(text.length * 1.2))));
  if (face) setFace(rocco, face, Math.min(200, Math.max(60, text.length * 2)));
}

/** Un mensaje de estado (no es Rocco hablando): solo al dueño. */
export function notify(rocco, message) {
  const o = owner(rocco);
  if (o) safe("aviso", () => o.sendMessage(message));
}

export function sound(rocco, id, volume = 1, pitch = 1) {
  safe("sonido", () => rocco.dimension.playSound(id, rocco.location, { volume, pitch }));
}

export function particle(rocco, id, offset = { x: 0, y: 1, z: 0 }) {
  const l = rocco.location;
  safe("particula", () => rocco.dimension.spawnParticle(id, { x: l.x + offset.x, y: l.y + offset.y, z: l.z + offset.z }));
}

/** Cada tick: la boca deja de moverse y la cara vuelve sola. */
export function tickLooks(rocco) {
  const r = rt(rocco);
  const t = now();
  if (r.talkUntil && r.talkUntil <= t) {
    r.talkUntil = 0;
    safe("boca", () => rocco.setProperty("rocco:talk", false));
  }
  if (r.faceUntil && r.faceUntil <= t) {
    r.faceUntil = 0;
    safe("cara", () => rocco.setProperty("rocco:face", faceIndex(calmFace(rocco))));
  }
}

export function inSameDimension(a, b) {
  return a.dimension.id === b.dimension.id;
}

export function writeMemory(rocco, text) {
  const memory = json(rocco, "memory", []);
  memory.push(text);
  while (memory.length > 30) memory.shift();
  setJson(rocco, "memory", memory);
}

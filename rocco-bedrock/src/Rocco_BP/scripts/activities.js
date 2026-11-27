// Lo que Rocco hace por su cuenta o cuando se lo pedis: mate (solo o cebandote), Roco Cola,
// bailar, aplaudir, festejar, saludar, sentarse y dormir. Los tiempos y los sonidos son los de
// RoccoEntity y MateShare de Java.
import { system, ItemStack } from "@minecraft/server";
import { rt, play, say, setFace, sound, owner, particle, cutAnim, setData, data } from "./rocco.js";
import { line } from "./offline.js";
import { now, chance, dist2, safe } from "./util.js";
import { inventoryOf, takeItem, giveToPlayer, countItem } from "./items.js";

/** Sonidos en ticks de la animacion: si la animacion cambio en el medio, no suenan. */
function timed(rocco, anim, cues) {
  const r = rt(rocco);
  const startedAt = now();
  for (const [tick, id, volume] of cues) {
    system.runTimeout(() => {
      if (!rocco.isValid || r.animName !== anim || r.animUntil < startedAt + tick) return;
      sound(rocco, id, volume);
    }, tick);
  }
}

export function busy(rocco) {
  const r = rt(rocco);
  return !!r.task || !!r.scene || r.pose !== "stand";
}

export function setTask(rocco, task, ticks) {
  const r = rt(rocco);
  r.task = task;
  r.taskUntil = now() + ticks;
}

export function clearTask(rocco) {
  const r = rt(rocco);
  r.task = null;
  r.taskUntil = 0;
  r.taskData = null;
}

// ---------------------------------------------------------------- mate solo

export function drinkMate(rocco) {
  if (busy(rocco)) return false;
  const ticks = play(rocco, "mate");
  setTask(rocco, "mate", ticks);
  safe("mate", () => rocco.setProperty("rocco:mate", true));
  timed(rocco, "mate", [
    [35, "rocco.leaves", 0.6], [64, "rocco.mate_pour", 0.9], [353, "rocco.mate_pour", 0.9],
    [130, "rocco.mate_sip", 0.85], [204, "rocco.mate_sip", 0.85], [278, "rocco.mate_sip", 0.85],
    [419, "rocco.mate_sip", 0.85], [493, "rocco.mate_sip", 0.85],
    [295, "rocco.mate_slurp", 0.9], [510, "rocco.mate_slurp", 0.9],
    [161, "rocco.mate_sigh", 0.8], [235, "rocco.mate_sigh", 0.8], [310, "rocco.mate_sigh", 0.8],
    [450, "rocco.mate_sigh", 0.8], [524, "rocco.mate_sigh", 0.8],
  ]);
  system.runTimeout(() => {
    if (!rocco.isValid) return;
    safe("mate", () => rocco.setProperty("rocco:mate", false));
    if (rt(rocco).task === "mate") clearTask(rocco);
  }, ticks);
  return true;
}

// ---------------------------------------------------------------- mate compartido

/** Rocco aparece a un bloque y medio adelante del jugador (con su puff), si esta a mas de 4. */
export function comeNear(rocco, player) {
  if (!player || !player.isValid) return;
  const same = player.dimension.id === rocco.dimension.id;
  if (same && dist2(player.location, rocco.location) <= 4 * 4) return;
  const v = player.getViewDirection();
  const len = Math.hypot(v.x, v.z) || 1;
  const at = { x: player.location.x + (v.x / len) * 1.5, y: player.location.y, z: player.location.z + (v.z / len) * 1.5 };
  particle(rocco, "rocco:magic_burst", { x: 0, y: 0.6, z: 0 });
  safe("acercarse", () => rocco.teleport(at, { dimension: player.dimension, facingLocation: player.location }));
  particle(rocco, "rocco:magic_burst", { x: 0, y: 0.6, z: 0 });
}

const SHARE_ROUNDS = 4;
const WAIT_TICKS = 20 * 30;

export function shareMate(rocco, player) {
  const r = rt(rocco);
  if (r.task === "share") {
    say(rocco, line("mate_share_ack"), "happy");
    return;
  }
  if (busy(rocco)) {
    say(rocco, line("mate_share_cant"), "bored");
    return;
  }
  // Si estaba lejos (se habia quedado en otro lado), viene al lado tuyo antes de cebar.
  comeNear(rocco, player);
  r.task = "share";
  r.taskUntil = Number.MAX_SAFE_INTEGER;
  r.taskData = { player: player.id, round: 0, step: "start", stepUntil: now() + play(rocco, "mate_share_start") };
  safe("mate", () => rocco.setProperty("rocco:mate", true));
  rocco.triggerEvent("rocco:busy");
  say(rocco, line("mate_share_ack"), "happy");
  sound(rocco, "rocco.leaves", 0.6);
}

function shareStep(rocco, step, anim) {
  const d = rt(rocco).taskData;
  d.step = step;
  d.stepUntil = now() + play(rocco, anim);
}

function endShare(rocco, category) {
  const d = rt(rocco).taskData;
  const player = d ? owner(rocco) : undefined;
  if (player) takeItem(inventoryOf(player), "rocco:mate", 64);
  if (category) say(rocco, line(category), "happy");
  play(rocco, "mate_put_away");
  system.runTimeout(() => {
    if (!rocco.isValid) return;
    safe("mate", () => rocco.setProperty("rocco:mate", false));
    safe("libre", () => rocco.triggerEvent("rocco:free"));
  }, 82);
  clearTask(rocco);
}

/** El jugador termino de tomar el mate que le paso Rocco. */
export function playerDrankMate(player, rocco) {
  const r = rt(rocco);
  if (r.task !== "share" || !r.taskData || r.taskData.player !== player.id) return false;
  takeItem(inventoryOf(player), "rocco:mate", 64);
  sound(rocco, "rocco.mate_slurp", 0.6);
  r.taskData.drank = true;
  return true;
}

export function tickShare(rocco) {
  const r = rt(rocco);
  if (r.task !== "share" || !r.taskData) return;
  const d = r.taskData;
  const player = owner(rocco);
  if (!player || player.dimension.id !== rocco.dimension.id || dist2(player.location, rocco.location) > 10 * 10) {
    endShare(rocco, "mate_share_alone");
    return;
  }
  const t = now();
  if (d.step === "wait") {
    if (d.drank) {
      d.drank = false;
      d.round++;
      shareStep(rocco, "take", "mate_take");
      say(rocco, line("mate_share_thanks"), "happy");
      return;
    }
    if (t > d.waitUntil) {
      endShare(rocco, "mate_share_alone");
    }
    return;
  }
  if (t < d.stepUntil) return;
  switch (d.step) {
    case "start":
    case "take":
      if (d.round >= SHARE_ROUNDS) {
        endShare(rocco, "mate_share_empty");
        return;
      }
      // Antes de cebar, a veces se toma uno el (como en Java).
      if (d.round > 0 && chance(0.35)) {
        shareStep(rocco, "sip", "mate_sip");
        timed(rocco, "mate_sip", [[40, "rocco.mate_sip", 0.85], [120, "rocco.mate_slurp", 0.9], [160, "rocco.mate_sigh", 0.8]]);
        return;
      }
      shareStep(rocco, "refill", "mate_refill");
      timed(rocco, "mate_refill", [[20, "rocco.mate_pour", 0.9]]);
      return;
    case "sip":
      shareStep(rocco, "refill", "mate_refill");
      timed(rocco, "mate_refill", [[20, "rocco.mate_pour", 0.9]]);
      return;
    case "refill":
      shareStep(rocco, "give", "mate_give");
      return;
    case "give": {
      const left = safe("dar mate", () => inventoryOf(player)?.addItem(new ItemStack("rocco:mate", 1)));
      if (left) {
        endShare(rocco, "mate_share_no_room");
        return;
      }
      say(rocco, line("mate_share_offer"), "happy");
      d.step = "wait";
      d.waitUntil = t + WAIT_TICKS;
      play(rocco, "mate_wait");
      return;
    }
  }
}

// ---------------------------------------------------------------- Roco Cola

const COLA_COOLDOWN = 20 * 60 * 10;

export function colaReady(rocco) {
  return now() - rt(rocco).lastColaGift > COLA_COOLDOWN;
}

/** Rocco se toma una (de su cuenta: no gasta las tuyas). */
export function drinkCola(rocco) {
  if (busy(rocco)) return false;
  const ticks = play(rocco, "cola_drink");
  setTask(rocco, "cola", ticks);
  rt(rocco).lastCola = now();
  timed(rocco, "cola_drink", [[10, "rocco.cola_open", 0.8], [30, "rocco.cola_drink_1", 0.8], [70, "rocco.cola_ahh_1", 0.8]]);
  system.runTimeout(() => {
    if (rocco.isValid && rt(rocco).task === "cola") clearTask(rocco);
  }, ticks);
  return true;
}

/** Te convida una Roco Cola (cada tanto, no es una fabrica). */
export function giveCola(rocco, player) {
  if (!colaReady(rocco)) {
    say(rocco, line("cola_cooldown"), "smug");
    return;
  }
  if (busy(rocco)) {
    say(rocco, line("cola_give_wait"), "bored");
    return;
  }
  const ticks = play(rocco, "give_cola");
  setTask(rocco, "give", ticks);
  rt(rocco).lastColaGift = now();
  say(rocco, line("cola_give"), "cheer");
  system.runTimeout(() => {
    if (!rocco.isValid) return;
    giveToPlayer(player, new ItemStack("rocco:roco_cola", 1));
    sound(rocco, "rocco.cola_clink", 0.8);
    if (rt(rocco).task === "give") clearTask(rocco);
  }, Math.max(1, Math.round(ticks * 0.6)));
}

// ---------------------------------------------------------------- gestos

function oneShot(rocco, anim, task, face) {
  if (busy(rocco)) return false;
  const ticks = play(rocco, anim);
  setTask(rocco, task, ticks);
  if (face) setFace(rocco, face, ticks);
  system.runTimeout(() => {
    if (rocco.isValid && rt(rocco).task === task) clearTask(rocco);
  }, ticks);
  return true;
}

export function dance(rocco) {
  if (busy(rocco)) return false;
  const anim = chance(0.5) ? "dance_fiesta" : "dance_meneito";
  play(rocco, anim);
  const ticks = 20 * 12;
  rt(rocco).animUntil = now() + ticks;
  setTask(rocco, "dance", ticks);
  setFace(rocco, "hum", ticks);
  sound(rocco, "rocco.hum", 0.7);
  system.runTimeout(() => {
    if (rocco.isValid && rt(rocco).task === "dance") {
      clearTask(rocco);
      cutAnim(rocco);
    }
  }, ticks);
  return true;
}

export function clap(rocco) {
  return oneShot(rocco, "clap", "clap", "cheer");
}

export function celebrate(rocco) {
  return oneShot(rocco, "celebrate", "celebrate", "cheer");
}

export function wave(rocco) {
  return oneShot(rocco, "wave", "wave", "happy");
}

export function eatFood(rocco, bad) {
  return oneShot(rocco, bad ? "food_eat_bad" : "food_eat", "eat", bad ? "sad" : "bliss");
}

// ---------------------------------------------------------------- sentarse y dormir

export function sit(rocco) {
  const r = rt(rocco);
  if (r.pose === "sit") return;
  if (r.task || r.scene) return;
  r.pose = "sit";
  play(rocco, "sit_down");
  rocco.triggerEvent("rocco:busy");
}

export function sleep(rocco) {
  const r = rt(rocco);
  if (r.pose === "sleep") return;
  if (r.task || r.scene) return;
  r.pose = "sleep";
  play(rocco, "sleep_start");
  setFace(rocco, "sleep", 0);
  rocco.triggerEvent("rocco:busy");
}

export function standUp(rocco) {
  const r = rt(rocco);
  if (r.pose === "stand") return;
  const wasSleeping = r.pose === "sleep";
  r.pose = "stand";
  play(rocco, wasSleeping ? "wake_up" : "stand_up");
  if (wasSleeping) setFace(rocco, "happy", 0);
  rocco.triggerEvent("rocco:free");
}

/** Lo que se sostiene mientras no hay otra animacion: sentado, dormido o pensando. */
export function poseAnim(rocco) {
  const r = rt(rocco);
  if (r.pose === "sit") return "sit";
  if (r.pose === "sleep") return "sleep";
  if (r.thinkUntil > now()) return "think";
  return null;
}

/** Rocco deja lo que estaba haciendo (sin tocar las escenas largas, que cierran solas). */
export function stopAll(rocco) {
  const r = rt(rocco);
  if (r.task === "share") endShare(rocco, null);
  clearTask(rocco);
  cutAnim(rocco);
  standUp(rocco);
}

export { countItem, particle, setData, data };

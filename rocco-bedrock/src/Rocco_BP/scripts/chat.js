// Hablar con Rocco: lo que le escribis (desde el menu o con /rocco:decir) y lo que hace con eso.
//
// Con el paquete "Rocco IA" (solo en un servidor dedicado, ver LEEME) la respuesta la arma la IA;
// si no esta, o tarda mas de lo que corresponde, contesta con sus frases propias, como en Java.
import { system, world } from "@minecraft/server";
import { rt, say, setFace, owner, play, data, setData, writeMemory, state, displayName, roccoOf } from "./rocco.js";
import { reply, line } from "./offline.js";
import { now, normalize, safe, dist2 } from "./util.js";
import {
  stopAll, drinkMate, shareMate, giveCola, dance, clap, celebrate, wave, sit, sleep, standUp, busy,
} from "./activities.js";
import { startAsado, startTortas, stopCooking } from "./cooking.js";
import { inviteTruco } from "./truco/session.js";
import { P } from "./gen/offline.js";

// ---------------------------------------------------------------- el puente con la IA

const AI_TIMEOUT = 20 * 8;
let aiSeenUntil = 0;
const pending = new Map();
let nextId = 1;

export function aiAvailable() {
  return now() < aiSeenUntil;
}

let aiError = "";

export function aiStatus() {
  return aiAvailable() ? aiError : null;
}

export function onScriptEvent(id, message) {
  if (id === "rocco:ia_hola") {
    aiSeenUntil = now() + 20 * 90;
    aiError = message.startsWith("error:") ? message.slice(6) : "";
    return;
  }
  // Le escribieron en el chat (lo manda el paquete de la IA, que es el unico que lo puede leer).
  if (id === "rocco:ia_chat") {
    let msg;
    try {
      msg = JSON.parse(message);
    } catch {
      return;
    }
    heardInChat(msg.jugador, String(msg.texto || ""));
    return;
  }
  if (id !== "rocco:ia_respuesta") return;
  let answer;
  try {
    answer = JSON.parse(message);
  } catch {
    return;
  }
  const waiting = pending.get(answer.id);
  if (!waiting) return;
  pending.delete(answer.id);
  waiting(answer);
}

function askAi(rocco, playerName, text) {
  return new Promise((resolve) => {
    const id = nextId++;
    const timer = system.runTimeout(() => {
      if (pending.delete(id)) resolve(null);
    }, AI_TIMEOUT);
    pending.set(id, (answer) => {
      system.clearRun(timer);
      resolve(answer);
    });
    const memory = (() => {
      try {
        return JSON.parse(String(rocco.getDynamicProperty("rocco:memory") ?? "[]")).slice(-6);
      } catch {
        return [];
      }
    })();
    const payload = JSON.stringify({
      id,
      jugador: playerName,
      texto: text.slice(0, 400),
      modo: data(rocco, "mode", "chill"),
      nombre: displayName(rocco),
      memoria: memory,
      hora: rocco.dimension.id,
    }).slice(0, 1900);
    safe("pedido a la IA", () => system.sendScriptEvent("rocco:ia_pedido", payload));
  });
}

// ---------------------------------------------------------------- lo que le dijeron

function heardInChat(playerId, text) {
  const player = world.getPlayers().find((p) => p.id === playerId);
  if (!player) return;
  const rocco = roccoOf(player);
  if (!rocco || state(rocco) !== "active") return;
  const near = rocco.dimension.id === player.dimension.id && dist2(rocco.location, player.location) < 16 * 16;
  if (!near && !/^\s*@?rocco\b/i.test(text)) return;
  hear(rocco, player, text, false);
}

export async function hear(rocco, player, message, echo = true) {
  const text = String(message || "").trim();
  // Se pudo haber ido entre que escribio y ahora (el comando corre un tick despues).
  if (!text || !player.isValid || !rocco.isValid) return;
  // El nombre se guarda ahora: si el jugador se va mientras Rocco piensa, ya no se puede leer.
  const name = player.name;
  if (echo) safe("eco", () => player.sendMessage("§7<" + player.name + "> " + text));
  if (state(rocco) !== "active") {
    if (state(rocco) === "plant") player.sendMessage({ translate: "rocco.chat.asleep" });
    return;
  }
  const normalized = normalize(text);
  // Callarlo o pedirle que hable mas.
  if (P.SHUSH.test(normalized)) {
    setData(rocco, "quiet_until", Date.now() + 20 * 60 * 1000);
    setData(rocco, "chatty_until", 0);
  } else if (P.CHATTY.test(normalized)) {
    setData(rocco, "chatty_until", Date.now() + 30 * 60 * 1000);
    setData(rocco, "quiet_until", 0);
  }
  const offline = reply(rocco, text);
  // Las ordenes se hacen siempre igual (rapido y sin gastar IA), como el atajo de Java.
  if (offline.verb) {
    if (offline.text) say(rocco, offline.text, offline.face);
    doVerb(rocco, player, offline.verb, offline.arg);
    return;
  }
  if (aiAvailable()) {
    const r = rt(rocco);
    r.thinkUntil = now() + AI_TIMEOUT;
    setFace(rocco, "think", AI_TIMEOUT);
    const answer = await askAi(rocco, name, text);
    r.thinkUntil = 0;
    if (!rocco.isValid) return;
    if (answer && answer.texto) {
      say(rocco, answer.texto, answer.cara || "happy");
      if (answer.accion && player.isValid) doVerb(rocco, player, answer.accion, answer.arg || "");
      writeMemory(rocco, name + ": " + text.slice(0, 80) + " | Rocco: " + String(answer.texto).slice(0, 80));
      return;
    }
  }
  say(rocco, offline.text, offline.face);
}

// ---------------------------------------------------------------- las ordenes

export function doVerb(rocco, player, verb, arg) {
  switch (verb) {
    case "seguir":
      if (rt(rocco).pose !== "stand") standUp(rocco);
      setData(rocco, "follow", true);
      safe("seguir", () => rocco.triggerEvent("rocco:follow"));
      return;
    case "esperar":
      setData(rocco, "follow", false);
      safe("quedarse", () => rocco.triggerEvent("rocco:stay"));
      return;
    case "parar":
      stopCooking(rocco);
      stopAll(rocco);
      return;
    case "parar_cocina":
      if (!stopCooking(rocco)) say(rocco, line("cook_not_cooking"), "bored");
      return;
    case "teletransportarse":
      teleportToOwner(rocco);
      return;
    case "dar_cola":
      giveCola(rocco, player);
      return;
    case "mate":
      if (!drinkMate(rocco)) say(rocco, line("travel_busy"), "bored");
      return;
    case "cebar_mate":
      shareMate(rocco, player);
      return;
    case "bailar":
      dance(rocco);
      return;
    case "aplaudir":
      clap(rocco);
      return;
    case "festejar":
      if (!celebrate(rocco)) say(rocco, line("celebrate_cant"), "bored");
      return;
    case "saludar":
      wave(rocco);
      return;
    case "asado":
      startAsado(rocco, player);
      return;
    case "tortas_fritas":
      startTortas(rocco, player);
      return;
    case "jugar_truco":
      inviteTruco(rocco, player);
      return;
    case "sentarse":
      sit(rocco);
      return;
    case "dormir":
      sleep(rocco);
      return;
    case "levantarse":
      standUp(rocco);
      return;
    default:
      return;
  }
}

export function teleportToOwner(rocco) {
  const o = owner(rocco);
  if (!o) return;
  if (busy(rocco)) stopAll(rocco);
  safe("puff", () => rocco.dimension.spawnParticle("rocco:magic_burst", { x: rocco.location.x, y: rocco.location.y + 0.6, z: rocco.location.z }));
  const dir = o.getViewDirection();
  const at = { x: o.location.x - dir.x * 1.5, y: o.location.y, z: o.location.z - dir.z * 1.5 };
  safe("teletransporte", () => rocco.teleport(at, { dimension: o.dimension }));
  system.runTimeout(() => {
    if (!rocco.isValid) return;
    safe("puff", () => rocco.dimension.spawnParticle("rocco:magic_burst", { x: rocco.location.x, y: rocco.location.y + 0.6, z: rocco.location.z }));
    say(rocco, line("teleport_arrived"), "smug");
    play(rocco, "wave");
  }, 2);
}

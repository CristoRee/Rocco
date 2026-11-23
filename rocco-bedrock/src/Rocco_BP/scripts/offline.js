// Rocco sin IA: lo mismo que OfflineRocco de Java (frases y patrones salen de ahi, ver
// tools/java_port.py). Primero las ordenes que se pueden hacer en Bedrock, despues la charla.
import { world } from "@minecraft/server";
import { LINES, P } from "./gen/offline.js";
import { normalize } from "./util.js";

const lastLine = new Map();

const BEDROCK_HELP =
  "Te sigo, te defiendo, tomo mate y te cebo, hago asado y tortas fritas, juego al truco, bailo y te guardo cosas. " +
  "Tocame para abrir mi menu. Para charlar, desde el menu (Hablar) o con /rocco:decir.";

/** Una frase de la categoria, sin repetir la ultima. {clave} se reemplaza con values. */
export function line(category, values = {}) {
  const lines = LINES[category];
  if (!lines || lines.length === 0) return "";
  let index = Math.floor(Math.random() * lines.length);
  if (lines.length > 1 && index === lastLine.get(category)) index = (index + 1) % lines.length;
  lastLine.set(category, index);
  let text = lines[index];
  for (const [k, v] of Object.entries(values)) text = text.split("{" + k + "}").join(String(v));
  return text;
}

export function hasLines(category) {
  return !!LINES[category];
}

function say(category, face) {
  return { text: line(category), face, verb: null };
}

function act(category, face, verb, arg = "") {
  return { text: category ? line(category) : "", face, verb, arg };
}

const DANCE = /\b(baila\w*|bailemos|un bailecito|un baile)\b/;
const CLAP = /\b(aplaud\w*|unos aplausos)\b/;
const CELEBRATE = /\b(festeja\w*|celebra\w*)\b/;
const MATE = /\b(cebate|tomate|toma(te)? (un|unos) mates?|un mate|unos mates|mate)\b/;
const SHARE_MATE = /\b(cebame|cebanos|pasame (un|el) mate|convidame (un|el)? ?mate|ceba(me)? (un|unos) mates?)\b/;
const WAVE = /\b(saluda\w*|hace(me)? (chau|hola) con la mano)\b/;

/** Las ordenes. null si no hay ninguna. */
function order(rocco, text) {
  if (P.NOT_FOLLOW.test(text)) return act("wait", "bored", "esperar");
  if (P.STOP_COOK && P.STOP_COOK.test(text)) return act("", "happy", "parar_cocina");
  if (P.STOP.test(text)) return act("stop", "bored", "parar");
  if (P.CALL && P.CALL.test(text)) return act("teleport_ack", "smug", "teletransportarse");
  if (P.COLA_GIVE.test(text)) return act("cola_give", "cheer", "dar_cola");
  if (SHARE_MATE.test(text)) return act("mate_share_ack", "happy", "cebar_mate");
  if (MATE.test(text)) return act("mate_ack", "happy", "mate");
  if (DANCE.test(text)) return act("dance_ack", "happy", "bailar");
  if (CLAP.test(text)) return act("clap_ack", "cheer", "aplaudir");
  if (CELEBRATE.test(text)) return act("celebrate_ack", "cheer", "festejar");
  if (WAVE.test(text)) return act("", "happy", "saludar");
  if (P.TORTAS.test(text)) return act("", "happy", "tortas_fritas");
  if (P.ASADO.test(text)) return act("", "happy", "asado");
  if (P.TRUCO.test(text)) return act("", "cheer", "jugar_truco");
  if (P.FOLLOW.test(text)) return act("follow", "happy", "seguir");
  if (P.WAKE.test(text)) return act("wake", "happy", "levantarse");
  if (P.SLEEP.test(text)) return act("sleep", "bored", "dormir");
  if (P.SIT.test(text)) return act("sit", "happy", "sentarse");
  if (P.WAIT.test(text)) return act("wait", "smug", "esperar");
  return null;
}

/** La respuesta a lo que le escribieron: { text, face, verb, arg }. */
export function reply(rocco, message) {
  const text = normalize(message);
  const isPastQuestion = P.PAST_QUESTION && P.PAST_QUESTION.test(text);
  const o = isPastQuestion ? null : order(rocco, text);
  if (o) return o;

  if (P.ROCCO_DEATH.test(text)) return say("death_info", "serious");
  if (P.ROCCO_FOOD.test(text)) return say("photosynthesis", "smug");
  if (P.VEGGIE_INSULT.test(text)) return say("veggie", "angry");
  if (P.RUDE.test(text)) return say("rude", "smug");
  if (P.SORRY.test(text)) return say("sorry", "smug");
  if (P.ROCCO_OK.test(text)) return say("rocco_ok", "happy");
  if (P.SHUSH.test(text)) return say("shush", "bored");
  if (P.CHATTY.test(text)) return say("chatty", "happy");
  if (P.WHO.test(text)) return say("who", "smug");
  if (P.ORIGIN.test(text)) return say("origin", "smug");
  // La ayuda de Java habla de cosas que aca no estan (craftear, la tapa): esta es la de Bedrock.
  if (P.HELP.test(text)) return { text: BEDROCK_HELP, face: "serious", verb: null };
  if (P.COMPLIMENT.test(text)) return say("compliment", "cheer");
  if (P.THANKS.test(text)) return say("thanks", "smug");
  if (P.PLAYER_SCARED.test(text)) return say("player_scared", "serious");
  if (P.PLAYER_SAD.test(text)) return say("player_sad", "sad");
  if (P.PLAYER_HAPPY.test(text)) return say("player_happy", "cheer");
  if (P.TIRED.test(text)) return say("player_tired", "bored");
  if (P.HUNGRY.test(text)) return say("player_hungry", "serious");
  if (P.BORED_PLAYER.test(text)) return say("player_bored", "smug");
  if (P.JOKE.test(text)) return say("joke", "smug");
  if (P.FACT.test(text)) return say("fact", "happy");
  if (P.WHAT_DOING.test(text)) return say("what_doing", "smug");
  if (P.TIME && P.TIME.test(text)) {
    const t = world.getTimeOfDay();
    return say(t >= 13000 && t < 23000 ? "time_night" : "time_day", "happy");
  }
  if (P.WEATHER.test(text)) return say("weather_talk", "bored");
  if (P.MOBS.test(text)) return say("mobs_talk", "serious");
  if (P.ORES.test(text)) return say("ores_talk", "smug");
  if (P.DIMENSIONS.test(text)) return say("dimension_talk", "serious");
  if (P.MATE_TALK.test(text)) return say("mate_talk", "happy");
  if (P.LIKES.test(text)) return say("likes", "happy");
  if (P.GOODNIGHT.test(text)) return say("goodnight", "happy");
  if (P.GOODMORNING.test(text)) return say("goodmorning", "happy");
  if (P.FAREWELL.test(text)) return say("farewell", "sad");
  if (P.HOW_ARE_YOU.test(text)) return say("how_are_you", "happy");
  if (P.GREETING.test(text)) return say("greeting", "happy");
  if (P.LAUGH.test(text)) return say("laugh", "happy");
  if (P.AGREE.test(text)) return say("agree", "happy");
  if (P.DISAGREE.test(text)) return say("disagree", "bored");
  if (text.includes("?")) return say("question", "smug");
  return say("filler", "bored");
}

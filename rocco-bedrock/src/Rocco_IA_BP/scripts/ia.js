// Rocco IA: el paquete opcional para servidores dedicados (BDS). Escucha los pedidos del paquete
// principal (scriptevent rocco:ia_pedido), le pregunta a la IA con la personalidad de Rocco y
// devuelve la respuesta (rocco:ia_respuesta). Tambien deja hablarle directo en el chat.
//
// La clave NUNCA va en el paquete: el servidor la lee de config/<uuid de este paquete>/secrets.json
// y el proveedor y el modelo de variables.json (ver LEEME).
import { world, system } from "@minecraft/server";
import { http, HttpRequest, HttpHeader, HttpRequestMethod } from "@minecraft/server-net";
import { secrets, variables } from "@minecraft/server-admin";
import { PROMPTS, FACES } from "./gen/prompt.js";

const PROVIDERS = {
  gemini: { url: "https://generativelanguage.googleapis.com/v1beta", model: "gemma-4-26b-a4b-it" },
  groq: { url: "https://api.groq.com/openai/v1", model: "auto" },
  openrouter: { url: "https://openrouter.ai/api/v1", model: "openrouter/free" },
  openai: { url: "https://api.openai.com/v1", model: "gpt-4o-mini" },
  propio: { url: "", model: "auto" },
};
const TIMEOUT_SECONDS = 9;

// Lo que puede hacer en Bedrock (los verbos de chat.js del paquete principal).
const ACTIONS = [
  "seguir | follow the player", "esperar | stay here, stop following", "parar | stop what you're doing",
  "teletransportarse | appear next to the player", "dar_cola | give the player a Roco Cola",
  "mate | drink mate yourself", "cebar_mate | share mate with the player (you pour, they drink)",
  "bailar | dance", "aplaudir | clap", "festejar | celebrate", "saludar | wave",
  "asado | make a barbecue (needs raw meat and coal)", "tortas_fritas | fry tortas fritas (needs wheat)",
  "jugar_truco | set up the truco table and play", "sentarse | sit down", "dormir | lie down to sleep",
  "levantarse | get up",
];

function setting(name, fallback) {
  try {
    const v = variables.get(name);
    return v === undefined || v === null || v === "" ? fallback : v;
  } catch {
    return fallback;
  }
}

function provider() {
  const id = String(setting("proveedor", "gemini")).toLowerCase();
  const base = PROVIDERS[id] ?? PROVIDERS.propio;
  return { id, url: String(setting("url", base.url)).replace(/\/+$/, ""), model: String(setting("modelo", base.model)) };
}

function key() {
  try {
    return secrets.get("clave");
  } catch {
    return undefined;
  }
}

let lastError = "";
let autoModel = null;

// ---------------------------------------------------------------- el prompt

function systemPrompt(req) {
  const mode = req.modo === "sin_filtro" ? "sin_filtro" : "chill";
  const actions = [
    "When the player asks for something you can really do, answer normally AND end your reply with exactly one order line:",
    "#accion: <verb>",
    "Verbs:",
    ...ACTIONS.map((a) => "- " + a),
    "Only use a verb when they really ask for it. Without an order, don't write the line.",
  ].join("\n");
  const rightNow = [
    "The player is " + req.jugador + ". Your name is " + req.nombre + ".",
    "You're in Minecraft Bedrock (" + String(req.hora || "").replace("minecraft:", "") + ").",
    req.memoria && req.memoria.length ? "Recent memory:\n" + req.memoria.map((m) => "- " + m).join("\n") : "",
  ].filter(Boolean).join("\n");
  return PROMPTS[mode].replace("{{actions}}", actions).replace("{{faces}}", FACES[mode]).replace("{{right_now}}", rightNow);
}

// ---------------------------------------------------------------- la llamada

async function chooseModel(p, secret) {
  if (p.model !== "auto") return p.model;
  if (autoModel) return autoModel;
  const req = new HttpRequest(p.url + "/models");
  req.method = HttpRequestMethod.Get;
  req.timeout = TIMEOUT_SECONDS;
  if (secret) req.headers = [new HttpHeader("Authorization", secret)];
  try {
    const res = await http.request(req);
    const ids = (JSON.parse(res.body).data || []).map((m) => m.id).filter((id) => !/whisper|tts|embed|guard|vision/i.test(id));
    autoModel = ids.find((id) => /versatile|70b/i.test(id)) || ids.find((id) => /instruct|chat/i.test(id)) || ids[0] || null;
  } catch {
    autoModel = null;
  }
  return autoModel || "llama-3.3-70b-versatile";
}

async function ask(req) {
  const p = provider();
  const secret = key();
  if (!secret && p.id !== "propio") throw new Error("falta la clave (config/.../secrets.json, \"clave\")");
  const prompt = systemPrompt(req);
  let request;
  if (p.id === "gemini") {
    request = new HttpRequest(p.url + "/models/" + encodeURIComponent(p.model) + ":generateContent");
    request.method = HttpRequestMethod.Post;
    request.headers = [new HttpHeader("Content-Type", "application/json"), new HttpHeader("x-goog-api-key", secret ?? "")];
    request.body = JSON.stringify({
      systemInstruction: { parts: [{ text: prompt }] },
      contents: [{ role: "user", parts: [{ text: req.texto }] }],
      generationConfig: { temperature: 0.9, maxOutputTokens: 300 },
    });
  } else {
    // La clave de estos proveedores se guarda completa como "Bearer ..." (el servidor no deja
    // armarla aca: el secreto no se puede leer, solo mandar tal cual).
    const model = await chooseModel(p, secret);
    request = new HttpRequest(p.url + "/chat/completions");
    request.method = HttpRequestMethod.Post;
    const headers = [new HttpHeader("Content-Type", "application/json")];
    if (secret) headers.push(new HttpHeader("Authorization", secret));
    request.headers = headers;
    request.body = JSON.stringify({
      model,
      messages: [{ role: "system", content: prompt }, { role: "user", content: req.texto }],
      temperature: 0.9,
      max_tokens: 300,
    });
  }
  request.timeout = TIMEOUT_SECONDS;
  const res = await http.request(request);
  if (res.status < 200 || res.status >= 300) throw new Error("HTTP " + res.status + " " + String(res.body).slice(0, 160));
  const data = JSON.parse(res.body);
  const text = p.id === "gemini"
    ? (data.candidates?.[0]?.content?.parts ?? []).map((x) => x.text || "").join("")
    : data.choices?.[0]?.message?.content ?? "";
  if (!text.trim()) throw new Error("respuesta vacia");
  return text;
}

// "@smug Que te crees?\n#accion: bailar" -> cara, texto y orden.
function parse(answer) {
  let text = String(answer).trim();
  let cara = "happy";
  let accion = "";
  const order = text.match(/#accion:\s*([a-z_]+)/i);
  if (order) {
    accion = order[1].toLowerCase();
    text = text.replace(/#accion:[^\n]*/i, "").trim();
  }
  const face = text.match(/^\s*[*_]*[@[]([a-z_]+)\]?[*_]*/i);
  if (face) {
    cara = face[1].toLowerCase();
    text = text.slice(face[0].length).trim();
  }
  text = text.replace(/\s*\[[a-zA-Z ]{2,24}\]/g, "").replace(/@voz:\S+/gi, "").trim();
  return { texto: text.slice(0, 400), cara, accion };
}

// ---------------------------------------------------------------- los eventos

system.afterEvents.scriptEventReceive.subscribe((event) => {
  if (event.id !== "rocco:ia_pedido") return;
  let req;
  try {
    req = JSON.parse(event.message);
  } catch {
    return;
  }
  ask(req)
    .then((answer) => {
      lastError = "";
      const out = parse(answer);
      system.sendScriptEvent("rocco:ia_respuesta", JSON.stringify({ id: req.id, ...out }).slice(0, 1900));
    })
    .catch((e) => {
      lastError = String(e);
      console.warn("[Rocco IA] " + lastError);
      // Sin respuesta: el paquete principal contesta con sus frases propias.
      system.sendScriptEvent("rocco:ia_respuesta", JSON.stringify({ id: req.id, texto: "" }));
    });
});

// Hablarle en el chat: si escribis cerca de tu Rocco (o empezas con "rocco"), le llega.
world.beforeEvents.chatSend.subscribe((event) => {
  const player = event.sender;
  const message = event.message;
  if (message.startsWith("/")) return;
  system.run(() => {
    system.sendScriptEvent("rocco:ia_chat", JSON.stringify({ jugador: player.id, texto: message.slice(0, 400) }));
  });
});

// Avisa que esta (el paquete principal muestra "IA conectada" mientras le llegue esto).
system.runInterval(() => {
  system.sendScriptEvent("rocco:ia_hola", lastError ? "error:" + lastError.slice(0, 200) : "ok");
}, 20 * 30);
system.run(() => system.sendScriptEvent("rocco:ia_hola", "ok"));

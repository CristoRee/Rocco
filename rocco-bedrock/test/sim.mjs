// Corre los scripts de Rocco para Bedrock contra la imitacion del juego, de punta a punta.
import { world, startup, advance, addPlayer, log, ItemStack, commands } from "@minecraft/server";
import { ui } from "@minecraft/server-ui";

const fails = [];
const errors = [];
const origWarn = console.warn;
console.warn = (m) => { log.warns.push(String(m)); };
const check = (what, ok) => { (ok ? origWarn : (x) => { fails.push(x); origWarn(x); })((ok ? "PASS " : "FAIL ") + what); };
// Cada tick deja correr las promesas pendientes (los formularios contestan asi).
const run = async (n) => {
  for (let i = 0; i < n; i++) {
    advance(1, (e) => { errors.push(e.stack || String(e)); });
    await new Promise((r) => setImmediate(r));
  }
};
const say = (p, text) => commands["rocco:decir"]({ sourceEntity: p }, text);
const inv = (e) => e.getComponent("minecraft:inventory").container;
const count = (e, id) => inv(e).slots.reduce((n, s) => n + (s && s.typeId === id ? s.amount : 0), 0);
const lastRocco = () => log.messages.filter((m) => m.includes("<Rocco>")).slice(-1)[0] || "";

await import("./main.js");
startup();
check("comandos registrados", !!commands["rocco:decir"] && !!commands["rocco:menu"] && !!commands["rocco:brotar"]);

// ---------- brotar y despertar
const p = addPlayer("Dev");
world.afterEvents.playerSpawn.fire({ player: p, initialSpawn: true });
await run(80);
const overworld = world.getDimension("minecraft:overworld");
const rocco = overworld.getEntities({ type: "rocco:rocco" })[0];
check("Rocco brota al entrar", !!rocco);
check("brota plantado", rocco.getDynamicProperty("rocco:state") === "plant" && rocco.getProperty("rocco:plant") === true);
check("muestra plant_idle", rocco.getProperty("rocco:anim") > 0);
world.afterEvents.playerInteractWithEntity.fire({ player: p, target: rocco });
await run(5);
check("empieza a despertar", rocco.getDynamicProperty("rocco:state") === "awakening");
await run(130);
check("despierto", rocco.getDynamicProperty("rocco:state") === "active");
check("dice algo al despertar", lastRocco().length > 10);
check("domado por el jugador", rocco.owner === p);
check("ya no es planta", rocco.getProperty("rocco:plant") === false);
check("vuelve al modo automatico", rocco.getProperty("rocco:anim") === 0);

// ---------- el menu y hablar
ui.respond = (form) => {
  if (form.kind === "action") {
    const i = form.buttons.findIndex((b) => JSON.stringify(b.text).includes("rocco.menu.talk"));
    return { selection: i };
  }
  return { formValues: ["hola rocco, como andas?"] };
};
const before = log.messages.length;
world.afterEvents.playerInteractWithEntity.fire({ player: p, target: rocco });
await run(5);
check("el menu habla y contesta", log.messages.slice(before).some((m) => m.includes("<Rocco>")));
ui.respond = () => ({ canceled: true });

// ---------- ordenes por /rocco:decir
say(p, "quedate aca");
await run(3);
check("quedate: deja de seguir", rocco.events.includes("rocco:stay"));
say(p, "seguime");
await run(3);
check("seguime: vuelve a seguir", rocco.events.includes("rocco:follow"));
say(p, "che, contame un chiste");
await run(3);
check("un chiste", lastRocco().length > 10);

// ---------- asado
inv(rocco).addItem(new ItemStack("minecraft:beef", 2));
inv(rocco).addItem(new ItemStack("minecraft:porkchop", 1));
inv(rocco).addItem(new ItemStack("minecraft:coal", 1));
say(p, "hace un asado");
await run(3);
check("arranca el asado", rocco.getProperty("rocco:anim") > 0 && rocco.events.includes("rocco:busy"));
let sawBits = false;
for (let i = 0; i < 900; i++) { await run(1); if (rocco.getProperty("rocco:bits") > 0) sawBits = true; }
check("el asado muestra la carne (bits)", sawBits);
check("asado listo: te da 2 de tira y 1 bondiola", count(p, "rocco:asado_de_tira") === 2 && count(p, "rocco:bondiola_asada") === 1);
check("gasto la carne", count(rocco, "minecraft:beef") === 0 && count(rocco, "minecraft:porkchop") === 0);
check("libre despues del asado", rocco.events.lastIndexOf("rocco:free") > rocco.events.lastIndexOf("rocco:busy"));

// ---------- tortas fritas (el trigo lo tenes vos)
inv(p).addItem(new ItemStack("minecraft:wheat", 2));
say(p, "haceme unas tortas fritas");
await run(1300);
check("tortas fritas: 2", count(p, "rocco:torta_frita") === 2 && count(p, "minecraft:wheat") === 0);

// ---------- mate compartido
say(p, "cebame un mate");
let rounds = 0;
for (let i = 0; i < 4000 && rounds < 10; i++) {
  await run(1);
  if (count(p, "rocco:mate") > 0) {
    await run(20);
    world.afterEvents.itemCompleteUse.fire({ source: p, itemStack: new ItemStack("rocco:mate") });
    rounds++;
  }
}
await run(200);
check("tomo varios mates (" + rounds + ")", rounds >= 3);
check("no te queda el mate", count(p, "rocco:mate") === 0);

// ---------- Roco Cola
say(p, "dame una roco cola");
await run(120);
check("te da una Roco Cola", count(p, "rocco:roco_cola") === 1);

// ---------- pelea, tumba y desenterrar
const zombie = overworld.spawnEntity("minecraft:zombie", { x: 3, y: 0, z: 3 });
world.afterEvents.entityHitEntity.fire({ damagingEntity: rocco, hitEntity: zombie });
await run(2);
check("le pega con animacion", rocco.getProperty("rocco:anim") > 0);
world.afterEvents.dataDrivenEntityTrigger.fire({ entity: rocco, eventId: "rocco:died" });
await run(200);
check("queda en la tumba", rocco.getDynamicProperty("rocco:state") === "grave");
world.afterEvents.playerInteractWithEntity.fire({ player: p, target: rocco });
await run(200);
check("lo desenterras", rocco.getDynamicProperty("rocco:state") === "active");

// ---------- equipo
inv(p).setItem(0, new ItemStack("minecraft:iron_chestplate"));
world.afterEvents.playerInteractWithEntity.fire({ player: p, target: rocco });
await run(3);
check("se pone la pechera de hierro", rocco.getProperty("rocco:armor_chest") === 3);
inv(p).setItem(0, new ItemStack("minecraft:diamond_sword"));
world.afterEvents.playerInteractWithEntity.fire({ player: p, target: rocco });
await run(3);
check("agarra la espada de diamante", rocco.getProperty("rocco:weapon") === 1 && rocco.getProperty("rocco:weapon_mat") === 4);
inv(p).setItem(0, new ItemStack("rocco:mochila_3"));
world.afterEvents.playerInteractWithEntity.fire({ player: p, target: rocco });
await run(3);
check("se pone la mochila 3", rocco.getProperty("rocco:pack") === 3);

// ---------- truco
let truco = { forms: 0, plays: 0, mates: 0 };
ui.respond = (form) => {
  truco.forms++;
  if (form.kind === "modal") return { formValues: [0, 0, 1, true] };
  // Juega siempre la primera opcion que no sea irse ni el mazo.
  const i = form.buttons.findIndex((b) => !JSON.stringify(b.text).match(/leave|mazo|Me voy/i));
  truco.plays++;
  return { selection: Math.max(0, i) };
};
say(p, "jugamos un truco?");
await run(5);
check("arma la mesa y las sillas", overworld.getEntities({ type: "rocco:truco_mesa" }).length === 1 && overworld.getEntities({ type: "rocco:truco_silla" }).length === 2);
let ended = false;
for (let i = 0; i < 20 * 60 * 25; i++) {
  await run(1);
  if (count(p, "rocco:mate") > 0) {
    await run(10);
    world.afterEvents.itemCompleteUse.fire({ source: p, itemStack: new ItemStack("rocco:mate") });
    truco.mates++;
  }
  if (overworld.getEntities({ type: "rocco:truco_mesa" }).length === 0) { ended = true; break; }
}
check("la partida termina (" + truco.plays + " jugadas tuyas, " + truco.mates + " mates)", ended);
check("alguien gano", log.messages.some((m) => m.includes("gui.rocco.truco.won")));
check("se guardo la mesa", overworld.getEntities({ type: "rocco:truco_carta" }).length === 0 && overworld.getEntities({ type: "rocco:truco_silla" }).length === 0);
check("Rocco se levanto", rocco.events.lastIndexOf("rocco:unseat") > rocco.events.lastIndexOf("rocco:seat"));
ui.respond = () => ({ canceled: true });

// ---------- un rato libre largo (mate, cola, charla espontanea)
const msgs = log.messages.length;
await run(20 * 60 * 15);
check("en 15 minutos libre dijo algo", log.messages.length > msgs);

// ---------- regalo al volver
world.setDynamicProperty("rocco:left:" + p.id, Date.now() - 30 * 3600 * 1000);
const items = inv(p).slots.filter(Boolean).length;
world.afterEvents.playerSpawn.fire({ player: p, initialSpawn: true });
await run(200);
check("regalo al volver despues de 30 horas", inv(p).slots.filter(Boolean).length > items);

const roccoWarns = log.warns.filter((w) => w.startsWith("[Rocco]") && !w.includes("listo") && !w.includes("brotado"));
origWarn("\nErrores de ejecucion: " + errors.length);
for (const e of errors.slice(0, 10)) origWarn(e);
origWarn("Avisos de Rocco: " + roccoWarns.length);
for (const w of [...new Set(roccoWarns)].slice(0, 20)) origWarn("  " + w);
origWarn("Fallas: " + fails.length);
process.exit(fails.length || errors.length || roccoWarns.length ? 1 : 0);

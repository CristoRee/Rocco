// Prueba de Rocco en el servidor oficial de Bedrock con un jugador simulado.
// Se larga con "/scriptevent rocco:prueba" y escribe "PRUEBA PASS/FAIL ..." en la consola.
import { world, system, ItemStack, GameMode } from "@minecraft/server";
import { spawnSimulatedPlayer } from "@minecraft/server-gametest";
// La tabla de animaciones de Rocco (la copia el lanzador desde el paquete armado). Las propiedades
// dinamicas de cada paquete son privadas: el estado se lee de lo que se ve (propiedades y animacion).
import { ANIMS } from "./anims.js";

let fails = 0;
const check = (what, ok) => {
  if (!ok) fails++;
  console.warn("PRUEBA " + (ok ? "PASS " : "FAIL ") + what);
};
const wait = (ticks) => system.waitTicks(ticks);
const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z);
const inv = (e) => e.getComponent("minecraft:inventory").container;
const count = (e, id) => {
  const c = inv(e);
  let n = 0;
  for (let i = 0; i < c.size; i++) {
    const s = c.getItem(i);
    if (s && s.typeId === id) n += s.amount;
  }
  return n;
};
const animIs = (r, ...names) => names.some((n) => ANIMS[n] && r.getProperty("rocco:anim") === ANIMS[n].id);
const state = (r) => {
  if (animIs(r, "grave_feet", "grave_hand", "grave_head", "death")) return "grave";
  if (animIs(r, "awaken")) return "awakening";
  if (r.getProperty("rocco:plant")) return "plant";
  return "active";
};
const say = (p, text) => p.runCommand('rocco:decir "' + text + '"');

function roccoOf(player) {
  return world.getDimension("overworld").getEntities({ type: "rocco:rocco", location: player.location, maxDistance: 8 })[0];
}

async function waitFor(what, ticks, fn) {
  for (let i = 0; i < ticks; i++) {
    let ok = false;
    try { ok = !!fn(); } catch { ok = false; }
    if (ok) return true;
    await wait(1);
  }
  return false;
}

async function run() {
  const dim = world.getDimension("overworld");
  const top = dim.getTopmostBlock({ x: 0, z: 0 });
  const base = { x: 0.5, y: (top ? top.y : 64) + 1, z: 0.5 };
  console.warn("PRUEBA suelo en y=" + base.y);
  dim.runCommand("time set day");
  dim.runCommand("gamerule domobspawning false");
  dim.runCommand("difficulty easy");

  const p = spawnSimulatedPlayer({ dimension: dim, ...base }, "Prueba", GameMode.Survival);
  await wait(100);
  let rocco = roccoOf(p);
  check("Rocco brota al entrar el jugador", !!rocco);
  if (!rocco) {
    p.runCommand("rocco:brotar");
    await wait(10);
    rocco = roccoOf(p);
  }
  if (!rocco) return;
  check("brota plantado", state(rocco) === "plant" && rocco.getProperty("rocco:plant") === true);

  // Despertar.
  p.lookAtEntity(rocco);
  await wait(5);
  console.warn("PRUEBA toque: " + p.interactWithEntity(rocco) + " distancia " + dist(p.location, rocco.location).toFixed(2));
  check("empieza a despertar", await waitFor("", 20, () => state(rocco) === "awakening"));
  check("despierta", await waitFor("", 200, () => state(rocco) === "active"));
  check("domado", !!rocco.getComponent("minecraft:is_tamed"));

  // Hablar.
  say(p, "hola rocco, como andas?");
  check("habla (boca)", await waitFor("", 40, () => rocco.getProperty("rocco:talk") === true));

  // Seguir: el jugador se aleja y Rocco va.
  await wait(60);
  p.teleport({ x: base.x + 10, y: base.y, z: base.z + 10 });
  const followed = await waitFor("", 400, () => dist(rocco.location, p.location) < 4);
  check("te sigue (" + dist(rocco.location, p.location).toFixed(1) + " bloques)", followed);

  // Quedarse.
  say(p, "quedate aca");
  await wait(20);
  p.teleport({ x: base.x - 12, y: base.y, z: base.z - 12 });
  await wait(100);
  check("se queda cuando se lo pedis", dist(rocco.location, p.location) > 6);
  say(p, "veni aca");
  check("viene al llamarlo", await waitFor("", 60, () => dist(rocco.location, p.location) < 4));
  say(p, "seguime");
  await wait(20);

  // Asado (con lo del jugador).
  inv(p).addItem(new ItemStack("minecraft:beef", 2));
  inv(p).addItem(new ItemStack("minecraft:coal", 1));
  say(p, "hace un asado");
  check("arranca el asado", await waitFor("", 40, () => rocco.getProperty("rocco:anim") > 0));
  let bits = false;
  const done = await waitFor("", 900, () => {
    if (rocco.getProperty("rocco:bits") > 0) bits = true;
    return count(p, "rocco:asado_de_tira") >= 2;
  });
  check("el asado muestra la carne", bits);
  check("asado listo: 2 de tira", done);

  // Tortas fritas.
  inv(p).addItem(new ItemStack("minecraft:wheat", 1));
  say(p, "haceme tortas fritas");
  check("tortas fritas", await waitFor("", 900, () => count(p, "rocco:torta_frita") >= 1));

  // Mate compartido: Rocco te pasa el mate y lo tomas de verdad.
  say(p, "cebame un mate");
  let drank = 0;
  for (let round = 0; round < 2; round++) {
    const got = await waitFor("", 700, () => count(p, "rocco:mate") > 0);
    if (!got) break;
    const c = inv(p);
    let slot = -1;
    for (let i = 0; i < c.size; i++) if (c.getItem(i)?.typeId === "rocco:mate") slot = i;
    if (slot > 8) {
      const s = c.getItem(slot);
      c.setItem(slot, undefined);
      c.setItem(1, s);
      slot = 1;
    }
    p.selectedSlotIndex = slot;
    p.useItemInSlot(slot);
    await wait(45);
    p.stopUsingItem();
    if (await waitFor("", 60, () => count(p, "rocco:mate") === 0)) drank++;
  }
  check("tomaste mates que te cebo (" + drank + ")", drank >= 1);
  say(p, "para");
  await wait(120);

  // Roco Cola.
  say(p, "dame una roco cola");
  check("te da una Roco Cola", await waitFor("", 200, () => count(p, "rocco:roco_cola") >= 1));

  // Pelea: un zombi cerca.
  await wait(60);
  const z = dim.spawnEntity("minecraft:zombie", { x: rocco.location.x + 4, y: rocco.location.y, z: rocco.location.z });
  const hp0 = z.getComponent("minecraft:health").currentValue;
  const fought = await waitFor("", 400, () => !z.isValid || z.getComponent("minecraft:health").currentValue < hp0);
  check("pelea con el zombi", fought);
  if (z.isValid) z.remove();

  // Equipo: le das una pechera de hierro.
  await wait(40);
  inv(p).setItem(0, new ItemStack("minecraft:iron_chestplate", 1));
  p.selectedSlotIndex = 0;
  p.lookAtEntity(rocco);
  await wait(5);
  p.interactWithEntity(rocco);
  check("se pone la pechera", await waitFor("", 40, () => rocco.getProperty("rocco:armor_chest") === 3));

  // Comida.
  inv(p).setItem(0, new ItemStack("minecraft:bread", 2));
  p.selectedSlotIndex = 0;
  await wait(60);
  console.warn("PRUEBA comida en la mano: " + (p.getComponent("minecraft:equippable")?.getEquipment("Mainhand")?.typeId) + " es comida: " + !!p.getComponent("minecraft:equippable")?.getEquipment("Mainhand")?.getComponent("minecraft:food") + " toque " + p.interactWithEntity(rocco));
  check("le das de comer", await waitFor("", 40, () => count(p, "minecraft:bread") === 1));

  // Un golpe mortal: a la tumba, y lo desenterras.
  await wait(80);
  rocco.applyDamage(500);
  check("queda en la tumba", await waitFor("", 200, () => state(rocco) === "grave"));
  check("sigue vivo", rocco.isValid);
  await wait(80);
  p.interactWithEntity(rocco);
  check("lo desenterras", await waitFor("", 300, () => state(rocco) === "active"));

  // Los muebles del truco en el motor real (la partida en si la prueba la simulacion de Node).
  const at = { x: p.location.x + 2, y: p.location.y, z: p.location.z };
  const mesa = dim.spawnEntity("rocco:truco_mesa", at);
  const silla = dim.spawnEntity("rocco:truco_silla", { x: at.x + 1, y: at.y, z: at.z });
  const sillaR = dim.spawnEntity("rocco:truco_silla", { x: at.x - 1, y: at.y, z: at.z });
  const carta = dim.spawnEntity("rocco:truco_carta", { x: at.x, y: at.y + 0.79, z: at.z });
  // Igual que el truco: el evento y el pedido de asiento en el mismo tick (ahi todavia no hay asiento).
  silla.triggerEvent("rocco:for_player");
  sillaR.setProperty("rocco:for_rocco", true);
  carta.setProperty("rocco:card", 23);
  check("en el mismo tick la silla todavia no tiene asiento (por eso se reintenta)", !silla.getComponent("minecraft:rideable"));
  let sentado = false;
  for (let i = 0; i < 20 && !sentado; i++) {
    await wait(2);
    const r = silla.getComponent("minecraft:rideable");
    sentado = !!r && r.addRider(p);
  }
  check("te sentas en la silla (reintentando)", sentado);
  await wait(5);
  const vehiculo = p.getComponent("minecraft:riding")?.entityRidingOn;
  check("seguis sentado", !!vehiculo && vehiculo.id === silla.id);
  check("la carta muestra el 5 de oros", carta.getProperty("rocco:card") === 23);
  rocco.triggerEvent("rocco:seat");
  rocco.teleport({ x: sillaR.location.x, y: sillaR.location.y + 0.5625, z: sillaR.location.z });
  rocco.setRotation({ x: 0, y: 90 });
  await wait(100);
  const dy = rocco.location.y - (sillaR.location.y + 0.5625);
  console.warn("PRUEBA dato Rocco sentado dy=" + dy.toFixed(3) + " rot=" + rocco.getRotation().y.toFixed(0));
  check("Rocco sentado no se cae", Math.abs(dy) < 0.05);
  check("Rocco mira la mesa", Math.abs(rocco.getRotation().y - 90) < 5);
  rocco.triggerEvent("rocco:unseat");
  silla.getComponent("minecraft:rideable")?.ejectRiders();
  for (const e of [mesa, silla, sillaR, carta]) e.remove();
  await wait(40);
  check("Rocco parado vuelve al piso", Math.abs(rocco.location.y - base.y) < 1.2);

  // El paquete de la IA (si esta cargado): avisa, contesta los pedidos y pasa el chat.
  if (iaSeen) {
    check("la IA avisa que esta", true);
    iaAnswer = null;
    system.sendScriptEvent("rocco:ia_pedido", JSON.stringify({ id: 99999, jugador: "Prueba", texto: "hola", modo: "chill", nombre: "Rocco", memoria: [] }));
    check("la IA contesta (sin clave: vacio, y Rocco usa sus frases)", await waitFor("", 200, () => iaAnswer !== null));
    console.warn("PRUEBA respuesta de la IA: " + iaAnswer);
    await wait(60);
    p.chat("hola rocco, todo bien?");
    check("hablarle por el chat le llega", await waitFor("", 60, () => rocco.getProperty("rocco:talk") === true));
    if (iaAnswer && iaAnswer.includes("Todo de diez")) {
      // Con el proveedor de prueba: lo que contesta la IA se dice y su orden se hace.
      // Que termine de bailar lo de antes (el proveedor de prueba siempre pide bailar).
      await waitFor("", 400, () => !animIs(rocco, "dance_fiesta", "dance_meneito"));
      // Que no este en otra cosa (un mate por su cuenta, por ejemplo): ocupado no baila.
      say(p, "para");
      await wait(40);
      say(p, "che, como andas hoy?");
      const danced = await waitFor("", 200, () => animIs(rocco, "dance_fiesta", "dance_meneito"));
      const current = Object.keys(ANIMS).find((n) => ANIMS[n].id === rocco.getProperty("rocco:anim")) ?? "auto";
      check("la IA le hace bailar (#accion: bailar) [animacion: " + current + "]", danced);
      await wait(40);
    }
  }

  console.warn("PRUEBA TERMINADO fallas=" + fails);
  p.disconnect();
}

let iaSeen = false;
let iaAnswer = null;
system.afterEvents.scriptEventReceive.subscribe((e) => {
  if (e.id === "rocco:ia_hola") iaSeen = true;
  if (e.id === "rocco:ia_respuesta") iaAnswer = e.message;
});


// Daño: que pasa con un golpe mortal de distintas causas.
async function dano() {
  const dim = world.getDimension("overworld");
  const top = dim.getTopmostBlock({ x: 0, z: 0 });
  const y = (top ? top.y : 64) + 1;
  for (const cause of ["entity_attack", "lava", "void", "script"]) {
    const r = dim.spawnEntity("rocco:rocco", { x: 2.5, y, z: 0.5 });
    r.triggerEvent("rocco:wake");
    await wait(10);
    if (cause === "script") r.applyDamage(500);
    else dim.runCommand("damage @e[type=rocco:rocco,r=3,x=2,y=" + y + ",z=0] 500 " + cause);
    await wait(10);
    const h = r.isValid ? r.getComponent("minecraft:health").currentValue : -1;
    console.warn("PRUEBA dano " + cause + ": vivo=" + r.isValid + " vida=" + h);
    if (r.isValid) r.remove();
    await wait(5);
  }
}

world.afterEvents.playerInteractWithEntity.subscribe((e) => console.warn("PRUEBA evento: toco " + e.target.typeId));
world.afterEvents.dataDrivenEntityTrigger.subscribe((e) => console.warn("PRUEBA evento de entidad: " + e.eventId));
world.beforeEvents.playerInteractWithEntity.subscribe((e) => console.warn("PRUEBA evento antes: toco " + e.target.typeId));

system.afterEvents.scriptEventReceive.subscribe((e) => {
  if (e.id === "rocco:prueba_dano") {
    dano().catch((err) => console.warn("PRUEBA FAIL excepcion: " + err));
    return;
  }
  if (e.id !== "rocco:prueba") return;
  run().catch((err) => console.warn("PRUEBA FAIL excepcion: " + err + " " + (err?.stack ?? "")));
});

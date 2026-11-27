// La mesa de truco en Bedrock: vos contra Rocco (mano a mano), como en Java pero con
// formularios en vez de la pantalla de cartas. Rocco saca la mesa y las sillas con magia, se
// sienta, reparte, piensa, canta y festeja con las animaciones de la escena; las cartas jugadas
// quedan boca arriba sobre la mesa.
import { system, world, ItemStack } from "@minecraft/server";
import { ActionFormData, ModalFormData } from "@minecraft/server-ui";
import { Game, RoccoAI, rules, T, LABELS, card } from "./game.js";
import { rt, play, say, setFace, owner, displayName, sound, cutAnim } from "../rocco.js";
import { line } from "../offline.js";
import { now, choice, chance, dist2, safe, passable, standable } from "../util.js";
import { inventoryOf, takeItem } from "../items.js";
import { comeNear } from "../activities.js";

const PLAYER = 0;
const ROCCO = 1;
const sessions = new Map();

const WHY = {
  "flor (se achicaron)": "flor_achico", "contraflor al resto": "contraflor", "envido no querido": "envido_no",
  "contraflor no querida": "contraflor_no", "se fueron al mazo": "mazo", envido: "envido",
  "flor contra flor": "flor_flor", flor: "flor", truco: "truco", "no quisieron el truco": "truco_no",
};

export function trucoOf(rocco) {
  return sessions.get(rocco.id);
}

function tell(s, message) {
  safe("truco", () => s.player.sendMessage(message));
}

function tr(key, ...args) {
  return { translate: key, with: args.map(String) };
}

function seatName(s, seat) {
  return seat === ROCCO ? displayName(s.rocco) : s.player.name;
}

function teamName(s, team) {
  return team === s.game.team[ROCCO] ? displayName(s.rocco) : s.player.name;
}

// ---------------------------------------------------------------- invitar y armar

export function inviteTruco(rocco, player) {
  if (sessions.has(rocco.id)) {
    say(rocco, line("truco_already"), "smug");
    return;
  }
  const r = rt(rocco);
  if (r.scene || r.task) {
    say(rocco, line("travel_busy"), "bored");
    return;
  }
  say(rocco, line("truco_invite"), "cheer");
  const form = new ModalFormData()
    .title({ translate: "gui.rocco.truco.lobby.title" })
    .dropdown({ translate: "rocco.truco.variant" }, [{ translate: "gui.rocco.truco.variant.uruguayo" }, { translate: "gui.rocco.truco.variant.argentino" }], { defaultValueIndex: 0 })
    .dropdown({ translate: "rocco.truco.points" }, ["30", "15"], { defaultValueIndex: 0 })
    .dropdown({ translate: "rocco.truco.level" }, [
      { translate: "gui.rocco.truco.level.tranqui" }, { translate: "gui.rocco.truco.level.normal" }, { translate: "gui.rocco.truco.level.picaro" },
    ], { defaultValueIndex: 1 })
    .toggle({ translate: "rocco.truco.mate" }, { defaultValue: true });
  form.show(player).then((res) => {
    if (res.canceled || !res.formValues) return;
    const [variant, points, level, mate] = res.formValues;
    start(rocco, player, {
      variant: variant === 1 ? "argentino" : "uruguayo",
      points: points === 1 ? 15 : 30,
      level: ["tranqui", "normal", "picaro"][Number(level)] ?? "normal",
      mate: !!mate,
    });
  }).catch((e) => console.warn("[Rocco] truco: " + e));
}

function horizontal(v) {
  const len = Math.hypot(v.x, v.z) || 1;
  return { x: v.x / len, y: 0, z: v.z / len };
}

function yawOf(d) {
  return (Math.atan2(-d.x, d.z) * 180) / Math.PI;
}

function add(a, d, k) {
  return { x: a.x + d.x * k, y: a.y + (d.y ?? 0) * k, z: a.z + d.z * k };
}

function clearAt(dim, at) {
  const feet = safe("bloque", () => dim.getBlock({ x: Math.floor(at.x), y: Math.floor(at.y), z: Math.floor(at.z) }));
  const head = safe("bloque", () => dim.getBlock({ x: Math.floor(at.x), y: Math.floor(at.y) + 1, z: Math.floor(at.z) }));
  const ground = safe("bloque", () => dim.getBlock({ x: Math.floor(at.x), y: Math.floor(at.y) - 1, z: Math.floor(at.z) }));
  // Como Java: alcanza con que no choque (pasto alto, flores y nieve fina no molestan).
  return !!feet && !!head && !!ground && passable(feet) && passable(head) && standable(ground);
}


function puff(dim, at) {
  safe("puff", () => dim.spawnParticle("rocco:magic_burst", { x: at.x, y: at.y + 0.5, z: at.z }));
}

function start(rocco, player, opts) {
  if (!rocco.isValid || !player.isValid || sessions.has(rocco.id)) return;
  comeNear(rocco, player);
  if (player.dimension.id !== rocco.dimension.id || dist2(player.location, rocco.location) > 12 * 12) {
    say(rocco, line("truco_not_here"), "bored");
    return;
  }
  // La mesa entre los dos: Rocco de un lado, vos del otro (como TrucoLayout en la mesa chica).
  const f = horizontal({ x: player.location.x - rocco.location.x, z: player.location.z - rocco.location.z });
  const base = { x: Math.floor(rocco.location.x) + 0.5, y: Math.floor(rocco.location.y), z: Math.floor(rocco.location.z) + 0.5 };
  const center = add(base, f, 12.5 / 16 + 0.25);
  const roccoSeat = add(center, f, -12.5 / 16);
  const playerSeat = add(center, f, 14 / 16);
  const dim = rocco.dimension;
  if (!clearAt(dim, center) || !clearAt(dim, roccoSeat) || !clearAt(dim, playerSeat)) {
    say(rocco, line("truco_no_room"), "think");
    return;
  }
  const yaw = yawOf(f);
  const table = safe("mesa", () => dim.spawnEntity("rocco:truco_mesa", center));
  const chairR = safe("silla", () => dim.spawnEntity("rocco:truco_silla", roccoSeat));
  const chairP = safe("silla", () => dim.spawnEntity("rocco:truco_silla", playerSeat));
  if (!table || !chairR || !chairP) {
    for (const e of [table, chairR, chairP]) if (e) safe("sacar", () => e.remove());
    say(rocco, line("truco_no_room"), "think");
    return;
  }
  safe("girar", () => {
    table.setRotation({ x: 0, y: yaw });
    chairR.setRotation({ x: 0, y: yaw });
    chairP.setRotation({ x: 0, y: yaw + 180 });
    chairR.setProperty("rocco:for_rocco", true);
    chairP.triggerEvent("rocco:for_player");
  });
  puff(dim, center);
  puff(dim, playerSeat);

  const s = {
    rocco, player, opts, table, chairs: [chairR, chairP], cards: [], yaw, f, center,
    game: new Game(rules(opts.variant, opts.points), 2, Math.random() < 0.5 ? 0 : 1),
    ai: new RoccoAI(opts.level),
    waitUntil: now() + 60,
    formOpen: false,
    stage: "summon",
    mateStage: null,
  };
  sessions.set(rocco.id, s);
  const r = rt(rocco);
  r.scene = { kind: "truco" };
  safe("sentar", () => rocco.triggerEvent("rocco:seat"));
  seatRocco(s);
  // El asiento de tu silla aparece con el evento "for_player", que se aplica recien al tick
  // siguiente: hasta entonces no hay a que subirse. seatPlayer lo reintenta un rato.
  seatPlayer(s);
  play(rocco, "truco_summon");
  say(rocco, line("truco_start"), "cheer");
  sound(rocco, "rocco.magic_burst", 0.8);
  s.waitUntil = now() + 60;
  updateFan(s);
}

const ROCCO_SEAT = 9 / 16;

function seatSpot(s) {
  const c = s.chairs[0];
  return c && c.isValid ? { x: c.location.x, y: c.location.y + ROCCO_SEAT, z: c.location.z } : null;
}

/** Rocco arriba de su silla (sin gravedad mientras esta sentado), mirando la mesa. */
function seatRocco(s) {
  const at = seatSpot(s);
  if (!at || !s.rocco.isValid) return;
  safe("sentar", () => s.rocco.teleport(at));
  safe("girar", () => s.rocco.setRotation({ x: 0, y: s.yaw }));
}

function riding(player, chair) {
  const rider = safe("asiento", () => player.getComponent("minecraft:riding"));
  return !!rider && rider.entityRidingOn && rider.entityRidingOn.id === chair.id;
}

function seatPlayer(s, tries = 0) {
  if (!sessions.has(s.rocco.id) || !s.player.isValid) return;
  const chair = s.chairs[1];
  if (!chair || !chair.isValid || riding(s.player, chair)) return;
  const seat = safe("asiento", () => chair.getComponent("minecraft:rideable"));
  if (seat && safe("sentarte", () => seat.addRider(s.player))) return;
  if (tries < 20) system.runTimeout(() => seatPlayer(s, tries + 1), 2);
}

// ---------------------------------------------------------------- cartas en la mesa

function cardSpot(s, seat, trick) {
  const right = { x: s.f.z, z: -s.f.x };
  const side = seat === PLAYER ? 0.22 : -0.22;
  const at = add(s.center, s.f, side);
  const r = add(at, right, (trick - 1) * 0.28);
  return { x: r.x, y: s.center.y + 0.79, z: r.z };
}

function placeCard(s, seat, trick, id) {
  const at = cardSpot(s, seat, trick);
  const e = safe("carta", () => s.rocco.dimension.spawnEntity("rocco:truco_carta", at));
  if (!e) return;
  safe("carta", () => {
    e.setProperty("rocco:card", id);
    e.setRotation({ x: 0, y: s.yaw + (seat === PLAYER ? 180 : 0) });
  });
  s.cards.push(e);
  sound(s.rocco, "rocco.card_slap", 0.7);
}

function clearCards(s) {
  for (const e of s.cards) safe("carta", () => e.isValid && e.remove());
  s.cards = [];
  sound(s.rocco, "rocco.card_sweep", 0.6);
}

function updateFan(s) {
  const n = s.game.hands[ROCCO].length;
  safe("abanico", () => s.rocco.setProperty("rocco:bits", (Math.max(0, Math.min(3, n)) & 3) << 17));
}

// ---------------------------------------------------------------- lo que pasa en la mesa

function anim(s, name, extra = 10) {
  const ticks = play(s.rocco, name);
  s.waitUntil = Math.max(s.waitUntil, now() + ticks + extra);
}

function events(s) {
  const g = s.game;
  for (const e of g.drainEvents()) {
    switch (e.kind) {
      case "NEW_HAND":
        tell(s, tr("gui.rocco.truco.hand", e.value, seatName(s, e.seat)));
        if (e.seat === ROCCO) anim(s, "truco_deal");
        sound(s.rocco, "rocco.card_riffle", 0.7);
        updateFan(s);
        break;
      case "MUESTRA":
        tell(s, { rawtext: [{ translate: "gui.rocco.truco.muestra" }, { text: ": " + card.name(e.card) }] });
        break;
      case "CARD_PLAYED":
        placeCard(s, e.seat, e.value, e.card);
        if (e.seat === ROCCO) {
          anim(s, "truco_play_" + Math.max(1, Math.min(3, g.hands[ROCCO].length + 1)), 4);
          updateFan(s);
        }
        break;
      case "CALL":
        tell(s, tr("gui.rocco.truco.called", seatName(s, e.seat), e.text));
        if (e.seat === ROCCO) {
          const t = e.call;
          const kind = t === T.FLOR || t === T.CONTRAFLOR_AL_RESTO ? "flor" : t === T.TRUCO || t === T.RETRUCO || t === T.VALE_CUATRO ? "truco" : "envido";
          anim(s, "truco_call_" + kind);
          say(s.rocco, line(s.mood === "farol" ? "truco_bluff" : kind === "flor" ? "truco_flor" : kind === "truco" ? "truco_call" : t === T.FALTA_ENVIDO ? "truco_falta" : "truco_envido"), kind === "truco" ? "smug" : "happy");
        }
        break;
      case "ANSWER":
        tell(s, tr("gui.rocco.truco.answered", seatName(s, e.seat), e.text));
        if (e.seat === ROCCO) {
          const yes = e.call === T.QUIERO;
          anim(s, yes ? "truco_quiero" : "truco_no_quiero");
          say(s.rocco, line(yes ? "truco_quiero" : "truco_no_quiero"), yes ? "serious" : "bored");
        }
        break;
      case "AUTO_FLOR":
        tell(s, tr("gui.rocco.truco.had_flor", seatName(s, e.seat)));
        break;
      case "TANTO_SAID":
        if (e.value >= 0) tell(s, tr("gui.rocco.truco.tanto", seatName(s, e.seat), e.value));
        else tell(s, tr("gui.rocco.truco.son_buenas", seatName(s, e.seat)));
        if (e.seat === ROCCO && e.value >= 0 && chance(0.5)) say(s.rocco, line("truco_tanto", { n: e.value }), "smug");
        break;
      case "POINTS":
        tell(s, { rawtext: [{ text: "+" + e.value + " " }, { translate: "rocco.truco.for" }, { text: " " + teamName(s, e.team) + " (" }, { translate: "gui.rocco.truco.why." + (WHY[e.text] ?? "truco") }, { text: ")" }] });
        break;
      case "MAZO":
        tell(s, tr("gui.rocco.truco.mazo", seatName(s, e.seat)));
        if (e.seat === ROCCO) {
          anim(s, "truco_mazo");
          say(s.rocco, line("truco_mazo"), "bored");
        }
        break;
      case "TRICK_WON":
        sound(s.rocco, "rocco.table_knock", 0.5);
        if (e.team === g.team[ROCCO] && chance(0.35)) say(s.rocco, line("truco_trick_win"), "smug");
        s.waitUntil = Math.max(s.waitUntil, now() + 20);
        break;
      case "HAND_OVER": {
        tell(s, tr("gui.rocco.truco.hand_won", teamName(s, e.team), e.value));
        const won = e.team === g.team[ROCCO];
        anim(s, won ? "truco_win_hand" : "truco_lose_hand", 30);
        if (!won) say(s.rocco, line("truco_hand_lost"), "sad");
        s.stage = "between";
        break;
      }
      case "GAME_OVER": {
        const won = g.winnerTeam === g.team[ROCCO];
        tell(s, tr("gui.rocco.truco.won", teamName(s, g.winnerTeam), g.score[PLAYER], g.score[ROCCO]));
        anim(s, won ? "truco_win_game" : "truco_lose_game", 40);
        say(s.rocco, line(won ? "truco_game_win" : "truco_game_lost"), won ? "cheer" : "sad");
        s.stage = "over";
        break;
      }
    }
  }
  scoreboard(s);
}

function scoreboard(s) {
  const g = s.game;
  safe("marcador", () => s.player.onScreenDisplay.setActionBar(
    s.player.name + " " + g.score[PLAYER] + "  —  " + displayName(s.rocco) + " " + g.score[ROCCO] + "   (a " + g.rules.targetPoints + ")",
  ));
}

// ---------------------------------------------------------------- tu turno: el formulario

function tableText(s) {
  const g = s.game;
  const lines = [];
  lines.push(s.player.name + " " + g.score[PLAYER] + " - " + displayName(s.rocco) + " " + g.score[ROCCO] + " (a " + g.rules.targetPoints + ")");
  if (g.muestra !== null) lines.push("Muestra: " + card.name(g.muestra));
  for (let t = 0; t <= g.trick && t < 3; t++) {
    const mine = g.played[t][PLAYER];
    const his = g.played[t][ROCCO];
    if (mine === null && his === null) continue;
    lines.push("Baza " + (t + 1) + ": vos " + (mine === null ? "-" : card.name(mine)) + " / Rocco " + (his === null ? "-" : card.name(his)));
  }
  if (g.phase === "RESPONSE" && g.callerSeat === ROCCO) lines.push("§eRocco canto " + LABELS[g.pending === "TRUCO" ? [T.TRUCO, T.RETRUCO, T.VALE_CUATRO][g.pendLevel - 1] : g.envChain[g.envChain.length - 1] ?? T.FLOR] + "§r");
  return lines.join("\n");
}

function showTurn(s) {
  const g = s.game;
  const legal = g.legalActions(PLAYER);
  if (!legal.length || s.formOpen) return;
  s.formOpen = true;
  const form = new ActionFormData()
    .title({ translate: g.phase === "RESPONSE" ? "gui.rocco.truco.answer" : g.phase === "DISCARD" ? "gui.rocco.truco.discard" : "gui.rocco.truco.your_turn" })
    .body(tableText(s));
  for (const x of legal) {
    if (x.type === T.PLAY || x.type === T.DISCARD) {
      const id = g.hands[PLAYER][x.card];
      form.button(card.name(id), card.texture(id));
    } else form.button(LABELS[x.type]);
  }
  form.button({ translate: "gui.rocco.truco.leave" });
  form.show(s.player).then((res) => {
    s.formOpen = false;
    if (!sessions.has(s.rocco.id)) return;
    if (res.canceled || res.selection === undefined) {
      // Se cerro sin elegir (o lo cerro el juego): vuelve a aparecer en un ratito.
      s.waitUntil = now() + 40;
      return;
    }
    if (res.selection === legal.length) {
      confirmLeave(s);
      return;
    }
    const x = legal[res.selection];
    if (x && g.apply(PLAYER, x)) events(s);
  }).catch((e) => {
    s.formOpen = false;
    console.warn("[Rocco] truco: " + e);
  });
}

function confirmLeave(s) {
  const form = new ActionFormData()
    .title({ translate: "gui.rocco.truco.leave_q" })
    .button({ translate: "gui.rocco.truco.yes" })
    .button({ translate: "gui.rocco.truco.no" });
  s.formOpen = true;
  form.show(s.player).then((res) => {
    s.formOpen = false;
    if (!res.canceled && res.selection === 0) {
      tell(s, { translate: "gui.rocco.truco.left" });
      say(s.rocco, line("truco_abandon"), "sad");
      end(s);
    } else s.waitUntil = now() + 10;
  });
}

// ---------------------------------------------------------------- el mate entre manos

function startMate(s) {
  s.mateStage = "cebar";
  anim(s, "truco_cebar_frente", 0);
  safe("mate", () => s.rocco.setProperty("rocco:mate", true));
  sound(s.rocco, "rocco.mate_pour", 0.9);
}

function tickMate(s) {
  if (now() < s.waitUntil) return true;
  switch (s.mateStage) {
    case "cebar": {
      const left = safe("dar mate", () => inventoryOf(s.player)?.addItem(new ItemStack("rocco:mate", 1)));
      if (left) {
        s.mateStage = null;
        return false;
      }
      say(s.rocco, line("truco_mate_offer"), "happy");
      play(s.rocco, "truco_mate_wait_frente");
      s.mateStage = "wait";
      s.mateUntil = now() + 20 * 20;
      return true;
    }
    case "wait":
      if (s.drank || now() > s.mateUntil) {
        if (s.drank) say(s.rocco, line("truco_mate_good"), "happy");
        takeItem(inventoryOf(s.player), "rocco:mate", 64);
        s.drank = false;
        anim(s, "truco_mate_back_frente", 0);
        s.mateStage = "back";
      }
      return true;
    case "back":
      s.mateStage = null;
      return false;
  }
  return false;
}

export function trucoMateDrunk(player) {
  for (const s of sessions.values()) {
    if (s.player.id === player.id && s.mateStage === "wait") {
      s.drank = true;
      return true;
    }
  }
  return false;
}

// ---------------------------------------------------------------- cada tick

export function tickTruco(rocco) {
  const s = sessions.get(rocco.id);
  if (!s) return;
  const g = s.game;
  if (!s.player.isValid || s.player.dimension.id !== rocco.dimension.id || dist2(s.player.location, s.center) > 16 * 16) {
    say(rocco, line("truco_cancel"), "sad");
    end(s);
    return;
  }
  // Rocco se queda en su silla aunque lo empujen (o si se cayo).
  if (now() % 10 === 0) {
    const at = seatSpot(s);
    if (at && (Math.abs(rocco.location.y - at.y) > 0.1 || (rocco.location.x - at.x) ** 2 + (rocco.location.z - at.z) ** 2 > 0.04)) seatRocco(s);
  }
  if (now() < s.waitUntil) return;
  if (s.stage === "summon") {
    anim(s, "truco_settle");
    s.stage = "play";
    events(s);
    return;
  }
  if (s.stage === "over") {
    end(s);
    return;
  }
  if (s.stage === "between") {
    if (s.mateStage && tickMate(s)) return;
    if (s.opts.mate && !s.mateDone && g.handNo % 2 === 1) {
      s.mateDone = true;
      startMate(s);
      return;
    }
    s.mateDone = false;
    safe("mate", () => rocco.setProperty("rocco:mate", false));
    clearCards(s);
    g.startNextHand();
    s.stage = "play";
    events(s);
    return;
  }
  // Le toca a Rocco: piensa un ratito (con su cara) y juega.
  if (g.legalActions(ROCCO).length) {
    if (!s.thinking) {
      s.thinking = true;
      play(rocco, "truco_think");
      setFace(rocco, "think", 40);
      s.waitUntil = now() + 20 + Math.floor(Math.random() * 25);
      return;
    }
    s.thinking = false;
    const d = s.ai.decide(g, ROCCO);
    if (d && g.apply(ROCCO, d.action)) {
      s.mood = d.mood;
      events(s);
      if (!rt(rocco).animUntil || rt(rocco).animUntil < now()) play(rocco, "truco_idle");
    }
    return;
  }
  if (g.legalActions(PLAYER).length) {
    if (rt(rocco).animUntil < now()) play(rocco, g.hands[ROCCO].length ? "truco_idle" : "truco_idle_nocards");
    showTurn(s);
  }
}

export function end(s) {
  sessions.delete(s.rocco.id);
  const rocco = s.rocco;
  clearCards(s);
  if (s.player.isValid) takeItem(inventoryOf(s.player), "rocco:mate", 64);
  for (const e of [s.table, ...s.chairs]) {
    if (e && e.isValid) {
      puff(e.dimension, e.location);
      safe("sacar", () => e.getComponent("minecraft:rideable")?.ejectRiders());
      safe("sacar", () => e.remove());
    }
  }
  if (rocco.isValid) {
    const r = rt(rocco);
    r.scene = null;
    cutAnim(rocco);
    safe("levantar", () => {
      rocco.setProperty("rocco:bits", 0);
      rocco.setProperty("rocco:mate", false);
      rocco.triggerEvent("rocco:unseat");
      rocco.teleport({ x: s.center.x - s.f.x * 1.6, y: s.center.y, z: s.center.z - s.f.z * 1.6 });
    });
  }
}

const PIECES = ["rocco:truco_mesa", "rocco:truco_silla", "rocco:truco_carta"];

/** Mesas, sillas y cartas que quedaron de una partida cortada (se cerro el mundo a mitad). */
export function sweepOrphans() {
  const owned = new Set();
  for (const s of sessions.values()) for (const e of [s.table, ...s.chairs, ...s.cards]) if (e && e.isValid) owned.add(e.id);
  for (const id of ["minecraft:overworld", "minecraft:nether", "minecraft:the_end"]) {
    const dim = safe("dimension", () => world.getDimension(id));
    if (!dim) continue;
    for (const type of PIECES) {
      for (const e of safe("piezas", () => dim.getEntities({ type })) || []) {
        if (owned.has(e.id)) continue;
        safe("sacar", () => e.getComponent("minecraft:rideable")?.ejectRiders());
        safe("sacar", () => e.remove());
      }
    }
  }
}

export function endAllFor(player) {
  for (const s of [...sessions.values()]) if (s.player.id === player.id) end(s);
}

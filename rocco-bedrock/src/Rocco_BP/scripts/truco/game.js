// El motor del truco, igual al de Java (TrucoGame, Ranks, Rules, Action, RoccoAI): sin nada de
// Minecraft, se le pasan las jugadas con apply() y se lee lo que paso con drainEvents().
// Asientos 0..n-1 en orden de juego; equipos seat % 2 (con 3, el mano solo contra los otros dos).

export const SUITS = ["espada", "basto", "oro", "copa"];
export const SUIT_NAMES = ["espadas", "bastos", "oros", "copas"];
export const NUMBERS = [1, 2, 3, 4, 5, 6, 7, 10, 11, 12];

export const card = {
  number: (id) => NUMBERS[id % 10],
  suit: (id) => Math.floor(id / 10),
  faceValue: (id) => (NUMBERS[id % 10] <= 7 ? NUMBERS[id % 10] : 0),
  name(id) {
    const n = NUMBERS[id % 10];
    const word = n === 1 ? "ancho" : n === 10 ? "sota" : n === 11 ? "caballo" : n === 12 ? "rey" : String(n);
    return (n === 10 ? "la " : "el ") + word + " de " + SUIT_NAMES[Math.floor(id / 10)];
  },
  texture: (id) => "textures/entity/rocco/cartas/" + SUITS[Math.floor(id / 10)] + "_" + NUMBERS[id % 10],
};

export const T = {
  PLAY: "PLAY", DISCARD: "DISCARD", TRUCO: "TRUCO", RETRUCO: "RETRUCO", VALE_CUATRO: "VALE_CUATRO",
  ENVIDO: "ENVIDO", REAL_ENVIDO: "REAL_ENVIDO", FALTA_ENVIDO: "FALTA_ENVIDO", FLOR: "FLOR",
  CONTRAFLOR_AL_RESTO: "CONTRAFLOR_AL_RESTO", CON_FLOR_ME_ACHICO: "CON_FLOR_ME_ACHICO",
  QUIERO: "QUIERO", NO_QUIERO: "NO_QUIERO", MAZO: "MAZO",
};

export const LABELS = {
  PLAY: "Jugar carta", DISCARD: "Descartar", TRUCO: "¡Truco!", RETRUCO: "¡Quiero retruco!",
  VALE_CUATRO: "¡Quiero vale cuatro!", ENVIDO: "¡Envido!", REAL_ENVIDO: "¡Real envido!", FALTA_ENVIDO: "¡Falta envido!",
  FLOR: "¡Flor!", CONTRAFLOR_AL_RESTO: "¡Contraflor al resto!", CON_FLOR_ME_ACHICO: "Con flor me achico",
  QUIERO: "¡Quiero!", NO_QUIERO: "No quiero", MAZO: "Me voy al mazo",
};

export function act(type, c = -1) {
  return { type, card: c };
}

export function rules(variant = "uruguayo", target = 30) {
  return { variant, targetPoints: target, conFlor: true, mazoEnPrimeraDaDos: true, manoSoloDeATres: true };
}
const hasMuestra = (r) => r.variant === "uruguayo";

// ---------------------------------------------------------------- jerarquia

const PIEZAS = [2, 4, 5, 11, 10];
const PIEZA_VAL = [10, 9, 8, 7, 7];

export function piezaIndex(c, muestra, r) {
  if (!hasMuestra(r) || muestra === null || card.suit(c) !== card.suit(muestra)) return -1;
  const m = card.number(muestra);
  const n = card.number(c);
  for (let i = 0; i < PIEZAS.length; i++) {
    if (n === PIEZAS[i]) return i;
    if (n === 12 && m === PIEZAS[i]) return i;
  }
  return -1;
}
const isPieza = (c, m, r) => piezaIndex(c, m, r) >= 0;

export function strength(c, muestra, r) {
  const p = piezaIndex(c, muestra, r);
  if (p >= 0) return 100 - p;
  const n = card.number(c);
  const s = card.suit(c);
  if (n === 1 && s === 0) return 90;
  if (n === 1 && s === 1) return 89;
  if (n === 7 && s === 0) return 88;
  if (n === 7 && s === 2) return 87;
  return { 3: 80, 2: 79, 1: 78, 12: 77, 11: 76, 10: 75, 7: 74, 6: 73, 5: 72 }[n] ?? 71;
}

function tantoValue(c, m, r) {
  const p = piezaIndex(c, m, r);
  return p >= 0 ? PIEZA_VAL[p] : card.faceValue(c);
}

export function hasFlorHand(h, m, r) {
  if (!r.conFlor || h.length < 3) return false;
  let np = 0;
  for (const c of h) if (isPieza(c, m, r)) np++;
  if (np >= 2) return true;
  if (np === 1) {
    const rest = h.filter((c) => !isPieza(c, m, r));
    return card.suit(rest[0]) === card.suit(rest[1]);
  }
  return card.suit(h[0]) === card.suit(h[1]) && card.suit(h[1]) === card.suit(h[2]);
}

export function florValue(h, m, r) {
  let v = 20;
  for (const c of h) v += tantoValue(c, m, r);
  return v;
}

export function envido(h, m, r) {
  let piezas = 0;
  for (const c of h) if (isPieza(c, m, r)) piezas++;
  if (piezas >= 1) {
    let bestP = -1;
    let bestPi = -1;
    h.forEach((c, i) => {
      if (isPieza(c, m, r) && tantoValue(c, m, r) > bestP) {
        bestP = tantoValue(c, m, r);
        bestPi = i;
      }
    });
    let other = 0;
    h.forEach((c, i) => {
      if (i !== bestPi) other = Math.max(other, tantoValue(c, m, r) % 10);
    });
    return 20 + bestP + other;
  }
  let best = 0;
  for (let i = 0; i < h.length; i++) {
    best = Math.max(best, card.faceValue(h[i]));
    for (let j = i + 1; j < h.length; j++) {
      if (card.suit(h[i]) === card.suit(h[j])) best = Math.max(best, 20 + card.faceValue(h[i]) + card.faceValue(h[j]));
    }
  }
  return best;
}

function shuffle(list, rand) {
  for (let i = list.length - 1; i > 0; i--) {
    const j = Math.floor(rand() * (i + 1));
    [list[i], list[j]] = [list[j], list[i]];
  }
  return list;
}

// ---------------------------------------------------------------- la partida

export class Game {
  constructor(r, players, firstDealer = 0, rand = Math.random) {
    if (players < 2 || players > 8) throw new Error("de 2 a 8 jugadores");
    this.rules = { ...r };
    this.n = players;
    this.rand = rand;
    this.manoSolo = players === 3;
    this.score = new Array(players).fill(0);
    this.team = new Array(players).fill(0);
    this.hands = Array.from({ length: players }, () => []);
    this.played = [0, 1, 2].map(() => new Array(players).fill(null));
    this.playedFirst = new Array(players).fill(false);
    this.florSaid = new Array(players).fill(false);
    this.trickResult = [-2, -2, -2];
    this.trickSeat = [-1, -1, -1];
    this.envChain = [];
    this.events = [];
    this.dealer = ((firstDealer % players) + players) % players;
    this.handNo = 0;
    this.winnerTeam = -1;
    this.startHand();
  }

  next(seat) {
    return (seat + 1) % this.n;
  }
  teamScore(t) {
    for (let s = 0; s < this.n; s++) if (this.team[s] === t) return this.score[s];
    return 0;
  }
  maxScore() {
    return Math.max(...this.score);
  }
  falta() {
    return Math.max(1, this.rules.targetPoints - this.maxScore());
  }
  drainEvents() {
    const e = this.events;
    this.events = [];
    return e;
  }
  ev(kind, seat, team, value = 0, c = null, call = null, text = "") {
    this.events.push({ kind, seat, team, value, card: c, call, text });
  }
  respondingTeam() {
    if (this.pending === "NONE") return -1;
    if (this.pending === "CONTRAFLOR") return this.florTeam;
    return 1 - this.callerTeam;
  }
  mustRespond(seat) {
    if (this.phase !== "RESPONSE" || this.team[seat] !== this.respondingTeam()) return false;
    if (this.pending === "FLOR" || this.pending === "CONTRAFLOR") return this.hasFlor(seat);
    return true;
  }

  startHand() {
    this.handNo++;
    this.mano = this.next(this.dealer);
    const deck = shuffle([...Array(40).keys()], this.rand);
    for (const h of this.hands) h.length = 0;
    let k = 0;
    for (let r = 0; r < 3; r++) for (let i = 0; i < this.n; i++) this.hands[(this.mano + i) % this.n].push(deck[k++]);
    if (this.manoSolo) this.hands[this.mano].push(deck[k++]);
    this.muestra = hasMuestra(this.rules) ? deck[k] : null;
    for (let s = 0; s < this.n; s++) this.team[s] = this.manoSolo ? (s === this.mano ? 0 : 1) : s % 2;
    this.trick = 0;
    this.playedInTrick = 0;
    this.leader = this.mano;
    this.turn = this.mano;
    for (const row of this.played) row.fill(null);
    this.trickResult.fill(-2);
    this.trickSeat.fill(-1);
    this.playedFirst.fill(false);
    this.florSaid.fill(false);
    this.trucoLevel = 0;
    this.handValue = 1;
    this.trucoRight = -1;
    this.pending = "NONE";
    this.callerSeat = -1;
    this.callerTeam = -1;
    this.pendLevel = 0;
    this.resumeTruco = false;
    this.envChain.length = 0;
    this.envidoDone = false;
    this.florDone = !this.rules.conFlor;
    this.florTeam = -1;
    this.ev("NEW_HAND", this.dealer, -1, this.handNo);
    if (this.muestra !== null) this.ev("MUESTRA", -1, -1, 0, this.muestra);
    this.phase = this.manoSolo ? "DISCARD" : "PLAY";
    this.ev("TURN", this.turn, this.team[this.turn]);
  }

  startNextHand() {
    if (this.phase !== "HAND_OVER") throw new Error("la mano no termino");
    this.dealer = this.next(this.dealer);
    this.startHand();
  }

  fullHandFor(seat) {
    const h = [...this.hands[seat]];
    for (let t = 0; t < 3; t++) if (this.played[t][seat] !== null) h.push(this.played[t][seat]);
    return h;
  }
  hasFlor(seat) {
    return hasFlorHand(this.hands[seat].length === 3 ? this.hands[seat] : this.fullHandFor(seat), this.muestra, this.rules);
  }
  canFlor(seat) {
    return this.rules.conFlor && !this.florDone && this.trick === 0 && !this.florSaid[seat] && !this.playedFirst[seat] && this.hasFlor(seat);
  }
  anyFlorSaid() {
    return this.florSaid.some((b) => b);
  }
  canEnvido(seat) {
    return this.trick === 0 && !this.playedFirst[seat] && !this.envidoDone && this.envChain.length === 0 && this.trucoLevel === 0
      && !this.anyFlorSaid() && !(this.rules.conFlor && this.hasFlor(seat));
  }
  nextTruco(level) {
    return [T.TRUCO, T.RETRUCO, T.VALE_CUATRO][level] ?? null;
  }
  envidoRaises() {
    const r = [];
    if (this.envChain.length === 0) return [T.ENVIDO, T.REAL_ENVIDO, T.FALTA_ENVIDO];
    const last = this.envChain[this.envChain.length - 1];
    const ne = this.envChain.filter((x) => x === T.ENVIDO).length;
    if (last === T.ENVIDO && ne < 2) r.push(T.ENVIDO);
    if (last === T.ENVIDO) r.push(T.REAL_ENVIDO);
    if (last !== T.FALTA_ENVIDO) r.push(T.FALTA_ENVIDO);
    return r;
  }

  legalActions(seat) {
    const a = [];
    switch (this.phase) {
      case "DISCARD":
        if (seat === this.mano) this.hands[seat].forEach((_, i) => a.push(act(T.DISCARD, i)));
        break;
      case "RESPONSE":
        if (!this.mustRespond(seat)) break;
        switch (this.pending) {
          case "TRUCO": {
            a.push(act(T.QUIERO), act(T.NO_QUIERO));
            const up = this.nextTruco(this.pendLevel);
            if (up) a.push(act(up));
            if (this.pendLevel === 1 && this.canEnvido(seat)) for (const t of this.envidoRaises()) a.push(act(t));
            if (this.canFlor(seat)) a.push(act(T.FLOR));
            break;
          }
          case "ENVIDO":
            a.push(act(T.QUIERO), act(T.NO_QUIERO));
            for (const t of this.envidoRaises()) a.push(act(t));
            if (this.canFlor(seat)) a.push(act(T.FLOR));
            break;
          case "FLOR":
            a.push(act(T.FLOR), act(T.CONTRAFLOR_AL_RESTO), act(T.CON_FLOR_ME_ACHICO));
            break;
          case "CONTRAFLOR":
            a.push(act(T.QUIERO), act(T.NO_QUIERO));
            break;
        }
        break;
      case "PLAY":
        if (seat !== this.turn) break;
        this.hands[seat].forEach((_, i) => a.push(act(T.PLAY, i)));
        if (this.canFlor(seat)) a.push(act(T.FLOR));
        else if (this.canEnvido(seat)) for (const t of this.envidoRaises()) a.push(act(t));
        if (this.trucoLevel < 3 && (this.trucoRight === -1 || this.trucoRight === this.team[seat])) a.push(act(this.nextTruco(this.trucoLevel)));
        a.push(act(T.MAZO));
        break;
    }
    return a;
  }

  isLegal(seat, x) {
    return this.legalActions(seat).some((y) => y.type === x.type && y.card === x.card);
  }

  apply(seat, x) {
    if (!this.isLegal(seat, x)) return false;
    switch (x.type) {
      case T.DISCARD:
        this.hands[seat].splice(x.card, 1);
        this.ev("DISCARDED", seat, this.team[seat]);
        this.phase = "PLAY";
        this.ev("TURN", this.turn, this.team[this.turn]);
        break;
      case T.PLAY:
        this.playCard(seat, x.card);
        break;
      case T.TRUCO:
      case T.RETRUCO:
      case T.VALE_CUATRO:
        if (this.pending === "TRUCO") this.acceptTruco(seat, false);
        this.callTruco(seat, x.type);
        break;
      case T.ENVIDO:
      case T.REAL_ENVIDO:
      case T.FALTA_ENVIDO:
        if (this.pending === "TRUCO") {
          this.resumeTruco = true;
          this.rtSeat = this.callerSeat;
          this.rtTeam = this.callerTeam;
          this.rtLevel = this.pendLevel;
        }
        this.envChain.push(x.type);
        this.pending = "ENVIDO";
        this.callerSeat = seat;
        this.callerTeam = this.team[seat];
        this.phase = "RESPONSE";
        this.ev("CALL", seat, this.team[seat], 0, null, x.type, LABELS[x.type]);
        break;
      case T.FLOR:
        this.sayFlor(seat, false);
        break;
      case T.CONTRAFLOR_AL_RESTO:
        this.florSaid[seat] = true;
        this.pending = "CONTRAFLOR";
        this.callerSeat = seat;
        this.callerTeam = this.team[seat];
        this.phase = "RESPONSE";
        this.ev("CALL", seat, this.team[seat], 0, null, x.type, LABELS[x.type]);
        break;
      case T.CON_FLOR_ME_ACHICO:
        this.ev("ANSWER", seat, this.team[seat], 0, null, x.type, LABELS[x.type]);
        this.florDone = true;
        this.award(this.florTeam, 3 * this.florHolders(this.florTeam) + 1, "flor (se achicaron)");
        this.resume();
        break;
      case T.QUIERO:
        this.answerQuiero(seat);
        break;
      case T.NO_QUIERO:
        this.answerNoQuiero(seat);
        break;
      case T.MAZO: {
        const t = this.team[seat];
        let pts = this.handValue;
        if (this.rules.mazoEnPrimeraDaDos && this.trick === 0 && this.playedInTrick < this.n && this.envChain.length === 0
          && !this.envidoDone && !this.anyFlorSaid() && this.trucoLevel === 0) pts++;
        this.ev("MAZO", seat, t, pts, null, x.type, LABELS[x.type]);
        this.endHand(1 - t, pts, "se fueron al mazo");
        break;
      }
    }
    return true;
  }

  playCard(seat, idx) {
    if (this.canFlor(seat)) {
      this.ev("AUTO_FLOR", seat, this.team[seat], 0, null, T.FLOR, "¡Flor!");
      this.sayFlor(seat, true);
      return;
    }
    const c = this.hands[seat].splice(idx, 1)[0];
    this.played[this.trick][seat] = c;
    if (this.trick === 0) this.playedFirst[seat] = true;
    this.playedInTrick++;
    this.ev("CARD_PLAYED", seat, this.team[seat], this.trick, c, null, card.name(c));
    if (this.playedInTrick === this.n) {
      this.resolveTrick();
      return;
    }
    this.turn = this.next(seat);
    this.ev("TURN", this.turn, this.team[this.turn]);
  }

  resolveTrick() {
    let best = -1;
    for (let i = 0; i < this.n; i++) best = Math.max(best, strength(this.played[this.trick][i], this.muestra, this.rules));
    const teams = new Set();
    let firstWinner = -1;
    for (let i = 0; i < this.n; i++) {
      const s = (this.leader + i) % this.n;
      if (strength(this.played[this.trick][s], this.muestra, this.rules) === best) {
        teams.add(this.team[s]);
        if (firstWinner < 0) firstWinner = s;
      }
    }
    if (teams.size > 1) {
      this.trickResult[this.trick] = -1;
      this.trickSeat[this.trick] = this.leader;
    } else {
      this.trickResult[this.trick] = this.team[firstWinner];
      this.trickSeat[this.trick] = firstWinner;
    }
    this.ev("TRICK_WON", this.trickSeat[this.trick], this.trickResult[this.trick], this.trick, null, null, this.trickResult[this.trick] < 0 ? "parda" : "");
    const w = this.decided();
    if (w >= 0) {
      this.endHand(w, this.handValue, "truco");
      return;
    }
    this.trick++;
    this.playedInTrick = 0;
    this.leader = this.trickSeat[this.trick - 1];
    this.turn = this.leader;
    this.ev("TURN", this.turn, this.team[this.turn]);
  }

  decided() {
    const wins = [0, 0];
    let pardas = 0;
    let done = 0;
    let firstNonParda = -1;
    for (let t = 0; t < 3; t++) {
      if (this.trickResult[t] === -2) break;
      done++;
      if (this.trickResult[t] === -1) pardas++;
      else {
        wins[this.trickResult[t]]++;
        if (firstNonParda < 0) firstNonParda = this.trickResult[t];
      }
    }
    if (wins[0] >= 2) return 0;
    if (wins[1] >= 2) return 1;
    if (done >= 2 && pardas >= 1 && firstNonParda >= 0 && done === 2) return firstNonParda;
    if (done === 3) return firstNonParda >= 0 ? firstNonParda : this.team[this.mano];
    return -1;
  }

  callTruco(seat, t) {
    this.pending = "TRUCO";
    this.callerSeat = seat;
    this.callerTeam = this.team[seat];
    this.pendLevel = this.trucoLevel + 1;
    this.phase = "RESPONSE";
    this.ev("CALL", seat, this.team[seat], this.pendLevel, null, t, LABELS[t]);
  }
  acceptTruco(seat, say) {
    this.trucoLevel = this.pendLevel;
    this.handValue = this.trucoLevel + 1;
    this.trucoRight = this.team[seat];
    if (say) this.ev("ANSWER", seat, this.team[seat], this.trucoLevel, null, T.QUIERO, "¡Quiero!");
  }

  answerQuiero(seat) {
    switch (this.pending) {
      case "TRUCO":
        this.acceptTruco(seat, true);
        this.pending = "NONE";
        this.callerSeat = -1;
        this.phase = "PLAY";
        this.ev("TURN", this.turn, this.team[this.turn]);
        break;
      case "ENVIDO": {
        this.ev("ANSWER", seat, this.team[seat], 0, null, T.QUIERO, "¡Quiero!");
        const holder = this.firstUnsaidFlor();
        if (this.rules.conFlor && holder >= 0) {
          this.envChain.length = 0;
          this.envidoDone = true;
          this.ev("AUTO_FLOR", holder, this.team[holder], 0, null, T.FLOR, "¡Flor!");
          this.pending = "NONE";
          this.sayFlor(holder, true);
          return;
        }
        this.resolveEnvido();
        this.envidoDone = true;
        this.resume();
        break;
      }
      case "CONTRAFLOR": {
        this.ev("ANSWER", seat, this.team[seat], 0, null, T.QUIERO, "Con flor quiero");
        const w = this.compareFlor();
        this.florDone = true;
        this.award(w, this.falta(), "contraflor al resto");
        this.resume();
        break;
      }
    }
  }

  answerNoQuiero(seat) {
    this.ev("ANSWER", seat, this.team[seat], 0, null, T.NO_QUIERO, "No quiero");
    switch (this.pending) {
      case "TRUCO":
        this.endHand(this.callerTeam, this.handValue, "no quisieron el truco");
        break;
      case "ENVIDO": {
        const pts = this.envChain.length === 1 ? 1 : this.envidoPoints(this.envChain.slice(0, -1));
        this.envidoDone = true;
        this.award(this.callerTeam, pts, "envido no querido");
        this.resume();
        break;
      }
      case "CONTRAFLOR":
        this.florDone = true;
        this.award(this.callerTeam, 3 * (this.florHolders(0) + this.florHolders(1)), "contraflor no querida");
        this.resume();
        break;
    }
  }

  envidoPoints(chain) {
    if (chain.includes(T.FALTA_ENVIDO)) return this.falta();
    return chain.reduce((p, t) => p + (t === T.REAL_ENVIDO ? 3 : 2), 0);
  }
  resolveEnvido() {
    const pts = this.envidoPoints(this.envChain);
    let bestTeam = -1;
    let best = -1;
    for (let i = 0; i < this.n; i++) {
      const s = (this.mano + i) % this.n;
      const v = envido(this.fullHandFor(s), this.muestra, this.rules);
      if (bestTeam === this.team[s]) continue;
      if (v > best) {
        best = v;
        bestTeam = this.team[s];
        this.ev("TANTO_SAID", s, this.team[s], v, null, T.ENVIDO, String(v));
      } else this.ev("TANTO_SAID", s, this.team[s], -1, null, T.ENVIDO, "Son buenas");
    }
    this.award(bestTeam, pts, "envido");
  }

  florHolders(t) {
    let c = 0;
    for (let s = 0; s < this.n; s++) if (this.team[s] === t && this.hasFlor(s)) c++;
    return c;
  }
  firstUnsaidFlor() {
    if (!this.rules.conFlor || this.florDone) return -1;
    for (let i = 0; i < this.n; i++) {
      const s = (this.mano + i) % this.n;
      if (!this.florSaid[s] && this.hasFlor(s)) return s;
    }
    return -1;
  }
  oppUnsaidFlor(t) {
    for (let s = 0; s < this.n; s++) if (this.team[s] !== t && !this.florSaid[s] && this.hasFlor(s)) return true;
    return false;
  }
  sayFlor(seat, auto) {
    if (!auto) this.ev("CALL", seat, this.team[seat], 0, null, T.FLOR, "¡Flor!");
    this.florSaid[seat] = true;
    if (this.pending === "ENVIDO") {
      this.envChain.length = 0;
      this.envidoDone = true;
      this.pending = "NONE";
    }
    if (this.pending === "TRUCO" && !this.resumeTruco) {
      this.resumeTruco = true;
      this.rtSeat = this.callerSeat;
      this.rtTeam = this.callerTeam;
      this.rtLevel = this.pendLevel;
    }
    if (this.pending === "FLOR" && this.team[seat] !== this.florTeam) {
      const w = this.compareFlor();
      this.florDone = true;
      this.award(w, 3 * (this.florHolders(0) + this.florHolders(1)), "flor contra flor");
      this.resume();
      return;
    }
    if (this.florTeam < 0) this.florTeam = this.team[seat];
    if (this.oppUnsaidFlor(this.florTeam)) {
      this.pending = "FLOR";
      this.callerSeat = seat;
      this.callerTeam = this.florTeam;
      this.phase = "RESPONSE";
      return;
    }
    for (let s = 0; s < this.n; s++) {
      if (this.team[s] === this.florTeam && !this.florSaid[s] && this.hasFlor(s)) {
        this.florSaid[s] = true;
        this.ev("AUTO_FLOR", s, this.team[s], 0, null, T.FLOR, "¡Flor!");
      }
    }
    this.florDone = true;
    for (let s = 0; s < this.n; s++) {
      if (this.team[s] === this.florTeam && this.hasFlor(s)) {
        this.ev("TANTO_SAID", s, this.team[s], florValue(this.fullHandFor(s), this.muestra, this.rules), null, T.FLOR, "");
      }
    }
    this.award(this.florTeam, 3 * this.florHolders(this.florTeam), "flor");
    this.resume();
  }
  compareFlor() {
    let bestTeam = -1;
    let best = -1;
    for (let i = 0; i < this.n; i++) {
      const s = (this.mano + i) % this.n;
      if (!this.hasFlor(s)) continue;
      if (!this.florSaid[s]) {
        this.florSaid[s] = true;
        this.ev("AUTO_FLOR", s, this.team[s], 0, null, T.FLOR, "¡Flor!");
      }
      const v = florValue(this.fullHandFor(s), this.muestra, this.rules);
      this.ev("TANTO_SAID", s, this.team[s], v, null, T.FLOR, String(v));
      if (v > best) {
        best = v;
        bestTeam = this.team[s];
      }
    }
    return bestTeam;
  }

  resume() {
    if (this.phase === "GAME_OVER" || this.phase === "HAND_OVER") return;
    if (this.resumeTruco) {
      this.resumeTruco = false;
      this.pending = "TRUCO";
      this.callerSeat = this.rtSeat;
      this.callerTeam = this.rtTeam;
      this.pendLevel = this.rtLevel;
      this.phase = "RESPONSE";
      return;
    }
    this.pending = "NONE";
    this.callerSeat = -1;
    this.phase = "PLAY";
    this.ev("TURN", this.turn, this.team[this.turn]);
  }

  award(t, pts, why) {
    if (this.phase === "GAME_OVER" || t < 0 || pts <= 0) return;
    for (let s = 0; s < this.n; s++) if (this.team[s] === t) this.score[s] += pts;
    this.ev("POINTS", -1, t, pts, null, null, why);
    for (let s = 0; s < this.n; s++) {
      if (this.score[s] >= this.rules.targetPoints) {
        this.phase = "GAME_OVER";
        this.winnerTeam = this.team[s];
        this.ev("GAME_OVER", s, this.team[s]);
        return;
      }
    }
  }
  endHand(t, pts, why) {
    if (this.phase === "GAME_OVER") return;
    if (this.rules.conFlor && !this.florDone) {
      let any = false;
      for (let s = 0; s < this.n; s++) if (this.hasFlor(s)) any = true;
      if (any) {
        const both = this.florHolders(0) > 0 && this.florHolders(1) > 0;
        const w = this.compareFlor();
        this.florDone = true;
        this.award(
          both ? w : this.florHolders(0) > 0 ? 0 : 1,
          both ? 3 * (this.florHolders(0) + this.florHolders(1)) : 3 * Math.max(this.florHolders(0), this.florHolders(1)),
          "flor",
        );
        if (this.phase === "GAME_OVER") return;
      }
    }
    this.pending = "NONE";
    this.callerSeat = -1;
    this.award(t, pts, why);
    if (this.phase === "GAME_OVER") return;
    this.phase = "HAND_OVER";
    this.ev("HAND_OVER", -1, t, pts, null, null, why);
  }
}

// ---------------------------------------------------------------- como juega Rocco

// Menos simulaciones que en Java (30/140/320): los scripts de Bedrock corren bastante mas lento y
// una jugada no puede trabar el mundo. Con estas sigue jugando bien (lo mide tools/truco_sim).
export const LEVELS = {
  tranqui: { samples: 20, bluff: 0.02, mistake: 0.25 },
  normal: { samples: 60, bluff: 0.08, mistake: 0.04 },
  picaro: { samples: 120, bluff: 0.13, mistake: 0.0 },
};

const find = (legal, t) => legal.find((x) => x.type === t) ?? null;
const has = (legal, t) => find(legal, t) !== null;

export class RoccoAI {
  constructor(level = "normal", rand = Math.random) {
    this.level = LEVELS[level] ?? LEVELS.normal;
    this.rand = rand;
  }

  decide(g, seat) {
    const legal = g.legalActions(seat);
    if (legal.length === 0) return null;
    if (legal.length === 1) return { action: legal[0], mood: "" };
    if (this.rand() < this.level.mistake && g.phase === "PLAY") {
      const plays = legal.filter((x) => x.type === T.PLAY);
      if (plays.length) return { action: plays[Math.floor(this.rand() * plays.length)], mood: "" };
    }
    if (g.phase === "DISCARD") return { action: this.bestDiscard(g, seat), mood: "" };
    if (g.phase === "RESPONSE") return this.respond(g, seat, legal);
    if (g.phase === "PLAY") return this.onTurn(g, seat, legal);
    return null;
  }

  onTurn(g, seat, legal) {
    const flor = find(legal, T.FLOR);
    if (flor) return { action: flor, mood: "flor" };
    const me = g.team[seat];
    if (has(legal, T.ENVIDO)) {
      const pe = this.tantoProb(g, seat, false, false);
      const behind = g.teamScore(1 - me) - g.teamScore(me);
      if (pe >= 0.93 && (behind > 0 || g.falta() <= 8) && this.rand() < 0.6) return { action: act(T.FALTA_ENVIDO), mood: "envido_fuerte" };
      if (pe >= 0.8 && this.rand() < 0.55) return { action: act(T.REAL_ENVIDO), mood: "envido_fuerte" };
      if (pe >= 0.64 && this.rand() < 0.8) return { action: act(T.ENVIDO), mood: "envido" };
      if (pe <= 0.3 && this.rand() < this.level.bluff) return { action: act(T.ENVIDO), mood: "farol" };
    }
    const p = this.winProb(g, seat, null);
    const truco = legal.find((x) => x.type === T.TRUCO || x.type === T.RETRUCO || x.type === T.VALE_CUATRO);
    if (truco) {
      const need = 0.68 + 0.06 * g.trucoLevel;
      if (p >= need && this.rand() < 0.75) return { action: truco, mood: "truco_fuerte" };
      if (p < 0.3 && g.trick >= 1 && this.rand() < this.level.bluff) return { action: truco, mood: "farol" };
    }
    if (p < 0.04 && g.handValue >= 3 && has(legal, T.MAZO)) return { action: act(T.MAZO), mood: "mazo" };
    return { action: this.bestCard(g, seat, legal), mood: p > 0.6 ? "confiado" : p < 0.3 ? "preocupado" : "" };
  }

  respond(g, seat, legal) {
    const me = g.team[seat];
    switch (g.pending) {
      case "TRUCO": {
        const flor = find(legal, T.FLOR);
        if (flor) return { action: flor, mood: "flor" };
        if (has(legal, T.ENVIDO)) {
          const pe = this.tantoProb(g, seat, false, false);
          if (pe >= 0.66 && this.rand() < 0.85) return { action: act(pe >= 0.82 ? T.REAL_ENVIDO : T.ENVIDO), mood: "envido_primero" };
        }
        const p = this.winProb(g, seat, null);
        const loseIfNo = g.teamScore(1 - me) + g.handValue >= g.rules.targetPoints;
        const up = legal.find((x) => x.type === T.RETRUCO || x.type === T.VALE_CUATRO);
        if (up && (p >= 0.8 || (p < 0.3 && this.rand() < this.level.bluff * 0.6))) return { action: up, mood: p >= 0.8 ? "truco_fuerte" : "farol" };
        const accept = 0.42 + 0.05 * (g.pendLevel - 1);
        if (p >= accept || loseIfNo) return { action: act(T.QUIERO), mood: p >= 0.6 ? "confiado" : "" };
        return { action: act(T.NO_QUIERO), mood: "no_quiero" };
      }
      case "ENVIDO": {
        const flor = find(legal, T.FLOR);
        if (flor) return { action: flor, mood: "flor" };
        const chain = g.envChain;
        const falta = chain.includes(T.FALTA_ENVIDO);
        const pe = this.tantoProb(g, seat, false, true);
        const ifNo = chain.length === 1 ? 1 : 2;
        const loseIfNo = g.teamScore(1 - me) + ifNo >= g.rules.targetPoints;
        const stakeNow = falta ? g.falta() : chain.reduce((s, t) => s + (t === T.REAL_ENVIDO ? 3 : 2), 0);
        if (has(legal, T.FALTA_ENVIDO) && pe >= 0.88 && (g.teamScore(1 - me) > g.teamScore(me) || g.falta() <= 8) && this.rand() < 0.5)
          return { action: act(T.FALTA_ENVIDO), mood: "envido_fuerte" };
        if (has(legal, T.REAL_ENVIDO) && pe >= 0.8 && this.rand() < 0.5) return { action: act(T.REAL_ENVIDO), mood: "envido_fuerte" };
        if (has(legal, T.ENVIDO) && pe >= 0.75 && this.rand() < 0.5) return { action: act(T.ENVIDO), mood: "envido_fuerte" };
        const ev = pe * stakeNow - (1 - pe) * stakeNow;
        if (ev > -ifNo || loseIfNo) return { action: act(T.QUIERO), mood: "quiero_envido" };
        return { action: act(T.NO_QUIERO), mood: "no_quiero" };
      }
      case "FLOR": {
        const pf = this.tantoProb(g, seat, true, true);
        if (pf >= 0.85 && g.falta() <= 12 && this.rand() < 0.6) return { action: act(T.CONTRAFLOR_AL_RESTO), mood: "flor_fuerte" };
        if (pf <= 0.2 && this.rand() < 0.5) return { action: act(T.CON_FLOR_ME_ACHICO), mood: "achico" };
        return { action: act(T.FLOR), mood: "flor" };
      }
      case "CONTRAFLOR": {
        const pf = this.tantoProb(g, seat, true, true);
        const loseIfNo = g.teamScore(1 - me) + 6 >= g.rules.targetPoints;
        return { action: act(pf >= 0.55 || loseIfNo ? T.QUIERO : T.NO_QUIERO), mood: pf >= 0.55 ? "flor_fuerte" : "no_quiero" };
      }
    }
    return { action: legal[0], mood: "" };
  }

  bestCard(g, seat, legal) {
    let best = null;
    let bp = -1;
    let bStr = 999;
    const h = g.hands[seat];
    for (const x of legal) {
      if (x.type !== T.PLAY) continue;
      const p = this.winProb(g, seat, x.card);
      const str = strength(h[x.card], g.muestra, g.rules);
      if (p > bp + 0.02 || (Math.abs(p - bp) <= 0.02 && str < bStr)) {
        bp = p;
        best = x;
        bStr = str;
      }
    }
    return best;
  }

  bestDiscard(g, seat) {
    const h = g.hands[seat];
    let bi = 0;
    let bv = -1;
    for (let i = 0; i < h.length; i++) {
      const rest = h.filter((_, j) => j !== i);
      let v = 0;
      for (const c of rest) v += strength(c, g.muestra, g.rules) - 70;
      v += envido(rest, g.muestra, g.rules) * 0.4;
      if (hasFlorHand(rest, g.muestra, g.rules)) v += 25;
      if (v > bv) {
        bv = v;
        bi = i;
      }
    }
    return act(T.DISCARD, bi);
  }

  unknownPool(g, mine) {
    const known = new Set(mine);
    if (g.muestra !== null) known.add(g.muestra);
    for (let t = 0; t < 3; t++) for (let s = 0; s < g.n; s++) if (g.played[t][s] !== null) known.add(g.played[t][s]);
    const pool = [];
    for (let i = 0; i < 40; i++) if (!known.has(i)) pool.push(i);
    return pool;
  }

  tantoProb(g, seat, flor, rivalCalled) {
    const r = g.rules;
    const m = g.muestra;
    const mine = g.fullHandFor(seat);
    const pool = this.unknownPool(g, mine);
    const myV = flor ? florValue(mine, m, r) : envido(mine, m, r);
    let wins = 0;
    let tot = 0;
    const S = Math.max(200, this.level.samples * 2);
    for (let k = 0; k < S && tot < S; k++) {
      shuffle(pool, this.rand);
      let p = 0;
      let bestMine = myV;
      let bestMineSeat = seat;
      let bestOpp = -1;
      let bestOppSeat = -1;
      for (let s = 0; s < g.n; s++) {
        if (s === seat) continue;
        const h = [];
        for (let t = 0; t < 3; t++) if (g.played[t][s] !== null) h.push(g.played[t][s]);
        while (h.length < 3) h.push(pool[p++]);
        if (flor && !hasFlorHand(h, m, r)) continue;
        const v = flor ? florValue(h, m, r) : envido(h, m, r);
        if (g.team[s] === g.team[seat]) {
          if (v > bestMine) {
            bestMine = v;
            bestMineSeat = s;
          }
        } else if (v > bestOpp) {
          bestOpp = v;
          bestOppSeat = s;
        }
      }
      if (rivalCalled && bestOpp < (flor ? 26 : 24)) continue;
      tot++;
      if (bestOpp < 0 || bestMine > bestOpp) wins++;
      else if (bestMine === bestOpp && closerToMano(g, bestMineSeat, bestOppSeat)) wins++;
    }
    return tot === 0 ? 0.5 : wins / tot;
  }

  winProb(g, seat, playIdx) {
    const pool = this.unknownPool(g, g.hands[seat]);
    let wins = 0;
    const S = this.level.samples;
    for (let k = 0; k < S; k++) {
      shuffle(pool, this.rand);
      const hs = [];
      let p = 0;
      for (let s = 0; s < g.n; s++) {
        const h = [];
        if (s === seat) h.push(...g.hands[seat]);
        else for (let i = 0; i < g.hands[s].length; i++) h.push(pool[p++]);
        hs.push(h);
      }
      if (rollout(g, hs, seat, playIdx) === g.team[seat]) wins++;
    }
    return wins / S;
  }
}

function closerToMano(g, a, b) {
  const mod = (x) => ((x % g.n) + g.n) % g.n;
  return mod(a - g.mano) < mod(b - g.mano);
}

function rollout(g, hs, seat, playIdx) {
  const n = g.n;
  const m = g.muestra;
  const r = g.rules;
  const team = g.team;
  const res = [...g.trickResult];
  const winSeat = [0, 0, 0];
  const pl = g.played.map((row) => [...row]);
  let trick = g.trick;
  let turn = g.turn;
  let leader = turn;
  for (let i = 0; i < n; i++) {
    const s = (((turn - 1 - i) % n) + n) % n;
    if (pl[trick][s] !== null) leader = s;
    else break;
  }
  let first = true;
  for (;;) {
    let cnt = 0;
    for (let s = 0; s < n; s++) if (pl[trick][s] !== null) cnt++;
    while (cnt < n) {
      const s = turn;
      const h = hs[s];
      const idx = first && s === seat && playIdx !== null ? playIdx : choose(h, pl[trick], leader, s, team, n, m, r);
      first = false;
      pl[trick][s] = h.splice(idx, 1)[0];
      cnt++;
      turn = (s + 1) % n;
    }
    let best = -1;
    for (let s = 0; s < n; s++) best = Math.max(best, strength(pl[trick][s], m, r));
    const ts = new Set();
    let fw = -1;
    for (let i = 0; i < n; i++) {
      const s = (leader + i) % n;
      if (strength(pl[trick][s], m, r) === best) {
        ts.add(team[s]);
        if (fw < 0) fw = s;
      }
    }
    res[trick] = ts.size > 1 ? -1 : team[fw];
    winSeat[trick] = ts.size > 1 ? leader : fw;
    const w = decideRollout(res, trick, team, g.mano);
    if (w >= 0) return w;
    leader = winSeat[trick];
    turn = leader;
    trick++;
  }
}

function decideRollout(res, trick, team, mano) {
  const wins = [0, 0];
  let pardas = 0;
  let firstNP = -1;
  const done = trick + 1;
  for (let t = 0; t <= trick; t++) {
    if (res[t] === -1) pardas++;
    else {
      wins[res[t]]++;
      if (firstNP < 0) firstNP = res[t];
    }
  }
  if (wins[0] >= 2) return 0;
  if (wins[1] >= 2) return 1;
  if (done === 2 && pardas >= 1 && firstNP >= 0) return firstNP;
  if (done === 3) return firstNP >= 0 ? firstNP : team[mano];
  return -1;
}

function choose(h, cur, leader, s, team, n, m, r) {
  let bestStr = -1;
  let bestTeam = -1;
  for (let i = 0; i < n; i++) {
    const q = (leader + i) % n;
    if (cur[q] !== null) {
      const v = strength(cur[q], m, r);
      if (v > bestStr) {
        bestStr = v;
        bestTeam = team[q];
      }
    }
  }
  let low = 0;
  let high = 0;
  let cheapWin = -1;
  for (let i = 0; i < h.length; i++) {
    const v = strength(h[i], m, r);
    if (v < strength(h[low], m, r)) low = i;
    if (v > strength(h[high], m, r)) high = i;
    if (v > bestStr && (cheapWin < 0 || v < strength(h[cheapWin], m, r))) cheapWin = i;
  }
  if (bestStr < 0) {
    if (h.length === 3) {
      const idx = [0, 1, 2].sort((a, b) => strength(h[a], m, r) - strength(h[b], m, r));
      return idx[1];
    }
    return high;
  }
  if (bestTeam === team[s]) return low;
  return cheapWin >= 0 ? cheapWin : low;
}

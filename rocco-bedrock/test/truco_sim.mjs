import { Game, RoccoAI, rules, T } from "../src/Rocco_BP/scripts/truco/game.js";
let games = 0, hands = 0, errors = 0, kinds = {};
const t0 = Date.now();
for (const variant of ["uruguayo", "argentino"]) for (const players of [2, 4]) for (let i = 0; i < 60; i++) {
  const g = new Game(rules(variant, 30), players, i);
  const ais = Array.from({ length: players }, (_, s) => new RoccoAI(["tranqui", "normal", "picaro"][s % 3]));
  let steps = 0;
  while (g.phase !== "GAME_OVER" && steps < 5000) {
    steps++;
    if (g.phase === "HAND_OVER") { g.startNextHand(); hands++; continue; }
    let moved = false;
    for (let s = 0; s < players; s++) {
      if (g.legalActions(s).length === 0) continue;
      const d = ais[s].decide(g, s);
      if (!d || !g.apply(s, d.action)) { errors++; console.log("jugada ilegal", variant, players, g.phase, g.pending, JSON.stringify(d)); }
      moved = true; break;
    }
    for (const e of g.drainEvents()) kinds[e.kind] = (kinds[e.kind] || 0) + 1;
    if (!moved) { errors++; console.log("nadie puede jugar", g.phase, g.pending, g.callerTeam, g.florTeam); break; }
  }
  if (g.phase !== "GAME_OVER") { errors++; console.log("no termino", variant, players); }
  games++;
}
console.log({ games, hands, errors, ms: Date.now() - t0 }, kinds);

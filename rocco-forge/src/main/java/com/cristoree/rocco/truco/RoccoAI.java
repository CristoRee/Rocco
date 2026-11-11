package com.cristoree.rocco.truco;

import java.util.*;

/**
 * Cómo juega Rocco. Solo usa lo que él puede saber: sus cartas, las que ya se jugaron, la muestra, los puntos y lo
 * que se cantó. Para las cartas y el truco reparte al azar las cartas que no ve muchas veces y juega el resto de la
 * mano con una política simple (Monte Carlo): así estima la probabilidad de ganar la mano con cada jugada.
 * El envido y la flor van por tablas de tanto, ajustadas por el puntaje. Faroles de vez en cuando.
 */
public final class RoccoAI {
    public enum Level {
        TRANQUI(30, 0.02, 0.25), NORMAL(140, 0.08, 0.04), PICARO(320, 0.13, 0.0);
        final int samples; final double bluff, mistake;
        Level(int samples, double bluff, double mistake) { this.samples = samples; this.bluff = bluff; this.mistake = mistake; }
    }
    /** lo que decidió y por qué (para elegir una frase de Rocco) */
    public record Decision(Action action, String mood) {}

    private final Level level;
    private final Random rng;
    public RoccoAI(Level level, long seed) { this.level = level; this.rng = new Random(seed); }

    public Decision decide(TrucoGame g, int seat) {
        List<Action> legal = g.legalActions(seat);
        if (legal.isEmpty()) return null;
        if (legal.size() == 1) return new Decision(legal.get(0), "");
        if (rng.nextDouble() < level.mistake) {                  // modo tranqui: a veces juega cualquier carta
            List<Action> plays = legal.stream().filter(a -> a.type() == Action.Type.PLAY).toList();
            if (!plays.isEmpty() && g.phase() == TrucoGame.Phase.PLAY) return new Decision(plays.get(rng.nextInt(plays.size())), "");
        }
        return switch (g.phase()) {
            case DISCARD -> new Decision(bestDiscard(g, seat), "");
            case RESPONSE -> respond(g, seat, legal);
            case PLAY -> onTurn(g, seat, legal);
            default -> null;
        };
    }

    // ================================================================================================ su turno
    private Decision onTurn(TrucoGame g, int seat, List<Action> legal) {
        Action flor = find(legal, Action.Type.FLOR);
        if (flor != null) return new Decision(flor, "flor");          // la flor siempre se canta
        int me = g.team(seat);
        // envido antes de tirar la primera carta
        if (has(legal, Action.Type.ENVIDO)) {
            double pe = tantoProb(g, seat, false, false);
            int behind = g.teamScore(1 - me) - g.teamScore(me);
            if (pe >= 0.93 && (behind > 0 || g.falta() <= 8) && rng.nextDouble() < 0.6) return new Decision(Action.of(Action.Type.FALTA_ENVIDO), "envido_fuerte");
            if (pe >= 0.8 && rng.nextDouble() < 0.55) return new Decision(Action.of(Action.Type.REAL_ENVIDO), "envido_fuerte");
            if (pe >= 0.64 && rng.nextDouble() < 0.8) return new Decision(Action.of(Action.Type.ENVIDO), "envido");
            if (pe <= 0.3 && rng.nextDouble() < level.bluff) return new Decision(Action.of(Action.Type.ENVIDO), "farol");
        }
        double p = winProb(g, seat, null);
        Action truco = legal.stream().filter(a -> a.type() == Action.Type.TRUCO || a.type() == Action.Type.RETRUCO || a.type() == Action.Type.VALE_CUATRO).findFirst().orElse(null);
        if (truco != null) {
            double need = 0.68 + 0.06 * g.trucoLevel();
            if (p >= need && rng.nextDouble() < 0.75) return new Decision(truco, "truco_fuerte");
            if (p < 0.3 && g.trick() >= 1 && rng.nextDouble() < level.bluff) return new Decision(truco, "farol");
        }
        if (p < 0.04 && g.handValue() >= 3 && has(legal, Action.Type.MAZO)) return new Decision(Action.of(Action.Type.MAZO), "mazo");
        return new Decision(bestCard(g, seat, legal), p > 0.6 ? "confiado" : p < 0.3 ? "preocupado" : "");
    }

    // ================================================================================================ responder
    private Decision respond(TrucoGame g, int seat, List<Action> legal) {
        int me = g.team(seat);
        switch (g.pending()) {
            case TRUCO -> {
                Action flor = find(legal, Action.Type.FLOR);
                if (flor != null) return new Decision(flor, "flor");
                if (has(legal, Action.Type.ENVIDO)) {                     // el envido va primero
                    double pe = tantoProb(g, seat, false, false);
                    if (pe >= 0.66 && rng.nextDouble() < 0.85) return new Decision(Action.of(pe >= 0.82 ? Action.Type.REAL_ENVIDO : Action.Type.ENVIDO), "envido_primero");
                }
                double p = winProb(g, seat, null);
                // si no quiere y con eso pierde la partida, se juega igual
                int ifNo = g.handValue();
                boolean loseIfNo = g.teamScore(1 - me) + ifNo >= g.rules.targetPoints;
                Action up = legal.stream().filter(a -> a.type() == Action.Type.RETRUCO || a.type() == Action.Type.VALE_CUATRO).findFirst().orElse(null);
                if (up != null && (p >= 0.8 || (p < 0.3 && rng.nextDouble() < level.bluff * 0.6))) return new Decision(up, p >= 0.8 ? "truco_fuerte" : "farol");
                double accept = 0.42 + 0.05 * (g.pendingLevel() - 1);
                if (p >= accept || loseIfNo) return new Decision(Action.of(Action.Type.QUIERO), p >= 0.6 ? "confiado" : "");
                return new Decision(Action.of(Action.Type.NO_QUIERO), "no_quiero");
            }
            case ENVIDO -> {
                Action flor = find(legal, Action.Type.FLOR);
                if (flor != null) return new Decision(flor, "flor");
                List<Action.Type> chain = g.envidoChain();
                boolean falta = chain.contains(Action.Type.FALTA_ENVIDO);
                double pe = tantoProb(g, seat, false, true);
                int ifNo = chain.size() == 1 ? 1 : 2;
                boolean loseIfNo = g.teamScore(1 - me) + ifNo >= g.rules.targetPoints;
                int stakeNow = falta ? g.falta() : chain.stream().mapToInt(t -> t == Action.Type.REAL_ENVIDO ? 3 : 2).sum();
                if (has(legal, Action.Type.FALTA_ENVIDO) && pe >= 0.88 && (g.teamScore(1 - me) > g.teamScore(me) || g.falta() <= 8) && rng.nextDouble() < 0.5)
                    return new Decision(Action.of(Action.Type.FALTA_ENVIDO), "envido_fuerte");
                if (has(legal, Action.Type.REAL_ENVIDO) && pe >= 0.8 && rng.nextDouble() < 0.5) return new Decision(Action.of(Action.Type.REAL_ENVIDO), "envido_fuerte");
                if (has(legal, Action.Type.ENVIDO) && pe >= 0.75 && rng.nextDouble() < 0.5) return new Decision(Action.of(Action.Type.ENVIDO), "envido_fuerte");
                // vale la pena querer si lo que gana en promedio supera lo que cede no queriendo
                double ev = pe * stakeNow - (1 - pe) * stakeNow;
                if (ev > -ifNo || loseIfNo) return new Decision(Action.of(Action.Type.QUIERO), "quiero_envido");
                return new Decision(Action.of(Action.Type.NO_QUIERO), "no_quiero");
            }
            case FLOR -> {
                double pf = tantoProb(g, seat, true, true);
                if (pf >= 0.85 && g.falta() <= 12 && rng.nextDouble() < 0.6) return new Decision(Action.of(Action.Type.CONTRAFLOR_AL_RESTO), "flor_fuerte");
                if (pf <= 0.2 && rng.nextDouble() < 0.5) return new Decision(Action.of(Action.Type.CON_FLOR_ME_ACHICO), "achico");
                return new Decision(Action.of(Action.Type.FLOR), "flor");
            }
            case CONTRAFLOR -> {
                double pf = tantoProb(g, seat, true, true);
                boolean loseIfNo = g.teamScore(1 - me) + 6 >= g.rules.targetPoints;
                return new Decision(Action.of(pf >= 0.55 || loseIfNo ? Action.Type.QUIERO : Action.Type.NO_QUIERO), pf >= 0.55 ? "flor_fuerte" : "no_quiero");
            }
            default -> { return new Decision(legal.get(0), ""); }
        }
    }

    // ================================================================================================ cartas
    private Action bestCard(TrucoGame g, int seat, List<Action> legal) {
        Action best = null; double bp = -1; int bStr = 999;
        List<Card> h = g.hand(seat);
        for (Action a : legal) {
            if (a.type() != Action.Type.PLAY) continue;
            double p = winProb(g, seat, a.card());
            int str = Ranks.strength(h.get(a.card()), g.muestra(), g.rules);
            if (p > bp + 0.02 || (Math.abs(p - bp) <= 0.02 && str < bStr)) { bp = p; best = a; bStr = str; }
        }
        return best;
    }
    private Action bestDiscard(TrucoGame g, int seat) {
        List<Card> h = g.hand(seat);
        int bi = 0; double bv = -1;
        for (int i = 0; i < h.size(); i++) {
            List<Card> rest = new ArrayList<>(h); rest.remove(i);
            double v = 0;
            for (Card c : rest) v += Ranks.strength(c, g.muestra(), g.rules) - 70;
            v += Ranks.envido(rest, g.muestra(), g.rules) * 0.4;
            if (Ranks.hasFlor(rest, g.muestra(), g.rules)) v += 25;
            if (v > bv) { bv = v; bi = i; }
        }
        return Action.discard(bi);
    }

    // ================================================================================================ simulación
    /** probabilidad de que su equipo gane el envido (o la flor si flor=true), repartiendo al azar lo que no ve.
     *  Si el rival cantó, se supone que tiene tanto: se descartan las muestras en las que el rival tiene poco. */
    double tantoProb(TrucoGame g, int seat, boolean flor, boolean rivalCalled) {
        int n = g.n; Rules r = g.rules; Card m = g.muestra();
        List<Card> mine = g.fullHandFor(seat);
        Set<Integer> known = new HashSet<>();
        for (Card c : mine) known.add(c.id());
        if (m != null) known.add(m.id());
        for (int t = 0; t < 3; t++) for (int s = 0; s < n; s++) if (g.played(t, s) != null) known.add(g.played(t, s).id());
        List<Card> pool = new ArrayList<>();
        for (int i = 0; i < 40; i++) if (!known.contains(i)) pool.add(Card.byId(i));
        int myV = flor ? Ranks.florValue(mine, m, r) : Ranks.envido(mine, m, r);
        int wins = 0, tot = 0, S = Math.max(200, level.samples * 2);
        for (int k = 0; k < S && tot < S; k++) {
            Collections.shuffle(pool, rng);
            int p = 0, bestMine = myV, bestMineSeat = seat, bestOpp = -1, bestOppSeat = -1;
            for (int s = 0; s < n; s++) {
                if (s == seat) continue;
                List<Card> h = new ArrayList<>();
                for (int t = 0; t < 3; t++) if (g.played(t, s) != null) h.add(g.played(t, s));
                while (h.size() < 3) h.add(pool.get(p++));
                if (flor && !Ranks.hasFlor(h, m, r)) continue;
                int v = flor ? Ranks.florValue(h, m, r) : Ranks.envido(h, m, r);
                if (g.team(s) == g.team(seat)) { if (v > bestMine) { bestMine = v; bestMineSeat = s; } }
                else if (v > bestOpp) { bestOpp = v; bestOppSeat = s; }
            }
            if (rivalCalled && bestOpp < (flor ? 26 : 24)) continue;        // el que canta suele tener
            tot++;
            if (bestOpp < 0 || bestMine > bestOpp) wins++;
            else if (bestMine == bestOpp && closerToMano(g, bestMineSeat, bestOppSeat)) wins++;
        }
        return tot == 0 ? 0.5 : wins / (double) tot;
    }
    private static boolean closerToMano(TrucoGame g, int a, int b) {
        return Math.floorMod(a - g.mano(), g.n) < Math.floorMod(b - g.mano(), g.n);
    }

    /** probabilidad de que su equipo gane la mano (si playIdx >= 0: jugando esa carta ahora). */
    double winProb(TrucoGame g, int seat, Integer playIdx) {
        int n = g.n; Rules r = g.rules; Card m = g.muestra();
        Set<Integer> known = new HashSet<>();
        for (Card c : g.hand(seat)) known.add(c.id());
        if (m != null) known.add(m.id());
        for (int t = 0; t < 3; t++) for (int s = 0; s < n; s++) if (g.played(t, s) != null) known.add(g.played(t, s).id());
        List<Card> pool = new ArrayList<>();
        for (int i = 0; i < 40; i++) if (!known.contains(i)) pool.add(Card.byId(i));
        int wins = 0, S = level.samples;
        int[] team = new int[n]; for (int s = 0; s < n; s++) team[s] = g.team(s);
        for (int k = 0; k < S; k++) {
            Collections.shuffle(pool, rng);
            List<List<Card>> hs = new ArrayList<>(); int p = 0;
            for (int s = 0; s < n; s++) {
                List<Card> h = new ArrayList<>();
                if (s == seat) h.addAll(g.hand(seat));
                else for (int i = 0; i < g.cardsLeft(s); i++) h.add(pool.get(p++));
                hs.add(h);
            }
            if (rollout(g, hs, team, seat, playIdx, m, r) == team[seat]) wins++;
        }
        return wins / (double) S;
    }

    /** juega el resto de la mano con una política simple; devuelve el equipo que la gana */
    private int rollout(TrucoGame g, List<List<Card>> hs, int[] team, int seat, Integer playIdx, Card m, Rules r) {
        int n = g.n;
        int[] res = new int[3]; int[] winSeat = new int[3];
        Card[][] pl = new Card[3][n];
        for (int t = 0; t < 3; t++) { res[t] = g.trickResult(t); for (int s = 0; s < n; s++) pl[t][s] = g.played(t, s); }
        int trick = g.trick();
        int turn = g.turn();
        // quién empezó la baza actual
        int leader = turn;
        for (int i = 0; i < n; i++) { int s = Math.floorMod(turn - 1 - i, n); if (pl[trick][s] != null) leader = s; else break; }
        boolean first = true;
        while (true) {
            int cnt = 0; for (int s = 0; s < n; s++) if (pl[trick][s] != null) cnt++;
            while (cnt < n) {
                int s = turn;
                List<Card> h = hs.get(s);
                int idx = (first && s == seat && playIdx != null) ? playIdx : choose(h, pl[trick], leader, s, team, n, m, r);
                first = false;
                pl[trick][s] = h.remove(idx); cnt++; turn = (s + 1) % n;
            }
            int best = -1; for (int s = 0; s < n; s++) best = Math.max(best, Ranks.strength(pl[trick][s], m, r));
            Set<Integer> ts = new HashSet<>(); int fw = -1;
            for (int i = 0; i < n; i++) { int s = (leader + i) % n; if (Ranks.strength(pl[trick][s], m, r) == best) { ts.add(team[s]); if (fw < 0) fw = s; } }
            res[trick] = ts.size() > 1 ? -1 : team[fw]; winSeat[trick] = ts.size() > 1 ? leader : fw;
            int w = decide(res, trick, team, g.mano());
            if (w >= 0) return w;
            leader = winSeat[trick]; turn = leader; trick++;
        }
    }
    private static int decide(int[] res, int trick, int[] team, int mano) {
        int[] wins = new int[2]; int pardas = 0, firstNP = -1, done = trick + 1;
        for (int t = 0; t <= trick; t++) { if (res[t] == -1) pardas++; else { wins[res[t]]++; if (firstNP < 0) firstNP = res[t]; } }
        if (wins[0] >= 2) return 0; if (wins[1] >= 2) return 1;
        if (done == 2 && pardas >= 1 && firstNP >= 0) return firstNP;
        if (done == 3) return firstNP >= 0 ? firstNP : team[mano];
        return -1;
    }
    /** política de la simulación: ganarla barato si se puede, si el compañero ya la gana tirar la más baja */
    private static int choose(List<Card> h, Card[] cur, int leader, int s, int[] team, int n, Card m, Rules r) {
        int bestStr = -1, bestTeam = -1;
        for (int i = 0; i < n; i++) { int q = (leader + i) % n; if (cur[q] != null) { int v = Ranks.strength(cur[q], m, r); if (v > bestStr) { bestStr = v; bestTeam = team[q]; } } }
        int low = 0, high = 0, cheapWin = -1;
        for (int i = 0; i < h.size(); i++) {
            int v = Ranks.strength(h.get(i), m, r);
            if (v < Ranks.strength(h.get(low), m, r)) low = i;
            if (v > Ranks.strength(h.get(high), m, r)) high = i;
            if (v > bestStr && (cheapWin < 0 || v < Ranks.strength(h.get(cheapWin), m, r))) cheapWin = i;
        }
        if (bestStr < 0) return h.size() == 3 ? mid(h, m, r) : high;            // empieza la baza
        if (bestTeam == team[s]) return low;
        return cheapWin >= 0 ? cheapWin : low;
    }
    private static int mid(List<Card> h, Card m, Rules r) {
        Integer[] idx = {0, 1, 2};
        Arrays.sort(idx, Comparator.comparingInt(i -> Ranks.strength(h.get(i), m, r)));
        return idx[1];
    }

    private static boolean has(List<Action> l, Action.Type t) { return find(l, t) != null; }
    private static Action find(List<Action> l, Action.Type t) { for (Action a : l) if (a.type() == t) return a; return null; }
}

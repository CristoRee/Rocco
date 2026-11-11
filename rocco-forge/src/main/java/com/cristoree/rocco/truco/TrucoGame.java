package com.cristoree.rocco.truco;

import java.util.*;

/**
 * Motor de truco (uruguayo con muestra y piezas, o argentino), sin nada de Minecraft: el servidor tiene una instancia
 * por mesa, le pasa las jugadas con {@link #apply(int, Action)} y lee lo que pasó con {@link #drainEvents()}.
 *
 * Asientos 0..n-1 en orden de juego (contrarreloj, "a la derecha"). Equipos fijos seat % 2 con 2, 4, 6 u 8
 * jugadores; con 3 jugadores, en cada mano el mano juega solo contra los otros dos (recibe 4 cartas y descarta 1).
 * Los puntos se guardan por asiento (con equipos fijos los compañeros tienen siempre los mismos).
 */
public final class TrucoGame {
    public enum Phase { DISCARD, PLAY, RESPONSE, HAND_OVER, GAME_OVER }
    public enum Pending { NONE, TRUCO, ENVIDO, FLOR, CONTRAFLOR }

    public final Rules rules;
    public final int n;
    private final Random rng;
    private final boolean manoSolo;
    private final int[] score;
    private int dealer, mano, handNo;
    private final List<List<Card>> hands = new ArrayList<>();
    private Card muestra;
    private final int[] team;
    // bazas
    private int trick;
    private final Card[][] played;
    private final int[] trickResult = new int[3];       // equipo ganador, -1 parda, -2 sin jugar
    private final int[] trickSeat = new int[3];         // quién ganó la baza (o quien la empezó si fue parda)
    private int leader, turn, playedInTrick;
    private final boolean[] playedFirst;
    // truco
    private int trucoLevel;                              // 0 nada, 1 truco, 2 retruco, 3 vale cuatro (querido)
    private int handValue = 1;
    private int trucoRight = -1;                          // equipo que puede subir (-1 = cualquiera)
    // lo que espera respuesta
    private Pending pending = Pending.NONE;
    private int callerSeat = -1, callerTeam = -1, pendLevel;
    private boolean resumeTruco; private int rtSeat, rtTeam, rtLevel;   // truco que quedó esperando (envido va primero / flor)
    // envido
    private final List<Action.Type> envChain = new ArrayList<>();
    private boolean envidoDone;
    // flor
    private final boolean[] florSaid;
    private boolean florDone; private int florTeam = -1;
    private Phase phase;
    private int winnerTeam = -1;
    private final List<TrucoEvent> events = new ArrayList<>();

    public TrucoGame(Rules rules, int players, long seed, int firstDealer) {
        if (players < 2 || players > 8) throw new IllegalArgumentException("de 2 a 8 jugadores");
        if (players % 2 == 1 && !(players == 3 && rules.manoSoloDeATres))
            throw new IllegalArgumentException("con " + players + " jugadores no se arman equipos parejos");
        this.rules = rules.copy(); this.n = players; this.rng = new Random(seed);
        this.manoSolo = players == 3;
        this.score = new int[n]; this.team = new int[n];
        this.played = new Card[3][n]; this.playedFirst = new boolean[n]; this.florSaid = new boolean[n];
        this.dealer = Math.floorMod(firstDealer, n);
        for (int i = 0; i < n; i++) hands.add(new ArrayList<>());
        startHand();
    }

    // ================================================================================================ lectura
    public Phase phase() { return phase; }
    public int turn() { return turn; }
    public int mano() { return mano; }
    public int dealer() { return dealer; }
    public int handNumber() { return handNo; }
    public int trick() { return trick; }
    public Card muestra() { return muestra; }
    public int team(int seat) { return team[seat]; }
    public int score(int seat) { return score[seat]; }
    public int teamScore(int t) { for (int s = 0; s < n; s++) if (team[s] == t) return score[s]; return 0; }
    public int maxScore() { int m = 0; for (int s : score) m = Math.max(m, s); return m; }
    public List<Card> hand(int seat) { return Collections.unmodifiableList(hands.get(seat)); }
    public int cardsLeft(int seat) { return hands.get(seat).size(); }
    public Card played(int trickIdx, int seat) { return played[trickIdx][seat]; }
    public int trickResult(int t) { return trickResult[t]; }
    public int trucoLevel() { return trucoLevel; }
    public int handValue() { return handValue; }
    public Pending pending() { return pending; }
    public int callerSeat() { return callerSeat; }
    public int pendingLevel() { return pendLevel; }
    public List<Action.Type> envidoChain() { return Collections.unmodifiableList(envChain); }
    public boolean envidoDone() { return envidoDone; }
    public boolean florDone() { return florDone; }
    public boolean florSaid(int seat) { return florSaid[seat]; }
    public int winnerTeam() { return winnerTeam; }
    public boolean manoSolo() { return manoSolo; }
    public int falta() { return Math.max(1, rules.targetPoints - maxScore()); }
    public boolean hasPlayedFirst(int seat) { return playedFirst[seat]; }
    public int next(int seat) { return (seat + 1) % n; }
    public List<TrucoEvent> drainEvents() { List<TrucoEvent> e = new ArrayList<>(events); events.clear(); return e; }
    /** equipo que tiene que contestar ahora (-1 si nadie) */
    public int respondingTeam() {
        return switch (pending) {
            case NONE -> -1;
            case CONTRAFLOR -> florTeam;
            default -> 1 - callerTeam;
        };
    }
    public boolean mustRespond(int seat) {
        if (phase != Phase.RESPONSE || team[seat] != respondingTeam()) return false;
        if (pending == Pending.FLOR || pending == Pending.CONTRAFLOR) return hasFlor(seat);
        return true;
    }

    // ================================================================================================ la mano
    private void startHand() {
        handNo++;
        mano = next(dealer);
        List<Card> deck = Card.deck();
        Collections.shuffle(deck, rng);
        for (List<Card> h : hands) h.clear();
        int k = 0;
        for (int r = 0; r < 3; r++) for (int i = 0; i < n; i++) hands.get((mano + i) % n).add(deck.get(k++));
        if (manoSolo) hands.get(mano).add(deck.get(k++));
        muestra = rules.muestra() ? deck.get(k) : null;
        for (int s = 0; s < n; s++) team[s] = manoSolo ? (s == mano ? 0 : 1) : s % 2;
        trick = 0; playedInTrick = 0; leader = mano; turn = mano;
        for (Card[] row : played) Arrays.fill(row, null);
        Arrays.fill(trickResult, -2); Arrays.fill(trickSeat, -1); Arrays.fill(playedFirst, false); Arrays.fill(florSaid, false);
        trucoLevel = 0; handValue = 1; trucoRight = -1;
        pending = Pending.NONE; callerSeat = -1; callerTeam = -1; resumeTruco = false;
        envChain.clear(); envidoDone = false; florDone = !rules.conFlor; florTeam = -1;
        ev(TrucoEvent.Kind.NEW_HAND, dealer, -1, handNo, null, null, "");
        if (muestra != null) ev(TrucoEvent.Kind.MUESTRA, -1, -1, 0, muestra, null, "");
        for (int s = 0; s < n; s++) ev(TrucoEvent.Kind.DEALT, s, team[s], hands.get(s).size(), null, null, "");
        phase = manoSolo ? Phase.DISCARD : Phase.PLAY;
        ev(TrucoEvent.Kind.TURN, turn, team[turn], 0, null, null, "");
    }

    /** Llamar cuando terminaron las animaciones / el mate entre manos. */
    public void startNextHand() {
        if (phase != Phase.HAND_OVER) throw new IllegalStateException("la mano no terminó");
        dealer = next(dealer);
        startHand();
    }

    // ================================================================================================ jugadas legales
    public boolean hasFlor(int seat) { return Ranks.hasFlor(hands.get(seat).size() == 3 ? hands.get(seat) : fullHandFor(seat), muestra, rules); }
    /** mano original (cartas en la mano + las que jugó) para tanto y flor */
    public List<Card> fullHandFor(int seat) {
        List<Card> h = new ArrayList<>(hands.get(seat));
        for (int t = 0; t < 3; t++) if (played[t][seat] != null) h.add(played[t][seat]);
        return h;
    }
    private boolean canFlor(int seat) {
        return rules.conFlor && !florDone && trick == 0 && !florSaid[seat] && !playedFirst[seat] && hasFlor(seat);
    }
    private boolean anyFlorSaid() { for (boolean b : florSaid) if (b) return true; return false; }
    private boolean canEnvido(int seat) {
        return trick == 0 && !playedFirst[seat] && !envidoDone && envChain.isEmpty() && trucoLevel == 0 && !anyFlorSaid()
                && !(rules.conFlor && hasFlor(seat));
    }
    private Action.Type nextTruco(int level) {
        return switch (level) { case 0 -> Action.Type.TRUCO; case 1 -> Action.Type.RETRUCO; case 2 -> Action.Type.VALE_CUATRO; default -> null; };
    }
    private List<Action.Type> envidoRaises() {
        List<Action.Type> r = new ArrayList<>();
        if (envChain.isEmpty()) { r.add(Action.Type.ENVIDO); r.add(Action.Type.REAL_ENVIDO); r.add(Action.Type.FALTA_ENVIDO); return r; }
        Action.Type last = envChain.get(envChain.size() - 1);
        long ne = envChain.stream().filter(x -> x == Action.Type.ENVIDO).count();
        if (last == Action.Type.ENVIDO && ne < 2) r.add(Action.Type.ENVIDO);
        if (last == Action.Type.ENVIDO) r.add(Action.Type.REAL_ENVIDO);
        if (last != Action.Type.FALTA_ENVIDO) r.add(Action.Type.FALTA_ENVIDO);
        return r;
    }

    public List<Action> legalActions(int seat) {
        List<Action> a = new ArrayList<>();
        switch (phase) {
            case DISCARD -> { if (seat == mano) for (int i = 0; i < hands.get(seat).size(); i++) a.add(Action.discard(i)); }
            case RESPONSE -> {
                if (!mustRespond(seat)) break;
                switch (pending) {
                    case TRUCO -> {
                        a.add(Action.of(Action.Type.QUIERO)); a.add(Action.of(Action.Type.NO_QUIERO));
                        Action.Type up = nextTruco(pendLevel);
                        if (up != null) a.add(Action.of(up));
                        if (pendLevel == 1 && canEnvido(seat)) for (Action.Type t : envidoRaises()) a.add(Action.of(t));
                        if (canFlor(seat)) a.add(Action.of(Action.Type.FLOR));
                    }
                    case ENVIDO -> {
                        a.add(Action.of(Action.Type.QUIERO)); a.add(Action.of(Action.Type.NO_QUIERO));
                        for (Action.Type t : envidoRaises()) a.add(Action.of(t));
                        if (canFlor(seat)) a.add(Action.of(Action.Type.FLOR));
                    }
                    case FLOR -> {
                        a.add(Action.of(Action.Type.FLOR)); a.add(Action.of(Action.Type.CONTRAFLOR_AL_RESTO));
                        a.add(Action.of(Action.Type.CON_FLOR_ME_ACHICO));
                    }
                    case CONTRAFLOR -> { a.add(Action.of(Action.Type.QUIERO)); a.add(Action.of(Action.Type.NO_QUIERO)); }
                    default -> {}
                }
            }
            case PLAY -> {
                if (seat != turn) break;
                for (int i = 0; i < hands.get(seat).size(); i++) a.add(Action.play(i));
                if (canFlor(seat)) a.add(Action.of(Action.Type.FLOR));
                else if (canEnvido(seat)) for (Action.Type t : envidoRaises()) a.add(Action.of(t));
                if (trucoLevel < 3 && (trucoRight == -1 || trucoRight == team[seat])) a.add(Action.of(nextTruco(trucoLevel)));
                a.add(Action.of(Action.Type.MAZO));
            }
            default -> {}
        }
        return a;
    }
    public boolean isLegal(int seat, Action act) {
        for (Action x : legalActions(seat)) if (x.equals(act)) return true;
        return false;
    }

    // ================================================================================================ aplicar
    /** Aplica la jugada; devuelve false (y no cambia nada) si no es legal. */
    public boolean apply(int seat, Action act) {
        if (!isLegal(seat, act)) return false;
        switch (act.type()) {
            case DISCARD -> {
                hands.get(seat).remove(act.card());
                ev(TrucoEvent.Kind.DISCARDED, seat, team[seat], 0, null, null, "");
                phase = Phase.PLAY;
                ev(TrucoEvent.Kind.TURN, turn, team[turn], 0, null, null, "");
            }
            case PLAY -> play(seat, act.card());
            case TRUCO, RETRUCO, VALE_CUATRO -> {
                if (pending == Pending.TRUCO) acceptTruco(seat, false);
                callTruco(seat, act.type());
            }
            case ENVIDO, REAL_ENVIDO, FALTA_ENVIDO -> {
                if (pending == Pending.TRUCO) {               // "el envido va primero": el truco queda esperando
                    resumeTruco = true; rtSeat = callerSeat; rtTeam = callerTeam; rtLevel = pendLevel;
                }
                envChain.add(act.type());
                pending = Pending.ENVIDO; callerSeat = seat; callerTeam = team[seat]; phase = Phase.RESPONSE;
                ev(TrucoEvent.Kind.CALL, seat, team[seat], 0, null, act.type(), act.label());
            }
            case FLOR -> sayFlor(seat, false);
            case CONTRAFLOR_AL_RESTO -> {
                florSaid[seat] = true;
                pending = Pending.CONTRAFLOR; callerSeat = seat; callerTeam = team[seat]; phase = Phase.RESPONSE;
                ev(TrucoEvent.Kind.CALL, seat, team[seat], 0, null, act.type(), act.label());
            }
            case CON_FLOR_ME_ACHICO -> {
                ev(TrucoEvent.Kind.ANSWER, seat, team[seat], 0, null, act.type(), act.label());
                florDone = true;
                award(florTeam, 3 * florHolders(florTeam) + 1, "flor (se achicaron)");
                resume();
            }
            case QUIERO -> answerQuiero(seat);
            case NO_QUIERO -> answerNoQuiero(seat);
            case MAZO -> {
                int t = team[seat], opp = 1 - t;
                int pts = handValue;
                if (rules.mazoEnPrimeraDaDos && trick == 0 && playedInTrick < n && envChain.isEmpty() && !envidoDone && !anyFlorSaid() && trucoLevel == 0) pts++;
                ev(TrucoEvent.Kind.MAZO, seat, t, pts, null, act.type(), act.label());
                endHand(opp, pts, "se fueron al mazo");
            }
        }
        return true;
    }

    private void play(int seat, int idx) {
        if (canFlor(seat)) {                              // la flor es obligatoria: se canta sola antes de jugar
            ev(TrucoEvent.Kind.AUTO_FLOR, seat, team[seat], 0, null, Action.Type.FLOR, "¡Flor!");
            sayFlor(seat, true);
            return;
        }
        Card c = hands.get(seat).remove(idx);
        played[trick][seat] = c;
        if (trick == 0) playedFirst[seat] = true;
        playedInTrick++;
        ev(TrucoEvent.Kind.CARD_PLAYED, seat, team[seat], trick, c, null, c.nombre());
        if (playedInTrick == n) { resolveTrick(); return; }
        turn = next(seat);
        ev(TrucoEvent.Kind.TURN, turn, team[turn], 0, null, null, "");
    }

    private void resolveTrick() {
        int best = -1;
        for (int i = 0; i < n; i++) {
            Card c = played[trick][i];
            best = Math.max(best, Ranks.strength(c, muestra, rules));
        }
        Set<Integer> teams = new HashSet<>(); int firstWinner = -1;
        for (int i = 0; i < n; i++) {
            int s = (leader + i) % n;                        // en orden de juego: el primero que tiró la más alta
            if (Ranks.strength(played[trick][s], muestra, rules) == best) { teams.add(team[s]); if (firstWinner < 0) firstWinner = s; }
        }
        if (teams.size() > 1) { trickResult[trick] = -1; trickSeat[trick] = leader; }
        else { trickResult[trick] = team[firstWinner]; trickSeat[trick] = firstWinner; }
        ev(TrucoEvent.Kind.TRICK_WON, trickSeat[trick], trickResult[trick], trick, null, null, trickResult[trick] < 0 ? "parda" : "");
        int w = decided();
        if (w >= 0) { endHand(w, handValue, "truco"); return; }
        trick++; playedInTrick = 0;
        leader = trickSeat[trick - 1];
        turn = leader;
        ev(TrucoEvent.Kind.TURN, turn, team[turn], 0, null, null, "");
    }

    /** equipo que ya ganó la mano por bazas, o -1 */
    private int decided() {
        int[] wins = new int[2]; int pardas = 0, done = 0, firstNonParda = -1;
        for (int t = 0; t < 3; t++) {
            if (trickResult[t] == -2) break;
            done++;
            if (trickResult[t] == -1) pardas++;
            else { wins[trickResult[t]]++; if (firstNonParda < 0) firstNonParda = trickResult[t]; }
        }
        if (wins[0] >= 2) return 0;
        if (wins[1] >= 2) return 1;
        if (done >= 2 && pardas >= 1 && firstNonParda >= 0) {
            // con una parda gana quien ganó la otra (si la parda fue primera: el de la segunda; si no: el de la primera)
            if (done == 2) return firstNonParda;
        }
        if (done == 3) {
            if (firstNonParda >= 0) return firstNonParda;            // 1-1 con parda: la primera que no fue parda
            return team[mano];                                        // tres pardas: gana el mano
        }
        return -1;
    }

    private void callTruco(int seat, Action.Type t) {
        pending = Pending.TRUCO; callerSeat = seat; callerTeam = team[seat]; pendLevel = trucoLevel + 1;
        phase = Phase.RESPONSE;
        ev(TrucoEvent.Kind.CALL, seat, team[seat], pendLevel, null, t, Action.of(t).label());
    }
    private void acceptTruco(int seat, boolean say) {
        trucoLevel = pendLevel; handValue = trucoLevel + 1; trucoRight = team[seat];
        if (say) ev(TrucoEvent.Kind.ANSWER, seat, team[seat], trucoLevel, null, Action.Type.QUIERO, "¡Quiero!");
    }

    private void answerQuiero(int seat) {
        switch (pending) {
            case TRUCO -> {
                acceptTruco(seat, true);
                pending = Pending.NONE; callerSeat = -1; phase = Phase.PLAY;
                ev(TrucoEvent.Kind.TURN, turn, team[turn], 0, null, null, "");
            }
            case ENVIDO -> {
                ev(TrucoEvent.Kind.ANSWER, seat, team[seat], 0, null, Action.Type.QUIERO, "¡Quiero!");
                int holder = firstUnsaidFlor();
                if (rules.conFlor && holder >= 0) {           // la flor anula el envido
                    envChain.clear(); envidoDone = true;
                    ev(TrucoEvent.Kind.AUTO_FLOR, holder, team[holder], 0, null, Action.Type.FLOR, "¡Flor!");
                    pending = Pending.NONE;
                    sayFlor(holder, true);
                    return;
                }
                resolveEnvido();
                envidoDone = true;
                resume();
            }
            case CONTRAFLOR -> {
                ev(TrucoEvent.Kind.ANSWER, seat, team[seat], 0, null, Action.Type.QUIERO, "Con flor quiero");
                int w = compareFlor();
                florDone = true;
                award(w, falta(), "contraflor al resto");
                resume();
            }
            default -> {}
        }
    }
    private void answerNoQuiero(int seat) {
        ev(TrucoEvent.Kind.ANSWER, seat, team[seat], 0, null, Action.Type.NO_QUIERO, "No quiero");
        switch (pending) {
            case TRUCO -> endHand(callerTeam, handValue, "no quiso el truco");
            case ENVIDO -> {
                int pts = envChain.size() == 1 ? 1 : envidoPoints(envChain.subList(0, envChain.size() - 1));
                envidoDone = true;
                award(callerTeam, pts, "envido no querido");
                resume();
            }
            case CONTRAFLOR -> {
                florDone = true;
                award(callerTeam, 3 * (florHolders(0) + florHolders(1)), "contraflor no querida");
                resume();
            }
            default -> {}
        }
    }

    private int envidoPoints(List<Action.Type> chain) {
        if (chain.contains(Action.Type.FALTA_ENVIDO)) return falta();
        int p = 0;
        for (Action.Type t : chain) p += t == Action.Type.REAL_ENVIDO ? 3 : 2;
        return p;
    }
    private void resolveEnvido() {
        int pts = envidoPoints(envChain);
        int bestTeam = -1, best = -1;
        for (int i = 0; i < n; i++) {                      // se dicen los tantos desde el mano
            int s = (mano + i) % n;
            int v = Ranks.envido(fullHandFor(s), muestra, rules);
            if (bestTeam == team[s]) continue;               // su equipo ya va ganando: no hace falta decir nada
            if (v > best) { best = v; bestTeam = team[s]; ev(TrucoEvent.Kind.TANTO_SAID, s, team[s], v, null, Action.Type.ENVIDO, String.valueOf(v)); }
            else ev(TrucoEvent.Kind.TANTO_SAID, s, team[s], -1, null, Action.Type.ENVIDO, "Son buenas");
        }
        award(bestTeam, pts, "envido");
    }

    // ---------------------------------------------------------------------------------------------- flor
    private int florHolders(int t) { int c = 0; for (int s = 0; s < n; s++) if (team[s] == t && hasFlor(s)) c++; return c; }
    private int firstUnsaidFlor() {
        if (!rules.conFlor || florDone) return -1;
        for (int i = 0; i < n; i++) { int s = (mano + i) % n; if (!florSaid[s] && hasFlor(s)) return s; }
        return -1;
    }
    private boolean oppUnsaidFlor(int t) {
        for (int s = 0; s < n; s++) if (team[s] != t && !florSaid[s] && hasFlor(s)) return true;
        return false;
    }
    private void sayFlor(int seat, boolean auto) {
        if (!auto) ev(TrucoEvent.Kind.CALL, seat, team[seat], 0, null, Action.Type.FLOR, "¡Flor!");
        florSaid[seat] = true;
        if (pending == Pending.ENVIDO) {                   // la flor anula el envido
            envChain.clear(); envidoDone = true;
            pending = resumeTruco ? Pending.NONE : Pending.NONE;
        }
        if (pending == Pending.TRUCO && !resumeTruco) {   // contestó al truco con flor: el truco sigue esperando
            resumeTruco = true; rtSeat = callerSeat; rtTeam = callerTeam; rtLevel = pendLevel;
        }
        if (pending == Pending.FLOR && team[seat] != florTeam) {    // "con flor quiero": se comparan, 3 por cada flor
            int w = compareFlor();
            florDone = true;
            award(w, 3 * (florHolders(0) + florHolders(1)), "flor contra flor");
            resume();
            return;
        }
        if (florTeam < 0) florTeam = team[seat];
        if (oppUnsaidFlor(florTeam)) {
            pending = Pending.FLOR; callerSeat = seat; callerTeam = florTeam; phase = Phase.RESPONSE;
            return;
        }
        for (int s = 0; s < n; s++) if (team[s] == florTeam && !florSaid[s] && hasFlor(s)) {
            florSaid[s] = true; ev(TrucoEvent.Kind.AUTO_FLOR, s, team[s], 0, null, Action.Type.FLOR, "¡Flor!");
        }
        florDone = true;
        for (int s = 0; s < n; s++) if (team[s] == florTeam && hasFlor(s))
            ev(TrucoEvent.Kind.TANTO_SAID, s, team[s], Ranks.florValue(fullHandFor(s), muestra, rules), null, Action.Type.FLOR, "");
        award(florTeam, 3 * florHolders(florTeam), "flor");
        resume();
    }
    /** compara las flores (se cantan solas las que faltaban); empate: la más cercana al mano */
    private int compareFlor() {
        int bestTeam = -1, best = -1;
        for (int i = 0; i < n; i++) {
            int s = (mano + i) % n;
            if (!hasFlor(s)) continue;
            if (!florSaid[s]) { florSaid[s] = true; ev(TrucoEvent.Kind.AUTO_FLOR, s, team[s], 0, null, Action.Type.FLOR, "¡Flor!"); }
            int v = Ranks.florValue(fullHandFor(s), muestra, rules);
            ev(TrucoEvent.Kind.TANTO_SAID, s, team[s], v, null, Action.Type.FLOR, String.valueOf(v));
            if (v > best) { best = v; bestTeam = team[s]; }
        }
        return bestTeam;
    }

    /** después de resolver un envido o una flor: vuelve el truco que esperaba, o sigue el juego */
    private void resume() {
        if (phase == Phase.GAME_OVER || phase == Phase.HAND_OVER) return;
        if (resumeTruco) {
            resumeTruco = false;
            pending = Pending.TRUCO; callerSeat = rtSeat; callerTeam = rtTeam; pendLevel = rtLevel; phase = Phase.RESPONSE;
            return;
        }
        pending = Pending.NONE; callerSeat = -1; phase = Phase.PLAY;
        ev(TrucoEvent.Kind.TURN, turn, team[turn], 0, null, null, "");
    }

    // ---------------------------------------------------------------------------------------------- puntos
    private void award(int t, int pts, String why) {
        if (phase == Phase.GAME_OVER || t < 0 || pts <= 0) return;
        for (int s = 0; s < n; s++) if (team[s] == t) score[s] += pts;
        ev(TrucoEvent.Kind.POINTS, -1, t, pts, null, null, why);
        for (int s = 0; s < n; s++) if (score[s] >= rules.targetPoints) {
            phase = Phase.GAME_OVER; winnerTeam = team[s];
            ev(TrucoEvent.Kind.GAME_OVER, s, team[s], 0, null, null, "");
            return;
        }
    }
    private void endHand(int t, int pts, String why) {
        if (phase == Phase.GAME_OVER) return;
        // flores que nunca se cantaron (la mano terminó antes): se cuentan igual
        if (rules.conFlor && !florDone) {
            boolean any = false;
            for (int s = 0; s < n; s++) if (hasFlor(s)) any = true;
            if (any) {
                boolean both = florHolders(0) > 0 && florHolders(1) > 0;
                int w = compareFlor();
                florDone = true;
                award(both ? w : (florHolders(0) > 0 ? 0 : 1), both ? 3 * (florHolders(0) + florHolders(1)) : 3 * Math.max(florHolders(0), florHolders(1)), "flor");
                if (phase == Phase.GAME_OVER) return;
            }
        }
        pending = Pending.NONE; callerSeat = -1;
        award(t, pts, why);
        if (phase == Phase.GAME_OVER) return;
        phase = Phase.HAND_OVER;
        ev(TrucoEvent.Kind.HAND_OVER, -1, t, pts, null, null, why);
    }

    private void ev(TrucoEvent.Kind k, int seat, int t, int v, Card c, Action.Type call, String text) {
        events.add(TrucoEvent.of(k, seat, t, v, c, call, text));
    }
}

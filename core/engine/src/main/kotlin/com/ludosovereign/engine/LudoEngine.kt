package com.ludosovereign.engine

import com.ludosovereign.engine.BoardTopology.Companion.BASE

/**
 * Pure reducer for Ludo. `(GameState, action) -> GameState`, optionally appending [GameEvent]s to a
 * sink. No Android, no randomness, no clocks: the same inputs always produce the same output, which
 * is what lets the AI search, undo, auto-save and replay all share this one implementation.
 *
 * ## Capture & Fortress invariants (Custom Rule #1)
 * When a token lands on main-track cell `c`, for every *other* team `T` present on `c`:
 * - if `c` is a safe tile: nothing happens (tokens coexist);
 * - else if `T` has exactly one token on `c` (a singleton): it is captured and sent to base;
 * - else (`T` has 2+ tokens on `c`, a Fortress): no capture, no bonus; the mover coexists.
 * Teammates never capture each other and stack freely (a team stack is a Fortress too).
 * Home-stretch cells are private and never interact.
 */
object LudoEngine {

    fun newGame(config: MatchConfig): GameState {
        val tpp = config.rules.tokensPerPlayer
        val tokens = IntArray(config.tokenCount) { id ->
            if (id % tpp < config.rules.tokensStartingOnBoard) 0 else BASE
        }
        return GameState(config, tokens, current = 0, phase = Phase.AWAITING_ROLL)
    }

    fun reduce(state: GameState, action: GameAction, events: MutableList<GameEvent>? = null): GameState =
        when (action) {
            is GameAction.Roll -> roll(state, action.value, events)
            is GameAction.MoveToken -> move(state, action.tokenId, events)
        }

    /** Target progress for [tokenId] with [dice], or null if the move is illegal. */
    fun targetProgress(state: GameState, tokenId: Int, dice: Int): Int? {
        val p = state.tokens[tokenId]
        val home = state.topology.homeProgress
        return when {
            p == BASE -> if (state.rules.releasesOn(dice)) 0 else null
            p >= home -> null
            p + dice <= home -> p + dice
            else -> null
        }
    }

    /**
     * Legal token ids for [dice]. Tokens of the same owner on the same progress are equivalent, so
     * only the lowest id of each stack is returned (keeps AI branching factor minimal).
     */
    fun legalMoves(state: GameState, dice: Int = state.dice): IntArray {
        if (state.phase == Phase.GAME_OVER || dice !in 1..6) return EMPTY
        val tpp = state.tokensPerPlayer
        val out = IntArray(tpp * state.config.playerCount)
        var n = 0
        for (player in state.controllablePlayers()) {
            val base = player * tpp
            for (id in base until base + tpp) {
                if (targetProgress(state, id, dice) == null) continue
                var dup = false
                for (k in 0 until n) {
                    val other = out[k]
                    if (other / tpp == player && state.tokens[other] == state.tokens[id]) { dup = true; break }
                }
                if (!dup) out[n++] = id
            }
        }
        return out.copyOf(n)
    }

    /** True if [tokenId] (or an equivalent stacked twin) may be moved with the pending dice. */
    fun isLegal(state: GameState, tokenId: Int): Boolean {
        if (state.phase != Phase.AWAITING_MOVE) return false
        val owner = state.ownerOf(tokenId)
        if (owner !in state.controllablePlayers()) return false
        return targetProgress(state, tokenId, state.dice) != null
    }

    fun roll(state: GameState, value: Int, events: MutableList<GameEvent>? = null): GameState {
        require(state.phase == Phase.AWAITING_ROLL) { "roll in phase ${state.phase}" }
        require(value in 1..6) { "dice value $value" }
        events?.add(GameEvent.DiceRolled(state.current, value))
        val rolled = state.rollCount + 1

        if (value == 6 && state.rules.tripleSixForfeit && state.consecutiveSixes >= 2) {
            events?.add(GameEvent.TripleSixForfeit(state.current))
            return passTurn(state.copy(rollCount = rolled), events)
        }
        val sixes = if (value == 6) state.consecutiveSixes + 1 else 0
        val next = state.copy(phase = Phase.AWAITING_MOVE, dice = value, consecutiveSixes = sixes, rollCount = rolled)
        if (legalMoves(next, value).isEmpty()) {
            events?.add(GameEvent.NoLegalMoves(state.current, value))
            return if (value == 6 && state.rules.bonusOnSix) {
                events?.add(GameEvent.BonusTurn(state.current, BonusReason.SIX))
                next.copy(phase = Phase.AWAITING_ROLL, dice = 0)
            } else passTurn(next, events)
        }
        return next
    }

    fun move(state: GameState, tokenId: Int, events: MutableList<GameEvent>? = null): GameState {
        require(isLegal(state, tokenId)) { "illegal move token=$tokenId in $state" }
        val topo = state.topology
        val rules = state.rules
        val dice = state.dice
        val owner = state.ownerOf(tokenId)
        val seat = state.seatOf(owner)
        val team = state.teamOf(owner)
        val from = state.tokens[tokenId]
        val to = targetProgress(state, tokenId, dice)!!

        val tokens = state.tokens.copyOf()
        val fromCell = topo.absoluteCell(seat, from)

        // Leaving a 2-stack breaks the Fortress and exposes the remaining singleton.
        if (events != null && rules.fortressEnabled && fromCell >= 0 && state.teamCountAt(fromCell, team) == 2) {
            events.add(GameEvent.FortressBroken(fromCell, team))
        }

        tokens[tokenId] = to
        if (from == BASE) events?.add(GameEvent.TokenReleased(tokenId, topo.startCell(seat)))
        else events?.add(GameEvent.TokenMoved(tokenId, from, to))
        if (events != null && !topo.isInHomeStretch(from) && topo.isInHomeStretch(to)) {
            events.add(GameEvent.TokenEnteredHomeStretch(tokenId))
        }

        var captured = false
        val toCell = topo.absoluteCell(seat, to)
        if (toCell >= 0) {
            captured = resolveLanding(state, tokens, tokenId, toCell, team, events)
        }

        var reachedHome = false
        var finishOrder = state.finishOrder
        if (to == topo.homeProgress) {
            reachedHome = true
            events?.add(GameEvent.TokenReachedHome(tokenId))
            if (!finishOrder.contains(owner) && countHome(tokens, owner, state) >= rules.tokensToWin) {
                finishOrder = finishOrder + owner
                events?.add(GameEvent.PlayerFinished(owner, finishOrder.size))
            }
        }

        var next = state.copy(tokens = tokens, phase = Phase.AWAITING_ROLL, dice = 0, finishOrder = finishOrder)

        val winner = winningTeam(next)
        if (winner >= 0) {
            val order = completeOrder(next)
            events?.add(GameEvent.GameOver(winner, order.toList()))
            return next.copy(phase = Phase.GAME_OVER, winnerTeam = winner, finishOrder = order)
        }

        val roller = state.current
        val rollerOut = !rules.teamUp && next.hasFinished(roller)
        val reason = when {
            rollerOut -> null
            captured && rules.bonusOnCapture -> BonusReason.CAPTURE
            reachedHome && rules.bonusOnHome -> BonusReason.HOME
            dice == 6 && rules.bonusOnSix -> BonusReason.SIX
            else -> null
        }
        if (reason != null) {
            events?.add(GameEvent.BonusTurn(roller, reason))
            // A capture/home bonus earned on a non-six roll starts a fresh six-chain.
            if (dice != 6) next = next.copy(consecutiveSixes = 0)
            return next
        }
        return passTurn(next, events)
    }

    /** Applies captures/standoffs on [cell]. Returns true if anything was captured. */
    private fun resolveLanding(
        state: GameState,
        tokens: IntArray,
        mover: Int,
        cell: Int,
        moverTeam: Int,
        events: MutableList<GameEvent>?,
    ): Boolean {
        val topo = state.topology
        val tpp = state.tokensPerPlayer
        val teams = state.config.teams
        val seats = state.config.seats
        val fortress = state.rules.fortressEnabled
        val safe = topo.isSafeCell(cell)

        // Count tokens per team on the cell after the move.
        val teamCounts = IntArray(state.config.playerCount)
        for (id in tokens.indices) {
            val owner = id / tpp
            if (topo.absoluteCell(seats[owner], tokens[id]) == cell) teamCounts[teams[owner]]++
        }

        if (events != null && fortress && teamCounts[moverTeam] == 2) {
            events.add(GameEvent.FortressFormed(cell, moverTeam, 2))
        }

        var captured = false
        for (enemyTeam in teamCounts.indices) {
            val count = teamCounts[enemyTeam]
            if (enemyTeam == moverTeam || count == 0) continue
            if (safe) continue
            if (fortress && count >= 2) {
                events?.add(GameEvent.FortressStandoff(mover, cell, enemyTeam))
                continue
            }
            for (id in tokens.indices) {
                val owner = id / tpp
                if (teams[owner] == enemyTeam && topo.absoluteCell(seats[owner], tokens[id]) == cell) {
                    events?.add(GameEvent.Captured(id, mover, cell, tokens[id]))
                    tokens[id] = BASE
                    captured = true
                }
            }
        }
        return captured
    }

    private fun countHome(tokens: IntArray, player: Int, state: GameState): Int {
        val tpp = state.tokensPerPlayer
        val home = state.topology.homeProgress
        var n = 0
        for (id in player * tpp until (player + 1) * tpp) if (tokens[id] == home) n++
        return n
    }

    /** Winning team id, or -1 while the match continues. */
    fun winningTeam(state: GameState): Int {
        val cfg = state.config
        if (cfg.rules.teamUp) {
            for (team in cfg.teams.distinct()) {
                val members = cfg.teams.indices.filter { cfg.teams[it] == team }
                if (members.all { state.hasFinished(it) }) return team
            }
            return -1
        }
        if (state.finishOrder.isEmpty()) return -1
        if (!cfg.rules.playForAllPlacements) return cfg.teams[state.finishOrder[0]]
        val remaining = cfg.playerCount - state.finishOrder.size
        return if (remaining <= 1) cfg.teams[state.finishOrder[0]] else -1
    }

    /** Appends unfinished players ranked by total progress so the final standings are complete. */
    private fun completeOrder(state: GameState): IntArray {
        val rest = (0 until state.config.playerCount)
            .filter { !state.finishOrder.contains(it) }
            .sortedByDescending { p -> totalProgress(state, p) }
        return state.finishOrder + rest.toIntArray()
    }

    fun totalProgress(state: GameState, player: Int): Int {
        val tpp = state.tokensPerPlayer
        var sum = 0
        for (id in player * tpp until (player + 1) * tpp) sum += state.tokens[id].coerceAtLeast(0)
        return sum
    }

    fun nextPlayer(state: GameState, from: Int = state.current): Int {
        val n = state.config.playerCount
        var p = from
        repeat(n) {
            p = (p + 1) % n
            // In Team Up a finished player keeps rolling for teammates; otherwise finished players sit out.
            if (state.rules.teamUp || !state.hasFinished(p)) return p
        }
        return from
    }

    private fun passTurn(state: GameState, events: MutableList<GameEvent>?): GameState {
        val to = nextPlayer(state)
        events?.add(GameEvent.TurnPassed(state.current, to))
        return state.copy(current = to, phase = Phase.AWAITING_ROLL, dice = 0, consecutiveSixes = 0)
    }

    private val EMPTY = IntArray(0)
}

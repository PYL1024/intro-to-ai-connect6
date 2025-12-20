package stud.g08;

import core.board.PieceColor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class PNSearcher {
    private static final int INF = 1_000_000_000;
    private static final int ATTACK_LIMIT = 8;
    private static final int DEFENSE_LIMIT = 6;
    private static final int THREAT_SCORE_THRESHOLD = 80_000;

    private final V1Board board;
    private final MoveGenerator moveGenerator;
    private final ThreatEvaluator threatEvaluator;
    private final Deque<V1Board.BoardState> stateStack = new ArrayDeque<>();

    private PieceColor attacker;
    private PieceColor defender;
    private long timeLimitMs;
    private long startTime;
    private int nodeBudget;
    private int nodes;

    // Zobrist & PN 缓存
    private long zobristKey = 0L;
    private static final long[][] ZOBRIST = new long[19 * 19][3];
    private final Map<Long, Result> pnCache = new HashMap<>();

    static {
        Random rnd = new Random(20251220L ^ 0xABCDEF987654321L);
        for (int i = 0; i < 19 * 19; i++) {
            for (int c = 0; c < 3; c++) {
                ZOBRIST[i][c] = rnd.nextLong();
            }
        }
    }

    public PNSearcher(V1Board board, MoveGenerator moveGenerator) {
        this.board = board;
        this.moveGenerator = moveGenerator;
        this.threatEvaluator = moveGenerator.getThreatEvaluator();
    }

    public int[] search(PieceColor attacker, PieceColor defender, long timeLimitMs, int nodeBudget) {
        this.attacker = attacker;
        this.defender = defender;
        this.timeLimitMs = timeLimitMs;
        this.nodeBudget = nodeBudget;
        this.nodes = 0;
        this.startTime = System.currentTimeMillis();
        this.pnCache.clear();
        this.zobristKey = computeInitialKey();

        if (attacker == null || defender == null) return null;
        if (!board.isLinesBuilt()) board.buildLines(attacker);

        Result r = pnOr(0);
        return r.bestMove;
    }

    private Result pnOr(int depth) {
        nodes++;
        if (timeout() || nodes > nodeBudget) return Result.fail();

        Result memo = pnCache.get(zobristKey);
        if (memo != null && memo.isExact()) return memo;

        if (hasWin(attacker)) return storeExact(new Result(0, INF, null));
        if (hasWin(defender)) return storeExact(new Result(INF, 0, null));

        List<ScoredMove> moves = generateThreatMoves(attacker, ATTACK_LIMIT);
        if (moves.isEmpty()) return storeExact(new Result(INF, 0, null));

        int bestProof = INF;
        int bestDisproofSum = 0;
        int[] best = null;
        for (ScoredMove sm : moves) {
            int[] mv = sm.move;
            apply(mv, attacker);
            Result child = pnAnd(depth + 1);
            revert(mv, attacker);
            if (child.proof < bestProof) {
                bestProof = child.proof;
                best = mv;
            }
            bestDisproofSum += child.disproof;
            if (bestProof == 0) break;
        }
        Result res = new Result(bestProof, bestDisproofSum, best);
        pnCache.put(zobristKey, res);
        return res;
    }

    private Result pnAnd(int depth) {
        nodes++;
        if (timeout() || nodes > nodeBudget) return Result.fail();

        Result memo = pnCache.get(zobristKey);
        if (memo != null && memo.isExact()) return memo;

        if (hasWin(attacker)) return storeExact(new Result(0, INF, null));
        if (hasWin(defender)) return storeExact(new Result(INF, 0, null));

        List<int[]> moves = generateDefenseMoves(defender, DEFENSE_LIMIT);
        if (moves.isEmpty()) return storeExact(new Result(0, INF, null));

        int proofSum = 0;
        int bestDisproof = INF;
        for (int[] mv : moves) {
            apply(mv, defender);
            Result child = pnOr(depth + 1);
            revert(mv, defender);
            proofSum += child.proof;
            if (child.disproof < bestDisproof) {
                bestDisproof = child.disproof;
            }
            if (proofSum >= INF) break;
        }
        Result res = new Result(proofSum, bestDisproof, null);
        pnCache.put(zobristKey, res);
        return res;
    }

    private boolean timeout() {
        return System.currentTimeMillis() - startTime > timeLimitMs;
    }

    private boolean hasWin(PieceColor color) {
        return moveGenerator.findWinningMove(color) != null;
    }

    private List<ScoredMove> generateThreatMoves(PieceColor color, int cap) {
        List<int[]> base = moveGenerator.generateMoves(color, defender);
        int limit = Math.min(cap, base.size());
        List<ScoredMove> res = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            int[] mv = base.get(i);
            int sc = evaluateThreatMove(mv, color);
            if (sc >= THREAT_SCORE_THRESHOLD) {
                res.add(new ScoredMove(mv, sc));
            }
        }
        res.sort((a, b) -> Integer.compare(b.score, a.score));
        return res;
    }

    private List<int[]> generateDefenseMoves(PieceColor color, int cap) {
        List<int[]> res = new ArrayList<>();
        List<Integer> def = threatEvaluator.getDefensePositions(attacker);
        if (!def.isEmpty()) {
            if (def.size() >= 2) res.add(new int[]{def.get(0), def.get(1)});
            else {
                int p = def.get(0);
                int partner = pickPartner(p, color);
                if (partner >= 0) res.add(new int[]{p, partner});
            }
        }
        List<int[]> base = moveGenerator.generateMoves(color, attacker);
        int limit = Math.min(cap, base.size());
        for (int i = 0; i < limit; i++) {
            int[] mv = base.get(i);
            if (!contains(res, mv)) res.add(mv);
        }
        return res;
    }

    private int evaluateThreatMove(int[] move, PieceColor color) {
        apply(move, color);
        int score;
        if (board.checkWinAt(move[0], color) || board.checkWinAt(move[1], color)) {
            score = 1_000_000;
        } else {
            int r1 = V1Board.toRow(move[0]);
            int c1 = V1Board.toCol(move[0]);
            int r2 = V1Board.toRow(move[1]);
            int c2 = V1Board.toCol(move[1]);
            ThreatAnalyzer.ThreatResult t1 = ThreatAnalyzer.analyzeThreat(board, r1, c1, color);
            ThreatAnalyzer.ThreatResult t2 = ThreatAnalyzer.analyzeThreat(board, r2, c2, color);
            score = t1.getTotalScore() + t2.getTotalScore();
            if (t1.hasFourThree() || t2.hasFourThree()) score += 180_000;
            if (t1.hasDoubleFour() || t2.hasDoubleFour()) score += 140_000;
            if (t1.hasDoubleThree() || t2.hasDoubleThree()) score += 90_000;
            if (t1.isCriticalThreat() || t2.isCriticalThreat()) score += 250_000;
            if (isSameLine(move[0], move[1])) score += 50_000;
        }
        revert(move, color);
        return score;
    }

    private boolean isSameLine(int p1, int p2) {
        for (Line ln : board.getValidLinesAt(p1)) {
            if (ln.containsPosition(p2)) return true;
        }
        return false;
    }

    private int pickPartner(int fixed, PieceColor color) {
        int best = -1, bestScore = Integer.MIN_VALUE;
        for (int pos : board.getCandidatePositions()) {
            if (pos == fixed || !board.isEmpty(pos)) continue;
            int sc = board.evaluateMoveIncrement(pos, color);
            if (sc > bestScore) { bestScore = sc; best = pos; }
        }
        return best;
    }

    private void apply(int[] mv, PieceColor color) {
        stateStack.push(board.captureStateForSearch());
        int p1 = mv[0], p2 = mv[1];
        board.setColor(p1, color);
        board.setColor(p2, color);
        xorZobrist(p1, color);
        xorZobrist(p2, color);
        board.updateCandidatesAfterMove(p1);
        board.updateCandidatesAfterMove(p2);
        board.updateLinesAfterMove(p1, color, attacker);
        board.updateLinesAfterMove(p2, color, attacker);
    }

    private void revert(int[] mv, PieceColor color) {
        int p1 = mv[0], p2 = mv[1];
        board.undoLinesAfterMove(p1, color, attacker);
        board.undoLinesAfterMove(p2, color, attacker);
        board.clearColor(p1);
        board.clearColor(p2);
        xorZobrist(p1, color);
        xorZobrist(p2, color);
        V1Board.BoardState st = stateStack.pop();
        board.restoreStateForSearch(st);
    }

    private boolean contains(List<int[]> list, int[] mv) {
        for (int[] m : list) {
            if ((m[0] == mv[0] && m[1] == mv[1]) || (m[0] == mv[1] && m[1] == mv[0])) return true;
        }
        return false;
    }

    private long computeInitialKey() {
        long key = 0L;
        for (int pos = 0; pos < 19 * 19; pos++) {
            PieceColor c = board.get(pos);
            int idx = (c == PieceColor.BLACK) ? 1 : (c == PieceColor.WHITE ? 2 : 0);
            key ^= ZOBRIST[pos][idx];
        }
        return key;
    }

    private void xorZobrist(int pos, PieceColor color) {
        int idx = (color == PieceColor.BLACK) ? 1 : (color == PieceColor.WHITE ? 2 : 0);
        zobristKey ^= ZOBRIST[pos][idx];
    }

    private Result storeExact(Result r) {
        pnCache.put(zobristKey, r);
        return r;
    }

    private static class ScoredMove {
        final int[] move;
        final int score;
        ScoredMove(int[] m, int s) { this.move = m; this.score = s; }
    }

    private static class Result {
        final int proof;
        final int disproof;
        final int[] bestMove;
        Result(int p, int d, int[] m) { this.proof = p; this.disproof = d; this.bestMove = m; }
        static Result fail() { return new Result(INF, INF, null); }
        boolean isExact() { return proof == 0 || disproof == 0 || (proof >= INF) || (disproof >= INF); }
    }
}

package stud.g09;

import core.board.PieceColor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 威胁空间搜索器（Threat Based Search, TBS）
 * 通过枚举强制性威胁着法与必然防守着法，搜索出可保证胜利的威胁链。
 * 搜索假设为我方主动进攻、对方被迫防守，适合在发现强威胁时快速截获必胜序列。
 */
public class ThreatSpaceSearcher {

    /** 默认最大搜索层（按我方着法层计数） */
    private static final int DEFAULT_MAX_DEPTH = 6;

    /** 默认时间预算（毫秒） */
    private static final long DEFAULT_TIME_LIMIT_MS = 700L;

    /** 进攻候选着法上限，控制分支度 */
    private static final int ATTACK_MOVE_LIMIT = 10;

    /** 防守候选着法上限 */
    private static final int DEFENSE_MOVE_LIMIT = 6;

    /** 无穷大评分（用于立即获胜判断） */
    private static final int WIN_SCORE = 1_000_000;

    private final V1Board board;
    private final MoveGenerator moveGenerator;
    private final ThreatEvaluator threatEvaluator;
    private final TranspositionTable tt;

    private PieceColor attackerColor;
    private PieceColor defenderColor;
    private int maxDepth;
    private long timeLimitMs;
    private long startTime;

    private int[] bestWinningMove;

    private final Deque<V1Board.BoardState> stateStack = new ArrayDeque<>();

    private long zobristKey = 0L;

    public ThreatSpaceSearcher(V1Board board, MoveGenerator moveGenerator, TranspositionTable tt) {
        this.board = board;
        this.moveGenerator = moveGenerator;
        this.threatEvaluator = new ThreatEvaluator(board);
        this.tt = tt;
    }

    /** 入口：在给定时间与深度限制下搜索强制胜利着法。 */
    public int[] searchForcingWin(PieceColor attacker, PieceColor defender, int maxDepth, long timeLimitMs) {
        this.attackerColor = attacker;
        this.defenderColor = defender;
        this.maxDepth = Math.max(2, maxDepth);
        this.timeLimitMs = timeLimitMs > 0 ? timeLimitMs : DEFAULT_TIME_LIMIT_MS;
        this.startTime = System.currentTimeMillis();
        this.bestWinningMove = null;
        this.stateStack.clear();
        this.zobristKey = tt.computeInitialKey(board);

        if (attackerColor == null || defenderColor == null) {
            return null;
        }

        if (!board.isLinesBuilt()) {
            board.buildLines(attackerColor);
        }

        List<ScoredMove> threatMoves = collectThreateningMoves();
        for (ScoredMove sm : threatMoves) {
            if (isTimeout()) {
                break;
            }
            int[] move = sm.move;

            applyMove(move, attackerColor);

            if (board.checkWinAt(move[0], attackerColor) || board.checkWinAt(move[1], attackerColor)) {
                bestWinningMove = move;
                revertMove(move, attackerColor);
                break;
            }

            boolean forcedWin = searchDefenses(maxDepth - 1);
            revertMove(move, attackerColor);

            if (forcedWin) {
                bestWinningMove = move;
                break;
            }
        }

        return bestWinningMove;
    }

    /** 带默认参数的简化接口。 */
    public int[] searchForcingWin(PieceColor attacker, PieceColor defender) {
        return searchForcingWin(attacker, defender, DEFAULT_MAX_DEPTH, DEFAULT_TIME_LIMIT_MS);
    }

    /** 枚举对手防守并递归验证我方是否仍有必胜链。 */
    private boolean searchDefenses(int depthLeft) {
        if (isTimeout()) {
            return false;
        }
        Boolean memo = tt.probeThreat(zobristKey);
        if (memo != null) return memo;
        if (depthLeft <= 0) {
            return moveGenerator.findWinningMove(attackerColor) != null;
        }

        List<int[]> defenseMoves = generateDefenseResponses();

        if (defenseMoves.isEmpty()) {
            tt.storeThreat(zobristKey, true);
            return true;
        }

        for (int[] defense : defenseMoves) {
            applyMove(defense, defenderColor);

            if (board.checkWinAt(defense[0], defenderColor) || board.checkWinAt(defense[1], defenderColor)) {
                revertMove(defense, defenderColor);
                tt.storeThreat(zobristKey, false);
                return false;
            }

            boolean attackerCanWin = searchAttacks(depthLeft - 1);
            revertMove(defense, defenderColor);

            if (!attackerCanWin) {
                tt.storeThreat(zobristKey, false);
                return false;
            }
        }

        tt.storeThreat(zobristKey, true);
        return true;
    }

    /** 递归枚举下一层我方威胁着法。 */
    private boolean searchAttacks(int depthLeft) {
        if (isTimeout()) {
            return false;
        }
        Boolean memo = tt.probeThreat(zobristKey);
        if (memo != null) return memo;
        if (depthLeft <= 0) {
            return moveGenerator.findWinningMove(attackerColor) != null;
        }

        List<ScoredMove> threatMoves = collectThreateningMoves();
        for (ScoredMove sm : threatMoves) {
            int[] move = sm.move;
            applyMove(move, attackerColor);

            if (board.checkWinAt(move[0], attackerColor) || board.checkWinAt(move[1], attackerColor)) {
                revertMove(move, attackerColor);
                tt.storeThreat(zobristKey, true);
                return true;
            }

            boolean forcedWin = searchDefenses(depthLeft - 1);
            revertMove(move, attackerColor);

            if (forcedWin) {
                tt.storeThreat(zobristKey, true);
                return true;
            }
        }

        tt.storeThreat(zobristKey, false);
        return false;
    }

    /** 生成对手的防守着法（优先防守点，辅以少量高分着法）。 */
    private List<int[]> generateDefenseResponses() {
        List<int[]> cached = tt.probeDefense(zobristKey);
        if (cached != null) return cached;
        List<int[]> responses = new ArrayList<>();
        List<Integer> defensePoints = threatEvaluator.getDefensePositions(attackerColor);

        if (!defensePoints.isEmpty()) {
            if (defensePoints.size() >= 2) {
                responses.add(new int[] { defensePoints.get(0), defensePoints.get(1) });
            } else {
                int defPos = defensePoints.get(0);
                int partner = findBestPartner(defPos, defenderColor);
                if (partner >= 0) {
                    responses.add(new int[] { defPos, partner });
                }
            }
        }

        List<int[]> fallback = moveGenerator.generateMoves(defenderColor, attackerColor);
        int limit = Math.min(DEFENSE_MOVE_LIMIT, fallback.size());
        for (int i = 0; i < limit; i++) {
            int[] mv = fallback.get(i);
            if (!containsMove(responses, mv)) {
                responses.add(mv);
            }
        }

        tt.storeDefense(zobristKey, responses);
        return responses;
    }

    /** 为单个防守点寻找最优搭档位。 */
    private int findBestPartner(int fixedPos, PieceColor color) {
        int bestPos = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int pos : board.getCandidatePositions()) {
            if (pos == fixedPos || !board.isEmpty(pos)) {
                continue;
            }
            int score = board.evaluateMoveIncrement(pos, color);
            if (score > bestScore) {
                bestScore = score;
                bestPos = pos;
            }
        }
        return bestPos;
    }

    /** 收集具备威胁性的进攻着法，并按威胁强度排序。 */
    private List<ScoredMove> collectThreateningMoves() {
        List<int[]> baseMoves = moveGenerator.generateMoves(attackerColor, defenderColor);
        int limit = Math.min(ATTACK_MOVE_LIMIT, baseMoves.size());

        List<ScoredMove> scored = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            int[] move = baseMoves.get(i);
            int threatScore = evaluateThreatMove(move, attackerColor);
            if (threatScore > 0) {
                scored.add(new ScoredMove(move, threatScore));
            }
        }

        scored.sort((a, b) -> b.score - a.score);
        return scored;
    }

    /** 评估某个着法的威胁强度（模拟落子后检测关键威胁）。 */
    private int evaluateThreatMove(int[] move, PieceColor color) {
        applyMove(move, color);

        int score;
        if (board.checkWinAt(move[0], color) || board.checkWinAt(move[1], color)) {
            score = WIN_SCORE;
        } else {
            int row1 = V1Board.toRow(move[0]);
            int col1 = V1Board.toCol(move[0]);
            int row2 = V1Board.toRow(move[1]);
            int col2 = V1Board.toCol(move[1]);

            ThreatAnalyzer.ThreatResult t1 = ThreatAnalyzer.analyzeThreat(board, row1, col1, color);
            ThreatAnalyzer.ThreatResult t2 = ThreatAnalyzer.analyzeThreat(board, row2, col2, color);

            score = t1.getTotalScore() + t2.getTotalScore();
            if (t1.hasFourThree() || t2.hasFourThree()) {
                score += 180_000;
            }
            if (t1.hasDoubleFour() || t2.hasDoubleFour()) {
                score += 140_000;
            }
            if (t1.hasDoubleThree() || t2.hasDoubleThree()) {
                score += 90_000;
            }
            if (t1.isCriticalThreat() || t2.isCriticalThreat()) {
                score += 250_000;
            }
            if (isSameLine(move[0], move[1])) {
                score += 50_000;
            }
        }

        revertMove(move, color);
        return score;
    }

    private boolean isSameLine(int p1, int p2) {
        for (Line ln : board.getValidLinesAt(p1)) {
            if (ln.containsPosition(p2)) return true;
        }
        return false;
    }

    /** 落子并更新候选/路表，压栈状态以便回溯。 */
    private void applyMove(int[] move, PieceColor color) {
        int pos1 = move[0];
        int pos2 = move[1];

        stateStack.push(board.captureStateForSearch());

        board.setColor(pos1, color);
        board.setColor(pos2, color);

        zobristKey = tt.xorZobrist(zobristKey, pos1, color);
        zobristKey = tt.xorZobrist(zobristKey, pos2, color);

        board.updateCandidatesAfterMove(pos1);
        board.updateCandidatesAfterMove(pos2);

        board.updateLinesAfterMove(pos1, color, attackerColor);
        board.updateLinesAfterMove(pos2, color, attackerColor);
    }

    /** 回溯落子，恢复候选/路表。 */
    private void revertMove(int[] move, PieceColor color) {
        int pos1 = move[0];
        int pos2 = move[1];

        board.undoLinesAfterMove(pos1, color, attackerColor);
        board.undoLinesAfterMove(pos2, color, attackerColor);

        board.clearColor(pos1);
        board.clearColor(pos2);

        zobristKey = tt.xorZobrist(zobristKey, pos1, color);
        zobristKey = tt.xorZobrist(zobristKey, pos2, color);

        V1Board.BoardState state = stateStack.pop();
        board.restoreStateForSearch(state);
    }

    private boolean containsMove(List<int[]> moves, int[] move) {
        for (int[] m : moves) {
            if ((m[0] == move[0] && m[1] == move[1]) || (m[0] == move[1] && m[1] == move[0])) {
                return true;
            }
        }
        return false;
    }

    private boolean isTimeout() {
        return System.currentTimeMillis() - startTime > timeLimitMs;
    }

    private static class ScoredMove {
        int[] move;
        int score;

        ScoredMove(int[] move, int score) {
            this.move = move;
            this.score = score;
        }
    }
}

package stud.g06;

import core.board.PieceColor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * α-β 剪枝搜索器 V2
 * <p>
 * 【高级剪枝优化】
 * - 基础 α-β 搜索 + 迭代加深
 * - 杀手启发 (Killer Move Heuristic)
 * - 历史启发 (History Heuristic)
 * - 威胁延伸 (Threat Extension)
 * - 主变量搜索 (Principal Variation Search)
 * - 时间裁剪控制
 * - 使用路表增量更新优化节点评估
 * 
 * @author V2阶段开发
 * @version 2.1
 */
public class AlphaBetaSearcher {

    // ===================== 常量定义 =====================

    /** 无穷大（用于α-β边界） */
    private static final int INF = 10000000;

    /** 获胜分值 */
    private static final int WIN_SCORE = 1000000;

    /** 默认搜索深度 */
    private static final int DEFAULT_DEPTH = 8;

    /** 每步最大搜索时间（毫秒） */
    private static final long MAX_TIME_MS = 500;

    /** 最大候选着法数量（用于剪枝） */
    private static final int MAX_MOVES = 8;

    /** 杀手着法槽数量 */
    private static final int KILLER_SLOTS = 2;

    /** 最大搜索深度（用于数组大小） */
    private static final int MAX_DEPTH = 10;

    /** 历史表大小（棋盘大小） */
    private static final int HISTORY_SIZE = 19 * 19;

    /** 威胁延伸深度阈值 */
    private static final int EXTENSION_THRESHOLD = 2;

    // ===================== 成员变量 =====================

    /** 关联的棋盘 */
    private final V1Board board;

    /** 着法生成器 */
    private final MoveGenerator moveGenerator;

    /** 己方颜色 */
    private PieceColor myColor;

    /** 对方颜色 */
    private PieceColor oppColor;

    /** 搜索开始时间 */
    private long startTime;

    /** 是否超时 */
    private boolean timeout;

    /** 搜索的节点数（用于统计） */
    private int nodeCount;

    /** 剪枝次数统计 */
    private int cutoffCount;

    /** 最佳着法（迭代加深过程中更新） */
    private int[] bestMove;

    /** 杀手着法表 [深度][槽位][着法] */
    private final int[][][] killerMoves;

    /** 历史启发表 [位置] */
    private final int[] historyTable;

    /** 主变量表（用于PVS） */
    private int[][] pvTable;

    /** 棋盘候选状态栈（用于精确回溯） */
    private Deque<V1Board.BoardState> stateStack;

    // ===================== 构造函数 =====================

    public AlphaBetaSearcher(V1Board board, MoveGenerator moveGenerator) {
        this.board = board;
        this.moveGenerator = moveGenerator;
        this.killerMoves = new int[MAX_DEPTH][KILLER_SLOTS][2]; // [depth][slot][pos1,pos2]
        this.historyTable = new int[HISTORY_SIZE];
        this.pvTable = new int[MAX_DEPTH][2];
        this.stateStack = new ArrayDeque<>();
    }

    /**
     * 重置启发表（每局游戏开始时调用）
     */
    public void resetHeuristics() {
        for (int d = 0; d < MAX_DEPTH; d++) {
            for (int s = 0; s < KILLER_SLOTS; s++) {
                killerMoves[d][s][0] = -1;
                killerMoves[d][s][1] = -1;
            }
        }
        Arrays.fill(historyTable, 0);
    }

    // ===================== 核心搜索方法 =====================

    /**
     * 【α-β搜索入口】
     * 使用迭代加深搜索最佳着法
     * 
     * @param myColor  己方颜色
     * @param oppColor 对方颜色
     * @return 最佳着法 [pos1, pos2]
     */
    public int[] search(PieceColor myColor, PieceColor oppColor) {
        this.myColor = myColor;
        this.oppColor = oppColor;
        this.startTime = System.currentTimeMillis();
        this.timeout = false;
        this.nodeCount = 0;
        this.cutoffCount = 0;
        this.bestMove = null;
        this.stateStack.clear();

        // 每次搜索前重置启发表，避免旧局面干扰
        resetHeuristics();

        // 确保路表已构建
        if (!board.isLinesBuilt()) {
            board.buildLines(myColor);
        }

        // 迭代加深搜索
        for (int depth = 2; depth <= DEFAULT_DEPTH; depth += 2) {
            if (isTimeout()) {
                break;
            }

            int[] move = searchAtDepth(depth);
            if (move != null && !timeout) {
                bestMove = move;
                // 记录主变量
                pvTable[0][0] = move[0];
                pvTable[0][1] = move[1];
            }
        }

        return bestMove;
    }

    /**
     * 在指定深度进行搜索
     */
    private int[] searchAtDepth(int depth) {
        List<ScoredMove> moves = generateOrderedMoves(myColor, oppColor, depth, true);
        if (moves.isEmpty()) {
            return null;
        }

        int[] best = null;
        int alpha = -INF;
        int beta = INF;
        boolean firstMove = true;

        for (ScoredMove sm : moves) {
            if (isTimeout()) {
                break;
            }

            int[] move = sm.move;

            // 模拟落子
            makeMove(move, myColor);

            int score;
            if (firstMove) {
                // 第一个着法使用完整窗口
                score = -alphaBeta(depth - 1, -beta, -alpha, oppColor, myColor, false, depth);
                firstMove = false;
            } else {
                // PVS: 使用空窗口试探
                score = -alphaBeta(depth - 1, -alpha - 1, -alpha, oppColor, myColor, false, depth);
                if (score > alpha && score < beta) {
                    // 重新搜索
                    score = -alphaBeta(depth - 1, -beta, -alpha, oppColor, myColor, false, depth);
                }
            }

            // 撤销落子
            undoMove(move, myColor);

            if (score > alpha) {
                alpha = score;
                best = move;
            }
        }

        return best;
    }

    /**
     * 【α-β核心算法 + PVS + 威胁延伸】
     * 
     * @param depth      剩余搜索深度
     * @param alpha      α值（当前最优下界）
     * @param beta       β值（当前最优上界）
     * @param current    当前行动方
     * @param opponent   对手
     * @param maximizing 是否为极大层
     * @param ply        当前层数（从根节点计算）
     * @return 评估分值
     */
    private int alphaBeta(int depth, int alpha, int beta, PieceColor current, PieceColor opponent, boolean maximizing, int ply) {
        nodeCount++;

        // 超时检查
        if (isTimeout()) {
            return 0;
        }

        // 终止条件：达到深度限制
        if (depth <= 0) {
            return evaluate(current);
        }

        // 生成着法
        List<ScoredMove> moves = generateOrderedMoves(current, opponent, ply, false);
        if (moves.isEmpty()) {
            return evaluate(current);
        }

        boolean firstMove = true;
        int bestScore = -INF;

        for (ScoredMove sm : moves) {
            int[] move = sm.move;

            // 模拟落子
            makeMove(move, current);

            // 检查是否获胜
            if (checkWin(move, current)) {
                undoMove(move, current);
                return WIN_SCORE - ply; // 越早获胜越好
            }

            // 威胁延伸：如果当前着法是致命威胁，延伸搜索
            int extension = 0;
            if (depth <= EXTENSION_THRESHOLD) {
                if (isThreatMove(move, current)) {
                    extension = 1;
                }
            }

            int score;
            if (firstMove) {
                // 第一个着法使用完整窗口
                score = -alphaBeta(depth - 1 + extension, -beta, -alpha, opponent, current, !maximizing, ply + 1);
                firstMove = false;
            } else {
                // PVS: 空窗口试探
                score = -alphaBeta(depth - 1 + extension, -alpha - 1, -alpha, opponent, current, !maximizing, ply + 1);
                if (score > alpha && score < beta) {
                    // 重新搜索
                    score = -alphaBeta(depth - 1 + extension, -beta, -alpha, opponent, current, !maximizing, ply + 1);
                }
            }

            // 撤销落子
            undoMove(move, current);

            if (score > bestScore) {
                bestScore = score;
            }

            // α-β剪枝
            if (score >= beta) {
                cutoffCount++;
                // 更新杀手着法
                updateKillerMove(ply, move);
                // 更新历史表
                updateHistoryTable(move, depth);
                return beta; // β剪枝
            }
            if (score > alpha) {
                alpha = score;
            }
        }

        return alpha;
    }

    /**
     * 判断着法是否为致命威胁
     */
    private boolean isThreatMove(int[] move, PieceColor color) {
        int row1 = V1Board.toRow(move[0]);
        int col1 = V1Board.toCol(move[0]);
        int row2 = V1Board.toRow(move[1]);
        int col2 = V1Board.toCol(move[1]);

        ThreatAnalyzer.ThreatResult result1 = ThreatAnalyzer.analyzeThreat(board, row1, col1, color);
        ThreatAnalyzer.ThreatResult result2 = ThreatAnalyzer.analyzeThreat(board, row2, col2, color);

        return result1.isCriticalThreat() || result2.isCriticalThreat();
    }

    /**
     * 更新杀手着法表
     */
    private void updateKillerMove(int ply, int[] move) {
        if (ply >= MAX_DEPTH) return;

        // 如果不是第一个杀手着法，则移动到第一个位置
        if (killerMoves[ply][0][0] != move[0] || killerMoves[ply][0][1] != move[1]) {
            killerMoves[ply][1][0] = killerMoves[ply][0][0];
            killerMoves[ply][1][1] = killerMoves[ply][0][1];
            killerMoves[ply][0][0] = move[0];
            killerMoves[ply][0][1] = move[1];
        }
    }

    /**
     * 更新历史表
     */
    private void updateHistoryTable(int[] move, int depth) {
        // 加权增量：深度越深，权重越大
        int bonus = depth * depth;
        historyTable[move[0]] += bonus;
        historyTable[move[1]] += bonus;
    }

    // ===================== 辅助方法 =====================

    /**
     * 生成排序后的候选着法（加入杀手启发和历史启发）
     */
    private List<ScoredMove> generateOrderedMoves(PieceColor current, PieceColor opponent, int ply, boolean isRoot) {
        List<ScoredMove> scoredMoves = new ArrayList<>();
        List<Integer> candidates = board.getCandidatePositions();

        // 评估每个候选位置
        List<ScoredPosition> positions = new ArrayList<>();
        for (int pos : candidates) {
            if (!board.isEmpty(pos)) continue;

            // 使用路表增量评估（Step 4 优化）
            int score = board.evaluateMoveIncrement(pos, current);
            // 加上位置分
            int row = V1Board.toRow(pos);
            int col = V1Board.toCol(pos);
            score += MoveGenerator.getPositionScore(row, col);

            // 加入历史启发分数
            score += historyTable[pos] / 10;

            // 防守优先加权：如果对手在该点落子会形成高/致命威胁，则这是防守点
            int defenseBonus = computeDefenseBonus(pos, current, opponent, isRoot);
            score += defenseBonus;

            positions.add(new ScoredPosition(pos, score));
        }

        // 按分值降序排序
        positions.sort((a, b) -> b.score - a.score);

        // 限制候选数量
        int limit = Math.min(MAX_MOVES, positions.size());

        // 组合成双子着法
        for (int i = 0; i < limit; i++) {
            for (int j = i + 1; j < limit; j++) {
                int pos1 = positions.get(i).position;
                int pos2 = positions.get(j).position;
                int combinedScore = positions.get(i).score + positions.get(j).score;

                // 杀手着法加分
                if (ply < MAX_DEPTH) {
                    if (isKillerMove(ply, pos1, pos2)) {
                        combinedScore += 5000; // 杀手着法高优先级
                    }
                }

                scoredMoves.add(new ScoredMove(new int[]{pos1, pos2}, combinedScore));
            }
        }

        // 按组合分排序
        scoredMoves.sort((a, b) -> b.score - a.score);

        // 返回着法列表（限制数量）
        int moveLimit = Math.min(MAX_MOVES, scoredMoves.size());
        return new ArrayList<>(scoredMoves.subList(0, moveLimit));
    }

    /**
     * 计算防守加分：如果对手在该点落子能形成威胁，则作为防守点提高优先级
     * - 致命威胁（冲四/活四/复合致命）：高加分
     * - 高威胁（活三/跳活三等）：中等加分
     * 根节点可适当放大权重
     */
    private int computeDefenseBonus(int pos, PieceColor current, PieceColor opponent, boolean isRoot) {
        int row = V1Board.toRow(pos);
        int col = V1Board.toCol(pos);

        // 假设对手在此落子，评估威胁
        ThreatAnalyzer.ThreatResult oppThreat = ThreatAnalyzer.analyzeThreat(board, row, col, opponent);

        int bonus = 0;
        if (oppThreat.isCriticalThreat()) {
            bonus += 120000;
        } else if (oppThreat.getBestPattern().isHighThreat()) {
            bonus += 60000;
        }

        // 根节点放大防守权重，保证先看防点
        if (isRoot && bonus > 0) {
            bonus = (int) (bonus * 1.5);
        }
        return bonus;
    }

    /**
     * 检查是否为杀手着法
     */
    private boolean isKillerMove(int ply, int pos1, int pos2) {
        for (int s = 0; s < KILLER_SLOTS; s++) {
            if ((killerMoves[ply][s][0] == pos1 && killerMoves[ply][s][1] == pos2) ||
                (killerMoves[ply][s][0] == pos2 && killerMoves[ply][s][1] == pos1)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 模拟落子（更新棋盘和路表）
     */
    private void makeMove(int[] move, PieceColor color) {
        int pos1 = move[0];
        int pos2 = move[1];

        // 记录候选/边界/计数状态，便于回溯
        stateStack.push(board.captureStateForSearch());

        // 更新棋盘状态（使用公共方法）
        board.setColor(pos1, color);
        board.setColor(pos2, color);

        // 更新候选位置
        board.updateCandidatesAfterMove(pos1);
        board.updateCandidatesAfterMove(pos2);

        // 更新路表（增量）
        board.updateLinesAfterMove(pos1, color, myColor);
        board.updateLinesAfterMove(pos2, color, myColor);
    }

    /**
     * 撤销落子（恢复棋盘和路表）
     */
    private void undoMove(int[] move, PieceColor color) {
        int pos1 = move[0];
        int pos2 = move[1];

        // 撤销路表更新
        board.undoLinesAfterMove(pos1, color, myColor);
        board.undoLinesAfterMove(pos2, color, myColor);

        // 恢复棋盘状态（使用公共方法）
        board.clearColor(pos1);
        board.clearColor(pos2);

        // 精确恢复候选/边界/计数状态
        V1Board.BoardState state = stateStack.pop();
        board.restoreStateForSearch(state);
    }

    /**
     * 检查着法是否导致获胜
     */
    private boolean checkWin(int[] move, PieceColor color) {
        return board.checkWinAt(move[0], color) || board.checkWinAt(move[1], color);
    }

    /**
     * 评估当前局面（基于路表）
     */
    private int evaluate(PieceColor side) {
        // 使用路表的全局评估
        int boardScore = board.evaluateBoard();

        // 如果是对方视角，取负
        if (side != myColor) {
            boardScore = -boardScore;
        }

        return boardScore;
    }

    /**
     * 检查是否超时
     */
    private boolean isTimeout() {
        if (timeout) return true;
        if (System.currentTimeMillis() - startTime > MAX_TIME_MS) {
            timeout = true;
            return true;
        }
        return false;
    }

    /**
     * 获取搜索统计信息
     */
    public int getNodeCount() {
        return nodeCount;
    }

    /**
     * 获取剪枝次数统计
     */
    public int getCutoffCount() {
        return cutoffCount;
    }

    /**
     * 获取剪枝效率（剪枝次数/节点数）
     */
    public double getCutoffRate() {
        return nodeCount > 0 ? (double) cutoffCount / nodeCount : 0;
    }

    public MoveGenerator getMoveGenerator() {
        return moveGenerator;
    }

    // ===================== 内部类 =====================

    private static class ScoredPosition {
        int position;
        int score;

        ScoredPosition(int position, int score) {
            this.position = position;
            this.score = score;
        }
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

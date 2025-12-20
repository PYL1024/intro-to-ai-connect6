package stud.g08;

import core.board.PieceColor;

import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
    /** 上一轮根节点分值（用于立志窗口） */
    private Integer lastRootScore = null;

    /** 杀手着法表 [深度][槽位][着法] */
    private final int[][][] killerMoves;

    /** 历史启发表 [位置] */
    private final int[] historyTable;

    /** 主变量表（用于PVS） */
    private int[][] pvTable;

    /** 棋盘候选状态栈（用于精确回溯） */
    private Deque<V1Board.BoardState> stateStack;

    /** 内部置换表（TT，含边界类型） */
    private final Map<Long, TTEntry> transpositionTable = new HashMap<>();

    /** 共享置换表（跨搜索器复用评估） */
    private final TranspositionTable sharedTT;

    /** Zobrist 哈希键（内部表） */
    private long zobristKey = 0L;
    /** Zobrist 哈希键（共享TT） */
    private long sharedKey = 0L;

    /** Zobrist 随机表 [pos][colorIndex] */
    private static final long[][] ZOBRIST = new long[19 * 19][3];

    static {
        Random rnd = new Random(20251220L);
        for (int i = 0; i < 19 * 19; i++) {
            for (int c = 0; c < 3; c++) {
                ZOBRIST[i][c] = rnd.nextLong();
            }
        }
    }

    // ===================== 构造函数 =====================

    public AlphaBetaSearcher(V1Board board, MoveGenerator moveGenerator, TranspositionTable sharedTT) {
        this.board = board;
        this.moveGenerator = moveGenerator;
        this.sharedTT = sharedTT;
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

        // 初始化 Zobrist 键（内部与共享TT）
        zobristKey = computeInitialInternalKey();
        sharedKey = sharedTT != null ? sharedTT.computeInitialKey(board) : 0L;

        // 迭代加深搜索（立志窗口）
        for (int depth = 2; depth <= DEFAULT_DEPTH; depth += 2) {
            if (isTimeout()) {
                break;
            }
            int asp = 5000;
            // 若共享TT在根有EXACT值，用其作为初始中心
            if (lastRootScore == null && sharedTT != null) {
                TranspositionTable.TTEntry rootE = sharedTT.probeEval(sharedKey, 0);
                if (rootE != null && rootE.flag == TranspositionTable.Flag.EXACT) {
                    lastRootScore = rootE.value;
                }
            }
            int baseAlpha = (lastRootScore != null) ? Math.max(-INF, lastRootScore - asp) : -INF;
            int baseBeta  = (lastRootScore != null) ? Math.min(INF,  lastRootScore + asp) :  INF;

            SearchResult result = searchAtDepthWithWindow(depth, baseAlpha, baseBeta);
            if (result.bestMove != null && !timeout) {
                bestMove = result.bestMove;
                // 记录主变量
                pvTable[0][0] = bestMove[0];
                pvTable[0][1] = bestMove[1];
                lastRootScore = result.bestScore;
                // 将根节点的最佳着法写入共享TT以便其他搜索器使用
                if (sharedTT != null) {
                    sharedTT.storeEval(sharedKey, 0, depth, bestMove, TranspositionTable.Flag.EXACT);
                }
            }

            // 立志窗口失败时重搜
            if (!timeout && lastRootScore != null && (lastRootScore <= baseAlpha || lastRootScore >= baseBeta)) {
                int a2 = (lastRootScore <= baseAlpha) ? -INF : baseAlpha;
                int b2 = (lastRootScore >= baseBeta)  ?  INF : baseBeta;
                SearchResult retry = searchAtDepthWithWindow(depth, a2, b2);
                if (retry.bestMove != null) {
                    bestMove = retry.bestMove;
                    lastRootScore = retry.bestScore;
                    pvTable[0][0] = bestMove[0];
                    pvTable[0][1] = bestMove[1];
                }
            }
        }

        // 更新上一轮统计用于自适应
        this.lastNodes = this.nodeCount;
        this.lastCutoff = getCutoffRate();
        return bestMove;
    }

    /**
     * 在指定深度进行搜索
     */
    private SearchResult searchAtDepthWithWindow(int depth, int alphaInit, int betaInit) {
        List<ScoredMove> moves = generateOrderedMoves(myColor, oppColor, depth, true);
        if (moves.isEmpty()) {
            return new SearchResult(null, -INF);
        }

        int[] best = null;
        int alpha = alphaInit;
        int beta = betaInit;
        boolean firstMove = true;

        for (int idx = 0; idx < moves.size(); idx++) {
            ScoredMove sm = moves.get(idx);
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

        return new SearchResult(best, alpha);
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

        // 置换表探测（内部 + 共享）
        TTEntry tt = transpositionTable.get(zobristKey);
        if (tt != null && tt.depth >= depth) {
            if (tt.flag == TTEntry.Flag.EXACT) {
                return tt.value;
            } else if (tt.flag == TTEntry.Flag.LOWER && tt.value >= beta) {
                return tt.value;
            } else if (tt.flag == TTEntry.Flag.UPPER && tt.value <= alpha) {
                return tt.value;
            }
        }
        if (sharedTT != null) {
            TranspositionTable.TTEntry se = sharedTT.probeEval(sharedKey, depth);
            if (se != null) {
                if (se.flag == TranspositionTable.Flag.EXACT) {
                    return se.value;
                } else if (se.flag == TranspositionTable.Flag.LOWER && se.value >= beta) {
                    return se.value;
                } else if (se.flag == TranspositionTable.Flag.UPPER && se.value <= alpha) {
                    return se.value;
                }
            }
        }

        // 终止条件：达到深度限制
        if (depth <= 0) {
            return quiescence(alpha, beta, current, opponent, ply);
        }

        // 生成着法
        List<ScoredMove> moves = generateOrderedMoves(current, opponent, ply, false);
        if (moves.isEmpty()) {
            return evaluate(current);
        }

        boolean firstMove = true;
        int bestScore = -INF;
        int alphaOrig = alpha;

        for (int idx = 0; idx < moves.size(); idx++) {
            ScoredMove sm = moves.get(idx);
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
                // LMR: 对非威胁且排序靠后的着法进行安全性降低（收紧触发与减深）
                boolean isDefBlock = isDefensiveBlock(move, current, opponent);
                boolean applyLMR = (depth >= 4) && (idx >= 4) && !isThreatMove(move, current) && !isDefBlock;
                if (applyLMR) {
                    int redDepth = Math.max(0, depth - 1 + extension);
                    score = -alphaBeta(redDepth, -alpha - 1, -alpha, opponent, current, !maximizing, ply + 1);
                    if (score > alpha) {
                        score = -alphaBeta(depth - 1 + extension, -beta, -alpha, opponent, current, !maximizing, ply + 1);
                    }
                } else {
                    // PVS: 空窗口试探
                    score = -alphaBeta(depth - 1 + extension, -alpha - 1, -alpha, opponent, current, !maximizing, ply + 1);
                    if (score > alpha && score < beta) {
                        // 重新搜索
                        score = -alphaBeta(depth - 1 + extension, -beta, -alpha, opponent, current, !maximizing, ply + 1);
                    }
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
                storeTT(beta, depth, TTEntry.Flag.LOWER, move);
                return beta; // β剪枝
            }
            if (score > alpha) {
                alpha = score;
            }
        }

        // 存入TT：根据区间判定类型
        TTEntry.Flag flag;
        if (alpha <= alphaOrig) {
            flag = TTEntry.Flag.UPPER;
        } else if (alpha >= beta) {
            flag = TTEntry.Flag.LOWER;
        } else {
            flag = TTEntry.Flag.EXACT;
        }
        storeTT(alpha, depth, flag, null);
        if (sharedTT != null) {
            TranspositionTable.Flag sFlag = (flag == TTEntry.Flag.EXACT) ? TranspositionTable.Flag.EXACT
                    : (flag == TTEntry.Flag.LOWER ? TranspositionTable.Flag.LOWER : TranspositionTable.Flag.UPPER);
            sharedTT.storeEval(sharedKey, alpha, depth, null, sFlag);
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
        // 自适应：根节点与局面阶段、威胁压力驱动的位置上限
        int pieceCount = board.getPieceCount();
        Phase phase = (pieceCount < 30) ? Phase.EARLY : (pieceCount < 80 ? Phase.MID : Phase.LATE);

        // 威胁压力（用于防守加权）
        int threatPressure = 0;
        try {
            ThreatEvaluator te = moveGenerator.getThreatEvaluator();
            te.getDefensePositions(opponent); // 触发评估刷新
            threatPressure = te.getCriticalThreatCount() * 2 + te.getThreatCount();
        } catch (Exception ignored) { }

        // 根节点位置上限（渐进加宽：深度越小越窄）
        int basePosCap;
        switch (phase) {
            case EARLY: basePosCap = isRoot ? Math.max(10, 16 - Math.max(0, ply - 2)) : 10; break;
            case MID:   basePosCap = isRoot ? Math.max(8, 12 - Math.max(0, ply - 2))  : 8;  break;
            default:    basePosCap = isRoot ? Math.max(6, 10 - Math.max(0, ply - 2))  : 6;  break;
        }

        // 时间压力调整
        int timeLeftMs = (int) (MAX_TIME_MS - (System.currentTimeMillis() - startTime));
        // 简易自适应调参：根据当前时间与统计（上一轮）微调位置/着法上限
        double adjust = tuningAdjustment();
        if (timeLeftMs < MAX_TIME_MS / 3) basePosCap = Math.max(4, basePosCap - 2);
        if (threatPressure >= 3) basePosCap = Math.min(basePosCap + 2, candidates.size());

        List<ScoredPosition> positions = new ArrayList<>(Math.min(basePosCap, candidates.size()));
        int counted = 0;
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
            // 威胁压力加权放大（根节点更明显）
            if (defenseBonus > 0) {
                double amp = 1.0 + Math.min(1.0, threatPressure * (isRoot ? 0.25 : 0.15));
                defenseBonus = (int) (defenseBonus * amp);
            }
            score += defenseBonus;

            positions.add(new ScoredPosition(pos, score));
            counted++;
            if (isRoot && counted >= basePosCap) break; // 根节点位置数量上限
        }

        // 按分值降序排序
        positions.sort((a, b) -> b.score - a.score);

        // 限制位置数量用于组合
        int dynamicPosLimit = Math.min(positions.size(), Math.max(4, (int)Math.round(basePosCap * adjust)));

        // 组合成双子着法
        for (int i = 0; i < dynamicPosLimit; i++) {
            for (int j = i + 1; j < dynamicPosLimit; j++) {
                int pos1 = positions.get(i).position;
                int pos2 = positions.get(j).position;
                int combinedScore = positions.get(i).score + positions.get(j).score;

                // 杀手着法加分
                if (ply < MAX_DEPTH) {
                    if (isKillerMove(ply, pos1, pos2)) {
                        combinedScore += 5000; // 杀手着法高优先级
                    }
                }

                // 置换表主变着法加分
                int[] ttMove = getTTMove();
                if (ttMove != null && ((ttMove[0] == pos1 && ttMove[1] == pos2) || (ttMove[0] == pos2 && ttMove[1] == pos1))) {
                    combinedScore += 7000;
                }

                // 双子协同加分：同一路或潜在双威胁
                combinedScore += computePairSynergy(pos1, pos2, current);

                scoredMoves.add(new ScoredMove(new int[]{pos1, pos2}, combinedScore));
            }
        }

        // 按组合分排序
        scoredMoves.sort((a, b) -> b.score - a.score);

        // 返回着法列表（自适应限制数量）
        int baseMoveCap;
        switch (phase) {
            case EARLY: baseMoveCap = 10; break;
            case MID:   baseMoveCap = 8;  break;
            default:    baseMoveCap = 6;  break;
        }
        if (timeLeftMs < MAX_TIME_MS / 3) baseMoveCap = Math.max(4, baseMoveCap - 2);
        if (threatPressure >= 3) baseMoveCap = Math.min(baseMoveCap + 2, scoredMoves.size());
        baseMoveCap = (int)Math.round(baseMoveCap * adjust);
        int moveLimit = Math.min(baseMoveCap, scoredMoves.size());
        return new ArrayList<>(scoredMoves.subList(0, moveLimit));
    }

    // ======== 简易自动调参：根据上一轮统计微调候选上限 ========
    private int lastNodes = 0;
    private double lastCutoff = 0.0;

    private double tuningAdjustment() {
        double adj = 1.0;
        // 收紧阈值与幅度，优先稳定
        if (lastCutoff < 0.28 && lastNodes > 250_000) adj -= 0.05; // 收窄 5%
        if (lastCutoff > 0.60 && lastNodes < 70_000) adj += 0.05; // 放宽 5%
        return Math.max(0.95, Math.min(1.05, adj));
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

        // Zobrist 更新
        xorZobrist(pos1, color);
        xorZobrist(pos2, color);
        if (sharedTT != null) {
            sharedKey = sharedTT.xorZobrist(sharedKey, pos1, color);
            sharedKey = sharedTT.xorZobrist(sharedKey, pos2, color);
        }

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

        // Zobrist 回滚（XOR 同一值两次即可回滚）
        xorZobrist(pos1, color);
        xorZobrist(pos2, color);
        if (sharedTT != null) {
            sharedKey = sharedTT.xorZobrist(sharedKey, pos1, color);
            sharedKey = sharedTT.xorZobrist(sharedKey, pos2, color);
        }

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
        int boardScore;
        if (sharedTT != null) {
            TranspositionTable.TTEntry e = sharedTT.probeEval(sharedKey, 0);
            if (e != null) {
                boardScore = e.value;
            } else {
                boardScore = board.evaluateBoard();
                sharedTT.storeEval(sharedKey, boardScore, 0, null, TranspositionTable.Flag.EXACT);
            }
        } else {
            boardScore = board.evaluateBoard();
        }

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

    // ===================== Zobrist / TT 工具 =====================

    private void xorZobrist(int pos, PieceColor color) {
        int cIdx = (color == PieceColor.BLACK) ? 1 : (color == PieceColor.WHITE ? 2 : 0);
        zobristKey ^= ZOBRIST[pos][cIdx];
    }

    private void storeTT(int value, int depth, TTEntry.Flag flag, int[] best) {
        TTEntry e = new TTEntry();
        e.value = value;
        e.depth = depth;
        e.flag = flag;
        e.bestMove = best;
        transpositionTable.put(zobristKey, e);
    }

    private int[] getTTMove() {
        TTEntry e = transpositionTable.get(zobristKey);
        if (e != null && e.bestMove != null) return e.bestMove;
        if (sharedTT != null) {
            TranspositionTable.TTEntry se = sharedTT.probeEval(sharedKey, 0);
            if (se != null) return se.bestMove;
        }
        return null;
    }

    private long computeInitialInternalKey() {
        long key = 0L;
        for (int pos = 0; pos < 19 * 19; pos++) {
            PieceColor c = board.get(pos);
            int cIdx = (c == PieceColor.BLACK) ? 1 : (c == PieceColor.WHITE ? 2 : 0);
            key ^= ZOBRIST[pos][cIdx];
        }
        return key;
    }

    private int quiescence(int alpha, int beta, PieceColor current, PieceColor opponent, int ply) {
        int standPat = evaluate(current);
        if (standPat >= beta) return beta;
        if (standPat > alpha) alpha = standPat;

        // 仅扩展战术着法（致命威胁或阻断）
        List<ScoredMove> tactical = generateTacticalMoves(current, opponent, ply);
        for (ScoredMove sm : tactical) {
            int[] move = sm.move;
            makeMove(move, current);
            int score = -quiescence(-beta, -alpha, opponent, current, ply + 1);
            undoMove(move, current);
            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }
        return alpha;
    }

    private static class SearchResult {
        int[] bestMove;
        int bestScore;
        SearchResult(int[] m, int s) { this.bestMove = m; this.bestScore = s; }
    }

    private List<ScoredMove> generateTacticalMoves(PieceColor current, PieceColor opponent, int ply) {
        List<ScoredMove> res = new ArrayList<>();
        List<int[]> base = moveGenerator.generateMoves(current, opponent);
        int cap = Math.min(6, base.size());
        for (int i = 0; i < cap; i++) {
            int[] m = base.get(i);
            int row1 = V1Board.toRow(m[0]);
            int col1 = V1Board.toCol(m[0]);
            int row2 = V1Board.toRow(m[1]);
            int col2 = V1Board.toCol(m[1]);
            ThreatAnalyzer.ThreatResult t1 = ThreatAnalyzer.analyzeThreat(board, row1, col1, current);
            ThreatAnalyzer.ThreatResult t2 = ThreatAnalyzer.analyzeThreat(board, row2, col2, current);
            int s = 0;
            if (t1.isCriticalThreat() || t2.isCriticalThreat()) s += 100000;
            if (t1.hasDoubleThree() || t2.hasDoubleThree()) s += 30000;
            if (s > 0) res.add(new ScoredMove(m, s));
        }
        res.sort((a, b) -> b.score - a.score);
        return res;
    }

    private int computePairSynergy(int pos1, int pos2, PieceColor color) {
        int synergy = 0;
        // 同一路协同
        List<Line> lines1 = board.getValidLinesAt(pos1);
        for (Line ln : lines1) {
            if (ln.containsPosition(pos2)) {
                synergy += 6000;
                break;
            }
        }
        return synergy;
    }

    /**
     * 判断是否为防守封堵型着法：当前位置若由对手落子会形成高/致命威胁，则视为封堵
     */
    private boolean isDefensiveBlock(int[] move, PieceColor current, PieceColor opponent) {
        int bonus1 = computeDefenseBonus(move[0], opponent, current, false);
        int bonus2 = computeDefenseBonus(move[1], opponent, current, false);
        return bonus1 > 0 || bonus2 > 0;
    }

    private enum Phase { EARLY, MID, LATE }

    /** 置换表条目 */
    private static class TTEntry {
        enum Flag { EXACT, LOWER, UPPER }
        int value;
        int depth;
        Flag flag;
        int[] bestMove;
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

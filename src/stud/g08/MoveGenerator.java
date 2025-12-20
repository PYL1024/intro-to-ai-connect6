package stud.g08;

import core.board.PieceColor;

import java.util.*;

/**
 * 着法生成器
 * <p>
 * 负责生成和排序候选着法，是AI决策的核心组件之一。
 * <p>
 * 【设计思想】
 * 1. 基于"路"的概念，优先考虑能形成/破坏关键棋型的位置
 * 2. 使用候选位置集合，避免全盘扫描
 * 3. 支持着法排序，为V2阶段的α-β剪枝提供支持
 * <p>
 * 【着法优先级】
 * 1. 胜着（能直接获胜的着法）
 * 2. 防守着法（阻止对手获胜）
 * 3. 进攻着法（形成己方活四、冲四、活三）
 * 4. 发展着法（形成己方活二、眠三）
 * 5. 位置着法（靠近中心、靠近已有棋子）
 * 
 * @author V1阶段开发
 * @version 1.0
 */
public class MoveGenerator {

    // ===================== 常量定义 =====================

    /** 棋盘大小 */
    private static final int SIZE = V1Board.SIZE;

    /** 最大候选着法数量 */
    private static final int MAX_CANDIDATES = 50;

    /** 位置分值表（中心高，边缘低） */
    private static final int[][] POSITION_SCORE = initPositionScore();

    // ===================== 成员变量 =====================

    /** 关联的棋盘 */
    private V1Board board;

    /** 威胁评估器 */
    private ThreatEvaluator threatEvaluator;

    // ===================== 构造函数 =====================

    public MoveGenerator(V1Board board) {
        this.board = board;
        this.threatEvaluator = new ThreatEvaluator(board);
    }

    // ===================== 胜着检测 =====================

    /**
     * 【胜着检测核心算法】
     * 检测是否存在能让己方直接获胜的着法
     * <p>
     * 伪代码：
     * ```
     * function findWinningMove(board, myColor):
     * candidates = board.getCandidatePositions()
     * for each pos1 in candidates:
     * // 检查单子胜着
     * if canWinAt(pos1, myColor):
     * // 寻找第二个位置组成完整着法
     * for each pos2 in candidates:
     * if pos2 != pos1:
     * return (pos1, pos2)
     * <p>
     * // 检查双子组合胜着
     * for each pos2 in candidates:
     * if pos2 > pos1: // 避免重复检查
     * if canWinWithTwo(pos1, pos2, myColor):
     * return (pos1, pos2)
     * return null
     * ```
     * 
     * @param myColor 己方颜色
     * @return 胜着（两个位置），如果不存在则返回null
     */
    public int[] findWinningMove(PieceColor myColor) {
        List<Integer> candidates = board.getCandidatePositions();
        // 1) 单点直接获胜（假设在该点落子）
        Integer singleWin = null;
        for (int pos : candidates) {
            if (board.isEmpty(pos) && board.checkWinIfPlace(pos, myColor)) {
                singleWin = pos;
                break;
            }
        }
        if (singleWin != null) {
            // 找任一第二点即可（保持有效着法）
            for (int pos : candidates) {
                if (pos != singleWin && board.isEmpty(pos)) {
                    return new int[] { singleWin, pos };
                }
            }
        }

        // 2) 双子组合获胜（同时在两个点落子）
        int n = candidates.size();
        for (int i = 0; i < n; i++) {
            int pos1 = candidates.get(i);
            if (!board.isEmpty(pos1)) {
                continue;
            }
            for (int j = i + 1; j < n; j++) {
                int pos2 = candidates.get(j);
                if (!board.isEmpty(pos2)) {
                    continue;
                }
                if (board.checkWinIfPlaceTwo(pos1, pos2, myColor)) {
                    return new int[] { pos1, pos2 };
                }
            }
        }

        return null;
    }

    /*
      检查两子组合是否能获胜
      查找两个位置，落子后能形成连六
     */
    // findTwoStoneWin/canFormFive 已由直接双点模拟替代

    // ===================== 着法生成 =====================

    /**
     * 【着法生成核心算法】
     * 生成并排序所有候选着法
     * <p>
     * 伪代码：
     * ```
     * function generateMoves(board, myColor, oppColor):
     * // 1. 检查胜着
     * winMove = findWinningMove(myColor)
     * if winMove != null:
     * return [winMove]
     * <p>
     * // 2. 检查防守
     * defensePositions = threatEvaluator.getDefensePositions(oppColor)
     * if defensePositions is urgent:
     * return generateDefenseMoves(defensePositions)
     * <p>
     * // 3. 生成进攻着法
     * candidates = []
     * for each pos in board.candidatePositions:
     * score = evaluatePosition(pos, myColor, oppColor)
     * candidates.add((pos, score))
     * <p>
     * // 4. 按分值排序
     * sort(candidates, by score descending)
     * <p>
     * // 5. 组合成双子着法
     * return combineMoves(candidates)
     * ```
     * 
     * @param myColor  己方颜色
     * @param oppColor 对手颜色
     * @return 排序后的着法列表（每个着法包含两个位置）
     */
    public List<int[]> generateMoves(PieceColor myColor, PieceColor oppColor) {
        List<int[]> moves = new ArrayList<>();

        // 1. 检查胜着
        int[] winMove = findWinningMove(myColor);
        if (winMove != null) {
            moves.add(winMove);
            return moves;
        }

        // 2. 获取候选位置及其分值
        List<ScoredPosition> scoredPositions = evaluateAllCandidates(myColor, oppColor);

        // 3. 检查是否需要紧急防守
        List<Integer> defensePositions = threatEvaluator.getDefensePositions(oppColor);
        boolean urgentDefense = threatEvaluator.getCriticalThreatCount() > 0
                || (threatEvaluator.getThreatCount() <= 2 && !defensePositions.isEmpty());

        // 4. 组合着法
        if (urgentDefense && !defensePositions.isEmpty()) {
            // 紧急防守模式：优先使用防守位置
            moves = generateDefensiveMoves(defensePositions, scoredPositions, myColor);
        } else {
            // 正常模式：按分值组合
            moves = generateOffensiveMoves(scoredPositions);
        }

        return moves;
    }

    /**
     * 评估所有候选位置
     */
    private List<ScoredPosition> evaluateAllCandidates(PieceColor myColor, PieceColor oppColor) {
        List<ScoredPosition> scored = new ArrayList<>();
        List<Integer> candidates = board.getCandidatePositions();

        for (int pos : candidates) {
            if (!board.isEmpty(pos))
                continue;

            int score = evaluatePosition(pos, myColor, oppColor);
            scored.add(new ScoredPosition(pos, score));
        }

        // 按分值降序排序
        scored.sort((a, b) -> b.score - a.score);

        // 限制候选数量
        if (scored.size() > MAX_CANDIDATES) {
            scored = scored.subList(0, MAX_CANDIDATES);
        }

        return scored;
    }

    /**
     * 评估单个位置的分值
     * 【Step 4 优化】优先使用路表增量评估
     * 
     * @param pos      位置
     * @param myColor  己方颜色
     * @param oppColor 对手颜色
     * @return 综合分值
     */
    private int evaluatePosition(int pos, PieceColor myColor, PieceColor oppColor) {
        int score = 0;
        int row = V1Board.toRow(pos);
        int col = V1Board.toCol(pos);

        // 1. 位置分（中心高）
        score += POSITION_SCORE[row][col];

        // 2. 使用路表增量评估（Step 4 优化）
        if (board.isLinesBuilt()) {
            // 基于路表的快速增量评估
            score += board.evaluateMoveIncrement(pos, myColor) * 2;
            // 对方视角的增量（防守价值）
            score += board.evaluateMoveIncrement(pos, oppColor);
        } else {
            // 回退到V1的四向扫描评估
            score += board.evaluatePositionScore(pos, myColor) * 2;
            score += board.evaluatePositionScore(pos, oppColor);
        }

        return score;
    }

    /**
     * 生成防守着法
     */
    private List<int[]> generateDefensiveMoves(List<Integer> defensePositions,
            List<ScoredPosition> scoredPositions,
            PieceColor myColor) {
        List<int[]> moves = new ArrayList<>();

        if (defensePositions.size() >= 2) {
            // 需要防守两个位置
            moves.add(new int[] { defensePositions.get(0), defensePositions.get(1) });
        } else if (defensePositions.size() == 1) {
            // 防守一个位置，另一个位置选择最佳进攻点
            int defPos = defensePositions.get(0);
            for (ScoredPosition sp : scoredPositions) {
                if (sp.position != defPos && board.isEmpty(sp.position)) {
                    moves.add(new int[] { defPos, sp.position });
                    break;
                }
            }
        }

        // 添加一些备选着法
        for (int i = 0; i < Math.min(5, scoredPositions.size()); i++) {
            for (int j = i + 1; j < Math.min(10, scoredPositions.size()); j++) {
                int pos1 = scoredPositions.get(i).position;
                int pos2 = scoredPositions.get(j).position;
                if (board.isEmpty(pos1) && board.isEmpty(pos2)) {
                    int[] move = new int[] { pos1, pos2 };
                    if (!containsMove(moves, move)) {
                        moves.add(move);
                    }
                }
            }
        }

        return moves;
    }

    /**
     * 生成进攻着法
     */
    private List<int[]> generateOffensiveMoves(List<ScoredPosition> scoredPositions) {
        List<int[]> moves = new ArrayList<>();
        int n = Math.min(MAX_CANDIDATES, scoredPositions.size());

        // 组合高分位置
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                int pos1 = scoredPositions.get(i).position;
                int pos2 = scoredPositions.get(j).position;
                if (board.isEmpty(pos1) && board.isEmpty(pos2)) {
                    moves.add(new int[] { pos1, pos2 });
                    if (moves.size() >= MAX_CANDIDATES) {
                        return moves;
                    }
                }
            }
        }

        return moves;
    }

    /**
     * 检查着法列表是否包含某着法
     */
    private boolean containsMove(List<int[]> moves, int[] move) {
        for (int[] m : moves) {
            if ((m[0] == move[0] && m[1] == move[1]) ||
                    (m[0] == move[1] && m[1] == move[0])) {
                return true;
            }
        }
        return false;
    }

    // ===================== 内部类 =====================

    /**
     * 带分值的位置
     */
    private static class ScoredPosition {
        int position;
        int score;

        ScoredPosition(int position, int score) {
            this.position = position;
            this.score = score;
        }
    }

    // ===================== 工具方法 =====================

    /**
     * 初始化位置分值表
     * 中心区域分值高，边缘分值低
     */
    private static int[][] initPositionScore() {
        int[][] score = new int[SIZE][SIZE];
        int center = SIZE / 2;

        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                // 使用曼哈顿距离计算，距中心越近分值越高
                int dist = Math.abs(i - center) + Math.abs(j - center);
                score[i][j] = Math.max(0, 20 - dist);
            }
        }

        return score;
    }

    /**
     * 获取位置分值
     */
    public static int getPositionScore(int row, int col) {
        if (V1Board.isValidPosition(row, col)) {
            return POSITION_SCORE[row][col];
        }
        return 0;
    }

    /**
     * 获取威胁评估器（供AI类使用）
     */
    public ThreatEvaluator getThreatEvaluator() {
        return threatEvaluator;
    }
}

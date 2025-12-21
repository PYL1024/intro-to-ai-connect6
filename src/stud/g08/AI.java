package stud.g08;

import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.List;

/**
 * V3阶段智能AI（TBS + α-β）
 * <p>
 * 【功能特性】
 * 1. 胜着检测：检测并优先选择能直接获胜的着法
 * 2. 威胁防守：检测对手威胁并进行有效防守
 * 3. 智能着法生成：基于棋型评估生成和排序着法
 * 4. 路表快速评估：基于有效路的增量评估
 * 5. α-β剪枝搜索：迭代加深搜索最优着法
 * 6. 【V3新增】威胁空间搜索（TBS）：强制性威胁链搜索，抢先找出必胜序列
 * <p>
 * 【决策流程】
 * 1. 检测己方胜着 → 如有，立即执行
 * 2. 检测对方威胁 → 如有紧急威胁，优先防守
 * 3. α-β搜索 / 【V3】威胁空间搜索 → 搜索最优着法
 * 4. 回退到启发式着法生成
 * <p>
 * 【设计说明】
 * - 继承自框架的AI基类
 * - 使用V1Board扩展棋盘功能 + 路表
 * - 集成α-β剪枝搜索 + 威胁空间搜索
 * 
 * @author 3阶段开发
 * @version 3.0
 */
public class AI extends core.player.AI {

    // ===================== 常量定义 =====================

    /** 棋盘大小 */
    private static final int BOARD_SIZE = 19;

    /** AI名称 */
    private static final String AI_NAME = "G08";

    /** 是否启用α-β搜索 */
    private static final boolean USE_ALPHA_BETA = true;

    /** 是否启用威胁空间搜索（TBS） */
    private static final boolean USE_THREAT_SPACE = true;

    /** TBS搜索最大深度（以我方着法层计） */
    private static final int TBS_MAX_DEPTH = 8;

    /** TBS默认时间预算（毫秒） */
    private static final long TBS_DEFAULT_TIME_LIMIT_MS = 1500;

    // ===================== 成员变量 =====================

    /** V1扩展棋盘 */
    private V1Board v1Board;

    /** 着法生成器 */
    private MoveGenerator moveGenerator;

    /** α-β搜索器 */
    private AlphaBetaSearcher alphaBetaSearcher;

    /** 威胁空间搜索器 */
    private ThreatSpaceSearcher threatSpaceSearcher;

    /** 共享置换表（评估缓存） */
    private TranspositionTable transpositionTable;

    /** TBS 时间预算（毫秒） */
    private long tbsTimeLimitMs = TBS_DEFAULT_TIME_LIMIT_MS;
    /** 当前策略ID（用于奖励反馈） */
    private int currentStrategyId = -1;

    // 简易搜索日志已移除

    /** 己方颜色 */
    private PieceColor myColor;

    /** 对方颜色 */
    private PieceColor oppColor;


    /** 回合计数 */
    private int turnCount;

    /** 路表是否已初始化 */
    private boolean linesInitialized;

    // ===================== 核心方法 =====================

    /**
     * 【AI决策核心算法 - V2版本】
     * 根据当前局面找出最佳着法
     * <p>
     * 【V2决策流程】
     * 1. 更新棋盘状态和路表
     * 2. 检测胜着 → 如有，立即执行
     * 3. 检测致命威胁 → 如有，优先防守
     * 4. α-β搜索 → 搜索最优着法
     * 5. 回退到启发式着法生成
     * 
     * @param opponentMove 对手的着法
     * @return 己方着法
     */
    

    /**
     * 核心决策逻辑（V2版本：集成α-β搜索 + 精细化威胁）
     */
    private int[] makeDecision() {
        // V1Board 与基础棋盘共享状态，无需额外同步

        // 【触发式补充】中后盘扩展候选范围，确保不漏远处威胁
        expandCandidatesIfNeeded();

        // 步骤1：检测胜着（最高优先级）
        int[] winMove = moveGenerator.findWinningMove(myColor);
        if (winMove != null) {
            return winMove;
        }

        // 步骤2：检测并处理致命威胁
        ThreatEvaluator threatEval = moveGenerator.getThreatEvaluator();
        List<Integer> defensePos = threatEval.getDefensePositions(oppColor);

        // 【触发式补充】确保防守点在候选列表中
        if (!defensePos.isEmpty()) {
            v1Board.ensureCandidates(defensePos);
        }

        // 有致命威胁（冲四/活四）必须立即防守
        if (threatEval.getCriticalThreatCount() > 0 && !defensePos.isEmpty()) {
            return createDefenseMove(defensePos);
        }
        // 若评估出现异常（致命威胁但未给出防点），执行兜底的四连端点防守扫描
        if (threatEval.getCriticalThreatCount() > 0 && defensePos.isEmpty()) {
            List<Integer> fallbackDefs = findCriticalDefenseFallback();
            if (!fallbackDefs.isEmpty()) {
                return createDefenseMove(fallbackDefs);
            }
        }

        // 步骤2.5：【V2新增】检测对方复合威胁（双活三/冲四活三）
        int[] compoundDefense = detectCompoundThreat();
        if (compoundDefense != null) {
            return compoundDefense;
        }

        // 步骤2.7：严格规则——若存在连续四子且两端皆空，必须双端封堵（高优先级且仅限该威胁）
        int[] strictPair = blockFourInSixPairStrict();
        if (strictPair != null) {
            return strictPair;
        }

        // 步骤2.8：提升优先级——一般 4-in-6 单端/中间双空威胁，先固定第一子再择第二子（低于严格双端，高于 TBS）
        int[] flexibleEarly = blockFourInSix();
        if (flexibleEarly != null) {
            return flexibleEarly;
        }


        // 步骤3：尝试威胁空间搜索（强制胜负链）
        int[] tbsMove = searchThreatSpace();
        if (tbsMove != null) {
            return tbsMove;
        }

        // 步骤3.5：进入 MCTS/α-β 前的保险防守扫描
        List<Integer> preDef = threatEval.getDefensePositions(oppColor);
        if (!preDef.isEmpty()) {
            return createDefenseMove(preDef);
        }
        if (threatEval.getThreatCount() > 0) {
            List<Integer> fb = findCriticalDefenseFallback();
            if (!fb.isEmpty()) return createDefenseMove(fb);
        }

        // 已提升：一般 4-in-6 逻辑已提前处理

        // 已移除：MCTS 阶段，时间全部留给 TBS 与 α-β

        // 步骤4：使用α-β搜索寻找最优着法
        if (USE_ALPHA_BETA && alphaBetaSearcher != null) {
            int[] searchMove = alphaBetaSearcher.search(myColor, oppColor);
            if (searchMove != null) {
                // 将搜索表现反馈给策略管理器
                int nodes = alphaBetaSearcher.getNodeCount();
                double cr = alphaBetaSearcher.getCutoffRate();
                StrategyManager.reportSearchReward(currentStrategyId, nodes, cr);
                return searchMove;
            }
        }

        // 步骤5：回退到启发式着法生成
        // 保守策略：威胁数≤2 时必须防守所有威胁
        if (!defensePos.isEmpty() && threatEval.getThreatCount() <= 2) {
            return createDefenseMove(defensePos);
        }

        // 生成并选择最优着法
        List<int[]> moves = moveGenerator.generateMoves(myColor, oppColor);
        if (!moves.isEmpty()) {
            return moves.get(0);
        }

        return null;
    }

    /**
     * 兜底防守：扫描对方在四方向的连续四子，返回其两端的立即空位作为防守点。
     */
    private List<Integer> findCriticalDefenseFallback() {
        List<Integer> points = new java.util.ArrayList<>();
        final int[] DX = {0, 1, 1, 1};
        final int[] DY = {1, 0, 1, -1};
        for (int idx = 0; idx < BOARD_SIZE * BOARD_SIZE; idx++) {
            if (this.board.get(idx) != oppColor) continue;
            int row = V1Board.toRow(idx);
            int col = V1Board.toCol(idx);
            for (int dir = 0; dir < 4; dir++) {
                int count = 1;
                int r = row + DX[dir], c = col + DY[dir];
                while (V1Board.isValidPosition(r, c) && this.board.get(V1Board.toIndex(r, c)) == oppColor) {
                    count++; r += DX[dir]; c += DY[dir];
                }
                int end1r = r, end1c = c; // 正向端点后的第一个非对方子
                r = row - DX[dir]; c = col - DY[dir];
                while (V1Board.isValidPosition(r, c) && this.board.get(V1Board.toIndex(r, c)) == oppColor) {
                    count++; r -= DX[dir]; c -= DY[dir];
                }
                int end2r = r, end2c = c; // 反向端点后的第一个非对方子
                if (count >= 4) {
                    // 两端若为空则加入防守点
                    if (V1Board.isValidPosition(end1r, end1c)) {
                        int p = V1Board.toIndex(end1r, end1c);
                        if (this.board.get(p) == PieceColor.EMPTY && !points.contains(p)) points.add(p);
                    }
                    if (V1Board.isValidPosition(end2r, end2c)) {
                        int p = V1Board.toIndex(end2r, end2c);
                        if (this.board.get(p) == PieceColor.EMPTY && !points.contains(p)) points.add(p);
                    }
                    if (points.size() >= 2) return points;
                }
            }
        }
        return points;
    }

    /**
     * V3：威胁空间搜索入口
     */
    private int[] searchThreatSpace() {
        if (!USE_THREAT_SPACE || threatSpaceSearcher == null) {
            return null;
        }
        return threatSpaceSearcher.searchForcingWin(myColor, oppColor, TBS_MAX_DEPTH, tbsTimeLimitMs);
    }

    // 已移除：MCTS 搜索入口

    /**
     * 【触发式补充】根据局面阶段扩展候选范围
     * 中后盘时主动扩展边界内的空位为候选
     */
    private void expandCandidatesIfNeeded() {
        int pieceCount = v1Board.getPieceCount();
        // 中盘开始（40子以上）扩展一次
        if (pieceCount >= 40 && pieceCount < 45) {
            v1Board.expandCandidatesInBounds(1);
        }
        // 后期（70子以上）再扩展
        if (pieceCount >= 70 && pieceCount < 75) {
            v1Board.expandCandidatesInBounds(2);
        }
    }

    /**
     * 检测对方的复合威胁（双活三、冲四活三等）
     * @return 如果发现复合威胁，返回防守着法；否则返回null
     */
    private int[] detectCompoundThreat() {
        List<Integer> candidates = v1Board.getCandidatePositions();

        int bestThreatPos = -1;
        int bestThreatScore = 0;

        // 检测对方每个候选位置是否能形成复合威胁
        for (int pos : candidates) {
            if (!v1Board.isEmpty(pos)) continue;

            int row = V1Board.toRow(pos);
            int col = V1Board.toCol(pos);

            // 模拟对方落子
            v1Board.setColor(pos, oppColor);
            ThreatAnalyzer.ThreatResult result = ThreatAnalyzer.analyzeThreat(v1Board, row, col, oppColor);
            v1Board.clearColor(pos);

            // 检查是否有复合威胁
            if (result.hasDoubleThree() || result.hasDoubleFour() || result.hasFourThree()) {
                int score = result.getTotalScore();
                if (score > bestThreatScore) {
                    bestThreatScore = score;
                    bestThreatPos = pos;
                }
            }
        }

        // 如果发现复合威胁，必须防守
        if (bestThreatPos >= 0) {
            // 第一子堵住威胁点，第二子选择最佳进攻位置
            int secondPos = findBestAttackPosition(bestThreatPos);
            if (secondPos >= 0) {
                return new int[] { bestThreatPos, secondPos };
            }
        }

        return null;
    }

    /**
     * 找到最佳进攻位置（排除指定位置）
     */
    private int findBestAttackPosition(int excludePos) {
        List<Integer> candidates = v1Board.getCandidatePositions();
        int bestPos = -1;
        int bestScore = -1;

        for (int pos : candidates) {
            if (pos == excludePos || !v1Board.isEmpty(pos)) continue;

            int score = v1Board.evaluateMoveIncrement(pos, myColor);
            score += MoveGenerator.getPositionScore(V1Board.toRow(pos), V1Board.toCol(pos));

            if (score > bestScore) {
                bestScore = score;
                bestPos = pos;
            }
        }

        return bestPos;
    }

    /**
     * 全局扫描：若对方在任意长度为6的连续片段中已有4子且我方未阻断，选取最佳双点堵截。
     */
    private int[] blockFourInSix() {
        final int[][] DIRS = { {0, 1}, {1, 0}, {1, 1}, {1, -1} };
        int bestScorePair = -1;
        int[] bestPair = null;
        int bestScoreSingle = -1;
        int bestSingle = -1;

        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                for (int[] d : DIRS) {
                    int endRow = row + d[0] * 5;
                    int endCol = col + d[1] * 5;
                    if (!V1Board.isValidPosition(endRow, endCol)) {
                        continue; // 片段越界
                    }

                    // 收集该片段的颜色与索引
                    PieceColor[] seg = new PieceColor[6];
                    int[] idxs = new int[6];
                    for (int k = 0; k < 6; k++) {
                        int r = row + d[0] * k;
                        int c = col + d[1] * k;
                        seg[k] = v1Board.getColor(r, c);
                        idxs[k] = V1Board.toIndex(r, c);
                    }

                    // 1) 优先：检测连续四子，若两端都为空（且不是我方子），双端必堵
                    for (int start = 0; start <= 2; start++) {
                        boolean fourRun = true;
                        for (int t = 0; t < 4; t++) {
                            if (seg[start + t] != oppColor) { fourRun = false; break; }
                        }
                        if (!fourRun) continue;
                        int left = start - 1;
                        int right = start + 4;
                        if (left >= 0 && right < 6) {
                            boolean endsEmpty = (seg[left] == PieceColor.EMPTY) && (seg[right] == PieceColor.EMPTY);
                            boolean endsNotMine = (seg[left] != myColor) && (seg[right] != myColor);
                            if (endsEmpty && endsNotMine) {
                                int pA = idxs[left], pB = idxs[right];
                                int score = evaluateDefensePair(pA, pB);
                                if (score > bestScorePair) {
                                    bestScorePair = score;
                                    bestPair = new int[] { pA, pB };
                                }
                            }
                        }
                    }

                    // 2) 次优：一般4/6威胁（含一端被我方堵或中间型），选择最佳单点封堵作为第一子
                    int oppCount = 0, myCount = 0, emptyCount = 0;
                    int[] empties = new int[2];
                    for (int k = 0; k < 6; k++) {
                        if (seg[k] == oppColor) oppCount++;
                        else if (seg[k] == myColor) myCount++;
                        else if (seg[k] == PieceColor.EMPTY) {
                            if (emptyCount < 2) empties[emptyCount] = idxs[k];
                            emptyCount++;
                        }
                    }
                    if (oppCount == 4 && myCount <= 1 && emptyCount >= 1 && emptyCount <= 2) {
                        int localBest = -1, localScore = -1;
                        for (int i = 0; i < Math.min(emptyCount, 2); i++) {
                            int candIdxInSeg = -1;
                            for (int k = 0; k < 6; k++) { if (idxs[k] == empties[i]) { candIdxInSeg = k; break; } }
                            int s = evaluateDefenseSingle(empties[i]);
                            // 特例：两侧皆为对方子，中间为双空（opp,opp,EMPTY,EMPTY,opp,opp），鼓励在中间单点封堵
                            boolean middleGapPair = (seg[0] == oppColor && seg[1] == oppColor && seg[2] == PieceColor.EMPTY && seg[3] == PieceColor.EMPTY && seg[4] == oppColor && seg[5] == oppColor);
                            if (middleGapPair && (candIdxInSeg == 2 || candIdxInSeg == 3)) {
                                s += 3000; // 轻微加分，保持低优先级性质
                            }
                            // 邻接偏好：若该端外侧紧邻是我方子，则降低优先级；若外侧不是我方子，则略微提高
                            int outsideK = (candIdxInSeg == 0) ? -1 : (candIdxInSeg == 5 ? 6 : (candIdxInSeg < 3 ? -1 : 6));
                            // 上述粗略判定不够精确，改用方向计算真实外侧邻居
                            int outsideRow, outsideCol;
                            if (candIdxInSeg == 0) {
                                outsideRow = row - d[0]; outsideCol = col - d[1];
                            } else if (candIdxInSeg == 5) {
                                outsideRow = row + d[0] * 6; outsideCol = col + d[1] * 6;
                            } else {
                                outsideRow = Integer.MIN_VALUE; outsideCol = Integer.MIN_VALUE;
                            }
                            if (outsideRow != Integer.MIN_VALUE && V1Board.isValidPosition(outsideRow, outsideCol)) {
                                PieceColor outside = v1Board.getColor(outsideRow, outsideCol);
                                if (outside == myColor) s -= 5000; else s += 1000;
                            }
                            if (s > localScore) { localScore = s; localBest = empties[i]; }
                        }
                        if (localScore > bestScoreSingle) { bestScoreSingle = localScore; bestSingle = localBest; }
                    }
                }
            }
        }

        // 若发现连续四子且两端皆空，强制双端堵住
        if (bestPair != null) {
            return bestPair;
        }

        // 否则，固定最佳单点为第一子，再以快速 α-β 选择第二子
        if (bestSingle >= 0) {
            int second = -1;
            if (alphaBetaSearcher != null) {
                int[] picked = alphaBetaSearcher.searchWithForcedFirst(myColor, oppColor, bestSingle, 4);
                if (picked != null && picked.length >= 2) {
                    second = (picked[0] == bestSingle) ? picked[1] : picked[0];
                }
            }
            if (second < 0) {
                second = findBestAttackPosition(bestSingle);
            }
            if (second >= 0) {
                return new int[] { bestSingle, second };
            }
            // fallback: 任意其他空位
            for (int pos : v1Board.getCandidatePositions()) {
                if (pos != bestSingle && v1Board.isEmpty(pos)) {
                    return new int[] { bestSingle, pos };
                }
            }
            return new int[] { bestSingle, bestSingle }; // 极端兜底
        }

        return null;
    }

    /**
     * 严格双端封堵：仅当存在对方连续四子且两端皆为空（且非我方子）时返回两端防守对。
     * 其他情况不做处理（交由主逻辑）。
     */
    private int[] blockFourInSixPairStrict() {
        final int[][] DIRS = { {0, 1}, {1, 0}, {1, 1}, {1, -1} };
        int bestScorePair = -1;
        int[] bestPair = null;

        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                for (int[] d : DIRS) {
                    int endRow = row + d[0] * 5;
                    int endCol = col + d[1] * 5;
                    if (!V1Board.isValidPosition(endRow, endCol)) continue;

                    PieceColor[] seg = new PieceColor[6];
                    int[] idxs = new int[6];
                    for (int k = 0; k < 6; k++) {
                        int r = row + d[0] * k;
                        int c = col + d[1] * k;
                        seg[k] = v1Board.getColor(r, c);
                        idxs[k] = V1Board.toIndex(r, c);
                    }
                    // 检测连续四子，双端为空
                    for (int start = 0; start <= 2; start++) {
                        boolean fourRun = true;
                        for (int t = 0; t < 4; t++) {
                            if (seg[start + t] != oppColor) { fourRun = false; break; }
                        }
                        if (!fourRun) continue;
                        int left = start - 1;
                        int right = start + 4;
                        if (left >= 0 && right < 6) {
                            boolean endsEmpty = (seg[left] == PieceColor.EMPTY) && (seg[right] == PieceColor.EMPTY);
                            boolean endsNotMine = (seg[left] != myColor) && (seg[right] != myColor);
                            // 额外约束：两端外侧紧邻也不得为我方子，否则不强制双端堵
                            boolean outsideOk = true;
                            int outLeftRow = row + d[0] * (left - 1);
                            int outLeftCol = col + d[1] * (left - 1);
                            int outRightRow = row + d[0] * (right + 1);
                            int outRightCol = col + d[1] * (right + 1);
                            if (V1Board.isValidPosition(outLeftRow, outLeftCol)) {
                                if (v1Board.getColor(outLeftRow, outLeftCol) == myColor) outsideOk = false;
                            }
                            if (V1Board.isValidPosition(outRightRow, outRightCol)) {
                                if (v1Board.getColor(outRightRow, outRightCol) == myColor) outsideOk = false;
                            }
                            if (endsEmpty && endsNotMine && outsideOk) {
                                int pA = idxs[left], pB = idxs[right];
                                int score = evaluateDefensePair(pA, pB);
                                if (score > bestScorePair) {
                                    bestScorePair = score;
                                    bestPair = new int[] { pA, pB };
                                }
                            }
                        }
                    }
                }
            }
        }
        return bestPair;
    }

    /**
     * 评估双防守点价值，综合位置分与己方/对方增量。
     */
    private int evaluateDefensePair(int posA, int posB) {
        if (posA == posB) return Integer.MIN_VALUE;
        return evaluateDefenseSingle(posA) + evaluateDefenseSingle(posB);
    }

    private int evaluateDefenseSingle(int pos) {
        int incMine = v1Board.evaluateMoveIncrement(pos, myColor);
        int incOpp = v1Board.evaluateMoveIncrement(pos, oppColor);
        int positional = MoveGenerator.getPositionScore(V1Board.toRow(pos), V1Board.toCol(pos));
        return incMine * 2 + incOpp + positional;
    }

    /**
     * 初始化或更新路表
     */
    private void initOrUpdateLines(Move opponentMove) {
        if (myColor == null) return;

        if (!linesInitialized) {
            // 首次构建路表
            v1Board.buildLines(myColor);
            linesInitialized = true;
        } else if (opponentMove != null) {
            // 增量更新路表
            int pos1 = opponentMove.index1();
            int pos2 = opponentMove.index2();
            if (pos1 >= 0) {
                v1Board.updateLinesAfterMove(pos1, oppColor, myColor);
            }
            if (pos2 >= 0) {
                v1Board.updateLinesAfterMove(pos2, oppColor, myColor);
            }
        }
    }

    /**
     * 创建防守着法
     */
    private int[] createDefenseMove(List<Integer> defensePositions) {
        if (defensePositions.size() >= 2) {
            return new int[] { defensePositions.get(0), defensePositions.get(1) };
        } else if (defensePositions.size() == 1) {
            // 防守一个点，另一个点选择最佳位置
            int defPos = defensePositions.get(0);
            List<Integer> candidates = v1Board.getCandidatePositions();
            for (int pos : candidates) {
                if (pos != defPos && v1Board.isEmpty(pos)) {
                    return new int[] { defPos, pos };
                }
            }
        }
        return null;
    }

    /**
     * 同步基础棋盘到V1Board
     */
    // 已移除：syncBoard()（V1Board 与基础棋盘共享，无需显式同步）

    /**
     * 确定己方颜色
     */
    private void determineColor() {
        if (myColor != null)
            return;
        // 优先使用框架分配的颜色，避免通过棋子数猜测
        PieceColor assigned = this.getColor();
        if (assigned != null && assigned != PieceColor.EMPTY) {
            myColor = assigned;
            oppColor = (assigned == PieceColor.BLACK) ? PieceColor.WHITE : PieceColor.BLACK;
            return;
        }

        // 兜底：若框架未设定颜色，再用棋子数推断（开局有天元黑子）
        int pieceCount = countPieces();
        if (pieceCount <= 1) {
            myColor = PieceColor.WHITE;
            oppColor = PieceColor.BLACK;
        } else {
            myColor = PieceColor.BLACK;
            oppColor = PieceColor.WHITE;
        }
    }

    /**
     * 计算棋盘上的棋子数量
     */
    private int countPieces() {
        int count = 0;
        for (int i = 0; i < BOARD_SIZE * BOARD_SIZE; i++) {
            if (this.board.get(i) != PieceColor.EMPTY) {
                count++;
            }
        }
        return count;
    }

    /**
     * 安全回退着法（当其他方法失败时使用）
     */
    private Move fallbackMove() {
        List<Integer> candidates = v1Board.getCandidatePositions();
        int pos1 = -1, pos2 = -1;

        for (int pos : candidates) {
            if (v1Board.isEmpty(pos)) {
                if (pos1 < 0) {
                    pos1 = pos;
                } else {
                    pos2 = pos;
                    break;
                }
            }
        }

        // 如果候选位置不足，全盘搜索
        if (pos1 < 0 || pos2 < 0) {
            for (int i = 0; i < BOARD_SIZE * BOARD_SIZE; i++) {
                if (this.board.get(i) == PieceColor.EMPTY) {
                    if (pos1 < 0) {
                        pos1 = i;
                    } else if (i != pos1) {
                        pos2 = i;
                        break;
                    }
                }
            }
        }

        return new Move(pos1, pos2);
    }

    // ===================== 框架接口方法 =====================

    @Override
    public String name() {
        return AI_NAME;
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        // 重置状态
        this.board = new V1Board();
        this.v1Board = (V1Board) this.board;
        this.moveGenerator = new MoveGenerator(v1Board);
        int ttCap = Integer.getInteger("g08.tt.capacity", 200_000);
        this.transpositionTable = new TranspositionTable(ttCap);
        this.alphaBetaSearcher = new AlphaBetaSearcher(v1Board, moveGenerator, transpositionTable);
        this.threatSpaceSearcher = new ThreatSpaceSearcher(v1Board, moveGenerator);
        // 策略选择（多臂轮换）
        StrategyManager.Selection sel = StrategyManager.pickStrategyForNewGame();
        this.currentStrategyId = sel.id;
        this.myColor = null;
        this.oppColor = null;
        this.turnCount = 0;
        this.linesInitialized = false;
    }

    // 在 findNextMove 中统一记录最终决策（覆盖所有来源：开局库、胜着、防守、TBS、α-β、启发式）
    @Override
    public Move findNextMove(Move opponentMove) {
        turnCount++;

        // 处理对手着法
        if (opponentMove != null) {
            this.board.makeMove(opponentMove);
            int pos1 = opponentMove.index1();
            int pos2 = opponentMove.index2();
            if (pos1 >= 0) {
                v1Board.updateCandidatesAfterMove(pos1);
            }
            if (pos2 >= 0) {
                v1Board.updateCandidatesAfterMove(pos2);
            }
            // 对手画像记录已移除
        }

        determineColor();
        initOrUpdateLines(opponentMove);

        int[] bestMove = makeDecision();

        // 日志记录已移除

        Move move;
        if (bestMove != null && bestMove.length >= 2) {
            move = new Move(bestMove[0], bestMove[1]);
        } else {
            move = fallbackMove();
        }

        this.board.makeMove(move);
        int pos1 = move.index1();
        int pos2 = move.index2();
        v1Board.updateCandidatesAfterMove(pos1);
        if (pos2 >= 0) {
            v1Board.updateCandidatesAfterMove(pos2);
        }
        if (linesInitialized) {
            v1Board.updateLinesAfterMove(pos1, myColor, myColor);
            if (pos2 >= 0) {
                v1Board.updateLinesAfterMove(pos2, myColor, myColor);
            }
        }

        return move;
    }
}
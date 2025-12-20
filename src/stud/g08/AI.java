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
    private static final int TBS_MAX_DEPTH = 6;

    /** TBS默认时间预算（毫秒） */
    private static final long TBS_DEFAULT_TIME_LIMIT_MS = 700;

    // ===================== 成员变量 =====================

    /** V1扩展棋盘 */
    private V1Board v1Board;

    /** 着法生成器 */
    private MoveGenerator moveGenerator;

    /** α-β搜索器 */
    private AlphaBetaSearcher alphaBetaSearcher;

    /** 威胁空间搜索器 */
    private ThreatSpaceSearcher threatSpaceSearcher;

    /** 蒙特卡洛树搜索器（MCTS） */
    private MCTSSearcher mctsSearcher;
    /** 共享置换表（评估缓存） */
    private TranspositionTable transpositionTable;

    /** 是否启用MCTS（策略可调整） */
    private boolean useMCTS = true;
    /** MCTS 时间预算（毫秒，策略可调整） */
    private int mctsTimeLimitMs = 600;
    /** TBS 时间预算（毫秒，可配置） */
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

        // 步骤3：低优先级开局库（仅在早期且确认无紧急威胁时使用）
        if (v1Board.getPieceCount() <= 8) {
            // 再次确认无致命/高威胁，防止漏掉对手四连
            ThreatEvaluator teEarly = moveGenerator.getThreatEvaluator();
            List<Integer> defEarly = teEarly.getDefensePositions(oppColor);
            if (teEarly.getCriticalThreatCount() > 0 || teEarly.getThreatCount() > 0) {
                if (!defEarly.isEmpty()) return createDefenseMove(defEarly);
                List<Integer> fb = findCriticalDefenseFallback();
                if (!fb.isEmpty()) return createDefenseMove(fb);
            } else {
                int[] book = OpeningBook.pickOpeningMove(v1Board, myColor, turnCount);
                if (book != null) {
                    return book;
                }
            }
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

        // 步骤3.6：尝试MCTS（仿真评估）
        int[] mctsMove = searchByMCTS();
        if (mctsMove != null) {
            return mctsMove;
        }

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

    /**
     * MCTS 搜索入口：在时间预算内返回仿真收益最高的着法
     */
    private int[] searchByMCTS() {
        if (!useMCTS || mctsSearcher == null) return null;
        return mctsSearcher.search(myColor, oppColor, mctsTimeLimitMs);
    }

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
        this.useMCTS = sel.strategy.useMCTS;
        this.mctsTimeLimitMs = sel.strategy.mctsTimeMs;
        this.mctsSearcher = new MCTSSearcher(v1Board, moveGenerator, transpositionTable);
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
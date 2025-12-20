package stud.g09;

import core.board.PieceColor;
import core.game.Game;
import core.game.Move;
import stud.g09.TranspositionTable;

import java.util.List;

/**
 * V2阶段智能AI
 * <p>
 * 【功能特性】
 * 1. 胜着检测：检测并优先选择能直接获胜的着法
 * 2. 威胁防守：检测对手威胁并进行有效防守
 * 3. 智能着法生成：基于棋型评估生成和排序着法
 * 4. 【V2新增】路表快速评估：基于有效路的增量评估
 * 5. 【V2新增】α-β剪枝搜索：迭代加深搜索最优着法
 * <p>
 * 【决策流程】
 * 1. 检测己方胜着 → 如有，立即执行
 * 2. 检测对方威胁 → 如有紧急威胁，优先防守
 * 3. 【V2】α-β搜索 → 搜索最优着法
 * 4. 回退到启发式着法生成
 * <p>
 * 【设计说明】
 * - 继承自框架的AI基类
 * - 使用V1Board扩展棋盘功能 + 路表
 * - 集成α-β剪枝搜索
 * 
 * @author V2阶段开发
 * @version 2.0
 */
public class AI extends core.player.AI {

    // ===================== 常量定义 =====================

    /** 棋盘大小 */
    private static final int BOARD_SIZE = 19;

    /** AI名称 */
//    private static final String AI_NAME = "G06-V2-AlphaBetaAI";
    private static final String AI_NAME = "G09";

    /** 是否启用α-β搜索 */
    private static final boolean USE_ALPHA_BETA = true;

    /** 是否启用威胁空间搜索（TBS） */
    private static final boolean USE_THREAT_SPACE = true;

    /** TBS搜索最大深度（以我方着法层计） */
    private static final int TBS_MAX_DEPTH = 6;

    /** TBS单步时间预算（毫秒） */
    private static final long TBS_TIME_LIMIT_MS = 700;

    // ===================== 成员变量 =====================

    /** V1扩展棋盘 */
    private V1Board v1Board;

    /** 着法生成器 */
    private MoveGenerator moveGenerator;

    /** α-β搜索器 */
    private AlphaBetaSearcher alphaBetaSearcher;

    /** 共享置换表 */
    private TranspositionTable transpositionTable;

    /** 威胁空间搜索器 */
    private ThreatSpaceSearcher threatSpaceSearcher;

    /** 己方颜色 */
    private PieceColor myColor;

    /** 对方颜色 */
    private PieceColor oppColor;

    /** 是否为第一步 */
    private boolean isFirstMove;

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
    @Override
    public Move findNextMove(Move opponentMove) {
        turnCount++;

        // 1. 处理对手着法
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
        }

        // 2. 确定己方颜色
        determineColor();

        // 3. 初始化/更新路表
        initOrUpdateLines(opponentMove);

        // 4. 执行决策
        int[] bestMove = makeDecision();

        // 5. 构建并返回着法
        Move move;
        if (bestMove != null && bestMove.length >= 2) {
            move = new Move(bestMove[0], bestMove[1]);
        } else {
            // 安全回退：随机选择
            move = fallbackMove();
        }

        // 6. 更新己方棋盘状态和路表
        this.board.makeMove(move);
        int pos1 = move.index1();
        int pos2 = move.index2();
        v1Board.updateCandidatesAfterMove(pos1);
        if (pos2 >= 0) {
            v1Board.updateCandidatesAfterMove(pos2);
        }
        // 更新路表
        if (linesInitialized) {
            v1Board.updateLinesAfterMove(pos1, myColor, myColor);
            if (pos2 >= 0) {
                v1Board.updateLinesAfterMove(pos2, myColor, myColor);
            }
        }

        return move;
    }

    /**
     * 核心决策逻辑（V2版本：集成α-β搜索 + 精细化威胁）
     */
    private int[] makeDecision() {
        // 同步棋盘状态到V1Board
        syncBoard();

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

        // 步骤2.5：【V2新增】检测对方复合威胁（双活三/冲四活三）
        int[] compoundDefense = detectCompoundThreat();
        if (compoundDefense != null) {
            return compoundDefense;
        }

        // 步骤3：威胁空间搜索（强制胜负链）
        int[] tbsMove = searchThreatSpace();
        if (tbsMove != null) {
            return tbsMove;
        }

        // 步骤4：使用α-β搜索寻找最优着法
        if (USE_ALPHA_BETA && alphaBetaSearcher != null) {
            int[] searchMove = alphaBetaSearcher.search(myColor, oppColor);
//            System.out.printf("nodes=%d, cutoffs=%d, rate=%.2f%n",
//            alphaBetaSearcher.getNodeCount(),
//            alphaBetaSearcher.getCutoffCount(),
//            alphaBetaSearcher.getCutoffRate());
            if (searchMove != null) {
                return searchMove;
            }
        }

        // 步骤5：回退到启发式着法生成
        // 威胁数<=2时优先防守
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
     * 威胁空间搜索入口
     */
    private int[] searchThreatSpace() {
        if (!USE_THREAT_SPACE || threatSpaceSearcher == null) {
            return null;
        }
        return threatSpaceSearcher.searchForcingWin(myColor, oppColor, TBS_MAX_DEPTH, TBS_TIME_LIMIT_MS);
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
    private void syncBoard() {
        // V1Board继承自Board，共享状态
        // 确保候选位置已更新
    }

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
        this.transpositionTable = new TranspositionTable();
        this.alphaBetaSearcher = new AlphaBetaSearcher(v1Board, moveGenerator, transpositionTable);
        this.threatSpaceSearcher = new ThreatSpaceSearcher(v1Board, moveGenerator, transpositionTable);
        this.myColor = null;
        this.oppColor = null;
        this.isFirstMove = true;
        this.turnCount = 0;
        this.linesInitialized = false;
    }
}
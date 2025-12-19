package stud.g06;

import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.List;

/**
 * V1阶段基础智能AI
 * 
 * 【功能特性】
 * 1. 胜着检测：检测并优先选择能直接获胜的着法
 * 2. 威胁防守：检测对手威胁并进行有效防守
 * 3. 智能着法生成：基于棋型评估生成和排序着法
 * 
 * 【决策流程】
 * 1. 检测己方胜着 → 如有，立即执行
 * 2. 检测对方威胁 → 如有紧急威胁，优先防守
 * 3. 生成候选着法 → 按评估分值选择最优着法
 * 
 * 【设计说明】
 * - 继承自框架的AI基类
 * - 使用V1Board扩展棋盘功能
 * - 支持与V2阶段的α-β剪枝集成
 * 
 * @author V1阶段开发
 * @version 1.0
 */
public class AI extends core.player.AI {

    // ===================== 常量定义 =====================

    /** 棋盘大小 */
    private static final int BOARD_SIZE = 19;

    /** AI名称 */
    private static final String AI_NAME = "G06-V1-SmartAI";

    // ===================== 成员变量 =====================

    /** V1扩展棋盘 */
    private V1Board v1Board;

    /** 着法生成器 */
    private MoveGenerator moveGenerator;

    /** 己方颜色 */
    private PieceColor myColor;

    /** 对方颜色 */
    private PieceColor oppColor;

    /** 是否为第一步 */
    private boolean isFirstMove;

    /** 回合计数 */
    private int turnCount;

    // ===================== 核心方法 =====================

    /**
     * 【AI决策核心算法】
     * 根据当前局面找出最佳着法
     * 
     * 伪代码：
     * ```
     * function findNextMove(opponentMove):
     * // 1. 更新棋盘状态
     * board.makeMove(opponentMove)
     * updateCandidates(opponentMove)
     * 
     * // 2. 检测胜着
     * winMove = moveGenerator.findWinningMove(myColor)
     * if winMove != null:
     * return winMove
     * 
     * // 3. 检测并防守威胁
     * threats = threatEvaluator.detectThreats(oppColor)
     * if hasCriticalThreat(threats):
     * defenseMove = generateDefenseMove(threats)
     * return defenseMove
     * 
     * // 4. 生成最优着法
     * moves = moveGenerator.generateMoves(myColor, oppColor)
     * bestMove = selectBestMove(moves)
     * 
     * // 5. 执行并返回
     * board.makeMove(bestMove)
     * return bestMove
     * ```
     * 
     * @param opponentMove 对手的着法
     * @return 己方着法
     */
    @Override
    public Move findNextMove(Move opponentMove) {
        turnCount++;

        // 1. 处理对手着法
        // 注意：Move类使用index1()和index2()获取位置索引
        if (opponentMove != null) {
            this.board.makeMove(opponentMove);
            // 更新V1Board的候选位置
            int pos1 = opponentMove.index1();
            int pos2 = opponentMove.index2();
            if (pos1 >= 0)
                v1Board.updateCandidatesAfterMove(pos1);
            if (pos2 >= 0)
                v1Board.updateCandidatesAfterMove(pos2);
        }

        // 2. 确定己方颜色
        determineColor();

        // 3. 执行决策
        int[] bestMove = makeDecision();

        // 4. 构建并返回着法
        Move move;
        if (bestMove != null && bestMove.length >= 2) {
            move = new Move(bestMove[0], bestMove[1]);
        } else {
            // 安全回退：随机选择
            move = fallbackMove();
        }

        // 5. 更新己方棋盘状态
        this.board.makeMove(move);
        v1Board.updateCandidatesAfterMove(move.index1());
        if (move.index2() >= 0) {
            v1Board.updateCandidatesAfterMove(move.index2());
        }

        return move;
    }

    /**
     * 核心决策逻辑
     */
    private int[] makeDecision() {
        // 同步棋盘状态到V1Board
        syncBoard();

        // 步骤1：检测胜着
        int[] winMove = moveGenerator.findWinningMove(myColor);
        if (winMove != null) {
            return winMove;
        }

        // 步骤2：检测并处理威胁
        ThreatEvaluator threatEval = moveGenerator.getThreatEvaluator();
        List<Integer> defensePos = threatEval.getDefensePositions(oppColor);
        int threatCount = threatEval.getThreatCount();

        // 威胁处理策略：
        // 1) 有致命威胁（冲四/活四）必须防
        // 2) 威胁数<=2（含活三等高威胁）必须优先防
        if (!defensePos.isEmpty()) {
            if (threatEval.getCriticalThreatCount() > 0 || threatCount <= 2) {
                return createDefenseMove(defensePos);
            }
        }

        // 步骤3：生成并选择最优着法
        List<int[]> moves = moveGenerator.generateMoves(myColor, oppColor);
        if (!moves.isEmpty()) {
            return moves.get(0); // 返回最高分着法
        }

        return null;
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

        // 根据先后手确定颜色
        // 先手为黑，后手为白
        if (turnCount == 1) {
            // 第一步，检查是否已有棋子
            int pieceCount = countPieces();
            if (pieceCount == 0) {
                myColor = PieceColor.BLACK;
                oppColor = PieceColor.WHITE;
            } else {
                myColor = PieceColor.WHITE;
                oppColor = PieceColor.BLACK;
            }
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
                    } else if (pos2 < 0 && i != pos1) {
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
        this.myColor = null;
        this.oppColor = null;
        this.isFirstMove = true;
        this.turnCount = 0;
    }
}
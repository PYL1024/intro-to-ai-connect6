package stud.g99;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;
//import core.player.AI; // 导入父类，避免命名混淆
import stud.util.ShuffleUtil;

import java.util.List;

/**
 * G99 洗牌版 AI：优先从中心13x13区域洗牌选位，失败后全局洗牌
 * 类名改为 G99AI 避免与父类 core.player.AI 重名，彻底解决命名冲突
 */
public class AI extends core.player.AI {
    // 棋盘基础常量
    private static final int BOARD_SIZE = 19;
    private static final int CENTER_SIZE = 13;
    // 中心13x13区域的坐标范围（19x19棋盘的(3,3)到(15,15)）
    private static final int CENTER_MIN_X = 3;
    private static final int CENTER_MAX_X = 15;
    private static final int CENTER_MIN_Y = 3;
    private static final int CENTER_MAX_Y = 15;
    // 中心区域最大尝试次数
    private static final int MAX_CENTER_ATTEMPTS = 10;

    @Override
    public Move findNextMove(Move opponentMove) {
        // 执行对手的落子
        this.board.makeMove(opponentMove);

        int attemptCount = 0;
        boolean useCenterArea = true; // 优先使用中心区域
        Integer index1 = null;
        Integer index2 = null;

        while (true) {
            List<Integer> shuffledPositions;
            if (useCenterArea) {
                // 1. 计算中心区域的起始/结束索引
                int centerStartIndex = CENTER_MIN_X * BOARD_SIZE + CENTER_MIN_Y;
                int centerEndIndex = CENTER_MAX_X * BOARD_SIZE + CENTER_MAX_Y;
                // 中心区域洗牌
                shuffledPositions = ShuffleUtil.shufflePositions(centerStartIndex, centerEndIndex, BOARD_SIZE);
                attemptCount++;

                // 中心区域尝试10次失败，切换到全局
                if (attemptCount >= MAX_CENTER_ATTEMPTS) {
                    useCenterArea = false;
                }
            } else {
                // 2. 全局棋盘洗牌
                shuffledPositions = ShuffleUtil.shuffleGlobalPositions(BOARD_SIZE);
            }

            // 3. 从洗牌列表选第一个有效空位（index1）
            index1 = getFirstEmptyPosition(shuffledPositions);
            if (index1 == null) {
                continue; // 无空位则重新洗牌
            }

            // 4. 从洗牌列表选第二个不同的有效空位（index2）
            index2 = getSecondEmptyPosition(shuffledPositions, index1);
            if (index2 != null) {
                break; // 找到两个有效位置，退出循环
            }
        }

        // 执行当前AI的落子并返回
        Move aiMove = new Move(index1, index2);
        this.board.makeMove(aiMove);
        return aiMove;
    }

    /**
     * 从洗牌列表中获取第一个空位的索引
     */
    private Integer getFirstEmptyPosition(List<Integer> shuffledPositions) {
        for (int pos : shuffledPositions) {
            if (this.board.get(pos) == PieceColor.EMPTY) {
                return pos;
            }
        }
        return null;
    }

    /**
     * 从洗牌列表中获取第二个与index1不同的空位索引
     */
    private Integer getSecondEmptyPosition(List<Integer> shuffledPositions, int index1) {
        for (int pos : shuffledPositions) {
            if (pos != index1 && this.board.get(pos) == PieceColor.EMPTY) {
                return pos;
            }
        }
        return null;
    }

    @Override
    public String name() {
        return "G99-ShuffleAI"; // 洗牌版AI名称标识
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        // 重置棋盘，避免跨游戏状态污染
        this.board = new Board();
    }
}
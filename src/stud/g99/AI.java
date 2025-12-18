package stud.g99;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.Random;

public class AI extends core.player.AI {
    private static final int BOARD_SIZE = 19;
    private static final int CENTER_SIZE = 13;
    private static final int MAX_INDEX = BOARD_SIZE * BOARD_SIZE - 1;
    // 13x13中心区域的起始和结束索引
    private static final int CENTER_START = 3 * BOARD_SIZE + 3;  // (3,3)
    private static final int CENTER_END = 15 * BOARD_SIZE + 15;  // (15,15)

    @Override
    public Move findNextMove(Move opponentMove) {
        this.board.makeMove(opponentMove);
        Random rand = new Random();
        int attemptCount = 0;
        boolean useCenter = true;

        while (true) {
            int index1, index2;

            if (useCenter) {
                // 在13x13中心区域选择
                index1 = CENTER_START + rand.nextInt(CENTER_SIZE * CENTER_SIZE);
                // 确保索引在中心区域内
                index1 = Math.min(index1, CENTER_END);

                index2 = CENTER_START + rand.nextInt(CENTER_SIZE * CENTER_SIZE);
                index2 = Math.min(index2, CENTER_END);

                attemptCount++;
                // 10次尝试失败后，切换到整个棋盘
                if (attemptCount >= 10) {
                    useCenter = false;
                }
            } else {
                // 在整个19x19棋盘选择
                index1 = rand.nextInt(MAX_INDEX + 1);
                index2 = rand.nextInt(MAX_INDEX + 1);
            }

            // 检查两个位置是否都是空位且不相同
            if (index1 != index2 &&
                    this.board.get(index1) == PieceColor.EMPTY &&
                    this.board.get(index2) == PieceColor.EMPTY) {

                Move move = new Move(index1, index2);
                this.board.makeMove(move);
                return move;
            }
        }
    }

    @Override
    public String name() {
        return "G99-Random3";
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
    }
}
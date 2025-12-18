package stud.g77;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.Random;

public class AI extends core.player.AI {
    private int steps = 0;
    private static final int BOARD_SIZE = 19;
    private static final int MAX_INDEX = BOARD_SIZE * BOARD_SIZE - 1;

    @Override
    public Move findNextMove(Move opponentMove) {
        // 记录对手的走法
        this.board.makeMove(opponentMove);
        Random rand = new Random();

        // 在19*19棋盘范围内随机选择两个不同的空位
        while (true) {
            int index1 = rand.nextInt(MAX_INDEX + 1);
            int index2 = rand.nextInt(MAX_INDEX + 1);

            // 检查两个位置是否都是空位且不相同
            if (index1 != index2 &&
                    this.board.get(index1) == PieceColor.EMPTY &&
                    this.board.get(index2) == PieceColor.EMPTY) {

                Move move = new Move(index1, index2);
                this.board.makeMove(move);
                steps++;
                return move;
            }
        }
    }

    @Override
    public String name() {
        return "G77-Random1";
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
        steps = 0;
    }
}
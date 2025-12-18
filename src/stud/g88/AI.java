package stud.g88;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AI extends core.player.AI {
    private int steps = 0;
    private static final int BOARD_SIZE = 19;
    private static final int MAX_INDEX = BOARD_SIZE * BOARD_SIZE - 1;

    // 8个方向的偏移量（上、下、左、右、四个对角线）
    private static final int[] DX = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final int[] DY = {-1, 0, 1, -1, 1, -1, 0, 1};

    @Override
    public Move findNextMove(Move opponentMove) {
        this.board.makeMove(opponentMove);
        Random rand = new Random();

        // 随机选择第一个子的位置
        int index1;
        do {
            index1 = rand.nextInt(MAX_INDEX + 1);
        } while (this.board.get(index1) != PieceColor.EMPTY);

        // 计算第一个子的坐标
        int x1 = index1 / BOARD_SIZE;
        int y1 = index1 % BOARD_SIZE;

        // 查找相邻的空位
        List<Integer> adjacentEmpty = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int x2 = x1 + DX[i];
            int y2 = y1 + DY[i];

            // 检查是否在棋盘范围内
            if (x2 >= 0 && x2 < BOARD_SIZE && y2 >= 0 && y2 < BOARD_SIZE) {
                int index2 = x2 * BOARD_SIZE + y2;
                if (this.board.get(index2) == PieceColor.EMPTY) {
                    adjacentEmpty.add(index2);
                }
            }
        }

        // 选择第二个子的位置
        int index2;
        if (!adjacentEmpty.isEmpty()) {
            // 有相邻空位，随机选择一个
            index2 = adjacentEmpty.get(rand.nextInt(adjacentEmpty.size()));
        } else {
            // 无相邻空位，在整个棋盘随机选择
            do {
                index2 = rand.nextInt(MAX_INDEX + 1);
            } while (index2 == index1 || this.board.get(index2) != PieceColor.EMPTY);
        }

        Move move = new Move(index1, index2);
        this.board.makeMove(move);
        steps++;
        return move;
    }

    @Override
    public String name() {
        return "G88-Random2";
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
        steps = 0;
    }
}
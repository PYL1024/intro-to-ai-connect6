package stud.g88;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;
//import core.player.AI;
import stud.util.ShuffleUtil;
import java.util.List;

public class AI extends core.player.AI {
    private int steps = 0;
    private static final int BOARD_SIZE = 19;
    private static final int[] DX = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final int[] DY = {-1, 0, 1, -1, 1, -1, 0, 1};

    @Override
    public Move findNextMove(Move opponentMove) {
        this.board.makeMove(opponentMove);

        // 1. 对全局位置洗牌
        List<Integer> globalShuffled = ShuffleUtil.shuffleGlobalPositions(BOARD_SIZE);

        // 2. 选第一个空位作为第一个落子
        Integer index1 = null;
        for (int pos : globalShuffled) {
            if (this.board.get(pos) == PieceColor.EMPTY) {
                index1 = pos;
                break;
            }
        }
        if (index1 == null) return null;

        // 3. 计算相邻3x3区域的索引范围并洗牌
        int x1 = index1 / BOARD_SIZE;
        int y1 = index1 % BOARD_SIZE;
        int minX = Math.max(0, x1 - 1);
        int maxX = Math.min(BOARD_SIZE - 1, x1 + 1);
        int minY = Math.max(0, y1 - 1);
        int maxY = Math.min(BOARD_SIZE - 1, y1 + 1);
        int startIndex = minX * BOARD_SIZE + minY;
        int endIndex = maxX * BOARD_SIZE + maxY;
        List<Integer> adjacentShuffled = ShuffleUtil.shufflePositions(startIndex, endIndex, BOARD_SIZE);

        // 4. 从相邻洗牌列表选第二个落子
        Integer index2 = null;
        for (int pos : adjacentShuffled) {
            if (pos != index1 && this.board.get(pos) == PieceColor.EMPTY) {
                index2 = pos;
                break;
            }
        }

        // 5. 相邻无空位则从全局选
        if (index2 == null) {
            for (int pos : globalShuffled) {
                if (pos != index1 && this.board.get(pos) == PieceColor.EMPTY) {
                    index2 = pos;
                    break;
                }
            }
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
package stud.g08;

import core.board.PieceColor;
import java.util.List;

/**
 * 简易开局库：在前几回合提供稳健开局着法。
 */
public class OpeningBook {
    /** 中心位置索引（19x19 棋盘） */
    private static final int CENTER = V1Board.toIndex(9, 9);

    /** 返回开局着法（如无匹配则返回 null）。优先级低，仅在无紧急威胁且局面很早时调用。 */
    public static int[] pickOpeningMove(V1Board board, PieceColor myColor, int turnCount) {
        int pieceCount = board.getPieceCount();
        if (pieceCount > 8) return null; // 仅早期使用

        // 模板1：白方首手（先下后手规则下首手为白）
        if (myColor == PieceColor.WHITE && pieceCount <= 1) {
            int a = tryEmpty(board, 9, 9 + 1);
            int b = tryEmpty(board, 9 + 1, 9);
            if (a >= 0 && b >= 0) return new int[]{a, b};
        }

        // 模板2：白方第二手，强化中心菱形
        if (myColor == PieceColor.WHITE && pieceCount <= 3) {
            int a = tryEmpty(board, 8, 9);
            int b = tryEmpty(board, 9, 8);
            if (a >= 0 && b >= 0) return new int[]{a, b};
        }

        // 模板3：黑方早期靠近中心的“十字”
        if (myColor == PieceColor.BLACK && pieceCount < 6) {
            int a = tryEmpty(board, 9, 8);
            int b = tryEmpty(board, 8, 9);
            if (a >= 0 && b >= 0) return new int[]{a, b};
        }

        // 模板4：中心对角菱形（任意颜色，早期）
        if (pieceCount < 6) {
            int a = tryEmpty(board, 8, 8);
            int b = tryEmpty(board, 10, 10);
            if (a >= 0 && b >= 0) return new int[]{a, b};
        }

        // 兜底：选择候选中距离中心最近的两个空点
        List<Integer> cands = board.getCandidatePositions();
        int best1 = -1, best2 = -1; int d1 = Integer.MAX_VALUE, d2 = Integer.MAX_VALUE;
        for (int p : cands) {
            if (!board.isEmpty(p)) continue;
            int d = manhattan(p, CENTER);
            if (d < d1) { d2 = d1; best2 = best1; d1 = d; best1 = p; }
            else if (d < d2 && p != best1) { d2 = d; best2 = p; }
        }
        if (best1 >= 0 && best2 >= 0) return new int[]{best1, best2};
        return null;
    }

    private static int tryEmpty(V1Board b, int r, int c) {
        if (!V1Board.isValidPosition(r, c)) return -1;
        int p = V1Board.toIndex(r, c);
        return b.isEmpty(p) ? p : -1;
    }

    private static int manhattan(int idx, int center) {
        int r1 = V1Board.toRow(idx), c1 = V1Board.toCol(idx);
        int r2 = V1Board.toRow(center), c2 = V1Board.toCol(center);
        return Math.abs(r1 - r2) + Math.abs(c1 - c2);
    }
}

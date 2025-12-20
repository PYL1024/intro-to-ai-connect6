package stud.g08;

import core.board.PieceColor;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * g08 共享置换表（TT）：用于缓存局面评估/仿真结果。
 */
public class TranspositionTable {
    public static class TTEntry {
        public int value;      // 评估分值
        public int depth;      // 可选：记录评估深度
        public int[] bestMove; // 可选：推荐着法
    }

    private static final int BOARD_SIZE = 19 * 19;
    private static final long[][] ZOBRIST = new long[BOARD_SIZE][3];

    static {
        Random rnd = new Random(20251220L);
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int c = 0; c < 3; c++) {
                ZOBRIST[i][c] = rnd.nextLong();
            }
        }
    }

    private final Map<Long, TTEntry> evalTable = new HashMap<>();

    public long computeInitialKey(V1Board board) {
        long key = 0L;
        for (int pos = 0; pos < BOARD_SIZE; pos++) {
            PieceColor c = board.get(pos);
            int idx = colorIndex(c);
            key ^= ZOBRIST[pos][idx];
        }
        return key;
    }

    public long xorZobrist(long key, int pos, PieceColor color) {
        int idx = colorIndex(color);
        return key ^ ZOBRIST[pos][idx];
    }

    private int colorIndex(PieceColor c) {
        if (c == PieceColor.BLACK) return 1;
        if (c == PieceColor.WHITE) return 2;
        return 0;
    }

    public TTEntry probeEval(long key, int requiredDepth) {
        TTEntry e = evalTable.get(key);
        if (e != null && e.depth >= requiredDepth) return e;
        return null;
    }

    public void storeEval(long key, int value, int depth, int[] bestMove) {
        TTEntry e = evalTable.get(key);
        if (e == null || depth >= e.depth) {
            e = new TTEntry();
            evalTable.put(key, e);
        }
        e.value = value;
        e.depth = depth;
        e.bestMove = bestMove;
    }
}

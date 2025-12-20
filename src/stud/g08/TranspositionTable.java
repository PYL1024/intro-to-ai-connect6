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
        public Flag flag;      // 边界类型
    }

    public enum Flag { EXACT, LOWER, UPPER }

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

    /** 容量上限（可调）；超出后按简单替换策略（低深度优先淘汰）。 */
    private final int capacity;

    public TranspositionTable() {
        this(200_000);
    }

    public TranspositionTable(int capacity) {
        this.capacity = capacity;
    }

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

    public void storeEval(long key, int value, int depth, int[] bestMove, Flag flag) {
        TTEntry e = evalTable.get(key);
        if (e == null) {
            // 容量控制：若超限，尝试淘汰一个浅深度条目
            if (evalTable.size() >= capacity) {
                evictShallowEntry();
            }
            e = new TTEntry();
            evalTable.put(key, e);
        } else if (depth < e.depth) {
            // 仅当新深度不低于旧深度时覆盖
            return;
        }
        e.value = value;
        e.depth = depth;
        e.bestMove = bestMove;
        e.flag = flag;
    }

    /** 简单淘汰策略：移除一个最浅深度的条目。 */
    private void evictShallowEntry() {
        Long targetKey = null;
        int shallow = Integer.MAX_VALUE;
        for (Map.Entry<Long, TTEntry> en : evalTable.entrySet()) {
            if (en.getValue().depth < shallow) {
                shallow = en.getValue().depth;
                targetKey = en.getKey();
            }
        }
        if (targetKey != null) {
            evalTable.remove(targetKey);
        }
    }
}

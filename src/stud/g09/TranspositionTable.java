package stud.g09;

import core.board.PieceColor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 共享置换表（α-β与TBS共用）。
 * - evalTable：存储局面评估（值、深度、上下界标记、主变化着法）
 * - threatTable：存储TBS强制胜负搜索结果（true=先手可逼胜）
 * - defenseCache：存储TBS下的防守着法列表，减少重复生成
 * 置换表的Zobrist键与棋盘19x19对应，颜色索引：0=EMPTY，1=BLACK，2=WHITE。
 */
public class TranspositionTable {

    /** 评估置换表条目 */
    public static class TTEntry {
        public enum Flag { EXACT, LOWER, UPPER }
        public int value;
        public int depth;
        public Flag flag;
        public int[] bestMove;
    }

    private static final int BOARD_SIZE = 19 * 19;
    private static final long[][] ZOBRIST = new long[BOARD_SIZE][3];

    static {
        Random rnd = new Random(20251220L); // 固定种子确保α-β与TBS一致
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int c = 0; c < 3; c++) {
                ZOBRIST[i][c] = rnd.nextLong();
            }
        }
    }

    private final Map<Long, TTEntry> evalTable = new HashMap<>();
    private final Map<Long, Boolean> threatTable = new HashMap<>();
    private final Map<Long, List<int[]>> defenseCache = new HashMap<>();

    /** 计算当前棋盘的Zobrist键。 */
    public long computeInitialKey(V1Board board) {
        long key = 0L;
        for (int pos = 0; pos < BOARD_SIZE; pos++) {
            PieceColor c = board.get(pos);
            int idx = colorIndex(c);
            key ^= ZOBRIST[pos][idx];
        }
        return key;
    }

    /** 按位置/颜色异或更新键（落子或回滚时调用）。 */
    public long xorZobrist(long key, int pos, PieceColor color) {
        int idx = colorIndex(color);
        return key ^ ZOBRIST[pos][idx];
    }

    private int colorIndex(PieceColor c) {
        if (c == PieceColor.BLACK) return 1;
        if (c == PieceColor.WHITE) return 2;
        return 0;
    }

    // ============== α-β评估表 ==============
    public TTEntry probeEval(long key, int requiredDepth) {
        TTEntry e = evalTable.get(key);
        if (e != null && e.depth >= requiredDepth) {
            return e;
        }
        return null;
    }

    public void storeEval(long key, int value, int depth, TTEntry.Flag flag, int[] bestMove) {
        TTEntry e = evalTable.get(key);
        if (e == null || depth >= e.depth) {
            e = new TTEntry();
            evalTable.put(key, e);
        }
        e.value = value;
        e.depth = depth;
        e.flag = flag;
        e.bestMove = bestMove;
    }

    // ============== TBS结果表 ==============
    public Boolean probeThreat(long key) {
        return threatTable.get(key);
    }

    public void storeThreat(long key, boolean result) {
        threatTable.put(key, result);
    }

    // ============== TBS防守缓存 ==============
    public List<int[]> probeDefense(long key) {
        return defenseCache.get(key);
    }

    public void storeDefense(long key, List<int[]> defenses) {
        defenseCache.put(key, defenses);
    }

    /** 清理全部缓存（新对局时调用）。 */
    public void clearAll() {
        evalTable.clear();
        threatTable.clear();
        defenseCache.clear();
    }
}

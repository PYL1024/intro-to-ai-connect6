package stud.g08;

import core.board.PieceColor;

import java.util.ArrayList;
import java.util.List;

/**
 * 威胁分析器 - 精细化威胁检测
 * 支持检测：跳活三、双活三、双冲四等复合威胁
 */
public class ThreatAnalyzer {

    private static final int BOARD_SIZE = 19;

    /**
     * 分析落子后形成的所有威胁
     * @param board 当前棋盘
     * @param row 落子行
     * @param col 落子列
     * @param color 落子颜色
     * @return 威胁分析结果
     */
    public static ThreatResult analyzeThreat(V1Board board, int row, int col, PieceColor color) {
        ThreatResult result = new ThreatResult();

        // 分析四个方向
        for (Direction dir : Direction.values()) {
            PatternInfo info = analyzeDirection(board, row, col, color, dir);
            result.addPattern(dir, info);
        }

        // 检测复合威胁
        result.detectCompoundThreats();

        return result;
    }

    /**
     * 分析单个方向的棋型
     */
    private static PatternInfo analyzeDirection(V1Board board, int row, int col, PieceColor color, Direction dir) {
        int dx = dir.dx;
        int dy = dir.dy;

        // 统计正反两个方向的情况
        int countPos = 0, countNeg = 0;  // 己方棋子数
        int emptyPos = 0, emptyNeg = 0;  // 空位数
        int gapsPos = 0, gapsNeg = 0;    // 间隙数（中间的空位）
        boolean blockedPos = false, blockedNeg = false;  // 是否被堵
        int[] gapPositionPos = new int[2];  // 间隙位置 [row, col]
        int[] gapPositionNeg = new int[2];
        boolean hasGapPos = false, hasGapNeg = false;

        // 正方向扫描（最多扫描6格）
        int prevEmpty = 0;
        for (int i = 1; i <= 6; i++) {
            int nr = row + i * dx;
            int nc = col + i * dy;
            if (!isValid(nr, nc)) {
                blockedPos = true;
                break;
            }
            PieceColor c = board.getColor(nr, nc);
            if (c == PieceColor.EMPTY) {  // 空位
                if (countPos == 0) {
                    emptyPos++;
                } else {
                    if (prevEmpty == 0) {
                        gapsPos++;
                        hasGapPos = true;
                        gapPositionPos[0] = nr;
                        gapPositionPos[1] = nc;
                    }
                    prevEmpty++;
                    if (prevEmpty >= 2) break;  // 连续两个空位，停止
                }
            } else if (c == color) {  // 己方棋子
                countPos++;
                prevEmpty = 0;
            } else {  // 对方棋子
                blockedPos = true;
                break;
            }
        }

        // 反方向扫描
        prevEmpty = 0;
        for (int i = 1; i <= 6; i++) {
            int nr = row - i * dx;
            int nc = col - i * dy;
            if (!isValid(nr, nc)) {
                blockedNeg = true;
                break;
            }
            PieceColor c = board.getColor(nr, nc);
            if (c == PieceColor.EMPTY) {
                if (countNeg == 0) {
                    emptyNeg++;
                } else {
                    if (prevEmpty == 0) {
                        gapsNeg++;
                        hasGapNeg = true;
                        gapPositionNeg[0] = nr;
                        gapPositionNeg[1] = nc;
                    }
                    prevEmpty++;
                    if (prevEmpty >= 2) break;
                }
            } else if (c == color) {
                countNeg++;
                prevEmpty = 0;
            } else {
                blockedNeg = true;
                break;
            }
        }

        // 综合分析得出棋型
        int totalCount = countPos + countNeg + 1;  // 加上当前落子
        int totalGaps = gapsPos + gapsNeg;
        boolean hasGap = hasGapPos || hasGapNeg;
        int blockCount = (blockedPos ? 1 : 0) + (blockedNeg ? 1 : 0);
        int totalEmpty = emptyPos + emptyNeg;

        Pattern pattern = evaluatePattern(totalCount, blockCount, hasGap, totalEmpty, totalGaps);

        PatternInfo info = new PatternInfo();
        info.pattern = pattern;
        info.pieceCount = totalCount;
        info.blockCount = blockCount;
        info.hasGap = hasGap;
        info.emptyCount = totalEmpty;

        return info;
    }

    /**
     * 根据统计数据评估棋型
     */
    private static Pattern evaluatePattern(int count, int blocked, boolean hasGap, int empty, int gaps) {
        // 六子及以上 - 获胜
        if (count >= 6) {
            return Pattern.SIX;
        }

        // 五子
        if (count == 5) {
            return Pattern.FIVE;
        }

        // 四子
        if (count == 4) {
            if (blocked == 0) {
                return Pattern.LIVE_FOUR;  // 活四 _XXXX_
            } else if (blocked == 1) {
                if (hasGap) {
                    return Pattern.JUMP_FOUR;  // 跳冲四 |XXX_X
                }
                return Pattern.RUSH_FOUR;  // 冲四 |XXXX_
            }
            return Pattern.NONE;  // 死四
        }

        // 三子
        if (count == 3) {
            if (blocked == 0) {
                if (hasGap) {
                    return Pattern.JUMP_THREE;  // 跳活三 _XX_X_ 或 _X_XX_
                }
                if (empty >= 2) {
                    return Pattern.LIVE_THREE;  // 活三 _XXX_
                }
            } else if (blocked == 1) {
                return Pattern.SLEEP_THREE;  // 眠三 |XXX_
            }
            return Pattern.NONE;
        }

        // 二子
        if (count == 2) {
            if (blocked == 0 && empty >= 2) {
                return Pattern.LIVE_TWO;  // 活二 _XX_
            } else if (blocked == 1) {
                return Pattern.SLEEP_TWO;  // 眠二 |XX_
            }
            return Pattern.NONE;
        }

        // 一子
        if (count == 1) {
            if (blocked == 0) {
                return Pattern.LIVE_ONE;
            }
            return Pattern.NONE;
        }

        return Pattern.NONE;
    }

    private static boolean isValid(int row, int col) {
        return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
    }

    /**
     * 单方向棋型信息
     */
    public static class PatternInfo {
        public Pattern pattern = Pattern.NONE;
        public int pieceCount = 0;
        public int blockCount = 0;
        public boolean hasGap = false;
        public int emptyCount = 0;

        public boolean isThree() {
            return pattern == Pattern.LIVE_THREE || pattern == Pattern.JUMP_THREE;
        }

        public boolean isFour() {
            return pattern == Pattern.RUSH_FOUR || pattern == Pattern.JUMP_FOUR || pattern == Pattern.LIVE_FOUR;
        }
    }

    /**
     * 威胁分析结果
     */
    public static class ThreatResult {
        private List<PatternInfo> patterns = new ArrayList<>();
        private Pattern bestPattern = Pattern.NONE;
        private int totalScore = 0;
        private boolean hasDoubleThree = false;
        private boolean hasDoubleFour = false;
        private boolean hasFourThree = false;  // 冲四活三

        public void addPattern(Direction dir, PatternInfo info) {
            patterns.add(info);
            if (info.pattern.getLevel() > bestPattern.getLevel()) {
                bestPattern = info.pattern;
            }
            totalScore += info.pattern.getScore();
        }

        /**
         * 检测复合威胁
         */
        public void detectCompoundThreats() {
            int threeCount = 0;
            int fourCount = 0;
            boolean hasLiveThree = false;
            boolean hasFour = false;

            for (PatternInfo info : patterns) {
                if (info.isThree()) {
                    threeCount++;
                    if (info.pattern == Pattern.LIVE_THREE || info.pattern == Pattern.JUMP_THREE) {
                        hasLiveThree = true;
                    }
                }
                if (info.isFour()) {
                    fourCount++;
                    hasFour = true;
                }
            }

            // 双活三
            if (threeCount >= 2 && hasLiveThree) {
                hasDoubleThree = true;
                totalScore += Pattern.DOUBLE_THREE.getScore();
                if (bestPattern.getLevel() < Pattern.DOUBLE_THREE.getLevel()) {
                    bestPattern = Pattern.DOUBLE_THREE;
                }
            }

            // 双冲四
            if (fourCount >= 2) {
                hasDoubleFour = true;
                totalScore += Pattern.DOUBLE_FOUR.getScore();
                if (bestPattern.getLevel() < Pattern.DOUBLE_FOUR.getLevel()) {
                    bestPattern = Pattern.DOUBLE_FOUR;
                }
            }

            // 冲四活三（也是致命威胁）
            if (hasFour && hasLiveThree) {
                hasFourThree = true;
                totalScore += 40000;  // 冲四活三加分
            }
        }

        public Pattern getBestPattern() {
            return bestPattern;
        }

        public int getTotalScore() {
            return totalScore;
        }

        public boolean hasDoubleThree() {
            return hasDoubleThree;
        }

        public boolean hasDoubleFour() {
            return hasDoubleFour;
        }

        public boolean hasFourThree() {
            return hasFourThree;
        }

        public boolean isCriticalThreat() {
            return bestPattern.isCriticalThreat() || hasDoubleThree || hasDoubleFour || hasFourThree;
        }

        public boolean isWinningThreat() {
            return bestPattern.isWinning() || hasDoubleFour;
        }
    }
}

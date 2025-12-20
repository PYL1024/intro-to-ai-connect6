package stud.g07;

import core.board.PieceColor;

import java.util.ArrayList;
import java.util.List;

/**
 * V1阶段扩展棋盘类
 * 
 * 在基础Board类上扩展，提供：
 * - 快速棋型检测
 * - 威胁位置记录
 * - 候选位置管理
 * - 增量更新机制（为V2阶段预留）
 * 
 * 【关键设计】
 * 1. 使用二维坐标和一维索引双重访问
 * 2. 维护"热点区域"减少扫描范围
 * 3. 支持撤销操作（用于搜索）
 * 
 * @author V1阶段开发
 * @version 1.0
 */
public class V1Board extends core.board.Board {

    // ===================== 常量定义 =====================

    /** 棋盘大小 */
    public static final int SIZE = 19;

    /** 需要连成的子数（六子棋） */
    public static final int WIN_COUNT = 6;

    /** 四个方向的行增量 */
    private static final int[] DX = { 0, 1, 1, 1 };

    /** 四个方向的列增量 */
    private static final int[] DY = { 1, 0, 1, -1 };

    /** 搜索范围半径（只在已有棋子周围搜索） */
    private static final int SEARCH_RADIUS = 2;

    // ===================== 成员变量 =====================

    /** 记录每个位置是否为候选位置 */
    private boolean[][] isCandidate;

    /** 候选位置列表 */
    private List<Integer> candidatePositions;

    /** 当前棋盘上的棋子数量 */
    private int pieceCount;

    /** 最小有效行 */
    private int minRow;

    /** 最大有效行 */
    private int maxRow;

    /** 最小有效列 */
    private int minCol;

    /** 最大有效列 */
    private int maxCol;

    // ===================== 构造函数 =====================

    public V1Board() {
        super();
        isCandidate = new boolean[SIZE][SIZE];
        candidatePositions = new ArrayList<>();
        pieceCount = 0;
        // 初始化边界为中心
        minRow = SIZE / 2;
        maxRow = SIZE / 2;
        minCol = SIZE / 2;
        maxCol = SIZE / 2;

        // 初始时将中心区域加入候选
        initCandidates();
    }

    /**
     * 初始化候选位置（开局时以中心为基准）
     */
    private void initCandidates() {
        int center = SIZE / 2;
        for (int i = center - 2; i <= center + 2; i++) {
            for (int j = center - 2; j <= center + 2; j++) {
                if (isValidPosition(i, j)) {
                    isCandidate[i][j] = true;
                    candidatePositions.add(toIndex(i, j));
                }
            }
        }
    }

    // ===================== 坐标转换方法 =====================

    /**
     * 二维坐标转一维索引
     */
    public static int toIndex(int row, int col) {
        return row * SIZE + col;
    }

    /**
     * 一维索引转行号
     */
    public static int toRow(int index) {
        return index / SIZE;
    }

    /**
     * 一维索引转列号
     */
    public static int toCol(int index) {
        return index % SIZE;
    }

    /**
     * 检查坐标是否有效
     */
    public static boolean isValidPosition(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    /**
     * 检查索引是否有效
     */
    public static boolean isValidIndex(int index) {
        return index >= 0 && index < SIZE * SIZE;
    }

    // ===================== 棋盘操作方法 =====================

    /**
     * 获取指定位置的棋子颜色
     * 
     * @param row 行号
     * @param col 列号
     * @return 棋子颜色
     */
    public PieceColor getColor(int row, int col) {
        if (!isValidPosition(row, col)) {
            return null;
        }
        return this.get(toIndex(row, col));
    }

    /**
     * 检查位置是否为空
     */
    public boolean isEmpty(int row, int col) {
        return isValidPosition(row, col) && getColor(row, col) == PieceColor.EMPTY;
    }

    /**
     * 检查位置是否为空（索引版本）
     */
    public boolean isEmpty(int index) {
        return isValidIndex(index) && this.get(index) == PieceColor.EMPTY;
    }

    /**
     * 落子后更新候选位置
     * 
     * @param index 落子位置
     */
    public void updateCandidatesAfterMove(int index) {
        int row = toRow(index);
        int col = toCol(index);

        // 从候选列表移除已落子位置
        isCandidate[row][col] = false;
        candidatePositions.remove(Integer.valueOf(index));

        // 更新边界
        minRow = Math.min(minRow, row);
        maxRow = Math.max(maxRow, row);
        minCol = Math.min(minCol, col);
        maxCol = Math.max(maxCol, col);

        // 将周围空位加入候选
        for (int dr = -SEARCH_RADIUS; dr <= SEARCH_RADIUS; dr++) {
            for (int dc = -SEARCH_RADIUS; dc <= SEARCH_RADIUS; dc++) {
                int nr = row + dr;
                int nc = col + dc;
                if (isValidPosition(nr, nc) && !isCandidate[nr][nc] && isEmpty(nr, nc)) {
                    isCandidate[nr][nc] = true;
                    candidatePositions.add(toIndex(nr, nc));
                }
            }
        }

        pieceCount++;
    }

    /**
     * 获取当前候选位置列表（深拷贝）
     */
    public List<Integer> getCandidatePositions() {
        return new ArrayList<>(candidatePositions);
    }

    /**
     * 获取有效搜索范围
     */
    public int[] getSearchBounds() {
        return new int[] {
                Math.max(0, minRow - SEARCH_RADIUS),
                Math.min(SIZE - 1, maxRow + SEARCH_RADIUS),
                Math.max(0, minCol - SEARCH_RADIUS),
                Math.min(SIZE - 1, maxCol + SEARCH_RADIUS)
        };
    }

    // ===================== 胜着检测方法 =====================

    /**
     * 【胜着检测核心算法】
     * 检测在指定位置落子后是否能形成连六
     * 
     * @param index 待检测位置
     * @param color 落子颜色
     * @return 是否能形成连六
     */
    public boolean checkWinAt(int index, PieceColor color) {
        int row = toRow(index);
        int col = toCol(index);

        // 检查四个方向
        for (int dir = 0; dir < 4; dir++) {
            int count = 1; // 包含当前位置

            // 正向计数
            int r = row + DX[dir];
            int c = col + DY[dir];
            while (isValidPosition(r, c) && getColor(r, c) == color) {
                count++;
                r += DX[dir];
                c += DY[dir];
            }

            // 反向计数
            r = row - DX[dir];
            c = col - DY[dir];
            while (isValidPosition(r, c) && getColor(r, c) == color) {
                count++;
                r -= DX[dir];
                c -= DY[dir];
            }

            // 六子棋需要连成6子
            if (count >= WIN_COUNT) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检测两个位置组合是否能形成胜利
     * 
     * @param pos1  第一个位置
     * @param pos2  第二个位置
     * @param color 棋子颜色
     * @return 是否能获胜
     */
    public boolean checkWinWithTwoMoves(int pos1, int pos2, PieceColor color) {
        // 注意：这里只是模拟，不实际修改棋盘
        // 假设在pos1与pos2同时落子后是否获胜
        return checkWinWithTemps(pos1, color, pos1, pos2) || checkWinWithTemps(pos2, color, pos1, pos2);
    }

    /**
     * 假设在index位置落子后是否能形成胜利（不改动真实棋盘）
     */
    public boolean checkWinIfPlace(int index, PieceColor color) {
        return checkWinWithTemps(index, color, index, -1);
    }

    /**
     * 假设在两个位置同时落子后是否能形成胜利（不改动真实棋盘）
     */
    public boolean checkWinIfPlaceTwo(int pos1, int pos2, PieceColor color) {
        return checkWinWithTemps(pos1, color, pos1, pos2) || checkWinWithTemps(pos2, color, pos1, pos2);
    }

    /**
     * 带两个临时棋子的胜利检测（假设tempA与tempB上有己方棋子）
     */
    private boolean checkWinWithTemps(int index, PieceColor color, int tempA, int tempB) {
        int row = toRow(index);
        int col = toCol(index);
        int tempARow = tempA >= 0 ? toRow(tempA) : -1;
        int tempACol = tempA >= 0 ? toCol(tempA) : -1;
        int tempBRow = tempB >= 0 ? toRow(tempB) : -1;
        int tempBCol = tempB >= 0 ? toCol(tempB) : -1;

        for (int dir = 0; dir < 4; dir++) {
            int count = 1;

            int r = row + DX[dir];
            int c = col + DY[dir];
            while (isValidPosition(r, c)) {
                if ((r == tempARow && c == tempACol) || (r == tempBRow && c == tempBCol)) {
                    count++;
                } else if (getColor(r, c) == color) {
                    count++;
                } else {
                    break;
                }
                r += DX[dir];
                c += DY[dir];
            }

            r = row - DX[dir];
            c = col - DY[dir];
            while (isValidPosition(r, c)) {
                if ((r == tempARow && c == tempACol) || (r == tempBRow && c == tempBCol)) {
                    count++;
                } else if (getColor(r, c) == color) {
                    count++;
                } else {
                    break;
                }
                r -= DX[dir];
                c -= DY[dir];
            }

            if (count >= WIN_COUNT) {
                return true;
            }
        }
        return false;
    }

    // ===================== 棋型检测方法 =====================

    /**
     * 【棋型检测核心算法】
     * 检测指定位置在某方向上的棋型
     * 
     * @param index 位置索引
     * @param color 棋子颜色
     * @param dir   方向索引（0-3）
     * @return 检测到的棋型
     */
    public Pattern detectPatternAt(int index, PieceColor color, int dir) {
        int row = toRow(index);
        int col = toCol(index);

        int count = 1; // 连续同色棋子数
        int block = 0; // 被阻挡的端数（0、1、2）
        int empty = 0; // 空位数（用于跳活检测）

        // 正向扫描
        int r = row + DX[dir];
        int c = col + DY[dir];
        boolean foundEmpty = false;
        while (isValidPosition(r, c)) {
            PieceColor pc = getColor(r, c);
            if (pc == color) {
                count++;
            } else if (pc == PieceColor.EMPTY && !foundEmpty) {
                empty++;
                foundEmpty = true;
                // 继续检测跳活
            } else {
                if (pc != PieceColor.EMPTY) {
                    block++;
                }
                break;
            }
            r += DX[dir];
            c += DY[dir];
        }
        if (!isValidPosition(r, c)) {
            block++;
        }

        // 反向扫描
        r = row - DX[dir];
        c = col - DY[dir];
        foundEmpty = false;
        while (isValidPosition(r, c)) {
            PieceColor pc = getColor(r, c);
            if (pc == color) {
                count++;
            } else if (pc == PieceColor.EMPTY && !foundEmpty) {
                empty++;
                foundEmpty = true;
            } else {
                if (pc != PieceColor.EMPTY) {
                    block++;
                }
                break;
            }
            r -= DX[dir];
            c -= DY[dir];
        }
        if (!isValidPosition(r, c)) {
            block++;
        }

        // 根据连子数和阻挡情况判断棋型
        return evaluatePattern(count, block, empty);
    }

    /**
     * 根据统计信息评估棋型
     * 
     * @param count 连续同色子数
     * @param block 被阻挡端数
     * @param empty 空位数（用于跳活）
     * @return 棋型
     */
    private Pattern evaluatePattern(int count, int block, int empty) {
        // 连六或更多
        if (count >= 6) {
            return count == 6 ? Pattern.SIX : Pattern.OVERLINE;
        }

        // 连五
        if (count == 5) {
            return Pattern.FIVE;
        }

        // 连四
        if (count == 4) {
            if (block == 0) {
                return Pattern.LIVE_FOUR; // 活四：两端都开放
            } else if (block == 1) {
                return Pattern.RUSH_FOUR; // 冲四：一端被堵
            }
            return Pattern.NONE; // 死四：两端都被堵
        }

        // 连三
        if (count == 3) {
            if (block == 0) {
                return Pattern.LIVE_THREE; // 活三
            } else if (block == 1) {
                return Pattern.SLEEP_THREE; // 眠三
            }
            return Pattern.NONE; // 死三
        }

        // 连二
        if (count == 2) {
            if (block == 0) {
                return Pattern.LIVE_TWO; // 活二
            } else if (block == 1) {
                return Pattern.SLEEP_TWO; // 眠二
            }
            return Pattern.NONE; // 死二
        }

        // 单子
        if (count == 1 && block < 2) {
            return Pattern.LIVE_ONE;
        }

        return Pattern.NONE;
    }

    /**
     * 获取位置在所有方向上的最高等级棋型
     */
    public Pattern getBestPatternAt(int index, PieceColor color) {
        Pattern best = Pattern.NONE;
        for (int dir = 0; dir < 4; dir++) {
            Pattern p = detectPatternAt(index, color, dir);
            if (p.getLevel() > best.getLevel()) {
                best = p;
            }
        }
        return best;
    }

    /**
     * 计算位置的综合分值
     */
    public int evaluatePositionScore(int index, PieceColor color) {
        int score = 0;
        for (int dir = 0; dir < 4; dir++) {
            Pattern p = detectPatternAt(index, color, dir);
            score += p.getScore();
        }
        return score;
    }

    // ===================== 连续同色子计数 =====================

    /**
     * 计算指定位置某方向的连续同色子数量
     */
    public int countConsecutive(int row, int col, PieceColor color, int dir) {
        int count = 0;
        int r = row, c = col;

        while (isValidPosition(r, c) && getColor(r, c) == color) {
            count++;
            r += DX[dir];
            c += DY[dir];
        }

        return count;
    }

    /**
     * 计算双向连续同色子数量（包括指定位置）
     */
    public int countBidirectional(int row, int col, PieceColor color, int dir) {
        // 假设(row, col)位置会放置color颜色的棋子
        int count = 1;

        // 正向
        int r = row + DX[dir];
        int c = col + DY[dir];
        while (isValidPosition(r, c) && getColor(r, c) == color) {
            count++;
            r += DX[dir];
            c += DY[dir];
        }

        // 反向
        r = row - DX[dir];
        c = col - DY[dir];
        while (isValidPosition(r, c) && getColor(r, c) == color) {
            count++;
            r -= DX[dir];
            c -= DY[dir];
        }

        return count;
    }

    // ===================== 工具方法 =====================

    /**
     * 获取棋盘状态的字符串表示（用于调试）
     */
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("  ");
        for (int c = 0; c < SIZE; c++) {
            sb.append(String.format("%2d", c % 10));
        }
        sb.append("\n");

        for (int r = 0; r < SIZE; r++) {
            sb.append(String.format("%2d", r));
            for (int c = 0; c < SIZE; c++) {
                PieceColor color = getColor(r, c);
                if (color == PieceColor.BLACK) {
                    sb.append(" X");
                } else if (color == PieceColor.WHITE) {
                    sb.append(" O");
                } else {
                    sb.append(" .");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public int getPieceCount() {
        return pieceCount;
    }
}

package stud.g09;

import core.board.PieceColor;

import java.util.ArrayList;
import java.util.List;

/**
 * V1阶段扩展棋盘类
 * <p>
 * 在基础Board类上扩展，提供：
 * - 快速棋型检测
 * - 威胁位置记录
 * - 候选位置管理
 * - 增量更新机制（为V2阶段预留）
 * <p>
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
    private static final int SEARCH_RADIUS_EARLY = 8;

    /** 中后盘扩大的搜索半径 */
    private static final int SEARCH_RADIUS_LATE = 10;

    /** 进入中后盘的棋子数阈值 */
    private static final int MID_GAME_THRESHOLD = 20;

    /** 后期全扩展的棋子数阈值 */
    private static final int LATE_GAME_THRESHOLD = 40;

    /** 路的最小有效长度（六子棋需要连成6子） */
    private static final int LINE_LENGTH = 6;

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

    // ===================== V2路表相关成员 =====================

    /** 所有有效路的列表 */
    private List<Line> lines;

    /** 每个格子每个方向覆盖的路索引列表 [row][col][dir] -> List<lineIndex> */
    private List<Integer>[][][] lineIndexByCell;

    /** 己方全局评分（所有有效路的己方分之和） */
    private int boardScoreMy;

    /** 对方全局评分（所有有效路的对方分之和） */
    private int boardScoreOpp;

    /** 路表是否已初始化 */
    private boolean linesBuilt;

    // ===================== 构造函数 =====================

    @SuppressWarnings("unchecked")
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

        // V2路表初始化
        lines = new ArrayList<>();
        lineIndexByCell = new ArrayList[SIZE][SIZE][4];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                for (int d = 0; d < 4; d++) {
                    lineIndexByCell[i][j][d] = new ArrayList<>();
                }
            }
        }
        boardScoreMy = 0;
        boardScoreOpp = 0;
        linesBuilt = false;

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
     * 设置指定位置的棋子颜色（用于搜索模拟落子）
     * 
     * @param index 位置索引
     * @param color 棋子颜色
     */
    public void setColor(int index, PieceColor color) {
        if (isValidIndex(index)) {
            this.set(index, color);
        }
    }

    /**
     * 清除指定位置的棋子（用于搜索撤销落子）
     * 
     * @param index 位置索引
     */
    public void clearColor(int index) {
        if (isValidIndex(index)) {
            this.set(index, PieceColor.EMPTY);
        }
    }

    /**
     * 获取当前搜索半径（根据棋子数动态调整）
     */
    public int getCurrentSearchRadius() {
        if (pieceCount >= LATE_GAME_THRESHOLD) {
            return SEARCH_RADIUS_LATE + 1; // 后期更大
        } else if (pieceCount >= MID_GAME_THRESHOLD) {
            return SEARCH_RADIUS_LATE;
        }
        return SEARCH_RADIUS_EARLY;
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

        // 根据阶段动态调整搜索半径
        int radius = getCurrentSearchRadius();

        // 将周围空位加入候选
        for (int dr = -radius; dr <= radius; dr++) {
            for (int dc = -radius; dc <= radius; dc++) {
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
        int radius = getCurrentSearchRadius();
        return new int[] {
                Math.max(0, minRow - radius),
                Math.min(SIZE - 1, maxRow + radius),
                Math.max(0, minCol - radius),
                Math.min(SIZE - 1, maxCol + radius)
        };
    }

    /**
     * 【触发式补充】确保指定位置在候选列表中
     * 用于防守点/威胁点未被覆盖时强制加入
     * 
     * @param index 需要加入候选的位置
     * @return 是否新增了候选
     */
    public boolean ensureCandidate(int index) {
        if (!isValidIndex(index) || !isEmpty(index)) {
            return false;
        }
        int row = toRow(index);
        int col = toCol(index);
        if (!isCandidate[row][col]) {
            isCandidate[row][col] = true;
            candidatePositions.add(index);
            return true;
        }
        return false;
    }

    /**
     * 【触发式补充】批量确保多个位置在候选列表中
     * 
     * @param positions 需要加入候选的位置列表
     * @return 新增的候选数量
     */
    public int ensureCandidates(List<Integer> positions) {
        int added = 0;
        for (int pos : positions) {
            if (ensureCandidate(pos)) {
                added++;
            }
        }
        return added;
    }

    /**
     * 【触发式补充】扩展边界范围内的所有空位为候选
     * 用于后期全局补充
     * 
     * @param extraRadius 额外扩展的半径（在当前边界基础上）
     * @return 新增的候选数量
     */
    public int expandCandidatesInBounds(int extraRadius) {
        int added = 0;
        int r1 = Math.max(0, minRow - extraRadius);
        int r2 = Math.min(SIZE - 1, maxRow + extraRadius);
        int c1 = Math.max(0, minCol - extraRadius);
        int c2 = Math.min(SIZE - 1, maxCol + extraRadius);

        for (int r = r1; r <= r2; r++) {
            for (int c = c1; c <= c2; c++) {
                if (!isCandidate[r][c] && isEmpty(r, c)) {
                    isCandidate[r][c] = true;
                    candidatePositions.add(toIndex(r, c));
                    added++;
                }
            }
        }
        return added;
    }

    /**
     * 检查指定位置是否在候选列表中
     */
    public boolean isCandidatePosition(int index) {
        if (!isValidIndex(index)) return false;
        int row = toRow(index);
        int col = toCol(index);
        return isCandidate[row][col];
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

    // ===================== 搜索状态快照（候选精确回溯） =====================

    /**
     * 用于搜索的棋盘候选状态快照
     */
    public static class BoardState {
        boolean[][] isCandidateCopy;
        List<Integer> candidatePositionsCopy;
        int minRowCopy;
        int maxRowCopy;
        int minColCopy;
        int maxColCopy;
        int pieceCountCopy;
    }

    /**
     * 捕获当前候选/边界/计数状态（不包含棋子颜色），用于搜索回溯
     */
    public BoardState captureStateForSearch() {
        BoardState state = new BoardState();
        state.isCandidateCopy = new boolean[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++) {
            System.arraycopy(isCandidate[r], 0, state.isCandidateCopy[r], 0, SIZE);
        }
        state.candidatePositionsCopy = new ArrayList<>(candidatePositions);
        state.minRowCopy = minRow;
        state.maxRowCopy = maxRow;
        state.minColCopy = minCol;
        state.maxColCopy = maxCol;
        state.pieceCountCopy = pieceCount;
        return state;
    }

    /**
     * 恢复候选/边界/计数状态（不触碰棋子颜色），用于搜索回溯
     */
    public void restoreStateForSearch(BoardState state) {
        for (int r = 0; r < SIZE; r++) {
            System.arraycopy(state.isCandidateCopy[r], 0, isCandidate[r], 0, SIZE);
        }
        candidatePositions.clear();
        candidatePositions.addAll(state.candidatePositionsCopy);
        minRow = state.minRowCopy;
        maxRow = state.maxRowCopy;
        minCol = state.minColCopy;
        maxCol = state.maxColCopy;
        pieceCount = state.pieceCountCopy;
    }

    // ===================== V2路表方法（Step 1: 路表生成） =====================

    /**
     * 【Step 1】构建所有有效路（一次全盘扫描）
     * 按四方向滑窗长度 LINE_LENGTH 遍历，生成路并建立索引
     * 
     * @param myColor 己方颜色（用于初始化路的计数）
     */
    public void buildLines(PieceColor myColor) {
        lines.clear();
        // 重置索引
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                for (int d = 0; d < 4; d++) {
                    lineIndexByCell[i][j][d].clear();
                }
            }
        }
        boardScoreMy = 0;
        boardScoreOpp = 0;

        Direction[] directions = Direction.getAllDirections();

        // 遍历四个方向
        for (int dir = 0; dir < 4; dir++) {
            int dx = DX[dir];
            int dy = DY[dir];
            Direction direction = directions[dir];

            // 确定起始位置遍历范围
            for (int row = 0; row < SIZE; row++) {
                for (int col = 0; col < SIZE; col++) {
                    // 检查从 (row, col) 出发是否能形成长度为 LINE_LENGTH 的路
                    int endRow = row + dx * (LINE_LENGTH - 1);
                    int endCol = col + dy * (LINE_LENGTH - 1);

                    if (!isValidPosition(endRow, endCol)) {
                        continue; // 超出边界
                    }

                    // 收集路上的所有位置
                    int[] positions = new int[LINE_LENGTH];
                    int myCount = 0;
                    int oppCount = 0;
                    int emptyCount = 0;

                    for (int k = 0; k < LINE_LENGTH; k++) {
                        int r = row + dx * k;
                        int c = col + dy * k;
                        int idx = toIndex(r, c);
                        positions[k] = idx;

                        PieceColor pc = getColor(r, c);
                        if (pc == PieceColor.EMPTY) {
                            emptyCount++;
                        } else if (pc == myColor) {
                            myCount++;
                        } else {
                            oppCount++;
                        }
                    }

                    // 创建路
                    int startPos = toIndex(row, col);
                    Line line = new Line(startPos, direction, dir, LINE_LENGTH, positions);
                    line.setMyCount(myCount);
                    line.setOppCount(oppCount);
                    line.setEmptyCount(emptyCount);

                    // 判断有效性（双方都有子则无效）
                    if (myCount > 0 && oppCount > 0) {
                        line.setValid(false);
                    }

                    // 评估棋型和分值
                    line.reevaluate();

                    // 添加到列表
                    int lineIndex = lines.size();
                    lines.add(line);

                    // 建立位置到路的索引
                    for (int k = 0; k < LINE_LENGTH; k++) {
                        int r = row + dx * k;
                        int c = col + dy * k;
                        lineIndexByCell[r][c][dir].add(lineIndex);
                    }

                    // 累加全局分
                    boardScoreMy += line.getMyScore();
                    boardScoreOpp += line.getOppScore();
                }
            }
        }

        linesBuilt = true;
    }

    // ===================== V2路表方法（Step 3: 增量更新） =====================

    /**
     * 【Step 3】落子后增量更新路表
     * 只更新经过该位置的路，并维护全局分
     * 
     * @param index   落子位置
     * @param color   落子颜色
     * @param myColor 己方颜色（用于判断是己方还是对方落子）
     */
    public void updateLinesAfterMove(int index, PieceColor color, PieceColor myColor) {
        if (!linesBuilt) {
            return;
        }

        int row = toRow(index);
        int col = toCol(index);
        boolean isMy = (color == myColor);

        // 遍历该位置涉及的四个方向的所有路
        for (int dir = 0; dir < 4; dir++) {
            List<Integer> lineIndices = lineIndexByCell[row][col][dir];
            for (int lineIdx : lineIndices) {
                Line line = lines.get(lineIdx);

                // 移除旧分值
                boardScoreMy -= line.getMyScore();
                boardScoreOpp -= line.getOppScore();

                // 更新计数
                line.addPiece(isMy);

                // 重新评估
                line.reevaluate();

                // 加回新分值
                boardScoreMy += line.getMyScore();
                boardScoreOpp += line.getOppScore();
            }
        }
    }

    /**
     * 【Step 3】撤销落子后增量回滚路表（用于搜索回溯）
     * 
     * @param index   撤销位置
     * @param color   撤销的棋子颜色
     * @param myColor 己方颜色
     */
    public void undoLinesAfterMove(int index, PieceColor color, PieceColor myColor) {
        if (!linesBuilt) {
            return;
        }

        int row = toRow(index);
        int col = toCol(index);
        boolean wasMy = (color == myColor);

        for (int dir = 0; dir < 4; dir++) {
            List<Integer> lineIndices = lineIndexByCell[row][col][dir];
            for (int lineIdx : lineIndices) {
                Line line = lines.get(lineIdx);

                // 移除当前分值
                boardScoreMy -= line.getMyScore();
                boardScoreOpp -= line.getOppScore();

                // 撤销计数
                line.removePiece(wasMy);

                // 重新评估
                line.reevaluate();

                // 加回恢复后的分值
                boardScoreMy += line.getMyScore();
                boardScoreOpp += line.getOppScore();
            }
        }
    }

    // ===================== V2路表方法（Step 2: 全局评估） =====================

    /**
     * 【Step 2】快速全局评估函数
     * 返回 己方分 - 对方分，用于搜索叶节点评估
     * 
     * @return 评估分值（正值表示己方优势）
     */
    public int evaluateBoard() {
        return boardScoreMy - boardScoreOpp;
    }

    /**
     * 获取己方全局分
     */
    public int getBoardScoreMy() {
        return boardScoreMy;
    }

    /**
     * 获取对方全局分
     */
    public int getBoardScoreOpp() {
        return boardScoreOpp;
    }

    /**
     * 获取所有路的列表（只读）
     */
    public List<Line> getLines() {
        return lines;
    }

    /**
     * 获取指定位置指定方向涉及的路索引列表
     */
    public List<Integer> getLineIndicesAt(int row, int col, int dir) {
        return lineIndexByCell[row][col][dir];
    }

    /**
     * 路表是否已构建
     */
    public boolean isLinesBuilt() {
        return linesBuilt;
    }

    /**
     * 获取指定位置涉及的所有有效路（用于候选评分优化）
     * 
     * @param index 位置索引
     * @return 有效路列表
     */
    public List<Line> getValidLinesAt(int index) {
        List<Line> result = new ArrayList<>();
        int row = toRow(index);
        int col = toCol(index);

        for (int dir = 0; dir < 4; dir++) {
            for (int lineIdx : lineIndexByCell[row][col][dir]) {
                Line line = lines.get(lineIdx);
                if (line.isValid()) {
                    result.add(line);
                }
            }
        }
        return result;
    }

    /**
     * 评估假设在某位置落子后的局部分值变化（不实际落子）
     * 用于候选评分优化
     * 
     * @param index   假设落子位置
     * @param myColor 己方颜色
     * @return 落子后的分值增量（正值表示有利）
     */
    public int evaluateMoveIncrement(int index, PieceColor myColor) {
        if (!linesBuilt || !isEmpty(index)) {
            return 0;
        }

        int row = toRow(index);
        int col = toCol(index);
        int deltaScore = 0;

        for (int dir = 0; dir < 4; dir++) {
            for (int lineIdx : lineIndexByCell[row][col][dir]) {
                Line line = lines.get(lineIdx);
                if (!line.isValid()) {
                    continue;
                }

                int oldMyScore = line.getMyScore();
                int oldOppScore = line.getOppScore();

                // 模拟落子后的新计数
                int newMyCount = line.getMyCount() + 1;
                int newOppCount = line.getOppCount();

                // 若对方也有子，落子后路失效
                if (newOppCount > 0) {
                    // 路将失效，损失当前对方分（阻止对方发展）
                    deltaScore += oldOppScore;
                } else {
                    // 估算新的己方棋型分
                    int newMyScore = estimateScoreForCount(newMyCount, line.getLength());
                    deltaScore += (newMyScore - oldMyScore);
                }
            }
        }

        return deltaScore;
    }

    /**
     * 根据连子数估算分值（简化版）
     */
    private int estimateScoreForCount(int count, int length) {
        if (count >= 6) return Pattern.SIX.getScore();
        if (count == 5) return Pattern.FIVE.getScore();
        if (count == 4) return length >= 6 ? Pattern.LIVE_FOUR.getScore() : Pattern.RUSH_FOUR.getScore();
        if (count == 3) return length >= 6 ? Pattern.LIVE_THREE.getScore() : Pattern.SLEEP_THREE.getScore();
        if (count == 2) return length >= 6 ? Pattern.LIVE_TWO.getScore() : Pattern.SLEEP_TWO.getScore();
        if (count == 1) return Pattern.LIVE_ONE.getScore();
        return 0;
    }
}

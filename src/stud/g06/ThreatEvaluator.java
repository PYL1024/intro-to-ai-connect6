package stud.g06;

import core.board.PieceColor;

import java.util.ArrayList;
import java.util.List;

/**
 * 威胁评估器
 * 
 * 负责检测和评估棋盘上的威胁情况，是V1阶段防守策略的核心。
 * 
 * 【威胁定义】
 * - 致命威胁(Critical)：对方冲四或活四，必须立即防守
 * - 高威胁(High)：对方活三，需要优先防守
 * - 中威胁(Medium)：对方眠三，需要关注
 * 
 * 【防守策略】
 * 1. 威胁数 ≤ 2：必须防守所有威胁
 * 2. 威胁数 > 2：按优先级选择防守（先防致命，再防高威胁）
 * 
 * @author V1阶段开发
 * @version 1.0
 */
public class ThreatEvaluator {

    // ===================== 常量定义 =====================

    /** 棋盘大小 */
    private static final int SIZE = V1Board.SIZE;

    /** 四个方向增量 */
    private static final int[] DX = { 0, 1, 1, 1 };
    private static final int[] DY = { 1, 0, 1, -1 };

    // ===================== 威胁信息类 =====================

    /**
     * 威胁信息内部类
     * 记录一个威胁的位置、类型和防守点
     */
    public static class Threat {
        /** 威胁所在位置（形成威胁的棋子位置） */
        public int position;

        /** 威胁类型（棋型） */
        public Pattern pattern;

        /** 防守点列表（堵住这些点可以化解威胁） */
        public List<Integer> defensePoints;

        /** 威胁方向 */
        public int direction;

        public Threat(int position, Pattern pattern, int direction) {
            this.position = position;
            this.pattern = pattern;
            this.direction = direction;
            this.defensePoints = new ArrayList<>();
        }

        /**
         * 获取威胁的危险等级
         */
        public int getDangerLevel() {
            return pattern.getLevel();
        }

        @Override
        public String toString() {
            return String.format("Threat[pos=%d, pattern=%s, dir=%d, defPoints=%s]",
                    position, pattern, direction, defensePoints);
        }
    }

    // ===================== 成员变量 =====================

    /** 关联的棋盘 */
    private V1Board board;

    /** 当前检测到的威胁列表 */
    private List<Threat> threats;

    /** 致命威胁数量 */
    private int criticalCount;

    /** 高威胁数量 */
    private int highCount;

    // ===================== 构造函数 =====================

    public ThreatEvaluator(V1Board board) {
        this.board = board;
        this.threats = new ArrayList<>();
        this.criticalCount = 0;
        this.highCount = 0;
    }

    // ===================== 威胁检测方法 =====================

    /**
     * 【威胁检测核心算法】
     * 扫描整个棋盘，检测对手的所有威胁
     * 
     * 伪代码：
     * ```
     * function detectThreats(board, oppColor):
     * threats = []
     * for each position in board:
     * if board[position] == oppColor:
     * for each direction in [横, 纵, 主对角, 副对角]:
     * pattern = detectPattern(position, direction)
     * if pattern.isThreat():
     * threat = createThreat(position, pattern, direction)
     * threat.defensePoints = findDefensePoints(position, direction)
     * threats.add(threat)
     * return threats
     * ```
     * 
     * @param oppColor 对手颜色
     * @return 检测到的威胁列表
     */
    public List<Threat> detectAllThreats(PieceColor oppColor) {
        threats.clear();
        criticalCount = 0;
        highCount = 0;

        // 使用边界优化，只扫描有棋子的区域
        int[] bounds = board.getSearchBounds();
        int minRow = bounds[0], maxRow = bounds[1];
        int minCol = bounds[2], maxCol = bounds[3];

        // 记录已检测的威胁（避免重复）
        boolean[][][] checked = new boolean[SIZE][SIZE][4];

        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                if (board.getColor(row, col) != oppColor) {
                    continue;
                }

                // 检查四个方向
                for (int dir = 0; dir < 4; dir++) {
                    if (checked[row][col][dir]) {
                        continue;
                    }

                    Threat threat = detectThreatInDirection(row, col, oppColor, dir);
                    if (threat != null) {
                        threats.add(threat);
                        // 标记这条线上的所有棋子为已检查
                        markLineChecked(row, col, dir, checked);

                        // 统计威胁等级
                        if (threat.pattern.isCriticalThreat()) {
                            criticalCount++;
                        } else if (threat.pattern.isHighThreat()) {
                            highCount++;
                        }
                    }
                }
            }
        }

        return threats;
    }

    /**
     * 检测某位置某方向的威胁
     */
    private Threat detectThreatInDirection(int row, int col, PieceColor color, int dir) {
        // 统计该方向的连续棋子和空位情况
        LineInfo info = analyzeLine(row, col, color, dir);

        // 根据连子数和空位判断是否构成威胁
        Pattern pattern = evaluateThreatPattern(info);

        if (pattern == Pattern.NONE || pattern.getLevel() < Pattern.SLEEP_THREE.getLevel()) {
            return null; // 不构成威胁
        }

        Threat threat = new Threat(V1Board.toIndex(row, col), pattern, dir);

        // 寻找防守点
        threat.defensePoints = findDefensePoints(row, col, color, dir, info);

        return threat;
    }

    /**
     * 分析某方向的连线信息
     */
    private LineInfo analyzeLine(int row, int col, PieceColor color, int dir) {
        LineInfo info = new LineInfo();
        info.count = 1;

        // 正向扫描
        int r = row + DX[dir];
        int c = col + DY[dir];
        int emptyInRow = 0;
        while (V1Board.isValidPosition(r, c) && emptyInRow < 2) {
            PieceColor pc = board.getColor(r, c);
            if (pc == color) {
                info.count++;
                if (emptyInRow > 0) {
                    info.hasGap = true;
                }
            } else if (pc == PieceColor.EMPTY) {
                emptyInRow++;
                if (info.emptyAfter1 == -1) {
                    info.emptyAfter1 = V1Board.toIndex(r, c);
                } else if (info.emptyAfter2 == -1) {
                    info.emptyAfter2 = V1Board.toIndex(r, c);
                }
            } else {
                info.blocked1 = true;
                break;
            }
            r += DX[dir];
            c += DY[dir];
        }
        if (!V1Board.isValidPosition(r, c)) {
            info.blocked1 = true;
        }

        // 反向扫描
        r = row - DX[dir];
        c = col - DY[dir];
        emptyInRow = 0;
        while (V1Board.isValidPosition(r, c) && emptyInRow < 2) {
            PieceColor pc = board.getColor(r, c);
            if (pc == color) {
                info.count++;
                if (emptyInRow > 0) {
                    info.hasGap = true;
                }
            } else if (pc == PieceColor.EMPTY) {
                emptyInRow++;
                if (info.emptyBefore1 == -1) {
                    info.emptyBefore1 = V1Board.toIndex(r, c);
                } else if (info.emptyBefore2 == -1) {
                    info.emptyBefore2 = V1Board.toIndex(r, c);
                }
            } else {
                info.blocked2 = true;
                break;
            }
            r -= DX[dir];
            c -= DY[dir];
        }
        if (!V1Board.isValidPosition(r, c)) {
            info.blocked2 = true;
        }

        return info;
    }

    /**
     * 连线信息类
     */
    private static class LineInfo {
        int count = 0;
        boolean blocked1 = false; // 正向是否被阻
        boolean blocked2 = false; // 反向是否被阻
        boolean hasGap = false; // 是否有跳（中间有空位）
        int emptyBefore1 = -1; // 反向第一个空位
        int emptyBefore2 = -1; // 反向第二个空位
        int emptyAfter1 = -1; // 正向第一个空位
        int emptyAfter2 = -1; // 正向第二个空位
    }

    /**
     * 根据连线信息评估威胁棋型
     */
    private Pattern evaluateThreatPattern(LineInfo info) {
        int blockCount = (info.blocked1 ? 1 : 0) + (info.blocked2 ? 1 : 0);

        // 五子或更多
        if (info.count >= 5) {
            return info.count >= 6 ? Pattern.SIX : Pattern.FIVE;
        }

        // 四子
        if (info.count == 4) {
            if (blockCount == 0) {
                return Pattern.LIVE_FOUR;
            } else if (blockCount == 1) {
                return Pattern.RUSH_FOUR;
            }
            return Pattern.NONE;
        }

        // 三子
        if (info.count == 3) {
            if (blockCount == 0) {
                return Pattern.LIVE_THREE;
            } else if (blockCount == 1) {
                return Pattern.SLEEP_THREE;
            }
            return Pattern.NONE;
        }

        return Pattern.NONE;
    }

    /**
     * 【防守点查找算法】
     * 找出能够化解威胁的防守点
     * 
     * @return 防守点列表
     */
    private List<Integer> findDefensePoints(int row, int col, PieceColor color, int dir, LineInfo info) {
        List<Integer> points = new ArrayList<>();

        // 根据威胁类型选择防守策略
        // 对于冲四和活四：必须堵住能让对方连成六子的位置
        // 对于活三：堵住两端空位

        // 收集所有空位作为潜在防守点
        if (info.emptyBefore1 != -1)
            points.add(info.emptyBefore1);
        if (info.emptyAfter1 != -1)
            points.add(info.emptyAfter1);

        // 对于活四，两端都要防
        if (info.count >= 4 && !info.blocked1 && !info.blocked2) {
            if (info.emptyBefore2 != -1)
                points.add(info.emptyBefore2);
            if (info.emptyAfter2 != -1)
                points.add(info.emptyAfter2);
        }

        return points;
    }

    /**
     * 标记一条线上的位置为已检查
     */
    private void markLineChecked(int row, int col, int dir, boolean[][][] checked) {
        PieceColor color = board.getColor(row, col);

        // 标记起始位置
        checked[row][col][dir] = true;

        // 正向标记
        int r = row + DX[dir];
        int c = col + DY[dir];
        while (V1Board.isValidPosition(r, c) && board.getColor(r, c) == color) {
            checked[r][c][dir] = true;
            r += DX[dir];
            c += DY[dir];
        }

        // 反向标记
        r = row - DX[dir];
        c = col - DY[dir];
        while (V1Board.isValidPosition(r, c) && board.getColor(r, c) == color) {
            checked[r][c][dir] = true;
            r -= DX[dir];
            c -= DY[dir];
        }
    }

    // ===================== 防守决策方法 =====================

    /**
     * 【防守策略核心算法】
     * 根据威胁情况决定防守位置
     * 
     * 策略：
     * 1. 如果有致命威胁（冲四、活四），必须优先防守
     * 2. 威胁数 ≤ 2 时，防守所有威胁
     * 3. 威胁数 > 2 时，按优先级选择最危险的威胁防守
     * 
     * @param oppColor 对手颜色
     * @return 需要防守的位置列表（最多2个，对应六子棋的两子着法）
     */
    public List<Integer> getDefensePositions(PieceColor oppColor) {
        List<Threat> allThreats = detectAllThreats(oppColor);
        List<Integer> defensePositions = new ArrayList<>();

        if (allThreats.isEmpty()) {
            return defensePositions; // 无威胁
        }

        // 按威胁等级排序（从高到低）
        allThreats.sort((t1, t2) -> t2.getDangerLevel() - t1.getDangerLevel());

        // 收集所有防守点，按优先级
        for (Threat threat : allThreats) {
            for (int point : threat.defensePoints) {
                if (!defensePositions.contains(point) && board.isEmpty(point)) {
                    defensePositions.add(point);
                    // 六子棋每步最多落两子
                    if (defensePositions.size() >= 2) {
                        return defensePositions;
                    }
                }
            }
        }

        return defensePositions;
    }

    /**
     * 判断是否存在紧急威胁需要防守
     */
    public boolean hasUrgentThreat(PieceColor oppColor) {
        detectAllThreats(oppColor);
        return criticalCount > 0 || highCount > 0;
    }

    /**
     * 获取威胁总数
     */
    public int getThreatCount() {
        return threats.size();
    }

    /**
     * 获取致命威胁数量
     */
    public int getCriticalThreatCount() {
        return criticalCount;
    }

    /**
     * 获取高威胁数量
     */
    public int getHighThreatCount() {
        return highCount;
    }

    /**
     * 获取当前威胁列表（只读）
     */
    public List<Threat> getThreats() {
        return new ArrayList<>(threats);
    }
}

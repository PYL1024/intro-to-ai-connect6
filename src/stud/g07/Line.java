package stud.g07;

/**
 * "路"的数据结构
 * 
 * 路(Line)是六子棋中一个重要概念，表示棋盘上一条潜在的连珠线。
 * 每条路包含一个方向和若干连续位置，用于快速评估棋型和生成着法。
 * 
 * 【设计说明 - 为V2阶段预留】
 * - 路可用于快速定位关键位置
 * - 路的分值可用于着法排序
 * - 路的更新可实现增量式评估
 * 
 * @author V1阶段开发
 * @version 1.0
 */
public class Line {
    /**
     * 路的起始位置（棋盘索引）
     */
    private final int startPos;

    /**
     * 路的方向
     */
    private final Direction direction;

    /**
     * 路的长度（包含的位置数）
     */
    private final int length;

    /**
     * 己方棋子数量
     */
    private int myCount;

    /**
     * 对方棋子数量
     */
    private int oppCount;

    /**
     * 空位数量
     */
    private int emptyCount;

    /**
     * 当前路的棋型（己方视角）
     */
    private Pattern myPattern;

    /**
     * 当前路的棋型（对方视角）
     */
    private Pattern oppPattern;

    /**
     * 路的分值（综合评估）
     */
    private int score;

    /**
     * 是否有效（没有被双方棋子阻断）
     */
    private boolean valid;

    /**
     * 构造函数
     * 
     * @param startPos  起始位置
     * @param direction 方向
     * @param length    长度
     */
    public Line(int startPos, Direction direction, int length) {
        this.startPos = startPos;
        this.direction = direction;
        this.length = length;
        this.myCount = 0;
        this.oppCount = 0;
        this.emptyCount = length;
        this.myPattern = Pattern.NONE;
        this.oppPattern = Pattern.NONE;
        this.score = 0;
        this.valid = true;
    }

    // ===================== Getters =====================

    public int getStartPos() {
        return startPos;
    }

    public Direction getDirection() {
        return direction;
    }

    public int getLength() {
        return length;
    }

    public int getMyCount() {
        return myCount;
    }

    public int getOppCount() {
        return oppCount;
    }

    public int getEmptyCount() {
        return emptyCount;
    }

    public Pattern getMyPattern() {
        return myPattern;
    }

    public Pattern getOppPattern() {
        return oppPattern;
    }

    public int getScore() {
        return score;
    }

    public boolean isValid() {
        return valid;
    }

    // ===================== Setters =====================

    public void setMyCount(int myCount) {
        this.myCount = myCount;
    }

    public void setOppCount(int oppCount) {
        this.oppCount = oppCount;
    }

    public void setEmptyCount(int emptyCount) {
        this.emptyCount = emptyCount;
    }

    public void setMyPattern(Pattern myPattern) {
        this.myPattern = myPattern;
    }

    public void setOppPattern(Pattern oppPattern) {
        this.oppPattern = oppPattern;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    // ===================== 更新方法 =====================

    /**
     * 当路上落子时更新计数
     * 
     * @param isMy 是否为己方落子
     */
    public void addPiece(boolean isMy) {
        if (isMy) {
            myCount++;
        } else {
            oppCount++;
        }
        emptyCount--;

        // 如果路上同时有双方棋子，该路失效
        if (myCount > 0 && oppCount > 0) {
            valid = false;
        }
    }

    /**
     * 计算路的潜力（还能发展成连六的可能性）
     * 
     * @return 如果不可能发展成连六返回0，否则返回潜力分值
     */
    public int calculatePotential() {
        if (!valid) {
            return 0;
        }
        // 六子棋需要连续6个子，如果路长度不足6，潜力为0
        if (length < 6) {
            return 0;
        }
        // 根据己方棋子数量计算潜力
        return myCount * myCount * 10;
    }

    @Override
    public String toString() {
        return String.format("Line[start=%d, dir=%s, len=%d, my=%d, opp=%d, empty=%d, pattern=%s]",
                startPos, direction, length, myCount, oppCount, emptyCount, myPattern);
    }
}

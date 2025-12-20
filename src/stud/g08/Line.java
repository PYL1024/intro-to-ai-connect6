package stud.g08;

/**
 * "路"的数据结构
 * 
 * 路(Line)是六子棋中一个重要概念，表示棋盘上一条潜在的连珠线。
 * 每条路包含一个方向和若干连续位置，用于快速评估棋型和生成着法。
 * 
 * 【设计说明 - V2阶段实现】
 * - 路可用于快速定位关键位置
 * - 路的分值可用于着法排序
 * - 路的更新可实现增量式评估
 * 
 * @author V1阶段开发
 * @version 2.0
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
     * 路的方向索引（0-3，对应 HORIZONTAL/VERTICAL/DIAGONAL_MAIN/DIAGONAL_ANTI）
     */
    private final int dirIndex;

    /**
     * 路的长度（包含的位置数）
     */
    private final int length;

    /**
     * 路上所有位置的索引列表（按方向顺序排列）
     */
    private final int[] positions;

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
     * @param dirIndex  方向索引（0-3）
     * @param length    长度
     * @param positions 路上所有位置的索引数组
     */
    public Line(int startPos, Direction direction, int dirIndex, int length, int[] positions) {
        this.startPos = startPos;
        this.direction = direction;
        this.dirIndex = dirIndex;
        this.length = length;
        this.positions = positions;
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

    public int getDirIndex() {
        return dirIndex;
    }

    public int getLength() {
        return length;
    }

    public int[] getPositions() {
        return positions;
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
     * 当路上撤销落子时更新计数（用于搜索回溯）
     * 
     * @param wasMy 撤销的是否为己方棋子
     */
    public void removePiece(boolean wasMy) {
        if (wasMy) {
            myCount--;
        } else {
            oppCount--;
        }
        emptyCount++;

        // 重新检查有效性
        valid = !(myCount > 0 && oppCount > 0);
    }

    /**
     * 检查指定位置是否在本路上
     * 
     * @param index 位置索引
     * @return 是否在路上
     */
    public boolean containsPosition(int index) {
        for (int pos : positions) {
            if (pos == index) {
                return true;
            }
        }
        return false;
    }

    /**
     * 重新评估路的棋型和分值（基于当前计数）
     * 仅当路有效时才计算
     */
    public void reevaluate() {
        if (!valid) {
            myPattern = Pattern.NONE;
            oppPattern = Pattern.NONE;
            score = 0;
            return;
        }

        // 己方视角棋型
        myPattern = evaluatePatternForCount(myCount, length);
        // 对方视角棋型
        oppPattern = evaluatePatternForCount(oppCount, length);

        // 综合分值 = 己方棋型分 × 2 + 对方棋型分（攻重于守）
        score = myPattern.getScore() * 2 + oppPattern.getScore();
    }

    /**
     * 根据连子数和路长度估算棋型（增强版）
     * 考虑更多细节：路长度、空位数等
     */
    private Pattern evaluatePatternForCount(int count, int lineLength) {
        if (count >= 6) {
            return Pattern.SIX;
        } else if (count == 5) {
            return Pattern.FIVE;
        } else if (count == 4) {
            // 活四需要两端有空位（路长度>=6且有>=2空位）
            int availableSpace = lineLength - count;
            if (availableSpace >= 2) {
                return Pattern.LIVE_FOUR;
            } else if (availableSpace >= 1) {
                return Pattern.RUSH_FOUR;
            }
            return Pattern.NONE;
        } else if (count == 3) {
            int availableSpace = lineLength - count;
            if (availableSpace >= 3) {
                // 有足够空间发展成活四
                return Pattern.LIVE_THREE;
            } else if (availableSpace >= 2) {
                // 可能是跳活三
                return Pattern.JUMP_THREE;
            } else if (availableSpace >= 1) {
                return Pattern.SLEEP_THREE;
            }
            return Pattern.NONE;
        } else if (count == 2) {
            int availableSpace = lineLength - count;
            if (availableSpace >= 4) {
                return Pattern.LIVE_TWO;
            } else if (availableSpace >= 1) {
                return Pattern.SLEEP_TWO;
            }
            return Pattern.NONE;
        } else if (count == 1) {
            return Pattern.LIVE_ONE;
        }
        return Pattern.NONE;
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

    /**
     * 获取己方视角的路分（用于全局评估）
     */
    public int getMyScore() {
        return valid ? myPattern.getScore() : 0;
    }

    /**
     * 获取对方视角的路分（用于全局评估）
     */
    public int getOppScore() {
        return valid ? oppPattern.getScore() : 0;
    }

    @Override
    public String toString() {
        return String.format("Line[start=%d, dir=%s, len=%d, my=%d, opp=%d, empty=%d, myPat=%s, oppPat=%s, valid=%b]",
                startPos, direction, length, myCount, oppCount, emptyCount, myPattern, oppPattern, valid);
    }
}

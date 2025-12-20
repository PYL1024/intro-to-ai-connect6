package stud.g08;

/**
 * 六子棋棋型枚举定义
 * <p>
 * 定义了六子棋中所有关键棋型及其威胁分值。
 * 棋型从低到高排序：活二 < 眠三 < 活三 < 冲四 < 活四 < 连五 < 连六
 * <p>
 * 【棋型说明】
 * - 活二(LIVE_TWO)：两子相连，两端都有空位，可发展成活三
 * - 眠三(SLEEP_THREE)：三子相连，一端被堵，只能单向发展
 * - 活三(LIVE_THREE)：三子相连，两端都有空位，可发展成活四或冲四
 * - 冲四(RUSH_FOUR)：四子相连，一端被堵，只能单向发展成连五
 * - 活四(LIVE_FOUR)：四子相连，两端都有空位，必胜棋型
 * - 连五(FIVE)：五子相连，一端被堵
 * - 连六(SIX)：六子相连，获胜棋型
 * 
 * @author V1阶段开发
 * @version 1.0
 */
public enum Pattern {
    /**
     * 空模式（无特殊棋型）
     */
    NONE(0, 0, "无棋型"),

    /**
     * 活一：单子，周围有发展空间
     * 威胁程度：极低
     */
    LIVE_ONE(1, 10, "活一"),

    /**
     * 活二：两子相连，两端开放
     * 如：_XX_ 或 _X_X_
     * 威胁程度：低
     */
    LIVE_TWO(2, 100, "活二"),

    /**
     * 眠二：两子相连，一端被堵
     * 如：|XX_ 或 _XX|
     * 威胁程度：很低
     */
    SLEEP_TWO(3, 50, "眠二"),

    /**
     * 眠三：三子相连，一端被堵
     * 如：|XXX_ 或 _XXX|
     * 威胁程度：低
     */
    SLEEP_THREE(4, 200, "眠三"),

    /**
     * 跳活三：三子带一空，两端开放
     * 如：_XX_X_ 或 _X_XX_
     * 威胁程度：中等
     */
    JUMP_THREE(5, 800, "跳活三"),

    /**
     * 活三：三子相连，两端开放
     * 如：_XXX_
     * 威胁程度：中等，可发展成活四
     */
    LIVE_THREE(6, 1000, "活三"),

    /**
     * 冲四：四子相连，一端被堵
     * 如：|XXXX_ 或 _XXXX|
     * 威胁程度：高，必须防守
     */
    RUSH_FOUR(7, 250000, "冲四"),

    /**
     * 跳冲四：四子带一空，一端被堵
     * 如：|XXX_X 或 X_XXX|
     * 威胁程度：高，必须防守
     */
    JUMP_FOUR(8, 245000, "跳冲四"),

    /**
     * 活四：四子相连，两端开放
     * 如：_XXXX_
     * 威胁程度：极高，几乎必胜
     */
    LIVE_FOUR(9, 250000, "活四"),

    /**
     * 双活三：同时形成两个活三
     * 威胁程度：极高，几乎必胜
     */
    DOUBLE_THREE(10, 245000, "双活三"),

    /**
     * 双冲四：同时形成两个冲四
     * 威胁程度：极高，必须防守
     */
    DOUBLE_FOUR(11, 255000, "双冲四"),

    /**
     * 连五：五子相连
     * 威胁程度：一步成六，即将获胜
     */
    FIVE(12, 500000, "连五"),

    /**
     * 连六：六子相连，获胜棋型
     */
    SIX(13, 1000000, "连六"),

    /**
     * 长连：超过六子（实际也算获胜）
     */
    OVERLINE(14, 1000000, "长连");

    /**
     * 棋型等级，用于比较
     */
    private final int level;

    /**
     * 威胁分值，用于评估
     */
    private final int score;

    /**
     * 棋型中文名称
     */
    private final String name;

    Pattern(int level, int score, String name) {
        this.level = level;
        this.score = score;
        this.name = name;
    }

    public int getLevel() {
        return level;
    }

    public int getScore() {
        return score;
    }

    public String getName() {
        return name;
    }

    /**
     * 判断是否为致命威胁（需要立即防守）
     * 冲四及以上都是致命威胁
     */
    public boolean isCriticalThreat() {
        return this.level >= RUSH_FOUR.level; // 包括 RUSH_FOUR, JUMP_FOUR, LIVE_FOUR, DOUBLE_THREE, DOUBLE_FOUR, FIVE, SIX
    }

    /**
     * 判断是否为高威胁（活三及以上）
     */
    public boolean isHighThreat() {
        return this.level >= LIVE_THREE.level; // 包括 LIVE_THREE 及以上
    }

    /**
     * 判断是否为中等威胁（跳活三及以上）
     */
    public boolean isMediumThreat() {
        return this.level >= JUMP_THREE.level;
    }

    /**
     * 判断是否为复合威胁（双活三或双冲四）
     */
    public boolean isCompoundThreat() {
        return this == DOUBLE_THREE || this == DOUBLE_FOUR;
    }

    /**
     * 判断是否为获胜棋型
     */
    public boolean isWinning() {
        return this.level >= SIX.level;
    }

    /**
     * 判断是否为即将获胜（连五或连六）
     */
    public boolean isNearWinning() {
        return this.level >= FIVE.level;
    }

    @Override
    public String toString() {
        return name + "(等级:" + level + ",分值:" + score + ")";
    }
}

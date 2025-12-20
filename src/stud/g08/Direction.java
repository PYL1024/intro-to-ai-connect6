package stud.g08;

/**
 * 方向枚举定义
 * <p>
 * 六子棋中检测连珠需要扫描四个方向：
 * - 水平方向（横向）
 * - 垂直方向（纵向）
 * - 主对角线方向（左上到右下）
 * - 副对角线方向（右上到左下）
 * 
 * @author V1阶段开发
 * @version 1.0
 */
public enum Direction {
    /**
     * 水平方向：从左到右
     * dx=0, dy=1 表示列号递增
     */
    HORIZONTAL(0, 1, "水平"),

    /**
     * 垂直方向：从上到下
     * dx=1, dy=0 表示行号递增
     */
    VERTICAL(1, 0, "垂直"),

    /**
     * 主对角线：从左上到右下
     * dx=1, dy=1 表示行列号同时递增
     */
    DIAGONAL_MAIN(1, 1, "主对角线"),

    /**
     * 副对角线：从右上到左下
     * dx=1, dy=-1 表示行号递增，列号递减
     */
    DIAGONAL_ANTI(1, -1, "副对角线");

    /**
     * 行增量
     */
    public final int dx;

    /**
     * 列增量
     */
    public final int dy;

    /**
     * 方向名称
     */
    public final String name;

    Direction(int dx, int dy, String name) {
        this.dx = dx;
        this.dy = dy;
        this.name = name;
    }

    /**
     * 获取所有四个方向
     */
    public static Direction[] getAllDirections() {
        return values();
    }

    @Override
    public String toString() {
        return name;
    }
}

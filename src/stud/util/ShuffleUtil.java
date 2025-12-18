package stud.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 洗牌算法工具类：提供棋盘位置的随机洗牌功能
 */
public class ShuffleUtil {
    /**
     * 对指定范围的棋盘位置进行洗牌
     * @param start 起始索引（包含）
     * @param end 结束索引（包含）
     * @param boardSize 棋盘大小（默认19）
     * @return 洗牌后的位置列表
     */
    public static List<Integer> shufflePositions(int start, int end, int boardSize) {
        List<Integer> positions = new ArrayList<>();
        // 填充指定范围的位置
        for (int i = start; i <= end; i++) {
            int x = i / boardSize;
            int y = i % boardSize;
            // 确保位置在棋盘范围内
            if (x >= 0 && x < boardSize && y >= 0 && y < boardSize) {
                positions.add(i);
            }
        }
        // 洗牌（Fisher-Yates算法，Collections.shuffle底层实现）
        Collections.shuffle(positions);
        return positions;
    }

    /**
     * 对整个棋盘位置洗牌
     * @param boardSize 棋盘大小
     * @return 洗牌后的全局位置列表
     */
    public static List<Integer> shuffleGlobalPositions(int boardSize) {
        return shufflePositions(0, boardSize * boardSize - 1, boardSize);
    }

    /**
     * 对中心13*13区域洗牌
     * @param boardSize 棋盘大小（19）
     * @return 洗牌后的中心区域位置列表
     */
    public static List<Integer> shuffleCenterPositions(int boardSize) {
        int centerStart = 3 * boardSize + 3; // (3,3)
        int centerEnd = 15 * boardSize + 15; // (15,15)
        return shufflePositions(centerStart, centerEnd, boardSize);
    }
}

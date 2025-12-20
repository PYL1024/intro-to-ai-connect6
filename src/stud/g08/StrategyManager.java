package stud.g08;

import java.util.Random;

/**
 * 策略管理（多臂老虎机轮换）
 * - 由于框架未直接提供胜负回调，这里使用轻量的轮换策略；
 * - 可根据搜索统计（节点/剪枝率）或外部结果集成奖励更新。
 */
public class StrategyManager {
    public static class Strategy {
        public boolean useMCTS;
        public int mctsTimeMs;
        Strategy(boolean useMCTS, int mctsTimeMs) { this.useMCTS = useMCTS; this.mctsTimeMs = mctsTimeMs; }
    }

    public static class Selection {
        public final int id;
        public final Strategy strategy;
        Selection(int id, Strategy strategy) { this.id = id; this.strategy = strategy; }
    }

        private static final Strategy[] STRATEGIES = new Strategy[] {
            new Strategy(true, 600),   // 默认
            new Strategy(true, 900),   // 更长仿真
            new Strategy(false, 0)     // 纯 α-β
    };

    private static final double[] totalReward = new double[STRATEGIES.length];
    private static final int[] visits = new int[STRATEGIES.length];
    private static int totalSelections = 0;
    private static final Random rnd = new Random(20251220L);

    /** UCB1 选择策略；无数据时均匀探索 */
    public static synchronized Selection pickStrategyForNewGame() {
        totalSelections++;
        double c = 1.0;
        int bestId = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < STRATEGIES.length; i++) {
            if (visits[i] == 0) {
                // 未探索，优先
                double val = 1e9 + rnd.nextDouble();
                if (val > bestScore) { bestScore = val; bestId = i; }
            } else {
                double avg = totalReward[i] / visits[i];
                double explore = c * Math.sqrt(Math.log(totalSelections + 1.0) / visits[i]);
                double val = avg + explore;
                if (val > bestScore) { bestScore = val; bestId = i; }
            }
        }
        if (bestId < 0) bestId = rnd.nextInt(STRATEGIES.length);
        return new Selection(bestId, STRATEGIES[bestId]);
    }

    /** 基于搜索表现的伪奖励：剪枝率高、节点少得分更高；范围粗略压到 [0,1] */
    public static synchronized void reportSearchReward(int strategyId, int nodes, double cutoffRate) {
        if (strategyId < 0 || strategyId >= STRATEGIES.length) return;
        double speed = 1.0 / (1.0 + nodes / 150000.0); // 节点越少越好
        double reward = 0.5 * Math.max(0, Math.min(1, cutoffRate)) + 0.5 * speed;
        visits[strategyId] += 1;
        totalReward[strategyId] += reward;
    }

    /** 可接入对局结果（胜=1，和=0.5，负=0）以强化策略权重 */
    public static synchronized void reportGameResult(int strategyId, double result) {
        if (strategyId < 0 || strategyId >= STRATEGIES.length) return;
        double r = Math.max(0, Math.min(1, result));
        visits[strategyId] += 1;
        totalReward[strategyId] += r;
    }

    // 外部脚本相关接口移除
}

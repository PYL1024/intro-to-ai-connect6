# g06 盘面估值与搜索落地路线（面向 α-β）

> 当前目标：能够快速找出当前局面下双方的“有效路”，并能对局面进行快速评估；
> 聚焦“快速找有效路 + 快速评估”，为后续博弈树搜索与 α-β 剪枝做准备。

## Step 1：路表生成（一次全盘）✅ 已完成

- 在 `V1Board` 增加 `buildLines(PieceColor myColor)`：按四方向滑窗长度 6 遍历，过滤双方同时占子的线段为无效路，生成 `List<Line>`。
- 初始化填充：`myCount/oppCount/emptyCount/valid`，判定 `myPattern/oppPattern` 与初始 `score/potential`。
- 建立 `lineIndexByCell[row][col][dir]` 索引，支持快速查找某位置涉及的路。

## Step 2：全局评估函数 ✅ 已完成

- 新增 `evaluateBoard()`：返回 `boardScoreMy - boardScoreOpp`。
- 路分由 `Line.reevaluate()` 计算：`myPattern.score * 2 + oppPattern.score`（攻重于守）。
- 该函数用于搜索叶节点评估与着法排序。
- 辅助：`evaluateMoveIncrement(index, myColor)` 评估假设落子的局部增量。

## Step 3：增量更新 ✅ 已完成

- 维护：`lines`、`lineIndexByCell[SIZE][SIZE][4]`、`boardScoreMy/boardScoreOpp`。
- `updateLinesAfterMove(index, color, myColor)`：只重算经过该点的路，更新计数/棋型/路分，差分更新全局分。
- `undoLinesAfterMove(index, color, myColor)`：撤销函数，用于搜索回溯。

## Step 4：候选与评估结合 ✅ 已完成

- MoveGenerator 的 `evaluatePosition` 优先使用 `board.evaluateMoveIncrement()` 进行路表增量评估。
- 当路表未构建时回退到 V1 的四向扫描评估。
- AlphaBetaSearcher 的 `generateOrderedMoves` 也使用路表增量评估排序候选。

## Step 5：威胁精细化 ✅ 已完成

### Pattern 枚举扩展

- 新增棋型：`SLEEP_TWO`（眠二）、`JUMP_THREE`（跳活三）、`JUMP_FOUR`（跳冲四）、`DOUBLE_THREE`（双活三）、`DOUBLE_FOUR`（双冲四）
- 调整分值体系：LIVE_THREE=1000, JUMP_THREE=800, LIVE_FOUR=50000, DOUBLE_THREE=45000, DOUBLE_FOUR=48000
- 新增方法：`isMediumThreat()`、`isCompoundThreat()`

### ThreatAnalyzer 新增类

- 精细化威胁检测：正反两个方向扫描，统计己方棋子数、空位数、间隙数、阻挡情况
- 复合威胁检测：`detectCompoundThreats()` 检测双活三、双冲四、冲四活三
- 结果类 `ThreatResult`：包含 `isCriticalThreat()`、`isWinningThreat()`、`hasDoubleThree()` 等

### AI.java 集成

- `detectCompoundThreat()`：在α-β搜索前检测对方复合威胁
- `findBestAttackPosition()`：防守后选择最佳进攻位置

## Step 6：搜索与 α-β 接入 ✅ 已完成 + 优化

### 基础搜索

- 新增 `AlphaBetaSearcher.java`：实现基础 α-β + 迭代加深（深度 2-4）。
- 时间裁剪：500ms 超时控制。
- 搜索时调用 `makeMove`/`undoMove` 进行模拟落子与回溯，使用路表增量更新。
- 在 `AI.java` 中集成：胜着检测 → 致命威胁防守 → 复合威胁检测 → α-β 搜索 → 启发式回退。

### 剪枝优化（V2.1）

- **杀手启发 (Killer Move Heuristic)**：`killerMoves[depth][slot]` 记录引起剪枝的着法，同层优先搜索
- **历史启发 (History Heuristic)**：`historyTable[position]` 记录每个位置引起剪枝的次数，累加权重
- **主变量搜索 (PVS)**：首子完整窗口，后续子空窗口试探，失败则重搜
- **威胁延伸 (Threat Extension)**：致命威胁着法延伸1层搜索深度
- 新增统计：`cutoffCount`、`getCutoffRate()` 评估剪枝效率

## Step 7：验证与基准（待实现）

- AITester 增加小深度搜索对局对比：基线 V1 vs 增量评估 + α-β。
- 构造冲四/活三局面单测，验证路表与防守点；记录评估函数调用次数与耗时做性能基线。

# g08 V1/V2 功能/函数索引

> 摘要：汇总当前版本在 g08 包内的核心数据结构、方法与用途，便于后续迭代（α-β 剪枝、增量评估等）。

## Pattern.java

- 枚举 `Pattern`：NONE/LIVE_ONE/LIVE_TWO/SLEEP_TWO/SLEEP_THREE/LIVE_THREE/RUSH_FOUR/LIVE_FOUR/FIVE/SIX/OVERLINE
- 字段：`level`（等级）、`score`（威胁分值）、`name`
- 方法：`getLevel()`、`getScore()`、`getName()`、`isCriticalThreat()`（≥冲四）、`isHighThreat()`（≥活三）、`isWinning()`（≥连六）、`isNearWinning()`（≥连五）、`toString()`

## Direction.java

- 枚举 `Direction`：HORIZONTAL、VERTICAL、DIAGONAL_MAIN、DIAGONAL_ANTI
- 字段：`dx`、`dy`、`name`
- 方法：`getAllDirections()`、`toString()`

## Line.java（V2路表实现）

- 字段：`startPos`、`direction`、`dirIndex`、`length`、`positions[]`、`myCount`、`oppCount`、`emptyCount`、`myPattern`、`oppPattern`、`score`、`valid`
- 构造：`Line(int startPos, Direction direction, int dirIndex, int length, int[] positions)`
- Getter：`getStartPos()`、`getDirection()`、`getDirIndex()`、`getLength()`、`getPositions()`、`getMyCount()`、`getOppCount()`、`getEmptyCount()`、`getMyPattern()`、`getOppPattern()`、`getScore()`、`isValid()`、`getMyScore()`、`getOppScore()`
- Setter：对应全部可变字段
- 更新：
  - `addPiece(boolean isMy)`：增计数，若双方都有子则失效
  - `removePiece(boolean wasMy)`：撤销计数，重新检查有效性（用于搜索回溯）
  - `containsPosition(int index)`：检查位置是否在路上
  - `reevaluate()`：重新评估棋型和分值
  - `calculatePotential()`：返回潜力分
- 内部：`evaluatePatternForCount(int count, int length)`：根据计数估算棋型

## V1Board.java（扩展棋盘 + V2路表）

- 常量：`SIZE=19`、`WIN_COUNT=6`、`SEARCH_RADIUS=2`、`LINE_LENGTH=6`、方向增量 `DX/DY`
- V1状态：`isCandidate`、`candidatePositions`、`pieceCount`、`minRow/maxRow/minCol/maxCol`
- V2状态：`lines`（路列表）、`lineIndexByCell[row][col][dir]`（位置-方向到路索引）、`boardScoreMy`、`boardScoreOpp`、`linesBuilt`
- 构造：`V1Board()` → 初始化候选、边界、路表结构
- V1方法：
  - 坐标：`toIndex(row,col)`、`toRow(idx)`、`toCol(idx)`、`isValidPosition(row,col)`、`isValidIndex(idx)`
  - 访问：`getColor(row,col)`、`isEmpty(row,col)`、`isEmpty(idx)`
  - 候选：`updateCandidatesAfterMove(idx)`、`getCandidatePositions()`
  - 边界：`getSearchBounds()`
  - 胜着检测：`checkWinAt(idx,color)`、`checkWinWithTwoMoves(pos1,pos2,color)`、`checkWinIfPlace(idx,color)`、`checkWinIfPlaceTwo(pos1,pos2,color)`、`checkWinWithTemps(...)`
  - 棋型检测/评分：`detectPatternAt(idx,color,dir)`、`evaluatePattern(count,block,empty)`、`getBestPatternAt(idx,color)`、`evaluatePositionScore(idx,color)`
  - 计数：`countConsecutive(row,col,color,dir)`、`countBidirectional(row,col,color,dir)`
  - 工具：`toDebugString()`、`getPieceCount()`
- **V2路表方法（新增）**：
  - 【Step 1】`buildLines(PieceColor myColor)`：一次全盘扫描，生成所有长度为6的路，建立位置到路的索引，初始化全局分
  - 【Step 3】`updateLinesAfterMove(index, color, myColor)`：落子后增量更新涉及的路，维护全局分
  - 【Step 3】`undoLinesAfterMove(index, color, myColor)`：撤销落子后回滚路表（用于搜索）
  - 【Step 2】`evaluateBoard()`：快速全局评估，返回 `boardScoreMy - boardScoreOpp`
  - `getBoardScoreMy()`、`getBoardScoreOpp()`：获取全局分
  - `getLines()`：获取路列表
  - `getLineIndicesAt(row, col, dir)`：获取位置-方向的路索引
  - `isLinesBuilt()`：路表是否已构建
  - `getValidLinesAt(index)`：获取位置涉及的有效路
  - `evaluateMoveIncrement(index, myColor)`：假设落子的局部分值增量（用于候选评分优化）
  - 内部：`estimateScoreForCount(count, length)`：根据计数估算分值

## ThreatEvaluator.java（威胁检测/防守点）

- 内部类 `Threat`：`position`、`pattern`、`defensePoints`、`direction`; 方法 `getDangerLevel()`、`toString()`
- 状态：`board`、`threats`、`criticalCount`、`highCount`
- 核心：`detectAllThreats(oppColor)` → 使用边界扫描、`detectThreatInDirection` → `analyzeLine`/`evaluateThreatPattern`
- 防守点：`findDefensePoints(...)` 收集空位；`markLineChecked(...)`
- 防守策略接口：`getDefensePositions(oppColor)`（排序、截断≤2点）
- 快捷查询：`hasUrgentThreat(oppColor)`、`getThreatCount()`、`getCriticalThreatCount()`、`getHighThreatCount()`、`getThreats()`

## MoveGenerator.java（着法生成）

- 常量：`SIZE`、`MAX_CANDIDATES=50`、`POSITION_SCORE`（中心高）
- 状态：`board`、`threatEvaluator`
- 胜着：`findWinningMove(myColor)`（单点/双点模拟 `checkWinIfPlace/Two`）
- 候选评分：`evaluateAllCandidates(myColor,oppColor)` → `evaluatePosition(pos,...)`
  - 【V2优化】优先使用 `board.evaluateMoveIncrement()` 路表增量评估
  - 回退到 V1 四向扫描评估
- 着法生成：`generateMoves(myColor,oppColor)`
  - 判定紧急防守：致命威胁或威胁数≤2且有防守点
  - 防守模式：`generateDefensiveMoves(defensePositions, scoredPositions, myColor)`
  - 进攻模式：`generateOffensiveMoves(scoredPositions)`
- 其他：`containsMove(...)` 去重，内部类 `ScoredPosition`
- 取威胁评估器：`getThreatEvaluator()`
- 静态：`getPositionScore(row, col)` 获取位置分

## AlphaBetaSearcher.java（V2新增：α-β搜索）

- 常量：`INF`、`WIN_SCORE`、`DEFAULT_DEPTH=4`、`MAX_TIME_MS=4500`、`MAX_MOVES=30`
- 状态：`board`、`moveGenerator`、`myColor`、`oppColor`、`startTime`、`timeout`、`nodeCount`、`bestMove`
- 核心：
  - `search(myColor, oppColor)`：迭代加深搜索入口，返回最佳着法
  - `searchAtDepth(depth)`：指定深度搜索
  - `alphaBeta(depth, alpha, beta, current, opponent, maximizing)`：α-β递归核心
- 辅助：
  - `generateOrderedMoves(current, opponent)`：基于路表增量评估排序候选
  - `makeMove(move, color)`：模拟落子（更新棋盘+路表）
  - `undoMove(move, color)`：撤销落子（恢复棋盘+路表）
  - `checkWin(move, color)`：检查着法是否导致获胜
  - `evaluate(side)`：基于路表的局面评估
  - `isTimeout()`：超时检查
- 统计：`getNodeCount()`
- 内部类：`ScoredPosition`、`ScoredMove`

## AI.java（V2决策主流程）

- 常量：`BOARD_SIZE=19`、`AI_NAME="G08-V3-TBS"`、`USE_ALPHA_BETA=true`
- 状态：`v1Board`、`moveGenerator`、`alphaBetaSearcher`、`myColor`、`oppColor`、`isFirstMove`、`turnCount`、`linesInitialized`
- 核心：`findNextMove(Move opponentMove)`
  - 同步对手落子到 `board` 与候选
  - `determineColor()` 判定先后手
  - `initOrUpdateLines(opponentMove)`：初始化或增量更新路表
  - `makeDecision()`：
    1. 胜着检测 → 如有立即执行
    2. 致命威胁防守（冲四/活四）
    3. α-β搜索最优着法
    4. 回退到启发式着法生成
  - 执行落子并更新候选和路表
- 防守辅助：`createDefenseMove(defensePositions)`
- 路表管理：`initOrUpdateLines(opponentMove)`
- 工具：`countPieces()`、`fallbackMove()`、`syncBoard()`
- 生命周期：`name()`、`playGame(Game game)` 重置状态并装配组件（含 AlphaBetaSearcher）

## 其他文件

- V1Algorithms.md：伪代码与流程说明（胜着检测、威胁、防守点、排序）。
- V2Roadmap.md：V2 开发路线图与进度跟踪。
- ShuffleUtil 等在 util 包，当前未被 g08 主要流程调用。

# g08 V3 功能索引与作用说明

> 概要：梳理 g08 阶段新增/强化的核心组件，列出作用、输入输出与协作关系，方便快速定位改进点。

## AI.java（决策编排）
- 常量：`AI_NAME="G08-V3-TBS"`、`USE_ALPHA_BETA`、`USE_THREAT_SPACE`、`TBS_MAX_DEPTH`、`TBS_TIME_LIMIT_MS`
- 决策流程：胜着检测 → 紧急防守 → 复合威胁判断 → 威胁空间搜索(TBS) → α-β 搜索 → 启发式回退
- 辅助：候选扩展 `expandCandidatesIfNeeded()`、复合威胁 `detectCompoundThreat()`、防守着法拼装 `createDefenseMove()`

## ThreatSpaceSearcher.java（V3新增：威胁空间搜索）
- 目标：枚举“强制威胁 -> 必然防守”链，提前截获必胜序列
- 接口：`searchForcingWin(attacker, defender, depth, timeMs)`
- 关键：威胁着法筛选 `collectThreateningMoves()`、防守生成 `generateDefenseResponses()`、递归过程 `searchAttacks()/searchDefenses()`、状态回溯 `applyMove()/revertMove()`
- 评分：使用 `ThreatAnalyzer` 对模拟落子后的威胁进行打分，优先双冲四/冲四活三等强威胁

## AlphaBetaSearcher.java（剪枝 + PVS + 启发）
- 搜索：迭代加深 + 主变量搜索(PVS) + 威胁延伸，时间裁剪 500ms
- 启发：历史表、杀手表、位置分、路表增量评估，最多 8 组双子着法
- 回溯：`captureStateForSearch()`/`restoreStateForSearch()` 与路表 `updateLinesAfterMove()`/`undoLinesAfterMove()` 组合保证精确复原

## MoveGenerator.java（着法生成与排序）
- 胜着：`findWinningMove()` 支持单点/双点模拟胜利
- 评估：位置分 + 路表增量分 + 对方视角防守分
- 输出：启发式进攻着法、紧急防守着法，供 AI/TBS/α-β 共用
- 工具：`getThreatEvaluator()` 暴露威胁信息给 AI/TBS

## ThreatAnalyzer.java（复合威胁检测）
- 单点威胁：四向扫描统计己方棋子、空位、间隙、阻断
- 复合威胁：识别双活三、双冲四、冲四活三，并计算总威胁分
- 返回：`ThreatResult` 包含最优棋型、威胁分、复合标记 `hasDoubleThree/hasDoubleFour/hasFourThree`

## ThreatEvaluator.java（全局威胁枚举与防守点）
- 扫描对手棋子，输出威胁列表与防守点，按致命/高威胁排序
- 快捷：`getDefensePositions()`、`getCriticalThreatCount()`、`getThreatCount()`，为 AI 防守与 TBS 防守生成服务

## V1Board.java + Line.java（路表与增量评估）
- 路表：`buildLines()` 全盘滑窗生成路；位置到路索引 `lineIndexByCell` 支撑增量更新
- 评估：`evaluateBoard()` 全局分；`evaluateMoveIncrement()` 用于候选排序与启发式
- 回溯：候选/边界快照 `captureStateForSearch()` + 路表增量 `updateLinesAfterMove()`/`undoLinesAfterMove()`

## Pattern.java / Direction.java（基础枚举）
- 棋型等级、分值、威胁判定辅助方法；方向枚举支撑路与棋型扫描

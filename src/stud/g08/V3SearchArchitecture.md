# G08 搜索架构概览

- AI：决策管线，顺序为 胜着 → 紧急防守 → TBS → MCTS → AlphaBeta → 启发式回退。
- MoveGenerator：候选与排序，路表增量评估、协同评分、防守点生成。
- ThreatEvaluator/Analyzer：威胁检测与防守点计算，提供致命/高威胁信息。
- AlphaBetaSearcher：迭代加深 + PVS/LMR/威胁延伸，基于路表快速评估，杀手/历史启发。
- ThreatSpaceSearcher：强制威胁链搜索，枚举我方威胁与对方防守，快速命中必胜序列。
- MCTSSearcher：在时间预算内，用UCT在候选着法间进行仿真评估，选择平均胜率最高的着法。

## 关键协作
- 所有搜索器共享 `V1Board` 的候选与路表接口，并使用增量更新/回溯。
- 评估层使用 `board.evaluateMoveIncrement()` 和 `board.evaluateBoard()` 保持一致性。
- 防守优先：AI 在 TBS/MCTS/AlphaBeta 之前均处理致命与复合威胁，保证保守策略。

## 代码清晰性
- 搜索入口方法：`searchThreatSpace()`、`searchByMCTS()`、`alphaBetaSearcher.search(...)`。
- 落子/回溯统一：在各搜索器中实现 `makeMove/undoMove/checkWin`，依赖 `captureStateForSearch/restoreStateForSearch`。
- 常量集中：AI 中统一控制各模块开关与时间/深度参数，便于调优。
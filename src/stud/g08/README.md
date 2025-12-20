# G08 设计与实现（合并版）

本文件整合原先的组件索引、架构概览、优化说明，聚焦代码中已实现的内容，便于阅读与维护。

## 决策流程
- 胜着检测 → 紧急防守/复合威胁 → 开局库（早期、低优先级）→ TBS → MCTS → α-β → 启发式回退。
- 防守优先：多处威胁检查与安全门，避免忽视对手强威胁。

## 架构与组件
- AI（src/stud/g08/AI.java）：编排流程，落子与回溯、阶段性候选扩展、复合威胁检测、开局库、TBS/MCTS/α-β 的入口。
- MoveGenerator（src/stud/g08/MoveGenerator.java）：生成高分进攻/紧急防守双子；评分包含位置分、路表增量分、防守权重与协同加权。
- ThreatEvaluator/ThreatAnalyzer（src/stud/g08/ThreatEvaluator.java, ThreatAnalyzer.java）：全局威胁枚举、防守点；单点复合威胁（双活三/双冲四/冲四活三）。
- V1Board/Line（src/stud/g08/V1Board.java, Line.java）：路表（长度6线段）及位置到路索引；增量更新与快照回溯；全局/增量评估。
- ThreatSpaceSearcher（src/stud/g08/ThreatSpaceSearcher.java）：威胁空间搜索（强制链：我攻→彼防），威胁筛选、防守生成、OR/AND 递归与缓存。
- PNSearcher（src/stud/g08/PNSearcher.java）：在 TBS 前做 AND/OR 证明搜索；Zobrist+缓存；限定威胁候选，控制节点/时间预算。
- AlphaBetaSearcher（src/stud/g08/AlphaBetaSearcher.java）：迭代加深、PVS、LMR、静态搜索、威胁延伸；共享 TT 与 Aspiration 窗口。
- MCTSSearcher（src/stud/g08/MCTSSearcher.java）：PUCT + 渐进加宽；先验来自 MoveGenerator；仿真/启发式回传；必要时回退共享 TT 估值。
- TranspositionTable（src/stud/g08/TranspositionTable.java）：共享 TT（EXACT/LOWER/UPPER + bestMove），容量可通过系统属性配置。
- OpeningBook（src/stud/g08/OpeningBook.java）：早期仅在确认无紧急威胁时使用的低优先级开局模板。

## 关键优化（对应实现位置）
1) 置换表 TT + Zobrist：AlphaBeta/MCTS/TBS 维护哈希；`probeEval/storeEval` 结合 `EXACT/LOWER/UPPER`；AlphaBeta 根采用期望窗（AI→AlphaBetaSearcher）。
2) LMR：对非战术且排序靠后着法先降深试探，fail-high 再全深复搜（AlphaBetaSearcher.alphaBeta）。
3) 静态搜索（Quiescence）：叶子仅扩展战术相关着法，降低评估噪声（AlphaBetaSearcher.quiescence）。
4) 双子协同评分：同一路/潜在双威胁组合加分（MoveGenerator，ThreatSpaceSearcher.evaluateThreatMove）。
5) TBS 缓存与裁剪：`resultCache/defenseCache`；进攻/防守上限；复合威胁模板评分；状态快照+回溯（ThreatSpaceSearcher）。
6) PN-search：OR/AND 证明/否证；Zobrist+缓存；威胁限定生成；时间/节点预算（PNSearcher，ThreatSpaceSearcher 搜索入口）。
7) MCTS（PUCT + Progressive Widening）：PUCT 选子，访问数驱动放宽展开；仿真回传；共享 TT 估值兜底（MCTSSearcher）。

## 参数与可调项
- 系统属性：`g08.tt.capacity`（整型）控制共享 TT 容量。
- 其他阈值（候选上限、评分权重等）在各类常量中定义，便于按机器性能或风格微调。

## 文件导航（关键文件）
- AI：src/stud/g08/AI.java
- MoveGenerator：src/stud/g08/MoveGenerator.java
- ThreatEvaluator / ThreatAnalyzer：src/stud/g08/ThreatEvaluator.java，src/stud/g08/ThreatAnalyzer.java
- V1Board / Line：src/stud/g08/V1Board.java，src/stud/g08/Line.java
- ThreatSpaceSearcher / PNSearcher：src/stud/g08/ThreatSpaceSearcher.java，src/stud/g08/PNSearcher.java
- AlphaBetaSearcher / TranspositionTable：src/stud/g08/AlphaBetaSearcher.java，src/stud/g08/TranspositionTable.java
- MCTSSearcher：src/stud/g08/MCTSSearcher.java
- OpeningBook：src/stud/g08/OpeningBook.java

## 备注
- 不依赖外部 JSON/脚本配置；日志与外部调参相关内容已移除。
- 文档已合并，历史说明可查 Git 历史。
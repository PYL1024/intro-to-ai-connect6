# G08 设计与实现（合并版）

本文件整合原先的组件索引、架构概览、优化说明，聚焦代码中已实现的内容，便于阅读与维护。

## 决策流程
- 胜着检测 → 紧急防守/复合威胁 → TBS → α-β（根前置 TSS 试探）→ 启发式回退。
- 防守优先：多处威胁检查与安全门，避免忽视对手强威胁。
- AI（src/stud/g08/AI.java）：编排流程，落子与回溯、阶段性候选扩展、复合威胁检测、TBS/α-β 的入口。

- ThreatEvaluator/ThreatAnalyzer（src/stud/g08/ThreatEvaluator.java, ThreatAnalyzer.java）：全局威胁枚举、防守点；单点复合威胁（双活三/双冲四/冲四活三）。
- ThreatSpaceSearcher（src/stud/g08/ThreatSpaceSearcher.java）：威胁空间搜索（强制链：我攻→彼防），威胁筛选、防守生成、OR/AND 递归与缓存。
- PNSearcher（src/stud/g08/PNSearcher.java）：在 TBS 前做 AND/OR 证明搜索；Zobrist+缓存；限定威胁候选，控制节点/时间预算。
- AlphaBetaSearcher（src/stud/g08/AlphaBetaSearcher.java）：迭代加深、PVS、LMR、静态搜索、威胁延伸；共享 TT 与 Aspiration 窗口；根节点 500ms TSS 试探（调用 ThreatSpaceSearcher）。
- TranspositionTable（src/stud/g08/TranspositionTable.java）：共享 TT（EXACT/LOWER/UPPER + bestMove），容量可通过系统属性配置。

### 端点/中间威胁的策略补充（优先级调整）
- 严格双端封堵（高优先）：当检测到连续四子且两端皆为空，且两端外侧紧邻也不是我方子时，强制双端堵住，避免被延展形成六连。
- 单端/中间双空（中优先）：当一端外侧已被我方子占据或出现“对方子 对方子 空 空 对方子 对方子”模式（两侧均为对方子，中间双空），先在更关键的一端或中间空位单点封堵；第二子通过局部 α-β（固定第一子）快速选择。该逻辑位于 TBS 之前，以避免低级防守失误；仍保持整体决策以 α-β/TBS 为主。

## 关键优化（对应实现位置）
1) 置换表 TT + Zobrist：AlphaBeta/TBS 维护哈希；`probeEval/storeEval` 结合 `EXACT/LOWER/UPPER`；AlphaBeta 根采用期望窗（AI→AlphaBetaSearcher）。
2) LMR：对非战术且排序靠后着法先降深试探，fail-high 再全深复搜（AlphaBetaSearcher.alphaBeta）。
3) 静态搜索（Quiescence）：叶子仅扩展战术相关着法，降低评估噪声（AlphaBetaSearcher.quiescence）。

5) TBS 缓存（含候选裁剪）：`resultCache/defenseCache`；启发裁剪攻击/防守候选上限；复合威胁模板评分；状态快照+回溯（ThreatSpaceSearcher）。
6) PN-search：OR/AND 证明/否证；Zobrist+缓存；威胁限定生成；时间/节点预算（PNSearcher，ThreatSpaceSearcher 搜索入口）。


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
- （已移除）MCTSSearcher：不再使用

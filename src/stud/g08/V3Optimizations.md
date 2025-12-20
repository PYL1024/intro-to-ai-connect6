# V3 优化（详细说明）

本节详细解释当前代码中已实现的优化算法、触发条件与关键实现位置，便于阅读与维护。

## 1) 置换表 TT + Zobrist 哈希
- 目的：避免重复搜索、用边界信息收窄窗口，提升剪枝效率。
- 数据结构：`TranspositionTable.TTEntry { long key; int value; int depth; Flag flag; int[] bestMove; }`，其中 `flag ∈ {EXACT, LOWER, UPPER}`。
- 关键位置：
	- AlphaBeta 计算初始键与维护：`AlphaBetaSearcher.computeInitialInternalKey()`、`xorZobrist()`；共享 TT 键 `sharedTT.computeInitialKey(board)`。
	- 置换表查询/存储：`sharedTT.probeEval(key, depth)`、`sharedTT.storeEval(key, depth, value, flag, bestMove)`。
	- Aspiration Window：根节点以 `lastRootScore ± window` 搜索，失败再扩大窗口（`searchAtDepthWithWindow(...)`）。
- 效果：重复局面直接返回；在 LOWER/UPPER 命中时可提前 cutoff 或快速 re-search。

## 2) LMR（Late Move Reductions）
- 目的：对排序靠后的“非战术”着法减深试探，节省时间；必要时复搜保证质量。
- 触发条件（AlphaBeta）：`depth ≥ 4 && idx ≥ 4 && !isThreatMove(move) && !isDefBlock`。
- 实现：先以 `reducedDepth = depth-1` 空窗口试探；若 `score > alpha`（fail-high），再以原深度全窗口复搜。
- 位置：`AlphaBetaSearcher.alphaBeta(...)` 中 LMR 分支与复搜逻辑。

## 3) 静态搜索（Quiescence Search）
- 目的：在叶子处继续仅扩展“战术相关”着法（致命威胁/双三等），减少静态评估噪声与地平线效应。
- 实现：`AlphaBetaSearcher.quiescence(alpha,beta,...)`，调用受限的着法生成函数，仅含关键战术候选。
- 与 LMR 配合：非关键分支被降深后若落到叶子，静搜可小范围延伸战术线，提升稳定性。

## 4) 双子协同评分（Pair Synergy）
- 目的：Connect6 每步落两子，同一路/相互增强的组合更强；在排序上显式加权。
- 实现：
	- 在 `MoveGenerator.generateMoves(...)` 评分中叠加两点的增量路表分，并对“同一路（`Line.containsPosition`）”加分。
	- 在 TBS 的 `evaluateThreatMove(...)` 中，若两点同一路则附加威胁加权（例如 +50_000）。
- 影响：有效提升高价值组合的排序优先级，间接提高剪枝效率。

## 5) TBS 缓存与裁剪（威胁空间搜索）
- 目的：威胁链搜索重复局面多，采用缓存与上限裁剪收敛搜索规模。
- 缓存：
	- `resultCache: Map<Long, Boolean>` 记忆当前键下攻方是否可成链；
	- `defenseCache: Map<Long, List<int[]>>` 缓存对手防守集合，避免重复生成。
- 候选裁剪：
	- 进攻上限 `ATTACK_MOVE_LIMIT`，在 `collectThreateningMoves()` 中按 `ThreatAnalyzer` 评分筛选（四三/双四/双三与致命威胁高权重）。
	- 防守上限 `DEFENSE_MOVE_LIMIT`，优先必堵点（`ThreatEvaluator.getDefensePositions()`），不足两点时为其拼接搭档，再补高分防守/反击。
- 回溯：统一使用 `captureStateForSearch/restoreStateForSearch` 与 `updateLinesAfterMove/undoLinesAfterMove`，保证候选/路表一致。
- 位置：`ThreatSpaceSearcher.*`。

## 6) PN-search（Proof-Number Search）
- 目的：在 TBS 前先以 AND/OR 树快速证明/否证是否存在必胜首手，加速命中强制胜链。
- 逻辑：
	- OR 节点（进攻）：`proof = min(child.proof)`，`disproof = Σchild.disproof`；返回最佳首手。
	- AND 节点（防守）：`proof = Σchild.proof`，`disproof = min(child.disproof)`。
- 裁剪与缓存：
	- 采用 Zobrist 键与 `pnCache: Map<Long, Result>` 记忆（`proof/disproof/bestMove`）。
	- 候选生成限制：进攻用 `ThreatAnalyzer` 评分筛选；防守优先必堵点，辅以少量高分着法；节点/时间预算控制。
- 位置：`PNSearcher.*`，在 `ThreatSpaceSearcher.searchForcingWin(...)` 入口先行调用。

## 7) MCTS（PUCT + Progressive Widening）
- 目的：在不确定局面下以仿真估计着法质量，结合先验平衡探索/利用。
- 选择（PUCT）：按 `Q + c_puct * P * sqrt(N_parent)/(1+N)` 选择子节点，`P` 来自 `MoveGenerator` 评分归一化。
- 渐进加宽：随访问数增长，逐步解锁更多子节点，避免初期过宽造成仿真稀疏。
- 回传：沿路径回传胜负/评估，节点累计 `N/W`；必要时以共享 TT 估值作为仿真缺省值。
- 位置：`MCTSSearcher.*`。

## 8) OpeningBook（低优先级）
- 仅在开局且确认无紧急威胁时尝试；以中心附近的稳健模板为主，优先保证安全性。

## 9) 参数与可调项
- 基于代码常量与少量系统属性（如 `g08.tt.capacity`）管理；不依赖外部 JSON/脚本。

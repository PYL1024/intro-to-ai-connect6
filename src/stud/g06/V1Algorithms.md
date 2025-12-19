# V1 阶段关键算法与伪代码

## 数据结构

- 棋盘：`V1Board` 扩展自框架 `Board`，支持候选位置、胜着检测、棋型评估。
- 棋型：`Pattern` 枚举，包含活二、眠三、活三、冲四、活四、连五、连六等。
- 路：`Line` 表示一条方向上的潜在连珠线，为 V2 增量评估预留。
- 威胁：`ThreatEvaluator.Threat` 记录威胁类型、方向、防守点。

## 胜着检测伪代码

```pseudo
function findWinningMove(board, myColor):
    candidates = board.getCandidatePositions()
    winPositions = []
    for pos in candidates:
        if board.isEmpty(pos) and board.checkWinAt(pos, myColor):
            winPositions.append(pos)
            if len(winPositions) >= 2:
                return [winPositions[0], winPositions[1]]
    if len(winPositions) == 1:
        winPos = winPositions[0]
        for pos in candidates:
            if pos != winPos and board.isEmpty(pos):
                return [winPos, pos]
    return findTwoStoneWin(board, candidates, myColor)
```

## 两子组合胜着伪代码

```pseudo
function findTwoStoneWin(board, candidates, myColor):
    fivePositions = []
    for pos in candidates:
        if board.isEmpty(pos) and canFormFive(board, pos, myColor):
            fivePositions.append(pos)
    for pos1 in fivePositions:
        for pos2 in candidates:
            if pos2 != pos1 and board.isEmpty(pos2):
                if board.checkWinWithTwoMoves(pos1, pos2, myColor):
                    return [pos1, pos2]
    return null
```

## 威胁检测伪代码

```pseudo
function detectThreats(board, oppColor):
    threats = []
    bounds = board.getSearchBounds()
    checked[SIZE][SIZE][4] = false
    for row in bounds.rows:
        for col in bounds.cols:
            if board.getColor(row, col) != oppColor:
                continue
            for dir in [0..3]:
                if checked[row][col][dir]:
                    continue
                threat = detectThreatInDirection(row, col, oppColor, dir)
                if threat != null:
                    threats.add(threat)
                    markLineChecked(row, col, dir, checked)
    return threats
```

### 单方向威胁检测

```pseudo
function detectThreatInDirection(row, col, color, dir):
    info = analyzeLine(row, col, color, dir)
    pattern = evaluateThreatPattern(info)
    if pattern.level < SLEEP_THREE.level:
        return null
    threat = Threat(pos=row*SIZE+col, pattern=pattern, direction=dir)
    threat.defensePoints = findDefensePoints(info)
    return threat
```

### 防守点选择策略

```pseudo
function getDefensePositions(board, oppColor):
    threats = detectThreats(board, oppColor)
    sort threats by danger desc
    defense = []
    for t in threats:
        for p in t.defensePoints:
            if board.isEmpty(p) and p not in defense:
                defense.add(p)
                if len(defense) == 2:
                    return defense
    return defense
```

## 着法生成与排序伪代码

```pseudo
function generateMoves(board, myColor, oppColor):
    // 1. 胜着
    winMove = findWinningMove(board, myColor)
    if winMove != null:
        return [winMove]

    // 2. 候选位置评分
    scored = []
    for pos in board.getCandidatePositions():
        if not board.isEmpty(pos):
            continue
        score = positionScore(pos)
              + 2 * patternScore(board, pos, myColor)
              + patternScore(board, pos, oppColor)
        scored.add((pos, score))
    sort scored desc by score
    scored = scored[:MAX_CANDIDATES]

    // 3. 威胁防守检查
    defense = getDefensePositions(board, oppColor)
    if hasCriticalThreat(defense):
        return generateDefensiveMoves(defense, scored)

    // 4. 进攻组合
    return combineTopPairs(scored)
```

## 位置评估（启发式）

- 位置分：距中心越近越高（曼哈顿距离）。
- 进攻分：己方棋型分值 ×2。
- 防守分：对方棋型分值。

## 集成建议

- `stud.g06.AI` 继承 `core.player.AI`，在 `playGame` 初始化 `V1Board` 与 `MoveGenerator`。
- `findNextMove` 流程：更新棋盘 → 胜着检测 → 威胁防守 → 生成着法 → 执行。

## 简单测试用例思路

1. 胜着检测：构造己方已有 5 连，候选点应返回直接成六的落点。
2. 冲四防守：对手有冲四，防守点应覆盖唯一空位。
3. 活三防守：对手活三，威胁数 ≤2 时应优先堵住。
4. 候选集生成：开局应围绕中心生成，非全盘随机。

## 性能与扩展

- 候选集围绕已有棋子半径 2，避免全盘扫描。
- 评估、威胁检测只在有效边界内遍历。
- 为 V2 预留：可将棋型检测改为增量更新；着法列表可直接供 α-β 剪枝排序使用。

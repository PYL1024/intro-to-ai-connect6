package stud.g08;

import core.board.PieceColor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * 轻量蒙特卡洛树搜索（MCTS）
 * - 根节点使用UCT在候选双子着法间选择
 * - 模拟采用启发式/随机混合策略，双子落子
 * - 与现有 MoveGenerator/ThreatAnalyzer/V1Board 协同
 */
public class MCTSSearcher {

    private final V1Board board;
    private final MoveGenerator moveGenerator;
    private final TranspositionTable tt;
    private final Random rnd = new Random(20251220L);

    private final Deque<V1Board.BoardState> stateStack = new ArrayDeque<>();

    /** 根节点候选数量上限 */
    private static final int ROOT_LIMIT = 10;
    /** 模拟最大步数（双子对弈轮数） */
    private static final int MAX_ROUNDS = 12;

    public MCTSSearcher(V1Board board, MoveGenerator moveGenerator, TranspositionTable tt) {
        this.board = board;
        this.moveGenerator = moveGenerator;
        this.tt = tt;
    }

    /** 在时间预算内返回最优双子着法 */
    public int[] search(PieceColor myColor, PieceColor oppColor, long timeBudgetMs) {
        if (!board.isLinesBuilt()) {
            board.buildLines(myColor);
        }
        long start = System.currentTimeMillis();

        List<int[]> candidates = moveGenerator.generateMoves(myColor, oppColor);
        int limit = Math.min(ROOT_LIMIT, candidates.size());
        if (limit == 0) return null;

        double[] wins = new double[limit];
        int[] plays = new int[limit];

        int totalPlays = 0;
        Node[] roots = new Node[limit];
        for (int i = 0; i < limit; i++) {
            roots[i] = new Node(null, candidates.get(i));
        }

        while (System.currentTimeMillis() - start < timeBudgetMs) {
            int idx = selectWithUCT(wins, plays, totalPlays);
            Node root = roots[idx];
            boolean win = runOneIteration(root, myColor, oppColor);
            plays[idx]++;
            if (win) wins[idx] += 1.0;
            totalPlays++;
        }

        int bestIdx = 0;
        double bestScore = -1e9;
        for (int i = 0; i < limit; i++) {
            double avg = plays[i] > 0 ? wins[i] / plays[i] : 0.0;
            if (avg > bestScore) {
                bestScore = avg;
                bestIdx = i;
            }
        }
        return candidates.get(bestIdx);
    }

    private int selectWithUCT(double[] wins, int[] plays, int total) {
        double C = 1.2; // 探索系数
        int best = 0;
        double bestVal = -1e9;
        for (int i = 0; i < plays.length; i++) {
            double v;
            if (plays[i] == 0) {
                v = 1e9; // 强制探索
            } else {
                double avg = wins[i] / plays[i];
                v = avg + C * Math.sqrt(Math.log(Math.max(1, total)) / plays[i]);
            }
            if (v > bestVal) {
                bestVal = v;
                best = i;
            }
        }
        return best;
    }

    /** 一次 MCTS 迭代：选择-扩展-仿真-回传 */
    private boolean runOneIteration(Node root, PieceColor myColor, PieceColor oppColor) {
        // 选择
        List<Node> path = new java.util.ArrayList<>();
        Node node = root;
        PieceColor current = myColor;
        PieceColor opponent = oppColor;
        while (node != null && node.isExpanded()) {
            path.add(node);
            makeMove(node.move, current);
            if (checkWin(node.move, current)) {
                // 终局：立即回传胜负
                backprop(path, true);
                // 回退
                for (int i = path.size()-1; i >= 0; i--) undoMove(path.get(i).move, (i % 2 == 0) ? myColor : oppColor);
                return true;
            }
            // 选择UCT子
            node = node.selectChild();
            // 交换执手
            PieceColor tmp = current; current = opponent; opponent = tmp;
        }

        if (node == null) {
            // 无子可扩展，视为失败回传
            backprop(path, false);
            for (int i = path.size()-1; i >= 0; i--) undoMove(path.get(i).move, (i % 2 == 0) ? myColor : oppColor);
            return false;
        }

        // 扩展当前节点
        path.add(node);
        makeMove(node.move, current);
        if (checkWin(node.move, current)) {
            backprop(path, current == myColor);
            for (int i = path.size()-1; i >= 0; i--) undoMove(path.get(i).move, (i % 2 == 0) ? myColor : oppColor);
            return current == myColor;
        }
        List<int[]> childrenMoves = moveGenerator.generateMoves(opponent, current);
        int cap = Math.min(6, childrenMoves.size());
        node.expand(childrenMoves.subList(0, cap));

        // 仿真：从一个新子开始随机/启发式模拟
        int[] playoutStart = node.children.isEmpty() ? null : node.children.get(rnd.nextInt(node.children.size())).move;
        boolean win = simulatePlayout(playoutStart, opponent, current, myColor);

        backprop(path, win);
        // 回退路径落子
        for (int i = path.size()-1; i >= 0; i--) undoMove(path.get(i).move, (i % 2 == 0) ? myColor : oppColor);
        return win;
    }

    /** playout策略：
     * 1) 如存在致命威胁防守/进攻，优先选取
     * 2) 否则从 MoveGenerator 前若干高分着法中随机挑选
     */
    private int[] pickPlayoutMove(PieceColor current, PieceColor opponent) {
        List<int[]> moves = moveGenerator.generateMoves(current, opponent);
        if (moves.isEmpty()) return null;
        int cap = Math.min(6, moves.size());
        // 简化：随机选前cap中的一个
        int idx = rnd.nextInt(cap);
        return moves.get(idx);
    }

    private boolean simulatePlayout(int[] startMove, PieceColor current, PieceColor opponent, PieceColor myColor) {
        List<int[]> appliedMoves = new ArrayList<>();
        List<PieceColor> appliedColors = new ArrayList<>();
        try {
            if (startMove != null) {
                makeMove(startMove, current);
                appliedMoves.add(startMove);
                appliedColors.add(current);
                if (checkWin(startMove, current)) {
                    return current == myColor;
                }
                // 交换执手
                PieceColor tmp = current; current = opponent; opponent = tmp;
            }
            for (int round = 0; round < MAX_ROUNDS; round++) {
                int[] mv = pickPlayoutMove(current, opponent);
                if (mv == null) break;
                makeMove(mv, current);
                appliedMoves.add(mv);
                appliedColors.add(current);
                if (checkWin(mv, current)) {
                    boolean win = (current == myColor);
                    return win;
                }
                PieceColor tmp = current; current = opponent; opponent = tmp;
            }
            // 终局判定：使用共享TT评估
            long key = tt.computeInitialKey(board);
            TranspositionTable.TTEntry e = tt.probeEval(key, 0);
            int eval;
            if (e != null) {
                eval = e.value;
            } else {
                eval = board.evaluateBoard();
                tt.storeEval(key, eval, 0, null);
            }
            return (myColor == PieceColor.BLACK && eval > 0) || (myColor == PieceColor.WHITE && eval < 0);
        } finally {
            // 回退仿真期间的所有落子
            for (int i = appliedMoves.size() - 1; i >= 0; i--) {
                undoMove(appliedMoves.get(i), appliedColors.get(i));
            }
        }
    }

    private void makeMove(int[] move, PieceColor color) {
        int pos1 = move[0];
        int pos2 = move[1];
        stateStack.push(board.captureStateForSearch());
        board.setColor(pos1, color);
        board.setColor(pos2, color);
        board.updateCandidatesAfterMove(pos1);
        board.updateCandidatesAfterMove(pos2);
        board.updateLinesAfterMove(pos1, color, color);
        board.updateLinesAfterMove(pos2, color, color);
    }

    private void undoMove(int[] move, PieceColor color) {
        int pos1 = move[0];
        int pos2 = move[1];
        board.undoLinesAfterMove(pos1, color, color);
        board.undoLinesAfterMove(pos2, color, color);
        board.clearColor(pos1);
        board.clearColor(pos2);
        V1Board.BoardState st = stateStack.pop();
        board.restoreStateForSearch(st);
    }

    private boolean checkWin(int[] move, PieceColor color) {
        return board.checkWinAt(move[0], color) || board.checkWinAt(move[1], color);
    }

    private static class Node {
        Node parent;
        int[] move;
        List<Node> children = new ArrayList<>();
        int visits = 0;
        int wins = 0;

        Node(Node parent, int[] move) {
            this.parent = parent;
            this.move = move;
        }

        boolean isExpanded() { return !children.isEmpty(); }

        Node selectChild() {
            double C = 1.2;
            Node best = null;
            double bestVal = -1e9;
            for (Node ch : children) {
                double avg = ch.visits > 0 ? ((double) ch.wins / ch.visits) : 0.0;
                double uct = avg + C * Math.sqrt(Math.log(Math.max(1, this.visits)) / Math.max(1, ch.visits));
                if (uct > bestVal) { bestVal = uct; best = ch; }
            }
            return best;
        }

        void expand(List<int[]> moves) {
            for (int[] m : moves) children.add(new Node(this, m));
        }
    }

    private void backprop(List<Node> path, boolean winForMe) {
        for (int i = 0; i < path.size(); i++) {
            Node n = path.get(i);
            n.visits++;
            // 假定路径从我方开始，偶数层为我方
            boolean isMyLayer = (i % 2 == 0);
            if ((winForMe && isMyLayer) || (!winForMe && !isMyLayer)) n.wins++;
        }
    }
}

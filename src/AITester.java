import core.game.Game;
import core.game.GameResult;
import core.game.timer.StopwatchCPU;
import core.game.ui.Configuration;
import core.match.GameEvent;
import core.match.Match;
import core.player.Player;

import java.util.ArrayList;

/**
 * 六子棋AI评测程序
 */
public class AITester {
    public static void main(String[] args) throws CloneNotSupportedException {
        StopwatchCPU timer = new StopwatchCPU();
        zeroCarnival(); // 随机棋手大狂欢 两个棋手对下好多局，会统计分数，函数里可以改局数
        //  oucLeague(); //海之子联赛 调用GUI下一局，可以看到落子情况
//         oneMatch(); //自组织一场比赛（两个棋手先后手各下一局，共下两局棋）
        double elapsedTime = timer.elapsedTime();
        System.out.printf("%.4f", elapsedTime);
    }

    /**
     * 这个用来完成项目二的第二部分内容，随机棋手的测试。
     *
     */
    private static void zeroCarnival() {
        // Zero大狂欢:)
        Configuration.GUI = false; // 不是使用GUI

        //默认生成配置文件中配置的AI棋手列表(根据id生成)
        // GameEvent event = new GameEvent("Carnival of Zeros");

        // 使用自己生成的AI棋手列表
        GameEvent event = new GameEvent("Carnival of Zeros", createPlayers());

        // 每对棋手下500局棋，先后手各250局
        // n个棋手，共下C(n,2)*500局棋，每个棋手下500*(n-1)局棋
        event.carnivalRun(30);
        event.showResults();
    }

    // 生成自己的棋手
    private static ArrayList<Player> createPlayers() {
        ArrayList<Player> players = new ArrayList<>();
        players.add(new stud.g09.AI());
        players.add(new stud.g08.AI());
        //players.add(new stud.g07.AI());
//        players.add(new stud.g88.AI());
//        players.add(new stud.g77.AI());
//        players.add(new stud.g99.AI());
        return players;
    }

    // 海之子联赛
    private static void oucLeague() throws CloneNotSupportedException {
        Configuration.GUI = true; // 使用GUI
        Configuration.STEP_INTER = 300;
        GameEvent event = new GameEvent("海之子排名赛");

        // 主场先手与其他棋手对局
        event.hostGames(Configuration.HOST_ID);

        event.showHostResults(Configuration.HOST_ID);
    }

    // 自组织一场比赛
    private static void oneMatch() {
        Configuration.GUI = true;
        Configuration.STEP_INTER = 300;
        Player one = new stud.g88.AI();
        Player two = new stud.g99.AI();
        Match match = new Match(2, one, two);
        for (Game game : match.getGames()) {
            game.run();
        }

        for (GameResult result : one.gameResults()) {
            System.out.println(result);
        }
    }
}

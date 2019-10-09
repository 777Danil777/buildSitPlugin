package hfib;

import java.io.PrintStream;
import java.util.concurrent.TimeUnit;
import java.util.TimerTask;
import java.util.Timer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;

import io.anuke.arc.*;
import io.anuke.arc.util.*;
import io.anuke.mindustry.*;
import io.anuke.mindustry.content.*;
import io.anuke.mindustry.entities.type.*;
import io.anuke.mindustry.game.*;
import io.anuke.mindustry.game.EventType.*;
import io.anuke.mindustry.core.GameState;
import io.anuke.mindustry.core.GameState.*;
import io.anuke.mindustry.gen.*;
import io.anuke.mindustry.type.*;
import io.anuke.mindustry.world.*;
import io.anuke.mindustry.net.Packets;
import static io.anuke.mindustry.Vars.*;

import io.anuke.mindustry.plugin.Plugin;

public class SurvPlugin extends Plugin {
  public class Difficulty {
    public final String name;
    public final int resources;
    public final int time;
    public final int ping;
    public final int radius;
    public final int wave;

    Difficulty(String name, int resources, int radius, int time, int ping, int wave) {
      this.name = name;
      this.resources = resources;
      this.radius = radius;
      this.time = time * 1000;
      this.ping = ping * 1000;
      this.wave = wave * 60;
    }
  }

  public HashMap<String, Difficulty> Difficulties = new HashMap<String, Difficulty>();

  final class LoopLogic extends TimerTask implements Cloneable {
    private int times = 0;

    @Override
    public void run() {
      times++;
      if(state.is(State.menu)) {
        Log.err(TAG + "Timer working, but server isn't running. Cancelling...");
        cancel();
        return;
      }
      int i1 = difficulty.time - (times * difficulty.ping);
      int i2 = (times * difficulty.ping) - difficulty.time;
      if((i1 < difficulty.ping && i2 > 0)
        || (i2 < difficulty.ping && i2 > 0)) {
        canBuild = false;
        state.wavetime = difficulty.wave;
        Log.info(TAG + "Build now denied!");
        Call.sendMessage("[gray]" + TAG + "[][red]Build now denied!");
        cancel();
        return;
      }
      long now = System.currentTimeMillis();
      String pretty = refTime(System.currentTimeMillis() + difficulty.time - times * difficulty.ping + difficulty.ping - System.currentTimeMillis());
      Call.sendMessage("Left time for building [cyan]" + pretty + "[]");
      Log.warn("Left time for building " + pretty);
    }

    protected final LoopLogic clone() {
      LoopLogic ll;
      try {
        ll = (LoopLogic)super.clone();
      } catch(Exception e) {
        ll = this;
      }
      return ll;
    }
  }

  private static void run(Runnable runnable, int delay) {
    new Thread(() -> {
      try {
        Thread.sleep(delay);
        runnable.run();
      }
      catch (Exception e) {
        Log.err(e.toString());
      }
    }).start();
  }

  public static String refTime(long ms) {
    return String.format("%d:%d", 
      TimeUnit.MILLISECONDS.toMinutes(ms),
      TimeUnit.MILLISECONDS.toSeconds(ms) - 
      TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(ms))
    );
  }

  public Difficulty parse(String str) {
    String prefix = "CUSTOM#";
    if(!str.startsWith(prefix)) {
      try {
        return Difficulties.get(str);
      } catch(Exception e) {
        return Difficulties.get("MEDIUM");
      }
    }
    int[] out = Arrays.stream(str.substring(prefix.length()).split(",")).mapToInt(Integer::parseInt).toArray();
    Difficulty diff;
    try {
      diff = new Difficulty(prefix+out[0]+","+out[1]+","+out[2]+","+out[3]+","+out[4],out[0],out[1],out[2],out[3],out[4]);
    } catch(Exception e) {
      diff = Difficulties.get("MEDIUM");
    }
    return diff;
  }

  private final static String TAG = "[Build&Sit]: ";
  private LoopLogic currentLoop;
  private Difficulty nextDifficulty;
  private Difficulty difficulty;

  private int mix = Core.settings.getInt("smix", 100);
  private boolean mode;
  private boolean canBuild;

  public SurvPlugin() {
    Difficulties.put(   "EASY", new Difficulty(   "EASY", 20000,  120, 360, 30, 120));
    Difficulties.put(   "HARD", new Difficulty(   "HARD",  5000,   60, 120, 30,  30));
    Difficulties.put( "MEDIUM", new Difficulty( "MEDIUM", 10000,   80, 300, 30,  60));
    Difficulties.put("EXTREME", new Difficulty("EXTREME",  1000,   30,  60, 10,  10));

    try {
      System.setOut(new PrintStream(System.out, true, "UTF-8"));
    } catch(Exception e) {}
    nextDifficulty = difficulty = parse(Core.settings.getString("sdifficulty", "medium").toUpperCase());
    Log.info("Owners of \"Build&Sit\" plugin: HFIB#7331 and ХАЛАДОС#8335\n  Beta-testers: SCHIZO#8586");
    Log.info(TAG + "Difficulty: " + difficulty.name);
    Events.on(WaveEvent.class, event -> {
      if(!mode) return;
      if(!canBuild) state.wavetime = difficulty.wave;
    });
    Events.on(GameOverEvent.class, event -> {
      if(!mode) return;
      try {
        currentLoop.cancel();
      } catch(Exception e) {}
    });
    Core.app.post(() -> {
      Events.on(BuildSelectEvent.class, event -> {
        if(!mode) return;
        try {
          Block block = event.builder.buildRequest().block;
          byte rotation = event.tile.rotation();
          if(!event.breaking) {
            if(canBuild) {
              Tile ncore = ((Player)event.builder).getClosestCore().getTile();
              if(Math.sqrt(Math.pow(ncore.x - event.tile.x, 2) + Math.pow(ncore.y - event.tile.y, 2)) > (float)difficulty.radius) {
                Call.beginBreak(event.builder.getTeam(), event.tile.x, event.tile.y);
                Call.onDeconstructFinish(event.tile, block, ((Player)event.builder).id);
                ((Player)event.builder).sendMessage(TAG + "You can build only in radius [cyan]"
                  + difficulty.radius + " blocks[] near any core");
              }
            } else {
              Call.beginBreak(event.builder.getTeam(), event.tile.x, event.tile.y);
              Call.onDeconstructFinish(event.tile, block, ((Player)event.builder).id);
            }
          } else {
            if(!canBuild) {
              Call.onKick(((Player)event.builder).con, Packets.KickReason.kick);
              Call.beginPlace(event.builder.getTeam(), event.tile.x, event.tile.y, block, rotation);
              Call.onConstructFinish(event.tile, block, ((Player)event.builder).id, rotation, event.builder.getTeam());
            }
          }
        } catch(Exception e) { Log.err(e.toString()); }
      });
    });
    Events.on(WorldLoadEvent.class, event -> {
      int random = (int)(Math.random() * 100);
      if(random >= mix) {
        Log.info(TAG + "Default mode.");
        Call.sendMessage(TAG + "Default mode.");
        mode = false;
      } else {
        Log.info(TAG + "Build&Sit mode.");
        Call.sendMessage(TAG + "Build&Sit mode.");
        mode = true;
      }
      if(!mode) return;
      if(!state.teams.get(Team.crux).cores.isEmpty()) {
        Log.warn(TAG + "No PVP maps!");
        Core.app.post(() -> { Events.fire(new GameOverEvent(Team.crux)); });
        return;
      }
      difficulty = nextDifficulty;
      canBuild = true;
      currentLoop = new LoopLogic().clone();
      Timer timer1 = new Timer();
      timer1.scheduleAtFixedRate(currentLoop, 1000, difficulty.ping);
      int i = 0;
      for(Item item : content.items()) {
        if(item.type == ItemType.material) {
          int add = Math.round(difficulty.resources / ++i);
          Core.app.post(() -> {
            state.teams.get(Team.sharded).cores.first().entity.items.set(item, add);
          });
        }
      }
    });
    Events.on(PlayerJoin.class, event -> {
      String msg;
      if(mode) {
        msg = "Build&Sit mode [green]enabled[]\n" +
              "Using difficulty: " + difficulty.name +
              "\nTotal resources: ~" + difficulty.resources +
              "\nAllowed radius for building: ~" + difficulty.radius +
              "[]blocks\nTime for building: [cyan]" + difficulty.time / 1000 +
              "s\nWaves every: [cyan]" + difficulty.wave / 60;
      }
      else {
        msg = "Build&Sit mode [red]disabled";
      }
      Call.onInfoMessage(event.player.con, msg);
    });
  }

  public void registerServerCommands(CommandHandler handler){
    handler.register("sdifficulty", "[type]", "Set difficulty to Build&Sit plugin.", args -> {
      if(args.length == 0) {
        Log.info(TAG + "Using difficulty {0}\n  Total resources: ~{1}\n  Allowed radius for building: ~{2}blocks\n"
          + "  Time for building: {3}s\n  Pings about time every: {4}s\n  Waves every: {5}s",
          difficulty.name, difficulty.resources, difficulty.radius,
          difficulty.time / 1000, difficulty.ping / 1000, difficulty.wave / 60
        );
        return;
      }
      Difficulty diff = parse(args[0].toUpperCase());
      
      if(state.is(State.menu)) {
        nextDifficulty = diff;
        difficulty = diff;
        Log.info(TAG + "Difficulty " + diff.name + " applied");
      }
      else {
        nextDifficulty = diff;
        Log.info(TAG + "Difficulty " + diff.name + " will apply after game over");
      }
      Core.settings.put("sdifficulty", diff.name);
      Core.settings.save();
    });
    handler.register("smode", "[type]", "Set mode for Build&Sit plugin", args -> {
      if(args.length == 0) {
        Log.info(TAG + "Plugin " + (mix > 0 ? "enabled" : "disabled"));
        return;
      }
      if(args[0].equals("on")) {
        mode = true;
        Core.settings.put("smix", 100);
        Log.info(TAG + "Plugin enabled");
      } else if(args[0].equals("off")) {
        try {
          currentLoop.cancel();
        } catch(Exception e) {}
        canBuild = true;
        mode = false;
        Core.settings.put("smix", 0);
        Log.info(TAG + "Plugin disabled");
      }
    });
    handler.register("smix", "[value]", "Set chance of enabling this module", args -> {
      if(args.length == 0) {
        Log.info(TAG + "Chance of build&sit survival mode now: " + mix + "%");
        return;
      }
      try {
        mix = Integer.parseInt(args[0]);
        Core.settings.put("smix", mix);
        Core.settings.save();
        Log.info(TAG + "Chance of Build&Sit survival mode now: " + mix + "%");
      } catch(NumberFormatException e) {
        Log.err(TAG + "Invalid number");
      }
    });
  }
}

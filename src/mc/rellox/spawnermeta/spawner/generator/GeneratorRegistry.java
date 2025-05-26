package mc.rellox.spawnermeta.spawner.generator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.scheduler.BukkitRunnable;

import mc.rellox.spawnermeta.SpawnerMeta;
import mc.rellox.spawnermeta.api.spawner.IGenerator;
import mc.rellox.spawnermeta.configuration.Settings;
import mc.rellox.spawnermeta.utility.reflect.Reflect.RF;

public final class GeneratorRegistry implements Listener {

    private static final Map<World, SpawnerWorld> SPAWNERS = new HashMap<>();

    private static BukkitRunnable active, offline_task;

    public static void initialize() {
        Bukkit.getPluginManager().registerEvents(new GeneratorRegistry(), SpawnerMeta.instance());
        load();
        retime(true);
    }

    public static void retime(boolean first) {
        if (active != null) active.cancel();
        active = runnable();
        active.runTaskTimer(SpawnerMeta.instance(), first ? 20 : 5, Settings.settings.ticking_interval);
        offline();
    }

    private static BukkitRunnable runnable() {
        return new BukkitRunnable() {
            int t = 0;
            final int f = Math.max(100, Settings.settings.check_present_interval / Settings.settings.ticking_interval);

            @Override
            public void run() {
                for (SpawnerWorld spawnerWorld : SPAWNERS.values()) {
                    spawnerWorld.tick();
                }
                if (++t > f) {
                    t = 0;
                    for (SpawnerWorld spawnerWorld : SPAWNERS.values()) {
                        spawnerWorld.reduce();
                    }
                }
            }
        };
    }

    private static void offline() {
        if (offline_task != null && !offline_task.isCancelled()) offline_task.cancel();
        if (Settings.settings.owned_offline_time <= 0) return;
        offline_task = new BukkitRunnable() {
            @Override
            public void run() {
                for (SpawnerWorld spawnerWorld : SPAWNERS.values()) {
                    spawnerWorld.control();
                }
            }
        };
        offline_task.runTaskTimer(SpawnerMeta.instance(), 20 * 60, 20 * 60);
    }

    private static void control() {
        try {
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (SpawnerWorld spawnerWorld : SPAWNERS.values()) {
                        spawnerWorld.control();
                    }
                }
            }.runTaskLater(SpawnerMeta.instance(), 5);
        } catch (Exception e) {}
    }

    public static void load() {
        try {
            for (World world : Bukkit.getWorlds()) {
                SpawnerWorld spawnerWorld = get(world);
                if (spawnerWorld != null) {
                    spawnerWorld.load();
                }
            }
        } catch (Exception e) {
            RF.debug(e);
        }
    }

    public static void reload() {
        try {
            clear();
            load();
        } catch (Exception e) {
            RF.debug(e);
        }
    }

    public static int active(World world) {
        if (world == null) {
            int total = 0;
            for (SpawnerWorld spawnerWorld : SPAWNERS.values()) {
                total += spawnerWorld.active();
            }
            return total;
        }
        if (Settings.inactive(world)) return 0;
        return get(world).active();
    }

    private static SpawnerWorld get(World world) {
        if (Settings.inactive(world)) return null;
        SpawnerWorld sw = SPAWNERS.get(world);
        if (sw == null) SPAWNERS.put(world, sw = new SpawnerWorld(world));
        return sw;
    }

    public static void put(Block block) {
        if (Settings.inactive(block.getWorld())) return;
        get(block.getWorld()).put(block);
    }

    public static IGenerator get(Block block) {
        if (Settings.inactive(block.getWorld())) return null;
        return get(block.getWorld()).get(block);
    }

    public static IGenerator raw(Block block) {
        if (Settings.inactive(block.getWorld())) return null;
        return get(block.getWorld()).raw(block);
    }

    public static List<IGenerator> list(World world) {
        if (world != null) {
            SpawnerWorld sw = get(world);
            return sw == null ? new ArrayList<>() : new ArrayList<>(sw.spawners.values());
        }
        List<IGenerator> generators = new ArrayList<>();
        for (SpawnerWorld spawnerWorld : SPAWNERS.values()) {
            generators.addAll(spawnerWorld.spawners.values());
        }
        return generators;
    }

    public static void update(Block block) {
        World world = block.getWorld();
        if (Settings.inactive(world)) return;

        IGenerator generator = get(world).get(block);
        if (generator != null) {
            generator.update();
            generator.valid();
            generator.rewrite();
        }
        SpawningManager.unlink(block);
    }

    public static void update() {
        for (SpawnerWorld spawnerWorld : SPAWNERS.values()) {
            spawnerWorld.update();
        }
    }

    public static void remove(Block block) {
        if (Settings.inactive(block.getWorld())) return;
        IGenerator generator = get(block.getWorld()).raw(block);
        if (generator != null) generator.remove(false);
    }

    public static void delete(Block block) {
        if (Settings.inactive(block.getWorld())) return;
        IGenerator generator = get(block.getWorld()).raw(block);
        if (generator != null) generator.remove(true);
        else block.setType(Material.AIR);
    }

    public static int remove(World world, boolean fully, Predicate<IGenerator> filter) {
        if (world != null) {
            if (Settings.inactive(world)) return 0;
            return get(world).remove(fully, filter);
        }
        int totalRemoved = 0;
        for (SpawnerWorld spawnerWorld : SPAWNERS.values()) {
            totalRemoved += spawnerWorld.remove(fully, filter);
        }
        return totalRemoved;
    }

    public static void clear() {
        for (SpawnerWorld spawnerWorld : SPAWNERS.values()) {
            spawnerWorld.clear();
        }
        SPAWNERS.clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onWorldLoad(WorldLoadEvent event) {
        try {
            World world = event.getWorld();
            if (Settings.inactive(world)) return;

            SpawnerWorld sw = new SpawnerWorld(world);
            SPAWNERS.put(world, sw);
            sw.load();
        } catch (Exception e) {
            RF.debug(e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onWorldUnload(WorldUnloadEvent event) {
        try {
            World world = event.getWorld();
            if (Settings.inactive(world)) return;

            SPAWNERS.remove(world);
        } catch (Exception e) {
            RF.debug(e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void onChunkLoad(ChunkLoadEvent event) {
        try {
            World world = event.getWorld();
            if (Settings.inactive(world)) return;

            get(world).load(event.getChunk());
        } catch (Exception e) {
            RF.debug(e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void onChunkUnload(ChunkUnloadEvent event) {
        try {
            World world = event.getWorld();
            if (Settings.inactive(world)) return;

            get(world).unload(event.getChunk());
        } catch (Exception e) {
            String m = e.getMessage();
            if (m != null && m.contains("Chunk not there when requested")) return;
            RF.debug(e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void onJoin(PlayerJoinEvent event) {
        control();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void onQuit(PlayerQuitEvent event) {
        control();
    }
}
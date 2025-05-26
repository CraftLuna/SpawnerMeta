package mc.rellox.spawnermeta.spawner.generator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;

import mc.rellox.spawnermeta.api.spawner.IGenerator;
import mc.rellox.spawnermeta.api.spawner.ISpawner;
import mc.rellox.spawnermeta.api.spawner.location.Pos;
import mc.rellox.spawnermeta.configuration.Settings;
import mc.rellox.spawnermeta.spawner.ActiveGenerator;

public class SpawnerWorld {

    public final World world;
    protected final Map<Pos, IGenerator> spawners;
    private final List<IGenerator> queue;

    public SpawnerWorld(World world) {
        this.world = world;
        this.spawners = new HashMap<>();
        this.queue = new LinkedList<>();
    }

    public void load() {
        for (Chunk chunk : world.getLoadedChunks()) {
            load(chunk);
        }
    }

    public void load(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof CreatureSpawner) {
                Block block = state.getBlock();
                if (!Settings.settings.ignored(block)) {
                    queue.add(new ActiveGenerator(ISpawner.of(block)));
                }
            }
        }
    }

    public void unload(Chunk chunk) {
        List<IGenerator> toRemove = new ArrayList<>();
        for (IGenerator generator : spawners.values()) {
            if (generator.in(chunk)) {
                generator.remove(false);
                toRemove.add(generator);
            }
        }
        toRemove.forEach(g -> spawners.remove(g.position()));
    }

    public void clear() {
        for (IGenerator generator : spawners.values()) {
            generator.clear();
        }
        spawners.clear();
    }

    public int active() {
        return spawners.size();
    }

    public void update() {
        for (IGenerator generator : spawners.values()) {
            generator.update();
        }
    }

    public void control() {
        for (IGenerator generator : spawners.values()) {
            generator.control();
        }
    }

    public void tick() {
        if (!queue.isEmpty()) {
            for (IGenerator generator : queue) {
                put(generator);
            }
            queue.clear();
        }
        for (IGenerator generator : spawners.values()) {
            generator.tick();
        }
    }

    public void reduce() {
        List<Pos> remove = new ArrayList<>();
        for (Map.Entry<Pos, IGenerator> entry : spawners.entrySet()) {
            IGenerator generator = entry.getValue();
            if (!generator.active() || !generator.present()) {
                generator.clear();
                remove.add(entry.getKey());
            }
        }
        for (Pos pos : remove) {
            spawners.remove(pos);
        }
    }

    public int remove(boolean fully, Predicate<IGenerator> filter) {
        List<Pos> remove = new ArrayList<>();
        for (Map.Entry<Pos, IGenerator> entry : spawners.entrySet()) {
            IGenerator generator = entry.getValue();
            if (generator.active() && filter.test(generator)) {
                generator.remove(fully);
                remove.add(entry.getKey());
            }
        }
        for (Pos pos : remove) {
            spawners.remove(pos);
        }
        return remove.size();
    }

    public void put(Block block) {
        put(new ActiveGenerator(ISpawner.of(block)));
    }

    private void put(IGenerator generator) {
        IGenerator last = spawners.put(generator.position(), generator);
        if (last != null) last.clear();
    }

    public IGenerator get(Block block) {
        IGenerator generator = spawners.get(Pos.of(block));
        if (generator == null) {
            if (block.getType() == Material.SPAWNER) put(block);
        } else if (!generator.active()) return null;
        return generator;
    }

    public IGenerator raw(Block block) {
        return spawners.get(Pos.of(block));
    }
}
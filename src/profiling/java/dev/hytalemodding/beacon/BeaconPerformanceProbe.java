package dev.hytalemodding.beacon;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.sun.management.ThreadMXBean;
import jdk.jfr.*;
import org.joml.Vector3d;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Mide fases reales con actores sin red; se compila solo para profiling y no simula jugadores conectados. */
final class BeaconPerformanceProbe {
    private static final int SAMPLES = 512;
    private static final int[] BASE_COUNTS = {0, 1, 8, 8};
    // El escenario separado no comparte radios; todos los escenarios cargan la misma región.
    private static final int SEPARATED_SPACING = 2 * BeaconPower.radius(4) + 32;
    private static final int LOADED_X = (12 + 3 * SEPARATED_SPACING) / 32 + 1;
    private static final int LOADED_Z = (12 + SEPARATED_SPACING) / 32 + 1;
    private final World world;
    private final Store<EntityStore> store;
    private final BeaconStatusSystem status;
    private final int systemIndex;
    private final Consumer<Throwable> done;
    private final ThreadMXBean memory = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private final List<BeaconStructures.Position> bases = new ArrayList<>();
    private final List<Ref<EntityStore>> actors = new ArrayList<>();
    private final List<String> rows = new ArrayList<>(List.of(
            "scenario,sections,beacons,actors,phase,samples,p50_us,p95_us,p99_us,mean_bytes,p95_bytes"));
    private final Recording recording;
    private Map<BeaconStructures.Position, Integer> discovered;
    private int scenario;
    private int sectionCount;

    private BeaconPerformanceProbe(World world, BeaconStatusSystem status, int systemIndex, Consumer<Throwable> done) throws Exception {
        this.world = world;
        this.store = world.getEntityStore().getStore();
        this.status = status;
        this.systemIndex = systemIndex;
        this.done = done;
        if (!memory.isThreadAllocatedMemorySupported()) throw new IllegalStateException("Thread allocation counters unavailable");
        memory.setThreadAllocatedMemoryEnabled(true);
        recording = new Recording(Configuration.getConfiguration("profile"));
        recording.setName("BeaconWorkload");
        recording.setDestination(Path.of("workload.jfr"));
        recording.setDumpOnExit(true);
        recording.setMaxSize(128L * 1024 * 1024);
        recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(2));
        recording.enable(Phase.class).withoutStackTrace();
        recording.start();
    }

    static void stopStartupRecording() {
        for (Recording recording : FlightRecorder.getFlightRecorder().getRecordings()) {
            if (recording.getName().equals("BeaconStartup")) recording.stop();
        }
    }

    static void run(World world, BeaconStatusSystem status, int index, Consumer<Throwable> done) {
        List<CompletableFuture<?>> sections = new ArrayList<>();
        for (int x = 0; x < LOADED_X; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < LOADED_Z; z++) {
            sections.add(world.getChunkStore().getChunkSectionReferenceAsync(x, y, z));
        }
        CompletableFuture.allOf(sections.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> {
            if (error != null) { done.accept(error); return; }
            world.execute(() -> {
                try {
                    var probe = new BeaconPerformanceProbe(world, status, index, done);
                    probe.guard(probe::prepare);
                } catch (Throwable failure) { done.accept(failure); }
            });
        });
    }

    private String scenarioName() {
        return "b" + BASE_COUNTS[scenario / 2] + (scenario / 2 == 3 ? "_separate_p" : "_overlap_p") + (scenario % 2 == 0 ? 8 : 16);
    }

    private void prepare() {
        for (var actor : actors) if (actor.isValid()) store.removeEntity(actor, RemoveReason.REMOVE);
        actors.clear();
        put(31, 32, 31, "Empty");
        for (var base : bases) put(base.x(), base.y(), base.z(), "Empty");
        bases.clear();
        int beaconCount = BASE_COUNTS[scenario / 2];
        boolean separate = scenario / 2 == 3;
        for (int x = 0; x < LOADED_X; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < LOADED_Z; z++) {
            var section = world.getChunkStore().getChunkSectionReferenceAtBlock(x * 32, y * 32, z * 32);
            world.getChunkStore().getStore().tryRemoveComponent(section, ChunkStore.REGISTRY.getNonTickingComponentType());
        }
        for (int i = 0; i < beaconCount; i++) {
            int spacing = separate ? SEPARATED_SPACING : 12;
            int x = 8 + (i % 4) * spacing, z = 8 + (i / 4) * spacing;
            var position = new BeaconStructures.Position(x, 32, z);
            for (int depth = 1; depth <= 4; depth++) for (int dx = -depth; dx <= depth; dx++) for (int dz = -depth; dz <= depth; dz++) {
                put(x + dx, 32 - depth, z + dz, "Iron_Block");
            }
            put(x, 32, z, "Beacon_Block");
            var block = BeaconSelection.blockEntity(world.getChunkStore(), position, true);
            if (block == null) throw new AssertionError("Profile base entity missing");
            BeaconSelection.save(block, BeaconPower.values()[i % 5], i % 2 == 0 ? "UPGRADE" : "REGENERATION");
            bases.add(position);
        }
        spawnActors(beaconCount, separate);
        int[] sections = {0};
        world.getChunkStore().getStore().forEachChunk(Query.and(ChunkSection.getComponentType(), BlockSection.getComponentType()),
                (chunk, buffer) -> { sections[0] += chunk.size(); });
        if (scenario == 0) sectionCount = sections[0];
        if (sectionCount < 2 * LOADED_X * LOADED_Z || sections[0] != sectionCount || BeaconStructures.findActive(world.getChunkStore()).size() != beaconCount) {
            throw new AssertionError("Profile S/B differs from requested workload: " + sections[0] + "/" + beaconCount);
        }
        status.delayedTick(1f, systemIndex, store);
        measure("control", () -> {});
        measure("discovery", () -> discovered = BeaconStructures.findActive(world.getChunkStore()));
        if (discovered.size() != beaconCount) throw new AssertionError("Discovery result changed while profiling");
        measure("statusNoPlayers", () -> status.delayedTick(1f, systemIndex, store));
        measure("visualPolling", () -> status.delayedTick(0.05f, systemIndex, store));
        for (var base : bases) {
            put(base.x(), base.y() + 8, base.z(), "Rock_Crystal_Blue_Small");
            put(base.x(), base.y() + 14, base.z(), "Rock_Crystal_Red_Small");
        }
        status.delayedTick(1f, systemIndex, store);
        measure("visualColoredPolling", () -> status.delayedTick(0.05f, systemIndex, store));
        for (var base : bases) {
            put(base.x(), base.y() + 8, base.z(), "Empty");
            put(base.x(), base.y() + 14, base.z(), "Empty");
        }
        status.delayedTick(1f, systemIndex, store);
        measure("refreshActors", this::refreshActors);
        // El tick que prepara y calienta la sonda no pertenece a la ventana de carga a 1 Hz.
        later(() -> {
            world.clearMetrics();
            later(() -> steady(0));
        });
    }

    private void spawnActors(int beaconCount, boolean separate) {
        for (int i = 0; i < (scenario % 2 == 0 ? 8 : 16); i++) {
            Vector3d position = separate ? new Vector3d(bases.get(i % beaconCount).x() + .5, 33, bases.get(i % beaconCount).z() + .5)
                    : new Vector3d(beaconCount == 1 ? 8.5 : 26.5, 33, beaconCount == 1 ? 8.5 : 14.5);
            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
            holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, new Rotation3f()));
            holder.addComponent(EffectControllerComponent.getComponentType(), new EffectControllerComponent());
            holder.addComponent(UUIDComponent.getComponentType(), new UUIDComponent(UUID.randomUUID()));
            holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
            var actor = store.addEntity(holder, AddReason.SPAWN);
            if (actor == null) throw new AssertionError("Profile actor missing");
            actors.add(actor);
        }
    }

    private void refreshActors() {
        for (var actor : actors) {
            status.refreshPowers(actor, store.getComponent(actor, TransformComponent.getComponentType()).getPosition(), store, store);
        }
    }

    private void measure(String name, Runnable action) {
        long warmupEnd = System.nanoTime() + 250_000_000L;
        do { action.run(); } while (System.nanoTime() < warmupEnd);
        long[] nanos = new long[SAMPLES], bytes = new long[SAMPLES];
        var event = new Phase();
        event.scenario = scenarioName();
        event.phase = name;
        event.begin();
        long thread = Thread.currentThread().threadId();
        for (int i = 0; i < SAMPLES; i++) {
            long allocated = memory.getThreadAllocatedBytes(thread);
            long before = System.nanoTime();
            action.run();
            nanos[i] = System.nanoTime() - before;
            bytes[i] = memory.getThreadAllocatedBytes(thread) - allocated;
            if (bytes[i] < 0) throw new AssertionError("Allocation counter unavailable");
        }
        event.end();
        event.commit();
        addRow(name, nanos, bytes);
    }

    private void steady(int round) {
        refreshActors();
        if (round < 4) { later(() -> steady(round + 1)); return; }
        long[] ticks = world.getBufferedTickLengthMetricSet().getValues(0);
        if (ticks.length == 0) throw new AssertionError("No world tick samples");
        addRow("tickSynthetic", ticks, null);
        System.out.println("Beacon profile completed: " + scenarioName());
        if (++scenario < BASE_COUNTS.length * 2) { later(this::prepare); return; }
        guard(() -> {
            Files.write(Path.of("profile-results.csv"), rows);
            recording.stop();
            recording.close();
            done.accept(null);
        });
    }

    private void addRow(String phase, long[] nanos, long[] bytes) {
        Arrays.sort(nanos);
        if (bytes != null) Arrays.sort(bytes);
        rows.add(String.format(Locale.ROOT, "%s,%d,%d,%d,%s,%d,%.3f,%.3f,%.3f,%.1f,%.0f", scenarioName(), sectionCount, bases.size(), actors.size(), phase,
                nanos.length, percentile(nanos, .5) / 1000., percentile(nanos, .95) / 1000., percentile(nanos, .99) / 1000.,
                bytes == null ? -1. : Arrays.stream(bytes).average().orElseThrow(), bytes == null ? -1. : (double) percentile(bytes, .95)));
    }

    private static long percentile(long[] sorted, double fraction) {
        return sorted[(int) Math.ceil(sorted.length * fraction) - 1];
    }

    private void put(int x, int y, int z, String id) {
        ChunkStore chunks = world.getChunkStore();
        int index = id.equals("Empty") ? 0 : BlockType.getAssetMap().getIndex(id);
        var section = chunks.getChunkSectionReferenceAtBlock(x, y, z);
        var blocks = chunks.getStore().getComponent(section, BlockSection.getComponentType());
        if (index >= 0 && blocks != null && blocks.get(x, y, z) == index) return;
        if (index < 0 || !BlockOperations.setBlock(chunks, section, x, y, z, index,
                BlockType.getAssetMap().getAsset(index), 0, 0, 0)) throw new AssertionError("Profile block placement failed: " + id);
    }

    private void later(Runnable action) {
        CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() -> {
            try { world.execute(() -> guard(action::run)); } catch (Throwable error) { done.accept(error); }
        });
    }

    private void guard(CheckedAction action) {
        try { action.run(); } catch (Throwable error) { recording.close(); done.accept(error); }
    }

    @FunctionalInterface
    private interface CheckedAction { void run() throws Exception; }

    @Name("beacon.ProfilePhase")
    @Label("Beacon controlled measurement")
    static final class Phase extends Event {
        String scenario;
        String phase;
    }
}

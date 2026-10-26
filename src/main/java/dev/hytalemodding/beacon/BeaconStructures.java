package dev.hytalemodding.beacon;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntLists;

import java.util.HashMap;
import java.util.Map;

/** Descubre y valida estructuras usando solo las secciones del mundo que ya están cargadas. */
final class BeaconStructures {
    private BeaconStructures() {}

    /** Coordenadas globales del bloque del Beacon, sin desplazamiento hacia su centro. */
    record Position(int x, int y, int z) {}

    /** Encuentra los Beacons con base válida, incluso cuando la base cruza límites de secciones. */
    static Map<Position, Integer> findActive(ChunkStore chunks) {
        Map<Position, Integer> active = new HashMap<>();
        int beaconId = BlockType.getAssetMap().getIndex("Beacon_Block");
        if (beaconId < 0) {
            return active;
        }
        var beaconIds = IntLists.singleton(beaconId);
        IntArrayList positions = new IntArrayList();
        chunks.getStore().forEachChunk(Query.and(ChunkSection.getComponentType(), BlockSection.getComponentType()), (sections, buffer) -> {
            for (int i = 0; i < sections.size(); i++) {
                BlockSection blocks = sections.getComponent(i, BlockSection.getComponentType());
                ChunkSection section = sections.getComponent(i, ChunkSection.getComponentType());
                if (blocks == null || section == null || !blocks.contains(beaconId)) {
                    continue;
                }
                positions.clear();
                blocks.find(beaconIds, (int blockIndex) -> positions.add(blockIndex));
                for (int blockIndex : positions) {
                    Position position = new Position(
                            ChunkUtil.minBlock(section.getX()) + ChunkUtil.xFromIndex(blockIndex),
                            ChunkUtil.minBlock(section.getY()) + ChunkUtil.yFromIndex(blockIndex),
                            ChunkUtil.minBlock(section.getZ()) + ChunkUtil.zFromIndex(blockIndex));
                    int tier = level(chunks, position);
                    if (tier > 0) active.put(position, tier);
                }
            }
        });
        return active;
    }

    /** Valida hasta cuatro capas; los bloques de secciones descargadas no cuentan. */
    static int level(ChunkStore chunks, Position position) {
        int beaconId = BlockType.getAssetMap().getIndex("Beacon_Block");
        int ironId = BlockType.getAssetMap().getIndex("Iron_Block");
        if (beaconId < 0 || ironId < 0 || blockAt(chunks, position.x(), position.y(), position.z()) != beaconId) return 0;
        return BeaconRules.pyramidLevel((dx, depth, dz) -> blockAt(chunks, position.x() + dx, position.y() - depth, position.z() + dz) == ironId);
    }

    /** Lee una sección ya cargada; devuelve -1 si no está disponible. */
    private static int blockAt(ChunkStore chunks, int x, int y, int z) {
        Ref<ChunkStore> sectionRef = chunks.getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return -1;
        }
        BlockSection section = chunks.getStore().getComponent(sectionRef, BlockSection.getComponentType());
        return section == null ? -1 : section.get(x, y, z);
    }
}

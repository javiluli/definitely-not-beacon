package dev.hytalemodding.beacon;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

/**
 * Selección pagada, guardada por Hytale en el propio bloque y eliminada al romperlo.
 * Al leer, el codec conserva cadenas desconocidas y nulos explícitos; los campos ausentes usan defaults.
 */
public final class BeaconSelection implements Component<ChunkStore> {
    public static final BuilderCodec<BeaconSelection> CODEC = BuilderCodec.builder(BeaconSelection.class, BeaconSelection::new)
            .append(new KeyedCodec<>("Primary", Codec.STRING), (s, v) -> s.primary = v, s -> s.primary).add()
            .append(new KeyedCodec<>("Secondary", Codec.STRING), (s, v) -> s.secondary = v, s -> s.secondary).add()
            .build();
    private static ComponentType<ChunkStore, BeaconSelection> type;
    String primary = "";
    String secondary = BeaconRules.SECONDARY_NONE;

    /** Registra el componente antes de cargar los mundos. */
    public static void register(com.hypixel.hytale.server.core.plugin.JavaPlugin plugin) {
        type = plugin.getChunkStoreRegistry().registerComponent(BeaconSelection.class, "BeaconSelection", CODEC);
    }

    /**
     * Obtiene la entidad del bloque en una sección cargada; no comprueba el tipo de bloque.
     * @param chunks almacén utilizado desde el hilo de su mundo
     * @param position coordenadas globales del bloque
     * @param create crea la entidad con una selección vacía si no existe una referencia válida
     * @return referencia válida, o {@code null} si la sección o sus componentes no están disponibles
     */
    static Ref<ChunkStore> blockEntity(ChunkStore chunks, BeaconStructures.Position position, boolean create) {
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(position.x(), position.y(), position.z());
        if (section == null || !section.isValid()) return null;
        Store<ChunkStore> store = chunks.getStore();
        BlockComponentSection blocks = store.getComponent(section, BlockComponentSection.getComponentType());
        if (blocks == null) return null;
        int index = ChunkUtil.indexBlock(position.x(), position.y(), position.z());
        Ref<ChunkStore> block = blocks.getBlockReference(index);
        if ((block == null || !block.isValid()) && create) {
            Holder<ChunkStore> holder = ChunkStore.REGISTRY.newHolder();
            holder.putComponent(BlockModule.BlockStateInfo.getComponentType(), new BlockModule.BlockStateInfo(index, section));
            holder.putComponent(type, new BeaconSelection());
            block = store.addEntity(holder, AddReason.SPAWN);
        }
        return block != null && block.isValid() ? block : null;
    }

    /**
     * Consulta la elección del bloque sin crear una entidad ni cargar secciones.
     * @param chunks almacén utilizado desde el hilo de su mundo
     * @param position coordenadas globales del bloque
     * @return elección persistida, o {@code null} si el bloque no tiene este componente disponible
     */
    static BeaconSelection get(ChunkStore chunks, BeaconStructures.Position position) {
        Ref<ChunkStore> block = blockEntity(chunks, position, false);
        return block == null ? null : chunks.getStore().getComponent(block, type);
    }

    /**
     * Sustituye la elección y marca su bloque para guardar; no confirma escritura síncrona en disco.
     * @param block entidad válida del Beacon, utilizada desde el hilo del mundo
     * @param primary principal ya validado para el cobro
     * @param secondary opción ya validada para el cobro
     */
    static void save(Ref<ChunkStore> block, BeaconPower primary, String secondary) {
        BeaconSelection selection = new BeaconSelection();
        selection.primary = primary.name();
        selection.secondary = secondary;
        block.getStore().putComponent(block, type, selection);
        BlockModule.BlockStateInfo info = block.getStore().getComponent(block, BlockModule.BlockStateInfo.getComponentType());
        if (info != null) info.markNeedsSaving();
    }

    int strength(BeaconPower power, int tier) {
        return BeaconRules.strength(tier, primary, secondary, power);
    }

    /**
     * Compara las cadenas exactas para evitar otro cobro, sin validar nivel ni normalizar datos.
     * @throws NullPointerException si coincide el principal y el secundario persistido es nulo
     */
    boolean matches(BeaconPower primary, String secondary) {
        return primary != null && primary.name().equals(this.primary) && this.secondary.equals(secondary);
    }

    @Nonnull
    @Override
    public BeaconSelection clone() {
        BeaconSelection copy = new BeaconSelection();
        copy.primary = primary;
        copy.secondary = secondary;
        return copy;
    }
}

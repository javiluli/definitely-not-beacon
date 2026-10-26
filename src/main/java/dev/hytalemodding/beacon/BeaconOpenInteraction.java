package dev.hytalemodding.beacon;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/** Abre una sola página con poderes, inventario y depósito real del Beacon. */
public final class BeaconOpenInteraction extends SimpleInstantInteraction {
    public static final BuilderCodec<BeaconOpenInteraction> CODEC = BuilderCodec.builder(
            BeaconOpenInteraction.class, BeaconOpenInteraction::new, SimpleInstantInteraction.CODEC).build();

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        var buffer = context.getCommandBuffer();
        var target = context.getTargetBlock();
        if (buffer == null || target == null) return;
        var ref = context.getEntity();
        Player player = buffer.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = buffer.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null || player.getPageManager().getCustomPage() != null) return;
        var chunks = buffer.getExternalData().getWorld().getChunkStore();
        var position = new BeaconStructures.Position(target.x, target.y, target.z);
        var block = BeaconSelection.blockEntity(chunks, position, true);
        if (block == null) return;
        var store = buffer.getStore();
        if (!validTarget(ref, store, position, block)) return;
        BeaconPaymentWindow deposit = new BeaconPaymentWindow();
        player.getPageManager().openCustomPageWithWindows(ref, store,
                new BeaconPage(playerRef, position, block, deposit), deposit);
    }

    static boolean validTarget(Ref<EntityStore> ref, Store<EntityStore> store, BeaconStructures.Position position, Ref<ChunkStore> block) {
        if (!ref.isValid() || !block.isValid()) return false;
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        return transform != null && transform.getPosition().distanceSquared(position.x() + .5, position.y() + .5, position.z() + .5) <= 64
                && block.equals(BeaconSelection.blockEntity(store.getExternalData().getWorld().getChunkStore(), position, false));
    }
}

package net.void_.lightentity.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;
import net.void_.lightentity.api.ILightEntity;
import net.void_.lightentity.api.event.LightEntityEvent;
import net.void_.lightentity.storage.WorldLightManager;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sent from client to server when a player clicks (interacts or attacks) a LightEntity hitbox.
 */
public class ServerboundInteractLightEntityPacket {

    public enum Action {
        INTERACT,
        ATTACK
    }

    private final UUID entityId;
    private final Action action;
    private final InteractionHand hand;
    private final Vec3 hitPos;

    public ServerboundInteractLightEntityPacket(UUID entityId, Action action, InteractionHand hand, Vec3 hitPos) {
        this.entityId = entityId;
        this.action = action;
        this.hand = hand;
        this.hitPos = hitPos;
    }

    public static void encode(ServerboundInteractLightEntityPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.entityId);
        buf.writeEnum(msg.action);
        buf.writeEnum(msg.hand);
        buf.writeDouble(msg.hitPos.x);
        buf.writeDouble(msg.hitPos.y);
        buf.writeDouble(msg.hitPos.z);
    }

    public static ServerboundInteractLightEntityPacket decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        Action action = buf.readEnum(Action.class);
        InteractionHand hand = buf.readEnum(InteractionHand.class);
        Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new ServerboundInteractLightEntityPacket(id, action, hand, pos);
    }

    public static void handle(ServerboundInteractLightEntityPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            WorldLightManager mgr = WorldLightManager.get(player.serverLevel());
            ILightEntity entity = mgr.getSpatialGrid().getById(msg.entityId);
            if (entity == null || entity.isDiscarded()) return;

            // Distance security check: player cannot interact beyond reach distance + margin
            double reachSq = 8.0 * 8.0;
            if (player.distanceToSqr(entity.getX(), entity.getY(), entity.getZ()) > reachSq) return;

            if (msg.action == Action.INTERACT) {
                LightEntityEvent.Interact event = new LightEntityEvent.Interact(entity, player, msg.hand, msg.hitPos);
                MinecraftForge.EVENT_BUS.post(event);
            } else if (msg.action == Action.ATTACK) {
                LightEntityEvent.Attack event = new LightEntityEvent.Attack(entity, player);
                MinecraftForge.EVENT_BUS.post(event);
            }
        });
        ctx.setPacketHandled(true);
    }
}

package net.void_.lightentity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.void_.lightentity.LightEntityMod;
import net.void_.lightentity.api.ILightEntity;
import net.void_.lightentity.network.LightNetwork;
import net.void_.lightentity.network.packet.ServerboundInteractLightEntityPacket;

import java.util.Optional;

/**
 * Client handlers for:
 * 1. F3+B Hitbox rendering in RenderLevelStageEvent.
 * 2. Left-click (Attack) and Right-click (Interact) hit detection via LightRaycast.
 */
@Mod.EventBusSubscriber(modid = LightEntityMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientHitboxAndInputHandler {

    /**
     * Renders white bounding boxes when F3+B is toggled in Minecraft.
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (!mc.getEntityRenderDispatcher().shouldRenderHitBoxes()) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();
        VertexConsumer builder = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        for (ILightEntity entity : ClientLightManager.getEntities()) {
            if (entity.isDiscarded()) continue;

            AABB bb = entity.getBoundingBox();
            poseStack.pushPose();
            poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

            // Draw clean white/cyan bounding box
            LevelRenderer.renderLineBox(poseStack, builder, bb, 1.0f, 1.0f, 1.0f, 1.0f);

            poseStack.popPose();
        }
    }

    /**
     * Handles Right-Click on empty air or blocks to detect interaction with LightEntity hitboxes.
     */
    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        handleInteract(event.getEntity(), event.getHand());
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) {
            handleInteract(event.getEntity(), event.getHand());
        }
    }

    private static void handleInteract(Player player, InteractionHand hand) {
        if (player == null || !player.level().isClientSide()) return;

        Optional<LightRaycast.RaycastHit> hitOpt = LightRaycast.raycast(player, ClientLightManager.getGrid(), 5.0);
        if (hitOpt.isPresent()) {
            LightRaycast.RaycastHit hit = hitOpt.get();
            LightNetwork.CHANNEL.sendToServer(new ServerboundInteractLightEntityPacket(
                    hit.entity().getId(),
                    ServerboundInteractLightEntityPacket.Action.INTERACT,
                    hand,
                    hit.hitPos()
            ));
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) return;
        Player player = event.getEntity();
        Vec3 blockHit = event.getHitVec().getLocation();
        double blockDist = player.getEyePosition().distanceTo(blockHit);

        Optional<LightRaycast.RaycastHit> hitOpt = LightRaycast.raycast(player, ClientLightManager.getGrid(), blockDist);
        if (hitOpt.isPresent()) {
            event.setCanceled(true);
            LightRaycast.RaycastHit hit = hitOpt.get();
            LightNetwork.CHANNEL.sendToServer(new ServerboundInteractLightEntityPacket(
                    hit.entity().getId(),
                    ServerboundInteractLightEntityPacket.Action.INTERACT,
                    event.getHand(),
                    hit.hitPos()
            ));
        }
    }

    /**
     * Handles Left-Click on empty air or blocks to attack / left-click LightEntity hitboxes.
     */
    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        if (player == null || !player.level().isClientSide()) return;

        Optional<LightRaycast.RaycastHit> hitOpt = LightRaycast.raycast(player, ClientLightManager.getGrid(), 5.0);
        if (hitOpt.isPresent()) {
            LightRaycast.RaycastHit hit = hitOpt.get();
            LightNetwork.CHANNEL.sendToServer(new ServerboundInteractLightEntityPacket(
                    hit.entity().getId(),
                    ServerboundInteractLightEntityPacket.Action.ATTACK,
                    InteractionHand.MAIN_HAND,
                    hit.hitPos()
            ));
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide()) return;
        Player player = event.getEntity();
        double blockDist = player.getEyePosition().distanceTo(Vec3.atCenterOf(event.getPos()));

        Optional<LightRaycast.RaycastHit> hitOpt = LightRaycast.raycast(player, ClientLightManager.getGrid(), blockDist);
        if (hitOpt.isPresent()) {
            event.setCanceled(true);
            LightRaycast.RaycastHit hit = hitOpt.get();
            LightNetwork.CHANNEL.sendToServer(new ServerboundInteractLightEntityPacket(
                    hit.entity().getId(),
                    ServerboundInteractLightEntityPacket.Action.ATTACK,
                    InteractionHand.MAIN_HAND,
                    hit.hitPos()
            ));
        }
    }
}

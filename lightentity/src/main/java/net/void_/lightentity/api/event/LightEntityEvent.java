package net.void_.lightentity.api.event;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import net.void_.lightentity.api.ILightEntity;

/**
 * Base event for player interactions with a LightEntity.
 */
public abstract class LightEntityEvent extends Event {

    private final ILightEntity entity;
    private final Player player;

    public LightEntityEvent(ILightEntity entity, Player player) {
        this.entity = entity;
        this.player = player;
    }

    public ILightEntity getEntity() {
        return entity;
    }

    public Player getPlayer() {
        return player;
    }

    /**
     * Fired when a player right-clicks (interacts with) a LightEntity.
     * Can be canceled to prevent further processing.
     */
    @Cancelable
    public static class Interact extends LightEntityEvent {
        private final InteractionHand hand;
        private final Vec3 hitPos;

        public Interact(ILightEntity entity, Player player, InteractionHand hand, Vec3 hitPos) {
            super(entity, player);
            this.hand = hand;
            this.hitPos = hitPos;
        }

        public InteractionHand getHand() {
            return hand;
        }

        public Vec3 getHitPos() {
            return hitPos;
        }
    }

    /**
     * Fired when a player left-clicks (attacks) a LightEntity.
     * Can be canceled.
     */
    @Cancelable
    public static class Attack extends LightEntityEvent {
        public Attack(ILightEntity entity, Player player) {
            super(entity, player);
        }
    }
}

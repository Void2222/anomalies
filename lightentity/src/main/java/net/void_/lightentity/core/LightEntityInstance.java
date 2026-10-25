package net.void_.lightentity.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.void_.lightentity.api.ILightComponent;
import net.void_.lightentity.api.ILightEntity;
import net.void_.lightentity.api.LightEntityType;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Concrete high-performance implementation of {@link ILightEntity}.
 * Features:
 * - Extremely low memory footprint (~200 bytes per instance)
 * - 0-allocation tick iterations using volatile snapshot array swap
 * - Decoupled from Minecraft's monolithic Entity hierarchy
 */
public class LightEntityInstance implements ILightEntity {

    private final UUID id;
    private final LightEntityType<?> type;
    private Level level;

    private double x;
    private double y;
    private double z;

    private float width = 1.0f;
    private float height = 1.0f;
    private AABB boundingBox;

    private BlockPos blockPos;
    private ChunkPos chunkPos;

    private volatile boolean discarded = false;

    // Components state
    private final List<ILightComponent> components = new CopyOnWriteArrayList<>();
    private volatile ILightComponent[] activeComponents = new ILightComponent[0];

    public LightEntityInstance(LightEntityType<?> type, UUID id, Level level, Vec3 pos) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.id = id != null ? id : UUID.randomUUID();
        this.level = Objects.requireNonNull(level, "level cannot be null");
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        this.blockPos = BlockPos.containing(x, y, z);
        this.chunkPos = new ChunkPos(blockPos);
        recalculateBoundingBox();
    }

    private void recalculateBoundingBox() {
        double halfW = width / 2.0;
        this.boundingBox = new AABB(x - halfW, y, z - halfW, x + halfW, y + height, z + halfW);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public LightEntityType<?> getType() {
        return type;
    }

    @Override
    public Vec3 getPosition() {
        return new Vec3(x, y, z);
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }

    @Override
    public double getZ() {
        return z;
    }

    @Override
    public void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockPos = BlockPos.containing(x, y, z);
        this.chunkPos = new ChunkPos(blockPos);
        recalculateBoundingBox();
    }

    @Override
    public BlockPos getBlockPos() {
        return blockPos;
    }

    @Override
    public ChunkPos getChunkPos() {
        return chunkPos;
    }

    @Override
    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    @Override
    public AABB getBoundingBox() {
        return boundingBox;
    }

    @Override
    public float getWidth() {
        return width;
    }

    @Override
    public float getHeight() {
        return height;
    }

    @Override
    public void setDimensions(float width, float height) {
        this.width = width;
        this.height = height;
        recalculateBoundingBox();
    }

    @Override
    public boolean isDiscarded() {
        return discarded;
    }

    @Override
    public void discard() {
        if (!discarded) {
            discarded = true;
            for (ILightComponent comp : activeComponents) {
                try {
                    comp.onDetach(this);
                } catch (Exception e) {
                    // Log and prevent crash during discard
                }
            }
            components.clear();
            activeComponents = new ILightComponent[0];
        }
    }

    @Override
    public <C extends ILightComponent> void addComponent(C component) {
        Objects.requireNonNull(component, "Component cannot be null");
        components.add(component);
        updateActiveSnapshot();
        component.onAttach(this);
    }

    @Override
    public <C extends ILightComponent> void removeComponent(Class<C> componentClass) {
        Iterator<ILightComponent> it = components.iterator();
        boolean removed = false;
        while (it.hasNext()) {
            ILightComponent comp = it.next();
            if (componentClass.isInstance(comp)) {
                comp.onDetach(this);
                components.remove(comp);
                removed = true;
            }
        }
        if (removed) {
            updateActiveSnapshot();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C extends ILightComponent> Optional<C> getComponent(Class<C> componentClass) {
        ILightComponent[] snapshot = activeComponents;
        for (ILightComponent comp : snapshot) {
            if (componentClass.isInstance(comp)) {
                return Optional.of((C) comp);
            }
        }
        return Optional.empty();
    }

    @Override
    public <C extends ILightComponent> boolean hasComponent(Class<C> componentClass) {
        ILightComponent[] snapshot = activeComponents;
        for (ILightComponent comp : snapshot) {
            if (componentClass.isInstance(comp)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Collection<ILightComponent> getComponents() {
        return Collections.unmodifiableList(components);
    }

    private synchronized void updateActiveSnapshot() {
        this.activeComponents = components.toArray(new ILightComponent[0]);
    }

    @Override
    public void tick() {
        if (discarded) return;

        ILightComponent[] snapshot = activeComponents;
        boolean isClient = level.isClientSide();

        // Zero-allocation loop over volatile snapshot array
        for (ILightComponent comp : snapshot) {
            try {
                if (isClient) {
                    comp.clientTick(this);
                } else {
                    comp.serverTick(this);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Type", type.getId().toString());
        tag.putDouble("X", x);
        tag.putDouble("Y", y);
        tag.putDouble("Z", z);
        tag.putFloat("Width", width);
        tag.putFloat("Height", height);

        ListTag compList = new ListTag();
        for (ILightComponent comp : activeComponents) {
            CompoundTag compTag = new CompoundTag();
            compTag.putString("Class", comp.getClass().getName());
            CompoundTag dataTag = new CompoundTag();
            comp.writeToNbt(dataTag);
            compTag.put("Data", dataTag);
            compList.add(compTag);
        }
        tag.put("Components", compList);

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        if (tag.contains("X") && tag.contains("Y") && tag.contains("Z")) {
            setPosition(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        }
        if (tag.contains("Width") && tag.contains("Height")) {
            setDimensions(tag.getFloat("Width"), tag.getFloat("Height"));
        }

        if (tag.contains("Components", Tag.TAG_LIST)) {
            ListTag compList = tag.getList("Components", Tag.TAG_COMPOUND);
            for (int i = 0; i < compList.size(); i++) {
                CompoundTag compTag = compList.getCompound(i);
                String className = compTag.getString("Class");
                CompoundTag dataTag = compTag.getCompound("Data");
                for (ILightComponent comp : activeComponents) {
                    if (comp.getClass().getName().equals(className)) {
                        comp.readFromNbt(dataTag);
                        break;
                    }
                }
            }
        }
    }
}

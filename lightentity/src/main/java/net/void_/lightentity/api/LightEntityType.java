package net.void_.lightentity.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Definition and Factory for LightEntity types.
 */
public class LightEntityType<T extends ILightEntity> {

    private static final ConcurrentHashMap<ResourceLocation, LightEntityType<?>> REGISTRY = new ConcurrentHashMap<>();

    private final ResourceLocation id;
    private final EntityFactory<T> factory;

    public LightEntityType(ResourceLocation id, EntityFactory<T> factory) {
        this.id = Objects.requireNonNull(id, "Type ID cannot be null");
        this.factory = Objects.requireNonNull(factory, "Factory cannot be null");
    }

    public ResourceLocation getId() {
        return id;
    }

    public T create(UUID uuid, Level level, Vec3 pos) {
        return factory.create(this, uuid, level, pos);
    }

    public static <E extends ILightEntity> LightEntityType<E> register(ResourceLocation id, EntityFactory<E> factory) {
        LightEntityType<E> type = new LightEntityType<>(id, factory);
        REGISTRY.put(id, type);
        return type;
    }

    public static LightEntityType<?> get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    @FunctionalInterface
    public interface EntityFactory<T extends ILightEntity> {
        T create(LightEntityType<T> type, UUID uuid, Level level, Vec3 pos);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LightEntityType<?> that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "LightEntityType[" + id + "]";
    }
}

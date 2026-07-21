package net.void_.anomalies.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import net.void_.anomalies.component.DamageComponent;
import net.void_.anomalies.component.ImpulseComponent;
import net.void_.anomalies.component.ParticleComponent;
import net.void_.anomalies.component.SoundComponent;
import net.void_.anomalies.component.TriggerComponent;

import java.util.ArrayList;
import java.util.List;

public class AnomalyEntity extends Entity {

    // Список наших компонентов, которые оживляют эту аномалию
    private final List<IAnomalyComponent> components = new ArrayList<>();

    // Строковый идентификатор типа аномалии для сохранения/загрузки
    private String anomalyType = "";

    // Счетчик для тиков
    private int tickCounter = 0;

    public AnomalyEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    // Метод для добавления компонентов прямо в коде
    public AnomalyEntity addComponent(IAnomalyComponent component) {
        this.components.add(component);
        return this;
    }

    // Установка типа аномалии (вызывается из ZoneFactory)
    public void setAnomalyType(String type) {
        this.anomalyType = type;
    }

    @Override
    public void tick() {
        super.tick();

        // Клиентская сторона
        if (this.level().isClientSide) {
            for (IAnomalyComponent component : components) {
                component.clientTick(this);
            }
            return;
        }

        // Серверная сторона: логика триггеров, урона и физики
        tickCounter++;

        if (tickCounter >= 10) {
            tickCounter = 0;
            for (IAnomalyComponent component : components) {
                component.serverTick(this);
            }
        }
    }

    // Делаем аномалию доступной для прицеливания (чтобы по ней можно было кликнуть/уверить)
    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
        // Синхронизация данных
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("AnomalyType")) {
            this.anomalyType = tag.getString("AnomalyType");
            rebuildComponents(); // Восстанавливаем компоненты при загрузке мира
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("AnomalyType", this.anomalyType);
    }

    // Метод для воссоздания компонентов при загрузке из сохранения
    private void rebuildComponents() {
        this.components.clear();

        if (anomalyType.equals("zharka")) {
            this.addComponent(new ParticleComponent(net.minecraft.core.particles.ParticleTypes.FLAME, 2, ParticleComponent.Shape.CYLINDER, 0.8D, 2.0D, 4));
            this.addComponent(new ParticleComponent(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, 5, ParticleComponent.Shape.CYLINDER, 0.6D, 1.8D, 2));
            this.addComponent(new SoundComponent(net.minecraft.sounds.SoundEvents.FIRE_AMBIENT, 40, net.minecraft.sounds.SoundSource.BLOCKS));
            DamageComponent damageComp = new DamageComponent(4.0F, this.level().damageSources().inFire());
            this.addComponent(new TriggerComponent(0.3D, (anom, target) -> {
                target.setSecondsOnFire(6);
                damageComp.inflictDamage(target);
            }));
        } else if (anomalyType.equals("tramplin")) {
            this.addComponent(new ParticleComponent(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, 3, ParticleComponent.Shape.DISC, 1.2D, 0.2D, 3));
            this.addComponent(new SoundComponent(net.minecraft.sounds.SoundEvents.ENDER_EYE_LAUNCH, 60, net.minecraft.sounds.SoundSource.BLOCKS));
            ImpulseComponent impulseComp = new ImpulseComponent(0.0D, 1.2D, 0.0D, false);
            DamageComponent damageComp = new DamageComponent(6.0F, this.level().damageSources().generic());
            this.addComponent(new TriggerComponent(0.5D, (anom, target) -> {
                impulseComp.applyImpulse(anom, target);
                damageComp.inflictDamage(target);
            }));
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
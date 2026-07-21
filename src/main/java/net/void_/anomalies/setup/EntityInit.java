package net.void_.anomalies.setup;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.void_.anomalies.Anomalies;
import net.void_.anomalies.core.AnomalyEntity;

public class EntityInit {

    // Создаем реестр для сущностей нашего мода
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Anomalies.MOD_ID);

    // Регистрируем нашу базовую аномалию
    public static final RegistryObject<EntityType<AnomalyEntity>> ANOMALY = ENTITIES.register("anomaly",
            () -> EntityType.Builder.<AnomalyEntity>of(AnomalyEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F) // Размер хитбокса по умолчанию (1х1 блок)
                    .clientTrackingRange(64) // Радиус, на котором клиент начинает видеть аномалию
                    .updateInterval(20)      // Частота обновления пакетов позиции
                    .build("anomaly")
    );

    // Метод для регистрации шины в главном классе мода
    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
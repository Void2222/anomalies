package net.void_.anomalies.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.void_.anomalies.core.AnomalyEntity;

public class AnomalyRenderer extends EntityRenderer<AnomalyEntity> {

    public AnomalyRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(AnomalyEntity entity) {
        // Возвращаем заглушку, так как модель мы не рендерим
        return ResourceLocation.fromNamespaceAndPath("anomalies", "textures/entity/anomaly.png");
    }

    // Полностью отключаем стандартный рендеринг тела сущности (ведь у нас только партиклы!)
    @Override
    public boolean shouldRender(AnomalyEntity livingEntity, net.minecraft.client.renderer.culling.Frustum camera, double camX, double camY, double camZ) {
        // Возвращаем true, чтобы клиент не отключал логику сущности,
        // но сама моделька рендериться не будет (ее просто нет)
        return true;
    }
}
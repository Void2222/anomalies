package net.void_.anomalies.components;

import net.void_.anomalies.core.IAnomalyComponent;
import net.void_.lightentity.api.ILightEntity;

public class SnapToGridComponent implements IAnomalyComponent {

    private boolean isSnapped = false;

    @Override
    public void serverTick(ILightEntity entity) {
        if (!isSnapped) {
            double x = Math.floor(entity.getX()) + 0.5D;
            double y = Math.floor(entity.getY());
            double z = Math.floor(entity.getZ());

            entity.setPosition(x, y, z);
            isSnapped = true;
        }
    }
}
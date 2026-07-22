package net.void_.anomalies.components;

import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;

public class SnapToGridComponent implements IAnomalyComponent {

    private boolean isSnapped = false;

    @Override
    public void serverTick(AnomalyEntity anomaly) {
        // Выравниваем только один раз при спавне/загрузке, чтобы не дергать позицию постоянно
        if (!isSnapped) {
            double x = Math.floor(anomaly.getX()) + 0.5D;
            double y = Math.floor(anomaly.getY()); // Нижняя граница блока (или оставь anomaly.getY() по желанию)
            double z = Math.floor(anomaly.getZ()) + 0.5D;

            anomaly.setPos(x, y, z);
            isSnapped = true;
        }
    }
}
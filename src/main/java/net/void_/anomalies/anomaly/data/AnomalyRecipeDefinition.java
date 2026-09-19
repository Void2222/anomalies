package net.void_.anomalies.anomaly.data;

import java.util.Collections;
import java.util.List;

public class AnomalyRecipeDefinition {
    private IngredientData input; // Для обратной совместимости со старыми JSON
    private List<IngredientData> inputs;
    private ResultData output;
    private int time;

    public List<IngredientData> getInputs() {
        if (inputs != null && !inputs.isEmpty()) {
            return inputs;
        }
        if (input != null) {
            return List.of(input);
        }
        return Collections.emptyList();
    }

    public ResultData getOutput() { return output; }
    public int getTime() { return time; }

    public static class IngredientData {
        private String item;
        private int count = 1;

        public String getItem() { return item; }
        public int getCount() { return count; }
    }

    public static class ResultData {
        private String item;
        private int count = 1;

        public String getItem() { return item; }
        public int getCount() { return count; }
    }
}
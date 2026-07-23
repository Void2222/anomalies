package net.void_.anomalies.anomaly.data;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.Random;

public class MinMaxRange {
    private final double min;
    private final double max;
    private static final Random RANDOM = new Random();

    public MinMaxRange(double min, double max) {
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
    }

    // Получить случайное целое число (для тиков, количества частиц)
    public int getInt() {
        if (min == max) return (int) min;
        return (int) (min + RANDOM.nextDouble() * (max - min + 1));
    }

    // Получить случайное дробное число (для урона, радиуса)
    public double getDouble() {
        if (min == max) return min;
        return min + RANDOM.nextDouble() * (max - min);
    }

    // Кастомный десериализатор для Gson, чтобы он понимал и `40`, и `[30, 60]`
    public static class Deserializer implements JsonDeserializer<MinMaxRange> {
        @Override
        public MinMaxRange deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonPrimitive()) {
                double val = json.getAsDouble();
                return new MinMaxRange(val, val);
            } else if (json.isJsonArray() && json.getAsJsonArray().size() >= 2) {
                double min = json.getAsJsonArray().get(0).getAsDouble();
                double max = json.getAsJsonArray().get(1).getAsDouble();
                return new MinMaxRange(min, max);
            }
            return new MinMaxRange(0, 0);
        }
    }

    // Проверка, равен ли диапазон нулю (или меньше/равен нулю)
    public boolean isZero() {
        return max <= 0.0;
    }

    public double getMax() {
        return max;
    }

}
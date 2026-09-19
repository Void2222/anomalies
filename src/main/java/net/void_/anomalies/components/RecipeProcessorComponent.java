package net.void_.anomalies.components;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.void_.anomalies.anomaly.data.AnomalyDefinition;
import net.void_.anomalies.anomaly.data.AnomalyRecipeDefinition;
import net.void_.anomalies.anomaly.data.CraftingSession;
import net.void_.anomalies.anomaly.data.ZoneConfig;
import net.void_.anomalies.anomaly.loader.AnomalyReloadListener;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;
import net.void_.anomalies.dsl.model.AnomalyScriptModel;
import net.void_.anomalies.dsl.registry.AnomalyScriptRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class RecipeProcessorComponent implements IAnomalyComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger("Anomalies-Recipes");
    private static final String NBT_KEY = "CraftingSessions";

    @Override
    public void serverTick(AnomalyEntity anomaly) {
        String type = anomaly.getAnomalyType();
        String state = anomaly.getCurrentState();

        Optional<AnomalyScriptModel> scriptOpt = AnomalyScriptRegistry.get(type);
        if (scriptOpt.isEmpty()) return;

        List<String> allowedRecipes = scriptOpt.get().getRecipesForState(state);
        if (allowedRecipes.isEmpty()) return;

        List<CraftingSession> sessions = readSessionsFromNBT(anomaly);

        AnomalyDefinition definition = AnomalyReloadListener.get(type, state);
        List<ZoneConfig> zones = definition != null ? definition.zones() : List.of();

        ZoneConfig zone0 = zones.isEmpty() ? null : zones.stream()
                .min(Comparator.comparingDouble(ZoneConfig::radius))
                .orElse(null);

        AABB scanBox = zone0 != null
                ? anomaly.getBoundingBox().inflate(zone0.radius())
                : anomaly.getBoundingBox().inflate(2.0, 2.0, 2.0);

        List<ItemEntity> rawItems = anomaly.level().getEntitiesOfClass(ItemEntity.class, scanBox);
        Map<UUID, ItemEntity> itemMap = new HashMap<>();

        for (ItemEntity item : rawItems) {
            if (!item.isAlive()) continue;

            boolean inside = zone0 != null
                    ? zone0.contains(anomaly, item)
                    : anomaly.position().closerThan(item.position(), 2.5);

            if (inside) {
                itemMap.put(item.getUUID(), item);
            }
        }

        Map<UUID, Integer> totalReservedPerItem = new HashMap<>();
        Iterator<CraftingSession> sessionIterator = sessions.iterator();

        while (sessionIterator.hasNext()) {
            CraftingSession session = sessionIterator.next();
            boolean sessionValid = true;
            Map<UUID, Integer> sessionReserves = session.getReservedItems();

            for (Map.Entry<UUID, Integer> entry : sessionReserves.entrySet()) {
                UUID uuid = entry.getKey();
                int reservedAmount = entry.getValue();
                ItemEntity item = itemMap.get(uuid);

                if (item == null || item.getItem().getCount() < (totalReservedPerItem.getOrDefault(uuid, 0) + reservedAmount)) {
                    sessionValid = false;
                    break;
                }
            }

            if (sessionValid) {
                sessionReserves.forEach((uuid, amount) ->
                        totalReservedPerItem.merge(uuid, amount, Integer::sum));
            } else {
                if (anomaly.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(
                            ParticleTypes.SMOKE,
                            anomaly.getX(), anomaly.getY() + 0.5, anomaly.getZ(),
                            8, 0.15, 0.15, 0.15, 0.02
                    );
                }
                sessionIterator.remove();
            }
        }

        Map<UUID, Integer> availableVirtualCount = new HashMap<>();
        for (Map.Entry<UUID, ItemEntity> entry : itemMap.entrySet()) {
            UUID uuid = entry.getKey();
            int realCount = entry.getValue().getItem().getCount();
            int reserved = totalReservedPerItem.getOrDefault(uuid, 0);
            availableVirtualCount.put(uuid, realCount - reserved);
        }

        for (String recipeName : allowedRecipes) {
            AnomalyRecipeDefinition recipeDef = AnomalyReloadListener.getRecipe(type, recipeName);
            if (recipeDef == null) {
                LOGGER.warn("Recipe '{}' listed in DSL state '{}' was NOT loaded into memory for type '{}'!", recipeName, state, type);
                continue;
            }

            List<AnomalyRecipeDefinition.IngredientData> ingredients = recipeDef.getInputs();
            if (ingredients.isEmpty()) continue;

            boolean matched = true;
            while (matched) {
                Map<UUID, Integer> candidateBinds = new HashMap<>();
                boolean allMatched = true;

                for (AnomalyRecipeDefinition.IngredientData ingredient : ingredients) {
                    // Исправлено под 1.20.1
                    Item requiredItem = BuiltInRegistries.ITEM.get(new ResourceLocation(ingredient.getItem()));
                    if (requiredItem == Items.AIR) {
                        allMatched = false;
                        break;
                    }

                    int needed = ingredient.getCount();

                    for (Map.Entry<UUID, ItemEntity> entry : itemMap.entrySet()) {
                        UUID uuid = entry.getKey();
                        ItemEntity item = entry.getValue();

                        if (item.getItem().getItem() == requiredItem) {
                            int alreadyBoundInThisBatch = candidateBinds.getOrDefault(uuid, 0);
                            int virtualAvailable = availableVirtualCount.getOrDefault(uuid, 0) - alreadyBoundInThisBatch;

                            if (virtualAvailable > 0) {
                                int take = Math.min(needed, virtualAvailable);
                                candidateBinds.merge(uuid, take, Integer::sum);
                                needed -= take;

                                if (needed <= 0) break;
                            }
                        }
                    }

                    if (needed > 0) {
                        allMatched = false;
                        break;
                    }
                }

                if (allMatched && !candidateBinds.isEmpty()) {
                    sessions.add(new CraftingSession(recipeName, 0, candidateBinds));
                    candidateBinds.forEach((uuid, taken) ->
                            availableVirtualCount.compute(uuid, (k, v) -> (v == null ? 0 : v) - taken));
                } else {
                    matched = false;
                }
            }
        }

        Iterator<CraftingSession> progressIterator = sessions.iterator();
        while (progressIterator.hasNext()) {
            CraftingSession session = progressIterator.next();
            AnomalyRecipeDefinition recipeDef = AnomalyReloadListener.getRecipe(type, session.getRecipeId());

            if (recipeDef == null) {
                progressIterator.remove();
                continue;
            }

            session.incrementProgress();

            if (session.getProgress() >= recipeDef.getTime()) {
                for (Map.Entry<UUID, Integer> entry : session.getReservedItems().entrySet()) {
                    ItemEntity item = itemMap.get(entry.getKey());
                    if (item != null) {
                        ItemStack stack = item.getItem();
                        stack.shrink(entry.getValue());
                        if (stack.isEmpty()) {
                            item.discard();
                        }
                    }
                }

                // Исправлено под 1.20.1
                Item outputItem = BuiltInRegistries.ITEM.get(new ResourceLocation(recipeDef.getOutput().getItem()));
                if (outputItem != Items.AIR) {
                    ItemStack resultStack = new ItemStack(outputItem, recipeDef.getOutput().getCount());
                    ItemEntity resultEntity = new ItemEntity(
                            anomaly.level(),
                            anomaly.getX(),
                            anomaly.getY() + 0.5,
                            anomaly.getZ(),
                            resultStack
                    );
                    anomaly.level().addFreshEntity(resultEntity);
                }

                progressIterator.remove();
            }
        }

        writeSessionsToNBT(anomaly, sessions);
    }

    private List<CraftingSession> readSessionsFromNBT(AnomalyEntity anomaly) {
        List<CraftingSession> list = new ArrayList<>();
        CompoundTag tag = anomaly.getPersistentData();
        if (tag.contains(NBT_KEY, Tag.TAG_LIST)) {
            ListTag tagList = tag.getList(NBT_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < tagList.size(); i++) {
                list.add(CraftingSession.deserializeNBT(tagList.getCompound(i)));
            }
        }
        return list;
    }

    private void writeSessionsToNBT(AnomalyEntity anomaly, List<CraftingSession> sessions) {
        ListTag tagList = new ListTag();
        for (CraftingSession session : sessions) {
            tagList.add(session.serializeNBT());
        }
        anomaly.getPersistentData().put(NBT_KEY, tagList);
    }
}
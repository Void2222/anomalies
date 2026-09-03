package net.void_.anomalies.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AnomalyMultitoolItem extends Item {

    public AnomalyMultitoolItem(Properties properties) {
        super(properties);
    }

    public static MultitoolMode getMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Mode")) {
            try {
                return MultitoolMode.valueOf(tag.getString("Mode"));
            } catch (IllegalArgumentException ignored) {}
        }
        return MultitoolMode.ANALYZE;
    }

    public static void setMode(ItemStack stack, MultitoolMode mode) {
        stack.getOrCreateTag().putString("Mode", mode.name());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Работаем только на сервере и только с главной рукой
        if (!level.isClientSide && hand == InteractionHand.MAIN_HAND) {
            CompoundTag tag = stack.getOrCreateTag();

            // 1. Проверяем, находится ли мультитул в процессе интерактивного ввода
            boolean isWaitingForOffset = tag.getBoolean("WaitingForOffset");
            boolean isWaitingForParams = tag.getBoolean("WaitingForParams");

            // 2. Если зажат Shift
            if (player.isShiftKeyDown()) {

                // Если игрок зажал Shift+ПКМ по воздуху ПОСЛЕ того, как выделил аномалию — отменяем выделение
                if (isWaitingForOffset || isWaitingForParams) {
                    tag.remove("WaitingForOffset");
                    tag.remove("WaitingForParams");
                    tag.remove("SelectedAnomaly");

                    player.sendSystemMessage(Component.literal("§c[Мультитул] Выбор аномалии сброшен."));
                    return InteractionResultHolder.success(stack);
                }

                // Если интерактивных режимов не было — переключаем режим мультитула по кругу
                MultitoolMode currentMode = getMode(stack);
                MultitoolMode nextMode = currentMode.next();
                setMode(stack, nextMode);

                // Очищаем сессионные NBT-теги при смене режима
                tag.remove("WaitingForOffset");
                tag.remove("WaitingForParams");
                tag.remove("SelectedAnomaly");

                // Сообщение в Action Bar
                player.displayClientMessage(
                        Component.literal("§lРежим: " + nextMode.getColor() + nextMode.getName()),
                        true
                );

                return InteractionResultHolder.success(stack);
            }
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag isAdvanced) {
        MultitoolMode mode = getMode(stack);

        tooltip.add(Component.literal("§7Текущий режим: ")
                .append(Component.literal(mode.getName()).withStyle(mode.getColor(), ChatFormatting.BOLD)));
        tooltip.add(Component.literal("§8" + mode.getDescription()));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§eShift + ПКМ по воздуху §7— сменить режим").withStyle(ChatFormatting.ITALIC));
    }
}
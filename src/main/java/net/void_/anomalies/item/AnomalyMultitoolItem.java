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

        // Переключение режимов по Shift + ПКМ в воздух
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                MultitoolMode current = getMode(stack);
                MultitoolMode next = current.next();
                setMode(stack, next);

                // Очищаем старые временные данные при смене режима
                CompoundTag tag = stack.getTag();
                if (tag != null) {
                    tag.remove("WaitingForParams");
                    tag.remove("WaitingForOffset");
                    tag.remove("SelectedAnomaly");
                }

                player.displayClientMessage(
                        Component.literal("§b[Мультитул] Режим изменен на: ")
                                .append(Component.literal(next.getName()).withStyle(next.getColor(), ChatFormatting.BOLD)),
                        true
                );
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        return super.use(level, player, hand);
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
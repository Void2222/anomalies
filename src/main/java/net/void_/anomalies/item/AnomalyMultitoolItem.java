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

        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                CompoundTag tag = stack.getOrCreateTag();

                // Очищаем сессионные NBT-теги при переключении режима
                tag.remove("WaitingForOffset");
                tag.remove("WaitingForParams");
                tag.remove("SelectedAnomaly");

                // Переключаем режим на следующий
                MultitoolMode currentMode = getMode(stack);
                MultitoolMode nextMode = currentMode.next();
                setMode(stack, nextMode);

                // Сообщение в Action Bar
                player.displayClientMessage(
                        Component.literal("§lРежим: " + nextMode.getColor() + nextMode.getName()),
                        true
                );
            }
            // Возвращаем sidedSuccess на обеих сторонах, чтобы клиент отправлял пакет на сервер
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
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
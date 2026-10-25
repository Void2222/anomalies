package net.void_.anomalies.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.void_.anomalies.network.AnomalyNetwork;
import net.void_.anomalies.network.SyncAnomalyOverridesPacket;

import java.util.UUID;

public class AnomalyEditScreen extends Screen {
    private final UUID anomalyId;
    private final CompoundTag overrides;

    private EditBox widthBox;
    private EditBox heightBox;
    private EditBox triggerBox;
    private Button pullToCenterButton;

    private boolean pullToCenter;

    public AnomalyEditScreen(UUID anomalyId, CompoundTag initialOverrides) {
        super(Component.literal("Редактор аномалии"));
        this.anomalyId = anomalyId;
        this.overrides = initialOverrides.copy();
        this.pullToCenter = overrides.contains("pullToCenter") ? overrides.getBoolean("pullToCenter") : true;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Поля ввода базовых параметров
        this.widthBox = new EditBox(this.font, centerX - 100, centerY - 60, 200, 20, Component.literal("Ширина"));
        this.widthBox.setValue(String.valueOf(overrides.contains("width") ? overrides.getDouble("width") : 1.0));
        this.addRenderableWidget(this.widthBox);

        this.heightBox = new EditBox(this.font, centerX - 100, centerY - 20, 200, 20, Component.literal("Высота"));
        this.heightBox.setValue(String.valueOf(overrides.contains("height") ? overrides.getDouble("height") : 1.0));
        this.addRenderableWidget(this.heightBox);

        this.triggerBox = new EditBox(this.font, centerX - 100, centerY + 20, 200, 20, Component.literal("Радиус триггера"));
        this.triggerBox.setValue(String.valueOf(overrides.contains("expandRadius") ? overrides.getDouble("expandRadius") : 1.0));
        this.addRenderableWidget(this.triggerBox);

        // Кнопка переключения физики втягивания
        this.pullToCenterButton = this.addRenderableWidget(Button.builder(
                Component.literal("Втягивание в центр: " + (pullToCenter ? "ВКЛ" : "ВЫКЛ")),
                button -> {
                    pullToCenter = !pullToCenter;
                    button.setMessage(Component.literal("Втягивание в центр: " + (pullToCenter ? "ВКЛ" : "ВЫКЛ")));
                }).bounds(centerX - 100, centerY + 60, 200, 20).build());

        // Кнопка сохранения
        this.addRenderableWidget(Button.builder(Component.literal("Сохранить"), button -> {
            saveChanges();
            onClose();
        }).bounds(centerX - 102, centerY + 100, 98, 20).build());

        // Кнопка отмены
        this.addRenderableWidget(Button.builder(Component.literal("Отмена"), button -> {
            onClose();
        }).bounds(centerX + 4, centerY + 100, 98, 20).build());
    }

    private void saveChanges() {
        try {
            if (!widthBox.getValue().isEmpty()) {
                overrides.putDouble("width", Double.parseDouble(widthBox.getValue()));
            }
            if (!heightBox.getValue().isEmpty()) {
                overrides.putDouble("height", Double.parseDouble(heightBox.getValue()));
            }
            if (!triggerBox.getValue().isEmpty()) {
                overrides.putDouble("expandRadius", Double.parseDouble(triggerBox.getValue()));
            }
            overrides.putBoolean("pullToCenter", pullToCenter);

            // Отправляем пакет синхронизации на сервер
            AnomalyNetwork.CHANNEL.sendToServer(new SyncAnomalyOverridesPacket(anomalyId, overrides));
        } catch (NumberFormatException ignored) {
            // Защита от некорректного ввода текста в числовые поля
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        guiGraphics.drawString(this.font, "Ширина:", widthBox.getX(), widthBox.getY() - 10, 0xA0A0A0);
        guiGraphics.drawString(this.font, "Высота:", heightBox.getX(), heightBox.getY() - 10, 0xA0A0A0);
        guiGraphics.drawString(this.font, "Радиус триггера:", triggerBox.getX(), triggerBox.getY() - 10, 0xA0A0A0);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
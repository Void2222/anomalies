package net.void_.anomalies.client.gui.editor.canvas;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * Утилита для рендеринга процедурной координатной сетки и плавных кривых Безье (Cubic Bezier).
 */
public final class CanvasRenderUtils {

    private CanvasRenderUtils() {}

    /**
     * Отрисовка адаптивной координатной сетки фона в стиле нодовых редакторов.
     */
    public static void renderGrid(PoseStack poseStack, int width, int height, float panX, float panY, float zoom) {
        float gridSize = 40.0f * zoom;
        if (gridSize < 10.0f) return;

        float offsetX = (panX % gridSize + gridSize) % gridSize;
        float offsetY = (panY % gridSize + gridSize) % gridSize;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f matrix = poseStack.last().pose();

        // Основная сетка (тонкие линии)
        float r = 0.22f, g = 0.22f, b = 0.25f, a = 0.5f;

        for (float x = offsetX; x < width; x += gridSize) {
            buffer.vertex(matrix, x, 0, 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, x, height, 0).color(r, g, b, a).endVertex();
        }

        for (float y = offsetY; y < height; y += gridSize) {
            buffer.vertex(matrix, 0, y, 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, width, y, 0).color(r, g, b, a).endVertex();
        }

        tesselator.end();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * Отрисовка плавной кубической кривой Безье с градиентом и надёжной толщиной.
     */
    public static void renderBezierCurve(PoseStack poseStack,
                                         float x1, float y1,
                                         float x2, float y2,
                                         int colorFrom, int colorTo,
                                         float thickness) {
        float dx = Math.abs(x2 - x1);
        float handleOffset = Math.max(40.0f, dx * 0.5f);

        float cx1 = x1 + handleOffset;
        float cy1 = y1;
        float cx2 = x2 - handleOffset;
        float cy2 = y2;

        int segments = 48; // Плотная гладкая кривая

        float r1 = ((colorFrom >> 16) & 0xFF) / 255.0f;
        float g1 = ((colorFrom >> 8) & 0xFF) / 255.0f;
        float b1 = (colorFrom & 0xFF) / 255.0f;
        float a1 = ((colorFrom >> 24) & 0xFF) / 255.0f;
        if (a1 == 0.0f) a1 = 1.0f;

        float r2 = ((colorTo >> 16) & 0xFF) / 255.0f;
        float g2 = ((colorTo >> 8) & 0xFF) / 255.0f;
        float b2 = (colorTo & 0xFF) / 255.0f;
        float a2 = ((colorTo >> 24) & 0xFF) / 255.0f;
        if (a2 == 0.0f) a2 = 1.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f matrix = poseStack.last().pose();

        float prevX = x1;
        float prevY = y1;

        // Несколько проходов со смещением для достижения сочной и четкой толщины провода без артефактов
        int passes = Math.max(1, (int) Math.ceil(thickness));

        for (int p = 0; p < passes; p++) {
            float off = (p - (passes - 1) / 2.0f) * 0.75f;
            prevX = x1;
            prevY = y1 + off;

            for (int i = 1; i <= segments; i++) {
                float t = (float) i / segments;
                float oneMinusT = 1.0f - t;

                float curX = (oneMinusT * oneMinusT * oneMinusT) * x1
                           + 3.0f * (oneMinusT * oneMinusT) * t * cx1
                           + 3.0f * oneMinusT * (t * t) * cx2
                           + (t * t * t) * x2;

                float curY = (oneMinusT * oneMinusT * oneMinusT) * y1
                           + 3.0f * (oneMinusT * oneMinusT) * t * cy1
                           + 3.0f * oneMinusT * (t * t) * cy2
                           + (t * t * t) * y2 + off;

                float prevT = (float) (i - 1) / segments;
                float curR = r1 + (r2 - r1) * prevT;
                float curG = g1 + (g2 - g1) * prevT;
                float curB = b1 + (b2 - b1) * prevT;
                float curA = a1 + (a2 - a1) * prevT;

                buffer.vertex(matrix, prevX, prevY, 0).color(curR, curG, curB, curA).endVertex();

                float nextR = r1 + (r2 - r1) * t;
                float nextG = g1 + (g2 - g1) * t;
                float nextB = b1 + (b2 - b1) * t;
                float nextA = a1 + (a2 - a1) * t;

                buffer.vertex(matrix, curX, curY, 0).color(nextR, nextG, nextB, nextA).endVertex();

                prevX = curX;
                prevY = curY;
            }
        }

        tesselator.end();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}

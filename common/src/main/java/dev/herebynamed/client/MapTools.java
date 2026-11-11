package dev.herebynamed.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.BooleanSupplier;

final class MapTools {
    private static final ResourceLocation ICONS = ResourceLocation.tryParse("hereby_named:textures/gui/map_tools.png");

    public interface HintedWidget {
        String hint();
        boolean showHint();
    }

    static Button icon(int x, int y, int icon, String hint, BooleanSupplier showHint, Button.OnPress action) {
        return new IconButton(x, y, icon, hint, showHint, action);
    }

    static Button selector(int x, int y, int width, String value, String hint, int color, BooleanSupplier showHint, Button.OnPress action) {
        return new SelectorButton(x, y, width, value, hint, color, showHint, action);
    }

    static void drawTooltip(GuiGraphics context, Font font, String text, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (text == null || text.isEmpty()) return;
        List<FormattedCharSequence> lines = font.split(Component.literal(text), 220);
        if (lines.isEmpty()) return;
        int maxLineWidth = 0;
        for (FormattedCharSequence line : lines) {
            maxLineWidth = Math.max(maxLineWidth, font.width(line));
        }
        int boxWidth = Math.min(240, maxLineWidth + 20);
        int boxHeight = 10 + lines.size() * 10;
        int drawX = mouseX + 12;
        int drawY = mouseY + 10;
        if (drawX + boxWidth > screenWidth) {
            drawX = mouseX - 12 - boxWidth;
        }
        if (drawX < 0) drawX = 0;
        if (drawY + boxHeight > screenHeight) {
            drawY = mouseY - 10 - boxHeight;
        }
        if (drawY < 0) drawY = 0;
        context.fill(drawX, drawY, drawX + boxWidth, drawY + boxHeight, 0xC8000000);
        for (int i = 0; i < lines.size(); i++) {
            context.drawString(font, lines.get(i), drawX + 10, drawY + 6 + i * 10, 0xFFFFFF);
        }
    }

    private static final class IconButton extends Button implements HintedWidget {
        private final int icon;
        private final String hint;
        private final BooleanSupplier showHint;

        IconButton(int x, int y, int icon, String hint, BooleanSupplier showHint, OnPress action) {
            super(x, y, 20, 20, Component.empty(), action, DEFAULT_NARRATION);
            this.icon = icon;
            this.hint = hint;
            this.showHint = showHint;
        }

        @Override
        public String hint() {
            return hint;
        }

        @Override
        public boolean showHint() {
            return showHint.getAsBoolean();
        }

        @Override
        protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            int iconX = getX() + (width - 16) / 2;
            int iconY = getY() + (height - 16) / 2;
            if (active) {
                if (isHovered()) {
                    iconY -= 1;
                    RenderSystem.setShaderColor(0.9f, 0.9f, 0.9f, 1.0f);
                } else {
                    RenderSystem.setShaderColor(0.9882f, 0.9882f, 0.9882f, 1.0f);
                }
            } else {
                RenderSystem.setShaderColor(0.25f, 0.25f, 0.25f, 1.0f);
            }
            if (isHovered()) {
                context.fill(iconX, iconY, iconX + 16, iconY + 16, 0x55ffffff);
            }
            context.blit(ICONS, iconX, iconY, icon * 16, 0, 16, 16);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private static final class SelectorButton extends Button implements HintedWidget {
        private final String hint;
        private final int color;
        private final BooleanSupplier showHint;

        SelectorButton(int x, int y, int width, String value, String hint, int color, BooleanSupplier showHint, OnPress action) {
            super(x, y, width, 20, Component.literal(value), action, DEFAULT_NARRATION);
            this.hint = hint;
            this.color = color;
            this.showHint = showHint;
        }

        @Override
        public String hint() {
            return hint;
        }

        @Override
        public boolean showHint() {
            return showHint.getAsBoolean();
        }

        @Override
        protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            int top = getY() + 2;
            context.fill(getX(), top, getX() + getWidth(), top + 16, isHovered() ? 0x66444444 : 0x66000000);
            context.fill(getX() + 3, top + 5, getX() + 5, top + 11, 0xff000000 | color);
            var font = Minecraft.getInstance().font;
            String label = font.plainSubstrByWidth(getMessage().getString(), getWidth() - 21);
            context.drawString(font, label, getX() + 9, top + 4, active ? 0xffffff : 0x777777);
            int arrow = getX() + getWidth() - 9;
            context.fill(arrow, top + 7, arrow + 5, top + 8, 0xffdddddd);
            context.fill(arrow + 1, top + 8, arrow + 4, top + 9, 0xffdddddd);
            context.fill(arrow + 2, top + 9, arrow + 3, top + 10, 0xffdddddd);
        }
    }

    private MapTools() {}
}

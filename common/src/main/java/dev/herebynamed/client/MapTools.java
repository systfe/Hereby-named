package dev.herebynamed.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import xaero.lib.client.gui.widget.Tooltip;
import xaero.map.gui.GuiTexturedButton;
import xaero.map.gui.TooltipButton;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Uses Xaero's own transparent icon buttons and tooltip handling. */
final class MapTools {
    private static final ResourceLocation ICONS = ResourceLocation.tryParse("hereby_named:textures/gui/map_tools.png");
    static Button icon(int x, int y, int icon, String hint, BooleanSupplier showHint, Button.OnPress action) {
        return new GuiTexturedButton(x, y, 20, 20, icon * 16, 0, 16, 16, ICONS, action,
                new Tooltip(Component.literal(hint)).withWidth(220)) {
            @Override public Supplier<Tooltip> getXaero_tooltip() { return showHint.getAsBoolean()?super.getXaero_tooltip():null; }
        };
    }
    static Button selector(int x, int y, int width, String value, String hint, int color, BooleanSupplier showHint, Button.OnPress action) {
        return new TooltipButton(x, y, width, 20, Component.literal(value), action, new Tooltip(Component.literal(hint))) {
            @Override public Supplier<Tooltip> getXaero_tooltip() { return showHint.getAsBoolean()?super.getXaero_tooltip():null; }
            @Override public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) { renderButton(context,mouseX,mouseY,delta); }
            public void renderButton(GuiGraphics context, int mouseX, int mouseY, float delta) {
                int top = getY()+2;
                context.fill(getX(), top, getX()+getWidth(), top+16, isHovered()?0x66444444:0x66000000);
                context.fill(getX()+3, top+5, getX()+5, top+11, 0xff000000|color);
                var font = Minecraft.getInstance().font;
                String label = font.plainSubstrByWidth(getMessage().getString(), getWidth()-21);
                context.drawString(font, label, getX()+9, top+4, active?0xffffff:0x777777);
                int arrow = getX()+getWidth()-9;
                context.fill(arrow, top+7, arrow+5, top+8, 0xffdddddd);
                context.fill(arrow+1, top+8, arrow+4, top+9, 0xffdddddd);
                context.fill(arrow+2, top+9, arrow+3, top+10, 0xffdddddd);
            }
        };
    }
    private MapTools() {}
}

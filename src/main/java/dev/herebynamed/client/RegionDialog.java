package dev.herebynamed.client;

import dev.herebynamed.domain.RegionBook;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import java.util.function.BiConsumer;

final class RegionDialog extends Screen implements EditorDialog {
    private final Screen parent;
    private final RegionBook.Region region;
    private final BiConsumer<String,Integer> save;
    private TextFieldWidget name, color;
    private String error = "";
    RegionDialog(Screen parent, RegionBook.Region region, BiConsumer<String,Integer> save) {
        super(Text.literal(region == null ? "新增命名区域" : "修改区域名称与颜色"));
        this.parent = parent; this.region = region; this.save = save;
    }
    @Override protected void init() {
        int x = width/2-110, y = height/2-50;
        name = new TextFieldWidget(textRenderer, x, y, 220, 20, Text.literal("区域名称")); name.setMaxLength(80);
        color = new TextFieldWidget(textRenderer, x, y+42, 220, 20, Text.literal("颜色 #RRGGBB")); color.setMaxLength(7);
        name.setText(region == null ? "" : region.name());
        color.setText(region == null ? "#66BBDD" : String.format("#%06X", region.color()));
        addDrawableChild(name); addDrawableChild(color); setInitialFocus(name);
        addDrawableChild(ButtonWidget.builder(Text.literal("保存"), button -> {
            try {
                String text = name.getText().strip(), hex = color.getText().replace("#", "");
                if (text.isBlank() || text.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("请输入有效区域名");
                if (!hex.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException("颜色格式为 #RRGGBB");
                save.accept(text, Integer.parseInt(hex, 16)); close();
            } catch (IllegalArgumentException ex) { error = ex.getMessage(); }
        }).dimensions(x,y+76,106,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("取消"), button -> close()).dimensions(x+114,y+76,106,20).build());
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context); context.drawCenteredTextWithShadow(textRenderer, title, width/2,height/2-85,0xffffff);
        context.drawTextWithShadow(textRenderer,"区域名称",width/2-110,height/2-63,0xdddddd);
        context.drawTextWithShadow(textRenderer,"标记颜色（#RRGGBB）",width/2-110,height/2-21,0xdddddd);
        super.render(context,mouseX,mouseY,delta);
        context.drawCenteredTextWithShadow(textRenderer,error,width/2,height/2+60,0xff7777);
    }
    @Override public void close() { client.setScreen(parent); }
}

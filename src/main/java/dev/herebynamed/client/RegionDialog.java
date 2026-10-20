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
    private TextFieldWidget name;
    private int selectedColor, top;
    private String error = "";
    RegionDialog(Screen parent, RegionBook.Region region, BiConsumer<String,Integer> save) {
        super(Text.literal(region == null ? "新增命名区域" : "修改区域名称与颜色"));
        this.parent = parent; this.region = region; this.save = save;
        selectedColor=region==null?0x66bbdd:region.color();
    }
    @Override protected void init() {
        int x = width/2-110; top=Math.max(8,(height-220)/2);
        String previousName=name==null?(region==null?"":region.name()):name.getText();
        name = new TextFieldWidget(textRenderer, x, top+30, 220, 18, Text.literal("区域名称")); name.setMaxLength(80);
        name.setText(previousName);
        addDrawableChild(name);
        addDrawableChild(new RegionColorPicker(x,top+74,220,selectedColor,color -> selectedColor=color));
        setInitialFocus(name);
        addDrawableChild(ButtonWidget.builder(Text.literal("保存"), button -> {
            try {
                String text = name.getText().strip();
                if (text.isBlank() || text.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("请输入有效区域名");
                save.accept(text, selectedColor); close();
            } catch (IllegalArgumentException ex) { error = ex.getMessage(); }
        }).dimensions(x,top+181,106,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("取消"), button -> close()).dimensions(x+114,top+181,106,20).build());
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        ClientUi.background(this,context,mouseX,mouseY,delta); context.drawCenteredTextWithShadow(textRenderer, title, width/2,top,0xffffff);
        context.drawTextWithShadow(textRenderer,"区域名称",width/2-110,top+18,0xdddddd);
        context.drawTextWithShadow(textRenderer,"标记颜色 · 点击色板或拖动调色",width/2-110,top+57,0xdddddd);
        context.fill(width/2+88,top+54,width/2+110,top+68,0xff000000|selectedColor);
        super.render(context,mouseX,mouseY,delta);
        context.drawCenteredTextWithShadow(textRenderer,error,width/2,top+209,0xff7777);
    }
    @Override public void close() { client.setScreen(parent); }
}

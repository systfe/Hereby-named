package dev.herebynamed.client;

import dev.herebynamed.domain.RegionBook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.BiConsumer;

final class RegionDialog extends Screen implements EditorDialog {
    private final Screen parent;
    private final RegionBook.Region region;
    private final BiConsumer<String,Integer> save;
    private EditBox name;
    private int selectedColor, top;
    private String error = "";
    RegionDialog(Screen parent, RegionBook.Region region, BiConsumer<String,Integer> save) {
        super(Component.literal(region == null ? "新增命名区域" : "修改区域名称与颜色"));
        this.parent = parent; this.region = region; this.save = save;
        selectedColor=region==null?0x66bbdd:region.color();
    }
    @Override protected void init() {
        int x = width/2-110; top=Math.max(8,(height-220)/2);
        String previousName=name==null?(region==null?"":region.name()):name.getValue();
        name = new EditBox(font, x, top+30, 220, 18, Component.literal("区域名称")); name.setMaxLength(80);
        name.setValue(previousName);
        addRenderableWidget(name);
        addRenderableWidget(new RegionColorPicker(x,top+74,220,selectedColor,color -> selectedColor=color));
        setInitialFocus(name);
        addRenderableWidget(Button.builder(Component.literal("保存"), button -> {
            try {
                String text = name.getValue().strip();
                if (text.isBlank() || text.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("请输入有效区域名");
                save.accept(text, selectedColor); onClose();
            } catch (IllegalArgumentException ex) { error = ex.getMessage(); }
        }).bounds(x,top+181,106,20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), button -> onClose()).bounds(x+114,top+181,106,20).build());
    }
    @Override public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        ClientUi.background(this,context,mouseX,mouseY,delta);
        super.render(context,mouseX,mouseY,delta);
        context.drawCenteredString(font, title, width/2,top,0xffffff);
        context.drawString(font,"区域名称",width/2-110,top+18,0xdddddd);
        context.drawString(font,"标记颜色 · 点击色板或拖动调色",width/2-110,top+57,0xdddddd);
        context.fill(width/2+88,top+54,width/2+110,top+68,0xff000000|selectedColor);
        context.drawCenteredString(font,error,width/2,top+209,0xff7777);
    }
    @Override public void onClose() { if(minecraft!=null) minecraft.setScreen(parent); }
}

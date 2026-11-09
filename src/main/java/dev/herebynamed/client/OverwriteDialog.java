package dev.herebynamed.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** Escape cancels the whole selection; neither choice is submitted before a click. */
final class OverwriteDialog extends Screen implements EditorDialog {
    private final Screen parent;
    private final int conflicts;
    private final Consumer<Boolean> submit;
    OverwriteDialog(Screen parent,int conflicts,Consumer<Boolean> submit) {
        super(Component.literal("群系选择与其他区域重叠"));
        this.parent=parent; this.conflicts=conflicts; this.submit=submit;
    }
    @Override protected void init() {
        int buttonWidth=Math.min(180,(width-30)/2), y=height/2+16;
        addRenderableWidget(Button.builder(Component.literal("不覆盖其他区域"),b -> choose(false)).bounds(width/2-buttonWidth-5,y,buttonWidth,20).build());
        addRenderableWidget(Button.builder(Component.literal("覆盖其他区域"),b -> choose(true)).bounds(width/2+5,y,buttonWidth,20).build());
    }
    private void choose(boolean overwrite) { if(minecraft!=null) minecraft.setScreen(parent); submit.accept(overwrite); }
    @Override public void onClose() { if(minecraft!=null) minecraft.setScreen(parent); }
    @Override public void render(GuiGraphics context,int mouseX,int mouseY,float delta) {
        ClientUi.background(this,context,mouseX,mouseY,delta);
        super.render(context,mouseX,mouseY,delta);
        context.drawCenteredString(font,title,width/2,height/2-42,0xffffff);
        context.drawCenteredString(font,"将涉及同图层其他区域的 "+conflicts+" 个区块",width/2,height/2-22,0xdddddd);
        context.drawCenteredString(font,"Esc 取消本次选择",width/2,height/2+46,0xaaaaaa);
    }
}

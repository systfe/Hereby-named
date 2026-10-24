package dev.herebynamed.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.function.Consumer;

/** Escape cancels the whole selection; neither choice is submitted before a click. */
final class OverwriteDialog extends Screen implements EditorDialog {
    private final Screen parent;
    private final int conflicts;
    private final Consumer<Boolean> submit;
    OverwriteDialog(Screen parent,int conflicts,Consumer<Boolean> submit) {
        super(Text.literal("群系选择与其他区域重叠"));
        this.parent=parent; this.conflicts=conflicts; this.submit=submit;
    }
    @Override protected void init() {
        int buttonWidth=Math.min(180,(width-30)/2), y=height/2+16;
        addDrawableChild(ButtonWidget.builder(Text.literal("不覆盖其他区域"),b -> choose(false)).dimensions(width/2-buttonWidth-5,y,buttonWidth,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("覆盖其他区域"),b -> choose(true)).dimensions(width/2+5,y,buttonWidth,20).build());
    }
    private void choose(boolean overwrite) { client.setScreen(parent); submit.accept(overwrite); }
    @Override public void close() { client.setScreen(parent); }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        ClientUi.background(this,context,mouseX,mouseY,delta);
        super.render(context,mouseX,mouseY,delta);
        context.drawCenteredTextWithShadow(textRenderer,title,width/2,height/2-42,0xffffff);
        context.drawCenteredTextWithShadow(textRenderer,"将涉及同图层其他区域的 "+conflicts+" 个区块",width/2,height/2-22,0xdddddd);
        context.drawCenteredTextWithShadow(textRenderer,"Esc 取消本次选择",width/2,height/2+46,0xaaaaaa);
    }
}

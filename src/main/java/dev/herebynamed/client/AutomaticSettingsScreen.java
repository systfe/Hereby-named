package dev.herebynamed.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

final class AutomaticSettingsScreen extends Screen implements EditorDialog {
    private final Screen parent;
    private final AutomaticRegions automatic;
    private int top;
    private boolean wasReady;
    AutomaticSettingsScreen(Screen parent,AutomaticRegions automatic) {
        super(Text.literal("自动创建区域"));this.parent=parent;this.automatic=automatic;
    }
    @Override protected void init() {
        wasReady=automatic.ready();
        top=Math.max(8,(height-236)/2);int x=width/2-120;
        addDrawableChild(ButtonWidget.builder(Text.literal(automatic.enabled()?"自动创建：开启":"自动创建：关闭"),b -> {
            automatic.toggle();b.setMessage(Text.literal(automatic.enabled()?"自动创建：开启":"自动创建：关闭"));
        }).dimensions(x,top+68,240,20).build()).active=automatic.ready();
        button(x,top+94,116,"完整名字白名单",() -> client.setScreen(new AutomaticConfigScreen(this,automatic,2))).active=wasReady;
        button(x+124,top+94,116,"词根白名单",() -> client.setScreen(new AutomaticConfigScreen(this,automatic,3))).active=wasReady;
        button(x,top+120,116,"区域判断设置",() -> client.setScreen(new AutomaticConfigScreen(this,automatic,0))).active=wasReady;
        button(x+124,top+120,116,"重新加载配置",() -> { automatic.reload();clearAndInit(); });
        button(x,top+146,116,"重新分析",() -> confirm("重新分析已探索地表？手动区域与删除保护会保留。",automatic::reanalyze));
        button(x+124,top+146,116,"允许删除处重建",() -> confirm("解除全部已删除自动区域的重建保护？此操作不会删除现有区域。",automatic::allowAgain))
                .active=HerebyNamedClient.REGIONS.automaticAllowed();
        button(x,top+194,240,"完成",this::close);
    }
    @Override public void tick() {
        HerebyNamedClient.REGIONS.busy();automatic.tick();
        if(wasReady!=automatic.ready()) clearAndInit();
    }
    private ButtonWidget button(int x,int y,int width,String label,Runnable action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label),b -> action.run()).dimensions(x,y,width,20).build());
    }
    private void confirm(String text,Runnable action) { client.setScreen(new Confirmation(this,text,action)); }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        ClientUi.background(this,context,mouseX,mouseY,delta);super.render(context,mouseX,mouseY,delta);
        context.drawCenteredTextWithShadow(textRenderer,title,width/2,top,0xffffff);
        context.drawCenteredTextWithShadow(textRenderer,"仅主世界地表 · 在游戏内编辑设置",width/2,top+20,0xbbbbbb);
        context.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(automatic.status(),Math.max(60,width-30)),width/2,top+35,0xc6dfcc);
        context.drawCenteredTextWithShadow(textRenderer,"已记录 "+automatic.sampleCount()+" 区块 · 待命名 "+automatic.unnamedCount(),width/2,top+49,0xbbbbbb);
        context.drawCenteredTextWithShadow(textRenderer,"在设置中保存即可生效，无需打开配置文件。",width/2,top+177,0xaaaaaa);
    }
    @Override public void close() { client.setScreen(parent); }
    private static final class Confirmation extends ConfirmScreen implements EditorDialog {
        Confirmation(Screen parent,String text,Runnable action) {
            super(yes -> { if(yes) action.run();net.minecraft.client.MinecraftClient.getInstance().setScreen(parent); },
                    Text.literal("确认操作"),Text.literal(text),Text.literal("确认"),Text.literal("取消"));
        }
    }
}

package dev.herebynamed.client;

import dev.herebynamed.platform.PlatformHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class AutomaticSettingsScreen extends Screen implements EditorDialog {
    private final Screen parent;
    private final AutomaticRegions automatic;
    private int top;
    private boolean wasReady;
    AutomaticSettingsScreen(Screen parent,AutomaticRegions automatic) {
        super(Component.literal("自动创建区域"));this.parent=parent;this.automatic=automatic;
    }
    @Override protected void init() {
        wasReady=automatic.ready();
        top=Math.max(8,(height-236)/2);int x=width/2-120;
        addRenderableWidget(Button.builder(Component.literal(automatic.enabled()?"自动创建：开启":"自动创建：关闭"),b -> {
            automatic.toggle();b.setMessage(Component.literal(automatic.enabled()?"自动创建：开启":"自动创建：关闭"));
        }).bounds(x,top+68,240,20).build()).active=automatic.ready();
        button(x,top+94,116,"默认名白名单",() -> { if(minecraft!=null) minecraft.setScreen(new AutomaticConfigScreen(this,automatic,2)); }).active=wasReady;
        button(x+124,top+94,116,"词根白名单",() -> { if(minecraft!=null) minecraft.setScreen(new AutomaticConfigScreen(this,automatic,3)); }).active=wasReady;
        button(x,top+120,116,"区域判断设置",() -> { if(minecraft!=null) minecraft.setScreen(new AutomaticConfigScreen(this,automatic,0)); }).active=wasReady;
        button(x+124,top+120,116,"重新加载配置",() -> { automatic.reload();rebuildWidgets(); });
        button(x,top+146,116,"重新分析",() -> confirm("重新分析已探索地表？手动区域与删除保护会保留。",automatic::reanalyze));
        button(x+124,top+146,116,"允许删除处重建",() -> confirm("解除全部已删除自动区域的重建保护？此操作不会删除现有区域。",automatic::allowAgain))
                .active=HerebyNamedClient.REGIONS.automaticAllowed();
        button(x,top+170,240,"地图字体设置：" + fontSummary(), () -> { if(minecraft!=null) minecraft.setScreen(new FontSettingsScreen(this,automatic)); }).active=wasReady;
        button(x,top+196,240,"完成",this::onClose);
    }
    private String fontSummary() {
        if (automatic == null || automatic.config() == null) return "地图衬线体";
        String f = automatic.config().mapFont;
        if (f == null || f.isBlank() || "hereby_named:map_font".equals(f)) return "思源宋体 / Cinzel";
        if ("minecraft:default".equals(f)) return "原版字体";
        return f;
    }
    @Override public void tick() {
        HerebyNamedClient.REGIONS.busy();automatic.tick();
        if(wasReady!=automatic.ready()) rebuildWidgets();
    }
    private Button button(int x,int y,int width,String label,Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label),b -> action.run()).bounds(x,y,width,20).build());
    }
    private void confirm(String text,Runnable action) { if(minecraft!=null) minecraft.setScreen(new Confirmation(this,text,action)); }
    @Override public void render(GuiGraphics context,int mouseX,int mouseY,float delta) {
        PlatformHelper.get().renderBackground(this,context,mouseX,mouseY,delta);super.render(context,mouseX,mouseY,delta);
        context.drawCenteredString(font,title,width/2,top,0xffffff);
        context.drawCenteredString(font,"仅主世界地表 · 在游戏内编辑设置",width/2,top+20,0xbbbbbb);
        context.drawCenteredString(font,font.plainSubstrByWidth(automatic.status(),Math.max(60,width-30)),width/2,top+35,0xc6dfcc);
        context.drawCenteredString(font,"已记录 "+automatic.sampleCount()+" 区块 · 待命名 "+automatic.unnamedCount(),width/2,top+49,0xbbbbbb);
        context.drawCenteredString(font,"在设置中保存即可生效，无需打开配置文件。",width/2,top+220,0xaaaaaa);
    }
    @Override public void onClose() { if(minecraft!=null) minecraft.setScreen(parent); }
    private static final class Confirmation extends ConfirmScreen implements EditorDialog {
        Confirmation(Screen parent,String text,Runnable action) {
            super(yes -> { if(yes) action.run(); Minecraft.getInstance().setScreen(parent); },
                    Component.literal("确认操作"), Component.literal(text), Component.literal("确认"), Component.literal("取消"));
        }
    }
}

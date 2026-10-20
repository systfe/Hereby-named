package dev.herebynamed.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

final class ClientUi {
    static void registerTitles(RegionTitles titles) {
        HudRenderCallback.EVENT.register((context,delta) -> { if(MinecraftClient.getInstance().currentScreen==null) titles.render(context,delta); });
    }
    static void background(Screen screen,DrawContext context,int mouseX,int mouseY,float delta) { screen.renderBackground(context); }
    private ClientUi() {}
}

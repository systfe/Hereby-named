package dev.herebynamed.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

public final class ClientUi {
    public static void registerTitles(RegionTitles titles) {
        HudRenderCallback.EVENT.register((context,counter) -> { if(Minecraft.getInstance().screen==null) titles.render(context,counter.getGameTimeDeltaPartialTick(false)); });
    }
    public static void background(Screen screen,GuiGraphics context,int mouseX,int mouseY,float delta) { screen.renderBackground(context,mouseX,mouseY,delta); }
    private ClientUi() {}
}

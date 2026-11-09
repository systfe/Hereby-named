package dev.herebynamed.fabric;

import dev.herebynamed.client.EditorDialog;
import dev.herebynamed.client.MapController;
import dev.herebynamed.platform.PlatformHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import xaero.map.gui.GuiMap;

import java.nio.file.Path;

public final class FabricPlatformHelper implements PlatformHelper {
    private static MapController active;

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean canSendToServer() {
        return dev.herebynamed.client.ClientTransport.canSend();
    }

    @Override
    public void sendToServer(FriendlyByteBuf packet) {
        dev.herebynamed.client.ClientTransport.send(packet);
    }

    @Override
    public boolean canSendToPlayer(ServerPlayer player) {
        return dev.herebynamed.net.ServerTransport.canSend(player);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, FriendlyByteBuf packet) {
        dev.herebynamed.net.ServerTransport.send(player, packet);
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.screens.Screen screen, net.minecraft.client.gui.GuiGraphics context, int mouseX, int mouseY, float delta) {
        dev.herebynamed.client.ClientUi.background(screen, context, mouseX, mouseY, delta);
    }

    @Override
    public void registerTitles(dev.herebynamed.client.RegionTitles titles) {
        dev.herebynamed.client.ClientUi.registerTitles(titles);
    }

    @Override
    public void initMapIntegration() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof GuiMap map)) return;
            if (active == null || active.screen() != map) {
                if (active != null) active.close();
                active = new MapController(map);
            }
            MapController controller = active;
            controller.build();
            ScreenEvents.afterRender(screen).register((s, context, mx, my, delta) -> controller.render(context, mx, my));
            ScreenEvents.afterTick(screen).register(s -> controller.tick());
            ScreenMouseEvents.allowMouseClick(screen).register((s, x, y, button) -> controller.mouseDown(x, y, button));
            ScreenMouseEvents.allowMouseRelease(screen).register((s, x, y, button) -> controller.mouseUp(x, y, button));
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, scan, mods) -> controller.key(key, mods));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (active != null && client.screen != active.screen() && !(client.screen instanceof EditorDialog)) {
                active.close();
                active = null;
            }
        });
    }
}

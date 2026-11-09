package dev.herebynamed.forge;

import dev.herebynamed.client.EditorDialog;
import dev.herebynamed.client.HerebyNamedClient;
import dev.herebynamed.client.MapController;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import xaero.map.gui.GuiMap;

public final class HerebyNamedForgeClient {
    private static MapController active;

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(HerebyNamedForgeClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(HerebyNamedForgeClient::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(HerebyNamedForgeClient::onLoggingOut);
        MinecraftForge.EVENT_BUS.addListener(HerebyNamedForgeClient::onRenderOverlay);

        HerebyNamedClient.initialize();
    }

    public static void initMapIntegration() {
        MinecraftForge.EVENT_BUS.addListener(HerebyNamedForgeClient::onScreenInit);
        MinecraftForge.EVENT_BUS.addListener(HerebyNamedForgeClient::onScreenRender);
        MinecraftForge.EVENT_BUS.addListener(HerebyNamedForgeClient::onScreenMouseClick);
        MinecraftForge.EVENT_BUS.addListener(HerebyNamedForgeClient::onScreenMouseRelease);
        MinecraftForge.EVENT_BUS.addListener(HerebyNamedForgeClient::onScreenKeyPressed);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        HerebyNamedClient.reset();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        HerebyNamedClient.reset();
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft client = Minecraft.getInstance();
            HerebyNamedClient.tick(client);
            if (active != null && client.screen != active.screen() && !(client.screen instanceof EditorDialog)) {
                active.close();
                active = null;
            }
            if (active != null) {
                active.tick();
            }
        }
    }

    private static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (Minecraft.getInstance().screen == null && VanillaGuiOverlay.CHAT_PANEL.id().equals(event.getOverlay().id())) {
            HerebyNamedClient.TITLES.render(event.getGuiGraphics(), event.getPartialTick());
        }
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof GuiMap map)) return;
        if (active == null || active.screen() != map) {
            if (active != null) active.close();
            active = new MapController(map);
        }
        active.build();
    }

    private static void onScreenRender(ScreenEvent.Render.Post event) {
        if (active != null && active.screen() == event.getScreen()) {
            active.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        }
    }

    private static void onScreenMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (active != null && active.screen() == event.getScreen()) {
            boolean handled = !active.mouseDown(event.getMouseX(), event.getMouseY(), event.getButton());
            if (handled) event.setCanceled(true);
        }
    }

    private static void onScreenMouseRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (active != null && active.screen() == event.getScreen()) {
            boolean handled = !active.mouseUp(event.getMouseX(), event.getMouseY(), event.getButton());
            if (handled) event.setCanceled(true);
        }
    }

    private static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (active != null && active.screen() == event.getScreen()) {
            boolean handled = !active.key(event.getKeyCode(), event.getModifiers());
            if (handled) event.setCanceled(true);
        }
    }

    private HerebyNamedForgeClient() {}
}

package dev.herebynamed.neoforge;

import dev.herebynamed.client.EditorDialog;
import dev.herebynamed.client.HerebyNamedClient;
import dev.herebynamed.client.MapController;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import xaero.map.gui.GuiMap;

public final class HerebyNamedNeoForgeClient {
    private static MapController active;

    public static void init(IEventBus modBus) {
        modBus.addListener(HerebyNamedNeoForgeClient::onRegisterGuiLayers);

        NeoForge.EVENT_BUS.addListener(HerebyNamedNeoForgeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(HerebyNamedNeoForgeClient::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(HerebyNamedNeoForgeClient::onLoggingOut);

        HerebyNamedClient.initialize();
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath("hereby_named", "titles"),
                (guiGraphics, deltaTracker) -> {
                    if (Minecraft.getInstance().screen == null) {
                        HerebyNamedClient.TITLES.render(guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(false));
                    }
                });
    }

    public static void initMapIntegration() {
        NeoForge.EVENT_BUS.addListener(HerebyNamedNeoForgeClient::onScreenInit);
        NeoForge.EVENT_BUS.addListener(HerebyNamedNeoForgeClient::onScreenRender);
        NeoForge.EVENT_BUS.addListener(HerebyNamedNeoForgeClient::onScreenMouseClick);
        NeoForge.EVENT_BUS.addListener(HerebyNamedNeoForgeClient::onScreenMouseRelease);
        NeoForge.EVENT_BUS.addListener(HerebyNamedNeoForgeClient::onScreenKeyPressed);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        HerebyNamedClient.reset();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        HerebyNamedClient.reset();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
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

    private HerebyNamedNeoForgeClient() {}
}

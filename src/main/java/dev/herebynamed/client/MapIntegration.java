package dev.herebynamed.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.*;
import xaero.map.gui.GuiMap;

final class MapIntegration {
    private static MapController active;
    static void initialize() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof GuiMap map)) return;
            if (active == null || active.screen() != map) { if (active != null) active.close(); active = new MapController(map); }
            MapController controller = active; controller.build();
            ScreenEvents.afterRender(screen).register((s, context, mx, my, delta) -> controller.render(context,mx,my));
            ScreenEvents.afterTick(screen).register(s -> controller.tick());
            ScreenMouseEvents.allowMouseClick(screen).register((s,x,y,button) -> controller.mouseDown(x,y,button));
            ScreenMouseEvents.allowMouseRelease(screen).register((s,x,y,button) -> controller.mouseUp(x,y,button));
            ScreenKeyboardEvents.allowKeyPress(screen).register((s,key,scan,mods) -> controller.key(key,mods));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (active != null && client.currentScreen != active.screen() && !(client.currentScreen instanceof EditorDialog)) {
                active.close(); active = null;
            }
        });
    }
}

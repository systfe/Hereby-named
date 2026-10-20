package dev.herebynamed.client;

import dev.herebynamed.HerebyNamed;
import dev.herebynamed.net.Wire;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

public final class HerebyNamedClient implements ClientModInitializer {
    public static final ClientRegions REGIONS = new ClientRegions();
    private final RegionTitles titles = new RegionTitles();
    @Override public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(Wire.CHANNEL, (client, handler, buf, sender) -> {
            try { Wire.Fragment fragment = Wire.Fragment.read(buf); client.execute(() -> REGIONS.receive(fragment)); }
            catch (RuntimeException ex) { HerebyNamed.LOG.warn("Invalid region snapshot fragment", ex); }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> { REGIONS.reset(); titles.reset(); });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { REGIONS.reset(); titles.reset(); });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            REGIONS.busy();
            if (client.world != null && client.player != null) {
                String dim = client.world.getRegistryKey().getValue().toString();
                titles.tick(dim, REGIONS.book(dim).namesAt(new dev.herebynamed.domain.ChunkCell(client.player.getBlockX() >> 4, client.player.getBlockZ() >> 4)));
            }
        });
        HudRenderCallback.EVENT.register((context, delta) -> { if (MinecraftClient.getInstance().currentScreen == null) titles.render(context, delta); });
        if (FabricLoader.getInstance().isModLoaded("xaeroworldmap")) MapIntegration.initialize();
        else HerebyNamed.LOG.warn("Xaero's World Map absent: shared regions remain available, map editing is disabled");
    }
}

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
    public static AutomaticRegions AUTOMATIC;
    private final RegionTitles titles = new RegionTitles();
    private dev.herebynamed.domain.ChunkCell lastCell;
    private String lastDimension="";
    private dev.herebynamed.domain.RegionBook lastBook;
    @Override public void onInitializeClient() {
        ClientTransport.initialize(REGIONS::receive);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            REGIONS.busy();
            if(AUTOMATIC!=null) AUTOMATIC.tick();
            if (client.world != null && client.player != null) {
                String dim = client.world.getRegistryKey().getValue().toString();
                var cell=new dev.herebynamed.domain.ChunkCell(client.player.getBlockX()>>4,client.player.getBlockZ()>>4);
                var book=REGIONS.book(dim);var names=book.namesAt(cell);
                if(dim.equals(lastDimension) && dev.herebynamed.domain.TitleQueue.automaticCreationAround(lastBook,book,lastCell,cell))
                    titles.observeWithoutTitle(dim,names);
                titles.tick(dim,names);lastCell=cell;lastDimension=dim;lastBook=book.snapshot();
            }
        });
        ClientUi.registerTitles(titles);
        if (FabricLoader.getInstance().isModLoaded("xaeroworldmap")) { AUTOMATIC=new AutomaticRegions();MapIntegration.initialize(); }
        else HerebyNamed.LOG.warn("Xaero's World Map absent: shared regions remain available, map editing is disabled");
    }
    private void reset() {
        REGIONS.reset();titles.reset();lastCell=null;lastDimension="";lastBook=null;
        if(AUTOMATIC!=null) AUTOMATIC.reset();
    }
}

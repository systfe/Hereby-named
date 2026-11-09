package dev.herebynamed.client;

import dev.herebynamed.HerebyNamed;
import dev.herebynamed.domain.ChunkCell;
import dev.herebynamed.domain.RegionBook;
import dev.herebynamed.domain.TitleQueue;
import dev.herebynamed.platform.PlatformHelper;
import net.minecraft.client.Minecraft;

public final class HerebyNamedClient {
    public static final ClientRegions REGIONS = new ClientRegions();
    public static AutomaticRegions AUTOMATIC;
    public static final RegionTitles TITLES = new RegionTitles();

    private static ChunkCell lastCell;
    private static String lastDimension = "";
    private static RegionBook lastBook;

    public static void initialize() {
        if (PlatformHelper.get().isModLoaded("xaeroworldmap")) {
            AUTOMATIC = new AutomaticRegions();
            PlatformHelper.get().initMapIntegration();
        } else {
            HerebyNamed.LOG.warn("Xaero's World Map absent: shared regions remain available, map editing is disabled");
        }
    }

    public static void reset() {
        REGIONS.reset();
        TITLES.reset();
        lastCell = null;
        lastDimension = "";
        lastBook = null;
        if (AUTOMATIC != null) AUTOMATIC.reset();
    }

    public static void tick(Minecraft client) {
        REGIONS.busy();
        if (AUTOMATIC != null) AUTOMATIC.tick();
        if (client.level != null && client.player != null) {
            String dim = client.level.dimension().location().toString();
            var cell = new ChunkCell(client.player.getBlockX() >> 4, client.player.getBlockZ() >> 4);
            var book = REGIONS.book(dim);
            var names = book.namesAt(cell);
            if (dim.equals(lastDimension) && TitleQueue.automaticCreationAround(lastBook, book, lastCell, cell)) {
                TITLES.observeWithoutTitle(dim, names);
            }
            TITLES.tick(dim, names);
            lastCell = cell;
            lastDimension = dim;
            lastBook = book.snapshot();
        }
    }

    private HerebyNamedClient() {}
}

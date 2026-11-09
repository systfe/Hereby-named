package dev.herebynamed.platform;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;

public final class FabricPlatformHelper implements PlatformHelper {
    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}

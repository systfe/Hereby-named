package dev.herebynamed.platform;

import java.nio.file.Path;
import java.util.ServiceLoader;

public interface PlatformHelper {
    Path configDir();
    boolean isModLoaded(String modId);

    static PlatformHelper get() {
        return Holder.INSTANCE;
    }

    static void init(PlatformHelper helper) {
        Holder.INSTANCE = helper;
    }

    class Holder {
        private static PlatformHelper INSTANCE = ServiceLoader.load(PlatformHelper.class)
                .findFirst().orElse(null);
    }
}

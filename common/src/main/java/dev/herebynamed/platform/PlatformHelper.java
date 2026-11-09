package dev.herebynamed.platform;

import dev.herebynamed.client.RegionTitles;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import java.nio.file.Path;
import java.util.ServiceLoader;

public interface PlatformHelper {
    Path configDir();
    boolean isModLoaded(String modId);

    boolean canSendToServer();
    void sendToServer(FriendlyByteBuf packet);

    boolean canSendToPlayer(ServerPlayer player);
    void sendToPlayer(ServerPlayer player, FriendlyByteBuf packet);

    default void initMapIntegration() {}
    default void registerTitles(RegionTitles titles) {}
    void renderBackground(Screen screen, GuiGraphics context, int mouseX, int mouseY, float delta);

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

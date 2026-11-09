package dev.herebynamed.neoforge;

import dev.herebynamed.platform.PlatformHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class NeoForgePlatformHelper implements PlatformHelper {
    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean canSendToServer() {
        return NeoForgeTransport.canSendToServer();
    }

    @Override
    public void sendToServer(FriendlyByteBuf packet) {
        NeoForgeTransport.sendToServer(packet);
    }

    @Override
    public boolean canSendToPlayer(ServerPlayer player) {
        return NeoForgeTransport.canSendToPlayer(player);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, FriendlyByteBuf packet) {
        NeoForgeTransport.sendToPlayer(player, packet);
    }

    @Override
    public void renderBackground(Screen screen, GuiGraphics context, int mouseX, int mouseY, float delta) {
        screen.renderBackground(context, mouseX, mouseY, delta);
    }

    @Override
    public void initMapIntegration() {
        HerebyNamedNeoForgeClient.initMapIntegration();
    }
}

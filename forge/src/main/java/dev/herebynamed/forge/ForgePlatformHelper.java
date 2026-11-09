package dev.herebynamed.forge;

import dev.herebynamed.platform.PlatformHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class ForgePlatformHelper implements PlatformHelper {
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
        return ForgeTransport.canSendToServer();
    }

    @Override
    public void sendToServer(FriendlyByteBuf packet) {
        ForgeTransport.sendToServer(packet);
    }

    @Override
    public boolean canSendToPlayer(ServerPlayer player) {
        return ForgeTransport.canSendToPlayer(player);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, FriendlyByteBuf packet) {
        ForgeTransport.sendToPlayer(player, packet);
    }

    @Override
    public void renderBackground(Screen screen, GuiGraphics context, int mouseX, int mouseY, float delta) {
        screen.renderBackground(context);
    }

    @Override
    public void initMapIntegration() {
        HerebyNamedForgeClient.initMapIntegration();
    }
}

package dev.herebynamed.fabric;

import dev.herebynamed.claim.ClaimProtectionEngine;
import dev.herebynamed.net.ServerTransport;
import dev.herebynamed.platform.PlatformHelper;
import dev.herebynamed.server.HerebyNamedServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;

public final class HerebyNamedFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PlatformHelper.init(new FabricPlatformHelper());
        ServerTransport.initialize(HerebyNamedServer::receive);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                HerebyNamedServer.onJoin(server, handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                HerebyNamedServer.onDisconnect(server, handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(HerebyNamedServer::onStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(HerebyNamedServer::onStopped);
        ServerTickEvents.END_SERVER_TICK.register(HerebyNamedServer::onServerTick);
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                HerebyNamedServer.registerCommands(dispatcher));

        // Claim permission event guards
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer sp) return ClaimProtectionEngine.canBreak(sp, pos);
            return true;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);
            if (!ClaimProtectionEngine.canInteractBlock(sp, pos, state)) return InteractionResult.FAIL;
            if (player.getItemInHand(hand).getItem() instanceof BlockItem) {
                BlockPos placePos = pos.relative(hitResult.getDirection());
                if (!ClaimProtectionEngine.canPlace(sp, placePos)) return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (!ClaimProtectionEngine.canAttackEntity(sp, entity)) return InteractionResult.FAIL;
            return InteractionResult.PASS;
        });
    }
}

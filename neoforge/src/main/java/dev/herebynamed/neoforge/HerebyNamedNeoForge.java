package dev.herebynamed.neoforge;

import dev.herebynamed.platform.PlatformHelper;
import dev.herebynamed.server.HerebyNamedServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("hereby_named")
public final class HerebyNamedNeoForge {
    public HerebyNamedNeoForge(IEventBus modBus) {
        PlatformHelper.init(new NeoForgePlatformHelper());

        modBus.addListener(NeoForgeTransport::registerPayloadHandlers);

        NeoForge.EVENT_BUS.addListener(this::onCommandsRegister);
        NeoForge.EVENT_BUS.addListener(this::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLeave);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(this::onBlockPlace);
        NeoForge.EVENT_BUS.addListener(this::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(this::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(this::onExplosionDetonate);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            HerebyNamedNeoForgeClient.init(modBus);
        }
    }

    private void onCommandsRegister(RegisterCommandsEvent event) {
        HerebyNamedServer.registerCommands(event.getDispatcher());
    }

    private void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HerebyNamedServer.onJoin(player.getServer(), player);
        }
    }

    private void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HerebyNamedServer.onDisconnect(player.getServer(), player);
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        HerebyNamedServer.onStopping(event.getServer());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        HerebyNamedServer.onStopped(event.getServer());
    }

    private void onServerTick(ServerTickEvent.Post event) {
        HerebyNamedServer.onServerTick(event.getServer());
    }

    private void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer sp) {
            if (!dev.herebynamed.claim.ClaimProtectionEngine.canBreak(sp, event.getPos())) {
                event.setCanceled(true);
            }
        }
    }

    private void onBlockPlace(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            if (!dev.herebynamed.claim.ClaimProtectionEngine.canPlace(sp, event.getPos())) {
                event.setCanceled(true);
            }
        }
    }

    private void onRightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            if (!dev.herebynamed.claim.ClaimProtectionEngine.canInteractBlock(sp, event.getPos(), event.getLevel().getBlockState(event.getPos()))) {
                event.setCanceled(true);
            }
        }
    }

    private void onAttackEntity(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            if (!dev.herebynamed.claim.ClaimProtectionEngine.canAttackEntity(sp, event.getTarget())) {
                event.setCanceled(true);
            }
        }
    }

    private void onExplosionDetonate(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> !dev.herebynamed.claim.ClaimProtectionEngine.canExplode(event.getLevel(), pos));
        event.getAffectedEntities().removeIf(ent -> !dev.herebynamed.claim.ClaimProtectionEngine.canExplode(ent.level(), ent.blockPosition()));
    }
}

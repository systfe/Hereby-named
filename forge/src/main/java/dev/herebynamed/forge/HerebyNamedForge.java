package dev.herebynamed.forge;

import dev.herebynamed.client.HerebyNamedClient;
import dev.herebynamed.platform.PlatformHelper;
import dev.herebynamed.server.HerebyNamedServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("hereby_named")
public final class HerebyNamedForge {
    public HerebyNamedForge() {
        PlatformHelper.init(new ForgePlatformHelper());

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);

        MinecraftForge.EVENT_BUS.addListener(this::onCommandsRegister);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerJoin);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLeave);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onBlockBreak);
        MinecraftForge.EVENT_BUS.addListener(this::onBlockPlace);
        MinecraftForge.EVENT_BUS.addListener(this::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(this::onAttackEntity);
        MinecraftForge.EVENT_BUS.addListener(this::onExplosionDetonate);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> HerebyNamedForgeClient::init);
    }

    private void setup(final FMLCommonSetupEvent event) {
        ForgeTransport.initialize(HerebyNamedClient.REGIONS::receive, HerebyNamedServer::receive);
    }

    private void onCommandsRegister(RegisterCommandsEvent event) {
        HerebyNamedServer.registerCommands(event.getDispatcher());
    }

    private void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            HerebyNamedServer.onJoin(player.getServer(), player);
        }
    }

    private void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            HerebyNamedServer.onDisconnect(player.getServer(), player);
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        HerebyNamedServer.onStopping(event.getServer());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        HerebyNamedServer.onStopped(event.getServer());
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            HerebyNamedServer.onServerTick(event.getServer());
        }
    }

    private void onBlockBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp) {
            if (!dev.herebynamed.claim.ClaimProtectionEngine.canBreak(sp, event.getPos())) {
                event.setCanceled(true);
            }
        }
    }

    private void onBlockPlace(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            if (!dev.herebynamed.claim.ClaimProtectionEngine.canPlace(sp, event.getPos())) {
                event.setCanceled(true);
            }
        }
    }

    private void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            if (!dev.herebynamed.claim.ClaimProtectionEngine.canInteractBlock(sp, event.getPos(), event.getLevel().getBlockState(event.getPos()))) {
                event.setCanceled(true);
            }
        }
    }

    private void onAttackEntity(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            if (!dev.herebynamed.claim.ClaimProtectionEngine.canAttackEntity(sp, event.getTarget())) {
                event.setCanceled(true);
            }
        }
    }

    private void onExplosionDetonate(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> !dev.herebynamed.claim.ClaimProtectionEngine.canExplode(event.getLevel(), pos));
        event.getAffectedEntities().removeIf(ent -> !dev.herebynamed.claim.ClaimProtectionEngine.canExplode(ent.level(), ent.blockPosition()));
    }
}

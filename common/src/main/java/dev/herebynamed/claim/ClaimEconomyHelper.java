package dev.herebynamed.claim;

import dev.herebynamed.claim.model.ClaimEconomyConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ClaimEconomyHelper {
    public static int getRequiredCost(ServerPlayer player, int chunkCount, ClaimEconomyConfig.ClaimCost costConfig) {
        if (!costConfig.enabled()) return 0;
        if (costConfig.adminBypass() && player.hasPermissions(2)) return 0;
        return Math.max(0, costConfig.amountPerChunk() * chunkCount);
    }

    public static Item resolveItem(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return null;
        return BuiltInRegistries.ITEM.get(id);
    }

    public static int countItem(ServerPlayer player, Item item) {
        if (item == null) return 0;
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static boolean checkAndDeduct(ServerPlayer player, int chunkCount, ClaimEconomyConfig.ClaimCost costConfig) {
        int required = getRequiredCost(player, chunkCount, costConfig);
        if (required <= 0) return true;

        Item item = resolveItem(costConfig.item());
        if (item == null) {
            player.sendSystemMessage(Component.literal("§c[领地] 认领物品配置错误: " + costConfig.item()));
            return false;
        }

        int available = countItem(player, item);
        if (available < required) {
            player.sendSystemMessage(Component.literal("§c[领地] 圈地所需物资不足！需要 " + required + " 个 " + item.getDescription().getString() + "，你只有 " + available + " 个。"));
            return false;
        }

        int remaining = required;
        for (int i = 0; i < player.getInventory().items.size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (!stack.isEmpty() && stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        player.containerMenu.broadcastChanges();
        player.sendSystemMessage(Component.literal("§a[领地] 已支付 " + required + " 个 " + item.getDescription().getString() + " 作为圈地费用。"));
        return true;
    }

    public static void refund(ServerPlayer player, int chunkCount, ClaimEconomyConfig.ClaimCost costConfig) {
        if (!costConfig.enabled() || !costConfig.refundOnUnclaim()) return;
        if (costConfig.adminBypass() && player.hasPermissions(2)) return;
        int amount = costConfig.amountPerChunk() * chunkCount;
        if (amount <= 0) return;

        Item item = resolveItem(costConfig.item());
        if (item == null) return;

        ItemStack refundStack = new ItemStack(item, amount);
        if (!player.getInventory().add(refundStack)) {
            player.drop(refundStack, false);
        }
        player.containerMenu.broadcastChanges();
        player.sendSystemMessage(Component.literal("§e[领地] 已退还 " + amount + " 个 " + item.getDescription().getString() + "。"));
    }

    private ClaimEconomyHelper() {}
}

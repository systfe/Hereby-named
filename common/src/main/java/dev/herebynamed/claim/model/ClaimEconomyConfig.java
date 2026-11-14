package dev.herebynamed.claim.model;

public record ClaimEconomyConfig(
    ClaimCost claimCost,
    Limits limits,
    ForceLoad forceLoad
) {
    public record ClaimCost(
        boolean enabled,
        String item,
        int amountPerChunk,
        boolean adminBypass,
        boolean refundOnUnclaim
    ) {
        public static final ClaimCost DEFAULT = new ClaimCost(false, "minecraft:emerald", 1, true, false);
    }

    public record Limits(
        int maxClaimChunksPerPlayer,
        int maxForceLoadedChunksPerPlayer,
        int maxGlobalForceLoadedChunks
    ) {
        public static final Limits DEFAULT = new Limits(100, 4, 32);
    }

    public record ForceLoad(
        boolean enabled,
        String costItem,
        int costAmountPerChunk,
        boolean onlyWhileOnline
    ) {
        public static final ForceLoad DEFAULT = new ForceLoad(true, "minecraft:diamond", 1, true);
    }

    public static final ClaimEconomyConfig DEFAULT = new ClaimEconomyConfig(
        ClaimCost.DEFAULT,
        Limits.DEFAULT,
        ForceLoad.DEFAULT
    );
}

package dev.herebynamed.claim.model;

import java.util.*;

public final class ClaimFlag {
    public static final int BREAK = 1 << 0;
    public static final int PLACE = 1 << 1;
    public static final int INTERACT = 1 << 2;
    public static final int CONTAINER = 1 << 3;
    public static final int PVP = 1 << 4;
    public static final int ANIMAL_PROTECT = 1 << 5;
    public static final int EXPLOSION_PROOF = 1 << 6;
    public static final int FIRE_SPREAD = 1 << 7;
    public static final int ENTER = 1 << 8;

    public static final int DEFAULT_VISITOR = ENTER | EXPLOSION_PROOF | FIRE_SPREAD;
    public static final int DEFAULT_MEMBER = BREAK | PLACE | INTERACT | CONTAINER | ANIMAL_PROTECT | ENTER | EXPLOSION_PROOF | FIRE_SPREAD;
    public static final int DEFAULT_ENVIRONMENT = EXPLOSION_PROOF | FIRE_SPREAD;

    public static final Map<String, Integer> NAME_TO_BIT = Map.of(
            "break", BREAK,
            "place", PLACE,
            "interact", INTERACT,
            "container", CONTAINER,
            "pvp", PVP,
            "animal", ANIMAL_PROTECT,
            "explosion", EXPLOSION_PROOF,
            "fire", FIRE_SPREAD,
            "enter", ENTER
    );

    public static int parseFlag(String name) {
        if (name == null) return 0;
        return NAME_TO_BIT.getOrDefault(name.toLowerCase(Locale.ROOT), 0);
    }

    private ClaimFlag() {}
}

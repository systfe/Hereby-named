package dev.herebynamed.claim.model;

public enum ClaimRole {
    OWNER(3, "领主"),
    ADMIN(2, "管理员"),
    MEMBER(1, "信任成员"),
    VISITOR(0, "访客");

    private final int level;
    private final String display;

    ClaimRole(int level, String display) {
        this.level = level;
        this.display = display;
    }

    public boolean isAtLeast(ClaimRole other) {
        return this.level >= other.level;
    }

    public String getDisplayName() {
        return display;
    }
}

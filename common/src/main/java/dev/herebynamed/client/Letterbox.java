package dev.herebynamed.client;

import net.minecraft.client.gui.GuiGraphics;

/** Cinematic bars that slide in with the region title and retract after it ends. */
final class Letterbox {
    private static final int BLACK = 0xff000000;
    private float from, target, current;
    private long since, duration;
    private float height = 0.08f;
    private String curve = "brake";
    void show(int durationMs, float heightPercent, String curve) {
        this.height = Math.max(0f, heightPercent) / 100f;
        this.curve = curve;
        transition(1f, durationMs);
    }
    void hide(int durationMs, String curve) {
        this.curve = curve;
        transition(0f, durationMs);
    }
    void reset() { from = target = current = 0f; duration = 0; since = System.nanoTime(); }
    private void transition(float next, int durationMs) {
        if (target == next) return;
        current = progress();
        from = current; target = next; since = System.nanoTime();
        duration = Math.max(1, durationMs) * 1_000_000L;
    }
    private float progress() {
        if (duration <= 0) return current = target;
        float t = Math.min(1f, (System.nanoTime() - since) / (float) duration);
        return current = from + (target - from) * TitleVisuals.ease(t, curve);
    }
    /** Frame time is real time, so skipped frames or an open screen never stretch the animation. */
    void render(GuiGraphics context) {
        float shown = progress();
        if (shown <= 0.001f) return;
        int windowHeight = context.guiHeight();
        int bar = Math.round(windowHeight * height * shown);
        if (bar <= 0) return;
        context.fill(0, 0, context.guiWidth(), bar, BLACK);
        context.fill(0, windowHeight - bar, context.guiWidth(), windowHeight, BLACK);
    }
}

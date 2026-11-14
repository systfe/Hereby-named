package dev.herebynamed.client;

import dev.herebynamed.HerebyNamed;
import dev.herebynamed.domain.TitleQueue;
import dev.herebynamed.platform.PlatformHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import java.lang.reflect.*;
import java.util.*;

/** Copies Traveler's actual renderer styles without replacing its dimension/biome titles. */
public final class RegionTitles {
    private TitleQueue queue = new TitleQueue();
    private Travelers travelers;
    private boolean checked;
    private List<String> displayed = List.of(), observed = List.of();
    private String dimension = "";
    private int remaining;
    private final Letterbox letterbox = new Letterbox();
    private final TitleSound sound = new TitleSound();
    private final TitleVisuals visuals = new TitleVisuals();
    private long shownAt;

    public void reset() {
        queue = new TitleQueue(); displayed = observed = List.of(); remaining = 0; dimension = ""; shownAt = 0;
        letterbox.reset(); sound.reset(); sound.load(); visuals.load();
    }

    public void observeWithoutTitle(String dimension, List<String> names) {
        queue.observeWithoutTitle(dimension, names); this.dimension = dimension; observed = List.copyOf(names); remaining = 0;
        if (travelers != null) travelers.clear();
        letterbox.hide(visuals.config().letterboxMs, visuals.config().curve);
    }

    public void tick(String dimension, List<String> names) {
        if (!checked) {
            checked = true;
            sound.load();
            visuals.load();
            if (PlatformHelper.get().isModLoaded("travelerstitles")) try { travelers = new Travelers(); }
            catch (ReflectiveOperationException | LinkageError ex) { HerebyNamed.LOG.warn("Traveler's Titles bridge unavailable; using built-in titles", ex); }
        }
        long now = System.nanoTime() / 1_000_000L;
        if (!this.dimension.equals(dimension) || !observed.equals(names)) { remaining = 0; if (travelers != null) travelers.clear(); }
        this.dimension = dimension; observed = List.copyOf(names);
        boolean busy = originalBusy();
        if (!busy) {
            if (remaining > 0) remaining--;
            if (travelers != null) travelers.tick();
        }
        if (travelers != null && remaining <= 0 && !travelers.showing()) {
            letterbox.hide(visuals.config().letterboxMs, visuals.config().curve);
        } else if (travelers == null && remaining <= 0) {
            letterbox.hide(visuals.config().letterboxMs, visuals.config().curve);
        }
        sound.tick(now);
        TitleVisuals.Config config = visuals.config();
        queue.update(dimension, names, now,
                busy || Minecraft.getInstance().screen != null,
                config.repeatCooldownMs, config.switchCooldownMs).ifPresent(next -> {
            displayed = next; remaining = 100; shownAt = System.nanoTime();
            boolean shown = travelers == null || travelers.show(next);
            if (!shown) return;
            letterbox.show(config.letterboxMs, config.letterboxPercent, config.curve);
            if (travelers != null || sound.builtInTitles()) sound.arm(now);
        });
    }

    /** Entrance settle: the line starts slightly larger and lower, then contracts into place. */
    void pushEntrance(GuiGraphics context, int line) {
        TitleVisuals.Config config = visuals.config();
        context.pose().pushPose();
        if ("none".equals(config.entranceMode) || config.entranceMs <= 0 || shownAt == 0) return;
        float progress = entranceProgress(line);
        if (progress >= 1f) return;
        float scale = "rise".equals(config.entranceMode) ? 1f : config.entranceScale - (config.entranceScale - 1f) * progress;
        float rise = config.entranceRise * (1f - progress);
        float centerX = context.guiWidth() / 2f, centerY = context.guiHeight() / 2f;
        context.pose().translate(0, rise, 0);
        context.pose().translate(centerX, centerY, 0);
        if ("squeeze".equals(config.entranceMode)) context.pose().scale(scale, 1f, 1f);
        else context.pose().scale(scale, scale, 1f);
        context.pose().translate(-centerX, -centerY, 0);
    }

    private float entranceProgress(int line) {
        TitleVisuals.Config config = visuals.config();
        long start = shownAt + (long) line * Math.max(0, config.lineStaggerMs) * 1_000_000L;
        float t = (System.nanoTime() - start) / (float) (config.entranceMs * 1_000_000L);
        return TitleVisuals.ease(t, config.curve);
    }

    private boolean originalBusy() { return travelers != null && travelers.busy(); }

    public void render(GuiGraphics context, float delta) {
        if (travelers != null) {
            letterbox.render(context);
            travelers.render(context, delta, this);
            return;
        }
        if (remaining <= 0 || displayed.isEmpty()) return;
        letterbox.render(context);
        Minecraft client = Minecraft.getInstance();
        int alpha = (int)(Math.min(1.0, Math.min((100 - remaining)/10.0, remaining/20.0))*255);
        if (alpha < 8) return;
        ResourceLocation fontLoc = ResourceLocation.tryParse("hereby_named:map_font");
        if (HerebyNamedClient.AUTOMATIC != null && HerebyNamedClient.AUTOMATIC.config() != null) {
            String custom = HerebyNamedClient.AUTOMATIC.config().mapFont;
            if (custom != null && !custom.isBlank()) {
                ResourceLocation parsed = ResourceLocation.tryParse(custom);
                if (parsed != null) fontLoc = parsed;
            }
        }
        Style fontStyle = Style.EMPTY.withFont(fontLoc);
        for (int i = 0; i < displayed.size(); i++) {
            float scale = i == 0 ? 2.0f : 1.25f;
            pushEntrance(context, i);
            context.pose().pushPose(); context.pose().scale(scale, scale, 1);
            Component comp = Component.literal(displayed.get(i)).withStyle(fontStyle);
            int w = client.font.width(comp);
            context.drawString(client.font, comp, (int)(context.guiWidth()/2f/scale - w/2), (int)((context.guiHeight()/2f-45+i*30)/scale), (alpha<<24)|0xffffff, true);
            context.pose().popPose();
            context.pose().popPose();
        }
    }

    private static final class Travelers {
        private final Object dimensionOriginal, biomeOriginal, first, second;
        private final Class<?> type;
        private final Method tick, clear, display, render;
        private static final String[] STYLE = {"titleFadeInTicks", "titleDisplayTime", "titleFadeOutTicks", "titleTextcolor",
                "titleDefaultTextColor", "showTextShadow", "titleTextSize", "titleXOffset", "titleYOffset", "isTextCentered"};

        Travelers() throws ReflectiveOperationException {
            Class<?> common = Class.forName("com.yungnickyoung.minecraft.travelerstitles.TravelersTitlesCommon");
            Object manager = common.getField("titleManager").get(null);
            if (manager == null) throw new ReflectiveOperationException("Title manager is not initialized");
            dimensionOriginal = manager.getClass().getField("dimensionTitleRenderer").get(manager);
            biomeOriginal = manager.getClass().getField("biomeTitleRenderer").get(manager);
            type = dimensionOriginal.getClass();
            Constructor<?> constructor = type.getConstructor(int.class, boolean.class, int.class, int.class, int.class,
                    String.class, boolean.class, double.class, int.class, int.class, boolean.class);
            first = constructor.newInstance(0, true, 10, 60, 20, "ffffff", true, 2.0, 0, -40, true);
            second = constructor.newInstance(0, true, 10, 60, 20, "ffffff", true, 1.25, 0, -10, true);
            tick = type.getMethod("tick"); clear = type.getMethod("clearTimer");
            Method foundDisplay = null, foundRender = null;
            for (Method m : type.getMethods()) {
                if (m.getName().equals("displayTitle") && m.getParameterCount() == 2) foundDisplay = m;
                if (m.getName().equals("renderText") && m.getParameterCount() == 2) foundRender = m;
            }
            if (foundDisplay == null || foundRender == null) throw new NoSuchMethodException("Traveler's renderer methods not found");
            display = foundDisplay; render = foundRender;
        }

        boolean busy() {
            try { return type.getField("titleTimer").getInt(dimensionOriginal) > 0 || type.getField("titleTimer").getInt(biomeOriginal) > 0; }
            catch (ReflectiveOperationException ex) { return false; }
        }

        boolean showing() { return titleTimer(first) > 0 || titleTimer(second) > 0; }

        private int titleTimer(Object renderer) {
            try { return type.getField("titleTimer").getInt(renderer); }
            catch (ReflectiveOperationException ex) { return 0; }
        }

        boolean show(List<String> names) {
            try {
                copyStyle(dimensionOriginal, first); copyStyle(biomeOriginal, second);
                display.invoke(first, Component.literal(names.get(0)), null);
                if (names.size() > 1) display.invoke(second, Component.literal(names.get(1)), null); else clear.invoke(second);
                return true;
            } catch (ReflectiveOperationException ex) { HerebyNamed.LOG.warn("Could not display region title", ex); return false; }
        }

        private void copyStyle(Object from, Object to) throws ReflectiveOperationException {
            for (String name : STYLE) { Field field = type.getField(name); field.set(to, field.get(from)); }
        }

        void clear() { try { clear.invoke(first); clear.invoke(second); } catch (ReflectiveOperationException ignored) { } }
        void tick() { try { tick.invoke(first); tick.invoke(second); } catch (ReflectiveOperationException ignored) { } }

        void render(GuiGraphics context, float delta, RegionTitles owner) {
            try {
                line(context, delta, owner, 0, dimensionOriginal, first);
                line(context, delta, owner, 1, biomeOriginal, second);
            } catch (ReflectiveOperationException ex) { HerebyNamed.LOG.warn("Could not render region titles", ex); }
        }

        private void line(GuiGraphics context, float delta, RegionTitles owner, int index, Object original, Object renderer) throws ReflectiveOperationException {
            if (titleTimer(original) > 0) return;
            owner.pushEntrance(context, index);
            try { render.invoke(renderer, delta, context); }
            finally { context.pose().popPose(); }
        }
    }
}

package dev.herebynamed.client;

import dev.herebynamed.HerebyNamed;
import dev.herebynamed.domain.TitleQueue;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
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
    public void reset() { queue = new TitleQueue(); displayed = observed = List.of(); remaining = 0; dimension = ""; }
    public void observeWithoutTitle(String dimension,List<String> names) {
        queue.observeWithoutTitle(dimension,names);this.dimension=dimension;observed=List.copyOf(names);remaining=0;
        if(travelers!=null) travelers.clear();
    }
    public void tick(String dimension, List<String> names) {
        if (!checked) {
            checked = true;
            if (FabricLoader.getInstance().isModLoaded("travelerstitles")) try { travelers = new Travelers(); }
            catch (ReflectiveOperationException | LinkageError ex) { HerebyNamed.LOG.warn("Traveler's Titles bridge unavailable; using built-in titles", ex); }
        }
        if (!this.dimension.equals(dimension) || !observed.equals(names)) { remaining = 0; if (travelers != null) travelers.clear(); }
        this.dimension = dimension; observed = List.copyOf(names);
        boolean busy = originalBusy();
        if (!busy) {
            if (remaining > 0) remaining--;
            if (travelers != null) travelers.tick();
        }
        queue.update(dimension, names, System.nanoTime()/1_000_000,
                busy || MinecraftClient.getInstance().currentScreen != null).ifPresent(next -> {
            displayed = next; remaining = 100;
            if (travelers != null) travelers.show(next);
        });
    }
    private boolean originalBusy() { return travelers != null && travelers.busy(); }
    public void render(DrawContext context, float delta) {
        if (originalBusy()) return;
        if (travelers != null) { travelers.render(context, delta); return; }
        if (remaining <= 0 || displayed.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        int alpha = (int)(Math.min(1.0, Math.min((100 - remaining)/10.0, remaining/20.0))*255);
        if (alpha < 8) return;
        for (int i = 0; i < displayed.size(); i++) {
            float scale = i == 0 ? 2.0f : 1.25f;
            context.getMatrices().push(); context.getMatrices().scale(scale, scale, 1);
            context.drawCenteredTextWithShadow(client.textRenderer, displayed.get(i),
                    (int)(context.getScaledWindowWidth()/2f/scale), (int)((context.getScaledWindowHeight()/2f-45+i*30)/scale), (alpha<<24)|0xffffff);
            context.getMatrices().pop();
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
            display = type.getMethod("displayTitle", Text.class, Text.class);
            render = type.getMethod("renderText", float.class, DrawContext.class);
        }
        boolean busy() {
            try { return type.getField("titleTimer").getInt(dimensionOriginal) > 0 || type.getField("titleTimer").getInt(biomeOriginal) > 0; }
            catch (ReflectiveOperationException ex) { return false; }
        }
        void show(List<String> names) {
            try {
                copyStyle(dimensionOriginal, first); copyStyle(biomeOriginal, second);
                display.invoke(first, Text.literal(names.get(0)), null);
                if (names.size() > 1) display.invoke(second, Text.literal(names.get(1)), null); else clear.invoke(second);
            } catch (ReflectiveOperationException ex) { HerebyNamed.LOG.warn("Could not display region title", ex); }
        }
        private void copyStyle(Object from, Object to) throws ReflectiveOperationException {
            for (String name : STYLE) { Field field = type.getField(name); field.set(to, field.get(from)); }
        }
        void clear() { try { clear.invoke(first); clear.invoke(second); } catch (ReflectiveOperationException ignored) { } }
        void tick() { try { tick.invoke(first); tick.invoke(second); } catch (ReflectiveOperationException ignored) { } }
        void render(DrawContext context, float delta) {
            try { render.invoke(first, delta, context); render.invoke(second, delta, context); }
            catch (ReflectiveOperationException ex) { HerebyNamed.LOG.warn("Could not render region titles", ex); }
        }
    }
}

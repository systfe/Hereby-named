package dev.herebynamed.client;

import dev.herebynamed.auto.*;
import dev.herebynamed.platform.PlatformHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.IntConsumer;

/** One isolated settings draft shared by the forms and list editors. */
final class AutomaticConfigScreen extends Screen implements EditorDialog {
    private static final TerrainKind[] KINDS = {TerrainKind.MOUNTAIN, TerrainKind.OCEAN, TerrainKind.FROZEN_OCEAN,
            TerrainKind.PLAIN, TerrainKind.FOREST, TerrainKind.FLOWER, TerrainKind.DESERT, TerrainKind.LAKE, TerrainKind.NORTHLAND, TerrainKind.OTHER};
    private static final String[] LABELS = {"山地", "大洋", "冻洋", "原野", "森林", "花草甸", "沙漠", "湖泊", "北地", "暂不分类"};
    private static final String[] CLIMATES = {"COLD", "TEMPERATE", "HOT"};

    private final Screen parent;
    private final AutomaticRegions automatic;
    private final AutoConfig draft;
    private final Map<String, String> rawFields = new LinkedHashMap<>();
    private final Map<String, IntConsumer> fieldWriters = new LinkedHashMap<>();
    private final List<String> rowLabels = new ArrayList<>();
    private final List<String> allBiomes = new ArrayList<>();
    private final List<String> filteredBiomes = new ArrayList<>();

    private int tab, page, x, contentWidth, rows;
    private boolean saving;
    private String error = "";
    private String biomeSearch = "";
    private boolean filterOnlyOverridden = false;
    private EditBox searchInput;

    AutomaticConfigScreen(Screen parent, AutomaticRegions automatic, int tab) {
        super(Component.literal("自动区域设置"));
        this.parent = parent;
        this.automatic = automatic;
        this.tab = tab;
        this.draft = automatic.settingsDraft();
    }

    private Button button(int bx, int by, int w, String text, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(text), b -> action.run()).bounds(bx, by, w, 20).build());
    }

    static String label(TerrainKind kind) {
        if (kind == null) return "暂不分类";
        for (int i = 0; i < KINDS.length; i++) if (KINDS[i] == kind) return LABELS[i];
        return kind == TerrainKind.RIVER ? "河流" : "暂不分类";
    }

    private void refreshBiomeList() {
        if (allBiomes.isEmpty()) {
            Set<String> set = new TreeSet<>();
            if (minecraft != null && minecraft.level != null) {
                try {
                    var reg = minecraft.level.registryAccess().registryOrThrow(Registries.BIOME);
                    for (var key : reg.keySet()) set.add(key.toString());
                } catch (Exception ignored) { }
            }
            if (draft.biomeOverrides != null) set.addAll(draft.biomeOverrides.keySet());
            if (set.isEmpty()) {
                set.addAll(List.of(
                        "minecraft:plains", "minecraft:sunflower_plains", "minecraft:snowy_plains", "minecraft:ice_spikes",
                        "minecraft:desert", "minecraft:swamp", "minecraft:mangrove_swamp", "minecraft:forest",
                        "minecraft:flower_forest", "minecraft:birch_forest", "minecraft:dark_forest", "minecraft:old_growth_birch_forest",
                        "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga", "minecraft:taiga", "minecraft:snowy_taiga",
                        "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills",
                        "minecraft:windswept_forest", "minecraft:windswept_savanna", "minecraft:jungle", "minecraft:sparse_jungle",
                        "minecraft:bamboo_jungle", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands",
                        "minecraft:meadow", "minecraft:cherry_grove", "minecraft:grove", "minecraft:snowy_slopes",
                        "minecraft:frozen_peaks", "minecraft:jagged_peaks", "minecraft:stony_peaks", "minecraft:river",
                        "minecraft:frozen_river", "minecraft:beach", "minecraft:snowy_beach", "minecraft:stony_shore",
                        "minecraft:warm_ocean", "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean", "minecraft:ocean",
                        "minecraft:deep_ocean", "minecraft:cold_ocean", "minecraft:deep_cold_ocean", "minecraft:frozen_ocean",
                        "minecraft:deep_frozen_ocean", "minecraft:mushroom_fields"
                ));
            }
            allBiomes.addAll(set);
        }
        filteredBiomes.clear();
        String query = biomeSearch.strip().toLowerCase(Locale.ROOT);
        for (String id : allBiomes) {
            boolean matchesSearch = query.isEmpty() || id.toLowerCase(Locale.ROOT).contains(query);
            boolean isOverridden = draft.biomeOverrides != null && draft.biomeOverrides.containsKey(id);
            if (matchesSearch && (!filterOnlyOverridden || isOverridden)) {
                filteredBiomes.add(id);
            }
        }
    }

    @Override protected void init() {
        rowLabels.clear();
        contentWidth = Math.min(350, width - 20);
        x = (width - contentWidth) / 2;
        rows = Math.max(1, Math.min(10, (height - (tab == 4 ? 164 : 140)) / 24));

        if (tab == 4) refreshBiomeList();
        page = Math.min(page, Math.max(0, (count() - 1) / rows));

        String[] tabs = {"区域门槛", "全局参数", "默认名", "词根", "群系分类"};
        int tabWidth = (contentWidth - 8) / 5;
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            button(x + i * (tabWidth + 2), 26, tabWidth, tabs[i], () -> navigate(index, 0)).active = tab != i;
        }

        if (tab == 0) thresholds();
        else if (tab == 1) globals();
        else if (tab == 4) biomes();
        else lists();

        int pages = Math.max(1, (count() + rows - 1) / rows);
        button(x, height - 54, 62, "上一页", () -> navigate(tab, page - 1)).active = page > 0;
        button(x + contentWidth - 62, height - 54, 62, "下一页", () -> navigate(tab, page + 1)).active = page + 1 < pages;
        button(x, height - 28, (contentWidth - 6) / 2, "保存并应用", this::save);
        button(x + (contentWidth + 6) / 2, height - 28, (contentWidth - 6) / 2, "取消", this::onClose);
        if (saving) for (var child : children()) if (child instanceof AbstractWidget widget) widget.active = false;
    }

    private int count() {
        return tab == 0 ? 9 : tab == 1 ? 11 : tab == 4 ? filteredBiomes.size() : names().size();
    }

    private List<AutoConfig.Name> names() {
        return tab == 3 ? draft.roots : draft.names;
    }

    private void navigate(int nextTab, int nextPage) {
        tab = nextTab;
        page = Math.max(0, nextPage);
        error = "";
        rebuildWidgets();
    }

    private boolean read() {
        try {
            Map<String, Integer> values = new LinkedHashMap<>();
            rawFields.forEach((key, value) -> values.put(key, Integer.parseInt(value)));
            values.forEach((key, value) -> fieldWriters.get(key).accept(value));
            error = "";
            return true;
        } catch (IllegalArgumentException ex) {
            error = "请输入有效的非负整数";
            return false;
        }
    }

    private EditBox number(int bx, int by, int w, int value, String hint, String key, IntConsumer writer) {
        EditBox input = new EditBox(font, bx, by, w, 18, Component.literal(hint));
        fieldWriters.put(key, writer);
        rawFields.putIfAbsent(key, Integer.toString(value));
        input.setMaxLength(7);
        input.setFilter(s -> s.matches("[0-9]*"));
        input.setValue(rawFields.get(key));
        input.setResponder(text -> rawFields.put(key, text));
        input.setTooltip(Tooltip.create(Component.literal(hint)));
        addRenderableWidget(input);
        return input;
    }

    private void thresholds() {
        for (int index = page * rows; index < Math.min(KINDS.length - 1, (page + 1) * rows); index++) {
            TerrainKind kind = KINDS[index];
            AutoConfig.Threshold value = threshold(kind);
            int y = 74 + (index - page * rows) * 24;
            int w = (contentWidth - 90) / 2;
            number(x + 90, y, w - 5, value.minimumSpan(), "最低跨度，单位为方块", kind + ":span",
                    v -> { var t = threshold(kind); setThreshold(kind, new AutoConfig.Threshold(v, t.maximumSpan())); });
            number(x + 90 + w, y, w - 5, value.maximumSpan(), "跨度必须小于此值；0 表示没有上限", kind + ":maximum",
                    v -> { var t = threshold(kind); setThreshold(kind, new AutoConfig.Threshold(t.minimumSpan(), v)); });
            rowLabels.add(label(kind));
        }
    }

    private AutoConfig.Threshold threshold(TerrainKind kind) {
        return switch (kind) {
            case MOUNTAIN -> draft.thresholds.mountain;
            case OCEAN -> draft.thresholds.ocean;
            case FROZEN_OCEAN -> draft.thresholds.frozenOcean;
            case PLAIN -> draft.thresholds.plain;
            case FOREST -> draft.thresholds.forest;
            case FLOWER -> draft.thresholds.flower;
            case DESERT -> draft.thresholds.desert;
            case LAKE -> draft.thresholds.lake;
            case NORTHLAND -> draft.thresholds.northland;
            default -> throw new IllegalArgumentException();
        };
    }

    private void setThreshold(TerrainKind kind, AutoConfig.Threshold value) {
        switch (kind) {
            case MOUNTAIN -> draft.thresholds.mountain = value;
            case OCEAN -> draft.thresholds.ocean = value;
            case FROZEN_OCEAN -> draft.thresholds.frozenOcean = value;
            case PLAIN -> draft.thresholds.plain = value;
            case FOREST -> draft.thresholds.forest = value;
            case FLOWER -> draft.thresholds.flower = value;
            case DESERT -> draft.thresholds.desert = value;
            case LAKE -> draft.thresholds.lake = value;
            case NORTHLAND -> draft.thresholds.northland = value;
            default -> throw new IllegalArgumentException();
        }
    }

    private void globals() {
        String[] labels = {"启用所需连续跨度", "所有区域最小跨度", "地表缓存区块上限", "分析间隔（秒）", "山脉／广域山地跨度", "广域山地最少区块数", "山岭长宽比（百分比）", ""};
        String[] hints = {"256～1000000 格；已记录地表须连成一片", "16～100000 格；对所有类别生效", "4096～500000；更高上限会占用更多内存", "10～600 秒；更频繁分析会增加开销", "16～1000000 格；长条山地达到此跨度用山脉，宽广山地用峦", "1～500000 个区块；宽广山地达到此面积用峦", "110～1000；180 表示长边大于短边的 1.8 倍时视为绵延山地", ""};
        int[] values = {draft.unlockSpan, draft.minimumSpan, draft.maximumSamples, draft.analysisIntervalSeconds, draft.mountainRangeSpan, draft.mountainMassifChunks, draft.mountainRidgeRatioPercent};
        IntConsumer[] setters = {v -> draft.unlockSpan = v, v -> draft.minimumSpan = v, v -> draft.maximumSamples = v, v -> draft.analysisIntervalSeconds = v, v -> draft.mountainRangeSpan = v, v -> draft.mountainMassifChunks = v, v -> draft.mountainRidgeRatioPercent = v};
        for (int index = page * rows; index < Math.min(11, (page + 1) * rows); index++) {
            int y = 74 + (index - page * rows) * 24;
            if (index == 7) {
                rowLabels.add("自动创建区域");
                button(x + contentWidth - 96, y, 90, draft.enabled ? "开启" : "关闭", () -> { draft.enabled = !draft.enabled; rebuildWidgets(); });
                continue;
            }
            if (index == 8) {
                rowLabels.add("删除保护（防重新生成）");
                button(x + contentWidth - 96, y, 90, draft.suppressDeletedRegions ? "开启" : "关闭", () -> { draft.suppressDeletedRegions = !draft.suppressDeletedRegions; rebuildWidgets(); });
                continue;
            }
            if (index == 9) {
                rowLabels.add("地图显示字体");
                String fontBtnText = (draft.mapFont == null || draft.mapFont.isBlank() || "hereby_named:map_font".equals(draft.mapFont))
                        ? "思源宋体" : "minecraft:default".equals(draft.mapFont) ? "原版字体" : "自定义";
                button(x + contentWidth - 96, y, 90, fontBtnText, () -> {
                    if (draft.mapFont == null || "hereby_named:map_font".equals(draft.mapFont)) draft.mapFont = "minecraft:default";
                    else draft.mapFont = "hereby_named:map_font";
                    rebuildWidgets();
                });
                continue;
            }
            if (index == 10) {
                rowLabels.add("地图字体不透明度");
                addRenderableWidget(new AbstractSliderButton(x + contentWidth - 96, y, 90, 20,
                        Component.literal(draft.mapFontOpacityPercent + "%"), draft.mapFontOpacityPercent / 100.0) {
                    @Override
                    protected void updateMessage() {
                        setMessage(Component.literal(draft.mapFontOpacityPercent + "%"));
                    }

                    @Override
                    protected void applyValue() {
                        draft.mapFontOpacityPercent = (int) Math.round(this.value * 100.0);
                    }
                });
                continue;
            }
            rowLabels.add(labels[index]);
            number(x + contentWidth - 96, y, 90, values[index], hints[index], "global:" + index, setters[index]);
        }
    }

    private void lists() {
        button(x + contentWidth - 58, 51, 58, "新增", () -> openEntry(-1));
        button(x + contentWidth - 126, 51, 64, "恢复默认", () -> {
            if (minecraft != null) minecraft.setScreen(new Confirm(this, "用默认名称替换当前草稿中的白名单？", () -> {
                try {
                    if (tab == 3) draft.roots = AutoConfig.defaultNames(true);
                    else draft.names = AutoConfig.defaultNames(false);
                    error = "";
                } catch (java.io.IOException ex) {
                    error = "读取默认名单失败";
                }
                page = 0;
            }));
        });
        for (int index = page * rows; index < Math.min(count(), (page + 1) * rows); index++) {
            int y = 74 + (index - page * rows) * 24, i = index;
            rowLabels.add(names().get(i).text());
            button(x + contentWidth - 86, y, 40, "编辑", () -> openEntry(i));
            button(x + contentWidth - 42, y, 40, "删除", () -> {
                if (minecraft != null) minecraft.setScreen(new Confirm(this, "从草稿中删除“" + rowLabels.get(i - page * rows) + "”？", () -> {
                    names().remove(i);
                }));
            });
        }
    }

    private void biomes() {
        // Search bar
        int searchW = contentWidth - 140;
        searchInput = new EditBox(font, x, 51, searchW, 18, Component.literal("搜索群系 ID"));
        searchInput.setValue(biomeSearch);
        searchInput.setResponder(s -> {
            biomeSearch = s;
            refreshBiomeList();
            page = 0;
            rebuildWidgets();
        });
        addRenderableWidget(searchInput);

        // Filter button: All vs Modified
        button(x + searchW + 4, 50, 64, filterOnlyOverridden ? "仅看已改" : "全部群系", () -> {
            filterOnlyOverridden = !filterOnlyOverridden;
            refreshBiomeList();
            page = 0;
            rebuildWidgets();
        });

        // Reset all button
        button(x + searchW + 72, 50, 64, "恢复全部", () -> {
            if (minecraft != null) minecraft.setScreen(new Confirm(this, "清除所有自定义群系分类，恢复默认推断？", () -> {
                if (draft.biomeOverrides != null) draft.biomeOverrides.clear();
                refreshBiomeList();
                page = 0;
                rebuildWidgets();
            }));
        });

        int startY = 76;
        for (int index = page * rows; index < Math.min(filteredBiomes.size(), (page + 1) * rows); index++) {
            int y = startY + (index - page * rows) * 24;
            String biomeId = filteredBiomes.get(index);
            boolean overridden = draft.biomeOverrides != null && draft.biomeOverrides.containsKey(biomeId);
            TerrainKind currentKind = overridden ? TerrainKind.valueOf(draft.biomeOverrides.get(biomeId))
                    : TerrainClassifier.defaultClassify(biomeId);

            rowLabels.add(biomeId);

            // Button to cycle through kinds
            int btnW = overridden ? 58 : 64;
            button(x + contentWidth - (overridden ? 98 : 64), y, btnW, label(currentKind), () -> {
                if (draft.biomeOverrides == null) draft.biomeOverrides = new LinkedHashMap<>();
                int nextIdx = (currentKind.ordinal() + 1) % KINDS.length;
                draft.biomeOverrides.put(biomeId, KINDS[nextIdx].name());
                rebuildWidgets();
            });

            // Single item reset button if overridden
            if (overridden) {
                button(x + contentWidth - 36, y, 34, "重置", () -> {
                    draft.biomeOverrides.remove(biomeId);
                    rebuildWidgets();
                });
            }
        }
    }

    private void openEntry(int index) {
        if (minecraft != null) minecraft.setScreen(new EntryScreen(index));
    }

    private void save() {
        if (saving || !read()) return;
        try { draft.validate(); } catch (IllegalArgumentException ex) { error = ex.getMessage(); return; }
        saving = true;
        rebuildWidgets();
        var connection = minecraft != null ? minecraft.getConnection() : null;
        var world = minecraft != null ? minecraft.level : null;
        automatic.saveSettings(draft, message -> {
            saving = false;
            if (minecraft == null || minecraft.screen != this || minecraft.getConnection() != connection || minecraft.level != world) return;
            if (message.isEmpty()) minecraft.setScreen(parent);
            else { error = message; rebuildWidgets(); }
        });
    }

    @Override public void onClose() { if (!saving && minecraft != null) minecraft.setScreen(parent); }

    @Override public void render(GuiGraphics context, int mx, int my, float delta) {
        PlatformHelper.get().renderBackground(this, context, mx, my, delta);
        super.render(context, mx, my, delta);
        context.drawCenteredString(font, title, width / 2, 8, 0xffffff);

        if (tab == 0) {
            context.drawString(font, "类别", x, 57, 0xbbbbbb);
            int w = (contentWidth - 90) / 2;
            String[] headers = {"最低跨度", "跨度上限"};
            for (int i = 0; i < 2; i++) context.drawString(font, headers[i], x + 90 + i * w, 57, 0xbbbbbb);
        } else if (tab == 4) {
            String status = minecraft != null && minecraft.level != null ? "共 " + count() + " 个群系" : "主界面模式（进存档可显示模组群系）";
            context.drawString(font, status, x, 38, 0x888888);
        } else {
            context.drawString(font, tab == 1 ? "悬停输入框查看范围" : "共 " + count() + " 条", x, 57, 0xbbbbbb);
        }

        int maxLabelW = tab == 0 ? 82 : tab == 4 ? contentWidth - 104 : contentWidth - 102;
        int startY = tab == 4 ? 82 : 79;
        for (int i = 0; i < rowLabels.size(); i++) {
            String text = rowLabels.get(i);
            int color = 0xffffff;
            if (tab == 4 && draft.biomeOverrides != null && draft.biomeOverrides.containsKey(text)) {
                color = 0x88ff88; // Modified entries highlighted in soft green
            }
            context.drawString(font, font.plainSubstrByWidth(text, maxLabelW), x, startY + i * 24, color);
        }

        context.drawCenteredString(font, (page + 1) + " / " + Math.max(1, (count() + rows - 1) / rows), width / 2, height - 48, 0xbbbbbb);
        String message = saving ? "正在保存…" : error.isEmpty() ? "保存后生效；取消不会修改现有设置" : error;
        context.drawCenteredString(font, font.plainSubstrByWidth(message, contentWidth), width / 2, height - 68, error.isEmpty() ? 0xaaaaaa : 0xff8888);
    }

    private static final class Confirm extends ConfirmScreen implements EditorDialog {
        Confirm(AutomaticConfigScreen parent, String question, Runnable action) {
            super(yes -> { if (yes) action.run(); Minecraft.getInstance().setScreen(parent); },
                    Component.literal("确认操作"), Component.literal(question), Component.literal("确认"), Component.literal("取消"));
        }
    }

    private final class EntryScreen extends Screen implements EditorDialog {
        private final int index;
        private final Set<String> kinds = new LinkedHashSet<>();
        private final Set<String> climates = new LinkedHashSet<>();
        private String entryError = "";
        private EditBox text;
        private String value;
        private int left, top, w;

        EntryScreen(int index) {
            super(Component.literal(tab == 3 ? "词根条目" : "默认名条目"));
            this.index = index;
            this.value = "";
            if (index >= 0) {
                var entry = names().get(index);
                value = entry.text();
                kinds.addAll(entry.kinds());
                climates.addAll(entry.climates());
            } else {
                kinds.add("ALL");
            }
        }

        private Button addButton(int bx, int by, int bw, String label, Runnable action) {
            return addRenderableWidget(Button.builder(Component.literal(label), b -> {
                value = text.getValue();
                action.run();
                if (minecraft != null && minecraft.screen == this) rebuildWidgets();
            }).bounds(bx, by, bw, 20).build());
        }

        @Override protected void init() {
            if (text != null) value = text.getValue();
            w = Math.min(300, width - 24);
            left = (width - w) / 2;
            top = Math.max(8, (height - 286) / 2);

            text = new EditBox(font, left, top + 38, w, 18, Component.literal("名称"));
            text.setMaxLength(48);
            text.setValue(value);
            addRenderableWidget(text);
            setInitialFocus(text);

            int climateWidth = (w - 4) / 3;
            for (int i = 0; i < CLIMATES.length; i++) {
                String climate = CLIMATES[i];
                String name = switch (climate) { case "COLD" -> "寒冷"; case "HOT" -> "炎热"; default -> "温和"; };
                addButton(left + i * (climateWidth + 2), top + 66, climateWidth, (climates.contains(climate) ? "✓ " : "") + name, () -> {
                    if (!climates.remove(climate)) climates.add(climate);
                });
            }
            addButton(left, top + 90, w, (climates.isEmpty() ? "✓ " : "") + "不限气候", climates::clear);
            addButton(left, top + 114, w, (kinds.isEmpty() || kinds.contains("ALL") ? "✓ " : "") + "适用于所有地形", () -> {
                kinds.clear();
                kinds.add("ALL");
            });
            int bw = (w - 4) / 3;
            for (int i = 0; i < KINDS.length - 1; i++) {
                String kind = KINDS[i].name();
                addButton(left + (i % 3) * (bw + 2), top + 138 + (i / 3) * 22, bw, (kinds.contains(kind) ? "✓ " : "") + LABELS[i], () -> {
                    kinds.remove("ALL");
                    if (!kinds.remove(kind)) kinds.add(kind);
                });
            }

            addButton(left, top + 218, (w - 6) / 2, "应用到草稿", this::apply);
            addButton(left + (w + 6) / 2, top + 218, (w - 6) / 2, "取消", this::onClose);
        }

        private void apply() {
            try {
                if (text.getValue().isBlank()) throw new IllegalArgumentException("请输入名称");
                if (text.getValue().codePoints().anyMatch(Character::isDigit)) throw new IllegalArgumentException("名称不能包含数字");
                var entry = new AutoConfig.Name(text.getValue().strip(), List.copyOf(kinds), List.copyOf(climates));
                AutoConfig check = draft.copy();
                List<AutoConfig.Name> list = tab == 3 ? check.roots : check.names;
                if (index < 0) list.add(entry);
                else list.set(index, entry);
                check.validate();
                if (tab == 3) draft.roots = list;
                else draft.names = list;
                onClose();
            } catch (IllegalArgumentException ex) {
                entryError = ex.getMessage();
            }
        }

        @Override public void onClose() { if (minecraft != null) minecraft.setScreen(AutomaticConfigScreen.this); }

        @Override public void render(GuiGraphics context, int mx, int my, float delta) {
            PlatformHelper.get().renderBackground(this, context, mx, my, delta);
            super.render(context, mx, my, delta);
            context.drawCenteredString(font, title, width / 2, top, 0xffffff);
            context.drawString(font, "名称（不含数字）", left, top + 23, 0xbbbbbb);
            context.drawCenteredString(font, "偏好可多选；留空表示不限", width / 2, top + 244, 0xaaaaaa);
            context.drawCenteredString(font, font.plainSubstrByWidth(entryError.isEmpty() ? "返回后点击“保存并应用”才会写入设置" : entryError, w), width / 2, top + 260, entryError.isEmpty() ? 0xaaaaaa : 0xff8888);
        }
    }
}

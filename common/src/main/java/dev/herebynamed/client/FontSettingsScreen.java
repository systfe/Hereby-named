package dev.herebynamed.client;

import dev.herebynamed.auto.AutoConfig;
import dev.herebynamed.platform.PlatformHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class FontSettingsScreen extends Screen implements EditorDialog {
    private final Screen parent;
    private final AutomaticRegions automatic;
    private EditBox fontInput;
    private int opacityPercent = 85;
    private String error = "";
    private int top;

    FontSettingsScreen(Screen parent, AutomaticRegions automatic) {
        super(Component.literal("地图显示字体设置"));
        this.parent = parent;
        this.automatic = automatic;
    }

    @Override
    protected void init() {
        top = Math.max(8, (height - 230) / 2);
        int x = width / 2 - 120;
        String curFont = (automatic != null && automatic.config() != null && automatic.config().mapFont != null)
                ? automatic.config().mapFont : "hereby_named:map_font";
        opacityPercent = (automatic != null && automatic.config() != null)
                ? automatic.config().mapFontOpacityPercent : 85;

        fontInput = new EditBox(font, x, top + 52, 240, 18, Component.literal("字体标识符"));
        fontInput.setMaxLength(120);
        fontInput.setValue(curFont);
        addRenderableWidget(fontInput);

        addRenderableWidget(Button.builder(Component.literal("预设：思源宋体 / Cinzel (默认)"), b -> {
            fontInput.setValue("hereby_named:map_font");
        }).bounds(x, top + 78, 240, 20).build());

        addRenderableWidget(Button.builder(Component.literal("预设：原版默认像素字体 (minecraft:default)"), b -> {
            fontInput.setValue("minecraft:default");
        }).bounds(x, top + 104, 240, 20).build());

        addRenderableWidget(Button.builder(Component.literal("文字不透明度：" + opacityPercent + "% (点击切换)"), b -> {
            if (opacityPercent >= 100) opacityPercent = 85;
            else if (opacityPercent >= 85) opacityPercent = 70;
            else if (opacityPercent >= 70) opacityPercent = 50;
            else if (opacityPercent >= 50) opacityPercent = 30;
            else opacityPercent = 100;
            b.setMessage(Component.literal("文字不透明度：" + opacityPercent + "% (点击切换)"));
        }).bounds(x, top + 130, 240, 20).build());

        addRenderableWidget(Button.builder(Component.literal("保存并应用"), b -> {
            String val = fontInput.getValue().strip();
            if (val.isBlank() || ResourceLocation.tryParse(val) == null) {
                error = "请输入有效的 ResourceLocation (例 hereby_named:map_font)";
                return;
            }
            if (automatic != null) {
                AutoConfig draft = automatic.settingsDraft();
                draft.mapFont = val;
                draft.mapFontOpacityPercent = opacityPercent;
                automatic.saveSettings(draft, msg -> {});
            }
            onClose();
        }).bounds(x, top + 158, 116, 20).build());

        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
                .bounds(x + 124, top + 158, 116, 20).build());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        PlatformHelper.get().renderBackground(this, context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(font, title, width / 2, top + 10, 0xffffff);
        context.drawCenteredString(font, "配置世界地图上区域名称渲染所使用的字体标识符与透明度", width / 2, top + 26, 0xbbbbbb);
        context.drawString(font, "字体标识符 (ResourceLocation 或资源包字体):", width / 2 - 120, top + 40, 0xdddddd);
        if (!error.isEmpty()) {
            context.drawCenteredString(font, error, width / 2, top + 188, 0xff6666);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}

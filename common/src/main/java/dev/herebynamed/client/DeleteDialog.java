package dev.herebynamed.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class DeleteDialog extends ConfirmScreen implements EditorDialog {
    DeleteDialog(Screen parent, String detail, Runnable confirmed) {
        super(yes -> { if (yes) confirmed.run(); Minecraft.getInstance().setScreen(parent); },
                Component.literal("确认删除？"), Component.literal(detail), Component.literal("删除"), Component.literal("取消"));
    }
}

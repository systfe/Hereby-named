package dev.herebynamed.client;

import net.minecraft.client.gui.screen.*;
import net.minecraft.text.Text;

final class DeleteDialog extends ConfirmScreen implements EditorDialog {
    DeleteDialog(Screen parent, String detail, Runnable confirmed) {
        super(yes -> { if (yes) confirmed.run(); net.minecraft.client.MinecraftClient.getInstance().setScreen(parent); },
                Text.literal("确认删除？"), Text.literal(detail), Text.literal("删除"), Text.literal("取消"));
    }
}

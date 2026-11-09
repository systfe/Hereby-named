package dev.herebynamed.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

@Mixin(Screen.class)
public interface ScreenAccessor {
    @Accessor("children") List<GuiEventListener> hereby$getChildren();
    @Accessor("renderables") List<Renderable> hereby$getRenderables();
    @Accessor("narratables") List<NarratableEntry> hereby$getNarratables();
}

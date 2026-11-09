package dev.herebynamed.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "xaero.map.gui.GuiMap", remap = false)
public interface GuiMapAccessor {
    @Accessor("cameraX") double hereby$cameraX();
    @Accessor("cameraZ") double hereby$cameraZ();
    @Accessor("scale") double hereby$scale();
    @Accessor("mouseBlockPosX") int hereby$mouseX();
    @Accessor("mouseBlockPosZ") int hereby$mouseZ();
}

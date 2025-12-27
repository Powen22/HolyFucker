package thunder.hack.injection;

import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(KeyBinding.class)
public abstract class MixinKeyBinding {
    @Shadow public abstract boolean equals(KeyBinding other);

    @Shadow public abstract boolean isPressed();
}

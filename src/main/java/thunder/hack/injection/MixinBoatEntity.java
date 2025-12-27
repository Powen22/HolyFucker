package thunder.hack.injection;

import net.minecraft.entity.vehicle.BoatEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BoatEntity.class)
public class MixinBoatEntity {
    // BoatFly module removed
}

package thunder.hack.injection;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thunder.hack.HolyFacker;
import thunder.hack.events.impl.UseTridentEvent;

import static thunder.hack.HolyFacker.mc;

@Mixin(TridentItem.class)
public abstract class MixinTridentItem {

    @Inject(method = "onStoppedUsing", at = @At(value = "HEAD"), cancellable = true)
    public void onStoppedUsingHook(ItemStack stack, World world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        if (user == mc.player && EnchantmentHelper.getTridentSpinAttackStrength(stack, mc.player) > 0) {
            UseTridentEvent e = new UseTridentEvent();
            HolyFacker.EVENT_BUS.post(e);
            if (e.isCancelled())
                ci.cancel();
        }
    }
}

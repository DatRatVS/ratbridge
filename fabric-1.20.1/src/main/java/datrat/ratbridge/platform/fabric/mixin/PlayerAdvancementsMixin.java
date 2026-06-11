package datrat.ratbridge.platform.fabric.mixin;

import datrat.ratbridge.platform.fabric.RatBridgeFabric;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Shadow
    private ServerPlayer player;

    @Shadow
    public abstract AdvancementProgress getOrStartProgress(Advancement advancement);

    @Inject(method = "award", at = @At("RETURN"))
    private void ratbridge$afterAward(Advancement advancement, String criterionName, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() && getOrStartProgress(advancement).isDone()) {
            RatBridgeFabric.onAdvancementEarned(player, advancement);
        }
    }
}

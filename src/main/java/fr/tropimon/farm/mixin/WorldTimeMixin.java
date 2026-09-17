package fr.tropimon.farm.mixin;

import fr.tropimon.farm.HabitatClock;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
abstract class WorldTimeMixin {
  @Inject(method = "onWorldTimeUpdate", at = @At("TAIL"))
  private void farmTime(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
    HabitatClock.accept(packet.getTime());
  }
}

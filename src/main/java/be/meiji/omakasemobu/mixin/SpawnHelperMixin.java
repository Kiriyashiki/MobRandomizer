package be.meiji.omakasemobu.mixin;

import be.meiji.omakasemobu.MobRandomizerMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.world.SpawnHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SpawnHelper.class)
public class SpawnHelperMixin {

  @Redirect(method = "setupSpawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getType()Lnet/minecraft/entity/EntityType;"))
  private static EntityType<?> setupSpawn(Entity entity) {

    return MobRandomizerMod.compliment(entity.getType());
  }
}

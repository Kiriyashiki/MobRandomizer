package be.meiji.omakasemobu.mixin;

import be.meiji.omakasemobu.MobRandomizerMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NaturalSpawner.class)
public class NaturalSpawnerMixin {

  @Redirect(method = "createState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getType()Lnet/minecraft/world/entity/EntityType;"))
  private static EntityType<?> setupSpawn(Entity entity) {

    return MobRandomizerMod.compliment(entity.getType());
  }
}

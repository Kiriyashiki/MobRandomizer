package be.meiji.omakasemobu.mixin;

import be.meiji.omakasemobu.MobRandomizerMod;
import static be.meiji.omakasemobu.MobRandomizerMod.TAG_ID;
import static be.meiji.omakasemobu.MobRandomizerMod.createRandomizedEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {

  @ModifyVariable(method = "addFreshEntity", at = @At("HEAD"), argsOnly = true)
  public Entity spawnEntity(Entity entity) {
    ServerLevelAccessor world = (ServerLevelAccessor) this;

    if (entity == null || entity.entityTags().contains(TAG_ID) || !MobRandomizerMod.canRandomize(entity.getType())) {
      return entity;
    }

    return createRandomizedEntity(world.getLevel(), entity, true);
  }
}

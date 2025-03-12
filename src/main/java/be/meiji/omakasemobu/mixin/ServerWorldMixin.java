package be.meiji.omakasemobu.mixin;

import be.meiji.omakasemobu.MobRandomizerMod;
import static be.meiji.omakasemobu.MobRandomizerMod.TAG_ID;
import static be.meiji.omakasemobu.MobRandomizerMod.createRandomizedEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerWorld.class)
public class ServerWorldMixin {

  @ModifyVariable(method = "spawnEntity", at = @At("HEAD"), argsOnly = true)
  public Entity spawnEntity(Entity entity) {
    ServerWorldAccess world = (ServerWorldAccess) this;

    if (entity == null || entity.getCommandTags().contains(TAG_ID) || !MobRandomizerMod.canRandomize(entity.getType())) {
      return entity;
    }

    return createRandomizedEntity(world.toServerWorld(), entity);
  }
}

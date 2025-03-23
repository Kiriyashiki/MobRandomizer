package be.meiji.omakasemobu.mixin;

import be.meiji.omakasemobu.MobRandomizerMod;
import static be.meiji.omakasemobu.MobRandomizerMod.TAG_ID;
import static be.meiji.omakasemobu.MobRandomizerMod.createRandomizedEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.ChunkRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ChunkRegion.class)
public abstract class ChunkRegionMixin {

  // yeah I'll have to figure this one out eventually
  @Shadow @Deprecated public abstract ServerWorld toServerWorld();

  @ModifyArg(
      method = "spawnEntity",
      at = @At(
          value = "INVOKE",
          target = "Lnet/minecraft/world/chunk/Chunk;addEntity(Lnet/minecraft/entity/Entity;)V"
      )
  )
  private Entity modifyEntityArgument(Entity entity) {
    if (entity == null || entity.getCommandTags().contains(TAG_ID) || !MobRandomizerMod.canRandomize(entity.getType())) {
      return entity;
    }

    ServerWorld world = this.toServerWorld();

    Entity newEntity = createRandomizedEntity(world, entity, false);

    if (newEntity instanceof MobEntity) {
      ((MobEntity) newEntity).setPersistent();
    }

    return newEntity;
  }
}

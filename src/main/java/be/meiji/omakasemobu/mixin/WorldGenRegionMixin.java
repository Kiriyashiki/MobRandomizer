package be.meiji.omakasemobu.mixin;

import be.meiji.omakasemobu.MobRandomizerMod;
import static be.meiji.omakasemobu.MobRandomizerMod.TAG_ID;
import static be.meiji.omakasemobu.MobRandomizerMod.createRandomizedEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionMixin {

  // yeah I'll have to figure this one out eventually
  @Shadow @Deprecated public abstract ServerLevel getLevel();

  @ModifyArg(
      method = "addFreshEntity",
      at = @At(
          value = "INVOKE",
          target = "Lnet/minecraft/world/level/chunk/ChunkAccess;addEntity(Lnet/minecraft/world/entity/Entity;)V"
      )
  )
  private Entity modifyEntityArgument(Entity entity) {
    if (entity == null || entity.entityTags().contains(TAG_ID) || !MobRandomizerMod.canRandomize(entity.getType())) {
      return entity;
    }

    ServerLevel world = this.getLevel();

    Entity newEntity = createRandomizedEntity(world, entity, false);

    if (newEntity instanceof Mob newMobEntity) {
      newMobEntity.setPersistenceRequired();
    }

    return newEntity;
  }
}

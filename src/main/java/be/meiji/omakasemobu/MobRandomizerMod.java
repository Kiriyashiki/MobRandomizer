package be.meiji.omakasemobu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MobRandomizerMod implements ModInitializer {

  public static final String MOD_ID = "mob_randomizer";
  public static final String TAG_ID = "randomized";
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  private static final EntityType<?>[] BLACKLIST = new EntityType<?>[]{
      EntityType.GIANT, EntityType.ENDER_DRAGON,
      EntityType.WITHER, EntityType.ILLUSIONER,
      EntityType.ZOMBIE_HORSE
  };

  private static final Map<Integer, Integer> RANDOMIZER = new HashMap<>();
  private static final Map<Integer, Integer> COMPLIMENT = new HashMap<>();


  public static boolean canRandomize(EntityType<?> entity) {
    // For mobs categorized as 「misc」 even though this category is mostly non-living entities.
    if ((EntityType.VILLAGER.equals(entity) || EntityType.SNOW_GOLEM.equals(entity)
         || EntityType.IRON_GOLEM.equals(entity)) && Arrays.stream(BLACKLIST)
            .noneMatch(e -> e == entity)) {
      return true;
    }
    return entity.getSpawnGroup() != SpawnGroup.MISC && !FeatureFlags.isNotVanilla(
        entity.getRequiredFeatures()) && Arrays.stream(BLACKLIST)
               .noneMatch(e -> e == entity);
  }

  @NotNull
  public static EntityType<?> randomize(EntityType<?> entityIn) {
    if (!canRandomize(entityIn)) {
      return entityIn;
    }

    int id = Registries.ENTITY_TYPE.getRawId(entityIn);
    return Registries.ENTITY_TYPE.get(RANDOMIZER.get(id));
  }

  @NotNull
  public static EntityType<?> compliment(EntityType<?> entityIn) {
    if (!canRandomize(entityIn)) {
      return entityIn;
    }

    int id = Registries.ENTITY_TYPE.getRawId(entityIn);
    return Registries.ENTITY_TYPE.get(COMPLIMENT.get(id));
  }

  public static Entity createRandomizedEntity(ServerWorld world, Entity entity, boolean doInit) {
    EntityType<?> newType = randomize(entity.getType());

    Entity newEntity = newType.create(world);
    if (newEntity == null) {
      return null;
    }

    if (doInit && entity instanceof MobEntity) {
      ((MobEntity) newEntity).initialize(world, world.getLocalDifficulty(newEntity.getBlockPos()),
          SpawnReason.TRIGGERED, null);
    }

    newEntity.copyPositionAndRotation(entity);

    if (entity instanceof MobEntity && newEntity instanceof MobEntity
        && ((MobEntity) entity).isPersistent()) {
      ((MobEntity) newEntity).setPersistent();
    }

    if (entity.hasVehicle()) {
      newEntity.startRiding(entity.getVehicle(), true);
    }
    newEntity.addCommandTag(TAG_ID);

    return newEntity;
  }

  private void onWorldLoad(MinecraftServer server, ServerWorld world) {
    ArrayList<Integer> ids = new ArrayList<>();
    RANDOMIZER.clear();
    COMPLIMENT.clear();

    Registries.ENTITY_TYPE.forEach((EntityType<?> entity) -> {
      if (canRandomize(entity)) {
        int id = Registries.ENTITY_TYPE.getRawId(entity);
        ids.add(id);
      }
    });

    Random random = new Random(world.getSeed());

    Collections.shuffle(ids, random);

    int size = ids.size();
    for (int i = 0; i + 1 < size; i += 2) {
      int id1 = ids.get(i);
      int id2 = ids.get(i + 1);
      RANDOMIZER.put(id1, id2);
      RANDOMIZER.put(id2, id1);
      COMPLIMENT.put(id1, id2);
      COMPLIMENT.put(id2, id1);
      LOGGER.info("Mapping {} <-> {}", Registries.ENTITY_TYPE.get(id1),
          Registries.ENTITY_TYPE.get(id2));
    }

    // If there's an odd number of entities, map the last entity to itself.
    if (size % 2 != 0) {
      int lastId = ids.get(size - 1);
      RANDOMIZER.put(lastId, lastId);
      COMPLIMENT.put(lastId, lastId);
      LOGGER.info("Mapping {} <-> {} (self mapping)", Registries.ENTITY_TYPE.get(lastId),
          Registries.ENTITY_TYPE.get(lastId));
    }
  }


  private void onServerTick(MinecraftServer server) {
    if (server.getTicks() % 20 == 0) {
      for (ServerWorld world : server.getWorlds()) {
        for (ServerPlayerEntity player : world.getPlayers()) {
          ArrayList<Entity> entities = (ArrayList<Entity>) world.getEntitiesByClass(Entity.class,
              new Box(player.getX() - 256, player.getY() - 256, player.getZ() - 256,
                  player.getX() + 256, player.getY() + 256, player.getZ() + 256),
              entity -> !entity.getCommandTags().contains(TAG_ID) && entity.isAlive()
                        && entity instanceof MobEntity);
          for (Entity entity : entities) {
            if (entity != null && !entity.getCommandTags().contains(TAG_ID)
                && MobRandomizerMod.canRandomize(entity.getType())) {
              Entity newEntity = createRandomizedEntity(world, entity, true);

              if (newEntity instanceof MobEntity) {
                ((MobEntity) newEntity).setPersistent();
              }

              if (newEntity != null) {
                entity.discard();
                world.spawnEntity(newEntity);
              }
            }
          }
        }
      }
    }
  }


  @Override
  public void onInitialize() {
    ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

    ServerWorldEvents.LOAD.register(this::onWorldLoad);
  }
}

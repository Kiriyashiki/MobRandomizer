package be.meiji.omakasemobu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
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
  public static final Random RANDOM = new Random();

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
    return entity.getSpawnGroup() != SpawnGroup.MISC && Arrays.stream(BLACKLIST)
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

  public static Entity createRandomizedEntity(ServerWorld world, Entity entity) {
    EntityType<?> newType = randomize(entity.getType());

    Entity newEntity = newType.create(world, SpawnReason.TRIGGERED);
    if (newEntity == null) {
      return null;
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

  private void onServerTick(MinecraftServer server) {
    if (server.getTicks() % 20 == 0) {
      for (ServerWorld world : server.getWorlds()) {
        for (ServerPlayerEntity player : world.getPlayers()) {
          ArrayList<Entity> entities = (ArrayList<Entity>) world.getEntitiesByClass(Entity.class,
              new Box(player.getX() - 160, player.getY() - 160, player.getZ() - 160,
                  player.getX() + 160, player.getY() + 160, player.getZ() + 160),
              entity -> !entity.getCommandTags().contains(TAG_ID) && entity.isAlive()
                        && entity instanceof MobEntity);
          for (Entity entity : entities) {
            if (entity != null && !entity.getCommandTags().contains(TAG_ID)
                && MobRandomizerMod.canRandomize(entity.getType())) {
              Entity newEntity = createRandomizedEntity(world, entity);

              world.spawnEntity(newEntity);
              entity.discard();
            }
          }
        }
      }
    }
  }


  @Override
  public void onInitialize() {
    ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

    ArrayList<Integer> ids = new ArrayList<>();
    ArrayList<Integer> available = new ArrayList<>();

    Registries.ENTITY_TYPE.forEach((EntityType<?> entity) -> {
      if (canRandomize(entity)) {
        int id = Registries.ENTITY_TYPE.getRawId(entity);
        ids.add(id);
        available.add(id);
      }
    });

    for (int id : ids) {
      if (available.isEmpty()) {
        LOGGER.error("Entity {} does not have a valid mapping!",
            Registries.ENTITY_TYPE.getId(Registries.ENTITY_TYPE.get(id)));
        break;
      }

      int mapTo = RANDOM.nextInt(available.size());
      RANDOMIZER.put(available.get(mapTo), id);
      COMPLIMENT.put(id, available.get(mapTo));
      LOGGER.info("Mapping {} -> {}", Registries.ENTITY_TYPE.get(available.get(mapTo)),
          Registries.ENTITY_TYPE.get(id));
      available.remove(mapTo);
    }
  }
}

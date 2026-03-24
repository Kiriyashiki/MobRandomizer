package be.meiji.omakasemobu;

import static be.meiji.omakasemobu.util.TomlHelper.parseStringList;
import static be.meiji.omakasemobu.util.TomlHelper.writeStringList;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MobRandomizerMod implements ModInitializer {

  public static final String MOD_ID = "mob_randomizer";
  public static final String TAG_ID = "randomized";
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  private static final EntityType<?>[] DEFAULT_BLACKLIST = new EntityType<?>[]{
      EntityType.GIANT, EntityType.ENDER_DRAGON,
      EntityType.WITHER, EntityType.ILLUSIONER,
      EntityType.ZOMBIE_HORSE
  };

  private static final List<EntityType<?>> blacklist = new ArrayList<>();

  private static final Map<Integer, Integer> RANDOMIZER = new HashMap<>();
  private static final Map<Integer, Integer> COMPLIMENT = new HashMap<>();


  public static boolean canRandomize(EntityType<?> entity) {
    // For mobs categorized as 「misc」 even though this category is mostly non-living entities.
    if ((EntityType.VILLAGER.equals(entity) || EntityType.SNOW_GOLEM.equals(entity)
         || EntityType.IRON_GOLEM.equals(entity) || EntityType.COPPER_GOLEM.equals(entity)) && blacklist.stream()
            .noneMatch(e -> e == entity)) {
      return true;
    }
    return entity.getCategory() != MobCategory.MISC && !FeatureFlags.isExperimental(
        entity.requiredFeatures()) && blacklist.stream().noneMatch(e -> e == entity);
  }

  @NotNull
  public static EntityType<?> randomize(EntityType<?> entityIn) {
    if (!canRandomize(entityIn)) {
      return entityIn;
    }

    int id = BuiltInRegistries.ENTITY_TYPE.getId(entityIn);
    return BuiltInRegistries.ENTITY_TYPE.byId(RANDOMIZER.get(id));
  }

  @NotNull
  public static EntityType<?> compliment(EntityType<?> entityIn) {
    if (!canRandomize(entityIn)) {
      return entityIn;
    }

    int id = BuiltInRegistries.ENTITY_TYPE.getId(entityIn);
    return BuiltInRegistries.ENTITY_TYPE.byId(COMPLIMENT.get(id));
  }

  public static Entity createRandomizedEntity(ServerLevel world, Entity entity, boolean doInit) {
    EntityType<?> newType = randomize(entity.getType());

    Entity newEntity = newType.create(world, EntitySpawnReason.TRIGGERED);
    if (newEntity == null) {
      return null;
    }

    newEntity.copyPosition(entity);

    if (doInit && entity instanceof Mob) {
      ((Mob) newEntity).finalizeSpawn(world, world.getCurrentDifficultyAt(newEntity.blockPosition()),
          EntitySpawnReason.TRIGGERED, null);
    }

    if (entity instanceof Mob mobEntity && newEntity instanceof Mob newMobEntity
        && mobEntity.isPersistenceRequired()) {
      newMobEntity.setPersistenceRequired();
    }

    if (entity.isPassenger()) {
      newEntity.startRiding(entity.getVehicle(), true, false);
    }
    newEntity.addTag(TAG_ID);

    return newEntity;
  }

  private static void config() {
    String path = "%s%s%s.toml".formatted(FabricLoader.getInstance().getConfigDir(), File.separator, MOD_ID);
    File configFile = new File(path);
    blacklist.clear();

    if (configFile.exists()) {
      try {
        List<String> entityNames = parseStringList(configFile, "blacklist");
        if (entityNames != null) {
          for (String entityName : entityNames) {
            for (EntityType<?> entity : BuiltInRegistries.ENTITY_TYPE) {
              if (entity.toShortString().equals(entityName)) {
                blacklist.add(entity);
              }
            }
          }
          LOGGER.info("Loaded blacklist : {}", blacklist);
          return;
        } else {
          LOGGER.error("The 'blacklist' key is empty in the config file. Loading default blacklist values.");
        }
      } catch (IOException e) {
        LOGGER.error("Failed to read config file with error : {}\nLoading default blacklist values.", e.getMessage());
      }
    } else {
      LOGGER.info("Initializing the config file...");

      List<String> defaultNames = new ArrayList<>();
      for (EntityType<?> type : DEFAULT_BLACKLIST) {
        defaultNames.add(type.toShortString());
      }

      try {
        writeStringList(configFile, "blacklist", defaultNames);
      } catch (IOException e) {
        LOGGER.error("Failed to create default config file at {}: {}", path, e.getMessage());
      }
    }

    blacklist.addAll(Arrays.asList(DEFAULT_BLACKLIST));
  }

  private void onWorldLoad(MinecraftServer server, ServerLevel world) {
    config();

    ArrayList<Integer> ids = new ArrayList<>();
    RANDOMIZER.clear();
    COMPLIMENT.clear();

    for (EntityType<?> entity : BuiltInRegistries.ENTITY_TYPE) {
      if (canRandomize(entity)) {
        int id = BuiltInRegistries.ENTITY_TYPE.getId(entity);
        ids.add(id);
      }
    }

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
      LOGGER.info("Mapping {} <-> {}", BuiltInRegistries.ENTITY_TYPE.byId(id1),
          BuiltInRegistries.ENTITY_TYPE.byId(id2));
    }

    // If there's an odd number of entities, map the last entity to itself.
    if (size % 2 != 0) {
      int lastId = ids.get(size - 1);
      RANDOMIZER.put(lastId, lastId);
      COMPLIMENT.put(lastId, lastId);
      LOGGER.info("Mapping {} <-> {} (self mapping)", BuiltInRegistries.ENTITY_TYPE.byId(lastId),
          BuiltInRegistries.ENTITY_TYPE.byId(lastId));
    }
  }


  private void onServerTick(MinecraftServer server) {
    if (server.getTickCount() % 20 == 0) {
      for (ServerLevel world : server.getAllLevels()) {
        for (ServerPlayer player : world.players()) {
          ArrayList<Entity> entities = (ArrayList<Entity>) world.getEntitiesOfClass(Entity.class,
              new AABB(player.getX() - 256, player.getY() - 256, player.getZ() - 256,
                  player.getX() + 256, player.getY() + 256, player.getZ() + 256),
              entity -> !entity.entityTags().contains(TAG_ID) && entity.isAlive()
                        && entity instanceof Mob);
          for (Entity entity : entities) {
            if (entity != null && !entity.entityTags().contains(TAG_ID)
                && MobRandomizerMod.canRandomize(entity.getType())) {
              Entity newEntity = createRandomizedEntity(world, entity, true);

              if (newEntity instanceof Mob newMobEntity) {
                newMobEntity.setPersistenceRequired();
              }

              if (newEntity != null) {
                entity.discard();
                world.addFreshEntity(newEntity);
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

    ServerLevelEvents.LOAD.register(this::onWorldLoad);
  }
}

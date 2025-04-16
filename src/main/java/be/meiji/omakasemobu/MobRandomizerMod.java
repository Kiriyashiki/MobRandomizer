package be.meiji.omakasemobu;

import static be.meiji.omakasemobu.util.TomlHelper.parseStringList;
import static be.meiji.omakasemobu.util.TomlHelper.writeStringList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.loader.api.FabricLoader;
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
         || EntityType.IRON_GOLEM.equals(entity)) && blacklist.stream()
            .noneMatch(e -> e == entity)) {
      return true;
    }
    return entity.getSpawnGroup() != SpawnGroup.MISC && !FeatureFlags.isNotVanilla(
        entity.getRequiredFeatures()) && blacklist.stream().noneMatch(e -> e == entity);
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

    Entity newEntity = newType.create(world, SpawnReason.TRIGGERED);
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

  private static void config() {
    String path = "%s%s%s.toml".formatted(FabricLoader.getInstance().getConfigDir(), File.separator, MOD_ID);
    File configFile = new File(path);
    blacklist.clear();

    if (configFile.exists()) {
      try {
        List<String> entityNames = parseStringList(configFile, "blacklist");
        if (entityNames != null) {
          for (String entityName : entityNames) {
            for (EntityType<?> entity : Registries.ENTITY_TYPE) {
              if (entity.getUntranslatedName().equals(entityName)) {
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
        defaultNames.add(type.getUntranslatedName());
      }

      try {
        writeStringList(configFile, "blacklist", defaultNames);
      } catch (IOException e) {
        LOGGER.error("Failed to create default config file at {}: {}", path, e.getMessage());
      }
    }

    blacklist.addAll(Arrays.asList(DEFAULT_BLACKLIST));
  }

  private void onWorldLoad(MinecraftServer server, ServerWorld world) {
    config();

    ArrayList<Integer> ids = new ArrayList<>();
    RANDOMIZER.clear();
    COMPLIMENT.clear();

    for (EntityType<?> entity : Registries.ENTITY_TYPE) {
      if (canRandomize(entity)) {
        int id = Registries.ENTITY_TYPE.getRawId(entity);
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

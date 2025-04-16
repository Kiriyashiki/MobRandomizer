package be.meiji.omakasemobu.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public class TomlHelper {

  /**
   * Reads a list of strings from the given TOML file for the specified key. The method searches for
   * a key and extracts its array value.
   *
   * @param configFile The TOML configuration file.
   * @param key        The key to search for.
   * @return A list of strings from the TOML array or null if the key is not found.
   * @throws IOException If the file cannot be read or if the TOML syntax is invalid.
   */
  @Nullable
  public static List<String> parseStringList(File configFile, String key) throws IOException {
    List<String> result = new ArrayList<>();
    String content = Files.readString(configFile.toPath()).trim();

    int keyIndex = content.indexOf(key);
    if (keyIndex == -1) {
      return null;
    }

    int startBracket = content.indexOf("[", keyIndex);
    int endBracket = content.indexOf("]", startBracket);
    if (startBracket == -1 || endBracket == -1) {
      throw new IOException("Invalid TOML syntax for key '" + key + "': missing brackets.");
    }

    String listContent = content.substring(startBracket + 1, endBracket);
    String[] entries = listContent.split(",");

    for (String entry : entries) {
      String trimmedEntry = entry.trim();
      if (trimmedEntry.startsWith("\"") && trimmedEntry.endsWith("\"")
          && trimmedEntry.length() >= 2) {
        String value = trimmedEntry.substring(1, trimmedEntry.length() - 1);
        result.add(value);
      }
    }
    return result;
  }

  /**
   * Writes a TOML formatted key with a list of string values to the specified file. The format
   * produced is: key = ["value1", "value2", ...]
   *
   * @param configFile The file to which to write the configuration.
   * @param key        The key to write.
   * @param values     The list of string values.
   * @throws IOException If the file cannot be written.
   */
  public static void writeStringList(File configFile, String key, List<String> values)
      throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append(key).append(" = [");
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append("\"").append(values.get(i)).append("\"");
    }
    sb.append("]\n");
    Files.writeString(configFile.toPath(), sb.toString());
  }
}

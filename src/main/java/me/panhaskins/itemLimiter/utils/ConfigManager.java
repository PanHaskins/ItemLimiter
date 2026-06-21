package me.panhaskins.itemLimiter.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;
import java.security.CodeSource;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public class ConfigManager {
    private final Plugin plugin;
    private final Map<String, FileConfiguration> configs = new HashMap<>();
    private final Map<String, File> files = new HashMap<>();

    /**
     * @param plugin  Instance of your plugin
     * @param patterns List of resource patterns, e.g. "functions/*.yml"
     */
    public ConfigManager(Plugin plugin, String... patterns) {
        this.plugin = plugin;
        for (String pattern : patterns) {
            registerPattern(pattern);
        }
    }

    private void registerPattern(String pattern) {
        String folder = "";
        String filePattern = pattern;
        int slash = pattern.lastIndexOf('/');
        if (slash >= 0) {
            folder = pattern.substring(0, slash);
            filePattern = pattern.substring(slash + 1);
        }

        Pattern regex = Pattern.compile(filePattern.replace("*", ".*"));
        File dir = new File(plugin.getDataFolder(), folder);
        if (!dir.exists()) dir.mkdirs();

        try {
            CodeSource codeSource = plugin.getClass().getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                try (JarFile jar = new JarFile(new File(codeSource.getLocation().toURI()))) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        if (entry.isDirectory()) continue;
                        String name = entry.getName();
                        if (!folder.isEmpty()) {
                            if (!name.startsWith(folder + "/")) continue;
                            name = name.substring(folder.length() + 1);
                        }
                        if (regex.matcher(name).matches()) {
                            String resourcePath = folder.isEmpty() ? name : folder + "/" + name;
                            File file = new File(dir, name);
                            if (!file.exists()) {
                                plugin.saveResource(resourcePath, false);
                            }
                            register(file, resourcePath);
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to load resources", e);
        }

        File[] filesArr = dir.listFiles((d, name) -> regex.matcher(name).matches());
        if (filesArr != null) {
            for (File file : filesArr) {
                String key = folder.isEmpty() ? file.getName() : folder + "/" + file.getName();
                if (!configs.containsKey(key)) {
                    register(file, key);
                }
            }
        }
    }

    private void register(File file, String key) {
        if (!file.exists()) {
            plugin.saveResource(key, false);
        }
        files.put(key, file);
        configs.put(key, YamlConfiguration.loadConfiguration(file));
    }

    /**
     * Get config by its relative path (e.g. "functions/something.yml")
     */
    public FileConfiguration getConfig(String key) {
        return configs.get(key);
    }

    public void reload(String key) {
        File file = files.get(key);
        if (file != null) {
            configs.put(key, YamlConfiguration.loadConfiguration(file));
        }
    }

    public void reloadAll() {
        for (String key : configs.keySet()) {
            reload(key);
        }
    }

    public Set<String> getConfigKeys() {
        return configs.keySet();
    }
}

package me.panhaskins.itemLimiter.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import me.clip.placeholderapi.PlaceholderAPI;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Translates mixed-format text into Adventure {@link Component Components}.
 *
 * <p>Converts legacy Minecraft formatting codes, hex colors, gradient shortcodes,
 * and MiniMessage tags into a unified MiniMessage representation, then deserializes
 * into an Adventure Component.
 *
 * <h2>Supported Formats</h2>
 * <table>
 *   <caption>Format categories and their syntax</caption>
 *   <tr><th>Category</th><th>Syntax</th></tr>
 *   <tr><td>Legacy colors</td><td>{@code &0}-{@code &f}, {@code §0}-{@code §f}</td></tr>
 *   <tr><td>Legacy decorations</td><td>{@code &l &o &m &n &k} (auto-closed on color change or reset)</td></tr>
 *   <tr><td>Legacy reset</td><td>{@code &r}, {@code §r}</td></tr>
 *   <tr><td>Hex colors</td><td>{@code &#RRGGBB}, {@code §#RRGGBB}, {@code &x&R&R&G&G&B&B}, {@code {#RRGGBB}}</td></tr>
 *   <tr><td>Gradient shortcodes</td><td>{@code {#RRGGBB>}text{#RRGGBB<}} with midpoints via {@code {#RRGGBB<>}}</td></tr>
 *   <tr><td>MiniMessage tags</td><td>All standard tags: hover, click, insertion, font, lang, keybind,
 *       selector, nbt, gradient, rainbow, transition, newline, etc.</td></tr>
 *   <tr><td>PlaceholderAPI</td><td>{@code %placeholder%} (resolved if the plugin is present)</td></tr>
 * </table>
 *
 * <h2>Pipeline</h2>
 * <pre>{@code input → PlaceholderAPI → translateFormats (single pass) → MiniMessage.deserialize()}</pre>
 *
 * <h2>Platform Support</h2>
 * <ul>
 *   <li><b>Paper:</b> {@link #translate(String)} returns a native {@link Component}.
 *       {@code Player} implements {@code Audience}, so
 *       {@code player.sendMessage(component)} works directly.</li>
 *   <li><b>Spigot:</b> {@link #translateToBaseComponents(String)} returns
 *       {@link BaseComponent BaseComponent[]}, or {@link #translateToLegacy(String)}
 *       returns a legacy §-coded string.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Simple translation
 * Component msg = Messager.translate("&aHello <bold>World</bold>!");
 *
 * // With PlaceholderAPI
 * Component msg = Messager.translate("Welcome %player_name%!", player);
 *
 * // Platform-aware sending
 * Messager.sendMessage(player, "&c&lWarning: <hover:show_text:'Details'>hover</hover>");
 *
 * // Spigot BaseComponent output
 * BaseComponent[] bungee = Messager.translateToBaseComponents("&#ff8800Orange");
 * }</pre>
 *
 * <h2>Design Notes</h2>
 * <ul>
 *   <li>Legacy codes inside MiniMessage tag arguments are intentionally <b>not</b> translated,
 *       preventing corruption of click commands, insertion text, and other tag values.
 *       Use MiniMessage syntax inside tag arguments:
 *       {@code <hover:show_text:'<green>text'>}.</li>
 *   <li>All format conversion happens in a single char-by-char pass with zero regex
 *       and zero intermediate {@link String} allocations.</li>
 * </ul>
 *
 * <p>This class is stateless and thread-safe. All methods are static and side-effect-free.
 *
 * @author PanHaskins
 * @since 1.0
 * @see MiniMessage
 * @see Component
 * @see BaseComponent
 */
public final class Messager {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final boolean PLACEHOLDER_API =
            Bukkit.getServer() != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

    private static final Map<Character, String> COLOUR_MAP = Map.ofEntries(
            Map.entry('0', "<black>"),          Map.entry('8', "<dark_gray>"),
            Map.entry('1', "<dark_blue>"),      Map.entry('9', "<blue>"),
            Map.entry('2', "<dark_green>"),     Map.entry('a', "<green>"),
            Map.entry('3', "<dark_aqua>"),      Map.entry('b', "<aqua>"),
            Map.entry('4', "<dark_red>"),       Map.entry('c', "<red>"),
            Map.entry('5', "<dark_purple>"),    Map.entry('d', "<light_purple>"),
            Map.entry('6', "<gold>"),           Map.entry('e', "<yellow>"),
            Map.entry('7', "<gray>"),           Map.entry('f', "<white>")
    );

    private static final Map<Character, String> DECORATION_MAP = Map.ofEntries(
            Map.entry('k', "obfuscated"),
            Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"),
            Map.entry('n', "underlined"),
            Map.entry('o', "italic")
    );

    private Messager() {}

    // ──────────────── Public API: Component output (Paper) ────────────────

    /**
     * Translates a message containing mixed formatting codes into an Adventure Component.
     *
     * @param message the message to translate, may contain legacy codes, hex colors,
     *                gradient shortcodes, and/or MiniMessage tags; may be {@code null}
     * @return the translated component, or {@link Component#empty()} if the message
     *         is {@code null} or empty
     * @see #translate(String, Player)
     * @see #translate(String, OfflinePlayer)
     */
    public static Component translate(String message) {
        return internalTranslate(message, null);
    }

    /**
     * Translates a message into an Adventure Component, resolving PlaceholderAPI
     * placeholders for the given player.
     *
     * @param message the message to translate; may be {@code null}
     * @param viewer  the online player whose placeholders to resolve
     * @return the translated component, or {@link Component#empty()} if the message
     *         is {@code null} or empty
     * @see #translate(String, OfflinePlayer)
     */
    public static Component translate(String message, Player viewer) {
        return internalTranslate(message, viewer);
    }

    /**
     * Translates a message into an Adventure Component, resolving PlaceholderAPI
     * placeholders for the given offline player.
     *
     * @param message the message to translate; may be {@code null}
     * @param viewer  the offline player whose placeholders to resolve
     * @return the translated component, or {@link Component#empty()} if the message
     *         is {@code null} or empty
     */
    public static Component translate(String message, OfflinePlayer viewer) {
        return internalTranslate(message, viewer);
    }

    /**
     * Translates a list of messages into Adventure Components.
     *
     * @param lines the messages to translate; must not be {@code null}
     * @return an unmodifiable-size list of translated components, in the same order
     * @throws NullPointerException if {@code lines} is {@code null}
     * @see #translate(List, OfflinePlayer)
     */
    public static List<Component> translate(List<String> lines) {
        return translate(lines, null);
    }

    /**
     * Translates a list of messages into Adventure Components with placeholder support.
     *
     * @param lines  the messages to translate; must not be {@code null}
     * @param viewer the offline player whose placeholders to resolve; may be {@code null}
     * @return a list of translated components, in the same order as {@code lines}
     * @throws NullPointerException if {@code lines} is {@code null}
     */
    public static List<Component> translate(List<String> lines, OfflinePlayer viewer) {
        Objects.requireNonNull(lines, "lines");
        List<Component> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(internalTranslate(line, viewer));
        }
        return result;
    }

    // ──────────────── Public API: BaseComponent output (Spigot) ────────────────

    /**
     * Translates a message into a BungeeCord {@link BaseComponent} array.
     *
     * <p>Internally uses {@code BungeeComponentSerializer}, which is lazy-loaded
     * to avoid {@link ClassNotFoundException} on servers without
     * {@code adventure-platform-bungeecord}. The {@link BaseComponent} return type
     * itself is always available on both Paper and Spigot.
     *
     * @param message the message to translate; may be {@code null}
     * @return the translated BaseComponent array
     * @see #translateToBaseComponents(String, OfflinePlayer)
     */
    public static BaseComponent[] translateToBaseComponents(String message) {
        return BungeeCompat.serialize(translate(message));
    }

    /**
     * Translates a message into a BungeeCord {@link BaseComponent} array
     * with placeholder support.
     *
     * @param message the message to translate; may be {@code null}
     * @param viewer  the offline player whose placeholders to resolve; may be {@code null}
     * @return the translated BaseComponent array
     */
    public static BaseComponent[] translateToBaseComponents(String message, OfflinePlayer viewer) {
        return BungeeCompat.serialize(translate(message, viewer));
    }

    /**
     * Translates a list of messages into BungeeCord {@link BaseComponent} arrays.
     *
     * @param lines the messages to translate; must not be {@code null}
     * @return a list of BaseComponent arrays, in the same order as {@code lines}
     * @throws NullPointerException if {@code lines} is {@code null}
     */
    public static List<BaseComponent[]> translateToBaseComponents(List<String> lines) {
        return translateToBaseComponents(lines, null);
    }

    /**
     * Translates a list of messages into BungeeCord {@link BaseComponent} arrays
     * with placeholder support.
     *
     * @param lines  the messages to translate; must not be {@code null}
     * @param viewer the offline player whose placeholders to resolve; may be {@code null}
     * @return a list of BaseComponent arrays, in the same order as {@code lines}
     * @throws NullPointerException if {@code lines} is {@code null}
     */
    public static List<BaseComponent[]> translateToBaseComponents(List<String> lines, OfflinePlayer viewer) {
        Objects.requireNonNull(lines, "lines");
        List<BaseComponent[]> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(translateToBaseComponents(line, viewer));
        }
        return result;
    }

    // ──────────────── Public API: Legacy string output ────────────────

    /**
     * Translates a message into a legacy §-coded string.
     *
     * <p>Useful for Spigot APIs that only accept plain strings with section-sign
     * formatting. Note that advanced formatting (hover, click, gradients) is lost
     * during this conversion, as the legacy format cannot represent them.
     *
     * @param message the message to translate; may be {@code null}
     * @return the legacy-formatted string
     * @see #translateToLegacy(String, OfflinePlayer)
     */
    public static String translateToLegacy(String message) {
        return LegacyCompat.serialize(translate(message));
    }

    /**
     * Translates a message into a legacy §-coded string with placeholder support.
     *
     * @param message the message to translate; may be {@code null}
     * @param viewer  the offline player whose placeholders to resolve; may be {@code null}
     * @return the legacy-formatted string
     */
    public static String translateToLegacy(String message, OfflinePlayer viewer) {
        return LegacyCompat.serialize(translate(message, viewer));
    }

    // ──────────────── Public API: Platform-aware sending ────────────────

    /**
     * Sends a translated message to a player, auto-detecting the server platform.
     *
     * <p>On Paper, uses the native {@code Audience.sendMessage(Component)} method.
     * On Spigot, falls back to {@code player.spigot().sendMessage(BaseComponent[])}.
     * For advanced Spigot integration, consider using {@code BukkitAudiences} from
     * {@code adventure-platform-bukkit} instead.
     *
     * @param player  the recipient; must not be {@code null}
     * @param message the message to translate and send; no-op if {@code null} or empty
     * @throws NullPointerException if {@code player} is {@code null}
     * @see #sendMessage(Player, List)
     * @see #sendActionBar(Player, String)
     */
    public static void sendMessage(Player player, String message) {
        Objects.requireNonNull(player, "player");
        if (message == null || message.isEmpty()) return;
        Component component = translate(message, player);
        if (PlatformCompat.IS_PAPER) {
            player.sendMessage(component);
        } else {
            player.spigot().sendMessage(BungeeCompat.serialize(component));
        }
    }

    /**
     * Sends multiple translated messages to a player sequentially.
     *
     * @param player   the recipient; must not be {@code null}
     * @param messages the messages to translate and send; must not be {@code null}
     * @throws NullPointerException if {@code player} or {@code messages} is {@code null}
     * @see #sendMessage(Player, String)
     */
    public static void sendMessage(Player player, List<String> messages) {
        Objects.requireNonNull(messages, "messages");
        for (String message : messages) {
            sendMessage(player, message);
        }
    }

    /**
     * Sends a translated action bar message, auto-detecting the server platform.
     *
     * <p>On Paper, uses {@code Audience.sendActionBar(Component)}.
     * On Spigot, uses {@code player.spigot().sendMessage(ChatMessageType.ACTION_BAR, ...)}.
     *
     * @param player  the recipient; must not be {@code null}
     * @param message the message to translate and send; no-op if {@code null} or empty
     * @throws NullPointerException if {@code player} is {@code null}
     * @see #sendMessage(Player, String)
     */
    public static void sendActionBar(Player player, String message) {
        Objects.requireNonNull(player, "player");
        if (message == null || message.isEmpty()) return;
        Component component = translate(message, player);
        if (PlatformCompat.IS_PAPER) {
            player.sendActionBar(component);
        } else {
            player.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    BungeeCompat.serialize(component)
            );
        }
    }

    private static Component internalTranslate(String message, OfflinePlayer viewer) {
        if (message == null || message.isEmpty()) return Component.empty();

        String processed = applyPlaceholders(message, viewer);
        processed = translateFormats(processed);

        try {
            return MINI_MESSAGE.deserialize(processed);
        } catch (Exception e) {
            return Component.text(message);
        }
    }

    private static String applyPlaceholders(String input, OfflinePlayer viewer) {
        return viewer != null && PLACEHOLDER_API
                ? PlaceholderAPI.setPlaceholders(viewer, input)
                : input;
    }

    private static String translateFormats(String input) {
        boolean hasLegacy = input.indexOf('&') >= 0 || input.indexOf('§') >= 0;
        boolean hasBrace  = input.indexOf('{') >= 0;
        if (!hasLegacy && !hasBrace) return input;

        char[] chars = input.toCharArray();
        int len = chars.length;
        StringBuilder out = new StringBuilder(len + 32);
        Deque<String> decorStack = new ArrayDeque<>();

        // Gradient state (lazy-initialized on first gradient token)
        boolean gradOpen = false;
        StringBuilder gradBuf = null;
        List<String> gradColors = null;

        for (int i = 0; i < len; i++) {
            char c = chars[i];
            StringBuilder target = gradOpen ? gradBuf : out;

            // ── 1. Skip MiniMessage tags verbatim ──
            if (c == '<' && i + 1 < len && isMiniMessageTagStart(chars[i + 1])) {
                int end = findTagEnd(chars, i, len);
                if (end > i) {
                    target.append(chars, i, end - i + 1);
                    i = end;
                    continue;
                }
            }

            // ── 2. Brace patterns: {#RRGGBB...} ──
            if (c == '{' && i + 8 < len && chars[i + 1] == '#' && isHexSequence(chars, i + 2, 6)) {
                int afterHex = i + 8;

                // {#RRGGBB} — plain brace hex color
                if (afterHex < len && chars[afterHex] == '}') {
                    closeDecorations(decorStack, target);
                    target.append("<#").append(chars, i + 2, 6).append('>');
                    i = afterHex;
                    continue;
                }

                // {#RRGGBB>} — gradient start
                if (afterHex + 1 < len && chars[afterHex] == '>' && chars[afterHex + 1] == '}') {
                    if (gradOpen) {
                        out.append("{#").append(gradColors.getFirst()).append('>').append(gradBuf);
                    }
                    gradOpen = true;
                    if (gradColors == null) gradColors = new ArrayList<>(4); else gradColors.clear();
                    if (gradBuf == null) gradBuf = new StringBuilder(); else gradBuf.setLength(0);
                    gradColors.add(lowercaseHex(chars, i + 2));
                    i = afterHex + 1;
                    continue;
                }

                // {#RRGGBB<} — gradient end
                if (afterHex + 1 < len && chars[afterHex] == '<' && chars[afterHex + 1] == '}') {
                    if (gradOpen) {
                        gradColors.add(lowercaseHex(chars, i + 2));
                        out.append("<gradient:#")
                                .append(String.join(":#", gradColors))
                                .append('>')
                                .append(gradBuf)
                                .append("</gradient>");
                        gradOpen = false;
                    } else {
                        target.append(chars, i, afterHex + 2 - i);
                    }
                    i = afterHex + 1;
                    continue;
                }

                // {#RRGGBB<>} — gradient midpoint
                if (afterHex + 2 < len && chars[afterHex] == '<'
                        && chars[afterHex + 1] == '>' && chars[afterHex + 2] == '}') {
                    if (gradOpen) {
                        gradColors.add(lowercaseHex(chars, i + 2));
                    } else {
                        target.append(chars, i, afterHex + 3 - i);
                    }
                    i = afterHex + 2;
                    continue;
                }
            }

            // ── 3. Legacy &/§ codes ──
            if ((c == '&' || c == '§') && i + 1 < len) {
                char code = Character.toLowerCase(chars[i + 1]);

                // &#RRGGBB / §#RRGGBB — simple hex
                if (code == '#' && i + 7 < len && isHexSequence(chars, i + 2, 6)) {
                    closeDecorations(decorStack, target);
                    target.append("<#").append(chars, i + 2, 6).append('>');
                    i += 7;
                    continue;
                }

                // &x&R&R&G&G&B&B — Spigot-style hex
                if (code == 'x' && i + 13 < len) {
                    StringBuilder hex = new StringBuilder(6);
                    boolean valid = true;
                    for (int j = 0; j < 6; j++) {
                        char prefix = chars[i + 2 + j * 2];
                        char digit  = chars[i + 3 + j * 2];
                        if ((prefix != '&' && prefix != '§') || Character.digit(digit, 16) < 0) {
                            valid = false;
                            break;
                        }
                        hex.append(digit);
                    }
                    if (valid) {
                        closeDecorations(decorStack, target);
                        target.append("<#").append(hex).append('>');
                        i += 13;
                        continue;
                    }
                }

                // Named colour
                String colour = COLOUR_MAP.get(code);
                if (colour != null) {
                    closeDecorations(decorStack, target);
                    target.append(colour);
                    i++;
                    continue;
                }

                // Decoration
                String deco = DECORATION_MAP.get(code);
                if (deco != null) {
                    target.append('<').append(deco).append('>');
                    decorStack.push(deco);
                    i++;
                    continue;
                }

                // Reset
                if (code == 'r') {
                    closeDecorations(decorStack, target);
                    target.append("<reset>");
                    i++;
                    continue;
                }
            }

            target.append(c);
        }

        // Handle unclosed gradient — restore as literal text
        if (gradOpen) {
            out.append("{#").append(gradColors.getFirst()).append('>').append(gradBuf);
        }

        closeDecorations(decorStack, out);
        return out.toString();
    }

    private static boolean isMiniMessageTagStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || c == '/' || c == '!' || c == '#';
    }

    private static int findTagEnd(char[] chars, int start, int len) {
        for (int i = start + 1; i < len; i++) {
            char c = chars[i];
            if (c == '\'' || c == '"') {
                char quote = c;
                for (i++; i < len; i++) {
                    if (chars[i] == '\\' && i + 1 < len) { i++; continue; }
                    if (chars[i] == quote) break;
                }
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    private static boolean isHexSequence(char[] chars, int start, int count) {
        for (int j = 0; j < count; j++) {
            if (Character.digit(chars[start + j], 16) < 0) return false;
        }
        return true;
    }

    private static String lowercaseHex(char[] chars, int start) {
        char[] hex = new char[6];
        for (int j = 0; j < 6; j++) {
            hex[j] = Character.toLowerCase(chars[start + j]);
        }
        return new String(hex);
    }

    private static void closeDecorations(Deque<String> decorations, StringBuilder builder) {
        while (!decorations.isEmpty()) {
            builder.append("</").append(decorations.pop()).append('>');
        }
    }

    private static final class BungeeCompat {
        private static final net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer
                SERIALIZER = net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer.get();

        static BaseComponent[] serialize(Component component) {
            return SERIALIZER.serialize(component);
        }
    }

    private static final class LegacyCompat {
        private static final net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                SERIALIZER = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();

        static String serialize(Component component) {
            return SERIALIZER.serialize(component);
        }
    }

    private static final class PlatformCompat {
        static final boolean IS_PAPER;
        static {
            boolean paper;
            try {
                Class.forName("io.papermc.paper.configuration.PaperConfigurations");
                paper = true;
            } catch (ClassNotFoundException e) {
                paper = false;
            }
            IS_PAPER = paper;
        }
    }
}

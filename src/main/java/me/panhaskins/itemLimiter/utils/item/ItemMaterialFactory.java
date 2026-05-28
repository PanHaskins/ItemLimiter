package me.panhaskins.itemLimiter.utils.item;

/**
 * Creates an {@link ItemMaterial} from a YAML {@code material:} value.
 *
 * <p>Used only at config-parse time. The registry walks factories in descending
 * prefix-length order; the first match wins and the prefix is stripped before
 * {@link #parse(String)} is called.
 */
public interface ItemMaterialFactory {

    /** YAML prefix this factory consumes (e.g. {@code "oraxen-"}); {@code ""} marks the fallback. */
    String prefix();

    /** Parse the id portion (raw value with prefix already removed). */
    ItemMaterial parse(String idPart);
}

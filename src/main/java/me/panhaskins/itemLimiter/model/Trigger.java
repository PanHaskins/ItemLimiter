package me.panhaskins.itemLimiter.model;

/**
 * Types of actions that can trigger an item cooldown.
 */
public enum Trigger {
    DAMAGE,
    CONSUME,
    THROW,
    BLOCK_BREAK,
    BLOCK_PLACE,
    FISHING,
    SHEAR,
    INTERACT
}

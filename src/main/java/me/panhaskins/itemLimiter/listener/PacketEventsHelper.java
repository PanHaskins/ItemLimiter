package me.panhaskins.itemLimiter.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCooldown;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PacketEventsHelper implements TriggerListener.CooldownHelper {

    private final Map<Material, ItemType> typeCache = new EnumMap<>(Material.class);
    private final Map<UUID, Map<ItemType, Long>> cooldownExpiry = new ConcurrentHashMap<>();

    public PacketEventsHelper() {
        for (Field field : ItemTypes.class.getFields()) {
            if (field.getType() == ItemType.class) {
                Material material = Material.matchMaterial(field.getName());
                if (material != null) {
                    try {
                        typeCache.put(material, (ItemType) field.get(null));
                    } catch (IllegalAccessException ignored) { }
                }
            }
        }

        PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract() {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                if (event.getPacketType() != PacketType.Play.Server.SET_COOLDOWN) return;
                Player player = event.getPlayer();
                Map<ItemType, Long> expiry = cooldownExpiry.get(player.getUniqueId());
                if (expiry == null) return;
                WrapperPlayServerSetCooldown wrapper = new WrapperPlayServerSetCooldown(event);
                Long expiresAt = expiry.get(wrapper.getItem());
                if (expiresAt == null) return;
                long remaining = expiresAt - System.currentTimeMillis();
                if (remaining <= 0) {
                    expiry.remove(wrapper.getItem());
                    return;
                }
                int ticks = (int) (remaining / 50);
                if (ticks > wrapper.getCooldownTicks()) {
                    wrapper.setCooldownTicks(ticks);
                }
            }
        });
    }

    @Override
    public void sendCooldown(Player player, Material material, int ticks) {
        ItemType type = typeCache.get(material);
        if (type != null) {
            WrapperPlayServerSetCooldown packet = new WrapperPlayServerSetCooldown(type, ticks);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        }
    }

    @Override
    public void trackCooldown(UUID playerId, Material material, long expiresAt) {
        ItemType type = typeCache.get(material);
        if (type != null) {
            cooldownExpiry.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                    .put(type, expiresAt);
        }
    }

    @Override
    public void onQuit(UUID playerId) {
        cooldownExpiry.remove(playerId);
    }
}

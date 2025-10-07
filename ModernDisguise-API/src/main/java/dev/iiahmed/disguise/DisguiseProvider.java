package dev.iiahmed.disguise;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import dev.iiahmed.disguise.util.DefaultEntityProvider;
import dev.iiahmed.disguise.util.DisguiseUtil;
import dev.iiahmed.disguise.util.Version;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public abstract class DisguiseProvider {

    private Pattern namePattern = Pattern.compile("^[a-zA-Z0-9_]{1,16}$");
    private boolean overrideChat = Version.isOver(18);
    private int nameLength = 16;

    private final Map<UUID, PlayerInfo> playerInfo = new ConcurrentHashMap<>();

    protected Plugin plugin;
    protected boolean entityDisguises, checkOnlineNames = true;
    protected EntityProvider entityProvider = new DefaultEntityProvider();

    /**
     * Set the username {@link Pattern} that is
     * used to validate nicknames.
     *
     * @param pattern the name pattern
     */
    public DisguiseProvider setNamePattern(@NotNull final Pattern pattern) {
        this.namePattern = pattern;
        return this;
    }

    /**
     * Set the maximum username length
     * allowed for nicknames.
     *
     * @param length the max length
     */
    public DisguiseProvider setNameLength(final int length) {
        this.nameLength = length;
        return this;
    }
    
    /**
     * Sets whether ModernDisguise should check if a player with a similar name
     * is currently online before allowing a disguise. This can help prevent
     * naming conflicts or impersonation issues by ensuring that a nickname
     * doesn't collide with existing players online.
     *
     * @param checkOnlineNames A boolean flag that determines the behavior:
     *                         - true: Enable online name checks. Disguise names
     *                           will be validated against currently online player names
     *                           to prevent duplicates or conflicts.
     *                         - false: Disable online name checks. Disguise names
     *                           can be set without regard to currently online player names.
     *
     * @see DisguiseProvider#shouldCheckOnlineNames()
     */
    @ApiStatus.Experimental
    public DisguiseProvider checkOnlineNames(final boolean checkOnlineNames) {
        this.checkOnlineNames = checkOnlineNames;
        return this;
    }

    /**
     * Determines whether ModernDisguise is currently configured to check if players with a similar
     * name to that being disguised to are currently online while disguising.
     *
     * @return True if ModernDisguise is set to check for the names, false otherwise.
     * @see DisguiseProvider#checkOnlineNames(boolean) (boolean)
     */
    public boolean shouldCheckOnlineNames() {
        return this.checkOnlineNames;
    }

    /**
     * Controls the ability of ModernDisguise to modify the chat behavior for servers
     * utilizing the Mojang Report feature, enabling disguised players to interact with chat.
     * This setting is designed to be compatible with existing chat plugins and should not
     * disrupt their functionality.
     *
     * @param overrideChat A boolean flag to allow or disallow the chat system override.
     * @see DisguiseProvider#shouldOverrideChat()
     */
    public DisguiseProvider allowOverrideChat(final boolean overrideChat) {
        this.overrideChat = overrideChat;
        return this;
    }

    /**
     * Determines whether ModernDisguise is currently configured to override the server's chat system.
     *
     * @return True if ModernDisguise is set to override the chat system, false otherwise.
     * @see DisguiseProvider#allowOverrideChat(boolean)
     */
    public boolean shouldOverrideChat() {
        return this.overrideChat;
    }

    /**
     * Retrieves the current {@link EntityProvider} instance.
     *
     * @return The current {@link EntityProvider} instance, or null if none is set.
     */
    public EntityProvider getEntityProvider() {
        return entityProvider;
    }

    /**
     * Sets the {@link EntityProvider} instance to be used.
     *
     * @param entityProvider The {@link EntityProvider} to set. This parameter cannot be null.
     * @throws NullPointerException if the provided entityProvider is null.
     */
    public void setEntityProvider(final @NotNull EntityProvider entityProvider) {
        this.entityProvider = entityProvider;
    }

    /**
     * Disguises a {@link Player} with a valid {@link Disguise}
     *
     * @param player   the disguising player
     * @param disguise the disguise that the player should use
     * @return the response of the disguise action (like reasons of failure or so)
     * @see DisguiseProvider#undisguise(Player)
     */
   public final @NotNull DisguiseResponse disguise(@NotNull final Player player, @NotNull final Disguise disguise) {
        final Logger logger = Bukkit.getLogger();
        logger.info("[ModernDisguise-Debug] ----------------- Starting Disguise -----------------");
        logger.info("[ModernDisguise-Debug] Player: " + player.getName() + " (" + player.getUniqueId() + ")");
        logger.info("[ModernDisguise-Debug] Requested Disguise Name: " + disguise.getName());
        logger.info("[ModernDisguise-Debug] Requested Disguise Has Skin: " + disguise.hasSkin());
        logger.info("[ModernDisguise-Debug] Requested Disguise Has Entity: " + disguise.hasEntity());

        if (!isVersionSupported()) {
            logger.warning("[ModernDisguise-Debug] Disguise FAIL: Version not supported.");
            return DisguiseResponse.FAIL_VERSION_NOT_SUPPORTED;
        }

        if (plugin == null || !plugin.isEnabled()) {
            logger.warning("[ModernDisguise-Debug] Disguise FAIL: Plugin not initialized.");
            return DisguiseResponse.FAIL_PLUGIN_NOT_INITIALIZED;
        }

        if (disguise.isEmpty()) {
            logger.warning("[ModernDisguise-Debug] Disguise FAIL: Disguise object is empty.");
            return DisguiseResponse.FAIL_EMPTY_DISGUISE;
        }

        if (disguise.hasEntity() && (!entityDisguises || !this.entityProvider.isSupported(disguise.getEntity()))) {
            logger.warning("[ModernDisguise-Debug] Disguise failed due to entity disguises are disabled or this entity is not supported.");
            return DisguiseResponse.FAIL_ENTITY_NOT_SUPPORTED;
        }

        String realName = player.getName();
        String nickname = realName;
        logger.info("[ModernDisguise-Debug] Getting player GameProfile...");
        final GameProfile profile = DisguiseUtil.getProfile(player);
        if (!player.isOnline() || profile == null) {
            logger.warning("[ModernDisguise-Debug] Disguise failed due to destined player is offline or profile could not be found.");
            return DisguiseResponse.FAIL_PROFILE_NOT_FOUND;
        }
        logger.info("[ModernDisguise-Debug] GameProfile found.");

        if (disguise.hasName() && !disguise.getName().equals(player.getName())) {
            final String name = disguise.getName();
            logger.info("[ModernDisguise-Debug] Name change requested to: " + name);

            if (name.length() > nameLength) {
                logger.warning("[ModernDisguise-Debug] Disguise failed due to provided name '" + name + "' is too long (>" + nameLength + ").");
                return DisguiseResponse.FAIL_NAME_TOO_LONG;
            }

            if (!namePattern.matcher(name).matches()) {
                logger.warning("[ModernDisguise-Debug] Disguise FAIL: Name '" + name + "' does not match pattern.");
                return DisguiseResponse.FAIL_NAME_INVALID;
            }

            if (this.checkOnlineNames) {
                logger.info("[ModernDisguise-Debug] Checking if player with name '" + name + "' is online...");
                final Player found = DisguiseUtil.getPlayer(name);
                if (found != null && found.isOnline()) {
                    logger.warning("[ModernDisguise-Debug] Disguise FAIL: Player with name '" + name + "' is already online.");
                    return DisguiseResponse.FAIL_NAME_ALREADY_ONLINE;
                }
            }

            nickname = name;
            try {
                logger.info("[ModernDisguise-Debug] Setting profile name in GameProfile object and registering name with server.");
                DisguiseUtil.PROFILE_NAME.set(profile, name);
                DisguiseUtil.register(name, player);
            } catch (final IllegalAccessException e) {
                logger.severe("[ModernDisguise-Debug] Disguise FAIL: A reflection error occurred while changing name.");
                e.printStackTrace();
                return DisguiseResponse.FAIL_NAME_CHANGE_EXCEPTION;
            }
        }

        Skin realSkin = null;
        if (disguise.hasSkin()) {
            logger.info("[ModernDisguise-Debug] Skin change requested. Backing up original skin...");
            final Optional<Property> optional = profile.getProperties().get("textures").stream().findFirst();
            if (optional.isPresent()) {
                realSkin = DisguiseUtil.getSkin(optional.get());
                profile.getProperties().removeAll("textures");
                logger.info("[ModernDisguise-Debug] Original skin backed up. Applying new skin.");
            } else {
                logger.warning("[ModernDisguise-Debug] Could not find original skin property to back up.");
            }
            profile.getProperties().put("textures", new Property("textures", disguise.getTextures(), disguise.getSignature()));
        }

        Entity entity = disguise.getEntity();
        if (isDisguised(player)) {
            logger.info("[ModernDisguise-Debug] Player was already disguised. Cleaning up old disguise data.");
            final PlayerInfo info = this.playerInfo.remove(player.getUniqueId());
            if (info.hasName()) {
                logger.info("[ModernDisguise-Debug] Unregistering old nickname: " + info.getNickname());
                DisguiseUtil.unregister(info.getNickname());
                if (disguise.hasName()) nickname = info.getNickname();
            }
            if (info.hasSkin()) {
                realSkin = info.getSkin();
            }
            realName = info.getName();

            if (info.hasEntity() && !disguise.hasEntity()) {
                entity = info.getEntity();
            }
        }

        logger.info("[ModernDisguise-Debug] Storing PlayerInfo. RealName: " + realName + ", Nickname: " + nickname);
        playerInfo.put(player.getUniqueId(), new PlayerInfo(realName, nickname, realSkin, entity));

        if (disguise.hasName() || disguise.hasSkin()) {
            logger.info("[ModernDisguise-Debug] Refreshing player for name/skin changes...");
            final boolean flying = player.isFlying();
            final int foodLevel = player.getFoodLevel();
            final float saturation = player.getSaturation();
            final float exhaustion = player.getExhaustion();

            this.refreshAsPlayer(player);

            player.teleport(player.getLocation());
            player.setFlying(flying);
            player.setFoodLevel(foodLevel);
            player.setSaturation(saturation);
            player.setExhaustion(exhaustion);
            logger.info("[ModernDisguise-Debug] Player refresh complete.");
        }

        if (disguise.hasEntity()) {
            logger.info("[ModernDisguise-Debug] Refreshing player as an entity...");
            refreshAsEntity(player, true, player.getWorld().getPlayers().toArray(new Player[0]));
            logger.info("[ModernDisguise-Debug] Entity refresh complete.");
        }

        logger.info("[ModernDisguise-Debug] Disguise SUCCESS.");
        logger.info("[ModernDisguise-Debug] ------------------ End of Disguise ------------------");
        return DisguiseResponse.SUCCESS;
    }

    /**
     * Unisguises a disguised {@link Player}
     *
     * @param player the undisguising {@link Player}
     * @return the response of the undisguise action (like reasons of failure or so)
     * @see DisguiseProvider#disguise(Player, Disguise)
     */
    public final @NotNull UndisguiseResponse undisguise(@NotNull final Player player) {
        // ADDED DEBUGGING
        final Logger logger = Bukkit.getLogger();
        logger.info("[ModernDisguise-Debug] ---------------- Starting Undisguise ----------------");
        logger.info("[ModernDisguise-Debug] Player: " + player.getName() + " (" + player.getUniqueId() + ")");

        if (!isDisguised(player)) {
            // ADDED DEBUGGING
            logger.warning("[ModernDisguise-Debug] Undisguise FAIL: Player is not disguised.");
            return UndisguiseResponse.FAIL_ALREADY_UNDISGUISED;
        }

        final GameProfile profile = DisguiseUtil.getProfile(player);
        if (profile == null) {
            if (player.isOnline()) {
                // ADDED DEBUGGING
                logger.severe("[ModernDisguise-Debug] Undisguise FAIL: Profile not found for an ONLINE player. This is unusual.");
                return UndisguiseResponse.FAIL_PROFILE_NOT_FOUND;
            }
            // ADDED DEBUGGING
            logger.info("[ModernDisguise-Debug] Player is offline, cleaning up their stored data.");
            final PlayerInfo info = this.playerInfo.remove(player.getUniqueId());
            if (info.hasName()) {
                DisguiseUtil.unregister(info.getNickname());
            }
            return UndisguiseResponse.SUCCESS;
        }

        final PlayerInfo info = this.playerInfo.get(player.getUniqueId());
        if (info.hasName()) {
            // ADDED DEBUGGING
            logger.info("[ModernDisguise-Debug] Restoring original name: " + info.getName());
            try {
                DisguiseUtil.PROFILE_NAME.set(profile, info.getName());
                DisguiseUtil.unregister(info.getNickname());
            } catch (final IllegalAccessException e) {
                 // ADDED DEBUGGING
                logger.severe("[ModernDisguise-Debug] Undisguise FAIL: A reflection error occurred while restoring name.");
                return UndisguiseResponse.FAIL_NAME_CHANGE_EXCEPTION;
            }
        }

        if (info.hasSkin()) {
            // ADDED DEBUGGING
            logger.info("[ModernDisguise-Debug] Restoring original skin.");
            final Skin skin = info.getSkin();
            profile.getProperties().removeAll("textures");
            profile.getProperties().put("textures", new Property("textures", skin.getTextures(), skin.getSignature()));
        }

        this.playerInfo.remove(player.getUniqueId());
        // ADDED DEBUGGING
        logger.info("[ModernDisguise-Debug] Refreshing player to show original appearance...");
        this.refreshAsPlayer(player);
        player.teleport(player.getLocation());

        // ADDED DEBUGGING
        logger.info("[ModernDisguise-Debug] Undisguise SUCCESS.");
        logger.info("[ModernDisguise-Debug] ----------------- End of Undisguise -----------------");
        return UndisguiseResponse.SUCCESS;
    }

    /**
     * @param player the {@link Player} being checked
     * @return true if the {@link Player} is disguised, false if the {@link Player} is not.
     */
    public final boolean isDisguised(@NotNull final Player player) {
        return this.playerInfo.containsKey(player.getUniqueId());
    }

    /**
     * @param player the {@link Player} being checked
     * @return true if the {@link Player} is disguised as an entity, false if the {@link Player} is not.
     */
    public final boolean isDisguisedAsEntity(@NotNull final Player player) {
        final PlayerInfo info = this.playerInfo.get(player.getUniqueId());
        return info != null && info.hasEntity() && this.entityProvider.isSupported(info.getEntityType());
    }

    /**
     * @param player the {@link Player} you're grabbing info about
     * @return the known info about a {@link Player}
     */
    public final @NotNull PlayerInfo getInfo(@NotNull final Player player) {
        if (this.playerInfo.containsKey(player.getUniqueId())) {
            return this.playerInfo.get(player.getUniqueId());
        }
        return new PlayerInfo(player.getName(), null, null, null);
    }

    /**
     * This sends packets to {@link Player}s to show changes like name and skin
     *
     * @param player the refreshed {@link Player}
     */
    abstract public void refreshAsPlayer(@NotNull final Player player);

    /**
     * @param refreshed the refreshed {@link Player}
     * @param targets   the needed {@link Player}s to receive refresh packets
     */
    abstract public void refreshAsEntity(@NotNull final Player refreshed, final boolean remove, final Player... targets);

    /**
     * @return false if version is NOT supported
     */
    public boolean isVersionSupported() {
        return DisguiseUtil.PRIMARY;
    }

    /**
     * @return false if version is NOT supported for entities
     */
    public boolean areEntitiesSupported() {
        return DisguiseUtil.INJECTION && this.entityProvider.isAvailable();
    }

    /**
     * @return the plugin used plugin to register listeners and refresh {@link Player}s
     */
    public final Plugin getPlugin() {
        return this.plugin;
    }

    /**
     * @return whether entity disguises are allowed or not
     * depends on whether the player initialized it like that or not
     */
    public final boolean performEntityDisguises() {
        return this.entityDisguises;
    }

}

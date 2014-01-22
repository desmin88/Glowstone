package net.glowstone;

import com.flowpowered.networking.NetworkServer;
import net.glowstone.command.*;
import net.glowstone.inventory.CraftingManager;
import net.glowstone.io.StorageQueue;
import net.glowstone.io.mcregion.McRegionWorldStorageProvider;
import net.glowstone.map.GlowMapView;
import net.glowstone.net.GlowNetworkServer;
import net.glowstone.net.SessionRegistry;
import net.glowstone.scheduler.GlowScheduler;
import net.glowstone.util.PlayerListFile;
import net.glowstone.util.ServerConfig;
import net.glowstone.util.SecurityUtils;
import net.glowstone.util.bans.BanManager;
import net.glowstone.util.bans.FlatFileBanManager;
import org.bukkit.*;
import org.bukkit.World.Environment;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.help.HelpMap;
import org.bukkit.inventory.*;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.StandardMessenger;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.util.CachedServerIcon;
import org.bukkit.util.permissions.DefaultPermissions;

import java.awt.image.BufferedImage;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.KeyPair;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The core class of the Glowstone server.
 * @author Graham Edgecombe
 */
public final class GlowServer implements Server {

    /**
     * The logger for this class.
     */
    public static final Logger logger = Logger.getLogger("Minecraft");

    /**
     * The protocol version supported by the server.
     */
    public static final int PROTOCOL_VERSION = 4;

    /**
     * The storage queue for handling I/O operations.
     */
    public static final StorageQueue storeQueue = new StorageQueue();

    /**
     * Creates a new server on TCP port 25565 and starts listening for
     * connections.
     * @param args The command-line arguments.
     */
    public static void main(String[] args) {
        try {
            storeQueue.start();

            ConfigurationSerialization.registerClass(GlowOfflinePlayer.class);

            GlowServer server = new GlowServer();
            server.start();
            server.bind();
            logger.info("Ready for connections.");
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Error during server startup.", t);
            System.exit(1);
        }
    }

    /**
     * The network executor service - Netty dispatches events to this thread
     * pool.
     */
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * A list of all the active {@link net.glowstone.net.GlowSession}s.
     */
    private final SessionRegistry sessions = new SessionRegistry();
    
    /**
     * The console manager of this server.
     */
    private final ConsoleManager consoleManager = new ConsoleManager(this, "jline");
    
    /**
     * The services manager of this server.
     */
    private final SimpleServicesManager servicesManager = new SimpleServicesManager();

    /**
     * The command map of this server.
     */
    private final GlowCommandMap commandMap = new GlowCommandMap(this);

    /**
     * The plugin manager of this server.
     */
    private final PluginManager pluginManager = new SimplePluginManager(this, commandMap);

    /**
     * The plugin channel messenger for the server.
     */
    private final Messenger messenger = new StandardMessenger();

    /**
     * The item metadata factory for the server.
     */
    private final ItemFactory itemFactory = null;

    /**
     * The help map for the server.
     */
    private final HelpMap helpMap = null;

    /**
     * The scoreboard manager for the server.
     */
    private final ScoreboardManager scoreboardManager = null;
    
    /**
     * The crafting manager for this server.
     */
    private final CraftingManager craftingManager = new CraftingManager();

    /**
     * The configuration for the server.
     */
    private final ServerConfig config = new ServerConfig();
    
    /**
     * The list of OPs on the server.
     */
    private final PlayerListFile opsList = new PlayerListFile(new File(ServerConfig.CONFIG_DIR, "ops.txt"));
    
    /**
     * The list of players whitelisted on the server.
     */
    private final PlayerListFile whitelist = new PlayerListFile(new File(ServerConfig.CONFIG_DIR, "whitelist.txt"));

    /**
     * The server's ban manager.
     */
    private BanManager banManager = new FlatFileBanManager(this);

    /**
     * The world this server is managing.
     */
    private final ArrayList<GlowWorld> worlds = new ArrayList<GlowWorld>();

    /**
     * The task scheduler used by this server.
     */
    private final GlowScheduler scheduler = new GlowScheduler(this);

    /**
     * The server's default game mode
     */
    private GameMode defaultGameMode = GameMode.CREATIVE;

    /**
     * The setting for verbose deprecation warnings.
     */
    private Warning.WarningState warnState = Warning.WarningState.DEFAULT;

    /**
     * Whether the server is shutting down
     */
    private boolean isShuttingDown = false;

    /**
     * A cache of existing OfflinePlayers
     */
    private final Map<String, OfflinePlayer> offlineCache = new ConcurrentHashMap<String, OfflinePlayer>();

    /**
     * A RSA key pair used for encryption and authentication
     */
    private final KeyPair keyPair = SecurityUtils.generateKeyPair();

    /**
     * The network server used for network communication
     */
    private final NetworkServer networkServer = new GlowNetworkServer(this);


    /**
     * Creates a new server.
     */
    public GlowServer() {
        Bukkit.setServer(this);

        config.load();
        warnState = Warning.WarningState.value(config.getString(ServerConfig.Key.WARNING_STATE));
        try {
            defaultGameMode = GameMode.valueOf(GameMode.class, config.getString(ServerConfig.Key.GAMEMODE));
        } catch (IllegalArgumentException e) {
            defaultGameMode = GameMode.SURVIVAL;
        } catch (NullPointerException e) {
            defaultGameMode = GameMode.SURVIVAL;
        }
        config.set(ServerConfig.Key.GAMEMODE, defaultGameMode.name());
        // config.getString("server.terminal-mode", "jline")
    }

    /**
     * Starts this server.
     */
    public void start() {
        consoleManager.setupConsole();
        
        // Load player lists
        opsList.load();
        whitelist.load();
        banManager.load();

        // Start loading plugins
        loadPlugins();

        // Begin registering permissions
        DefaultPermissions.registerCorePermissions();

        // Register these first so they're usable while the worlds are loading
        GlowCommandMap.initGlowPermissions(this);
        commandMap.register(new MeCommand(this));
        commandMap.register(new ColorCommand(this));
        commandMap.register(new KickCommand(this));
        commandMap.register(new ListCommand(this));
        commandMap.register(new TimeCommand(this));
        commandMap.register(new WhitelistCommand(this));
        commandMap.register(new BanCommand(this));
        commandMap.register(new GameModeCommand(this));
        commandMap.register(new OpCommand(this));
        commandMap.register(new DeopCommand(this));
        commandMap.register(new StopCommand(this));
        commandMap.register(new SaveCommand(this));
        commandMap.register(new SayCommand(this));
        commandMap.removeAllOfType(ReloadCommand.class);
        commandMap.register(new ReloadCommand(this));
        commandMap.register(new HelpCommand(this, commandMap.getKnownCommands(false)));

        enablePlugins(PluginLoadOrder.STARTUP);

        // Create worlds
        String world = config.getString(ServerConfig.Key.LEVEL_NAME);
        createWorld(WorldCreator.name(world).environment(Environment.NORMAL));
        if (getAllowNether()) {
            createWorld(WorldCreator.name(world + "_nether").environment(Environment.NETHER));
        }
        if (getAllowEnd()) {
            createWorld(WorldCreator.name(world + "_the_end").environment(Environment.THE_END));
        }

        // Finish loading plugins
        enablePlugins(PluginLoadOrder.POSTWORLD);
        commandMap.registerServerAliases();
        consoleManager.refreshCommands();
    }

    /**
     * Binds this server to the address specified in the configuration.
     */
    public void bind() {
        String ip = getIp();
        int port = getPort();

        SocketAddress address;
        if (ip.length() == 0) {
            address = new InetSocketAddress(port);
        } else {
            address = new InetSocketAddress(ip, port);
        }

        logger.log(Level.INFO, "Binding to address: {0}...", address);
        networkServer.bind(address);
    }
    
    /**
     * Stops this server.
     */
    public void shutdown() {
        // This is so we don't run this twice (/stop and actual shutdown)
        if (isShuttingDown) return;
        isShuttingDown = true;
        logger.info("The server is shutting down...");
        
        // Stop scheduler and disable plugins
        scheduler.stop();
        pluginManager.clearPlugins();

        // Kick (and save) all players
        for (Player player : getOnlinePlayers()) {
            player.kickPlayer("Server shutting down.");
        }
        
        // Save worlds
        for (World world : getWorlds()) {
            unloadWorld(world, true);
        }
        storeQueue.end();
        
        // Gracefully stop the network server
        networkServer.shutdown();
        
        // And finally kill the console
        consoleManager.stop();
    }
    
    /**
     * Loads all plugins, calling onLoad, &c.
     */
    private void loadPlugins() {
        // clear the map
        commandMap.removeAllOfType(PluginCommand.class);

        File folder = new File(config.getString(ServerConfig.Key.PLUGIN_FOLDER));
        if (!folder.isDirectory() && !folder.mkdirs()) {
            logger.log(Level.SEVERE, "Could not create plugins directory: " + folder);
        }
        
        // clear plugins and prepare to load
        pluginManager.clearPlugins();
        pluginManager.registerInterface(JavaPluginLoader.class);
        Plugin[] plugins = pluginManager.loadPlugins(folder);

        // call onLoad methods
        for (Plugin plugin : plugins) {
            try {
                plugin.onLoad();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error loading {0}: {1}", new Object[]{plugin.getDescription().getName(), ex.getMessage()});
                ex.printStackTrace();
            }
        }
    }
    
    /**
     * Enable all plugins of the given load order type.
     * @param type The type of plugin to enable.
     */
    private void enablePlugins(PluginLoadOrder type) {
        Plugin[] plugins = pluginManager.getPlugins();
        for (Plugin plugin : plugins) {
            if (!plugin.isEnabled() && plugin.getDescription().getLoad() == type) {
                List<Permission> perms = plugin.getDescription().getPermissions();
                for (Permission perm : perms) {
                    try {
                        pluginManager.addPermission(perm);
                    } catch (IllegalArgumentException ex) {
                        getLogger().log(Level.WARNING, "Plugin " + plugin.getDescription().getFullName() + " tried to register permission '" + perm.getName() + "' but it's already registered", ex);
                    }
                }

                try {
                    pluginManager.enablePlugin(plugin);
                } catch (Throwable ex) {
                    logger.log(Level.SEVERE, "Error loading {0}", plugin.getDescription().getFullName());
                    ex.printStackTrace();
                }
            }
        }
    }

    /**
     * Reloads the server, refreshing settings and plugin information
     */
    public void reload() {
        try {
            // Reload relevant configuration
            config.load();
            opsList.load();
            whitelist.load();
            
            // Reset crafting
            craftingManager.resetRecipes();
            
            // Load plugins
            loadPlugins();
            DefaultPermissions.registerCorePermissions();
            GlowCommandMap.initGlowPermissions(this);
            commandMap.registerAllPermissions();
            enablePlugins(PluginLoadOrder.STARTUP);
            enablePlugins(PluginLoadOrder.POSTWORLD);
            commandMap.registerServerAliases();
            consoleManager.refreshCommands();
        }
        catch (Exception ex) {
            logger.log(Level.SEVERE, "Uncaught error while reloading: {0}", ex.getMessage());
            ex.printStackTrace();
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // Access to internals

    /**
     * Gets the session registry.
     * @return The {@link SessionRegistry}.
     */
    public SessionRegistry getSessionRegistry() {
        return sessions;
    }

    /**
     * Get the key pair generated at server start up
     * @return The key pair generated at server start up
     */
    public KeyPair getKeyPair() {
        return keyPair;
    }
    
    /**
     * Returns the list of OPs on this server.
     */
    public PlayerListFile getOpsList() {
        return opsList;
    }
    
    /**
     * Returns the list of OPs on this server.
     */
    public PlayerListFile getWhitelist() {
        return whitelist;
    }

    /**
     * Returns the folder where configuration files are stored
     */
    public File getConfigDir() {
        return ServerConfig.CONFIG_DIR;
    }

    /**
     * Returns the currently used ban manager for the server
     */
    public BanManager getBanManager() {
        return banManager;
    }

    /**
     * Set the ban manager for the server
     */
    public void setBanManager(BanManager manager) {
        this.banManager = manager;
        manager.load();
        logger.log(Level.INFO, "Using {0} for ban management", manager.getClass().getName());
    }

    /**
     * Get a list of all commands the server has available
     */
    protected String[] getAllCommands() {
        HashSet<String> knownCommands = new HashSet<String>(commandMap.getKnownCommandNames());
        return knownCommands.toArray(new String[knownCommands.size()]);
    }

    /**
     * Return the crafting manager.
     * @return The server's crafting manager.
     */
    public CraftingManager getCraftingManager() {
        return craftingManager;
    }

    /**
     * Get the storage queue used for I/O operations.
     * @return The {@link StorageQueue}.
     */
    public StorageQueue getStorageQueue() {
        return storeQueue;
    }

    /**
     * Get whether fuzzy command matching is enabled.
     * @return True if fuzzy command matching is enabled.
     */
    public boolean getFuzzyCommandMatching() {
        return config.getBoolean(ServerConfig.Key.FUZZY_COMMANDS);
    }

    /**
     * Get the format for log filenames, where "%D" is replaced by the date.
     * @return The log filename format.
     */
    public String getLogFile() {
        return "logs/log-%D.txt";
        //return config.getString(ServerConfig.Key.LOG_FILE);
    }

    ////////////////////////////////////////////////////////////////////////////
    // Static server properties

    public String getName() {
        return "Glowstone";
    }

    public String getVersion() {
        return getClass().getPackage().getImplementationVersion();
    }

    public String getBukkitVersion() {
        return getClass().getPackage().getSpecificationVersion();
    }

    public Logger getLogger() {
        return logger;
    }

    @Override
    public boolean isPrimaryThread() {
        return false;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Access to Bukkit API

    public PluginManager getPluginManager() {
        return pluginManager;
    }

    public GlowScheduler getScheduler() {
        return scheduler;
    }

    public ServicesManager getServicesManager() {
        return servicesManager;
    }

    public Messenger getMessenger() {
        return messenger;
    }

    public HelpMap getHelpMap() {
        return helpMap;
    }

    public ItemFactory getItemFactory() {
        return itemFactory;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Commands and console

    public ConsoleCommandSender getConsoleSender() {
        return consoleManager.getSender();
    }

    public PluginCommand getPluginCommand(String name) {
        Command command = commandMap.getCommand(name);
        if (command instanceof PluginCommand) {
            return (PluginCommand) command;
        } else {
            return null;
        }
    }

    public Map<String, String[]> getCommandAliases() {
        Map<String, String[]> aliases = new HashMap<String, String[]>();
        ConfigurationSection section = config.getSection("aliases");
        if (section == null) return aliases;
        List<String> cmdAliases = new ArrayList<String>();
        for (String key : section.getKeys(false)) {
            cmdAliases.clear();
            cmdAliases.addAll(section.getStringList(key));
            aliases.put(key, cmdAliases.toArray(new String[cmdAliases.size()]));
        }
        return aliases;
    }

    public void reloadCommandAliases() {
        commandMap.removeAllOfType(MultipleCommandAlias.class);
        commandMap.registerServerAliases();
    }

    public boolean dispatchCommand(CommandSender sender, String commandLine) {
        try {
            if (commandMap.dispatch(sender, commandLine, false)) {
                return true;
            }

            if (getFuzzyCommandMatching()) {
                if (commandMap.dispatch(sender, commandLine, true)) {
                    return true;
                }
            }

            return false;
        }
        catch (CommandException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new CommandException("Unhandled exception executing command", ex);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // Player management

    public Set<OfflinePlayer> getOperators() {
        Set<OfflinePlayer> offlinePlayers = new HashSet<OfflinePlayer>();
        for (String name : opsList.getContents()) {
            offlinePlayers.add(getOfflinePlayer(name));
        }
        return offlinePlayers;
    }

    public Player[] getOnlinePlayers() {
        ArrayList<Player> result = new ArrayList<Player>();
        for (World world : getWorlds()) {
            for (Player player : world.getPlayers())
                result.add(player);
        }
        return result.toArray(new Player[result.size()]);
    }

    public OfflinePlayer[] getOfflinePlayers() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Player getPlayer(String name) {
        name = name.toLowerCase();
        Player bestPlayer = null;
        int bestDelta = -1;
        for (Player player : getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(name)) {
                int delta = player.getName().length() - name.length();
                if (bestPlayer == null || delta < bestDelta) {
                    bestPlayer = player;
                }
            }
        }
        return bestPlayer;
    }

    public Player getPlayerExact(String name) {
        for (Player player : getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name))
                return player;
        }
        return null;
    }

    public List<Player> matchPlayer(String name) {
        name = name.toLowerCase();

        ArrayList<Player> result = new ArrayList<Player>();
        for (Player player : getOnlinePlayers()) {
            String lower = player.getName().toLowerCase();
            if (lower.equals(name)) {
                result.clear();
                result.add(player);
                break;
            } else if (lower.contains(name)) {
                result.add(player);
            }
        }
        return result;
    }

    public OfflinePlayer getOfflinePlayer(String name) {
        OfflinePlayer player = getPlayerExact(name);
        if (player == null) {
            player = offlineCache.get(name);
            if (player == null) {
                player = new GlowOfflinePlayer(this, name);
                offlineCache.put(name, player);
                // Call creation event here?
            }
        } else {
            offlineCache.remove(name);
        }
        return player;
    }

    public void savePlayers() {
        for (Player player : getOnlinePlayers())
            player.saveData();
    }

    public int broadcastMessage(String message) {
        return broadcast(message, BROADCAST_CHANNEL_USERS);
    }

    public int broadcast(String message, String permission) {
        int count = 0;
        for (Permissible permissible : getPluginManager().getPermissionSubscriptions(permission)) {
            if (permissible instanceof CommandSender && permissible.hasPermission(permission)) {
                ((CommandSender) permissible).sendMessage(message);
                ++count;
            }
        }
        return count;
    }

    public Set<OfflinePlayer> getWhitelistedPlayers() {
        Set<OfflinePlayer> players = new HashSet<OfflinePlayer>();
        for (String name : whitelist.getContents()) {
            players.add(getOfflinePlayer(name));
        }
        return players;
    }

    public void reloadWhitelist() {
        whitelist.load();
    }

    public Set<String> getIPBans() {
        return banManager.getIpBans();
    }

    public void banIP(String address) {
        banManager.setIpBanned(address, true);
    }

    public void unbanIP(String address) {
        banManager.setIpBanned(address, false);
    }

    public Set<OfflinePlayer> getBannedPlayers() {
        Set<OfflinePlayer> bannedPlayers = new HashSet<OfflinePlayer>();
        for (String name : banManager.getBans()) {
            bannedPlayers.add(getOfflinePlayer(name));
        }
        return bannedPlayers;
    }

    ////////////////////////////////////////////////////////////////////////////
    // World management

    public GlowWorld getWorld(String name) {
        for (GlowWorld world : worlds) {
            if (world.getName().equalsIgnoreCase(name))
                return world;
        }
        return null;
    }

    public GlowWorld getWorld(UUID uid) {
        for (GlowWorld world : worlds) {
            if (uid.equals(world.getUID()))
                return world;
        }
        return null;
    }

    public List<World> getWorlds() {
        return new ArrayList<World>(worlds);
    }

    /**
     * Gets the default ChunkGenerator for the given environment.
     * @return The ChunkGenerator.
     */
    private ChunkGenerator getGenerator(String name, Environment environment) {
        ConfigurationSection worlds = config.getSection("worlds");
        if (worlds != null && worlds.contains(name + ".generator")) {
            String[] args = worlds.getString(name + ".generator").split(":", 2);
            if (getPluginManager().getPlugin(args[0]) == null) {
                logger.log(Level.WARNING, "Plugin {0} specified for world {1} does not exist, using default.", new Object[]{args[0], name});
            } else {
                return getPluginManager().getPlugin(args[0]).getDefaultWorldGenerator(name, args.length == 2 ? args[1] : "");
            }
        }

        if (environment == Environment.NETHER) {
            return new net.glowstone.generator.UndergroundGenerator();
        } else if (environment == Environment.THE_END) {
            return new net.glowstone.generator.CakeTownGenerator();
        } else {
            return new net.glowstone.generator.SurfaceGenerator();
        }
    }

    public GlowWorld createWorld(WorldCreator creator) {
        GlowWorld world = getWorld(creator.name());
        if (world != null) {
            return world;
        }

        if (creator.generator() == null) {
            creator.generator(getGenerator(creator.name(), creator.environment()));
        }

        world = new GlowWorld(this, creator.name(), creator.environment(), creator.seed(), new McRegionWorldStorageProvider(new File(getWorldContainer(), creator.name())), creator.generator());
        worlds.add(world);
        return world;
    }

    public boolean unloadWorld(String name, boolean save) {
        GlowWorld world = getWorld(name);
        return world != null && unloadWorld(world, save);
    }

    public boolean unloadWorld(World world, boolean save) {
        if (!(world instanceof GlowWorld)) {
            return false;
        }
        if (save) {
            world.setAutoSave(false);
            ((GlowWorld) world).save(false);
        }
        if (worlds.contains(world)) {
            worlds.remove(world);
            ((GlowWorld) world).unload();
            EventFactory.onWorldUnload((GlowWorld)world);
            return true;
        }
        return false;
    }

    public GlowMapView getMap(short id) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public GlowMapView createMap(World world) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    ////////////////////////////////////////////////////////////////////////////
    // Inventory and crafting

    @Override
    public List<Recipe> getRecipesFor(ItemStack result) {
        return craftingManager.getRecipesFor(result);
    }

    @Override
    public Iterator<Recipe> recipeIterator() {
        return craftingManager.iterator();
    }

    public boolean addRecipe(Recipe recipe) {
        return craftingManager.addRecipe(recipe);
    }

    @Override
    public void clearRecipes() {
        craftingManager.clearRecipes();
    }

    @Override
    public void resetRecipes() {
        craftingManager.resetRecipes();
    }

    @Override
    public Inventory createInventory(InventoryHolder owner, InventoryType type) {
        return null;
    }

    @Override
    public Inventory createInventory(InventoryHolder owner, int size) {
        return null;
    }

    @Override
    public Inventory createInventory(InventoryHolder owner, int size, String title) {
        return null;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Server icons

    @Override
    public CachedServerIcon getServerIcon() {
        return null;
    }

    @Override
    public CachedServerIcon loadServerIcon(File file) throws IllegalArgumentException, Exception {
        return null;
    }

    @Override
    public CachedServerIcon loadServerIcon(BufferedImage image) throws IllegalArgumentException, Exception {
        return null;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Plugin messages

    @Override
    public void sendPluginMessage(Plugin source, String channel, byte[] message) {
        StandardMessenger.validatePluginMessage(getMessenger(), source, channel, message);
        for (Player player : getOnlinePlayers()) {
            player.sendPluginMessage(source, channel, message);
        }
    }

    @Override
    public Set<String> getListeningPluginChannels() {
        HashSet<String> result = new HashSet<String>();
        for (Player player : getOnlinePlayers()) {
            result.addAll(player.getListeningPluginChannels());
        }
        return result;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Configuration with special handling

    public GameMode getDefaultGameMode() {
        return defaultGameMode;
    }

    public void setDefaultGameMode(GameMode mode) {
        defaultGameMode = mode;
    }

    public int getSpawnRadius() {
        return config.getInt(ServerConfig.Key.SPAWN_RADIUS);
    }

    public void setSpawnRadius(int value) {
        config.set(ServerConfig.Key.SPAWN_RADIUS, value);
    }

    public boolean hasWhitelist() {
        return config.getBoolean(ServerConfig.Key.WHITELIST);
    }

    public void setWhitelist(boolean enabled) {
        config.set(ServerConfig.Key.WHITELIST, enabled);
    }

    public Warning.WarningState getWarningState() {
        return warnState;
    }

    public void configureDbConfig(com.avaje.ebean.config.ServerConfig dbConfig) {
        com.avaje.ebean.config.DataSourceConfig ds = new com.avaje.ebean.config.DataSourceConfig();
        ConfigurationSection section = config.getSection("database");
        ds.setDriver(section.getString("driver", "org.sqlite.JDBC"));
        ds.setUrl(section.getString("url", "jdbc:sqlite:{DIR}{NAME}.db"));
        ds.setUsername(section.getString("username", "glow"));
        ds.setPassword(section.getString("password", "stone"));
        ds.setIsolationLevel(com.avaje.ebeaninternal.server.lib.sql.TransactionIsolation.getLevel(section.getString("isolation", "SERIALIZABLE")));

        if (ds.getDriver().contains("sqlite")) {
            dbConfig.setDatabasePlatform(new com.avaje.ebean.config.dbplatform.SQLitePlatform());
            dbConfig.getDatabasePlatform().getDbDdlSyntax().setIdentity("");
        }

        dbConfig.setDataSourceConfig(ds);
    }

    ////////////////////////////////////////////////////////////////////////////
    // Configuration

    public String getIp() {
        return config.getString(ServerConfig.Key.SERVER_IP);
    }

    public int getPort() {
        return config.getInt(ServerConfig.Key.SERVER_PORT);
    }

    public String getServerName() {
        return config.getString(ServerConfig.Key.SERVER_NAME);
    }

    public String getServerId() {
        return Integer.toHexString(getServerName().hashCode());
    }

    public int getMaxPlayers() {
        return config.getInt(ServerConfig.Key.MAX_PLAYERS);
    }

    public String getUpdateFolder() {
        return config.getString(ServerConfig.Key.UPDATE_FOLDER);
    }

    public File getUpdateFolderFile() {
        return new File(getUpdateFolder());
    }

    public boolean getOnlineMode() {
        return config.getBoolean(ServerConfig.Key.ONLINE_MODE);
    }

    public boolean getAllowNether() {
        return config.getBoolean(ServerConfig.Key.ALLOW_NETHER);
    }

    public boolean getAllowEnd() {
        return config.getBoolean(ServerConfig.Key.ALLOW_END);
    }

    public int getViewDistance() {
        return config.getInt(ServerConfig.Key.VIEW_DISTANCE);
    }

    public String getMotd() {
        return config.getString(ServerConfig.Key.MOTD);
    }

    public File getWorldContainer() {
        return new File(config.getString(ServerConfig.Key.WORLD_FOLDER));
    }

    public String getWorldType() {
        return config.getString(ServerConfig.Key.LEVEL_TYPE);
    }

    public boolean getGenerateStructures() {
        return config.getBoolean(ServerConfig.Key.GENERATE_STRUCTURES);
    }

    public long getConnectionThrottle() {
        return config.getInt(ServerConfig.Key.CONNECTION_THROTTLE);
    }

    public int getTicksPerAnimalSpawns() {
        return config.getInt(ServerConfig.Key.ANIMAL_TICKS);
    }

    public int getTicksPerMonsterSpawns() {
        return config.getInt(ServerConfig.Key.MONSTER_TICKS);
    }

    public boolean isHardcore() {
        return config.getBoolean(ServerConfig.Key.HARDCORE);
    }

    public boolean useExactLoginLocation() {
        return config.getBoolean(ServerConfig.Key.EXACT_LOGIN_LOCATION);
    }

    public int getMonsterSpawnLimit() {
        return config.getInt(ServerConfig.Key.MONSTER_LIMIT);
    }

    public int getAnimalSpawnLimit() {
        return config.getInt(ServerConfig.Key.ANIMAL_LIMIT);
    }

    public int getWaterAnimalSpawnLimit() {
        return config.getInt(ServerConfig.Key.WATER_ANIMAL_LIMIT);
    }

    public int getAmbientSpawnLimit() {
        return config.getInt(ServerConfig.Key.AMBIENT_LIMIT);
    }

    public String getShutdownMessage() {
        return config.getString(ServerConfig.Key.SHUTDOWN_MESSAGE);
    }

    public boolean getAllowFlight() {
        return config.getBoolean(ServerConfig.Key.ALLOW_FLIGHT);
    }
}

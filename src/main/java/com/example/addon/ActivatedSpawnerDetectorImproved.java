//creds to etianl <3 pizzaman
package pwn.noobs.trouserstreak.modules;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.block.enums.TrialSpawnerState;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.chunk.*;
import pwn.noobs.trouserstreak.Trouser;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Collectors;

public class ActivatedSpawnerDetector extends Module {
    // Constants
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<Block> GEODE_BLOCKS = Set.of(
        Blocks.AMETHYST_BLOCK,
        Blocks.BUDDING_AMETHYST,
        Blocks.CALCITE,
        Blocks.SMOOTH_BASALT,
        Blocks.AMETHYST_CLUSTER,
        Blocks.LARGE_AMETHYST_BUD,
        Blocks.MEDIUM_AMETHYST_BUD,
        Blocks.SMALL_AMETHYST_BUD
    );
    private static final int MAX_INT = 2000000000;
    private static final int CHUNK_SIZE = 16;
    
    // Settings groups
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgLocations = settings.createGroup("Location Toggles");
    private final SettingGroup locationLogs = settings.createGroup("Location Logs");

    // General settings
    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Display info about spawners in chat.")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Boolean> displayCoords = sgGeneral.add(new BoolSetting.Builder()
        .name("display-coords")
        .description("Displays coords of activated spawners in chat.")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Boolean> stashMessage = sgGeneral.add(new BoolSetting.Builder()
        .name("stash-message")
        .description("Toggle the message reminding you about stashes.")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Boolean> lessSpam = sgGeneral.add(new BoolSetting.Builder()
        .name("less-stash-spam")
        .description("Do not display the message reminding you about stashes if NO chests within 16 blocks of spawner.")
        .defaultValue(true)
        .visible(stashMessage::get)
        .build()
    );
    
    private final Setting<Boolean> airChecker = sgGeneral.add(new BoolSetting.Builder()
        .name("check-air-disturbances")
        .description("Detects spawners as activated if there are air disturbances around them.")
        .defaultValue(false)
        .build()
    );
    
    private final Setting<Boolean> ignoreGeodes = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-geodes")
        .description("Skips air check for spawners near geode blocks to reduce false positives.")
        .defaultValue(true)
        .visible(airChecker::get)
        .build()
    );
    
    private final Setting<List<Block>> storageBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("storage-blocks")
        .description("Blocks the module checks for when considering displaying messages and renders.")
        .defaultValue(Blocks.CHEST, Blocks.BARREL, Blocks.HOPPER, Blocks.DISPENSER)
        .build()
    );
    
    private final Setting<Boolean> deactivatedSpawner = sgGeneral.add(new BoolSetting.Builder()
        .name("deactivated-spawner-detector")
        .description("Detects spawners with torches on them.")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Integer> torchScanDistance = sgGeneral.add(new IntSetting.Builder()
        .name("torch-scan-distance")
        .description("How many blocks from the spawner to look for light-emitting blocks")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 10)
        .visible(deactivatedSpawner::get)
        .build()
    );

    // Location toggle settings
    private final Setting<Boolean> trialSpawner = sgLocations.add(new BoolSetting.Builder()
        .name("trial-spawner-detector")
        .description("Detects activated Trial Spawners.")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Boolean> showMoreMenu = sgLocations.add(new BoolSetting.Builder()
        .name("show-more-toggles")
        .description("Expand the location toggles menu.")
        .defaultValue(false)
        .build());
    
    private final Setting<Boolean> enableDungeon = sgLocations.add(new BoolSetting.Builder()
        .name("enable-dungeon")
        .description("Enable detection for dungeons.")
        .defaultValue(true)
        .visible(showMoreMenu::get)
        .build());
    
    private final Setting<Boolean> enableMineshaft = sgLocations.add(new BoolSetting.Builder()
        .name("enable-mineshaft")
        .description("Enable detection for mineshafts.")
        .defaultValue(true)
        .visible(showMoreMenu::get)
        .build());
    
    private final Setting<Boolean> enableBastion = sgLocations.add(new BoolSetting.Builder()
        .name("enable-bastion")
        .description("Enable detection for bastions.")
        .defaultValue(true)
        .visible(showMoreMenu::get)
        .build());
    
    private final Setting<Boolean> enableWoodlandMansion = sgLocations.add(new BoolSetting.Builder()
        .name("enable-woodland-mansion")
        .description("Enable detection for woodland mansions.")
        .defaultValue(true)
        .visible(showMoreMenu::get)
        .build());
    
    private final Setting<Boolean> enableFortress = sgLocations.add(new BoolSetting.Builder()
        .name("enable-fortress")
        .description("Enable detection for fortresses.")
        .defaultValue(true)
        .visible(showMoreMenu::get)
        .build());
    
    private final Setting<Boolean> enableStronghold = sgLocations.add(new BoolSetting.Builder()
        .name("enable-stronghold")
        .description("Enable detection for strongholds.")
        .defaultValue(true)
        .visible(showMoreMenu::get)
        .build());

    // Render settings
    private final Setting<Boolean> lessRenderSpam = sgRender.add(new BoolSetting.Builder()
        .name("less-render-spam")
        .description("Do not render big box if NO chests within range of spawner.")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Integer> renderDistance = sgRender.add(new IntSetting.Builder()
        .name("render-distance")
        .description("How many chunks from the player to render detected spawners.")
        .defaultValue(32)
        .min(6)
        .sliderRange(6, 1024)
        .build()
    );
    
    private final Setting<Boolean> removeOutsideRenderDistance = sgRender.add(new BoolSetting.Builder()
        .name("remove-outside-render")
        .description("Removes cached block positions when they leave render distance.")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Show tracers to the Spawner.")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Boolean> nearestTracer = sgRender.add(new BoolSetting.Builder()
        .name("nearest-tracer-only")
        .description("Show only one tracer to the nearest Spawner.")
        .defaultValue(false)
        .build()
    );
    
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );
    
    // Color settings
    private final Setting<SettingColor> spawnerSideColor = sgRender.add(new ColorSetting.Builder()
        .name("spawner-side-color")
        .description("Color of the activated spawner.")
        .defaultValue(new SettingColor(251, 5, 5, 70))
        .visible(() -> shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both)
        .build()
    );
    
    private final Setting<SettingColor> spawnerLineColor = sgRender.add(new ColorSetting.Builder()
        .name("spawner-line-color")
        .description("Color of the activated spawner.")
        .defaultValue(new SettingColor(251, 5, 5, 235))
        .visible(() -> shapeMode.get() == ShapeMode.Lines || shapeMode.get() == ShapeMode.Both || tracers.get())
        .build()
    );
    
    private final Setting<SettingColor> trialSideColor = sgRender.add(new ColorSetting.Builder()
        .name("trial-side-color")
        .description("Color of the activated trial spawner.")
        .defaultValue(new SettingColor(255, 100, 0, 70))
        .visible(() -> trialSpawner.get() && (shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both))
        .build()
    );
    
    private final Setting<SettingColor> trialLineColor = sgRender.add(new ColorSetting.Builder()
        .name("trial-line-color")
        .description("Color of the activated trial spawner.")
        .defaultValue(new SettingColor(255, 100, 0, 235))
        .visible(() -> trialSpawner.get() && (shapeMode.get() == ShapeMode.Lines || shapeMode.get() == ShapeMode.Both || tracers.get()))
        .build()
    );
    
    private final Setting<SettingColor> deactivatedSpawnerSideColor = sgRender.add(new ColorSetting.Builder()
        .name("deactivated-spawner-side-color")
        .description("Color of the spawner with torches.")
        .defaultValue(new SettingColor(251, 5, 251, 70))
        .visible(() -> deactivatedSpawner.get() && (shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both))
        .build()
    );
    
    private final Setting<SettingColor> deactivatedSpawnerLineColor = sgRender.add(new ColorSetting.Builder()
        .name("deactivated-spawner-line-color")
        .description("Color of the spawner with torches.")
        .defaultValue(new SettingColor(251, 5, 251, 235))
        .visible(() -> deactivatedSpawner.get() && (shapeMode.get() == ShapeMode.Lines || shapeMode.get() == ShapeMode.Both))
        .build()
    );
    
    private final Setting<Boolean> rangeRendering = sgRender.add(new BoolSetting.Builder()
        .name("spawner-range-rendering")
        .description("Renders the rough active range of a mob spawner block.")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<SettingColor> rangeSideColor = sgRender.add(new ColorSetting.Builder()
        .name("spawner-range-side-color")
        .description("Color of the active spawner range.")
        .defaultValue(new SettingColor(5, 178, 251, 30))
        .visible(() -> rangeRendering.get() && (shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both))
        .build()
    );
    
    private final Setting<SettingColor> rangeLineColor = sgRender.add(new ColorSetting.Builder()
        .name("spawner-range-line-color")
        .description("Color of the active spawner range.")
        .defaultValue(new SettingColor(5, 178, 251, 155))
        .visible(() -> rangeRendering.get() && (shapeMode.get() == ShapeMode.Lines || shapeMode.get() == ShapeMode.Both))
        .build()
    );
    
    private final Setting<SettingColor> trialRangeSideColor = sgRender.add(new ColorSetting.Builder()
        .name("trial-range-side-color")
        .description("Color of the active trial spawner range.")
        .defaultValue(new SettingColor(150, 178, 251, 30))
        .visible(() -> trialSpawner.get() && rangeRendering.get() && (shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both))
        .build()
    );
    
    private final Setting<SettingColor> trialRangeLineColor = sgRender.add(new ColorSetting.Builder()
        .name("trial-range-line-color")
        .description("Color of the active trial spawner range.")
        .defaultValue(new SettingColor(150, 178, 251, 155))
        .visible(() -> trialSpawner.get() && rangeRendering.get() && (shapeMode.get() == ShapeMode.Lines || shapeMode.get() == ShapeMode.Both))
        .build()
    );

    // Location logging settings
    private final Setting<Boolean> locLogging = locationLogs.add(new BoolSetting.Builder()
        .name("enable-location-logging")
        .description("Logs the locations of detected spawners to a csv file and a table.")
        .defaultValue(false)
        .build()
    );

    // Data storage
    private final List<LoggedSpawner> loggedSpawners = new ArrayList<>();
    private final Set<BlockPos> loggedSpawnerPositions = new HashSet<>();
    private final Set<BlockPos> scannedPositions = Collections.synchronizedSet(new HashSet<>());
    private final Set<BlockPos> spawnerPositions = Collections.synchronizedSet(new HashSet<>());
    private final Set<BlockPos> trialSpawnerPositions = Collections.synchronizedSet(new HashSet<>());
    private final Set<BlockPos> deactivatedSpawnerPositions = Collections.synchronizedSet(new HashSet<>());
    private final Set<BlockPos> noRenderPositions = Collections.synchronizedSet(new HashSet<>());
    
    private int closestSpawnerX = MAX_INT;
    private int closestSpawnerY = MAX_INT;
    private int closestSpawnerZ = MAX_INT;
    private double spawnerDistance = MAX_INT;
    private boolean activatedSpawnerFound = false;

    public ActivatedSpawnerDetector() {
        super(Trouser.baseHunting, "ActivatedSpawnerDetector", 
            "Detects if a player has been near a mob spawner. Useful for finding player stashes.");
    }

    @Override
    public void onActivate() {
        clearChunkData();
        loadLogs();
    }

    @Override
    public void onDeactivate() {
        clearChunkData();
    }

    @EventHandler
    private void onScreenOpen(OpenScreenEvent event) {
        if (event.screen instanceof DisconnectedScreen || event.screen instanceof DownloadingTerrainScreen) {
            clearChunkData();
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        clearChunkData();
    }

    private void clearChunkData() {
        scannedPositions.clear();
        spawnerPositions.clear();
        deactivatedSpawnerPositions.clear();
        noRenderPositions.clear();
        trialSpawnerPositions.clear();
        closestSpawnerX = MAX_INT;
        closestSpawnerY = MAX_INT;
        closestSpawnerZ = MAX_INT;
        spawnerDistance = MAX_INT;
    }

    private boolean chunkContainsGeodeBlocks(WorldChunk chunk, int sectionsToCheck) {
        ChunkSection[] sections = chunk.getSectionArray();
        for (int i = 0; i < Math.min(sections.length, sectionsToCheck); i++) {
            ChunkSection section = sections[i];
            if (!section.isEmpty()) {
                var blockStatesContainer = section.getBlockStateContainer();
                Palette<BlockState> blockStatePalette = blockStatesContainer.data.palette();
                int blockPaletteLength = blockStatePalette.getSize();
                
                for (int j = 0; j < blockPaletteLength; j++) {
                    BlockState blockPaletteEntry = blockStatePalette.get(j);
                    if (GEODE_BLOCKS.contains(blockPaletteEntry.getBlock())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;
        
        // Get all loaded chunks
        Set<WorldChunk> chunks = Arrays.stream(mc.world.getChunkManager().chunks.chunks)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        chunks.forEach(this::processChunk);
        
        if (nearestTracer.get()) {
            updateNearestSpawner();
        }
        
        if (removeOutsideRenderDistance.get()) {
            removeChunksOutsideRenderDistance(chunks);
        }
    }

    private void processChunk(WorldChunk chunk) {
        List<BlockEntity> blockEntities = new ArrayList<>(chunk.getBlockEntities().values());
        
        for (BlockEntity blockEntity : blockEntities) {
            if (blockEntity instanceof MobSpawnerBlockEntity) {
                processMobSpawner((MobSpawnerBlockEntity) blockEntity, chunk);
            } else if (blockEntity instanceof TrialSpawnerBlockEntity && trialSpawner.get()) {
                processTrialSpawner((TrialSpawnerBlockEntity) blockEntity);
            }
        }
    }

    private void processMobSpawner(MobSpawnerBlockEntity spawner, WorldChunk chunk) {
        BlockPos pos = spawner.getPos();
        String monster = getSpawnerMonsterType(spawner);
        
        if (shouldSkipSpawner(pos)) return;
        
        if (airChecker.get() && (spawner.getLogic().spawnDelay == 20 || spawner.getLogic().spawnDelay == 0)) {
            checkAirDisturbances(spawner, pos, monster, chunk);
        } else if (spawner.getLogic().spawnDelay != 20) {
            handleActivatedSpawner(spawner, pos, monster);
        }
    }

    private String getSpawnerMonsterType(MobSpawnerBlockEntity spawner) {
        if (spawner.getLogic().spawnEntry != null && spawner.getLogic().spawnEntry.getNbt() != null) {
            return spawner.getLogic().spawnEntry.getNbt().get("id").toString();
        }
        return null;
    }

    private boolean shouldSkipSpawner(BlockPos pos) {
        return trialSpawnerPositions.contains(pos) || 
               noRenderPositions.contains(pos) || 
               deactivatedSpawnerPositions.contains(pos) || 
               spawnerPositions.contains(pos) ||
               (mc.world.getRegistryKey() == World.NETHER && isNetherSpawner(pos));
    }

    private boolean isNetherSpawner(BlockPos pos) {
        // Additional checks for Nether spawners if needed
        return false;
    }

    private void checkAirDisturbances(MobSpawnerBlockEntity spawner, BlockPos pos, String monster, WorldChunk chunk) {
        if (monster == null || scannedPositions.contains(pos)) return;
        
        boolean geodeNearby = ignoreGeodes.get() && isNearGeode(pos, chunk);
        boolean airDisturbed = checkAirAroundSpawner(pos, monster);
        
        if (airDisturbed && !geodeNearby) {
            displayLocationMessage(monster, pos);
        }
        
        scannedPositions.add(pos);
    }

    private boolean isNearGeode(BlockPos pos, WorldChunk chunk) {
        if (ignoreGeodes.get() && chunkContainsGeodeBlocks(chunk, Math.min(chunk.getSectionArray().length, 20))) {
            for (int x = -5; x <= 5; x++) {
                for (int y = -5; y <= 5; y++) {
                    for (int z = -5; z <= 5; z++) {
                        if (GEODE_BLOCKS.contains(mc.world.getBlockState(pos.add(x, y, z)).getBlock())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean checkAirAroundSpawner(BlockPos pos, String monster) {
        boolean airFound = false;
        boolean caveAirFound = false;
        
        int[] ranges = getScanRangesForMonster(monster);
        
        for (int x = ranges[0]; x < ranges[1]; x++) {
            for (int y = ranges[2]; y < ranges[3]; y++) {
                for (int z = ranges[4]; z < ranges[5]; z++) {
                    BlockPos bpos = pos.add(x, y, z);
                    BlockState state = mc.world.getBlockState(bpos);
                    
                    if (state.getBlock() == Blocks.AIR) airFound = true;
                    if (state.getBlock() == Blocks.CAVE_AIR) caveAirFound = true;
                    
                    if (caveAirFound && airFound) return true;
                }
            }
        }
        return false;
    }

    private int[] getScanRangesForMonster(String monster) {
        if (monster.contains("zombie") || monster.contains("skeleton") || ":spider".equals(monster)) {
            return new int[]{-2, 2, -1, 3, -2, 2};
        } else if (monster.contains("cave_spider")) {
            return new int[]{-1, 2, 0, 2, -1, 2};
        } else if (monster.contains("silverfish")) {
            return new int[]{-3, 4, -2, 4, -3, 4};
        }
        return new int[]{0, 0, 0, 0, 0, 0};
    }

    private void handleActivatedSpawner(MobSpawnerBlockEntity spawner, BlockPos pos, String monster) {
        activatedSpawnerFound = true;
        
        if (chatFeedback.get()) {
            if (monster != null) {
                displayLocationMessage(monster, pos);
            } else {
                sendGenericSpawnerMessage(pos);
            }
        }
        
        spawnerPositions.add(pos);
        if (locLogging.get()) logSpawner(pos);
        
        checkForDeactivatedSpawner(pos);
        checkForNearbyStorage(pos);
    }

    private void displayLocationMessage(String monster, BlockPos pos) {
        if (monster.contains("zombie") || monster.contains("skeleton") || ":spider".equals(monster)) {
            if (":spider".equals(monster) && mc.world.getBlockState(pos.down()).getBlock() == Blocks.BIRCH_PLANKS && enableWoodlandMansion.get()) {
                sendWoodlandMansionMessage(pos);
            } else if (enableDungeon.get()) {
                sendDungeonMessage(pos);
            }
        } else if (monster.contains("cave_spider") && enableMineshaft.get()) {
            sendMineshaftMessage(pos);
        } else if (monster.contains("silverfish") && enableStronghold.get()) {
            sendStrongholdMessage(pos);
        } else if (monster.contains("blaze") && enableFortress.get()) {
            sendFortressMessage(pos);
        } else if (monster.contains("magma") && enableBastion.get()) {
            sendBastionMessage(pos);
        } else {
            sendGenericSpawnerMessage(pos);
        }
    }

    private void sendDungeonMessage(BlockPos pos) {
        if (displayCoords.get()) {
            ChatUtils.sendMsg(Text.of("§cASD§r | Detected Activated §cDUNGEON§r Spawner! Block Position: " + pos));
        } else {
            ChatUtils.sendMsg(Text.of("§cASD§r | Detected Activated §cDUNGEON§r Spawner!"));
        }
    }

    private void sendWoodlandMansionMessage(BlockPos pos) {
        if (displayCoords.get()) {
            ChatUtils.sendMsg(Text.of("§cASD§r | Detected Activated §cWOODLAND MANSION§r Spawner! Block Position: " + pos));
        } else {
            ChatUtils.sendMsg(Text.of("§cASD§r | Detected Activated §cWOODLAND MANSION§r Spawner!"));
        }
    }

    private void sendMineshaftMessage(BlockPos pos) {
        if (displayCoords.get()) {
            ChatUtils.sendMsg(Text.of("§cASD§r | Detected Activated §cMINESHAFT§r Spawner! Block Position: " + pos));
        } else {
            ChatUtils.sendMsg(Text.of("§cASD§r | Detected Activated §cMINESHAFT§r Spawner!"));
        }
    }

    private void sendStrongholdMessage(BlockPos pos) {
        if (displayCoords.get()) {
            ChatUtils.sendMsg(Text.of("§cASD§r | Detected Activated §cSTRONGHOLD§r Spawner! Block Position: " + pos));
        } else {
            ChatUtils.sendMsg(Text.of("§cASD§r | Detected Activated §cSTRONGHOLD§r Spawner!"));
        }
    }

    private void sendFortressMessage(BlockPos pos) {
        if (displayCoords.get()) {
            ChatUtils.sendMsg(Text.of("§cASD§r | Detected Activated §cFORTRESS§r Spawner! Block Position: " + pos));
        } else {
            ChatUtils.sendMsg(Text.of("§cASD§r | Detected Activated §cFORTRESS§r Spawner!"));
        }
    }

    private void sendBastionMessage(BlockPos pos) {
        if (displayCoords.get()) {
            ChatUtils.sendMsg(Text.of("§cASD§r | Detected Activated §cBASTION§r Spawner! Block Position: " + pos));
        } else {
            ChatUtils.sendMsg(Text.of("§cASD§r | Detected Activated §cBASTION§r Spawner!"));
        }
    }

    private void sendGenericSpawnerMessage(BlockPos pos) {
        if (displayCoords.get()) {
            ChatUtils.sendMsg(Text.of("§cASD§r | Detected Activated Spawner! Block Position: " + pos));
        } else {
            ChatUtils.sendMsg(Text.of("§cASD§r | Detected Activated Spawner!"));
        }
    }

    private void checkForDeactivatedSpawner(BlockPos pos) {
        if (!deactivatedSpawner.get()) return;
        
        boolean lightsFound = scanForLights(pos);
        
        if (lightsFound) {
            deactivatedSpawnerPositions.add(pos);
            if (chatFeedback.get()) {
                ChatUtils.sendMsg(Text.of("The Spawner has torches or other light blocks!"));
            }
        }
    }

    private boolean scanForLights(BlockPos pos) {
        int distance = torchScanDistance.get();
        
        for (int x = -distance; x <= distance; x++) {
            for (int y = -distance; y <= distance; y++) {
                for (int z = -distance; z <= distance; z++) {
                    Block block = mc.world.getBlockState(pos.add(x, y, z)).getBlock();
                    if (isLightBlock(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isLightBlock(Block block) {
        return block == Blocks.TORCH || 
               block == Blocks.SOUL_TORCH || 
               block == Blocks.REDSTONE_TORCH || 
               block == Blocks.JACK_O_LANTERN || 
               block == Blocks.GLOWSTONE || 
               block == Blocks.SHROOMLIGHT || 
               block == Blocks.OCHRE_FROGLIGHT || 
               block == Blocks.PEARLESCENT_FROGLIGHT || 
               block == Blocks.SEA_LANTERN || 
               block == Blocks.LANTERN || 
               block == Blocks.SOUL_LANTERN || 
               block == Blocks.CAMPFIRE || 
               block == Blocks.SOUL_CAMPFIRE;
    }

    private void checkForNearbyStorage(BlockPos pos) {
        boolean chestFound = scanForStorageBlocks(pos);
        
        if (!chestFound && lessRenderSpam.get()) {
            noRenderPositions.add(pos);
        }
        
        if (chatFeedback.get() && stashMessage.get() && (!lessSpam.get() || chestFound)) {
            error("There may be stashed items in the storage near the spawners!");
        }
    }

    private boolean scanForStorageBlocks(BlockPos pos) {
        for (int x = -16; x <= 16; x++) {
            for (int y = -16; y <= 16; y++) {
                for (int z = -16; z <= 16; z++) {
                    BlockPos bpos = pos.add(x, y, z);
                    if (storageBlocks.get().contains(mc.world.getBlockState(bpos).getBlock())) {
                        return true;
                    }
                    
                    Box box = new Box(bpos);
                    if (!mc.world.getEntitiesByClass(ChestMinecartEntity.class, box, entity -> true).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void processTrialSpawner(TrialSpawnerBlockEntity trialSpawner) {
        BlockPos pos = trialSpawner.getPos();
        
        if (shouldSkipTrialSpawner(pos)) return;
        
        if (chatFeedback.get()) {
            if (displayCoords.get()) {
                ChatUtils.sendMsg(Text.of("§cASD§r | Detected Activated §cTRIAL§r Spawner! Block Position: " + pos));
            } else {
                ChatUtils.sendMsg(Text.of("§cASD§r | Detected Activated §cTRIAL§r Spawner!"));
            }
        }
        
        trialSpawnerPositions.add(pos);
        
        boolean chestFound = scanForStorageBlocks(pos);
        if (!chestFound && lessRenderSpam.get()) {
            noRenderPositions.add(pos);
        }
        
        if (chatFeedback.get() && stashMessage.get() && (!lessSpam.get() || chestFound)) {
            error("There may be stashed items in the storage near the spawners!");
        }
        
        if (locLogging.get()) logSpawner(pos);
    }

    private boolean shouldSkipTrialSpawner(BlockPos pos) {
        return trialSpawnerPositions.contains(pos) || 
               noRenderPositions.contains(pos) || 
               deactivatedSpawnerPositions.contains(pos) || 
               spawnerPositions.contains(pos) || 
               trialSpawner.getLogic().getState() == TrialSpawnerState.WAITING_FOR_PLAYERS;
    }

    private void updateNearestSpawner() {
        try {
            Set<BlockPos> combinedPositions = new HashSet<>();
            combinedPositions.addAll(spawnerPositions);
            combinedPositions.addAll(deactivatedSpawnerPositions);
            combinedPositions.addAll(trialSpawnerPositions);

            if (!combinedPositions.isEmpty()) {
                BlockPos nearest = combinedPositions.stream()
                    .min(Comparator.comparingDouble(pos -> 
                        Math.sqrt(Math.pow(pos.getX() - mc.player.getBlockX(), 2) + 
                                 Math.pow(pos.getZ() - mc.player.getBlockZ(), 2))))
                    .orElse(null);
                
                if (nearest != null) {
                    closestSpawnerX = nearest.getX();
                    closestSpawnerY = nearest.getY();
                    closestSpawnerZ = nearest.getZ();
                }
            }
        } catch (Exception e) {
            Trouser.LOG.error("Error updating nearest spawner", e);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (shouldRender()) {
            renderSpawners(event);
            renderTrialSpawners(event);
            
            if (nearestTracer.get()) {
                renderNearestTracer(event);
            }
        }
    }

    private boolean shouldRender() {
        return spawnerSideColor.get().a > 5 || 
               spawnerLineColor.get().a > 5 || 
               rangeSideColor.get().a > 5 || 
               rangeLineColor.get().a > 5;
    }

    private void renderSpawners(Render3DEvent event) {
        synchronized (spawnerPositions) {
            spawnerPositions.forEach(pos -> {
                if (isWithinRenderDistance(pos)) {
                    renderSpawner(pos, event);
                }
            });
        }
    }

    private void renderTrialSpawners(Render3DEvent event) {
        synchronized (trialSpawnerPositions) {
            trialSpawnerPositions.forEach(pos -> {
                if (isWithinRenderDistance(pos)) {
                    renderTrialSpawner(pos, event);
                }
            });
        }
    }

    private boolean isWithinRenderDistance(BlockPos pos) {
        BlockPos playerPos = new BlockPos(mc.player.getBlockX(), pos.getY(), mc.player.getBlockZ());
        return pos != null && playerPos.isWithinDistance(pos, renderDistance.get() * CHUNK_SIZE);
    }

    private void renderSpawner(BlockPos pos, Render3DEvent event) {
        Vec3d start = new Vec3d(pos.getX(), pos.getY(), pos.getZ());
        Vec3d end = new Vec3d(pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        
        // Render range if enabled
        if (rangeRendering.get() && (!lessRenderSpam.get() || !noRenderPositions.contains(pos))) {
            Box rangeBox = new Box(
                start.add(17, 17, 17),
                end.add(-16, -16, -16)
            );
            renderRange(rangeBox, rangeSideColor.get(), rangeLineColor.get(), shapeMode.get(), event);
        }
        
        // Render spawner box
        Box spawnerBox = new Box(start.add(1, 1, 1), end);
        if (deactivatedSpawnerPositions.contains(pos)) {
            render(spawnerBox, deactivatedSpawnerSideColor.get(), deactivatedSpawnerLineColor.get(), shapeMode.get(), event);
        } else {
            render(spawnerBox, spawnerSideColor.get(), spawnerLineColor.get(), shapeMode.get(), event);
        }
    }

    private void renderTrialSpawner(BlockPos pos, Render3DEvent event) {
        Vec3d start = new Vec3d(pos.getX(), pos.getY(), pos.getZ());
        Vec3d end = new Vec3d(pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        
        // Render range if enabled
        if (trialSpawner.get() && rangeRendering.get() && (!lessRenderSpam.get() || !noRenderPositions.contains(pos))) {
            Box rangeBox = new Box(
                start.add(15, 15, 15),
                end.add(-14, -14, -14)
            );
            renderRange(rangeBox, trialRangeSideColor.get(), trialRangeLineColor.get(), shapeMode.get(), event);
        }
        
        // Render spawner box
        Box spawnerBox = new Box(start.add(1, 1, 1), end);
        if (deactivatedSpawnerPositions.contains(pos)) {
            render(spawnerBox, deactivatedSpawnerSideColor.get(), deactivatedSpawnerLineColor.get(), shapeMode.get(), event);
        } else {
            render(spawnerBox, trialSideColor.get(), trialLineColor.get(), shapeMode.get(), event);
        }
    }

    private void renderNearestTracer(Render3DEvent event) {
        if (closestSpawnerX != MAX_INT) {
            Box box = new Box(
                new Vec3d(closestSpawnerX, closestSpawnerY, closestSpawnerZ),
                new Vec3d(closestSpawnerX + 1, closestSpawnerY + 1, closestSpawnerZ + 1)
            );
            
            Color color = trialSpawnerPositions.contains(new BlockPos(closestSpawnerX, closestSpawnerY, closestSpawnerZ)) ? 
                trialLineColor.get() : spawnerLineColor.get();
            
            render2(box, color, color, ShapeMode.Sides, event);
        }
    }

    private void render(Box box, Color sides, Color lines, ShapeMode shapeMode, Render3DEvent event) {
        if (tracers.get() && !nearestTracer.get()) {
            event.renderer.line(
                RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                box.minX + 0.5, box.minY + ((box.maxY - box.minY) / 2), box.minZ + 0.5,
                lines
            );
        }
        event.renderer.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 
            sides, new Color(0, 0, 0, 0), shapeMode, 0);
    }

    private void render2(Box box, Color sides, Color lines, ShapeMode shapeMode, Render3DEvent event) {
        if (tracers.get()) {
            event.renderer.line(
                RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                box.minX + 0.5, box.minY + ((box.maxY - box.minY) / 2), box.minZ + 0.5,
                lines
            );
        }
        event.renderer.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 
            sides, new Color(0, 0, 0, 0), shapeMode, 0);
    }

    private void renderRange(Box box, Color sides, Color lines, ShapeMode shapeMode, Render3DEvent event) {
        event.renderer.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 
            sides, lines, shapeMode, 0);
    }

    private void removeChunksOutsideRenderDistance(Set<WorldChunk> chunks) {
        removeBlockPosOutsideRenderDistance(scannedPositions, chunks);
        removeBlockPosOutsideRenderDistance(spawnerPositions, chunks);
        removeBlockPosOutsideRenderDistance(deactivatedSpawnerPositions, chunks);
        removeBlockPosOutsideRenderDistance(trialSpawnerPositions, chunks);
        removeBlockPosOutsideRenderDistance(noRenderPositions, chunks);
    }

    private void removeBlockPosOutsideRenderDistance(Set<BlockPos> blockSet, Set<WorldChunk> worldChunks) {
        blockSet.removeIf(blockpos -> {
            BlockPos boxPos = new BlockPos(
                (int) Math.floor(blockpos.getX()), 
                (int) Math.floor(blockpos.getY()), 
                (int) Math.floor(blockpos.getZ())
            );
            return !worldChunks.contains(mc.world.getChunk(boxPos));
        });
    }

    private void logSpawner(BlockPos pos) {
        if (!loggedSpawnerPositions.contains(pos)) {
            loggedSpawnerPositions.add(pos);
            loggedSpawners.add(new LoggedSpawner(pos.getX(), pos.getY(), pos.getZ()));
            saveJson();
            saveCsv();
        }
    }

    private void saveCsv() {
        try (Writer writer = new FileWriter(getCsvFile())) {
            writer.write("X,Y,Z\n");
            for (LoggedSpawner ls : loggedSpawners) {
                ls.write(writer);
            }
        } catch (IOException ignored) {}
    }

    private void saveJson() {
        try (Writer writer = new FileWriter(getJsonFile())) {
            GSON.toJson(loggedSpawners, writer);
        } catch (IOException ignored) {}
    }

    private File getJsonFile() {
        return new File(new File(new File("TrouserStreak", "ActivatedSpawners"), Utils.getFileWorldName()), "spawners.json");
    }

    private File getCsvFile() {
        return new File(new File(new File("TrouserStreak", "ActivatedSpawners"), Utils.getFileWorldName()), "spawners.csv");
    }

    private void loadLogs() {
        File file = getJsonFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                List<LoggedSpawner> data = GSON.fromJson(reader, new TypeToken<List<LoggedSpawner>>() {}.getType());
                if (data != null) {
                    loggedSpawners.addAll(data);
                    data.forEach(ls -> loggedSpawnerPositions.add(new BlockPos(ls.x, ls.y, ls.z)));
                    return;
                }
            } catch (Exception ignored) {}
        }
        
        file = getCsvFile();
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                reader.readLine(); // Skip header
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] values = line.split(",");
                    LoggedSpawner ls = new LoggedSpawner(
                        Integer.parseInt(values[0]),
                        Integer.parseInt(values[1]),
                        Integer.parseInt(values[2])
                    );
                    loggedSpawners.add(ls);
                    loggedSpawnerPositions.add(new BlockPos(ls.x, ls.y, ls.z));
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        loggedSpawners.sort(Comparator.comparingInt(s -> s.y));
        WVerticalList list = theme.verticalList();
        
        WButton clear = list.add(theme.button("Clear Logged Positions")).widget();
        clear.action = this::clearLoggedPositions;
        
        if (!loggedSpawners.isEmpty()) {
            WTable table = list.add(new WTable()).widget();
            fillTable(theme, table);
        }
        
        return list;
    }

    private void clearLoggedPositions() {
        loggedSpawners.clear();
        loggedSpawnerPositions.clear();
        saveJson();
        saveCsv();
    }

    private void fillTable(GuiTheme theme, WTable table) {
        loggedSpawners.stream()
            .distinct()
            .forEach(ls -> {
                table.add(theme.label("Pos: " + ls.x + ", " + ls.y + ", " + ls.z));
                
                WButton gotoBtn = table.add(theme.button("Goto")).widget();
                gotoBtn.action = () -> PathManagers.get().moveTo(new BlockPos(ls.x, ls.y, ls.z), true);
                
                WMinus delete = table.add(theme.minus()).widget();
                delete.action = () -> removeLoggedSpawner(ls, theme, table);
                
                table.row();
            });
    }

    private void removeLoggedSpawner(LoggedSpawner ls, GuiTheme theme, WTable table) {
        loggedSpawners.remove(ls);
        loggedSpawnerPositions.remove(new BlockPos(ls.x, ls.y, ls.z));
        table.clear();
        fillTable(theme, table);
        saveJson();
        saveCsv();
    }

    private static class LoggedSpawner {
        public final int x, y, z;

        public LoggedSpawner(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public void write(Writer writer) throws IOException {
            writer.write(x + "," + y + "," + z + "\n");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LoggedSpawner)) return false;
            LoggedSpawner that = (LoggedSpawner) o;
            return x == that.x && y == that.y && z == that.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }
}

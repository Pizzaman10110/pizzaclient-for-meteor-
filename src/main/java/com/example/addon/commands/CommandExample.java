package java.com.pizzaclient.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandSource;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class WorldInfoCommand extends Command {
    // 1.20+ specific blocks
    private static final Set<Block> NEW_OVERWORLD_BLOCKS = EnumSet.of(
        Blocks.DEEPSLATE,
        Blocks.AMETHYST_BLOCK,
        Blocks.AZALEA,
        Blocks.BIG_DRIPLEAF,
        Blocks.BIG_DRIPLEAF_STEM,
        Blocks.SMALL_DRIPLEAF,
        Blocks.CAVE_VINES,
        Blocks.CAVE_VINES_PLANT,
        Blocks.SPORE_BLOSSOM,
        Blocks.COPPER_ORE,
        Blocks.DEEPSLATE_COPPER_ORE,
        Blocks.DEEPSLATE_IRON_ORE,
        Blocks.DEEPSLATE_COAL_ORE,
        Blocks.DEEPSLATE_REDSTONE_ORE,
        Blocks.DEEPSLATE_EMERALD_ORE,
        Blocks.DEEPSLATE_GOLD_ORE,
        Blocks.DEEPSLATE_LAPIS_ORE,
        Blocks.DEEPSLATE_DIAMOND_ORE,
        Blocks.GLOW_LICHEN,
        Blocks.RAW_COPPER_BLOCK,
        Blocks.RAW_IRON_BLOCK,
        Blocks.DRIPSTONE_BLOCK,
        Blocks.MOSS_BLOCK,
        Blocks.POINTED_DRIPSTONE,
        Blocks.SMOOTH_BASALT,
        Blocks.TUFF,
        Blocks.CALCITE,
        Blocks.HANGING_ROOTS,
        Blocks.ROOTED_DIRT,
        Blocks.AZALEA_LEAVES,
        Blocks.FLOWERING_AZALEA_LEAVES,
        // 1.19+ blocks
        Blocks.MUD,
        Blocks.MUDDY_MANGROVE_ROOTS,
        Blocks.MANGROVE_ROOTS,
        Blocks.MANGROVE_LEAVES,
        Blocks.MANGROVE_LOG,
        Blocks.MANGROVE_PROPAGULE,
        Blocks.PACKED_MUD,
        Blocks.MUD_BRICKS,
        // 1.20+ blocks
        Blocks.CHERRY_LOG,
        Blocks.CHERRY_LEAVES,
        Blocks.CHERRY_SAPLING,
        Blocks.PINK_PETALS,
        Blocks.BAMBOO_BLOCK,
        Blocks.BAMBOO_PLANKS,
        Blocks.BAMBOO_MOSAIC,
        Blocks.CHISELED_BOOKSHELF,
        Blocks.DECORATED_POT
    );

    private static final Set<Block> ORE_BLOCKS = EnumSet.of(
        Blocks.COAL_ORE,
        Blocks.COPPER_ORE,
        Blocks.DEEPSLATE_COPPER_ORE,
        Blocks.IRON_ORE,
        Blocks.DEEPSLATE_IRON_ORE,
        Blocks.GOLD_ORE,
        Blocks.DEEPSLATE_GOLD_ORE,
        Blocks.LAPIS_ORE,
        Blocks.DEEPSLATE_LAPIS_ORE,
        Blocks.DIAMOND_ORE,
        Blocks.DEEPSLATE_DIAMOND_ORE,
        Blocks.REDSTONE_ORE,
        Blocks.DEEPSLATE_REDSTONE_ORE,
        Blocks.EMERALD_ORE,
        Blocks.DEEPSLATE_EMERALD_ORE,
        Blocks.NETHER_QUARTZ_ORE,
        Blocks.NETHER_GOLD_ORE,
        Blocks.ANCIENT_DEBRIS
    );

    public WorldInfoCommand() {
        super("world", "Displays comprehensive world information including borders, spawn, and generation details");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            displayWorldInfo(false);
            return SINGLE_SUCCESS;
        });

        builder.then(literal("save").executes(ctx -> {
            if (mc.player.getMainHandStack().isEmpty()) {
                error("Hold any item in your main hand to save world info");
                return SINGLE_SUCCESS;
            }
            displayWorldInfo(true);
            return SINGLE_SUCCESS;
        }));
    }

    private void displayWorldInfo(boolean saveToFile) {
        World world = mc.world;
        if (world == null) {
            error("World is not loaded");
            return;
        }

        // Gather all information
        WorldInfoData info = new WorldInfoData(
            checkChunkGeneration(getCurrentChunk()),
            getWorldBordersString(world),
            getSpawnLocationString(world),
            getDeathLocationString(),
            "Difficulty: " + world.getDifficulty(),
            "Permission Level: " + mc.player.getPermissionLevel(),
            "Simulation Distance: " + world.getSimulationDistance() + " chunks",
            "View Distance: " + world.getViewDistance() + " chunks",
            "Day: " + (int) Math.floor(world.getTime() / 24000),
            "Time: " + String.format("%d:%02d", 
                (world.getTimeOfDay() / 1000 + 6) % 24, 
                (world.getTimeOfDay() % 1000) * 60 / 1000),
            "Moon Phase: " + world.getMoonPhase(),
            "Players: " + getKnownPlayers(world.getScoreboard())
        );

        // Display or save
        if (saveToFile) {
            saveWorldInfoToFile(info);
        } else {
            info.sendToChat();
        }
    }

    private record WorldInfoData(
        GenerationInfo generation,
        String borders,
        String spawn,
        Text death,
        String difficulty,
        String permission,
        String simulation,
        String viewDistance,
        String day,
        String time,
        String moonPhase,
        String players
    ) {
        public void sendToChat() {
            ChatUtils.sendMsg(Text.of(generation.message));
            ChatUtils.sendMsg(Text.of(borders));
            ChatUtils.sendMsg(Text.of(spawn));
            ChatUtils.sendMsg(death);
            ChatUtils.sendMsg(Text.of(difficulty));
            ChatUtils.sendMsg(Text.of(permission));
            ChatUtils.sendMsg(Text.of(simulation));
            ChatUtils.sendMsg(Text.of(viewDistance));
            ChatUtils.sendMsg(Text.of(day));
            ChatUtils.sendMsg(Text.of(time));
            ChatUtils.sendMsg(Text.of(moonPhase));
            ChatUtils.sendMsg(Text.of(players));
        }
    }

    private String getKnownPlayers(Scoreboard scoreboard) {
        StringBuilder builder = new StringBuilder();
        scoreboard.getKnownScoreHolders().forEach(holder -> 
            builder.append(holder.getNameForScoreboard()).append(", ")
        );
        return builder.isEmpty() ? "None" : builder.substring(0, builder.length() - 2);
    }

    private WorldChunk getCurrentChunk() {
        return mc.world.getChunk((int) mc.player.getX() >> 4, (int) mc.player.getZ() >> 4);
    }

    private GenerationInfo checkChunkGeneration(WorldChunk chunk) {
        boolean isNewGeneration = false;
        boolean foundAnyOre = false;

        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = 0; x < 16 && !(isNewGeneration && foundAnyOre); x++) {
            for (int z = 0; z < 16 && !(isNewGeneration && foundAnyOre); z++) {
                for (int y = chunk.getBottomY(); y < chunk.getTopY(); y++) {
                    pos.set(x, y, z);
                    Block block = chunk.getBlockState(pos).getBlock();

                    if (!foundAnyOre && ORE_BLOCKS.contains(block)) {
                        foundAnyOre = true;
                    }
                    if (!isNewGeneration && NEW_OVERWORLD_BLOCKS.contains(block)) {
                        isNewGeneration = true;
                    }

                    if (isNewGeneration && foundAnyOre) break;
                }
            }
        }

        String message = isNewGeneration 
            ? "This chunk contains 1.17+ blocks (new generation)" 
            : "This chunk contains only pre-1.17 blocks (old generation)";
        
        return new GenerationInfo(isNewGeneration, message);
    }

    private String getWorldBordersString(World world) {
        return String.format("World Borders - X: %d to %d | Z: %d to %d",
            (int) world.getWorldBorder().getBoundWest(),
            (int) world.getWorldBorder().getBoundEast(),
            (int) world.getWorldBorder().getBoundNorth(),
            (int) world.getWorldBorder().getBoundSouth()
        );
    }

    private String getSpawnLocationString(World world) {
        BlockPos spawn = world.getLevelProperties().getSpawnPos();
        return String.format("World Spawn: %d %d %d", spawn.getX(), spawn.getY(), spawn.getZ());
    }

    private Text getDeathLocationString() {
        return mc.player.getLastDeathPos()
            .map(pos -> Text.of(String.format(
                "Last Death: %d %d %d in %s",
                pos.pos().getX(),
                pos.pos().getY(),
                pos.pos().getZ(),
                pos.dimension().getValue()
            )))
            .orElse(Text.of("No recorded death location"));
    }

    private void saveWorldInfoToFile(WorldInfoData info) {
        try {
            String serverId = getServerIdentifier();
            Path dir = Paths.get("TrouserStreak", "SavedWorldInfo", serverId);
            Path file = dir.resolve("WorldInfo_" + System.currentTimeMillis() + ".txt");
            
            Files.createDirectories(dir);
            
            try (FileWriter writer = new FileWriter(file.toFile())) {
                writer.write(info.generation.message + "\n");
                writer.write(info.borders + "\n");
                writer.write(info.spawn + "\n");
                writer.write(info.death.getString() + "\n");
                writer.write(info.difficulty + "\n");
                writer.write(info.permission + "\n");
                writer.write(info.simulation + "\n");
                writer.write(info.viewDistance + "\n");
                writer.write(info.day + "\n");
                writer.write(info.time + "\n");
                writer.write(info.moonPhase + "\n");
                writer.write(info.players + "\n");
            }
            
            info.sendToChat();
            ChatUtils.sendMsg(Text.of("§aWorld info saved to: §f" + file));
        } catch (IOException e) {
            error("Failed to save world info: " + e.getMessage());
        }
    }

    private String getServerIdentifier() {
        if (mc.isInSingleplayer()) {
            String path = mc.getServer().getSavePath(WorldSavePath.ROOT).toString();
            return path.substring(path.lastIndexOf(File.separatorChar) + 1)
                .replace(':', '_');
        }
        return mc.getCurrentServerEntry().address.replace(':', '_');
    }

    private record GenerationInfo(boolean isNewGeneration, String message) {}
}

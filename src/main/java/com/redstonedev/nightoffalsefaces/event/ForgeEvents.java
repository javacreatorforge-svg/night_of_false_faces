package com.redstonedev.nightoffalsefaces.event;

import com.redstonedev.nightoffalsefaces.NightOfFalseFaces;
import com.redstonedev.nightoffalsefaces.command.SkinwalkerCommand;
import com.redstonedev.nightoffalsefaces.entity.FaceThiefEntity;
import com.redstonedev.nightoffalsefaces.entity.FaceThiefEntity.Disguise;
import com.redstonedev.nightoffalsefaces.entity.SkinwalkerSheepEntity;
import com.redstonedev.nightoffalsefaces.init.ModBlocks;
import com.redstonedev.nightoffalsefaces.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ForgeEvents {

    private static final Disguise[] ANIMAL_FORMS = {
            Disguise.PIG, Disguise.CHICKEN, Disguise.COW, Disguise.VILLAGER, Disguise.SHEEP, Disguise.HORSE,
            Disguise.WOLF, Disguise.DONKEY, Disguise.RABBIT, Disguise.WANDERING_TRADER, Disguise.CAT };

    private static final Random RNG = new Random();
    private int tickCounter = 0;

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        SkinwalkerCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) return;
        tickCounter++;
        if (tickCounter % 100 != 0) return; // ~5s
        for (ServerLevel level : event.getServer().getAllLevels()) {
            trySpawnFaceThief(level);
            trySpawnSheep(level);
            tryDropGutBlock(level);
        }
    }

    private List<ServerPlayer> realPlayers(ServerLevel level) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (!p.isCreative() && !p.isSpectator() && p.isAlive()) out.add(p);
        }
        return out;
    }

    private boolean hasFaceThief(ServerLevel level) {
        return !level.getEntities(ModEntities.FACE_THIEF.get(), n -> !n.isRemoved()).isEmpty();
    }

    private int sheepCount(ServerLevel level) {
        return level.getEntities(ModEntities.SKINWALKER_SHEEP.get(), n -> !n.isRemoved()).size();
    }

    private boolean isUnderground(ServerLevel level, ServerPlayer p) {
        BlockPos pos = p.blockPosition();
        return pos.getY() < level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - 5;
    }

    private void trySpawnFaceThief(ServerLevel level) {
        List<ServerPlayer> players = realPlayers(level);
        if (players.isEmpty() || hasFaceThief(level)) return; // only one at a time

        for (ServerPlayer player : players) {
            boolean underground = isUnderground(level, player);
            boolean night = !level.isDay() || underground;
            // Kept fairly rare so he doesn't show up WAY too much.
            if (RNG.nextInt(500) != 0) continue;

            BlockPos pos = underground ? pickCaveSpawn(level, player) : pickSurfaceSpawn(level, player);
            if (pos == null) continue;

            FaceThiefEntity ent = ModEntities.FACE_THIEF.get().create(level);
            if (ent == null) return;
            ent.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0);

            // Night: stalks a bunch. Day: shapeshifts a bunch.
            int r = RNG.nextInt(100);
            if (night) {
                if (r < 60) ent.setStalking(true);
                else if (r < 90) ent.setDisguise(ANIMAL_FORMS[RNG.nextInt(ANIMAL_FORMS.length)]);
                // else walk normally (turns aggressive when seen)
            } else {
                if (r < 70) {
                    if (RNG.nextInt(100) < 20) ent.disguiseAsPlayer();
                    else ent.setDisguise(ANIMAL_FORMS[RNG.nextInt(ANIMAL_FORMS.length)]);
                } else if (r < 85) {
                    ent.setStalking(true);
                }
                // else walk normally
            }

            ent.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            level.addFreshEntity(ent);
            return;
        }
    }

    private void trySpawnSheep(ServerLevel level) {
        if (level.isDay() == false) return;            // sheep only during the day
        if (sheepCount(level) >= 2) return;
        List<ServerPlayer> players = realPlayers(level);
        if (players.isEmpty()) return;
        for (ServerPlayer player : players) {
            if (isUnderground(level, player)) continue;
            if (RNG.nextInt(700) != 0) continue;
            BlockPos pos = pickSurfaceSpawn(level, player);
            if (pos == null) continue;
            SkinwalkerSheepEntity sheep = ModEntities.SKINWALKER_SHEEP.get().create(level);
            if (sheep == null) return;
            sheep.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0);
            sheep.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            level.addFreshEntity(sheep);
            return;
        }
    }

    private BlockPos pickSurfaceSpawn(ServerLevel level, ServerPlayer player) {
        BlockPos origin = player.blockPosition();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = RNG.nextDouble() * Math.PI * 2.0;
            double dist = 14 + RNG.nextInt(15);
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * dist);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos c = new BlockPos(x, y, z);
            if (level.getBlockState(c).isAir() && level.getBlockState(c.above()).isAir()
                    && !level.getBlockState(c.below()).isAir()) {
                return c;
            }
        }
        return null;
    }

    /** Pick a real open air pocket near the player - never inside a wall (fixes the wall hurt-loop). */
    private BlockPos pickCaveSpawn(ServerLevel level, ServerPlayer player) {
        BlockPos origin = player.blockPosition();
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = RNG.nextDouble() * Math.PI * 2.0;
            double dist = 6 + RNG.nextInt(10);
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * dist);
            for (int dy = 0; dy <= 6; dy++) {
                for (int sign = 1; sign >= -1; sign -= 2) {
                    if (dy == 0 && sign == -1) continue;
                    int y = origin.getY() + sign * dy;
                    BlockPos c = new BlockPos(x, y, z);
                    // Need TWO blocks of air and a solid floor - guarantees an open pocket, not a wall.
                    if (level.getBlockState(c).isAir() && level.getBlockState(c.above()).isAir()
                            && level.getBlockState(c.below()).getMaterial().isSolid()
                            && c.distSqr(origin) >= 25) {
                        return c;
                    }
                }
            }
        }
        return null;
    }

    private void tryDropGutBlock(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (RNG.nextInt(1500) != 0) continue;
            BlockPos origin = player.blockPosition();
            for (int attempt = 0; attempt < 16; attempt++) {
                int x = origin.getX() + RNG.nextInt(33) - 16;
                int z = origin.getZ() + RNG.nextInt(33) - 16;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos c = new BlockPos(x, y, z);
                BlockState here = level.getBlockState(c);
                BlockState below = level.getBlockState(c.below());
                if (here.isAir() && below.getMaterial().isSolid() && !below.is(ModBlocks.GUT.get())) {
                    level.setBlock(c, ModBlocks.GUT.get().defaultBlockState(), 3);
                    break;
                }
            }
        }
    }
}

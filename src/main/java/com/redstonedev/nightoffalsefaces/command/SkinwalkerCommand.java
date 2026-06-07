package com.redstonedev.nightoffalsefaces.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.redstonedev.nightoffalsefaces.entity.FaceThiefEntity;
import com.redstonedev.nightoffalsefaces.entity.SkinwalkerSheepEntity;
import com.redstonedev.nightoffalsefaces.init.ModEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * /skinwalker run shapeshifting  - spawns a Face Thief disguised as a random animal
 * /skinwalker run stalk          - spawns a Face Thief in stalking mode
 * /skinwalker run player         - spawns a Face Thief disguised as a random online player
 * /skinwalker run sheep          - spawns a Skinwalker Sheep
 */
public class SkinwalkerCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("skinwalker")
                        .requires(src -> src.hasPermission(0))
                        .then(Commands.literal("run")
                                .then(Commands.literal("shapeshifting").executes(ctx -> spawnThief(ctx, "shapeshifting")))
                                .then(Commands.literal("stalk").executes(ctx -> spawnThief(ctx, "stalk")))
                                .then(Commands.literal("player").executes(ctx -> spawnThief(ctx, "player")))
                                .then(Commands.literal("sheep").executes(SkinwalkerCommand::spawnSheep))));
    }

    private static Vec3 frontOf(ServerPlayer player, ServerLevel level) {
        Vec3 look = player.getLookAngle();
        double x = player.getX() + look.x * 12.0;
        double z = player.getZ() + look.z * 12.0;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        return new Vec3(x + 0.5, y, z + 0.5);
    }

    private static int spawnThief(CommandContext<CommandSourceStack> ctx, String mode) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("Must be run by a player.")); return 0; }
        ServerLevel level = source.getLevel();
        Vec3 pos = frontOf(player, level);

        FaceThiefEntity thief = ModEntities.FACE_THIEF.get().create(level);
        if (thief == null) { source.sendFailure(Component.literal("Failed to create the skinwalker.")); return 0; }
        thief.moveTo(pos.x, pos.y, pos.z, player.getYRot() + 180.0F, 0);
        thief.finalizeSpawn(level, level.getCurrentDifficultyAt(thief.blockPosition()), MobSpawnType.COMMAND, null, null);

        if (mode.equals("shapeshifting")) {
            FaceThiefEntity.Disguise[] forms = {
                    FaceThiefEntity.Disguise.PIG, FaceThiefEntity.Disguise.CHICKEN, FaceThiefEntity.Disguise.COW,
                    FaceThiefEntity.Disguise.VILLAGER, FaceThiefEntity.Disguise.SHEEP, FaceThiefEntity.Disguise.HORSE,
                    FaceThiefEntity.Disguise.WOLF, FaceThiefEntity.Disguise.DONKEY, FaceThiefEntity.Disguise.RABBIT,
                    FaceThiefEntity.Disguise.WANDERING_TRADER, FaceThiefEntity.Disguise.CAT };
            thief.setDisguise(forms[level.getRandom().nextInt(forms.length)]);
        } else if (mode.equals("player")) {
            thief.disguiseAsPlayer();
        } else {
            thief.setStalking(true);
        }
        level.addFreshEntity(thief);
        source.sendSuccess(Component.literal("Spawned a skinwalker (" + mode + ")."), false);
        return 1;
    }

    private static int spawnSheep(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("Must be run by a player.")); return 0; }
        ServerLevel level = source.getLevel();
        Vec3 pos = frontOf(player, level);
        SkinwalkerSheepEntity sheep = ModEntities.SKINWALKER_SHEEP.get().create(level);
        if (sheep == null) { source.sendFailure(Component.literal("Failed to create the sheep.")); return 0; }
        sheep.moveTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360F, 0);
        sheep.finalizeSpawn(level, level.getCurrentDifficultyAt(sheep.blockPosition()), MobSpawnType.COMMAND, null, null);
        level.addFreshEntity(sheep);
        source.sendSuccess(Component.literal("Spawned a Skinwalker Sheep."), false);
        return 1;
    }
}

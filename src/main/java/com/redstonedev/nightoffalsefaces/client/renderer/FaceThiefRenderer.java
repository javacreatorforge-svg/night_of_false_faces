package com.redstonedev.nightoffalsefaces.client.renderer;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.redstonedev.nightoffalsefaces.client.model.FaceThiefModel;
import com.redstonedev.nightoffalsefaces.entity.FaceThiefEntity;
import com.redstonedev.nightoffalsefaces.entity.FaceThiefEntity.Disguise;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib3.renderers.geo.GeoEntityRenderer;

import java.util.EnumMap;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class FaceThiefRenderer extends GeoEntityRenderer<FaceThiefEntity> {

    private final EnumMap<Disguise, Entity> fakeCache = new EnumMap<>(Disguise.class);
    private RemotePlayer fakePlayer;
    private String fakePlayerName = "";

    public FaceThiefRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new FaceThiefModel());
        this.shadowRadius = 0.5F;
        this.widthScale  = 1.33F;
        this.heightScale = 1.33F;
    }

    @Override
    public void render(FaceThiefEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Disguise disguise = entity.getDisguise();
        if (disguise == Disguise.NONE) {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }

        Entity fake = (disguise == Disguise.PLAYER)
                ? getFakePlayer(entity.getDisguisePlayerName())
                : getFakeAnimal(disguise);
        if (fake == null) {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }

        fake.setPos(entity.getX(), entity.getY(), entity.getZ());
        fake.setYRot(entityYaw);
        fake.setXRot(0);
        fake.yRotO = entityYaw;
        fake.xRotO = 0;
        if (fake instanceof LivingEntity) {
            LivingEntity le = (LivingEntity) fake;
            le.yBodyRot = entityYaw; le.yBodyRotO = entityYaw;
            le.yHeadRot = entityYaw; le.yHeadRotO = entityYaw;
            // Drive a real walking animation when the Face Thief is moving.
            boolean moving = entity.isMovingAnim();
            le.animationSpeedOld = le.animationSpeed;
            le.animationSpeed += ((moving ? 1.0F : 0.0F) - le.animationSpeed) * 0.4F;
            le.animationPosition += le.animationSpeed;
        }
        fake.tickCount = entity.tickCount;

        @SuppressWarnings("unchecked")
        EntityRenderer<Entity> r = (EntityRenderer<Entity>)
                Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(fake);
        if (r != null) {
            r.render(fake, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        } else {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        }
    }

    private Entity getFakeAnimal(Disguise disguise) {
        Entity fake = fakeCache.get(disguise);
        if (fake == null) {
            if (Minecraft.getInstance().level == null) return null;
            EntityType<?> type = typeFor(disguise);
            fake = type != null ? type.create(Minecraft.getInstance().level) : null;
            if (fake != null) fakeCache.put(disguise, fake);
        }
        return fake;
    }

    private EntityType<?> typeFor(Disguise d) {
        if (d == Disguise.PIG) return EntityType.PIG;
        if (d == Disguise.CHICKEN) return EntityType.CHICKEN;
        if (d == Disguise.COW) return EntityType.COW;
        if (d == Disguise.VILLAGER) return EntityType.VILLAGER;
        if (d == Disguise.SHEEP) return EntityType.SHEEP;
        if (d == Disguise.HORSE) return EntityType.HORSE;
        if (d == Disguise.WOLF) return EntityType.WOLF;
        if (d == Disguise.DONKEY) return EntityType.DONKEY;
        if (d == Disguise.RABBIT) return EntityType.RABBIT;
        if (d == Disguise.WANDERING_TRADER) return EntityType.WANDERING_TRADER;
        if (d == Disguise.CAT) return EntityType.CAT;
        return null;
    }

    /** A fake player wearing the mimicked player's skin + name tag (best effort). */
    private RemotePlayer getFakePlayer(String name) {
        if (name == null || name.isEmpty()) name = "Steve";
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (fakePlayer != null && name.equals(fakePlayerName)) return fakePlayer;

        GameProfile profile = null;
        if (mc.getConnection() != null) {
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                if (info.getProfile().getName().equalsIgnoreCase(name)) { profile = info.getProfile(); break; }
            }
        }
        if (profile == null) {
            profile = new GameProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()), name);
        }
        fakePlayer = new RemotePlayer((ClientLevel) mc.level, profile, null);
        fakePlayerName = name;
        return fakePlayer;
    }
}

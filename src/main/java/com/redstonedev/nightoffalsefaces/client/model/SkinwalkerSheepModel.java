package com.redstonedev.nightoffalsefaces.client.model;

import com.redstonedev.nightoffalsefaces.NightOfFalseFaces;
import com.redstonedev.nightoffalsefaces.entity.SkinwalkerSheepEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;

public class SkinwalkerSheepModel extends AnimatedGeoModel<SkinwalkerSheepEntity> {
    private static final ResourceLocation MODEL =
            new ResourceLocation(NightOfFalseFaces.MODID, "geo/skinwalker_sheep.geo.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(NightOfFalseFaces.MODID, "textures/entity/skinwalker_sheep.png");
    private static final ResourceLocation ANIMATIONS =
            new ResourceLocation(NightOfFalseFaces.MODID, "animations/skinwalker_sheep.animation.json");

    @Override public ResourceLocation getModelResource(SkinwalkerSheepEntity e)     { return MODEL; }
    @Override public ResourceLocation getTextureResource(SkinwalkerSheepEntity e)   { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(SkinwalkerSheepEntity e) { return ANIMATIONS; }
}

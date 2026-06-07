package com.redstonedev.nightoffalsefaces.client.renderer;

import com.redstonedev.nightoffalsefaces.client.model.SkinwalkerSheepModel;
import com.redstonedev.nightoffalsefaces.entity.SkinwalkerSheepEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib3.renderers.geo.GeoEntityRenderer;

@OnlyIn(Dist.CLIENT)
public class SkinwalkerSheepRenderer extends GeoEntityRenderer<SkinwalkerSheepEntity> {
    public SkinwalkerSheepRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new SkinwalkerSheepModel());
        this.shadowRadius = 0.4F;
    }
}

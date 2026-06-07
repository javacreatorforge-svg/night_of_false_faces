package com.redstonedev.nightoffalsefaces.init;

import com.redstonedev.nightoffalsefaces.NightOfFalseFaces;
import com.redstonedev.nightoffalsefaces.entity.FaceThiefEntity;
import com.redstonedev.nightoffalsefaces.entity.SkinwalkerSheepEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, NightOfFalseFaces.MODID);

    public static final RegistryObject<EntityType<FaceThiefEntity>> FACE_THIEF =
            ENTITIES.register("face_thief", () -> EntityType.Builder
                    .<FaceThiefEntity>of(FaceThiefEntity::new, MobCategory.MONSTER)
                    .sized(1.0F, 3.2F)
                    .clientTrackingRange(12)
                    .build(new ResourceLocation(NightOfFalseFaces.MODID, "face_thief").toString()));

    public static final RegistryObject<EntityType<SkinwalkerSheepEntity>> SKINWALKER_SHEEP =
            ENTITIES.register("skinwalker_sheep", () -> EntityType.Builder
                    .<SkinwalkerSheepEntity>of(SkinwalkerSheepEntity::new, MobCategory.CREATURE)
                    .sized(0.9F, 1.3F)
                    .clientTrackingRange(10)
                    .build(new ResourceLocation(NightOfFalseFaces.MODID, "skinwalker_sheep").toString()));
}

package com.redstonedev.nightoffalsefaces.init;

import com.redstonedev.nightoffalsefaces.NightOfFalseFaces;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, NightOfFalseFaces.MODID);

    public static final RegistryObject<SoundEvent> HURT        = register("face_thief_hurt");
    public static final RegistryObject<SoundEvent> SPOTTED     = register("face_thief_spotted");
    public static final RegistryObject<SoundEvent> SCREAM      = register("face_thief_scream");
    public static final RegistryObject<SoundEvent> DEATH       = register("face_thief_death");
    public static final RegistryObject<SoundEvent> AFTERMATH   = register("face_thief_aftermath");
    public static final RegistryObject<SoundEvent> CHASE_THEME = register("face_thief_chase_theme");
    public static final RegistryObject<SoundEvent> HELPME      = register("face_thief_helpme");

    /** Plays randomly during the world (never on spawn). */
    public static final List<RegistryObject<SoundEvent>> RANDOM_NOISES = new ArrayList<>();
    static {
        RANDOM_NOISES.add(register("face_thief_noise1"));
        RANDOM_NOISES.add(register("face_thief_noise2"));
        RANDOM_NOISES.add(register("face_thief_noise3"));
        RANDOM_NOISES.add(register("face_thief_distance_moan"));
        RANDOM_NOISES.add(register("face_thief_mimicking_helpme_echo"));
        RANDOM_NOISES.add(register("face_thief_ambient"));
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name,
                () -> new SoundEvent(new ResourceLocation(NightOfFalseFaces.MODID, name)));
    }
}

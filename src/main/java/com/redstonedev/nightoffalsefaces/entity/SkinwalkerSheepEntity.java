package com.redstonedev.nightoffalsefaces.entity;

import com.mojang.math.Vector3f;
import com.redstonedev.nightoffalsefaces.init.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import software.bernie.geckolib3.util.GeckoLibUtil;

public class SkinwalkerSheepEntity extends Animal implements IAnimatable {

    private final AnimationFactory factory = GeckoLibUtil.createFactory(this);
    private boolean transforming = false;

    public SkinwalkerSheepEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 12.0F));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level.isClientSide || transforming) return;

        Player p = nearestRealPlayer();
        if (p != null) {
            double dist = this.distanceTo(p);
            boolean noticed = dist < 16.0D && (isPlayerStaringAt(p) || this.getSensing().hasLineOfSight(p));
            if (noticed) lockYawTo(p); // stare back
            if (dist < 2.6D) transform(p);   // too close -> reveal
        }
    }

    private void transform(Player p) {
        if (!(this.level instanceof ServerLevel)) return;
        transforming = true;
        ServerLevel sl = (ServerLevel) this.level;
        DustParticleOptions blood = new DustParticleOptions(new Vector3f(0.55F, 0.0F, 0.0F), 1.5F);
        sl.sendParticles(blood, this.getX(), this.getY() + 0.7D, this.getZ(), 40, 0.4D, 0.6D, 0.4D, 0.02D);

        FaceThiefEntity thief = ModEntities.FACE_THIEF.get().create(this.level);
        if (thief != null) {
            thief.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0);
            thief.finalizeSpawn(sl, this.level.getCurrentDifficultyAt(this.blockPosition()),
                    net.minecraft.world.entity.MobSpawnType.CONVERSION, null, null);
            thief.setFaceThiefAggressive(true);
            this.level.addFreshEntity(thief);
        }
        this.discard();
    }

    private Player nearestRealPlayer() {
        Player best = null;
        double bestSq = 24.0D * 24.0D;
        for (int i = 0; i < this.level.players().size(); i++) {
            Player pl = this.level.players().get(i);
            if (pl.isCreative() || pl.isSpectator() || !pl.isAlive()) continue;
            double d = pl.distanceToSqr(this);
            if (d < bestSq) { bestSq = d; best = pl; }
        }
        return best;
    }

    private boolean isPlayerStaringAt(Player p) {
        double dx = this.getX() - p.getX();
        double dy = this.getEyeY() - p.getEyeY();
        double dz = this.getZ() - p.getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001D) return false;
        dx /= len; dy /= len; dz /= len;
        Vec3 look = p.getViewVector(1.0F);
        return (look.x * dx + look.y * dy + look.z * dz) > 0.9D && p.hasLineOfSight(this);
    }

    private void lockYawTo(Player p) {
        double dx = p.getX() - this.getX();
        double dz = p.getZ() - this.getZ();
        float yaw = (float) (Math.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;
        this.setYBodyRot(yaw); this.setYHeadRot(yaw); this.setYRot(yaw);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Hitting it makes it reveal (no real damage taken first).
        if (!this.level.isClientSide && source.getEntity() instanceof LivingEntity && !transforming) {
            Player p = nearestRealPlayer();
            transform(p != null ? p : null);
            return true;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(this, "controller", 4, this::predicate));
    }

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        if (event.isMoving()) {
            event.getController().setAnimation(new AnimationBuilder().loop("animation.skinwalker_sheep.walk"));
        } else {
            event.getController().setAnimation(new AnimationBuilder().loop("animation.skinwalker_sheep.idle"));
        }
        return PlayState.CONTINUE;
    }

    @Override public AnimationFactory getFactory() { return factory; }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob mate) { return null; }

    @Override public boolean removeWhenFarAway(double d) { return false; }
}

package com.redstonedev.nightoffalsefaces.entity;

import com.mojang.math.Vector3f;
import com.redstonedev.nightoffalsefaces.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib3.core.AnimationState;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.builder.ILoopType.EDefaultLoopTypes;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import software.bernie.geckolib3.util.GeckoLibUtil;

import java.util.List;

public class FaceThiefEntity extends Monster implements IAnimatable {

    // Camel is 1.20+ and does NOT exist in 1.19.2, so it is intentionally omitted.
    public enum Disguise { NONE, PIG, CHICKEN, COW, VILLAGER, SHEEP, HORSE,
        WOLF, DONKEY, RABBIT, WANDERING_TRADER, CAT, PLAYER }

    private static final EntityDataAccessor<Boolean> DATA_AGGRESSIVE =
            SynchedEntityData.defineId(FaceThiefEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_STALKING =
            SynchedEntityData.defineId(FaceThiefEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CLIMBING =
            SynchedEntityData.defineId(FaceThiefEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SPOTTED =
            SynchedEntityData.defineId(FaceThiefEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_DISGUISE =
            SynchedEntityData.defineId(FaceThiefEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_POSTURE =
            SynchedEntityData.defineId(FaceThiefEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_MOVING =
            SynchedEntityData.defineId(FaceThiefEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_PLAYER_NAME =
            SynchedEntityData.defineId(FaceThiefEntity.class, EntityDataSerializers.STRING);

    private static final double SPEED_WALK = 0.20D;
    private static final double SPEED_RUN  = 0.34D;
    private static final double SPEED_DISGUISED = 0.18D;
    private static final double SPEED_CROUCH = 0.24D;
    private static final double SPEED_CRAWL  = 0.18D;

    private final AnimationFactory factory = GeckoLibUtil.createFactory(this);

    private int aliveTicks = 0;
    private int spottedTicks = 0;
    private int aggroNoKillTicks = 0;
    private int wanderUnnoticedTicks = 0;
    private boolean spotSoundPlayed = false;
    private boolean pendingDespawn = false;
    private boolean playAftermath = false;
    private double lastX, lastZ;

    private int helpmeCooldown;
    private int noiseCooldown;
    private int screamCooldown = 0;
    private boolean clientChaseSoundStarted = false;

    public FaceThiefEntity(EntityType<? extends FaceThiefEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.xpReward = 0;
        this.maxUpStep = 1.0F;
        this.helpmeCooldown = 600 + this.random.nextInt(1200);
        this.noiseCooldown  = 700 + this.random.nextInt(1400);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 5000.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.MOVEMENT_SPEED, SPEED_WALK)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_AGGRESSIVE, false);
        this.entityData.define(DATA_STALKING, false);
        this.entityData.define(DATA_CLIMBING, false);
        this.entityData.define(DATA_SPOTTED, false);
        this.entityData.define(DATA_DISGUISE, 0);
        this.entityData.define(DATA_POSTURE, 0);
        this.entityData.define(DATA_MOVING, false);
        this.entityData.define(DATA_PLAYER_NAME, "");
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        WallClimberNavigation nav = new WallClimberNavigation(this, level);
        nav.setCanOpenDoors(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true,
                e -> e instanceof Player && !((Player) e).isCreative() && !((Player) e).isSpectator()));
    }

    // === Accessors ============================================================
    public boolean isFaceThiefAggressive() { return this.entityData.get(DATA_AGGRESSIVE); }
    public boolean isStalking()  { return this.entityData.get(DATA_STALKING); }
    public boolean isClimbing()  { return this.entityData.get(DATA_CLIMBING); }
    public boolean isSpotted()   { return this.entityData.get(DATA_SPOTTED); }
    public int getPosture()      { return this.entityData.get(DATA_POSTURE); }
    public boolean isMovingAnim(){ return this.entityData.get(DATA_MOVING); }
    public String getDisguisePlayerName() { return this.entityData.get(DATA_PLAYER_NAME); }
    public Disguise getDisguise() {
        int i = this.entityData.get(DATA_DISGUISE);
        Disguise[] v = Disguise.values();
        return v[Math.max(0, Math.min(i, v.length - 1))];
    }
    public boolean isDisguised() { return getDisguise() != Disguise.NONE; }

    public void setFaceThiefAggressive(boolean aggressive) {
        boolean was = this.entityData.get(DATA_AGGRESSIVE);
        this.entityData.set(DATA_AGGRESSIVE, aggressive);
        if (aggressive) {
            this.entityData.set(DATA_STALKING, false);
            this.entityData.set(DATA_SPOTTED, false);
            this.entityData.set(DATA_DISGUISE, 0);
            this.setNoAi(false);
            if (!was && !this.level.isClientSide) {
                this.level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        ModSounds.SCREAM.get(), SoundSource.HOSTILE, 1.5F, 1.0F);
            }
        }
    }
    public void setStalking(boolean stalking) { this.entityData.set(DATA_STALKING, stalking); }
    public void setClimbing(boolean climbing) { this.entityData.set(DATA_CLIMBING, climbing); }
    public void setDisguise(Disguise disguise) {
        this.entityData.set(DATA_DISGUISE, disguise.ordinal());
        this.setNoAi(false);
        this.refreshDimensions();
    }
    public void setDisguisePlayerName(String name) { this.entityData.set(DATA_PLAYER_NAME, name); }

    public void disguiseAsPlayer() {
        String name = "Steve";
        if (this.level instanceof ServerLevel) {
            List<ServerPlayer> players = ((ServerLevel) this.level).players();
            if (!players.isEmpty()) name = players.get(this.random.nextInt(players.size())).getGameProfile().getName();
        }
        setDisguisePlayerName(name);
        setDisguise(Disguise.PLAYER);
    }

    @Override
    public boolean onClimbable() { return !isDisguised() && this.isClimbing(); }

    // === Dimensions / posture =================================================
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (isDisguised()) return EntityDimensions.scalable(0.9F, 1.4F);
        int p = this.entityData.get(DATA_POSTURE);
        if (p == 2) return EntityDimensions.scalable(0.9F, 0.9F);
        if (p == 1) return EntityDimensions.scalable(0.9F, 1.9F);
        return EntityDimensions.scalable(1.0F, 3.2F);
    }

    private int clearanceAt(BlockPos base) {
        int clear = 0;
        for (int i = 0; i < 4; i++) {
            BlockPos p = base.above(i);
            if (this.level.getBlockState(p).getCollisionShape(this.level, p).isEmpty()) clear++;
            else break;
        }
        return clear;
    }

    // === Tick =================================================================
    @Override
    public void tick() {
        super.tick();
        if (this.level.isClientSide) {
            if (!clientChaseSoundStarted && isFaceThiefAggressive() && !isStalking() && !isDisguised()) {
                clientChaseSoundStarted = true;
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                        net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> com.redstonedev.nightoffalsefaces.client.sound.ClientChaseSoundStarter.start(this));
            }
            return;
        }
        if (!isDisguised()) {
            boolean blocked = this.horizontalCollision;
            LivingEntity target = this.getTarget();
            if (blocked && target != null && target.getY() > this.getY() + 1.5) {
                Vec3 d = this.getDeltaMovement();
                this.setDeltaMovement(d.x, Math.max(0.18D, d.y), d.z);
            }
            this.setClimbing(blocked);
        } else {
            this.setClimbing(false);
        }
    }

    @Override
    public void aiStep() {
        if (!this.level.isClientSide && pendingDespawn) { this.discard(); return; }
        super.aiStep();
        if (this.level.isClientSide) return;

        aliveTicks++;
        if (helpmeCooldown > 0) helpmeCooldown--;
        if (noiseCooldown > 0) noiseCooldown--;
        if (screamCooldown > 0) screamCooldown--;

        double mdx = this.getX() - lastX, mdz = this.getZ() - lastZ;
        boolean moving = (mdx * mdx + mdz * mdz) > 1.0E-5D;
        lastX = this.getX(); lastZ = this.getZ();
        this.entityData.set(DATA_MOVING, moving);

        Player nearest = nearestRealPlayer();

        int posture = 0;
        if (!isDisguised()) {
            int clear = clearanceAt(this.blockPosition());
            if (nearest != null && (isFaceThiefAggressive() || isSpotted())) {
                double tdx = nearest.getX() - this.getX(), tdz = nearest.getZ() - this.getZ();
                double tlen = Math.sqrt(tdx * tdx + tdz * tdz);
                if (tlen > 0.001D) {
                    int ox = (int) Math.round(tdx / tlen), oz = (int) Math.round(tdz / tlen);
                    if (ox != 0 || oz != 0) clear = Math.min(clear, clearanceAt(this.blockPosition().offset(ox, 0, oz)));
                }
            }
            posture = clear <= 1 ? 2 : (clear == 2 ? 1 : 0);
        }
        if (posture != this.entityData.get(DATA_POSTURE)) {
            this.entityData.set(DATA_POSTURE, posture);
            this.refreshDimensions();
            this.getNavigation().stop();
        }

        double speed;
        if (isDisguised()) speed = SPEED_DISGUISED;
        else if (posture == 2) speed = SPEED_CRAWL;
        else if (posture == 1) speed = SPEED_CROUCH;
        else if (isFaceThiefAggressive() || (isSpotted() && moving)) speed = SPEED_RUN;
        else speed = SPEED_WALK;
        AttributeInstance attr = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null && Math.abs(attr.getBaseValue() - speed) > 1e-6) attr.setBaseValue(speed);

        if (isFaceThiefAggressive() || isSpotted()) breakDoorsAndGlass();

        tickRandomSounds(nearest);

        if (isStalking()) {
            if (nearest != null) {
                lockYawTo(nearest);
                if (isPlayerStaringAt(nearest)) { despawnWithAftermath(); return; }
            }
            wanderTimeout(nearest);
            return;
        }

        if (isDisguised()) {
            if (nearest != null) {
                double dist = this.distanceTo(nearest);
                if (dist < 3.0D) { reveal(); }
                else if (dist < 4.5D) { this.getNavigation().stop(); lockYawTo(nearest); }
                else { this.getNavigation().moveTo(nearest, 1.0D); }
            } else {
                this.getNavigation().stop();
            }
            if (aliveTicks >= 6000) despawnWithAftermath();
            return;
        }

        if (isFaceThiefAggressive()) {
            if (nearest != null) this.setTarget(nearest);
            aggroNoKillTicks++;
            if (screamCooldown <= 0) {
                this.level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        ModSounds.SCREAM.get(), SoundSource.HOSTILE, 1.2F, 1.0F);
                screamCooldown = 800 + this.random.nextInt(800);
            }
            if (aggroNoKillTicks >= 1200) despawnWithAftermath();
            return;
        }

        boolean lowArea = posture != 0;
        if (nearest != null && (isPlayerStaringAt(nearest) || canSee(nearest))) {
            if (lowArea) { setFaceThiefAggressive(true); return; }
            if (!isSpotted()) {
                this.entityData.set(DATA_SPOTTED, true);
                spottedTicks = 0;
                if (!spotSoundPlayed) {
                    this.level.playSound(null, this.getX(), this.getY(), this.getZ(),
                            ModSounds.SPOTTED.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
                    spotSoundPlayed = true;
                }
            }
        }

        if (isSpotted()) {
            spottedTicks++;
            wanderUnnoticedTicks = 0;
            if (nearest == null) { this.entityData.set(DATA_SPOTTED, false); return; }
            double dist = this.distanceTo(nearest);
            if (dist < 3.5D) { setFaceThiefAggressive(true); return; }
            if (isPlayerStaringAt(nearest)) {
                this.getNavigation().stop();
                lockYawTo(nearest);
            } else {
                this.getNavigation().moveTo(nearest, 1.0D);
            }
            if (spottedTicks >= 6000) setFaceThiefAggressive(true);
        } else {
            wanderTimeout(nearest);
        }
    }

    private void wanderTimeout(Player nearest) {
        boolean noticed = nearest != null && (this.distanceTo(nearest) < 8.0D || isPlayerStaringAt(nearest));
        if (noticed) wanderUnnoticedTicks = 0;
        else wanderUnnoticedTicks++;
        if (wanderUnnoticedTicks >= 1200) despawnWithAftermath();
    }

    private void reveal() {
        bloodBurst();
        setDisguise(Disguise.NONE);
        setFaceThiefAggressive(true);
    }

    private void despawnWithAftermath() { playAftermath = true; pendingDespawn = true; }

    private void bloodBurst() {
        if (!(this.level instanceof ServerLevel)) return;
        ServerLevel sl = (ServerLevel) this.level;
        DustParticleOptions blood = new DustParticleOptions(new Vector3f(0.55F, 0.0F, 0.0F), 1.5F);
        sl.sendParticles(blood, this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ(),
                40, 0.4D, 0.6D, 0.4D, 0.02D);
    }

    private void breakDoorsAndGlass() {
        BlockPos base = this.blockPosition();
        for (int dy = 0; dy <= 2; dy++) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos p = base.above(dy).relative(dir);
                Block b = this.level.getBlockState(p).getBlock();
                boolean glass = this.level.getBlockState(p).is(net.minecraft.tags.BlockTags.IMPERMEABLE)
                        || b == Blocks.GLASS_PANE;
                if (b instanceof DoorBlock || glass) this.level.destroyBlock(p, false);
            }
        }
    }

    private void tickRandomSounds(Player nearest) {
        if (helpmeCooldown <= 0 && nearest != null) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double dist = 8 + this.random.nextInt(8);
            this.level.playSound(null, nearest.getX() + Math.cos(angle) * dist, nearest.getY(),
                    nearest.getZ() + Math.sin(angle) * dist, ModSounds.HELPME.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
            helpmeCooldown = 1200 + this.random.nextInt(2000);
        }
        if (noiseCooldown <= 0) {
            SoundEvent s = ModSounds.RANDOM_NOISES.get(this.random.nextInt(ModSounds.RANDOM_NOISES.size())).get();
            this.level.playSound(null, this.getX(), this.getY(), this.getZ(), s, SoundSource.HOSTILE, 0.8F, 1.0F);
            noiseCooldown = 500 + this.random.nextInt(900);
        }
    }

    private Player nearestRealPlayer() {
        Player best = null;
        double bestSq = 64.0D * 64.0D;
        for (int i = 0; i < this.level.players().size(); i++) {
            Player p = this.level.players().get(i);
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
            double d = p.distanceToSqr(this);
            if (d < bestSq) { bestSq = d; best = p; }
        }
        return best;
    }

    private boolean canSee(Player p) {
        if (this.distanceTo(p) > 28.0D) return false;
        if (!this.hasLineOfSight(p)) return false;
        Vec3 to = p.position().subtract(this.position()).normalize();
        Vec3 facing = Vec3.directionFromRotation(0.0F, this.getYRot());
        return (to.x * facing.x + to.z * facing.z) > 0.2D;
    }

    private boolean isPlayerStaringAt(Player p) {
        if (this.distanceTo(p) > 48.0D) return false;
        double dx = this.getX() - p.getX();
        double dy = this.getEyeY() - p.getEyeY();
        double dz = this.getZ() - p.getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001D) return false;
        dx /= len; dy /= len; dz /= len;
        Vec3 look = p.getViewVector(1.0F);
        double dot = look.x * dx + look.y * dy + look.z * dz;
        return dot > 0.9D && p.hasLineOfSight(this);
    }

    private void lockYawTo(Player p) {
        double dx = p.getX() - this.getX();
        double dz = p.getZ() - this.getZ();
        float yaw = (float) (Math.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;
        this.setYBodyRot(yaw); this.setYHeadRot(yaw);
        this.yHeadRotO = yaw; this.yBodyRotO = yaw; this.yRotO = yaw;
        this.setYRot(yaw);
    }

    // === Animations ===========================================================
    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(this, "controller", 3, this::predicate));
        data.addAnimationController(new AnimationController<>(this, "attack_controller", 0, this::attackPredicate));
    }

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        AnimationController<?> controller = event.getController();
        if (isDisguised()) return PlayState.STOP;

        String K = "animation.face_thief.";
        int posture = getPosture();
        boolean moving = isMovingAnim();
        boolean aggressive = isFaceThiefAggressive() || (isSpotted() && moving);

        String anim;
        if (posture == 2) {
            anim = moving ? (aggressive ? K + "crawlaggressive" : K + "crawlcalm") : K + "crawlidle";
        } else if (posture == 1) {
            anim = moving ? (aggressive ? K + "crouchaggressive " : K + "crouchcalm") : K + "crouchidle";
        } else if (isSpotted() && !moving) {
            anim = K + "spotted";
        } else if (aggressive && moving) {
            anim = K + "run";
        } else if (moving) {
            anim = K + "walk";
        } else {
            anim = K + "idle";
        }
        controller.setAnimation(new AnimationBuilder().loop(anim));
        return PlayState.CONTINUE;
    }

    private <E extends IAnimatable> PlayState attackPredicate(AnimationEvent<E> event) {
        AnimationController<?> controller = event.getController();
        if (this.swinging && controller.getAnimationState() == AnimationState.Stopped) {
            controller.markNeedsReload();
            controller.setAnimation(new AnimationBuilder()
                    .addAnimation("animation.face_thief.run", EDefaultLoopTypes.PLAY_ONCE));
            this.swinging = false;
        }
        return PlayState.CONTINUE;
    }

    @Override public AnimationFactory getFactory() { return factory; }

    // === Sounds ===============================================================
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.HURT.get(); }
    @Override protected SoundEvent getDeathSound() { return ModSounds.DEATH.get(); }
    @Override protected float getSoundVolume() { return 1.0F; }

    // === Damage ===============================================================
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source == DamageSource.IN_WALL) return false; // no suffocation hurt-loop
        boolean result = super.hurt(source, amount);
        if (result && source.getEntity() instanceof LivingEntity) {
            if (isDisguised()) reveal();
            else setFaceThiefAggressive(true);
        }
        return result;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        this.swing(InteractionHand.MAIN_HAND);
        boolean ok = super.doHurtTarget(target);
        if (ok && target instanceof Player && !target.isAlive()) {
            despawnWithAftermath();
        }
        return ok;
    }

    // === Removal ==============================================================
    @Override
    public void remove(RemovalReason reason) {
        if (!this.level.isClientSide && playAftermath) {
            this.level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.AFTERMATH.get(), SoundSource.HOSTILE, 1.5F, 1.0F);
            stopChaseThemeForNearbyPlayers();
        }
        super.remove(reason);
    }

    private void stopChaseThemeForNearbyPlayers() {
        if (!(this.level instanceof ServerLevel)) return;
        ServerLevel serverLevel = (ServerLevel) this.level;
        ResourceLocation sound = ModSounds.CHASE_THEME.get().getLocation();
        ClientboundStopSoundPacket packet = new ClientboundStopSoundPacket(sound, SoundSource.HOSTILE);
        for (ServerPlayer p : serverLevel.players()) {
            if (p.distanceToSqr(this) < 96.0D * 96.0D) p.connection.send(packet);
        }
    }

    // === NBT ==================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Aggressive", isFaceThiefAggressive());
        tag.putBoolean("Stalking", isStalking());
        tag.putBoolean("Spotted", isSpotted());
        tag.putInt("Disguise", this.entityData.get(DATA_DISGUISE));
        tag.putString("DisguisePlayer", getDisguisePlayerName());
        tag.putInt("AliveTicks", aliveTicks);
        tag.putInt("SpottedTicks", spottedTicks);
        tag.putInt("AggroNoKill", aggroNoKillTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("Aggressive")) this.entityData.set(DATA_AGGRESSIVE, true);
        if (tag.getBoolean("Stalking")) setStalking(true);
        this.entityData.set(DATA_SPOTTED, tag.getBoolean("Spotted"));
        this.entityData.set(DATA_DISGUISE, tag.getInt("Disguise"));
        setDisguisePlayerName(tag.getString("DisguisePlayer"));
        aliveTicks = tag.getInt("AliveTicks");
        spottedTicks = tag.getInt("SpottedTicks");
        aggroNoKillTicks = tag.getInt("AggroNoKill");
    }
}

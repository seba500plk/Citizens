package com.citizens.resources.redecouverte.NPClib;

import net.minecraft.server.Entity;
import net.minecraft.server.EntityHuman;
import net.minecraft.server.EntityPlayer;
import net.minecraft.server.ItemInWorldManager;
import net.minecraft.server.MathHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PathEntity;
import net.minecraft.server.Vec3D;
import net.minecraft.server.World;

import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import com.citizens.Constants;
import com.citizens.Pathfinding.Point;
import com.citizens.Pathfinding.Citizens.BinaryPathFinder;
import com.citizens.Pathfinding.Citizens.CitizensPathHeuristic;
import com.citizens.Pathfinding.Citizens.MinecraftPathWorld;
import com.citizens.Pathfinding.Citizens.NPCPathPlayer;
import com.citizens.Utils.Messaging;
import com.citizens.resources.redecouverte.NPClib.NPCAnimator.Animation;

public class PathNPC extends EntityPlayer {
	public HumanNPC npc;
	private PathEntity path;
	protected Entity targetEntity;
	protected final NPCAnimator animations = new NPCAnimator(this);

	protected boolean targetAggro = false;
	private boolean hasAttacked = false;
	protected boolean jumping = false;
	protected boolean randomPather = false;
	private int pathTicks = 0;
	private int pathTickLimit = -1;
	private int stationaryTicks = 0;
	private int stationaryTickLimit = -1;
	private int attackTimes = 0;
	private int attackTimesLimit = -1;
	private int prevX;
	private int prevY;
	private int prevZ;
	protected float pathingRange = 16;
	private final BinaryPathFinder pather;

	public PathNPC(MinecraftServer minecraftserver, World world, String s,
			ItemInWorldManager iteminworldmanager) {
		super(minecraftserver, world, s, iteminworldmanager);
		this.pather = new BinaryPathFinder(new CitizensPathHeuristic(),
				new NPCPathPlayer(this.npc), new MinecraftPathWorld(this
						.getBukkitEntity().getWorld()));
	}

	public void updateMove() {
		hasAttacked = false;
		jumping = false;
		if (randomPather) {
			takeRandomPath();
		}
		updateTarget();
		updatePathingState();
		if (this.path != null) {
			Vec3D vector = getPathVector();
			if (vector != null) {
				handleMove(vector);
			}
			this.Q(); // Update entity
		} else {
			this.Q(); // Update entity
			this.path = null;
		}
	}

	private void handleMove(Vec3D vector) {
		int yHeight = MathHelper.floor(this.boundingBox.b + 0.5D);
		boolean inWater = this.ac();
		boolean inLava = this.ad();
		if (vector != null) {
			double diffX = vector.a - this.locX;
			double diffZ = vector.c - this.locZ;
			double diffY = vector.b - yHeight;
			float diffYaw = getYawDifference(diffZ, diffX);

			this.yaw += diffYaw;
			if (diffY > 0.0D) {
				jumping = true;
			}
			// Walk.
			moveOnCurrentHeading();
		}
		if (this.positionChanged && !this.pathFinished()) {
			jumping = true;
		}
		if (this.random.nextFloat() < 0.8F && (inWater || inLava)) {
			jumping = true;
		}
		if (jumping) {
			jump();
		}
	}

	private void updateTarget() {
		if (!this.hasAttacked && this.targetEntity != null) {
			this.path = this.world.findPath(this, this.targetEntity,
					pathingRange);
		}
		if (targetEntity != null) {
			// Has target died?
			if (!this.targetEntity.S()) {
				resetTarget();
			}
			if (targetEntity != null && targetAggro) {
				float distanceToEntity = this.targetEntity.f(this);
				// If a direct line of sight exists.
				if (this.e(this.targetEntity)) {
					// In range?
					if (isWithinAttackRange(this.targetEntity, distanceToEntity)) {
						// Attack.
						this.attackEntity(this.targetEntity);
					}
				}
			}
		}
	}

	private void updatePathingState() {
		Location loc = this.bukkitEntity.getLocation();
		if (prevX == loc.getBlockX() && prevY == loc.getBlockY()
				&& prevZ == loc.getBlockZ()) {
			++stationaryTicks;
		} else {
			stationaryTicks = 0;
		}
		++pathTicks;
		if ((pathTickLimit != -1 && pathTicks >= pathTickLimit)
				|| (stationaryTickLimit != -1 && stationaryTicks >= stationaryTickLimit)) {
			reset();
		}
		prevX = loc.getBlockX();
		prevY = loc.getBlockY();
		prevZ = loc.getBlockZ();
	}

	private Vec3D getPathVector() {
		Vec3D vec3d = path.a(this);
		double length = (this.length * 1.9F);
		// 2.0 -> 1.9 - closer to destination before stopping.
		while (vec3d != null
				&& vec3d.d(this.locX, vec3d.b, this.locZ) < length * length) {
			this.path.a(); // Increment path index.
			// Is path finished?
			if (this.path.b()) {
				vec3d = null;
				reset();
			} else {
				vec3d = this.path.a(this);
			}
		}
		return vec3d;
	}

	private float getYawDifference(double diffZ, double diffX) {
		float vectorYaw = (float) (Math.atan2(diffZ, diffX) * 180.0D / Math.PI) - 90.0F;
		float diffYaw = vectorYaw - this.yaw;

		for (this.aA = this.aE; diffYaw < -180.0F; diffYaw += 360.0F) {
		}
		while (diffYaw >= 180.0F) {
			diffYaw -= 360.0F;
		}
		if (diffYaw > 30.0F) {
			diffYaw = 30.0F;
		}
		if (diffYaw < -30.0F) {
			diffYaw = -30.0F;
		}
		return diffYaw;
	}

	private void moveOnCurrentHeading() {
		this.a(this.az, this.aA);
	}

	private void jump() {
		boolean inWater = this.ac();
		boolean inLava = this.ad();
		// Both values taken from minecraft source.
		if (inWater || inLava) {
			this.motY += 0.03999999910593033D;
		} else if (this.onGround) {
			this.motY = 0.41999998688697815D + Constants.JUMP_FACTOR;
			// Default (0.42) not enough to get over a block (bug?).
		}
	}

	private void incrementAttackTimes() {
		if (this.attackTimesLimit != -1) {
			++this.attackTimes;
			if (this.attackTimes >= this.attackTimesLimit) {
				resetTarget();
			}
		}
	}

	private void reset() {
		this.pathTicks = 0;
		this.stationaryTicks = 0;
		this.path = null;
		this.pathTickLimit = -1;
		this.stationaryTickLimit = -1;
		this.pathingRange = 16;
	}

	private void resetTarget() {
		this.targetEntity = null;
		this.targetAggro = false;
		this.attackTimes = 0;
		this.attackTimesLimit = -1;
		reset();
	}

	private boolean isHoldingBow() {
		return this.inventory.items[this.inventory.itemInHandIndex] != null
				&& this.inventory.items[this.inventory.itemInHandIndex].id == 261;
	}

	private boolean isWithinAttackRange(Entity entity, float distance) {
		// Bow distance from EntitySkeleton.
		// Other from EntityCreature.
		return (isHoldingBow() && distance < 10)
				|| (this.attackTicks <= 0 && distance < 1.5F
						&& entity.boundingBox.e > this.boundingBox.b && entity.boundingBox.b < this.boundingBox.e);
	}

	private void attackEntity(Entity entity) {
		this.attackTicks = 20; // Possibly causes attack spam (maybe higher?).
		if (isHoldingBow()) {
			// Code from EntitySkeleton.
			double distX = entity.locX - this.locX;
			double distZ = entity.locZ - this.locZ;
			double arrowDistY = entity.locY - 0.20000000298023224D
					- entity.locY;
			float distance = (float) (Math.sqrt(distX * distX + distZ * distZ) * 0.2F);
			Vector velocity = new Vector(distX, arrowDistY + distance, distZ);

			this.bukkitEntity.getWorld().spawnArrow(
					this.getBukkitEntity().getLocation(), velocity, 0.6F, 12F);
		} else {
			this.performAction(Animation.SWING_ARM);
			LivingEntity e = (LivingEntity) entity.getBukkitEntity();
			e.damage(this.inventory.a(entity));
		}
		hasAttacked = true;
		incrementAttackTimes();
	}

	private float getBlockPathWeight(int i, int j, int k) {
		return 0.5F - this.world.m(i, j, k);
	}

	private void takeRandomPath() {
		if (!hasAttacked && this.targetEntity != null
				&& (this.path == null || this.random.nextInt(20) == 0)) {
			this.path = this.world.findPath(this, this.targetEntity,
					pathingRange);
		} else if (!hasAttacked
				&& (this.path == null && this.random.nextInt(70) == 0 || this.random
						.nextInt(70) == 0)) { // 80 -> 70 - path faster.
			boolean flag = false;
			int x = -1;
			int y = -1;
			int z = -1;
			float pathWeight = -99999.0F;
			for (int l = 0; l < 10; ++l) {
				int x2 = MathHelper.floor(this.locX + this.random.nextInt(13)
						- 6.0D);
				int y2 = MathHelper.floor(this.locY + this.random.nextInt(7)
						- 3.0D);
				int z2 = MathHelper.floor(this.locZ + this.random.nextInt(13)
						- 6.0D);
				float tempPathWeight = this.getBlockPathWeight(x2, y2, z2);

				if (tempPathWeight > pathWeight) {
					pathWeight = tempPathWeight;
					x = x2;
					y = y2;
					z = z2;
					flag = true;
				}
			}
			if (flag) {
				createPathEntity(x, y, z);
			}
		}
	}

	public EntityHuman getClosestPlayer(double range) {
		EntityHuman entityhuman = this.world.a(this, range);
		return entityhuman != null && this.e(entityhuman) ? entityhuman : null;
	}

	public void targetClosestPlayer(boolean aggro, double range) {
		this.targetEntity = this.getClosestPlayer(range);
		this.targetAggro = aggro;
	}

	public boolean startPath(Location loc, int maxTicks,
			int maxStationaryTicks, float pathingRange) {
		this.pathTickLimit = maxTicks;
		this.stationaryTickLimit = maxStationaryTicks;
		this.pathingRange = pathingRange;
		return createPath(loc);
	}

	private boolean createPath(Location loc) {
		createPathEntity(loc);
		return pathFinished();
	}

	private void createPathEntity(Location loc) {
		createPathEntity(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
	}

	private void createPathEntity(int x, int y, int z) {
		long mc = System.nanoTime();
		this.path = this.world.a(this, x, y, z, pathingRange);
		mc = System.nanoTime() - mc; // Test our own pathfinder :).
		Location loc = this.bukkitEntity.getLocation();
		pather.recalculate(
				new Point(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()),
				new Point(x, y, z));
		long ours = System.nanoTime();
		pather.find();
		ours = System.nanoTime() - ours;
		Messaging.log("Time difference: " + ((ours - mc) / 1000000) + "ms");

	}

	public void setTarget(LivingEntity entity, boolean aggro, int maxTicks,
			int maxStationaryTicks, float pathingRange) {
		this.targetEntity = ((CraftLivingEntity) entity).getHandle();
		this.targetAggro = aggro;
		this.pathTickLimit = maxTicks;
		this.pathingRange = pathingRange;
		this.stationaryTickLimit = maxStationaryTicks;
	}

	public void setAttackTimes(int times) {
		this.attackTimesLimit = times;
	}

	public boolean pathFinished() {
		return path == null;
	}

	public void cancelPath() {
		reset();
	}

	public void cancelTarget() {
		resetTarget();
	}

	public boolean hasTarget() {
		return this.targetEntity == null;
	}

	public void performAction(Animation action) {
		this.animations.performAnimation(action);
	}
}
package xyz.kybe.hweflytweaks;

import com.jcraft.jorbis.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.events.client.EventUpdate;
import org.rusherhack.client.api.events.player.EventMove;
import org.rusherhack.client.api.events.player.EventTravel;
import org.rusherhack.client.api.events.render.EventRender3D;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.render.IRenderer3D;
import org.rusherhack.client.api.setting.ColorSetting;
import org.rusherhack.client.api.utils.ChatUtils;
import org.rusherhack.core.event.stage.Stage;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.EnumSetting;
import org.rusherhack.core.setting.NullSetting;
import org.rusherhack.core.setting.NumberSetting;
import org.rusherhack.core.utils.ColorUtils;
import xyz.kybe.hweflytweaks.util.BaritoneUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class WallHugAutomationModule extends ToggleableModule {
	public NullSetting positionSettings = new NullSetting("Position Settings", "Settings for position");
	public EnumSetting<Direction> direction = new EnumSetting<>("Direction", "Direction your traveling", Direction.NORTH);
	public NumberSetting<Integer> wallOffset = new NumberSetting<>("Wall Offset", "The Wall Offset (direction x 4 wide offset right wall would be 2)", 2, -30000000, 30000000);
	public NumberSetting<Integer> YLevel = new NumberSetting<>("Y Level", 120, -64, 319);
	public EnumSetting<Railing> railing = new EnumSetting<>("Railing", "Which side to hug", Railing.BOTH);
	public BooleanSetting setOnEnable = new BooleanSetting("Set On Enable", "Set \"Direction\", \"Y Level\" and \"Wall Offset\" when enabling", true);

	public enum Railing {
		LEFT,
		RIGHT,
		BOTH
	}

	public NullSetting colorSettings = new NullSetting("Color Settings", "Settings for colors");
	public BooleanSetting render = new BooleanSetting("Render", "Render positions", true);
	public ColorSetting pastColor = new ColorSetting("Past Color", "Color of past positions", ColorUtils.transparency(Color.GRAY.getRGB(), 100));
	public ColorSetting holeColor = new ColorSetting("Hole Color", "Color of holes", ColorUtils.transparency(Color.RED.getRGB(), 100));
	public ColorSetting blockageColor = new ColorSetting("Blockage Color", "Color of blockages", ColorUtils.transparency(Color.YELLOW.getRGB(), 100));
	public ColorSetting validColor = new ColorSetting("Valid Color", "Color of valid positions", ColorUtils.transparency(Color.GREEN.getRGB(), 100));

	public BooleanSetting manageEfly = new BooleanSetting("Manage Efly", "Automatically manages the efly module for automated wall hugging", true);

	ArrayList<BlockPos> holes = new ArrayList<>();
	ArrayList<BlockPos> blockages = new ArrayList<>();
	ArrayList<BlockPos> valids = new ArrayList<>();

	boolean baritone = false;

	public WallHugAutomationModule() {
		super("WallHugAutomation", ModuleCategory.MOVEMENT);

		positionSettings.addSubSettings(direction, wallOffset, YLevel, railing, setOnEnable);
		colorSettings.addSubSettings(render, pastColor, holeColor, blockageColor, validColor);
		this.registerSettings(positionSettings, colorSettings, manageEfly);
	}

	@Override
	public void onDisable() {
		ToggleableModule eflyModule = (ToggleableModule) RusherHackAPI.getModuleManager().getFeature("ElytraFly").get();
		eflyModule.setToggled(false);
	}

	@Subscribe
	public void onMove(EventUpdate event) {
		updateBlocks();
		if (mc.player == null || mc.level == null || !manageEfly.getValue()) return;

		ToggleableModule eflyModule = (ToggleableModule) RusherHackAPI.getModuleManager().getFeature("ElytraFly").get();

		Vec3 playerPosVec = mc.player.position();
		BlockPos playerBlockPos = mc.player.blockPosition();
		Direction dir = direction.getValue();

		if (mc.player.getY() < YLevel.getValue() || isOffsetIncorrect(playerBlockPos) && !baritone) {
			eflyModule.setToggled(false);
			baritone = true;
		}

		if (baritone) {
			eflyModule.setToggled(false);
			List<BlockPos> path = getRailingPositions();
			BlockPos target = null;

			for (BlockPos pos : path) {
				boolean tooClose = blockages.stream().anyMatch(b -> b.closerThan(pos, 4));
				if (!tooClose) tooClose = holes.stream().anyMatch(h -> h.closerThan(pos, 4));
				if (mc.level.getBlockState(pos.below()).getCollisionShape(mc.level, pos.below()).isEmpty()) tooClose = true;
				if (!tooClose) {
					target = pos;
					break;
				}
			}

			if (target == null && !path.isEmpty()) {
				target = path.getLast();
			}

			if (target != null) {
				if (hasReachedTarget(target, direction.getValue(), mc.player.position(), wallOffset.getValue())) {
					BaritoneUtils.stopBaritone();
					baritone = false;
					eflyModule.setToggled(true);
					return;
				}

				if (!BaritoneUtils.isBaritonePathing()) {
					BaritoneUtils.goTo(target);
				} else {
					var currentGoal = BaritoneUtils.getBaritoneTarget();
					if (currentGoal != null) {
						BlockPos current = currentGoal.getGoalPos();
						if (!current.equals(target)) {
							BaritoneUtils.goTo(target);
						}
					}
				}
			}


			return;
		}

		BlockPos frontPos = playerBlockPos.relative(dir, 1);
		boolean frontBlocked = blockages.stream().anyMatch(b -> b.equals(frontPos));
		boolean nearHole = holes.stream().anyMatch(h -> h.getY() < YLevel.getValue() &&
				Vec3.atCenterOf(h).distanceTo(playerPosVec) < 10 &&
				isInFront(playerPosVec, h, dir));

		if (frontBlocked || nearHole) {
			eflyModule.setToggled(false);
			baritone = true;
			return;
		}

		eflyModule.setToggled(true);

		BlockPos rightRail = playerBlockPos.relative(dir.getClockWise(), 1);
		BlockPos leftRail = playerBlockPos.relative(dir.getCounterClockWise(), 1);

		boolean rightHasHole = holes.stream().anyMatch(h -> Vec3.atCenterOf(h).distanceTo(Vec3.atCenterOf(rightRail)) < 5);
		boolean leftHasHole = holes.stream().anyMatch(h -> Vec3.atCenterOf(h).distanceTo(Vec3.atCenterOf(leftRail)) < 5);

		if (playerPosVec.y > YLevel.getValue() + 1) {
			float mainYaw = dir.toYRot();
			float adjustedYaw = mainYaw;

			Direction sideToWall = null;
			if (!leftHasHole && railing.getValue() != Railing.RIGHT) {
				sideToWall = dir.getCounterClockWise();
			} else if (!rightHasHole && railing.getValue() != Railing.LEFT) {
				sideToWall = dir.getClockWise();
			}

			if (sideToWall != null) {
				Direction awayFromWall = sideToWall.getOpposite();
				float sideYaw = awayFromWall.toYRot();
				adjustedYaw = mainYaw + Mth.wrapDegrees(sideYaw - mainYaw) * 0.05f;
			}

			mc.player.setYRot(adjustedYaw);
			mc.player.setYHeadRot(adjustedYaw);
			mc.player.setYBodyRot(adjustedYaw);
			eflyModule.setToggled(true);
			return;
		}


		if (!mc.player.isFallFlying()) return;

		Direction sideToWall = null;
		if (!leftHasHole && railing.getValue() != Railing.RIGHT) {
			sideToWall = dir.getCounterClockWise();
		} else if (!rightHasHole && railing.getValue() != Railing.LEFT) {
			sideToWall = dir.getClockWise();
		}

		float mainYaw = dir.toYRot();
		float adjustedYaw = mainYaw;

		if (sideToWall != null) {
			float sideYaw = sideToWall.toYRot();
			adjustedYaw = mainYaw + Mth.wrapDegrees(sideYaw - mainYaw) * 0.05f;
		}

		mc.player.setYRot(adjustedYaw);
		mc.player.setYHeadRot(adjustedYaw);
		mc.player.setYBodyRot(adjustedYaw);

		eflyModule.setToggled(true);
	}

	private boolean isOffsetIncorrect(BlockPos pos) {
		Direction dir = direction.getValue();
		int offset = wallOffset.getValue();

		switch (dir.getAxis()) {
			case X -> {
				return pos.getZ() != offset;
			}
			case Z -> {
				return pos.getX() != offset;
			}
		}
		return false;
	}

	private boolean hasReachedTarget(BlockPos target, Direction dir, Vec3 playerPos, int desiredOffset) {
		boolean passedMainAxis = switch (dir.getAxis()) {
			case X -> dir.getStepX() > 0 ? playerPos.x > target.getX() : playerPos.x < target.getX();
			case Z -> dir.getStepZ() > 0 ? playerPos.z > target.getZ() : playerPos.z < target.getZ();
			case Y -> false;
		};

		boolean offsetCorrect = switch (dir.getAxis()) {
			case X -> Math.abs(playerPos.z - desiredOffset) <= 1;
			case Z -> Math.abs(playerPos.x - desiredOffset) <= 1;
			case Y -> false;
		};

		if (playerPos.y != YLevel.getValue()) return false;

		double closeEnoughDist = 1.5;
		boolean physicallyClose = Vec3.atCenterOf(target).distanceTo(playerPos) <= closeEnoughDist;

		return (passedMainAxis && offsetCorrect) || physicallyClose;
	}

	private boolean isInFront(Vec3 player, BlockPos target, Direction dir) {
		switch (dir.getAxis()) {
			case X -> {
				return (dir.getStepX() > 0 && target.getX() > player.x) || (dir.getStepX() < 0 && target.getX() < player.x);
			}
			case Z -> {
				return (dir.getStepZ() > 0 && target.getZ() > player.z) || (dir.getStepZ() < 0 && target.getZ() < player.z);
			}
		}
		return false;
	}

	@Override
	public void onEnable() {
		if (!setOnEnable.getValue()) return;
		if (mc.player == null) return;

		Direction facing = mc.player.getDirection();
		direction.setValue(facing);

		BlockPos playerPos = mc.player.blockPosition();

		YLevel.setValue(playerPos.getY());

		int offset;
		if (facing.getAxis() == Direction.Axis.X) {
			offset = playerPos.getZ();
		} else {
			offset = playerPos.getX();
		}

		wallOffset.setValue(offset);
	}

	@Subscribe
	public void onRender3D(EventRender3D event) {
		final IRenderer3D renderer = event.getRenderer();

		renderer.begin(event.getMatrixStack());

		for (BlockPos pos : holes) {
			renderer.drawBox(pos, false, true, holeColor.getValue().getRGB());
		}

		for (BlockPos pos : blockages) {
			renderer.drawBox(pos, false, true, blockageColor.getValue().getRGB());
		}

		for (BlockPos pos : valids) {
			renderer.drawBox(pos, false, true, validColor.getValue().getRGB());
		}

		renderer.end();
	}

	public void updateBlocks() {
		if (mc.player == null || mc.level == null) return;
		holes.clear();
		blockages.clear();
		valids.clear();

		List<BlockPos> railingPositions = getRailingPositions();
		for (BlockPos pos : railingPositions) {
			BlockPos below = pos.below();
			BlockPos rightRail = pos.relative(direction.getValue().getClockWise(), 1);
			BlockPos leftRail = pos.relative(direction.getValue().getCounterClockWise(), 1);

			if (mc.level.getBlockState(below).getCollisionShape(mc.level, below).isEmpty()) {
				holes.add(below);
			} else {
				valids.add(below);
			}

			if (!mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty()) {
				blockages.add(pos);
			}

			if (!mc.level.getBlockState(pos.above()).getCollisionShape(mc.level, pos.above()).isEmpty()) {
				blockages.add(pos.above());
			}
			if (!mc.level.getBlockState(pos.above(2)).getCollisionShape(mc.level, pos.above(2)).isEmpty()) {
				blockages.add(pos.above(2));
			}

			switch (railing.getValue()) {
				case BOTH -> {
					if (mc.level.getBlockState(rightRail).getCollisionShape(mc.level, rightRail).isEmpty()) {
						holes.add(rightRail);
					} else {
						valids.add(rightRail);
					}
					if (mc.level.getBlockState(leftRail).getCollisionShape(mc.level, leftRail).isEmpty()) {
						holes.add(leftRail);
					} else {
						valids.add(leftRail);
					}
				}
				case LEFT -> {
					if (mc.level.getBlockState(leftRail).getCollisionShape(mc.level, leftRail).isEmpty()) {
						holes.add(leftRail);
					} else {
						valids.add(leftRail);
					}
				}
				case RIGHT -> {
					if (mc.level.getBlockState(rightRail).getCollisionShape(mc.level, rightRail).isEmpty()) {
						holes.add(rightRail);
					} else {
						valids.add(rightRail);
					}
				}
			}
		}
	}

	public List<BlockPos> getRailingPositions() {
		List<BlockPos> positions = new ArrayList<>();
		if (mc.player == null || mc.level == null) return positions;

		BlockPos playerPos = mc.player.blockPosition();
		Direction dir = this.direction.getValue();
		int wallOffset = this.wallOffset.getValue();

		for (int i = 0; i < 500; i++) {
			BlockPos pos;

			if (dir.getAxis() == Direction.Axis.X) {
				int x = playerPos.getX() + (dir.getStepX() * i);
				pos = new BlockPos(x, YLevel.getValue(), wallOffset);
			} else {
				int z = playerPos.getZ() + (dir.getStepZ() * i);
				pos = new BlockPos(wallOffset, YLevel.getValue(), z);
			}

			if (!mc.level.isLoaded(pos)) break;

			positions.add(pos);
		}

		return positions;
	}

	private static Vec3 updateFallFlyingMovement(Vec3 vec3, Vec3 lookAngle, float xRot) {
		if (mc.player == null) return null;

		float f = xRot * ((float) Math.PI / 180F);
		double d = Math.sqrt(lookAngle.x * lookAngle.x + lookAngle.z * lookAngle.z);
		double e = vec3.horizontalDistance();
		boolean isGoingUp = mc.player.getDeltaMovement().y <= (double) 0.0F;
		double g = isGoingUp && mc.player.hasEffect(MobEffects.SLOW_FALLING) ? Math.min(mc.player.getGravity(), 0.01) : mc.player.getGravity();
		double h = Mth.square(Math.cos(f));
		vec3 = vec3.add(0.0F, g * ((double) -1.0F + h * (double) 0.75F), 0.0F);
		if (vec3.y < (double) 0.0F && d > (double) 0.0F) {
			double i = vec3.y * -0.1 * h;
			vec3 = vec3.add(lookAngle.x * i / d, i, lookAngle.z * i / d);
		}

		if (f < 0.0F && d > (double) 0.0F) {
			double i = e * (double) (-Mth.sin(f)) * 0.04;
			vec3 = vec3.add(-lookAngle.x * i / d, i * 3.2, -lookAngle.z * i / d);
		}

		if (d > (double) 0.0F) {
			vec3 = vec3.add((lookAngle.x / d * e - vec3.x) * 0.1, 0.0F, (lookAngle.z / d * e - vec3.z) * 0.1);
		}

		return vec3.multiply(0.99F, 0.98F, 0.99F);
	}
}

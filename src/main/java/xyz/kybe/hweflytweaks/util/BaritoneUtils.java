package xyz.kybe.hweflytweaks.util;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.core.BlockPos;

public class BaritoneUtils {
	public static IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

	public static void goTo(BlockPos pos) {
		baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(pos));
	}

	public static boolean isBaritonePathing() {
		return baritone.getPathingBehavior().isPathing();
	}

	public static void stopBaritone() {
		baritone.getPathingBehavior().cancelEverything();
	}

	public static GoalBlock getBaritoneTarget() {
		Goal goal = baritone.getPathingBehavior().getGoal();
		if (goal instanceof GoalBlock) {
			return (GoalBlock) goal;
		}
		return null;
	}
}

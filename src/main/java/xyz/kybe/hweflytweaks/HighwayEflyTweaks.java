package xyz.kybe.hweflytweaks;

import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;

public class HighwayEflyTweaks extends Plugin {
	@Override
	public void onLoad() {
		final WallHugAutomationModule wallHugAutomationModule = new WallHugAutomationModule();
		RusherHackAPI.getModuleManager().registerFeature(wallHugAutomationModule);
	}
	
	@Override
	public void onUnload() {
	}
}
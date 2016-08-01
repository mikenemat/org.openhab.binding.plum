package org.openhab.binding.plum.internal;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;
import org.openhab.binding.plum.PlumBindingConfig;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlumTCPStreamListener implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(PlumTCPStreamListener.class);
	private EventPublisher eventPublisher = null;
	private Set<PlumBindingConfig> configs = null;
	private Map<String, Integer> currentLlidLevels = null;
	private PlumMotionWatchdog watchdog = null;

	public PlumTCPStreamListener(EventPublisher eventPublisher, Set<PlumBindingConfig> configs,
			Map<String, Integer> currentLlidLevels, PlumMotionWatchdog watchdog) {
		this.eventPublisher = eventPublisher;
		this.configs = configs;
		this.currentLlidLevels = currentLlidLevels;
		this.watchdog = watchdog;
	}

	private void publishState(PlumBindingConfig c, State state) {
		eventPublisher.postUpdate(c.getName(), state);
	}

	@Override
	public void run() {
		String ipAddr = configs.iterator().next().getIpAddr();

		logger.info("Starting monitor thread for Plum IP: " + ipAddr);
		Socket socket = null;
		BufferedReader input = null;
		while (true) {
			try {
				socket = new Socket(ipAddr, 2708);
				input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

				String s = null;
				while ((s = input.readLine()) != null) {
					logger.info("Plum TCP Stream event from IP " + ipAddr + " Message: " + s);

					JSONObject j = new JSONObject(s);
					String type = j.getString("type");

					if (configs.isEmpty()) {
						throw new RuntimeException("Missing configs..");
					}

					for (PlumBindingConfig config : configs) {
						if (type.equals("dimmerchange")
								&& (config.getType().equals("dimmer") || config.getType().equals("switch"))) {
							int level = j.getInt("level");
							currentLlidLevels.put(config.getLlid(), level);

							if (level == 0) {
								publishState(config, OnOffType.OFF);
							} else if (level == 255) {
								publishState(config, OnOffType.ON);
							} else {
								publishState(config, new PercentType((int) (100 * ((float) level / 255))));
							}
						} else if (type.equals("power") && config.getType().equals("powermeter")) {
							int power = j.getInt("watts");
							publishState(config, new DecimalType(power));
						} else if (type.equals("pirSignal") && config.getType().equals("motion")) {
							if (watchdog.isNotRunning(config)) {
								logger.info("Plum motion sensor triggered: " + config.getName());
								publishState(config, OpenClosedType.OPEN);
								watchdog.updateWatchdog(config);
							} else {
								logger.info(
										"Plum motion sensor IGNORED due to existing motion delay: " + config.getName());
							}
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			if (input != null) {
				try {
					input.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			if (socket != null) {
				try {
					socket.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public Set<PlumBindingConfig> getConfigs() {
		return configs;
	}

}

/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.plum;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.openhab.binding.plum.internal.PlumMotionWatchdog;
import org.openhab.binding.plum.internal.PlumTCPStreamListener;
import org.openhab.binding.plum.internal.PlumUtilities;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.binding.BindingProvider;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.IncreaseDecreaseType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mike Nemat
 */

public class PlumActiveBinding extends AbstractActiveBinding<PlumBindingProvider> implements ManagedService {
	private static final Logger logger = LoggerFactory.getLogger(PlumActiveBinding.class);
	private HashMap<String, String> m_config = null;
	private long m_refreshInterval = 60000L;
	private boolean m_isActive = false; // state of binding
	private Map<String, Integer> currentLlidLevels = new HashMap<String, Integer>();

	// Mapping of Plum IP addreses to Threads monitoring them
	private static Map<String, Thread> threads = new HashMap<String, Thread>();
	private static Map<String, PlumTCPStreamListener> plumTCPStreams = new HashMap<String, PlumTCPStreamListener>();
	private Thread watchdogThread = null;

	/**
	 * Constructor
	 */
	public PlumActiveBinding() {

	}

	/**
	 * Inherited from AbstractBinding. This method is invoked by the framework
	 * whenever a command is coming from openhab, i.e. a switch is flipped via
	 * the GUI or other controls. The binding translates this openhab command
	 * into a message to the modem. {@inheritDoc}
	 */

	@Override
	public void internalReceiveCommand(String itemName, Command command) {
		logger.info("Item: {} got command {}", itemName, command);

		if (!(isProperlyConfigured() && m_isActive)) {
			logger.debug("not ready to handle commands yet, returning.");
			return;
		}
		boolean commandHandled = false;
		for (PlumBindingProvider provider : providers) {
			if (provider.providesBindingFor(itemName)) {
				commandHandled = true;
				PlumBindingConfig c = provider.getPlumBindingConfig(itemName);
				if (c == null) {
					logger.warn("could not find config for item {}", itemName);
				} else {
					sendCommand(c, command);
				}
			}
		}

		if (!commandHandled) {
			logger.warn("No converter found for item = {}, command = {}, ignoring.", itemName, command.toString());
		}
	}

	/**
	 * Send command to Plum device
	 * 
	 * @param c
	 *            item binding configuration
	 * @param command
	 *            The command to be sent
	 */
	private void sendCommand(PlumBindingConfig c, Command command) {

		if (command instanceof OnOffType || command instanceof PercentType) {
			int val = -1;
			if (command instanceof OnOffType) {
				if (((OnOffType) command).equals(OnOffType.ON)) {
					val = 255;
				} else if (((OnOffType) command).equals(OnOffType.OFF)) {
					val = 0;
				}

			} else if (command instanceof PercentType) {
				val = (int) ((((PercentType) command).doubleValue() / 100) * 255);
			}
			if (val > -1) {
				Map<String, Object> args = new HashMap<String, Object>();
				args.put("level", val);
				args.put("llid", c.getLlid());
				try {
					sendHttpCommand(c.getIpAddr(), "setLogicalLoadLevel", args);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		} else if (command instanceof IncreaseDecreaseType) {
			Integer val = currentLlidLevels.get(c.getLlid());
			if (val == null) {
				logger.warn("You must wait for the first poll (60s after startup) before controlling dimmers");
				return;
			}
			if (command.equals(IncreaseDecreaseType.INCREASE)) {
				if (255 - val <= 13) {
					val = 255;
				} else {
					val += 13;
				}
			} else if (command.equals(IncreaseDecreaseType.DECREASE)) {
				if (val <= 13) {
					val = 0;
				} else {
					val -= 13;
				}
			}
			Map<String, Object> args = new HashMap<String, Object>();
			args.put("level", val);
			args.put("llid", c.getLlid());
			try {
				sendHttpCommand(c.getIpAddr(), "setLogicalLoadLevel", args);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}

		}
	}

	private String sendHttpCommand(String ip, String apiCall, Map<String, Object> args) throws Exception {

		HttpClient httpclient = PlumUtilities.getHttpClient();
		// Prepare a request object
		HttpPost httppost = new HttpPost("https://" + ip + ":8443/v2/" + apiCall);
		httppost.setHeader("User-Agent", "Plum/2.3.0 (iPhone; iOS 9.2.1; Scale/2.00)");
		httppost.setHeader("X-Plum-House-Access-Token", PlumUtilities.sha256(m_config.get("house_token")));
		httppost.setHeader("Content-type", "application/json");

		JSONObject json = new JSONObject();
		for (Entry<String, Object> kv : args.entrySet()) {
			json.put(kv.getKey(), kv.getValue());
		}

		StringEntity se = new StringEntity(json.toString());
		se.setContentEncoding("UTF-8");

		httppost.setEntity(se);

		// Execute the request
		HttpResponse response = httpclient.execute(httppost);

		// Examine the response status
		logger.info("Plum HTTP Request to IP " + ip + " returned: " + response.getStatusLine());

		// Get hold of the response entity
		HttpEntity entity = response.getEntity();
		String responseString = null;
		if (entity != null) {
			responseString = EntityUtils.toString(entity, "UTF-8");
		}

		// If the response does not enclose an entity, there is no need
		// to worry about connection release
		if (entity != null) {
			httpclient.getConnectionManager().shutdown();
		}

		return responseString;
	}

	/**
	 * Inherited from AbstractBinding. Activates the binding. There is nothing
	 * we can do at this point if we don't have the configuration information,
	 * which usually comes in later, when updated() is called. {@inheritDoc}
	 */
	@Override
	public void activate() {
		logger.debug("activating binding");

		m_isActive = true;

		initialize();
	}

	/**
	 * Inherited from AbstractBinding. Deactivates the binding. The Controller
	 * is stopped and the serial interface is closed as well. {@inheritDoc}
	 */
	@Override
	public void deactivate() {
		logger.debug("deactivating binding!");
		shutdown();
		m_isActive = false;
	}

	/**
	 * Inherited from AbstractActiveBinding. {@inheritDoc}
	 */
	@Override
	protected String getName() {
		return "Plum";
	}

	/**
	 * Inherited from AbstractActiveBinding. Periodically called by the
	 * framework to execute a refresh of the binding. {@inheritDoc}
	 */
	@Override
	protected void execute() {
		PlumGenericBindingProvider p = (PlumGenericBindingProvider) providers.iterator().next();
		// Only call getLLM once per LLID.
		Map<String, List<PlumBindingConfig>> configByLLID = new HashMap<String, List<PlumBindingConfig>>();
		for (PlumBindingConfig c : p.getPlumBindingConfigs()) {
			List<PlumBindingConfig> configs = configByLLID.get(c.getLlid());
			if (configs == null) {
				configs = new ArrayList<PlumBindingConfig>();
				configByLLID.put(c.getLlid(), configs);
			}
			configs.add(c);
		}

		for (String llid : configByLLID.keySet()) {
			Map<String, Object> args = new HashMap<String, Object>();
			args.put("llid", llid);
			String httpReply = null;
			JSONObject jsonParsed = null;
			String ipAddr = configByLLID.get(llid).get(0).getIpAddr();
			try {
				logger.info("Polling Plum HTTP " + ipAddr);
				httpReply = sendHttpCommand(ipAddr, "getLogicalLoadMetrics", args);
				logger.trace("Got Plum HTTP: " + httpReply);
				jsonParsed = new JSONObject(httpReply);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			for (PlumBindingConfig c : configByLLID.get(llid)) {

				if (c.getType().equals("dimmer") || c.getType().equals("switch")) {
					int level = jsonParsed.getInt("level");
					int lightpads = jsonParsed.getJSONArray("lightpad_metrics").length();
					if (level > 0 && lightpads > 1) {
						// Work around the behaviour where level is the sum
						// of n
						// levels where n = # of lightpads in a llid. Divide
						// levels
						// by n.
						level = (int) ((float) level / lightpads);
					}
					currentLlidLevels.put(llid, level);
					if (level == 0) {
						publishState(c.getName(), OnOffType.OFF);
					} else if (level == 255) {
						publishState(c.getName(), OnOffType.ON);
					} else {
						publishState(c.getName(), new PercentType((int) (100 * ((float) level / 255))));
					}
				} else if (c.getType().equals("powermeter")) {
					int power = jsonParsed.getInt("power");
					publishState(c.getName(), new DecimalType(power));
				}

			}

		}
	}

	private void publishState(String name, State state) {
		eventPublisher.postUpdate(name, state);
	}

	/**
	 * Inherited from AbstractActiveBinding. Returns the refresh interval (time
	 * between calls to execute()) in milliseconds. {@inheritDoc}
	 */
	@Override
	protected long getRefreshInterval() {
		return m_refreshInterval;
	}

	/**
	 * Inherited from AbstractActiveBinding. This method is called by the
	 * framework whenever there are changes to a binding configuration.
	 * 
	 * @param provider
	 *            the binding provider where the binding has changed
	 * @param itemName
	 *            the item name for which the binding has changed
	 */
	@Override
	public void bindingChanged(BindingProvider provider, String itemName) {
		super.bindingChanged(provider, itemName);
		initialize();
	}

	/**
	 * Initialize the binding: initialize the driver etc
	 */
	private void initialize() {

		logger.debug("initializing...");

		PlumGenericBindingProvider p = (PlumGenericBindingProvider) providers.iterator().next();
		// Create threads by IP
		Map<String, Set<PlumBindingConfig>> configByIP = new HashMap<String, Set<PlumBindingConfig>>();
		for (PlumBindingConfig c : p.getPlumBindingConfigs()) {
			Set<PlumBindingConfig> configs = configByIP.get(c.getIpAddr());
			if (configs == null) {
				configs = new HashSet<PlumBindingConfig>();
				configByIP.put(c.getIpAddr(), configs);
			}
			configs.add(c);
		}

		PlumMotionWatchdog w = new PlumMotionWatchdog(eventPublisher);
		watchdogThread = new Thread(w);
		watchdogThread.start();

		for (String ip : configByIP.keySet()) {
			Set<PlumBindingConfig> configs = configByIP.get(ip);
			if (threads.get(ip) == null) {
				PlumTCPStreamListener stream = new PlumTCPStreamListener(eventPublisher, configs, currentLlidLevels, w);
				Thread t = new Thread(stream);
				t.start();
				threads.put(ip, t);
				plumTCPStreams.put(ip, stream);
			} else {
				plumTCPStreams.get(ip).getConfigs().addAll(configs);
			}
		}
	}

	/**
	 * Clean up all state.
	 */
	private void shutdown() {
		logger.debug("shutting down binding");
		for (Thread t : threads.values()) {
			t.stop();
			t.destroy();
		}
		watchdogThread.stop();
		watchdogThread.destroy();

	}

	@Override
	public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
		logger.warn(
				"The Plum Binding does not support runtime updates. Restart OpenHAB for item changes to take effect");
		HashMap<String, String> newConfig = new HashMap<String, String>();
		if (m_config == null) {
			logger.debug("seems like our configuration has been erased, will reset everything!");

			// turn config into new HashMap
			for (Enumeration<String> e = properties.keys(); e.hasMoreElements();) {
				String key = e.nextElement();
				String value = properties.get(key).toString();
				newConfig.put(key, value);
			}

			m_config = newConfig;

			if (m_config.containsKey("refresh")) {
				try {
					m_refreshInterval = Long.parseLong(m_config.get("refresh"));
					setProperlyConfigured(true);
				} catch (NumberFormatException e) {
					logger.error(
							"Are you sure you provided a numerical value for the openhab.cfg option plum:refresh?");
				}
			}
		}

	}
}
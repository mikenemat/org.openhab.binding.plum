/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.plum;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.binding.BindingProvider;
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
	private HashMap<String, String> m_config = new HashMap<String, String>();
	private final long m_refreshInterval = 60000L;
	private boolean m_isActive = false; // state of binding

	private static Map<PlumBindingConfig, Boolean> threads = new HashMap<PlumBindingConfig, Boolean>();

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
			int val = 0;
			if (command instanceof OnOffType) {
				if (((OnOffType) command).equals(OnOffType.ON)) {
					val = 255;
				} else if (((OnOffType) command).equals(OnOffType.OFF)) {
					val = 0;
				}

			} else if (command instanceof PercentType) {
				val = (int) (((PercentType) command).doubleValue() * 255);
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
		HttpClient httpclient = new DefaultHttpClient();

		SSLContext ctx = SSLContext.getInstance("TLS");
		X509TrustManager tm = new X509TrustManager() {

			@Override
			public void checkClientTrusted(X509Certificate[] xcs, String string) throws CertificateException {
			}

			@Override
			public void checkServerTrusted(X509Certificate[] xcs, String string) throws CertificateException {
			}

			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}
		};
		ctx.init(null, new TrustManager[] { tm }, null);
		SSLSocketFactory ssf = new SSLSocketFactory(ctx, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
		ClientConnectionManager ccm = httpclient.getConnectionManager();
		SchemeRegistry sr = ccm.getSchemeRegistry();
		sr.register(new Scheme("https", 8443, ssf));

		httpclient = new DefaultHttpClient(ccm, httpclient.getParams());

		// Prepare a request object
		HttpPost httppost = new HttpPost("https://" + ip + ":8443/v2/" + apiCall);
		httppost.setHeader("User-Agent", "Plum/2.3.0 (iPhone; iOS 9.2.1; Scale/2.00)");
		httppost.setHeader("X-Plum-House-Access-Token", sha256(m_config.get("house_token")));
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
		logger.info("Plum HTTP: " + response.getStatusLine());

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
		for (final PlumBindingConfig c : p.getPlumBindingConfigs()) {
			Map<String, Object> args = new HashMap<String, Object>();
			args.put("llid", c.getLlid());
			try {
				logger.info("Polling Plum HTTP " + c.getIpAddr());
				String s = sendHttpCommand(c.getIpAddr(), "getLogicalLoadMetrics", args);
				logger.info("Got Plum HTTP: " + s);
				JSONObject j = new JSONObject(s);

				int level = j.getInt("level");
				int lightpads = j.getJSONArray("lightpad_metrics").length();
				if (level > 0 && lightpads > 1) {
					level = (int) ((float) level / lightpads);
				}

				if (level == 0) {
					publishState(c.getName(), OnOffType.OFF);
				} else if (level == 255) {
					publishState(c.getName(), OnOffType.ON);
				} else {
					publishState(c.getName(), new PercentType((int) (100 * ((float) level / 255))));
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}

		}
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
	 * Inherited from the ManagedService interface. This method is called
	 * whenever the configuration is updated. This could be signaling that e.g.
	 * the port has changed etc. {@inheritDoc}
	 */
	@Override
	public void updated(Dictionary<String, ?> config) throws ConfigurationException {
		HashMap<String, String> newConfig = new HashMap<String, String>();
		if (config == null) {
			logger.debug("seems like our configuration has been erased, will reset everything!");
		} else {
			// turn config into new HashMap
			for (Enumeration<String> e = config.keys(); e.hasMoreElements();) {
				String key = e.nextElement();
				String value = config.get(key).toString();
				newConfig.put(key, value);
			}
		}

		if (newConfig.entrySet().equals(m_config.entrySet())) {
			logger.debug("config has not changed, done.");
			return;
		}
		m_config = newConfig;

		// configuration has changed
		if (m_isActive) {
			if (isProperlyConfigured()) {
				logger.debug("global binding config has changed, resetting.");
				shutdown();
			} else {
				logger.debug("global binding config has arrived.");
			}
		}
		logger.debug("configuration update complete!");
		setProperlyConfigured(true);
		if (m_isActive) {
		}
		return;
	}

	private void publishState(String name, State state) {
		eventPublisher.postUpdate(name, state);
	}

	/**
	 * Initialize the binding: initialize the driver etc
	 */
	private void initialize() {

		logger.debug("initializing...");

		PlumGenericBindingProvider p = (PlumGenericBindingProvider) providers.iterator().next();
		for (final PlumBindingConfig c : p.getPlumBindingConfigs()) {
			if (threads.get(c) == null || threads.get(c) == false) {
				Runnable r = new Runnable() {
					private void publishState(State state) {
						PlumActiveBinding.this.publishState(c.getName(), state);
					}

					@Override
					public void run() {
						logger.info("Starting monitor thread for Plum IP: " + c.getIpAddr());
						Socket socket = null;
						BufferedReader input = null;
						while (true) {
							try {
								socket = new Socket(c.getIpAddr(), 2708);
								input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

								String s = null;
								while ((s = input.readLine()) != null) {
									logger.info("Plum IP " + c.getIpAddr() + " Message: " + s);
									JSONObject j = new JSONObject(s);
									String type = j.getString("type");
									if (type.equals("dimmerchange")) {
										int level = j.getInt("level");

										if (level == 0) {
											publishState(OnOffType.OFF);
										} else if (level == 255) {
											publishState(OnOffType.ON);
										} else {
											publishState(new PercentType((int) (100 * ((float) level / 255))));
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
				};
				new Thread(r).start();
				threads.put(c, true);
			}
		}
	}

	/**
	 * Clean up all state.
	 */
	private void shutdown() {
		logger.debug("shutting down binding");

	}

	public static String sha256(String base) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
			StringBuffer hexString = new StringBuffer();

			for (int i = 0; i < hash.length; i++) {
				String hex = Integer.toHexString(0xff & hash[i]);
				if (hex.length() == 1) {
					hexString.append('0');
				}
				hexString.append(hex);
			}

			return hexString.toString();
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}
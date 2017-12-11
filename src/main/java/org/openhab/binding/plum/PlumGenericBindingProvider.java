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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openhab.core.binding.BindingConfig;
import org.openhab.core.items.Item;
import org.openhab.model.item.binding.AbstractGenericBindingProvider;
import org.openhab.model.item.binding.BindingConfigParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for parsing the binding configuration.
 *
 * @author MikeNemat
 */
public class PlumGenericBindingProvider extends AbstractGenericBindingProvider implements PlumBindingProvider {

    private static final Logger logger = LoggerFactory.getLogger(PlumGenericBindingProvider.class);
    private final Map<String, Item> items = new HashMap<String, Item>();

    /**
     * Inherited from AbstractGenericBindingProvider. {@inheritDoc}
     */
    @Override
    public String getBindingType() {
        return "plum";
    }

    /**
     * Inherited from AbstractGenericBindingProvider. {@inheritDoc}
     */
    @Override
    public void validateItemType(Item item, String bindingConfig) throws BindingConfigParseException {
        String[] parts = parseConfigString(bindingConfig);
        if (parts.length != 3) {
            throw new BindingConfigParseException("item config must have addr:llid#feature format");
        }
    }

    /**
     * Inherited from AbstractGenericBindingProvider. Processes Plum binding
     * configuration string. {@inheritDoc}
     */
    @Override
    public void processBindingConfiguration(String context, Item item, String bindingConfig)
            throws BindingConfigParseException {
        super.processBindingConfiguration(context, item, bindingConfig);
        String[] parts = parseConfigString(bindingConfig);
        if (parts.length != 3) {
            throw new BindingConfigParseException("item config must have addr:llid#feature format");
        }

        String[] params = parts[2].split(",");
        String feature = params[0];
        HashMap<String, String> args = new HashMap<String, String>();
        for (int i = 1; i < params.length; i++) {
            String[] kv = params[i].split("=");
            if (kv.length == 2) {
                args.put(kv[0], kv[1]);
            } else {
                logger.error("parameter {} does not have format a=b", params[i]);
            }
        }
        PlumBindingConfig config = new PlumBindingConfig(item.getName(), parts[0], parts[1], feature);
        addBindingConfig(item, config);

        logger.info("processing item \"{}\" read from .items file with cfg string {}", item.getName(), bindingConfig);
        items.put(item.getName(), item);
    }

    /**
     * Inherited from PlumBindingProvider. {@inheritDoc}
     */
    @Override
    public PlumBindingConfig getPlumBindingConfig(String itemName) {
        return (PlumBindingConfig) this.bindingConfigs.get(itemName);
    }

    /**
     * Parses binding configuration string. The config string has the format:
     *
     * xx.xxx.xxx:productKey#feature,param1=yyy,param2=zzz
     *
     * @param bindingConfig
     *            string with binding parameters
     * @return String array with split arguments:
     *         [address,prodKey,features+params]
     * @throws BindingConfigParseException
     *             if parameters are invalid
     */
    private String[] parseConfigString(String bindingConfig) throws BindingConfigParseException {
        // the config string has the format
        //
        // xx.xx.xx:productKey#feature
        //
        String shouldBe = "should be address:llid#feature, e.g. 192.168.1.2:1111111111111-222222-333-444444444444444#switch,param=xxx";
        String[] segments = bindingConfig.split("#");
        if (segments.length != 2) {
            throw new BindingConfigParseException("invalid item format: " + bindingConfig + ", " + shouldBe);
        }
        String[] dev = segments[0].split(":");

        if (dev.length != 2) {
            throw new BindingConfigParseException("missing colon in item format: " + bindingConfig + ", " + shouldBe);
        }
        String addr = dev[0];
        String[] retval = { addr, dev[1], segments[1] };

        return retval;
    }

    @Override
    public Collection<PlumBindingConfig> getPlumBindingConfigs() {
        List<PlumBindingConfig> l = new ArrayList<PlumBindingConfig>();
        for (BindingConfig c : bindingConfigs.values()) {
            l.add((PlumBindingConfig) c);
        }
        return l;
    }
}

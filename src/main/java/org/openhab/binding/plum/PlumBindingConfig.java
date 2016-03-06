/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.plum;

import org.openhab.core.binding.BindingConfig;

public class PlumBindingConfig implements BindingConfig {

	public PlumBindingConfig(String name, String ipAddr, String llid, String type) {
		this.name = name;
		this.ipAddr = ipAddr;
		this.llid = llid;
		this.type = type;
	}

	private final String name;
	private final String ipAddr;
	private final String llid;
	private final String type;

	public String getName() {
		return name;
	}

	public String getIpAddr() {
		return ipAddr;
	}

	public String getLlid() {
		return llid;
	}

	public String getType() {
		return type;
	}

}

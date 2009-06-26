/**
 * Copyright (c) 2009 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.fedoraproject.candlepin.model;

import java.util.UUID;

/**
 * @author mmccune
 *
 */
public class BaseModel {

	private String uuid;
	private String name;

	/**
	 * Construct new with UUID
	 * @param uuid
	 */
	public BaseModel(String uuid) {
		this.uuid = uuid;
	}
	
	/**
	 * Default constructor
	 */
	public BaseModel() {
		
	}
	
	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Generate a UUID for an object.
	 * @return String UUID.
	 */
	public static String generateUUID() {
		return UUID.randomUUID().toString(); 
	}
}

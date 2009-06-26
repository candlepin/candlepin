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

import java.util.List;


public class Entitlement extends BaseModel {
	
	private Organization org;
	private List<Entitlement> childEntitlements;

	/**
	 * @return the org
	 */
	public Organization getOrg() {
		return org;
	}

	/**
	 * @param org the org to set
	 */
	public void setOrg(Organization org) {
		this.org = org;
	}

	/**
	 * @return the childEntitlements
	 */
	public List<Entitlement> getChildEntitlements() {
		return childEntitlements;
	}

	/**
	 * @param childEntitlements the childEntitlements to set
	 */
	public void setChildEntitlements(List<Entitlement> childEntitlements) {
		this.childEntitlements = childEntitlements;
	}

}

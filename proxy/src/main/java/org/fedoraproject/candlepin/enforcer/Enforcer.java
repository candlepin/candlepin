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
package org.fedoraproject.candlepin.enforcer;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Product;

public interface Enforcer {
	
	/**
	 * Validate that a consumer can consume an entitlement for a product.
	 * 
	 * Ensures sufficient entitlements remain, but also verifies all attributes 
	 * on the product and relevant entitlement pool pass using the current 
	 * policy.
	 * 
	 * @param consumer Consumer who wishes to consume an entitlement.
	 * @param product Product consumer wishes to have access too.
	 */
	public void validate(Consumer consumer, Product product);
	
}

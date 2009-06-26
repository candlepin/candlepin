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

import java.util.LinkedList;
import java.util.List;


public class Consumer extends BaseModel {
	
	private String type;
	private Organization organization;
	private Consumer parent;
	private List<Product> consumedProducts;
	
	/**
	 * @param uuid
	 */
	public Consumer(String uuid) {
		super(uuid);
	}
	/**
	 * @return the type
	 */
	public String getType() {
		return type;
	}
	/**
	 * @param type the type to set
	 */
	public void setType(String type) {
		this.type = type;
	}
	/**
	 * @return the parent
	 */
	public Consumer getParent() {
		return parent;
	}
	/**
	 * @param parent the parent to set
	 */
	public void setParent(Consumer parent) {
		this.parent = parent;
	}
	/**
	 * @return the consumedProducts
	 */
	public List<Product> getConsumedProducts() {
		return consumedProducts;
	}
	/**
	 * @param consumedProducts the consumedProducts to set
	 */
	public void setConsumedProducts(List<Product> consumedProducts) {
		this.consumedProducts = consumedProducts;
	}
	/**
	 * @return the organization
	 */
	public Organization getOrganization() {
		return organization;
	}
	/**
	 * @param organization the organization to set
	 */
	public void setOrganization(Organization organization) {
		this.organization = organization;
	}
	
	/**
	 * Add a Product to this Consumer.
	 * 
	 */
	public void addConsumedProduct(Product p) {
		if (this.consumedProducts == null) {
			this.consumedProducts = new LinkedList<Product>();
		}
		this.consumedProducts.add(p);
		
	}
	
	
}

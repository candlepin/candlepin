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

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.SequenceGenerator;



/**
 * 
 * 
 *      Attributes can be thought of as a hint on some restriction on the usage of an entitlement.
 *       They will not actually contain the logic on how to enforce the Attribute, but basically just act as a constant the policy rules can look for, and a little metadata that may be required to enforce.
 * Attributes can be affiliated with a given product in the product database, or they can be affiliated with entitlements granted within a particular customer's order/certificate.
 * All Attributes must pass for the entitlement to be granted to a consumer. Not sure if this statement will stand the test of time, may be some issues here with "enabling" attributes vs "restricting" attributes and knowing when to grant/not grant based on the outcome of multiple checks. Will see how it goes.
 * Attributes can be associated with a product, or more commonly with an order of that product contained in the cert. For us, this probably means they'll be associated with entitlement pools in the database.
 *         o If the same Attribute is found on both the product and the entitlement pool, the entitlement pool's version can be assumed as the authoritative one to check. 
 */

@Entity
@Table(name = "cp_attribute")
@SequenceGenerator(name="seq_attribute", sequenceName="seq_attribute", allocationSize=1)
@Embeddable
public class Attribute  implements Persisted {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="seq_attribute")
    private Long id;
    
    
    @Column(nullable = false)
	private String name;
    
//    @Column(nullable = false)
    @Column
	private Long quantity;

    public Attribute() {

    }

    public Attribute(String name, Long quantity) {
        this.name = name;
        this.quantity = quantity;
    }

	public String getName() {
		return name;
	}
	
    /**
     * @return the id
     */
    public Long getId() {
        return this.id;
    }
    
    
	public void setName(String name) {
		this.name = name;
	}

	public Long getQuantity() {
		return quantity;
	}

	public void setQuantity(Long quantity) {
		this.quantity = quantity;
	}
	
    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) return true;
        if (!(anObject instanceof Attribute)) return false;
        
        Attribute another = (Attribute) anObject;
        
        return 
            name.equals(another.getName()) &&
            quantity.equals(another.getQuantity());
    }
    
    @Override
    public int hashCode() {
        return name.hashCode()*31 + quantity.hashCode();
    }
}

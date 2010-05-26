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
package org.fedoraproject.candlepin.client.model;

import java.util.Date;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonIgnore;

/**
 * Simple Pool Model
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class Pool extends TimeStampedEntity{
    protected Long id;
    protected String productName;
    protected String productId;
    protected Long quantity;
    protected Long consumed;
    protected Date startDate;
    protected Date endDate;
    private Set<Attribute> attributes;
    private boolean active;
    private Long subscriptionId;
    private Entitlement sourceEntitlement;
    private boolean activeSubscription;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public Long getQuantity() {
        return quantity;
    }

    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    public Long getConsumed() {
        return consumed;
    }

    public void setConsumed(Long consumed) {
        this.consumed = consumed;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

	public Set<Attribute> getAttributes() {
		return attributes;
	}

	public void setAttributes(Set<Attribute> attributes) {
		this.attributes = attributes;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Long getSubscriptionId() {
		return subscriptionId;
	}

	public void setSubscriptionId(Long subscriptionId) {
		this.subscriptionId = subscriptionId;
	}

	@JsonIgnore
	public void setUnlimited(boolean value){}

	public Entitlement getSourceEntitlement() {
		return sourceEntitlement;
	}

	public void setSourceEntitlement(Entitlement sourceEntitlement) {
		this.sourceEntitlement = sourceEntitlement;
	}

	public boolean isActiveSubscription() {
		return activeSubscription;
	}

	public void setActiveSubscription(boolean activeSubscription) {
		this.activeSubscription = activeSubscription;
	}
}

/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.candlepin.jackson.HateoasInclude;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;

/**
 * SourceSubscription represents the subscription
 * from which a pool was created.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_pool_source_sub")
public class SourceSubscription extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    // An identifier for the subscription this pool is associated with. Note
    // that this is not a database foreign key. The subscription identified
    // could exist in another system only accessible to us as a service.
    // Actual implementations of our SubscriptionService will be used to use
    // this data.
    @Column(nullable = false)
    @Size(max = 255)
    private String subscriptionId;

    // since one subscription can create multiple pools, we need to use a
    // combination of subid/some other key to uniquely identify a pool.
    // subscriptionSubKey is set in the js rules, according to the same logic
    // that will create more than one pool per sub.
    @Column(nullable = false)
    @Size(max = 255)
    private String subscriptionSubKey;

    /**
     * pool derived from the source
     */
    @OneToOne
    @ForeignKey(name = "fk_sourcesub_pool")
    @JoinColumn(nullable = false, unique = true)
    @XmlTransient
    private Pool pool;

    public SourceSubscription() {
    }

    public SourceSubscription(String subscriptionId, String subscriptionSubKey) {
        this();
        this.setSubscriptionId(subscriptionId);
        this.setSubscriptionSubKey(subscriptionSubKey);
    }
    /**
     * @return subscription id associated with this pool.
     */
    public String getSubscriptionId() {
        return subscriptionId;
    }

    /**
     * @param subscriptionId associates the given subscription.
     */
    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    /**
     * @return the subscriptionSubKey
     */
    public String getSubscriptionSubKey() {
        return subscriptionSubKey;
    }

    /**
     * @param subscriptionSubKey the subscriptionSubKey to set
     */
    public void setSubscriptionSubKey(String subscriptionSubKey) {
        this.subscriptionSubKey = subscriptionSubKey;
    }

    /**
     * @return the pool
     */
    public Pool getPool() {
        return pool;
    }

    /**
     * @param pool the pool to set
     */
    public void setPool(Pool pool) {
        this.pool = pool;
    }

    /**
     * @return the id
     */
    @Override
    @HateoasInclude
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }
}

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
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;

/**
 * SubscriptionToken
 */
@XmlRootElement
@Entity
@Table(name = "cp_subscription_token")
public class SubscriptionToken extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;
    
    @Column(nullable = true, unique = true)
    private String token;
    
    @ManyToOne
    @ForeignKey(name = "fk_subscription_token")
    @JoinColumn
    private Subscription subscription;
    
    public String getId() {
        return this.id;
    }
    
    public void setId(String id) {
        this.id = id;
    }


    /**
     * @param subscription the subscription to set
     */
    public void setSubscription(Subscription subscription) {
        this.subscription = subscription;
    }


    /**
     * @return the subscription
     */
    @XmlTransient
    public Subscription getSubscription() {
        return subscription;
    }


    /**
     * @param token the token to set
     */
    public void setToken(String token) {
        this.token = token;
    }


    /**
     * @return the token
     */
    public String getToken() {
        return token;
    }

}

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

import org.hibernate.annotations.ForeignKey;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * SubscriptionToken
 */
@XmlRootElement
@Entity
@Table(name = "cp_subscription_token")
@SequenceGenerator(name = "seq_subscription_token",
    sequenceName = "seq_subscription_token", allocationSize = 1)
public class SubscriptionToken extends AbstractHibernateObject {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
        generator = "seq_subscription_token")
    private Long id;
    
    @Column(nullable = true, unique = true)
    private String token;
    
    // TODO: Should this be bi-directional with a cascade? Subs/tokens could be outside
    // our database, but it's unlikely one would be in the db and the other not.
    @ManyToOne
    @ForeignKey(name = "fk_subscription_token")
    @JoinColumn
    private Subscription subscription;
    
    public Long getId() {
        return this.id;
    }
    
    public void setId(Long id) {
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

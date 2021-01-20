/**
 * Copyright (c) 2009 - 2021 Red Hat, Inc.
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

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

/**
 * Entity represents the consumer activation keys used during registration.
 */
@Entity
@Table(name = ConsumerActivationKey.DB_TABLE)
public class ConsumerActivationKey extends AbstractHibernateObject<ConsumerActivationKey> {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp2_consumer_activation_key";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 37)
    @NotNull
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consumer_id", nullable = false)
    @NotNull
    private Consumer consumer;

    @Column(name = "activation_key_id")
    private String activationKeyId;

    @Column(name = "activation_key_name")
    private String activationKeyName;

    public ConsumerActivationKey() {
        // Intentionally left empty
    }

    public ConsumerActivationKey(Consumer consumer, String activationKeyId, String activationKeyName) {
        this.consumer = consumer;
        this.activationKeyId = activationKeyId;
        this.activationKeyName = activationKeyName;
    }

    public String getActivationKeyName() {
        return activationKeyName;
    }

    public void setActivationKeyName(String activationKeyName) {
        this.activationKeyName = activationKeyName;
    }

    public String getActivationKeyId() {
        return activationKeyId;
    }

    public void setActivationKeyId(String activationKeyId) {
        this.activationKeyId = activationKeyId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }
}

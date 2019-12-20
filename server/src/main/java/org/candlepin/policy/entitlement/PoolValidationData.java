/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.policy.entitlement;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Pool;


/**
 * DTO for validation of pools (pre entitlement rules).
 */
public class PoolValidationData {

    private final Consumer consumer;
    private final Consumer hostConsumer;
    private final Enforcer.CallerType caller;
    private final Pool pool;
    private final Integer quantity;
    private final ConsumerType consumerType;

    /**
     * Use this Builder to construct a PoolValidationData object by using the setters and the build method.
     * Parameters that are required: consumer, pool, caller, consumerType.
     */
    public static class Builder {

        // Required parameters
        private Consumer consumer;
        private Pool pool;
        private Enforcer.CallerType caller;

        // Optional parameters
        private Consumer hostConsumer;
        private Integer quantity;
        private ConsumerType consumerType;

        public Builder setConsumer(Consumer consumer) {
            this.consumer = consumer;
            return this;
        }

        public Builder setHostConsumer(Consumer hostConsumer) {
            this.hostConsumer = hostConsumer;
            return this;
        }

        public Builder setCaller(Enforcer.CallerType caller) {
            this.caller = caller;
            return this;
        }

        public Builder setPool(Pool pool) {
            this.pool = pool;
            return this;
        }

        public Builder setQuantity(Integer quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder setConsumerType(ConsumerType consumerType) {
            this.consumerType = consumerType;
            return this;
        }

        public PoolValidationData build() throws IllegalStateException {
            if (this.consumer == null) {
                throw new IllegalStateException("consumer is needed for PoolValidationData");
            }

            if (this.pool == null) {
                throw new IllegalStateException("pool is needed for PoolValidationData");
            }

            if (this.caller == null) {
                throw new IllegalStateException("caller is needed for PoolValidationData");
            }

            if (this.consumerType == null) {
                throw new IllegalStateException("consumer type is needed for PoolValidationData");
            }

            return new PoolValidationData(this);
        }
    }

    /*
     * Private on purpose. Use the Builder to get an instance.
     */
    private PoolValidationData(Builder builder) {
        this.consumer = builder.consumer;
        this.hostConsumer = builder.hostConsumer;
        this.caller = builder.caller;
        this.pool = builder.pool;
        this.quantity = builder.quantity;
        this.consumerType = builder.consumerType;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public Consumer getHostConsumer() {
        return hostConsumer;
    }

    public Enforcer.CallerType getCaller() {
        return caller;
    }

    public Pool getPool() {
        return pool;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public ConsumerType getConsumerType() {
        return consumerType;
    }
}

/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

package org.candlepin.dto.manifest.v2;

import java.util.Date;

public record SubscriptionDTO(
    String subscriptionId,
    Date startDate,
    Date endDate,
    String contractNumber,
    String accountNumber,
    String productId
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String subscriptionId;
        private Date startDate;
        private Date endDate;
        private String contractNumber;
        private String accountNumber;
        private String productId;

        public Builder setSubscriptionId(String subscriptionId) {
            this.subscriptionId = subscriptionId;
            return this;
        }

        public Builder setStartDate(Date startDate) {
            this.startDate = startDate;
            return this;
        }

        public Builder setEndDate(Date endDate) {
            this.endDate = endDate;
            return this;
        }

        public Builder setContractNumber(String contractNumber) {
            this.contractNumber = contractNumber;
            return this;
        }

        public Builder setAccountNumber(String accountNumber) {
            this.accountNumber = accountNumber;
            return this;
        }

        public Builder setProductId(String productId) {
            this.productId = productId;
            return this;
        }

        public SubscriptionDTO build() {
            return new SubscriptionDTO(
                this.subscriptionId,
                this.startDate,
                this.endDate,
                this.contractNumber,
                this.accountNumber,
                this.productId
            );
        }
    }
}

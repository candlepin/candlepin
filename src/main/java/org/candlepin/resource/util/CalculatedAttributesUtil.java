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
package org.candlepin.resource.util;

import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Pool;
import org.candlepin.policy.js.quantity.QuantityRules;

import com.google.inject.Inject;

import org.xnap.commons.i18n.I18n;

/**
 * CalculatedAttributesUtil
 */
public class CalculatedAttributesUtil {
    private I18n i18n;
    private ConsumerCurator consumerCurator;
    private QuantityRules quantityRules;

    @Inject
    public CalculatedAttributesUtil(I18n i18n, ConsumerCurator consumerCurator,
        QuantityRules quantityRules) {
        this.i18n = i18n;
        this.consumerCurator = consumerCurator;
        this.quantityRules = quantityRules;
    }

    public Pool addCalculatedAttributes(Pool p, Consumer c, Principal principal) {
        if (c == null) {
            return p;
        }

        if (!principal.canAccess(c, Access.READ_ONLY)) {
            throw new ForbiddenException(i18n.tr("User {0} cannot access consumer {1}",
                principal.getPrincipalName(), c.getUuid()));
        }

        p.addCalculatedAttribute("suggested_quantity",
            String.valueOf(quantityRules.getSuggestedQuantity(p, c)));

        //TODO set with value of instance_multiplier
        p.addCalculatedAttribute("quantity_increment", null);
        return p;
    }

    public Pool addCalculatedAttributes(Pool p, String consumerUuid, Principal principal) {
        if (consumerUuid == null) {
            return p;
        }

        Consumer c = consumerCurator.findByUuid(consumerUuid);
        if (c == null) {
            throw new NotFoundException(i18n.tr("consumer: {0} not found",
                consumerUuid));
        }
        return addCalculatedAttributes(p, c, principal);
    }
}

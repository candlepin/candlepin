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
package org.candlepin.policy;

import org.candlepin.model.Consumer;
import org.candlepin.model.Product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;


/**
 * A FactValueCalculator allows the rules to determine which consumer fact is associated with a particular
 * product attribute, and determines the calculated fact value that should be used when determining coverage.
 */
public class FactValueCalculator {

    private static final Logger log = LoggerFactory.getLogger(FactValueCalculator.class);

    // A map of commonly associated product attributes to consumer facts.
    private static final Map<String, String> ATTRIBUTES_TO_FACTS;

    static {
        ATTRIBUTES_TO_FACTS = new HashMap<>();
        ATTRIBUTES_TO_FACTS.put(Product.Attributes.VCPU, Consumer.Facts.CORES);
        ATTRIBUTES_TO_FACTS.put(Product.Attributes.CORES, Consumer.Facts.CORES);
        ATTRIBUTES_TO_FACTS.put(Product.Attributes.RAM, Consumer.Facts.RAM);
        ATTRIBUTES_TO_FACTS.put(Product.Attributes.SOCKETS, Consumer.Facts.SOCKETS);
        ATTRIBUTES_TO_FACTS.put(Product.Attributes.STORAGE_BAND, Consumer.Facts.STORAGE_BAND);
    }

    private FactValueCalculator() {
        // private & empty by design
    }

    /*
     * The default calculator used if there is no custom calculator
     * defined for the product attribute. The RAW consumer fact value
     * is returned, or otherwise 1.
     */
    private static int defaultCalculator(String productAttribute, Consumer consumer) {
        String fact = consumer.getFact(ATTRIBUTES_TO_FACTS.get(productAttribute));
        return fact != null ? Integer.parseInt(fact) : 1;
    }

    /**
     * Returns the calculated fact value of that product attribute for that consumer, to determine coverage.
     * @param productAttribute the name of the product attribute related to the fact value we want.
     * @param consumer the consumer whose fact value we want to calculate.
     * @return the fact value in form of an integer (cores, ram in GB, sockets, guest limit, etc.)
     */
    public static int getFact(String productAttribute, Consumer consumer) {
        if (Product.Attributes.CORES.equals(productAttribute)) {
            int consumerSockets = getFact(Product.Attributes.SOCKETS, consumer);

            // Use the 'default' calculator to get the RAW cores value from the consumer.
            int consumerCoresPerSocket = defaultCalculator(productAttribute, consumer);

            int cores = consumerCoresPerSocket * consumerSockets;
            log.debug("Consumer has {} cores.", cores);
            return cores;
        }
        else if (Product.Attributes.VCPU.equals(productAttribute)) {
            return getFact(Product.Attributes.CORES, consumer);
        }
        else if (Product.Attributes.RAM.equals(productAttribute)) {
            // Consumer ram value must be converted to GB to be compared to the one specified on the Product.
            int consumerRam = defaultCalculator(productAttribute, consumer);
            double ramGb = consumerRam / 1024D / 1024D;
            return (int) Math.round(ramGb);
        }
        else {
            return defaultCalculator(productAttribute, consumer);
        }
    }
}

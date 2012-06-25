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
package org.candlepin.client;

import java.util.List;

import org.candlepin.client.model.Entitlement;
import org.candlepin.client.model.EntitlementCertificate;
import org.candlepin.client.model.Pool;
import org.candlepin.client.model.ProductCertificate;

/**
 * CandlepinClientFacade
 */
public interface CandlepinClientFacade {

    /**
     * Returns true if the client is already registered
     *
     * @return true if registered
     */
    boolean isRegistered();

    /**
     * Returns the UUID for the consumer, or null if not registered.
     *
     * @return the UUID of the consumer
     */
    String getUUID();

    /**
     * Registers a consumer with a provided name and type. The credentials are
     * user for authentication.
     *
     * @return The UUID of the new consumer.
     */
    String register(String username, String password, String name, String type);

    /**
     * Updates the consumer information, based on the current jvm info.
     *
     * @return <code>true</code>, if successful
     */
    boolean updateConsumer();

    /**
     * Register to an existing consumer. The credentials are user for
     * authentication.
     *
     * @param username the username
     * @param password the password
     * @param uuid the uuid
     * @return true, if successful
     */
    boolean registerExisting(String username, String password, String uuid);

    /**
     * Remove he consumer from candlepin and all of the entitlements which the
     * conumser have subscribed to.
     */
    void unRegister();

    /**
     * List the pools which the consumer could subscribe to
     *
     * @return the list of exception
     */
    List<Pool> listPools();

    List<Entitlement> bindByPool(Long poolId, int quantity);

    List<Entitlement> bindByProductId(String productId, int quantity);

    List<Entitlement> bindByRegNumber(String regNo, int quantity);

    List<Entitlement> bindByRegNumber(String regNo, int quantity, String email,
        String defLocale);

    void unBindBySerialNumber(int serialNumber);

    void unBindAll();

    boolean updateEntitlementCertificates();

    List<EntitlementCertificate> getCurrentEntitlementCertificates();

    List<ProductCertificate> getInstalledProductCertificates();

    /**
     * @param password
     */
    void generatePKCS12Certificates(String password);
}

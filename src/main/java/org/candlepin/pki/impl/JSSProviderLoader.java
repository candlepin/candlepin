/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.pki.impl;

import org.mozilla.jss.CertDatabaseException;
import org.mozilla.jss.CryptoManager;
import org.mozilla.jss.InitializationValues;
import org.mozilla.jss.JSSProvider;
import org.mozilla.jss.KeyDatabaseException;
import org.mozilla.jss.NotInitializedException;
import org.mozilla.jss.crypto.AlreadyInitializedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.Security;


/**
 * Provides initialization logic for JSS via the initialize method,
 * Provides the addProvider() method, which, when called, initializes the JSS CryptoManager (which in turn
 * initializes the NSS DB) and loads the JSS Provider into the JVM.
 */
public class JSSProviderLoader {
    private static final Logger log = LoggerFactory.getLogger(JSSProviderLoader.class);

    private static final String NSS_DB_LOCATION = "/etc/pki/nssdb";
    private static final String PROVIDER_NAME = "Mozilla-JSS";

    private static Provider provider = null;

    private JSSProviderLoader() {
        throw new UnsupportedOperationException("JSSProviderLoader should not be instantiated");
    }

    /**
     * Fetches a string representing the JSS version loaded at runtime. This function should never
     * return null.
     *
     * @return
     *  JSS version string
     */
    public static String getJSSVersion() {
        return JSSProvider.class.getPackage().getSpecificationVersion();
    }

    /**
     * Initializes the JSS CryptoManager (which in turn initializes the NSS DB), and loads the JSS provider
     * into the JVM. The method should be called during context initialization. Can be called more than once
     * without ill effect (It will be a no-op if the provider is already installed).
     */
    public static synchronized void initialize() {
        if (provider != null) {
            return; // Already initialized
        }

        log.info("Initializing JSS CryptoManager...");
        log.info("Using JSS v{}", getJSSVersion());

        InitializationValues initValues = new InitializationValues(NSS_DB_LOCATION);

        initValues.noCertDB = true;
        initValues.readOnly = false;
        initValues.noModDB = false;
        initValues.installJSSProvider = false;
        initValues.initializeJavaOnly = false;

        try {
            CryptoManager.initialize(initValues);
        }
        catch (AlreadyInitializedException | CertDatabaseException | GeneralSecurityException |
            KeyDatabaseException e) {
            throw new JSSLoaderException("Could not initialize CryptoManager!", e);
        }

        // Create a JSS provider to return
        provider = new JSSProvider();

        // Ensure the provider is not installed on the provider chain
        if (Security.getProvider(PROVIDER_NAME) != null) {
            log.warn("JSS security provider installed on provider chain; removing...");

            // Don't pollute the provider space with JSS -- it's broken
            Security.removeProvider(PROVIDER_NAME);
        }

        log.info("JSS initialization complete");
    }

    /**
     * Fetches the JSS security provider. If JSS has not yet been initialized, and the initialize
     * argument is true, it will be initialized before fetching the provider. If the initialize
     * argument is false and JSS is not initialized, this method throws an exception.
     *
     * @param initialize
     *  whether to initialize JSS if it has not yet been initialized
     *
     * @return
     *  the JSS security provider
     */
    public static synchronized Provider getProvider(boolean initialize) {
        if (initialize) {
            initialize();
        }

        if (provider == null) {
            throw new IllegalStateException("JSS has not yet been initialized");
        }

        return provider;
    }

    /**
     * Fetches the JSS CryptoManager. If JSS has not yet been initialized, and the initialize
     * argument is true, it will be initialized before fetching the provider. If the initialize
     * argument is false and JSS is not initialized, this method throws an exception.
     *
     * @param initialize
     *  whether to initialize JSS if it has not yet been initialized
     *
     * @throws JSSLoaderException
     *  if the CryptoManager cannot be fetched
     *
     * @return
     *  the JSS crypto manager
     */
    public static synchronized CryptoManager getCryptoManager(boolean initialize) {
        if (initialize) {
            initialize();
        }

        try {
            return CryptoManager.getInstance();
        }
        catch (NotInitializedException e) {
            throw new JSSLoaderException("Unable to fetch CryptoManager", e);
        }

    }

}

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
import org.mozilla.jss.JSSProvider;
import org.mozilla.jss.KeyDatabaseException;
import org.mozilla.jss.crypto.AlreadyInitializedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.Arrays;

/**
 * Provides the addProvider() method, which, when called, initializes the JSS CryptoManager (which in turn
 * initializes the NSS DB) and loads the JSS Provider into the JVM.
 */
public class JSSProviderLoader {
    private static JSSProvider jssProvider = null;
    private static final String NSS_DB_LOCATION = "/etc/pki/nssdb";
    private static final Logger log = LoggerFactory.getLogger(JSSProviderLoader.class);

    static {
        // Satellite 6 is only supported on 64 bit architectures
        addLibraryPath("/usr/lib64/jss");
        System.loadLibrary("jss4");
    }

    /**
     * Code from http://fahdshariff.blogspot.jp/2011/08/changing-java-library-path-at-runtime.html so that we
     * can add the JSS directory to the load path without having to require it as a JVM option on startup.
     * Modifying java.library.path doesn't work because the JVM has already loaded that property.
     *
     * Note that this method is doing some tricks with reflection to make the usr_paths field accessible.
     * If we are deployed under a strict SecurityManager policy this isn't going to work.
     *
     * @param pathToAdd
     * @throws NoSuchFieldException if the "usr_paths" field is missing which it shouldn't be.
     * @throws IllegalAccessException on access error
     */
    public static void addLibraryPath(String pathToAdd)  {
        final Field usrPathsField;
        try {
            usrPathsField = ClassLoader.class.getDeclaredField("usr_paths");

            /* TODO maybe detect if we are running in a development mode and only do setAccessible(true) then.
             * Depending on the JVM's SecurityManager policy, this might fail and production deployments
             * should probably have the -Djava.library.path set correctly anyway to inform users/admins
             * that we're using native code.
             */
            // setAccessible applies only to the single instance of Field so we do not need to set it back
            usrPathsField.setAccessible(true);

            //get array of paths
            final String[] paths = (String[]) usrPathsField.get(null);

             //check if the path to add is already present
            for (String path : paths) {
                if (path.equals(pathToAdd)) {
                    return;
                }
            }

            //add the new path
            final String[] newPaths = Arrays.copyOf(paths, paths.length + 1);
            newPaths[newPaths.length - 1] = pathToAdd;
            usrPathsField.set(null, newPaths);
        }
        catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Could not add " + pathToAdd + " to library path", e);
        }
    }

    private JSSProviderLoader() {
        // static methods only
    }

    public static void addProvider() {
        log.debug("Starting call to JSSProviderLoader.addProvider()...");
        CryptoManager.InitializationValues ivs =
            new CryptoManager.InitializationValues(NSS_DB_LOCATION);
        ivs.noCertDB = true;
        ivs.installJSSProvider = false;
        ivs.initializeJavaOnly = false;

        try {
            CryptoManager.initialize(ivs);
        }
        catch (AlreadyInitializedException e) {
            log.warn("CryptoManager was already initialized.");
        }
        catch (KeyDatabaseException | CertDatabaseException | GeneralSecurityException e) {
            log.error("Could not initialize CryptoManager!", e);
        }

        jssProvider = new JSSProvider();
        int addProviderReturn = Security.addProvider(jssProvider);
        log.debug("Finished call to JSSProviderLoader.addProvider(). Returned value: {}",
            addProviderReturn);
    }
}

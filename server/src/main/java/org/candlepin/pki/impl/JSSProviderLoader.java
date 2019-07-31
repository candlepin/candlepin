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

import org.apache.commons.lang3.StringUtils;
import org.mozilla.jss.CertDatabaseException;
import org.mozilla.jss.JSSProvider;
import org.mozilla.jss.KeyDatabaseException;
import org.mozilla.jss.crypto.AlreadyInitializedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
            throw new JSSLoaderException("Could not add " + pathToAdd + " to library path", e);
        }
    }

    private JSSProviderLoader() {
        // static methods only
    }

    /**
     * Initializes the JSS CryptoManager (which in turn initializes the NSS DB), and loads the JSS provider
     * into the JVM. The method should be called during context initialization. Can be called more than once
     * without ill effect (-1 will be returned if the provider is already installed).
     *
     * Note: This method uses reflection to initialize the CryptoManager, due to dynamically loading the
     * InitializationValues class, depending on which JSS version is on the classpath. This is because of
     * breaking changes introduced between JSS 4.4.X (RHEL 7) and 4.5.0+ (RHEL 8).
     */
    public static void addProvider() {
        log.debug("Starting call to JSSProviderLoader.addProvider()...");

        ClassLoader loader = JSSProviderLoader.class.getClassLoader();

        Object initializationValuesObject = getInitializationValuesObject(loader);
        Class<?> ivsClass = initializationValuesObject.getClass();

        try {
            // Set values on fields of the InitializationValues object.
            Field noCertDB = ivsClass.getField("noCertDB");
            Field installJSSProvider = ivsClass.getField("installJSSProvider");
            Field initializeJavaOnly = ivsClass.getField("initializeJavaOnly");
            noCertDB.set(initializationValuesObject, true);
            installJSSProvider.set(initializationValuesObject, false);
            initializeJavaOnly.set(initializationValuesObject, false);

            // Initialize the CryptoManager, which will initialize the nss DB.
            Class<?> cryptoManagerClass = loader.loadClass("org.mozilla.jss.CryptoManager");
            Method initialize = cryptoManagerClass.getMethod("initialize", ivsClass);
            initialize.invoke(null, initializationValuesObject);
        }
        catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException |
            InvocationTargetException | IllegalAccessException e) {

            if (e.getCause() instanceof AlreadyInitializedException) {
                log.warn("CryptoManager was already initialized.");
            }
            else if (e.getCause() instanceof KeyDatabaseException ||
                e.getCause() instanceof CertDatabaseException ||
                e.getCause() instanceof GeneralSecurityException) {
                throw new JSSLoaderException("Could not initialize CryptoManager!", e.getCause());
            }
            else {
                throw new JSSLoaderException("Could not initialize CryptoManager!", e);
            }
        }

        jssProvider = new JSSProvider();
        int addProviderReturn = Security.addProvider(jssProvider);
        log.debug("Finished call to JSSProviderLoader.addProvider(). Returned value: {}",
            addProviderReturn);
    }

    /*
     * Load the appropriate InitializationValues class, depending on JSS version,
     * and return an instance of it. We load:
     * - the 4.4.Z version of the class for RHEL 7.6+
     * - the 4.5.Z+ version of the class for RHEL 8+
     */
    private static Object getInitializationValuesObject(ClassLoader loader) {
        String jssVersionStr = JSSProvider.class.getPackage().getSpecificationVersion();
        log.info("Using JSS version {}", jssVersionStr);

        float jssVersion;
        // Get the X.Y out of the version, even if it is in the format X.Y.Z
        if (StringUtils.countMatches(jssVersionStr, ".") == 1) {
            jssVersion = new Float(jssVersionStr);
        }
        else {
            int indexOfLastPeriod = jssVersionStr.lastIndexOf(".");
            jssVersion = new Float(jssVersionStr.substring(0, indexOfLastPeriod));
        }

        try {
            String ivsClassName;
            if (Float.compare(jssVersion, 4.4f) == 0) {
                ivsClassName = "org.mozilla.jss.CryptoManager$InitializationValues";
            }
            else if (Float.compare(jssVersion, 4.4f) > 0) {
                ivsClassName = "org.mozilla.jss.InitializationValues";
            }
            else {
                throw new JSSLoaderException("Candlepin does not support JSS versions less than 4.4!");
            }
            return loader.loadClass(ivsClassName).getConstructor(String.class).newInstance(NSS_DB_LOCATION);
        }
        catch (InstantiationException | ClassNotFoundException | IllegalAccessException |
            NoSuchMethodException | InvocationTargetException e) {

            throw new JSSLoaderException("Could not instantiate a JSS InitializationValues object!", e);
        }
    }
}

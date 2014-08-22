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
package org.candlepin.gutterball.util;

import org.candlepin.gutterball.eventhandler.EventHandler;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

/**
 * EventHandlerLoader Loads subclasses of EventHandler from the package
 * that EventHandler is found in.
 *
 * Adapted from http://stackoverflow.com/a/15519745
 */
public class EventHandlerLoader {

    private static final char DOT = '.';
    private static final char SLASH = '/';
    private static final String CLASS_SUFFIX = ".class";

    private EventHandlerLoader() {
    }

    /**
     * Get EventHandlers, this way we don't have to worry about registering them.
     *
     * @return list of subclasses of EventHandler
     */
    public static List<Class<? extends EventHandler>> getClasses() {
        String packageName = EventHandler.class.getPackage().getName();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        String scannedPath = EventHandler.class.getPackage().getName().replace(DOT, SLASH);
        Enumeration<URL> resources;
        try {
            resources = loader.getResources(scannedPath);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Unable to load " + scannedPath, e);
        }
        List<Class<? extends EventHandler>> classes = new LinkedList<Class<? extends EventHandler>>();
        while (resources.hasMoreElements()) {
            File file = new File(resources.nextElement().getFile());
            classes.addAll(find(file, packageName));
        }
        return classes;
    }

    private static List<Class<? extends EventHandler>> find(File file, String scannedPackage) {
        List<Class<? extends EventHandler>> classes = new LinkedList<Class<? extends EventHandler>>();
        String resource = scannedPackage + DOT + file.getName();
        if (file.isDirectory()) {
            for (File nestedFile : file.listFiles()) {
                classes.addAll(find(nestedFile, scannedPackage));
            }
        }
        else if (resource.endsWith(CLASS_SUFFIX)) {
            try {
                Class<?> tmpClass = Class.forName(
                        resource.substring(0, resource.length() - CLASS_SUFFIX.length()));
                if (!tmpClass.isInterface() && EventHandler.class.isAssignableFrom(tmpClass)) {
                    classes.add((Class<? extends EventHandler>) tmpClass);
                }
            }
            catch (ClassNotFoundException ignore) {
                // ignore
            }
        }
        return classes;
    }

}

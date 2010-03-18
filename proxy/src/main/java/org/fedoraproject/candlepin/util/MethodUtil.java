/**
 * Copyright (c) 2009 Red Hat, Inc.
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

package org.fedoraproject.candlepin.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.log4j.Logger;

/**
 * A simple class that assists with method invocation.  We should just use 
 * the jakarta-commons MethodUtils class, but that class can't deal with 
 * static methods, so it is useless to us.
 * @version $Rev$
 */
public class MethodUtil {

    private static Logger log = Logger.getLogger(MethodUtil.class);
        
    /**
     * Private constructore
     */
    private MethodUtil() {
    }

    /* This is insanity itself, but the reflection APIs ignore inheritance.
     * So, if you ask for a method that accepts (Integer, HashMap, HashMap),
     * and the class only has (Integer, Map, Map), you won't find the method.
     * This method uses Class.isAssignableFrom to solve this problem.
     */
    @SuppressWarnings("unchecked")
    private static boolean isCompatible(Class[] declaredParams, Object[] params) {
        if (params.length != declaredParams.length) {
            return false;
        }

        for (int i = 0; i < params.length; i++) {
            if (!declaredParams[i].isInstance(params[i])) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Calls the setter for the <code>fieldName</code> of the object
     * <code>o</code> with the given <code>param</code> 
     * @param o object being modified
     * @param fieldName field to be set
     * @param param value to be used
     */
    public static void callSetter(Object o, String fieldName, Object param) {
        String setter = "set" +  fieldName.substring(0, 1).toUpperCase() +
            fieldName.substring(1);
        MethodUtil.callMethod(o, setter, param);
    }
   
    /**
     * Call the specified method with the specified arguments, converting
     * the argument type if necessary.
     * @param o The object from which to call the method
     * @param methodCalled The method to call
     * @param params a Collection of the parameters to methodCalled
     * @return the results of the method of the subclass
     */
    @SuppressWarnings("unchecked")
    public static Object callMethod(Object o, String methodCalled, 
                                    Object... params) {
        /* This whole method is currently an ugly mess that needs to be 
         * refactored.   rbb
         */
        if (log.isDebugEnabled()) {
            log.debug("Trying to call: " + methodCalled + " in " + o.getClass());
        }
        Class myClass = o.getClass();
        Method[] methods;
        try {
            methods = myClass.getMethods();
        }
        catch (SecurityException e) {
            // This should _never_ happen, because the Handler classes must
            // have public classes if they're expected to work.
            throw new IllegalArgumentException("no public methods in class " + myClass);
        }

        Method foundMethod = null;
        Object[] converted = new Object[params.length];
        boolean rightMethod = false;
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].getName().equals(methodCalled)) {
                foundMethod = methods[i];
                
                Class[] types = foundMethod.getParameterTypes();
                if (types.length != params.length) {
                    continue;
                }
        
                // We have a method that might work, now we need to loop 
                // through the params and make sure that the types match 
                // with what was provided in the Collection.  If they don't 
                // match, try to do a translation, if that fails try the next
                boolean found = true;
                for (int j = 0; j < types.length; j++) {
                    Object curr = params[j];
                    if (log.isDebugEnabled()) {
                        log.debug("Trying to translate from: " + 
                                 ((curr == null) ? null : curr.getClass()) +
                                 " to: " + types[j] + 
                                 " isInstance: " + types[j].isInstance(curr));
                    }
                    if (curr != null && curr.getClass().isPrimitive() && 
                        types[j].isPrimitive()) {
                        if (log.isDebugEnabled()) {
                            log.debug("2 primitives");
                        }
                        converted[j] = curr;
                    }
                    if ((curr == null && !types[j].isPrimitive()) || 
                        types[j].isInstance(curr)) {
                        if (log.isDebugEnabled()) {
                            log.debug("same type");
                        }
                        converted[j] = curr;
                        continue;
                    }
                    try {
                        if (log.isDebugEnabled()) {
                            log.debug("calling converter: " + curr);
                        }
                        converted[j] = Translator.convert(curr, types[j]);
                    }
                    catch (RuntimeException e) {
                        log.debug("Couldn't translate between " + curr + 
                                  " and " + types[j]);
                        // move on to the next method.
                        found = false; 
                        break;
                    }
                }
                if (found) {
                    rightMethod = found;
                    break;
                }
            }
        }

        if (!rightMethod) {
            String message = "Could not find method called: " + methodCalled + 
                           " in class: " + o.getClass().getName() + " with params: ["; 
            for (int i = 0; i < params.length; i++) {
                if (params[i] != null) {
                    message = message + ("type: " + params[i].getClass().getName() + 
                              ", value: " + params[i]);
                    if (i < params.length - 1) {
                        message = message + ", ";
                    }
                }
            }
            message = message + "]";
            throw new RuntimeException(message);
        }
        try {
            return foundMethod.invoke(o, converted);
        }
        catch (IllegalAccessException e) {
            throw new RuntimeException("Could not access " + methodCalled, e);
        }
        catch (InvocationTargetException e) {
            throw new RuntimeException("Something bad happened when " +
                                                "calling " + methodCalled, e);
        }
    }
    
    /**
     * Create a new instance of the classname passed in.
     * 
     * @param className the class to construct
     * @param args arguments to the ctor of the given className
     * @return instance of class passed in.
     */
    @SuppressWarnings("unchecked")
    public static Object callNewMethod(String className, Object... args) {
        Object retval = null;
        
        try {
            Class clazz = Thread.currentThread().
                            getContextClassLoader().loadClass(className);
            System.out.println("cz: " + clazz.getName());
            if (args == null || args.length == 0) {
                retval = clazz.newInstance();                
            }
            else {
                try {
                    Constructor[] ctors = clazz.getConstructors();
                    for (Constructor ctor : ctors) {
                        if (isCompatible(ctor.getParameterTypes(), args)) {
                            return ctor.newInstance(args);
                        }
                    }
                }
                catch (IllegalArgumentException e) {
                    throw new RuntimeException(e);
                }
                catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }

        }
        catch (InstantiationException e) {
            throw new RuntimeException(e);
        }
        catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        
        return retval;
    }
}

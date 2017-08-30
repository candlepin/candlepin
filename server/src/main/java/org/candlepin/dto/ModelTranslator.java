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
package org.candlepin.dto;

import org.candlepin.model.CandlepinQuery;



/**
 * The ModelTranslator interface defines common functionality required by every ModelTranslator
 * implementation.
 */
public interface ModelTranslator {

    /**
     * Registers the given translator to the specified model class. If a translator has already
     * been registered with the given class, the translator will be replaced with the newly
     * specified translator, and the previous translator will be returned. If the model class has
     * not yet been registered to a translator, this method returns null.
     *
     * @param srcClass
     *  The model class to register
     *
     * @param translator
     *  The translator to register for the given class
     *
     * @throws IllegalArgumentException
     *  if either srcClass or translator are null
     *
     * @return
     *  The translator previously registered for the given class, or null if the model class had not
     *  yet been registered.
     */
    <I, O> ObjectTranslator registerTranslator(Class<I> srcClass, ObjectTranslator<I, O> translator);

    /**
     * Unregisters and returns any translator associated with the given class. If the class has not
     * been associated with a translator, this method returns null.
     *
     * @param srcClass
     *  The model class to unregister
     *
     * @throw IllegalArgumentException
     *  if srcClass is null
     *
     * @return
     *  The previously registered translator for the given class, or null a translator had not been
     *  registered
     */
    ObjectTranslator unregisterTranslator(Class srcClass);

    /**
     * Retrieves the translator registered for the specified class. If a translator has not been
     * registered for the given class, this method returns null.
     * <p></p>
     * Note that unlike the findTranslator methods, this method will only perform an exact class
     * match, and will not attempt to find a suitable translator if the given class does not have
     * an explicitly registered translator.
     *
     * @param srcClass
     *  The model class for which to retrieve a translator
     *
     * @throw IllegalArgumentException
     *  if srcClass is null
     *
     * @return
     *  The translator registered for the given class, or null if a translator has not yet been
     *  registered
     */
    ObjectTranslator getTranslator(Class srcClass);

    /**
     * Finds a translator for the given class. If a translator cannot be found, this method
     * returns null.
     *
     * @param srcClass
     *  The source class for which to find a translator
     *
     * @throws IllegalArgumentException
     *  if srcClass is null
     *
     * @return
     *  a translator for the given source object, or null if a translator could not be found
     */
    ObjectTranslator findTranslatorByClass(Class srcClass);

    /**
     * Finds a translator for a specific object instance. If an appropriate translator cannot be
     * found, this method returns null.
     *
     * @param instance
     *  The specific object instance for which to fetch a translator
     *
     * @throws IllegalArgumentException
     *  if instance is null
     *
     * @return
     *  a translator for the given object instance, or null if a translator could not be found
     */
    ObjectTranslator findTranslatorByInstance(Object instance);

    /**
     * Builds a new instance from the given source object using the translators registered to
     * translator to process the object and any nested objects it contains. If this factory does
     * not have a registered translator which can process the source object and its nested objects,
     * this method throws a TransformationException. If the source object is null, this method
     * returns null.
     *
     * @param source
     *  The source object for which to build a DTO
     *
     * #throws TranslationException
     *  if a translator cannot be found for the source object or any of its nested objects
     *
     * @return
     *  a newly translated instance of the source object, or null if the source object is null
     */
    <I, O> O translate(I source);

    // TODO: Add a translate method for doing bulk entity translation.

    /**
     * Applies a translate to the specified query that uses this ModelTranslator to translate the
     * entities fetched by the query to DTOs. If this factory does not have a registered translator
     * which can process the query's entities and their nested objects, this method will complete
     * as normal, but the CandlepinQuery will throw a DTOException when it fetches any results.
     *
     * @param query
     *  The CandlepinQuery to translate
     *
     * @throws IllegalArgumentException
     *  if query is null
     *
     * #throws TranslationException
     *  if a translator cannot be found for the source object or any of its nested objects
     *
     * @return
     *  A translated CandlepinQuery using this ModelTranslator for its element transformation
     */
    <I, O> CandlepinQuery<O> translateQuery(CandlepinQuery<I> query);

    // /**
    //  * Populates the given destination object with data from the source object, using the specified
    //  * ModelTranslator to populate nested objects and object collections. If a ModelTranslator is
    //  * not provided, any nested objects or object collections will be set to null or empty values as
    //  * appropriate.
    //  *
    //  * @param source
    //  *  The source object from which to fetch data
    //  *
    //  * @param destination
    //  *  The destination object to populate
    //  *
    //  * @throws IllegalArgumentException
    //  *  if either source or destination objects are null
    //  *
    //  * @throws TranslationException
    //  *  if a translator cannot be found for the source object or any of its nested objects
    //  *
    //  * @return
    //  *  The populated destination object
    //  */
    // <I, O> O populate(I source, O destination);

}

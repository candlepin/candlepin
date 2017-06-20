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
import org.candlepin.model.ModelEntity;



/**
 * The DTOFactory interface defines common functionality required by every DTO factory
 * implementation.
 */
public interface DTOFactory {

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
    <I extends ModelEntity<I>, O extends CandlepinDTO<O>> EntityTranslator registerTranslator(
        Class<I> srcClass, EntityTranslator<I, O> translator);

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
    <I extends ModelEntity<I>, O extends CandlepinDTO<O>> EntityTranslator<I, O> unregisterTranslator(
        Class<I> srcClass);

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
    <I extends ModelEntity<I>, O extends CandlepinDTO<O>> EntityTranslator<I, O> getTranslator(
        Class<I> srcClass);

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
    EntityTranslator findTranslatorByClass(Class<? extends ModelEntity> srcClass);

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
    EntityTranslator findTranslatorByInstance(ModelEntity instance);

    /**
     * Builds a DTO from the given source object using the translators registered to this factory
     * to process the object and any nested objects it contains. If this factory does not have a
     * registered translator which can process the source object and its nested objects, this
     * method throws a DTOException. If the source object is null, this method returns null.
     *
     * @param source
     *  The source object for which to build a DTO
     *
     * #throws DTOException
     *  if a translator cannot be found for the source object or any of its nested objects
     *
     * @return
     *  a new DTO representing the source object, or null if the source object is null
     */
    <I extends ModelEntity<I>, O extends CandlepinDTO<O>> O buildDTO(ModelEntity<I> source);

    // TODO: Add a buildDTOs method for doing bulk entity translation.

    /**
     * Applies a transform to the specified query that uses this DTOFactory to transform the
     * entities fetched by the query to DTOs. If this factory does not have a registered translator
     * which can process the query's entities and their nested objects, this method will complete
     * as normal, but the CandlepinQuery will throw a DTOException when it fetches any results.
     *
     * @param query
     *  The CandlepinQuery to transform
     *
     * @throws IllegalArgumentException
     *  if query is null
     *
     * #throws DTOException
     *  if a translator cannot be found for the source object or any of its nested objects
     *
     * @return
     *  A transformed CandlepinQuery using this DTOFactory for its transformation
     */
    <I extends ModelEntity<I>, O extends CandlepinDTO<O>> CandlepinQuery<O> transformQuery(
        CandlepinQuery<I> query);

}

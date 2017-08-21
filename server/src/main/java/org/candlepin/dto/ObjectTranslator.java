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



/**
 * The ObjectTranslator interface defines a set of standard functionality to be provided by all
 * translator instances
 *
 * @param <I>
 *  The class of objects that can be processed by this translator
 *
 * @param <O>
 *  The class of objects to be output by this translator
 */
public interface ObjectTranslator<I, O> {

    /**
     * Translates the given source object, omitting any nested objects and object collections from
     * the resultant output.
     *
     * @param source
     *  The source object to translate
     *
     * @return
     *  a translated copy of the source object, or null if the source object is null
     */
    O translate(I source);

    /**
     * Translates the given source object, using the provided ModelTranslator to translate any
     * nested objects. If a ModelTranslator is not provided, then any nested objects will be
     * omitted from the resultant object.
     *
     * @param modelTranslator
     *  The ModelTranslator to use for translating nested objects in the source object
     *
     * @param source
     *  The source object to translate
     *
     * @return
     *  a translated copy of the source object, or null if the source object is null
     */
    O translate(ModelTranslator modelTranslator, I source);

    /**
     * Populates the given destination object with data from the source object, setting any nested
     * objects or object collections to null or empty values as appropriate.
     *
     * @param source
     *  The source object from which to fetch data
     *
     * @param destination
     *  The destination object to populate
     *
     * @throws IllegalArgumentException
     *  if source is null
     *
     * @throws IllegalArgumentException
     *  if destination is null
     *
     * @return
     *  The populated destination object
     */
    O populate(I source, O destination);

    /**
     * Populates the given destination object with data from the source object, using the specified
     * ModelTranslator to populate nested objects and object collections. If a ModelTranslator is
     * not provided, any nested objects or object collections will be set to null or empty values as
     * appropriate.
     *
     * @param source
     *  The source object from which to fetch data
     *
     * @param destination
     *  The destination object to populate
     *
     * @throws IllegalArgumentException
     *  if source is null
     *
     * @throws IllegalArgumentException
     *  if destination is null
     *
     * @return
     *  The populated destination object
     */
    O populate(ModelTranslator modelTranslator, I source, O destination);

    // We probably don't need these quite yet. At present, only two callers would make use of them
    // (ProductManager and ContentManager). However, if these would be useful in the future, we
    // should uncomment these definitions and implement them on the translators.

    // /**
    //  * Checks if the destination would be changed if populated by the source object, assuming any
    //  * nested objects or object collections would be set to null.
    //  *
    //  * @param source
    //  *  The source object from which to fetch data
    //  *
    //  * @param destination
    //  *  The destination object to check for changes
    //  *
    //  * @throws IllegalArgumentException
    //  *  if source is null
    //  *
    //  * @throws IllegalArgumentException
    //  *  if destination is null
    //  *
    //  * @return
    //  *  true if the destination object would be changed if populated by the source object; false
    //  *  otherwise
    //  */
    // boolean wouldChange(I source, O destination);

    // /**
    //  * Checks if the destination would be changed if populated by the source object, using the
    //  * provided model translator to check nested objects and object collections. If the given
    //  * translator is null, any nested objects and object collections will be checked as if they
    //  * are null.
    //  *
    //  * @param source
    //  *  The source object from which to fetch data
    //  *
    //  * @param destination
    //  *  The destination object to check for changes
    //  *
    //  * @throws IllegalArgumentException
    //  *  if source is null
    //  *
    //  * @throws IllegalArgumentException
    //  *  if destination is null
    //  *
    //  * @return
    //  *  true if the destination object would be changed if populated by the source object; false
    //  *  otherwise
    //  */
    // boolean wouldChange(ModelTranslator modelTranslator, I source, O destination);

}

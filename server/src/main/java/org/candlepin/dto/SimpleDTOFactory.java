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
import org.candlepin.util.ElementTransformer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;



/**
 * The SimpleDTOFactory class provides the basic functionality required for building DTOs from
 * model entities. The factory works by delegating the translation work to one or more
 * EntityTranslator instances, which are registered to a given DTOFactory instance.
 */
public class SimpleDTOFactory implements DTOFactory {
    private static Logger log = LoggerFactory.getLogger(DTOFactory.class);

    protected Map<Class, EntityTranslator> translators;


    /**
     * Initializes a new DTOFactory instance.
     */
    public SimpleDTOFactory() {
        this.translators = new HashMap<Class, EntityTranslator>();
    }

    /**
     * {@inheritDoc}
     */
    public <I extends ModelEntity<I>, O extends CandlepinDTO<O>> EntityTranslator registerTranslator(
        Class<I> srcClass, EntityTranslator<I, O> translator) {

        if (srcClass == null) {
            throw new IllegalArgumentException("srcClass is null");
        }

        if (translator == null) {
            throw new IllegalArgumentException("translator is null");
        }

        EntityTranslator existing = this.translators.get(srcClass);
        this.translators.put(srcClass, translator);

        return existing;
    }

    /**
     * {@inheritDoc}
     */
    public <I extends ModelEntity<I>, O extends CandlepinDTO<O>> EntityTranslator<I, O> unregisterTranslator(
        Class<I> srcClass) {

        if (srcClass == null) {
            throw new IllegalArgumentException("srcClass is null");
        }

        return this.translators.remove(srcClass);
    }

    /**
     * {@inheritDoc}
     */
    public <I extends ModelEntity<I>, O extends CandlepinDTO<O>> EntityTranslator<I, O> getTranslator(
        Class<I> srcClass) {

        if (srcClass == null) {
            throw new IllegalArgumentException("srcClass is null");
        }

        return this.translators.get(srcClass);
    }

    /**
     * Fetches a translator for the given class. If a translator cannot be found, this method
     * returns null.
     * <p></p>
     * This method uses the following algorithm to determine which translator to use:
     * <pre>
     * 1. Fetch the source object's class, C
     * 2. Fetch any translator T registered for class C
     * 3. If T is not null, go to step 8
     * 4. Fetch the list of interfaces, L, implemented by class C
     * 5. For each interface I in list L...
     *    a. fetch the translator T registered for interface I
     *    b. if T is not null, go to step 8
     * 6. Set C to the superclass of C
     * 7. If C is not null, go to step 2
     * 8. Return T
     * </pre>
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
    public EntityTranslator findTranslatorByClass(Class<? extends ModelEntity> srcClass) {
        if (srcClass == null) {
            throw new IllegalArgumentException("srcClass is null");
        }

        EntityTranslator translator = null;
        for (Class cls = srcClass; cls != null && translator == null; cls = cls.getSuperclass()) {
            translator = this.translators.get(cls);

            // Check interfaces, if necessary
            if (translator == null) {
                translator = this.findTranslatorByInterfaces(cls);
            }
        }

        return translator;
    }

    /**
     * Recursive implementation for fetching translators by interface for a given class.
     */
    private EntityTranslator findTranslatorByInterfaces(Class<? extends ModelEntity> srcClass) {
        EntityTranslator translator = null;

        for (Class iface : srcClass.getInterfaces()) {
            translator = this.translators.get(iface);

            if (translator == null) {
                translator = this.findTranslatorByInterfaces(iface);

                if (translator != null) {
                    break;
                }
            }
        }

        return translator;
    }

    /**
     * Fetches a translator for a specific object instance. If an appropriate translator cannot be
     * found, this method returns null.
     * <p></p>
     * Functionally, the output of this method is identical to the output of the
     * <tt>findTranslatorByClass</tt> method with the class of the object instance:
     * <pre>
     *  EntityTranslator translator = factory.findTranslatorByClass(instance.getClass());
     * </pre>
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
    public EntityTranslator findTranslatorByInstance(ModelEntity instance) {
        if (instance == null) {
            throw new IllegalArgumentException("instance is null");
        }

        return this.findTranslatorByClass(instance.getClass());
    }

    /**
     * {@inheritDoc}
     */
    public <I extends ModelEntity<I>, O extends CandlepinDTO<O>> O buildDTO(ModelEntity<I> source) {
        O output = null;

        if (source != null) {
            EntityTranslator translator = this.findTranslatorByClass(source.getClass());

            if (translator == null) {
                Class srcClass = source.getClass();
                throw new DTOException("Unable to find translator for source object class: " + srcClass);
            }

            output = (O) translator.translate(this, source);
        }

        return output;
    }

    // TODO: Add a buildDTOs method for doing bulk entity translation.

    /**
     * {@inheritDoc}
     */
    public <I extends ModelEntity<I>, O extends CandlepinDTO<O>> CandlepinQuery<O> transformQuery(
        CandlepinQuery<I> query) {
        // TODO: It would be great if we could make this method, and the CandlepinQuery more
        // generic, but type erasure makes this pretty cumbersome to do properly.

        if (query == null) {
            throw new IllegalArgumentException("query is null");
        }

        final DTOFactory factory = this;
        return query.transform(new ElementTransformer<I, O>() {
            private EntityTranslator translator;
            // This should be fine for now, but if we ever have queries that return multiple
            // entity types, this will need to be changed.

            public O transform(I source) {
                O output = null;

                if (source != null) {
                    // Look up our translator if we haven't already
                    if (this.translator == null) {
                        this.translator = factory.findTranslatorByClass(source.getClass());

                        if (this.translator == null) {
                            throw new DTOException("Unable to find translator for source object class: " +
                                source.getClass());
                        }
                    }

                    // Translate our output
                    output = (O) this.translator.translate(factory, source);
                }

                return output;
            }
        });
    }

}

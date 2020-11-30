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
import org.candlepin.util.ElementTransformer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;



/**
 * The SimpleModelTranslator class provides the basic functionality required for building DTOs from
 * model entities. The factory works by delegating the translation work to one or more
 * ObjectTranslator instances, which are registered to a given ModelTranslator instance.
 */
public class SimpleModelTranslator implements ModelTranslator {
    private static Logger log = LoggerFactory.getLogger(ModelTranslator.class);

    // output => input => translator
    protected Map<Class, Map<Class, ObjectTranslator>> translators;


    /**
     * Initializes a new ModelTranslator instance.
     */
    public SimpleModelTranslator() {
        this.translators = new HashMap<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <I, O> ObjectTranslator<I, O> registerTranslator(ObjectTranslator<I, O> translator,
        Class<I> inputClass, Class<O> outputClass) {

        if (translator == null) {
            throw new IllegalArgumentException("translator is null");
        }

        if (inputClass == null) {
            throw new IllegalArgumentException("inputClass is null");
        }

        if (outputClass == null) {
            throw new IllegalArgumentException("outputClass is null");
        }

        Map<Class, ObjectTranslator> inputMappings = this.translators.get(outputClass);
        if (inputMappings == null) {
            inputMappings = new HashMap<>();
            this.translators.put(outputClass, inputMappings);
        }

        ObjectTranslator<I, O> existing = (ObjectTranslator<I, O>) inputMappings.get(inputClass);
        inputMappings.put(inputClass, translator);

        return existing;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <I, O> ObjectTranslator<I, O> unregisterTranslator(Class<I> inputClass, Class<O> outputClass) {
        if (inputClass == null) {
            throw new IllegalArgumentException("inputClass is null");
        }

        if (outputClass == null) {
            throw new IllegalArgumentException("outputClass is null");
        }

        Map<Class, ObjectTranslator> inputMappings = this.translators.get(outputClass);
        return inputMappings != null ? (ObjectTranslator<I, O>) inputMappings.remove(inputClass) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int unregisterTranslator(ObjectTranslator translator) {
        if (translator == null) {
            throw new IllegalArgumentException("translator is null");
        }

        int mappings = 0;

        for (Map<Class, ObjectTranslator> inputMappings : this.translators.values()) {
            Iterator<ObjectTranslator> translators = inputMappings.values().iterator();
            while (translators.hasNext()) {
                ObjectTranslator existing = translators.next();

                if (translator.equals(existing)) {
                    translators.remove();
                    ++mappings;
                }
            }
        }

        return mappings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <I, O> ObjectTranslator<I, O> getTranslator(Class<I> inputClass, Class<O> outputClass) {
        if (inputClass == null) {
            throw new IllegalArgumentException("inputClass is null");
        }

        if (outputClass == null) {
            throw new IllegalArgumentException("outputClass is null");
        }

        Map<Class, ObjectTranslator> inputMappings = this.translators.get(outputClass);
        return inputMappings != null ? (ObjectTranslator<I, O>) inputMappings.get(inputClass) : null;
    }

    /**
     * Attempts to find the nearest class to the given source class within the set of provided
     * mapped classes
     *
     * @param source
     *  The source class for which to find the nearest mapped class
     *
     * @param mappedClasses
     *  The set of mapped classes to search
     *
     * @return
     *  The nearest mapped class to the given source class, or null if a class could not be found
     */
    private Class findNearestMappedClass(Class source, Set<Class> mappedClasses) {
        while (source != null) {
            if (mappedClasses.contains(source)) {
                return source;
            }

            Class iface = this.findNearestMappedInterface(source, mappedClasses);
            if (iface != null) {
                return iface;
            }

            source = source.getSuperclass();
        }

        return null;
    }

    /**
     * Attempts to find the nearest interface to the given source class within the set of provided
     * mapped classes
     *
     * @param source
     *  The source class for which to find the nearest mapped interface
     *
     * @param mappedClasses
     *  The set of mapped classes to search
     *
     * @return
     *  The nearest mapped interface to the given source class, or null if a class could not be
     *  found
     */
    private Class findNearestMappedInterface(Class source, Set<Class> mappedClasses) {
        for (Class iface : source.getInterfaces()) {
            if (mappedClasses.contains(iface)) {
                return iface;
            }

            iface = this.findNearestMappedInterface(iface, mappedClasses);
            if (iface != null) {
                return iface;
            }
        }

        return null;
    }

    /**
     * Fetches a translator for the given class map. If a translator cannot be found, this method
     * throws a translation exception
     * <p></p>
     * This method uses the following algorithm to match a given class to a mapped class:
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
     * @param inputClass
     *  The input class for which to find a translator
     *
     * @param outputClass
     *  The output class for which to find a translator
     *
     * @throws IllegalArgumentException
     *  if inputClass is null or outputClass is null
     *
     * #throws TranslationException
     *  if a translator cannot be found for the given class map
     *
     * @return
     *  a translator for the given source object
     */
    @Override
    public <I, O> ObjectTranslator<I, O> findTranslatorByClass(Class<I> inputClass, Class<O> outputClass) {
        if (inputClass == null) {
            throw new IllegalArgumentException("inputClass is null");
        }

        if (outputClass == null) {
            throw new IllegalArgumentException("outputClass is null");
        }

        ObjectTranslator<I, O> translator = null;

        // TODO: This is broken for finding nearest output. Output cannot be less specific than
        // specified; it can only get more specific.
        Class outputKey = this.findNearestMappedClass(outputClass, this.translators.keySet());
        if (outputKey != null) {
            Map<Class, ObjectTranslator> inputMappings = this.translators.get(outputKey);

            Class inputKey = this.findNearestMappedClass(inputClass, inputMappings.keySet());
            if (inputKey != null) {
                translator = (ObjectTranslator<I, O>) inputMappings.get(inputKey);
            }
        }

        if (translator == null) {
            String msg = String.format("Unable to find translator for translation: %s => %s",
                inputClass.getSimpleName(), outputClass.getSimpleName());

            throw new TranslationException(msg);
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
     *  ObjectTranslator translator = factory.findTranslatorByClass(instance.getClass());
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
    @Override
    public <I, O> ObjectTranslator<I, O> findTranslatorByInstance(I instance, Class<O> outputClass) {
        if (instance == null) {
            throw new IllegalArgumentException("instance is null");
        }

        if (outputClass == null) {
            throw new IllegalArgumentException("outputClass is null");
        }

        return this.findTranslatorByClass((Class<I>) instance.getClass(), outputClass);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <I, O> O translate(I input, Class<O> outputClass) {
        if (outputClass == null) {
            throw new IllegalArgumentException("outputClass is null");
        }

        O output = null;

        if (input != null) {
            ObjectTranslator<I, O> translator = this.findTranslatorByClass(
                (Class<I>) input.getClass(), outputClass);

            output = translator.translate(this, input);
        }

        return output;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <I, O> Function<I, O> getStreamMapper(Class<I> inputClass, Class<O> outputClass) {
        if (inputClass == null) {
            throw new IllegalArgumentException("inputClass is null");
        }

        if (outputClass == null) {
            throw new IllegalArgumentException("outputClass is null");
        }

        ObjectTranslator<I, O> translator = this.findTranslatorByClass(inputClass, outputClass);
        return (input) -> translator.translate(this, input);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <I, O> CandlepinQuery<O> translateQuery(CandlepinQuery<I> query, Class<O> outputClass) {
        // TODO: It would be great if we could make this method, and the CandlepinQuery more
        // generic, but type erasure makes this pretty cumbersome to do properly.
        if (query == null) {
            throw new IllegalArgumentException("query is null");
        }

        if (outputClass == null) {
            throw new IllegalArgumentException("outputClass is null");
        }

        return query.transform(new ElementTransformer<I, O>() {
            private ModelTranslator modelTranslator;
            private Class<O> outputClass;

            // This should be fine for now, but if we ever have queries that return multiple
            // entity types, this will need to be changed.
            private ObjectTranslator<I, O> translator;

            public ElementTransformer<I, O> init(ModelTranslator modelTranslator, Class<O> outputClass) {
                this.modelTranslator = modelTranslator;
                this.outputClass = outputClass;

                return this;
            }

            public O transform(I source) {
                O output = null;

                if (source != null) {
                    // Look up our translator if we haven't already
                    if (this.translator == null) {
                        this.translator = this.modelTranslator
                            .findTranslatorByClass((Class<I>) source.getClass(), this.outputClass);
                    }

                    // Translate our output
                    output = this.translator.translate(this.modelTranslator, source);
                }

                return output;
            }
        }.init(this, outputClass));
    }

}

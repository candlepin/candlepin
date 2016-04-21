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
package org.candlepin.resteasy;

import com.google.inject.Inject;

import java.util.Iterator;



/**
 * The IterableStreamingOutputFactory is a simple factory to support creating
 * IterableStreamingOutput instances

 * Once installed, classes can have the factory injected (or otherwise provided by Guice) to create
 * IterableStreamingOutput instances:
 * <pre>
 *  \@Inject IterableStreamingOutputFactory isoFactory;
 *  ...
 *  return Response.ok(this.isoFactory.create(iterable)).build();
 * </pre>
 */
public class IterableStreamingOutputFactory {

    private JsonProvider jsonProvider;

    /**
     * Initalizes a new IterableStreamingOutputFactory which will create IterableStreamingOutput
     * instances with the given JsonProvider.
     *
     * @param jsonProvider
     *  The JsonProvider instance to use when creating new IterableStreamingOutput objects
     *
     * @throws IllegalArgumentException
     *  if jsonProvider is null
     */
    @Inject
    public IterableStreamingOutputFactory(JsonProvider jsonProvider) {
        if (jsonProvider == null) {
            throw new IllegalArgumentException("jsonProvider is null");
        }

        this.jsonProvider = jsonProvider;
    }

    /**
     * Creates a new IterableStreamingOutput instance for the given iterable object, using the
     * specified iterator transformer.
     *
     * @param iterable
     *  An iterable object to pass to the new IterableStreamingOutput object
     *
     * @param transformer
     *  An iterator transformer to use for transforming data provided by the given iterable before
     *  it is output to the stream; may be null
     *
     * @return
     *  a new IterableStreamingOutput instance
     */
    public <T> IterableStreamingOutput<T> create(Iterable<T> iterable, IteratorTransformer<T> transformer) {
        IterableStreamingOutput<T> iso = new IterableStreamingOutput<T>(this.jsonProvider, iterable);
        iso.setTransformer(transformer);

        return iso;
    }

    /**
     * Creates a new IterableStreamingOutput instance for the given iterable object, using the
     * specified iterator transformer.
     *
     * @param iterable
     *  An iterable object to pass to the new IterableStreamingOutput object
     *
     * @return
     *  a new IterableStreamingOutput instance
     */
    public <T> IterableStreamingOutput<T> create(Iterable<T> iterable) {
        return this.create(iterable, null);
    }

    /**
     * Creates a new IterableStreamingOutput instance for the given iterator, using the specified
     * iterator transformer.
     *
     * @param iterator
     *  An iterator to pass to the new IterableStreamingOutput object
     *
     * @param transformer
     *  An iterator transformer to use for transforming data provided by the given iterator before
     *  it is output to the stream; may be null
     *
     * @return
     *  a new IterableStreamingOutput instance
     */
    public <T> IterableStreamingOutput<T> create(Iterator<T> iterator, IteratorTransformer<T> transformer) {
        IterableStreamingOutput<T> iso = new IterableStreamingOutput<T>(this.jsonProvider, iterator);
        iso.setTransformer(transformer);

        return iso;
    }

    /**
     * Creates a new IterableStreamingOutput instance for the given iterator.
     *
     * @param iterator
     *  An iterator to pass to the new IterableStreamingOutput object
     *
     * @return
     *  a new IterableStreamingOutput instance
     */
    public <T> IterableStreamingOutput<T> create(Iterator<T> iterator) {
        return this.create(iterator, null);
    }
}

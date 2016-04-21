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

import org.candlepin.model.ResultIterator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;



/**
 * The IterableStreamingOutput class provides a simple implementation for streaming the contents of
 * an iterable collection to clients. Resources can use this to reduce boilerplate code, turning a
 * 10-line block (not including required imports) into a one-line statement:
 * <pre>
 *   return Response.ok(new IterableStreamingOutput(iterable, jsonProvider)).build();
 * </pre>
 *
 * If a ResultIterator is provided, it will automatically be closed once all elements have been
 * written to the underlying output stream.
 *
 * @param <T>
 *  The type to be streamed by this output streamer
 */
public class IterableStreamingOutput<T> implements StreamingOutput {

    protected JsonProvider jsonProvider;
    protected ObjectMapper mapper;

    protected Iterator<T> iterator;
    protected IteratorTransformer<T> transformer;

    /**
     * Creates a new IterableStreamingOutput using the given iterable collection as its data source
     * to stream to the client.
     *
     * @param jsonProvider
     *  A JsonProvider instance responsible for providing JSON conversion services
     *
     * @param iterable
     *  The interable collection containing the data to stream to the client
     *
     * @throws NullPointerException
     *  if iterable is null
     */
    public IterableStreamingOutput(JsonProvider jsonProvider, Iterable<T> iterable) {
        this(jsonProvider, iterable.iterator());
    }

    /**
     * Creates a new IterableStreamingOutput using the given iterator as its data source to stream
     * to the client.
     *
     * @param jsonProvider
     *  A JsonProvider instance responsible for providing JSON conversion services
     *
     * @param iterator
     *  The iterator containing the data to stream to the client
     *
     * @throws IllegalArgumentException
     *  if either iterator or jsonProvider are null
     */
    public IterableStreamingOutput(JsonProvider jsonProvider, Iterator<T> iterator) {
        if (jsonProvider == null) {
            throw new IllegalArgumentException("jsonProvider is null");
        }

        if (iterator == null) {
            throw new IllegalArgumentException("iterator is null");
        }

        this.jsonProvider = jsonProvider;
        this.mapper = this.jsonProvider.locateMapper(Object.class, MediaType.APPLICATION_JSON_TYPE);
        this.iterator = iterator;
    }

    @Override
    public void write(OutputStream stream) throws IOException, WebApplicationException {
        JsonGenerator generator = this.mapper.getJsonFactory().createGenerator(stream);
        generator.writeStartArray();

        if (this.transformer != null) {
            while (this.iterator.hasNext()) {
                T element = this.transformer.transform(this.iterator.next());

                if (element != null) {
                    this.mapper.writeValue(generator, element);
                }
            }
        }
        else {
            while (this.iterator.hasNext()) {
                this.mapper.writeValue(generator, this.iterator.next());
            }
        }

        generator.writeEndArray();
        generator.flush();
        generator.close();

        if (this.iterator instanceof ResultIterator) {
            ((ResultIterator) this.iterator).close();
        }
    }

    /**
     * Sets the transformer to use to transform data from the backing iterator before writing it to
     * the output stream. If the provided transformer is null, no transformation will be performed
     * on the data.
     *
     * @param transformer
     *  The transformer to use for transforming data; may be null
     *
     * @return
     *  this IterableStreamingOutput instance
     */
    public IterableStreamingOutput setTransformer(IteratorTransformer<T> transformer) {
        this.transformer = transformer;
        return this;
    }

    /**
     * Retrieves the iterator transformer this object will use for transforming data before writing
     * it to the output stream. If no transformer has been set, this method returns null.
     *
     * @return
     *  the transformer to be used for transforming data, or null if a transformer has not been set
     */
    public IteratorTransformer<T> getTransformer() {
        return this.transformer;
    }
}

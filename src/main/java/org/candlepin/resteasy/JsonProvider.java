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

import org.candlepin.config.Config;
import org.candlepin.jackson.HateoasBeanPropertyFilter;

/**
 * JsonProvider
 *
 * Our own json provider for jax-rs, allowing us to configure jackson as we see fit
 * and deal with input validation.
 */
@Provider
@Produces({"application/*+json", "text/json"})
@Consumes({"application/*+json", "text/json"})
public class JsonProvider extends JacksonJsonProvider {

    public static register(boolean indentJson){
        ResteasyProviderFactory rpf = ResteasyProviderFactory.getInstance();
        JsonProvider jsonprovider = new JsonProvider(indentJson);
        rpf.addMessageBodyReader(jsonprovider);
        rpf.addMessageBodyWriter(jsonprovider);
        RegisterBuiltin.register(rpf);
    }

    @Inject
    public JsonProvider(Config config){
        this(config.indentJson());
    }

    public JsonProvider(boolean indentJson) {
        // Prefer jackson annotations, but use jaxb if no jackson.
        super(Annotations.JACKSON, Annotations.JAXB);

        ObjectMapper mapper = _mapperConfig.getDefaultMapper();
        configureHateoasObjectMapper(mapper, config);
        setMapper(mapper);
    }

    private void configureHateoasObjectMapper(ObjectMapper mapper, boolean indentJson) {
        mapper.configure(SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS, false);

        if (indentJson) {
            mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
        }

        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider = filterProvider.addFilter("ApiHateoas",
            new HateoasBeanPropertyFilter());
        filterProvider.setFailOnUnknownId(false);
        mapper.setFilters(filterProvider);

        AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
        AnnotationIntrospector secondary = new JaxbAnnotationIntrospector();
        AnnotationIntrospector pair = new AnnotationIntrospector.Pair(primary, secondary);
        mapper.setAnnotationIntrospector(pair);
    }
}

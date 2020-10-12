/*
 *  Copyright (c) 2009 - ${YEAR} Red Hat, Inc.
 *
 *  This software is licensed to you under the GNU General Public License,
 *  version 2 (GPLv2). There is NO WARRANTY for this software, express or
 *  implied, including the implied warranties of MERCHANTABILITY or FITNESS
 *  FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 *  along with this software; if not, see
 *  http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 *  Red Hat trademarks are not licensed under GPLv2. No permission is
 *  granted to use or replicate Red Hat trademarks that are incorporated
 *  in this software or its documentation.
 */

package org.candlepin.resteasy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;
import java.util.Optional;

@Component
@Provider
@Produces({"application/json", "application/*+json", "text/json"})
@Consumes({"application/json", "application/*+json", "text/json"})
public class CustomResteasyJackson2Provider extends ResteasyJackson2Provider {
    private ObjectMapper mapper;

    @Autowired
    public CustomResteasyJackson2Provider(@Qualifier("springObjectMapper") ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ObjectMapper locateMapper(Class<?> type, MediaType mediaType) {
        return Optional.ofNullable(_mapperConfig.getConfiguredMapper()).orElse(mapper);
    }
}

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
package org.candlepin.resource;

import java.util.ArrayList;

import io.swagger.annotations.Info;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.jaxrs.Reader;
import io.swagger.jaxrs.config.ReaderListener;
import io.swagger.models.ExternalDocs;
import io.swagger.models.SecurityRequirement;
import io.swagger.models.Swagger;
import io.swagger.models.auth.BasicAuthDefinition;

/**
 * Swagger Configuration. This class is scaned together with other resources
 * in this package.
 *
 * @author fnguyen
 *
 */
@SwaggerDefinition(
    info = @Info(description = "Candlepin is subscription management" +
    " server written in Java. It helps with management " +
    "of software subscriptions.", title = "Candlepin", version = "")
    )
public class CandlepinSwaggerConfig implements ReaderListener {

    public static final String BASIC_AUTH = "basic";

    @Override
    public void beforeScan(Reader reader, Swagger swagger) {
    }

    @Override
    public void afterScan(Reader reader, Swagger swagger) {
        swagger.setExternalDocs(new ExternalDocs("Project website: ", "http://candlepinproject.org/"));
        BasicAuthDefinition basic = new BasicAuthDefinition();
        basic.setDescription("Candlepin requires HTTP Basic authentication of an owner");
        swagger.addSecurityDefinition(BASIC_AUTH, basic);
        SecurityRequirement req = new SecurityRequirement();
        req.setRequirements(BASIC_AUTH, new ArrayList<String>());
        swagger.addSecurity(req);
    }
}

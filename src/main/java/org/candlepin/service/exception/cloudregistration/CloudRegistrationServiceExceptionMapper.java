/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.service.exception.cloudregistration;

import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.CandlepinException;
import org.candlepin.exceptions.NotAuthorizedException;

import org.xnap.commons.i18n.I18n;

import javax.ws.rs.core.Response;



public class CloudRegistrationServiceExceptionMapper {
    private CloudRegistrationServiceExceptionMapper() { };

    public static void map(CloudRegistrationServiceException e, String providerType, I18n i18n) {
        if (e instanceof CloudRegistrationAuthorizationException) {
            throw new NotAuthorizedException(i18n.tr(
                "Cloud provider or account details could not be resolved to an organization"));
        }
        else if (e instanceof CloudRegistrationMissingTypeException) {
            throw new BadRequestException(i18n.tr(
                "Request is missing cloud provider type (e.g. amazon, gcp, azure)"));
        }
        else if (e instanceof CloudRegistrationBadTypeException) {
            throw new IllegalArgumentException(i18n.tr(
                "Unrecognized provider type in request: {0}", providerType));
        }
        else if (e instanceof CloudRegistrationBadMetadataException) {
            throw new BadRequestException(i18n.tr(
                "Unable to retrieve organization ID with provided meta-data"));
        }
        else if (e instanceof CloudRegistrationUnknownDataException) {
            throw new CandlepinException(Response.Status.fromStatusCode(e.getStatus()),
                i18n.tr("Unexpected data error from Cloud Registration Service: {0}", e.getMessage()));
        }
        else {
            throw new CandlepinException(Response.Status.fromStatusCode(e.getStatus()),
                i18n.tr("Unexpected error from Cloud Registration Service: {0}", e.getMessage()));
        }
    }
}

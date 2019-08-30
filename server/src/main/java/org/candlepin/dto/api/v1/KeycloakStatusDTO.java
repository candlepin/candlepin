/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

/**
 * Keycloak parameters needed by a client in order to use OIDC-based authentication.
 *
 * This lets a client retrieve the relevant configuration from Candlepin, which are then used to exchange
 * offline token for access token (or any other flow supported by Keycloak and implemented on the client).
 */
public class KeycloakStatusDTO extends StatusDTO {

    private String keycloakRealm;
    private String keycloakAuthUrl;
    private String keycloakResource;


    public String getKeycloakResource() {
        return keycloakResource;
    }

    public KeycloakStatusDTO setKeycloakResource(String keycloakResource) {
        this.keycloakResource = keycloakResource;
        return this;
    }

    public String getKeycloakAuthUrl() {
        return keycloakAuthUrl;
    }

    public KeycloakStatusDTO setKeycloakAuthUrl(String keycloakAuthUrl) {
        this.keycloakAuthUrl = keycloakAuthUrl;
        return this;
    }

    public String getKeycloakRealm() {
        return keycloakRealm;
    }

    public KeycloakStatusDTO setKeycloakRealm(String keycloakRealm) {
        this.keycloakRealm = keycloakRealm;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof KeycloakStatusDTO && super.equals(obj)) {
            KeycloakStatusDTO that = (KeycloakStatusDTO) obj;
            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getKeycloakRealm(), that.getKeycloakRealm())
                .append(this.getKeycloakAuthUrl(), that.getKeycloakAuthUrl())
                .append(this.getKeycloakResource(), that.getKeycloakResource());

            return builder.isEquals();
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(7, 17)
            .append(super.hashCode())
            .append(this.getKeycloakRealm())
            .append(this.getKeycloakAuthUrl())
            .append(this.getKeycloakResource());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    public KeycloakStatusDTO populate(KeycloakStatusDTO source) {
        super.populate(source);

        this.setKeycloakAuthUrl(source.getKeycloakAuthUrl());
        this.setKeycloakRealm(source.getKeycloakRealm());
        this.setKeycloakResource(source.getKeycloakResource());

        return this;
    }
}

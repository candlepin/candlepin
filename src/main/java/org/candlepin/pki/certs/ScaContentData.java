/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.pki.certs;

import java.util.List;

/**
 * Immutable repository metadata after SCA content filtering
 *
 * @param id repository identifier
 * @param type repository type
 * @param name repository name
 * @param label repository label
 * @param vendor repository vendor
 * @param path resolved and encoded repository path
 * @param gpgUrl repository GPG URL
 * @param enabled explicit disabled flag, or null for enabled content
 * @param metadataExpiration optional metadata expiration
 * @param requiredTags optional required tags
 * @param arches repository architectures
 */
record ScaContentData(String id, String type, String name, String label, String vendor, String path,
    String gpgUrl, Boolean enabled, Long metadataExpiration, List<String> requiredTags, List<String> arches) {

    ScaContentData {
        requiredTags = requiredTags == null ? null : List.copyOf(requiredTags);
        arches = List.copyOf(arches);
    }
}

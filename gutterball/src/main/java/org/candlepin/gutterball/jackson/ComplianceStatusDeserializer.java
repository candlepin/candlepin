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

package org.candlepin.gutterball.jackson;

import org.candlepin.gutterball.model.snapshot.NonCompliantProductReference;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Converts a data map into a Set of the maps String keys.
 */
public class ComplianceStatusDeserializer extends JsonDeserializer<ComplianceStatus> {

    @Override
    public ComplianceStatus deserialize(JsonParser parser, DeserializationContext context)
        throws IOException, JsonProcessingException {

        JsonNode node = parser.readValueAsTree();

        ComplianceStatus status = new ComplianceStatus();

        status.setDate(node.get("date").asDate());
        status.setCompliantUntil(node.get("compliantUntil").asDate());
        status.setStatus(node.get("status").asText());



/*
    "status": {
        "date": 1411742028603,
        "compliantUntil": null,
        "nonCompliantProducts": [],
        "compliantProducts": {
            "37060": [{
                "id": "ff80808148b1f8040148b261cdf80032",
                "consumer": null,
                "pool": {
                    "id": "ff8080814836a535014836a5d6f80cc7",
                    "productId": "awesomeos-virt-datacenter",
                    "productName": "Awesome OS Virtual Datacenter",
                    "href": "/pools/ff8080814836a535014836a5d6f80cc7"
                },
                "certificates": [],
                "quantity": 1,
                "startDate": 1409616000000,
                "endDate": 1441152000000,
                "href": "/entitlements/ff80808148b1f8040148b261cdf80032",
                "created": 1411742027256,
                "updated": 1411742027369
            }]
        },
        "partiallyCompliantProducts": {},
        "partialStacks": {},
        "reasons": [],
        "compliant": true,
        "status": "valid"
    }
*/

        return status;
    }
}

/*
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    @JsonIgnore
    private String id;

    @XmlTransient
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compliance_snap_id", nullable = false)
    @NotNull
    private Compliance complianceSnapshot;

    @XmlElement
    @Column(nullable = false, unique = false)
    private Date date;

    @XmlElement
    @Column(nullable = true, unique = false, name = "compliant_until")
    private Date compliantUntil;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String status;

    @OneToMany(mappedBy = "complianceStatus", targetEntity = ComplianceReason.class, fetch = FetchType.LAZY)
    @BatchSize(size = 25)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    private Set<ComplianceReason> reasons;

    @OneToMany(mappedBy = "complianceStatus",
        targetEntity = CompliantProductReference.class, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @JsonDeserialize(converter = CompliantProductReferenceDeserializer.class)
    private Set<CompliantProductReference> compliantProducts;

    @OneToMany(mappedBy = "complianceStatus",
        targetEntity = PartiallyCompliantProductReference.class, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @JsonDeserialize(converter = PartiallyCompliantProductReferenceDeserializer.class)
    private Set<PartiallyCompliantProductReference> partiallyCompliantProducts;

    @OneToMany(mappedBy = "complianceStatus",
        targetEntity = NonCompliantProductReference.class, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @JsonDeserialize(converter = NonCompliantProductReferenceDeserializer.class)
    private Set<NonCompliantProductReference> nonCompliantProducts;

    @ElementCollection(fetch = FetchType.LAZY)
    @BatchSize(size = 25)
    @CollectionTable(name = "gb_partialstack_snap", joinColumns = @JoinColumn(name = "comp_status_id"))
    @Column(name = "stacking_id")
    @JsonDeserialize(converter = MapToKeysConverter.class)
    private Set<String> partialStacks;

    @Column(name = "management_enabled")
    private Boolean managementEnabled;
*/
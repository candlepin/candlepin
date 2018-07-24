/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.candlepin.dto.CandlepinDTO;
import org.candlepin.util.SetView;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * DTO that encapsulates the results from
 * {@link org.candlepin.resource.HypervisorResource#hypervisorUpdateAsync} and
 * {@link org.candlepin.resource.HypervisorResource#hypervisorUpdate}:
 *
 * <pre>
 *     created: List of {@link HypervisorConsumerDTO}s (consumers) that have just been created.
 *     updated: List of {@link HypervisorConsumerDTO}s (consumers) that have had their guest IDs updated.
 *     unchanged: List of {@link HypervisorConsumerDTO}s (consumers) that have not been changed.
 *     failed: a list of strings formated as '{host_virt_id}: Error message'.
 * </pre>
 */
public class HypervisorUpdateResultDTO extends CandlepinDTO<HypervisorUpdateResultDTO> {
    // TODO Once we have removed the need to store serialized java objects as the resultData of JobStatus
    // this class should be removed and replaced.
    private static final long serialVersionUID = -42133742L;

    /**
     * Serialization utility class for serializing null Sets as empty HashSets.
     */
    private static class NullToEmptySetSerializer extends JsonSerializer<Set> {

        @Override
        public void serialize(Set value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (value == null) {
                provider.defaultSerializeValue(new HashSet<>(), gen);
            }
            else {
                provider.defaultSerializeValue(value, gen);
            }
        }
    }

    private Set<HypervisorConsumerDTO> created;
    private Set<HypervisorConsumerDTO> updated;
    private Set<HypervisorConsumerDTO> unchanged;
    private Set<String> failed;

    /**
     * Initializes a new HypervisorUpdateResultDTO instance with empty values.
     */
    public HypervisorUpdateResultDTO() {
        // Intentionally left blank
    }

    /**
     * Initializes a new HypervisorUpdateResultDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public HypervisorUpdateResultDTO(HypervisorUpdateResultDTO source) {
        super(source);
    }

    public boolean wasCreated(HypervisorConsumerDTO hypervisorConsumerDTO) {
        return this.created != null && created.contains(hypervisorConsumerDTO);
    }

    /**
     * Utility method to validate HypervisorConsumerDTO input
     */
    private boolean isNullOrIncomplete(HypervisorConsumerDTO hypervisorConsumerDTO) {
        return hypervisorConsumerDTO == null ||
            hypervisorConsumerDTO.getUuid() == null || hypervisorConsumerDTO.getUuid().isEmpty() ||
            hypervisorConsumerDTO.getName() == null || hypervisorConsumerDTO.getName().isEmpty() ||
            hypervisorConsumerDTO.getOwner() == null || hypervisorConsumerDTO.getOwner().getKey() == null ||
            hypervisorConsumerDTO.getOwner().getKey().isEmpty();
    }

    /**
     * Adds a created consumer to this HypervisorUpdateResultDTO object.
     *
     * @param hypervisorConsumerDTO the created consumer to add to this HypervisorUpdateResultDTO object.
     *
     * @return true if the consumer was added, false otherwise.
     *
     * @throws IllegalArgumentException if the hypervisorConsumerDTO is null or incomplete
     *
     */
    public boolean addCreated(HypervisorConsumerDTO hypervisorConsumerDTO) {
        if (isNullOrIncomplete(hypervisorConsumerDTO)) {
            throw new IllegalArgumentException("consumer is null or incomplete");
        }

        if (this.created == null) {
            this.created = new HashSet<>();
        }

        return this.created.add(hypervisorConsumerDTO);
    }

    /**
     * Adds an updated consumer to this HypervisorUpdateResultDTO object.
     *
     * @param hypervisorConsumerDTO the updated consumer to add to this HypervisorUpdateResultDTO object.
     *
     * @return true if the consumer was added, false otherwise.
     *
     * @throws IllegalArgumentException if the hypervisorConsumerDTO is null or incomplete
     *
     */
    public boolean addUpdated(HypervisorConsumerDTO hypervisorConsumerDTO) {
        if (isNullOrIncomplete(hypervisorConsumerDTO)) {
            throw new IllegalArgumentException("consumer is null or incomplete");
        }

        if (this.updated == null) {
            this.updated = new HashSet<>();
        }

        return this.updated.add(hypervisorConsumerDTO);
    }

    /**
     * Adds an unchanged consumer to this HypervisorUpdateResultDTO object.
     *
     * @param hypervisorConsumerDTO the unchanged consumer to add to this HypervisorUpdateResultDTO object.
     *
     * @return true if the consumer was added, false otherwise.
     *
     * @throws IllegalArgumentException if the hypervisorConsumerDTO is null or incomplete
     *
     */
    public boolean addUnchanged(HypervisorConsumerDTO hypervisorConsumerDTO) {
        if (isNullOrIncomplete(hypervisorConsumerDTO)) {
            throw new IllegalArgumentException("consumer is null or incomplete");
        }

        if (this.unchanged == null) {
            this.unchanged = new HashSet<>();
        }

        return this.unchanged.add(hypervisorConsumerDTO);
    }

    /**
     * Adds a consumer update failure message to this HypervisorUpdateResultDTO object.
     *
     * @param hostVirtId the id of the consumer that failed to update,
     * to add to this HypervisorUpdateResultDTO object.
     *
     * @param errorMessage the error message of the failure to update,
     * to add to this HypervisorUpdateResultDTO object.
     *
     * @return true if the failure message was added, false otherwise.
     *
     * @throws IllegalArgumentException if the hostVirtId is null
     *
     */
    public boolean addFailed(String hostVirtId, String errorMessage) {
        if (hostVirtId == null) {
            throw new IllegalArgumentException("update failure consumer id is null");
        }

        if (this.failed == null) {
            this.failed = new HashSet<>();
        }

        String error = errorMessage == null ? "" : errorMessage;
        return this.failed.add(hostVirtId + ": " + error);
    }

    /**
     * Retrieves the list of created hypervisor consumers of this HypervisorUpdateResultDTO object.
     *
     * @return the created consumers of this result, or null if it has not yet been defined.
     */
    @JsonSerialize(nullsUsing = NullToEmptySetSerializer.class)
    public Collection<HypervisorConsumerDTO> getCreated() {
        return this.created != null ? new SetView<>(this.created) : null;
    }

    /**
     * Adds the collection of created consumers to this HypervisorUpdateResultDTO.
     *
     * @param created
     *  A collection of created consumers to attach to this DTO, or null to clear the existing ones
     *
     * @return
     *  A reference to this DTO
     */
    public HypervisorUpdateResultDTO setCreated(Collection<HypervisorConsumerDTO> created) {
        if (created != null) {
            if (this.created == null) {
                this.created = new HashSet<>();
            }
            else {
                this.created.clear();
            }

            for (HypervisorConsumerDTO dto : created) {
                if (dto.getUuid() == null) {
                    throw new IllegalArgumentException(
                        "collection contains null or incomplete consumer objects");
                }
            }

            this.created.addAll(created);
        }
        else {
            this.created = null;
        }
        return this;
    }

    /**
     * Retrieves the list of updated hypervisor consumers of this HypervisorUpdateResultDTO object.
     *
     * @return the updated consumers of this result, or null if it has not yet been defined.
     */
    @JsonSerialize(nullsUsing = NullToEmptySetSerializer.class)
    public Collection<HypervisorConsumerDTO> getUpdated() {
        return this.updated != null ? new SetView<>(this.updated) : null;
    }

    /**
     * Adds the collection of updated consumers to this HypervisorUpdateResultDTO.
     *
     * @param updated
     *  A collection of updated consumers to attach to this DTO, or null to clear the existing ones
     *
     * @return
     *  A reference to this DTO
     */
    public HypervisorUpdateResultDTO setUpdated(Collection<HypervisorConsumerDTO> updated) {
        if (updated != null) {
            if (this.updated == null) {
                this.updated = new HashSet<>();
            }
            else {
                this.updated.clear();
            }

            for (HypervisorConsumerDTO dto : updated) {
                if (dto.getUuid() == null) {
                    throw new IllegalArgumentException(
                        "collection contains null or incomplete consumer objects");
                }
            }

            this.updated.addAll(updated);
        }
        else {
            this.updated = null;
        }
        return this;
    }

    /**
     * Retrieves the list of unchanged hypervisor consumers of this HypervisorUpdateResultDTO object.
     *
     * @return the unchanged consumers of this result, or null if it has not yet been defined.
     */
    @JsonSerialize(nullsUsing = NullToEmptySetSerializer.class)
    public Collection<HypervisorConsumerDTO> getUnchanged() {
        return this.unchanged != null ? new SetView<>(this.unchanged) : null;
    }

    /**
     * Adds the collection of unchanged consumers to this HypervisorUpdateResultDTO.
     *
     * @param unchanged
     *  A collection of unchanged consumers to attach to this DTO, or null to clear the existing ones
     *
     * @return
     *  A reference to this DTO
     */
    public HypervisorUpdateResultDTO setUnchanged(Collection<HypervisorConsumerDTO> unchanged) {
        if (unchanged != null) {
            if (this.unchanged == null) {
                this.unchanged = new HashSet<>();
            }
            else {
                this.unchanged.clear();
            }

            for (HypervisorConsumerDTO dto : unchanged) {
                if (dto.getUuid() == null) {
                    throw new IllegalArgumentException(
                        "collection contains null or incomplete consumer objects");
                }
            }

            this.unchanged.addAll(unchanged);
        }
        else {
            this.unchanged = null;
        }
        return this;
    }

    /**
     * Retrieves the list of failed consumer updates of this HypervisorUpdateResultDTO object.
     *
     * @return the failed consumer updates of this result, or null if it has not yet been defined.
     */
    @JsonSerialize(nullsUsing = NullToEmptySetSerializer.class)
    public Collection<String> getFailedUpdate() {
        return this.failed != null ? new SetView<>(this.failed) : null;
    }

    /**
     * Adds the collection of failed updates of consumers to this HypervisorUpdateResultDTO.
     *
     * @param failedUpdate
     *  A collection of failed updates of consumers to attach to this DTO, or null to clear the existing ones
     *
     * @return
     *  A reference to this DTO
     */
    public HypervisorUpdateResultDTO setFailedUpdate(Collection<String> failedUpdate) {
        if (failedUpdate != null) {
            if (this.failed == null) {
                this.failed = new HashSet<>();
            }
            else {
                this.failed.clear();
            }

            for (String update : failedUpdate) {
                if (update == null) {
                    throw new IllegalArgumentException(
                        "collection contains null failure updates");
                }
            }

            this.failed.addAll(failedUpdate);
        }
        else {
            this.failed = null;
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format(
            "Created: %s, Updated: %s, Unchanged: %s, Failed: %s",
            this.getCreated() != null ? this.getCreated().size() : 0,
            this.getUpdated() != null ? this.getUpdated().size() : 0,
            this.getUnchanged() != null ? this.getUnchanged().size() : 0,
            this.getFailedUpdate() != null ? this.getFailedUpdate().size() : 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof HypervisorUpdateResultDTO) {
            HypervisorUpdateResultDTO that = (HypervisorUpdateResultDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getCreated(), that.getCreated())
                .append(this.getUpdated(), that.getUpdated())
                .append(this.getUnchanged(), that.getUnchanged())
                .append(this.getFailedUpdate(), that.getFailedUpdate());

            return builder.isEquals();
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(this.getCreated())
            .append(this.getUpdated())
            .append(this.getUnchanged())
            .append(this.getFailedUpdate());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HypervisorUpdateResultDTO clone() {
        HypervisorUpdateResultDTO copy = super.clone();

        copy.setCreated(this.getCreated());
        copy.setUpdated(this.getUpdated());
        copy.setUnchanged(this.getUnchanged());
        copy.setFailedUpdate(this.getFailedUpdate());

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HypervisorUpdateResultDTO populate(HypervisorUpdateResultDTO source) {
        super.populate(source);

        this.setCreated(source.getCreated());
        this.setUpdated(source.getUpdated());
        this.setUnchanged(source.getUnchanged());
        this.setFailedUpdate(source.getFailedUpdate());

        return this;
    }
}

/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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

import org.candlepin.dto.TimestampedCandlepinDTO;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;



/**
 * A DTO representation of the ImportRecord entity
 */
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ImportRecordDTO extends TimestampedCandlepinDTO<ImportRecordDTO> {

    public static final long serialVersionUID = 1L;

    private String id;
    private String status;
    private String statusMessage;
    private String filename;
    private String generatedBy;
    private Date generatedDate;
    private ImportUpstreamConsumerDTO upstreamConsumer;


    /**
     * Initializes a new ImportRecordDTO instance with null values.
     */
    public ImportRecordDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new ImportRecordDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public ImportRecordDTO(ImportRecordDTO source) {
        super(source);
    }

    /**
     * Fetches this import record's ID. If the ID has not yet been set, this method returns null.
     *
     * @return
     *  The ID of this import record, or null if the ID has not been set.
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets or clears the ID of this import record.
     *
     * @param id
     *  The ID to set for this import record, or null to clear any existing ID
     *
     * @return
     *  A reference to this DTO
     */
    public ImportRecordDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Fetches this status of this import record. If the status has not yet been set, this method
     * returns null.
     *
     * @return
     *  The status of this import record, or null if the status has not been set.
     */
    public String getStatus() {
        return this.status;
    }

    /**
     * Sets or clears the status of this import record.
     *
     * @param status
     *  The status to set for this import record, or null to clear any existing status
     *
     * @return
     *  A reference to this DTO
     */
    public ImportRecordDTO setStatus(String status) {
        this.status = status;
        return this;
    }

    /**
     * Fetches this status message of this import record. If the message has not yet been set, this
     * method returns null.
     *
     * @return
     *  The status message of this import record, or null if the message has not been set.
     */
    public String getStatusMessage() {
        return this.statusMessage;
    }

    /**
     * Sets or clears the status message of this import record.
     *
     * @param statusMessage
     *  The status message to set for this import record, or null to clear any existing message
     *
     * @return
     *  A reference to this DTO
     */
    public ImportRecordDTO setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
        return this;
    }

    /**
     * Fetches this name of the imported file for this import record. If the file name has not yet
     * been set, this method returns null.
     *
     * @return
     *  The file name of this import record, or null if the file name has not been set.
     */
    public String getFileName() {
        return this.filename;
    }

    /**
     * Sets or clears the name of the imported file for this import record.
     *
     * @param filename
     *  The file name to set for this import record, or null to clear any existing file name
     *
     * @return
     *  A reference to this DTO
     */
    public ImportRecordDTO setFileName(String filename) {
        this.filename = filename;
        return this;
    }

    /**
     * Fetches the source that generated this import record. If the source has not yet been set,
     * this method returns null.
     *
     * @return
     *  The source that generated this import record, or null if the source has not been set.
     */
    public String getGeneratedBy() {
        return this.generatedBy;
    }

    /**
     * Sets or clears the source that generated this import record.
     *
     * @param source
     *  The source to set for this import record, or null to clear any existing source
     *
     * @return
     *  A reference to this DTO
     */
    public ImportRecordDTO setGeneratedBy(String source) {
        this.generatedBy = source;
        return this;
    }

    /**
     * Fetches this date this import record was generated. If the date has not yet been set, this
     * method returns null.
     *
     * @return
     *  The date this import record was generated, or null if the generation date has not been set.
     */
    public Date getGeneratedDate() {
        return this.generatedDate;
    }

    /**
     * Sets or clears the date this import record was generated.
     *
     * @param date
     *  The generated date to set for this import record, or null to clear any existing date
     *
     * @return
     *  A reference to this DTO
     */
    public ImportRecordDTO setGeneratedDate(Date date) {
        this.generatedDate = date;
        return this;
    }

    /**
     * Fetches this upstream consumer for this import record. If the consumer has not yet been set,
     * this method returns null.
     *
     * @return
     *  The upstream consumer for this import record, or null if the consumer has not been set.
     */
    public ImportUpstreamConsumerDTO getUpstreamConsumer() {
        return this.upstreamConsumer;
    }

    /**
     * Sets or clears the upstream consumer for this import record.
     *
     * @param upstreamConsumer
     *  The upstream consumer to set for this import record, or null to clear any existing
     *  upstreamConsumer
     *
     * @return
     *  A reference to this DTO
     */
    public ImportRecordDTO setUpstreamConsumer(ImportUpstreamConsumerDTO upstreamConsumer) {
        this.upstreamConsumer = upstreamConsumer;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("ImportRecordDTO [id: %s, status: %s]", this.getId(), this.getStatus());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof ImportRecordDTO && super.equals(obj)) {
            ImportRecordDTO that = (ImportRecordDTO) obj;

            ImportUpstreamConsumerDTO thisUpstreamConsumer = this.getUpstreamConsumer();
            ImportUpstreamConsumerDTO thatUpstreamConsumer = that.getUpstreamConsumer();

            String thisUCID = thisUpstreamConsumer != null ? thisUpstreamConsumer.getId() : null;
            String thatUCID = thatUpstreamConsumer != null ? thatUpstreamConsumer.getId() : null;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getStatus(), that.getStatus())
                .append(this.getStatusMessage(), that.getStatusMessage())
                .append(this.getFileName(), that.getFileName())
                .append(this.getGeneratedBy(), that.getGeneratedBy())
                .append(this.getGeneratedDate(), that.getGeneratedDate())
                .append(thisUCID, thatUCID);

            return builder.isEquals();
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        ImportUpstreamConsumerDTO thisUpstreamConsumer = this.getUpstreamConsumer();

        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.getId())
            .append(this.getStatus())
            .append(this.getStatusMessage())
            .append(this.getFileName())
            .append(this.getGeneratedBy())
            .append(this.getGeneratedDate())
            .append(thisUpstreamConsumer != null ? thisUpstreamConsumer.getId() : null);

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImportRecordDTO clone() {
        ImportRecordDTO copy = super.clone();

        Date generatedDate = this.getGeneratedDate();
        copy.setGeneratedDate(generatedDate != null ? (Date) generatedDate.clone() : null);

        ImportUpstreamConsumerDTO upstreamConsumer = this.getUpstreamConsumer();
        copy.setUpstreamConsumer(upstreamConsumer != null ? upstreamConsumer.clone() : null);

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImportRecordDTO populate(ImportRecordDTO source) {
        super.populate(source);

        this.setId(source.getId());
        this.setStatus(source.getStatus());
        this.setStatusMessage(source.getStatusMessage());
        this.setFileName(source.getFileName());
        this.setGeneratedBy(source.getGeneratedBy());
        this.setGeneratedDate(source.getGeneratedDate());
        this.setUpstreamConsumer(source.getUpstreamConsumer());

        return this;
    }
}

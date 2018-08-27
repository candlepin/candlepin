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

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.pinsetter.core.model.JobStatus;

/**
 * The JobStatusTranslator provides translation from JobStatus model objects to JobStatusDTOs
 */
public class JobStatusTranslator extends TimestampedEntityTranslator<JobStatus, JobStatusDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public JobStatusDTO translate(JobStatus source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JobStatusDTO translate(ModelTranslator translator, JobStatus source) {
        return source != null ? this.populate(translator, source, new JobStatusDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JobStatusDTO populate(JobStatus source, JobStatusDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JobStatusDTO populate(ModelTranslator modelTranslator, JobStatus source, JobStatusDTO dest) {

        dest = super.populate(modelTranslator, source, dest);

        dest.setId(source.getId())
            .setCorrelationId(source.getCorrelationId())
            .setGroup(source.getGroup())
            .setPrincipalName(source.getPrincipalName())
            .setOwnerId(source.getOwnerId())
            .setResult(source.getResult())
            .setResultData(source.getResultData())
            .setStartTime(source.getStartTime())
            .setFinishTime(source.getFinishTime())
            .setState(source.getState() != null ? source.getState().name() : null)
            .setTargetId(source.getTargetId())
            .setTargetType(source.getTargetType())
            .setDone(source.isDone());

        return dest;
    }
}

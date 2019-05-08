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

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatus.JobState;



/**
 * The AsyncJobStatusTranslator provides translation from AsyncJobStatus model objects to
 * AsyncJobStatusDTOs
 */
public class AsyncJobStatusTranslator extends TimestampedEntityTranslator<AsyncJobStatus, AsyncJobStatusDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncJobStatusDTO translate(AsyncJobStatus source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncJobStatusDTO translate(ModelTranslator translator, AsyncJobStatus source) {
        return source != null ? this.populate(translator, source, new AsyncJobStatusDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncJobStatusDTO populate(AsyncJobStatus source, AsyncJobStatusDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncJobStatusDTO populate(ModelTranslator translator, AsyncJobStatus source,
        AsyncJobStatusDTO destination) {

        destination = super.populate(translator, source, destination);

        destination.setId(source.getId());
        destination.setName(source.getName());
        destination.setGroup(source.getGroup());
        destination.setOrigin(source.getOrigin());
        destination.setExecutor(source.getExecutor());
        destination.setPrincipal(source.getPrincipal());
        destination.setStartTime(source.getStartTime());
        destination.setEndTime(source.getEndTime());
        destination.setAttempts(source.getAttempts());
        destination.setMaxAttempts(source.getMaxAttempts());
        destination.setResult(source.getJobResult());

        JobState state = source.getState();
        destination.setState(state != null ? state.name() : null);

        JobState pstate = source.getPreviousState();
        destination.setPreviousState(pstate != null ? pstate.name() : null);

        return destination;
    }

}

package org.candlepin.gutterball.curator.jpa;

import org.candlepin.gutterball.model.jpa.ComplianceSnapshot;

import com.google.inject.Inject;

public class ComplianceSnapshotCurator extends BaseCurator<ComplianceSnapshot> {

    @Inject
    public ComplianceSnapshotCurator() {
        super(ComplianceSnapshot.class);
    }

}

/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.pinsetter.tasks;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.wideplay.warp.persist.PersistenceService;
import com.wideplay.warp.persist.UnitOfWork;
import java.util.List;
import org.fedoraproject.candlepin.CandlepinCommonTestingModule;
import org.fedoraproject.candlepin.CandlepinNonServletEnvironmentTestingModule;
import org.fedoraproject.candlepin.model.ImportRecord;
import org.fedoraproject.candlepin.model.ImportRecordCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class ImportRecordJobTest {

    private OwnerCurator ownerCurator;
    private ImportRecordCurator importRecordCurator;
    private ImportRecordJob job;

    @Before
    public void init() {
        CandlepinCommonTestingModule testingModule = new CandlepinCommonTestingModule();
        Injector injector = Guice.createInjector(
                testingModule,
                new CandlepinNonServletEnvironmentTestingModule(),
                PersistenceService.usingJpa()
                    .across(UnitOfWork.REQUEST)
                    .buildModule()
        );

        this.ownerCurator = injector.getInstance(OwnerCurator.class);
        this.importRecordCurator = injector.getInstance(ImportRecordCurator.class);
        this.job = injector.getInstance(ImportRecordJob.class);
    }

    @Test
    public void noRecords() throws Exception {
        Owner owner = new Owner("owner");
        Owner another = new Owner("another_owner");
        ownerCurator.create(owner);
        ownerCurator.create(another);

        // just make sure that running this on owners with no records
        // doesn't blow up
        this.job.execute(null);
    }

    @Test
    public void singleOwner() throws Exception {
        Owner owner = new Owner("owner");
        ownerCurator.create(owner);
        
        for (int i = 0; i < 13; i++) {
            ImportRecord record = new ImportRecord(owner);
            record.recordStatus(ImportRecord.Status.SUCCESS, "great!");
            
            this.importRecordCurator.create(record);
        }
        
        this.job.execute(null);

        List<ImportRecord> records = this.importRecordCurator.findRecords(owner);

        Assert.assertEquals(10, records.size());
    }

    @Test
    public void lessThanThreshold() throws Exception {
        Owner owner = new Owner("owner");
        ownerCurator.create(owner);

        for (int i = 0; i < 7; i++) {
            ImportRecord record = new ImportRecord(owner);
            record.recordStatus(ImportRecord.Status.SUCCESS, "great!");

            this.importRecordCurator.create(record);
        }

        this.job.execute(null);

        List<ImportRecord> records = this.importRecordCurator.findRecords(owner);

        Assert.assertEquals(7, records.size());
    }

    @Test
    public void multipleOwners() throws Exception {
        Owner owner1 = new Owner("owner1");
        Owner owner2 = new Owner("owner2");
        ownerCurator.create(owner1);
        ownerCurator.create(owner2);

        for (int i = 0; i < 23; i++) {
            ImportRecord record = new ImportRecord(owner1);
            record.recordStatus(ImportRecord.Status.FAILURE, "Bad bad");

            this.importRecordCurator.create(record);
        }

        for (int i = 0; i < 4; i++) {
            ImportRecord record = new ImportRecord(owner2);
            record.recordStatus(ImportRecord.Status.SUCCESS, "Excellent");

            this.importRecordCurator.create(record);
        }

        this.job.execute(null);

        Assert.assertEquals(10, importRecordCurator.findRecords(owner1).size());
        Assert.assertEquals(4, importRecordCurator.findRecords(owner2).size());
    }

}

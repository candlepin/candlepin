package org.candlepin.model;

import java.sql.Blob;
import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import org.candlepin.sync.file.ManifestFileType;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(ManifestFileRecord.class)
public abstract class ManifestFileRecord_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<ManifestFileRecord, String> filename;
	public static volatile SingularAttribute<ManifestFileRecord, String> targetId;
	public static volatile SingularAttribute<ManifestFileRecord, Blob> fileData;
	public static volatile SingularAttribute<ManifestFileRecord, String> principalName;
	public static volatile SingularAttribute<ManifestFileRecord, String> id;
	public static volatile SingularAttribute<ManifestFileRecord, ManifestFileType> type;

	public static final String FILENAME = "filename";
	public static final String TARGET_ID = "targetId";
	public static final String FILE_DATA = "fileData";
	public static final String PRINCIPAL_NAME = "principalName";
	public static final String ID = "id";
	public static final String TYPE = "type";

}


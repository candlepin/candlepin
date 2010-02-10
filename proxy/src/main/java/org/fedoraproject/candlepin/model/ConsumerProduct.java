package org.fedoraproject.candlepin.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.hibernate.annotations.ForeignKey;

/**
 * ConsumerProducts store the products which a consumer may be consumng products which they are not entitled to.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_consumer_products")
@SequenceGenerator(name="seq_consumer_products", sequenceName="seq_consumer_products", allocationSize=1)
public class ConsumerProduct implements Persisted {
    
    // TODO: Don't know if this is a good idea, technically the consumer +
    // product oid should be the key.
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="seq_consumer_products")
    private Long id;
    
    @ManyToOne
    @ForeignKey(name = "fk_consumer_product_owner")
    private Consumer consumer;    
    
    @Column(nullable=false)
    private String productOID ;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    public String getProductOID() {
        return productOID;
    }

    public void setProductOID(String productOID) {
        this.productOID = productOID;
    }
    
}

package org.fedoraproject.candlepin.model;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name = "cp_rules")
@SequenceGenerator(name="seq_rules", sequenceName="seq_rules", allocationSize=1)
@Embeddable
public class Rules implements Persisted {
    
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="seq_rules")
    private Long id;

    @Lob
    @Column(name = "rules_blob")
    private String rules;
    
    public Rules(String rulesBlob) {
        this.rules = rulesBlob;
    }
    
    public Rules() {
    }
    
    /**
     * @return rules blob
     */
    public String getRules() {
        return rules;
    }
    
    
    @Override
    public Serializable getId() {
        // TODO Auto-generated method stub
        return this.id;
    }

}

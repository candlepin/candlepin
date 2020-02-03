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
package org.candlepin.hibernate;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import org.hibernate.annotations.Type;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Id;
import javax.persistence.Persistence;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Test for ResultDataUserType
 */
public class ResultDataUserTypeTest {
    private EntityManagerFactory emf;
    private EntityManager em;

    /**
     * Silly class to serialize in different ways to test ResultDataUserType
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.PROPERTY)
    public static class JsonThing implements Serializable {
        private String name;
        private Date created;
        private int age;
        private boolean approved;
        private List<String> favoriteAnimals;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Date getCreated() {
            return created;
        }

        public void setCreated(Date created) {
            this.created = created;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public boolean isApproved() {
            return approved;
        }

        public void setApproved(boolean approved) {
            this.approved = approved;
        }

        public List<String> getFavoriteAnimals() {
            return favoriteAnimals;
        }

        public void setFavoriteAnimals(List<String> favoriteAnimals) {
            this.favoriteAnimals = favoriteAnimals;
        }
    }

    /**
     * Entity that will represent a table with a bytea[] column meant to hold various objects
     */
    @Entity
    @Table(name = "ResultData")
    public static class ResultData {
        @Id
        private int id;

        // VARBINARY is like bytea in PostgreSQL. See https://stackoverflow.com/a/9693743/6124862
        @Column(columnDefinition = "VARBINARY(1000000)")
        @Type(type = "org.candlepin.hibernate.ResultDataUserType")
        private JsonThing resultData;

        @Column(columnDefinition = "VARBINARY(1000000)")
        @Type(type = "org.candlepin.hibernate.ResultDataUserType")
        private Object untypedData;

        @Column(columnDefinition = "VARBINARY(1000000)")
        @Type(type = "org.candlepin.hibernate.ResultDataUserType")
        private Serializable unmappableData;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public JsonThing getResultData() {
            return resultData;
        }

        public void setResultData(JsonThing resultData) {
            this.resultData = resultData;
        }

        public Object getUntypedData() {
            return untypedData;
        }

        public void setUntypedData(Object untypedData) {
            this.untypedData = untypedData;
        }

        public Serializable getUnmappableData() {
            return unmappableData;
        }

        public void setUnmappableData(Serializable unmappableData) {
            this.unmappableData = unmappableData;
        }
    }

    @Before
    public void setUp() {
        emf =  Persistence.createEntityManagerFactory("testingUserType");
        em = emf.createEntityManager();
    }

    @After
    public void tearDown() {
        em.close();
        emf.close();
    }

    @Test
    public void testUserTypeWritesAndReadsJson() {
        JsonThing expectedThing = new JsonThing();
        expectedThing.setName("Abe Lincoln");
        expectedThing.setAge(50);
        expectedThing.setApproved(true);
        expectedThing.setCreated(new Date());
        expectedThing.setFavoriteAnimals(Arrays.asList("dog", "cat", "horse"));

        ResultData resultDataRow = new ResultData();
        resultDataRow.setResultData(expectedThing);
        resultDataRow.setId(1);

        em.getTransaction().begin();
        em.persist(resultDataRow);
        em.getTransaction().commit();
        em.clear();

        resultDataRow = em.find(ResultData.class, 1);

        JsonThing actualThing = resultDataRow.getResultData();
        assertEquals("Abe Lincoln", actualThing.getName());
        assertEquals(50, actualThing.getAge());
        assertEquals(true, actualThing.isApproved());
        assertNotNull(actualThing.getCreated());
        assertEquals(Arrays.asList("dog", "cat", "horse"), actualThing.getFavoriteAnimals());
    }

    /**
     * If we don't provide a type with a Jackson mapping, we should get back a version of the data placed in
     * a Map.
     */
    @Test
    public void testReturnsMapIfNoMappingAtAll() {
        JsonThing untypedThing = new JsonThing();
        untypedThing.setName("George Washington");
        untypedThing.setAge(60);
        untypedThing.setApproved(true);
        untypedThing.setCreated(new Date());
        untypedThing.setFavoriteAnimals(Arrays.asList("lion", "tiger", "bear"));

        ResultData resultDataRow = new ResultData();
        resultDataRow.setUntypedData(untypedThing);
        resultDataRow.setId(1);

        em.getTransaction().begin();
        em.persist(resultDataRow);
        em.getTransaction().commit();
        em.clear();

        resultDataRow = em.find(ResultData.class, 1);

        Object actualUntypedThing = resultDataRow.getUntypedData();
        assertThat(actualUntypedThing, instanceOf(Map.class));

        Map actualThing = (Map) actualUntypedThing;
        assertEquals("George Washington", actualThing.get("name"));
        assertEquals(60, actualThing.get("age"));
        assertEquals(true, actualThing.get("approved"));
        assertNotNull(actualThing.get("created"));
        assertEquals(Arrays.asList("lion", "tiger", "bear"), actualThing.get("favoriteAnimals"));
    }

    /**
     * The unmappableData field is of type Serializable which has no constructor, meaning Jackson can't
     * instantiate it to map the data.  This test will check to make sure that if Jackson can't do the
     * mapping, at least we get an version of the data placed in a Map.
     */
    @Test
    public void testDeserializesToMapIfMappingFails() {
        JsonThing badThing = new JsonThing();
        badThing.setName("Herbert Hoover");

        ResultData resultDataRow = new ResultData();
        resultDataRow.setUnmappableData(badThing);
        resultDataRow.setId(1);

        em.getTransaction().begin();
        em.persist(resultDataRow);
        em.getTransaction().commit();
        em.clear();

        resultDataRow = em.find(ResultData.class, 1);

        Object actualUntypedThing = resultDataRow.getUnmappableData();
        assertThat(actualUntypedThing, instanceOf(Map.class));

        Map actualThing = (Map) actualUntypedThing;
        assertEquals("Herbert Hoover", actualThing.get("name"));
    }

    @Test
    public void testUserTypeReadsSerializedInstance() throws Exception {
        JsonThing byteSerializedThing = new JsonThing();
        byteSerializedThing.setName("Harry Truman");
        byteSerializedThing.setAge(65);
        byteSerializedThing.setApproved(true);
        byteSerializedThing.setCreated(new Date());
        byteSerializedThing.setFavoriteAnimals(Arrays.asList("duck", "goose", "chicken"));

        byte[] serializedBytes;
        try (
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos)
        ) {
            oos.writeObject(byteSerializedThing);
            serializedBytes = baos.toByteArray();
        }

        em.getTransaction().begin();
        em.createNativeQuery("INSERT INTO ResultData (id, resultData) VALUES (1, ?)")
            .setParameter(1, serializedBytes).executeUpdate();
        em.getTransaction().commit();
        em.clear();

        ResultData resultDataRow = em.find(ResultData.class, 1);
        JsonThing actualThing = resultDataRow.getResultData();
        assertEquals("Harry Truman", actualThing.getName());
        assertEquals(65, actualThing.getAge());
        assertEquals(true, actualThing.isApproved());
        assertNotNull(actualThing.getCreated());
        assertEquals(Arrays.asList("duck", "goose", "chicken"), actualThing.getFavoriteAnimals());
    }
}

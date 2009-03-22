package example;

import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

//CREATE TABLE ADDRESS(ID INTEGER NOT NULL PRIMARY KEY,FIRSTNAME VARCHAR,LASTNAME VARCHAR,STREET VARCHAR,CITY VARCHAR)

@Entity
@Table
public class Address {
	@Id @Column public int id;
	@Column public String firstName;
	@Column public String lastName;
	@Column public String street;
	@Column public String city;
	@OneToMany(mappedBy="address") public Set<Document> documents;
}

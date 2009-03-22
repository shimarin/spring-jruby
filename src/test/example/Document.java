package example;

import java.math.BigDecimal;
import java.util.Collection;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

//CREATE TABLE DOCUMENT(ID INTEGER NOT NULL PRIMARY KEY,ADDRESSID INTEGER,TOTAL DECIMAL)
@Entity
@Table
public class Document {
	@Id @Column public int id;
	@ManyToOne @JoinColumn(name="addressId") public Address address;
	@Column public BigDecimal total;
	@OneToMany(mappedBy="document") public Collection<Position> positions;
}

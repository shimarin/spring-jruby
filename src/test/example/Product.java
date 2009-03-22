package example;

import java.math.BigDecimal;
import java.util.Collection;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

//CREATE TABLE PRODUCT(ID INTEGER NOT NULL PRIMARY KEY,NAME VARCHAR,COST DECIMAL)

@Entity
@Table
public class Product {
	@Id @Column public int id;
	@Column public String name;
	@Column public BigDecimal cost;
	@OneToMany(mappedBy="product") public Collection<Position> positions;
}

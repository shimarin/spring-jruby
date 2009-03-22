package example;

import java.math.BigDecimal;
import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

//CREATE TABLE ORDERS(ORDERID INTEGER NOT NULL PRIMARY KEY,CUSTOMERID VARCHAR,EMPLOYEEID INTEGER,ORDERDATE TIMESTAMP,
//REQUIREDDATE TIMESTAMP,SHIPPEDDATE TIMESTAMP,SHIPVIA INTEGER,FREIGHT NUMERIC,SHIPNAME VARCHAR,SHIPADDRESS VARCHAR,
//SHIPCITY VARCHAR,SHIPREGION VARCHAR,SHIPPOSTALCODE VARCHAR,SHIPCOUNTRY VARCHAR)


@Entity
@Table(name="orders")
public class Order {
	@Id @Column public int orderId;
	@Column public String customerId;
	@Column public Integer employeeId;
	@Column public Timestamp orderDate;
	@Column public Timestamp requiredDate;
	@Column public Timestamp shippedDate;
	@Column public Integer shipVia;
	@Column public BigDecimal freight;
	@Column public String shipName;
	@Column public String shipAddress;
	@Column public String shipCity;
	@Column public String shipRegion;
	@Column public String shipPostalCode;
	@Column public String shipCountry;
}

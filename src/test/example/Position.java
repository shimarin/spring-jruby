package example;

import java.math.BigDecimal;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

//CREATE TABLE POSITIONS(DOCUMENTID INTEGER,POSITIONNO INTEGER,PRODUCTID INTEGER,QUANTITY INTEGER,PRICE DECIMAL,
//PRIMARY KEY(DOCUMENTID,POSITIONNO))
@Entity
@Table(name="positions")
@IdClass(PositionPK.class)
public class Position {
	@ManyToOne @JoinColumn(name="documentId") public Document document;
	@Id @Column public int documentId;
	@Id @Column public int positionNo;
	@ManyToOne @JoinColumn(name="productId") public Product product;
	@Column public Integer quantity;
	@Column public BigDecimal price;
}

package net.stbbs.entities;

import java.sql.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name="insentivepoint")
public class InsentivePoint {
	@Column(name="pointid") @Id @GeneratedValue public int pointId;
	@Column(name="personid") public int personId;
	@Column(name="pointdate") public Date pointDate;
	@Column(name="amount") public int amount;
	@Column(name="reason") public String reason;
	

}

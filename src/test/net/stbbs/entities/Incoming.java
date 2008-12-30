package net.stbbs.entities;

import java.sql.Date;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;

@Entity
@Table(name="incoming")
@IdClass(IncomingPK.class)
public class Incoming {

	@Column(name="serviceid",nullable=false,length=20) @Id public String serviceId;
	@Column(name="incomingdate",nullable=false) @Id public Date incomingDate;
	@Column(name="amount") public int amount;
	@Column(name="effectivemonths") public int effectiveMonths;
	@Column(name="method") public int method;
	@Column(name="comments") public String comments;

	public Incoming(Map<String, Object> row2) {
	}


}

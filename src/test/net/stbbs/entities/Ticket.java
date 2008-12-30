package net.stbbs.entities;

import java.sql.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name="ticket")
public class Ticket {

	@Column(name="ticketid",length=24) @Id public String ticketId;
	@Column(name="issueddate") public Date issuedDate;
	@Column(name="comments") public String comments;
	@Column(name="usedby",length=20) public String usedby;
	
}

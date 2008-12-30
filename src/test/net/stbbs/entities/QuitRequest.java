package net.stbbs.entities;

import java.sql.Date;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name="quit_requests")
public class QuitRequest extends HashMap {
	
	@Column(name="id") @Id @GeneratedValue public int id;
	@Column(name="service_id",nullable=false,length=255) public String serviceId;
	@Column(name="quit_date",nullable=false) public Date quitDate;
	@Column(name="comments",length=255) public String comments;
	@Column(name="cancelled",columnDefinition="boolean DEFAULT false not null") public boolean cancelled;
	
	public QuitRequest(Map<String, Object> result) {
		this.put("id", result.get("id"));
		// TODO Auto-generated constructor stub
	}

	public Integer getId() {
		// TODO Auto-generated method stub
		return (Integer)get("id");
	}

}

package net.stbbs.entities;

import java.sql.Date;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name="service")
public class Service {

	@Column(name="id",length=20) @Id public String id;
	@Column(name="password",length=20) public String password;
	@Column(name="chargeperhalfyear") public int chargePerhalfYear;
	@Column(name="personid") public int personId;
	@Column(name="startdate") public Date startDate;
	@Column(name="introducerid") public int introducerId;
	@Column(name="comments") public String comments;
	@Column(name="urgeddate") public Date urgedDate;
	
	public Service(Map<String, Object> result) {
		// TODO Auto-generated constructor stub
	}

}

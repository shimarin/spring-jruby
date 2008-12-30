package net.stbbs.entities;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name="person")
public class Person extends HashMap<String, Object> {

	@Column(name="personid") @Id public int personId;
	@Column(name="name",nullable=false) public String name;
	@Column(name="email") public String email;
	@Column(name="altemail") public String altEmail;
	@Column(name="zip") public String zip;
	@Column(name="address") public String address;
	@Column(name="phone") public String phone;
	@Column(name="fax") public String fax;
	@Column(name="cellular") public String cellular;
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public Person(Map<String,Object> sqlResult)
	{
		super();
		put("name", sqlResult.get("name"));
		put("email", sqlResult.get("email"));
		put("altEmail", sqlResult.get("altEmail"));
		put("zip", sqlResult.get("zip"));
		put("address", sqlResult.get("address"));
		put("phone", sqlResult.get("phone"));
		put("fax", sqlResult.get("fax"));
		put("cellular", sqlResult.get("cellular"));
	}
}

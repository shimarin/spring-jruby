package net.stbbs.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name="join_requests")
public class JoinRequest {

	@Column(name="id") @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public int id;
	@Column(name="code",nullable=false,unique=true,length=32) public String code;
	@Column(name="name",nullable=false) public String name;
	@Column(name="tel") public String tel;
	@Column(name="account") public String account;
	@Column(name="pass") public String pass;
	@Column(name="domain") public String domain;
	@Column(name="introducer") public String introducer;
	@Column(name="sql") public Boolean sql;
	@Column(name="mysql") public Boolean mysql;


}

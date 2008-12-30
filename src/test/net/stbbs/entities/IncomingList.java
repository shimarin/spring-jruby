package net.stbbs.entities;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Collection;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

public class IncomingList {

	private Collection<Incoming> incomings;
	private Date limitDate;
	private int point;

	public void add(Incoming incoming) {
		if (incomings == null) incomings = new ArrayList<Incoming>();
		incomings.add(incoming);
	}

	public void setLimitDate(Date limitDate) {
		this.limitDate = limitDate;
	}

	public void setPoint(int point) {
		this.point = point;
	}

	public int size() {
		if (incomings == null) return 0;
		return incomings.size();
	}

	public Collection<Incoming> getIncomings() {
		return incomings;
	}

	public void setIncomings(Collection<Incoming> incomings) {
		this.incomings = incomings;
	}

	public Date getLimitDate() {
		return limitDate;
	}

	public int getPoint() {
		return point;
	}

}

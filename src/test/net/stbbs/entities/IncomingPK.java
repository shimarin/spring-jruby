package net.stbbs.entities;

import java.io.Serializable;
import java.sql.Date;
import javax.persistence.Embeddable;

@Embeddable
public class IncomingPK implements Serializable {
	public String serviceId;
	public Date incomingDate;

}

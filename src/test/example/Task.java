package example;

import java.math.BigDecimal;
import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;

//CREATE TABLE TASKS(
//SERIES VARCHAR,TASK VARCHAR,SUBTASK VARCHAR,STARTTIMESTAMP TIMESTAMP,ENDTIMESTAMP TIMESTAMP,PERCENT DECIMAL,
//PRIMARY KEY(SERIES,TASK,SUBTASK))
@Entity
@Table(name="tasks")
@IdClass(TaskPK.class)
public class Task {
	@Id @Column public String series;
	@Id @Column public String task;
	@Id @Column public String subTask;
	@Column public Timestamp startTimestamp;
	@Column public Timestamp endTimestamp;
	@Column public BigDecimal percent;
}

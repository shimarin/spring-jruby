package net.stbbs.spring.jruby;

import java.util.ArrayList;
import java.util.Collection;

public class TableDescription {
	private String tableName;
	private Collection<ColumnDescription> columns;
	
	public TableDescription(String tableName)
	{
		this.tableName = tableName;
		this.columns = new ArrayList<ColumnDescription>();
	}
	
	public void addColumn(ColumnDescription col)
	{
		columns.add(col);
	}
	
	public String getTableName()
	{
		return tableName;
	}
	
	public Collection<ColumnDescription> getColumns()
	{
		return columns;
	}
}

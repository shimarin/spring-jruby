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
	
	static public class ColumnDescription {
		private String name;
		private String type;
		private int precision;
		
		public ColumnDescription(String name, String type, int precision)
		{
			this.name = name;
			this.type = type;
			this.precision = precision;
		}

		public String getName() {
			return name;
		}

		public String getType() {
			return type;
		}

		public int getPrecision() {
			return precision;
		}
	}

}

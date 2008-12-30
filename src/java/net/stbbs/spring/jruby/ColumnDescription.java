package net.stbbs.spring.jruby;

public class ColumnDescription {
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

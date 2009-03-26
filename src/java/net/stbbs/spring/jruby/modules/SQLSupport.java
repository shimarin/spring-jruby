package net.stbbs.spring.jruby.modules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import java.sql.Connection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import javax.sql.DataSource;

import net.stbbs.jruby.Decorator;
import net.stbbs.jruby.Util;
import net.stbbs.spring.jruby.TableDescription;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyHash;
import org.jruby.RubyInteger;
import org.jruby.RubyModule;
import org.jruby.RubyNumeric;
import org.jruby.RubySymbol;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.InvalidResultSetAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;

public class SQLSupport extends DataSourceSupport {

	public static void onRegister(RubyModule module)
	{
		Ruby runtime = module.getRuntime();
		Util.registerDecorator(runtime, SqlRowSetDecorator.class);
		Util.registerDecorator(runtime, SqlRowProxyDecorator.class);
		Util.registerDecorator(runtime, ResourceDecorator.class);
		Util.registerDecorator(runtime, DateDecorator.class);
		Util.registerDecorator(runtime, TimestampDecorator.class);
		Util.registerDecorator(runtime, BigDecimalDecorator.class);
		Util.registerDecorator(runtime, DataSourceDecorator.class);
	}

	public SQLSupport(Ruby runtime, IRubyObject self)
	{
		super(runtime, self);
	}
	
	protected JdbcTemplate getJdbcTemplate()
	{
		return new JdbcTemplate(getDataSource());
	}
	
	@JRubyMethod(required=1)
	public SqlRowSet q(IRubyObject self, IRubyObject[] args, Block block) {
		// 引数が０の場合エラー
		if (args.length < 1) {
			throw self.getRuntime().newArgumentError("Method requires at least one argument.");
		}

		Object[] sqlArgs = new Object[args.length - 1];
		for (int i = 1; i < args.length; i++) {
			sqlArgs[i - 1] = JavaEmbedUtils.rubyToJava(runtime, args[i], null);
		}
		SqlRowSet rs = this.getJdbcTemplate().queryForRowSet(
			args[0].asString().getUnicodeValue(), sqlArgs
		);
		return rs;
	}

	@JRubyMethod(required=1)
	public SqlRowProxy q1(IRubyObject self, IRubyObject[] args, Block block) {
		// 引数が０の場合エラー
		if (args.length < 1) {
			throw self.getRuntime().newArgumentError("Method requires at least one argument.");
		}

		SqlRowSet rowSet = q(self, args, block);
		if (!rowSet.next()) return null;

		return new SqlRowProxy(rowSet);
	}
	
	@JRubyMethod(required=1)
	public void x(IRubyObject self, IRubyObject[] args, Block block) {
		// 引数が０の場合エラー
		if (args.length < 1) {
			throw self.getRuntime().newArgumentError(args.length, 1);
		}
		
		this.getJdbcTemplate().execute(args[0].asString().getUnicodeValue());

	}

	protected int insert_one(String tableName, RubyHash row)
	{
		RubyArray keys = row.keys();
		int len = (int)keys.length().getLongValue();
		StringBuffer colsPart = new StringBuffer();
		StringBuffer valuesPart = new StringBuffer();
		Object[] values = new Object[len];
		for (int i = 0; i < len; i++) {
			IRubyObject key = keys.at(RubyInteger.int2fix(runtime, i));
			IRubyObject value = row.fastARef(key);
			if (i > 0) {
				colsPart.append(',');
				valuesPart.append(',');
			}
			colsPart.append(key.asString().getUnicodeValue());
			valuesPart.append('?');
			values[i] = JavaEmbedUtils.rubyToJava(runtime, value, null);
		}
		return getJdbcTemplate().update(
				"insert into " + tableName + "(" + colsPart.toString() + ") values(" + valuesPart.toString() + ")",
				values);
	}
	
	@JRubyMethod(required=2)
	public int insert(IRubyObject self, IRubyObject[] args, Block block)
	{
		String tableName = args[0].asString().getUnicodeValue();
		if (!(args[1] instanceof RubyArray)) {
			return insert_one(tableName, (RubyHash)args[1]);
		}
		
		//else 
		RubyArray rows = (RubyArray)args[1];
		int len = (int)rows.length().getLongValue();
		int total = 0;
		for (int i = 0; i < len; i++) {
			IRubyObject row = rows.at(RubyInteger.int2fix(runtime, i));
			total += insert_one(tableName, (RubyHash)row);
		}
		return total;
	}

	@JRubyMethod(required=1)
	public SqlRowSet tables(IRubyObject self, IRubyObject[] args, Block block)
	{
		DataSource ds = getDataSource();
		Connection con = null;
		String databaseProductName;
		try {
			try {
				con = ds.getConnection();
				databaseProductName = con.getMetaData().getDatabaseProductName();
			}
			finally {
				con.close();
			}
		}
		catch (SQLException ex) {
			throw self.getRuntime().newIOError("SQLException:" + ex.getMessage());
		}
		if (databaseProductName.equals("PostgreSQL")) {
			SqlRowSet rs = this.getJdbcTemplate().queryForRowSet("select * from  pg_tables where schemaname = current_schema()");
			return rs;
		} else if (databaseProductName.equals("MySQL")) {
			SqlRowSet rs = this.getJdbcTemplate().queryForRowSet("show tables");
			return rs;
		}
		return null;
	}

	@JRubyMethod(required=1)
	public int u(IRubyObject self, IRubyObject[] args, Block block) {
		// 引数が０の場合エラー
		if (args.length < 1) {
			throw runtime.newArgumentError("Method requires at least one argument.");
		}

		Object[] sqlArgs = new Object[args.length - 1];
		for (int i = 1; i < args.length; i++) {
			sqlArgs[i - 1] = JavaEmbedUtils.rubyToJava(runtime, args[i], null);
		}
		return this.getJdbcTemplate().update(args[0].asString().getUnicodeValue(), sqlArgs);
	}

	@JRubyMethod(required=1)
	public TableDescription desc(IRubyObject self, IRubyObject[] args, Block block) {
		// 引数が０の場合エラー
		if (args.length != 1) {
			throw runtime.newArgumentError("Method requires one argument.");
		}

		// テーブルにクエリをかけてメタデータを得る
		IRubyObject objTableName = args[0];
		String tableName = objTableName.asString().getUnicodeValue();
		SqlRowSet rs = this.getJdbcTemplate().queryForRowSet("select * from " + tableName + " where 1=2");
		SqlRowSetMetaData md = rs.getMetaData();
		int cnt = md.getColumnCount();
		TableDescription td = new TableDescription(tableName);
		for (int i = 1; i <= cnt; i++) {
			String name = md.getColumnName(i);
			String type = md.getColumnTypeName(i);
			int precision = md.getPrecision(i);
			td.addColumn(new TableDescription.ColumnDescription(name, type, precision));
		}
		return td;
	}

	@JRubyMethod(required=1)
	public java.util.Date sqlDate(IRubyObject self, IRubyObject[] args, Block block) {
		// 引数が足りない場合エラー
		if (args.length < 3) {
			throw self.getRuntime().newArgumentError("Method requires at least 3 argument.");
		}

		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.YEAR, (int)args[0].convertToInteger().getLongValue());
	    cal.set(Calendar.MONTH, (int)args[1].convertToInteger().getLongValue() - 1);
	    cal.set(Calendar.DAY_OF_MONTH, (int)args[2].convertToInteger().getLongValue());
    	cal.set(Calendar.HOUR_OF_DAY, args.length > 3? (int)args[3].convertToInteger().getLongValue() : 0);
    	cal.set(Calendar.MINUTE, args.length > 4? (int)args[4].convertToInteger().getLongValue() : 0);
    	cal.set(Calendar.SECOND, args.length > 5? (int)args[5].convertToInteger().getLongValue() : 0);
    	cal.set(Calendar.MILLISECOND, args.length > 6? (int)args[6].convertToInteger().getLongValue() : 0);

    	if (args.length > 3) {
    		return new java.sql.Timestamp(cal.getTimeInMillis());
    	}
    	//else 
		return new java.sql.Date(cal.getTimeInMillis());
	}

	@JRubyMethod
	public java.util.Date sqlNow(IRubyObject self, IRubyObject[] args, Block block) {
		return new java.sql.Timestamp(System.currentTimeMillis());
	}
	
	@Decorator(SqlRowSet.class)
	public static class SqlRowSetDecorator {
		private SqlRowSet sqlRowSet;
		public SqlRowSetDecorator(SqlRowSet sqlRowSet)
		{
			this.sqlRowSet = sqlRowSet;
		}
		
		public static void onRegister(RubyModule module)
		{
			module.include(new IRubyObject[] {module.getRuntime().getModule("Enumerable")});
		}

		@JRubyMethod
		public SqlRowProxy fetch(IRubyObject self, IRubyObject[] args, Block block)
		{
			if (!sqlRowSet.next()) return null;
			return new SqlRowProxy(sqlRowSet);
		}
		
		@JRubyMethod
		public IRubyObject each(IRubyObject self, IRubyObject[] args, Block block)
		{
			if (!block.isGiven()) return self;
			Ruby runtime = self.getRuntime();
			while (sqlRowSet.next()) {
				block.call(self.getRuntime().getCurrentContext(), new IRubyObject[] {JavaEmbedUtils.javaToRuby(runtime, new SqlRowProxy(sqlRowSet))});
			}
			sqlRowSet.beforeFirst();
			return self;
		}

		@JRubyMethod
		public RubyArray to_hash_array(IRubyObject self, IRubyObject[] args, Block block)
		{
			Ruby runtime = self.getRuntime();
			RubyArray array = runtime.newArray();
			while (sqlRowSet.next()) {
				String[] columnNames = sqlRowSet.getMetaData().getColumnNames();
				RubyHash assoc = RubyHash.newHash(runtime);
				for (String columnName:columnNames) {
					RubySymbol sym = RubySymbol.newSymbol(runtime, columnName.toLowerCase());
					assoc.aset(sym, JavaEmbedUtils.javaToRuby(runtime, sqlRowSet.getObject(columnName)) );
				}
				array.add(assoc);
			}
			sqlRowSet.beforeFirst();
			return array;
		}
		
		@JRubyMethod
		public IRubyObject dup(IRubyObject self, IRubyObject[] args, Block block)
		{
			return to_hash_array(self, args, block);
		}
	}
	
	public static class SqlRowProxy {
		private SqlRowSet sqlRowSet;
		public SqlRowProxy(SqlRowSet sqlRowSet)
		{
			this.sqlRowSet = sqlRowSet;
		}
		public Object getValue(String fieldName)
		{
			return sqlRowSet.getObject(fieldName);
		}
		public Object getValue(int fieldNumber)
		{
			return sqlRowSet.getObject(fieldNumber);
		}
		public String[] getColumnNames()
		{
			return sqlRowSet.getMetaData().getColumnNames();
		}
		public int getColumnCount()
		{
			return sqlRowSet.getMetaData().getColumnCount();
		}
	}
	
	@Decorator(SqlRowProxy.class)
	public static class SqlRowProxyDecorator {
		private SqlRowProxy sqlRowProxy;
		private HashMap<String,IRubyObject> overriddens;
		public SqlRowProxyDecorator(SqlRowProxy sqlRowProxy)
		{
			this.sqlRowProxy = sqlRowProxy;
			overriddens = new HashMap<String,IRubyObject>();
		}
		
		public static void onRegister(RubyModule module)
		{
			module.include(new IRubyObject[] {module.getRuntime().getModule("Enumerable")});
		}

		@JRubyMethod(name="[]", required=1)
		public Object getValue(IRubyObject self, IRubyObject[] args, Block block)
		{
			if (args[0] instanceof RubyNumeric) {
				int index = (int)args[0].convertToInteger().getLongValue();
				int columnCount = sqlRowProxy.getColumnCount(); 
				if (columnCount <= index) {
					throw self.getRuntime().newIndexError("Index out of range: max(" + (columnCount - 1) + ") given(" + index + ")");
				}
				return sqlRowProxy.getValue(index + 1);
			}
			String fieldName = args[0].asString().getUnicodeValue().toLowerCase();
			if (overriddens.containsKey(fieldName)) {
				return overriddens.get(fieldName);
			}
			return sqlRowProxy.getValue(fieldName);
		}

		@JRubyMethod(name="[]=", required=1)
		public void setValue(IRubyObject self, IRubyObject[] args, Block block)
		{
			String fieldName = args[0].asString().getUnicodeValue();
			if (args[0] instanceof RubyNumeric) {
				int index = (int)args[0].convertToInteger().getLongValue();
				int columnCount = sqlRowProxy.getColumnCount(); 
				if (columnCount <= index) {
					throw self.getRuntime().newIndexError("Index out of range: max(" + (columnCount - 1) + ") given(" + index + ")");
				}
				fieldName = sqlRowProxy.getColumnNames()[index];
			}
			overriddens.put(fieldName.toLowerCase(), args[1]);
		}

		@JRubyMethod
		public IRubyObject each(IRubyObject self, IRubyObject[] args, Block block)
		{
			if (!block.isGiven()) return self;
			Ruby runtime = self.getRuntime();

			RubyHash hash = this.to_hash(self, null);
			IRubyObject[] keys = Util.convertRubyArray(hash.keys());
			for (IRubyObject key:keys) {
				block.call(self.getRuntime().getCurrentContext(), new IRubyObject[] {key, hash.aref(key)});
			}
			return self;
		}

		@JRubyMethod(required=1,optional=1)
		public Object method_missing(IRubyObject self, IRubyObject[] args, Block block)
		{
			String methodName = args[0].asString().getUnicodeValue().toLowerCase();
			if (methodName.matches(".+=$")) {
				// 代入形
				methodName = methodName.substring(0, methodName.length() - 1);
				overriddens.put(methodName, args[1]);
				return args[1];
			}
			return sqlRowProxy.getValue(methodName);
		}
		
		@JRubyMethod
		public IRubyObject inspect(IRubyObject self, IRubyObject[] args, Block block)
		{
			return to_hash(self, args, block).inspect();
		}
		
		@JRubyMethod
		public IRubyObject dup(IRubyObject self, IRubyObject[] args, Block block)
		{
			return to_hash(self, args, block);
		}

		protected RubyHash to_hash(IRubyObject self, String[] names)
		{
			Ruby runtime = self.getRuntime();
			Set<String> columnNames = new HashSet<String>();
			
			if (names != null) {
				for (String name:names) {
					columnNames.add(name.toLowerCase());
				}
			} else {
				for (String columnName:sqlRowProxy.getColumnNames()) {
					columnNames.add(columnName.toLowerCase());
				}
				columnNames.addAll(overriddens.keySet());
			}
			RubyHash assoc = RubyHash.newHash(runtime);
			for (String columnName:columnNames) {
				RubySymbol sym = RubySymbol.newSymbol(runtime, columnName);
				try {
					assoc.aset(sym, 
							overriddens.containsKey(columnName)? overriddens.get(columnName) : 
							JavaEmbedUtils.javaToRuby(runtime, sqlRowProxy.getValue(columnName)) );
				}
				catch (InvalidResultSetAccessException ex) {
					assoc.aset(sym, runtime.getNil());
				}
			}
			return assoc;
			
		}
		
		@JRubyMethod(optional=1)
		public RubyHash to_hash(IRubyObject self, IRubyObject[] args, Block block)
		{
			Ruby runtime = self.getRuntime();
			Set<String> columnNames = new HashSet<String>();
			
			String[] names = null;
			if (args.length > 0) {
				IRubyObject[] givenNames = Util.convertRubyArray(args[0].convertToArray());
				names = new String[givenNames.length];
				for (int i = 0; i < names.length; i++)  {
					names[i] = givenNames[i].asString().getUnicodeValue().toLowerCase();
				}
			}
			return to_hash(self, names);
		}
	}
	
	@Decorator(Resource.class)
	public static class ResourceDecorator {
		
		private Resource resource;
		
		public ResourceDecorator(Resource resource)
		{
			this.resource = resource;
		}
		
		@JRubyMethod
		public void sqlExec(IRubyObject self, IRubyObject[] args, Block block) throws IOException
		{
			InputStream is = resource.getInputStream();
			SimpleJdbcTemplate jt = new SimpleJdbcTemplate(DataSourceSupport.getDataSource(self));
			try {
				InputStreamReader insr = new InputStreamReader(is, "UTF-8");
				BufferedReader br = new BufferedReader(insr);
				String line;
				while ((line = br.readLine()) != null) {
					jt.getJdbcOperations().execute(line);
				}
				br.close();
				insr.close();
			}
			finally {
				is.close();
			}
		}
	}
	
	@Decorator(java.sql.Date.class)
	public static class DateDecorator {
		
		java.sql.Date self;
		
		public DateDecorator(java.sql.Date self)
		{
			this.self = self;
		}
		
		@JRubyMethod
		public String inspect(IRubyObject self, IRubyObject[] args, Block block)
		{
			// SQL99 format
			DateFormat df = new SimpleDateFormat("yyyy-MM-dd"); // TODO: make it mt safe
			return '"' + df.format(this.self) + '"';
		}
		
	}

	@Decorator(Timestamp.class)
	public static class TimestampDecorator {
		Timestamp self;
		
		public TimestampDecorator(Timestamp self)
		{
			this.self = self;
		}
		
		@JRubyMethod
		public String inspect(IRubyObject self, IRubyObject[] args, Block block)
		{
			// SQL99 format
			DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"); // TODO: make it mt safe
			return '"' + df.format(this.self) + '"';
		}
	}

	@Decorator(BigDecimal.class)
	public static class BigDecimalDecorator {
		BigDecimal self;
		
		public BigDecimalDecorator(BigDecimal self)
		{
			this.self = self;
		}
		
		@JRubyMethod
		public String inspect(IRubyObject self, IRubyObject[] args, Block block)
		{
			return '"' + this.self.toPlainString() + '"';
		}
	}
	
	@Decorator(DataSource.class)
	public static class DataSourceDecorator {
		DataSource dataSource;
		public DataSourceDecorator(DataSource dataSource)
		{
			this.dataSource = dataSource;
		}
		
		@JRubyMethod
		public IRubyObject withConnection(IRubyObject self, IRubyObject[] args, Block block)
		{
			Ruby runtime = self.getRuntime();
			if (!block.isGiven()) return runtime.getNil();
			Connection con = DataSourceUtils.getConnection(dataSource);
			try {
				return block.call(runtime.getCurrentContext(), new IRubyObject[] {JavaEmbedUtils.javaToRuby(runtime, con)});
			}
			finally {
				DataSourceUtils.releaseConnection(con, dataSource);
			}
		}
	}
}

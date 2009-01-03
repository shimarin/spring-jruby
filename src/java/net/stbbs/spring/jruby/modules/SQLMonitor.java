package net.stbbs.spring.jruby.modules;

import java.sql.SQLException;
import java.util.Calendar;

import java.sql.Connection;
import javax.sql.DataSource;

import net.stbbs.spring.jruby.ColumnDescription;
import net.stbbs.spring.jruby.SpringIntegratedJRubyRuntime;
import net.stbbs.spring.jruby.TableDescription;

import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;

@Module("SQLMonitor")
public class SQLMonitor {

	protected DataSource getDataSource(SpringIntegratedJRubyRuntime ruby, IRubyObject self)
	{
		return ruby.getComponent(self, "dataSource");
	}
	
	@ModuleMethod(arity=ModuleMethod.ARITY_ONE_REQUIRED)
	public Object q(SpringIntegratedJRubyRuntime ruby, IRubyObject self, IRubyObject[] args, Block block) {
		// 引数が０の場合エラー
		if (args.length < 1) {
			throw self.getRuntime().newArgumentError("Method requires at least one argument.");
		}

		// データソースを取得し、JdbcTemplateを作成
		JdbcTemplate jt = new JdbcTemplate(getDataSource(ruby, self));
		Object[] sqlArgs = new Object[args.length - 1];
		for (int i = 1; i < args.length; i++) {
			sqlArgs[i - 1] = ruby.toJava(args[i]);
		}
		SqlRowSet rs = jt.queryForRowSet(
			args[0].asString().getUnicodeValue(), sqlArgs
		);
		return rs;
	}

	@ModuleMethod(arity=ModuleMethod.ARITY_ONE_REQUIRED)
	public Object tables(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block)
	{
		DataSource ds = getDataSource(ruby, self);
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
			SqlRowSet rs = new JdbcTemplate(ds).queryForRowSet("select * from  pg_tables where schemaname = current_schema()");
			return rs;
		}
		return ruby.getNil();
	}

	@ModuleMethod(arity=ModuleMethod.ARITY_ONE_REQUIRED)
	public Object u(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) {
		// 引数が０の場合エラー
		if (args.length < 1) {
			throw ruby.newArgumentError("Method requires at least one argument.");
		}

		// データソースを取得し、JdbcTemplateを作成
		JdbcTemplate jt = new JdbcTemplate(getDataSource(ruby, self));
		Object[] sqlArgs = new Object[args.length - 1];
		for (int i = 1; i < args.length; i++) {
			sqlArgs[i - 1] = ruby.toJava(args[i]);
		}
		return jt.update(args[0].asString().getUnicodeValue(), sqlArgs);
	}

	@ModuleMethod(arity=ModuleMethod.ARITY_ONE_REQUIRED)
	public Object desc(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) {
		// 引数が０の場合エラー
		if (args.length != 1) {
			throw ruby.newArgumentError("Method requires one argument.");
		}

		// データソースを取得し、JdbcTemplateを作成
		JdbcTemplate jt = new JdbcTemplate(getDataSource(ruby, self));
		// テーブルにクエリをかけてメタデータを得る
		IRubyObject objTableName = args[0];
		String tableName = objTableName.asString().getUnicodeValue();
		SqlRowSet rs = jt.queryForRowSet("select * from " + tableName + " where 1=2");
		SqlRowSetMetaData md = rs.getMetaData();
		int cnt = md.getColumnCount();
		TableDescription td = new TableDescription(tableName);
		for (int i = 1; i <= cnt; i++) {
			String name = md.getColumnName(i);
			String type = md.getColumnTypeName(i);
			int precision = md.getPrecision(i);
			td.addColumn(new ColumnDescription(name, type, precision));
		}
		return td;
	}

	@ModuleMethod(arity=ModuleMethod.ARITY_THREE_REQUIRED)
	public Object sqlDate(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) {
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

	@ModuleMethod(arity=ModuleMethod.ARITY_NO_ARGUMENTS)
	public Object sqlNow(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) {
		return new java.sql.Timestamp(System.currentTimeMillis());
	}
}

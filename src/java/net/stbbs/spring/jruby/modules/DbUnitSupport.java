package net.stbbs.spring.jruby.modules;

import java.io.File;
import java.io.InputStream;
import java.sql.Connection;

import javax.sql.DataSource;

import org.dbunit.AbstractDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.excel.XlsDataSet;
import org.dbunit.ext.db2.Db2Connection;
import org.dbunit.ext.h2.H2Connection;
import org.dbunit.ext.hsqldb.HsqldbConnection;
import org.dbunit.ext.mssql.MsSqlConnection;
import org.dbunit.ext.mysql.MySqlConnection;
import org.dbunit.ext.oracle.OracleConnection;
import org.dbunit.operation.DatabaseOperation;
import org.jruby.Ruby;
import org.jruby.anno.JRubyMethod;
import org.jruby.anno.JRubyModule;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

@JRubyModule(name="DbUnitSupport")
public class DbUnitSupport extends DataSourceSupport {
	static class TransactionAwareDataSourceDatabaseTester extends AbstractDatabaseTester {
		private TransactionAwareDataSourceProxy dataSource;

		public TransactionAwareDataSourceDatabaseTester( DataSource dataSource )
		{
			super();
			if (dataSource instanceof TransactionAwareDataSourceProxy) {
				this.dataSource = (TransactionAwareDataSourceProxy)dataSource;
			} else {
				this.dataSource = new TransactionAwareDataSourceProxy(dataSource);
			}
		}

		public IDatabaseConnection getConnection() throws Exception {
			String dataSourceType = dataSource.getTargetDataSource().getClass().getName();
			Connection con = dataSource.getConnection();
			String databaseProductName = con.getMetaData().getDatabaseProductName();
			
			if ("H2".equals(databaseProductName)) {
				IDatabaseConnection con2 = new H2Connection(con, getSchema());
				con2.getConfig().setProperty(DatabaseConfig.PROPERTY_ESCAPE_PATTERN, "\"?\"");
				return con2;
			} else if ("com.mysql.jdbc.jdbc2.optional.MysqlDataSource".equals(dataSourceType)) {
				IDatabaseConnection con2 = new MySqlConnection(con, getSchema());
				con2.getConfig().setProperty(DatabaseConfig.PROPERTY_ESCAPE_PATTERN, "`?`");
				return con2;
			} else if ("com.ibm.db2.jcc.DB2SimpleDataSource".equals(dataSourceType)) {
				// http://www-01.ibm.com/software/data/db2/express/download.html
				return new Db2Connection(con, getSchema());
			} else if ("org.hsqldb.jdbc.jdbcDataSource".equals(dataSourceType)) {
				return new HsqldbConnection(con, getSchema());
			} else if ("com.microsoft.sqlserver.jdbc.SQLServerDataSource".equals(dataSourceType)) {
				IDatabaseConnection con2 = new MsSqlConnection(con, getSchema());
				con2.getConfig().setProperty(DatabaseConfig.PROPERTY_ESCAPE_PATTERN, "[?]");
				return con2;
			} else if ("oracle.jdbc.pool.OracleDataSource".equals(dataSourceType)) {
				return new OracleConnection(con, getSchema());
			} 
			// else
			return new DatabaseConnection( con, getSchema() );
		}
	}
	
	public DbUnitSupport(Ruby runtime, IRubyObject self)
	{
		super(runtime, self);
	}
	
	protected void executeInsertOperation(IRubyObject self, IDataSet fixtures) throws Exception
	{
		IDatabaseTester tester = new TransactionAwareDataSourceDatabaseTester(getDataSource());
		IDatabaseConnection conn = null;
		try {
			conn = tester.getConnection();
			DatabaseOperation.INSERT.execute(conn, fixtures);
		}
		finally {
			if (conn != null)
				tester.closeConnection(conn);
		}
	}
	
	@JRubyMethod(required=1)
	public IRubyObject loadXlsFixture(IRubyObject self, IRubyObject[] args, Block block) throws Exception
	{
		// 引数が０の場合エラー
		if (args.length < 1) {
			throw self.getRuntime().newArgumentError("Method requires at least one argument.");
		}
		
		XlsDataSet ds = null;
		Object o = toJava(args[0]);
		InputStream inputStreamNeedToClose = null;
		if (o instanceof File) {
			ds = new XlsDataSet((File)o);
		} else if (o instanceof InputStream) {
			ds = new XlsDataSet((InputStream)o);
		} else if (o instanceof Resource){
			inputStreamNeedToClose = ((Resource)o).getInputStream(); 
			ds = new XlsDataSet(inputStreamNeedToClose);
		} else {
			ds = new XlsDataSet(new File(args[0].asString().getUnicodeValue()));
		}
		try {
			executeInsertOperation(self, ds); 
		}
		finally {
			if (inputStreamNeedToClose != null) {
				inputStreamNeedToClose.close();
				inputStreamNeedToClose = null;
			}
		}
		return runtime.getNil();
	}

}

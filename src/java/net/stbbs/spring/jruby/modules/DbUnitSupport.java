package net.stbbs.spring.jruby.modules;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.sql.DataSource;

import net.stbbs.spring.dbunit.TransactionAwareDataSourceDatabaseTester;
import net.stbbs.spring.jruby.SpringIntegratedJRubyRuntime;

import org.dbunit.IDatabaseTester;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.excel.XlsDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.springframework.core.io.Resource;

@Module
public class DbUnitSupport {
	protected DataSource getDataSource(SpringIntegratedJRubyRuntime ruby, IRubyObject self)
	{
		return ruby.getComponent(self, "dataSource");
	}

	protected void executeInsertOperation(SpringIntegratedJRubyRuntime ruby, IRubyObject self, 
			IDataSet fixtures) throws Exception
	{
		IDatabaseTester tester = new TransactionAwareDataSourceDatabaseTester(getDataSource(ruby, self));
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
	
	@ModuleMethod(arity=ModuleMethod.ARITY_ONE_ARGUMENT)
	public IRubyObject loadXlsFixture(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) throws Exception
	{
		// 引数が０の場合エラー
		if (args.length < 1) {
			throw self.getRuntime().newArgumentError("Method requires at least one argument.");
		}
		
		XlsDataSet ds = null;
		Object o = ruby.toJava(args[0]);
		if (o instanceof File) {
			ds = new XlsDataSet((File)o);
		} else if (o instanceof InputStream) {
			ds = new XlsDataSet((InputStream)o);
		} else if (o instanceof Resource){
			ds = new XlsDataSet(((Resource)o).getInputStream());
		} else {
			ds = new XlsDataSet(new File(args[0].asString().getUnicodeValue()));
		}
		
		executeInsertOperation(ruby, self, ds); 
		return ruby.getNil();
	}

}

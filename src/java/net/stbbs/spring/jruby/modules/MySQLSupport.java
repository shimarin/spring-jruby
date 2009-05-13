package net.stbbs.spring.jruby.modules;

import java.util.Map;

import javax.sql.DataSource;

import net.stbbs.jruby.Util;

import org.jruby.Ruby;
import org.jruby.RubyHash;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;

public class MySQLSupport {
	@JRubyMethod(required=2)
	public DataSource newMySQLDataSource(IRubyObject self, IRubyObject[] args, Block block)
	{
		Ruby runtime = self.getRuntime();
		if (args.length < 2) {
			throw runtime.newArgumentError(args.length, 2);
		}
		
		String databaseName = args[0].asString().getUnicodeValue();
		String user = args[1].asString().getUnicodeValue();
		MysqlDataSource ds = new MysqlDataSource();
		ds.setDatabaseName(databaseName);
		ds.setUser(user);
		ds.setServerName("localhost");
		ds.setUseUnicode(true);
		ds.setCharacterEncoding("UTF8");
		ds.setZeroDateTimeBehavior("convertToNull");
		if (args.length > 2) {
			BeanWrapper bw = new BeanWrapperImpl(ds);
			Map<String,IRubyObject> options = Util.convertRubyHash((RubyHash)args[2]);
			for (Map.Entry<String,IRubyObject> entry:options.entrySet()) {
				String key = entry.getKey();
				if (bw.isWritableProperty(key)) {
					Object value = JavaEmbedUtils.rubyToJava(runtime, entry.getValue(), null);
					bw.setPropertyValue(key, value);
				}
			}
		}
		return ds;
	}
}

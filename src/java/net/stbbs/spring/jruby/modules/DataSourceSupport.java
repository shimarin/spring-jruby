package net.stbbs.spring.jruby.modules;

import javax.sql.DataSource;

import org.jruby.Ruby;
import org.jruby.RubyString;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.builtin.IRubyObject;

public abstract class DataSourceSupport {
	protected Ruby runtime;
	protected IRubyObject self;
	
	public static DataSource getDataSource(IRubyObject self, Ruby runtime)
	{
		IRubyObject c = ApplicationContextSupport.evalWithApplicationContextBinding(self, "dataSource");
		return  (DataSource)JavaEmbedUtils.rubyToJava(runtime, c, DataSource.class);
	}

	public static DataSource getDataSource(IRubyObject self)
	{
		return getDataSource(self, self.getRuntime());
	}

	protected DataSource getDataSource()
	{
		return getDataSource(self, runtime);
	}
	
	protected DataSourceSupport(Ruby runtime, IRubyObject self) {
		this.runtime = runtime;
		this.self = self;
	}
	
	protected Object toJava(IRubyObject ro)
	{
		return JavaEmbedUtils.rubyToJava(runtime, ro, null);
	}
}

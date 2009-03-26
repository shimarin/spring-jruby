package net.stbbs.spring.jruby.blazeds;

import org.jruby.Ruby;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

import flex.messaging.FactoryInstance;
import flex.messaging.FlexFactory;
import flex.messaging.config.ConfigMap;

public class JRubyFactory implements FlexFactory {

	private IRubyObject applicationContext;
	
	public JRubyFactory(IRubyObject applicationContext)
	{
		this.applicationContext = applicationContext;
	}
	
	public FactoryInstance createFactoryInstance(String id, ConfigMap properties) {
		FactoryInstance fi = new FactoryInstance(this, id, properties);
		fi.setSource(properties.getProperty(FlexFactory.SOURCE));
		return fi;
	}

	public Object lookup(FactoryInstance factoryInfo) {
		Ruby runtime = applicationContext.getRuntime();
		IRubyObject ro = applicationContext.callMethod(runtime.getCurrentContext(), "allocateProxyObject");
		ThreadContext context = runtime.getCurrentContext();
		return JavaEmbedUtils.rubyToJava(runtime, ro.callMethod(context, factoryInfo.getSource()), null);
	}

	public void initialize(String id, ConfigMap properties) {
	}

}

package net.stbbs.spring.jruby.modules;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyModule;
import org.jruby.RubyString;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import net.stbbs.jruby.Decorator;
import net.stbbs.jruby.Util;

public class RequestContextSupport {
	
	protected static final String P_ATTRIBUTE_NAME = "__ruby_p";
	
	public static void onRegister(RubyModule module)
	{
		Ruby runtime = module.getRuntime();
		Util.registerDecorator(runtime, SessionProxyDecorator.class);
		Util.registerDecorator(runtime, ParamsProxyDecorator.class);
	}

	@JRubyMethod
	public HttpServletRequest servletRequest(IRubyObject self, IRubyObject[] args, Block block)
	{
		return ((ServletRequestAttributes)RequestContextHolder.getRequestAttributes()).getRequest();
	}
	
	@JRubyMethod
	public SessionProxy session(IRubyObject self, IRubyObject[] args, Block block)
	{
		return new SessionProxy(RequestContextHolder.getRequestAttributes());
	}
	
	@JRubyMethod
	public ParamsProxy params(IRubyObject self, IRubyObject[] args, Block block)
	{
		return new ParamsProxy(this.servletRequest(self, args, block));
	}
	
	@JRubyMethod
	public void p(IRubyObject self, IRubyObject[] args, Block block)
	{
		ApplicationContextDecorator.p(args);
		List<IRubyObject> ruby_p = getRubyPObjects();
		if (ruby_p == null) {
			ruby_p = new ArrayList<IRubyObject>();
			RequestContextHolder.getRequestAttributes().setAttribute(P_ATTRIBUTE_NAME, ruby_p, RequestAttributes.SCOPE_REQUEST);
		}
		for (IRubyObject obj:args) {
			ruby_p.add(obj.callMethod(self.getRuntime().getCurrentContext(), "dup"));
		}
	}
	
	public static List<IRubyObject> getRubyPObjects()
	{
		return (List<IRubyObject>)RequestContextHolder.getRequestAttributes().getAttribute(
				P_ATTRIBUTE_NAME, RequestAttributes.SCOPE_REQUEST);
	}
	
	public static class ParamsProxy {
		private HttpServletRequest request;
		public ParamsProxy(HttpServletRequest request)
		{
			this.request = request;
		}
		public String[] getParameterValues(String name)
		{
			return request.getParameterValues(name);
		}
	}
	
	@Decorator(ParamsProxy.class)
	public static class ParamsProxyDecorator {
		private ParamsProxy paramsProxy;
		public ParamsProxyDecorator(ParamsProxy paramsProxy)
		{
			this.paramsProxy = paramsProxy;
		}
		@JRubyMethod(name="[]")
		public Object getValue(IRubyObject self, IRubyObject[] args, Block block)
		{
			String name = args[0].asString().getUnicodeValue();
			String[] values = paramsProxy.getParameterValues(name);
			if (values == null) return null;
			if (values.length == 1) return values[0];
			// else
			Ruby runtime = self.getRuntime();
			RubyArray arr = runtime.newArray(values.length);
			for (String value:values) {
				arr.add(RubyString.newUnicodeString(runtime, value));
			}
			return arr;
		}
	}

	public static class SessionProxy {
		private RequestAttributes requestAttributes;
		public SessionProxy(RequestAttributes requestAttributes)
		{
			this.requestAttributes = requestAttributes;
		}
		public Object getValue(String name)
		{
			return requestAttributes.getAttribute(name, RequestAttributes.SCOPE_SESSION);
		}
		public void setValue(String name, Object value)
		{
			requestAttributes.setAttribute(name, value, RequestAttributes.SCOPE_SESSION);
		}
	}
	
	@Decorator(SessionProxy.class)
	public static class SessionProxyDecorator {
		private SessionProxy sessionProxy;
		
		public SessionProxyDecorator(SessionProxy sessionProxy)
		{
			this.sessionProxy = sessionProxy;
		}
		
		@JRubyMethod(name="[]")
		public Object getValue(IRubyObject self, IRubyObject[] args, Block block)
		{
			String name = args[0].asString().getUnicodeValue();
			return sessionProxy.getValue(name);
		}
		
		@JRubyMethod(name="[]=")
		public void setValue(IRubyObject self, IRubyObject[] args, Block block)
		{
			String name = args[0].asString().getUnicodeValue();
			Object obj;
			if (args[1] instanceof RubyString) {
				// 文字列の場合特別に処理しないとISO-8859-1で取り出されてしまう
				obj = args[1].asString().getUnicodeValue();
			} else {
				obj = JavaEmbedUtils.rubyToJava(self.getRuntime(), args[1], null);
			}
			sessionProxy.setValue(name, obj);	// Javaからも使えるようにJavaオブジェクトに変換
		}
	}
}

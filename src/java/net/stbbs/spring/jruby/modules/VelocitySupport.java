package net.stbbs.spring.jruby.modules;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.util.Map;

import net.stbbs.spring.jruby.modules.DownloadSupport.DownloadContent;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.context.Context;
import org.jruby.Ruby;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.springframework.core.io.Resource;

/**
 * 
 * @author shimarin
 *
 */
public class VelocitySupport {
	
	private Ruby runtime;
	private IRubyObject self;
	
	public VelocitySupport(Ruby runtime, IRubyObject self)
	{
		this.runtime = runtime;
		this.self = self;
	}

	protected Resource getResource(IRubyObject resourceName)
	{
		return (Resource)self.callMethod(runtime.getCurrentContext(), "getResource", new IRubyObject[] {resourceName.asString()});
		
	}
	
	@JRubyMethod(required=1)
	public DownloadContent page(IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		Ruby runtime = self.getRuntime();
		// 引数が足りない場合エラー
		if (args.length < 1) {
			throw runtime.newArgumentError("Method requires at least one argument.");
		}

		VelocityContext ctx = new VelocityContext();
		// VelocityContextにインスタンス変数を全部セットする
		Map iv = self.getInstanceVariables();
		java.util.Iterator i = iv.entrySet().iterator();
		while (i.hasNext()) {
			Map.Entry entry = (Map.Entry)i.next();
			String strKey = (String)entry.getKey();
    		if (strKey.startsWith("@")) {
    			strKey = strKey.substring(1);
    		}
    		Object value = entry.getValue();
    		if (value instanceof IRubyObject) {
    			value = JavaEmbedUtils.rubyToJava(runtime, (IRubyObject)value, null);
    		}
    		ctx.put(strKey, value);
		}

		// Velocityテンプレートをロード
		Resource res;
		Object jo = JavaEmbedUtils.rubyToJava(runtime, args[0], null);
		if (jo instanceof Resource) {
			res = (Resource)jo;
		} else {
			res = getResource(args[0]);
		}
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		OutputStreamWriter writer = new OutputStreamWriter(baos, "UTF-8");
		InputStream is = null;
		try {
			is = res.getInputStream();
			InputStreamReader isr = new InputStreamReader(is, "UTF-8");
			Velocity.evaluate(ctx, writer, res.getFilename(), isr);
			isr.close();
		}
		finally {
			if (is != null) is.close();
		}
		writer.close();
		return new DownloadContent("text/html;charset=UTF-8", baos.toByteArray());
	}
	
	@JRubyMethod
	public VelocityContext createVelocityContext(IRubyObject self, IRubyObject[] args, Block block)
	{
		return new VelocityContext();
	}
	
	@JRubyMethod(required=2)
	public String evaluateVelocity(IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		 StringWriter writer = new StringWriter();
		 Context context = (Context)JavaEmbedUtils.rubyToJava(runtime, args[0], null);
		 InputStream is = null;
		 try {
			 is = getResource(args[1]).getInputStream();
			 InputStreamReader isr = new InputStreamReader(is, "UTF-8");
			 Velocity.evaluate(context, writer, args[1].asString().getUnicodeValue(), isr);
			 isr.close();
		 }
		 finally {
			 if (is != null) is.close();
		 }
		 return writer.toString();
	}
}

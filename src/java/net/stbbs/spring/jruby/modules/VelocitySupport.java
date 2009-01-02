package net.stbbs.spring.jruby.modules;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;

import net.stbbs.spring.jruby.DownloadContent;
import net.stbbs.spring.jruby.SpringIntegratedJRubyRuntime;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.context.Context;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * 
 * @author shimarin
 *
 */
@Module
public class VelocitySupport {

	@ModuleMethod(arity=ModuleMethod.ARITY_NO_ARGUMENTS)
	public IRubyObject createVelocityContext(SpringIntegratedJRubyRuntime ruby, IRubyObject self, IRubyObject[] args, Block block)
	{
		return ruby.toRuby(new VelocityContext());
	}
	
	@ModuleMethod(arity=ModuleMethod.ARITY_TWO_ARGUMENTS)
	public IRubyObject evaluateVelocity(SpringIntegratedJRubyRuntime ruby, IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		 StringWriter writer = new StringWriter();
		 Context context = (Context)ruby.toJava(args[0]);
		 String template = args[1].asString().getUnicodeValue();
		 InputStream is = null;
		 try {
			 is = ruby.getResource(template).getInputStream();
			 InputStreamReader isr = new InputStreamReader(is, "UTF-8");
			 Velocity.evaluate(context, writer, template, isr);
			 isr.close();
		 }
		 finally {
			 if (is != null) is.close();
		 }
		 return ruby.newUnicodeString(writer.toString());
	}
	
	@ModuleMethod(arity=ModuleMethod.ARITY_ONE_ARGUMENT)
	public IRubyObject page(SpringIntegratedJRubyRuntime ruby, IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		// 引数が足りない場合エラー
		if (args.length < 1) {
			throw ruby.newArgumentError("Method requires at least one argument.");
		}

		VelocityContext vc = new VelocityContext();
		if (block.isGiven()) {
			block.call(ruby.getCurrentContext(), new IRubyObject[] {ruby.toRuby(vc)});
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		OutputStreamWriter writer = new OutputStreamWriter(baos, "UTF-8");
		String template = args[0].asString().getUnicodeValue();
		InputStream is = null;
		try {
			is = ruby.getResource(template).getInputStream();
			InputStreamReader isr = new InputStreamReader(is, "UTF-8");
			Velocity.evaluate(vc, writer, template, isr);
			isr.close();
		}
		finally {
			if (is != null) is.close();
		}
		writer.close();
		DownloadContent dl = new DownloadContent("text/html;charset=UTF-8", baos.toByteArray());
		return ruby.toRuby(dl);

	}
}

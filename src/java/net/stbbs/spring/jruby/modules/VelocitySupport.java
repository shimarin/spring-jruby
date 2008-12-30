package net.stbbs.spring.jruby.modules;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;

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
		 InputStream is = ruby.getResource(template).getInputStream();
		 InputStreamReader isr = new InputStreamReader(is, "UTF-8");
		 Velocity.evaluate(context, writer, template, isr);
		 isr.close();
		 is.close();
		 return ruby.newUnicodeString(writer.toString());
	}
}

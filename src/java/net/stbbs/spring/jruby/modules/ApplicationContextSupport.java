package net.stbbs.spring.jruby.modules;

import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.RubyString;
import org.jruby.RubySymbol;
import org.jruby.RubyThread;
import org.jruby.anno.JRubyMethod;
import org.jruby.parser.LocalStaticScope;
import org.jruby.runtime.Block;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

import net.stbbs.jruby.Util;
import net.stbbs.spring.jruby.modules.ApplicationContextDecorator.ScriptEvalProxyDecorator;

public class ApplicationContextSupport {
	public static final String APPLICATIONCONTEXT_OBJECT_NAME = "__JRubyApplicationContext";
	public static final String BINDING_TLD_NAME = "applicationContextBinding";

	public static void onRegister(RubyModule module)
	{
		Ruby runtime = module.getRuntime();
		Util.registerDecorator(runtime, ApplicationContextDecorator.class);
		Util.registerDecorator(runtime, ResourceDecorator.class);
		Util.registerDecorator(runtime, ScriptEvalProxyDecorator.class);
	}

	@JRubyMethod
	public static IRubyObject applicationContext(IRubyObject self, IRubyObject[] args, Block block)
	{
		return self.getRuntime().getGlobalVariables().get(APPLICATIONCONTEXT_OBJECT_NAME);
	}

	public static IRubyObject scopedInstanceEval(IRubyObject self, IRubyObject script)
	{
		Ruby runtime = self.getRuntime();
		ThreadContext context = runtime.getCurrentContext();
		RubyThread thread = context.getThread();
		DynamicScope evalScope = new DynamicScope(new LocalStaticScope(null)); //context.getCurrentScope().cloneScope();
		context.pushScope(evalScope);
		IRubyObject binding = self.callMethod(context, "binding");
		thread.aset(RubySymbol.newSymbol(runtime, BINDING_TLD_NAME), binding);
		try {
			return self.callMethod(context, "instance_eval", script);
		} 
		finally {
			context.popScope();
		}
	}
	
	public static IRubyObject scopedInstanceEval(IRubyObject self, String script)
	{
		Ruby runtime = self.getRuntime();
		return scopedInstanceEval(self, RubyString.newUnicodeString(runtime, script));
	}

	public static IRubyObject evalWithApplicationContextBinding(IRubyObject self, String expr)
	{
		Ruby runtime = self.getRuntime();
		ThreadContext context = runtime.getCurrentContext();
		RubyThread thread = context.getThread();
		IRubyObject binding = thread.aref(RubySymbol.newSymbol(runtime, BINDING_TLD_NAME));
		IRubyObject[] args = new IRubyObject[binding == null? 1 : 2];
		args[0] = RubyString.newUnicodeString(runtime, expr);
		if (args.length > 1) args[1] = binding;
		return self.callMethod(context, "eval", args);

	}
}

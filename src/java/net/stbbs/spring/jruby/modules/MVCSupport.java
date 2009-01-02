package net.stbbs.spring.jruby.modules;

import java.util.HashMap;
import java.util.Map;

import net.stbbs.spring.jruby.SpringIntegratedJRubyRuntime;

import org.jruby.RubyProc;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

@Module
public class MVCSupport {
	private Map<String,RubyProc> controllers = new HashMap<String,RubyProc>();
	
	@ModuleMethod(arity=ModuleMethod.ARITY_TWO_ARGUMENTS)
	public IRubyObject registController(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block)
	{
		// 引数が足りない場合エラー
		if (args.length < 2) {
			throw self.getRuntime().newArgumentError("Method requires at least two arguments.");
		}
		String name = args[0].asString().getUnicodeValue();
		RubyProc proc = (RubyProc)args[1];
		synchronized (controllers) {
			controllers.put(name, proc);
		}
		return ruby.getNil();
	}
	
	@ModuleMethod(arity=ModuleMethod.ARITY_ONE_ARGUMENT)
	public IRubyObject getController(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block)
	{
		// 引数が足りない場合エラー
		if (args.length < 1) {
			throw ruby.newArgumentError("Method requires at least one argument.");
		}
		String name = args[0].asString().getUnicodeValue();
		synchronized (controllers) {
			return controllers.get(name);
		}
	}

	@ModuleMethod(arity=ModuleMethod.ARITY_ONE_ARGUMENT)
	public IRubyObject removeController(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block)
	{
		// 引数が足りない場合エラー
		if (args.length < 1) {
			throw ruby.newArgumentError("Method requires at least one argument.");
		}
		String name = args[0].asString().getUnicodeValue();
		synchronized (controllers) {
			controllers.remove(name);
		}
		return ruby.getNil();
	}

	public RubyProc getController(String name)
	{
		synchronized (controllers) {
			return controllers.get(name);
		}
	}

}

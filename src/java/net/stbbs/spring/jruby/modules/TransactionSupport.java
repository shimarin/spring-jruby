package net.stbbs.spring.jruby.modules;


import org.jruby.Ruby;
import org.jruby.RubyString;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

public class TransactionSupport {

	private Ruby runtime;
	private IRubyObject self;
	
	public TransactionSupport(Ruby runtime, IRubyObject self)
	{
		this.self = self;
		this.runtime = runtime;
	}

	protected PlatformTransactionManager getTransactionManager()
	{
		IRubyObject t = self.callMethod(runtime.getCurrentContext(), "eval", RubyString.newUnicodeString(runtime, "transactionManager"));
		return (PlatformTransactionManager)JavaEmbedUtils.rubyToJava(runtime, t, PlatformTransactionManager.class);
	}

	@JRubyMethod
	public IRubyObject withTransaction(IRubyObject self, IRubyObject[] args, Block block) {
		PlatformTransactionManager txManager = getTransactionManager();
		TransactionStatus status = txManager.getTransaction(null);
		IRubyObject ret;
		try {
			ret = block.call(runtime.getCurrentContext(), new IRubyObject[] {JavaEmbedUtils.javaToRuby(self.getRuntime(), status)});
			txManager.commit(status);
		}
		catch (RuntimeException ex) {
			if (!status.isCompleted()) txManager.rollback(status);
			throw ex;
		}
		return ret;
	}

	@JRubyMethod
	public IRubyObject withRollbackOnlyTransaction(IRubyObject self, IRubyObject[] args, Block block) {
		PlatformTransactionManager txManager = getTransactionManager();
		TransactionStatus status = txManager.getTransaction(null);
		IRubyObject ret;
		try {
			status.setRollbackOnly();
			ret = block.call(runtime.getCurrentContext(), new IRubyObject[] {JavaEmbedUtils.javaToRuby(self.getRuntime(), status)});
		}
		finally {
			if (!status.isCompleted()) txManager.rollback(status);
		}
		return ret;
	}


}

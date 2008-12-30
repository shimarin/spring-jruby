package net.stbbs.spring.jruby.modules;

import net.stbbs.spring.jruby.SpringIntegratedJRubyRuntime;

import org.jruby.RubyModule;
import org.jruby.RubyString;
import org.jruby.RubySymbol;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

public class TransactionSupport {

	protected PlatformTransactionManager getTransactionManager(SpringIntegratedJRubyRuntime ruby,IRubyObject self)
	{
		return ruby.getComponent(self, "transactionManager");
	}

	@ModuleMethod(arity=ModuleMethod.ARITY_NO_ARGUMENTS)
	public IRubyObject withTransaction(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) {
		PlatformTransactionManager txManager = getTransactionManager(ruby, self);
		TransactionStatus status = txManager.getTransaction(null);
		IRubyObject ret;
		try {
			ret = block.call(ruby.getCurrentContext(), new IRubyObject[] {JavaEmbedUtils.javaToRuby(self.getRuntime(), status)});
			txManager.commit(status);
		}
		catch (RuntimeException ex) {
			txManager.rollback(status);
			throw ex;
		}
		return ret;
	}

	@ModuleMethod(arity=ModuleMethod.ARITY_NO_ARGUMENTS)
	public IRubyObject withRollbackOnlyTransaction(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) {
		PlatformTransactionManager txManager = getTransactionManager(ruby, self);
		TransactionStatus status = txManager.getTransaction(null);
		IRubyObject ret;
		try {
			status.setRollbackOnly();
			ret = block.call(ruby.getCurrentContext(), new IRubyObject[] {JavaEmbedUtils.javaToRuby(self.getRuntime(), status)});
		}
		finally {
			txManager.rollback(status);
		}
		return ret;
	}


}

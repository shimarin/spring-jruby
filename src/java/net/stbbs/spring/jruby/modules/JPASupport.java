package net.stbbs.spring.jruby.modules;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import net.stbbs.spring.jruby.SpringIntegratedJRubyRuntime;

import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

public class JPASupport {

	@ModuleMethod(arity=ModuleMethod.ARITY_NO_ARGUMENTS)
	public IRubyObject withEntityManager(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) {
		IRubyObject sf = self.callMethod(ruby.getCurrentContext(), "entityManagerFactory");
		EntityManager em = ((EntityManagerFactory)ruby.toJava(sf)).createEntityManager();
		try {
			return block.call(ruby.getCurrentContext(), new IRubyObject[]{ruby.toRuby(em)});
		}
		finally {
			em.close();
		}
	}

}

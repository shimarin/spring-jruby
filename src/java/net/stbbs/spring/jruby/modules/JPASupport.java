package net.stbbs.spring.jruby.modules;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.jruby.Ruby;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

public class JPASupport {

	@JRubyMethod
	public IRubyObject withEntityManager(IRubyObject self, IRubyObject[] args, Block block) {
		Ruby runtime = self.getRuntime();
		IRubyObject sf = self.callMethod(runtime.getCurrentContext(), "entityManagerFactory");
		EntityManager em = ((EntityManagerFactory)JavaEmbedUtils.rubyToJava(runtime, sf, EntityManagerFactory.class)).createEntityManager();
		try {
			return block.call(runtime.getCurrentContext(), new IRubyObject[]{JavaEmbedUtils.javaToRuby(runtime, em)});
		}
		finally {
			em.close();
		}
	}

}

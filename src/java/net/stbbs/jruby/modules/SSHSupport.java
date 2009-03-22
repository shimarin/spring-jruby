package net.stbbs.jruby.modules;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import net.stbbs.jruby.Decorator;
import net.stbbs.jruby.Util;

import org.jruby.Ruby;
import org.jruby.RubyHash;
import org.jruby.RubyModule;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class SSHSupport {
	public static void onRegister(RubyModule module)
	{
		Ruby runtime = module.getRuntime();
		Util.registerDecorator(runtime, JSchDecorator.class);
		Util.registerDecorator(runtime, JSchSessionDecorator.class);
		JSch.setConfig("StrictHostKeyChecking", "no");
	}

	@JRubyMethod
	public JSch newJSch(IRubyObject self, IRubyObject[] args, Block block) throws JSchException
	{
		JSch jsch = new JSch();
		String sep = System.getProperty("file.separator");
		File idrsa = new File(System.getProperty("user.home") + sep + ".ssh" + sep + "id_rsa");
		if (idrsa.exists()) {
			jsch.addIdentity(idrsa.toString());
		}
		return jsch; 
	}

	@Decorator(JSch.class)
	static public class JSchDecorator {

		@JRubyMethod(required=2)
		public void withSession(IRubyObject self, IRubyObject[] args, Block block) throws JSchException
		{
			Ruby runtime = self.getRuntime();
			String user = args.length > 0? args[0].asString().getUnicodeValue() : null;
			String host = args.length > 1? args[1].asString().getUnicodeValue() : null;
			if (user == null || host == null) {
				throw runtime.newArgumentError("Both :user and :host is required.");
			}
			RubyHash options = args.length > 2? (RubyHash)args[2] : null;
			Integer port = options != null? Util.getOptionInteger(options, "port") : null; 
			String password = options != null? Util.getOptionString(options, "password") : null;

			JSch jsch = (JSch)JavaEmbedUtils.rubyToJava(runtime, self, JSch.class);
			Session session;
			if (port == null) {
				session = jsch.getSession(user, host);
			} else {
				session = jsch.getSession(user, host, port);
			}
			if (password != null) {
				session.setPassword(password);
			}
			session.connect();
			try {
				if (block.isGiven()) {
					block.call(
						runtime.getCurrentContext(), 
						new IRubyObject[] {JavaEmbedUtils.javaToRuby(runtime, session)});
				}
			}
			finally {
				session.disconnect();
			}
		}
	}

	@Decorator(Session.class)
	static public class JSchSessionDecorator {

		@JRubyMethod(required=1)
		public int exec(IRubyObject self, IRubyObject[] args, Block block) throws JSchException, IOException
		{
			Ruby runtime = self.getRuntime();
			Session session = (Session)JavaEmbedUtils.rubyToJava(runtime, self, Session.class);
			ChannelExec channel = (ChannelExec)session.openChannel("exec");
			channel.setCommand(args[0].asString().getUnicodeValue());
			channel.connect();
			try {
				InputStream is = channel.getInputStream();
				try {
					if (block.isGiven()) {
						block.call(runtime.getCurrentContext(), 
							new IRubyObject[] {JavaEmbedUtils.javaToRuby(runtime, is)});
					}
				}
				finally {
					is.close();
				}
			}
			finally {
				channel.disconnect();
			}
			return channel.getExitStatus();
		}
	}

}

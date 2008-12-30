package net.stbbs.spring.jruby.modules;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import net.stbbs.spring.jruby.SpringIntegratedJRubyRuntime;

import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMailMessage;

@Module
public class MailSupport {
	@ModuleMethod(arity=ModuleMethod.ARITY_NO_ARGUMENTS)
	public IRubyObject createJisMailMessage(
		SpringIntegratedJRubyRuntime ruby, IRubyObject self, IRubyObject[] args, Block block) throws MessagingException
	{
		JavaMailSender mailSender = ruby.getComponent(self, "mailSender");
		return ruby.toRuby(new JisMailMessage(mailSender));
	}
	
	@ModuleMethod(arity=ModuleMethod.ARITY_ONE_REQUIRED)
	public IRubyObject sendMailMessage(
		SpringIntegratedJRubyRuntime ruby, IRubyObject self, IRubyObject[] args, Block block) throws MessagingException
	{
		JavaMailSender mailSender = ruby.getComponent(self, "mailSender");
		Object o = ruby.toJava(args[0]);
		MimeMessage mm;
		if (o instanceof MimeMailMessage) {
			mm = ((MimeMailMessage)o).getMimeMessage();
		} else {
			mm = (MimeMessage)o;
		}
		mailSender.send(mm);
		return ruby.getNil();
	}
}

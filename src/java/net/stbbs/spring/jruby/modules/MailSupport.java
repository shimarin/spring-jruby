package net.stbbs.spring.jruby.modules;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.jruby.Ruby;
import org.jruby.anno.JRubyMethod;
import org.jruby.anno.JRubyModule;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMailMessage;

@JRubyModule(name="MaiSupport")
public class MailSupport {
	private JavaMailSender _mailSender;
	private Ruby runtime;
	private IRubyObject self;

	public MailSupport(Ruby runtime, IRubyObject self)
	{
		this.runtime = runtime;
		this.self = self;
	}
	
	protected JavaMailSender getMailSender()
	{
		if (_mailSender == null) {
			IRubyObject cf = self.callMethod(runtime.getCurrentContext(), "mailSender");
			_mailSender = (JavaMailSender)JavaEmbedUtils.rubyToJava(runtime, cf, JavaMailSender.class);

		}
		return _mailSender;
	}
	
	@JRubyMethod
	public JisMailMessage createJisMailMessage(
		IRubyObject self, IRubyObject[] args, Block block) throws MessagingException
	{
		return new JisMailMessage(getMailSender());
	}
	
	@JRubyMethod(required=1)
	public void sendMailMessage(
		IRubyObject self, IRubyObject[] args, Block block) throws MessagingException
	{
		Object o = JavaEmbedUtils.rubyToJava(runtime, args[0], null);
		MimeMessage mm;
		if (o instanceof MimeMailMessage) {
			mm = ((MimeMailMessage)o).getMimeMessage();
		} else {
			mm = (MimeMessage)o;
		}
		getMailSender().send(mm);
	}
}

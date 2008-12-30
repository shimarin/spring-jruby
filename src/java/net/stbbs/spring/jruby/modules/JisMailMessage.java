package net.stbbs.spring.jruby.modules;

import javax.mail.MessagingException;

import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMailMessage;

public class JisMailMessage extends MimeMailMessage {

	static final String ENCODING = "iso-2022-jp";
	
	public JisMailMessage(JavaMailSender mailSender) throws MessagingException
	{
		super(mailSender.createMimeMessage());
		this.getMimeMessage().setHeader("Content-Transfer-Encoding", "7bit");
	}
	
	public void setSubject(String subject)
	{
		try {
			this.getMimeMessage().setSubject(subject, ENCODING);
		} catch (MessagingException e) {
			throw new MailParseException(e);
		}
	}
	
	public void setText(String text)
	{
		try {
			this.getMimeMessage().setText(text, ENCODING);
		} catch (MessagingException e) {
			throw new MailParseException(e);
		}
	}
	
}

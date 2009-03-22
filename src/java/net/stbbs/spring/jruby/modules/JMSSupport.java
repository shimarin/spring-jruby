package net.stbbs.spring.jruby.modules;

import javax.jms.JMSException;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;

import org.jruby.Ruby;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

public class JMSSupport {
	static private Object mutex = new Object();
	static private TopicConnection topicConnection = null;
	private Ruby runtime;
	private IRubyObject self;
	
	public JMSSupport(Ruby runtime, IRubyObject self)
	{
		this.runtime = runtime;
		this.self = self;
	}
	
	protected TopicConnection getTopicConnection() throws JMSException
	{
		synchronized(mutex) {
			if (topicConnection == null) {
				IRubyObject cf = self.callMethod(runtime.getCurrentContext(), "connectionFactory");
				TopicConnectionFactory connectionFactory = (TopicConnectionFactory)JavaEmbedUtils.rubyToJava(runtime, cf, TopicConnectionFactory.class);
				topicConnection = connectionFactory.createTopicConnection();
				topicConnection.start();
			}
			return topicConnection;
		}
	}
	
	public Object withTopicSession(IRubyObject self, IRubyObject[] args, Block block)
	{
		// createTopicSession
		// block call
		// topicSession.close
		return null;
	}
	
	public Object createTopicSession(IRubyObject self, IRubyObject[] args, Block block)
	{
		//s = tc.createTopicSession(false,Java::javax.jms.TopicSession::AUTO_ACKNOWLEDGE)
		// withPublisher 特異メソッドを追加
		// p = s.createPublisher(s.createTopic("topic"))
		// pにpublish特異メソッドを追加
		// p.publish(s.createTextMessage("ほげ"))
		// createSubscriber特異メソッドを追加
		// sにsetMessageListener特異メソッドを追加
		return null;
	}
}

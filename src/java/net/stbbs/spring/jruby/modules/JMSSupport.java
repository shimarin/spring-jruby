package net.stbbs.spring.jruby.modules;

import javax.jms.JMSException;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;

import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

import net.stbbs.spring.jruby.SpringIntegratedJRubyRuntime;

public class JMSSupport {

	private TopicConnection topicConnection = null;
	
	synchronized protected TopicConnection getTopicConnection(SpringIntegratedJRubyRuntime ruby, IRubyObject self) throws JMSException
	{
		if (topicConnection == null) {
			TopicConnectionFactory connectionFactory = ruby.getComponent(self, "connectionFactory");
			topicConnection = connectionFactory.createTopicConnection();
			topicConnection.start();
		}
		return topicConnection;
	}
	
	public Object withTopicSession(SpringIntegratedJRubyRuntime ruby, IRubyObject self, IRubyObject[] args, Block block)
	{
		// createTopicSession
		// block call
		// topicSession.close
		return null;
	}
	
	public Object createTopicSession(SpringIntegratedJRubyRuntime ruby, IRubyObject self, IRubyObject[] args, Block block)
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

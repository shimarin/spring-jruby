package net.stbbs.spring.jruby;

import java.io.IOException;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.web.context.support.AbstractRefreshableWebApplicationContext;

public class JRubyWebApplicationContext extends
		AbstractRefreshableWebApplicationContext {

	@Override
	protected void loadBeanDefinitions(DefaultListableBeanFactory lbf)
			throws IOException, BeansException {
		// TODO Auto-generated method stub

	}

}

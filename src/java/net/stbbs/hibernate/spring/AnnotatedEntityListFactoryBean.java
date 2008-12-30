package net.stbbs.hibernate.spring;

import java.util.Collection;
import javax.persistence.Entity;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.opensymphony.xwork2.util.ResolverUtil;

public class AnnotatedEntityListFactoryBean 
	extends AbstractFactoryBean {

	private String[] packages;
	
	public void setPackages(String[] packages) {
		this.packages = packages;
	}
	
	public void setPackage(String pkg)
	{
		this.packages = new String[] { pkg };
	}

	@Override
	protected Object createInstance() throws Exception {
		ResolverUtil<Class> resolver = new ResolverUtil<Class>();
		resolver.findAnnotated(Entity.class, packages);
		return resolver.getClasses();
	}

	@Override
	public Class getObjectType() {
		return Collection.class;
	}

}

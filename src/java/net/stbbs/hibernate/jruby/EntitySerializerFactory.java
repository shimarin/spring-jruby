package net.stbbs.hibernate.jruby;

public class EntitySerializerFactory extends net.stbbs.hibernate.EntitySerializerFactory {
	@Override
	public EntitySerializer newInstance()
	{
		return new EntitySerializer();
	}
}

load "net/stbbs/spring/jruby/instanceEvalServlet.rb"

BlazeDSConfig = {
	:remoting=>{
		:endpoint_url=>"/messagebroker/amf",
		:destinations=>{
			:myBeaso=>{:bean=>"myBean", :exclude_methods=>[:hoge]},
			:myBean2=>{:bean=>"myBean2", :exclude_methods=>[:honya]}
		}
	}
}

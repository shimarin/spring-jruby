include_resource "classpath:net/stbbs/spring/jruby/instanceEvalServlet.rb"

BlazeDSConfig = {
	:remoting=>{
		:endpoint_url=>"/messagebroker/amf",
		:destinations=>{
			:myBeaso=>{:bean=>"myBean", :exclude_methods=>[:hoge]},
			:myBean2=>{:bean=>"myBean2", :exclude_methods=>[:honya]}
		}
	}
}

# return true/false/nil
def instance_eval_servlet_host_check(hostname, ipaddress)
end

# return true/false
def instance_eval_servlet_authentication(username, password)
end

def webapplication_init
  p "webapplication_init"
end
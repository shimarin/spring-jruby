include ApplicationContextSupport
include TransactionSupport if defined? TransactionSupport
include SQLSupport if defined? SQLSupport
include HibernateSupport if defined? HibernateSupport
include MailSupport if defined? MailSupport
include VelocitySupport if defined? VelocitySupport

if defined? GraphicsSupport then
	include GraphicsSupport
	include_class "java.awt.Color"
	include_class "java.awt.BasicStroke"
end 

include DbUnitSupport if defined? DbUnitSupport
include MVCSupport if defined? MVCSupport
include POISupport if defined? POISupport
include MIDISupport if defined? MIDISupport
include SSHSupport if defined? SSHSupport
include Dom4jSupport if defined? Dom4jSupport
include RequestContextSupport if defined? RequestContextSupport
include PHPRPCSupport if defined? PHPRPCSupport

if defined? BlazeDSSupport then
	include BlazeDSSupport
	remoting_endpoint_url "/messagebroker/amf"
	destination :myBean
end

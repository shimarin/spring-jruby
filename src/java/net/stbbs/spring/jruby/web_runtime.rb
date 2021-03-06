java_module Java::net.stbbs.jruby.modules.InputStreamSupport
java_module Java::net.stbbs.jruby.modules.JavaTypeSupport
java_module Java::net.stbbs.jruby.modules.DOMSupport
java_module Java::net.stbbs.jruby.modules.DateSupport
java_module Java::net.stbbs.jruby.modules.MIDISupport
java_module Java::net.stbbs.jruby.modules.GraphicsSupport
java_module Java::net.stbbs.jruby.modules.URLConnectionSupport
java_module Java::net.stbbs.jruby.modules.JRubyRuntimeSupport
java_module "net.stbbs.jruby.modules.POISupport", "org.apache.poi.hssf.usermodel.HSSFWorkbook"
java_module "net.stbbs.jruby.modules.ICUSupport", "com.ibm.icu.text.Normalizer"
java_module "net.stbbs.jruby.modules.Dom4jSupport", "org.dom4j.Document"
java_module "net.stbbs.jruby.modules.PHPRPCSupport", "org.phprpc.PHPRPC_Client"
java_module "net.stbbs.jruby.modules.SSHSupport", "com.jcraft.jsch.JSch"
java_module "net.stbbs.jruby.modules.JSONSupport", "net.arnx.jsonic.JSON"
java_module "net.stbbs.jruby.modules.CSVSupport", ["spiffy.core.util.HashMapBuilder", "org.supercsv.io.ICsvWriter"]
java_module Java::net.stbbs.spring.jruby.modules.RequestContextSupport, "org.springframework.web.context.request.RequestContextHolder"
java_module Java::net.stbbs.spring.jruby.modules.JNDISupport
java_module Java::net.stbbs.spring.jruby.modules.DownloadSupport
java_module Java::net.stbbs.spring.jruby.modules.SQLSupport, "org.springframework.jdbc.core.JdbcTemplate"
java_module Java::net.stbbs.spring.jruby.modules.TransactionSupport, "org.springframework.transaction.PlatformTransactionManager"
java_module "net.stbbs.spring.jruby.modules.BlazeDSSupport", "flex.messaging.Destination"
java_module "net.stbbs.spring.jruby.modules.HibernateSupport", "org.hibernate.SessionFactory"
java_module "net.stbbs.spring.jruby.modules.HibernateAnnotationsSupport", ["org.hibernate.cfg.AnnotationConfiguration", "javax.persistence.Entity"]
java_module "net.stbbs.spring.jruby.modules.JasperReportsSupport", ["net.sf.jasperreports.engine.JasperReport","net.sf.jasperreports.engine.export.oasis.JROdsExporter", "org.apache.commons.digester.Digester"]
java_module "net.stbbs.spring.jruby.modules.DbUnitSupport", ["org.dbunit.IDatabaseTester","org.apache.poi.hssf.usermodel.HSSFWorkbook"]
java_module "net.stbbs.spring.jruby.modules.MySQLSupport", "com.mysql.jdbc.jdbc2.optional.MysqlDataSource"
java_module "net.stbbs.spring.jruby.modules.MailSupport", "javax.mail.internet.MimeMessage"
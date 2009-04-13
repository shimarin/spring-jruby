package net.stbbs.spring.jruby.modules;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import net.sf.jasperreports.engine.JRAbstractExporter;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRExporterParameter;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.export.JRHtmlExporter;
import net.sf.jasperreports.engine.export.JRRtfExporter;
import net.sf.jasperreports.engine.export.JRTextExporter;
import net.sf.jasperreports.engine.export.JRXlsExporter;
import net.sf.jasperreports.engine.export.oasis.JROdsExporter;
import net.sf.jasperreports.engine.export.oasis.JROdtExporter;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.stbbs.jruby.Util;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyModule;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.DataSourceUtils;

public class JasperReportsSupport {
	
	static public void onRegister(RubyModule module)
	{
		Ruby runtime = module.getRuntime();
		Util.registerDecorator(runtime, Resource.class, ResourceDecorator.class);
		Util.registerDecorator(runtime, JasperDesign.class, JasperDesignDecorator.class);
		Util.registerDecorator(runtime, JasperReport.class, JasperReportDecorator.class);
		Util.registerDecorator(runtime, JasperPrint.class, JasperPrintDecorator.class);
	}
	
	static public class ResourceDecorator {
		private Resource resource;
		
		public ResourceDecorator(Resource resource)
		{
			this.resource = resource;
		}
		
		@JRubyMethod
		public JasperDesign loadJasperDesign(IRubyObject self, IRubyObject[] args, Block block) throws IOException, JRException
		{
			Ruby runtime = self.getRuntime();
			InputStream is = resource.getInputStream();
			JasperDesign design;
			try {
				design = JRXmlLoader.load(is);
				if (block != null && block.isGiven()) {
					block.call(runtime.getCurrentContext(), new IRubyObject[] {JavaEmbedUtils.javaToRuby(runtime, design)});
				}
			}
			finally {
				is.close();
			}
			return design;
		}

		@JRubyMethod
		public JasperReport compileJasperDesign(IRubyObject self, IRubyObject[] args, Block block) throws IOException, JRException
		{
			Ruby runtime = self.getRuntime();
			JasperDesign design = loadJasperDesign(self, args, null);
			return JasperCompileManager.compileReport(design);
		}
	}
	
	static public class JasperDesignDecorator {
		private JasperDesign design;
		public JasperDesignDecorator(JasperDesign design)
		{
			this.design = design;
		}
	
		@JRubyMethod
		public JasperReport compile(IRubyObject self, IRubyObject[] args, Block block) throws JRException
		{
			return JasperCompileManager.compileReport(design);
		}
		
		@JRubyMethod
		public byte[] compileAsByteArray(IRubyObject self, IRubyObject[] args, Block block) throws JRException
		{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			JasperCompileManager.compileReportToStream(design, baos);
			return baos.toByteArray();
		}
		
	}
	
	static public class JasperReportDecorator {
		private JasperReport report;
		public JasperReportDecorator(JasperReport report)
		{
			this.report = report;
		}
		
		@JRubyMethod
		public JasperPrint fillReport(IRubyObject self, IRubyObject[] args, Block block) throws JRException
		{
			// paramaters,datasource
			Ruby runtime = self.getRuntime();
			JasperPrint print;
			Map parameters = new HashMap();
			if (args.length > 0) {
				Map<String,IRubyObject> hash = Util.convertRubyHash(args[0].convertToHash());
				for (Map.Entry<String, IRubyObject> entry : hash.entrySet()) {
					parameters.put(entry.getKey(), JavaEmbedUtils.rubyToJava(runtime, entry.getValue(), null));
				}
			}
			Object jo = args.length > 1? JavaEmbedUtils.rubyToJava(runtime, args[1], null) : DataSourceSupport.getDataSource(self);
			if (jo instanceof JRDataSource) {
				print = JasperFillManager.fillReport(report, parameters, (JRDataSource)jo);
			} else if (jo instanceof DataSource){
				DataSource ds = (DataSource)jo;
				Connection connection = DataSourceUtils.getConnection(ds);
				try {
					print = JasperFillManager.fillReport(report, parameters, connection);
				}
				finally {
					DataSourceUtils.releaseConnection(connection, ds);
				}
			} else if (args[0] instanceof RubyArray) {
				// TODO: convert contents
				JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource((RubyArray)args[0]);
				print = JasperFillManager.fillReport(report, parameters, dataSource);
			} else if (jo instanceof Collection) {
				JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource((Collection)jo);
				print = JasperFillManager.fillReport(report, parameters, dataSource);
			} else {
				throw runtime.newArgumentError("Argument 1 must be JRDataSource or DataSource");
			}
		    
		    return print;
		}
		
	}
	
	static public class JasperPrintDecorator {
		private JasperPrint print;
		
		public JasperPrintDecorator(JasperPrint print)
		{
			this.print = print;
		}
		
		@JRubyMethod
		public byte[] export(IRubyObject self, IRubyObject[] args, Block block) throws JRException
		{
			Ruby runtime = self.getRuntime();
			String format = "pdf";
			if (args.length > 0) {
				format = args[0].asString().getUnicodeValue();
			}
			OutputStream os = null;
			if (args.length > 1) {
				os = (OutputStream) JavaEmbedUtils.rubyToJava(runtime, args[1], OutputStream.class);
			}
			ByteArrayOutputStream baos = (os == null)? new ByteArrayOutputStream() : null;
			if (os == null) os = baos;
			
			if ("pdf".equals(format)) {
				try {
					Class.forName("com.lowagie.text.DocumentException");
				}
				catch (ClassNotFoundException ex) {
					throw runtime.newRuntimeError("iText-*.jar is missing in your classpath.");
				}
				JasperExportManager.exportReportToPdfStream(print, os);
			} else if ("xml".equals(format)) {
				JasperExportManager.exportReportToXmlStream(print, os);
			} else {
				JRAbstractExporter exporter;
				if ("html".equals(format)){
					exporter = new JRHtmlExporter();
				} else if ("csv".equals(format)){
					exporter = new JRCsvExporter();
				} else if ("ods".equals(format)){
					exporter = new JROdsExporter(); 
				} else if ("odt".equals(format)){
					exporter = new JROdtExporter(); 
				} else if ("rtf".equals(format)){
					exporter = new JRRtfExporter();
				} else if ("text".equals(format) || "txt".equals(format)){
					exporter = new JRTextExporter();
				} else if ("xls".equals(format)){
					exporter = new JRXlsExporter();
				} else {
					throw runtime.newArgumentError("Wrong export format:" + format);
				}
				exporter.setParameter(JRExporterParameter.JASPER_PRINT, print);
				exporter.setParameter(JRExporterParameter.OUTPUT_STREAM, os);
				if (block.isGiven()) {
					block.call(runtime.getCurrentContext(), new IRubyObject[] {JavaEmbedUtils.javaToRuby(runtime, exporter)});
				}
				exporter.exportReport();
			}
			
			if (baos != null) {
				return baos.toByteArray();
			}
			// else
			return null;
		}
	}
}

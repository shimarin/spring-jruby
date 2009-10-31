package net.stbbs.jruby.modules;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import net.stbbs.jruby.Util;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyHash;
import org.jruby.RubyModule;
import org.jruby.RubyNumeric;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.supercsv.io.AbstractCsvWriter;
import org.supercsv.io.AbstractCsvWriter;
import org.supercsv.io.CsvBeanWriter;
import org.supercsv.io.ICsvBeanWriter;
import org.supercsv.io.ICsvMapWriter;
import org.supercsv.prefs.CsvPreference;
public class CSVSupport {
	
	public static void onRegister(RubyModule module)
	{
		Ruby runtime = module.getRuntime();
		Util.registerDecorator(runtime, AbstractCsvWriter.class, AbstractCsvWriterDecorator.class);
	}
	
	@JRubyMethod
	public AbstractCsvWriter newCSVWriter(IRubyObject self, IRubyObject[] args,Block block)
	{
		Ruby runtime = self.getRuntime();
		if (args.length < 1) {
			throw self.getRuntime().newArgumentError(1, args.length);
		}
		Writer writer = (Writer)JavaEmbedUtils.rubyToJava(runtime, args[0], null);
		
		return new CsvBeanWriter(writer, CsvPreference.EXCEL_PREFERENCE);
		/*
		prefs = org.supercsv.prefs.CsvPreference.new( '"'.to_java_char , ','.to_java_char , "\r\n" )
		baos = java.io.ByteArrayOutputStream.new
		writer = java.io.OutputStreamWriter.new(baos, "MS932")
		csvwriter = org.supercsv.io.CsvBeanWriter.new(writer, prefs)
		*/
	}

	static public class AbstractCsvWriterDecorator {
		private AbstractCsvWriter csvWriter;
		private String[] nameMapping;
		public AbstractCsvWriterDecorator(AbstractCsvWriter csvWriter)
		{
			this.csvWriter = csvWriter;
		}
		
		@JRubyMethod(required=1, name="<<")
		public AbstractCsvWriter _write(IRubyObject self, IRubyObject[] args,Block block) throws IOException
		{
			Ruby runtime = self.getRuntime();
			if (args.length < 1) {
				throw self.getRuntime().newArgumentError(1, args.length);
			}
			IRubyObject row = args[0];
			if (row instanceof RubyHash) {
				Map<String, IRubyObject> map = Util.convertRubyHash((RubyHash)row);
				((ICsvMapWriter)csvWriter).write(map, nameMapping);
			} else {
				((ICsvBeanWriter)csvWriter).write(JavaEmbedUtils.rubyToJava(runtime, row, null), nameMapping);
			}
			return csvWriter;
		}
		
		@JRubyMethod(required=1, name="name_mapping=")
		public String[] _let_name_mipping(IRubyObject self, IRubyObject[] args,Block block)
		{
			Ruby runtime = self.getRuntime();
			if (args.length < 1) {
				throw self.getRuntime().newArgumentError(1, args.length);
			}
			RubyArray arr = args[0].convertToArray();
			int len = arr.getLength();
			nameMapping = new String[len];
			for (int i = 0; i < len; i++) {
				nameMapping[i] = arr.at(RubyNumeric.int2fix(runtime, i)).asString().getUnicodeValue();
			}
			return nameMapping;
		}
		
	}
}

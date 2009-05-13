package net.stbbs.jruby.modules;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;

import net.stbbs.jruby.Decorator;
import net.stbbs.jruby.Util;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFDataFormat;
import org.apache.poi.hssf.usermodel.HSSFRichTextString;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.jruby.Ruby;
import org.jruby.RubyFloat;
import org.jruby.RubyModule;
import org.jruby.RubyNumeric;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

public class POISupport {
	public static void onRegister(RubyModule module)
	{
		Ruby runtime = module.getRuntime();
		Util.registerDecorator(runtime, InputStream.class, InputStreamDecorator.class);
		Util.registerDecorator(runtime, HSSFSheetDecorator.class);
		Util.registerDecorator(runtime, HSSFWorkbookDecorator.class);
	}
	
	@JRubyMethod
	public IRubyObject workbook(IRubyObject self, IRubyObject[] args, Block block)
	{
		HSSFWorkbook wb = new HSSFWorkbook();
		// wbにdownload っていう特異メソッドをつける
		IRubyObject rwb = JavaEmbedUtils.javaToRuby(self.getRuntime(), wb);
		if (block.isGiven()) {
			block.call(
				self.getRuntime().getCurrentContext(), 
				new IRubyObject[] {rwb});
		}
		return rwb;
	}

	public static class InputStreamDecorator {
		private InputStream is;
		public InputStreamDecorator(InputStream is)
		{
			this.is = is;
		}
		
		@JRubyMethod
		public HSSFWorkbook loadExcelWorkbook(IRubyObject self, IRubyObject[] args, Block block) throws IOException
		{
			return new HSSFWorkbook(is);
		}
	}
	
	@Decorator(HSSFSheet.class)
	public static class HSSFSheetDecorator {
		private HSSFSheet sheet;
		private Ruby runtime;

		public HSSFSheetDecorator(Ruby runtime, HSSFSheet sheet)
		{
			this.sheet = sheet;
			this.runtime = runtime;
		}

		@JRubyMethod(name="[]", required=1)
		public IRubyObject getCell(IRubyObject self, IRubyObject[] args, Block block)
		{
			// 引数が足りない場合エラー
			if (args.length < 1) {
				throw runtime.newArgumentError("Method requires at least one argument.");
			}
			HSSFRow row = sheet.getRow(RubyNumeric.num2int(args[0]));
			if (args.length == 1 || row == null) {
				return JavaEmbedUtils.javaToRuby(runtime, row);
			}
			HSSFCell cell = row.getCell(RubyNumeric.num2int(args[1]));
			return JavaEmbedUtils.javaToRuby(runtime, cell);
		}

		@JRubyMethod(name="[]=", required=3)
		public IRubyObject setCell(IRubyObject self, IRubyObject[] args, Block block)
		{
			// 引数が足りない場合エラー
			if (args.length < 3) {
				throw runtime.newArgumentError("Method requires at least three arguments.");
			}
			IRubyObject rdsty = self.getInstanceVariable("@dateCellStyle");
			HSSFCellStyle dsty = rdsty.isNil()? null : (HSSFCellStyle)JavaEmbedUtils.rubyToJava(runtime, rdsty, HSSFCellStyle.class);
			int rownum = RubyNumeric.num2int(args[0]);
			HSSFRow row = sheet.getRow(rownum);
			if (row == null) {
				row = sheet.createRow(rownum);
			}
			int cellnum = RubyNumeric.num2int(args[1]);
			HSSFCell cell = row.getCell(cellnum);
			if (cell == null) {
				cell = row.createCell(cellnum);
			}
			IRubyObject v = args[2];
			Object vj = JavaEmbedUtils.rubyToJava(runtime, v, null);
			if (v instanceof RubyFloat) {
				cell.setCellValue(RubyFloat.num2dbl(v));
			} else if (v instanceof RubyNumeric) {
				cell.setCellValue(RubyNumeric.num2long(v));
			} else if (vj instanceof Date) {
				cell.setCellValue((Date)vj);
				cell.setCellStyle(dsty);
			} else if (vj instanceof Calendar) {
				cell.setCellValue((Calendar)vj);
				cell.setCellStyle(dsty);
			} else if (vj instanceof Boolean) {
				cell.setCellValue((Boolean)vj);
			} else {
				cell.setCellValue(new HSSFRichTextString(v.asString().getUnicodeValue()));
			}
			return v;
		}

	}

	@Decorator(HSSFWorkbook.class)
	public static class HSSFWorkbookDecorator {
		
		private HSSFWorkbook workbook;
		private Ruby runtime;
		
		public HSSFWorkbookDecorator(Ruby runtime, HSSFWorkbook workbook)
		{
			this.workbook = workbook;
			this.runtime = runtime;
		}
		
		@JRubyMethod
		public byte[] toByteArray(IRubyObject self, IRubyObject[] args, Block block) throws IOException 
		{
			// 作成したExcel文書をbyte[]に変換して返す
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			workbook.write(baos);
			return baos.toByteArray();
		}

		@JRubyMethod
		public IRubyObject worksheet(IRubyObject self, IRubyObject[] args, Block block) {
			HSSFSheet sheet = workbook.createSheet(args[0].asString().getUnicodeValue());
			IRubyObject rsheet = JavaEmbedUtils.javaToRuby(runtime, sheet);
			HSSFCellStyle dateCellStyle = workbook.createCellStyle();
			dateCellStyle.setDataFormat(HSSFDataFormat.getBuiltinFormat("m/d/yy"));
			rsheet.setInstanceVariable("@dateCellStyle", JavaEmbedUtils.javaToRuby(runtime, dateCellStyle));
			if (block.isGiven()) {
				block.call(runtime.getCurrentContext(), new IRubyObject[] {rsheet});
			}
			return rsheet;
		}
		
		@JRubyMethod(name="[]",required=1)
		public HSSFSheet get_sheet_by_index(IRubyObject self, IRubyObject[] args, Block block)
		{
			return workbook.getSheetAt((int)args[0].convertToInteger().getLongValue());
		}
	}
}

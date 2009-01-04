package net.stbbs.spring.jruby.modules;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

import net.stbbs.spring.jruby.DownloadContent;
import net.stbbs.spring.jruby.SpringIntegratedJRubyRuntime;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFDataFormat;
import org.apache.poi.hssf.usermodel.HSSFRichTextString;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.jruby.RubyFloat;
import org.jruby.RubyNumeric;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;

public class POISupport {
	@ModuleMethod(arity=ModuleMethod.ARITY_NO_ARGUMENTS)
	public IRubyObject workbook(final SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block)
	{
		HSSFWorkbook wb = new HSSFWorkbook();
		// wbにdownload っていう特異メソッドをつける
		IRubyObject rwb = ruby.toRuby(wb);
		rwb.getSingletonClass().defineMethod("download", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				HSSFWorkbook wb = (HSSFWorkbook)ruby.toJava(self);
				// 作成したExcel文書をbyte[]に変換して返す
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try {
					wb.write(baos);
				} catch (IOException e) {
					ruby.newIOError(e.getMessage());
				}
				return ruby.toRuby(new DownloadContent("application/vnd.ms-excel", baos.toByteArray()) );
			}

			public Arity getArity() {
				return Arity.NO_ARGUMENTS;
			}
		});
		rwb.getSingletonClass().defineMethod("worksheet", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				HSSFWorkbook wb = (HSSFWorkbook)ruby.toJava(self);
				HSSFSheet sheet = wb.createSheet(args[0].asString().getUnicodeValue());
				IRubyObject rsheet = ruby.toRuby(sheet);
				HSSFCellStyle dateCellStyle = wb.createCellStyle();
				dateCellStyle.setDataFormat(HSSFDataFormat.getBuiltinFormat("m/d/yy"));
				rsheet.setInstanceVariable("@dateCellStyle", ruby.toRuby(dateCellStyle));
				rsheet.getSingletonClass().defineMethod("[]", new Callback() {
					public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block)
					{
						// 引数が足りない場合エラー
						if (args.length < 1) {
							throw ruby.newArgumentError("Method requires at least one argument.");
						}
						HSSFSheet sheet = (HSSFSheet)ruby.toJava(self);
						HSSFRow row = sheet.getRow(RubyNumeric.num2int(args[0]));
						if (args.length == 1 || row == null) {
							return ruby.toRuby(row);
						}
						HSSFCell cell = row.getCell(RubyNumeric.num2int(args[1]));
						return ruby.toRuby(cell);
					}
					public Arity getArity() { return Arity.ONE_REQUIRED; }
				});
				rsheet.getSingletonClass().defineMethod("[]=", new Callback() {
					public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block)
					{
						// 引数が足りない場合エラー
						if (args.length < 3) {
							throw ruby.newArgumentError("Method requires at least three arguments.");
						}
						HSSFSheet sheet = (HSSFSheet)ruby.toJava(self);
						IRubyObject rdsty = self.getInstanceVariable("@dateCellStyle");
						HSSFCellStyle dsty = rdsty.isNil()? null : (HSSFCellStyle)ruby.toJava(rdsty);
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
						Object vj = ruby.toJava(v);
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
					public Arity getArity() { return Arity.THREE_REQUIRED; }
				});
				if (block.isGiven()) {
					block.call(ruby.getCurrentContext(), new IRubyObject[] {rsheet});
				}
				return rsheet;
			}

			public Arity getArity() {
				return Arity.ONE_ARGUMENT;
			}
		});
		if (block.isGiven()) {
			block.call(
				ruby.getCurrentContext(), 
				new IRubyObject[] {rwb});
		}
		return rwb;
	}
}

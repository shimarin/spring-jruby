package net.stbbs.spring.jruby.modules;

import java.io.UnsupportedEncodingException;
import java.util.List;

import net.stbbs.jruby.Util;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.servlet.ServletRequestContext;
import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyHash;
import org.jruby.RubyModule;
import org.jruby.RubyString;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class FileUploadSupport {

	public static void onRegister(RubyModule module)
	{
		Ruby runtime = module.getRuntime();
		Util.registerDecorator(runtime, FileItem.class, FileItemDecorator.class);
	}
	
	@JRubyMethod
	public RubyHash parseUplpadedFiles(IRubyObject self, IRubyObject[] args, Block block) throws FileUploadException, UnsupportedEncodingException
	{
		Ruby runtime = self.getRuntime();
		DiskFileItemFactory factory = new DiskFileItemFactory();
		ServletFileUpload fu = new ServletFileUpload();
		fu.setFileItemFactory(factory);
		ServletRequestContext rq = new ServletRequestContext(((ServletRequestAttributes)RequestContextHolder.getRequestAttributes()).getRequest());
		List<FileItem> items = fu.parseRequest(rq);
		RubyHash hash = new RubyHash(runtime);
		for (FileItem item:items) {
			RubyString fieldName = RubyString.newUnicodeString(runtime, item.getFieldName());
			
			IRubyObject ritem = item.isFormField()? RubyString.newUnicodeString(runtime, item.getString("UTF-8")) :
				JavaEmbedUtils.javaToRuby(runtime, item);
			if (hash.has_key(fieldName).isTrue()) {
				IRubyObject ref = hash.aref(fieldName);
				RubyArray arr = (ref instanceof RubyArray)? ((RubyArray)ref) : ref.convertToArray();  
				arr.append(ritem);
			} else {
				hash.aset(fieldName, ritem);
			}
			
		}
		return hash;
	}
	
	public static class FileItemDecorator {
		private FileItem fileItem;
		public FileItemDecorator(FileItem fileItem)
		{
			this.fileItem = fileItem;
		}
		
	}

}

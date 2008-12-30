package net.stbbs.spring.jruby;

import java.io.IOException;
import java.io.OutputStream;

public class DownloadContent {
	
	private String contentType = "text/plain";
	private byte[] content;

	public DownloadContent(String contentType, byte[] content)
	{
		this.contentType = contentType;
		this.content = content;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public void setContent(byte[] content) {
		this.content = content;
	}

	public String getContentType() {
		return contentType;
	}

	public int getContentLength() {
		return content.length;
	}

	public void out(OutputStream out) throws IOException {
		out.write(content);
	}

}

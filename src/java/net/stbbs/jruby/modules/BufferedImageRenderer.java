package net.stbbs.jruby.modules;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

public class BufferedImageRenderer {
	public static byte[] toByteArray(BufferedImage bufferedImage, String format) throws IOException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(bufferedImage, format, baos);
		return baos.toByteArray();
	}

}

package net.stbbs.spring.jruby.modules;

import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;

import org.jruby.runtime.builtin.IRubyObject;

import net.stbbs.spring.jruby.SpringIntegratedJRubyRuntime;

public class GraphicsFrame extends JFrame {

	private SpringIntegratedJRubyRuntime ruby;
	private BufferedImage buffer;
	private IRubyObject enhancedBuffer;
	
	protected GraphicsFrame(SpringIntegratedJRubyRuntime ruby, int width, int height)
	{
		buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		enhancedBuffer = GraphicsSupport.enhanceBuffer(ruby, buffer);
		this.setSize(width, height);
		this.setVisible(true);
	}
	
	public IRubyObject getBuffer() { return enhancedBuffer; }
	
	public void paint(Graphics g)
	{
		g.drawImage(buffer, 0, 0, null);
	}
	
}

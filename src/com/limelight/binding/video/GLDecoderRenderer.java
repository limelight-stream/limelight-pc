package com.limelight.binding.video;


import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureData;
import com.limelight.gui.StreamFrame;
import com.limelight.nvstream.av.video.VideoDepacketizer;
import com.limelight.nvstream.av.video.cpu.AvcDecoder;

import javax.media.opengl.*;
import javax.media.opengl.awt.GLCanvas;
import javax.swing.*;

import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.IntBuffer;


/**
 * Author: spartango
 * Date: 2/1/14
 * Time: 11:42 PM.
 */
public class GLDecoderRenderer extends AbstractCpuDecoder implements GLEventListener {
    protected BufferedImage image;
    protected int[]         imageBuffer;
    
    protected long lastRender = System.currentTimeMillis();

    protected final GLProfile      glprofile;
    protected final GLCapabilities glcapabilities;
    protected final GLCanvas       glcanvas;
    private         FPSAnimator    animator;
    protected       Texture        texture;
    protected       IntBuffer      bufferRGB;

    public GLDecoderRenderer() {
        GLProfile.initSingleton();
        glprofile = GLProfile.getDefault();
        glcapabilities = new GLCapabilities(glprofile);
        glcanvas = new GLCanvas(glcapabilities);
    }
    
    @Override
    public int getColorMode() {
        // Force the renderer to use a buffered image that's friendly with OpenGL
    	return AvcDecoder.NATIVE_COLOR_ARGB;
    }

    @Override
    public boolean setupInternal(Object renderTarget, int drFlags) {
        final StreamFrame frame = (StreamFrame) renderTarget;
        final JPanel renderingSurface = frame.getRenderingSurface();

        image = new BufferedImage(width, height,
                                  BufferedImage.TYPE_INT_ARGB);
        imageBuffer = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        bufferRGB = IntBuffer.wrap(imageBuffer);

        // Add canvas to the frame
        glcanvas.setSize(renderingSurface.getWidth(), renderingSurface.getHeight());
        glcanvas.addGLEventListener(this);

        for (MouseListener m : renderingSurface.getMouseListeners()) {
            glcanvas.addMouseListener(m);
        }

        for (KeyListener k : renderingSurface.getKeyListeners()) {
            glcanvas.addKeyListener(k);
        }

        for (MouseMotionListener m : renderingSurface.getMouseMotionListeners()) {
            glcanvas.addMouseMotionListener(m);
        }

        frame.setLayout(null);
        frame.add(glcanvas, 0, 0);

        animator = new FPSAnimator(glcanvas, targetFps);
        
        System.out.println("Using OpenGL rendering");
        
        return true;
    }

    public boolean start(VideoDepacketizer depacketizer) {
    	if (!super.start(depacketizer)) {
    		return false;
    	}
        animator.start();
        return true;
    }

    public void reshape(GLAutoDrawable glautodrawable, int x, int y, int width, int height) {
    }

    public void init(GLAutoDrawable glautodrawable) {
    }

    public void dispose(GLAutoDrawable glautodrawable) {
    }

    public void display(GLAutoDrawable glautodrawable) {
        // Decode the image
        boolean decoded = AvcDecoder.getRgbFrameInt(imageBuffer, imageBuffer.length);
        if (!decoded) {
            return;
        }

        GL2 gl = glautodrawable.getGL().getGL2();

        IntBuffer bufferRGB = IntBuffer.wrap(imageBuffer);
        gl.glEnable(GL.GL_TEXTURE_2D);
        // OpenGL only supports BGRA and RGBA, rather than ARGB or ABGR (from the buffer)
        // So we instruct it to read the packed RGB values in the appropriate (REV) order
        Texture texture = new Texture(gl,
                                      new TextureData(glprofile,
                                                      4,
                                                      width,
                                                      height,
                                                      0,
                                                      GL.GL_BGRA,
                                                      GL2GL3.GL_UNSIGNED_INT_8_8_8_8_REV,
                                                      false,
                                                      false,
                                                      true,
                                                      bufferRGB,
                                                      null)
        );
        texture.enable(gl);
        texture.bind(gl);

        gl.glBegin(GL2GL3.GL_QUADS);
        // This flips the texture as it draws it, as the opengl coordinate system is different
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(-1.0f, 1.0f, 1.0f); // Bottom Left Of The Texture and Quad

        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(1.0f, 1.0f, 1.0f); // Bottom Right Of The Texture and Quad

        gl.glTexCoord2f(1.0f, 1.0f);
        gl.glVertex3f(1.0f, -1.0f, 1.0f); // Top Right Of The Texture and Quad

        gl.glTexCoord2f(0.0f, 1.0f);
        gl.glVertex3f(-1.0f, -1.0f, 1.0f);

        gl.glEnd();
        texture.disable(gl);
        texture.destroy(gl);

        lastRender = System.currentTimeMillis();
    }

    /**
     * Stops the decoding and rendering of the video stream.
     */
    @Override
    public void stop() {
    	super.stop();
        animator.stop();
    }
}

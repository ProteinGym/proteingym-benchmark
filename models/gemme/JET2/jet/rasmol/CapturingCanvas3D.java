package jet.rasmol;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.media.j3d.*;
import javax.vecmath.*;
import com.sun.image.codec.jpeg.*;


public class CapturingCanvas3D extends Canvas3D  
{

    public boolean writeJPEG_;
    private int postSwapCount_;

    public CapturingCanvas3D(GraphicsConfiguration gc) 
    {
	super(gc);
	postSwapCount_ = 0;
    }
    public void capture() 
    { 
	writeJPEG_ = true; 
	repaint(); 
    }
    
    public void postSwap() 
    {
	//super.postSwap();
	if(writeJPEG_) 
	    {
		System.out.println("Writing JPEG");
		GraphicsContext3D  ctx = getGraphicsContext3D();
	
		Raster ras = new Raster(
					new Point3f(-1.0f,-1.0f,-1.0f),
					Raster.RASTER_COLOR,
					0,0,
					getWidth(),getHeight(),
					new ImageComponent2D(
							     ImageComponent.FORMAT_RGB,
							     new BufferedImage(1024,768,
									       BufferedImage.TYPE_INT_RGB)),
					null);
		
		ctx.readRaster(ras);
		
		// Now strip out the image info
		BufferedImage img = ras.getImage().getImage();
		
		// write that to disk....
		try 
		    {
			FileOutputStream out = new FileOutputStream("Capture"+postSwapCount_+".jpg");
			JPEGImageEncoder encoder = JPEGCodec.createJPEGEncoder(out);
			JPEGEncodeParam param = encoder.getDefaultJPEGEncodeParam(img);
			param.setQuality(0.9f,false); // 90% qualith JPEG
			encoder.setJPEGEncodeParam(param);
			encoder.encode(img);
			writeJPEG_ = false;
			out.close();
		    } 
		catch ( IOException e ) 
		    {
			System.out.println("JPEG file I/O exception!");
		    }
		postSwapCount_++;
		System.out.println("JPEG file written to disk");
	    }
    }
}

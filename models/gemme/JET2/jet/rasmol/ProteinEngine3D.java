package jet.rasmol;

import com.sun.j3d.utils.universe.*;
import com.sun.j3d.utils.behaviors.mouse.*;
import com.sun.j3d.utils.picking.*;
import javax.media.j3d.*;
import javax.vecmath.*;
import java.awt.GraphicsConfiguration;
import java.awt.event.*;
import java.util.*;
import com.sun.j3d.utils.geometry.*;
import java.awt.*;

public class ProteinEngine3D implements MouseListener
{
    SimpleUniverse simpleU;
    TransformGroup root;
    BranchGroup group;
    jet.rasmol.CapturingCanvas3D canvas3D;
    jet.rasmol.model.SqueletonModel squeleton;
    PickCanvas pickCanvas;
    int hits=0;
    Vector sequenceList,propertyList;

    public ProteinEngine3D(){}
    
    public ProteinEngine3D(Vector sequenceList, Vector propertyList) 
    {
	canvas3D=new jet.rasmol.CapturingCanvas3D(SimpleUniverse.getPreferredConfiguration());
	canvas3D.setSize(1024, 768);
	canvas3D.addMouseListener(this);	
	this.sequenceList=sequenceList;
	this.propertyList=propertyList;
	reset();
    }
 
    public Canvas3D getCanvas(){ return (Canvas3D)canvas3D;}

    public void capture(){ canvas3D.capture(); }

    public BranchGroup getVision() 
    {
	BranchGroup group = new BranchGroup();
	
	BoundingSphere bounds = new BoundingSphere();
	bounds.setRadius(1000.0);

	group.addChild(root);
      
        MouseRotate myMouseRotate = new MouseRotate();
        myMouseRotate.setTransformGroup(root);
        myMouseRotate.setSchedulingBounds(bounds);
        group.addChild(myMouseRotate);

        MouseTranslate myMouseTranslate = new MouseTranslate();
        myMouseTranslate.setTransformGroup(root);
        myMouseTranslate.setSchedulingBounds(bounds);
        group.addChild(myMouseTranslate);

        MouseZoom myMouseZoom = new MouseZoom();
        myMouseZoom.setTransformGroup(root);
        myMouseZoom.setSchedulingBounds(new BoundingSphere());
        group.addChild(myMouseZoom);
	
	AmbientLight lightA = new AmbientLight();
        lightA.setInfluencingBounds(bounds);
	group.addChild(lightA);
	
	
	DirectionalLight lightD1 = new DirectionalLight();
        lightD1.setDirection(new Vector3f(0.0f,5.0f,5.0f));
        lightD1.setInfluencingBounds(bounds);
        group.addChild(lightD1);

	DirectionalLight lightD2 = new DirectionalLight();
        lightD2.setDirection(new Vector3f(0.0f,-5.0f,5.0f));
        lightD2.setInfluencingBounds(bounds);
        group.addChild(lightD2);
	
        Background background = new Background();
        background.setColor(0.0f, 0.0f, 0.0f);
        background.setApplicationBounds(bounds);
        group.addChild(background);

	group.compile();

	pickCanvas = new PickCanvas(canvas3D, group); 
	pickCanvas.setMode(PickCanvas.BOUNDS); 

	return group;
    } 
   
    public void reset()
    {
	if(simpleU!=null) simpleU.cleanup();
	simpleU = new SimpleUniverse(canvas3D);

	root=new TransformGroup();
	root.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        root.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
	root.setCapability(TransformGroup.ALLOW_CHILDREN_WRITE);
	
	squeleton=new jet.rasmol.model.SqueletonModel(sequenceList,propertyList);
	
	root.addChild(squeleton);

	simpleU.getViewingPlatform().setNominalViewingTransform();
	simpleU.addBranchGraph(getVision());
    }
    
    public void updateAppearance()
    {
	squeleton.updateAppearance();
    }
	
    public void mouseClicked(MouseEvent e)
    { 
     	pickCanvas.setShapeLocation(e);
	PickResult result = pickCanvas.pickClosest();
	hits++;
	
	if (result == null) 
	    {

		System.out.println("Nothing picked");
	     
	    } 
	else 
	    {
		
		Primitive p = (Primitive)result.getNode(PickResult.PRIMITIVE);
		String info;
		Shape3D s = (Shape3D)result.getNode(PickResult.SHAPE3D);
		
		if (p != null) 
		    {
			info=squeleton.getResidueInfo((Sphere)p);
			System.out.println(info);	
		    } 
		else if (s != null) 
		    {
			
			System.out.println(s.getClass().getName()+" shape3d ");
			
		    } 
		else
		    {
			
			System.out.println("null");
			
		    } 
	 }
    }
    
    public void mouseEntered(MouseEvent e){}
     
    public void mouseExited(MouseEvent e){}
	
    public void mousePressed(MouseEvent e){}
        
    public void mouseReleased(MouseEvent e) {}
}

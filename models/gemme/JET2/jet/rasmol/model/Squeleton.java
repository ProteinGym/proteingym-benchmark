package jet.rasmol.model;

import com.sun.j3d.utils.geometry.*;
import com.sun.j3d.utils.universe.*;
import javax.media.j3d.*;
import javax.vecmath.*;
import java.util.*;

import jet.rasmol.SequenceAppearance;


public class Squeleton extends TransformGroup
{

    Vector sequenceContent;
    Vector sequenceProperty;
    Vector3f centerContent;
    Vector seqAp;
    Vector sphereIndex;

    public Squeleton()
    {
	super();
	initTG();
    }
    
    private void initCarbonAlphaCoordinates()
    {
	jet.data.DataEngine dataEngine=jet.data.DataEngine.instance();
	jet.data.datatype.Sequence3D sequence;
	jet.data.datatype.Residue3D residue;
	seqAp=dataEngine.getSequenceAppearance();
	
	Vector chainCoordinates = null;

	Point3f p3d;
	int i,j;
	float[] coord= new float[3];
	float x,y,z,xCenter,yCenter,zCenter;
	float xmin=10000.0f,ymin=10000.0f,zmin=10000.0f;
	float xmax=-10000.0f,ymax=-10000.0f,zmax=-10000.0f;
	
	sequenceContent = new Vector(1,1);
	sequenceProperty=new Vector(1,1);
	
	for(i=0;i<dataEngine.numSequence();i++)
	    {
		chainCoordinates=new Vector(100,50);
		sequence=dataEngine.getSequence(i); //System.out.println("nombre de residue= "+sequence.numResidue());
		
		for(j=0;j<sequence.numResidue();j++)
		    {
			
			residue=sequence.getResidue(j,jet.data.datatype.Sequence3D.DIRECT);
			
			if(residue.containsAtomCoordinates())//&&(!residue.hasSequenceBreak()))
			    {
				//System.out.println("residue "+ j+" : "+residue.hasSequenceBreak());
				
				p3d=residue.getCarbonAlphaCoordinates();
				p3d.get(coord); x=coord[0]/50.0f; y=coord[1]/50.0f; z=coord[2]/50.0f;
				chainCoordinates.add(new Point3f(x,y,z));
				
				if(x<xmin) xmin=x; if(x>xmax) xmax=x;
				if(y<ymin) ymin=y; if(y>ymax) ymax=y;
				if(z<zmin) zmin=z; if(z>zmax) zmax=z;
			    }
			
			else
			    {
				if(chainCoordinates.size()>0)
				    {
					sequenceContent.add(chainCoordinates); //System.out.println(chainCoordinates);
					chainCoordinates=new Vector(100,50);
				    }
}
		    }
		
		sequenceContent.add(chainCoordinates); //System.out.println(chainCoordinates);
	    }

	xCenter=(xmin+xmax)/2;
	yCenter=(ymin+ymax)/2;
	zCenter=(zmin+zmax)/2;
	
	centerContent=new Vector3f(-xCenter,-yCenter,-zCenter);
    }
    
    private void initChains()
    {
	
	Vector chainCoordinates;
	LineAttributes lineAttrib;
	Appearance appearance;
	Vector colors=new Vector(1,1);
	ColoringAttributes color;
	
	color=new ColoringAttributes();
	color.setColor(1.0f, 1.0f, 1.0f);
	colors.add(color);
	color=new ColoringAttributes();
	color.setColor(0.0f, 1.0f, 0.0f);
	colors.add(color);
	color=new ColoringAttributes();
	color.setColor(1.0f, 0.0f, 0.0f);
	colors.add(color);
	color=new ColoringAttributes();
	color.setColor(0.0f, 1.0f, 1.0f);
	colors.add(color);
	color=new ColoringAttributes();
	color.setColor(1.0f, 0.0f, 1.0f);
	colors.add(color);
	color=new ColoringAttributes();
	color.setColor(1.0f, 1.0f, 0.0f);
	colors.add(color);
	
	for(int i=0;i<sequenceContent.size();i++)
	    {
		chainCoordinates=(Vector)sequenceContent.get(i);

		if(chainCoordinates.size()>5)
		    {
			appearance = new Appearance();
			
			appearance.setColoringAttributes((ColoringAttributes)colors.get(i));
			lineAttrib = new LineAttributes();
			lineAttrib.setLineWidth(5.0f);
			appearance.setLineAttributes(lineAttrib);
			this.addChild(new Shape3D(getGeometry(chainCoordinates), appearance));
		    }
	    }
    }
    
    private IndexedLineArray getGeometry(Vector chainCoordinates)
    {
	int i;
	IndexedLineArray squeleton=null;
	
	try
	    {
		squeleton= new IndexedLineArray(chainCoordinates.size(), GeometryArray.COORDINATES, chainCoordinates.size()*2);

		squeleton.setCoordinate(0,(Point3f)chainCoordinates.get(0));
		squeleton.setCoordinateIndex( 0, 0);

		for(i=1;i<chainCoordinates.size()-1;i++)
		    {
			squeleton.setCoordinate(i,(Point3f)chainCoordinates.get(i));
			squeleton.setCoordinateIndex((i*2)-1,i );
			squeleton.setCoordinateIndex(i*2, i);
		    }
		
		i=chainCoordinates.size()-1;
		squeleton.setCoordinate(i,(Point3f)chainCoordinates.get(i));
		squeleton.setCoordinateIndex((i*2)-1,i);
	    }
	
	catch(Exception e)
	    {System.out.println("Problem with structure 3D GEOMETRY");}
	
	return squeleton;
	
    }
    
    private void placeBalls()
    {
	TransformGroup placeSphere;
	Transform3D translate = new Transform3D();
	Sphere sphere;
	int i,j;
	Vector chainSpheres; 
	Vector chainCoordinates,chainColors=null;

	//sphereIndex=new Vector(1,1);

	for(i=0;i<sequenceContent.size();i++)
	    {
		chainCoordinates=(Vector)sequenceContent.get(i);
		//if(property!=null)chainColors=(Vector) sequenceProperty.get(i);
		//chainSpheres=new Vector(1,1);

		for(j=0;j<chainCoordinates.size();j++)
		    {
			if(((SequenceAppearance)seqAp.get(i)).isVisible(j))
			    {
				placeSphere=new TransformGroup();
				sphere=new Sphere(0.02f);//,Sphere.ENABLE_APPEARANCE_MODIFY,getBallAppearance(i,j));
				//shape.set=sphere.cloneTree();//cloneTree();
				sphere.setAppearance(getBallAppearance(i,j));
				translate.set(new Vector3f((Point3f)chainCoordinates.get(j)));
				placeSphere.setTransform(translate);
				placeSphere.addChild(sphere);
				this.addChild(placeSphere);
				//chainSpheres.add(sphere);
			    }
		    }
		//sphereIndex.add(chainSpheres);
	    }
    }
    
    private Appearance getBallAppearance(int chain, int position)
    {
	Appearance app;
	Color3f ambientColor = new Color3f(0.0f, 0.0f, 0.0f);
	Color3f emissiveColor = ((SequenceAppearance)seqAp.get(chain)).getColor(position);
	Color3f diffuseColor = new Color3f(1.0f, 1.0f, 1.0f);
	Color3f specularColor =new Color3f(0.0f, 0.0f, 0.0f);
	float shininess = 70 ;
	Material mat = new Material(ambientColor, emissiveColor, diffuseColor, specularColor, shininess);
	mat.setLightingEnable(true);

	app = new Appearance();

	if(((SequenceAppearance)seqAp.get(chain)).isTransparent(position))
	    app.setTransparencyAttributes(new TransparencyAttributes(TransparencyAttributes.NICEST, 0.1f));
	//else app.setTransparencyAttributes(new TransparencyAttributes(TransparencyAttributes.NONE, 0.1f));

	app.setMaterial(mat);
	//app.setCapability(Appearance.ALLOW_MATERIAL_WRITE);
	//app.setCapability(Appearance.ALLOW_TRANSPARENCY_ATTRIBUTES_WRITE);
	return app;
    }

    /*private void initSeqPanels()
      {
      jprotein.rasmol.model.SeqPanel seqPanel=new jprotein.rasmol.model.SeqPanel();
      this.addChild(seqPanel.generateSeqPanel());
      //this.addChild(seqPanel.placeTips());

      }*/

    public void updateAppearance()
    {
	int i,j;
	Appearance app;
	Color3f ambientColor = new Color3f(0.0f, 0.0f, 0.0f);
	Color3f emissiveColor; 
	Color3f diffuseColor = new Color3f(1.0f, 1.0f, 1.0f);
	Color3f specularColor =new Color3f(0.0f, 0.0f, 0.0f);
	float shininess = 70 ;
	Material mat;
	Vector spheres;
	
	for(i=0;i<sphereIndex.size();i++)
	    {
		spheres=(Vector)sphereIndex.get(i);
		for(j=0;j<spheres.size();j++)
		    {
			app=new Appearance();//
		
			emissiveColor= ((SequenceAppearance)seqAp.get(i)).getColor(j);
			mat= new Material(ambientColor, emissiveColor, diffuseColor, specularColor, shininess);
			mat.setLightingEnable(true);
			app.setMaterial(mat);
			if(((SequenceAppearance)seqAp.get(i)).isTransparent(j))
			    app.setTransparencyAttributes(new TransparencyAttributes(TransparencyAttributes.NICEST, 0.1f));
			//else app.setTransparencyAttributes(new TransparencyAttributes(TransparencyAttributes.NONE, 0.1f));
			((Sphere)spheres.get(j)).setAppearance(app);
		    }
	    }


    }
    
    private void initTG()
    {
	Transform3D translate = new Transform3D();

	this.initCarbonAlphaCoordinates();
	translate.set(centerContent);
	this.setTransform(translate);
	this.initChains();
	//this.initSeqPanels();
	this.placeBalls();
    }

}

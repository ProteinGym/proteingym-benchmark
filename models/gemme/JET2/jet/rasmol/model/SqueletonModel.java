package jet.rasmol.model;

import com.sun.j3d.utils.geometry.*;
import com.sun.j3d.utils.universe.*;
import javax.media.j3d.*;
import javax.vecmath.*;
import jet.data.datatype.*;

import java.util.*;


public class SqueletonModel extends TransformGroup
{
    private Vector spheres;
    private Vector seqAp;
    private Vector sequenceContent;
    Vector sequenceList;

    static Color3f[] colors={new Color3f(0.0f, 0.0f, 1.0f),new Color3f(0.0f, 1.0f, 0.0f),new Color3f(1.0f, 0.0f, 0.0f),new Color3f(1.0f, 1.0f, 0.0f),new Color3f(1.0f, 0.0f, 1.0f),new Color3f(0.0f, 1.0f, 1.0f),new Color3f(1.0f, 1.0f, 1.0f),new Color3f(0.3f, 0.3f, 0.3f)};

    public SqueletonModel(Vector sequenceList, Vector propertyList)
    {
	super();
	this.sequenceList=sequenceList;
	initCarbonAlphaCoordinates();
	initChains();
	generateSqueleton();
	centerSqueleton();
	seqAp=new Vector(sequenceList.size());
	jet.rasmol.SequenceAppearance sA;

	Vector prop,selection;
	int i,j;

	for(i=0;i<propertyList.size();i++) 
	    {
		prop=(Vector)propertyList.get(i);
		selection=new Vector(prop.size());
		
		for(j=0;j<prop.size();j++)
		    {
			if(((Double)prop.get(i)).doubleValue()>0.01) selection.add(new Integer(j));
		
		    }
		
		propertyList.setElementAt(new jet.rasmol.Property(colors[i],prop),i);
		sA=new jet.rasmol.SequenceAppearance((jet.data.datatype.Sequence3D)sequenceList.get(i),(jet.rasmol.Property)propertyList.get(i));
		sA.select(selection);
		seqAp.add(sA);		
	    }
	
    }
    
    public void setAppearance(Vector seqAp) { this.seqAp=seqAp; }
    
    private void initCarbonAlphaCoordinates()
    {
	//jprotein.data.DataEngine dataEngine=jprotein.data.DataEngine.instance();
	jet.data.datatype.Sequence3D sequence;
	jet.data.datatype.Residue3D residue;
	
	
	Vector chainCoordinates = null;

	Point3f p3d;
	int i,j;
	float[] coord= new float[3];
	float x,y,z,xCenter,yCenter,zCenter;
	float xmin=10000.0f,ymin=10000.0f,zmin=10000.0f;
	float xmax=-10000.0f,ymax=-10000.0f,zmax=-10000.0f;
	
	sequenceContent = new Vector(1,1);

	
	for(i=0;i<sequenceList.size();i++)
	    {
		chainCoordinates=new Vector(100,50);
		sequence=(jet.data.datatype.Sequence3D)sequenceList.get(i); //System.out.println("nombre de residue= "+sequence.numResidue());
		
		for(j=0;j<sequence.size();j++)
		    {
			
			residue=sequence.getResidue(j,jet.data.datatype.Sequence3D.DIRECT);
			
			if(residue.containsAtomCoordinates())//&&(!residue.hasSequenceBreak()))
			    {
				//System.out.println("residue "+ j+" : "+residue.hasSequenceBreak());
				
				p3d=residue.getCarbonAlphaCoordinates();
				p3d.get(coord); x=coord[0]/50.0f; y=coord[1]/50.0f; z=coord[2]/50.0f;
				chainCoordinates.add(new Point3f(x,y,z));
				
				
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


    }
    private void initChains()
    {
	
	Vector chainCoordinates;
	LineAttributes lineAttrib;
	Appearance appearance;
	Shape3D shape;
	
	for(int i=0;i<sequenceContent.size();i++)
	    {
		chainCoordinates=(Vector)sequenceContent.get(i);

		if(chainCoordinates.size()>5)
		    {
			appearance = new Appearance();
			
			appearance.setColoringAttributes(new ColoringAttributes(colors[i],ColoringAttributes.FASTEST));
			lineAttrib = new LineAttributes();
			lineAttrib.setLineWidth(5.0f);
			lineAttrib.setLineAntialiasingEnable(true); 
			appearance.setLineAttributes(lineAttrib);
			shape=new Shape3D(getGeometry(chainCoordinates), appearance);
			shape.setPickable(false);
			this.addChild(shape);
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
    private void generateSqueleton()
    {
	int i,j;

	jet.data.datatype.Sequence3D sequence;
	jet.data.datatype.Residue3D residue;
	jet.rasmol.model.ResidueModel rm;
	//seqAp=dataEngine.getSequenceAppearance();
	spheres=new Vector(1,1);
	Vector sphereGroup;

	for(i=0;i<sequenceList.size();i++)
	    {
		sphereGroup=new Vector(1,1);
		sequence=(jet.data.datatype.Sequence3D)sequenceList.get(i); 
		for(j=0;j<sequence.size();j++)
		    {
			residue=sequence.getResidue(j,jet.data.datatype.Sequence3D.DIRECT);
			if(residue.containsAtomCoordinates())
			    {
				rm=new ResidueModel(residue,j,colors[i]);
				this.addChild(rm);
				sphereGroup.add(rm);
			
			    }
		    }
		spheres.add(sphereGroup);
	    }
    }
    
    public void updateAppearance()
    {
	int i,j;
	jet.rasmol.model.ResidueModel rm;
	Color3f color;
	boolean transparent;
	Vector sphereGroup;

	for(i=0;i<spheres.size();i++)
	    {
		sphereGroup=(Vector)spheres.get(i);

		for(j=0;j<sphereGroup.size();j++)
		    {
			rm=(jet.rasmol.model.ResidueModel)sphereGroup.get(j);
			color=((jet.rasmol.SequenceAppearance)seqAp.get(i)).getColor(j);
			transparent=((jet.rasmol.SequenceAppearance)seqAp.get(i)).isTransparent(j);
			rm.modifyAppearance(color,transparent);
		    }

	    }

    }

    private void centerSqueleton()
    {
	float[] coord= new float[3];
	float x,y,z,xCenter,yCenter,zCenter;
	float xmin=10000.0f,ymin=10000.0f,zmin=10000.0f;
	float xmax=-10000.0f,ymax=-10000.0f,zmax=-10000.0f;
	Transform3D translate = new Transform3D();
	int i,j;
	jet.rasmol.model.ResidueModel rm;
	Vector sphereGroup;

	
	for(i=0;i<spheres.size();i++)
	    {
		sphereGroup=(Vector)spheres.get(i);

		for(j=0;j<sphereGroup.size();j++)
		    {
			rm=(jet.rasmol.model.ResidueModel)sphereGroup.get(j);
			rm.getPosition().get(coord); x=coord[0]; y=coord[1]; z=coord[2];
				
			if(x<xmin) xmin=x; if(x>xmax) xmax=x;
			if(y<ymin) ymin=y; if(y>ymax) ymax=y;
			if(z<zmin) zmin=z; if(z>zmax) zmax=z;
		    }

	    }
	xCenter=(xmin+xmax)/2.0f;
	yCenter=(ymin+ymax)/2.0f;
	zCenter=(zmin+zmax)/2.0f;
	translate.set(new Vector3f(-xCenter,-yCenter,-zCenter));
	this.setTransform(translate);
    }

    public String getResidueInfo(Sphere sphere)
    {
	jet.rasmol.model.ResidueModel rm;
	Vector sphereGroup;
	Iterator sphereList;
	int i,j;

	for(i=0;i<spheres.size();i++)
	    {
		sphereGroup=(Vector)spheres.get(i);
		
		for(j=0;j<sphereGroup.size();j++)
		    {
			rm=(jet.rasmol.model.ResidueModel)sphereGroup.get(j);
			sphereList=rm.getSphereList().iterator();
			
			while(sphereList.hasNext())
			    {
				if(((Sphere)sphereList.next()).equals(sphere)) 
				    return ((jet.data.datatype.Sequence3D)sequenceList.get(i)).getSequenceName()+"\t"+rm.getResidue().getResidueCode()+"\t"+rm.getResidue().getPosition();
			    }
		    }
	    }
	return "nothing";
    }
}

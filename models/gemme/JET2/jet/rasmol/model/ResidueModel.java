package jet.rasmol.model;

import com.sun.j3d.utils.geometry.*;
import com.sun.j3d.utils.universe.*;
import javax.media.j3d.*;
import javax.vecmath.*;
import java.awt.Color;
import java.util.*;

public class ResidueModel extends TransformGroup
{
    
    Appearance appearance;
    Point3f p3d;
    int index;
    jet.data.datatype.Residue3D residue;
    Vector sphereList;
    Color3f defaultColor;
    public ResidueModel(jet.data.datatype.Residue3D residue, int index,Color3f defaultColor)
    {	
	super();
	setIndex(index);
	this.defaultColor=defaultColor;
	sphereList=new Vector(1,1);
	TransformGroup tG;
	Transform3D translate;
	Sphere sphere;//=new Sphere(0.02f);
	float[] coord;
	this.residue=residue;
	
	Iterator atoms=residue.getAllAtoms().iterator();
	jet.data.datatype.Atom atom;
	initAppearance();
	while(atoms.hasNext())
	{
	    coord= new float[3];

	    atom=(jet.data.datatype.Atom)atoms.next();
	    atom.getCoordinates().get(coord);
	    
	    coord[0]*=0.02f; coord[1]*=0.02f; coord[2]*=0.02f;
	    p3d=new Point3f(coord);
	    sphere=new Sphere(atom.getRadius()*0.02f);
	    sphere.setAppearance(appearance);
	    tG = new TransformGroup();
	    translate = new Transform3D();
	    translate.set(new Vector3f(p3d));
	    tG.setTransform(translate);
	    tG.addChild(sphere);	    
	    this.addChild(tG);
	    sphereList.add(sphere);
	}

	
    }
    
    public int getIndex() { return index; }
    public void setIndex(int index) { this.index=index; }

    public Vector getSphereList() { return sphereList;}
    public Point3f getPosition(){ return p3d;}

    public String toString() { return residue.toString(); }    
    public jet.data.datatype.Residue3D getResidue() { return residue; }
    
    private void initAppearance()
    {

	Color3f ambientColor = new Color3f(0.2f, 0.2f, 0.2f);
	Color3f emissiveColor = defaultColor;
	Color3f diffuseColor = new Color3f(0.9f, 0.9f, 0.9f);
	Color3f specularColor =new Color3f(0.9f,0.9f,0.9f);
	float shininess =50 ;
	
	Material mat = new Material(ambientColor, emissiveColor, diffuseColor, specularColor, shininess);
	mat.setShininess(shininess);
	mat.setLightingEnable(true);
	
	appearance=new Appearance();

	appearance.setMaterial(mat);
	appearance.setCapability(Appearance.ALLOW_MATERIAL_WRITE);
	
	appearance.setTransparencyAttributes(new TransparencyAttributes(TransparencyAttributes.NONE,0.0f));
	appearance.setCapability(Appearance.ALLOW_TRANSPARENCY_ATTRIBUTES_WRITE);
	//appearance.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_WRITE);
	//appearance.setColoringAttributes(new ColoringAttributes(defaultColor,ColoringAttributes.NICEST));

    }


    public void modifyAppearance(Color3f color, boolean transparent)
	{
	    
	    Color3f ambientColor = new Color3f(0.2f, 0.2f, 0.2f);
	    Color3f emissiveColor = color;
	    Color3f diffuseColor = new Color3f(1.0f, 1.0f, 1.0f);
	    Color3f specularColor =new Color3f(1.0f, 1.0f, 1.0f);
	    float shininess = 90 ;
	    Material mat = new Material(ambientColor, emissiveColor, diffuseColor, specularColor, shininess);
	    appearance.setMaterial(mat);
	    
	    //appearance.setColoringAttributes(new ColoringAttributes(color,ColoringAttributes.NICEST));
	    
	    if(transparent)
		appearance.setTransparencyAttributes(new TransparencyAttributes(TransparencyAttributes.NICEST,0.9f));
	    else 
		appearance.setTransparencyAttributes(new TransparencyAttributes(TransparencyAttributes.NONE,0.0f));
	    
	}
}

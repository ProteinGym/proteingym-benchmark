package jet.data.datatype;

import java.util.*;
import javax.vecmath.*;

public class Atom1 
{
    Point3f coord;
    int atomIndex;
    static int numVect=16;
    String name;
    
    private static Vector cloud=null;
    private Vector access=null;
    
    private static char[] atomSymbol= {'C','O','N','S','X'};
    private static float[] atomRadius = {1.872f,1.4f,1.507f,1.848f,0.0f};
    private static double[] atomArea={44.04f,24.63f,28.54f,42.92f,0.0f};
 
    public Atom1(String symbol, Point3f coord)
    {
	setName(symbol);
	setSymbol(getName().charAt(0));
	setCoordinates(coord);
    }
    
    public static int getAtomIndex(char symbol)
    {
	for(int i=0;i<atomSymbol.length;i++)
	    {
		if(symbol==atomSymbol[i]) return i;
	    }
	return atomSymbol.length-1;
    }
    
    
    public int getAtomIndex(){ return atomIndex;}

    public Point3f getCoordinates() { return coord; }
    public void setCoordinates(Point3f coord) { this.coord=coord; }

    public String toString() { return getName(); }
    public String getName() { return name; }
    public void setName(String name) { this.name=name.trim(); }
    
    public static float getRadius(char symbol) { return atomRadius[getAtomIndex(symbol)];}
    public float getRadius() { return atomRadius[getAtomIndex()]; }

    public char getSymbol() {  return atomSymbol[getAtomIndex()]; }
    public void setSymbol(char symbol) { atomIndex=getAtomIndex(symbol);}
    

    public boolean isSideChainAtom() 
    { 
	if(name.length()>1)
	    {
		if(getName().charAt(1)=='A') return false;
		return true;
	    }
	return false; 
    }
    
  
    public float distance(jet.data.datatype.Atom atom)
    {
	return distance(atom.getCoordinates());
    }
    
    public float distance(jet.data.datatype.Residue3D residue)
    {
	return distance(residue.getCenterGravityCoordinates());
    }

    public float distance(Point3f point)
    {
	return this.getCoordinates().distance(point);
    }   
    
    public double getPercentAccessibility()
    { 
	double percent=0.0;
	int i;

	if(access==null) return 1.0;

	for(i=0;i<access.size();i++) { if(isAccessible(i)) {percent+=1.0;} }

	percent/=((double)access.size());
	//System.out.println(percent);
	return percent; 
    }

    public double getAtomSurfaceArea()
    {
	return atomArea[getAtomIndex()];
    }

    public double getAtomAccessibleSurfaceArea()
    {
	return getAtomSurfaceArea()* getPercentAccessibility();
    }

    public void setAccessibility(jet.data.datatype.Atom atom)
    {
	setAccessibility(atom,getRadius('O'));
    }

    public void setAccessibility(jet.data.datatype.Atom atom, float probeRadius)
    {
	setAccessibility(atom,probeRadius,0.0f);
    }

    public void setAccessibility(jet.data.datatype.Atom atom, float probeRadius,float noise) 
    {
	
	int i;
	Point3f projection, p;
	float minDist=atom.getRadius()+probeRadius;
	initAccess();

	for(i=0; i<cloud.size();i++)
	    {
		if(isAccessible(i))
		    {
			p=new Point3f(getCoordinates());
			projection=new Point3f(getProjection(i));
			projection.scale(getRadius()+probeRadius);
			p.add(projection);
			if(p.distance(atom.getCoordinates())<minDist) setAccessible(false,i);
		    }
	    }
    }
    
    public void setAccessible(boolean b, int i) { access.setElementAt(new Boolean(b),i); }
    
    public boolean isAccessible(int i) { return ((Boolean)access.get(i)).booleanValue();}

    public void initAccess()
    {
	if(cloud==null) initProjections();
	
	if(access==null) 
	    {
		int i=0;
		access=new Vector(cloud.size()); access.setSize(cloud.size());
		while(i<cloud.size()) setAccessible(true,i++);
	    }
    }

    public Point3f getProjection(int i) { return (Point3f)cloud.get(i); } 

    private void initProjections()
    {
	float[] proj=new float[3];
	double cosTeta,sinTeta,cosPhi;
	double phi=0.0,teta=0.0,dteta,dphi;
	int i,j;

	if(cloud==null)
	    {
		cloud=new Vector(numVect*numVect);
		dphi=dteta=(Math.PI*2.0)/(double)numVect;
		
		for(i=0;i<numVect;i++)
		    {
			teta+=dteta;
			cosTeta=Math.cos(teta); sinTeta=Math.sin(teta);

			for(j=0;j<numVect;j++)
			    {
				phi+=dphi;
				cosPhi=Math.cos(phi);

				/*X*/proj[0]=(float)(cosPhi*cosTeta);
				/*Y*/proj[1]=(float)(cosPhi*sinTeta);
				/*Z*/proj[2]=(float)Math.sin(phi);
				
				cloud.add(new Point3f(proj));
			    }
		    }
	    }
	
    }

    
}

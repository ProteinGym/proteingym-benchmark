package jet.data.datatype;

import java.util.*;

import javax.vecmath.Point3f;

import jet.data.datatype.*;

/** Classe représentant les atomes ainsi que leurs coordonnées 3D et gérant leur 
 * accessibilité par rapport à une sonde (paramètre "probe_radius" du fichier de 
 * configuration). La surface accessible d'un atome est discrétisée en 
 * numVect*numVect points stockés dans le vecteur cloud. */


public class Atom 
{
    /** Coordonnées de l'atome */
    Point3f coord;
    /** Index du symbole de l'atome dans le tableau atomSymbol. */
    int atomIndex;
    /** La surface accessible de l'atome (sphère) est discrétisée en numVect*numVect points.
     * Pour numVect=16, la surface est découpée en 16*16=256 surfaces de 2*pi/16=pi/8 de coté. */
    static int numVect=16;
    /** Symbole representant l'atome */
    String name;   
    /** Position de l'atome dans la structure PDB */
    int position;
    /** Accéssibilité des numVect*numVect points stockés dans le vecteur cloud. */
    private Vector access=null;
    
    /** Accéssibilité de l'atome. */
    private boolean accessible;
      
    /** Variables statiques correspondant aux caractéristiques des différents atomes possibles. */  
    /***/
    
    /** Ensemble des points discrétisants la surface accessible de l'atome. */
    private static Vector cloud=null;  
    /** Symboles des atomes */
    private static char[] atomSymbol= {'C','O','N','S','X'};
    /** Rayons des atomes */
    private static float[] atomRadius = {1.872f,1.4f,1.507f,1.848f,0.0f};
    /** Surfaces des atomes */
    private static double[] atomArea={44.04f,24.63f,28.54f,42.92f,0.0f};
    
    /***/
    
    /** CONSTRUCTEURS */
    /***/
    
    public Atom(String symbol)
    {
	this(symbol,0,true,new Point3f());
    }
    public Atom(String symbol, int pos,boolean access, Point3f coord)
    {
	setName(symbol);
	setSymbol(getName().charAt(0));
	setPosition(pos);
	setCoordinates(coord);
	setAccessible(access);
    }
    
    /***/
    /** Methodes statiques retournant des caractéristiques générale des atomes */
    
    public static int getAtomIndex(char symbol)
    {
	for(int i=0;i<atomSymbol.length;i++)
	    {
		if(symbol==atomSymbol[i]) return i;
	    }
	return atomSymbol.length-1;
    }
    
    public static float getRadius(char symbol) { return atomRadius[getAtomIndex(symbol)];}
    
    /***/
    /** ACCESSEURS */
    /***/
    
    public int getAtomIndex(){ return atomIndex;}
    public boolean isAccessible(){ return accessible;}
    public Point3f getCoordinates() { return coord; }
    public String getName() { return name; }
    public int getPosition() { return position; }
    public float getRadius() { return atomRadius[getAtomIndex()]; }
    public char getSymbol() {  return atomSymbol[getAtomIndex()]; }  
    public double getAtomSurfaceArea()
    {
	return atomArea[getAtomIndex()];
    }
    public boolean isAccessible(int i) { return ((Boolean)access.get(i)).booleanValue();}
    public Point3f getProjection(int i) { return (Point3f)cloud.get(i); } 
    
    /***/
    /** MODIFICATEURS */
    /***/
    
    public void setAccessible(boolean access){accessible=access;}
    public void setSymbol(char symbol) { atomIndex=getAtomIndex(symbol);}
    public void setCoordinates(Point3f coord) { this.coord=coord; }   
    public void setName(String name) { this.name=name.trim(); }
    public void setPosition(int pos){this.position=pos;}
    public void setAccessible(boolean b, int i) { access.setElementAt(new Boolean(b),i); }
    
    /***/
    
    public String toString() { return getName(); }
    
    /** l'atome appartient il à une chaine latérale */ 
    
    public boolean isSideChainAtom() 
    { 
	if(name.length()>1)
	    {
		if(getName().charAt(1)=='A') return false;
		/* Seul le carbone alpha de la chaine squelette est sur deux lettres (à vérifier) */
		return true;
	    }
	return false; 
    }
    
    /** Méthodes pour le calcul de la distance entre deux atomes. */ 
    
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
    
    /***/
    
    /** Retourne le pourcentage de la surface de l'atome qui est accessible. */
      
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
    
    /** Retourne la surface de l'atome qui est accessible. */

    public double getAtomAccessibleSurfaceArea()
    {
	return getAtomSurfaceArea()* getPercentAccessibility();
    }
    
    /** Méthode pour calculer les points accessibles de l'atome 
     * courant par un atome par défaut (oxygène) sachant 
     * que l'espace est aussi encombré par l'atome atom. */

    public void setAccessibility(jet.data.datatype.Atom atom)
    {
	setAccessibility(atom,getRadius('O'));
    }
  
    /** Méthode pour calculer les points accessibles de l'atome 
     * courant par un atome sonde de rayon probeRadius sachant 
     * que l'espace est aussi encombré par l'atome atom. */

    public void setAccessibility(jet.data.datatype.Atom atom, float probeRadius)
    {
	setAccessibility(atom,probeRadius,0.0f);
    }

    /** Méthode pour calculer les points accessibles de l'atome 
     * courant par un atome sonde de rayon probeRadius sachant 
     * que l'espace est aussi encombré par l'atome atom en 
     * spécifiant un bruit (?). */
    
    public void setAccessibility(jet.data.datatype.Atom atom, float probeRadius,float noise) 
    {
	
	int i;
	Point3f projection, p;
	float minDist=atom.getRadius()+probeRadius;
	initAccess(false);

	for(i=0; i<cloud.size();i++)
	    {
		if(isAccessible(i))
		    {
			/* Coordonnées de l'atome courant. */
			p=new Point3f(getCoordinates());
			/* Coordonnées de la projection i sur la sphère représentant l'atome
			 * (le centre de la sphère a pour coordonnée (0,0,0)). */
			projection=new Point3f(getProjection(i));
			/* Les projections sont faites sur un rayon=getRadius()+probeRadius. */
			projection.scale(getRadius()+probeRadius);
			/* Ajout des coordonnées de la projection i aux coordonnées de l'atome. */
			p.add(projection);
			/* Si la distance entre p (position possible de l'atome sonde) et l'atome 
			 * atom est supérieure au rayon de l'atome atom plus le rayon de l'atome 
			 * sonde (minDist), alors le point projection i est accessible. */
			if(p.distance(atom.getCoordinates())<minDist) setAccessible(false,i);
		    }
	    }
    }  

    /** Initialisation à "true" de l'accessibilité des points 
     * discrétisant la surface de la sphère représentant l'atome. */
    
    public void initAccess(boolean forcer)
    {
	if(cloud==null) initProjections();
	
	if(access==null||forcer) 
	    {
		int i=0;
		access=new Vector(cloud.size()); access.setSize(cloud.size());
		while(i<cloud.size()) setAccessible(true,i++);
	    }
    }

    /** Calcul et initialisation des coordonnées des projections sur la 
     * surface de la sphère d'accessibilité de l'atome. Le centre de la 
     * sphère est l'origine du repère et le rayon est égal à 1. */
    
    private void initProjections()
    {
	float[] proj=new float[3];
	double cosTeta,sinTeta,cosPhi;
	/* La surface de la sphère est parcourue selon les valeurs de 
	 * phi et teta deux angles pris sur deux disques orthogonaux. 
	 * Les deux angles varient respectivement de dphi et dteta. */
	double phi=0.0,teta=0.0,dteta,dphi;
	int i,j;

	if(cloud==null)
	    {
		cloud=new Vector(numVect*numVect);
		/* Distance entre deux points successifs du vecteur cloud selon les axes de phi et teta. */
		dphi=dteta=(Math.PI*2.0)/(double)numVect;
		
		for(i=0;i<numVect;i++)
		    {
			teta+=dteta;
			cosTeta=Math.cos(teta); sinTeta=Math.sin(teta);

			for(j=0;j<numVect;j++)
			    {
				phi+=dphi;
				cosPhi=Math.cos(phi);

				/* Calcul (à l'aide de cosPhi, cosTeta, sinTeta) des coordonnées des projections sur la surface de la sphère. */
				/*X*/proj[0]=(float)(cosPhi*cosTeta);
				/*Y*/proj[1]=(float)(cosPhi*sinTeta);
				/*Z*/proj[2]=(float)Math.sin(phi);
				
				cloud.add(new Point3f(proj));
			    }
		    }
	    }
	
    }

    /** Méthode equals qui ne compare deux atomes que par rapport à leurs noms. */
    
    public boolean equals(Object o)
    {
	if(o instanceof jet.data.datatype.Atom)
	    {
		if(((jet.data.datatype.Atom)o).getName().compareTo(this.getName())==0) return true;
	    }
	return false;
    }
}

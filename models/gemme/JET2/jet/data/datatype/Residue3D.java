package jet.data.datatype;
import java.util.Vector;

import javax.vecmath.Point3f;

/** On ajoute une chaine squelette,
 * une chaine laterale (composes d'atomes), 
 * un centre de gravite, 
 * un rayon max (par rapport au centre de gravite) 
 * et un sequence_break (?) à la classe residu.
 * Les coordonnees sont stockees dans les atomes qui constituent le residu. */

public class Residue3D extends jet.data.datatype.Residue
{

    //private static final String[] atomCode={"N","CA","C","O"};
    
    /** chaine squelette */ 
    private Vector squeleton=null;
    /** et chaine laterale */
    private Vector sidechain=null; 
    /** ? **/
    private boolean sequenceBreak=false; 
    /** rayon max (par rapport au centre de gravite) **/
    private float radius=0; 
    /** centre de gravite **/
    private Point3f cGCoord=null; 
    
    /***/
    /** CONSTRUCTEURS */
    /***/
    
    public Residue3D(String residue) { super(residue); }
    public Residue3D(String residue, int pos){ this(residue); setPosition(pos);}
    public Residue3D(String residue, int pos, String id){ this(residue); setPosition(pos); setId(id);}
    public Residue3D(String residue, int pos, String id, String chainID){ this(residue); setPosition(pos); setId(id); setChainId(chainID);}

    // public static String getAtomCode(int index){ return atomCode[index];}
    
    /***/
    /** MODIFICATEURS */
    /***/
    
    /** Ajout d'atomes */
    
    public void addAtom(String name, int pos, boolean access,Point3f coord3D)
    {
	
	boolean found=false;
	
	jet.data.datatype.Atom atom=new jet.data.datatype.Atom(name,pos,access,coord3D);
	
	if(sidechain==null) sidechain=new Vector();
	if(squeleton==null) squeleton=new Vector();
	if(atom.isSideChainAtom())
		/* On stocke les atomes de la chaine laterale */
		sidechain.add(atom); 
	else 
		/* On stocke les atomes du squelette */
	    {
		
		/* Le carbone alpha est stocke en premier dans le squelette */
		if((name.length()>1)&&(name.charAt(0)=='C')&&((name.charAt(1)=='A'))) 
		    squeleton.add(0,atom);
		else 
		    squeleton.add(atom);
	    }
	  
	    
    }
    
    public void setSequenceBreak(boolean sequenceBreak){ this.sequenceBreak=sequenceBreak;}
    
    /***/
    /** ACCESSEURS */
    /***/
    
    public boolean hasSequenceBreak() { return sequenceBreak;}
  
    public boolean containsAtomCoordinates() 
    {
	if((squeleton!=null)&&(squeleton.size()>0)) return true;
	else return false;
    }
    
    /** Accesseurs de la chaine squelette */
    
    public int getSqueletonSize()
    {
	if((squeleton!=null)&&(squeleton.size()>0)) return squeleton.size();
	else return 0;
    }
    
    /** Accesseurs de la chaine laterale **/
    
    public int getSideChainSize()
    { 
	if(sidechain!=null) return sidechain.size(); 
	else return 0;
    }
    
    /** Retourne tous les atomes d'un residu */
    
    public Vector getAllAtoms()
    {
    	Vector atoms=new Vector();
    	for (int i=0;i<squeleton.size();i++)
    	{
    		if (((jet.data.datatype.Atom)squeleton.get(i)).isAccessible()) 
    			atoms.add(squeleton.get(i));
    	}
    	
		if((sidechain!=null)&&(sidechain.size()>0))
		{
			for (int i=0;i<sidechain.size();i++)
	    	{
	    		if (((jet.data.datatype.Atom)sidechain.get(i)).isAccessible()) 
	    			atoms.add(sidechain.get(i));
	    	}
		}
		return atoms;
    }

    /** Accesseurs d'atomes (dans la chaine laterale et le squelette) */
    
    public jet.data.datatype.Atom getSqueletonAtom(int i) 
    { return (jet.data.datatype.Atom)squeleton.get(i);}
    
    public jet.data.datatype.Atom getSqueletonAtom(String atomName) 
    {
	int index=squeleton.indexOf(new Atom(atomName));
	if(index!=-1) 
	    { 
		return (jet.data.datatype.Atom)squeleton.get(index);
	    }
	return null;
    }

    public int getSqueletonAtomIndex(String atomName)
    {
        if(squeleton==null){return(-1);}
	else
	{
	    return(squeleton.indexOf(new Atom(atomName)));
        }
    }

    public jet.data.datatype.Atom getSideChainAtom(int i) 
    { 
	return (jet.data.datatype.Atom)sidechain.get(i);}

    public jet.data.datatype.Atom getSideChainAtom(String atomName) 
    {
	int index=sidechain.indexOf(new Atom(atomName));
	if(index!=-1) 
	    {
		return (jet.data.datatype.Atom)sidechain.get(index);
	    }
	return null;
    }

    public int getSideChainAtomIndex(String atomName)
    {
        /*System.out.print("hello I am testing:");
        System.out.println(atomName);*/
        if(sidechain==null){return(-1);}
	else
	{
           return(sidechain.indexOf(new Atom(atomName)));
	}
    }
    
    /** Coordonnees du carbone alpha qui est en position 0 du squelette */
    
    public Point3f getCarbonAlphaCoordinates()
    { 
	if((squeleton!=null)&&(squeleton.size()>0))
	    {
		return getSqueletonAtom(0).getCoordinates();
	    }
	return new Point3f();
    }
    
    /***/
    /** METHODES */
    /***/
    
    /** Calcul du centre de gravite */
    
    public Point3f getCenterGravityCoordinates()
    {
	float x=0.0f,y=0.0f,z=0.0f,n;
	float[] coord=new float[3];
	int i;
	if(cGCoord==null) 
	    {
		if(sidechain!=null)
		    { 
			n=sidechain.size()+squeleton.size();
			for(i=0;i<sidechain.size();i++)
			    {
				getSideChainAtom(i).getCoordinates().get(coord);
				x+=coord[0]; y+=coord[1]; z+=coord[2];
			    }
		    }
		else { n=squeleton.size(); }

		for(i=0;i<squeleton.size();i++)
		{
		    getSqueletonAtom(i).getCoordinates().get(coord);
		    x+=coord[0]; y+=coord[1]; z+=coord[2];
		}
	
		cGCoord=new Point3f(x/n,y/n,z/n);
	    }
	return cGCoord;
    }
  
    /** Calcul du rayon max par rapport au centre de gravite */
    
    public float getRadius()
    {
	int i;
	float max,radius=-1.0f;
	if(this.radius==0) 
    	{
		if(!(radius>0.0f))
		    {
			max=-10000.0f;
	
			if(sidechain!=null)
			    {
				for(i=0;i<sidechain.size();i++)
				    {
					if(max<(radius=getSideChainAtom(i).distance(getCenterGravityCoordinates())))
					    max=radius;
					
				    }
			    }
			for(i=0;i<squeleton.size();i++)
			{
			    if(max<(radius=getSqueletonAtom(i).distance(getCenterGravityCoordinates())))
				max=radius;
			    
			}
	
			radius=max;
		    }
    	}
	return radius;
    }
    
    /** Calcul de la distance minimale entre deux residus (en comparant les distances
     *  entre leurs atomes). */
    
    public float minAtomDistance(Residue3D residue)
    {
	int i,j;
	float dist,min=10000.0f;
	Vector atomsI=this.getAllAtoms(),atomsJ;
	jet.data.datatype.Atom atomI,atomJ;
	if(residue!=null)
	    {
		for(i=0;i<atomsI.size();i++)
		    {
			atomsJ=residue.getAllAtoms();
			atomI=(jet.data.datatype.Atom)atomsI.get(i);
			
			for(j=0;j<atomsJ.size();j++)
			    {
				atomJ=(jet.data.datatype.Atom)atomsJ.get(j);
				dist=atomI.distance(atomJ);
				if(dist<min) min=dist;
			    }
		    }
		return min;
	    }
	return -1.0f;
	
    }
    
    /** Calcul de la distance minimale entre deux chaines laterales de deux 
     * residus (en comparant les distance entre leur atomes). */
    
    public float minSideChainDistance(Residue3D residue)
    {
	int i,j;
	float dist,min=10000.0f;
	if(residue!=null)
	    {
		for(i=0;i<this.getSideChainSize();i++)
		    {
			for(j=0;j<residue.getSideChainSize();j++)
			    {
				dist=this.getSideChainAtom(i).distance(residue.getSideChainAtom(j));
				if(dist<min) min=dist;
			    }
		    }
		return min;
	    }
	return -1.0f;
    }
    
    /** Calcul de la distance entre les 2 centre de gravité de 2 residus. */
    
    public float distance(Residue3D residue)
    {
	return this.getCenterGravityCoordinates().distance(residue.getCenterGravityCoordinates());			        
    }

    public boolean equals(Object o) 
    {
    	//System.out.println("equals Residue3D");
	if(o instanceof Residue3D) 
	    {
		//System.out.println("coord1:"+((Residue3D)o).getCarbonAlphaCoordinates());
		//System.out.println("coord2:"+getCarbonAlphaCoordinates());
		//System.out.print(" "+((Residue3D)o).getCarbonAlphaCoordinates().equals(getCarbonAlphaCoordinates()));
		return ((Residue3D)o).getCarbonAlphaCoordinates().equals(getCarbonAlphaCoordinates()); 
	    }
	else
	    {
		if(o instanceof jet.data.datatype.Residue) 
		    {
			return super.equals(o);
		    }	
	    }
	return false;
    }

}

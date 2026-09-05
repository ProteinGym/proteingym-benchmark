package jet.cluster.data;

import java.util.*;

public class Cluster 
{
    private Vector core, coreIndex,neibs,neibsIndex;
    private Vector rim, rimIndex;
    private float radius;
    private double traceMoy=-1.0;
    private double pcMoy=-1.0;
    private double scoreMoy=-1.0;
    
    public Cluster(float radius) 
    { 
	this.radius=radius; 
	core=new Vector(1,1); neibs=new Vector(1,1);  
	coreIndex=new Vector(1,1);
	neibsIndex=new Vector(1,1);
	rim=new Vector(1,1);
	rimIndex=new Vector(1,1);
    }
    
    public double getScoreMoy(){return scoreMoy;}
    public void setScoreMoy(double scoreMoy){this.scoreMoy=scoreMoy;}
    
    public double getTraceMoy(){return traceMoy;}
    public void setTraceMoy(double traceMoy){this.traceMoy=traceMoy;}
    
    public double getpcMoy(){return pcMoy;}
    public void setpcMoy(double pcMoy){this.pcMoy=pcMoy;}

    public boolean isNeighbour(jet.data.datatype.Residue3D residue) 
    { 
	return neibs.contains(residue); 
    }
    
    public boolean isNeighbour(jet.cluster.data.Cluster clust) 
    { 
    	boolean neighbour=false;
    	for(int i=0;i<clust.getCoreResidues().size();i++)
    	{
    		neighbour=neighbour||isNeighbour((jet.data.datatype.Residue3D)clust.getCoreResidues().get(i));
    	}
    	return neighbour;
    }
    
    public boolean isEmbedded(jet.cluster.data.Cluster clust) 
    { 
    	boolean embedded=false;
    	Vector c1=new Vector(clust.getCoreResidues()); c1.retainAll(getCoreResidues());
    	if (c1.size()>0) embedded=true;
    	return embedded;
    }

    public void addResidue(jet.cluster.data.ProxList pl)
    {
	getCoreResidues().add(pl.getResidue());
	getCoreIndex().add(new Integer(pl.getId()));
	getNeighbourResidues().remove(pl.getResidue());
	getNeighbourIndex().remove(new Integer(pl.getId()));
	
	addNeighbours(pl.getNeighbourResidues(radius));
	//System.out.println(pl.getNeighbourResidues(radius));
    }

    public void addRimResidue(jet.cluster.data.ProxList pl)
    {
	getRimResidues().add(pl.getResidue());
	getRimIndex().add(new Integer(pl.getId()));
	getNeighbourResidues().remove(pl.getResidue());
	getNeighbourIndex().remove(new Integer(pl.getId()));
	addNeighbours(pl.getNeighbourResidues(radius));
    }
    
    private void addNeighbours(Vector neighboursNodes)
    {
    	Vector neighbours=new Vector();
    	Vector neighboursIndex=new Vector();
    	for (int i=0;i<neighboursNodes.size();i++)
    	{
    		neighbours.add(((jet.cluster.data.ProxNode)neighboursNodes.get(i)).getResidue());
    		neighboursIndex.add(((jet.cluster.data.ProxNode)neighboursNodes.get(i)).getRef().getId());
    	}
    	
		Vector v1=new Vector(neighbours); v1.retainAll(getNeighbourResidues());
		Vector v2=new Vector(neighbours); v2.retainAll(getCoreResidues());
		Vector v3=new Vector(neighbours); v3.removeAll(v1); v3.removeAll(v2);
		getNeighbourResidues().addAll(v3);
		
		Vector v1I=new Vector(neighboursIndex); v1I.retainAll(getNeighbourIndex());
		Vector v2I=new Vector(neighboursIndex); v2I.retainAll(getCoreIndex());
		Vector v3I=new Vector(neighboursIndex); v3I.removeAll(v1I); v3I.removeAll(v2I);
		getNeighbourIndex().addAll(v3I);
		
    }  
    
    public Vector getCoreIndex() { return coreIndex; }
    public Vector getCoreResidues() { return core; }

    public Vector getRimIndex() { return rimIndex; }
    public Vector getRimResidues() { return rim; }

    public Vector getNeighbourIndex(){ return neibsIndex; }
    public Vector getNeighbourResidues(){ return neibs; }

    public void merge(jet.cluster.data.Cluster cluster)
    {

		Vector c1=new Vector(cluster.getCoreResidues()); c1.retainAll(getCoreResidues());
		Vector c2=new Vector(cluster.getCoreResidues()); c2.removeAll(c1);
		getCoreResidues().addAll(c2);
		Vector cI1=new Vector(cluster.getCoreIndex()); cI1.retainAll(getCoreIndex());
		Vector cI2=new Vector(cluster.getCoreIndex()); cI2.removeAll(cI1);
		getCoreIndex().addAll(cI2);
		
			
		Vector n1=new Vector(cluster.getNeighbourResidues()); n1.retainAll(getNeighbourResidues());
		Vector n2=new Vector(cluster.getCoreResidues()); n2.retainAll(getCoreResidues());
		Vector n3=new Vector(cluster.getNeighbourResidues()); n3.removeAll(n1); n3.removeAll(n2);
		getNeighbourResidues().addAll(n3);
			
		Vector n1I=new Vector(cluster.getNeighbourIndex()); n1I.retainAll(getNeighbourIndex());
		Vector n2I=new Vector(cluster.getCoreIndex()); n2I.retainAll(getCoreIndex());
		Vector n3I=new Vector(cluster.getNeighbourIndex()); n3I.removeAll(n1I); n3I.removeAll(n2I);
		getNeighbourIndex().addAll(n3I);

    }
    
    public boolean equals(Object o)
    {
    	//System.out.print("equals cluster");
	if(o instanceof jet.cluster.data.Cluster)
	    {
		((jet.cluster.data.Cluster)o).getCoreResidues().equals(getCoreResidues());
		
	    }
	return false;
    }
    
    public String toString ()
    {
    	String s="(";
    	for(int i=0;i<coreIndex.size();i++)
    		s=s+coreIndex.get(i)+" ";
    	return s+")";
    }
    
}

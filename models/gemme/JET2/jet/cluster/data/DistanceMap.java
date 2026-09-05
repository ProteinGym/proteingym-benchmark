package jet.cluster.data;

import java.util.*;

/** Classe stockant pour chaque residu toutes les distances aux autres residus */

public class DistanceMap
{
    ArrayList plList;

    public DistanceMap(jet.data.datatype.Sequence3D seq,float distanceMax)
    {
	plList=new ArrayList(seq.size());
	initDistanceMap(seq,distanceMax);
    }
    
    public jet.cluster.data.ProxList getProxList(int index) 
    { 
	return (jet.cluster.data.ProxList)plList.get(index); 
    }
    
    public void initDistanceMap(jet.data.datatype.Sequence3D seq,float distanceMax)
    {
	int i,j;
	float distance;
	jet.data.datatype.Residue3D resRef,resComp;
	jet.cluster.data.ProxList plRef=null,plComp=null;
	
	for(i=0;i<seq.size();i++)
	    {
		plList.add(new jet.cluster.data.ProxList(seq.getResidue(i,jet.data.datatype.Sequence3D.DIRECT),i));
	    }
	
	for(i=0;i<seq.size();i++)
	    {
		resRef=getProxList(i).getResidue();
		plRef=getProxList(i);
		
		for(j=i+1;j<seq.size();j++)
		    {
			plComp=getProxList(j);
			resComp=getProxList(j).getResidue(); 
			distance=resRef.minAtomDistance(resComp);
			if (distance<=distanceMax)
				{
				plRef.addProxNode(new jet.cluster.data.ProxNode(plComp,distance));
				plComp.addProxNode(new jet.cluster.data.ProxNode(plRef,distance));
				}
		    }
	    }
    }
   

    public void displayDistanceMap()
    {
	int i,j;
	jet.cluster.data.ProxNode pn;
	jet.cluster.data.ProxList pl;

	for(i=0;i<plList.size();i++)
	    {
		pl=getProxList(i);
		System.out.print(pl.getId()+" --> ");
		for(j=0;j<20;j++)
		    {
			pn=pl.getProxNode(j);
			System.out.print("[ "+pn.getRef().getId()+" : "+(int)pn.getDistance()+" ] ");
		    }
		System.out.println("\n\n");
	    }
    }
    
    public int getLength(){ return plList.size();}
  
}


package jet.cluster.data;

import java.util.*;

/** Classe associant à un résidu toutes ses distances aux dix résidus les plus proches (plist) */

public class ProxList
{
    private ArrayList pList;
    private jet.data.datatype.Residue3D residue;
    
    private int id;

    public ProxList(int id)
    {
	setId(id);
	pList=new ArrayList(10);
	
    }


    public ProxList(jet.data.datatype.Residue3D residue, int id)
    {
	setId(id);
	setResidue(residue);
	
	pList=new ArrayList(10);
	
    }

    public int getId(){return id;}
    
    public void setId(int id){this.id=id;}

    public jet.data.datatype.Residue3D getResidue(){ return residue;}
    
    public void setResidue(jet.data.datatype.Residue3D residue){this.residue=residue;}
    
  
   
    public void addProxNode(jet.cluster.data.ProxNode pn)
    {
	int i=0;

	while(i<pList.size())
	    {
		if(getProxNode(i).getDistance()>pn.getDistance()) break;
		i++;
	    }
	
	if(i==pList.size()) pList.add(pn);
	else pList.add(i,pn);
	    
    }

    public ProxNode getProxNode(int index) { return (jet.cluster.data.ProxNode) pList.get(index); }
    
    public int getLength(){ return pList.size(); }

    public Vector getNeighbourResidues(float radius)
    {
	int i=0;
	Vector neibs=new Vector(1,1);
	//System.out.println(getProxNode(i).getDistance()+" --> "+radius);
	while((i<pList.size())&&(getProxNode(i).getDistance()< radius )) 
	    {
		//System.out.print("hi ho");
		//neibs.add(getProxNode(i++).getResidue());
		neibs.add(getProxNode(i++));
	    }
	//System.out.print("\n");
	return neibs;
    }

}

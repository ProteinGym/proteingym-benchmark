package jet.cluster.data;

import java.util.*;

/** Classe stockant pour chaque atome toutes les distances aux autres atomes */

public class AtomDistanceMap extends DistanceMap 
{
    
    public AtomDistanceMap(jet.data.datatype.Sequence3D seq,float distanceMax) {	
        super(seq,distanceMax);
        //do whatever you want to do in your constructor here
    }

   public void initDistanceMap(jet.data.datatype.Sequence3D seq,float distanceMax)
    {
	int i,j;
	int nbBonded;
	float distance;
	jet.data.datatype.Atom atomRef,atomComp;
	jet.cluster.data.ProxListAt plRef=null,plComp=null;
	int n=0;

	for (int pos=0;pos<seq.size();pos++)
	    {
		Vector atoms=seq.getResidue(pos,jet.data.datatype.Sequence3D.DIRECT).getAllAtoms();
		for (int posAtom=0;posAtom<atoms.size();posAtom++)
		    {
			plList.add(new jet.cluster.data.ProxListAt((jet.data.datatype.Atom)atoms.get(posAtom),((jet.data.datatype.Atom)atoms.get(posAtom)).getPosition()));
			n++;
		    }
	    }

	for (i=0;i<plList.size();i++)
	    {
		plRef=(jet.cluster.data.ProxListAt) getProxList(i);
		atomRef=plRef.getAtom();
		nbBonded=0;
		for(j=i+1;j<plList.size();j++)
		    {
			plComp=(jet.cluster.data.ProxListAt) getProxList(j);
			atomComp=plComp.getAtom(); 
			distance=atomRef.distance(atomComp);
			/* get atom in the neighbour list only if it is closer than 
			   the cutoff value and if it is not bonded to the atom
			   assume that any two non-bonded atoms should be at a distance greater than 2 A */
			if ( (distance<=distanceMax) && (distance > 2.0) )
			    {
				plRef.addProxNode(new jet.cluster.data.ProxNodeAt(plComp,distance));
				plComp.addProxNode(new jet.cluster.data.ProxNodeAt(plRef,distance));
			    }
			else
			    {
				nbBonded++;
			    }
		    }
		//	if(nbBonded>2){System.out.println("WARNING this atom has more than two bonded neighbours");}
	    }
    }
    
}

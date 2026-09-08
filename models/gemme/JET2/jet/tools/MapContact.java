package jet.tools;

import java.util.*;
import java.io.*;

/** Classe dont les methodes permmettent d'identifier à partir des coordonnées 3D de deux 
 * structures (issus du meme fichier pdb) les residus à l'interface de ces deux structures */

public class MapContact 
{
   
	/** Retourne un vecteur de Double indiquant pour chacune des deux sequences les residus à 
     * l'interface (5.0) avec l'autre sequence et ceux qui ne le sont pas (0.0). Les residus
     * à l'interface de chacune des structures sont ceux dont la distance avec l'autre structure 
     * est inferieur à cutoff*/
	
    public static Vector mapToDouble(jet.data.datatype.Sequence3D seqR, jet.data.datatype.Sequence3D seqL, float probeRadius, double cutoff)
    {
	Double near=new Double(5.0),far=new Double(0.0); 
	Vector m;
	if (probeRadius==0) m=mapToBoolean(seqR, seqL, cutoff);
	else m=mapToBoolean(seqR, seqL, probeRadius, cutoff);
	Iterator map;
	Vector result=new Vector(2),resCol=new Vector(m.size());
	
	for(int i=0;i<2;i++)
	    {
		map=((Vector)m.get(i)).iterator();
		resCol=new Vector(((Vector)m.get(i)).size());
		
		while(map.hasNext())
		    {
			if(((Boolean)map.next()).booleanValue()) resCol.add(near);
			else resCol.add(far);
		    }
		result.add(resCol);
	    }
	return result;
    }
    
    /** Retourne un vecteur de booleen indiquant pour chacune des deux sequences les residus à 
     * l'interface (true) avec l'autre sequence et ceux qui ne le sont pas (false). Les residus
     * à l'interface de chacune des structures sont ceux dont la distance avec l'autre structure 
     * est inferieur à cutoff. Cette fonction est identique à la fonction TestAccess.getInterface 
     * sauf que la distance minimale est calculée ici sur tous les atomes du residus alors que 
     * dans la classe TestAccess c'est fait pour les atomes de la chaine laterale. */
    
    public static Vector mapToBoolean(jet.data.datatype.Sequence3D seqR, jet.data.datatype.Sequence3D seqL, double cutoff)
    {
	int i,j;
	Vector interfaceTable = new Vector(2);
	Vector distR=new Vector(seqR.size()), distL=new Vector(seqL.size());
	distR.setSize(seqR.size());  
	distL.setSize(seqL.size());
	
	Boolean near=new Boolean(true),far=new Boolean(false); 
	
	for(i=0;i<seqR.size();i++)
	    {
		
		for(j=0;j<seqL.size();j++)
		    {
			
			if((seqR.getResidue(i,1).minAtomDistance(seqL.getResidue(j,1)))<cutoff)
			    {
				distR.setElementAt(near,i);
				distL.setElementAt(near,j);
			    }
		    }
	    }
	
	for(i=0;i<distR.size();i++)
	    {
		if((Boolean)distR.get(i)==null) distR.setElementAt(far,i);
	    }
	
	for(i=0;i<distL.size();i++)
	    {
		if((Boolean)distL.get(i)==null) distL.setElementAt(far,i);
		
	    }	
	
	interfaceTable.add(distR); interfaceTable.add(distL);
	
	return interfaceTable;
    }
    
    /** Retourne un vecteur de booleen indiquant pour chacune des deux sequences les residus à 
     * l'interface (true) avec l'autre sequence et ceux qui ne le sont pas (false). Les residus
     * à l'interface de chacune des structures sont ceux dont la surface accessible (à la sonde 
     * de diametre probeRadius) varie d'au moins surfComplVar angstrom carre avant et apres 
     * complexation des deux sequences. */
    
    public static Vector mapToBoolean(jet.data.datatype.Sequence3D seqR, jet.data.datatype.Sequence3D seqL, float probeRadius, double surfComplVar)
    {
	int i;
	
	/* Vecteur retourné indiquant pour les residus de chaque chaine par un booleen si celui ci est à l'interface ou non */
	Vector interfaceTable = new Vector(2);
	/* Vecteur de booleen pour chaque chaine */
	Vector distR=new Vector(seqR.size()), distL=new Vector(seqL.size());	
	distR.setSize(seqR.size());  
	distL.setSize(seqL.size());		
	/* Vecteurs contenant les surfaces accessible de chaque residu de chaque chaine */
	Vector surfAccessR=new Vector(seqR.size()), surfAccessL=new Vector(seqL.size());
	Vector surfAccessComplR=new Vector(seqR.size()), surfAccessComplL=new Vector(seqL.size());
	
	Boolean near=new Boolean(true),far=new Boolean(false); 
	/* mapping sur chaque residu des surfaces accessibles avant complexation des deux sequences. */
	//System.out.println("debut mapSurfAccess avant complexation");
	//jprotein.tools.MapAccess.mapToBoolean(seqR,0.5,probeRadius);
	//System.out.println("debut mapSurfAccess avant complexation");
	jet.tools.MapAccess.mapSurfAccess(seqR,probeRadius);
	jet.tools.MapAccess.mapSurfAccess(seqL,probeRadius);
	//System.out.println("fin mapSurfAccess avant complexation");
	/* recuperation en angtrom carre de la surface accessible pour chaque residu. */
	//System.out.println("debut resSurfAccess");
	surfAccessR=jet.tools.MapAccess.resSurfAccess(seqR);
	surfAccessL=jet.tools.MapAccess.resSurfAccess(seqL);
	//System.out.println("fin resSurfAccess");
	/* mapping sur chaque residu des surfaces accessibles apres complexation des deux sequences. */
	//System.out.println("debut mapSurfAccess apres complexation");
	jet.tools.MapAccess.mapSurfAccess(seqR,seqL,probeRadius);
	//System.out.println("fin mapSurfAccess apres complexation");
	/* recuperation en angtrom carre de la surface accessible pour chaque residu. */
	//System.out.println("debut resSurfAccess");
	surfAccessComplR=jet.tools.MapAccess.resSurfAccess(seqR);
	surfAccessComplL=jet.tools.MapAccess.resSurfAccess(seqL);
	//System.out.println("fin resSurfAccess");
	/* Calcul des residus à l'interface de la premiere sequence en fonction 
	 * de la variation de leur surface accessible avant et apres complexation. */	
	//System.out.println("debut calcul res surf");
	for(i=0;i<seqR.size();i++)
	    {
		/* residu à l'interface si la surface accessible varie d'au moins surfComplVar A². */
		//if(((Double)surfAccessR.get(i)-(Double)surfAccessComplR.get(i))>surfComplVar)
		/* residu à l'interface si la surface accessible varie d'au moins 10%. */
		if(((Double)surfAccessComplR.get(i)/(Double)surfAccessR.get(i))<0.90)
			{
			distR.setElementAt(near,i);
			}
		else distR.setElementAt(far,i);
	    }
	/* Calcul des residus à l'interface de la deuxieme sequence en fonction 
	 * de la variation de leur surface accessible avant et apres complexation. */
	//System.out.println("fin calcul res surf");
	//System.out.println("debut calcul res surf");
	for(i=0;i<seqL.size();i++)
    	{
		/* residu à l'interface si la surface accessible varie d'au moins surfComplVar A². */
		//if(((Double)surfAccessL.get(i)-(Double)surfAccessComplL.get(i))>surfComplVar)
		/* residu à l'interface si la surface accessible varie d'au moins 10%. */
		if(((Double)surfAccessComplL.get(i)/(Double)surfAccessL.get(i))<0.90)
			{
			distL.setElementAt(near,i);
			}
		else distL.setElementAt(far,i);
    	}
	//System.out.println("fin calcul res surf");
	interfaceTable.add(distR); interfaceTable.add(distL);
	
	return interfaceTable;
    }   
   
    
    public static Vector resContactNaccess(File pdbFileBound, double probeRadius, double contactCutoff, String command) throws jet.exception.NaccessException
    {
    	Vector result=new Vector(7);
    	
	// run Naccess and get info on bound complex
    	Vector rsaComplexBound=jet.tools.MapAccess.resSurfNaccess(pdbFileBound, probeRadius,true,command)[0];

    	/* -1 car on n'a pas besoin du pourcentage de surface accessible */
    	for (int i=0;i<(rsaComplexBound.size()-1);i++)
    	{
    		result.add(rsaComplexBound.get(i));
    	}

	// !!!!! rsaComplexBound is in fact: asaComplexBound !!!!
    	rsaComplexBound=(Vector)rsaComplexBound.get(3);

    	/* Calcul du nombre de chaines */
    	int nbChaines=1;
    	String chaineCourante=(String)((Vector)result.get(1)).get(0);
    	
    	for (int i=0;i<((Vector)result.get(1)).size();i++)
    	{
    		if (!chaineCourante.equals((String)((Vector)result.get(1)).get(i)))
    		{
    			nbChaines++;
    			chaineCourante=(String)((Vector)result.get(1)).get(i);
    		}
    	}
    	
    	jet.io.file.PdbFileTransform pdbft;
    	pdbft= new jet.io.file.PdbFileTransform(pdbFileBound.getPath(),"_naccessCutFilesTemp");
    	Vector cut;
    	
    	Vector rsaComplexUnbound=new Vector();
    	
    	File dir=pdbFileBound.getParentFile();
		File[] dirFiles;
		int nb=0;
		while(nb<nbChaines)
    	{
    	
			cut=new Vector();
    		cut.add(nb);
    		pdbft.cut(cut,false);
    		dirFiles=dir.listFiles();
    		for (int i=0;i<dirFiles.length;i++)
	    	{
	    		if (dirFiles[i].getAbsolutePath().lastIndexOf("_naccessCutFilesTemp")!=-1)
				{
				    // run Naccess and get info on unbound partners
				    // !!!!! rsaComplexUnbound is in fact: asaComplexUnbound !!!!
				    rsaComplexUnbound.addAll((Vector)jet.tools.MapAccess.resSurfNaccess(dirFiles[i],probeRadius,false,command)[0].get(3));
				    dirFiles[i].delete();
				}
	    	}
	    	nb++;
    	}
	    result.add(rsaComplexUnbound);
    	
    	if (rsaComplexUnbound.size()!=rsaComplexBound.size())
    	{
    		System.err.println("fichier bound et unbound de taille differentes");
    		System.err.println("Impossible de calculer une interface");
    		System.exit(1);
    	}
    	
    	Vector rsaVariation=new Vector();
    	Vector interfaceResidu=new Vector();
    	
    	for (int i=0;i<rsaComplexUnbound.size();i++)
    	{
    		if ((Double)rsaComplexUnbound.get(i)==0.0)
    		{
    			interfaceResidu.add(new Double(0.0));
    			rsaVariation.add(0.0);
    		}
    		else
    		{
		    rsaVariation.add(1-((Double)rsaComplexBound.get(i)/(Double)rsaComplexUnbound.get(i)));
		    // changed for strictly above threshold
		    if ((Double)rsaVariation.lastElement()>contactCutoff) interfaceResidu.add(new Double(5.0));
		    else interfaceResidu.add(new Double(0.0));
    		}
    	}
    	
    	result.add(rsaVariation);
    	result.add(interfaceResidu);
    	
    	return result;
    }
}

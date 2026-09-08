package jet;

import java.util.*;
import java.io.*;

/** Classe permettant de calculer à partir d'un ensemble de fichier pdb ligand-recepteur, 
 * des caractéristiques de leurs interfaces:
 * <BR> - %age des residus de l'interface du recepteur qui sont accessibles  
 * <BR> - %age des residus de l'interface du ligand qui sont accessibles
 * <BR> - meme %age mais en tenant compte des deux structures (ligand et recepteur) 
 * <BR> - nombre de residus à l'interface en moyenne pour chacune des structures. */

public class TestAccess
{
	
	/** Main permettant de calculer à partir d'un ensemble de fichiers pdb ligand-recepteur
	 * (couples de fichiers de nom pdbName_r_b.pdb et pdbName_l_b.pdb), des caractéristiques 
	 * de leurs interfaces. Les arguments sont les suivants dans cet ordre: 
	 * <BR> Répertoire des fichier pdb
	 * <BR> Distance maximale entre deux residus (de deux chaines différentes) pour considérer
	 *  qu'ils sont à une interface
	 * <BR> Pourcentage de surface accessible pour considérer un residu comme accessible. */
	
    public static void main(String[] args)
    {
	if(args.length!=3) 
	    {
		System.err.println("Missing arguments : \n\njava jprotein.TestAccess [directory] [interface maxDist] [min accessibility ratio]");
		System.exit(1);
	    }
	/* Récupération des arguments */
	File dir=new File(args[0]);	
	double maxDist=Double.valueOf(args[1]).doubleValue();
	double minAccess=Double.valueOf(args[2]).doubleValue();
	/* Initialisation du compteur de temps */
	int i,beginTime=(int)System.currentTimeMillis();
	double time, estimate;
	
	File[] files=dir.listFiles();
	String filename;
	Vector pdbList=new Vector(1,1);
	double[] res;

	/* On stocke toutes les racines communes aux couples de fichiers ligand-recepteur */
	for(i=0;i<files.length;i++)
	    {
		/* Recupération de la racine commune au couple de fichier ligand-recepteur */
		filename=files[i].getAbsolutePath();
		System.out.println(""+filename);
		filename=filename.substring(0,filename.lastIndexOf("_"));
		System.out.println(""+filename);
		filename=filename.substring(0,filename.lastIndexOf("_"));
		System.out.println(""+filename);
		if(!pdbList.contains(filename))
		    {
			pdbList.add(filename);
		    }
	    }

	System.out.println("Total structures to analyse : "+pdbList.size()*2);
	Vector muR=new Vector(1,1), muL=new Vector(1,1), muA=new Vector(1,1);
	Vector muAR=new Vector(1,1);
	String name;
	/* Récupération des resultats de la fonction analyse sur chaque couple de structures */
	for(i=0;i<pdbList.size();i++)
	    {
		name=(String)pdbList.get(i);
		name=name.substring(name.lastIndexOf("\\")+1);
		res=analyse((String)pdbList.get(i),minAccess,maxDist);
		System.out.println(name+" : "+res[0]+"\t"+res[1]+"\t"+res[2]+"\t"+res[3]);
		muR.add(new Double(res[0])); muL.add(new Double(res[1])); muA.add(new Double(res[2]));
		muAR.add(new Double(res[3]));
	    }
	/* Affichage des resultats apres analyse statistique (moyenne et dispersion) */
	System.out.println("Receptor access : "+jet.tree.tools.Statistic.mean(muR)+" +/- "+jet.tree.tools.Statistic.standardError(muR));
	System.out.println("Ligand access : "+jet.tree.tools.Statistic.mean(muL)+" +/- "+jet.tree.tools.Statistic.standardError(muL));
	System.out.println("Total access : "+jet.tree.tools.Statistic.mean(muA)+" +/- "+jet.tree.tools.Statistic.standardError(muA));
	System.out.println("Residue in interface : "+jet.tree.tools.Statistic.mean(muAR)+" +/- "+jet.tree.tools.Statistic.standardError(muAR));
	/* Affichage du temps écoulé */
	System.out.println("Analysis took "+(((double)((int)System.currentTimeMillis()-beginTime))/60000.0)+" minutes");
    }
    
    
    /** Retourne un tableau de 4 resultats: 
     * <BR> - %age des residus de l'interface du recepteur (fichier: pdbName_r_b.pdb) qui 
     * sont accessibles (calculés en fonction de minAccess) 
     * <BR> - %age des residus de l'interface du ligand (fichier: pdbName_l_b.pdb) qui 
     * sont accessibles (calculés en fonction de minAccess)  
     * <BR> - meme %age mais en tenant compte des deux structures (ligand et recepteur) 
     * <BR> - nombre de residus à l'interface en moyenne pour chacune des structures. */
    
    public static double[] analyse(String pdbName, double minAccess, double maxDist)
    {
	int i,j;
	int wR=0,wL=0,rR=0,rL=0;
	double[] res=new double[4];

	Double yep=new Double(80.00), nope=new Double(30.00);

	jet.data.datatype.Sequence3D seqR=null,seqL=null;
	jet.io.file.PdbFileReader pdb;
    /* Recuperation du recepteur */
	pdb=new jet.io.file.PdbFileReader(pdbName+"_r_b.pdb");
	Vector pdbInfo=jet.data.dataformat.parser.PDB.getSequenceInfo(pdb,false);	
	seqR=((jet.data.dataformat.info.PdbSequenceInfo)pdbInfo.get(0)).getSequence();	
	//seqR=(jprotein.data.datatype.Sequence3D)pdbInfo.getData().get(0);
	/* Recuperation du ligand */
	pdb=new jet.io.file.PdbFileReader(pdbName+"_l_b.pdb");
	pdbInfo=jet.data.dataformat.parser.PDB.getSequenceInfo(pdb,false);	
	seqL=((jet.data.dataformat.info.PdbSequenceInfo)pdbInfo.get(0)).getSequence();	
	//seqL=(jprotein.data.datatype.Sequence3D)pdb.getData().get(0);
	
	/*********************************Test start*******************/
	
	/* Etude de l'accessiblité du recepteur */
	Vector surfColor;
	
	Vector surfR=access2PDB(seqR,minAccess,1.4f);
	surfColor=new Vector(surfR.size());
	for(i=0;i<surfR.size();i++)
	    {
		if(((Boolean)surfR.get(i)).booleanValue()) surfColor.add(yep);
		else surfColor.add(nope);
	    }
	/* Ecriture de l'accessiblité du recepteur dans un fichier pdb*/
	new jet.io.file.PdbFileTransform(pdbName+"_r_b.pdb","_axs",surfColor);
	/* Etude de l'accessiblité du ligand */
	Vector surfL=access2PDB(seqL,minAccess,1.4f);
	surfColor=new Vector(surfL.size());
	for(i=0;i<surfL.size();i++)
	    {
		if(((Boolean)surfL.get(i)).booleanValue()) surfColor.add(yep);
		else surfColor.add(nope);
	    }
	/* Ecriture de l'accessiblité du ligand dans un fichier pdb*/
	new jet.io.file.PdbFileTransform(pdbName+"_l_b.pdb","_axs",surfColor);
	
	/* Recuperation de l'interface entre les deux chaines protéiques */
	Vector v=getInterface(seqR, seqL, maxDist);
	Vector interR=(Vector)v.get(0); 
	Vector interL=(Vector)v.get(1);

	/* Calcul du nombre de residus à l'interface du recepteur, accessible (rR) et non accessible (wR) */
	for(i=0;i<interR.size();i++)
	    {
		if(((Boolean)interR.get(i)).booleanValue())
		    {
			if(((Boolean)surfR.get(i)).booleanValue()) rR++;
			else wR++;
		    }
	    }
	/* Calcul du nombre de residus à l'interface du ligand, accessible (rL) et non accessible (wL) */
	for(i=0;i<interL.size();i++)
	    {
		if(((Boolean)interL.get(i)).booleanValue())
		    {
			if(((Boolean)surfL.get(i)).booleanValue()) rL++;
			else wL++;
		    }
	    }
	/* Retour des resultats */
	res[0]=((double)rR)/((double)(wR+rR));
	res[1]=((double)rL)/((double)(wL+rL));
	res[2]=((double)(rR+rL))/((double)(wR+wL+rR+rL)); 
	res[3]=((double)(wR+wL+rR+rL))/2.0;
	
	return res;
    }
  
    /** Retourne un vecteur de booleen etablissant si le residu i de la sequence de reference 
     * est accessible ou non. Idem methode mapToBoolean de la classe jprotein.MapAccess */
    
    public static Vector access2PDB(jet.data.datatype.Sequence3D ref, double minAccess, float probeRadius)
    {
       
	int i,j,k,l,c;
	float dist;
	double sum;
      
	Vector residueI=null,residueK=null,residuePrev=null,residueNext=null,result=new Vector(ref.size()); 
	Vector percentTable=new Vector(1,1);
	Boolean yes=new Boolean(true), no=new Boolean(false);
	jet.data.datatype.Atom atomJ,atomL;

	for(i=0;i<ref.size();i++)
	    {
		sum=0.0;
		//residueI=ref.getResidue(i,1).getAllAtoms(); 
		if(i!=0) 
		    { 
			residuePrev=residueI;
			residueI=residueNext;
		    }
		else 
		    {
			residueI=ref.getResidue(0,1).getAllAtoms(); 
		    }
		
		if(i<ref.size()-1) 
		    {
			residueNext=ref.getResidue(i+1,1).getAllAtoms();
		    }
		
		for(j=0;j<residueI.size();j++)
		    {
			atomJ=(jet.data.datatype.Atom)residueI.get(j);
			
			for(l=0;l<residueI.size();l++)
			    {
				if(j!=l)
				    {
					atomL=(jet.data.datatype.Atom)residueI.get(l);
					dist=atomJ.getRadius()+atomL.getRadius()+probeRadius;
					if(atomJ.distance(atomL)<dist) atomJ.setAccessibility(atomL,probeRadius,0.0f);
				    }
			    }

			if(i!=0)
			    {
				for(l=0;l<residuePrev.size();l++)
				    {
					atomL=(jet.data.datatype.Atom)residuePrev.get(l);
					dist=atomJ.getRadius()+atomL.getRadius()+probeRadius;
					if(atomJ.distance(atomL)<dist) atomJ.setAccessibility(atomL,probeRadius,0.0f);
				    }
			    }

			if(i<ref.size()-1)
			    {
				for(l=0;l<residueNext.size();l++)
				    {
					atomL=(jet.data.datatype.Atom)residueNext.get(l);
					dist=atomJ.getRadius()+atomL.getRadius()+probeRadius;
					if(atomJ.distance(atomL)<dist) atomJ.setAccessibility(atomL,probeRadius,0.0f);
				    }
			    }
		    }
		
		for(j=0;j<residueI.size();j++)
		    {
			atomJ=(jet.data.datatype.Atom)residueI.get(j);
			sum+=atomJ.getAtomAccessibleSurfaceArea();
		    }
		percentTable.add(new Double(sum)); 
	    }
	
	//for all residues, measure surface accessibility considering the rest of the protein
	
	for(i=0;i<ref.size();i++)
	    {
		residueI=ref.getResidue(i,1).getAllAtoms();
		
		//for all atoms in this residue
		
		for(j=0;j<residueI.size();j++)
		    {
			atomJ=(jet.data.datatype.Atom)residueI.get(j);
			
			//for all residues other than himself and his two neighbours
			for(k=0;k<ref.size();k++)
			    {
				if(k!=i)
				{
				    //if the space seperating the considered atom and the residue 
				    //is not sufficient to insert the probe
				    if(atomJ.distance(ref.getResidue(k,1)) < probeRadius+atomJ.getRadius()+ref.getResidue(k,1).getRadius()+2.2f)
					{
					    residueK=ref.getResidue(k,1).getAllAtoms();
					    //for all atoms in this residue
					    for(l=0;l<residueK.size();l++)
						{
						    atomL=(jet.data.datatype.Atom)residueK.get(l);
						    dist=atomJ.getRadius()+atomL.getRadius();
						    
						    //if the atoms are in contact, check the influence on the considered atom's accessibilty
						    if(atomJ.distance(atomL)<dist+probeRadius) 
							{
							    atomJ.setAccessibility(atomL,probeRadius,0.0f);
							}
						    
						}
					}
				}
			    }
			
		    }
		
	    }	
	
	
	for(i=0;i<ref.size();i++)
	    {
		c=0;
		residueI=ref.getResidue(i,1).getAllAtoms();
		sum=0.0;
		for(j=0;j<residueI.size();j++) 
		    {
			atomJ=(jet.data.datatype.Atom)residueI.get(j);
			sum+=atomJ.getAtomAccessibleSurfaceArea();  
		    }
		if(sum>minAccess*((Double)percentTable.get(i)).doubleValue()) result.add(no);
		else result.add(yes);
	    }


	return result;
	
    }
    
    /** Retourne un vecteur de booleen idenquant pour chacune des deux sequences les residus à 
     * l'interface (true) avec l'autre sequence et ceux qui ne le sont pas (false). Les residus
     * à l'interface de chacune des structures sont ceux dont la distance avec l'autre structure 
     * est inferieur à minDist */
    
    public static Vector getInterface(jet.data.datatype.Sequence3D seqR, jet.data.datatype.Sequence3D seqL, double minDist)
    {
	int i,j;
	float dist;
	Vector interfaceTable = new Vector(2);
	Vector distR=new Vector(seqR.size()), distL=new Vector(seqL.size());
	distR.setSize(seqR.size());  
	distL.setSize(seqL.size());
	
	Boolean near=new Boolean(true),far=new Boolean(false); 
	
	for(i=0;i<seqR.size();i++)
	    {
		
		for(j=0;j<seqL.size();j++)
		    {
			
			if((dist=seqR.getResidue(i,1).minSideChainDistance(seqL.getResidue(j,1)))<minDist)
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
}


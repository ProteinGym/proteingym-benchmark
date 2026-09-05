package jet.tools;

import java.util.*;
import java.io.*;

/** Classe contenant des methodes statiques pour etablir l'accessibilité 
 * des residus d'une sequence */

public class MapAccess
{

	/** Retourne un vecteur de Double contenant 0.3 si le residu n'est pas accessible et 0.6 
	 * si il l'est. */
	
	public static Vector mapToDouble(jet.data.datatype.Sequence3D ref, double minAccess, float probeRadius)
    {
	Vector m=mapToBoolean(ref, minAccess, probeRadius);
	Vector result_total=new Vector(2);
	Vector result=new Vector(((Vector)m.get(0)).size());
	Iterator iter=((Vector)m.get(0)).iterator();
	Double yes=new Double(1.0), no=new Double(0.0);
	while(iter.hasNext())
	    {
		if(((Boolean)iter.next()).booleanValue()) 
		    result.add(yes);
		else result.add(no);
	    }
	result_total.add(result);
	result_total.add(m.get(1));
	return result_total;
    }
      
    /** Retourne un vecteur de booleen etablissant si le residu i de la sequence de reference 
     * est accessible ou non. Cette accessibilité est fonction du rapport entre la surface 
     * accessible lorsque l'on considere l'encombrement du à tous les residus de la proteine 
     * et lorsque l'on considere uniquement l'encombrement des residus precedents et suivants. 
     * Si ce rapport est superieur à minAccess alors le residu est dit accessible par la sonde 
     * de rayon probeRadius. */  	
      
	public static Vector mapToBoolean(jet.data.datatype.Sequence3D ref, double minAccess, float probeRadius)
    {
	int i,j,k,l;
	float dist;
	double sum;
      
	Vector residueI=null,residueK=null,residuePrev=null,residueNext=null,result=new Vector(ref.size()); 
	Vector percentTable=new Vector(1,1);
	Boolean yes=new Boolean(true), no=new Boolean(false);
	
	jet.data.datatype.Atom atomJ,atomL;
	
	/* Pour chaque residu, on calcule l'accessibilité de tous les atomes par une 
	 * sonde en considerant l'encombrement du aux atomes de ce residu et aux atomes 
	 * des residu precedent et suivant dans la sequence ref. On calcule aussi la surface 
	 * accessible de chaque residus en fonction de ce meme encombrement. */
	for(i=0;i<ref.size();i++)
	    {
		//System.out.println("debut calcul accessibility resI voisins:"+i);
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
		/* Pour chaque atomes de residuI on calcule petit à petit les points 
		 * qui ne sont pas accessibles par une sonde à cause de l'encombrement 
		 * du aux autres atomes de residuI (au debut tous les points de tous 
		 * les atomes sont accessibles) */
		for(j=0;j<residueI.size();j++)
		    {
			atomJ=(jet.data.datatype.Atom)residueI.get(j);
			
			for(l=0;l<residueI.size();l++)
			    {
				if(j!=l)
				    {
					atomL=(jet.data.datatype.Atom)residueI.get(l);
					//dist=atomJ.getRadius()+atomL.getRadius()+probeRadius;
					dist=atomJ.getRadius()+atomL.getRadius()+(probeRadius*2);
					/* Si la distance entre les deux atomes est inferieur à dist 
					 * alors certains points de l'atome "atomJ" ne sont pas accesibles par 
					 * la sonde de rayon probeRaduis entre ces deux atomes 
					 * => il faut les calculer */
					if(atomJ.distance(atomL)<dist) atomJ.setAccessibility(atomL,probeRadius,0.0f);
				    }
			    }

			if(i!=0)
			    {
				for(l=0;l<residuePrev.size();l++)
				    {
					atomL=(jet.data.datatype.Atom)residuePrev.get(l);
					//dist=atomJ.getRadius()+atomL.getRadius()+probeRadius;
					dist=atomJ.getRadius()+atomL.getRadius()+(probeRadius*2);
					if(atomJ.distance(atomL)<dist) atomJ.setAccessibility(atomL,probeRadius,0.0f);
				    }
			    }

			if(i<ref.size()-1)
			    {
				for(l=0;l<residueNext.size();l++)
				    {
					atomL=(jet.data.datatype.Atom)residueNext.get(l);
					//dist=atomJ.getRadius()+atomL.getRadius()+probeRadius;
					dist=atomJ.getRadius()+atomL.getRadius()+(probeRadius*2);
					if(atomJ.distance(atomL)<dist) atomJ.setAccessibility(atomL,probeRadius,0.0f);
				    }
			    }
		    }
		/* On calcule la surface accessible de residuI (somme des surfaces 
		 * accessibles de chaque atome de ce residu) */
		for(j=0;j<residueI.size();j++)
		    {
			atomJ=(jet.data.datatype.Atom)residueI.get(j);
			sum+=atomJ.getAtomAccessibleSurfaceArea();
		    }
		percentTable.add(new Double(sum)); 
	    }
	
	/* for all residues, measure surface accessibility considering the rest of the protein */
	
	for(i=0;i<ref.size();i++)
	    {
		//System.out.println("debut calcul accessibility resI tout:"+i);
		residueI=ref.getResidue(i,1).getAllAtoms();
		
		/* for all atoms in this residue */
		
		for(j=0;j<residueI.size();j++)
		    {
			atomJ=(jet.data.datatype.Atom)residueI.get(j);
			
			/* for all residues other than himself and his two neighbours 
			 * (j'ai pas l'impression qu'on retire les deux voisins)*/
			for(k=0;k<ref.size();k++)
			    {
				if(k!=i)
				{
				    /* if the space seperating the considered atom and the residue 
				     * is not sufficient to insert the probe (pourquoi 2.2f: parceque getRadius est la 
				     * distance max entre le centre de gravité du residu et le centre de l'atome, il 
				     * manque le rayon de l'atome 1.507 le plus gros (pourquoi N et pas S) plus la moitie de la sonde d'eau (1.4/2)) */
				    //if(atomJ.distance(ref.getResidue(k,1)) < probeRadius+atomJ.getRadius()+ref.getResidue(k,1).getRadius()+2.2f)
					if(atomJ.distance(ref.getResidue(k,1)) < (probeRadius*2)+atomJ.getRadius()+ref.getResidue(k,1).getRadius()+1.848f)
					{
					    residueK=ref.getResidue(k,1).getAllAtoms();
					    /* for all atoms in this residue */
					    for(l=0;l<residueK.size();l++)
						{
						    atomL=(jet.data.datatype.Atom)residueK.get(l);
						    //dist=atomJ.getRadius()+atomL.getRadius()+probeRadius;
						    dist=atomJ.getRadius()+atomL.getRadius()+(probeRadius*2);
						    /* if the atoms are in contact, check the influence on the considered atom's accessibilty */
						    if(atomJ.distance(atomL)<dist) 
							{
							    atomJ.setAccessibility(atomL,probeRadius,0.0f);
							}
						    
						}
					}
				}
			    }
			
		    }
		
	    }	
	
	/* On etabli l'accessibilité ou non d'un residu en fonction du pourcentage que represente 
	 * la surface accessible reelle d'un residu par rapport à la surface accessible lorsque 
	 * l'on considere uniquement l'encombrement des residus precedents et suivants. Si ce 
	 * pourcentage est superieur à minAccess alors le residu est dit accessible 
	 * (conforme à l'article). */
	Vector result_surf_axs=new Vector(1,1);
	for(i=0;i<ref.size();i++)
	    {
		residueI=ref.getResidue(i,1).getAllAtoms();
		sum=0.0;
		for(j=0;j<residueI.size();j++) 
		    {
			atomJ=(jet.data.datatype.Atom)residueI.get(j);
			sum+=atomJ.getAtomAccessibleSurfaceArea();  
		    }
		if(sum>minAccess*((Double)percentTable.get(i)).doubleValue()) result.add(yes);
		else result.add(no);
		result_surf_axs.add(new Double(sum)); 
	    }
	Vector result_total=new Vector(2);
	result_total.add(result);
	result_total.add(result_surf_axs);
	return result_total;
	
    }

	public static void mapSurfAccessMoi(jet.data.datatype.Sequence3D ref, float probeRadius)
	{
	int i,j,k,l;
	float dist;
      
	Vector residueI=null,residueK=null; 
	//float radiusMax=0.0f;
	//String aaMax="";
	//int nbAA=0;
	//int nbAtomComp=0;
	
	jet.data.datatype.Atom atomJ,atomL;
			
	/* for all residues, measure surface accessibility considering the rest of the protein */
	
	for(i=0;i<ref.size();i++)
	{
		
		//System.out.println("debut calcul accessibility resI:"+i);
		//System.out.println("rayon res "+ref.getResidue(i,1).getResidueSymbol()+"="+ref.getResidue(i,1).getRadius());
		//System.out.println("rayon max res "+aaMax+"="+radiusMax);
		//nbAA=0;
		//nbAtomComp=0;
		for(k=0;k<ref.size();k++)
	    {
			//if (radiusMax<ref.getResidue(k,1).getRadius())
			//{
			//	radiusMax=ref.getResidue(k,1).getRadius();
			//	aaMax=""+ref.getResidue(k,1).getResidueSymbol();
			//}
			//System.out.println("rayon res "+ref.getResidue(k,1).getResidueSymbol()+"="+ref.getResidue(k,1).getRadius());
			if(ref.getResidue(i,1).distance(ref.getResidue(k,1)) <(probeRadius*2)+ref.getResidue(i,1).getRadius()+ref.getResidue(k,1).getRadius())
			{
				//nbAA++;
				residueI=ref.getResidue(i,1).getAllAtoms();
				for(j=0;j<residueI.size();j++)
			    {
					atomJ=(jet.data.datatype.Atom)residueI.get(j);
					if(atomJ.distance(ref.getResidue(k,1)) <(probeRadius*2)+atomJ.getRadius()+ref.getResidue(k,1).getRadius())
					{
						residueK=ref.getResidue(k,1).getAllAtoms();
						for(l=0;l<residueK.size();l++)
				    	{
							atomL=(jet.data.datatype.Atom)residueK.get(l);
							//dist=atomJ.getRadius()+atomL.getRadius()+probeRadius;
							dist=atomJ.getRadius()+atomL.getRadius()+(probeRadius*2);
							/* if the atoms are in contact, check the influence on the considered atom's accessibilty */
							if(atomJ.distance(atomL)<dist) 
							{
								atomJ.setAccessibility(atomL,probeRadius,0.0f);
								//nbAtomComp++;
							}
				    	}
					}
			    }
			}
	    }
		//System.out.println("nbAA="+nbAA+" nb Comp="+nbAtomComp);
		//System.out.println("fin calcul accessibility resI:"+i);
	}	
	}
    

	public static void mapSurfAccess(jet.data.datatype.Sequence3D ref, float probeRadius)
		{
		int i,j,k,l;
		float dist;
	      
		Vector residueI=null,residueK=null; 
		
		jet.data.datatype.Atom atomJ,atomL;
				
		/* for all residues, measure surface accessibility considering the rest of the protein */
		
		for(i=0;i<ref.size();i++)
		    {
			residueI=ref.getResidue(i,1).getAllAtoms();
			
			/* for all atoms in this residue */
			//System.out.println("debut calcul accessibility resI:"+i);
			for(j=0;j<residueI.size();j++)
			    {
				atomJ=(jet.data.datatype.Atom)residueI.get(j);
				//System.out.println("debut calcul accessibility atom:"+j+" par rapport au atomes du meme residu");
				for(l=0;l<residueI.size();l++)
			    {
				if(j!=l)
				    {
					atomL=(jet.data.datatype.Atom)residueI.get(l);
					//dist=atomJ.getRadius()+atomL.getRadius()+probeRadius;
					dist=atomJ.getRadius()+atomL.getRadius()+(probeRadius*2);
					/* Si la distance entre les deux atomes est inferieur à dist 
					 * alors certains points de l'atome "atomJ" ne sont pas accesibles par 
					 * la sonde de rayon probeRaduis entre ces deux atomes 
					 * => il faut les calculer */
					if(atomJ.distance(atomL)<dist) atomJ.setAccessibility(atomL,probeRadius,0.0f);
				    }
			    }
				//System.out.println("fin calcul accessibility atom:"+j+" par rapport au atomes du meme residu");			
				/* for all residues other than himself and his two neighbours 
				 * (j'ai pas l'impression qu'on retire les deux voisins)*/
				//System.out.println("debut calcul accessibility atom:"+j+" par rapport aux atomes des autres residus");
				
				for(k=0;k<ref.size();k++)
				    {
					if(k!=i)
						{
					    /* if the space seperating the considered atom and the residue 
					   	 * is not sufficient to insert the probe (pourquoi 2.2f) (parceque 
					   	 * c'est la distance d'un atome à un résidus qui est plus gros, 
					   	 * peut etre un encombrement moyen d'un residu) */
					   	//if(atomJ.distance(ref.getResidue(k,1)) <probeRadius+atomJ.getRadius()+ref.getResidue(k,1).getRadius()+2.2f)
						if(atomJ.distance(ref.getResidue(k,1)) <(probeRadius*2)+atomJ.getRadius()+ref.getResidue(k,1).getRadius())
							{
					   		residueK=ref.getResidue(k,1).getAllAtoms();
						    /* for all atoms in this residue */
					   		//System.out.println("debut calcul accessibility atom:"+j+" par rapport aux atomes du residu:"+k);
						    for(l=0;l<residueK.size();l++)
						    	{
							    atomL=(jet.data.datatype.Atom)residueK.get(l);
							    //dist=atomJ.getRadius()+atomL.getRadius()+probeRadius;
							    dist=atomJ.getRadius()+atomL.getRadius()+(probeRadius*2);
							    /* if the atoms are in contact, check the influence on the considered atom's accessibilty */
							    if(atomJ.distance(atomL)<dist) 
									{
								    atomJ.setAccessibility(atomL,probeRadius,0.0f);
									}
						    	}
						    //System.out.println("debut calcul accessibility atom:"+j+" par rapport aux atomes du residu:"+k);
					   		}
						
						}
				    }
				//System.out.println("fin calcul accessibility atom:"+j+" par rapport aux atomes des autres residus");
			    }
			//System.out.println("fin calcul accessibility resI:"+i);
			}
		
    	}
		
	
	
	public static void mapSurfAccess(jet.data.datatype.Sequence3D ref1,jet.data.datatype.Sequence3D ref2, float probeRadius)
    	{
		int i,j,k,l;
		float dist;
	      
		Vector residueI=null,residueJ=null; 
		jet.data.datatype.Residue3D residue3DI=null,residue3DJ=null;
		jet.data.datatype.Atom atomI,atomJ;
				
		
		for(i=0;i<ref1.size();i++)
		    {
			residue3DI=ref1.getResidue(i,1);
			for(j=0;j<ref2.size();j++)
		    	{
				residue3DJ=ref2.getResidue(j,1);
				//System.out.println("calcul distance resI:"+i+" resJ:"+j);
				dist=residue3DI.getRadius()+residue3DJ.getRadius()+(probeRadius*2);
				//System.out.println("fin calcul distance resI:"+i+" resJ:"+j);
				if(residue3DI.distance(residue3DJ)<dist) 
					{
					//System.out.println("en interaction");
					residueI=residue3DI.getAllAtoms();
					residueJ=residue3DJ.getAllAtoms();
					//System.out.println("debut calcul access atom res:"+i+" res:"+j);
					for(k=0;k<residueI.size();k++)
				    	{
						atomI=(jet.data.datatype.Atom)residueI.get(k);
				    
						for(l=0;l<residueJ.size();l++)
					    	{
							atomJ=(jet.data.datatype.Atom)residueJ.get(l);
						    //dist=atomJ.getRadius()+atomL.getRadius()+probeRadius;
						    dist=atomI.getRadius()+atomJ.getRadius()+(probeRadius*2);
						 //   System.out.println("debut calcul access atom:"+l+" atom:"+k);
						    /* if the atoms are in contact, check the influence on the considered atom's accessibilty */
						    if(atomI.distance(atomJ)<dist) 
								{
							    atomI.setAccessibility(atomJ,probeRadius,0.0f);
							    atomJ.setAccessibility(atomI,probeRadius,0.0f);
								}
						  //  System.out.println("fin calcul access atom:"+l+" atom:"+k);
					    	}
				    	}
					//System.out.println("fin calcul access atom res:"+i+" res:"+j);
					}
		    	}
		    }
    	}
	
	
	public static Vector resSurfAccess(jet.data.datatype.Sequence3D ref)
    {
		double sum;
	      
		Vector residueI;
		Vector percentTable=new Vector(1,1);
		
		jet.data.datatype.Atom atomI;
		
		for(int i=0;i<ref.size();i++)
	    	{
			sum=0.0;
			residueI=ref.getResidue(i,1).getAllAtoms();			
			
			for(int j=0;j<residueI.size();j++)
				{
				atomI=(jet.data.datatype.Atom)residueI.get(j);
				sum+=atomI.getAtomAccessibleSurfaceArea();
				}
			percentTable.add(new Double(sum));
			
		    }
		return percentTable;
		
    }
	
	public static Vector resPercentSurfAccess(jet.data.datatype.Sequence3D ref)
    {
		double sum;
	      
		Vector residueI;
		Vector percentTable=new Vector(1,1);
		
		jet.data.datatype.Atom atomI;
		
		for(int i=0;i<ref.size();i++)
	    	{
			sum=0.0;
			residueI=ref.getResidue(i,1).getAllAtoms();			
			
			for(int j=0;j<residueI.size();j++)
				{
				atomI=(jet.data.datatype.Atom)residueI.get(j);
				sum+=atomI.getPercentAccessibility();
				}
			sum=sum/residueI.size();
			percentTable.add(new Double(sum));
			
		    }
		return percentTable;
		
    }
	
	
	public static void initAccess (jet.data.datatype.Sequence3D ref)
	{
		Vector residue=new Vector();
		for(int i=0;i<ref.size();i++)
	    {
			residue=ref.getResidue(i,1).getAllAtoms();
			for(int j=0;j<residue.size();j++)
			{
				((jet.data.datatype.Atom)residue.get(j)).initAccess(true);
			}
	    }
	}		
	
    public static Vector[] resSurfNaccess(File pdbFile, double probeRadius, boolean hetatom, String naccessCommand) throws jet.exception.NaccessException
    {
	
    	String dir=pdbFile.getParentFile().getAbsolutePath();
	
	// run Naccess
    	if (hetatom) new jet.external.Naccess(naccessCommand+" -h",pdbFile.getAbsolutePath(), dir);
    	else new jet.external.Naccess(naccessCommand,pdbFile.getAbsolutePath(), dir);
    	
    	String filename=pdbFile.getPath();
    	if (filename.lastIndexOf(".")!=-1) filename=filename.substring(0,filename.lastIndexOf("."));
    	
    	Vector bufferResAxs=jet.io.file.FileIO.readFile(filename+".rsa");
    	Vector bufferAtomAxs=jet.io.file.FileIO.readFile(filename+".asa");
    	
    	if ((bufferResAxs.size()==0)||(bufferAtomAxs.size()==0))
	    {
    		throw new jet.exception.NaccessException();
	    }
    	
	// read Naccess output files
    	File naccessFile=new File(filename+".rsa");
	naccessFile.delete();
    	naccessFile=new File(filename+".asa");
    	naccessFile.delete();
    	naccessFile=new File(filename+".log");
    	naccessFile.delete();
    	
    	String line="";
	
    	for (int j=0;j<bufferResAxs.size();j++)
	    {
		line=(String)bufferResAxs.get(j);
		if (!line.substring(0,3).equals("RES"))
		    {
			bufferResAxs.remove(j);
			j--;
		    }
		/*	else if (line.substring(13,14).trim().length()!=0)
			{
			bufferResAxs.remove(j);
			j--;
			}*/
	    }
    	
    	line="";
    	for (int j=0;j<bufferAtomAxs.size();j++)
	    {
		line=(String)bufferAtomAxs.get(j);
		if (!line.substring(0,4).equals("ATOM"))
		    {
			bufferAtomAxs.remove(j);
			j--;
		    }
		//	else if ((line.substring(16,17).trim().length()!=0)||(line.substring(26,27).trim().length()!=0))
		else if (line.substring(16,17).trim().length()!=0)
		    {
			bufferAtomAxs.remove(j);
			j--;
		    }
	    }
    	
    	bufferResAxs=jet.Result.splitColData(bufferResAxs,9);
    	
    	Vector[] result=new Vector[2];
    	Vector resultResAxs=new Vector(5);
    	
    	resultResAxs.add(jet.Result.findColData(bufferResAxs, 1));//res name
    	resultResAxs.add(jet.Result.findColData(bufferResAxs, 2));//chain name
    	resultResAxs.add(jet.Result.findColData(bufferResAxs, 3));//res position
    	resultResAxs.add(jet.Result.parseColData(bufferResAxs, 4));//surface axs res
    	resultResAxs.add(jet.Result.parseColData(bufferResAxs, 5));//percent surface axs res
    	
    	result[0]=resultResAxs;
    	
    	bufferAtomAxs=jet.Result.splitColData(bufferAtomAxs,22);
    	
    	Vector resultAtomAxs=new Vector(6);
    	
    	resultAtomAxs.add(jet.Result.findColData(bufferAtomAxs, 3));//res name
    	resultAtomAxs.add(jet.Result.findColData(bufferAtomAxs, 4));//chain name
    	resultAtomAxs.add(jet.Result.findColData(bufferAtomAxs, 5));//res pos
    	resultAtomAxs.add(jet.Result.findColData(bufferAtomAxs, 2));//atom name
    	resultAtomAxs.add(jet.Result.findColData(bufferAtomAxs, 1));//atom pos
    	resultAtomAxs.add(jet.Result.parseColData(bufferAtomAxs, 9));//surface axs atom
    	result[1]=resultAtomAxs;
    	
    	return result;
    	
    }

    // This is the one !
    public static Vector mapToDouble(Vector percentAxs,double cutoff)
    {
	Vector axsDouble=new Vector(percentAxs.size());
	double percent=0.0;
	for (int i=0; i<percentAxs.size();i++) 
	    {
		percent=(Double)percentAxs.get(i);
		// changed for strictly above threshold
		if (percent>(cutoff*100)) axsDouble.add(1.0);
		else  axsDouble.add(0.0);
	    }
	
	return axsDouble;
    }

    public static Vector mapToDouble(Vector axs, Vector percentAxs,double cutoff)
    {
	Vector axsDouble=new Vector(percentAxs.size());
	double percent=0.0;
	double axsVal=0.0;
	for (int i=0; i<percentAxs.size();i++) 
	    {
		percent=(Double)percentAxs.get(i);
		axsVal=(Double)axs.get(i);
		// changed for strictly above threshold
		if (percent>(cutoff*100)) axsDouble.add(1.0);
		else {
		    if (axsVal>0.0) axsDouble.add(0.3);
		    else axsDouble.add(0.0);
		}
	    }
	
	return axsDouble;
    }
    
    public static Vector mapToBoolean(Vector percentAxs,double cutoff)
    {
	//Vector percentAxs=(Vector)jprotein.tools.MapAccess.resSurfNaccess(pdbFile,probeRadius).get(4);
		Vector axs=new Vector(percentAxs.size());
		double percent=0.0;
		for (int i=0; i<percentAxs.size();i++) 
		{
			percent=Double.parseDouble((String)percentAxs.get(i));
			if (percent>=cutoff) axs.add(true);
			else axs.add(false);
		}
		
		return axs;
		}

 
}

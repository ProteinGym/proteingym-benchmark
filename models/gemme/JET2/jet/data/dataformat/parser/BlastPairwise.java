package jet.data.dataformat.parser;

import java.util.*;

import jet.data.dataformat.info.PairwiseSequenceInfo;

/** Classe pour parser les resultats des requetes blast 
 * et calculer les caracteristiques des alignements obtenus par blast */

public class BlastPairwise extends Blast
{
    
	/** Parsing ligne à ligne ecupere les sequences 
	 * homologues (ainsi que des informations sur l'alignement avec la 
	 * sequence de reference) sous forme d'objets BlastPairwiseInfo à 
	 * partir de la reponse du serveur blast */
	
    public static Vector getPairwiseInfo(Vector blastData)
    {
	jet.data.datatype.Sequence ref=null;
	PairwiseSequenceInfo seq=null;

	/* Lancement de la requete et recuperation des donnees de la requete */
	Vector blastList=new Vector(50,50);
	
	int i,pos,len,posVerif;
	String str="",line="",residueSequence="",refResidueSequence="";
	String[] subLine;
	boolean done=false;

	if (blastData==null) blastData=new Vector(1);
	
	/* Boucle sur les resultats du serveur blast */
	for(i=0;i<blastData.size();i++)
	    {
		/* Une ligne des resultats */
		line=((String)blastData.get(i)).trim();
		if(line.length()>0)
		    {
			if(line.indexOf(">")==0)
			    {
				/* Ligne concernant le nom de la sequence */
				if((seq!=null)&&(residueSequence.length()>0)&&(seq.overlapEndChecked())) 
				    {
					/* Le nom et la sequence sont connus et on observe
					 *  une ligne avec un nouveau nom de sequence ==> 
					 *  On doit stocker les infos de l'ancienne sequence 
					 *  et creer une nouvelle sequence */
					
					/* Partie de la sequence de reference recouvrée*/
					ref.setSequence(refResidueSequence);
					/* Parametre de la sequence recupérée */
					seq.setSequence(residueSequence);
					len=seq.getEndOverlap()-seq.getStartOverlap(); 
					seq.setOverlapLength(len);
					seq.setRefSequence(ref);
					/* Ajout de la sequence à la liste finale 
					 * de sequences recupérees sur le serveur blast */
					addSequence(blastList,seq);
				    }
				/* Changement de sequence de reference et recuperee */
				done=false;
				residueSequence=""; refResidueSequence="";
				seq=new jet.data.dataformat.info.PairwiseSequenceInfo();
				
				ref=new jet.data.datatype.Sequence();
				/* Recuperation du nom */
				seq.setSequenceName((line.split("\\s+")[0]).substring(1));
			    }
			else
			    {
				/* Ligne concernant autre chose que le nom de la sequence */
				if(seq!=null)
					/* Une blastsequence est en cours de recuperation */
				    {
					if(!seq.expectChecked())
						/* Evalue non traitee ==> on la recupere */
					    {
						if(((pos=line.toLowerCase().indexOf("expect"))!=-1))
						    {
							posVerif=line.indexOf("=",pos);
								{
								str=line.substring(posVerif+1).trim();
								if((pos=str.indexOf(","))!=-1) str=str.substring(0,pos);
								try
								{					
								seq.setEValue(Double.valueOf(str).doubleValue());	
								}catch(NumberFormatException e)
									{
									if (str.charAt(0)=='e')
										{
										str="1"+str;
										seq.setEValue(Double.valueOf(str).doubleValue());	
										}
									}
								}
						    }
					    }
					
					else if((!seq.identityChecked())&&(!seq.gapChecked()))
					/* Pourcentage d'identite non traite ==> on le recupere */
					{
						if(line.toLowerCase().indexOf("identities")!=-1)
						{
							subLine=line.split("\\s+");
							if((posVerif=subLine[3].indexOf("%"))!=-1)
							{
								str=subLine[3].substring(1,posVerif);
								try
								{
									seq.setIdentity(Integer.valueOf(str).intValue());
								}catch(NumberFormatException e)
								{
									System.out.println("Error in percent identity parsing:"+str);
								}
							}
							if(line.toLowerCase().indexOf("gaps =")!=-1)
							{
								if((posVerif=subLine[11].indexOf("%"))!=-1)
								{
									str=subLine[11].substring(1,posVerif);
									try
									{
										seq.setGap(Integer.valueOf(str).intValue());
									}catch(NumberFormatException e)
									{
										System.out.println("Error in percent gap parsing:"+str);
									}
								}
							}		
							else seq.setGap(0);
						}
						
					}
					else if(!seq.overlapStartChecked())
						/* Debut de l'alignement dans la sequence de reference 
						 * non traite ==> on le recupere ainsi que la premiere 
						 * partie de la sequence de reference */
					{
						if(line.indexOf("Query")==0)
						    /* Ligne concernant la premiere partie de la sequence de reference */
						{
							subLine=line.split("\\s+");
							str=subLine[1];
							try
							{
								seq.setStartOverlap(Integer.valueOf(str).intValue()-1);
							}catch(NumberFormatException e){}
							refResidueSequence+=subLine[2];
							try
							{
								seq.setEndOverlap(Integer.valueOf(subLine[3]).intValue()); 
							}catch(NumberFormatException e){}
						}
					}
					else if(seq.allPreliminaryChecked())
						/* Tous les preliminaires ont ete fait on a recupere la 
						 * premiere partie de la sequence de reference ==> on doit 
						 * recuperer les deux sequences (reference et blast) ainsi 
						 * que la position de fin de l'alignement dans la sequence de reference */
					    {
						if(!done)
						    {
							if(line.indexOf("Sbjc")==0)
								/* Ligne concernant la sequence blast */
							    {

								residueSequence+=line.split("\\s+")[2];
							    }
							else if(line.indexOf("Query")==0)
								/* Ligne concernant la sequence de reference */
							    {
								seq.setEndOverlapCheck(false);
								subLine=line.split("\\s+");
								refResidueSequence+=subLine[2];
								try
								{
								seq.setEndOverlap(Integer.valueOf(subLine[3]).intValue()); 
								}catch(NumberFormatException e){}
							    }
							else if(line.indexOf("Score")!=-1) 
									/* Il peu y avoir plusieurs alignements locaux pour une 
									 * meme sequence. On ne recupere que le premier (evalue 
									 * la plus faible) */
									{ done=true; }
						    }
					    }
				    }
				
			    }
		    }
	    }
	/* On est sorti de la boucle ==> il faut recuperer la derniere sequence blast */
	if((seq!=null)&&(residueSequence.length()>0)) 
	    {
		/* Le nom et la sequence sont connus ==> On stocke les infos de cette sequence */
		/* Partie de la sequence de reference recouvrée*/
		ref.setSequence(refResidueSequence);
		/* Parametre de la sequence recupérée */
		seq.setSequence(residueSequence);
		seq.setRefSequence(ref);
		len=seq.getEndOverlap()-seq.getStartOverlap();		    
		seq.setOverlapLength(len);
		/* Ajout de la sequence à la liste finale 
		 * de sequences recupérees sur le serveur blast */
		addSequence(blastList,seq);
	    }
	
	/* Retour de la liste de sequences blast */
	return blastList;
    } 

    /** (à voir :boguée dans certaines executions variable shift deborde de alignComp) 
     * Retourne un vecteur de composition. 
     * Le resultat est sous forme de vecteur de la meme longueur que la sequence ref.
     * Chaque position du vecteur correspond à une position de la sequence ref et contient 
     * un tableau dont chaque case stocke le nombre de residu d'un type à cette position 
     * dans l'alignement. */
    
    public static Vector getAlignmentComposition(Vector pAli, jet.data.datatype.Sequence ref)
    {
	Vector alignComp=null;
	jet.data.datatype.Residue gap=new jet.data.datatype.Residue('-');
	if(pAli.size()>0)
	    {
		jet.data.datatype.Sequence refSeq;
		PairwiseSequenceInfo pa;
		int i,j,shift;
		int[] index;
		
		alignComp=new Vector(ref.size());
		/* Chaque position de alignCom contient un tableau contenant le 
		 * nombre de chacun des "24" aa à cette position de la sequence 
		 * de reference  */
		for(i=0;i<ref.size();i++)
		    { index=new int[24]; alignComp.add(index); Arrays.fill(index,0);}

		/* Boucle sur chaque sequence blast */
		for(i=0;i<pAli.size();i++)
		    {
			pa=(PairwiseSequenceInfo)pAli.get(i);
			refSeq=pa.getRefSequence();
			shift=pa.getStartOverlap();
			//System.out.println("sequence="+i);
			//System.out.println("start shift="+shift);
			/* Boucle sur chaque residu de la sequence de reference (refSeq) de la sequence blast j.
			 * Cette sequence est une sous sequence de la sequence de reference ref (elle 
			 * commence a la position "shift". */
			for(j=0;j<refSeq.size();j++)
			{
			    if(!gap.equals(refSeq.getResidue(j)))
			    	/* Les gap dans la sequence refSeq ne correspondent 
			    	 * pas à des position de la sequence de reference ref */
				{
			    	/* Le tableau de composition de la position "shift"  
			    	 * est augmente pour la case correspondant au residu observé */
			    	//System.out.print("."+shift);
				    ((int[])alignComp.get(shift++))[pa.getResidue(j).getResidueIndex()]++; 
				}
			}
			//System.out.println("");
		    }
	    }
	
	return alignComp;
    }
    
    /** Converti en frequence le vecteur de composition (methode getAlignmentComposition) 
     * contenant le nombre et le type de residus à chaque position. */
    
    public static Vector getCompositionFrequencies(Vector ac)
    {

	Vector acf=new Vector(ac.size());
	int i,j;
	double gapNoise=0.0;
	double total;
	int[] indexAc;
	double[] index;

	for(i=0;i<ac.size();i++)
	    {
		index=new double[24];
		indexAc=(int[])ac.get(i);
		
		total=0.0;
		
		for(j=0;j<20;j++) total+=(double)indexAc[j];
		total=1.0/total;
		for(j=0;j<20;j++) index[j]=((double)indexAc[j])*total;		
		acf.add(index);
	    }

	return acf;
    }

    /** Calcule "l'information content" pour chaque position d'un vecteur contenant les frequences 
     * des differents type de residu a chaque position (methode getCompositionFrequencies). 
     * Verifier la formule de l'ic. */
    
    public static Vector getInformationContent(Vector acf)
    {
	int i,j;
	double index[];
	double sMax=Math.log(20.0)/Math.log(10.0),s;
	Vector sic=new Vector(acf.size());

	for(i=0;i<acf.size();i++)
	    {
		s=sMax;
		index=(double[])acf.get(i);
		for(j=0;j<20;j++) 
		    {
			if(index[j]>0.00000001)
			    {
				if(index[j]>0.999) { index[j]=0.999; }
				s+=index[j]*(Math.log(index[j])/Math.log(10.0));
			    }
		    }
		sic.add(new Double(s));
	    }

	return sic;
    }

}

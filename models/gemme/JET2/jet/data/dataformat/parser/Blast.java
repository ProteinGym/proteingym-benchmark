package jet.data.dataformat.parser;

import java.util.*;

/** Classe pour traiter (filtrer) les sequences obtenues par blast  */

public class Blast
{
    
	/** Recupere a partir d'un vecteur de sequences blast (sequenceList) celles dont l'identité 
	 * par rapport a la sequence de reference est entre "low" et "high" */
	
    public static void filterSequenceIdentities(Vector sequenceList, int low, int high)
    {
	jet.data.dataformat.info.PairwiseSequenceInfo seq;
	int count=0;
	int identityMax=0;
	int identityMin=100;
	double identityAverage=0;
	
	for(int i=0;i<sequenceList.size();i++)
	{
		seq=(jet.data.dataformat.info.PairwiseSequenceInfo)sequenceList.get(i);
		if(seq.getIdentity()>high) sequenceList.remove(i--);
		else if(seq.getIdentity()<low) sequenceList.remove(i--);
		else
		{
			count++;
			if (seq.getIdentity()>identityMax) identityMax=seq.getIdentity();
			if (seq.getIdentity()<identityMin) identityMin=seq.getIdentity();
			identityAverage=identityAverage+(double)seq.getIdentity();
		}
	}
	identityAverage=identityAverage/(double)count;
	
	/*
	
	System.out.println("number of sequences in partition "+low+" "+high+":"+count);
	System.out.println("identityMax:"+identityMax);
	System.out.println("identityMin:"+identityMin);
	System.out.println("identityMoy:"+identityAverage);
    
    */
    
    }
    
    /** Recupere a partir d'un vecteur de sequences blast (sequenceList) celles dont le pourcentage  
	 * de gap est inferieur à maxPercentGap */
    
    public static void filterSequenceGap(Vector sequenceList,int maxGapPercent)
    {
    	jet.data.dataformat.info.PairwiseSequenceInfo seq;
    	//int count=sequenceList.size();
    	for(int i=0;i<sequenceList.size();i++)
    	    {
    		seq=(jet.data.dataformat.info.PairwiseSequenceInfo)sequenceList.get(i);
    		if(seq.getGap()>maxGapPercent) sequenceList.remove(i--);
    	    }
    	//count=count-sequenceList.size();
    	//System.err.println(count+" sequences were removed according Evalue");
    }
    
    /** Recupere a partir d'un vecteur de sequences blast (sequenceList) celles dont l'evalue 
	 * de l'alignement par rapport a la sequence de reference est inferieur à "max" */
    
    public static void filterSequenceEValues(Vector sequenceList, double max)
    {
	jet.data.dataformat.info.PairwiseSequenceInfo seq;
	//int count=sequenceList.size();
	for(int i=0;i<sequenceList.size();i++)
	    {
		seq=(jet.data.dataformat.info.PairwiseSequenceInfo)sequenceList.get(i);
		if(seq.getEValue()>max) sequenceList.remove(i--);
	    }
	//count=count-sequenceList.size();
	//System.err.println(count+" sequences were removed according Evalue");
    }

    /** Recupere a partir d'un vecteur de sequences blast (sequenceList) celles dont la longueur 
	 * de l'alignement est entre "low" et "high" */
    
    public static void filterSequenceLengths(Vector sequenceList, int low, int high)
    {
	jet.data.dataformat.info.PairwiseSequenceInfo seq;
	//int count=sequenceList.size();
	for(int i=0;i<sequenceList.size();i++)
	    {
		seq=(jet.data.dataformat.info.PairwiseSequenceInfo)sequenceList.get(i);
		if(seq.size()>high) sequenceList.remove(i--);
		else if(seq.size()<low) sequenceList.remove(i--);
	    }
	//count=count-sequenceList.size();
	//System.err.println(count+" sequences were removed according length");
    }
    
    /** Retire les sequences dont les residus sont identiques a partir d'un 
     * vecteur de sequences blast (sequenceList) */
    
    public static void removeRedundance(Vector sequenceList)
    {
	int i,j;
	//int count=0;
	jet.data.datatype.Sequence seqI,seqJ;

	for(i=0;i<sequenceList.size()-1;i++)
	    {
		seqI=(jet.data.datatype.Sequence)sequenceList.get(i);
		for(j=i+1;j<sequenceList.size();j++)
		    {
			seqJ=(jet.data.datatype.Sequence)sequenceList.get(j);
			if(seqI.isIdentical(seqJ)) 
			    {
				sequenceList.remove(j--);
				//count++;
			    }
		    }
	    }
	//System.err.println(count+" redundant sequences were removed");
    }
    
    /** Methode conservant la sequence de plus forte identité parmi les sequences ayant le meme nom 
     * (à utiliser apres removeRedundance). */
    
    public static void removeSameName(Vector sequenceList)
    {
    	int i,j;
    	jet.data.dataformat.info.PairwiseSequenceInfo seqI,seqJ;

    	for(i=0;i<sequenceList.size()-1;i++)
    	{
    		seqI=(jet.data.dataformat.info.PairwiseSequenceInfo)sequenceList.get(i);
    		for(j=i+1;j<sequenceList.size();j++)
    		{
    			seqJ=(jet.data.dataformat.info.PairwiseSequenceInfo)sequenceList.get(j);
    			if(seqI.equals(seqJ)) 
    			{
    				if (seqI.getIdentity()>seqJ.getIdentity())
    					sequenceList.remove(j--);
    				else
    				{
    					sequenceList.remove(i--);
    					break;
    				}
    			}
    		}
    	}
    }
    

    /** Methode identique a la methode "getPairwiseInfo" de la classe "BlastPairwise". 
     * Cette version n'est pas utilisée */
    
    public static Vector getSequenceInfo(jet.data.dataformat.Format bf)
    {
	jet.data.dataformat.info.PairwiseSequenceInfo seq=null;
	Vector blastData=bf.getData(), blastList=new Vector(50,50);
	
	int i,j,pos,len;
	String str="",line="",residueSequence="";
	String[] subLine;
	boolean done=false;

	for(i=0;i<blastData.size();i++)
	    {
		line=((String)blastData.get(i)).trim();
		if(line.length()>0)
		    {
			if(line.indexOf(">")==0)
			    {
				if((seq!=null)&&(residueSequence.length()>0)) 
				    {
					seq.setSequence(residueSequence);
					addSequence(blastList,seq);
				    }
				done=false;
				residueSequence="";
				seq=new jet.data.dataformat.info.PairwiseSequenceInfo();
				seq.setSequenceName((line.split("\\s+")[0]).substring(1));
			    }
			else
			    {
				if(seq!=null)
				    {
					if(!seq.expectChecked())
					    {
						if((pos=line.toLowerCase().indexOf("expect"))!=-1)
						    {
							str=line.substring(line.indexOf("=",pos)+1);
						
							if((pos=str.indexOf(","))!=-1) str=str.substring(0,pos);
							
							seq.setEValue(Double.valueOf(str.trim()).doubleValue());
							 
						    }
					    }
					
					else if(!seq.identityChecked())
					    {
						if(line.toLowerCase().indexOf("identities")!=-1)
						    {
							subLine=line.split("\\s+");
							
							str=subLine[3].substring(1,subLine[3].indexOf("%"));
							seq.setIdentity(Integer.valueOf(str).intValue());	
						    }		
					    }
					
					else if(!seq.overlapStartChecked())
					    {
						if(line.indexOf("Query")!=-1)
						    {
							str=line.split("\\s+")[1];
							seq.setStartOverlap(Integer.valueOf(str).intValue()-1);
						    }
					    }
					
					else if(seq.allPreliminaryChecked())
					    {
						if(!done)
						    {
							if(line.indexOf("Sbjc")==0)
							    {
								subLine=line.trim().split("\\s+");
								subLine=subLine[2].split("-+");
								for(j=0;j<subLine.length;j++) residueSequence+=subLine[j]; 
							    }
							else if(line.indexOf("Query")==0)
							    {
								str=line.split("\\s+")[3].trim();
								seq.setEndOverlap(Integer.valueOf(str).intValue());
		
								len=seq.getEndOverlap()-seq.getStartOverlap(); 
								seq.setOverlapLength(len);
							    }
							else if(line.indexOf("Score")!=-1) { done=true; }
						    }
					    }
				    }
				
			    }
		    }
	    }
	if((seq!=null)&&(residueSequence.length()>0)) 
	    {
		seq.setSequence(residueSequence);
		addSequence(blastList,seq);
	    }

	return blastList;
    } 

    /** Ajoute la sequence "seq" dans le vecteur "blastList" dans l'ordre 
     * croissant de l'overlap par rapport à la sequence de reference */
    
    static void addSequence(Vector blastList, jet.data.dataformat.info.PairwiseSequenceInfo seq)
    {
	jet.data.dataformat.info.PairwiseSequenceInfo seqI;
	
	for(int i=0;i<blastList.size();i++)
	    {
		seqI=(jet.data.dataformat.info.PairwiseSequenceInfo)blastList.get(i);
		if(seq.getOverlapLength()<seqI.getOverlapLength())
		    {
			blastList.add(i,seq);
			return;
		    }
	    }
	blastList.add(seq);
    }    

    
   
}

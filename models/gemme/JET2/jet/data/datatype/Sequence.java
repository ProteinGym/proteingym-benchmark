package jet.data.datatype;

import java.util.*;
import java.io.*;

/** Une séquence a un nom et est un vecteur de Résidus. */

public class Sequence extends Vector
{
    /***/
    /** VARIABLE STATIQUES */
    /***/
    
    static jet.data.datatype.Residue gap=new jet.data.datatype.Residue('-');
    static jet.data.datatype.Residue ext=new jet.data.datatype.Residue('.');
    
    /***/
    /** VARIABLE D'INSTANCES */
    /***/
    
    /** Nom d'une sequence */
    protected String sequenceName;
    protected String chainId="";
    
    /** CONSTRUCTEURS */
    /***/
    
    public Sequence()
    {
	super(10,10);
    }
    
    public Sequence(String sequenceName)
    {
	this();
	setSequenceName(sequenceName);
    }

    public Sequence(String sequenceName, String residueSequence)
    {
	this(sequenceName);
	setSequence(residueSequence);
    }
    
    /***/  
    /** ACCESSEURS */
    /***/
    
    public Vector getSequence() { return this; }   
    public String getSequenceName() { return sequenceName; }   
    public String getChainId(){ return chainId; }
    public int getSequenceLength() { return size();}   
    public jet.data.datatype.Residue getResidue(int pos) 
    { return (jet.data.datatype.Residue) get(pos); }

    /***/
    /** MODIFICATEURS */
    /***/
    
    /** Ajout de residus à partir d'une sequence au format String. */
    
    public void setSequence(String residueSequence) 
    {
	jet.data.datatype.Residue res=null;

	for(int i=0; i<residueSequence.length(); i++)
	  {
	      if(residueSequence.substring(i,i+1).compareTo("-")==0) 
		  {
		      res=new jet.data.datatype.Residue("gap");
		      res.setPosition(i+1);
		  }
	      else 
		  {
		      res=new jet.data.datatype.Residue(residueSequence.substring(i,i+1));
		      res.setPosition(i+1);
		  }
	       add(res);
	  }
    }
    public void setChainId(String chainId) { this.chainId=chainId;; }   
    public void setSequenceName(String sequenceName)
    { 
	this.sequenceName=sequenceName;
    }
    
    /***/
    /** METHODES */
    /***/
    
    /** Compte le nombre de residu non gap de la sequence. */
    
    public int getNonGappedSequenceLength()
    {
	int count=0;

	for(int i=0;i<size();i++)
	    {
		if((!gap.equals(getResidue(i)))&&(!ext.equals(getResidue(i)))) count++; 
	    }
	return count;
    }
    
    /** Compte le nombre de residus non gap jusqu'a la position index. */
    
    public int getNonGappedPosition(int index)
    {
	int countResidue=0;

	for(int i=0;i<getSequenceLength();i++)
	    {
		if((!getResidue(i).equals(gap))&&(!getResidue(i).equals(ext)))
		    {
			if(i==index) return countResidue;
			countResidue++;
		    }
		
	    }
	return -1;
    }
    
    /** Calcul le nombre de residus de la sequence courante present dans la sequence seq 
     * sur le nombre de residus de la sequence courante */
    
    public double getLocalIdentity(jet.data.datatype.Sequence seq)
    {
    	//System.out.println("calcul indentité:");
    	double percentIdentity=0.0;
    	int nbIdentique=0;
    	int j=0;
    	//System.out.println("nb res ref:"+this.size()+" nb res seq:"+seq.size());
    	for (int i=0;i<this.size();i++)
    	{
    		//System.out.print(" "+this.getResidue(i).getResidueCode()+this.getResidue(i).getPosition()+"...");
    		while ((j<seq.size())&&(seq.getResidue(j).getPosition()<this.getResidue(i).getPosition()))
    		{
    			//System.out.print(" "+seq.getResidue(j).getResidueCode()+seq.getResidue(j).getPosition());
    			j++;
    		}
    		
    		if (j<seq.size())
    		{
    			//System.out.print(" "+seq.getResidue(j).getResidueCode()+seq.getResidue(j).getPosition());
    			if (this.getResidue(i).getPosition()==seq.getResidue(j).getPosition())
    			{
    				
    				//System.out.print("... posok");
    				if (this.getResidue(i).getResidueIndex()==seq.getResidue(j).getResidueIndex())
    				{
    					nbIdentique++;
    					//System.out.print("... idok");
    				}
    				//else System.out.print("... idpok");
    			}
    			//else System.out.print("... pospok");
    		}
    		else break;
    		//System.out.println("");
    	}
    	
    	percentIdentity=(double)nbIdentique/(double)this.size();
    	//System.out.println("identité="+nbIdentique+"/"+this.size()+"="+percentIdentity);
    	return percentIdentity;
    }
    
    /** Calcul le nombre de residus de la sequence courante present dans la sequence seq 
     * sur le nombre de residus de la sequence la plus grande */
    
    public double getGlobalIdentity(jet.data.datatype.Sequence seq)
    {
    	//System.out.println("calcul indentité:");
    	double percentIdentity=0.0;
    	int nbIdentique=0;
    	int j=0;
    	//System.out.println("nb res ref:"+this.size()+" nb res seq:"+seq.size());
    	for (int i=0;i<this.size();i++)
    	{
    		//System.out.print(" "+this.getResidue(i).getResidueCode()+this.getResidue(i).getPosition()+"...");
    		while ((j<seq.size())&&(seq.getResidue(j).getPosition()<this.getResidue(i).getPosition()))
    		{
    			//System.out.print(" "+seq.getResidue(j).getResidueCode()+seq.getResidue(j).getPosition());
    			j++;
    		}
    		
    		if (j<seq.size())
    		{
    			//System.out.print(" "+seq.getResidue(j).getResidueCode()+seq.getResidue(j).getPosition());
    			if (this.getResidue(i).getPosition()==seq.getResidue(j).getPosition())
    			{
    				
    				//System.out.print("... posok");
    				if (this.getResidue(i).getResidueIndex()==seq.getResidue(j).getResidueIndex())
    				{
    					nbIdentique++;
    					//System.out.print("... idok");
    				}
    				//else System.out.print("... idpok");
    			}
    			//else System.out.print("... pospok");
    		}
    		else break;
    		//System.out.println("");
    	}
    	
    	percentIdentity=(double)nbIdentique/(this.size()>seq.size()?(double)this.size():(double)seq.size());
    	//System.out.println("identité="+nbIdentique+"/"+this.size()+"="+percentIdentity);
    	return percentIdentity;
    }
    
    
    /** Test permettant de savoir si la sequence correspond à une protéine */
    
    public boolean isProtein()
    {
    	boolean isProtein=false;
    	for (int i=0; i<this.size();i++)
    	{
    		isProtein=isProtein||((this.getResidue(i).getResidueIndex()>=0)&&(this.getResidue(i).getResidueIndex()<=21));
    	}
    	return isProtein;
    }

    /** Test d'egalite effectue par rapport aux residus composants les 2 sequences. */
    
    public boolean isIdentical(jet.data.datatype.Sequence seq)
    {
	int i;
	if(seq.size()!=this.size()) return false;

	for(i=0;i<seq.size();i++)
	    {
		if(!(seq.getResidue(i)).equals((Residue)this.getResidue(i))) return false;
	    }
	return true;

    }
    
    /** Test d'egalite effectue par rapport au nom des 2 sequences consideree. */
    
    public boolean equals(Object o)
    {
	if(o instanceof jet.data.datatype.Sequence)
	    {
		if(((jet.data.datatype.Sequence)o).getSequenceName().toLowerCase().compareTo(getSequenceName().toLowerCase())==0) return true;
		
	    }
	return false;
    }
    
    /** Converti la sequence en un objet String au format fasta. */
    
    public String toFasta()
    {
	int c=0;
	String fasta=">"+getSequenceName()+"\n\n";
	//String fasta=">sp;"+getSequenceName()+"\n\n";
	for(int i=0;i<size();i++)
	    {
		if(((Residue)get(i)).equals(gap)) fasta+="-";
		else fasta+=((Residue)get(i)).toString();
		
		if(!(++c<60)) { c=0; fasta+="\n";} 
	    }
	fasta+="*\n";//pourquoi "*" ?
	return fasta;
    }
    
    /** Converti la sequence en un objet String. */
    
    public String toString() 
    {
	Iterator seq=iterator();
	String str="";
	while(seq.hasNext()) 
	    {
		str+=((Residue)seq.next()).toString(); 
	    }
	return str;		
    }
}

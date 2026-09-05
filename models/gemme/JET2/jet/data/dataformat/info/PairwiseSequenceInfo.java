package jet.data.dataformat.info;

import java.util.*;

/** Classe qui etend la classe sequence. Elle concerne des sequences 
 * obtenues par Blast et ajoute des information sur l'evalue, le 
 * pourcentage d'identité et la region de recouvrement (overlap) de 
 * l'alignement avec la sequence de reference*/


public class PairwiseSequenceInfo extends jet.data.datatype.Sequence
{
	/** Debut, fin, longueur, pourcentage d'identité et pourcentage de gap de l'alignement 
	 * par rapport à la sequence de reference */
	
    private int start,end,length,identity,gap;
    
    /** Evalue  de l'alignement*/
    private double expect;
    
    /** Variables booleenne permettant de savoir si les 
     * variable debut, identité et evalue ont ete ajoutée */
    private boolean startCheck=false,endCheck=false, identityCheck=false, gapCheck=false,expectCheck=false;

	/** Partie recouvrée de la sequence de reference */
	jet.data.datatype.Sequence ref;

    /***/
    /** CONSTRUCTEURS */
    /***/
    
    public PairwiseSequenceInfo(){ super(); }
    
    /***/
    /** MODIFICATEURS */
    /***/
    
    public void setEndOverlap(int end) { this.end=end; endCheck=true;}
    public void setOverlapLength(int length) { this.length=length; }
    public void setStartOverlap(int start){ this.start=start; startCheck=true; }
    public void setIdentity(int identity) { this.identity=identity; identityCheck=true;}
    public void setEValue(double expect) {this.expect=expect; expectCheck=true;}
    public void setGap(int gap) {this.gap=gap; gapCheck=true;}
    
    public void setEndOverlapCheck(boolean endCheck) { this.endCheck=endCheck;}
    
    public void setRefSequence(jet.data.datatype.Sequence ref) {this.ref=ref;}
    
    /***/
    /** ACCESSEURS */
    /***/
    
    public int getEndOverlap(){ return this.end; }   
    public int getOverlapLength(){ return this.length; }   
    public int getStartOverlap(){ return this.start; }
    public boolean overlapStartChecked(){ return startCheck; }
    public boolean overlapEndChecked(){ return endCheck; }
    public int getIdentity() { return this.identity; }
    public boolean identityChecked() { return identityCheck; } 
    public int getGap() { return this.gap; }
    public boolean gapChecked() { return gapCheck; } 
    public double getEValue() { return this.expect; }
    public boolean expectChecked(){ return expectCheck; }
    
    public boolean allPreliminaryChecked() 
    { return ((expectChecked()&&identityChecked())&&overlapStartChecked()); }
   
    public jet.data.datatype.Sequence getRefSequence() { return ref; }
    
    /***/
    /** METHODES */
    /***/
    
    
    /** Non utilisée */
    
    public int overlaps(jet.data.dataformat.info.PairwiseSequenceInfo seq)
    {
	int len, len1=this.getOverlapLength(), len2=seq.getOverlapLength();
	int startDif=seq.getStartOverlap()-this.getStartOverlap();
	if(startDif<0) 
	    { 
		startDif*=-1;
		len1=seq.getOverlapLength(); len2=this.getOverlapLength(); 
	    }
	len=len1-startDif;
	return Math.min(len,len2);
    }
    
    public void computeIdentity()
    {
    	Iterator seq=iterator();
    	Iterator refIter=this.ref.iterator();
    	int identity=0;
    	while(seq.hasNext()&&refIter.hasNext()) 
    	    {
    		if (((jet.data.datatype.Residue)seq.next()).equals(((jet.data.datatype.Residue)refIter.next())))
    			identity++; 
    	    }
    	setIdentity((identity*100)/ref.size());	
    }
    
}

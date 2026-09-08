package jet.tree.data;

import java.util.*;
import java.lang.*;

/** Un objet leaf est un vecteur stockant temporairement pendant la construction
 *  de l'arbre phylogénétique les distances d'une sequence alignee "ali" avec les 
 *  autres sequences de l'alignement (et aussi à elle meme) qui n'ont pas encore 
 *  ete reliées phylogenetiquement. Ainsi lorsque l'arbre est 
 *  construit l'objet ne stocke plus de distance entre les sequences car ces 
 *  distances peuvent etre calculées à partir de l'arbre (distance entre les noeuds). 
 *  Elle stocke aussi le backtrace et le 
 * conservedIndex associé à la sequence "ali". Comme ceux ci sont ici egaux à la 
 * sequence moins les extensions, les objet leaf peuvent donc etre vus comme les 
 * feuilles des arbres des sequences consensus et backtraces. */

public class Leaf extends Vector
{
    static jet.data.datatype.Residue gap=new jet.data.datatype.Residue('-');
    static jet.data.datatype.Residue ext=new jet.data.datatype.Residue('.');
    private jet.data.datatype.Sequence ali=null;
    private Vector profile=null;
    private double sum=0.0;
    private boolean refreshSum=true;
    private Vector conservedIndex=null;
    private Vector backtrace=null;
    private Vector posSeqInMultiAli=new Vector();
    private double nbOfSeq=1.0;
    private java.math.BigInteger nbOfSeqBig=new java.math.BigInteger("1");
    private jet.tree.data.Leaf father=null;
    
    /***/
    /** CONSTRUCTEURS */
    /***/
    
    public Leaf(){ super(1,1);}
    
    public Leaf(jet.data.datatype.Sequence ali, Vector distances, int posInMultiAli) 
    {
    	super(distances); 
    	this.ali=ali;
    	this.posSeqInMultiAli.add(posInMultiAli);
    }

    /***/
    /** ACCESSEURS */
    /***/  
    public jet.tree.data.Leaf goFather(){ return this.father;}
    public jet.data.datatype.Sequence getSequence(){ return ali;}   
    public double getSum() { if(refreshSum) updateSum(); return sum; }
    public double getDistance(int index) { return ((Double)get(index)).doubleValue(); }   
    public Double getDoubleDistance(int index) { return (Double)get(index); }
    public Vector getBackTrace() { return backtrace; }
    public Vector getPosSeqInMultiAli() { return posSeqInMultiAli; }
    
    public Vector getProfile()  
    { if(profile==null) initProfile(); return profile; }
    public Vector getConservedResidue() 
    { if(conservedIndex==null) initConservedResidue(); return conservedIndex; }
    
    public double getNumberOfSequencesDouble(){return nbOfSeq;}
    public java.math.BigInteger getNumberOfSequencesBig(){return nbOfSeqBig;}
    
    /***/
    /** MODIFICATEURS */
    /***/ 
    public void setFather(Leaf father) {this.father=father;}   
    public void addDistance(double d) { refreshSum=true; add(new Double(d)); }
    public void removeDistance(int index) { refreshSum=true; remove(index); }   
    
    public void setConservedResidue(Vector conservedIndex)
    { this.conservedIndex=conservedIndex; setBackTrace(getConservedResidue());}
    
    public void setProfile(Vector profile)
    { this.profile=profile;}

    public void resetConservedResidue()
    { if(conservedIndex!=null){ conservedIndex.clear(); conservedIndex=null;} }

    public void setBackTrace(Vector backtrace){ this.backtrace=backtrace; }
    
    public void setNumberOfSequences(Vector nbOfSeqDoubleAndBig)
    {
    	nbOfSeq=((Double)nbOfSeqDoubleAndBig.get(0)).doubleValue();
    	nbOfSeqBig=((java.math.BigInteger)nbOfSeqDoubleAndBig.get(1));
    }
    
    /***/
    /** METHODES */
    /***/
    
    public Vector computeNumberOfSequences(boolean bool)
    { 	
    	Vector res=new Vector();
    	if (profile!=null)
    	{
    		double numberOfSequencesWithoutGap=1.0;
    		java.math.BigInteger numberOfSequencesWithoutGapBig=new java.math.BigInteger("1");
    		int size,nbGap=0;
    		if (bool) System.out.print(""+getProfile());
	    	if (bool) System.out.print("double"+numberOfSequencesWithoutGap+" big"+numberOfSequencesWithoutGapBig);
	    	for(int i=0;i<profile.size();i++)
	        {
	    		if (((Vector)profile.get(i)).size()>1)
	    		{
	    			size=0;
		    		for(int j=0;j<((Vector)profile.get(i)).size();j++)
			        {
		    			if(((Character)(((Vector)profile.get(i)).get(j))).charValue()!='-')
		    				size++;
		    			else 
		    				nbGap++;
			        }
		    		if (size==0) size=1;
		    		if (size==0) System.out.print("size equals to 0:"+((Vector)profile.get(i)).size()+" "+nbGap);
		    		
	    		}
	    		else size=1;
	    		
	    		if (bool) System.out.print("*"+size);
	    		numberOfSequencesWithoutGap=numberOfSequencesWithoutGap*(double)size;
	    		numberOfSequencesWithoutGapBig=numberOfSequencesWithoutGapBig.multiply(new java.math.BigInteger(""+(int)size));
	        }
	    	if (bool) System.out.print("="+numberOfSequencesWithoutGap+";"+numberOfSequencesWithoutGapBig);
	    	double numberOfSequences=numberOfSequencesWithoutGap;
	    	java.math.BigInteger numberOfSequencesBig=new java.math.BigInteger(""+numberOfSequencesWithoutGapBig);
	    
	    	for (int i=0;i<profile.size()/2;i++)
	    	{
	    		numberOfSequences=numberOfSequences+jet.tools.Statistic.nbCombinaison(nbGap,i)*numberOfSequencesWithoutGap;
	    		numberOfSequencesBig=numberOfSequencesBig.add(new java.math.BigInteger(""+(int)jet.tools.Statistic.nbCombinaison(nbGap,i)).multiply(numberOfSequencesWithoutGapBig));
	    	}
	    	
	    	if (bool) System.out.println(" nbGap:"+nbGap+"-->"+numberOfSequences+";"+numberOfSequencesBig);
	    	res.add(numberOfSequences);
	    	res.add(numberOfSequencesBig);
	    	return res; 
    	}
    	else
    	{
    		System.err.println("profile of node null ---> number of sequences not compute");
    		res.add(1.0);
    		res.add(new java.math.BigInteger("1"));
    		return res;
    	}
    }  
    
    
    /** Vide le vecteur des distances */
    
    public void unload(){ clear(); }
    
    /** Calcule la somme des distances */
    
    private void updateSum()
    {
	sum=0.0; refreshSum=false;
	for(int i=0; i<size(); i++) sum+=getDistance(i);
    }
     
    /** Initialise le vecteur des residus conservés sans les residus "ext" 
     * (pourquoi pas les "gap" aussi?). Les residus dans "conservedIndex" 
     * sont indexes par rapport à leur position dans la sequence alignée */
    
    public void initConservedResidue()
    {	
	conservedIndex=new Vector(1,1);
	
	for(int i=0;i<getSequence().getSequenceLength();i++)
	    {
		if((!getSequence().getResidue(i).equals(ext))&&(!getSequence().getResidue(i).equals(gap)))
		    conservedIndex.add(new jet.data.datatype.IndexedResidue(getSequence().getResidue(i),i));
	    }
	
    }
    
    public void initProfile()
    {	
	profile=new Vector(1,1);
	
	for(int i=0;i<getSequence().getSequenceLength();i++)
	    {
		profile.add(new Vector(22));
		if(getSequence().getResidue(i).equals(ext))
			((Vector)profile.lastElement()).add(gap.getResidueSymbol());
		else
			((Vector)profile.lastElement()).add(getSequence().getResidue(i).getResidueSymbol());
	    }
	
    }
    
    /** Le test d'egalité de deux objets leaf se fait 
     * par rapport aux sequences "ali" qui les composent */
    
    public boolean equals(Object o)
    {
	if(o instanceof jet.tree.data.Leaf) { return ((jet.tree.data.Leaf)o).getSequence().equals(getSequence()); }
	
	return false;
    }
    
    /** Retourne un String représentant l'objet leaf courant 
     * par le nom de la sequence "ali" */
    
    public String toString()
    { 
    	return getSequence().getSequenceName()+",\n numberOfSequences:"+getNumberOfSequencesDouble()+",\n profileSeq:"+getProfile()+"\n,"+getSequence()+"\n"; 
    }
   
}

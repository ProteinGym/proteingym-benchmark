package jet.tree.data;

import java.util.*;
import java.io.*;

/** La classe SimpleNode herite des caracteristiques de la classe Leaf. 
 * Elle est utilisée pour modéliser des noeuds internes d'un arbre 
 * phylogenetique. Cette classe reecrit les methodes initConservedResidue() 
 * et resetConservedResidue() de la classe Leaf. */

public class SimpleNode extends jet.tree.data.Leaf implements Serializable
{
    /** Fils gauche et droit */
    private jet.tree.data.Leaf left, right;
    /** Distance entre le noeud courant et chacun des deux noeuds fils. */
    private double vLeft=0.0,vRight=0.0;

    /***/
    /** CONSTRUCTEURS */
    /***/
    
    public SimpleNode(jet.tree.data.Leaf left, jet.tree.data.Leaf right)
    {
	setLeft(left); setRight(right);right.setFather(this);left.setFather(this);
	setPosSeqInMultiAli();
    }
    
    /***/
    /** ACCESSEURS */
    /***/  
    
    public jet.tree.data.Leaf goLeft(){ return this.left;}
    public jet.tree.data.Leaf goRight(){ return this.right;}
    
    public double getLeftVertice() { return vLeft; }
    public double getRightVertice() { return vRight; }
    
    /***/
    /** MODIFICATEURS */
    /***/ 
    
    public void setLeft(Leaf left) {this.left=left;}
    public void setRight(Leaf right) {this.right=right;}    
    
    public void setLeftVertice(double distance){ this.vLeft=distance;}
    public void setRightVertice(double distance){ this.vRight=distance;}
    public void setPosSeqInMultiAli()
    {  
    	this.getPosSeqInMultiAli().addAll(left.getPosSeqInMultiAli());
    	this.getPosSeqInMultiAli().addAll(right.getPosSeqInMultiAli());
    }
    
    /***/
    /** METHODES */
    /***/
    
    /** Calcule les residus conservés du noeud courant ainsi que les residus 
     * conservés et backtrace de tous les noeuds fils car la methode est 
     * recursive sur chaque noeud de l'arbre via l'appel à la methode 
     * getConservedResidue(). */
    
    public void initConservedResidue()
    {
	Vector v;
	
	/* Pour les residus conservés, on retient les residus 
	 * conservés dans les deux fils */
	v=new Vector(goRight().getConservedResidue()); 
	v.retainAll(goLeft().getConservedResidue());
	setConservedResidue(v);
	
	/* Pour les residus backtrace, on retire des fils les 
	 * residus conservés pour le noeud courant */
	v=new Vector(goRight().getConservedResidue()); 
	v.removeAll(getConservedResidue());
	goRight().setBackTrace(v);

	v=new Vector(goLeft().getConservedResidue()); 
	v.removeAll(getConservedResidue());
	goLeft().setBackTrace(v);

    }
    
    public void initProfile()
    {
	Vector v;
	Vector v1;
	//v=new Vector(goLeft().getProfile()); 
	//v1=new Vector(goRight().getProfile());
	
	Vector profileLeft=goLeft().getProfile();
	v=new Vector(profileLeft.size());
	for(int i=0;i<profileLeft.size();i++)
    {
		v.add(new Vector(21));
		for(int j=0;j<((Vector)profileLeft.get(i)).size();j++)
	    {
			
			((Vector)v.lastElement()).add(((Vector)profileLeft.get(i)).get(j));
	    }
    }
	
	Vector profileRight=goRight().getProfile();
	v1=new Vector(profileRight.size());
	for(int i=0;i<profileRight.size();i++)
    {
		v1.add(new Vector(21));
		for(int j=0;j<((Vector)profileRight.get(i)).size();j++)
	    {
			
			((Vector)v1.lastElement()).add(((Vector)profileRight.get(i)).get(j));
	    }
    }
	 
	for(int i=0;i<v.size();i++)
    {
		((Vector)v1.get(i)).removeAll(((Vector)v.get(i)));
		((Vector)v.get(i)).addAll(((Vector)v1.get(i)));
    }
	setProfile(v);
	setNumberOfSequences(computeNumberOfSequences(false));
	
    }
    
    /** Supprime les infos concernant les residus concervés 
     * du noeud courant ainsi que de tous les noeuds fils. */
    
    public void resetConservedResidue()
    {
	goRight().resetConservedResidue(); 
	goLeft().resetConservedResidue();
	super.resetConservedResidue();
    }
    
    
    /** Deux noeuds sont egaux si leur methodes toString retourne le meme String. 
     * Autrement dit si leur sous arbres sont egaux. */
    
    
    public boolean equals(Object o)
    {
	if(o instanceof jet.tree.data.SimpleNode) 
	    {
		if(((jet.tree.data.SimpleNode)o).toString().compareTo(toString())==0) return true;
	    }
	return false;
    }
    
    
    /** Retourne au format String les caracteristiques du sous arbre du noeud courant 
     * (tous les descendants) ==> (,) quand les deux fils sont des noeuds, 
     * (nomseq1,nomseq2) quand ceux sont des feuilles */
    
    //public String toString() {return "( \n"+left.toString()+":"+getLeftVertice()+"\n,\n"+right.toString()+":"+getRightVertice()+",\n backtrace:"+getBackTrace()+",\n conserved:"+getConservedResidue()+",\n profile:"+getProfile()+"\n)"; }
    public String toString() {return "( \n"+left.toString()+":"+getLeftVertice()+"\n,\n"+right.toString()+":"+getRightVertice()+",\n numberOfSequences:"+getNumberOfSequencesDouble()+",\n profileNode:"+getProfile()+"\n)"; }
   
    public String toStringScore() {return "( \n"+left.toString()+":"+getLeftVertice()+"\n,\n"+right.toString()+":"+getRightVertice()+",\n numberOfSequences:"+getNumberOfSequencesDouble()+"\n)"; }
    
   // public String toStringNode() {return "( \n:"+getLeftVertice()+" , \n:"+getRightVertice()+",\n backtrace:"+getBackTrace()+",\n conserved:"+getConservedResidue()+",\n profile:"+getProfile()+"\n)\n"; }
    
    public String toStringNode() {return "( \n:"+getLeftVertice()+" , \n:"+getRightVertice()+",\n profile:"+getProfile()+"\n)\n"; }

}

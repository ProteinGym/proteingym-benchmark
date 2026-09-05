package jet.data.datatype;

import java.util.*;

/** Une sequence3D hérite d'une sequence. Elle peut etre indexeee et 
 * on peut lui ajouter des residu uns à uns. 
 * NB: Seul le constructeur par defaut, getSequenceLength() et getResidue sont utilisés 
 * dans l'execution de JET et PSIJET. Les autres constructeurs et fonctions sont utilisées
 * par PdbFileReader2 qui est buggée et non utilisée. */

public class Sequence3D extends jet.data.datatype.Sequence
{
    
    /** index du premier residu de la sequence. */
    private int sequenceOffset;
    /** Le nom d'une sequence est souvent de la forme header_chainId. */
    private String header="unknown";
    
    /** Variable statique indiquant la methode de lecture de la sequence:
     * INDEXED si le premier residu à un index egal à "sequenceOffset".
     * DIRECT si le premier residu à un index egal à 0. */
    public static final int DIRECT=1, INDEXED=0;
    
    /***/
    /** CONSTRUCTEURS */
    /***/
    
    public Sequence3D(){ super();}
    
    public Sequence3D(String sequenceName, jet.data.datatype.Sequence residueSequence)
    { 
    	super(sequenceName,residueSequence.toString());   	
    }

    public Sequence3D(String sequenceHeader, String chainId)
    {
	super();
	setChainId(chainId);
	setSequenceName(sequenceHeader+"_"+getChainId().toLowerCase());
	setSequenceBegin(1);
    }
    
    /***/  
    /** ACCESSEURS */
    /***/   

    public int getSequenceBegin() { return sequenceOffset; }
    public int getSequenceLength() { return size();}//inutile car deja dans la classe mère
    public String getHeader(){ return header;}
    public jet.data.datatype.Residue3D getResidue(int index, int mode)
    {
	if(mode==DIRECT) return (jet.data.datatype.Residue3D)get(index);
	else return (jet.data.datatype.Residue3D) get(index-sequenceOffset);
    }
    
    /***/  
    /** MODIFICATEURS */
    /***/  
  
    public void setSequenceBegin(int sequenceOffset) { this.sequenceOffset=sequenceOffset; }   
    public void setHeader(String header){ this.header=header;}
    
    /** Ajout d'un residu  à la sequence par le biais du code du residu. */
    
    public void addResidue(String residueCode) { add(new jet.data.datatype.Residue3D(residueCode));}
    
    /** Ajout d'un residu à la sequence. */
    
    public void addResidue(jet.data.datatype.Residue3D residue) { add(residue); }
    
    /** Test d'egalite effectue par rapport aux residus composants les 2 sequences3D. */
    
    public boolean isIdenticalSeq(jet.data.datatype.Sequence seq)
    {
	int i;
	if(seq.size()!=this.size()) return false;

	for(i=0;i<seq.size();i++)
	    {
		if(this.getResidue(i).getResidueIndex()!=seq.getResidue(i).getResidueIndex()) return false;
	    }
	return true;

    }
    
    public int numResidue()
    {
    	return size();
    }
}

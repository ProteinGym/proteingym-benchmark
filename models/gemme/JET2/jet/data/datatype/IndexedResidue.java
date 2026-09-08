package jet.data.datatype;

/** Objet Residue ayant un "index" stockant la position 
 * du residu dans la sequence d'origine */

public class IndexedResidue extends jet.data.datatype.Residue
{
    private int index;
    
    public IndexedResidue(jet.data.datatype.Residue residue, int index)
    {
	super(residue.getResidueSymbol());
	this.index=index;
    }
    
    public int getIndex(){ return index;}
    
    public String toString()
    {
	return "( "+super.toString()+" , "+getIndex()+" )";
    }
    
    /** Test d'egalité sur le type du residu et sa position dans la sequence */
    
    public boolean equals(Object o)
    {
	jet.data.datatype.IndexedResidue ir;
    
	if(o instanceof jet.data.datatype.IndexedResidue)
	    {
		ir=(jet.data.datatype.IndexedResidue)o;
		if((ir.getIndex()==getIndex())&&(ir.getResidueIndex()==getResidueIndex())) return true;
	    }
	return false;
	
    }
}

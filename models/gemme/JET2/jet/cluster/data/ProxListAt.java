package jet.cluster.data;

import java.util.*;

/** Classe associant à un résidu toutes ses distances aux dix résidus les plus proches (plist) */

public class ProxListAt extends ProxList
{
    private jet.data.datatype.Atom atom;

    public ProxListAt(jet.data.datatype.Residue3D residue, int id)
    {
	super(residue,id);
    }
	
    public ProxListAt(jet.data.datatype.Atom atom, int id)
    {
	super(id);
	setAtom(atom);
	
    }
    
    public jet.data.datatype.Atom getAtom(){ return atom;}

    public void setAtom(jet.data.datatype.Atom atom){this.atom=atom;}

}

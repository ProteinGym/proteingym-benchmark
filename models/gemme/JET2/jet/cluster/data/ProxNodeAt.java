package jet.cluster.data;

import java.util.*;

public class ProxNodeAt extends ProxNode
{
    jet.cluster.data.ProxListAt ref;

    public ProxNodeAt(jet.cluster.data.ProxListAt ref, float distance)
    {
	super((jet.cluster.data.ProxList) ref,distance);
    }

    public jet.data.datatype.Atom getAtom() { return ref.getAtom(); }
}

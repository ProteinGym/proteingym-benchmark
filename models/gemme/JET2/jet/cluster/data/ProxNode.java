package jet.cluster.data;

import java.util.*;

public class ProxNode
{
    float distance;
    jet.cluster.data.ProxList ref;

    public ProxNode(jet.cluster.data.ProxList ref, float distance)
    {
	setRef(ref);
	setDistance(distance);
    }

    public void setRef(jet.cluster.data.ProxList ref){ this.ref=ref;}

    public jet.cluster.data.ProxList getRef(){ return ref;}
   
    public void setDistance(float distance){ this.distance=distance;}

    public float getDistance(){return distance;}

    public jet.data.datatype.Residue3D getResidue() { return ref.getResidue(); }
}

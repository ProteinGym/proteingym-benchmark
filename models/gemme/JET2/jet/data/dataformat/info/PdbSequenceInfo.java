package jet.data.dataformat.info;

import java.util.*;

/** Classe pour stocker les residus3D d'une sequence3D ainsi que leurs proprietes (temperature) **/

public class PdbSequenceInfo
{
	/** Proprietes des residus */
    private Vector propertyData;
    /** Sequence de residus 3D */
    private jet.data.datatype.Sequence3D seq;

    public PdbSequenceInfo(jet.data.datatype.Sequence3D seq, Vector propertyData)
    {
	this.seq=seq;
	this.propertyData=propertyData;
    }
    
    public void setPropertyData(Vector propertyData)
    { this.propertyData=propertyData;}
    
    public Vector getPropertyData(){ return propertyData; }
    public jet.data.datatype.Sequence3D getSequence(){ return seq; }
}

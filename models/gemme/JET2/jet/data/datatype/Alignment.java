package jet.data.datatype;

import java.util.*;

public class Alignment extends jet.data.datatype.Sequence
{
    public Alignment()
    {
	super();
    }
    public Alignment(String sequenceName, String residueSequence)
    {
	super(sequenceName,residueSequence);
    }
    
    public int getSequencePosition(int index)
    {
	jet.data.datatype.Residue gap= new jet.data.datatype.Residue("-");
	int countResidue=0;

	for(int i=0;i<getSequenceLength();i++)
	    {
		if(!getResidue(i).equals(gap))
		    {
			if(i==index) return countResidue;
			countResidue++;
		    }
		
	    }
	return -1;
    }
    
    public int nonGappedSequenceLength()
    { 
	Iterator iter=this.iterator();
	jet.data.datatype.Residue gap=new jet.data.datatype.Residue("-");
	int i=0;

	while(iter.hasNext()) 
	    {
		if(((jet.data.datatype.Residue)iter.next()).equals(gap)) i++;
	    }
	return size()-i;
    }
}

package jet.tools;
import java.util.*;

/** classe non utilisée */

public class MapContact1 
{
   
    public static Vector map(jet.data.datatype.Sequence3D seq1, jet.data.datatype.Sequence3D seq2,float minCutoff)
    {
	Vector contact=new Vector(2);
	contact.add(new Vector(seq1.size()));
	contact.add(new Vector(seq2.size()));
		
	return contact;
    }

}

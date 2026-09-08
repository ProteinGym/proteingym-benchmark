package jet.io.file;

import java.util.*;


public class FastaFileWriter extends jet.io.file.FileIO
{

    
    public FastaFileWriter(String filename, Vector sequenceData)
    {
	Vector data=new Vector(50, 50);
	
	for(int i=0;i<sequenceData.size();i++)
	    data.add(((jet.data.datatype.Sequence)sequenceData.get(i)).toFasta());
	
	super.writeFile(filename, data,false);	
    }

}

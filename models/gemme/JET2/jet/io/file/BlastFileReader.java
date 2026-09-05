package jet.io.file;

import java.util.*;

public class BlastFileReader extends jet.io.file.FileIO implements jet.data.dataformat.Format
{
    
    Vector dataBuffer=null;
    
    public BlastFileReader(String fileName)
	{
	    dataBuffer=readFile(fileName);
	}
    
    public Vector getData()
	{
	    return dataBuffer;
	}

}

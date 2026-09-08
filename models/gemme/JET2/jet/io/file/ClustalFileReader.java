package jet.io.file;

import java.util.*;

public class ClustalFileReader extends jet.io.file.FileIO implements jet.data.dataformat.Format
{
    String filename;

    public ClustalFileReader(String filename)
    {
	setFileName(filename);
    }
    
    public void setFileName(String filename)
    {
	this.filename=filename;
    }
    
    public Vector getData()
    {
	return super.readFile(filename);
    }

}

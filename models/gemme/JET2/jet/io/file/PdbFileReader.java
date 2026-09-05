package jet.io.file;

import java.util.*;

/** Classe pour lire un fichier pdb en le stockant ligne par ligne dans un vecteur. */

public class PdbFileReader extends jet.io.file.FileIO implements jet.data.dataformat.Format
{
    private String path;
    
    public PdbFileReader(String path)
    {	
	
	setPath(path);
	/*
	  int pos;
	  if((pos=path.lastIndexOf('\\'))<0) { if((pos=path.lastIndexOf('/'))<0) pos=0; }
	  setHeader(path.substring(pos+1, path.indexOf(".",pos+1)));
	*/
    }
    public void setPath(String path){ this.path=path;}
    public String getPath(){ return path; }

    /** Methode de lecture ligne par ligne heritee de FileIO retournant un vecteur. */

    public Vector getData() { return readFile(getPath()); }
    
}

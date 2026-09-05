package jet.io.net;

import java.io.*;
import java.net.*;
import java.util.*;

/** Classe pour r�cup�rer une structure chez un client web via un code pdb **/ 

public class PdbCodeClient extends WebClient implements jet.data.dataformat.Format
{
    String pdbCode;
    
    /** Initialisation du client web **/
    
    public PdbCodeClient(String url)
    { 
	super(url);
    }
    
    public void setPDBCode(String pdbCode){ this.pdbCode=pdbCode; }
    
    public String getPDBCode() { return pdbCode; }

    public Vector sendCommand() 
    { 
	return sendCommand("fileFormat=pdb&compression=NO&structureId="+getPDBCode()); 
    }

    /** Envoi de la requète et récupération des infos **/
    
    public Vector getData(){ return sendCommand(); }

    
    
}


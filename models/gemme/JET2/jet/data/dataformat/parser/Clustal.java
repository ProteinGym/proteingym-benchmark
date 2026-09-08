package jet.data.dataformat.parser;

import java.util.*;

/** Classe pour parser les fichier clustalW et recuperer les sequences de l'alignment */

public class Clustal
{ 
	/** Retourne un vecteur de sequences à partir d'un fichier clustalW */
	
    public static Vector getSequenceData(jet.data.dataformat.Format cf)
    {	
	Vector sequences=null;
	try 
	    {	
		sequences=new Vector(10,10);
		Vector residueSequence=new Vector(10,10);
	
		Vector data=cf.getData();
		int i=0,l;
		String line,resSeq;
		String [] subline;
		boolean go=false, addit=false;
		
		for(l=1;l<data.size();l++)
		    {
			line =(String)data.get(l);

			if((line.trim().length()>1)&&(line.substring(0,1).compareTo(" ")!=0))
			    {
				if(!go) go=true;
				 
				subline=line.split("\\s+");
				
				if(addit) 
				    {
					resSeq=(String)residueSequence.get(i)+subline[1];
					residueSequence.setElementAt(resSeq,i++);
				    }
				else 
				    {
					sequences.add(new jet.data.datatype.Sequence(subline[0]));
					residueSequence.add(subline[1]);
				    }
			    }
			else 
			    {
				if(go) addit=true;
				i=0;
			    }
		    }

		for(i=0;i<sequences.size();i++)
		    { 
			((jet.data.datatype.Sequence)sequences.get(i)).setSequence((String)residueSequence.get(i));
		    }

	
	    }
		
	catch(Exception e) { System.err.println("Error reported in clustal parser : "+e); }
	return sequences;
    }
}

package jet.io.net;

import java.util.*;

/** Classe permettant de lancer des requetes avec parametres 
 * sur le serveur blast puis de recuperer les resultats. */

public class BlastClient extends jet.io.net.WebClient implements jet.data.dataformat.Format
{

	/***/
    /** VARIABLE D'INSTANCES */
	/***/
	
	/** Parametre blast */	
    String database, matrix,gapCosts;  
    /** Parametre blast */	
    int maxResults;
    /** Identifiant permettant de recuperer les resultat de l'interogation sur le serveur */
    String RID;  
    /** Parametre blast */	
    double eValue;
    
    /** Sequence a envoyer sur le serveur blast dont on veut trouveer des homologues */
    jet.data.datatype.Sequence sequence=null;
    /** Indicateur de progression */
    jet.ProgressIndicator progress;
    
    /***/
    /** CONSTRUCTEURS */
	/***/
    
    public BlastClient(String url, String database, String matrix, int maxResults, double eValue,int gap_exist,int gap_ext)
    {
    	super(url);
    	setDatabase(database); setMatrix(matrix); 
    	setMaxResults(maxResults); setExpectedValue(eValue); 
    	setGapCosts(gap_exist,gap_ext);
    	progress=new jet.ProgressIndicator("Blast");
    }
    
    /***/
    /** MODIFICATEURS */
    /***/
    
    public void setDatabase(String database){this.database=database;}
    public void setExpectedValue(double eValue){this.eValue=eValue;}
    public void setGapCosts(int gap_exist,int gap_ext) { gapCosts=gap_exist+"%20"+gap_ext; }
    public void setMatrix(String matrix){this.matrix=matrix;}
    public void setMaxResults(int maxResults) { this.maxResults=maxResults; }
    public void setQuerySequence(jet.data.datatype.Sequence sequence) {this.sequence=sequence;}
    public void setQuerySequence(String sequence) {setQuerySequence(new jet.data.datatype.Sequence("ref",sequence));}
    private void setRID(String RID){ this.RID=RID; }

    /***/  
    /** ACCESSEURS */
    /***/
    
    public String getDatabase(){ return database;}
    public double getExpectedValue(){ return eValue; }
    public String getGapCosts(){ return gapCosts;}
    public String getMatrix(){ return matrix; } 
    public int getMaxResults(){ return maxResults; }
    private String getRID(){ return RID; }
    public jet.data.datatype.Sequence getQuerySequence(){return sequence;} 
       
    /***/  
    /** METHODES */
    /***/
    
    /** Lance la requete sur le serveur à trois reprises. */
    
    public Vector getData()
    {
	Vector dataBuffer=null;
	int retry=0;
	/* On effectue la connection à 3 reprise si rien n'est obtenu */
	while((dataBuffer==null)&&(retry++<3)) 
	    {
		dataBuffer=initData();
		if(dataBuffer==null) 
		    {
			System.err.println("Reconnecting in 10 seconds");
			try
			    { Thread.sleep(10000); } 
			catch(Exception e){}
		    }		
	    }
	return dataBuffer;
    }
    
    /** Lance la requete puis recupere les donnees sur le serveur. */
    
    public Vector initData()
    {
	Vector dataBuffer=null;	
	if(getQuerySequence()!=null)
	    {
		try
		    {
			progress.setStatus("Connecting to server");
			new Thread(progress).start();
		       	sendPutCommand();
			progress.setStatus("Waiting for server");
			dataBuffer=sendGetCommand();
			progress.stop();
		    }		
		catch(Exception e)
		    {
			System.err.println("Failed to obtain query result- > "+e);
		    }		
	    }	
	return dataBuffer;
    }
    
    /** Envoi de la requete et recuperation du RID (identifiant de resultat) 
     * permettant de recuperer les resultats sur le serveur. */
    
    public void sendPutCommand() throws Exception
    {
    /* Elaboration de la requete a lancer sur la base */
	String str,params="CMD=PUT&DATABASE="+getDatabase()+"&EXPECT="+getExpectedValue()+"&GAPCOSTS="+getGapCosts()+"&ALIGNMENTS="+getMaxResults()+"&MATRIX_NAME="+getMatrix()+"&QUERY="+getQuerySequence().toString();	
	/* Envoi de la requete au serveur et recuperation des donnees de reponse du serveur (ligne par ligne). */
	Iterator outputData=sendCommand(params).iterator();
	
	try
	    {	
		setRID("");
		
		/* Recuperation du "RID" */
		while(outputData.hasNext())
		    {
			str=(String)outputData.next();
			//System.out.println(""+str);
			
			if(str.indexOf("RID")!=-1) 
			    {
				if(str.trim().indexOf("RID")==0) 
				    {
					setRID(str.substring(str.indexOf("=")+1).trim());
					break;
				    }
			    }
		    }			
		if(getRID().length()==0) throw new Exception();	
	    }
	catch(Exception e) 
	    {	
		System.err.println("Failed to obtain RID -> "+e); throw new Exception(); 
	    }
        
    }

    /** Envoi une requete au serveur pour recuperer les resultats de 
     * l'interogation de la base sous forme d'un vecteur de lignes */

    public Vector sendGetCommand() throws Exception
    {
    /* Elaboration de la requete pour recuperer les resultats */
	String str,params="CMD=GET&DESCRIPTIONS=0&FORMAT_TYPE=TEXT&ALIGNMENTS="+getMaxResults()+"&EXPECT="+getExpectedValue()+"&RID="+getRID();
	Vector buffer=null; 
	Iterator outputData;
	boolean resend=true;
	int numConnections=0, maxConnections=50;

	try
	    {
		/* La requete est relancée tant que les resultats ne sont pas disponibles */
		while(resend)
		    {
			resend=false;
			/* Envoi de la requete pour recuperer des resultats */
			buffer=sendCommand(params);
			outputData=buffer.iterator();
			/* Boucle sur chaque ligne des retour des serveur */
			while(outputData.hasNext()) 
			    {
				str=(String)outputData.next();
				
				if(str.indexOf("Status")!=-1) 
				    { 
					/* Si le "status" est "waiting" les resultats ne sont pas encore disponibles */
					if(str.indexOf("WAITING")!=-1) resend=true;
					break;
				    }
			    }			
			if(resend) 
			    {
				/* Resultats non disponibles on relance la requete 
				 * si on n'a pas atteint le nb max de connexions */
				if(!(++numConnections < maxConnections)) throw new Exception(); 
				/* On attend 10 secondes */
				Thread.sleep(10000);
			    }
		    }		 
	    }
	catch(Exception e) {buffer=null; System.err.println("Failed to fetch result -> "); throw new Exception(); }
	
	return buffer;
    }

   
    
}



package jet.io.net;

import java.util.*;

public class PsiBlastClient extends jet.io.net.WebClient implements jet.data.dataformat.Format
{

    private String database, matrix, RID,gapCosts,RTOE,prevRID,PGR,format;
    private double eValue;
    private boolean retrieveMode=false;
    private Vector goodGI;
    private int iteration=1,maxIteration,maxResults;
    private jet.data.datatype.Sequence sequence=null;
    private jet.ProgressIndicator progress;
    
    public PsiBlastClient(String url, String database, String matrix, int maxResults, double eValue,int gap_exist,int gap_ext,int maxIteration)
    {
	super(url);
	setDatabase(database); setMatrix(matrix); 
	setMaxResults(maxResults); setExpectedValue(eValue); 
	setGapCosts(gap_exist,gap_ext);
	setMaxIteration(maxIteration);
	goodGI=new Vector(1,1);
	progress=new jet.ProgressIndicator("Psi-Blast");

    }
    
    public void setDatabase(String database){this.database=database;}
    public void setExpectedValue(double eValue){this.eValue=eValue;}
    public void setMaxIteration(int maxIteration){this.maxIteration=maxIteration;}
    public int getMaxIteration(){ return maxIteration;}
    public void setGapCosts(int gap_exist,int gap_ext){gapCosts=gap_exist+"%20"+gap_ext;}
    public void setMatrix(String matrix){this.matrix=matrix;}
    public void setMaxResults(int maxResults) { this.maxResults=maxResults; }
    public void setQuerySequence(jet.data.datatype.Sequence sequence) {this.sequence=sequence;}

    public void setQuerySequence(String sequence) 
    { 
	setQuerySequence(new jet.data.datatype.Sequence("ref",sequence));
    }
 
    private void setIteration(int it){ iteration=it; }
    private void iterate(){ iteration++; }
    private int getCurrentIteration(){ return iteration;}
    private void setRID(String RID){ this.RID=RID; }
    private void setPGR(String PGR){ this.PGR=PGR; }
    private String getPGR(){ return PGR;}
    public String getDatabase(){ return database;}
    public double getExpectedValue(){ return eValue; }
    public String getGapCosts(){ return gapCosts;}
    public String getMatrix(){ return matrix; } 
    public int getMaxResults(){ return maxResults; }
    private String getRID(){ return RID; }
    public void setPrevRID(String prevRID){ this.prevRID=prevRID;}
    public String getPrevRID(){ return prevRID; }
    public String getPsiThreshhold(){ return "0.005"; }
    public String getRTOE() { return RTOE;}
    public void setRTOE(String RTOE) { this.RTOE=RTOE;}
    public void setRetrieveMode(boolean retrieveMode)
    { this.retrieveMode=retrieveMode; }
    public String getFormat()
    { if(retrieveMode) return "text"; else return "HTML"; }
    public int getNumDescriptions() 
    { //if(retrieveMode) return 0; else 
	return getMaxResults(); 
    }
    public int getNumAlignments() 
    {if(retrieveMode) return getMaxResults(); else return 0; }
    public jet.data.datatype.Sequence getQuerySequence()
    { 
	return sequence; 
    } 
    
    public Vector getData()
    {
	Vector v,dataBuffer=null;
	int i,retry=0;
	while((dataBuffer==null)&&(retry++<10)) 
	    {
		
		try { dataBuffer=initData();} 
		catch(Exception e1)
		{
			System.err.println("Reconnecting in 10 seconds");
			try { Thread.sleep(10000); } 
			catch(Exception e2){}
		}
		if(dataBuffer==null) 
		    {
			System.err.println("Reconnecting in 10 seconds");
			try { Thread.sleep(10000); } 
			catch(Exception e){}
		    }
	    }
	
	return dataBuffer;
    }
   
    public Vector initData() throws Exception
    {
	Vector dataBuffer=null;
	int i;
	//System.out.println("sequence:\n"+getQuerySequence());
	//boolean resend = true;
	//int nbResend=0;
	//while (resend)
	//{
		//resend=false;
		if(getQuerySequence()!=null)
		{
		    try
		    {
			progress.setStatus("Connecting to server");
			new Thread(progress).start();
			sendInitialPsiPutCommand();
	
			while(getCurrentIteration()<getMaxIteration())
			{
			    
			    progress.setStatus("Processing iteration "+getCurrentIteration());
			    sendPsiGetCommand();
			   
			    iteratePsiPutCommand();
			 
			    iterate();
			}
			setRetrieveMode(true);
			
			progress.setStatus("Processing iteration "+getCurrentIteration());
			dataBuffer=sendPsiGetCommand();
			
			progress.stop();
		    }
		    
		    catch(Exception e)
		    {
			System.err.println("Failed to obtain query result \n\n"+e);
			throw new Exception();
			//nbResend++;
			//setIteration(1);
			//if (nbResend<1) resend=true;
		    }
		}
	    
	//}
	//if (dataBuffer==null) System.out.println("null");
	//System.out.println("taille data: "+dataBuffer.size());
	//jprotein.io.file.FileIO.writeFile("Blast.dat",dataBuffer);
	return dataBuffer;
    }
    
    
    public void parseGI(Vector buffer)
    {
	Iterator iter=buffer.iterator();
	String line,pGI;
	int pos,pos1,pos2;
	
	
	goodGI.clear();
	
	while(iter.hasNext())
	    {
		line=(String)iter.next();
		if((pos1=line.indexOf("good_GI"))!=-1)
		{
		    pos1=line.indexOf("VALUE",pos1);  
		    pos1=line.indexOf("=",pos1); 
		    pos2=line.indexOf("\"",pos1+3);
		    pGI=new String(line.substring(pos1+3,pos2));
		    goodGI.add(pGI);
		}
	    }


    }
    
    public void parsePGR(Vector buffer)
	{
	    Iterator iter=buffer.iterator();
	    String line;
	    int pos1,pos2;
	    while(iter.hasNext())
	    {
		line=(String)iter.next();
		if((pos1=line.indexOf("\"_PGR\""))!=-1)
		{
		    pos1=line.indexOf("value",pos1);
		    pos1=line.indexOf("=",pos1); 
		    pos2=line.indexOf("\"",pos1+2);
		    setPGR(new String(line.substring(pos1+2,pos2)));
		    break;
		}
	    }
	}

    public void sendInitialPsiPutCommand() throws Exception
	{
	    String str,params="CMD=PUT&NCBI_GI=on&DATABASE="+getDatabase()+"&EXPECT="+getExpectedValue()+"&GAPCOSTS="+getGapCosts()+"&HITLIST_SIZE="+getMaxResults()+"&MATRIX_NAME="+getMatrix()+"&QUERY="+getQuerySequence().toString()+"&RUN_PSIBLAST=ON&I_TRESH="+getPsiThreshhold();
	    //http://www.ncbi.nlm.nih.gov/blast/oldblast/Blast.cgi?CMD=Web&LAYOUT=TwoWindows&AUTO_FORMAT=Semiauto&ALIGNMENTS=250&ALIGNMENT_VIEW=Pairwise&CDD_SEARCH=on&CLIENT=web&COMPOSITION_BASED_STATISTICS=on&DATABASE=nr&DESCRIPTIONS=500&ENTREZ_QUERY=%28none%29&EXPECT=10&FORMAT_OBJECT=Alignment&FORMAT_TYPE=HTML&I_THRESH=0.005&MATRIX_NAME=BLOSUM62&NCBI_GI=on&PAGE=Proteins&PROGRAM=blastp&SERVICE=plain&SET_DEFAULTS.x=41&SET_DEFAULTS.y=5&SHOW_OVERVIEW=on&END_OF_HTTPGET=Yes&SHOW_LINKOUT=yes&GET_SEQUENCE=yes&SEARCH_NAME=bp
	    //http://www.ncbi.nlm.nih.gov/blast/blast.cgi
	    //String str,params="CMD=PUT&NCBI_GI=on&DATABASE="+getDatabase()+"&EXPECT="+getExpectedValue()+"&GAPCOSTS="+getGapCosts()+"&HITLIST_SIZE="+getMaxResults()+"&MATRIX_NAME="+getMatrix()+"&QUERY="+getQuerySequence().toString()+"&RUN_PSIBLAST=ON&I_TRESH="+getPsiThreshhold();
	    
	    Vector buffer=sendCommand(params);
	    
	    boolean resend = true;
		int nbResend=0;
		while (resend)
		{
			resend = false;
			boolean startParse=false;
			Iterator outputData=buffer.iterator();
			int debut=0;
			
		    try
		    {	
			
			setRID("");
			setRTOE("");
			
			while(outputData.hasNext())
			{
			    str=(String)outputData.next();
			    //System.out.println(""+str);
			    
			    
			    
				if((str.indexOf("<input name=\"RTOE\" type=\"hidden\" value=\"")!=-1) && (str.indexOf("<input name=\"RID\" type=\"hidden\" value=\"")!=-1))
				{  
					debut=str.indexOf("<input name=\"RTOE\" type=\"hidden\" value=\"");
				    setRTOE(str.substring(debut+40,debut+40+2));  
				    //System.out.println(""+str.substring(debut+40,debut+40+2));
				    debut=str.indexOf("<input name=\"RID\" type=\"hidden\" value=\"");
				    setRID(str.substring(debut+39,debut+39+11));
				    //System.out.println(""+str.substring(debut+39,debut+39+11));
				    setPrevRID(getRID());    
				    break;
				}
				
			    
			    
			    /*
			    
			    if(startParse)
			    {
			    
				if(str.indexOf("RTOE")!=-1) 
				{  
				    setRTOE(str.substring(str.indexOf("=")+1).trim());   
				    break;
				}
				else if(str.indexOf("RID")!=-1) 
				//else if(str.indexOf("<input name=\"RID\" type=\"hidden\" value=\"")!=-1) 
				{
					
				    setRID(str.substring(str.indexOf("=")+1).trim());	
				    setPrevRID(getRID());
				}
				
				
			    }
			    else if(str.indexOf("")!=-1)
			    {
				startParse=true;
			    }
			    
			    */
			    
			}
			if(getRID().length()==0) 
				{
				//System.out.println("je passe par la 1"); 
				throw new Exception(); 
				}
			parsePGR(buffer);
			
		    }
		    
		    catch(Exception e) 
		    {	
			System.err.println("Failed to obtain RID -> "+e);
			nbResend++;
			if (nbResend<=10) resend=true;
			else throw new Exception();
		    }
		}
	    
	}
    
    public Vector iteratePsiPutCommand() throws Exception
	{
	    
	    String str,params="CMD=Put&NCBI_GI=on&SERVICE=psi&RUN_PSIBLAST=ON&SEARCH_DB_STATUS=43&HITLIST_SIZE="+getMaxResults()+"&QUERY="+getQuerySequence().toString()+"&DATABASE="+getDatabase()+"&EXPECT="+getExpectedValue()+"&I_TRESH="+getPsiThreshhold()+"&RID="+getRID()+"&PREV_RID="+getPrevRID()+"&CDD_RID=0&NEXT_I=Run%20PSI-Blast%20iteration%20"+(getCurrentIteration()+1)+"&RTOE="+getRTOE()+"&STEP_NUMBER="+getCurrentIteration()+"&_PGR="+getPGR();
	    
	    int i;
	    boolean startParse=false;
	    Iterator outputData,gi=goodGI.iterator();
	    String giCode;
	    Vector buffer;
	    /*
	    while(gi.hasNext())
	    {
		giCode=(String)gi.next();
		params+="&checked_GI="+giCode+"&good_GI="+giCode;
	    }
	    */
	    buffer=sendCommand(params);
	    outputData=buffer.iterator();
	    int debut=0;
	    
	    try
	    {	
		setPrevRID(getRID());
		setRID("");
		setRTOE("");
		
		while(outputData.hasNext())
		{
		    str=(String)outputData.next();
		    
		    //str=(String)outputData.next();
		    //System.out.println(""+str);	
		    
		    
		    
		    
			if((str.indexOf("<input name=\"RTOE\" type=\"hidden\" value=\"")!=-1) && (str.indexOf("<input name=\"RID\" type=\"hidden\" value=\"")!=-1))
			{  
				debut=str.indexOf("<input name=\"RTOE\" type=\"hidden\" value=\"");
			    setRTOE(str.substring(debut+40,debut+40+2));  
			    //System.out.println(""+str.substring(debut+40,debut+40+2));
			    debut=str.indexOf("<input name=\"RID\" type=\"hidden\" value=\"");
			    setRID(str.substring(debut+39,debut+39+11));
			    //System.out.println(""+str.substring(debut+39,debut+39+11));
			    setPrevRID(getRID());    
			    break;
			}
			
			
		    		    
		    /*
		    
		    if(startParse)
		    {
			if(str.indexOf("RTOE")!=-1) 
			{  
			    setRTOE(str.substring(str.indexOf("=")+1).trim());   
			    break;
				    }
			else if(str.indexOf("RID")!=-1) 
			{
			    setRID(str.substring(str.indexOf("=")+1).trim());
			}
				
		    }
		    else if(str.indexOf("QBlastInfoBegin")!=-1)
		    {
				startParse=true;
		    }
		    
		    */
		    
		}
		if(getRID().length()==0)
			{
			//System.out.println("je passe par la 2"); 
			throw new Exception(); 
			}
		parsePGR(buffer);
		
	    }
	    catch(Exception e) 
	    {	
		System.err.println("Failed to obtain RID -> "+e); throw new Exception(); 
	    }
	    return buffer;
	}
    
    public Vector sendPsiGetCommand() throws Exception
	{
	    
	    String str,giCode,params="CMD=GET&NCBI_GI=on&RUN_PSIBLAST=ON&NUM_OVERVIEW=0&HITLIST_SIZE="+getMaxResults()+"&ALIGNMENTS="+getNumAlignments()+"&DESCRIPTIONS="+getNumDescriptions()+"&EXPECT="+getExpectedValue()+"&I_TRESH="+getPsiThreshhold()+"&RID="+getRID()+"&RTOE="+getRTOE()+"&FORMAT_TYPE="+getFormat()+"&STEP_NUMBER="+getCurrentIteration()+"&_PGR="+getPGR();

	    Iterator gi=goodGI.iterator();
	    /*
	    while(gi.hasNext())
	    {
		giCode=(String)gi.next();
		params+="&good_GI="+giCode+"&checked_GI="+giCode;
	    }
	    */
	    Vector buffer=null; 
	    Iterator outputData;
	    boolean resend=true;
	    int numConnections=0, maxConnections=50;
	  
	    try
	    {	
		while(resend)
		{
		    resend=false;
		    
		    buffer=sendCommand(params);
		    outputData=buffer.iterator();
		    
		    while(outputData.hasNext()) 
		    {
			str=(String)outputData.next();
			
			if(str.indexOf("Status")!=-1) 
			{ 
			    if(str.indexOf("WAITING")!=-1) resend=true;
			    break;
			}
		    }
		    
		    
		    if(resend) 
			{
			    if(!(++numConnections < maxConnections)) throw new Exception(); 
			    Thread.sleep(10000);
			}
		    else 
			{ 
			    //parseGI(buffer); 
			    parsePGR(buffer);
			}
		}
		
	    }
	    catch(Exception e) 
	    {
		buffer=null; 
		System.err.println("Failed to fetch sequences:"); 
		e.printStackTrace();
		throw new Exception(); 
	    }
	
	    return buffer;
	}
    
    
}


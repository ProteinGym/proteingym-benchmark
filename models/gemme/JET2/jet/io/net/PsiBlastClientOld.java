package jet.io.net;

import java.util.*;

public class PsiBlastClientOld extends jet.io.net.WebClient implements jet.data.dataformat.Format
{

    String database, matrix, RID,gapCosts,RTOE,prevRID;
    int maxResults;
    double eValue;
    Vector goodGI;
    int iteration;
    jet.data.datatype.Sequence sequence=null;
    jet.ProgressIndicator progress;
    
    public PsiBlastClientOld(String url, String database, String matrix, int maxResults, double eValue,int gap_exist,int gap_ext,int iteration)
    {
	super(url);
	setDatabase(database); setMatrix(matrix); 
	setMaxResults(maxResults); setExpectedValue(eValue); 
	setGapCosts(gap_exist,gap_ext);
	setNumIteration(iteration);
	progress=new jet.ProgressIndicator("Blast");
    }
    
    public void setDatabase(String database){this.database=database;}
    public void setExpectedValue(double eValue){this.eValue=eValue;}
    public void setNumIteration(int iteration){this.iteration=iteration;}
    public int getNumIteration(){ return iteration;}
    public void setGapCosts(int gap_exist,int gap_ext)
    {
	gapCosts=gap_exist+"%20"+gap_ext;
    }
    
    public void setMatrix(String matrix){this.matrix=matrix;}
    public void setMaxResults(int maxResults) { this.maxResults=maxResults; }

    public void setQuerySequence(jet.data.datatype.Sequence sequence) {this.sequence=sequence;}

    public void setQuerySequence(String sequence) 
    { 
	setQuerySequence(new jet.data.datatype.Sequence("ref",sequence));
    }

    private void setRID(String RID){ this.RID=RID; }

    public String getDatabase(){ return database;}
    public double getExpectedValue(){ return eValue; }
    public String getGapCosts(){ return gapCosts;}
    public String getMatrix(){ return matrix; } 
    public int getMaxResults(){ return maxResults; }
    private String getRID(){ return RID; }
    public void setPrevRID(String prevRID){ this.prevRID=prevRID;}
    public String getPrevRID(){ return prevRID; }
    public String getPsiThreshhold(){ return "0.001"; }
    public String getRTOE() { return RTOE;}
    public void setRTOE(String RTOE) { this.RTOE=RTOE;}
   
    public jet.data.datatype.Sequence getQuerySequence()
    { 
	return sequence; 
    } 
    
    public Vector getData()
    {
	Vector dataBuffer=null;
	int retry=0;
	while((dataBuffer==null)&&(retry++<3)) 
	    {
		dataBuffer=initData();
		if(dataBuffer==null) 
		    {
			System.err.println("Reconnecting in 10 seconds");
			try { Thread.sleep(10000); } 
			catch(Exception e){}
		    }
	    }
	return dataBuffer;
    }
   
    public Vector initData()
    {
	Vector dataBuffer=null;
	int i;
	if(getQuerySequence()!=null)
	{
	    try
	    {
		progress.setStatus("Connecting to server");
		new Thread(progress).start();
		sendPutCommand();
		progress.setStatus("Waiting for server");
		dataBuffer=sendInitialPsiGetCommand();
		for(i=1;i<getNumIteration();i++)
		{
		    progress.setStatus("Starting iteration "+(i+1));
		    dataBuffer.add(new String("ave maria"));
		    dataBuffer.addAll(iterPutCommand());
		    progress.setStatus("Processing iteration "+(i+1));
		    dataBuffer.add(new String("dios mio"));
		    dataBuffer.addAll(sendIterPsiGetCommand());
		    System.err.println(goodGI);
		}
		progress.stop();
	    }
	    
	    catch(Exception e)
	    {
		System.err.println("Failed to obtain query result \n\n"+e);
	    }
		
	}
	
	return dataBuffer;
    }
    
    
    public void parseGI(Vector buffer)
    {
	Iterator iter=buffer.iterator();
	goodGI=new Vector(1,1);
	String line;
	int pos1,pos2;
	while(iter.hasNext())
	    {
		line=(String)iter.next();
		if((pos1=line.indexOf("good_GI"))!=-1)
		    {
			pos1=line.indexOf("=",pos1);
			pos2=line.indexOf("\">",pos1);
			goodGI.add(new String(line.substring(pos1+3,pos2)));
		    }
	    }
	
    }
    public void sendPutCommand() throws Exception
    {
	String str,params="CMD=PUT&DATABASE="+getDatabase()+"&EXPECT="+getExpectedValue()+"&GAPCOSTS="+getGapCosts()+"&HITLIST_SIZE="+getMaxResults()+"&MATRIX_NAME="+getMatrix()+"&QUERY="+getQuerySequence().toString()+"&RUN_PSIBLAST=ON&I_TRESH="+getPsiThreshhold();
	
	boolean startParse=false;
	Iterator outputData=sendCommand(params).iterator();
	
	
	try
	    {	
	
		setRID("");
		setRTOE("");
		
		while(outputData.hasNext())
		    {
			str=(String)outputData.next();
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
					setPrevRID(getRID());
				    }
				
				
			    }
		    	else if(str.indexOf("QBlastInfoBegin")!=-1)
			    {
				startParse=true;
			    }
		    }
		if(getRID().length()==0) throw new Exception();
	
	    }

	catch(Exception e) 
	    {	
		System.err.println("Failed to obtain RID -> "+e); throw new Exception(); 
	    }
        
    }
    
  
    public Vector sendInitialPsiGetCommand() throws Exception
    {
	String str,params="CMD=GET&DESCRIPTIONS="+getMaxResults()+"&FORMAT_TYPE=HTML&ALIGNMENTS=0&NUM_OVERVIEW=0&EXPECT="+getExpectedValue()+"&RID="+getRID()+"&RUN_PSIBLAST=ON"+"&I_TRESH="+getPsiThreshhold()+"&RTOE="+getRTOE();
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
			else parseGI(buffer);
		    }
		 
	    }
	catch(Exception e) 
	    {
		buffer=null; 
		System.err.println("Failed to fetch sequences"); 
		throw new Exception(); 
	    }
	
	return buffer;
    }

  
    public Vector sendIterPsiGetCommand() throws Exception
    {
	String str,params="CMD=GET&DESCRIPTIONS="+getMaxResults()+"&FORMAT_TYPE=HTML&ALIGNMENTS=0&NUM_OVERVIEW=0&EXPECT="+getExpectedValue()+"&RID="+getRID()+"&RUN_PSIBLAST=ON&I_TRESH="+getPsiThreshhold()+"&RTOE="+getRTOE();
	Iterator gi=goodGI.iterator();
	String giCode;

	while(gi.hasNext())
	    {
		giCode=(String)gi.next();
		params+="&good_GI="+giCode+"&checked_GI="+giCode;
	    }
	//System.err.println(params);
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
			else parseGI(buffer);
		    }
		 
	    }
	catch(Exception e) 
	    {
		buffer=null; 
		System.err.println("Failed to fetch sequences"); 
		throw new Exception(); 
	    }
	
	return buffer;
    }

    public Vector iterPutCommand() throws Exception
    {

	String str,params="CMD=Put&SERVICE=psi&DATABASE="+getDatabase()+"&FORMAT_TYPE=HTML&NUM_OVERVIEW=0&ALIGNMENTS=0&DESCRIPTIONS="+getMaxResults()+"&QUERY="+getQuerySequence().toString()+"&EXPECT="+getExpectedValue()+"&RUN_PSIBLAST=ON"+"&I_TRESH="+getPsiThreshhold()+"&RID="+getRID()+"&PREV_RID="+getPrevRID()+"&CDD_RID=0&NEXT_I=Run%20PSI-Blast%20iteration%202&RTOE="+getRTOE()+"&_PGR=0&SEARCH_DB_STATUS=43";
	
	int i;
	boolean startParse=false;
	Iterator outputData,gi=goodGI.iterator();
	String giCode;
	Vector out;

	while(gi.hasNext())
	    {
		giCode=(String)gi.next();
		params+="&checked_GI="+giCode+"&good_GI="+giCode;
	    }
	//System.err.println(params);
	out=sendCommand(params);
	
	
	outputData=out.iterator();
	try
	    {	
		setPrevRID(getRID());
		setRID("");
		setRTOE("");
		
		while(outputData.hasNext())
		    {
			str=(String)outputData.next();
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
		    }
		if(getRID().length()==0) throw new Exception();
		
		
	
	
	    }
	catch(Exception e) 
	    {	
		System.err.println("Failed to obtain RID -> "+e); throw new Exception(); 
	    }
	return out;
    }
}


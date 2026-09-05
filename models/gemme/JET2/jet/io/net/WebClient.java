package jet.io.net;

import java.io.*;
import java.net.*;
import java.util.*;

public class WebClient
{
    String url;

    public WebClient(String url) { setURL(url+"?"); }
    
    public String getURL(){ return url; }    
    
    public void setURL(String url){ this.url=url; }
    
    public Vector sendCommand() { return sendCommand(""); }
    
    public Vector sendCommand(String params)
    {
	
	BufferedReader in;
	String line;
	Vector outputData=new Vector(20,20);

	try
	    {
		URL urlCommand=new URL(getURL()+params);
		in = new BufferedReader(new InputStreamReader(urlCommand.openStream())); 
		
		while((line=in.readLine())!=null) outputData.add(line);
		    
		in.close();		
	    }
	
	catch(Exception e) {System.err.println("Failed to connect to server -> "+e);}
	
	return outputData;
    }
 
    
}


package jet.external;

import java.io.*;

import jet.JET;

/** Classe permettant d'executer une commande et de recuperer les erreurs d'execution */

public class Command
{
	String command="";
	String dir="";
	
	public Command(){;}
	
    public Command(String command,String dir)
    {
    	setCommand(command);
    	setDirectory(dir);
    }
    
    public String getCommand(){return this.command;}
    public String getDirectory(){return this.dir;}
    
    public void setCommand(String command){this.command=command;}
    public void setDirectory(String dir){this.dir=dir;}
    
    public int sendCommand(){
    
    	int exitVal=0;
    	
        try
	    {  
	        File dirFile=new File(dir);
			
			Runtime rt = Runtime.getRuntime();
			if (JET.DEBUG) System.out.println("Command: "+command);
			Process proc = rt.exec(command,null,dirFile);
		
			new Thread(new StreamReader(proc.getErrorStream())).start();
			new Thread(new StreamReader(proc.getInputStream())).start();
			  
			exitVal = proc.waitFor();
			if (JET.DEBUG) System.out.println("Exit value: "+exitVal);
	    } 
        catch (Throwable t)
	    {
        	t.printStackTrace();
	    }
        return exitVal;
    }
    


}

/** Processus permettant de lire des flux d'entree */

class StreamReader implements Runnable
{
	/** Flux d'entree */
    InputStream in=null;
    /** Visualisation ou non des donnees du flux */
    boolean verbose;

    public StreamReader(InputStream in)
    {
	this(in,false);
    }
    public StreamReader(InputStream in,boolean verbose)
    { 
	this.in=in;
	this.verbose=verbose;
    }
    
    public void run()
    {
	String str;
	try
	    {
		
		InputStreamReader is = new InputStreamReader(in);
		BufferedReader br = new BufferedReader(is);
		if(verbose)
		{    
		    while((str=br.readLine())!=null) System.out.println(str);
		}
		else 
		{
		    while(br.readLine()!=null);
		}
	    }
	catch(Exception e){}
    }
}

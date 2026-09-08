package jet.io.file;

import java.util.*;
import java.io.*;

/** Classe pour définir les méthodes d'écriture et de lecture de fichier par lignes */

public class FileIO
{
    
    /** Ecriture de chaque objet du vecteur data sur une ligne du fichier filename */
    
    public static void writeFile(String filename, Vector data,boolean printAppend)
    {
	//System.out.print("what do I write? ");
	//System.out.println(filename);
	Iterator iter=data.iterator();
	
	if(filename.length()>0) 
	    {
		File outputFile=new File(filename).getParentFile();
		if((outputFile!=null)&&(!outputFile.isDirectory()))
		    {
			System.err.println("output path : "+outputFile.getAbsolutePath()+" doesn't exists !");
			System.err.println("Creating output path: "+outputFile.getAbsolutePath());
			outputFile.mkdirs();
		    }
	    }
	try
	    {
		
		
		BufferedWriter out = new BufferedWriter(new FileWriter(filename,printAppend));
		while(iter.hasNext())
		    {
			out.write((String)iter.next()+"\n");
		    }
		
		out.close();
	    }
	
	
	catch(Exception e){e.printStackTrace();}
	
    }
    
    /** Stockage de chaque ligne du fichier filename dans un vecteur */
    
    public static Vector readFile(String filename)
    {
	//System.out.print("what do I read? ");
	//System.out.println(filename);

	Vector data=null;
	try
	    {
		data=new Vector(200,200);
		BufferedReader br=null;
		String line;
		File f = new File(filename);
		
		br = new BufferedReader(new FileReader(f));
		
		while((line = br.readLine()) != null)
		    {
			data.add(line);
		    }
		br.close();
	    }
	
	catch (FileNotFoundException e)
	    {
		System.err.println(e);
	    }
	
	catch (IOException e)
	    {
		System.err.println(e);
	    }
	
	catch(Exception e)
	    {
		System.err.println(e);
	    }
	
	finally{ }
	return data;
    }
}

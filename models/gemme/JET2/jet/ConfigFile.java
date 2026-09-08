package jet;

import java.util.*;
import java.io.*;

/** Classe pour gerer le fichier de configuration. Les paramatres dans 
 * le fichier de configuration doivent etre de la forme suivante: <BR>
 * ">categorie1<BR>
 * argument1 valeur1<BR>
 * argument2 valeur2<BR>
 * >categorie2<BR>
 * argument1 valeur1<BR>
 * argument2 valeur2" */

public class ConfigFile extends jet.io.file.FileIO
{
    
	/** Chaque element du vecteur configData correspond à une ligne du fichier de configuration. */
	Vector configData;
	/** nom du fichier de configuration */
	String fileName="";

    /***/
    /** CONSTRUCTEURS */
    /***/
    
    public ConfigFile(String filename)
    {
    	if (new File(filename).exists())
    	{
		    /* Recuperation du fichier de configuration sous forme de 
		     * vecteur (chaque elements est une ligne du fichier) */
			this.configData=readFile(filename);
			this.fileName=filename;
    	}
    	else
    	{
    		writeFile(filename, new Vector(),false);
    		this.configData=new Vector();
			this.fileName=filename;
    	}
    }
    
    /***/
    /** METHODES */
    /***/
    
    public String getFileName()
    {
	return fileName;
    }

    /** Methode qui retourne au format Double l'argument "arg" de la catégorie 
     * "category" dans le fichier de configuration */
    
    public double getDoubleParam(String category, String arg)
    {
    	String res = getParam(category,arg);
    	if (res.equals("")) res="-1";
    	return Double.valueOf(res).doubleValue();
    }

    /** Methode qui retourne au format int l'argument "arg" de la catégorie 
     * "category" dans le fichier de configuration */
    
    public int getIntParam(String category, String arg)
    {
    	String res = getParam(category,arg);
    	if (res.equals("")) res="-1";
    	return Integer.valueOf(res).intValue();
    }

    /** Methode pour recuperer l'argument "arg" de la catégorie 
     * "category" dans le fichier de configuration. */
    
    public String getParam(String category, String arg)
    {
	Iterator iter=configData.iterator();
	String line;
	boolean look=false;
	/* Boucle sur les lignes du fichier de configuration. */
	while(iter.hasNext()) 
	    {
		line=(String)iter.next();
		/* Si la ligne ne contient pas d'information elle n'est pas traitée. */
		if(line.trim().length()>1)
		    {			
			/* Les lignes definissant les categories des parametres commencent par ">". */
			if(line.indexOf(">")!=-1)
			    {
				/* Si on trouve la categorie "category" on le signale. */
				if(look) { break; }
				else if(line.indexOf(category)!=-1) { look=true; }
			    }
			else 
				if(look) 
			    {
				/* On est dans la bonne catégorie ==> Récupération de la 
				 * valeur du paramètre (deuxieme mot de la ligne). */
				if(line.indexOf(arg)!=-1) { return (line.trim().split("\\s+"))[1]; }
			    }
		    }
	    }
	
	return ""; 
    }
    
    /** Methode pour changer à "newVal" l'argument "arg" de la catégorie 
     * "category" dans le fichier de configuration. */
    
    public void setParam(String category, String arg, String newVal)
    {
    String oldVal="";
	Iterator iter=configData.iterator();
	String line;
	boolean look=false;
	boolean fin=false;
	int numLine=0;
	/* Boucle sur les lignes du fichier de configuration. */
	while((!fin)&&(iter.hasNext())) 
	    {
		line=(String)iter.next();
		/* Si la ligne ne contient pas d'information elle n'est pas traitée. */
		if(line.trim().length()>1)
		    {			
			/* Les lignes definissant les categories des parametres commencent par ">". */
			if(line.indexOf(">")!=-1)
			    {
				/* Si on trouve la categorie "category" on le signale. */
				if(look) { break; }
				else if(line.indexOf(category)!=-1) { look=true; }
			    }
			else 
				if(look) 
			    {
				/* On est dans la bonne catégorie ==> Récupération de la 
				 * valeur du paramètre (deuxieme mot de la ligne). */
				if(line.indexOf(arg)!=-1)
					{ 
					oldVal = (line.trim().split("\\s+"))[1]; 
					line=line.replace(oldVal, newVal);
					configData.set(numLine, line);
					writeFile(fileName,configData,false);
					fin=true;
					}
			    }
		    }
		numLine++;
	    }
    }
    
    public void addCategory(String category)
    {
    	Iterator iter=configData.iterator();
		String line;
		boolean look=false;
		/* Boucle sur les lignes du fichier de configuration. */
		while((!look)&&(iter.hasNext())) 
		{
			line=(String)iter.next();
			/* Si la ligne ne contient pas d'information elle n'est pas traitée. */
			if(line.trim().length()>1)
			{			
				/* Les lignes definissant les categories des parametres commencent par ">". */
				if(line.indexOf(">")!=-1)
				{
					/* Si on trouve la categorie "category" on le signale. */
					if(line.indexOf(category)!=-1) { look=true; }
				}
			}
		}
		if (!look)
		{
	    	configData.add("*****************************************");
	    	configData.add(">"+category);
	    	writeFile(fileName,configData,false);
		}
    }
    
    public void addParameter(String category, String parameter, String valParameter, String comParameter)
    {
    	addCategory(category);
    	//System.out.println("param:"+getParam(category, parameter));
    	if (getParam(category, parameter).equals(""))
    	{
    		//System.out.println("debut:"+configData.size());
	    	Iterator iter=configData.iterator();
	    	String line;
	    	boolean fin=false;
	    	int numLine=0;
	    	/* Boucle sur les lignes du fichier de configuration. */
	    	while((!fin)&&(iter.hasNext())) 
	    	{
	    		line=(String)iter.next();
	    		/* Si la ligne ne contient pas d'information elle n'est pas traitée. */
	    		if(line.trim().length()>1)
	    		{			
	    			/* Les lignes definissant les categories des parametres commencent par ">". */
	    			if(line.indexOf(">")!=-1)
	    			{
	    				//System.out.println("nv category");
		    			/* Si on trouve la categorie "category" on le signale. */
	    				if(line.indexOf(category)!=-1)
		    			{ 
		    				/* On est dans la bonne catégorie ==> ajout du parametre et de sa valeur. */
		    				//System.out.println("bonne category");
		    				configData.add(numLine+1, parameter+"\t"+valParameter+"\t\t\t\t\t\t"+comParameter);
	    					writeFile(fileName,configData,false);
	    					fin=true;
		    			}
	    			}
	    		}
	    		numLine++;
	    	}
    	}
    	else setParam(category, parameter, valParameter);
    }
    
}

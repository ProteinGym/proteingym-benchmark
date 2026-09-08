package jet;

import java.util.*;
import java.io.*;

public class Result {
	
	/** Format les resultats avant ecriture : place les String contenu dans nom_colonne en valeur d'entete des colonnes 
	 * contenues dans resultsdouble de result au 1/10000, ajout du nombres d'espaces necessaires à chaque colonne du 
	 * vecteur result (en fonction du plus grand mot de la colonne) */
	
	public static Vector FormatResult(Vector result, Vector nom_colonnes)
	{
		int nbValeur=((Vector)result.get(0)).size();
		for(int i=1;i<result.size();i++)
		{
			if (nbValeur!=((Vector)result.get(i)).size())
			{
				System.err.println("resultats de taille différentes FormatResult");
				System.err.println("taille colonne 0:"+nbValeur+" taille colonne "+i+":"+((Vector)result.get(i)).size());
				new Exception().printStackTrace();
				System.exit(0);
			}	
		}
		Vector data=new Vector();
		if (result.size()!=nom_colonnes.size())
		{
			nom_colonnes=new Vector(1,1);
			for (int i=0;i<result.size();i++)
			{
				nom_colonnes.add("col"+(i+1));
			}
		}
		
		for(int i=0;i<result.size();i++)
		{
			for(int j=0;j<((Vector)result.get(i)).size();j++)
			{
				if (((Vector)result.get(i)).get(j) instanceof Double)
					((Vector)result.get(i)).set(j,(int)(((Double)((Vector)result.get(i)).get(j))*10000)/10000.0);
			}
			data=addColToFormatResults(data,((Vector)result.get(i)),(String)nom_colonnes.get(i));
		}	
		return data;	
	}

	/** Lance le formatage des resultats contenus dans result et d'entete 
	 * nom_colonnes puis les écrit dans le fichier filename */
	
	public static void WriteResult(Vector result, Vector nom_colonnes, String filename)
	{
		if (result.size()>0)
		{
			Vector formatResult=FormatResult(result, nom_colonnes);
			jet.io.file.FileIO.writeFile(filename,formatResult,false);
		}
	}
	
	/** Lecture des entetes de colonnes contenues dans le fichier dataFile (premier ligne du fichier) */
	
	public static Vector readCaracResult(String dataFile)
	{
		Vector nom_colonnes=new Vector();
		Vector data=jet.io.file.FileIO.readFile(dataFile);
		if (data.size()>0)
		{
			String line=(String)data.get(0);
			String[] subLine=line.split("\\s+");
			nom_colonnes=new Vector(subLine.length);
			for (int i=0; i<subLine.length;i++) nom_colonnes.add(subLine[i]);
		}
		return nom_colonnes;
	}
	
	/** Lecture des resultats (sans les entetes de colonnes) contenus dans le fichier dataFile */
	
	public static Vector readValuesResult(String dataFile)
	{
		Vector result=new Vector();
		Vector data=jet.io.file.FileIO.readFile(dataFile);
		if (data.size()>0)
		{
			String[] subLine=((String)data.get(0)).split("\\s+");
			data.remove(0);
			result=new Vector(subLine.length);
			for (int i=0; i<subLine.length;i++) result.add(new Vector());
			for (int i=0; i<data.size();i++)
			{
				subLine=((String)data.get(i)).split("\\s+");
				for (int j=0; j<subLine.length;j++) ((Vector)result.get(j)).add(subLine[j]);
			}
		}
		return result;
	}
	
	/** Coupe les fichiers de resultats par chaine */
	
	public static void cutPdbChainResult(String dataFile)
	{
		String prefix=dataFile.substring(0, dataFile.lastIndexOf("_"));
		String suffix=dataFile.substring(dataFile.lastIndexOf("_"), dataFile.length());
		Vector nom_colonnes=readCaracResult(dataFile);
		Vector values=readValuesResult(dataFile);		
		int numChainCol=Result.searchNumCol(nom_colonnes, "chain");
		if (numChainCol!=-1)
		{
			int i=0, nbLines=((Vector)values.get(numChainCol)).size();
			Vector chainResultsTemp=new Vector();
			String chainTemp;
			while (i<nbLines)
			{
				chainResultsTemp.clear();
				chainTemp=(String)((Vector)values.get(numChainCol)).get(i);
				//System.out.println("********************chaine:"+chainTemp);
				while ((i<nbLines)&&(chainTemp.equals((String)((Vector)values.get(numChainCol)).get(i))))
				{
					Result.addLine(chainResultsTemp, Result.searchLine(i, values));
					i++;
				}
				//System.out.println("i="+i);
				Result.WriteResult(chainResultsTemp, nom_colonnes, prefix+"_"+chainTemp+suffix);
				//System.out.println("ecriture "+prefix+"_"+chainTemp+"_axs.res");
			}
		}
		
	}
	
	/** Methode pour ecrire la colonne "col" du fichier de resultats "dataFile" 
	 * dans la colonne temperature du fichier pdb "pdbFile". Le nouveau fichier 
	 * crée est de la forme pdbFile_nomCol.pdb avec nomCol l'entete de la colonne 
	 * "col" dans le fichier de resultats. */
	
	public static void convertResultToPDB(String dataFile,String pdbFile, int col, int unite)
    {
		Vector data=jet.io.file.FileIO.readFile(dataFile);
		if (data.size()>0)
		{
			String line=(String)data.get(0);
			String[] subLine=line.split("\\s+");
			/* On recupere l'entete de la colonne col */
			String type=subLine[col];
			/* On retire la premiere ligne qui decrit le contenu des colonnes */
			data.remove(0);
			new jet.io.file.PdbFileTransform(pdbFile,"_"+type, parseColData(data,col),unite);
		}
    }
	
	/** Ecriture de la colonne nomCol du fichier de resultats dataFile dans la colonne temperature du fichier pdb pdbFile.
	 * Unite=1 ecriture des valeurs de la colonne pour chaque residu, 
	 * unite=2 ecriture des valeurs de la colonne pour chaque atomes. */
	
	public static boolean convertResultToPDB(String dataFile,String pdbFile, String nomCol, int unite)
    {
	boolean colFind=false;
	Vector data=jet.io.file.FileIO.readFile(dataFile);
	if (data.size()>0)
	{
		String line=(String)data.get(0);
		String[] subLine=line.split("\\s+");
		/* On recupere le numero de la colonne nomCol */
		int numCol=-1;
		for (int i=0; i<subLine.length;i++) if (subLine[i].equals(nomCol)) numCol=i;
		/* On retire la premiere ligne qui decrit le contenu des colonnes et on ecrit le fichier pdb */
		if (numCol!=-1)
		{
			String type=subLine[numCol];
			data.remove(0);
			new jet.io.file.PdbFileTransform(pdbFile,"_"+type, parseColData(data,numCol),unite);
			colFind=true;
		}
		else 
		{
			//System.err.println("pas de colonne "+nomCol+" dans le fichier "+dataFile);
			colFind=false;
		}
	}
	return colFind;
    }
   
	/** Data contient les donnees d'un fichier resultats par ligne. La methode retourne les valeurs 
	 * (String) de la colonne col.*/
	
    public static Vector findColData(Vector data, int col)
    {
	Iterator iter=data.iterator();
	Vector stringData=new Vector(100,100);
	String line;
	String[] subLine;
	String s;
	
	if (col>=0)
	{
		while(iter.hasNext())
		{
			line=(String)iter.next();
			subLine=line.split("\\s+");
			if (col<subLine.length)
			{
				s=subLine[col];
				stringData.add(s);
			}
		}
	}

	return stringData;
    } 
    
    /** Data contient les donnees d'un fichier resultats par ligne. La methode retourne les valeurs 
	 * (Double) de la colonne col.*/
	
    public static Vector parseColData(Vector data, int col)
    {
	Iterator iter=data.iterator();
	Vector doubleData=new Vector(100,100);
	String line;
	String[] subLine;
	double d;
	if (col>=0)
	{
		while(iter.hasNext())
		{
			line=(String)iter.next();
			subLine=line.split("\\s+");
			if (col<subLine.length)
			{
				d=((int)(Double.valueOf(subLine[col]).doubleValue()*10000)/10000.0);
				//d=(100.0-Double.valueOf(subLine[col]).doubleValue())/100.0;
				doubleData.add(new Double(d));
			}
		}
	}
	return doubleData;
    } 
    
    /** Data contient les donnees d'un fichier resultats par ligne. Methode qui separe une colonne en deux en ajoutant
     *  un espace a la position posSeparation dans chaque element de type String du vecteur data. */
    
    public static Vector splitColData(Vector data, int posSeparation)
    {
	Iterator iter=data.iterator();
	Vector newData=new Vector();
	String line;
	while(iter.hasNext())
	{
		line=(String)iter.next();
		//if (line.charAt(posSeparation)!=' ')
		if (posSeparation<line.length())
			newData.add(new String(line.substring(0, posSeparation)+" "+line.substring(posSeparation)));
		else 
			newData.add(new String(line+""));
    }
	return newData;
    } 
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * La methode multiplie les valeurs (de type String ou Double) d'une meme ligne situees dans les colonnes 
     * dont l'indice apparait dans le vecteur cols. Les resultats sont ajoutes a la matrice data dans une nouvelle
     *  colonne a la position numNewCol. */
    
    public static void multiplyColData(Vector data, Vector cols, int numNewCol)
    {
    	Vector valeurs_colonne=new Vector();
    	double valTemp;
    	if (cols.size()>0)
    	{
	    	for (int i=0;i<((Vector)data.get(((Integer)cols.get(0)).intValue())).size();i++)
	    	{
	    		valTemp=1.0;
	    		for (int j=0;j<cols.size();j++)
	    		{
	    			if (((Vector)data.get(((Integer)cols.get(j)).intValue())).get(i) instanceof Double)
	    				valTemp=valTemp*(Double)((Vector)data.get(((Integer)cols.get(j)).intValue())).get(i);
	    			if (((Vector)data.get(((Integer)cols.get(j)).intValue())).get(i) instanceof String)
	    				valTemp=valTemp*Double.valueOf((String)((Vector)data.get(((Integer)cols.get(j)).intValue())).get(i)).doubleValue();
	    		}
	    		valeurs_colonne.add(valTemp);
	    	}
    	}
    	addCol(data,valeurs_colonne,numNewCol);
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * La methode divise les valeurs (de type String ou Double) d'une meme ligne situees dans les colonnes 
     * dont l'indice apparait dans le vecteur cols. Les resultats sont ajoutes a la matrice data dans une nouvelle
     *  colonne a la position numNewCol. */
    
    public static void dividColData(Vector data, Vector cols, int numNewCol)
    {
    	Vector valeurs_colonne=new Vector();
    	double valTemp;
    	if (cols.size()>0)
    	{
	    	for (int i=0;i<((Vector)data.get(((Integer)cols.get(0)).intValue())).size();i++)
	    	{
	    		valTemp=1.0;
	    		if (((Vector)data.get(((Integer)cols.get(0)).intValue())).get(i) instanceof Double)
    				valTemp=(Double)((Vector)data.get(((Integer)cols.get(0)).intValue())).get(i);
    			if (((Vector)data.get(((Integer)cols.get(0)).intValue())).get(i) instanceof String)
    				valTemp=Double.valueOf((String)((Vector)data.get(((Integer)cols.get(0)).intValue())).get(i)).doubleValue();
	    		
	    		for (int j=1;j<cols.size();j++)
	    		{
	    			if (((Vector)data.get(((Integer)cols.get(j)).intValue())).get(i) instanceof Double)
	    				valTemp=valTemp/(Double)((Vector)data.get(((Integer)cols.get(j)).intValue())).get(i);
	    			if (((Vector)data.get(((Integer)cols.get(j)).intValue())).get(i) instanceof String)
	    				valTemp=valTemp/Double.valueOf((String)((Vector)data.get(((Integer)cols.get(j)).intValue())).get(i)).doubleValue();
	    		}
	    		valeurs_colonne.add(valTemp);
	    	}
    	}
    	addCol(data,valeurs_colonne,numNewCol);
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * La methode additionne les valeurs (de type String ou Double) d'une meme ligne situees dans les colonnes 
     * dont l'indice apparait dans le vecteur cols. Les resultats sont ajoutes a la matrice data dans une nouvelle
     *  colonne a la position numNewCol. */
    
    public static void addColData(Vector data, Vector cols, int numNewCol)
    {
    	Vector valeurs_colonne=new Vector();
    	double valTemp;
    	if (cols.size()>0)
    	{
	    	for (int i=0;i<((Vector)data.get(((Integer)cols.get(0)).intValue())).size();i++)
	    	{
	    		valTemp=0.0;
	    		for (int j=0;j<cols.size();j++)
	    		{
	    			if (((Vector)data.get(((Integer)cols.get(j)).intValue())).get(i) instanceof Double)
	    				valTemp=valTemp+(Double)((Vector)data.get(((Integer)cols.get(j)).intValue())).get(i);
	    			if (((Vector)data.get(((Integer)cols.get(j)).intValue())).get(i) instanceof String)
	    				valTemp=valTemp+Double.valueOf((String)((Vector)data.get(((Integer)cols.get(j)).intValue())).get(i)).doubleValue();
	    		}
	    		valeurs_colonne.add(valTemp);
	    	}
    	}
    	addCol(data,valeurs_colonne,numNewCol);
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * La methode soustrait les valeurs (de type String ou Double) d'une meme ligne situees dans les colonnes 
     * dont l'indice apparait dans le vecteur cols. Les resultats sont ajoutes a la matrice data dans une nouvelle
     *  colonne a la position numNewCol. */
  
    public static void minusColData(Vector data, Vector cols, int numNewCol)
    {
    	Vector valeurs_colonne=new Vector();
    	double valTemp;
    	if (cols.size()>0)
    	{
	    	for (int i=0;i<((Vector)data.get(((Integer)cols.get(0)).intValue())).size();i++)
	    	{
	    		valTemp=0.0;
	    		if (((Vector)data.get(((Integer)cols.get(0)).intValue())).get(i) instanceof Double)
    				valTemp=(Double)((Vector)data.get(((Integer)cols.get(0)).intValue())).get(i);
    			if (((Vector)data.get(((Integer)cols.get(0)).intValue())).get(i) instanceof String)
    				valTemp=Double.valueOf((String)((Vector)data.get(((Integer)cols.get(0)).intValue())).get(i)).doubleValue();
	    		for (int j=1;j<cols.size();j++)
	    		{
	    			if (((Vector)data.get(((Integer)cols.get(j)).intValue())).get(i) instanceof Double)
	    				valTemp=valTemp-(Double)((Vector)data.get(((Integer)cols.get(j)).intValue())).get(i);
	    			if (((Vector)data.get(((Integer)cols.get(j)).intValue())).get(i) instanceof String)
	    				valTemp=valTemp-Double.valueOf((String)((Vector)data.get(((Integer)cols.get(j)).intValue())).get(i)).doubleValue();
	    		}
	    		valeurs_colonne.add(valTemp);
	    	}
    	}
    	addCol(data,valeurs_colonne,numNewCol);
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne.
     * La methode copie la colonne a la position col de data et l'ajoute a data a la position col+1. */
    
    public static void cloneColData(Vector data, int col)
    {
    	Vector valeurs_colonne=new Vector();
    	if ((col>=0)&&(col<data.size()))
    	{
	    	for (int i=0;i<((Vector)data.get(col)).size();i++)
	    	{
	    		valeurs_colonne.add(((Vector)data.get(col)).get(i));
	    	}
	    }
    	addCol(data,valeurs_colonne,col+1);
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne.
     *  La methode divise par nb les valeur de la colonne a la position col. */
    
    public static void dividColData(Vector data, int col, int nb)
    {
    	if ((col>=0)&&(col<data.size())&&(nb>0))
    	{
	    	for (int i=0;i<((Vector)data.get(col)).size();i++)
	    	{
	    		if (((Vector)data.get(col)).get(i) instanceof String)
	    			((Vector)data.get(col)).set(i,Double.valueOf((String)((Vector)data.get(col)).get(i)).doubleValue()/(double)nb);
	    		if (((Vector)data.get(col)).get(i) instanceof Double)
	    			((Vector)data.get(col)).set(i,(Double)((Vector)data.get(col)).get(i)/(double)nb);
	    	}
	    }
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne.
     * La methode multiplie par nb les valeur de la colonne a la position col. */
    
    public static void multiplyColData(Vector data, int col, int nb)
    {
    	if ((col>=0)&&(col<data.size())&&(nb>0))
    	{
	    	for (int i=0;i<((Vector)data.get(col)).size();i++)
	    	{
	    		if (((Vector)data.get(col)).get(i) instanceof String)
	    			((Vector)data.get(col)).set(i,Double.valueOf((String)((Vector)data.get(col)).get(i)).doubleValue()*(double)nb);
	    		if (((Vector)data.get(col)).get(i) instanceof Double)
	    			((Vector)data.get(col)).set(i,(Double)((Vector)data.get(col)).get(i)*(double)nb);
	    	}
	    }
    }
    
    /** Ajoute la colonne d'entete nomCol du fichier de resultats dataFileResult au fichier de resultats dataFileResults. */
   
    public static void addColToFormatResults(String dataFileResults,String dataFileResult, String nomCol)
    {
    	Vector dataResults=jet.io.file.FileIO.readFile(dataFileResults);    	
    	int col=searchNumCol(readCaracResult(dataFileResult),nomCol);
    	Vector valeurs_colonne=findColData(readValuesResult(dataFileResult),col);    	
    	if ((valeurs_colonne.size()>0)&&(dataResults.size()>0))
    	{
	    	dataResults=addColToFormatResults(dataResults,valeurs_colonne,nomCol);
	    	jet.io.file.FileIO.writeFile(dataFileResults,dataResults,false);
    	}
    }
    
    /** DataResults contient les lignes d'un fichier resultat par ligne. La methode ajote la colonne d'entete nom_colonne
     *  et de valeurs contenues dans valeurs_colonne aux colonnes de representees par dataResults. */
    
    public static Vector addColToFormatResults(Vector dataResults, Vector valeurs_colonne, String nom_colonne)
    {
    	Vector data=new Vector(dataResults.size());
    	if (dataResults.size()==0)
    	{
    		Vector dataTemp = new Vector(valeurs_colonne.size()+1);
    		dataTemp.add("");
    		for (int pos=0; pos<valeurs_colonne.size(); pos++)
			{
    			dataTemp.add("");
			}
    		dataResults=dataTemp;
    	}
    	if(dataResults.size()==valeurs_colonne.size()+1)
    	{
    		int taille_col_temp=0;
    		int taille_col= nom_colonne.length();
    		for(int j=0;j<valeurs_colonne.size();j++)
    		{
    			taille_col_temp=(""+valeurs_colonne.get(j)).length();
    			if (taille_col_temp>taille_col) taille_col=taille_col_temp;
    		}
    		taille_col=taille_col+1;
    		    		
    		String ligne=""+dataResults.get(0);
    		String mot_temp=""+nom_colonne;
    		String espaces="";
    			
			for(int nb_car=mot_temp.length();nb_car<taille_col;nb_car++) 
				espaces=espaces+" ";
			ligne=ligne+mot_temp+espaces;
			data.add(ligne);
			
			for (int pos=0; pos<valeurs_colonne.size(); pos++)
			{		
				ligne=""+dataResults.get(pos+1);					
				mot_temp=""+valeurs_colonne.get(pos);
				espaces="";					
				for(int nb_car=mot_temp.length();nb_car<taille_col;nb_car++) 
					espaces=espaces+" ";
				ligne=ligne+mot_temp+espaces;
				
				data.add(ligne);		
			}			   		
    	}
    	else
    	{
    		System.err.println("Resultats de taille differentes addCol"); 
    		data=dataResults;
    	}
    	return data;
    	
    }
    
    /** nomCols contient des entetes de colonnes. La methode retourne la position de l'entete nomCol 
     * dans nomsCols, -1 sinon */
    
    public static int searchNumCol(Vector nomCols, String nomCol)
    {
    	int num=-1;
    	for (int i=0;i<nomCols.size();i++) if (((String)nomCols.get(i)).equals(nomCol)) num=i;
    	return num;
    }
    
    /** nomCols contient des entetes de colonnes. La methode retourne un vecteur les positions des l'entete de nomsCols
     * dont le nom contient nomCol. */
    
    public static Vector searchNumCols(Vector nomCols, String nomCol)
    {
    	Vector numCols=new Vector();
    	for (int i=0;i<nomCols.size();i++) if (((String)nomCols.get(i)).indexOf(nomCol)!=-1) numCols.add(i);
    	return numCols;
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * La methode retourne le numero de ligne de l'element de la colonne col dont la valeur est la plus 
     * proche de value. */
    
    public static int searchNumLine(int col, double value, Vector data)
    {
    	//System.out.println("taille lines:"+data.size());
    	int ligne=-1;
    	if ((data.size()!=0)&&(col>=0)&&(col<data.size()))
    	{
	    	double minEccart=Math.abs(value-((Double)((Vector)data.get(col)).get(0)));
	    	ligne=0;
	    	for (int i=1; i<((Vector)data.get(col)).size();i++)
	    	{
	    		if (minEccart>Math.abs(value-((Double)((Vector)data.get(col)).get(i))))
	    		{
	    			minEccart=Math.abs(value-((Double)((Vector)data.get(col)).get(i)));
	    			ligne=i;
	    		}
	    	}
    	}
    	//System.out.println("num line:"+ligne);
    	return ligne;
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * La methode retourne le numero de ligne du premier element de la colonne col dont la valeur est egale 
     * a value. */
    
    public static int searchNumLine(int col, String value, Vector data)
    {
//    	System.out.println("taille lines:"+data.size());
    	int ligne=-1;
    	if ((data.size()>0)&&(col<data.size())&&(col>=0))
    	{
	    	for (int i=0; i<((Vector)data.get(col)).size();i++)
	    	{
	    		if (value.equals(((Vector)data.get(col)).get(i))) ligne=i;
	    	}
    	}
    	//System.out.println("num line:"+ligne);
    	return ligne;
    	
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * La methode retourne les lignes pour lesquelles l'element de la colonne col a une valeur egale 
     * a value. */
    
    public static Vector searchLines(int col, String value, Vector data)
    {
//    	System.out.println("taille lines:"+data.size());
    	Vector newData=new Vector(data.size());
    	for (int i=0; i<data.size();i++)
    	{
    		newData.add(new Vector());
    	}
    	
    	if ((data.size()>0)&&(col>=0)&&(col<data.size()))
    	{
	    	for (int i=0; i<((Vector)data.get(col)).size();i++)
	    	{
	    		if (value.equals(((Vector)data.get(col)).get(i)))
	    		{
	    			for (int j=0; j<newData.size();j++)
	    	    	{
	    	    		((Vector)newData.get(j)).add(((Vector)data.get(j)).get(i));
	    	    	}
	    		}
	    	}
    	}
    	//System.out.println("num line:"+ligne);
    	return newData;
    	
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * La methode retourne le numero de ligne dont l'element de la colonne col a la plus petite valeur de 
     * cette colonne. */
    
    public static int searchNumLineMin(int col, Vector data)
    {
    	int ligne=-1;
    	if ((data.size()>0)&&(col>=0)&&(col<data.size()))
    	{
	    	double min=((Double)((Vector)data.get(col)).get(0));
	    	ligne=0;
	    	for (int i=1; i<((Vector)data.get(col)).size();i++)
	    	{
	    		if (min>((Double)((Vector)data.get(col)).get(i)))
	    		{
	    			min=((Double)((Vector)data.get(col)).get(i));
	    			ligne=i;
	    		}
	    	}
    	}
    	
    	return ligne;
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * La methode retourne la ligne a la position numLine. */
    
    public static Vector searchLine(int numLine, Vector data)
    {
    	//System.out.println("num line:"+numLine+" taille lines:"+data.size());
    	Vector line=new Vector();
    	if ((numLine>=0)&&(numLine<numberOfLines(data)))
    	{
	    	for (int i=0;i<data.size();i++)
	    	{
	    		line.add(((Vector)data.get(i)).get(numLine));
	    	}
    	}
    	else
    	{
    		for (int i=0;i<data.size();i++)
	    	{
	    		line.add(null);
	    	}
    	}
    	return line;
    	
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * La methode retourne la valeur de l'element a la ligne line et la colonne col. -1 si la position est 
     * en dehors de la matrice */
    
    public static double searchValue(int col, int line, Vector data)
    {
    	if ((data.size()>0)&&(col>=0)&&(col<data.size())&&(line>=0)&&(line<((Vector)data.get(col)).size())) return (Double)((Vector)data.get(col)).get(line);
    	else return -1;
    	
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * La methode retourne la moyenne des valeur des elements de la colonnne col. */
    
    public static double meanCol(Vector data,int numCol)
    {
    	
    	double mean=0.0;
    	if ((numCol>=0)&&(numCol<data.size()))
    	{
	    	Vector col = (Vector)data.get(numCol);
	    	double temp=0.0;
	    	double nb=0;
	    	for (int i=0; i<col.size();i++)
	    	{
	    		temp=0.0;
	    		try
	    		{
	    			if (col.get(i) instanceof String)	
	    				temp=Double.parseDouble((String)col.get(i));
	    			if (col.get(i) instanceof Double)	
	    				temp=(Double)col.get(i);
	    			//System.out.print(" "+temp);
	    			mean=mean+temp;
	    			nb++;
	    		}
	    		catch(NumberFormatException e)
	    		{
	    			System.err.println("trying to sum something which is not a Double");
	    		}
	    	}
	    	mean=mean/nb;
	    	//System.out.println("");
    	}
    	return mean;
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * La methode regroupe les lignes par valeur identique des elements de la colonne numColRegroup. La methode 
     * retourne ensuite des lignes contenant les valeurs moyennes de chaque colonne des lignes regroupees . */
    
    public static Vector regroupAndMean(Vector data,int numColRegroup)
    {
    	int nb=0;
    	Vector dataSave=new Vector();
    	Vector dataTemp=new Vector();
    	Vector meanData=new Vector();
    	
    	if (data.size()!=0)
    	{
    		for (int i=0; i<data.size();i++)
        	{
        		meanData.add(new Vector());
        		dataSave.add(new Vector());
        	}
    		for (int i=0; i<data.size();i++)
	    	{
	    		for (int j=0; j<((Vector)data.get(i)).size();j++)
	        	{
	    			((Vector)dataSave.get(i)).add(((Vector)data.get(i)).get(j));
	        	}
	    	}
    		if ((numColRegroup>=0)&&(numColRegroup<data.size()))
    		{
	    		while(((Vector)dataSave.get(numColRegroup)).size()>nb)
	    		//for (int i=0; i<((Vector)data.get(numColRegroup)).size();i++)
		    	{
	    			if (((Vector)dataSave.get(numColRegroup)).get(nb) instanceof String)
	    			{
	    				dataTemp=removeLines(numColRegroup, ((String)((Vector)dataSave.get(numColRegroup)).get(nb)), dataSave);
	
	    				for (int j=0; j<dataTemp.size();j++)
	    		    	{
	    					if (((Vector)dataTemp.get(j)).get(0) instanceof Double)	
	    						((Vector)meanData.get(j)).add(meanCol(dataTemp,j));
	    					else
	    					{
	    						if (j==numColRegroup)
	    							((Vector)meanData.get(j)).add(((Vector)dataTemp.get(j)).get(0));
	    						else
	    							((Vector)meanData.get(j)).add("regroup");
	    					}
	    		    	}
	    			}
	    			else nb++;
		    	}
    		}
    		else 
    		{
    			for (int j=0; j<dataSave.size();j++)
		    	{
					if (((Vector)dataSave.get(j)).get(0) instanceof Double)	
						((Vector)meanData.get(j)).add(meanCol(dataSave,j));
					else
					{
						if (j==numColRegroup)
							((Vector)meanData.get(j)).add(((Vector)dataSave.get(j)).get(0));
						else
							((Vector)meanData.get(j)).add("regroup");
					}
		    	}
    		}
    	}
    	return meanData;
    }
    
    /** DataAll et dataAdded contiennent les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * Les deux matrice doivent avoir les memes dimensions sauf si dataAll est vide. La methode retourne la somme des deux matrices,
     * ou la matrice dataAdded si dataAll est vide. */
    
    public static void sumResult(Vector dataAll,Vector dataAdded)
    {
    	if (dataAll.size()>0)
    	{
    		if (dataAdded.size()>0)
        	{
		    	for (int i=0; i<dataAll.size();i++)
		    	{
		    		for (int j=0; j<((Vector)dataAll.get(i)).size();j++)
		        	{
		    			if (((Vector)dataAll.get(i)).get(j) instanceof Double)
		    				((Vector)dataAll.get(i)).set(j,((Double)((Vector)dataAll.get(i)).get(j))+((Double)((Vector)dataAdded.get(i)).get(j)));
		        	}
		    	}
        	}
    	}
    	else
    	{
    		for (int i=0; i<dataAdded.size();i++)
	    	{
	    		dataAll.add(((Vector)dataAdded.get(i)).clone());
	    	}
    	}
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * La methode divise les valeurs de la matrice par nb. */
    
    public static void dividResult(Vector data, int nb)
    {
    	if ((data.size()>0)&&(nb!=0))
    	{
	    	for (int i=0; i<data.size();i++)
	    	{
	    		for (int j=0; j<((Vector)data.get(i)).size();j++)
	        	{
	    			if (((Vector)data.get(i)).get(j) instanceof Double)
	    				((Vector)data.get(i)).set(j,((Double)((Vector)data.get(i)).get(j))/(double)nb);
	        	}
	    	}
    	}
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * La methode ajoute la ligne line a la matrice data. line et data doivent avoir le meme nombre de colonnes. */
    
    public static void addLine(Vector data, Vector line)
    {
    	//System.out.println("taille lines:"+data.size()+" taille line:"+line.size());
    	if (data.size()!=line.size()&&(line.size()!=0))
    	{
    		if (data.size()==0)
    		{
    			for (int i=0;i<line.size();i++)
    	    	{
    				data.add(new Vector());
    	    		((Vector)data.get(i)).add(line.get(i));
    	    	}
    		}
    		else System.err.println("Resultats de taille differentes addLine"); 
    	}
    	else
    	{
	    	for (int i=0;i<line.size();i++)
	    	{
	    		((Vector)data.get(i)).add(line.get(i));
	    	}
    	}
    }
    
    /** Data et data1 contiennent les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * La methode ajoute les lignes de data1 a la matrice data. data1 et data doivent avoir le meme nombre de colonnes. */
    
    public static void addLines(Vector data, Vector data1)
    {
    	//System.out.println("taille lines:"+data.size()+" taille line:"+data1.size());
    	if (data.size()!=data1.size()&&(data1.size()!=0))
    	{
    		if (data.size()==0)
    		{
    			for (int i=0;i<data1.size();i++)
    	    	{
    				data.add(new Vector());
    	    		((Vector)data.get(i)).addAll((Vector)data1.get(i));
    	    	}
    		}
    		else System.err.println("Resultats de taille differentes addLines"); 
    	}
    	else
    	{
	    	for (int i=0;i<data1.size();i++) 
	    		((Vector)data.get(i)).addAll((Vector)data1.get(i));
	    		//addLine(data, searchLine(i, data1));
    	}
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * La methode retire la ligne a la position numLine de la matrice data. */
    
    public static Vector removeLine(Vector data, int numLine)
    {
    	Vector line=new Vector(data.size());
    	if ((data.size()>0)&&(numLine<((Vector)data.get(0)).size())&&(numLine>=0))
    	{
	    	for (int i=0;i<data.size();i++)
	    	{
	    		line.add(((Vector)data.get(i)).remove(numLine));
	    	}
    	}
    	return line;
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * La methode retire les lignes dont la valeur de l'element de la colonne col est egale a value. */
    
    public static Vector removeLines(int col, String value, Vector data)
    {
    	Vector newData=new Vector(data.size());
    	for (int i=0; i<data.size();i++)
    	{
    		newData.add(new Vector());
    	}
    	
    	if ((data.size()!=0)&&(col>=0)&&(col<data.size()))
    	{
	    	for (int i=0; i<((Vector)data.get(col)).size();i++)
	    	{
	    		if (value.equals(((Vector)data.get(col)).get(i)))
	    		{
	    			addLine(newData, removeLine(data, i));
	    			i--;
	    		}
	    	}
    	}
    	//System.out.println("num line:"+ligne);
    	return newData;
    	
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * La methode ajoute la colonne valeurs_colonne a la matrice data. valeurs_colonne doit avoir autant d'element 
     * que data a de lignes. */
    
    public static void addCol(Vector data, Vector valeurs_colonne, int posCol)
    {
    	if ((data.size()>0)&&(posCol>=0))
    	{
    		if (posCol>data.size()) posCol=data.size();
	    	if (((Vector)data.get(0)).size()!=valeurs_colonne.size())
	    	{
	    		System.err.println("Resultats de taille differentes addCol");
	    	}
	    	else
	    	{
	    		if (valeurs_colonne.size()!=0) data.add(posCol,valeurs_colonne);
	    		else System.err.println("colonne à ajouter vide");
	    	}
    	}
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * La methode retire la colonne a la position posCol de data. */
    
    public static void removeCol(Vector data, int posCol)
    {
    	if ((data.size()>0)&&(posCol>=0)&&(data.size()>posCol))
    	{
	    	data.remove(posCol);
    	}
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * La methode retourne la colonne a la position posCol de data. */
    
    public static Vector getCol(Vector data, int posCol)
    {
    	Vector col=new Vector();
    	if ((data.size()>0)&&(posCol>=0)&&(data.size()>posCol))
    	{
    		col=(Vector)data.get(posCol);
    	}
    	return col;
    }
    
    /** Data contient les donnees d'un fichier resultats dans une matrice (vecteur de vecteur) par colonne. 
     * La methode retourne le nombre de lignes de la matrice data. */
    
    public static int numberOfLines(Vector data)
    {
    	if (data.size()>0) return ((Vector)data.get(0)).size();
    	else return 0;
    }
	
}

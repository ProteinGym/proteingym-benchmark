package jet.tree.tools;

import java.util.*;
import java.io.*;

/** Classe pour stocker les distances entre les residus */

public class SubstitutionMatrix implements Serializable
{
	/** Expected value de la matrice */
    private double eValue=-1000;
    /** Matrice contenant les distance entre les residus. 
     * Cette matrice est symetrique selon la diagonale. */
    private double[][] subMatrix;
    private transient Vector index=null;
    /** Fichier contenant la matrice */
    String matrixfile;
    
    /***/
    /** CONSTRUCTEURS */
    /***/
    
    public SubstitutionMatrix(String matrixFile)
    {
	parseMatrixFile(matrixFile);
	matrixfile=matrixFile;
    }
    
    /***/
    /** ACCESSEURS */
    /***/
    
   /** Retourne la distance entre deux residus res1 et res2 
    * selon la matrice de substitution subMatrix. */
    
    public double getDistance(jet.data.datatype.Residue res1, jet.data.datatype.Residue res2) 
    { 
	//System.out.print(matrixfile+"\t"+res1+" : "+res2+" -> ");
	if(res1.getResidueIndex()>=res2.getResidueIndex())
	    {
		//System.out.println(subMatrix[res1.getResidueIndex()][res2.getResidueIndex()]);
		return subMatrix[res1.getResidueIndex()][res2.getResidueIndex()];
	    }
	else 
	    {
		//System.out.println(subMatrix[res2.getResidueIndex()][res1.getResidueIndex()]);
		return subMatrix[res2.getResidueIndex()][res1.getResidueIndex()]; 
	    }
    }

    public double getExpectedValue(){ return eValue;}    
    private int getIndex(int pos) { return ((Integer)index.get(pos)).intValue(); }
    private boolean indexInitiated() { return (index!=null); }
    
    /***/
    /** MODIFICATEURS */
    /***/
    
    private void clearIndex() { index.clear(); index=null; }
    private void setExpectedValue(double eValue){ this.eValue=eValue;}
       
    /***/
    /** METHODES */
    /***/
    
    /** Parse le fichier "matrixFile" contenant la matrice de substitution
     *  afin d'initialiser les variables d'instance "evalue", "subMatrix" et "index" */
    
    private void parseMatrixFile(String matrixFile)
    {
	Iterator iter=readFile(matrixFile).iterator();
	String line; String[] subline;
	int row=1,col;
	
	while(iter.hasNext())
	    {
		line=(String)iter.next();
		if(!indexInitiated()) initIndex(line);
		else
		    {
			subline=line.trim().split("\\s+");
		
			for(col=row;col<subline.length;col++) 
			    subMatrix[getIndex(row-1)][getIndex(col-1)] = Double.parseDouble(subline[col]);
			row++;
		    }
	    }
    }
    
    /** Lecture du fichier "matrixFile" pour extraire et initialiser l'"evalue" 
     * et retourner un vecteur contenant les lignes du fichier correspondant à la 
     * matrice de substitution. */
    
    private Vector readFile(String matrixFile)
    {
	Vector data=null;
	int pos;
	try
	    {
		data = new Vector(30,10);
		String line;
		File f = new File(matrixFile);
		BufferedReader br= new BufferedReader(new FileReader(f));
		
		while((line = br.readLine()) != null) 
		    { 
			if( line.length()>0)
			    {
				if(line.charAt(0) != '#' ) data.add(line); 
				else extractExpectedValue(line);
				    
			    }
		    }
		
		br.close();
		
	    }
	catch(Exception e){ System.err.println("Substitution Matrix File not found"); System.err.println(e); } finally{}
	return data;
    }
   
    /** Initialise la matrice "subMatrix" et stocke dans le vecteur "index" 
     * l'index des residus dans l'ordre dans lequel ils sont rencontrés dans 
     * la premiere ligne de la matrice. */
    
    private void initIndex(String header)
    {
	subMatrix=new double[24][24];
	String[] subHeader=header.trim().split("\\s+");
	index=new Vector(1,1);
	
	for(int i=0;i<subHeader.length;i++) 
	    {
		if(subHeader[i].compareTo("*")==0) subHeader[i]="GAP";
		index.add(new Integer(jet.data.datatype.Residue.getResidueIndex(subHeader[i])));
	    }
	
    }
  
    /** Extraction et initialisation de l'"evalue" à partir de la ligne du 
     * fichier source de la matrice (matrixfile) contenant cette valeur. */
    
    private void extractExpectedValue(String line)
    { 
	int start,end;
	line=line.toLowerCase();
	
	if((start=line.indexOf("expected"))!=-1)
	    {
		
		start=line.indexOf("=",start)+1;
		line=line.substring(start).trim();
		setExpectedValue(Double.valueOf((line.split(","))[0]).doubleValue());
		//System.out.println("eValue="+getExpectedValue());
	    }

    }
    
    /** Calcul de l'evalue de la matrice de substitution à partir des de la matrice 
     * du nombre de substitution observées dans la banque de donnees qui a permis de 
     * construire la matrice. Formule: somme des p(AAi)p(AAj)s(AAi,AAj). */
    
    public void calculExpectedValue(String observedMatrix)
    { 
    	double evalue=0.0;
    	int[][] matrix=new int[20][20];
    	int i,j;
    	for(i=0;i<20;i++)
    	{
    		for(j=0;j<20;j++) matrix[i][j]=0;
    	}
    	
    	Vector data=jet.io.file.FileIO.readFile(observedMatrix);
    	i=0;
    	while ((i<data.size())&&(((String)data.get(i)).charAt(0) == '#')) i++;
    	String[] line=((String)data.get(i)).trim().split("\\s+");
    	i++;
    	int[] index=new int[line.length];
    	double[] pAA=new double[line.length];
    	for(j=0;j<line.length;j++) 
	    {
    		if(line[j].compareTo("*")!=0)
    			index[j]=jet.data.datatype.Residue.getResidueIndex(line[j]);
	    }  	
    	
    	int row=0,col;
    	while (i<data.size())
    	{
    		line=((String)data.get(i)).trim().split("\\s+");
    		
			for(col=1;col<line.length;col++) 
			    matrix[index[row]][index[col-1]] = Integer.parseInt(line[col]);
			row++;
    		i++;
    	}
    	System.out.println("native hsdm");
    	for(i=0;i<20;i++)
    	{
    		for(j=0;j<20;j++) System.out.print(""+matrix[i][j]+"\t");
    		System.out.println("");
    	}
    	
    	double sommeij=0;
    	double sommei=0;
    	for(i=0;i<20;i++)
    	{
    		for(j=0;j<20;j++)
    		{
    			sommeij=sommeij+matrix[i][j];
    		}
    	}
    	
    	System.out.println("sommeij:"+sommeij);
    	
    	for(i=0;i<pAA.length;i++)
    	{
    		for(j=0;j<pAA.length;j++)
        	{
    			sommei=sommei+matrix[i][j]+matrix[j][i];
        	}
    		sommei=sommei-2*matrix[i][i];
    		System.out.println("somme"+i+":"+sommei);
    		pAA[i]=((matrix[i][i]+(sommei/2))/sommeij);
    		sommei=0;
    		
    	}  	
    	System.out.println("pAA");
    	for(i=0;i<20;i++) System.out.print(""+pAA[i]+"\t");
    	System.out.println("");
    	System.out.println("hdsm");
    	for(i=0;i<20;i++)
    	{
    		for(j=0;j<20;j++) System.out.print(""+subMatrix[i][j]+"\t");
    		System.out.println("");
    	}
    	
    	
    	sommeij=0;
    	int nb=0;
    	for(i=0;i<20;i++)
    	{
    		for(j=0;j<20;j++)
    			{
    			if (subMatrix[i][j]!=0)
    			{
    			sommeij=sommeij+subMatrix[i][j];
    			nb++;
    			}
    			}
    		
    	}
    	System.out.println("moyenne matrice hdsm:"+sommeij/nb);
    	
    	
    	
    	for(i=0;i<20;i++)
    		for(j=0;j<20;j++) evalue=evalue+subMatrix[i][j]*pAA[i]*pAA[j];
    	System.out.println("evalue:"+evalue);
    	setExpectedValue(evalue);
    	
    }
    
       
}

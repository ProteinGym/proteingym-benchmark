package jet.io.file;

import java.util.*;
import java.io.*;

/** Classe qui reprend le fichier pdb d'entree pour y intégrer les valeurs des resultats obtenus. 
 * Un suffixe "suffix" identifiant le type des resultats est ajouté au nom du fichier pdb d'entree.
 * Les valeurs des resultats remplacent les temperature des fichiers pdb. On obtient un fichier pour
 * toute la sequence, et un fichier pour chaque chaine de la séquence.
 * Remarque: Verifier la fonction transform(), passer cut en argument du constructeur, 
 * lancer tranformCut() et transform() en dehors du constructeur en fonction de cut. */

public class PdbFileTransformbis extends FileIO
{
	/** Valeurs résultats pour chaque residus */
    Vector values;
    /** Nom du fichier pdb */
    String fileName;
    /** Represente le type de resultats traités */
    String suffix="";
    /** Transformation du fichier pdb avec ou sans decoupage des chaines */
    boolean cut=false;
    
    int unite=RES;
    
    static int RES=1;
    static int ATOM=2;

    /***/
    /** CONSTRUCTEURS */
    /***/
    
    public PdbFileTransformbis(String fileName)
    {
	this.fileName=fileName;
	
    }
    
    public PdbFileTransformbis(String fileName, String suffix)
    {
	this.fileName=fileName;
	this.suffix=suffix;
    }
    
    
    public PdbFileTransformbis(String fileName, String suffix, Vector values)
    {
	this.fileName=fileName;
	this.values=values;
	this.suffix=suffix;
	if (cut) transformCut();
	else transform();
	
    }
    
    public PdbFileTransformbis(String fileName, String suffix, Vector values, int unite)
    {
	this.fileName=fileName;
	this.values=values;
	this.suffix=suffix;
	this.unite=unite;
	if (cut) transformCut();
	else transform();
	
    }
    
    /***/
    /** ACCESSEURS */
    /***/
    
    public String getFileName(){return fileName;}
    public int getUnite(){return unite;}
    
    public void setUnite(int unite){this.unite=unite;}
    
    /***/
    /** METHODES */
    /***/
    
    
    public boolean retire(Vector moleculesConserver)
    {
    	
    	if (moleculesConserver==null)
    	{
    	moleculesConserver=new Vector();
    	String pdbCode=fileName.substring(fileName.lastIndexOf("/")+1, fileName.lastIndexOf("/")+5);

    	moleculesConserver.addAll(jet.InterfaceAnalysis.selectionnerReactifsHeterodimer(pdbCode));
    	moleculesConserver.addAll(jet.InterfaceAnalysis.selectionnerReactifsHomodimer(pdbCode));
    	moleculesConserver.addAll(jet.InterfaceAnalysis.selectionnerReactifsTransient(pdbCode));
    	
    	
    	System.out.println("Enzyme compound for "+pdbCode+":");
    	for(int j=0;j<moleculesConserver.size();j++)
    	{
    		System.out.print(" "+(String)moleculesConserver.get(j));
    	}
    	System.out.println("");
    	}
    	
    	
    	
    	boolean transformed=false;
    	Vector pdbData=readFile(fileName);
    	
    	//Vector data;
    	int taille=pdbData.size();
    	String molecule="";
	    boolean retirer;
    	if ((moleculesConserver.size()==1)&&(((String)moleculesConserver.get(0)).equals("tout")))
    	{
    		for(int i=0;i<pdbData.size();i++)
	    	{
	    		
	    		if((((String)pdbData.get(i)).indexOf("HETATM")==0)&&(((String)pdbData.get(i)).substring(17,20).equals("HOH")))
	    		{
	    			pdbData.remove(i--);
	    		}
	    	}
    	}
    	else
    	{
    		/* Boucle sur chaque ligne du fichier pdb d'entree */
	    	for(int i=0;i<pdbData.size();i++)
	    	{
	    		
	    		if(((String)pdbData.get(i)).indexOf("HETATM")==0)
	    		{
	    			//System.out.println(""+pdbData.get(i));
	    			retirer=true;
	    			for(int j=0;j<moleculesConserver.size();j++)
	    	    	{
	    				molecule=(String)moleculesConserver.get(j);
	    				if (((String)pdbData.get(i)).substring(17,20).equals(molecule))
	    				{
	    					retirer=false;
	    					break;
	    				}
	    	    	}
	    			if (retirer)
			    	{
			    		//System.out.println("retire "+((String)pdbData.get(i)).substring(17,20));
			    		pdbData.remove(i--);
			    	}
	    		}
	    	}
    	}
    	
	    if (taille!=pdbData.size()) transformed=true;
	    if (transformed)
		{
	    	String outputFile;
	    	if (fileName.indexOf(this.suffix)==-1) outputFile=fileName.substring(0,fileName.lastIndexOf("."))+this.suffix+".pdb";
	    	else outputFile=fileName;
		    File file = new File (outputFile);
		    if (file.exists()) file.delete();
		    writeFile(outputFile,pdbData);
		    this.fileName=outputFile;
		}
	    return transformed;
    }
    /** Ajoute des numeros de chaine à des fichier PDB qui n'en possèdent pas et format le facteur de temperature */
    
    public boolean formatPDB()
    {
	String str;
	
	String chainCar="chaine de début hihi";
	int i,chainId=0;
	boolean transformed=false;
	Vector pdbData=readFile(fileName);
	
	/*
	
	Vector resMod=new Vector();
	boolean present;

	 */

	/* Boucle sur chaque ligne du fichier pdb d'entree */
	/* Pour la suite on suppose que les valeurs resultats correspondent au atomes du fichier d'entree,
	 * dans le meme ordre (peut etre ajouter un test sur le numero du residu). */
	for(i=0;i<pdbData.size();i++)
	    {
		
		/*
		
		if(((String)pdbData.get(i)).indexOf("MODRES")==0)
		{
			str=((String)pdbData.get(i)).substring(12,15);
			present = false;
			for (int z=0;z<resMod.size();z++) if (str.equals(((String)resMod.get(z)))) present=true;
			if (!present) resMod.add(str);
		}
		if(((String)pdbData.get(i)).indexOf("HETATM")==0)
		{
			
			
			present = false;
			str=((String)pdbData.get(i)).substring(17,20);
			for (int z=0;z<resMod.size();z++) if (str.equals(((String)resMod.get(z)))) present=true;
			if (present)
			{
				//System.out.print("res mod:");
				//for (int z=0;z<resMod.size();z++) System.out.print(" "+((String)resMod.get(z)));
				//System.out.println();
				//System.out.println("line:"+((String)pdbData.get(i)));
				str=((String)pdbData.get(i)).replaceFirst("HETATM", "ATOM  ");
				pdbData.setElementAt(str,i);
				//pdbData.remove(i);
				//i--;
				//continue;
				//System.out.println("modi:"+((String)pdbData.get(i)));
			}
		}
		
		*/
		
		if(((String)pdbData.get(i)).indexOf("ATOM")==0)
			/* On est a une ligne "ATOM". */
		    {			
			
			if (((String)pdbData.get(i)).length()<65) pdbData.setElementAt(((String)pdbData.get(i)).substring(0,60)+"   0.0",i);
			else pdbData.setElementAt(((String)pdbData.get(i)).substring(0,60)+"   0.0"+((String)pdbData.get(i)).substring(66,((String)pdbData.get(i)).trim().length()),i);
			transformed = true;
			
			if((!chainCar.equals("chaine de début hihi"))&&(chainCar.compareTo(((String)pdbData.get(i)).substring(21,22).trim())!=0))
				/* L'ID de la chaine change sans qu'il y ait eu de balise "TER" */
				{
				chainId++;
				}
			/* On recupere l'ID de la chaine */
			chainCar=((String)pdbData.get(i)).substring(21,22).trim();	
			if (chainCar.equals(""))
				{
				str=((String)pdbData.get(i)).substring(0,21)+chainId+((String)pdbData.get(i)).substring(22);
				pdbData.setElementAt(str.substring(0),i);
				//transformed=true;
				}
		    }	
		else if(((String)pdbData.get(i)).indexOf("TER")==0)
			{		
			
			if (chainCar.equals(""))
				{
				
				if (((String)pdbData.get(i)).length()==22)
					{
					
					str=((String)pdbData.get(i)).substring(0,21)+chainId;
					pdbData.setElementAt(str.substring(0),i);
					transformed=true;
					}
				else if (((String)pdbData.get(i)).length()>22)
					{
					
					str=((String)pdbData.get(i)).substring(0,21)+chainId+((String)pdbData.get(i)).substring(22);
					pdbData.setElementAt(str.substring(0),i);
					transformed=true;
					}
				}
			chainId++;
			}
	    }
		if (transformed)
		{
			String outputFile;
	    	if (fileName.indexOf(this.suffix)==-1) outputFile=fileName.substring(0,fileName.lastIndexOf("."))+this.suffix+".pdb";
	    	else outputFile=fileName;
		    File file = new File (outputFile);
		    if (file.exists()) file.delete();
		    writeFile(outputFile,pdbData);
		    this.fileName=outputFile;	
		}
		return transformed;
    }
    
    /** Autre version de la méthode transformCut. 
     * Cette methode ne coupe pas les resultats pour chaque chaine. */
    public void transform()
    {
    	String str;
    	int i,num,resNum=-1;
    	double temp=0.0;
    	String tempStr="";
    	Vector pdbData=readFile(fileName);
    	/* Iterateur sur chaque valeur du vecteur de resultats (concerne chaque residu de la sequence). */
    	Iterator val=values.iterator();
    	/* Boucle sur chaque ligne du fichier pdb d'entree */
    	/* Pour la suite on suppose que les valeurs resultats correspondent au atomes du fichier d'entree,
    	 * dans le meme ordre (peut etre ajouter un test sur le numero du residu). */
    	for(i=0;i<pdbData.size();i++)
    	    {
    		
    		if(((String)pdbData.get(i)).indexOf("ATOM")==0)
    			/* On est a une ligne "ATOM". */
    		    {
    			
	    			/* On recupere la position du residu */
	    			num=Integer.valueOf(((String)pdbData.get(i)).substring(22,26).trim()).intValue();
	    			if(num!=resNum)
	    				/* On change de residu dans le fichier pdb ==> On doit regarder
	    				 *  la valeur suivante dans le vecteur resultat */
	    			{
	    				resNum=num;
	    				if (unite==RES)
	        			{
		    				if(val.hasNext()) temp=((int)(((Double)val.next()).doubleValue()*1000)/1000.0);
		    				else temp=0.0;
	        			}
	    			}
	    			if (unite==ATOM)
	        		{
	        			if (((String)pdbData.get(i)).substring(26,27).trim().equals(""))
	        			{
	        				if(val.hasNext()) temp=((int)(((Double)val.next()).doubleValue()*1000)/1000.0);
	        				else temp=0.0;
	        			}
	        			else temp=0.0;
	        		}
    			
    			/* Remplacement de la temperature dans le fichier pdb par la valeur du resultat */
    			str=((String)pdbData.get(i)).substring(0,60);
    			tempStr=""+temp;
    			while (tempStr.length()<6) tempStr=" "+tempStr;
    			str+=tempStr;
    			if(str.length()<=66)
    				str=str.substring(0,str.length());
    			else
    				str=str.substring(0,66);
    			pdbData.setElementAt(str.substring(0),i);		
    		    }
    		/* Si c'est un terminateur de chaine alors rien a faire (ne pas supprimer) */
    		else if(((String)pdbData.get(i)).indexOf("TER")==0);
    		/* Elimination des lignes du fichier pdb d'entree qui ne correspondent pas aux atomes */
    		else if(((String)pdbData.get(i)).indexOf("HETATM")==0)
    		    {
    			pdbData.remove(i--);
    		    }
    		else if(((String)pdbData.get(i)).indexOf("CONECT")==0)
    		    {
    			pdbData.remove(i--);
    		    }
    		else /* Si autre chose on retire la ligne aussi */ 
    			pdbData.remove(i--);
    	    }
    	/* ecriture du fichier pdb pour toutes les chaines (sequence entiere) avec un nom 
    	 * appropprié fonction des resultats représentés */
    	writeFile(fileName.substring(0,fileName.lastIndexOf("."))+suffix+".pdb",pdbData);
	
    }
    
    /** Cette methode récupère les resultats sous forme de vecteur et ecrit les valeurs 
     * de ces resultats dans le fichier pdb à la place des temperatures des atomes. 
     * On obtient un fichier pour toute la sequence, et un fichier pour chaque chaine 
     * de la séquence. */
    
    public void transformCut()
    {
	String str;
	String chainCar="";
	int i,num,resNum=-1,chainId=0,start=0;
	double temp=0.0;
	String tempStr="";
	Vector pdbData=readFile(fileName),data;
	/* Iterateur sur chaque valeur du vecteur de resultats (concerne chaque residu de la sequence). */
	Iterator val=values.iterator();
	/* Boucle sur chaque ligne du fichier pdb d'entree */
	/* Pour la suite on suppose que les valeurs resultats correspondent au atomes du fichier d'entree,
	 * dans le meme ordre (peut etre ajouter un test sur le numero du residu). */
	for(i=0;i<pdbData.size();i++)
	    {
		
		if(((String)pdbData.get(i)).indexOf("ATOM")==0)
			/* On est a une ligne "ATOM". */
		    {			
			/* Pas besoin de cette partie car si pas de ter c'est que cela ne doit pas etre coupé
			 * mais de toute facon dans le traitement par JET le decoupage n'est pas fait sur "ter" 
			 * mais sur le changement de chaine */
			
			if((chainCar!="")&&(chainCar.compareTo(((String)pdbData.get(i)).substring(21,22))!=0)&&(start!=i))
				/* L'ID de la chaine change sans qu'il y ait eu de balise "TER" */
			{
				data=new Vector(pdbData.subList(start,i));
				//chainCar=((String)pdbData.get(i)).substring(21,22).trim();			
				writeFile(fileName.substring(0,fileName.lastIndexOf("."))+"_"+chainCar+suffix+".pdb",data);
				chainId++;
				start=i;
			}
			/* On recupere l'ID de la chaine */
			chainCar=((String)pdbData.get(i)).substring(21,22).trim();	
			
			num=Integer.valueOf(((String)pdbData.get(i)).substring(22,26).trim()).intValue();
			if(num!=resNum)
				/* On change de residu dans le fichier pdb ==> On doit regarder
				 *  la valeur suivante dans le vecteur resultat */
			{
				resNum=num;
				if (unite==RES)
    			{
    				if(val.hasNext()) temp=((int)(((Double)val.next()).doubleValue()*1000)/1000.0);
    				else temp=0.0;
    			}
			}
			if (unite==ATOM)
    		{
    			if (((String)pdbData.get(i)).substring(26,27).trim().equals(""))
    			{
    				if(val.hasNext()) temp=((int)(((Double)val.next()).doubleValue()*1000)/1000.0);
    				else temp=0.0;
    			}
    			else temp=0.0;
    		}
			
			/* Remplacement de la temperature dans le fichier pdb par la valeur du resultat */
			str=((String)pdbData.get(i)).substring(0,60);
			tempStr=""+temp;
			while (tempStr.length()<6) tempStr=" "+tempStr;
			str+=tempStr;
			if(str.length()<=66)
				str=str.substring(0,str.length());
			else
				str=str.substring(0,66);
			pdbData.setElementAt(str.substring(0),i);		
		    }
		/* Elimination des lignes du fichier pdb d'entree qui ne correspondent pas aux atomes */
		else if(((String)pdbData.get(i)).indexOf("HETATM")==0)
		    {
			pdbData.remove(i--);
		    }
		else if(((String)pdbData.get(i)).indexOf("CONECT")==0)
		    {
			pdbData.remove(i--);
		    }
		else if(((String)pdbData.get(i)).indexOf("TER")==0)
			{
			if ((((i+1<pdbData.size())&&((String)pdbData.get(i+1)).indexOf("ATOM")==0)||(chainId!=0)))
			/* Derniere ligne concernant les atomes de la chaine atteinte (il y a un "TER" à la 
			 * fin de chaque chaine) ==> ecriture du fichier pdb pour cette chaine avec un nom 
			 * appropprié fonction des resultats représentés si plusieurs chaines */
			
		    {
			data=new Vector(pdbData.subList(start,i+1));
			//chainCar=((String)pdbData.get(i)).substring(21,22).trim();
			if (chainCar.equals(""))
				writeFile(fileName.substring(0,fileName.lastIndexOf("."))+"_"+chainId+suffix+".pdb",data);
			else
				writeFile(fileName.substring(0,fileName.lastIndexOf("."))+"_"+chainCar+suffix+".pdb",data);
			chainId++;
			start=i+1;
		    }
			}
		else /* Si autre chose on retire la ligne aussi */ 
			pdbData.remove(i--);
	    }
	/* ecriture du fichier pdb pour toutes les chaines (sequence entiere) avec un nom 
	 * appropprié fonction des resultats représentés */
	writeFile(fileName.substring(0,fileName.lastIndexOf("."))+suffix+".pdb",pdbData);
	
    }
    
    public void cut(Vector chains)
    {
    	Vector chainTemp=new Vector();
    	int i=0;
    	boolean fin=false;
    	if (chains.size()>0)
    	{
	    	chainTemp.add(chains.elementAt(i));
	    	while ((!fin)&&(WriteChains(chainTemp,true)))
	    	{
	    		chainTemp.remove(0);
	    		i++;
	    		if (i<chains.size()) chainTemp.add(chains.elementAt(i));
	    		else fin=true;
	    	}
    	}
    	else
    	{
    		chainTemp.add(i);
	    	while (WriteChains(chainTemp,true))
	    	{
	    		chainTemp.remove(0);
	    		i++;
	    		chainTemp.add(i);
	    	}
    	}
    }
    
    /** Concatene les champs correspondant aux informations sur les coordonnées des atomes de deux fichier pdb. 
     * (placement des chaines fait en fonction des numeros de residus au niveau des "TER". */
    
    public void concat(jet.io.file.PdbFileTransform pdbft)
    {
    	
    	//System.out.println("debut concat");
    	Vector pdbData1=readFile(fileName);
    	Vector pdbData2=readFile(pdbft.getFileName());
    	Vector pdbDataFinal=new Vector(1,1);
    	Iterator iter1=pdbData1.iterator();
    	Iterator iter2=pdbData2.iterator();
    	String line1="",line2="";
    	//if (iter1.hasNext()) line1=(String)iter1.next();
    	while((iter1.hasNext())&&((line1=(String)iter1.next()).substring(0,3).compareTo("ATO")!=0))
    	{
    		pdbDataFinal.add(line1);
    	}
    	//if (iter2.hasNext()) line2=(String)iter2.next();
    	while((iter2.hasNext())&&((line2=(String)iter2.next()).substring(0,3).compareTo("ATO")!=0));
    	
    	int atomID1=0;
    	int atomID2=0;
    	while(iter1.hasNext()||iter2.hasNext())
	    {
    		//System.out.println("debut 1");
    		if (iter1.hasNext()) atomID1=Integer.valueOf(line1.substring(6,11).trim()).intValue();
    		else atomID1=1000000;
    		if (iter2.hasNext()) atomID2=Integer.valueOf(line2.substring(6,11).trim()).intValue();
    		else atomID2=1000000;
    		if (atomID1<atomID2)
    		{
    			pdbDataFinal.add(line1);
    			while((iter1.hasNext())&&((line1=(String)iter1.next()).substring(0,3).compareTo("ATO")==0))
    			{
    				//System.out.println("debut 11");
    				pdbDataFinal.add(line1);
    				//if (iter1.hasNext()) line1=(String)iter1.next();
    		    	//else line1="     ";
    				//System.out.println("fin 11");
    			}
    			if (line1.substring(0,3).compareTo("TER")==0) pdbDataFinal.add(line1);
    			else pdbDataFinal.add("TER "+((String)pdbDataFinal.lastElement()).substring(4,26));
    				
    			//if (iter1.hasNext()) line1=(String)iter1.next();
    		    while((iter1.hasNext())&&((line1=(String)iter1.next()).substring(0,3).compareTo("ATO")!=0))
    		    {
    		    	//System.out.println("debut 12");
    		    	//if (iter1.hasNext()) line1=(String)iter1.next();
    		    	//else line1="     ";
    		    	//System.out.println("fin 12");
    		   	}   			
    		}
    		else
    		{
    			pdbDataFinal.add(line2);
    			while((iter2.hasNext())&&((line2=(String)iter2.next()).substring(0,3).compareTo("ATO")==0))
    			{
    				//System.out.println("debut 21");
    				pdbDataFinal.add(line2);
    				//if (iter2.hasNext()) line2=(String)iter2.next();
    	    		//else line2="    ";
    				//System.out.println("fin 21");
    			}
    			if (line2.substring(0,3).compareTo("TER")==0) pdbDataFinal.add(line2);
    			else pdbDataFinal.add("TER "+((String)pdbDataFinal.lastElement()).substring(4,26));
    				
    			//if (iter2.hasNext()) line2=(String)iter2.next();
    			while((iter2.hasNext())&&((line2=(String)iter2.next()).substring(0,3).compareTo("ATO")!=0))
    		    {
    		    	//System.out.println("debut 22");
    		    	//if (iter2.hasNext()) line2=(String)iter2.next();
    		    	//else line2="     ";
    		    	//System.out.println("fin 22");
    		   	} 
    		}
    		//System.out.println("fin 1");
	    }
    	pdbDataFinal.add("END          ");
    	String outputFile=fileName.substring(0,fileName.lastIndexOf("/")+5)+this.suffix+".pdb";
    	File file = new File (outputFile);
    	if (file.exists()) file.delete();
    	writeFile(outputFile,pdbDataFinal);
    	this.fileName=outputFile;
    	//System.out.println("fin concat");
    	
    }
    
    /** Ecris dans un fichier pdb les chaines dont le numero est contenu dans le vecteur chains. Le vecteur doit etre trié 
     * par position croissante. */
    
    public boolean WriteChains(Vector chains,boolean printChainId)
    {
    	
    String chainCar="";
    String chainSuffix="_";
    //this.suffix=suffix;	
	int chainId=0;
	Iterator iter=chains.iterator();
	boolean all=(chains.size()==0)?true:false;
	int nextPrintedChain=(!all)?((iter.hasNext())?((Integer)iter.next()).intValue():-1):0;
	Vector pdbData=readFile(fileName);
	
	
	/* Boucle sur chaque ligne du fichier pdb d'entree */
	for(int i=0;i<pdbData.size();i++)
	{
		//System.out.println("i:"+i+" "+pdbData.get(i+1));
		if(
				(((String)pdbData.get(i)).indexOf("TER")==0)
				||(
						(((String)pdbData.get(i)).indexOf("ATOM")==0)
						&&(
								(i+1>=pdbData.size())
								||(((String)pdbData.get(i+1)).indexOf("END")==0)
								||((((String)pdbData.get(i)).substring(21,22).compareTo(((String)pdbData.get(i+1)).substring(21,22))!=0)
										//(((String)pdbData.get(i+1)).indexOf("ATOM")==0)
										//&&(((String)pdbData.get(i)).substring(21,22).compareTo(((String)pdbData.get(i+1)).substring(21,22))!=0)
								)
						)
				)
		)
		{
			
			if(chainId!=nextPrintedChain) pdbData.remove(i--);	
			else
			{
				nextPrintedChain=(!all)?((iter.hasNext())?((Integer)iter.next()).intValue():-1):(chainId+1);
				chainCar=((String)pdbData.get(i)).substring(21,22).trim();
				if (!chainCar.equals(""))
					chainSuffix=chainSuffix+chainCar;
				else
					chainSuffix=chainSuffix+chainId;
			}
			chainId++;
		}
		else
		{
			if((chainId!=nextPrintedChain)&&(((String)pdbData.get(i)).indexOf("ATOM")==0)) pdbData.remove(i--);
		}
	}
	
	if ((nextPrintedChain>=chainId)&&(!all)) return false;
		
	if (printChainId)
		writeFile(fileName.substring(0,fileName.lastIndexOf("."))+chainSuffix+this.suffix+".pdb",pdbData);
	else
		writeFile(fileName.substring(0,fileName.lastIndexOf("."))+this.suffix+".pdb",pdbData);
	
	return true;
    }
    
    
}

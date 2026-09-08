package jet;

import java.util.*;
import java.io.*;

/** Classe d'entrée du programme (main). Gère la lecture sans effort des paramètres passés au programme, 
 * la récupération des fichiers de structures, les analyses JET ainsi que les évaluations des résultats JET. */

public class JET
{
	public static boolean DEBUG = true;
	
	public static void main(String[] args) 
	{
		
			/* exemple ligne de commande */
			
			/*
			
			params= "-c default.conf " +
					"-l caracTest.jet " +
					"-i /home/stefan/projet_decrypton/structures/struc_in_test " +
					"-o /home/stefan/projet_decrypton/resultats_temp/ " +
					"-b /home/stefan/projet_decrypton/structures/blastFile1000serveur " +
					//"-f /home/stefan/projet_decrypton/structures/fastaFile " +
					"-p J " +
					"-r local " +
					"-n 1 " +
					"-s 0.49";
			
			args=params.split("\\s+");
			
			*/
		
		if (optionChecking(args))
		{
			args=optionPreproccessing(args);
			
			File file=null;
			File[] inputFiles=null;
			File[] inputFilesFormatted=null;	
			
			int input=-1,output=-1,jetOption=-1;
			boolean mergeOption=false;
			int nbIteration=1,threshold=-1;
			String nameColPdbFile = "";
			
			for (int j=0;j<args.length;j=j+2)
			{
				if (args[j].equals("-i"))
				{
					file=new File(args[j+1]);
					if(file.isDirectory()) inputFiles=file.listFiles();
					else
					{
						inputFiles=new File[1];
						inputFiles[0]=file;
					}
					input=j+1;
				}
				if (args[j].equals("-o")) output=j+1;
				if (args[j].equals("-p")) jetOption=j+1;
				if (args[j].equals("-t")) threshold=Integer.valueOf(args[j+1]).intValue();
				if (args[j].equals("-m"))
					if (args[j+1].equals("T"))
						mergeOption=true;
				if (args[j].equals("-n"))
					nbIteration=Integer.valueOf(args[j+1]).intValue();
				if (args[j].equals("-g"))
					nameColPdbFile=args[j+1].trim();
			}			
			
			if ((inputFiles==null)||(inputFiles.length<=0)) System.err.println("I/O error or no sequences for directory:"+args[input]);
			else
			{
				inputFilesFormatted=formatInputFile(mergeOption,inputFiles,args[output],null);
				
				String argJET=""+args[jetOption];
				if (nbIteration>1)
				{
					String argIterativeJET="";
					if (args[jetOption].indexOf("J")!=-1)
					{
						args[jetOption]=args[jetOption].replaceAll("J","");
						argIterativeJET=argIterativeJET+"J";
					}
					if (args[jetOption].indexOf("C")!=-1)
					{
						args[jetOption]=args[jetOption].replaceAll("C","");
						argIterativeJET=argIterativeJET+"C";
					}
					System.err.println(""+args[jetOption]);
					basicJET(args,inputFilesFormatted);
					args[jetOption]=argIterativeJET;
					System.err.println(""+args[jetOption]);
					iterativeJET(args, inputFilesFormatted, nbIteration);
					args[jetOption]=argJET;
				}
				else
					basicJET(args,inputFilesFormatted);
				
				if (threshold!=-1) 
					iterativeResAnalysis(inputFilesFormatted,threshold);
				
				if (args[jetOption].indexOf("R")!=-1)
				{
					System.out.println("***** Results analysis ******");
					manyPdbManyTestJet(args,inputFilesFormatted);
					System.out.println("***** End Results analysis ******");
				}
				
				Vector colNamesAuth = new Vector();
				
				if ((!nameColPdbFile.equals("")))
				{
					String[] tab = nameColPdbFile.split(",");
					for (int nb=0;nb<tab.length;nb++)
						colNamesAuth.add(tab[nb]);			
				}
				generatePDBResult(inputFilesFormatted, colNamesAuth);
				
			}
		}
		else System.err.println(""+usage());
	}

	/** Jet sans itération */
	
	public static void basicJET(String[] args, File[] inputFilesFormatted)
	{
		System.err.println("******** basic JET ***********");
		
		String[] paramsJet=new String[args.length];
		for (int i=0;i<paramsJet.length;i++) paramsJet[i]=args[i];
		
		int input=-1,output=-1,jetOption=-1,jetConfigFile=-1;
		
		for (int j=0;j<paramsJet.length;j++)
		{
			if (paramsJet[j].equals("-i")) input=j+1;
			if (paramsJet[j].equals("-o")) output=j+1;
			if (paramsJet[j].equals("-p")) jetOption=j+1;
			if (paramsJet[j].equals("-c")) jetConfigFile=j+1;
		}
		
		jet.ConfigFile configFile = new jet.ConfigFile(paramsJet[jetConfigFile]);
		
		configFile.setParam("Cluster", "namePcCol", "pc");
		configFile.setParam("Cluster", "nameTraceCol","trace");
		
		String pdb_code="";	
		
		for (int struc_i=0;struc_i<inputFilesFormatted.length;struc_i++)
		{
			paramsJet[input]=inputFilesFormatted[struc_i].getAbsolutePath();
			try{
				pdb_code=paramsJet[input].substring(paramsJet[input].lastIndexOf(File.separator)+1,paramsJet[input].lastIndexOf("."));
				paramsJet[output]=args[output]+File.separator+pdb_code;
				JET.run(paramsJet);
				concatResults(paramsJet[jetOption],paramsJet[input],paramsJet[output]);
			}catch (jet.exception.NaccessException exc)
			{System.err.println("no naccess results for the pdb file:"+paramsJet[input]);}
		}
		
	}

	/** Jet avec itération */
	
	public static void iterativeJET(String[] args, File[] inputFilesFormatted, int nbIteration)
	{
		System.err.println("******** Iterative JET ***********");
		
		String pdb_code="";	
		String[] paramsJet=new String[args.length];
		for (int i=0;i<paramsJet.length;i++) paramsJet[i]=args[i];
		
		int input=-1,output=-1,jetOption=-1,jetConfigFile=-1;
		int psiblastFormatOption=-1;
		
		for (int j=0;j<paramsJet.length;j++)
		{
			if (paramsJet[j].equals("-i")) input=j+1;
			if (paramsJet[j].equals("-o")) output=j+1;
			if (paramsJet[j].equals("-p")) jetOption=j+1;
			if (paramsJet[j].equals("-c")) jetConfigFile=j+1;
			if (paramsJet[j].equals("-b")) psiblastFormatOption=j+1;
		}
		
		jet.ConfigFile configFile = new jet.ConfigFile(paramsJet[jetConfigFile]);
		
		String retrievingMethod=configFile.getParam("SequenceRetrieving", "method");
		
		configFile.setParam("Cluster", "namePcCol", "pc");
		
		File jetResFile;
		Vector jetResults;
		Vector nameJetResults;
		String[] nameOfAnalysis;
		if (paramsJet[jetOption].indexOf("J")!=-1)
		{
			if (paramsJet[jetOption].indexOf("C")!=-1)
			{
				nameOfAnalysis=new String[3];
				nameOfAnalysis[0]="trace";
				nameOfAnalysis[1]="clusters";
				nameOfAnalysis[2]="clusnumber";
			}
			else
			{
				nameOfAnalysis=new String[1];
				nameOfAnalysis[0]="trace";
			}
			configFile.setParam("Cluster", "nameTraceCol",nameOfAnalysis[0]);
		}
		else
		{
			if (paramsJet[jetOption].indexOf("C")!=-1)
			{
				nameOfAnalysis=new String[2];
				nameOfAnalysis[0]="clusters";
				nameOfAnalysis[1]="clusnumber";
			}
			else nameOfAnalysis=new String[0];
		}
	
		Vector[] allValues=new Vector[nameOfAnalysis.length];
		
		int numCol;
		
		for (int struc_i=0;struc_i<inputFilesFormatted.length;struc_i++)
		{
			for (int j=0;j<nameOfAnalysis.length;j++) allValues[j]= new Vector();
			paramsJet[input]=inputFilesFormatted[struc_i].getAbsolutePath();
			configFile.setParam("SequenceRetrieving", "method", retrievingMethod);
			try
			{
				pdb_code=paramsJet[input].substring(paramsJet[input].lastIndexOf(File.separator)+1,paramsJet[input].lastIndexOf("."));
				paramsJet[output]=args[output]+File.separator+pdb_code;
				
				if (psiblastFormatOption==-1)
				{
					String[] paramsJetTemp=new String[paramsJet.length];
					int h;
					for (h=0;h<paramsJet.length;h++) paramsJetTemp[h]=paramsJet[h];
					paramsJet=new String[paramsJet.length+2];
					for (h=0;h<paramsJetTemp.length;h++) paramsJet[h]=paramsJetTemp[h];
					paramsJet[h]="-b";
					paramsJet[h+1]="";
					psiblastFormatOption = h+1;
				}
				paramsJet[psiblastFormatOption]=paramsJet[output];
						
				for (int i=0;i<nbIteration;i++)
				{
					if ((paramsJet[jetOption].indexOf("J")==-1)&&(paramsJet[jetOption].indexOf("C")!=-1)) configFile.setParam("Cluster", "nameTraceCol", "trace"+i);
					if ((paramsJet[jetOption].indexOf("J")!=-1)&&(paramsJet[jetOption].indexOf("C")!=-1)) configFile.setParam("Cluster", "nameTraceCol", "trace");
					
					if (!retrievingMethod.equals("input"))
					{
						if (i==1)
						{
							configFile.setParam("SequenceRetrieving", "method", "input");
							configFile.setParam("SequenceRetrieving", "format", "psiblast");
						}
					}
					JET.run(paramsJet);
					jetResFile=concatResults(paramsJet[jetOption],paramsJet[input],paramsJet[output]);
					if (jetResFile.exists())
					{
						jetResults=Result.readValuesResult(jetResFile.getAbsolutePath());
						nameJetResults=Result.readCaracResult(jetResFile.getAbsolutePath());
							
						for (int j=0;j<nameOfAnalysis.length;j++)
						{
							numCol=Result.searchNumCol(nameJetResults,nameOfAnalysis[j]);
							if (numCol!=-1) allValues[j].add(jetResults.get(numCol));
						}
					}
				}		
			}catch (jet.exception.NaccessException exc)
			{System.err.println("no naccess results for the pdb file:"+paramsJet[input]);}
		
			jetResFile=new File(paramsJet[output]+File.separator+paramsJet[input].substring(paramsJet[input].lastIndexOf(File.separator)+1,paramsJet[input].lastIndexOf(".pdb"))+"_jet.res");			
			jetResults=Result.readValuesResult(jetResFile.getAbsolutePath());
			nameJetResults=Result.readCaracResult(jetResFile.getAbsolutePath());
			
			Vector numberOfValues;
			Vector maxOfValues;
			double sum,max;
			for (int k=0;k<nameOfAnalysis.length;k++)
			{
				numberOfValues=new Vector();
				maxOfValues=new Vector();
				
				if (allValues[k].size()>0)
				{
					for (int i=0;i<((Vector)allValues[k].get(0)).size();i++)
					{
						sum=0.0;
						max=0.0;
						for (int j=0;j<allValues[k].size();j++)
						{
							if (Double.parseDouble((String)((Vector)allValues[k].get(j)).get(i))>0.0) sum=sum+1.0;
							if (Double.parseDouble((String)((Vector)allValues[k].get(j)).get(i))>max) max=Double.parseDouble((String)((Vector)allValues[k].get(j)).get(i));
						}
						numberOfValues.add(sum/nbIteration);
						maxOfValues.add(max);
					}
				}
			
				Vector numCols=Result.searchNumCols(nameJetResults, nameOfAnalysis[k]);
				numCols.removeAll(Result.searchNumCols(nameJetResults, nameOfAnalysis[k]+"Ic"));
				for (int j=0;j<numCols.size();j++)
				{
					Result.removeCol(jetResults,((Integer)numCols.get(j)).intValue()-j);
					nameJetResults.remove(((Integer)numCols.get(j)).intValue()-j);
				}

				
				for (int j=0;j<allValues[k].size();j++)
				{
					Result.addCol(jetResults, ((Vector)allValues[k].get(j)),jetResults.size());
					nameJetResults.add(nameOfAnalysis[k]+j);	
				}
	
				if (nameOfAnalysis[k].equals("trace"))
				{
					Result.addCol(jetResults, maxOfValues,jetResults.size());
					nameJetResults.add(nameOfAnalysis[k]+"Max");
				}
				if (nameOfAnalysis[k].equals("clusters"))
				{
					Result.addCol(jetResults, numberOfValues,jetResults.size());
					nameJetResults.add(nameOfAnalysis[k]+"Occur");
				}
				
			}
			Result.WriteResult(jetResults, nameJetResults, jetResFile.getAbsolutePath());
		}
	}
	
	/** Analyse des resultats Jet avec itération : Ecriture des scores dans la colonne clusters 
	 * du fichier résultat de JET pour les résidus apparaissant 
	 * un nombre de fois supérieur ou égal à threshold */

	public static void iterativeResAnalysis(File[] inputFilesFormatted,int threshold)
	{
		Vector jetResults;
		Vector nameJetResults;
		Vector clustersValues=new Vector();
		
		for (int i=0;i<inputFilesFormatted.length;i++)
		{
			String nameFile=inputFilesFormatted[i].getAbsolutePath();
			File jetResFile=new File(nameFile.substring(0,nameFile.lastIndexOf(".pdb"))+"_jet.res");
			jetResults=Result.readValuesResult(jetResFile.getAbsolutePath());
			nameJetResults=Result.readCaracResult(jetResFile.getAbsolutePath());
			
			int numCol=Result.searchNumCol(nameJetResults,"clusters");
			Result.removeCol(jetResults, numCol);
			Result.removeCol(nameJetResults, numCol);
			
			Vector cols=Result.searchNumCols(nameJetResults,"clusters");
			cols.removeAll(Result.searchNumCols(nameJetResults,"clustersOccur"));
			numCol=Result.searchNumCol(nameJetResults,"clustersOccur");
			if (numCol!=-1)
			{
				Vector occurs=(Vector)jetResults.get(numCol);
				if (threshold>cols.size())
				{
					System.err.println("incorrect value "+threshold+" of option -t for jet results file: "+jetResFile.getAbsolutePath());
					threshold=cols.size();
					System.err.println("new value fixed to: "+threshold);
				}
				if (cols.size()>1)
				{
					clustersValues.clear();
					for (int k=0;k<occurs.size();k++)
					{
						if (Double.parseDouble((String)occurs.get(k))>=threshold) clustersValues.add(Double.parseDouble((String)occurs.get(k)));
						else clustersValues.add(0.0);
								
					}
					Result.addCol(jetResults, clustersValues,jetResults.size());
					nameJetResults.add("clusters");
					Result.WriteResult(jetResults, nameJetResults, jetResFile.getAbsolutePath());
				}
				else 
					System.err.println("no iterative JET results in file: "+jetResFile.getAbsolutePath());
			}
			else System.err.println("missing column containing number of occurrence for a residue (clustersOccur column) in jet results file: "+jetResFile.getAbsolutePath());
		}
	}

	/** Initialisation (en fonction des paramètres d'entrée du programme) des variables et lancement 
	 * des différents algorithmes JET. */
	
	public static void run(String[] args) throws jet.exception.NaccessException
	{
	int i;
	
	File outputFile=null,pdbFile=null,alignFile=null;
	String pdbCode="",pdbPath="",outputPath="",alignPath="",programs="";
	
	String alignFileType="";
	
	jet.ConfigFile configFile=null;
	jet.ConfigFile logFile=null;
	
	/* Récupération des arguments: option -w pour un code pdb, -i pour l'input et -o pour l'output */ 
	
	for (int j=0; j<args.length;j=j+2)
	{
		switch(args[j].charAt(1))
	    {
		    case 'i': /* input pdb file */
			{ 
				pdbPath=args[j+1];
				break; 
			}
		    case 'w': /* code pdb */
			{ 
				pdbCode=args[j+1];
				break;	
			}
		    case 'o': /* output pdb file */
			{ 
				outputPath=args[j+1];
				break;
			}  
		    case 'b': /* input align file */
		    {
		    	
		    	alignPath=args[j+1];
		    	break;
		    }
		    case 'f': /* input align file */
		    {
		    	alignPath=args[j+1];
		    	break;
		    }
		    case 'c': /* input config file */
		    {
		    	configFile=new jet.ConfigFile(args[j+1]);
		    	break;
		    }
		    case 'l': /* input log file */
		    {
		    	logFile=new jet.ConfigFile(args[j+1]);
		    	break;
		    }
		    case 'p': /* JET algorithm option */
		    {
		    	programs=args[j+1];
		    	break;
		    }
	    }
	}
	
	/* Création du fichier de structure en entrée à partir de l'input */
	
	if(pdbPath.length()>0) 
	    {
		pdbFile=new File(pdbPath);
		if(!pdbFile.exists()) 
		    {
			System.err.println("Input path : "+pdbPath+" doesn't exists");
			System.exit(1);
		    }
	    }
	
	/* Création du fichier d'output */
	
	if(outputPath.length()>0) 
	    {
		outputFile=new File(outputPath);
		if(!outputFile.isDirectory()) 
		    {
			System.err.println("Output path : "+outputPath+" doesn't exists !");
			System.err.println("Creating output path: "+outputPath);
			outputFile.mkdir();
		    }
	    }
	
	/* Si pas d'output on récupère le répertoire d'input */
	
	else 
	    {
		if(pdbFile!=null)
		    {
			if(pdbFile.isDirectory()) outputFile=new File(pdbFile.getPath());
			else if(pdbFile.isFile()) outputFile=pdbFile.getParentFile();
		    }
		else 
		    {
			System.err.println("Output path : "+outputPath+" missing!");
			System.exit(1);
		    }
	    }
	
	/* Si pas d'input (option -i) on récupère la structure par un code pdb (option -w) */
	
	if(pdbFile==null)
	    {
		if(pdbCode.length()==4)
		    {
			pdbFile=fetchPdbFile(configFile,pdbCode,outputFile);
			if(!pdbFile.exists())
			    {
				System.err.println("Incorrect pdb code"); 
				System.exit(1);
			    }
		    }
		else 
		    { 
			System.err.println("Incorrect pdb code"); 
			System.exit(1);
		    }
	    }
	
	/* Lecture du fichier de structure pdb */	
	pdbFile = readFile(pdbFile,outputFile);

	
	/* Recupération des fichier d'alignement correspondant au code pdb du fichier pdb. */	
	alignFileType=configFile.getParam("SequenceRetrieving", "format");
	File[] alignFileList=null;
	if(alignPath.length()>0)
	{ 
		Vector alignFileFiltered=new Vector();
		alignFile=new File(alignPath); 
		if(!alignFile.exists()) 
		{
		System.err.println("Input path : "+alignPath+" doesn't exists");
		System.exit(1);
		} 
		if(alignFile.isDirectory()) alignFileList = alignFile.listFiles();	
		else
		{
			alignFileList= new File[1];
			alignFileList[0]=alignFile;
		}
	
		String pdb_code;
		boolean exist=false;
		
		exist=false;
		pdb_code=pdbFile.getAbsolutePath();
		pdb_code=pdb_code.substring(pdb_code.lastIndexOf(File.separator)+1,pdb_code.lastIndexOf("."));			
		for (i=0;i<alignFileList.length;i++)
		{			
			if ((alignFileList[i].getAbsolutePath().lastIndexOf(pdb_code)!=-1)&&(alignFileList[i].getAbsolutePath().lastIndexOf("."+alignFileType)!=-1 ))
			{
				if(!alignFileList[i].isDirectory())
				{
					alignFileFiltered.add(readFile(alignFileList[i],outputFile));
					exist=true;
				}
			}
		}
		if (!exist) System.err.println("no align file for pdb file : "+pdbFile.getAbsolutePath());
		
		if (alignFileFiltered.size()>0) alignFileList=new File[alignFileFiltered.size()];
		else alignFileList=null;
		for(i=0;i<alignFileFiltered.size();i++) alignFileList[i]=(File)alignFileFiltered.get(i);
		
	}
	
	/* Lancements des analyses dans le bon ordre */
	
	if (programs.indexOf('A')!=-1)
	{
		System.out.println("***** Access analysis ******");
		AccessAnalysis jet=new AccessAnalysis(configFile);
		jet.analyse(pdbFile);
		System.out.println("***** End Access analysis ******");
	}
	
	if (programs.indexOf('I')!=-1)
	{
		System.out.println("***** Interface analysis ******");
		InterfaceAnalysis jet=new InterfaceAnalysis(configFile,logFile);
		jet.analyse(pdbFile);
		System.out.println("***** End Interface analysis ******");
	}
	
	if (programs.indexOf('V')!=-1)
	{
		System.out.println("***** CV analysis ******");
		CVAnalysis jet=new CVAnalysis(configFile,false);
		jet.analyse(pdbFile);
		System.out.println("***** End CV analysis ******");
	}

	if (programs.indexOf('J')!=-1)
	{
		System.out.println("***** JET analysis ******");
		JetAnalysis jet=new JetAnalysis(configFile,logFile);
		jet.analyse(pdbFile,alignFileList);
		System.out.println("***** End JET analysis ******");
	}
	
	if (programs.indexOf('C')!=-1)
	{
		System.out.println("***** Cluster analysis ******");
		ClusterAnalysis jet=new ClusterAnalysis(configFile);
		jet.analyse(pdbFile);	
		System.out.println("***** End Cluster analysis ******");
	}
	
	if (programs.indexOf('E')!=-1)
	{
		System.out.println("***** ET analysis ******");
		ETResultAnalysis jet=new ETResultAnalysis();
		jet.analyse(pdbFile);	
		System.out.println("***** End ET analysis ******");
	}
	}

	/** Récupération sur le web d'un fichier pdb à partir d'un code pdb **/

    public static File fetchPdbFile(jet.ConfigFile configFile, String pdbCode, File outputPath)
    {
		File file=new File(outputPath,pdbCode+".pdb");
		jet.ProgressIndicator progress=new jet.ProgressIndicator("PDB");
		progress.setStatus("Retrieve structure "+pdbCode);
		new Thread(progress).start();
		
		/* initialisation client web */
		String url=configFile.getParam("PDB","url");
		System.out.println(url);
		jet.io.net.PdbCodeClient pdb=new jet.io.net.PdbCodeClient(url);
		pdb.setPDBCode(pdbCode);
		pdb.sendCommand();
	
		/* Ecriture de la structure sur le fichier de sortie */
	
		jet.io.file.FileIO.writeFile(file.getPath(),pdb.getData(),false);
	
		progress.stop();
		return file;
    }

    /** Lecture du fichier d'entrée et écriture sur le répertoire de sortie */

    public static File readFile(File pdbPath, File outputPath)
    {

		if(!pdbPath.getParentFile().equals(outputPath)) 
		    {
			/* Copie des fichiers d'entrée sur la sortie */
			jet.io.file.FileIO.writeFile(new File(outputPath,pdbPath.getName()).getPath(),jet.io.file.FileIO.readFile(pdbPath.getPath()),false);
		    } 	
		
		return new File(outputPath,pdbPath.getName());
	    
    }
    
    /** Formatage des fichiers inputFiles (concaténés, nettoyés et copiés)*/
    
    public static File[] formatInputFile(boolean merge, File[] inputFiles, String outputPath, Vector moleculesConserver)
	{
    	System.out.println("***** Format Files ******");
		File[] inputFilesFormated=null;
		File[] inputFilesFinal=null;
		
		if ((inputFiles.length>1)&&(merge))
			inputFilesFormated=boundPDBFile(inputFiles);
		else inputFilesFormated=inputFiles;
		
		inputFilesFormated=parsePDBFile(inputFilesFormated,moleculesConserver);
		inputFilesFinal=copyPDBFile(inputFilesFormated, outputPath);
		removePDBFile(inputFilesFormated, "_formatted");
		System.out.println("***** End Format Files ******");
		return inputFilesFinal;
	}
	
    /** Nettoyage des différentes molécules n'apparaissant pas dans moleculesConserver
     * et mise en conformité avec le format pdb des fichiers inputFiles.*/
    
	public static File[] parsePDBFile(File[] inputFiles, Vector moleculesConserver)
	{
		File[] inputFilesFinal=null;
		Vector inputFilesParsed=new Vector(1,1);
		jet.io.file.PdbFileTransform pdbft=null;
		String pdbPath="";
		boolean transformed=false;
		for (int i=0;i<inputFiles.length;i++)
		{
			transformed=false;
			pdbPath=inputFiles[i].getAbsolutePath();
			pdbft= new jet.io.file.PdbFileTransform(pdbPath,"_formatted");
			transformed=pdbft.formatPDB();
			transformed=pdbft.retire(moleculesConserver)||transformed;
			
			if (transformed)
			{
				inputFilesParsed.add(new File(pdbft.getFileName()));
				inputFiles[i]=null;
			}
		}
		for (int i=0;i<inputFiles.length;i++)
		{
			if (inputFiles[i]!=null) inputFilesParsed.add(inputFiles[i]);
		}
		inputFilesFinal=new File[inputFilesParsed.size()];
		for (int i=0;i<inputFilesFinal.length;i++) inputFilesFinal[i]=(File)inputFilesParsed.get(i);
	
		return inputFilesFinal;
	}
	
	 /** Concaténation des fichiers inputFiles possédant le même code pdb 
	  * (4 premières lettre du nom de fichier).*/
	
	public static File[] boundPDBFile(File[] inputFiles)
	{
		Vector inputFilesBounded=new Vector(1,1);
		File[] inputFilesFinal=null;
		String pdbCode="";
		String pdbCodeTemp="";
		String pdbPath="";
		String pdbPathTemp="";
		jet.io.file.PdbFileTransform pdbft=null;
		jet.io.file.PdbFileTransform pdbftTemp=null;
		boolean transformed=false;
		for (int i=0;i<inputFiles.length;i++)
		{
			if (inputFiles[i]!=null)
			{
				transformed=false;
				pdbPath=inputFiles[i].getAbsolutePath();
				pdbCode=pdbPath.substring(pdbPath.lastIndexOf(File.separator)+1,pdbPath.lastIndexOf("."));
				pdbft= new jet.io.file.PdbFileTransform(pdbPath,"_formatted");
				for (int j=(i+1);j<inputFiles.length;j++)
				{
					if (inputFiles[j]!=null)
					{
						pdbPathTemp=inputFiles[j].getAbsolutePath();
						pdbCodeTemp=pdbPathTemp.substring(pdbPathTemp.lastIndexOf(File.separator)+1,pdbPath.lastIndexOf("."));
						
						pdbftTemp=new jet.io.file.PdbFileTransform(pdbPathTemp);
						if (pdbCode.equals(pdbCodeTemp))
						{
							transformed=true;
							pdbft.concat(pdbftTemp);
							inputFiles[j]=null;
							
						}
					}
				}
				if (transformed)
				{
					inputFilesBounded.add(new File(pdbft.getFileName()));
					inputFiles[i]=null;
				}
			}
		}
		
		for (int i=0;i<inputFiles.length;i++)
		{
			if (inputFiles[i]!=null) inputFilesBounded.add(inputFiles[i]);
		}
		inputFilesFinal=new File[inputFilesBounded.size()];
		for (int i=0;i<inputFilesFinal.length;i++) inputFilesFinal[i]=(File)inputFilesBounded.get(i);

		return inputFilesFinal;
	}
	
	/** Ecriture sur la sortie outputPath/pdbCode des fichiers inputFiles.*/
	
	public static File[] copyPDBFile(File[] inputFiles, String outputPath)
	{
		File[] inputFilesFinal=null;
		Vector inputFilesCopied=new Vector(1,1);
		String pdbCode="";
		String pdbPath="";
		String pdbName="";
		File outputFile;
		
		for (int i=0;i<inputFiles.length;i++)
		{
			if (inputFiles[i]!=null)
			{
				pdbPath=(inputFiles[i]).getAbsolutePath();
				pdbCode=pdbPath.substring(pdbPath.lastIndexOf(File.separator)+1,pdbPath.lastIndexOf("."));
				pdbCode=pdbCode.replaceAll("_formatted", "");
				pdbName=pdbPath.substring(pdbPath.lastIndexOf(File.separator)+1,pdbPath.lastIndexOf(".pdb"));
				pdbName=pdbName.replaceAll("_formatted", "");
				
				System.out.println("copy file:"+outputPath+File.separator+pdbCode+File.separator+pdbName+".pdb");
				jet.io.file.FileIO.writeFile(outputPath+File.separator+pdbCode+File.separator+pdbName+".pdb",jet.io.file.FileIO.readFile(pdbPath),false);
				inputFilesCopied.add(new File(outputPath+File.separator+pdbCode+File.separator+pdbName+".pdb"));
			}
		}
		inputFilesFinal=new File[inputFilesCopied.size()];
		for (int i=0;i<inputFilesFinal.length;i++) inputFilesFinal[i]=(File)inputFilesCopied.get(i);
		
		return inputFilesFinal;
	}
	
	/** Retire de la liste inputFiles les fichiers dont le nom contient suffix.*/
	
	public static void removePDBFile(File[] inputFiles, String suffix)
	{
		for (int i=0;i<inputFiles.length;i++)
			if ((inputFiles[i]!=null)&&(inputFiles[i].getAbsolutePath().lastIndexOf(suffix)!=-1))
				inputFiles[i].delete();		
	}
	
	/** Lancement et récupération de l'analyse les résultats contenu dans la colonne nameColAnalysed 
	 * du fichier résultats de JET 
	 * obtenu à partir du fichier pdb inputPath et contenu dans le repertoire outputPath. 
	 * coverageOfAnalysis correspond à la couverture des résidus que l'on considère positifs.
	 * seuilScore correspond au score à partir duquel les résidus sont considérés comme positifs.
	 * .*/
	
	public static Vector[] analyseJetResults(String inputPath, String outputPath, String nameColAnalysed, double coverageOfAnalysis, double seuilScore) throws jet.exception.NaccessException
	{
		
		String pdb_code=inputPath.substring(inputPath.lastIndexOf(File.separator)+1,inputPath.lastIndexOf(".pdb"));
		String filename=inputPath.substring(0,inputPath.lastIndexOf("."));
		String output_directory=outputPath+File.separator;
		
		jet.ConfigFile caracTestFile = new jet.ConfigFile("caracTest.jet");
		int protSize;
		
		File outputFile=null;
		File[] outputFiles=null;
		
		if(output_directory.length()>0) 
	    {
			outputFile=new File(output_directory);
			if(outputFile.isDirectory())
			{
				jet.io.file.PdbFileTransform pdbft;
				if ((new File(filename+"_jet.res").exists())&&(!Result.convertResultToPDB(filename+"_jet.res", inputPath, nameColAnalysed,1)))
					if ((new File(filename+"_et.res").exists())&&(!Result.convertResultToPDB(filename+"_et.res", inputPath, nameColAnalysed,1)))
						System.err.println("pas de colonne "+nameColAnalysed+" dans les fichiers "+filename+"_et.res et "+filename+"_jet.res");
				pdbft= new jet.io.file.PdbFileTransform(filename+"_"+nameColAnalysed+".pdb");
				pdbft.cut(new Vector(),true);
				
				outputFiles=outputFile.listFiles();
			}
	    }
		
		boolean plusieurs_resultats=false;
		
		String[] paramsTest=new String[5];
		
		String chaine=""; 
		int extPos=-1;
		int chainPos=-1;
		
		int nbRes=0;		
		
		Vector result=new Vector();
		Vector lines=new Vector();
		Vector lineTemp=new Vector();
		Vector allResult=new Vector();
		
		if (outputFiles!=null)
		{
			plusieurs_resultats=false;
			for (int i=0;i<outputFiles.length;i++)
			{
				if (outputFiles[i].getAbsolutePath().lastIndexOf(pdb_code+"_"+nameColAnalysed+"_")!=-1 )
				{
					plusieurs_resultats=true;
					
					paramsTest[0]=outputFiles[i].getAbsolutePath();
					extPos=outputFiles[i].getAbsolutePath().lastIndexOf(".pdb");
					chainPos=outputFiles[i].getAbsolutePath().lastIndexOf("_")+1;
					chaine=outputFiles[i].getAbsolutePath().substring(chainPos,extPos);
					
					paramsTest[1]=output_directory+pdb_code+"_inter_"+chaine+".pdb";
					if (new File(paramsTest[1]).exists())
					{
						paramsTest[2]=output_directory+pdb_code+"_axs_"+chaine+".pdb";
						paramsTest[3]=output_directory+pdb_code+"_eval_"+nameColAnalysed+"_"+chaine+".res";
						paramsTest[4]=String.valueOf(seuilScore);
						nbRes++;
						result=TestMethods.main(paramsTest);
						lineTemp=Result.searchLine(Result.searchNumLine(0, coverageOfAnalysis, result),result);
						lineTemp.add(0, ""+pdb_code.substring(0,4)+":"+chaine);
						protSize=(int)caracTestFile.getDoubleParam(""+lineTemp.get(0),"size");
						lineTemp.add(1, protSize);
						Result.addLine(lines,lineTemp);
						System.out.print("sequence "+pdb_code+" "+chaine+": ");
						System.out.println("accuracy "+nameColAnalysed+":"+Result.searchValue(8, Result.searchNumLine(0, coverageOfAnalysis,result),result));
						Result.sumResult(allResult,result);
						
					}
				}
			}
			if (!plusieurs_resultats)
			{
				for (int i=0;i<outputFiles.length;i++)
				{
					if (outputFiles[i].getAbsolutePath().lastIndexOf(pdb_code+"_"+nameColAnalysed+".pdb")!=-1 )
					{
						paramsTest[0]=outputFiles[i].getAbsolutePath();
						paramsTest[1]=output_directory+pdb_code+"_inter.pdb";
						if (new File(paramsTest[1]).exists())
						{
							paramsTest[2]=output_directory+pdb_code+"_axs.pdb";
							paramsTest[3]=output_directory+pdb_code+"_eval_"+nameColAnalysed+".res";
							paramsTest[4]=String.valueOf(seuilScore);
							nbRes++;
							result=TestMethods.main(paramsTest);
							lineTemp=Result.searchLine(Result.searchNumLine(0, coverageOfAnalysis, result),result);
							lineTemp.add(0, ""+pdb_code.substring(0,4));
							protSize=(int)caracTestFile.getDoubleParam(""+lineTemp.get(0),"size");
							lineTemp.add(1, protSize);
							Result.addLine(lines,lineTemp);
							System.out.print("sequence "+pdb_code+": ");
							System.out.println("accuracy "+nameColAnalysed+":"+Result.searchValue(8, Result.searchNumLine(0, coverageOfAnalysis,result),result));
							Result.sumResult(allResult,result);
						}
					}
				}
			}
			
			Result.dividResult(allResult,nbRes);
		}
		
		Vector[] sortie=new Vector[2];
		sortie[0]=allResult;
		sortie[1]=lines;
		
		return sortie;
	}
	
	/** Concaténation des résultats de jet, d'accessibilité, de clusterisation et d'interface. */
	
	public static File concatResults(String paramsAnalysis, String pdbFile, String directoryResults)
	{

		String pdb_code=pdbFile.substring(pdbFile.lastIndexOf(File.separator)+1,pdbFile.lastIndexOf(".pdb"));
		String output_directory=directoryResults+File.separator;
		File jetResFile=new File(output_directory+pdb_code+"_jet.res");
		
		if (jetResFile.exists())
		{
			
			Vector jetResults=Result.readValuesResult(output_directory+pdb_code+"_jet.res");
			Vector nameJetResults=Result.readCaracResult(output_directory+pdb_code+"_jet.res");
			
			boolean transformed=false;
			int numCol;
			Vector numCols;
			if (paramsAnalysis.indexOf('A')!=-1)
			{
				Vector axsResults=Result.readValuesResult(output_directory+pdb_code+"_axs.res");
				Vector nameAxsResults=Result.readCaracResult(output_directory+pdb_code+"_axs.res");
				numCol=Result.searchNumCol(nameJetResults,"axs");
				if (Result.searchNumCol(nameAxsResults,"axs")!=-1)
				{
					if (numCol==-1)
					{
						Result.addCol(jetResults, (Vector)axsResults.get(Result.searchNumCol(nameAxsResults,"axs")),jetResults.size());
						nameJetResults.add("axs");
					}
					else
					{
						Result.removeCol(jetResults,numCol);
						Result.addCol(jetResults, (Vector)axsResults.get(Result.searchNumCol(nameAxsResults,"axs")),numCol);
					}
					
					transformed=true;
				}
				
			}
			if (paramsAnalysis.indexOf('C')!=-1)
			{
				Vector clusterResults=Result.readValuesResult(output_directory+pdb_code+"_clusters.res");
				Vector nameClusterResults=Result.readCaracResult(output_directory+pdb_code+"_clusters.res");
				
				numCols=Result.searchNumCols(nameJetResults, "clusters");
				
				for (int j=0;j<numCols.size();j++)
				{
					Result.removeCol(jetResults,((Integer)numCols.get(j)).intValue()-j);
					nameJetResults.remove(((Integer)numCols.get(j)).intValue()-j);
				}
				numCols=Result.searchNumCols(nameClusterResults, "clusters");
				for (int j=0;j<numCols.size();j++)
				{
					Result.addCol(jetResults, (Vector)clusterResults.get(((Integer)numCols.get(j)).intValue()),jetResults.size());
					nameJetResults.add(nameClusterResults.get(((Integer)numCols.get(j)).intValue()));
				}
                // add the number of cluster				
				numCols=Result.searchNumCols(nameClusterResults, "clusnumber");
				for (int j=0;j<numCols.size();j++)
				{
					Result.addCol(jetResults, (Vector)clusterResults.get(((Integer)numCols.get(j)).intValue()),jetResults.size());
					nameJetResults.add(nameClusterResults.get(((Integer)numCols.get(j)).intValue()));
				}		

				
				transformed=true;
			}
			
			if (paramsAnalysis.indexOf('I')!=-1)
			{
				Vector interResults=Result.readValuesResult(output_directory+pdb_code+"_inter.res");
				Vector nameInterResults=Result.readCaracResult(output_directory+pdb_code+"_inter.res");
				numCol=Result.searchNumCol(nameJetResults,"inter");
				if (Result.searchNumCol(nameInterResults,"inter")!=-1)
				{
					if (numCol==-1)
					{
						Result.addCol(jetResults, (Vector)interResults.get(Result.searchNumCol(nameInterResults,"inter")),jetResults.size());
						nameJetResults.add("inter");
					}
					else
					{
						Result.removeCol(jetResults,numCol);
						Result.addCol(jetResults, (Vector)interResults.get(Result.searchNumCol(nameInterResults,"inter")),numCol);
					}
					transformed=true;
				}
			}
			if (transformed) Result.WriteResult(jetResults, nameJetResults, output_directory+pdb_code+"_jet.res");
		}
		
		return jetResFile;
	}
	
	/** Verification de la ligne d'options passée en paramètre de JET */
	
	public static boolean optionChecking(String[] args)
    {
		String orderedValidProgramOption="AIVJCERG";
		String programOptionWithAlignFile="J";
		String validJetOption="iobwclmnpfsatrgd";
		String obligateOptions="iocp";
		String alignFileOptions="bf";
		String userOptions="";

		boolean correctSyntax=true;
		boolean inputAlignFile=true;
		
		int threshold=-1;
		int nbIteration=-1;
		
		File file=null;
		if (args.length>0)
		{
		
			int j=0;
			while((j<args.length)&&(!((args[j].equals("-h"))||(args[j].equals("--h"))||(args[j].equals("-help"))||(args[j].equals("--help")))))
				j++;
			if (j<args.length) correctSyntax=false;
			
			j=0;
			while((j<args.length)&&(correctSyntax))
			{
				if (!(((j+1)<args.length)&&(args[j].charAt(0)=='-')&&(validJetOption.indexOf(args[j].charAt(1))!=-1)))
				{
					correctSyntax=false;
					System.err.println("invalid option or incorrect syntax for: "+args[j]);
				}
				j=j+2;
			}
			j=0;
			while((j<args.length)&&(correctSyntax))
			{
				if (args[j].equals("-p"))
				{
					userOptions=userOptions+args[j].charAt(1);
					for(int i=0;i<args[j+1].length();i++)
					{
						if (orderedValidProgramOption.indexOf(args[j+1].charAt(i))==-1)
						{
							System.err.println("invalid value:"+args[j+1].charAt(i)+" for option "+args[j]);
							correctSyntax=false;
						}
					}
					boolean isOptionWithAlignFile=false;
					for(int i=0;i<programOptionWithAlignFile.length();i++)
					{
						if (args[j+1].indexOf(programOptionWithAlignFile.charAt(i))!=-1)
						{
							isOptionWithAlignFile=true;
							break;
						}
					}
					if (!isOptionWithAlignFile) inputAlignFile=false;
				}
				if (args[j].equals("-i"))
				{
					userOptions=userOptions+args[j].charAt(1);
					file=new File(args[j+1]);
					if (file.exists()) args[j+1]=file.getAbsolutePath();
					else {System.err.println("pdb input file not found: "+args[j+1]);correctSyntax=false;}
				}
				if (args[j].equals("-o"))
				{
					userOptions=userOptions+args[j].charAt(1);
					file=new File(args[j+1]);
					if(!file.isDirectory()) 
					{
						System.err.println("Output path : "+args[j+1]+" doesn't exists !");
						System.err.println("Creating output path: "+args[j+1]);
						try{file.mkdir();}
						catch(Exception e){System.err.println("can not creat output file (option -o): "+args[j+1]);correctSyntax=false;}
					}
					if (file.exists()) args[j+1]=file.getAbsolutePath();
					else {System.err.println("pdb output file not found (option -o): "+args[j+1]);correctSyntax=false;}
				}
				if (args[j].equals("-l"))
				{
					userOptions=userOptions+args[j].charAt(1);
					file=new File(args[j+1]);
					try{file.createNewFile();}
					catch(Exception e){System.err.println("can not creat log file (option -l): "+args[j+1]);correctSyntax=false;}
				}
				if (args[j].equals("-b"))
				{
					userOptions=userOptions+args[j].charAt(1);
					file=new File(args[j+1]);
					if (file.exists()) args[j+1]=file.getAbsolutePath();
					else {System.err.println("Blast input file not found (option -b): "+args[j+1]);correctSyntax=false;}
				}
				if (args[j].equals("-f"))
				{
					userOptions=userOptions+args[j].charAt(1);
					file=new File(args[j+1]);
					if (file.exists()) args[j+1]=file.getAbsolutePath();
					else {System.err.println("Fasta input file not found (option -f): "+args[j+1]);correctSyntax=false;}
				}
				if (args[j].equals("-c"))
				{
					userOptions=userOptions+args[j].charAt(1);
					file=new File(args[j+1]);
					if (!file.exists()) {System.err.println("Config input file not found (option -c): "+args[j+1]);correctSyntax=false;}
				}
				if (args[j].equals("-m"))
				{
					userOptions=userOptions+args[j].charAt(1);
					if (!((args[j+1].equals("T"))||(args[j+1].equals("F")))) 
						{System.err.println("incorrect value for option "+args[j]+": "+args[j+1]);correctSyntax=false;}
				}
				
				if (args[j].equals("-d"))
				{
					userOptions=userOptions+args[j].charAt(1);
					if (!((args[j+1].equals("chain"))||(args[j+1].equals("complex")))) 
						{System.err.println("incorrect value for option "+args[j]+": "+args[j+1]);correctSyntax=false;}
				}
				
				if (args[j].equals("-n"))
				{
					userOptions=userOptions+args[j].charAt(1);
					try{
						nbIteration=Integer.valueOf(args[j+1]).intValue();
						if ((nbIteration<1)||(nbIteration>50))
							throw new NumberFormatException();
					}
					catch(NumberFormatException e)
					{
						System.err.println("incorrect value for option -n: "+args[j+1]);
						correctSyntax=false;
					}
					
				}
				if (args[j].equals("-t"))
				{
					userOptions=userOptions+args[j].charAt(1);
					try{
						threshold=Integer.valueOf(args[j+1]).intValue();
						if ((threshold<1)||(threshold>50))
							throw new NumberFormatException();
					}
					catch(NumberFormatException e)
					{
						System.err.println("incorrect value for option -t: "+args[j+1]);
						correctSyntax=false;
					}
				}
				if (args[j].equals("-a"))
				{
					userOptions=userOptions+args[j].charAt(1);
					try{
						int a=Integer.valueOf(args[j+1]).intValue();
						if (!((a==0)||(a==1)||(a==2)||(a==3)||(a==4)||(a==5)))
							throw new NumberFormatException();
					}
					catch(NumberFormatException e)
					{
						System.err.println("incorrect value for option -a: "+args[j+1]);
						correctSyntax=false;
					}
				}
				if (args[j].equals("-r"))
				{
					userOptions=userOptions+args[j].charAt(1);
					try{
						if (!((args[j+1].equals("input"))||(args[j+1].equals("local"))||(args[j+1].equals("server"))))
							throw new NumberFormatException();
						if ((args[j+1].equals("local"))||(args[j+1].equals("server"))) inputAlignFile=false;
					}
					catch(NumberFormatException e)
					{
						System.err.println("incorrect value for option -r: "+args[j+1]);
						correctSyntax=false;
					}
				}
				if (args[j].equals("-w"))
				{
					userOptions=userOptions+args[j].charAt(1);
					try{
						Integer.valueOf(args[j+1].charAt(0)).intValue();
						if (args[j+1].length()!=4)
							throw new NumberFormatException();
					}
					catch(NumberFormatException e)
					{
						System.err.println("incorrect pdb code for option -w: "+args[j+1]);
						correctSyntax=false;
					}
					
				}
				if (args[j].equals("-g"))
				{
					userOptions=userOptions+args[j].charAt(1);
				}
				if (args[j].equals("-s"))
				{
					userOptions=userOptions+args[j].charAt(1);
					try{
						double coverage=Double.valueOf(args[j+1]).doubleValue();
						if ((coverage<=0.0)||(coverage>=0.50))
							throw new NumberFormatException();
					}
					catch(NumberFormatException e)
					{
						System.err.println("incorrect value for option -s: "+args[j+1]);
						correctSyntax=false;
					}
				}
				j=j+2;
			}
			
			if ((threshold!=-1)&&(nbIteration!=-1))
			{
				if ((threshold<1)||(threshold>nbIteration))
				{
					System.err.println("incorrect value for option -t:"+threshold);
					correctSyntax=false;
				}
			}
			
			if (correctSyntax)
			{
				String s="";
				for (j=0; j<obligateOptions.length();j++)
				{
					if (userOptions.indexOf(obligateOptions.charAt(j))==-1)
					{
						correctSyntax=false;
						if (s.equals("")) s="missing mandatory option(s): "+obligateOptions.charAt(j);
						else s=s+obligateOptions.charAt(j);
					}
				}
				System.err.println(s);
				
				if (inputAlignFile)
				{
					String tempOption="";
					for (j=0; j<alignFileOptions.length();j++) 
						if (userOptions.indexOf(alignFileOptions.charAt(j))!=-1)
							tempOption=tempOption+alignFileOptions.charAt(j);
					if ((tempOption.length()==0)||(tempOption.length()>1))
					{
						correctSyntax=false;
						System.err.println("default option -r input assume using only one of the ["+alignFileOptions+"] option(s)");
					}
				}
			}
		}
		else
		{
			System.err.println("missing option(s)");
			correctSyntax=false;
		}
		
    	return correctSyntax;
    }
	
	/** Ecriture dans le fichier de configuration de certaines valeurs par défaut 
	 * ou passés en ligne de commande des paramètres.*/
	
	public static String[] optionPreproccessing(String[] args)
    {
		int configInput=-1;
		
		for (int j=0;j<args.length;j=j+2)
			if (args[j].equals("-c")) configInput=j+1;
		
		jet.ConfigFile configFile = new jet.ConfigFile(args[configInput]);
		
		String method="input";
		String coverage="-1";
		String analysis="2";
		String accessType="chain";
		boolean seeLogFile=false;
		
		for (int j=0;j<args.length;j=j+2)
		{
			if (args[j].equals("-b"))
				configFile.setParam("SequenceRetrieving", "format", "psiblast");
			if (args[j].equals("-f"))
				configFile.setParam("SequenceRetrieving", "format", "fasta");
			if (args[j].equals("-a"))
				analysis=args[j+1];
			if (args[j].equals("-s"))
				coverage=args[j+1];
			if (args[j].equals("-r"))
				method=args[j+1];
			if (args[j].equals("-l"))
				seeLogFile=true;
			if (args[j].equals("-d"))
				accessType=args[j+1];
				
		}

		String[] newsArgs;
		if (!seeLogFile)
		{
			newsArgs=new String[args.length+2];
			int i;
			for (i=0;i<args.length;i++) newsArgs[i]=args[i];
			newsArgs[i]="-l";
			newsArgs[i+1]="caracTest.dat";
		}
		else
		{
			newsArgs=new String[args.length];
			for (int i=0;i<args.length;i++) newsArgs[i]=args[i];
		}
		
		configFile.setParam("Cluster", "analysis", analysis);
		configFile.setParam("Cluster", "coverage", coverage);
		configFile.setParam("SequenceRetrieving", "method", method);
		configFile.setParam("Access", "accessType", accessType);
		
		return newsArgs;
    }
	
	/** aide obtenu avec l'option -h ou suite à une erreur dans la ligne de commande */
	
	public static String usage()
    {
		//String s="usage: [.conf] [option_jet(AJC)] [option_io(iob) b optionnel] [parametres_io]";
		
		String s="\nCommand line: java jet.JET [option]\n"
			
		+"\nMandatory options:\n"
		
		+"-c config-file : file containing values of JET parameters (see default.conf)\n"
		+"-i input-file : input pdb file or directory with all input pdb files."
		+" These files must match to the pattern pdbCode_chain.pdb\n"
		+"-o ouput-directory : directory where JET results file are generated\n"
		+"-p type-of-program {AIJCRG}: A to compute accessibility of residues and atoms, "
		+"I to compute interface residues if the pdb input file is a complex, "
		+"J to launch JET analysis, C to launch the clustering algorithm, "
		+"R to evaluate jet results according to real interface residues (I analysis needed), "
		+" and G to generate pdb files with jet results (see -g option).\n"
		
		+"\nFacultative options:\n"
		
		+"-l log-file : Summary of JET results for each"
		+" protein (pdb code, length, identity distribution for retrieved sequences)\n"
		+"-b blast-file : psiblast input file or directory psiblast input files used by the JET analysis."
		+" These files must match to the pattern pdbCode_chain.psiblast\n"
		+"-f fasta-file : fasta input file or directory fasta input files used by the JET analysis."
		+" These files must match to the pattern pdbCode_chain.fasta\n"
		+"-w pdb_code : pdb code of complex or protein the user want to analyse."
		+" The pdb file corresponding to this pdb code is retrieving on the pdb database" 
		+" site find in the config file\n"
		+"-m merging-option {T|F}: if T the input pdb file with same pdb code are merged"
		+" in one pdb file. If F no merging is done\n"
		+"-s coverage-threshold ]0.0,0.5[ or -1: mean coverage of clusters compute by JET, -1 for JET compute coverage\n"
		+"-n nb (1,50): if nb is more than 1 iJET is run with nb iteration. For nb equals to 1 basic JET is run\n"
		+"-t threshold (1,50): in iterative mode (see option -n) residus which appear equal or " 
		+"more than threshold are selected\n"
		+"-a type-of-analyse {1|2}: 1 for JET analysis based on conservation properties (trace)."
		+"2 for JET analysis based on conservation (trace) and physical-chemical properties (pc)\n"
		+"-r retrieving-method {input;server;local}: input for input psiblast file (assume the use of option -b or -f)."
		+" local for local psiblast analysis (the command to run local psiblast must appear in the config-file)."
		+" server for server psiblast analysis (web address of the psiblast server must appear in the config-file).\n"
		+"-g pdb-results-file {tr;freq;pc;trace;clusters;axs;surfAxs;percentSurfAxs;inter;atomAxs;atomSurfAxs}:"
		+" List of names separated by comas (assume the use of -p G option). Names are those of the columns in"
		+" the results files. Columns results selected with these names are writed in pdb file format"
		+" (temperature factor column of the pdb file containing value of selected column) and could be viewed in rasmol."
		+" Example: 'trace,clusters' if the user want to have trace and clusters results in pdb format.\n"
		+"-d access-type-option {chain|complex}: if equal to chain then the accessibility is computed independently for each chain"
		+" of the complex. If equal to complex then the accessibility is computed for the entire complex (ie residues at interface"
		+" of the chains are inaccessible).\n";

		return s;

    }
	
	/** Lancement de l'analyse des résultats jet (en fonction des interfaces expérimentales 
	 * dans les fichiers inter.res) pour les fichiers pdb de la liste inputFilesFormatted.
	 * Les colonnes contenu dans le vecteur nameAnalysedResults sont analysées pour des couvertures 
	 * contenues dans le vecteur coverageAnalysedResults.
	 * Pour chaque colonne analysée on obtient les fichiers d'analyse suivants: 
	 * - /jet_eval_colonneAnalysée[paramAnalyse].res contenant les résultats moyens sur toute 
	 * la liste inputFilesFormatted.
	 * - /jet_eval_colonneAnalyséeLine[paramAnalyse].res contenant les résultats de chaque chaines. 
	 * L'analyse peut etre faite pour plusieures valeurs de scores à partir duquel on considère un résidu comme positif.*/
	
	public static void manyPdbManyTestJet(String[] args, File[] inputFilesFormatted)
	{
		
		/* A mettre en parametre option (choisir option) le fonctionnement de  nameAnalysedResults et coverageAnalysedResults
		 * est decrit dans l'entete de la methode . De toute facon tout cela est plutot pour le developpeur */
		
		Vector nameAnalysedResults=new Vector();
		nameAnalysedResults.add("clusters");
		nameAnalysedResults.add("trace");
	
		Vector coverageAnalysedResults=new Vector();
		coverageAnalysedResults.add(1.0);
		coverageAnalysedResults.add(0.25);
		
		/* fin a mettre en parametre option */
	
		String pdb_code="";	
		String output_directory="";
		File outputFile=null;
			
		String[] paramsJet=new String[args.length];
			
		for (int i=0;i<paramsJet.length;i++)
		{
			paramsJet[i]=args[i];
		}
		
		int input=-1;
		int output=-1;
		
		for (int j=0;j<paramsJet.length;j++)
		{
			if (paramsJet[j].equals("-i")) input=j+1;
			if (paramsJet[j].equals("-o")) output=j+1;
		}
		
		long time;
		
		Calendar cal=Calendar.getInstance();

		output_directory=output_directory+"["+(cal.get(Calendar.MONTH)+1)+"m";
		output_directory=output_directory+cal.get(Calendar.DAY_OF_MONTH)+"d";
		output_directory=output_directory+cal.get(Calendar.HOUR_OF_DAY)+"h";
		output_directory=output_directory+cal.get(Calendar.MINUTE)+"mn";
		output_directory=output_directory+cal.get(Calendar.SECOND)+"s]";
		
		if (inputFilesFormatted.length==1)
		{
			String path=inputFilesFormatted[0].getAbsolutePath();
			pdb_code=path.substring(path.lastIndexOf(File.separator)+1,path.lastIndexOf("."));
			path=args[output]+File.separator+pdb_code;
			outputFile=new File(path+File.separator+output_directory);
		}
		else outputFile=new File(args[4]+File.separator+"all"+File.separator+output_directory);

		Vector[] sortie=new Vector[2];
		
		Vector resultTrace;
		Vector nbResults;
		Vector result_temp;
		Vector line;
	
		double acc_temp=0.0;
		
		int struc_i=0;
		String param_experience="";
		
		Vector nom_colonnes=new Vector(13);
		for (int seuilScore=0;seuilScore<=0;seuilScore++)
		{
							
			time=Calendar.getInstance().getTimeInMillis();
			
			resultTrace=new Vector();
			nbResults=new Vector();
			line=new Vector();
	
			for (int nbResAnalysed=0;nbResAnalysed<nameAnalysedResults.size();nbResAnalysed++)
			{
				resultTrace.add(new Vector());
				nbResults.add(0);
				line.add(new Vector());
			}
		
			for (struc_i=0;struc_i<inputFilesFormatted.length;struc_i++)
			{
				paramsJet[input]=inputFilesFormatted[struc_i].getAbsolutePath();
				try{
					pdb_code=paramsJet[input].substring(paramsJet[input].lastIndexOf(File.separator)+1,paramsJet[input].lastIndexOf("."));
					paramsJet[output]=args[output]+File.separator+pdb_code;	
					acc_temp=0;
					result_temp=new Vector();
				
					for (int nbResAnalysed=0;nbResAnalysed<nameAnalysedResults.size();nbResAnalysed++)
					result_temp.add(new Vector());

					for (int nbResAnalysed=0;nbResAnalysed<nameAnalysedResults.size();nbResAnalysed++)
					{
						sortie=analyseJetResults(paramsJet[input],paramsJet[output],(String)nameAnalysedResults.get(nbResAnalysed),((Double)coverageAnalysedResults.get(nbResAnalysed)).doubleValue(),(double)seuilScore);
						/* 
						 sortie=this.analyseJetResults(paramsJet,(String)nameAnalysedResults.get(nbResAnalysed)+seuilScore,((Double)coverageAnalysedResults.get(nbResAnalysed)).doubleValue(),0.0);
						 */
						Result.sumResult((Vector)result_temp.get(nbResAnalysed),sortie[0]);
						Result.addLines((Vector)line.get(nbResAnalysed),sortie[1]);
					}									
					
					for (int nbResAnalysed=0;nbResAnalysed<nameAnalysedResults.size();nbResAnalysed++)
					{
						if (((Vector)result_temp.get(nbResAnalysed)).size()!=0)
						{
							acc_temp=Result.searchValue(8, Result.searchNumLine(0,((Double)coverageAnalysedResults.get(nbResAnalysed)).doubleValue() ,(Vector)result_temp.get(nbResAnalysed)),(Vector)result_temp.get(nbResAnalysed));
							System.out.println("complex accuracy "+(String)nameAnalysedResults.get(nbResAnalysed)+":"+acc_temp);
							Result.sumResult((Vector)resultTrace.get(nbResAnalysed),(Vector)result_temp.get(nbResAnalysed));
							nbResults.set(nbResAnalysed,((Integer)nbResults.get(nbResAnalysed)).intValue()+1 );
						}
					}

				}catch (jet.exception.NaccessException exc)
				{System.err.println("no naccess results for the pdb file:"+paramsJet[input]);}
			
			}
		
			for (int nbResAnalysed=0;nbResAnalysed<nameAnalysedResults.size();nbResAnalysed++)
			{
				Result.dividResult((Vector)resultTrace.get(nbResAnalysed),((Integer)nbResults.get(nbResAnalysed)).intValue());
			}
		
			for (int nbResAnalysed=0;nbResAnalysed<nameAnalysedResults.size();nbResAnalysed++)
				System.out.println("average accuracy "+(String)nameAnalysedResults.get(nbResAnalysed)+":"+Result.searchValue(8, Result.searchNumLine(0,((Double)coverageAnalysedResults.get(nbResAnalysed)).doubleValue(),(Vector)resultTrace.get(nbResAnalysed)),(Vector)resultTrace.get(nbResAnalysed)));
			
			param_experience="["+seuilScore+"]";
				
			nom_colonnes=new Vector(13);
				
			nom_colonnes.add("cover");nom_colonnes.add("coverSurf");nom_colonnes.add("sens");nom_colonnes.add("ScoreSens");
			nom_colonnes.add("PPV");nom_colonnes.add("ScorePPV");nom_colonnes.add("spec");nom_colonnes.add("ScoreSpec");
			nom_colonnes.add("acc");nom_colonnes.add("ScoreAcc");nom_colonnes.add("EF");nom_colonnes.add("ScoreEF");
				
			for (int nbResAnalysed=0;nbResAnalysed<nameAnalysedResults.size();nbResAnalysed++)
				Result.WriteResult((Vector)resultTrace.get(nbResAnalysed), nom_colonnes,outputFile.getAbsolutePath()+File.separator+"jet_eval_"+(String)nameAnalysedResults.get(nbResAnalysed)+param_experience+".res");
				
			nom_colonnes.add(0,"size");nom_colonnes.add(0,"pdbCode");
				
			for (int nbResAnalysed=0;nbResAnalysed<nameAnalysedResults.size();nbResAnalysed++)
				Result.WriteResult((Vector)line.get(nbResAnalysed), nom_colonnes,outputFile.getAbsolutePath()+File.separator+"jet_eval_"+(String)nameAnalysedResults.get(nbResAnalysed)+"Line"+param_experience+".res");
						
		}
		//System.out.println(""+outputFile.getAbsolutePath());
		
	}
	
	/** Génération des résultats au format pdb pour visualisation (vmd,rasmol...) */
    
    public static void generatePDBResult(File[] inputFilesFormatted, Vector colNameCons)
    {
    	
    	System.err.println("******** generate PDB Results ***********");
	jet.io.file.PdbFileTransform pdbft;
	boolean findCol;
	boolean findFile;
	File pdbfile;
	String filename;
	File directory;
	File[] fileList;
	Vector colNameAuth= new Vector();
	
	for (int struc_i=0;struc_i<inputFilesFormatted.length;struc_i++)
	    {
		pdbfile=inputFilesFormatted[struc_i];
		filename=pdbfile.getPath();
		directory = pdbfile.getParentFile();
		filename=filename.substring(0,filename.lastIndexOf("."));

		if (new File(filename+"_jet.res").exists()) colNameAuth.addAll(Result.readCaracResult(filename+"_jet.res"));
		if (new File(filename+"_axs.res").exists()) colNameAuth.addAll(Result.readCaracResult(filename+"_axs.res"));
		if (new File(filename+"_inter.res").exists()) colNameAuth.addAll(Result.readCaracResult(filename+"_inter.res"));
		if (new File(filename+"_clusters.res").exists()) colNameAuth.addAll(Result.readCaracResult(filename+"_clusters.res"));
		if (new File(filename+"_atomAxs.res").exists()) colNameAuth.addAll(Result.readCaracResult(filename+"_atomAxs.res"));
		
		fileList=directory.listFiles();
		
		for (int i=0;i<fileList.length;i++)
		    {
			findFile=false;
			if (fileList[i].getName().endsWith(".pdb"))
			    {
				for (int j=0;j<colNameCons.size();j++)
				    {
					if (fileList[i].getName().contains("_"+colNameCons.get(j)))
					    {
						findFile=true;
						break;
					    }
				    }
				if (!findFile)
				    {
					for (int j=0;j<colNameAuth.size();j++)
					    {
						if ( (fileList[i].getName().contains("_"+colNameAuth.get(j)+".")) || (fileList[i].getName().contains("_"+colNameAuth.get(j)+"_")) )
						    {
							System.out.println("_"+colNameAuth.get(j));
							System.out.println(fileList[i].getName());
							fileList[i].delete();
							break;
						    }
					    }
					
				    }
				
			    }
			
		    }

		for (int i=0; i<colNameCons.size();i++)
		    {
			findCol=false;
			String colName =  (String)colNameCons.get(i);
			if (!new File(filename+"_"+colName+".pdb").exists())
			    {
				if (new File(filename+"_jet.res").exists()) findCol=Result.convertResultToPDB(filename+"_jet.res", pdbfile.getPath(), colName,1);
				if ((new File(filename+"_axs.res").exists())&&(!findCol)) findCol=Result.convertResultToPDB(filename+"_axs.res", pdbfile.getPath(), colName,1);
				if ((new File(filename+"_inter.res").exists())&&(!findCol)) findCol=Result.convertResultToPDB(filename+"_inter.res", pdbfile.getPath(), colName,1);
				if ((new File(filename+"_clusters.res").exists())&&(!findCol)) findCol=Result.convertResultToPDB(filename+"_clusters.res", pdbfile.getPath(), colName,1);
				if ((new File(filename+"_atomAxs.res").exists())&&(!findCol)) findCol=Result.convertResultToPDB(filename+"_atomAxs.res", pdbfile.getPath(), colName,2);
			    }
			else findCol=true;
			if (findCol) 
			    {
				pdbft= new jet.io.file.PdbFileTransform(filename+"_"+colName+".pdb");
				pdbft.cut(new Vector(),true);
			    }
			else
			    System.err.println("Column '"+colName+"' not found in results files, unable to generate the corresponding pdb file");
		    }
	    }
	System.err.println("******** End generate PDB Results ***********");
    }
}



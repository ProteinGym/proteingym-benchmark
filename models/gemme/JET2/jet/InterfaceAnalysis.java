package jet;

import java.util.*;
import java.io.*;

public class InterfaceAnalysis
{
    jet.ConfigFile cf,caracTestFile;
    double contact_cutoff;
    float probe_radius;
    
    public InterfaceAnalysis(jet.ConfigFile cf,jet.ConfigFile lf) 
    { 
	this.cf=cf;
	this.caracTestFile = lf;
	this.contact_cutoff=cf.getDoubleParam("Interface","cutoff");if (contact_cutoff==-1) contact_cutoff=0.1;
	this.probe_radius=(float)cf.getDoubleParam("Access","probe_radius");if (probe_radius==(float)-1) probe_radius=(float)1.4;
    }
    
    
    public void analyse(File pdbfile) throws jet.exception.NaccessException
    {
    	
    	String pdbCode=pdbfile.getAbsolutePath();
    	File outputFile=new File(pdbCode.substring(0,pdbCode.lastIndexOf("/")));
    	pdbCode=pdbCode.substring(pdbCode.lastIndexOf("/")+1, pdbCode.lastIndexOf("/")+5);
    	String s="";
    	String category=pdbCode;
    	Vector moleculesConserver=new Vector();
    	Vector pdbFiles=new Vector();
    	
    	/* recherche des ligands à conserver dans le calcul de l'interface */
 
    	System.out.print("Enzyme compound for "+pdbCode+":");
    	if (cf.getParam("Interface", "ligand").equals("yes"))
    	{
	    	/* composants predefinis */
	    	moleculesConserver.addAll(selectionnerReactifsHeterodimer(pdbCode));
	    	moleculesConserver.addAll(selectionnerReactifsHomodimer(pdbCode));
	    	moleculesConserver.addAll(selectionnerReactifsTransient(pdbCode));
    		/* composants non predefinis */
	    	if (moleculesConserver.size()==0)
	    	{
	    		jet.io.file.PdbFileReader pdb=new jet.io.file.PdbFileReader(pdbfile.getPath());
	    		Vector pdbEnzymeCodes=jet.data.dataformat.parser.PDB.getEnzymeCodes(pdb);
	    		moleculesConserver=searchEnzymeCompound(cf,pdbEnzymeCodes);
	    	}
	    	for(int j=0;j<moleculesConserver.size();j++) s=s+" "+(String)moleculesConserver.get(j);
	    	/* Sauvegarde dans le fichier de caracteristique de l'analyse */
	    	if (moleculesConserver.size()!=0) caracTestFile.addParameter(category, "EnzymeCompound",s, "Enzyme compound");
    	}
    	if (moleculesConserver.size()==0) s="nothing";
    	System.out.println(s);
    	
    	/* recherche des structures pdb homologues (95%) */
    	
    	System.out.print("Homologous structures for "+pdbCode+":");
    	Vector pdbCodes=new Vector();
    	
    	if (cf.getParam("Interface", "homologousPDB").equals("yes"))
    	{
	    	/* structures homologues predefinies */
	    	pdbCodes.addAll(selectionnerCodesHeterodimer(cf,pdbCode));
	    	pdbCodes.addAll(selectionnerCodesHomodimer(cf,pdbCode));
	    	pdbCodes.addAll(selectionnerCodesTransient(cf,pdbCode));
	    	/* structures homologues non predefinies */
	    	if (pdbCodes.size()==0)
	    		pdbCodes=searchHomologousPDB(cf,pdbCode);
	    	s="";
	    	for (int i=0; i<pdbCodes.size();i++) s=s+" "+pdbCodes.get(i);
	    	/* Sauvegarde dans le fichier de caracteristique de l'analyse */
	    	if (pdbCodes.size()!=0) caracTestFile.addParameter(category, "HomologousPDB",s, "homologous pdb structures");
	    	/* recuperation des fichiers pdb homologues sur le web */
	    	pdbFiles=retrievePdbFiles(cf,pdbCodes,outputFile);
	    	File[] pdbFilesTab=new File[pdbFiles.size()];
	    	for (int i=0; i<pdbFiles.size();i++) pdbFilesTab[i]=(File)pdbFiles.get(i);
	    	/* parsing des fichiers pdb homologues pour conserver les ligands selectionnes*/
			pdbFilesTab=jet.JET.parsePDBFile(pdbFilesTab,moleculesConserver);
	    	
			pdbFiles.clear();
			for (int i=0; i<pdbFilesTab.length;i++)
			{
				pdbFiles.add(pdbFilesTab[i]);
			}	
    	}
    	if (pdbCodes.size()==0) s="nothing";
    	System.out.println(s);
    	
    	naccessInterface(pdbfile,pdbFiles); 
    
    }
    
    
    public static Vector selectionnerReactifsTransient(String pdbCode)
    {
    	Vector moleculesConserver=new Vector();
    	if (pdbCode.equals("1apm")||pdbCode.equals("1efu")||pdbCode.equals("1g3n")||pdbCode.equals("1got")
    			||pdbCode.equals("1k9o")||pdbCode.equals("1rrp")||pdbCode.equals("1ugh")||pdbCode.equals("1ytf"))
    	{
	    	moleculesConserver.add("ATP");moleculesConserver.add("ADP");moleculesConserver.add("GDP");moleculesConserver.add("PHA");
	    	moleculesConserver.add("GNP");
    	}
    	return moleculesConserver;
    }
    
    public static Vector selectionnerReactifsHeterodimer(String pdbCode)
    {
    	Vector moleculesConserver=new Vector();
    	if (pdbCode.equals("1all"))
    	{
    		moleculesConserver.add("CYC");moleculesConserver.add("CH3");
    	}
    	if (pdbCode.equals("1hcg")||pdbCode.equals("1luc")||pdbCode.equals("1tcr"));
    	if (pdbCode.equals("1scu"))
    	{
    		moleculesConserver.add("SUC");moleculesConserver.add("PO4");moleculesConserver.add("COA");moleculesConserver.add("ATP");
    		moleculesConserver.add("ADP");moleculesConserver.add("AMP");moleculesConserver.add("PHS");
    	}
    	if (pdbCode.equals("1tco"))
    	{
    		moleculesConserver.add("PO4");moleculesConserver.add(" ZN");moleculesConserver.add(" FE");moleculesConserver.add("FK5");
    	}
    	if (pdbCode.equals("1ubs"))
    	{
    		moleculesConserver.add("PLS");moleculesConserver.add("IPL");moleculesConserver.add("P1T");moleculesConserver.add("F6F");
    		moleculesConserver.add("G3H");moleculesConserver.add("SER");moleculesConserver.add("PLP");moleculesConserver.add("TRP");
    		moleculesConserver.add("G3P");moleculesConserver.add("PLT");
    	}
    	if (pdbCode.equals("1wdc"))
    	{
    		moleculesConserver.add(" CA");moleculesConserver.add(" MG");
    	}
    	if (pdbCode.equals("2pcd"))
    	{
    		moleculesConserver.add(" FE");moleculesConserver.add("DHB");moleculesConserver.add("3HB");moleculesConserver.add("PHB");
    	}
    	if (pdbCode.equals("8atc")||pdbCode.equals("9atc"))
    	{
    		moleculesConserver.add("PCT");moleculesConserver.add("PAL");moleculesConserver.add("CTP");moleculesConserver.add("PO4");
    		moleculesConserver.add("6PR");
    	}
    	return moleculesConserver;
    }
    
    public static Vector selectionnerReactifsHomodimer(String pdbCode)
    {
    	Vector moleculesConserver=new Vector();
    	if (pdbCode.equals("1gdh")||pdbCode.equals("1gl1")||pdbCode.equals("1ids")||pdbCode.equals("1ies")||
    			pdbCode.equals("1leh")||pdbCode.equals("1pky")||pdbCode.equals("2eip")||pdbCode.equals("2pol"));
    	
    	if (pdbCode.equals("1nqv"))
    	{
    		moleculesConserver.add("LMZ");moleculesConserver.add("RLP");
    	}
    	if (pdbCode.equals("1bnc"))
    	{
    		moleculesConserver.add("ATP");moleculesConserver.add("COA");moleculesConserver.add("ADP");
    		moleculesConserver.add("PO4");moleculesConserver.add("BTN");
    	}
    	if (pdbCode.equals("1daa"))
    	{
    		moleculesConserver.add("PLP");moleculesConserver.add("PMP");moleculesConserver.add("PDA");
    	}
    	if (pdbCode.equals("1dpg"))
    	{
    		moleculesConserver.add("BG6");moleculesConserver.add("NDP");moleculesConserver.add("NAD");
    		moleculesConserver.add("NAP");
    	}
    	if (pdbCode.equals("1ecp"))
    	{
    		moleculesConserver.add("FMB");moleculesConserver.add("SO4");moleculesConserver.add("6MP");
    		moleculesConserver.add("ADN");moleculesConserver.add("PO4");moleculesConserver.add("NOS");
    	}
    	if (pdbCode.equals("1efu"))
    	{
    		moleculesConserver.add("GDP");moleculesConserver.add("PHA");
    	}
    	if (pdbCode.equals("1frp"))
    	{
    		moleculesConserver.add("AMP");moleculesConserver.add("F6P");moleculesConserver.add("PO4");
    		moleculesConserver.add("AHG");
    	}
    	if (pdbCode.equals("1fuq"))
    	{
    		moleculesConserver.add("CIT");moleculesConserver.add("SIF");
    	}
    	if (pdbCode.equals("1ges"))
    	{
    		moleculesConserver.add("FAD");moleculesConserver.add("NAP");
    	}
    	if (pdbCode.equals("1glq"))
    	{
    		moleculesConserver.add("GTB");moleculesConserver.add("GTT");
    	}
    	if (pdbCode.equals("1gpm"))
    	{
    		moleculesConserver.add("AMP");moleculesConserver.add("POP");
    	}
    	if (pdbCode.equals("1hur"))
    	{
    		moleculesConserver.add("GDP");moleculesConserver.add("GTP");
    	}
    	if (pdbCode.equals("1hyh"))
    	{
    		moleculesConserver.add("NAD");
    	}
    	if (pdbCode.equals("1mas"))
    	{
    		moleculesConserver.add("PIR");
    	}
    	if (pdbCode.equals("1mld"))
    	{
    		moleculesConserver.add("CIT");moleculesConserver.add("MLT");moleculesConserver.add("NAD");
    	}
    	if (pdbCode.equals("1nhk"))
    	{
    		moleculesConserver.add("tout");
    		//moleculesConserver.add("CMP");moleculesConserver.add("ADP");moleculesConserver.add("MY2");
    		//moleculesConserver.add("CMP");moleculesConserver.add("ADP");
    	}
    	if (pdbCode.equals("1oro"))
    	{
    		moleculesConserver.add("ORO");moleculesConserver.add("PRP");moleculesConserver.add("OMP");
    	}
    	if (pdbCode.equals("1osj"))
    	{
    		moleculesConserver.add("NAD");
    	}
    	if (pdbCode.equals("1poy"))
    	{
    		moleculesConserver.add("SPD");
    	}
    	if (pdbCode.equals("1qor"))
    	{
    		moleculesConserver.add("NAP");
    	}
    	if (pdbCode.equals("1rah"))
    	{
    		moleculesConserver.add("PAL");moleculesConserver.add("FLC");
    	}
    	if (pdbCode.equals("1scu"))
    	{
    		moleculesConserver.add("SUC");moleculesConserver.add("PO4");moleculesConserver.add("COA");moleculesConserver.add("ATP");
    		moleculesConserver.add("ADP");moleculesConserver.add("AMP");moleculesConserver.add("PHS");
    	}
    	if (pdbCode.equals("1set"))
    	{
    		moleculesConserver.add("SSA");moleculesConserver.add("AHX");
    	}
    	if (pdbCode.equals("1sft"))
    	{
    		moleculesConserver.add("IN5");moleculesConserver.add("SCP");moleculesConserver.add("PLD");
    		moleculesConserver.add("PDD");moleculesConserver.add("PMP");moleculesConserver.add("PLP");
    	}
    	if (pdbCode.equals("1tph"))
    	{
    		moleculesConserver.add("PGH");moleculesConserver.add("PGA");
    	}
    	if (pdbCode.equals("1xik"))
    	{
    		moleculesConserver.add("ATP");moleculesConserver.add("TTP");
    	}
    	if (pdbCode.equals("2cst"))
    	{
    		moleculesConserver.add("MAE");
    	}
    	if (pdbCode.equals("2hhm"))
    	{
    		moleculesConserver.add("SO4");moleculesConserver.add("IPD");moleculesConserver.add(" GD");
    	}
    	if (pdbCode.equals("2pcd"))
    	{
    		moleculesConserver.add("DHB");moleculesConserver.add("3HB");moleculesConserver.add("4HB");
    	}
    	if (pdbCode.equals("3lad"))
    	{
    		moleculesConserver.add("FAD");
    	}
    	if (pdbCode.equals("3mde"))
    	{
    		moleculesConserver.add("FAD");moleculesConserver.add("CO8");
    	}
    	if (pdbCode.equals("6gsv"))
    	{
    		moleculesConserver.add("GPS");
    	}
    	if (pdbCode.equals("8cat"))
    	{
    		moleculesConserver.add("HEM");
    	}
    	return moleculesConserver;
    	
    }
    
    
    public static Vector selectionnerCodesTransient(jet.ConfigFile cf, String pdbCode)
    {
    	Vector pdbCodes= new Vector();
    	
    	if (pdbCode.equals("1apm"))
    	{
    		pdbCodes.add("1atp");pdbCodes.add("1jbp");
    	}
    	if (pdbCode.equals("1efu"))
    	{
    		pdbCodes.add("1etu");
    	}
    	if (pdbCode.equals("1got"))
    	{
    		pdbCodes.add("1fqk");
    	}
    	if (pdbCode.equals("1rrp"))
    	{
    		pdbCodes.add("1qg4");
    	}
    	if (pdbCode.equals("1g3n"))
    	{
    		pdbCodes.add("1blx");pdbCodes.add("1jow");pdbCodes.add("1xo2");pdbCodes.add("1euf");pdbCodes.add("2f2c");
    	}
    	if (pdbCode.equals("1ytf"))
    	{
    		pdbCodes.add("1rm1");
    	}
    	
    	return pdbCodes;
    }
    
    public static Vector selectionnerCodesHeterodimer(jet.ConfigFile cf, String pdbCode)
    {
    	Vector pdbCodes= new Vector();
    	
    	if (pdbCode.equals("1all")||pdbCode.equals("1hcg")||pdbCode.equals("1luc")||pdbCode.equals("1wdc"));
    	if (pdbCode.equals("1scu"))
    	{
    		pdbCodes.add("1jll");pdbCodes.add("2scu");
    	}
    	if (pdbCode.equals("1tco"))
    	{
    		pdbCodes.add("1mf8");pdbCodes.add("2jog");pdbCodes.add("2p6b");
    	}
    	if (pdbCode.equals("1tcr"))
    	{
    		pdbCodes.add("1sbb");pdbCodes.add("2ckb");
    	}
    	if (pdbCode.equals("1ubs"))
    	{
    		pdbCodes=searchHomologousPDB(cf,pdbCode);
    	}
    	if (pdbCode.equals("2pcd"))
    	{
    		pdbCodes.add("3pca");pdbCodes.add("3pcb");pdbCodes.add("3pcc");
    	}
    	if (pdbCode.equals("8atc")||pdbCode.equals("9atc"))
    	{
    		pdbCodes.add("8at1");pdbCodes.add("9atc");pdbCodes.add("8atc");pdbCodes.add("2h3e");
    	}
    	
    	return pdbCodes;
    }
    
    public static Vector selectionnerCodesHomodimer(jet.ConfigFile cf, String pdbCode)
    {
    	Vector pdbCodes= new Vector();
    	
    	if (pdbCode.equals("1nqv"))
    	{
    		pdbCodes.add("1nqx");
    	}
    	if (pdbCode.equals("1daa")||pdbCode.equals("1dpg")||pdbCode.equals("1nhk")||pdbCode.equals("1oro")
    			||pdbCode.equals("1set")||pdbCode.equals("2pol"))
    	{
    		pdbCodes=searchHomologousPDB(cf,pdbCode);
    	}
    	if (pdbCode.equals("1fuq")||pdbCode.equals("1gdh")||pdbCode.equals("1gl1")||pdbCode.equals("1gpm")
    			||pdbCode.equals("1hyh")||pdbCode.equals("1ids")||pdbCode.equals("1ies")||pdbCode.equals("1leh")
    			||pdbCode.equals("1pky")||pdbCode.equals("1poy")||pdbCode.equals("1qor")||pdbCode.equals("2cst")
    			||pdbCode.equals("2eip")||pdbCode.equals("3lad")||pdbCode.equals("6gsv")||pdbCode.equals("8cat")
    			||pdbCode.equals("3mde"));
    	if (pdbCode.equals("1bnc"))
    	{
    		pdbCodes.add("1dv1");pdbCodes.add("1dv2");pdbCodes.add("2gps");pdbCodes.add("1k69");
    	}
    	if (pdbCode.equals("1ecp"))
    	{
    		pdbCodes.add("1a69");pdbCodes.add("1oty");pdbCodes.add("1pk7");pdbCodes.add("1pro");
    	}
    	if (pdbCode.equals("1efu"))
    	{
    		pdbCodes.add("1etu");pdbCodes.add("1ls2");
    	}
    	if (pdbCode.equals("1frp"))
    	{
    		pdbCodes.add("1cnq");pdbCodes.add("1fbc");
    	}
    	if (pdbCode.equals("1ges"))
    	{
    		pdbCodes.add("1get");
    	}
    	if (pdbCode.equals("1glq"))
    	{
    		pdbCodes.add("1gsy");
    	}
    	if (pdbCode.equals("1hur"))
    	{
    		pdbCodes.add("1o3y");pdbCodes.add("1r8q");
    	}
    	if (pdbCode.equals("1mas"))
    	{
    		pdbCodes.add("2mas");
    	}
    	if (pdbCode.equals("1mld"))
    	{
    		pdbCodes.add("2dfd");
    	}
    	if (pdbCode.equals("1osj"))
    	{
    		pdbCodes.add("1hex");
    	}
    	if (pdbCode.equals("1rah"))
    	{
    		pdbCodes.add("1acm");pdbCodes.add("1r0c");
    	}
    	if (pdbCode.equals("1scu"))
    	{
    		pdbCodes.add("1jll");pdbCodes.add("2scu");
    	}
    	if (pdbCode.equals("1sft"))
    	{
    		pdbCodes.add("1bd0");pdbCodes.add("1epv");pdbCodes.add("1xql");pdbCodes.add("2sfp");
    	}
    	if (pdbCode.equals("1tph"))
    	{
    		pdbCodes.add("1ssg");
    	}
    	if (pdbCode.equals("1xik"))
    	{
    		pdbCodes.add("3r1r");pdbCodes.add("4r1r");
    	}
    	if (pdbCode.equals("2hhm"))
    	{
    		pdbCodes.add("1awd");
    	}
    	if (pdbCode.equals("2pcd"))
    	{
    		pdbCodes.add("1ykl");pdbCodes.add("3pcb");pdbCodes.add("3pcg");
    	}
    	
    	
    	return pdbCodes;
    }
    
    /** A verifier (pb) */
    
    public static Vector searchEnzymeCompound(jet.ConfigFile cf, Vector pdbEnzymeCodes)
    {
    	Vector compound=new Vector();
    	if (pdbEnzymeCodes.size()>0)
    	{
	    	String line="";
	    	String caracCompound="";
	    	String[] subLine;
	    	Vector data=jet.io.file.FileIO.readFile(cf.getParam("Interface", "enzymeCpd"));
	    	int k;
	    	System.out.println("longueur"+data.size());
	    	for (int i=0; i<data.size();i++)
	    	{
	    		System.out.println(i);
	    		line=(String)data.get(i);
			//System.out.println(line);
	    		subLine=line.split("\\s+");
	    		if (subLine[0].equals("ENTRY"))
	    		{	
	    			k=0;
		    		for (int j=0;j<pdbEnzymeCodes.size();j++)
		    		{
			    		if (subLine[2].equals((String)pdbEnzymeCodes.get(j)))
			    		{
			    			k=1;
			    			//line=(String)data.get(i+k);
			    			while (!(line=(String)data.get(i+k)).startsWith("///"))
			    			{
			    				if ((line.startsWith("SUBSTRATE"))
			    					||(line.startsWith("PRODUCT"))
			    					||(line.startsWith("COFACTOR")))
			    				{
			    					if (line.lastIndexOf("[CPD:C")!=-1)
			    						compound.add(line.substring(12,line.lastIndexOf("[CPD:C")).trim());
			    					k++;
			    					while ((line=(String)data.get(i+k)).startsWith("            "))
			    	    			{
			    						if (line.lastIndexOf("[CPD:C")!=-1)
			    							compound.add(line.substring(12,line.lastIndexOf("[CPD:C")).trim());
			    						k++;
			    	    			}
			    				}
			    				else k++;
			    			}
			    			break;
			    		}
		    		}
		    		i=i+k;
	    		}
	    	}
    	}	    	
    	//for (int i=0;i<compound.size();i++) System.out.println(" "+compound.get(i));
    	return compound;	   
    }
			    					
    
    public static Vector searchHomologousPDB(jet.ConfigFile cf, String pdbCode)
    {
    	Vector pdbCodes=new Vector();
    	String[] subLine;
    	String line="";
    	String nbCluster="";
    	int k;
    	Vector data=jet.io.file.FileIO.readFile(cf.getParam("Interface", "clusteredPDB"));
    	for (int i=0; i<data.size();i++)
    	{
    		line=(String)data.get(i);
    		subLine=line.split("\\s+");
    		if (subLine[2].toLowerCase().substring(0,4).equals(pdbCode.toLowerCase()))
    		{
    			nbCluster=subLine[0];
    			k=1;
    			subLine=((String)data.get(i-k)).split("\\s+");
    			
    			while (nbCluster.equals(subLine[0]))
    			{
    				if ((!subLine[2].toLowerCase().substring(0,4).equals(pdbCode.toLowerCase()))
    						&&(!pdbCodes.contains(subLine[2].toLowerCase().substring(0,4))))
    				{
    					pdbCodes.add(subLine[2].toLowerCase().substring(0,4));
    				}
    				k--;
    				subLine=((String)data.get(i-k)).split("\\s+");
    			}
    			k=1;
    			subLine=((String)data.get(i+k)).split("\\s+");
    			while (nbCluster.equals(subLine[0]))
    			{
    				if ((!subLine[2].toLowerCase().substring(0,4).equals(pdbCode.toLowerCase()))
    						&&(!pdbCodes.contains(subLine[2].toLowerCase().substring(0,4))))
    				{
    					pdbCodes.add(subLine[2].toLowerCase().substring(0,4));
    				}
    				k++;
    				subLine=((String)data.get(i+k)).split("\\s+");
    			}
    			break;
    		}
    	}
    	return pdbCodes;
    }
    
    public static Vector retrievePdbFiles(jet.ConfigFile cf, Vector pdbCodes, File outputFile)
    {
    	Vector pdbFiles=new Vector();
    	File fileTemp;
    	for (int i=0;i<pdbCodes.size();i++)
    	{
    		fileTemp=new File(outputFile.getAbsolutePath()+"/"+(String)pdbCodes.get(i)+"/"+(String)pdbCodes.get(i)+".pdb");
    		
    		if (!fileTemp.exists())
    		{
    			pdbFiles.add(JET.fetchPdbFile(cf,((String)pdbCodes.get(i)), new File(outputFile.getAbsolutePath()+"/"+(String)pdbCodes.get(i))));
    		}
    		else pdbFiles.add(new File(outputFile.getAbsolutePath()+"/"+(String)pdbCodes.get(i)+"/"+(String)pdbCodes.get(i)+".pdb"));
    	}
    	
    	return pdbFiles;
    }
    
    public Vector naccessInterface(File pdbFileBound) throws jet.exception.NaccessException
    {
    	// identifies interface residues through running Naccess !!
    	Vector result=jet.tools.MapContact.resContactNaccess(pdbFileBound,probe_radius, contact_cutoff,cf.getParam("Software","naccess"));
    	
    	/* Lecture du fichier pdb */
    	jet.io.file.PdbFileReader pdb=new jet.io.file.PdbFileReader(pdbFileBound.getPath());
    	/* Recuperation par le parseur des infos de structure 3D */
    	Vector pdbInfo=jet.data.dataformat.parser.PDB.getSequenceInfo(pdb,false);
    	
    	Vector contact=new Vector(pdbInfo.size());	
    	Vector positions=new Vector(pdbInfo.size());	
    	Vector chaines=new Vector(pdbInfo.size());
    	Vector codes=new Vector(pdbInfo.size());
    	
    	Vector interfaceResidu=(Vector)result.get(6);
    	String chaineCourante=(String)((Vector)result.get(1)).get(0);

    	int i=0;

    	while (i<((Vector)result.get(1)).size())
    	{
    		contact.add(new Vector());
    		chaines.add(new Vector());

    		while ((i<((Vector)result.get(1)).size())&&(((String)((Vector)result.get(1)).get(i)).equals(chaineCourante)))
    		{
		    ((Vector)contact.lastElement()).add(interfaceResidu.get(i));
		    ((Vector)chaines.lastElement()).add(((Vector)result.get(1)).get(i));
		    i++;
    		}
    		if (i<((Vector)result.get(1)).size()) chaineCourante=(String)((Vector)result.get(1)).get(i);
    	}
    	
    	
    	int nb=0,j,k;
    	i=0;
    	boolean dejaVuSeq=false;
    	
    	while(i<pdbInfo.size())
    	{
    		positions.add(new Vector());
    		codes.add(new Vector());
    		jet.data.datatype.Sequence3D seq=((jet.data.dataformat.info.PdbSequenceInfo)pdbInfo.get(i)).getSequence();
    		dejaVuSeq=false;
    		nb=0;
    		/* On teste si la chaine à la position i a deja ete observées dans le fichier pdb */
    		while((!dejaVuSeq)&&(nb<i))
    	    {
    			if (seq.isIdenticalSeq(((jet.data.dataformat.info.PdbSequenceInfo)pdbInfo.get(nb)).getSequence()))
    			{
    				/* Si oui il faut regrouper les infos des deux chaines identiques et éliminer 
    				 * les infos concernant la chaine courante */
    				for (k=0;k<((Vector)contact.get(nb)).size();k++)
    				{
    					if(((Double)((Vector)contact.get(i)).elementAt(k)).doubleValue()>0.0)
    						((Vector)contact.get(nb)).setElementAt(((Vector)contact.get(i)).elementAt(k), k);
    				}
    				contact.setElementAt(contact.get(nb),i);
    				dejaVuSeq=true;
    			}
    			else
    			{
    			nb++;
    			}
    			
    	    }

    		/* A chaque fois qu'on ajoute des informations (accessibilité et contact) sur une chaine 
    		 * on ajoute aussi les informations sur le type et la position des residus */
    	
    		i++;
    		for(j=0;j<seq.size();j++)
    		{			
    			((Vector)codes.lastElement()).add(seq.getResidue(j).getResidueCode());
    			((Vector)positions.lastElement()).add(seq.getResidue(j).getPosition());
    		}	
    	}  	
		
		result=new Vector(4);
		
		result.add(codes);
		result.add(positions);
		result.add(chaines);
		result.add(contact);

		return result;
    }
    	
    public void naccessInterface(File pdbFileBound,Vector pdbFiles) throws jet.exception.NaccessException
    { 
    	String pdbCode=pdbFileBound.getPath();
    	if (pdbCode.lastIndexOf("/")!=-1) pdbCode=pdbCode.substring(pdbCode.lastIndexOf("/")+1,pdbCode.lastIndexOf("/")+5);
    	
    	System.out.println("Analysis of :"+pdbCode);
    	
    	Vector resultFinal=naccessInterface(pdbFileBound);
    	
    	Vector nom_colonnes=new Vector(4+pdbFiles.size());
    	nom_colonnes.add("Res");nom_colonnes.add("pos");nom_colonnes.add("chain");nom_colonnes.add(""+pdbCode);
    	Vector result=new Vector(4+pdbFiles.size());
    	
    	for(int i=0;i<resultFinal.size();i++) result.add(new Vector());
    	for(int j=0;j<result.size();j++)
    	{
	    	for(int i=0;i<((Vector)resultFinal.get(j)).size();i++)
	    	{
	    		((Vector)result.get(j)).addAll(((Vector)((Vector)resultFinal.get(j)).get(i))); 
	    		
	    	}
    	}

    	Vector resultTempSeq;
    	Vector resultChaineTemp;

    	/* Lecture du fichier pdb */
    	jet.io.file.PdbFileReader pdb=new jet.io.file.PdbFileReader(pdbFileBound.getPath());
    	/* Recupération par le parseur des infos de structure 3D */
    	Vector pdbInfoRef=jet.data.dataformat.parser.PDB.getSequenceInfo(pdb,false);
    	Vector pdbInfoSeq; 
    	jet.data.datatype.Sequence3D seqRef;
    	jet.data.datatype.Sequence3D seq;
    	
    	int position;
    	int m;
    	double percentIdentity=0.0;
    	
    	/* Boucle sur chacune des sequences homologues recuperees */
    	for (int i=0;i<pdbFiles.size();i++)
    	{
    		
        	try{
		    /* Lecture du fichier pdb */
		    pdb=new jet.io.file.PdbFileReader(((File)pdbFiles.get(i)).getPath());
		    
		    /* Recupération par le parseur des infos de structure 3D */
		    pdbInfoSeq=jet.data.dataformat.parser.PDB.getSequenceInfo(pdb,false);
		    pdbCode=((File)pdbFiles.get(i)).getPath();
		    if (pdbCode.lastIndexOf("/")!=-1) pdbCode=pdbCode.substring(pdbCode.lastIndexOf("/")+1,pdbCode.lastIndexOf("/")+5);
		    System.out.println("Analysis of :"+pdbCode);
		    /* Calcul de l'interface pour la sequence homologue */
		    resultTempSeq=naccessInterface((File)pdbFiles.get(i));
		    /* Copie de ces resultats dans la sortie */
		    nom_colonnes.add(""+pdbCode);
		    result.add(new Vector());
		    
		    /* Boucle sur chaque chaine de la sequence de reference */
	            for (int j=0;j<pdbInfoRef.size();j++)
			{
			    seqRef=((jet.data.dataformat.info.PdbSequenceInfo)pdbInfoRef.get(j)).getSequence();
			    
			    resultChaineTemp=new Vector();
			    
			    for(int nb=0;nb<((Vector)((Vector)resultFinal.get(1)).get(j)).size();nb++)
	    	    		resultChaineTemp.add(0.0); 
			    
			    /* Boucle sur chaque chaine d'un des sequence homologue consideree */
			    for (int k=0;k<pdbInfoSeq.size();k++)
				{
				    seq=((jet.data.dataformat.info.PdbSequenceInfo)pdbInfoSeq.get(k)).getSequence();
				    percentIdentity=seqRef.getLocalIdentity(seq);
				    /* Si l'identité est superieure à 0.95 on calcul et ajoute à l'interface 
				     * de la sequence de reference les residus à l'interface de la sequence
				     *  homologue considérée */
				    if (percentIdentity>=0.95)
	        			{
					    /* "m" position dans les resultats d'interface de la sequence homologue */
					    m=0;
					    /* Boucle sur chaque residu des resultats d'interface de la seq ref */
					    for (int l=0;l<((Vector)((Vector)resultFinal.get(1)).get(j)).size();l++)
	        				{
						    /* Recuperation de la position du residu */
						    position=((Integer)((Vector)((Vector)resultFinal.get(1)).get(j)).get(l)).intValue();
						    /* recherche de cette position dans les resultats 
						     * d'interface de la sequence hommologue */
						    while (
							   (m<((Vector)((Vector)resultTempSeq.get(1)).get(k)).size())
							   &&(((Integer)((Vector)((Vector)resultTempSeq.get(1)).get(k)).get(m)).intValue()<position)
							   ) 
							m++;
						    /* Si tous les residus n'ont pas ete traités*/
						    if (m<((Vector)((Vector)resultTempSeq.get(1)).get(k)).size())
							{
							    /* Si le residu est trouvé et qu'il est à l'interface dans la structure homologue 
							     * on l'ajoute à l'interface de la structure de reference */
							    if (((Integer)((Vector)((Vector)resultTempSeq.get(1)).get(k)).get(m)).intValue()==position)
								{
								    
								    if(((Double)((Vector)((Vector)resultTempSeq.get(3)).get(k)).get(m)).doubleValue()>0.0)
									{
									    ((Vector)((Vector)resultFinal.get(3)).get(j)).set(l, ((Double)((Vector)((Vector)resultTempSeq.get(3)).get(k)).get(m)).doubleValue());
									    resultChaineTemp.set(l, ((Double)((Vector)((Vector)resultTempSeq.get(3)).get(k)).get(m)).doubleValue());
									}
								}
							    
							}
						    else break;
						    
	        				}
	        			}
				}
			    ((Vector)result.lastElement()).addAll(resultChaineTemp); 
	        	}
        	}catch (jet.exception.NaccessException exc)
        	{System.err.println("no naccess results for the pdb file:"+((File)pdbFiles.get(i)).getPath());}
    	}
    	
    	nom_colonnes.add("inter");
    	result.add(new Vector());

    	for(int i=0;i<((Vector)resultFinal.lastElement()).size();i++) 
    		((Vector)result.lastElement()).addAll(((Vector)((Vector)resultFinal.lastElement()).get(i))); 

    	String filename=pdbFileBound.getPath();
    	if (filename.lastIndexOf(".")!=-1) filename=filename.substring(0,filename.lastIndexOf("."));
    	
    	if (!new File(filename+"_axs.res").exists())
    	{
    		System.out.println("Missing access file "+filename+"_axs.res");
    		System.out.println("***** Access analysis of file "+filename+".pdb ******");
        	AccessAnalysis jet=new AccessAnalysis(cf);
		jet.setAccessType("chain");
        	jet.analyse(pdbFileBound);
		jet.setAccessType("complex");
        	jet.analyse(pdbFileBound);
        	System.out.println("***** End Access analysis ******");
    	}

	// remove residues whose rASAm < 0.25 (buried residues in the unbound form)
    	Vector axsResults=Result.readValuesResult(filename+"_u_axs.res");
	Vector nameAxsResults=Result.readCaracResult(filename+"_u_axs.res");
	int numCol=Result.searchNumCol(nameAxsResults,"axs");
	if (numCol!=-1)
	    {
		Vector axsValues=(Vector)axsResults.get(numCol);
		for(int i=0;i<((Vector)result.lastElement()).size();i++)
		    {
			if (Double.parseDouble((String)axsValues.get(i))<1.0) ((Vector)result.lastElement()).set(i,0.0);
		    }
		    }
    	// remove residues whose rASAc > 0.25 (surface residues in the bound form)
	axsResults=Result.readValuesResult(filename+"_b_axs.res");
	nameAxsResults=Result.readCaracResult(filename+"_b_axs.res");
	numCol=Result.searchNumCol(nameAxsResults,"axs");
	if (numCol!=-1)
	    {
		Vector axsValues=(Vector)axsResults.get(numCol);
		for(int i=0;i<((Vector)result.lastElement()).size();i++)
		    {
			if (Double.parseDouble((String)axsValues.get(i))==1.0) ((Vector)result.lastElement()).set(i,0.0);
		    }
	    }


    	Result.WriteResult(result, nom_colonnes, filename+"_inter.res");
    	//Result.cutPdbChainResult(filename+"_inter.res");
    	
    	Result.convertResultToPDB(filename+"_inter.res", pdbFileBound.getPath(), "inter",1);
    	//jet.io.file.PdbFileTransform pdbft;
    	//pdbft= new jet.io.file.PdbFileTransform(filename+"_inter.pdb");
		//pdbft.cut(new Vector());
    	
    }
      
    
}

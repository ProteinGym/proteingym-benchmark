package jet;

import java.util.*;
import java.io.*;

import jet.tools.MapClusters;

public class ClusterAnalysis {

	jet.ConfigFile cf;	

 	/* Cluster Parmeters */
	float maxDist;
     
    public ClusterAnalysis(jet.ConfigFile cf) 
    { 
	this.cf=cf; 
    }

    public void analyse(File pdbfile) throws jet.exception.NaccessException
    {
    	String filename=pdbfile.getAbsolutePath();
    	if (filename.lastIndexOf(".pdb")!=-1) filename=filename.substring(0,filename.lastIndexOf(".pdb"));
    	String resJetFilename=null;
    	String axsJetFilename=null;
    	String atomAxsJetFilename=null;
   	String cvJetFilename=null;
	String cvLocalJetFilename=null;
    	String nameTraceColumn;
    	String namepcColumn;
    	String analysis;
    	double coverage;
	int complete;
	int layers;

	/*	int p;
	double max=0.0;
	for (p=0;p<20;p++) if (jet.data.datatype.Residue.getResiduePC(p)>max) max=jet.data.datatype.Residue.getResiduePC(p);*/
    	
    	if(!new File(filename+"_axs.res").exists())
    	{
    		System.err.println("Missing access file "+filename+"_axs.res");
    		System.out.println("***** Access analysis of file "+filename+".pdb ******");
        	AccessAnalysis jet=new AccessAnalysis(cf);
        	jet.analyse(pdbfile);
        	System.out.println("***** End Access analysis ******");
    	}

    	if(!new File(filename+"_cv.res").exists())
    	{
    		System.err.println("Missing circular variance file "+filename+"_cv.res");
    		System.out.println("***** CV analysis of file "+filename+".pdb ******");
        	CVAnalysis jet=new CVAnalysis(cf,false);
        	jet.analyse(pdbfile);
        	System.out.println("***** End CV analysis ******");
    	}

    	if(!new File(filename+"_cvlocal.res").exists())
    	{
    		System.err.println("Missing circular variance file "+filename+"_cvlocal.res");
    		System.out.println("***** CVLOCAL analysis of file "+filename+".pdb ******");
        	CVAnalysis jet=new CVAnalysis(cf,true);
        	jet.analyse(pdbfile);
        	System.out.println("***** End CVLOCAL analysis ******");
    	}

	//	if(new File(filename+"_axs.res").exists())
	if(   (new File(filename+"_axs.res").exists()) & (new File(filename+"_cv.res").exists()) & (new File(filename+"_cvlocal.res").exists()) )
    	{
    		axsJetFilename=filename+"_axs.res";
    		cvJetFilename=filename+"_cv.res";
		cvLocalJetFilename=filename+"_cvlocal.res";
    		
    		if(new File(filename+"_atomAxs.pdb").exists()) 
		    atomAxsJetFilename=filename+"_atomAxs.pdb";
    		
    		analysis="";
    		coverage=-1;
		complete=-1;
    		layers=3;

    		nameTraceColumn="";
    		namepcColumn="";
    		
    		if(new File(filename+"_jet.res").exists()) 
    		{
    			resJetFilename=filename+"_jet.res";
    		
    			coverage=cf.getDoubleParam("Cluster","coverage");
    			analysis=cf.getParam("Cluster","analysis");
			layers=(int)cf.getIntParam("Cluster","layers");if (layers==(float)-1) layers=3;
			complete=(int)cf.getIntParam("Cluster","complete");
    			nameTraceColumn=cf.getParam("Cluster","nameTraceCol");
    			namepcColumn=cf.getParam("Cluster","namePcCol");
    			maxDist=(float)cf.getDoubleParam("Cluster","max_dist");if (maxDist==(float)-1) maxDist=(float)5.0;
    			
    		}
    		
	    	if ((!nameTraceColumn.equals(""))&&(!namepcColumn.equals("")))
	    		{

	    		Vector jetResultCarac=Result.readCaracResult(resJetFilename);
	    		Vector axsResultCarac=Result.readCaracResult(axsJetFilename);
	    		Vector cvResultCarac=Result.readCaracResult(cvJetFilename);
			Vector cvLocalResultCarac=Result.readCaracResult(cvLocalJetFilename);
	    		Vector jetResult=Result.readValuesResult(resJetFilename);
	    		Vector axsResult=Result.readValuesResult(axsJetFilename);
	    		Vector cvResult=Result.readValuesResult(cvJetFilename);
			Vector cvLocalResult=Result.readValuesResult(cvLocalJetFilename);
	    		
	    		if ((jetResult.size()>0)&&(axsResult.size()>0)&&((Vector)jetResult.get(0)).size()==((Vector)axsResult.get(0)).size())
	    		{
			    Vector traceAxs=new Vector(),pcAxs=new Vector(),  scoreAxs=new Vector(),axs=new Vector(), cv=new Vector(), cvlocal=new Vector();
		    		
			    int numColJetTrace=Result.searchNumCol(jetResultCarac, nameTraceColumn);			    
			    int numColJetpc=Result.searchNumCol(jetResultCarac, namepcColumn);
			    
			    int numColAxsAxs=Result.searchNumCol(axsResultCarac, "axs");
			    int numColAxsChains=Result.searchNumCol(axsResultCarac, "chain");
			    int numColCvCv=Result.searchNumCol(cvResultCarac, "cv");
			    int numColCvLocalCv=Result.searchNumCol(cvLocalResultCarac, "cv");
			    int numColCvChains=Result.searchNumCol(cvResultCarac, "chain");
			    int numColCvLocalChains=Result.searchNumCol(cvLocalResultCarac, "chain");
			    int numColJetChains=Result.searchNumCol(jetResultCarac, "chain");
			    int numColJetCode=Result.searchNumCol(jetResultCarac, "AA");
			    int numColJetPos=Result.searchNumCol(jetResultCarac, "pos");
			    
			    if ((numColJetTrace!=-1)&&(numColJetpc!=-1)&&(numColAxsAxs!=-1)&&(numColAxsChains!=-1)&&(numColJetChains!=-1)&&(numColJetCode!=-1)&&(numColJetPos!=-1))	
		    		{
				    jet.io.file.PdbFileReader pdb;
				    Vector pdbInfo;
				    if (atomAxsJetFilename==null)
					{
					    /* Lecture du fichier pdb */
					    pdb=new jet.io.file.PdbFileReader(pdbfile.getPath());
					    /* Recupération par le parseur des infos de structure 3D */
					    pdbInfo=jet.data.dataformat.parser.PDB.getSequenceInfo(pdb,false);
					}
				    else
					{
					    /* Lecture du fichier pdb */
					    pdb=new jet.io.file.PdbFileReader(atomAxsJetFilename);
					    /* Recupération par le parseur des infos de structure 3D */
					    pdbInfo=jet.data.dataformat.parser.PDB.getSequenceInfo(pdb,true);
					}	
				    Vector clustered=new Vector();
				    clustered.add(new Vector()); // cluster values
				    clustered.add(new Vector()); // cluster number
				    
				    Vector codes=new Vector(), positions=new Vector(), chains=new Vector();
				    
				    //Vector traceMoy=new Vector(), pcMoy=new Vector(),scoreMoy=new Vector();
				    
				    int i=0;
				    String chainID="";
				    
				    /* for each file analysed we reset the cluster count */
				    MapClusters.reset_cluster_count();
				    int countRes=0;
				    int k=0;
				    while(i<pdbInfo.size())
					{
					    traceAxs.clear();
					    pcAxs.clear();
					    scoreAxs.clear();
					    axs.clear();
					    cv.clear();
					    cvlocal.clear();
					    jet.data.datatype.Sequence3D seq=((jet.data.dataformat.info.PdbSequenceInfo)pdbInfo.get(i)).getSequence();	
					    System.out.println(""+nameTraceColumn+" cluster analysis of sequence: "+seq.getSequenceName());		       
					    chainID=seq.getChainId();
					    countRes=k+seq.size();

					    while(k<countRes)
						    //for (int k=0;k<((Vector)jetResult.get(numColJetTrace)).size();k++)
						{
						    if ((chainID.equals(((Vector)jetResult.get(numColJetChains)).get(k)))
							&&(chainID.equals(((Vector)axsResult.get(numColAxsChains)).get(k)))
							&&(chainID.equals(((Vector)cvResult.get(numColCvChains)).get(k)))
							&&(chainID.equals(((Vector)cvLocalResult.get(numColCvLocalChains)).get(k))))
					    		{
							    if (Double.parseDouble((String)((Vector)axsResult.get(numColAxsAxs)).get(k))==1.0)
								//if (Double.parseDouble((String)((Vector)axsResult.get(numColAxsAxs)).get(k))>=0.0)
								//if (Double.parseDouble((String)((Vector)axsResult.get(numColAxsAxs)).get(k))>0.5)
					    			{
								    traceAxs.add(Double.parseDouble((String)((Vector)jetResult.get(numColJetTrace)).get(k)));
								    pcAxs.add(Double.parseDouble((String)((Vector)jetResult.get(numColJetpc)).get(k)));
								    scoreAxs.add(Double.parseDouble((String)((Vector)jetResult.get(numColJetTrace)).get(k))*Math.sqrt(Double.parseDouble((String)((Vector)jetResult.get(numColJetpc)).get(k))*2.21));
								    //scoreAxs.add(Double.parseDouble((String)((Vector)jetResult.get(numColJetTrace)).get(k))*Math.sqrt(Double.parseDouble((String)((Vector)jetResult.get(numColJetpc)).get(k))*1.87));
								    axs.add(1.0);
								    cv.add(Double.parseDouble((String)((Vector)cvResult.get(numColCvCv)).get(k)));
								    cvlocal.add(Double.parseDouble((String)((Vector)cvLocalResult.get(numColCvLocalCv)).get(k)));
					    			}
							    else
					    			{
								    traceAxs.add(0.0);
								    pcAxs.add(0.0);
								    scoreAxs.add(0.0);
								    axs.add(0.0);
								    cv.add(0.0);
								    cvlocal.add(0.0);
					    			}
							    
							    codes.add(((Vector)jetResult.get(numColJetCode)).get(k));
							    //positions.add(Integer.parseInt((String)((Vector)jetResult.get(numColJetPos)).get(k)));
							    positions.add((String)((Vector)jetResult.get(numColJetPos)).get(k));
							    chains.add(((Vector)jetResult.get(numColJetChains)).get(k));
							    k++;
					    		}
						    else{
							System.out.println("Error: chain interrupted");
							k=10000;
						    }
						    
					    	}
					    
					    //if((seq.size()>20) && (seq.isProtein()) && jet.tools.OrderValue.hasValue(traceAxs))
					    if((seq.size()>20) && (seq.isProtein()))
						{
						    // identify clusters
						    //System.out.print("identify clusters in: ");
						    //System.out.println(seq);
						    Vector tmp = jet.tools.MapClusters.map(seq,traceAxs,pcAxs,axs,cv, cvlocal, maxDist, analysis,complete,layers,coverage);
						    // mtrace(j)
						    ((Vector)clustered.get(0)).addAll((Vector)tmp.get(0));
						    // cluster number
						    ((Vector)clustered.get(1)).addAll((Vector)tmp.get(1));
						}
					    else
					    	{
						    ((Vector)clustered.get(0)).addAll(fillVector(seq.size()));
						    ((Vector)clustered.get(1)).addAll(fillVector(seq.size()));
						    //clustered.add(fillVector(seq.size()));
					    	}
					    i++;
					}
				    
				    /* Ecriture dans nomfichier_clusters.res des informations sur les 
				     * residus clusterisés */
				    
				    Vector nom_colonnes=new Vector(5);
				    Vector result=new Vector(5);
				    nom_colonnes.add("AA");nom_colonnes.add("pos");nom_colonnes.add("chain");nom_colonnes.add("clusters");
				    nom_colonnes.add("clusnumber");
				    result.add(codes);result.add(positions);result.add(chains);result.add((Vector)(clustered.get(0)));
				    result.add((Vector)(clustered.get(1)));
				    Result.WriteResult(result, nom_colonnes, filename+"_clusters.res");
				    //Result.cutPdbChainResult(filename+"_clusters.res");
				    
				    Result.convertResultToPDB(filename+"_clusters.res", pdbfile.getPath(), "clusters",1);
				    //jet.io.file.PdbFileTransform pdbft;
				    //pdbft= new jet.io.file.PdbFileTransform(filename+"_clusters.pdb");
				    //pdbft.cut(new Vector());
				    
				    File atomAxsFile;
				    if ((atomAxsJetFilename!=null)&&((atomAxsFile=new File(atomAxsJetFilename)).exists())) atomAxsFile.delete();
				    
				    /*
				      
				      pdbft= new jprotein.io.file.PdbFileTransform(pdbfile.getPath(), "_traceMoy", traceMoy);
				      pdbft= new jprotein.io.file.PdbFileTransform(filename+"_traceMoy.pdb");
				      pdbft.cut(new Vector());
				      
				      pdbft= new jprotein.io.file.PdbFileTransform(pdbfile.getPath(), "_pcMoy", pcMoy);
				      pdbft= new jprotein.io.file.PdbFileTransform(filename+"_pcMoy.pdb");
				      pdbft.cut(new Vector());
				      
				      pdbft= new jprotein.io.file.PdbFileTransform(pdbfile.getPath(), "_scoreMoy", scoreMoy);
				      pdbft= new jprotein.io.file.PdbFileTransform(filename+"_scoreMoy.pdb");
				      pdbft.cut(new Vector());
				      
				    */
				    
		    		}
		    		else System.err.println("données non trouvées dans les fichiers de resultats jet et/ou d'accessibilité");
	    		}
	    		else System.err.println("fichier de resultats jet et d'accessibilité incompatibles");
			}
    	}
    	else System.err.println("fichier d'accessibilité manquant");
    }
    
    /** Methode pour retourner un vecteur de taille size rempli de 0.0 */
	 
    public Vector fillVector(int size)
    {
	int i;
	Vector v=new Vector(size);
	for(i=0;i<size;i++) v.add(new Double(0.0)); 
	return v;
    }
    
}

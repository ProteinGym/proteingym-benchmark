package jet;

import java.util.*;
import java.io.*;

public class ETResultAnalysis {

	double coverage;
	
	public ETResultAnalysis() {}
	
    public ETResultAnalysis(double coverage) 
    { 
	this.coverage=coverage; 
    }
    
    public void analyse(File pdbfile)
    {
    	Vector ori=new Vector();
    	String pdbCode=pdbfile.getAbsolutePath();
    	String chainID="";
    	int posChainID=-1;
    	pdbCode=pdbCode.substring(pdbCode.lastIndexOf("/")+1,pdbCode.lastIndexOf("/")+5);
    	pdbCode=pdbCode.toUpperCase();
    	String prefix=pdbfile.getAbsolutePath().substring(pdbfile.getAbsolutePath().lastIndexOf("/")+1,pdbfile.getAbsolutePath().lastIndexOf(".pdb"));
    	File directory=pdbfile.getParentFile();
    	File[] directoryFiles=null;
    	if(directory!=null) directoryFiles=directory.listFiles();
    	
    	/* Lecture du fichier pdb */	
		jet.io.file.PdbFileReader pdb=new jet.io.file.PdbFileReader(pdbfile.getPath());
		Vector pdbInfo=jet.data.dataformat.parser.PDB.getSequenceInfo(pdb,false);
    	
		Vector ETRes = new Vector();		
    	Vector ETResClusTemp = new Vector();
    	Vector ETResTraceTemp = new Vector();
    	
    	for(int j=0;j<directoryFiles.length;j++)
    	{
    		//System.out.println("1");
    		if ((directoryFiles[j].getAbsolutePath().toUpperCase().lastIndexOf(pdbCode)!=-1)
    				&&(directoryFiles[j].getAbsolutePath().lastIndexOf(".etvx")!=-1))
    		{
    			posChainID=directoryFiles[j].getAbsolutePath().toUpperCase().lastIndexOf(pdbCode)+4;
    			chainID=directoryFiles[j].getAbsolutePath().toUpperCase().substring(posChainID, posChainID+1);
    			//System.out.println("chain:"+chainID);
    			jet.io.file.ETFileReader ETFileReader=new jet.io.file.ETFileReader(directoryFiles[j].getAbsolutePath());
    			
    			String rhoValue=ETFileReader.extractRhoValue(0.25);
    			ETResTraceTemp=ETFileReader.getValueData();
    			//System.out.println("*********** rhoValue:"+rhoValue);
    			Vector clustersPosition=new Vector();
    			for(int k=0;k<directoryFiles.length;k++)
    	    	{
    				if ((directoryFiles[k].getAbsolutePath().lastIndexOf(".clusters")!=-1)
    						&&(directoryFiles[k].getAbsolutePath().toUpperCase().lastIndexOf(pdbCode+chainID)!=-1))	
    	    		{
    				//	System.out.println("*********** fichier:"+directoryFiles[k].getAbsolutePath());
    					ETFileReader=new jet.io.file.ETFileReader(directoryFiles[k].getAbsolutePath());
    					clustersPosition=ETFileReader.extractClusterData(rhoValue);
    					
    	    		}
    	    	}
    			
    			for (int i=0;i<pdbInfo.size();i++)
    	    	{	
    				
    				jet.data.datatype.Sequence3D seq=((jet.data.dataformat.info.PdbSequenceInfo)pdbInfo.get(i)).getSequence();
    	    		
    				if (seq.getChainId().equals(chainID))
    	    		{
    					
    					/*
    					
            			System.out.println("size:"+ETResTraceTemp.size());
            			System.out.println("size0:"+((Vector)ETResTraceTemp.get(0)).size());
            			System.out.println("size1:"+((Vector)ETResTraceTemp.get(1)).size());
            			System.out.println("size2:"+((Vector)ETResTraceTemp.get(2)).size());
            			
            			*/
            			            			
            			ori.clear();
            			double valueMax=0.0;
            			double value=0.0;
            			ETResTraceTemp.add(2, new Vector());
            			for(int l=0;l<((Vector)ETResTraceTemp.get(3)).size();l++)
            			{
            				//System.out.println("3");
            				((Vector)ETResTraceTemp.get(2)).add(chainID);
            				value=Double.parseDouble((String)((Vector)ETResTraceTemp.get(3)).get(l));
            				if (value>valueMax) 
            				{
            					valueMax=value;
            					//System.out.println("4:"+valueMax);
            				}
            			}
            			
            			for(int l=0;l<((Vector)ETResTraceTemp.get(3)).size();l++)
            			{
            				value=Double.parseDouble((String)((Vector)ETResTraceTemp.get(3)).get(l));
            				value=1-(value/valueMax);
            				((Vector)ETResTraceTemp.get(3)).set(l, value);
            				//System.out.println("5:"+value);
            			}
            			
            			ori.addAll(((Vector)ETResTraceTemp.get(3)));
            			Vector v=jet.tools.OrderValue.orderProperty(ori);
            			//Positions des proprietes 
            			Vector oPos=(Vector)v.get(0);
            			//Proprietes 
            			Vector oProp=(Vector)v.get(1);
            			
            			//System.out.println("oPropSize:"+oProp.size());
            			//System.out.println("oPosSize:"+oProp.size());
            			
            			/*
            			
            			for (int nb=(int)(oProp.size()*0.25);nb<oProp.size();nb++)
            			{
            			//	System.out.println("oPos:"+oPos.get(nb)+" nb:"+nb);
            				((Vector)ETResTraceTemp.get(3)).set(((Integer)oPos.get(nb)).intValue(), 0.0);
            			}
            			
            			*/
    					
            			jet.Result.removeLines(1, "-", ETResTraceTemp);
            			
            			/*
            			
            			for(int l=0;l<((Vector)ETResTraceTemp.get(3)).size();l++)
            			{
            				System.out.println(""+((Vector)ETResTraceTemp.get(0)).get(l)+" "+((Vector)ETResTraceTemp.get(1)).get(l)+" "+((Vector)ETResTraceTemp.get(2)).get(l)+" "+((Vector)ETResTraceTemp.get(3)).get(l));
            			}
            			
            			*/
            			
    					Vector positions=new Vector();
        	    		for(int k=0;k<clustersPosition.size();k++)
    	    	    	{
        	    			positions.addAll((Vector)clustersPosition.get(k));
    	    	    	}
        	    		//System.out.println("*********** position des clusters ");
        	    		//for(int k=0;k<positions.size();k++)
    	    	    	//{
        	    		//	System.out.print(" "+positions.get(k));
    	    	    	//}
        	    		//System.out.println("");
    					ETResClusTemp=new Vector();
    	    			for(int k=0;k<5;k++)
    	    	    	{
    	    				ETResClusTemp.add(new Vector());
    	    	    	}
    	    			for(int k=0;k<seq.size();k++)
    	    	    	{
    	    				((Vector)ETResClusTemp.get(0)).add(""+seq.getResidue(k).getResidueCode());
    	    				((Vector)ETResClusTemp.get(1)).add(""+seq.getResidue(k).getPosition());
    	    				((Vector)ETResClusTemp.get(2)).add(""+seq.getChainId());
    	    				
    	    				if (positions.contains(seq.getResidue(k).getPosition()))
    	    					((Vector)ETResClusTemp.get(3)).add(1.0);
    	    				else
    	    					((Vector)ETResClusTemp.get(3)).add(0.0);
    	    				
    	    				//jprotein.Result.searchNumLine(1,(String)((Vector)ETResTraceTemp.get(1)).get(k) , ETResClusTemp);
    	    				
    	    				//if (((String)((Vector)ETResTraceTemp.get(1)).get(k)).equals(""+seq.getResidue(k).getPosition())) 
    	    				((Vector)ETResClusTemp.get(4)).add(""+((Vector)ETResTraceTemp.get(3)).get(jet.Result.searchNumLine(1, ""+seq.getResidue(k).getPosition(), ETResTraceTemp)));
    	    				//else
    	    				//{
    	    				//	((Vector)ETResClusTemp.get(3)).add(-1.0);
    	    				//	System.out.println("err:"+((Vector)ETResTraceTemp.get(1)).get(k)+"!="+seq.getResidue(k).getPosition());
    	    				//}
    	    	    	}
    	    			if (ETRes.size()==0)
    	    			{
    	    				ETRes = ETResClusTemp;
    	    				//System.out.println("6");
    	    			}
    	    			else
    	    			{
    	    				for(int k=0;k<ETRes.size();k++) ((Vector)ETRes.get(k)).addAll((Vector)ETResClusTemp.get(k));
    	    				//System.out.println("7");
    	    			}
    	    		}
    	    			
    	    	}
    			
    		}
    	}
    	
    	
    	
    	//System.out.println("****************taille etres avant:"+((Vector)ETRes.get(0)).size());
    	
    	//System.out.println("taille:"+ETRes.size());
    	//System.out.println("taille col:"+((Vector)ETRes.get(0)).size());
    	
    	if(ETRes.size()==0)
    	{
    		for (int i=0;i<pdbInfo.size();i++)
	    	{
    			jet.data.datatype.Sequence3D seq=((jet.data.dataformat.info.PdbSequenceInfo)pdbInfo.get(i)).getSequence();
    			ETResClusTemp=new Vector();
    			for(int k=0;k<5;k++) ETResClusTemp.add(new Vector());
    			for(int k=0;k<seq.size();k++)
    	    	{
    				((Vector)ETResClusTemp.get(0)).add(""+seq.getResidue(k).getResidueCode());
    				((Vector)ETResClusTemp.get(1)).add(""+seq.getResidue(k).getPosition());
    				((Vector)ETResClusTemp.get(2)).add(""+seq.getChainId());
    				((Vector)ETResClusTemp.get(3)).add(0.0);
    				((Vector)ETResClusTemp.get(4)).add("0.0");
    	    	}
    			if (ETRes.size()==0) ETRes = ETResClusTemp;
    			else for(int k=0;k<ETRes.size();k++) ((Vector)ETRes.get(k)).addAll((Vector)ETResClusTemp.get(k));
	    	}
    	}
    	
    	for (int i=0;i<((Vector)ETRes.get(1)).size();i++)
    	{
    		try
    		{
    		Integer.parseInt(((String)((Vector)ETRes.get(1)).get(i)));
    		}catch (NumberFormatException excp)
    		{
    			//System.out.println("position:"+((String)((Vector)ETRes.get(1)).get(i)));
    			for (int j=0;j<ETRes.size();j++)
            	{
    				((Vector)ETRes.get(j)).remove(i);
            	}
    			i--;
    		}
    	}
    	
		/* Recupération par le parseur des infos sur les codes enzymes */
    	Vector resMod=jet.data.dataformat.parser.PDB.getModResidus(pdb);
    
    	//System.out.println("res mod:");
		//for (int z=0;z<resMod.size();z++) System.out.println(""+((String)resMod.get(z)));  	
    	
    	//System.out.println("ETRes:");
    	//for (int z=0;z<((Vector)ETRes.get(0)).size();z++)
		//	System.out.println(""+((Vector)ETRes.get(0)).get(z)+" "+((Vector)ETRes.get(1)).get(z)+" "+((Vector)ETRes.get(2)).get(z)+" "+((Vector)ETRes.get(3)).get(z));
		boolean present;
    	
    	String pos;
    	String chaine;
    	for (int i=0;i<((Vector)ETRes.get(0)).size();i++)
    	{
    		present = false;
    		pos=((String)((Vector)ETRes.get(1)).get(i));
    		chaine=((String)((Vector)ETRes.get(2)).get(i));
    		
    		for (int j=0;j<resMod.size();j++)
        	{
	    		if ((chaine.equals(((String)resMod.get(j)).split("\\s+")[0].trim()))
	    				&&(pos.equals(((String)resMod.get(j)).split("\\s+")[1].trim()))) present = true;
        	}
    		//System.out.println(""+present);
    		if (present)
    		{
    			//System.out.println("pos:"+pos+" chaine:"+chaine);
    			for (int j=0;j<ETRes.size();j++)
            	{
    				((Vector)ETRes.get(j)).remove(i);
            	}
    			i--;
    		}
    	}
    	
    	//System.out.println("ETRes:");
    	//for (int z=0;z<((Vector)ETRes.get(0)).size();z++)
		//	System.out.println(""+((Vector)ETRes.get(0)).get(z)+" "+((Vector)ETRes.get(1)).get(z)+" "+((Vector)ETRes.get(2)).get(z)+" "+((Vector)ETRes.get(3)).get(z));
		
    	
    	Vector nom_colonnes=new Vector(5);
    	nom_colonnes.add("AA");nom_colonnes.add("pos");nom_colonnes.add("chain");nom_colonnes.add("ETclusters");nom_colonnes.add("ETtrace");
    	
    	Vector ETResFinal = new Vector();
    	Vector ETResAll =new Vector();
    	boolean dejaVuSequence;
    	int ouVuSequence,nb;
    	int numCol, numLine;
    	double maxIdentity;
    	double tempIdentity;
    	Vector allSequence= new Vector();
    	for (int i=0;i<pdbInfo.size();i++)
    	{
    		
    		allSequence.add(((jet.data.dataformat.info.PdbSequenceInfo)pdbInfo.get(i)).getSequence());
    		chainID=((jet.data.datatype.Sequence3D)allSequence.get(i)).getChainId();
    		
    		
    		dejaVuSequence=false;
			ouVuSequence=i;
			
			//System.out.println("seq:"+i);
			//System.out.println("chaine:"+chainID);
    		
			nb=0;
			maxIdentity=0.0;
			tempIdentity=0.0;
			
			while (nb<i)
		    {
				//System.out.print(" i:"+i+" nb:"+nb);
				tempIdentity=((jet.data.datatype.Sequence3D)allSequence.get(i)).getGlobalIdentity((jet.data.datatype.Sequence3D)allSequence.get(nb));
				//System.out.println("tempIdentity:"+tempIdentity);
				if (tempIdentity>0.95)
				{
					dejaVuSequence=true;
					//System.out.print(" true");
					if (tempIdentity>maxIdentity)
					{
						ouVuSequence=nb;
						maxIdentity=tempIdentity;
						
					}
				}
				nb++;	
				//System.out.println("deja vu:"+dejaVuSequence+",ou vu:"+ouVuSequence);
		    }
			//System.out.println(" ouVuSequence:"+ouVuSequence+" dejaVuSequence:"+dejaVuSequence+" maxIdentity"+maxIdentity);
    		if (!dejaVuSequence)
    		{
    			//System.out.println("pas deja vu");
    			ETResClusTemp=Result.searchLines(2, chainID, ETRes);
    			if (((Vector)ETResClusTemp.get(0)).size()==0)
    			{
    				//System.out.println("pas de res et, taille:"+((jprotein.data.datatype.Sequence3D)allSequence.get(i)).size());
	    			for (int j=0;j<((jet.data.datatype.Sequence3D)allSequence.get(i)).size();j++)
	        	   	{
	    				numCol=Result.searchNumCol(nom_colonnes,"AA");
	        			((Vector)ETResClusTemp.get(numCol)).add(((jet.data.datatype.Sequence3D)allSequence.get(i)).getResidue(j).getResidueCode());
	       				numCol=Result.searchNumCol(nom_colonnes,"pos");
	       				((Vector)ETResClusTemp.get(numCol)).add(((jet.data.datatype.Sequence3D)allSequence.get(i)).getResidue(j).getPosition());
	       				numCol=Result.searchNumCol(nom_colonnes,"chain");
	       				((Vector)ETResClusTemp.get(numCol)).add(chainID);
	       				numCol=Result.searchNumCol(nom_colonnes,"ETtrace");
	       				((Vector)ETResClusTemp.get(numCol)).add(0.0);
	       				numCol=Result.searchNumCol(nom_colonnes,"ETclusters");
	       				((Vector)ETResClusTemp.get(numCol)).add(0.0);
	       	    	}
   				}
    			//else System.out.println("deja res et, taille:"+((Vector)ETResClusTemp.get(0)).size());
    			ETResAll.add(ETResClusTemp);
    		}
    		else
    		{
    			//System.out.println("deja vu");
    			//System.out.println("identity "+i+" "+ouVuSequence+":"+maxIdentity);
    			if (maxIdentity!=1.0)
				{
    			//	System.out.println("pas 100% identique");
    				ETResClusTemp=Result.searchLines(2, chainID, ETRes);
    				if (((Vector)ETResClusTemp.get(0)).size()==0)
        			{
    					//System.out.println("pas de res et, taille:"+((jprotein.data.datatype.Sequence3D)allSequence.get(i)).size());
						for (int j=0;j<((jet.data.datatype.Sequence3D)allSequence.get(i)).size();j++)
	        	    	{
							numCol=Result.searchNumCol(nom_colonnes,"pos");
							((Vector)ETResClusTemp.get(numCol)).add(((jet.data.datatype.Sequence3D)allSequence.get(i)).getResidue(j).getPosition());
							numLine=Result.searchNumLine(numCol, ""+((jet.data.datatype.Sequence3D)allSequence.get(i)).getResidue(j).getPosition(), (Vector)ETResAll.get(ouVuSequence));
							numCol=Result.searchNumCol(nom_colonnes,"ETclusters");
							if(numLine!=-1)
		        				((Vector)ETResClusTemp.get(numCol)).add(((Vector)((Vector)ETResAll.get(ouVuSequence)).get(numCol)).get(numLine));
							else
								((Vector)ETResClusTemp.get(numCol)).add(0.0);
							numCol=Result.searchNumCol(nom_colonnes,"ETtrace");
							if(numLine!=-1)
		        				((Vector)ETResClusTemp.get(numCol)).add(((Vector)((Vector)ETResAll.get(ouVuSequence)).get(numCol)).get(numLine));
							else
								((Vector)ETResClusTemp.get(numCol)).add(0.0);
	    					numCol=Result.searchNumCol(nom_colonnes,"AA");
	        				((Vector)ETResClusTemp.get(numCol)).add(((jet.data.datatype.Sequence3D)allSequence.get(i)).getResidue(j).getResidueCode());
	        				numCol=Result.searchNumCol(nom_colonnes,"chain");
	        				((Vector)ETResClusTemp.get(numCol)).add(chainID);
	        				
	        	    	}
        			}
    				//else System.out.println("deja res et, taille:"+((Vector)ETResClusTemp.get(0)).size());
				}
    			else
    			{
    				
	    			ETResClusTemp=(Vector)ETResAll.get(ouVuSequence);
	    			//System.out.println("100% identique, taille:"+((Vector)ETResClusTemp.get(0)).size());
	    			numCol=Result.searchNumCol(nom_colonnes,"chain");
	    			for (int j=0;j<((Vector)ETResClusTemp.get(numCol)).size();j++)
	    	    	{
	    				((Vector)ETResClusTemp.get(numCol)).set(j, chainID);
	    	    	}
    			}
    			ETResAll.add(ETResClusTemp);
    		}
	    	//	System.out.println("ETResClusTemp:");
	    		//for (int z=0;z<((Vector)ETResClusTemp.get(0)).size();z++)
				//	System.out.println(""+((Vector)ETResClusTemp.get(0)).get(z)+" "+((Vector)ETResClusTemp.get(1)).get(z)+" "+((Vector)ETResClusTemp.get(2)).get(z)+" "+((Vector)ETResClusTemp.get(3)).get(z))
	    		Result.addLines(ETResFinal, (Vector)ETResAll.lastElement());
    		
    		
    	}
    	
    	//System.out.println("*******************taille etres apres:"+((Vector)ETResFinal.get(0)).size());
    	
    	Result.WriteResult(ETResFinal, nom_colonnes, directory.getAbsolutePath()+"/"+prefix+"_et.res");
    	//System.out.println(""+directory.getAbsolutePath()+"/"+prefix+"_et.res");
    	Result.convertResultToPDB(directory.getAbsolutePath()+"/"+prefix+"_et.res", pdbfile.getAbsolutePath(), "ETclusters",1);
    	jet.io.file.PdbFileTransform pdbft;
    	pdbft= new jet.io.file.PdbFileTransform(directory.getAbsolutePath()+"/"+prefix+"_ETclusters.pdb");
		pdbft.cut(new Vector(),true);
		
		Result.convertResultToPDB(directory.getAbsolutePath()+"/"+prefix+"_et.res", pdbfile.getAbsolutePath(), "ETtrace",1);
    	pdbft= new jet.io.file.PdbFileTransform(directory.getAbsolutePath()+"/"+prefix+"_ETtrace.pdb");
		pdbft.cut(new Vector(),true);
		
		
    } 	
}



package jet;

import java.util.*;
import java.io.*;

public class AccessAnalysis {

    jet.ConfigFile cf;	
    double axs_res_cutoff;
    double axs_atom_cutoff;
    float probe_radius;
    String accessType;
    Boolean suffixispresent;
    // addition for maximum contact distance
    float maxDist;
    
    public AccessAnalysis(jet.ConfigFile cf) 
    { 
	this.cf=cf; 
	this.probe_radius=(float)cf.getDoubleParam("Access","probe_radius");if (probe_radius==(float)-1) probe_radius=(float)1.4;
	this.axs_res_cutoff=cf.getDoubleParam("Access","res_cutoff");if (axs_res_cutoff==-1) axs_res_cutoff=0.05;
	this.axs_atom_cutoff=cf.getDoubleParam("Access","atom_cutoff");if (axs_atom_cutoff==-1) axs_atom_cutoff=0.01;
	this.accessType=cf.getParam("Access","accessType");
	this.maxDist=(float)cf.getDoubleParam("Access","max_dist");if (maxDist==(float)-1) maxDist=(float)5.0;
	this.suffixispresent=false;

    }

    public void setAccessType(String overrideType)
    {
	this.accessType=overrideType;
	suffixispresent=true;
    }
    
    public void analyse(File pdbfile) throws jet.exception.NaccessException
    {
    	
    	/************************Reading line command arguments***************************************/
    	
        /* Lecture du fichier pdb */
    	jet.io.file.PdbFileReader pdb=new jet.io.file.PdbFileReader(pdbfile.getPath());
    	/* Recupération par le parseur des infos de structure 3D */
    	Vector pdbInfo=jet.data.dataformat.parser.PDB.getSequenceInfo(pdb,false);
    	int i,j,k;
    	
    	Vector resNameAtom=new Vector(),chainNameAtom=new Vector(),resPosAtom=new Vector(),atomName=new Vector();
    	Vector atomPos=new Vector(),atomSurfAxs=new Vector(),atomAxs=new Vector();
    	
    	Vector axs=new Vector();
	// addition for close to a surface residue
	// Vector axsClose=new Vector();
    	Vector surfAxs=new Vector();
    	Vector percentSurfAxs=new Vector();
    	/* Vecteurs contenant les informations sur le type et la position des 
    	 * residus des differentes chaines. Ces vecteurs sont utilisés pour 
    	 * ecrire en sortie les resultats de l'analyse des interfaces */
    	Vector codes=new Vector(), positions=new Vector(), chains=new Vector();
    	int nb=0;
    	boolean dejaVuSeq=false;

    	jet.io.file.PdbFileTransform pdbft;
    	pdbft= new jet.io.file.PdbFileTransform(pdbfile.getPath(),"_naccessAnalyseCutFilesTemp");
    	
	Vector cut=new Vector();
	
	if (this.accessType.equals("chain"))
	    {
		for (int h=0;h<pdbInfo.size();h++)
		    {
			cut.add(new Vector());
			((Vector)cut.lastElement()).add(h);
		    }
	    }
	
	if (this.accessType.equals("complex"))
	    {
		cut.add(new Vector());
		for (int h=0;h<pdbInfo.size();h++)
		    {
			((Vector)cut.lastElement()).add(h);
		    }
	    }
	
	File dir=pdbfile.getParentFile();
	File[] dirFiles;
	Vector resultResAxs=new Vector();
	Vector resultAtomAxs=new Vector();
	Vector[] resultAxs;
	jet.data.datatype.Sequence3D seq;
	
	i=0;
	j=0;
	k=0;

	while(i<cut.size())
	    {
		// in the case of complex option
		for (j=0;j<((Vector)cut.get(i)).size();j++)
		    {
			if (((Vector)cut.get(i)).size()>1)
			    {
				k=((Integer)((Vector)cut.get(i)).get(j));
				seq=((jet.data.dataformat.info.PdbSequenceInfo)pdbInfo.get(k)).getSequence();
				/*	if(!((seq.size()>20) && (seq.isProtein())))
				    {
					System.out.println("bad seq");
					cut.add(0,new Vector());
					System.out.println(cut);
					i++;
					System.out.println(i);
					System.out.println(j);
					((Vector)cut.get(0)).add(((Vector)cut.get(i)).remove(j));
					j--;
					}*/
			    }
		    }
		i++;
	    }
	
	i=0;
	j=0;
	k=0;
	
    	/* Calcul des residus accessibles et regroupement des informations   
    	 * des accessibilités des chaines identiques 
    	 * Remarque : un residu est accessible si il l'est dans au moins une des chaines */


	while(i<cut.size())
	    {
		// in the case of chain option
		if (((Vector)cut.get(i)).size()==1)
		    {
			seq=((jet.data.dataformat.info.PdbSequenceInfo)pdbInfo.get((Integer)((Vector)cut.get(i)).get(0))).getSequence();
			if((seq.size()>20) && (seq.isProtein()))
			    seq=null;
		    }
		// in the case of complex option
		else seq=null;
    		if(seq==null)
		    {
    			/* quick and dirty hack, do not cut the files when complex is taken into account */
    			if ( this.accessType.equals("chain") ) pdbft.cut((Vector)cut.get(i),false);
			else pdbft.writeComplex(true);
    			
	    		dirFiles=dir.listFiles();
	    		for (j=0;j<dirFiles.length;j++)
			    {		
	    			/* quick and dirty hack to test chain vs. complex */
	    			/* doing that we force to examine the complete pdb file instead of the cuts files */
				//	boolean condition = this.accessType.equals("chain") ? dirFiles[j].getAbsolutePath().lastIndexOf("_naccessAnalyseCutFilesTemp")!=-1 : dirFiles[j].getAbsolutePath().equals(pdbfile.getAbsolutePath());
				boolean condition = dirFiles[j].getAbsolutePath().lastIndexOf("_naccessAnalyseCutFilesTemp")!=-1;
		    		if (condition)
				    {
		    			if (JET.DEBUG) System.out.println("File: "+dirFiles[j].getAbsolutePath());
		    			
					// perform Naccess analysis
					resultAxs=jet.tools.MapAccess.resSurfNaccess(dirFiles[j],probe_radius,false,cf.getParam("Software","naccess"));
		        		resultResAxs=resultAxs[0];
					codes.add(resultResAxs.get(0));
					positions.add(resultResAxs.get(2));
					/*System.out.print("length of axs results:");
					System.out.println(((Vector)resultResAxs.get(2)).size());*/
					chains.add(resultResAxs.get(1));
					// convert asa percentage into 0 or 1 according to threshold
		    			//axs.add(jet.tools.MapAccess.mapToDouble((Vector)resultResAxs.get(4),axs_res_cutoff));
					axs.add(jet.tools.MapAccess.mapToDouble((Vector)resultResAxs.get(3),(Vector)resultResAxs.get(4),axs_res_cutoff));
	
					//System.out.print("AXS: ");
					//System.out.println(axs);	
					//System.out.print("Value of i: ");
					//System.out.println(i);	
					//System.out.print("Length of axs: ");
					//System.out.println(((Vector)axs.get(i)).size());
					//System.out.println(positions);
		        		surfAxs.add(resultResAxs.get(3));
		        		percentSurfAxs.add(resultResAxs.get(4));

					// addition for computing distance matrix
					if(this.accessType.equals("chain")) 
					    {
						jet.data.datatype.Sequence3D seqtmp=((jet.data.dataformat.info.PdbSequenceInfo)pdbInfo.get(i)).getSequence();
						jet.cluster.data.DistanceMap dm= new jet.cluster.data.DistanceMap(seqtmp,maxDist);
						jet.cluster.data.ProxList plVoisin;
						Vector axsClosetmp=new Vector(seqtmp.size());
						Vector axstmp=(Vector)axs.get(i);
						Boolean isneighbor;
						for (int ii=0;ii<seqtmp.size();ii++)
						    {
							isneighbor=false;
							if((Double)axstmp.get(ii)<1.0)
							    {
								plVoisin=dm.getProxList(ii);
								int jj=0;
								while((!isneighbor)&&(jj<plVoisin.getLength()))
								    
								    {
									if (
									    (plVoisin.getProxNode(jj).getDistance()< maxDist)
									    &&((Double)axstmp.get(plVoisin.getProxNode(jj).getRef().getId())!=0.0)
									    )
									    {
										isneighbor=true;
									    }
									jj++;
								    }
							    }
							if(isneighbor){axsClosetmp.add((Double)axstmp.get(ii)+0.5);}
							else{axsClosetmp.add(axstmp.get(ii));}
						    }
						
						axs.set(i,axsClosetmp);
						//	System.out.println("Size of axsClose");
						// System.out.println(axsClosetmp.size());
					    }
						   
					
					
		        		resultAtomAxs=resultAxs[1];

		        		resNameAtom.add(resultAtomAxs.get(0));
		        		chainNameAtom.add(resultAtomAxs.get(1));
		        		resPosAtom.add(resultAtomAxs.get(2));
		        		atomName.add(resultAtomAxs.get(3));
		        		atomPos.add(resultAtomAxs.get(4));
					// convert asa percentage into 0 or 1 according to threshold
		        		atomAxs.add(jet.tools.MapAccess.mapToDouble((Vector)resultAtomAxs.get(5),axs_atom_cutoff));
		        		atomSurfAxs.add(resultAtomAxs.get(5));
		        		
		        		
		        		if (((Vector)cut.get(i)).size()>1)
		        			dejaVuSeq=true;
		        		else
		        			dejaVuSeq=false;
			    		nb=0;
			    		
			    		/* On teste si la (ou les) chaines à la position i ont deja ete observées dans le fichier pdb */
			    		while((!dejaVuSeq)&&(nb<i))
					    {
			    			if (JET.DEBUG) System.out.println("nb:"+nb);
			    			if (JET.DEBUG) System.out.println("i:"+i);
			    			int y=(Integer)((Vector)cut.get(i)).get(0);
			    			seq=((jet.data.dataformat.info.PdbSequenceInfo)pdbInfo.get(y)).getSequence();
			    			if ((((Vector)cut.get(nb)).size()==1)&&(seq.isIdenticalSeq(((jet.data.dataformat.info.PdbSequenceInfo)pdbInfo.get((Integer)((Vector)cut.get(nb)).get(0))).getSequence())))
			    			{
			    				for (k=0;k<((Vector)axs.get(nb)).size();k++)
			    				{
			    					if(((Double)((Vector)axs.get(i)).elementAt(k)).doubleValue()==1.0)
								    {
			    						((Vector)axs.get(nb)).setElementAt(((Vector)axs.get(i)).elementAt(k), k);
								    }
			    				}
			    				if (((Vector)atomAxs.get(nb)).size()==((Vector)atomAxs.get(i)).size())
			    				{
				    				for (k=0;k<((Vector)atomAxs.get(nb)).size();k++)
				    				{
				    					if(((Double)((Vector)atomAxs.get(i)).elementAt(k)).doubleValue()==1.0)
				    						((Vector)atomAxs.get(nb)).setElementAt(((Vector)atomAxs.get(i)).elementAt(k), k);
				    				}
			    				}

			    				dejaVuSeq=true;
			    			}
			    			else
			    			{
			    			nb++;
			    			}
					    }
			    		// hack 3: do no delete the pdb file if we deal with complex !!!!
			    		//if(this.accessType.equals("chain"))
					dirFiles[j].delete();
				    }
			    }
		    }
    		else
    		{

    			codes.add(new Vector());
			positions.add(new Vector());
			chains.add(new Vector());
    			axs.add(new Vector());
			// addition
			//axsClose.add(new Vector());
        		surfAxs.add(new Vector());
        		percentSurfAxs.add(new Vector());
        		
        		resNameAtom.add(new Vector());
        		chainNameAtom.add(new Vector());
        		resPosAtom.add(new Vector());
        		atomName.add(new Vector());
        		atomAxs.add(new Vector());
        		atomPos.add(new Vector());
        		atomSurfAxs.add(new Vector());
        		
			// Get all info from Sequence3D object called seq and initialize asa values to 0.0
        		for (int pos=0;pos<seq.size();pos++)
        		{
        			((Vector)codes.lastElement()).add(((jet.data.datatype.Residue)seq.get(pos)).getResidueCode());
        			((Vector)positions.lastElement()).add(((jet.data.datatype.Residue)seq.get(pos)).getId());
        			((Vector)chains.lastElement()).add(seq.getChainId());
        			((Vector)axs.lastElement()).add((double)0.0);
				// addition
				//((Vector)axsClose.lastElement()).add((double)0.0);
        			((Vector)surfAxs.lastElement()).add((double)0.0);
        			((Vector)percentSurfAxs.lastElement()).add((double)0.0);
        			
        			Vector atoms=seq.getResidue(pos, 1).getAllAtoms();
        			for (int posAtom=0;posAtom<atoms.size();posAtom++)
            		{
        				((Vector)resNameAtom.lastElement()).add(((jet.data.datatype.Residue)seq.get(pos)).getResidueCode());
        				((Vector)chainNameAtom.lastElement()).add(seq.getChainId());
        				((Vector)resPosAtom.lastElement()).add(((jet.data.datatype.Residue)seq.get(pos)).getId());
        				((Vector)atomName.lastElement()).add(((jet.data.datatype.Atom)atoms.get(posAtom)).getName());
        				((Vector)atomPos.lastElement()).add(((jet.data.datatype.Atom)atoms.get(posAtom)).getPosition());
        				((Vector)atomAxs.lastElement()).add((double)0.0);
        				((Vector)atomSurfAxs.lastElement()).add((double)0.0);
            		}
        		}
    		}
    		i++;
	    }
    	/* Concaténation des informations de contact et d'accessibilité des différentes chaines */
		
    	Vector axsFinal=new Vector();
	//addition
	//Vector axsCloseFinal=new Vector();
    	Vector surfAxsFinal=new Vector();
    	Vector percentSurfAxsFinal=new Vector();
    	Vector codesFinal=new Vector();
    	Vector positionsFinal=new Vector();
    	Vector chainsFinal=new Vector();
    	for(i=0;i<positions.size();i++)
    	{
    		axsFinal.addAll((Vector)axs.get(i)); 
		//addition
    		//axsCloseFinal.addAll((Vector)axsClose.get(i)); 
    		surfAxsFinal.addAll((Vector)surfAxs.get(i));
    		percentSurfAxsFinal.addAll((Vector)percentSurfAxs.get(i)); 
    		codesFinal.addAll((Vector)codes.get(i)); 
    		positionsFinal.addAll((Vector)positions.get(i));
    		chainsFinal.addAll((Vector)chains.get(i));
    	}

    	Vector resNameAtomFinal=new Vector();
    	Vector chainNameAtomFinal=new Vector();
    	Vector resPosAtomFinal=new Vector();
    	Vector atomNameFinal=new Vector();
    	Vector atomPosFinal=new Vector();
    	Vector atomAxsFinal=new Vector();
    	Vector atomSurfAxsFinal=new Vector();  
    	
    	for(i=0;i<resNameAtom.size();i++)
    	{
    		resNameAtomFinal.addAll((Vector)resNameAtom.get(i)); 
    		chainNameAtomFinal.addAll((Vector)chainNameAtom.get(i)); 
    		resPosAtomFinal.addAll((Vector)resPosAtom.get(i));
    		atomNameFinal.addAll((Vector)atomName.get(i)); 
    		atomPosFinal.addAll((Vector)atomPos.get(i)); 
    		atomAxsFinal.addAll((Vector)atomAxs.get(i)); 
    		atomSurfAxsFinal.addAll((Vector)atomSurfAxs.get(i)); 
    	}
    	
    	/* Reordonnancement des chaines */
    	
    	if (this.accessType.equals("complex"))
    	{
	    	chains=new Vector(pdbInfo.size());
	    	positions=new Vector(pdbInfo.size());
	    	codes=new Vector(pdbInfo.size());
	    	axs=new Vector(pdbInfo.size());
		//addition
		//axsClose=new Vector(pdbInfo.size());
	    	surfAxs=new Vector(pdbInfo.size());
	    	percentSurfAxs=new Vector(pdbInfo.size());
	    	
	    	resNameAtom=new Vector(pdbInfo.size());
	    	chainNameAtom=new Vector(pdbInfo.size());
	    	resPosAtom=new Vector(pdbInfo.size());
	    	atomName=new Vector(pdbInfo.size());
	    	atomPos=new Vector(pdbInfo.size());
	    	atomAxs=new Vector(pdbInfo.size());
	    	atomSurfAxs=new Vector(pdbInfo.size());  

	    	for (i=0;i<pdbInfo.size();i++)
			{
	    		chains.add(new Vector());
	    		positions.add(new Vector());
	    		codes.add(new Vector());
	    		axs.add(new Vector());
			// addition
			//axsClose.add(new Vector());
	    		surfAxs.add(new Vector());
	    		percentSurfAxs.add(new Vector());
	    		
	    		resNameAtom.add(new Vector());
	    		chainNameAtom.add(new Vector());
	    		resPosAtom.add(new Vector());
	    		atomName.add(new Vector());
	    		atomPos.add(new Vector());
	    		atomAxs.add(new Vector());
	    		atomSurfAxs.add(new Vector());
			}
	    	
			Vector realChainPos = new Vector(pdbInfo.size());
			for (i=0;i<cut.size();i++)
				for (j=0;j<((Vector)cut.get(i)).size();j++)
					realChainPos.add(((Vector)cut.get(i)).get(j));
	
			int posChainCourante=0;
			String chainBefore=(String)chainsFinal.get(0);
			if (chainsFinal.size()>0) k=(Integer)realChainPos.get(posChainCourante);
			for (int l=0;l<chainsFinal.size();l++)
			{
				if (!chainBefore.equals((String)chainsFinal.get(l)))
				{
					chainBefore=(String)chainsFinal.get(l);
					posChainCourante++;
					k=(Integer)realChainPos.get(posChainCourante);	    
				}
				((Vector)chains.get(k)).add(chainsFinal.get(l));
				((Vector)positions.get(k)).add(positionsFinal.get(l));
				((Vector)codes.get(k)).add(codesFinal.get(l));
				((Vector)axs.get(k)).add(axsFinal.get(l));
				// addition
				//((Vector)axsClose.get(k)).add(axsCloseFinal.get(l));
				((Vector)surfAxs.get(k)).add(surfAxsFinal.get(l));
				((Vector)percentSurfAxs.get(k)).add(percentSurfAxsFinal.get(l));
			}
			
			posChainCourante=0;
			chainBefore=(String)chainNameAtomFinal.get(0);
			if (chainNameAtomFinal.size()>0) k=(Integer)realChainPos.get(posChainCourante);
			for (int l=0;l<chainNameAtomFinal.size();l++)
			{
				if (!chainBefore.equals((String)chainNameAtomFinal.get(l)))
				{
					chainBefore=(String)chainNameAtomFinal.get(l);
					posChainCourante++;
					k=(Integer)realChainPos.get(posChainCourante);
				}
				
				((Vector)chainNameAtom.get(k)).add(chainNameAtomFinal.get(l));
				((Vector)resPosAtom.get(k)).add(resPosAtomFinal.get(l));
				((Vector)resNameAtom.get(k)).add(resNameAtomFinal.get(l));
				((Vector)atomAxs.get(k)).add(atomAxsFinal.get(l));
				((Vector)atomSurfAxs.get(k)).add(atomSurfAxsFinal.get(l));
				((Vector)atomName.get(k)).add(atomNameFinal.get(l));
				((Vector)atomPos.get(k)).add(atomPosFinal.get(l));
			}
	    	
	    	
	    	/* Concaténation des informations de contact et d'accessibilité des différentes chaines */
			
	    	axsFinal=new Vector();
		//addition
	    	//axsCloseFinal=new Vector();
	    	surfAxsFinal=new Vector();
	    	percentSurfAxsFinal=new Vector();
	    	codesFinal=new Vector();
	    	positionsFinal=new Vector();
	    	chainsFinal=new Vector();
	    	    	
	    	for(i=0;i<positions.size();i++)
	    	{
	    		axsFinal.addAll((Vector)axs.get(i)); 
	    		//addition
			//axsCloseFinal.addAll((Vector)axsClose.get(i)); 
	    		surfAxsFinal.addAll((Vector)surfAxs.get(i));
	    		percentSurfAxsFinal.addAll((Vector)percentSurfAxs.get(i)); 
	    		codesFinal.addAll((Vector)codes.get(i)); 
	    		positionsFinal.addAll((Vector)positions.get(i));
	    		chainsFinal.addAll((Vector)chains.get(i));
	    	}  
	    	
	    	resNameAtomFinal=new Vector();
	    	chainNameAtomFinal=new Vector();
	    	resPosAtomFinal=new Vector();
	    	atomNameFinal=new Vector();
	    	atomPosFinal=new Vector();
	    	atomAxsFinal=new Vector();
	    	atomSurfAxsFinal=new Vector();    	
	    	
	    	for(i=0;i<resNameAtom.size();i++)
	    	{
	    		resNameAtomFinal.addAll((Vector)resNameAtom.get(i)); 
	    		chainNameAtomFinal.addAll((Vector)chainNameAtom.get(i)); 
	    		resPosAtomFinal.addAll((Vector)resPosAtom.get(i));
	    		atomNameFinal.addAll((Vector)atomName.get(i)); 
	    		atomPosFinal.addAll((Vector)atomPos.get(i)); 
	    		atomAxsFinal.addAll((Vector)atomAxs.get(i)); 
	    		atomSurfAxsFinal.addAll((Vector)atomSurfAxs.get(i)); 
	    	}
    	}
    	
    	/* Ecriture dans nomfichier_axs.res des informations sur les 
    	 * residus à l'interface et accessibles qui sont suceptibles 
    	 * d'etre conservés par l'evolution. */
    	
    	Vector nom_colonnes=new Vector(6);
    	Vector result=new Vector(6);
    	String filename=pdbfile.getPath();
    	filename=filename.substring(0,filename.lastIndexOf("."));
	String suffix="";

	if (suffixispresent)
	    {
		if (this.accessType.equals("chain"))
		    {
			suffix="_u";
		    }
		
		if (this.accessType.equals("complex"))
		    {
			suffix="_b";
		    }
	    }

	nom_colonnes.add("AA");nom_colonnes.add("pos");nom_colonnes.add("chain");nom_colonnes.add("axs");nom_colonnes.add("surfAxs");nom_colonnes.add("percentSurfAxs");
	//nom_colonnes.add("AA");nom_colonnes.add("pos");nom_colonnes.add("chain");nom_colonnes.add("axs");nom_colonnes.add("surfAxs");nom_colonnes.add("percentSurfAxs");nom_colonnes.add("CloseSurf");
	result.add(codesFinal);result.add(positionsFinal);result.add(chainsFinal);result.add(axsFinal);result.add(surfAxsFinal);result.add(percentSurfAxsFinal);
	//result.add(codesFinal);result.add(positionsFinal);result.add(chainsFinal);result.add(axsFinal);result.add(surfAxsFinal);result.add(percentSurfAxsFinal);result.add(axsCloseFinal);

    	Result.WriteResult(result, nom_colonnes, filename+suffix+"_axs.res");

	nom_colonnes=new Vector(7);
    	result=new Vector(7);
    	
    	nom_colonnes.add("AA");nom_colonnes.add("posAA");nom_colonnes.add("chain");nom_colonnes.add("atom");
    	nom_colonnes.add("posAtom");nom_colonnes.add("atomAxs");nom_colonnes.add("atomSurfAxs");
    	result.add(resNameAtomFinal);result.add(resPosAtomFinal);result.add(chainNameAtomFinal);
    	result.add(atomNameFinal);result.add(atomPosFinal);result.add(atomAxsFinal);result.add(atomSurfAxsFinal);
 
    	Result.WriteResult(result, nom_colonnes, filename+suffix+"_atomAxs.res");

    	//Result.convertResultToPDB(filename+"_atomAxs.res", pdbfile.getPath(), "atomAxs",2);
    	//pdbft= new jet.io.file.PdbFileTransform(filename+"_atomAxs.pdb");
    }
	
}

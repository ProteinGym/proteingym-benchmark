package jet;

import java.util.*;
import java.io.*;
import javax.vecmath.*;


public class CVAnalysis {

    jet.ConfigFile cf;	
    float maxDist;
    String accessType;
    Boolean local;


    public CVAnalysis(jet.ConfigFile cf,Boolean loc) 
    { 		
	this.cf=cf;
	if(loc){maxDist=(float)12.0;local=true;}
	else{this.maxDist=(float)cf.getDoubleParam("CV","max_dist");if (maxDist==(float)-1) maxDist=(float)100.0;local=false;}
	this.accessType=cf.getParam("Access","accessType");
    }
    
    public void analyse(File pdbfile) throws jet.exception.NaccessException
    {
	/* Lecture du fichier pdb */
    	jet.io.file.PdbFileReader pdb=new jet.io.file.PdbFileReader(pdbfile.getPath());
	
    	/* Recupération par le parseur des infos de structure 3D */	
    	Vector pdbInfo=jet.data.dataformat.parser.PDB.getSequenceInfo(pdb,false);
	Vector pdbInfoTMP = jet.data.dataformat.parser.PDB.getSequenceInfo(pdb,false,this.accessType.equals("chain"));
	int s=0;
	int i,j,nb;
	String chainID="";
	Vector cvAt=new Vector();

	/* Vecteurs de resultats */
	Vector codes=new Vector(100,100), positions=new Vector(100,100),chainId=new Vector(100,100);
	Vector cvResults=new Vector(100,100);
	
	int countChains=0;

	boolean dejaVuSequence=false;

	for(s=0;s<pdbInfoTMP.size();s++)
	    {
		cvAt.clear();
		jet.data.datatype.Sequence3D seq=((jet.data.dataformat.info.PdbSequenceInfo)pdbInfoTMP.get(s)).getSequence();	
		if(this.accessType.equals("chain"))
		   {
		      System.out.println("circular variance analysis of sequence: "+seq.getSequenceName());	
		   }
		else
		   {
		      System.out.println("circular variance analysis of the complex");
		   }
		
		chainID=seq.getChainId();
		
		if( (seq.size()>20) && (seq.isProtein()) )
		   {
		       jet.cluster.data.AtomDistanceMap dmAt= new jet.cluster.data.AtomDistanceMap(seq,maxDist);
		       jet.cluster.data.ProxListAt plVoisin;
	
		       for (i=0;i<dmAt.getLength();i++)
			   {
			       nb=0;
			       plVoisin=(jet.cluster.data.ProxListAt) dmAt.getProxList(i);
			       Point3f vecI = (Point3f) ((jet.data.datatype.Atom) plVoisin.getAtom()).getCoordinates();
			       Point3f sumI = new Point3f();
			       
			       for (j=0;j<plVoisin.getLength();j++)
				   {
				       if( (plVoisin.getProxNode(j).getDistance()<= maxDist) && (plVoisin.getProxNode(j).getDistance()> 2.0)  )
					   {
					       jet.data.datatype.Atom at= ((jet.cluster.data.ProxListAt)((jet.cluster.data.ProxNodeAt) plVoisin.getProxNode(j)).getRef()).getAtom();
					       Point3f ri = new Point3f();
					       ri.sub( ((Point3f) at.getCoordinates()), vecI);
					       ri.scale((float) 1.0/(ri.distance(new Point3f())));
					       sumI.add(ri);
					       nb++;
					   }
				   }
			       // list of cv values for every atom of a chain
			       cvAt.add((double) 1 - ((sumI.distance(new Point3f()))/nb));
			   }
		   
                Vector cv = new Vector();
                cv.addAll(computeCVPerResidue(seq,cvAt));
                Vector cvnorm = new Vector();
                double max=0.0;
                for(i=0;i<cv.size();i++) if((Double)cv.get(i)>max) max=(Double)cv.get(i);
                for(i=0;i<cv.size();i++) cvnorm.add((Double) cv.get(i)/max);
                cvResults.addAll(cvnorm);
		System.out.println("");

		for (int pos=0; pos<seq.size(); pos++)
		    {
			codes.add(seq.getResidue(pos).getResidueCode());
			//positions.add(seq.getResidue(pos).getPosition());
			positions.add(seq.getResidue(pos).getId());
			if(this.accessType.equals("chain"))
			{
			   chainId.add(chainID);
			}
			else
			{
			   chainId.add(seq.getResidue(pos).getChainId());
			}
		    }

		}
	else
		{
                    for (int pos=0; pos<seq.size(); pos++)
                    {
                        codes.add(seq.getResidue(pos).getResidueCode());
                        //positions.add(seq.getResidue(pos).getPosition());
                        positions.add(seq.getResidue(pos).getId());
                        if(this.accessType.equals("chain"))
                        {
                           chainId.add(chainID);
                        }
                        else
                        {
                           chainId.add(seq.getResidue(pos).getChainId());
                        }
			cvResults.add(0.0);
                    }

		   System.out.println(" - the sequence is too small or not a protein");
		}
	    }


	/* Generation du fichiers de resultats */
	
	Vector nom_colonnes=new Vector(4);
	Vector result=new Vector(4);
	nom_colonnes.add("AA");nom_colonnes.add("pos");nom_colonnes.add("chain");nom_colonnes.add("cv");
	
	result.add(codes);result.add(positions);result.add(chainId);result.add(cvResults);
	
	writeResult(result, nom_colonnes, pdbfile);

    }


    public Vector computeCVPerResidue(jet.data.datatype.Sequence3D seq, Vector cvAt)
    {
    	Vector cv=new Vector();
	int indexAt=0;
	Double cvVal;
	System.out.println(seq);
	for (int pos=0;pos<seq.size();pos++)
	    {
		cvVal=0.0;
		Vector atoms=seq.getResidue(pos,jet.data.datatype.Sequence3D.DIRECT).getAllAtoms();
		for (int posAtom=0;posAtom<atoms.size();posAtom++)
		    {
			cvVal=cvVal+(Double)cvAt.get(indexAt);
			indexAt++;
		    }
		cvVal=cvVal/atoms.size();
		cv.add(cvVal);
	    }
	return(cv);
    }


	    
    /** Ecriture des résultats */
    
    public void writeResult(Vector result, Vector nom_colonnes, File pdbfile)
    {
	String filename=pdbfile.getPath();
	filename=filename.substring(0,filename.lastIndexOf("."));
	if(local){Result.WriteResult(result, nom_colonnes, filename+"_cvlocal.res");}
	else{Result.WriteResult(result, nom_colonnes, filename+"_cv.res");}
	jet.io.file.PdbFileTransform pdbft;
	pdbft= new jet.io.file.PdbFileTransform(pdbfile.getPath());
	pdbft.cut(new Vector(),true);
    }


}
		 
			       
			       




   
	 










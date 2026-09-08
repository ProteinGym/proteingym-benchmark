package jet;

import java.io.File;
import java.util.Vector;
import java.util.Random;

public class JetAnalysis{
	
	/** fichier d'entrée de configuration */
	jet.ConfigFile cf;
	/** fichier de sortie des caractéristiques de l'analyse */
	jet.ConfigFile caracTestFile;
	
	int numAli;
	int numMulti;

    public JetAnalysis(jet.ConfigFile cf,jet.ConfigFile ctf) 
    { 
	this.cf=cf; 
	this.caracTestFile = ctf;
    }
    
    public void analyse(File pdbfile,File[] alignFileList)
    {
	
	/************************Reading line command arguments***************************************/
	
	/* Lecture du fichier pdb */	
	jet.io.file.PdbFileReader pdb=new jet.io.file.PdbFileReader(pdbfile.getPath());
	/* Recupération par le parseur des infos de structure 3D */
	Vector pdbInfo=jet.data.dataformat.parser.PDB.getSequenceInfo(pdb,false);
	/* Sequence étudiée */
	jet.data.datatype.Sequence3D ref;
	int i;
	
	/* Vecteurs de resultats */
	Vector codes=new Vector(100,100), positions=new Vector(100,100),chainId=new Vector(100,100),mu=new Vector(100,100),freq=new Vector(100,100);
	Vector trace=null,traceResults=new Vector(100,100);
	Vector pcResults=new Vector(100,100);
	Vector toutes_traces= new Vector(pdbInfo.size());
	/* Recuperation du nom du fichier sans l'extention (pour sauvegarder les resultats 
	 * dans des fichier d'extention differente mais de radical commun) */
	String filename=pdbfile.getPath();
	filename=filename.substring(0,filename.lastIndexOf("."));
	String pdbCode=filename.substring(filename.lastIndexOf(File.separator)+1,filename.length());
	
	/* On boucle sur chaque sequence3D qui represente chaque chaine de la sequence. 
	 * Ainsi les resultats pour chaque chaine sont concaténés */
	
	Vector chains=new Vector(pdbInfo.size());
	int nb=0;
	int nbSeqDiff=0;
	boolean dejaVuSequence=false;
	int ouVuSequence=0;
	File alignFile=null;
	
	for(i=0;i<pdbInfo.size();i++)
	    {
		/* Récupération de la chaine à étudier */
		ref=((jet.data.dataformat.info.PdbSequenceInfo)pdbInfo.get(i)).getSequence();
		
		dejaVuSequence=false;
		ouVuSequence=i;
		nb=0;
		while ((!dejaVuSequence) && (nb<i))
		    {
			if (ref.isIdenticalSeq(((jet.data.dataformat.info.PdbSequenceInfo)pdbInfo.get(nb)).getSequence()))
			    {
				dejaVuSequence=true;
				ouVuSequence=nb;
			    }
			nb++;	
		    }
		
		/* Analyse des sequences de taille>20 */
		if((!dejaVuSequence) && (ref.size()>20) && (ref.isProtein()))
		    {
			
			/* Recuperation du fichier d'alignement correspondant */
			String alignFileType=cf.getParam("SequenceRetrieving", "format");
			String nameChain="";
			alignFile=null;
			int nbBlastFile=0;
			if (alignFileList!=null)
			    {
				for (int f=0; f<alignFileList.length;f++)
				    {
					nameChain=alignFileList[f].getAbsolutePath().substring(filename.lastIndexOf(File.separator));
					
					if ((nameChain.lastIndexOf("_"+ref.getChainId()+"."+alignFileType)!=-1)&&(nameChain.lastIndexOf(pdbCode)!=-1))
					    {
						nbBlastFile++;
						alignFile=alignFileList[f];
					    }
				    }
				if (nbBlastFile>1) System.err.println("many psiblast file for the same sequence, taking last");
			    }
			else alignFile=null;
			
			if (alignFile!=null) System.out.println("Alignment file:"+alignFile.getAbsolutePath());
			else System.err.println("no blast file");
			
			/* Lancement de l'analyse de la trace */
			
			if((trace=doAnalysis(filename,alignFile,ref))!=null)
			    {
				/* Analyse reussie */
				
				nbSeqDiff++;
				
			    }
			else
			    /* Erreur dans l'analyse de la sequence */
			    {
				
				System.out.println("Unable to analyse "+ref.getSequenceName());
				/* Ajout aux vecteurs de resultats de valeurs 
				 * par defaut significative d'une absence de resultat */
				trace=new Vector();
				for (int nb_res=0;nb_res<2;nb_res++) trace.add(fillVector(ref.size()));
				/* score en fonction des propriété physico-chimiques */
				Vector pcTemp=new Vector(ref.size());
				double max=0.0;
				for (int k=0;k<20;k++) if (jet.data.datatype.Residue.getResiduePC(k)>max) max=jet.data.datatype.Residue.getResiduePC(k);
				for (int k=0;k<ref.getSequenceLength();k++) pcTemp.add(jet.data.datatype.Residue.getResiduePC(ref.getResidue(k).getResidueSymbol())/max);
				trace.add(pcTemp);
				trace.add(fillVector(ref.size()));
			    }
		    }
		else 
		    /* Sequence trop courte ou deja vue */
		    {
			if (dejaVuSequence)
			    /* deja vue */
			    {
				System.out.println("Sequence "+ref.getSequenceName()+" same as sequence number "+ouVuSequence);
				trace=(Vector)toutes_traces.elementAt(ouVuSequence);
				
			    }
			else
			    /* Sequence trop courte ou pas une proteine */
			    {
				System.out.println("Unable to analyse "+ref.getSequenceName()+" : the sequence is too small or not a protein");
				/* Ajout aux vecteurs de resultats de valeurs 
				 * par defaut significative d'une absence de resultat */
				trace=new Vector();
				for (int nb_res=0;nb_res<4;nb_res++) trace.add(fillVector(ref.size()));
				
			    }
		    }
		if (trace.size()>0){
		    
		    chains.add(i);
		    
		    /* Recupération des informations sur l'analyse */
		    String category=pdbCode+":"+ref.getChainId();
		    caracTestFile.addParameter(category, "size", ""+ref.size(), "size of the sequence");
		    
		    for (int pos=0; pos<ref.size(); pos++)
			{
			    codes.add(ref.getResidue(pos).getResidueCode());
			    positions.add(ref.getResidue(pos).getId());
			    chainId.add(ref.getChainId());
			}
		    
		    mu.addAll((Vector)trace.get(0));
		    freq.addAll((Vector)trace.get(1));
		    pcResults.addAll((Vector)trace.get(2));
		    traceResults.addAll((Vector)trace.get(3));
		}
		
		toutes_traces.add(trace);
	    }
	
	/* Generation du fichiers de resultats */
	
	Vector nom_colonnes=new Vector(7);
	Vector result=new Vector(7);
	nom_colonnes.add("AA");nom_colonnes.add("pos");nom_colonnes.add("chain");
	result.add(codes);result.add(positions);result.add(chainId);
	if(new File(filename+"_axs.res").exists())
	    {
		nom_colonnes.add("axs");
		Vector axsResults=Result.readValuesResult(filename+"_axs.res");
		Vector nameAxsResults=Result.readCaracResult(filename+"_axs.res");
		result.add((Vector)axsResults.get(Result.searchNumCol(nameAxsResults,"axs")));
	    }
	
	if(new File(filename+"_cv.res").exists())
	    {
		nom_colonnes.add("cv");
		Vector cvResults=Result.readValuesResult(filename+"_cv.res");
		Vector nameCvResults=Result.readCaracResult(filename+"_cv.res");
		result.add((Vector)cvResults.get(Result.searchNumCol(nameCvResults,"cv")));
	    }

	// these columns are 5, 6, 7, 8	
	nom_colonnes.add("pc");nom_colonnes.add("tr");nom_colonnes.add("freq");nom_colonnes.add("trace");
	result.add(pcResults);result.add(mu);result.add(freq);result.add(traceResults);
	writeResult(result, nom_colonnes, pdbfile);
	
    }
    
    /** Retourne un vecteur de taille size contenant de 0.0 */
    
    public Vector fillVector(int size)
    {
	int i;
	Vector v=new Vector(size);
	for(i=0;i<size;i++) v.add(new Double(0.0)); 
	return v;
    }
    
    
    /** Récupération des séquences à partir d'un fichier FASTA. Retourne un vecteur de sequences. */
    
    public Vector requeteFasta(File fastaFile) throws Exception
    {
	jet.io.file.FastaFileReader ffr=new jet.io.file.FastaFileReader(fastaFile.getPath());
	Vector sequenceList=ffr.getSequenceData();
	return sequenceList;
    }
    
    /** Lancement d'une requete sur le serveur PSI-BLAST (adresse web "url"). 
     * Retourne les données de sortie PSI-BLAST sous forme de vecteur ligne à ligne. */
    
    public Vector psiBlastServerRequest(jet.data.datatype.Sequence ref,String url, String database, String matrix, int maxResults, double eValue, int gap_exist, int gap_ext, int maxIteration,String filename) throws Exception
    {
	
	System.out.println("Submitting sequence:\n"+ref.toFasta());
	Vector blastData=null;
	
	jet.io.net.WebClient bc;
	bc=new jet.io.net.PsiBlastClient(url, database, matrix, maxResults, eValue,gap_exist,gap_ext,maxIteration);
	((jet.io.net.PsiBlastClient)bc).setQuerySequence(ref);
	blastData=((jet.io.net.PsiBlastClient)bc).getData();
	
	if ((blastData==null)||(blastData.size()==0))
	    {
		System.err.println("no psiblast server result for sequence "+ref.getSequenceName());			
	    }
	else
	    {
		jet.io.file.FileIO.writeFile(filename+"_"+ref.getChainId()+".psiblast",blastData,false);
	    }
	
	return blastData;
	
    }
    
    /** Lancement d'une requete PSI-BLAST en local (ligne de commande "command"). 
     * Retourne les données de sortie 
     * PSI-BLAST sous forme de vecteur ligne à ligne. */
    
    public Vector psiBlastLocalRequest(String command, jet.data.datatype.Sequence ref, String database, String matrix, int maxResults, double eValue, int gap_exist, int gap_ext, int maxIteration,String filename) throws Exception
    {
	
	System.out.println("Submitting sequence:\n"+ref.toFasta());
	Vector blastData=null;
	
	String[] subLine=ref.toFasta().split("\\n+");
	Vector dataRefFasta=new Vector();
	for (int i=0;i<subLine.length;i++)
	    {
		dataRefFasta.add(subLine[i]);
	    }
	jet.io.file.FileIO.writeFile(filename+"_"+ref.getChainId()+".fasta", dataRefFasta,false);
	System.out.println("start psiblast");
	new jet.external.PsiBlast(command,database,matrix.toUpperCase(), filename+"_"+ref.getChainId()+".fasta", filename+"_"+ref.getChainId()+".psiblast", maxResults, eValue, 0.005, gap_exist,gap_ext,maxIteration,"2");
	System.out.println("end psiblast");
	blastData=jet.io.file.FileIO.readFile(filename+"_"+ref.getChainId()+".psiblast");
	
	if ((blastData==null)||(blastData.size()==0)) 
	    {
		System.err.println("no psiblast local result for sequence "+ref.getSequenceName());			
	    }
	else
	    {
		int nbLine=0;
		while((nbLine<blastData.size())&&(((String)blastData.get(nbLine)).lastIndexOf("Results from round "+maxIteration)==-1)) 
		    nbLine++;
		
		if (nbLine<blastData.size())
		    for (int nb=0;nb<nbLine;nb++) blastData.remove(0);
		
		
		if (blastData.size()!=0) jet.io.file.FileIO.writeFile(filename+"_"+ref.getChainId()+".psiblast",blastData,false);
		else System.err.println("no psiblast local result for sequence "+ref.getSequenceName());	
	    }
	
	return blastData;
	
    }
    
    int filteringIteration = 1;
    /** Filtrage itératif d'un vecteur de sequences en fonction de plusieurs parametres (Evalue, length ...)
     * jusqu'à atteindre un nombre suffisant de séquence. */
    
    public Vector iterativeSequenceFiltering(Vector sequenceList, jet.data.datatype.Sequence ref, int maxResults, double eValue, int minLength, int maxLength, int minIdentity, int maxIdentity,int maxGapPercent, int length, int maxLoad) throws Exception
    {
	
	Vector tempList=new Vector();
	filteringIteration = 1;
	/* On retire les sequences en trop (utile si on a utilisé un fichier blast en input) */
	for (int i=maxResults;i<sequenceList.size();) sequenceList.remove(i);
	
	System.out.println("Selected "+sequenceList.size()+" sequences before filtering");
	
	// the first filtering is performed with a 51% coverage criterion and evalue threshold of 100.0	
	filterSequences(sequenceList,(int)(0.5*(double)ref.size())+1, maxLength, 0, 100,100.0,100,1,Integer.MAX_VALUE);
	
	double eValueTemp=eValue;
	int minLengthTemp=minLength;
	boolean stop=false;
	int[] removing=new int[5];
	for (int i=0; i<removing.length;i++) removing[i]=0;
	
	while ((!stop)&&(eValueTemp<=100.0))
	    {
		minLengthTemp=minLength;
		// out condition: either the nbseq is below 100 or the coverage criterion is below 51%
		while ((!stop)&&(minLengthTemp>=((int)(0.5*(double)ref.size())+1)))
		    
		    {
			//System.out.println("Fixing minLength to :"+minLengthTemp+" and eValue to :"+eValueTemp);
			tempList.clear();
			tempList.addAll(sequenceList);
			removing=filterSequences(tempList,minLengthTemp, maxLength, minIdentity, maxIdentity,eValueTemp,maxGapPercent,length,maxLoad);
			
			if ((numMulti==-1)&&(numAli==-1))
			    {
				/* numMulti et numAli pas fixé --> on essai de recuperer
				   toute l'information contenue dans l'ensemble des seqeunces */
				numAli=(int)Math.sqrt(tempList.size());
				if (numAli<10) numAli=10;
				if (numAli>50) numAli=50;
				numMulti=numAli;
			    }
			// if numMulti was initially at -1, then it has been changed to a number between 10 and 50	
			if (numMulti==-1)
			    {
				/* numMulti pas fixé --> on essai de recuperer toute l'info 
				 * et au moins deux alignements */
				numMulti=tempList.size()/numAli;
				if (numMulti<2) numMulti=2;
			    }
			
			// if numAli was initially at -1, then it has been changed to a number between 10 and 50 
			if (numAli==-1)
			    {
				/* numAli pas fixé --> on essai de recuperer toute l'info 
				 * et on ne va pas en dessous de 10 seq par alignement */
				numAli=tempList.size()/numMulti;
				if (numAli<10) numAli=10;
			    }
			
			// this will return true (out condition) if 10<=nbseq<12 and no improvement
			// can be made with coverage or evalue (then change the value of numAli to 0.8*nbseq)
			// or if seq>12 (sufficient number of sequences)
			if (verifyNumberSequences(tempList, ref,removing))
			    {
				stop=true;
				sequenceList=tempList;
			    }
			// if some sequences were removed for displaying too low coverage
			if (removing[0]!=0)
			    {
				// if the coverage criterion is above 51% of the query sequence
				if (minLengthTemp>((int)(0.5*(double)ref.size())+1))
							{
							    // then decrease the criterion by 10%
							    minLengthTemp=((int)(((double)minLengthTemp)*0.9));
							    // but do not go below 51%
							    if (minLengthTemp<((int)(0.5*(double)ref.size())+1))
								minLengthTemp=((int)(0.5*(double)ref.size())+1);
							}
				// if the coverage is below 51% put it at 40% (out condition)
				else minLengthTemp=(int)(0.4*(double)ref.size());
			    }
			// if no sequence were removed based on coverage put the criterion at 40% (out condition)
			else minLengthTemp=(int)(0.4*(double)ref.size());
		    }
		
		// if some sequences were removed based on e-value
		if (removing[1]!=0)
		    {
			eValueTemp=eValueTemp*10;
		    }
		else eValueTemp=1000.0;
		
	    }	
	
	if (!stop) return new Vector();
	else return sequenceList;
	
    }
    
    /** Filtrage d'un vecteur de sequences en fonction de plusieurs parametres (Evalue, length ...)*/
    
    public int[] filterSequences(Vector sequenceList,int minLength, int maxLength, int minIdentity, int maxIdentity,double eValue, int maxGapPercent, int length, int maxLoad) throws Exception
    {
	int countR;
	int[] removing= new int[7];
	countR=sequenceList.size();
	
	System.out.println("Filtering sequences (Iteration "+filteringIteration+"):");
	filteringIteration++;
	countR=sequenceList.size();
	jet.data.dataformat.parser.Blast.filterSequenceIdentities(sequenceList,minIdentity,maxIdentity);
	removing[2]=countR-sequenceList.size();
	System.out.println("  -Identity: Removed "+removing[2]+" seqs");
	countR=sequenceList.size();
	jet.data.dataformat.parser.Blast.removeRedundance(sequenceList);
	removing[3]=countR-sequenceList.size();
	System.out.println("  -Redundancy: Removed "+removing[3]+" seqs");		
	countR=sequenceList.size();
	jet.data.dataformat.parser.Blast.removeSameName(sequenceList);
	removing[4]=countR-sequenceList.size();
	System.out.println("  -Similar names: Removed "+removing[4]+" seqs");
	countR=sequenceList.size();
	jet.data.dataformat.parser.Blast.filterSequenceGap(sequenceList,maxGapPercent);
	removing[5]=countR-sequenceList.size();
	System.out.println("  -Gaps: Removed "+removing[5]+" seqs");
	countR=sequenceList.size();
	jet.data.dataformat.parser.Blast.filterSequenceLengths(sequenceList,minLength,maxLength);	
	removing[0]=countR-sequenceList.size();
	System.out.println("  -Length: Removed "+removing[0]+" seqs");
	countR=sequenceList.size();
	jet.data.dataformat.parser.Blast.filterSequenceEValues(sequenceList,eValue);	
	removing[1]=countR-sequenceList.size();
	System.out.println("  -EValue: Removed "+removing[1]+" seqs");
	countR=sequenceList.size();
	Vector randList1=new Vector(sequenceList);
	jet.data.dataformat.parser.Blast.filterSequenceIdentities(randList1,0,39);
	Vector randList2=new Vector(sequenceList);
	jet.data.dataformat.parser.Blast.filterSequenceIdentities(randList2,40,59);
	Vector randList3=new Vector(sequenceList);
	jet.data.dataformat.parser.Blast.filterSequenceIdentities(randList3,60,79);
	Vector randList4=new Vector(sequenceList);
	jet.data.dataformat.parser.Blast.filterSequenceIdentities(randList4,80,99);
	sequenceList.clear();
	sequenceList.addAll(limitLoad(randList1,randList2,randList3,randList4,length,maxLoad));
	removing[6]=countR-sequenceList.size();
	System.out.println("  -CPU Load: Removed "+removing[6]+" seqs");
	System.out.println("Selected "+sequenceList.size()+" after filtering");
	return removing;
	
    }
    
    /** Vérification du nombre de séquences. Retourne true si sequenceList contient assez de séquences
     * et false sinon. */
    
    public boolean verifyNumberSequences(Vector sequenceList, jet.data.datatype.Sequence ref, int[] removing) throws Exception
    {
	
	if(sequenceList!=null)
	    {
		if(sequenceList.size()!=0)
		    {
			if (numMulti!=1)
			    {
				// numAli is comprised between 10 (nbseq<100) and 50 (nbseq>2500)
				// condition is true only if nbseq < 12
				if(sequenceList.size()<(int)(numAli*1.20))
				    { 
					// then if nbseq is above 10 and nothing was removed based on coverage or evalue
					if((sequenceList.size()>=10)&&(removing[0]==0)&&(removing[1]==0))
					    {
						/* Au minimum on a numAli=8 (=10*0.8) */
						// then return true and change numAli (condition while seems useless)
						while(sequenceList.size()<(int)(numAli*1.20))
						    numAli=(int)(((double)sequenceList.size())*0.8);
					    }
					else return false;	
				    }
				// else if nbseq is above 12 then return true
			    }
		    }
		else return false; 
	    }
	else return false;     
	
	return true;
    }
    
    /** reducing diversity of sequences partition */
    
    public void filterPartition(Vector sequenceList, int max) throws Exception
    {
	Vector[] randList=new Vector[4];
	
	randList[0]=new Vector(sequenceList);
	jet.data.dataformat.parser.Blast.filterSequenceIdentities(randList[0],0,39);
	randList[1]=new Vector(sequenceList);
	jet.data.dataformat.parser.Blast.filterSequenceIdentities(randList[1],40,59);
	randList[2]=new Vector(sequenceList);
	jet.data.dataformat.parser.Blast.filterSequenceIdentities(randList[2],60,79);
	randList[3]=new Vector(sequenceList);
	jet.data.dataformat.parser.Blast.filterSequenceIdentities(randList[3],80,99);
	
	System.out.print("iterated blast partition before filtering:");
	for (int h=0;h<randList.length;h++) System.out.print(" "+randList[h].size());
	System.out.println("");
	
	int rand;
	
	for (int h=0;h<randList.length;h++)
	    {		
		/* optimisation a faire en fonction du nombre de sequences dans randList (prendre au lieu d'enlever) */
		while (randList[h].size()>max)
		    {
			rand=(int)(Math.random()*(double)randList[h].size());
			randList[h].remove(rand);				
			
		    }
		
	    }
	
	System.out.print("iterated blast partition after filtering:");
	for (int h=0;h<randList.length;h++) System.out.print(" "+randList[h].size());
	System.out.println("");
	
	sequenceList=new Vector();
	for (int h=0;h<randList.length;h++)
	    {
		sequenceList.addAll(randList[h]);
	    }
    }
    
    
    /** Methode calculant le nombre de sequences à prendre dans chaque partition. 
     * Facteur permet de fixer 
     * le nb de sequence minimal d'une partition sachant que
     *  proportion pourcent de ces sequences seront choisies,
     * avec facteur= 1/proportion. */
    
    public int[] calculPartitionNumbers(int[] nbSeqPartition,double facteur) throws Exception
    {
	
	int[] nbs=new int[nbSeqPartition.length];
	for (int k=0;k<nbs.length;k++) nbs[k]=nbSeqPartition[k];
	int[] indices=new int[4];
	indices[0]=0;indices[1]=1;indices[2]=2;indices[3]=3;
	int temp;
	
	/* Tri croissant des partitions en fonction du nombre de sequences */
	
	for (int k=0;k<(nbs.length-1);k++)
	    {
		for (int l=k+1;l<nbs.length;l++)
		    {
			if (nbs[l]<nbs[k])
			    {
				temp=nbs[k];
				nbs[k]=nbs[l];
				nbs[l]=temp;
				temp=indices[k];
				indices[k]=indices[l];
				indices[l]=temp;
			    }
		    }
		
	    }
	
	/* initialisation des partitions */
	int seuil=(numAli/nbSeqPartition.length);
	for (int k=0;k<nbs.length;k++) nbs[k]=seuil;
	int reste=numAli-seuil*4;
	int nb=0;
	while (reste>0)
	    {
		nbs[nb]++;
		reste--;
		nb++;
	    }
	
	boolean continu,next,arret;
	int s=0;
	int iter=0;
	nb=-1;

			for (int k=0;k<nbSeqPartition.length;k++)
			{
				continu=true;
				s=(int)(((double)nbs[indices[k]])*facteur);
				arret=(s<=nbSeqPartition[indices[k]]);
				if (!arret)
				{
					next=false;
					iter=-1;
					while((!next)&&(nb<indices.length))
					{
						nb++;
						iter++;
						if(nb<indices.length)
						{
							next=true;
							for(int l=0;l<=k;l++) next=next&&(indices[l]!=nb);
							next=next&&((int)(((double)(nbs[nb]+1))*facteur)<=nbSeqPartition[nb]);
						}
						else
						{
							if (iter==nb) arret=true;
							else
							{
								iter=-1;
								nb=-1;
							}
						}
					}
				}
				while (continu&&(!arret))
				{
					nbs[indices[k]]--;
					nbs[nb]++;
					continu=((int)(((double)nbs[indices[k]])*facteur)>nbSeqPartition[indices[k]]);
					if(continu)
					{
						next=false;
						iter=-1;
						while((!next)&&(nb<indices.length))
						{
							nb++;
							iter++;
							if(nb<indices.length)
							{
								next=true;
								for(int l=0;l<=k;l++) next=next&&(indices[l]!=nb);
								next=next&&((int)(((double)(nbs[nb]+1))*facteur)<=nbSeqPartition[nb]);
							}
							else
							{
								if (iter==nb) arret=true;
								else
								{
									iter=-1;
									nb=-1;
								}
							}
						}
					}
				}
				if(arret) k=nbSeqPartition.length;
			}
	    	return nbs;
	    }
	    
	    /** Echantillonnage aléatoire des séquences (sequenceList) et calcul pour chaque 
	     * échantillon d'un alignement. */
	    
	    public void makeAlignment(String filename,jet.data.datatype.Sequence3D ref, Vector sequenceList, String command,jet.ProgressIndicator progress) throws Exception
	    {
	    	//System.out.println("**********init**********");
	    	/* Ecriture au format fasta des sequences recupérées */
	    	Vector fastaList=new Vector();
	    	fastaList.add(ref);
	    	fastaList.addAll(sequenceList);
	    	new jet.io.file.FastaFileWriter(filename+"_"+ref.getChainId()+".fasta",fastaList);
	    	
	    	/* Sample homolog sequences and align them with CLustaW external command */
			int rand;
			
			Vector sampleList=null;
					
			//progress=new jet.ProgressIndicator("Clustal",true);
			//progress.setProgress(0.0);
			//new Thread(progress).start();
			
			Vector[] randList=new Vector[4];
			randList[0]=new Vector(sequenceList);
			jet.data.dataformat.parser.Blast.filterSequenceIdentities(randList[0],0,39);
			randList[1]=new Vector(sequenceList);
			jet.data.dataformat.parser.Blast.filterSequenceIdentities(randList[1],40,59);
			randList[2]=new Vector(sequenceList);
			jet.data.dataformat.parser.Blast.filterSequenceIdentities(randList[2],60,79);
			randList[3]=new Vector(sequenceList);
			jet.data.dataformat.parser.Blast.filterSequenceIdentities(randList[3],80,99);
			
			/* Calcul du nombre de sequences à prendre dans chaque partition */
			
			int[] nbsSave=new int[4];
			nbsSave[0]=randList[0].size();nbsSave[1]=randList[1].size();nbsSave[2]=randList[2].size();nbsSave[3]=randList[3].size();
			
			/* proportion des sequences selectionnees pour une partition */
			double proportion = 0.5;
			double facteur = 1/proportion;
			int nbSeq=0;
			while((nbSeq<numAli)&&(proportion<=1.0))
			{
				nbSeq=0;
				nbsSave[0]=randList[0].size();nbsSave[1]=randList[1].size();nbsSave[2]=randList[2].size();nbsSave[3]=randList[3].size();
				nbsSave=calculPartitionNumbers(nbsSave,facteur);
				for (int nbPart=0;nbPart<nbsSave.length;nbPart++)
				{
					if (nbsSave[nbPart]<=randList[nbPart].size()) nbSeq=nbSeq+nbsSave[nbPart]; 
					else nbSeq=nbSeq+randList[nbPart].size(); 
				}
				proportion=proportion+0.1;
				facteur = 1/proportion;
		    }
			
			System.out.println("proportion="+(proportion-0.1));
			System.out.print("selected partition:");
			for (int h=0;h<nbsSave.length;h++) System.out.print(" "+nbsSave[h]);
			System.out.println("");
			
			int[] nbs=new int[4];				
			
			/* Boucle sur le nombre d'alignements "numMulti" a effectuer */
			for(int i=0;i<numMulti;i++)
			{
			    //progress.setProgress((double)i/(double)numMulti);
				/* Liste des sequences blast */
				//System.out.println("**********sort**********");
				randList[0]=new Vector(sequenceList);
				jet.data.dataformat.parser.Blast.filterSequenceIdentities(randList[0],0,39);
				randList[1]=new Vector(sequenceList);
				jet.data.dataformat.parser.Blast.filterSequenceIdentities(randList[1],40,59);
				randList[2]=new Vector(sequenceList);
				jet.data.dataformat.parser.Blast.filterSequenceIdentities(randList[2],60,79);
				randList[3]=new Vector(sequenceList);
				/* L'identité est recupérée sur la sortie blast qui arrondi 
				 * à l'entier inférieur ==> jamais egal à 100 */
				jet.data.dataformat.parser.Blast.filterSequenceIdentities(randList[3],80,99);

				/* Liste echantillon utilisée pour l'alignement "i" courant */
				sampleList=new Vector(1,1);
				sampleList.add(ref);
				/* Echantillonage aleatoire de "numAli" sequences parmis celles recuperees par blast */
				
				for (int nbPart=0;nbPart<nbsSave.length;nbPart++) nbs[nbPart]=nbsSave[nbPart];
				
				int j=0;
				//System.out.println("**********sampling**********");
				while (j<numAli)
				{
					/* Ajouter test pour chaque partition par rapport à nbs */
					
					for (int h=0;h<randList.length;h++)
					{				
						if (randList[h].size()>0)
						{
							if (nbs[h]>0){
								rand=(int)(Math.random()*(double)randList[h].size());
								sampleList.add((jet.data.datatype.Sequence)randList[h].remove(rand));				
								j++;
								nbs[h]--;
							}
						}
					}
				}
				System.out.println("**********write**********");
				/* Ecriture des sequences de sampleList dans un fichier fasta */
			   	new jet.io.file.FastaFileWriter(filename+"_"+i+".fasta",sampleList);
				//	System.out.println("**********start muscle**********");
			   	/* Execution de ClustalW sur ce fichier fasta */
			   	//new jet.external.ClustalW(command,filename+"_"+i+".fasta");
				/* Execution de Muscle sur ce fichier fasta */
			   	new jet.external.Muscle(command,filename+"_"+i+".fasta");
				//  System.out.println("**********end muscle**********");
			   	
			}
			//progress.setProgress(1.0); 
			//progress.stop();
			System.out.println("");

			/* clearing samples from memory */
			sampleList.clear(); sampleList=null;
			sequenceList.clear(); sequenceList=null;
	    }
	    
    /** Calcul des traces des residus de la sequence "ref" sur chaque alignement. */
    
    public Vector makeETAnalysis (String filename, jet.data.datatype.Sequence3D ref,String[] subMatrixFile, double maxCoverage,jet.ProgressIndicator progress) throws Exception
    {
	/* ET analysis of the generated multiple alignments */
	Vector sampleList=null;
	boolean relaunch=true;
	jet.data.datatype.MultiAlignment ma=null;
	
	//	progress=new jet.ProgressIndicator("ET",true);
	//progress.setProgress(0.0);
	
	//	new Thread(progress).start();
	
	jet.tree.tools.ET et=null;
	double[] depth=null;
	Vector depthRec=new Vector(ref.size());
	int iterTree=0;
	int nbBadAlignment=0;
	
	/* "depthRec" est un vecteur composé d'autant de vecteurs que la taille 
	 * de la sequence ref. Un vecteur à la position i stockera toutes les 
	 * traces observées dans les alignements du residu i */
	for(int i=0;i<ref.size();i++) depthRec.add(new Vector(1,1));
	
	/* Pour chaque alignement */
	for(int i=0;i<numMulti;i++)
	    {
		relaunch=true;
		iterTree=0;
		
		/* A la premiere iteration on utilise la matrice blosum 
		 * et a la deuxieme la matrice gonnet (voir parametre config) */
		while((relaunch))
		    {
			/* creation d'un arbre vide */
			et=new jet.tree.tools.ET(subMatrixFile[iterTree]);
			
			//	progress.setProgress((double)i/(double)numMulti);
			
			/* Lecture de l'alignement multiple (fichier clustalW) "i" */
			sampleList=jet.data.dataformat.parser.Clustal.getSequenceData(new jet.io.file.ClustalFileReader(filename+"_"+i+".aln"));
			
			/* Stockage des sequences alignees dans l'alignement ma */
			ma=new jet.data.datatype.MultiAlignment(sampleList);
			
			/* generation de la trace */
			try
			    {
				/* construction de l'arbre */
				jet.tree.tools.NJ nJ=et.generateTree(ma, ref);

				/* Calcule le niveau trace des residus (en double) relatif au niveau 
				 * max considéré pour atteindre une couverture de "coverage"%.
				 * Formule: (Nt-Ltj)/Nt */
				depth=et.getRelativeTraceDepth(et.generateTraceRecord(nJ,ma,ref),maxCoverage);

				relaunch=false;
			    }
			catch(Exception e)
			    { 
				System.out.println("Unable to generate tree with "+subMatrixFile[iterTree]+" matrix for alignment "+i);
				/* Si aucun arbre avec blosum62, on relance */
				relaunch=true; iterTree++;
				/* On fait trois analyses (blosum puis gonnet puis hsdm) */
				if(!(iterTree<subMatrixFile.length)) 
				    { 
					nbBadAlignment++;
					System.out.println("nb bad Alignement :"+nbBadAlignment);
					if ((numMulti-nbBadAlignment)>(numMulti*0.3))
					    {
						relaunch=false;
						depth=null;
					    }
					else
					    {
						System.out.println("Sequence "+ref.getSequenceName()+" will not be analysed"); 
						/* arret */
						//	progress.setProgress(1.0);
						//	progress.stop();
						
						/* Cleaning up sampling and clustalW files */
						File file;
						String name;
						for(i=0;i<numMulti;i++)
						    {
							name=filename+"_"+i;
							file=new File(name+".aln"); file.delete();
							file=new File(name+".dnd"); file.delete();
							//file=new File(name+".fasta"); file.delete();
						    }
						
						
						return null; 
					    }
				    }
				else System.out.println("Trying to generate tree with "+subMatrixFile[iterTree]+" matrix");
				
			    }
			
		    }
		/* Stockage de la relative depth pour chaque residu de la sequence
		 * (on est toujours dans la boucle sur chaque alignement donc il 
		 * peut y en avoir autant que d'alignements par residu) */
		if (depth!=null)
		    {
			for(int j=0;j<depth.length;j++)
			    {	
				/* On evite les niveau de trace à 0 pour lesquelles aucune trace n'a ete trouvée */
				if(depth[j]>0.000001) ((Vector)depthRec.get(j)).add(new Double(depth[j]));
			    }	
		    }
		
		File file;
		String name=filename+"_"+i;
		file=new File(name+".aln"); file.delete();
		file=new File(name+".dnd"); file.delete();
		file=new File(name+".fasta"); file.delete();
		
	    }	
	numMulti=numMulti-nbBadAlignment;
	//	progress.setProgress(1.0);
	//	progress.stop();
	System.out.println("");
	
	/* removing depth table from memory */
	depth=null;
	
	return depthRec;
    }

	    
	    /** Calcul des moyennes des traces sur tous les alignements. */
	    
	    public Vector analyseTrace(String filename, jet.data.datatype.Sequence3D ref, Vector depthRec, double minCutoff) throws Exception
	    {
	    	double[] mu=new double[ref.size()],std=new double[ref.size()],freq=new double[ref.size()];
			Vector traceDepth=null,traceResults=new Vector(ref.size());
				
			/* Boucle sur les traces de chaque residu (i==>toutes les 
			 * traces du residus i observées dans tous les alignements).*/
			for(int i=0;i<depthRec.size();i++)
			{
				traceDepth=(Vector)depthRec.get(i);
				/* Pourcentage d'alignement pour lesquels on a observé une trace pour le residu i */
				freq[i]=((double)traceDepth.size())/((double)numMulti);
				if (traceDepth.size()>1) std[i]=jet.tools.Statistic.standardError(traceDepth);
				else std[i]=0.0;
				mu[i]=jet.tools.Statistic.mean(traceDepth);
			}
			/* removing depth records from memory */
			depthRec.clear(); depthRec=null;
			traceDepth.clear(); traceDepth=null;
			
			for(int i=0;i<mu.length;i++)
			{
				/* On evite les residus pour lesquels on a rien enregistré car ils ne passaient pas les critères */
				if(mu[i]>0.000001)
				{
					if(freq[i]>minCutoff) traceResults.add(new Double(mu[i]*freq[i]));
					else traceResults.add(new Double(0.0));
				}
				else traceResults.add(new Double(0.0));
			}

			/* "Results" : les resultats retournés */
			Vector results=new Vector();
			/* "mu" : la trace, "std" : la dispersion de "mu", "freq" : la frequence de la trace */
			Vector mubis=new Vector(ref.size()),freqbis=new Vector(ref.size());
			for (int pos=0; pos<ref.size(); pos++)
			{
				mubis.add(mu[pos]);
				freqbis.add(freq[pos]);		
			}
			results.add(mubis);		
			
			results.add(freqbis);
			
			/* score en fonction des propriété physico-chimiques */
			Vector pcResults=new Vector(ref.size());
			double max=0.0;
			for (int i=0;i<20;i++) if (jet.data.datatype.Residue.getResiduePC(i)>max) max=jet.data.datatype.Residue.getResiduePC(i);
			for (int i=0;i<ref.getSequenceLength();i++) pcResults.add(jet.data.datatype.Residue.getResiduePC(ref.getResidue(i).getResidueSymbol())/max);
			results.add(pcResults);
			
			results.add(traceResults); 
			
			return results;
	    }
	    
	    /** Lancement de l'analyse JET */
	    
    public Vector doAnalysis(String filename, File multAlignFile, jet.data.datatype.Sequence3D ref)
    {
	
	/* "results" : les resultats retournés */
	Vector results;
	
	try{
	    
	    /* QBlast Query Parameter */
	    String retrievingMethod=cf.getParam("SequenceRetrieving","method").toLowerCase();
	    String format=cf.getParam("SequenceRetrieving","format").toLowerCase();
	    double eValue=cf.getDoubleParam("QBlast","eValue");if (eValue==-1) eValue=1.0E-5;
	    int maxResults=cf.getIntParam("QBlast","results");if (maxResults==-1) maxResults=5000;
	    String url=cf.getParam("QBlast","url");if (url.equals("")) url="http://www.ncbi.nlm.nih.gov/BLAST/Blast.cgi";
	    String database=cf.getParam("QBlast","database");if (database.equals("")) database="nr";
	    String blastDBLocation=cf.getParam("Data","blastDatabases");
	    String matrix=cf.getParam("QBlast","matrix");if (matrix.equals("")) matrix="blosum62";
	    int gap_exist=cf.getIntParam("QBlast","gap_existence");if (gap_exist==-1) gap_exist=11;
	    int gap_ext=cf.getIntParam("QBlast","gap_extension");if (gap_ext==-1) gap_ext=1;
	    int maxIteration=cf.getIntParam("QBlast","max_iter");if (maxIteration==-1) maxIteration=3;
	    
	    /* Sequence Filtering Parameters */
	    int minIdentity=(int)(cf.getDoubleParam("Filter","min_identity")*100.0);if (minIdentity==-1) minIdentity=20;
	    int maxIdentity=(int)(cf.getDoubleParam("Filter","max_identity")*100.0);if (maxIdentity==-1) maxIdentity=98;
	    int maxLoad=(int)(cf.getDoubleParam("Filter","max_load"));if (maxLoad==-1) maxLoad=500000;
	    int maxGapPercent=10;//pourcentage max de gap dans un alignement
	    
	    /* Sample Parameters */
	    double length_cutoff=cf.getDoubleParam("Sample","length_cutoff");if (length_cutoff==-1) length_cutoff=0.80;
	    int minLength=(int)((double)ref.size()*length_cutoff);
	    /* on ajoute 10% de la longueur de la sequence car on 
	     * recupere des sequences alignees avec des gaps. */
	    int maxLength=ref.size()*110/100; 
	    
	    /* ClustalW Parameters */
	    numMulti=cf.getIntParam("ET","msaNumber");
	    numAli=cf.getIntParam("ET","seqNumber");
	    
	    if ((numAli<10)&&(numAli>0)) numAli=10;
	    if (numAli<1) numAli=-1;
	    if (numMulti<1) numMulti=-1;
	    
	    String clustalCommand=cf.getParam("Software","clustalW");
	    String muscleCommand=cf.getParam("Software","muscle");
	    String psiblastCommand=cf.getParam("Software","psiblast");
	    String naccessCommand=cf.getParam("Software","naccess");
	    
	    /* ET Parameters */
	    String dirMatrix=cf.getParam("Data","substMatrix");
	    File blosum=new File(dirMatrix,"blosum62");
	    File gonnet=new File(dirMatrix,"gonnet");
	    File hsdm=new File(dirMatrix,"hsdm");
	    String[] subMatrixFile={blosum.getAbsolutePath(),gonnet.getAbsolutePath(),hsdm.getAbsolutePath()};
	    double maxCoverage=cf.getDoubleParam("ET","coverage");if (maxCoverage==-1) maxCoverage=0.95;
	    double minCutoff=cf.getDoubleParam("ET","freq_cutoff");if (minCutoff==-1) minCutoff=0.0;
	    
	    /*********************************Application start******************************************/
	    
	    jet.ProgressIndicator progress=null;
	    
	    /*****************************sequence retrieving and filtering**********************************/
	    
	    System.out.println("Analysis of sequence: "+ref.getSequenceName());
	    
	    Vector sequenceList=null;
	    Vector multAlignData=null;
	    
	    if (retrievingMethod.equals("input"))
		{
		    if (format.equals("fasta"))
			{
			    /* Obtain homolog sequences from input fasta file */
			    sequenceList=requeteFasta(multAlignFile);
			    if (sequenceList.size()!=0) sequenceList=iterativeSequenceFiltering(sequenceList,ref,maxResults, eValue, minLength, maxLength, minIdentity, maxIdentity,maxGapPercent,ref.size(),maxLoad);
			}
		    if (format.equals("psiblast"))
			{
			    /* Obtain homolog sequences from input psiblast file */
			    multAlignData=jet.io.file.FileIO.readFile(multAlignFile.getAbsolutePath());
			    sequenceList=jet.data.dataformat.parser.BlastPairwise.getPairwiseInfo(multAlignData);
			    if (sequenceList.size()!=0) sequenceList=iterativeSequenceFiltering(sequenceList,ref,maxResults, eValue, minLength, maxLength, minIdentity, maxIdentity,maxGapPercent,ref.size(),maxLoad);
			}
		}
	    else
		{
		    /* Obtain homolog sequences from psiblast server query */
		    if (retrievingMethod.equals("server")) multAlignData= psiBlastServerRequest(ref,url, database, matrix, maxResults, 100.0, gap_exist, gap_ext, maxIteration,filename);
		    /* Obtain homolog sequences from psiblast local query */
		    if (retrievingMethod.equals("local")) multAlignData=psiBlastLocalRequest(psiblastCommand, ref, blastDBLocation+File.separator+database, matrix, maxResults, 100.0, gap_exist, gap_ext, maxIteration,filename);
		    sequenceList=jet.data.dataformat.parser.BlastPairwise.getPairwiseInfo(multAlignData);
		    if (sequenceList.size()!=0) sequenceList=iterativeSequenceFiltering(sequenceList,ref,maxResults, eValue, minLength, maxLength, minIdentity, maxIdentity,maxGapPercent,ref.size(),maxLoad);
		}
	    
	    System.out.println("Nb alignments:"+numMulti+" Nb sequences:"+numAli);
	    
	    if ((sequenceList==null)||(sequenceList.size()==0)) return null;
	    
	    /*
	      Vector ic=null;		
	      ic=jprotein.data.dataformat.parser.BlastPairwise.getAlignmentComposition(sequenceList,ref);
	      ic=jprotein.data.dataformat.parser.BlastPairwise.getCompositionFrequencies(ic);
	      ic=jprotein.data.dataformat.parser.BlastPairwise.getInformationContent(ic);
	    */
	    
	    Vector randList1=new Vector(sequenceList);
	    jet.data.dataformat.parser.Blast.filterSequenceIdentities(randList1,0,39);
	    Vector randList2=new Vector(sequenceList);
	    jet.data.dataformat.parser.Blast.filterSequenceIdentities(randList2,40,59);
	    Vector randList3=new Vector(sequenceList);
	    jet.data.dataformat.parser.Blast.filterSequenceIdentities(randList3,60,79);
	    Vector randList4=new Vector(sequenceList);
	    jet.data.dataformat.parser.Blast.filterSequenceIdentities(randList4,80,99);
	    
	    
	    System.out.println("Final iterated blast partition:"+randList1.size()+" "+randList2.size()+" "+randList3.size()+" "+randList4.size());
	    
	    String pdbCode=filename.substring(filename.lastIndexOf(File.separator)+1,filename.length());
	    String category=pdbCode+":"+ref.getChainId();
	    caracTestFile.addParameter(category, "partition",""+randList1.size()+";"+randList2.size()+";"+randList3.size()+";"+randList4.size(), "partition of sequences after PSI-BLAST+filtering");
	    caracTestFile.addParameter(category, "numMulti",""+numMulti, "Number of multiple alignment");
	    caracTestFile.addParameter(category, "numAli",""+numAli, "Number of sequences in alignment");
	    
	    /**************************END of retrieving and filtering********************************/
	    
	    /*************************SAMPLING and CLUSTALW Aligning*********************************/	
	    
	    /* reducing diversity of sequences partition */
	    
	    /*
	      
	      filterPartition(sequenceList,300);
	      
	    */
	    
	    /* Sample homolog sequences and align them with CLustaW external command */
	    
	    /*
	      
	      numAli=(int)Math.sqrt(sequenceList.size());
	      if (numAli<10) numAli=10;
	      if (numAli>50) numAli=50;
	      numMulti=numAli;
	      
	    */
	    
	    //makeAlignment(filename,ref,sequenceList,clustalCommand,progress);
	    makeAlignment(filename,ref,sequenceList,muscleCommand,progress);	
	    
	    /********************************END OF SAMPLING****************************************************/
	    
	    /*******************************START OF ET ANALYSIS************************************************/
	    
	    /* ET analysis of the generated multiple alignments */
	    
	    Vector depthRec=makeETAnalysis(filename,ref,subMatrixFile,maxCoverage,progress);
	    if (depthRec==null) return null;
	    
	    /**************************END OF ET ANALYSIS********************/
	    
	    /********************* Analyse statistique des résultats d'ET *****************/
	    
	    results=analyseTrace(filename,ref,depthRec,minCutoff);
	    
	}catch(Exception e)
	    {
		results=null;
		System.err.println("JET error analysis for the file:"+filename);
		System.err.println("No trace computed for this file");
		e.printStackTrace();
	    }
	return results;	    
    }	
    
	    /** Ecriture des résultats */
	    
	    public void writeResult(Vector result, Vector nom_colonnes, File pdbfile)
	    {
	    	String filename=pdbfile.getPath();
			filename=filename.substring(0,filename.lastIndexOf("."));
			Result.WriteResult(result, nom_colonnes, filename+"_jet.res");
			jet.io.file.PdbFileTransform pdbft;
			pdbft= new jet.io.file.PdbFileTransform(pdbfile.getPath());
			pdbft.cut(new Vector(),true);
	    }
	
	private Vector limitLoad(Vector seq1, Vector seq2, Vector seq3, Vector seq4, int seqLength , int maxLoad)
	{
		Vector result = new Vector();		
		int l1 = seq1.size();
		int l2 = seq2.size();
		int l3 = seq3.size();
		int l4 = seq4.size();
		int outl1,outl2,outl3,outl4;

		int upperBound = Math.max(Math.max(l1,l2),Math.max(l3,l4));
		int lowerBound = 0;
		int cutoff = -1;
		if ((l1+l2+l3+l4)*seqLength>maxLoad)
		{
			while (lowerBound<upperBound)
			{
				cutoff = lowerBound+(upperBound-lowerBound)/2;
				outl1 = Math.min(l1,cutoff);
				outl2 = Math.min(l2,cutoff);
				outl3 = Math.min(l3,cutoff);
				outl4 = Math.min(l4,cutoff);
				if ((outl1+outl2+outl3+outl4)*seqLength>maxLoad)
				{
					upperBound = cutoff-1;
				}
				else if ((outl1+outl2+outl3+outl4)*seqLength<maxLoad)
				{
					lowerBound = cutoff+1;
				}
				else
				{
					lowerBound = cutoff;
					upperBound = cutoff;
				}
			}
			restrictRandom(seq1,cutoff);
			restrictRandom(seq2,cutoff);
			restrictRandom(seq3,cutoff);
			restrictRandom(seq4,cutoff);
		}
		result.addAll(seq1);
		result.addAll(seq2);
		result.addAll(seq3);
		result.addAll(seq4);
		return result;
	}
	
	Random _rnd = new Random();

	private void restrictRandom(Vector v, int n)
	{
		while(v.size()>n)
		{
			int i = _rnd.nextInt(v.size());
			v.removeElementAt(i);
		}
	}
	    
}

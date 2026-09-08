package jet.tools;
import java.util.*;

/** Classe gérant l'initialisation des parametres et le lancement du clustering 3D des residus traces:
 * Calcul des graines, extension des graines, calcul des seuils pour la taille des clusters via
 * la génération alétoire de clusters*/

public class MapClusters 
{
    
    /** Initialisation et lancement de la clusterisation des acides aminés accessibles (surface) 
     * de la séquence ref, en fonction de leur score de trace et/ou de pc (dépend de analysisType).  
     * Deux résidus clusterisent si leur distance est inférieur à radius.
     * Les clusters couvre une proportion de la surface dépendante de percentInterface.
     * Ce parametre est soit donné en entrée soit évalué par une relation entre la taille des interfaces 
     * et des surfaces (percentInterface=26.54/surfSize+0.03)*/
    static int cluster_count = 1;
    
    public static void reset_cluster_count() { cluster_count=1; }
    
    public static Vector map(jet.data.datatype.Sequence3D ref, Vector trace, Vector pc, Vector surface, Vector cv, Vector cvlocal, float radius, String analysisType, int complete, int layers, double percentInterface)
    {
	jet.cluster.Clustering clustering=new jet.cluster.Clustering(ref,radius);
	
	Vector clusterTable=new Vector();
	Vector clusterTableTMP=new Vector();
	Vector clusterData=new Vector(1,1);

	Vector clusterListMain=new Vector();
	Vector clusterDataMain=new Vector(1,1);

	int i,j;

	int surfSize=0;

	boolean minScore=true;
        boolean goodToComplete;
	Vector statistique=new Vector();

	for(i=0;i<surface.size();i++) if ((Double)surface.get(i)==1.0) surfSize=surfSize+1;
	
	if (percentInterface==-1) percentInterface=(26.54/(double)surfSize)+0.03;
	
	System.out.println("surface size:"+surfSize);
	System.out.println("percent interface used:"+percentInterface);
	
	double minScoreResiduGraine,minScoreClusterGraine,minScoreResidu,minScoreCluster;
	
	Vector scoreSeeds=new Vector();
	Vector scoreExtension=new Vector();
	Vector scoreRim=new Vector();
	Vector pcModifiedMoy=new Vector();
	Vector sortedScore;
	boolean doUnion=false;
	
	// calculation of the average trace value trace(j)	
	Vector traceMoy=clustering.calculScoreMoy(trace,surface);
	int nbHigh=0;
	int nbVeryHigh=0;
	Double meanTrace=0.0;
	Double sdTrace=0.0;
	for (i=0;i<trace.size();i++)
	    {
		if ((Double)traceMoy.get(i)>0.6)
		    {
			nbHigh++;
			if ((Double)traceMoy.get(i)>0.8)
			    {
				nbVeryHigh++;
			    }
		    }
		if ( ((Double)traceMoy.get(i)>0)||((Double)traceMoy.get(i)+(Double)trace.get(i)==0) )
		    {
			meanTrace=meanTrace+(Double)traceMoy.get(i)/surfSize;
		    }
	    }
	for (i=0;i<trace.size();i++)
	    {
		if ( ((Double)traceMoy.get(i)>0)||((Double)traceMoy.get(i)+(Double)trace.get(i)==0) )
		    {
			sdTrace=sdTrace+((meanTrace-(Double)traceMoy.get(i))*(meanTrace-(Double)traceMoy.get(i)))/surfSize;
		    }
	    }

	System.out.print("ratio between conserved residue number and percentInterface:");
	System.out.print(" "+(double)(nbHigh)/surfSize/percentInterface);
	System.out.println("");
	System.out.print("mean trace over the surface:");
	System.out.print(" "+meanTrace);
	System.out.println("");
	System.out.print("sd trace over the surface:");
	System.out.print(" "+sdTrace);
	System.out.println("");

	if ( (((double)(nbHigh)/surfSize/percentInterface) <= 0.3) && analysisType.equals("0") )
	    {
		analysisType="5";
	    }
	
	double max=0.0;
	/* calcul des valeurs utilisées pour l'analyse en propriétés physico-chimiques.
	 * trace*pc² avec utilisation des valeur de pc originelle (0 à 2.21 et pas entre 0 et 1).*/
	for (i=0;i<20;i++) if (jet.data.datatype.Residue.getResiduePC(i)>max) max=jet.data.datatype.Residue.getResiduePC(i);
	// calculation of the p(j)=d(j)*propensity(j)^2
	for (i=0;i<trace.size();i++) pcModifiedMoy.add((Double)trace.get(i)*(Math.pow(((Double)pc.get(i))*max,2)));
	// calculation of the average pctrace value pctrace(j)
	pcModifiedMoy=clustering.calculScoreMoy(pcModifiedMoy,surface);	

	Vector analyse=new Vector();
	Vector seuilConf=new Vector();
	Vector seuilMoyCluster=new Vector();
	Vector seuilResidu=new Vector();
	Vector forcedResults=new Vector();
	
	/* Initialisation des scores à clustériser pour le calcul des graines 
	 * en fonction de l'analyse voulue en entrée  */

	boolean stillTrying=true;
	boolean doNotScratch=false;

	while(stillTrying)
	    {
		// Analyse basée sur la trace uniquement
		if (analysisType.equals("1"))
		    {
			System.out.println("Analysis type chosen is 1 for "+ref.getSequenceName()+": evolutionary information only");
			/* Clusterisation basée sur la trace (conservation) */
			analyse.add(traceMoy);
			/* Extension basée sur la trace (conservation) */
			scoreExtension=traceMoy;
			/* seuil de confiance (sur la distribution des tailles des 
			 * clusters générés aléatoirement) pour la taille des clusters sélectionnés */
			seuilConf.add(0.1);
			/* La valeur seuil moyenne pour le calcul de la graine est celle du résidu dont le score 
			 * est à la position (percentInterface/4.0) de la distribution triée des scores*/
			seuilMoyCluster.add(percentInterface/4.0);
			//seuilMoyCluster.add(percentInterface/16.0);
			/* Valeur seuil pour considérer un résidu */
			seuilResidu.add((percentInterface*2.0>1.0)?1.0:(percentInterface*2.0));
			/* si true alors on permet de baisser les seuil de confiance afin de récupérer au moins un cluster.
			 * Donc si false on peut obtenir aucun cluster. */
			forcedResults.add(true);
		    }
		
		// analyse basée sur la trace et sur les trace & propriétés physico-chimiques
		if (analysisType.equals("2"))
		    {
			System.out.println("Analysis type chosen is 2 for "+ref.getSequenceName()+": evolutionary trace and physical-chemical properties");
			/* Clusterisation basée sur la trace (conservation) et les proprietes physico-chimiques*/
			analyse.add(traceMoy);
			analyse.add(pcModifiedMoy);
			
			/* Initialisation des scores à clustériser pour la phase d'extension des graines  */
			for (i=0;i<trace.size();i++) scoreExtension.add(((Double)trace.get(i)+(Double)pc.get(i))/2.0);
			scoreExtension=clustering.calculScoreMoy(scoreExtension,surface);
			
			seuilConf.add(0.1);
			seuilConf.add(0.1);
			// cluster-trace threshold	
			seuilMoyCluster.add(percentInterface/4.0);
			seuilMoyCluster.add(percentInterface/4.0);
			// residue trace threshold
			seuilResidu.add((percentInterface*2.0>1.0)?1.0:(percentInterface*2.0));
			seuilResidu.add((percentInterface*2.0>1.0)?1.0:(percentInterface*2.0));
			
			forcedResults.add(true);
			/* on ne force pas l'obtention d'un cluster pour l'analyse via pc */
			forcedResults.add(false);
			doNotScratch=true;
		    }
		
		// Analyse basée sur la trace uniquement pour les seeds, sur la mixed trace pour les extensions
		if (analysisType.equals("3")||analysisType.equals("0"))
		    {
			System.out.println("Analysis type chosen is 3 for "+ref.getSequenceName()+": (1) evolutionary trace only for the seeds, (2) trace and interface propensities for the extension, (3) circular variance and interface propensities for the outer layer");
			/* Clusterisation basée sur la trace (conservation) */
			analyse.add(traceMoy);
			/* Initialisation des scores à clustériser pour la phase d'extension des graines  */
			for (i=0;i<trace.size();i++) scoreExtension.add(((Double)trace.get(i)+(Double)pc.get(i))/2.0);
			scoreExtension=clustering.calculScoreMoy(scoreExtension,surface);
			/* Initialisation des scores à clustériser pour la phase d'extension finale des clusters  */
			for (i=0;i<trace.size();i++) scoreRim.add(((1.0 - (Double)cvlocal.get(i))+(Double)pc.get(i))/2.0);
			scoreRim=clustering.calculScoreMoy(scoreRim,surface);

			/* seuil de confiance (sur la distribution des tailles des 
			 * clusters générés aléatoirement) pour la taille des clusters sélectionnés */
			seuilConf.add(0.1);
			/* La valeur seuil moyenne pour le calcul de la graine est celle du résidu dont le score 
			 * est à la position (percentInterface/4.0) de la distribution triée des scores*/
			seuilMoyCluster.add(percentInterface/4.0);
			/* Valeur seuil pour considérer un résidu */
			seuilResidu.add((percentInterface*2.0>1.0)?1.0:(percentInterface*2.0));
			/* si true alors on permet de baisser les seuil de confiance afin de récupérer au moins un cluster.
			 * Donc si false on peut obtenir aucun cluster. */
			forcedResults.add(false);
		    }
		
		
		// Analyse basée sur la mixed cvtrace pour les seeds, sur la mixed cvtrace pour les extensions
		if (analysisType.equals("4"))
		    {
			System.out.println("Analysis type chosen is 4 for "+ref.getSequenceName()+": combination of trace and circular variance for seed detection and extension");
			doUnion=true;
			/* Clusterisation basée sur la cvTrace (conservation+cv) */
			Vector cvTrace=new Vector();
			for (i=0;i<trace.size();i++) cvTrace.add(((Double)trace.get(i)+(1.0 - (Double)cvlocal.get(i)))/2.0);
			cvTrace=clustering.calculScoreMoy(cvTrace,surface);
			analyse.add(cvTrace);
			/* Initialisation des scores à clustériser pour la phase d'extension des graines  */
			for (i=0;i<trace.size();i++) scoreExtension.add(((Double)trace.get(i)+(1.0 - (Double)cvlocal.get(i)))/2.0);
			scoreExtension=clustering.calculScoreMoy(scoreExtension,surface);
			/* Initialisation des scores à clustériser pour l'addition d'une couche extérieure  */
			for (i=0;i<trace.size();i++) scoreRim.add(((1.0 - (Double)cvlocal.get(i))+(Double)pc.get(i))/2.0);
			scoreRim=clustering.calculScoreMoy(scoreRim,surface);

			/* seuil de confiance (sur la distribution des tailles des 
			 * clusters générés aléatoirement) pour la taille des clusters sélectionnés */
			seuilConf.add(1.0);
			/* La valeur seuil moyenne pour le calcul de la graine est celle du résidu dont le score 
			 * est à la position (percentInterface/4.0) de la distribution triée des scores*/
			seuilMoyCluster.add(percentInterface/4.0);
			/* Valeur seuil pour considérer un résidu */
			seuilResidu.add((percentInterface*2.0>1.0)?1.0:(percentInterface*2.0));
			/* si true alors on permet de baisser les seuil de confiance afin de récupérer au moins un cluster.
			 * Donc si false on peut obtenir aucun cluster. */
			forcedResults.add(true);
		    }
		
		// Analyse basée sur la mixed pcCV pour les seeds, sur la mixed pcCV pour les extensions
                if (analysisType.equals("5"))
                    {
			System.out.println("Analysis type chosen is 5 for "+ref.getSequenceName()+": geometrical and physical-chemical properties only");
                        /* Clusterisation basée sur la pcCV (pc2*cv) */
                        Vector pcCV=new Vector();
			// calculation of the p(j)=d(j)*propensity(j)^2
        		//for (i=0;i<trace.size();i++) pcCV.add((1.0 - (Double)cv.get(i))*(Math.pow(((Double)pc.get(i))*max,2)));
			if(complete==2){for (i=0;i<trace.size();i++) pcCV.add(((1.0 - (Double)cvlocal.get(i))+(Double)pc.get(i))/2.0);}
                        else{for (i=0;i<trace.size();i++) pcCV.add(((1.0 - (Double)cv.get(i))+(Double)pc.get(i))/2.0);}
			pcCV=clustering.calculScoreMoy(pcCV,surface);
                        analyse.add(pcCV);
                        /* Initialisation des scores à clustériser pour la phase d'extension des graines  */
                        if(complete==2){for (i=0;i<trace.size();i++) scoreExtension.add(((1.0 - (Double)cvlocal.get(i))+(Double)pc.get(i))/2.0);}
			else{for (i=0;i<trace.size();i++) scoreExtension.add(((1.0 - (Double)cv.get(i))+(Double)pc.get(i))/2.0);}
                        scoreExtension=clustering.calculScoreMoy(scoreExtension,surface);
			// !!!!!!!!!!!!! try this out !!!!!!!!!
			if(complete==2){for (i=0;i<trace.size();i++) scoreRim.add(((1.0 - (Double)cvlocal.get(i))+(Double)pc.get(i))/2.0);}
			else{for (i=0;i<trace.size();i++) scoreRim.add(((1.0 - (Double)cv.get(i))+(Double)pc.get(i))/2.0);}
			//for (i=0;i<trace.size();i++) scoreRim.add(1.0 - (Double)cv.get(i));
			scoreRim=clustering.calculScoreMoy(scoreRim,surface);

                        /* seuil de confiance (sur la distribution des tailles des 
                         * clusters générés aléatoirement) pour la taille des clusters sélectionnés */
                        seuilConf.add(1.0);
                        /* La valeur seuil moyenne pour le calcul de la graine est celle du résidu dont le score 
                         * est à la position (percentInterface/4.0) de la distribution triée des scores*/
                        seuilMoyCluster.add(percentInterface/4.0);
                        /* Valeur seuil pour considérer un résidu */
                        seuilResidu.add((percentInterface*2.0>1.0)?1.0:(percentInterface*2.0));
                        /* si true alors on permet de baisser les seuil de confiance afin de récupérer au moins un cluster.
                         * Donc si false on peut obtenir aucun cluster. */
                        forcedResults.add(true);
                    }
	
		jet.cluster.data.Cluster clust;
		
		/* lancement des calculs des graines (pc et trace ou seulement trace) */
		// nbAnalyse span a range from 0 to 1 corresponding to the number of analyses
		for (int nbAnalyse=0; nbAnalyse<analyse.size();nbAnalyse++)
		    {
			// the array analyse contains score vectors
			scoreSeeds=(Vector)analyse.get(nbAnalyse);

			/* tri des scores */
			sortedScore = (Vector)jet.tools.OrderValue.orderProperty(scoreSeeds).get(1);
			/* récupération des seuils initialisés précédemment */
			// cutoff value of the trace to select "residue-trace threshold" residues
			minScoreResiduGraine=jet.tools.OrderValue.percent(sortedScore, (Double)seuilResidu.get(nbAnalyse), false);
			// cutoff value of the trace to select "cluster-trace threshold" residues
			minScoreClusterGraine=jet.tools.OrderValue.percent(sortedScore, (Double)seuilMoyCluster.get(nbAnalyse), false);
			
			System.out.println("******* scoreResGraine:"+minScoreResiduGraine+"******* scoreClusGraine:"+minScoreClusterGraine+"********");
			
			// be aware that the list of clusters in clustering is initialize prior to detection
			generateSeeds(clustering,scoreSeeds,pc,traceMoy,minScoreResiduGraine,minScoreClusterGraine,surfSize,minScore);
			
			if( !(analysisType.equals("4")) && !(analysisType.equals("5")))
			    {
				System.out.println("Filter by size after seed generation");
				/* Génération aléatoire de cluster en respectant la meme procedur de clusterisation */
				// returns the distribution of clusters numbers over 6000 iterations and distribution of clusters cores max size over the total nb of clusters 
				statistique=randomTraceMap(ref,scoreSeeds,pc,traceMoy,surface,radius,6000, minScoreResiduGraine,minScoreClusterGraine,minScore);
				// among the identified clusters, retain only those that have a significantly large size 
				if (((Vector)clustering.getClusterList()).size()>0) clustering.setClusterList(selectSeeds(((Vector)clustering.getClusterList()),statistique,(Double)seuilConf.get(nbAnalyse),(Boolean)forcedResults.get(nbAnalyse)));
			    }
			// merge clusters whose residues are neighbors or that are included one in the other
			// previous clusters are erased except if anal=2 (2 kinds of seeds detection one after the other)
			clusterTableTMP=clustering.mergeClust();
			if(doNotScratch)
			    {
				for(int k=0;k<clusterTableTMP.size();k++)
				    {
					clusterTable.add((jet.cluster.data.Cluster)clusterTableTMP.get(k));
				    }
			    }
			else
			    {
				clusterTable=clusterTableTMP;
			    }	
		    }
		
		System.out.print(" selected cluster graine size:");
		for(i=0;i<clusterTable.size();i++)
		    {
			clust=(jet.cluster.data.Cluster)clusterTable.get(i);
			System.out.print(" "+clust.getCoreIndex().size());
		    }
		System.out.println("");
		
		clustering.setClusterList(clusterTable);
		
		System.out.print("predicted mean scores for seeds:");
		System.out.print(" "+clustering.getClusterMoys(scoreSeeds));
		System.out.println("");
		System.out.print("predicted dispersion scores for seeds:");
		System.out.print(" "+clustering.getClusterSDs(scoreSeeds));
		System.out.println("");
		
		boolean goodToGo=true;

		/* test whether the seeds pass all criteria required to be considered protein-protein binding site*/
		if(analysisType.equals("0"))
		    {
			System.out.println("Test of the quality of the detected seeds...");
			// compute mean and dispersion
			Vector clusterMoys = clustering.getClusterMoys(scoreSeeds);
			Vector clusterSDs = clustering.getClusterSDs(scoreSeeds);

			boolean notGoodShape=false;
			Double s;

			if(clusterTable.size()==0){
			    System.out.println("No cluster seed !!!");
			    clustering.clearClusterList();
			    clusterTable=new Vector();
			    analyse.clear();
			    scoreExtension.clear();
			    scoreRim.clear();
			    seuilConf.clear();
			    seuilMoyCluster.clear();
			    seuilResidu.clear();
			    forcedResults.clear();
			    analysisType="5";
			    goodToGo=false;
			    stillTrying=true;
			}

			else{
			    for(i=0;i<clusterTable.size();i++)
				{
				    if( ((Double)clusterSDs.get(i) / (Double)clusterMoys.get(i)) <= (sdTrace/meanTrace/6) )
					{
					    notGoodShape=true;
					}
				}
			    
			    if(notGoodShape)
				{
				    System.out.println("Seeds did not pass the test !");
				    clustering.clearClusterList();
				    clusterTable=new Vector();
				    analyse.clear();
				    scoreExtension.clear();
				    scoreRim.clear();
				    seuilConf.clear();
				    seuilMoyCluster.clear();
				    seuilResidu.clear();
				    forcedResults.clear();
				    analysisType="4";
				    doUnion=true;
				    goodToGo=false;
				    stillTrying=true;
				}
			}
		    }

		if(goodToGo)
		    {
			/* réinitialisation des seuils pour le score moyen des clusters et le score des résidus 
			 * en vue de la phase d'extension des graines.*/

			// score Extension is the quantity mtrace(j)
			sortedScore = (Vector)jet.tools.OrderValue.orderProperty(scoreExtension).get(1);
			// cutoff value of the trace to select "residue-mixed-trace threshold" residues
			minScoreResidu=jet.tools.OrderValue.percent(sortedScore, (percentInterface*2.0>1.0)?1.0:(percentInterface*2.0), false);
			// cutoff value of the trace to select "cluster-mixed-trace threshold" residues	
			minScoreCluster=jet.tools.OrderValue.percent(sortedScore, percentInterface/2.0, false);
			
			System.out.println("******* scoreResExt:"+minScoreResidu+"******* scoreClusExt:"+minScoreCluster+"********");
			/* Calcul des extensions si on a obtenu des graines */
			if ( ((Vector)clustering.getClusterList()).size()>0 && layers>1 ) spreadSeedsLocalMax(clustering,scoreExtension,pc,traceMoy,minScoreResidu,minScoreCluster,surfSize);
			
			if ( analysisType.equals("4") || analysisType.equals("5"))
			    {
				System.out.println("Filter by size after extension");
				/* Génération aléatoire de cluster en respectant la meme procedure de clusterisation */
				// returns the distribution of clusters numbers over 6000 iterations and distribution of clusters cores max size over the total nb of clusters 
				statistique=randomTraceMap(ref,scoreSeeds,pc,traceMoy,surface,radius,6000, minScoreResidu,minScoreCluster,minScore);
				// among the identified clusters, retain only those that have a significantly large size
                                // only in the case we are in the main scoring scheme 
                                double seuilConf45 = 0.1;
                                if ( (complete==2) && !(doUnion) ){seuilConf45 = 1;}  
				if (((Vector)clustering.getClusterList()).size()>0) clustering.setClusterList(selectSeeds(((Vector)clustering.getClusterList()),statistique,seuilConf45,true));
				
				// merge clusters whose residues are neighbors or that are included one in the other
				clusterTable=clustering.mergeClust();
			    }
			
			System.out.print("predicted cluster size after spread:");
			
			/* Recuperation des scores des residus clusterises */
			clusterData=getClusteredScores(ref,clusterTable,scoreExtension);
			System.out.println("");
			
			
			if (!(analysisType.equals("1")) && !(analysisType.equals("2")))
			    {
				/* réinitialisation des seuils pour le score moyen des rim de clusters et le score des résidus 
				 * en vue de la phase d'extension des clusters par ajout de rim.*/
				sortedScore = (Vector)jet.tools.OrderValue.orderProperty(scoreRim).get(1);
				// cutoff value of the trace to select "residue-mixed-trace threshold" residues
				minScoreResidu=jet.tools.OrderValue.percent(sortedScore, (percentInterface*2.0>1.0)?1.0:(percentInterface*2.0), false);
				System.out.println("******* scoreResRim:"+minScoreResidu+"******* scoreClusRim: Core mean score********");
				if ( ((Vector)clustering.getClusterList()).size()>0 && layers>2 ) spreadRim(clustering,scoreRim,cv,traceMoy,minScoreResidu,surfSize);
			    }
			
			System.out.print("predicted cluster size after rim spread:");
			
			/* Recuperation des scores des residus clusterises */
			clusterData=getClusteredScores(ref,clusterTable,scoreExtension);
			System.out.println("");
			/* to increase the counter count, next chain analysed will have another cluster number */
			if( !analysisType.equals("3") && !analysisType.equals("4") && !analysisType.equals("0") && (complete==0) ){
			    cluster_count += clusterTable.size();}
			
			double seuilConfiance=0.1;
			System.out.println("Seuil de confiance de 0.1 à 1.0:");
			System.out.print("==>seuil pour la taille des clusters:");
			while (seuilConfiance<=1.0)
			    {
				System.out.print(" "+atConfidence(seuilConfiance,(int[])statistique.get(1),true));
				seuilConfiance=seuilConfiance+0.1;
			    }
			System.out.println("");
			System.out.print("==>seuil pour le nombre de clusters:");
			seuilConfiance=0.1;
			while (seuilConfiance<=1.0)
			    {
				System.out.print(" "+atConfidence(seuilConfiance,(int[])statistique.get(0),false));
				seuilConfiance=seuilConfiance+0.1;
			    }
			System.out.println("");
			if(complete<2){
 			    System.out.println("The patches for "+ref.getSequenceName()+" were predicted by using anal "+analysisType);}
			
			if (clusterTable.size()<=0) System.out.println("no valid cluster");
			
			stillTrying=false;
		    }
		else
		    {
			clusterData=null;
		    }

		if(complete==2){
		    // clusterListMain will be modified by merging those clusters that can be complemented with the corresponding secondary cluster
                    clusterTableTMP.clear();
                    for(int k=0;k<clusterListMain.size();k++)
                    {
                        clusterTableTMP.add((jet.cluster.data.Cluster)(clusterListMain.get(k)));
                    }
		    if (doUnion)
			{
			    for(int k=0;k<((Vector)clustering.getClusterList()).size();k++)
				{
				    clusterListMain.add((jet.cluster.data.Cluster)(((Vector)clustering.getClusterList()).get(k)));
				}
			}
		    else
			{
			    clustering.extendClust(clusterListMain);
			}
		    clustering.setClusterList(clusterListMain);
		    clusterTable=clustering.mergeClust();
                    goodToComplete=true;
                    for(int k=0;k<clusterTable.size();k++)
                    {
                       clust=(jet.cluster.data.Cluster)clusterTable.get(k);
                       System.out.print((double) clust.getCoreIndex().size() / surfSize);
                       if( ((double) clust.getCoreIndex().size() / surfSize) > 0.5 )
                       {
                         goodToComplete=false;
                       }
                    }
                    if(!goodToComplete){
                    clusterTable=clusterTableTMP;}
		    cluster_count += clusterTable.size();
		    System.out.print("Final size:");
		    clusterData=getClusteredScores(ref,clusterTable,scoreExtension);
		    System.out.println("");
		}
		else{
		    // if the anal type is 3 or 4 or 5 and we do have valid cluster(s) and we want to complete the prediction
		    if( (complete==1) && clusterTable.size()>0 && !stillTrying && (analysisType.equals("3") || analysisType.equals("0") || analysisType.equals("4") || analysisType.equals("5")) ){

                        goodToComplete=true;
                        for(int k=0;k<clusterTable.size();k++)
                        {
                           clust=(jet.cluster.data.Cluster)clusterTable.get(k);
                           if( ((double) clust.getCoreIndex().size() / surfSize) > 0.5 )
                           {
                             goodToComplete=false;
                           }
                        }
                        if(goodToComplete){
			System.out.print("Search for extension by other method: anal");
			for(int k=0;k<((Vector)clustering.getClusterList()).size();k++)
			    {
				clusterListMain.add((jet.cluster.data.Cluster)((Vector)clustering.getClusterList()).get(k));
			    }
			clustering.clearClusterList();
			clusterDataMain=clusterData;
			clusterTable=new Vector();
			analyse.clear();
			scoreExtension.clear();
			scoreRim.clear();
			seuilConf.clear();
			seuilMoyCluster.clear();
			seuilResidu.clear();
			forcedResults.clear();
			
			if( analysisType.equals("3") || analysisType.equals("4") || analysisType.equals("0") ){
			    analysisType="5";
			}
			else{
			    analysisType="4";
			}
			
			System.out.println(analysisType);
			stillTrying=true;
			complete+=1;
		    }}		
		}
		
	    }
	cluster_count += 1;
	return clusterData;
    }

    /** for each residue in index, clusterise it if possible
     * - add it to an existing cluster if it is in the neighbor list and its score is above minScoreClusterGraine
     * - create a new cluster if it is not present in any neighbor list and its score is above minScoreClusterGraine
     * - do nothing otherwise */
    public static void generateSeeds(jet.cluster.Clustering clustering, Vector score,Vector pc, Vector trace, double minScoreResidu, double minScoreCluster, int surfSize, boolean minScore)
    {

	Vector index=new Vector(1,1);
	int clustersSize;	
	double coverage=0.0;


	index.clear();
	clustersSize=0;
	
	clustering.setClusterList(new Vector());
				
	for (int y=0;y<score.size();y++) index.add(y);
	// System.out.println(index.size());

	// retain only scores (traces or pctraces) above the cutoff value for residues
	index=filtrerScore(score,index,minScoreResidu);
	//System.out.println(index.size());
	// get indices for ordered retained scores 
	index=trierScore(score,index);
	//System.out.println(index.size());
	clustering.cluster(index,score,pc,trace,minScoreCluster,surfSize,minScore);
		
	// compute the total coverage of the surface by the clusters cores 	
	for(int i=0;i<((Vector)clustering.getClusterList()).size();i++)
	    clustersSize=clustersSize+((jet.cluster.data.Cluster)((Vector)clustering.getClusterList()).get(i)).getCoreIndex().size();
	if (surfSize>0) coverage=((double)clustersSize)/((double)surfSize);
    }
    
	/** Sélection des clusters du vecteur clusterTable vérifiant la statistique pour le seuil de 
	 * confiance (Pvaleur) seuilConfiance. Si forcedResults=true la sélection est relachée 
	 * jusqu'à l'obtention d'un cluster. */
	
    public static Vector selectSeeds(Vector clusterTable, Vector statistique,double seuilConfiance, boolean forcedResults)
    {
    	
    	jet.cluster.data.Cluster clust;
    	boolean validCluster=false;
    	int minClusterSize=0;
    	
    	Vector clusterTableGraine=new Vector();
    	Vector taillesCluster=new Vector();
    		
    	taillesCluster.clear();
    		
    	System.out.print("predicted cluster graine size:");
    	for(int i=0;i<clusterTable.size();i++)
    	{
    		clust=(jet.cluster.data.Cluster)clusterTable.get(i);
    		taillesCluster.add(clust.getCoreIndex().size());
    		System.out.print(" "+((Integer)taillesCluster.get(i)).intValue());
    	}
    	System.out.println("");
    	
    	int nbClus=clusterTable.size();
	// calculation of the fraction of iterations displaying nbClus or less clusters
    	double confNbClus=whichConfidence(nbClus, (int[])statistique.get(0), false);
    	System.out.println("Confidence threshold for the number of clusters predicted:"+confNbClus);
    	int clusSizeMax=0;
    	for(int i=0;i<clusterTable.size();i++)
    	{
    		if (((Integer)taillesCluster.get(i)).intValue()>clusSizeMax)
    		{
    			clusSizeMax=((jet.cluster.data.Cluster)clusterTable.get(i)).getCoreIndex().size();
    		}
    	}
    	double confTailleClus;
    	double scoreTraceMoyClus;
    	double scoreFinal;
    	double scoreTraceMoyAll=0.0;
    	double scorePcMoyClus;
    	int nbResidus=0;
	// calculation of the fraction of iterations displaying clusSizeMax or larger size as maximum core size
    	System.out.println("Confidence threshold for the size of the largest cluster:"+whichConfidence(clusSizeMax, (int[])statistique.get(1), true));
    		
	// calculation of the mean traceMoy value over all clusters
    	for(int i=0;i<clusterTable.size();i++)
    	{
    		scoreTraceMoyAll=scoreTraceMoyAll+((jet.cluster.data.Cluster)clusterTable.get(i)).getTraceMoy()*((Integer)taillesCluster.get(i)).intValue();
    		nbResidus=nbResidus+((Integer)taillesCluster.get(i)).intValue();
    	}
    	scoreTraceMoyAll=scoreTraceMoyAll/(double)nbResidus;
    	
    	System.out.print("fixing cluster size to:");
    	while ((!validCluster)&&(seuilConfiance<=1.0))
    	{
		// determine the minimum size displayed by 10% of the iterations
    		minClusterSize=atConfidence(seuilConfiance,(int[])statistique.get(1),true);
    		
    		System.out.println("..."+minClusterSize);
    			
    		for(int i=0;i<clusterTable.size();i++)
    		{
    			confTailleClus=whichConfidence(((Integer)taillesCluster.get(i)).intValue(), (int[])statistique.get(1), true);
    			scoreTraceMoyClus=scoreTraceMoyAll/((jet.cluster.data.Cluster)clusterTable.get(i)).getTraceMoy();
    			scorePcMoyClus=((jet.cluster.data.Cluster)clusterTable.get(i)).getpcMoy()*jet.data.datatype.Residue.getResiduePC("PHE");
    			
    			scoreFinal=confTailleClus;
    				
    			System.out.print(" cluster "+i+" score:"+scoreFinal);
    			System.out.print(" scoreTaille:"+confTailleClus);
    			System.out.print(" scClus:"+scoreTraceMoyClus);
    			System.out.print(" scPcClus:"+scorePcMoyClus);
    		
			// clusters are considered ok if their size is larger than the minimum size displayed in 10% of iterations	
    			if (scoreFinal<=seuilConfiance)
    	    		
    			{
    				validCluster=true;
    				clusterTableGraine.add(clusterTable.get(i));
    				System.out.println(" ... ok:");
    			}
    			else System.out.println(" ... not ok:");
    				
    		}
		// in the case no cluster is found, we relax the confidence constraint (analysis based on trace only)
    		if (forcedResults)  seuilConfiance=seuilConfiance+0.1;
    		else seuilConfiance=1.1;
    	}

	// if still no cluster could be labelled "ok" take the biggest one anyway
    	if ((forcedResults)&&(!validCluster))
    	{
    		for(int i=0;i<clusterTable.size();i++)
    		{
    			if (((Integer)taillesCluster.get(i)).intValue()==clusSizeMax)
    			{
    				validCluster=true;
    				clusterTableGraine.add(clusterTable.get(i));
    			}
    		}
    	}
    		
    	return clusterTableGraine;
    }
    
    /** Extension des clusters de clustering */
    
    public static void spreadSeeds(jet.cluster.Clustering clustering, Vector score,Vector pc, Vector trace, double minTraceResidu, double minTraceCluster, int surfSize)
    {
    	Vector neighbour=new Vector();
    	Vector index=new Vector();
    	Vector clusterTableGraine=clustering.getClusterList();
	int nb=0;
	boolean arret=false;

	double maxTraceResidu=1;
       
	// stop when clusters do not evolve anymore
	while(!arret)
	    {
		nb++;			
		// remove all elements from index vector (so index is empty)
		index.clear();
		// non-redundant concatenation of the neighbours lists of all clusters
		for (int i=0;i<clusterTableGraine.size();i++)
		    {
			// remove all elements from neighbour vector (so neighbour is empty)
			neighbour.clear();
			// add the list of neighbours of cluster i in neighbour vector
			neighbour.addAll(((jet.cluster.data.Cluster)clusterTableGraine.get(i)).getNeighbourIndex());
			// retain only elements of neighbour vector that are in index (none 1st time, or neighbours common to clusters i and (i-1))
			neighbour.retainAll(index);
			// remove all elements of index vector that are in neighbour (none 1st time, or neighbours common to clusters i and (i-1))
			index.removeAll(neighbour);
			// add the list of neighbours of cluster i in index vector
			index.addAll(((jet.cluster.data.Cluster)clusterTableGraine.get(i)).getNeighbourIndex());
		    }
		neighbour.clear();
		neighbour.addAll(index);
		//System.out.println(neighbour.size());
		//System.out.println(index.size());
		// retain only residues whose score is above the "residue-mixed-trace" cutoff	
		index=filtrerScore (score,index,minTraceResidu);
		//System.out.println(minTraceResidu);
		//System.out.println(maxTraceResidu);
		// jeopardize
		//minTraceResidu=0.0;
		//index=filtrerScore (score,index,minTraceResidu,maxTraceResidu);
		//System.out.println(neighbour.size());
		//System.out.println(index.size());
		// get the order of the scores 
		index=trierScore(score,index);
		/** for each residue in index, clusterise it if possible
		 * - add it to an existing cluster if it is in the neighbor list and its score is above minScoreCluster
		 * - create a new cluster if it is not present in any neighbor list and its score is above minScoreCluster
		 * - do nothing otherwise 
		 * return true if clusters have evolved */
		arret=!clustering.cluster(index,score,pc, trace, minTraceCluster,surfSize,true);
		//maxTraceResidu=clustering.cluster(index,score,pc, trace, minTraceCluster,surfSize);
		//arret=(maxTraceResidu==0);
		clusterTableGraine=clustering.getClusterList();

	    }
    }


    // ensure that all residues in the newly added layer display scores inferior to the max score of the previous layer  
    public static void spreadSeedsLocalMax(jet.cluster.Clustering clustering, Vector score,Vector pc, Vector trace, double minTraceResidu, double minTraceCluster, int surfSize)
    {
    	Vector neighbour=new Vector();
    	Vector index=new Vector();
    	Vector clusterTableGraine=clustering.getClusterList();
	int nb=0;
	boolean arret=false;

	double maxTraceResidu=1;
      
	System.out.println("Spreading...");

	// stop when clusters do not evolve anymore
	while(!arret)
	    {
		nb++;			
		// remove all elements from index vector (so index is empty)
		index.clear();
		// get all neighbours of all clusters
		index.addAll(getAllNeighbours(clusterTableGraine));
		neighbour.clear();
		neighbour.addAll(index);

		// retain only residues whose score is above the "residue-mixed-trace" cutoff 
		index=filtrerScore (score,index,minTraceResidu);

		// get the order of the scores 
		index=trierScore(score,index);
		/** for each residue in index, clusterise it if possible
		 * - add it to an existing cluster if it is in the neighbor list and its score is above minScoreCluster
		 * - create a new cluster if it is not present in any neighbor list and its score is above minScoreCluster
		 * - do nothing otherwise 
		 * return true if clusters have evolved */
		
		/* cluster all neighboring residues and return the max trace of the layer
		   if the cluster has not evolved, the value 0 is returned
		   be aware that the current implementation does not suit cases when multiple clusters exist */
		maxTraceResidu=clustering.cluster(index,score,pc, trace, minTraceCluster,maxTraceResidu);
		arret=(maxTraceResidu==0);
		clusterTableGraine=clustering.getClusterList();

	    }
    }

   /** Extension des clusters de clustering au cours d'une troisième phase 
       une seule couche est ajoutee composee des residus voisins les moins conserves et les plus exposes*/
    
    public static void spreadRim(jet.cluster.Clustering clustering, Vector score,Vector cv, Vector trace, double minTraceResidu, int surfSize)
    {
	System.out.println("Spreading rim...");
    	Vector index=new Vector();
    	Vector clusterTable=clustering.getClusterList();
 		
	// get all neighbours of all clusters
	index.addAll(getAllNeighbours(clusterTable));
	// retain only residues whose score is above the "residue-mixed-trace" cutoff	
	index=filtrerScore (score,index,minTraceResidu);
	// get the order of the scores 
	index=trierScore(score,index);
	/** for each residue in index, clusterise it if possible
	 * - add it to an existing cluster if it is in the neighbor list and its score is above minScoreCluster
	 * - create a new cluster if it is not present in any neighbor list and its score is above minScoreCluster
	 * - do nothing otherwise 
	 * return true if clusters have evolved */
	clustering.clusterRim(index,score,cv, trace,surfSize);
	clusterTable=clustering.getClusterList();
	
    }
    
   /** Extension des clusters de clustering au cours d'une troisième phase 
       une seule couche est ajoutee composee des residus voisins les moins conserves et les plus exposes*/
    
    public static void spreadRim(jet.cluster.Clustering clustering, Vector score,Vector cv, Vector trace, double minTraceResidu, double seuil, int surfSize)
    {
	System.out.println("Spreading rim...");
    	Vector index=new Vector();
    	Vector clusterTable=clustering.getClusterList();
 		
	// get all neighbours of all clusters
	index.addAll(getAllNeighbours(clusterTable));
	// retain only residues whose score is above the "residue-mixed-trace" cutoff	
	index=filtrerScore (score,index,minTraceResidu);
	// get the order of the scores 
	index=trierScore(score,index);
	/** for each residue in index, clusterise it if possible
	 * - add it to an existing cluster if it is in the neighbor list and its score is above minScoreCluster
	 * - create a new cluster if it is not present in any neighbor list and its score is above minScoreCluster
	 * - do nothing otherwise 
	 * return true if clusters have evolved */
	clustering.clusterRim(index,score,cv, trace,seuil,surfSize);
	clusterTable=clustering.getClusterList();
	
    }

    /** Génération aléatoire de clusters sur la proteine ref en utilisant la distribution de scores trace
     *  mélangée aléatoirement ainsi que les scores seuil de clusterisation minScoreResidu et minScoreCluster.
     *   La génération est faite nbIteration fois. 
     *  La distribution des tailles de clusters et du nombre de cluster est retournée. */
    
    public static Vector randomTraceMap(jet.data.datatype.Sequence3D ref,Vector score,Vector pc, Vector trace, Vector surface, float radius, int nbIteration, double minScoreResidu,double minScoreCluster,boolean minScore)
    {
    	jet.cluster.Clustering clustering=new jet.cluster.Clustering(ref,radius);

    	int surfSize=0;
    	for(int i=0;i<surface.size();i++) if ((Double)surface.get(i)==1.0) surfSize=surfSize+1;

    	Vector index=new Vector(1,1),clusterTable;
    	
    	int i,j;
    	
    	int[] tailleClusterMax=new int[ref.size()+1];
    	int[] nbClusters=new int[ref.size()+1];
    	
    	for (i=0;i<ref.size()+1;i++)
    	{
    		tailleClusterMax[i]=0;
    		nbClusters[i]=0;
    	}
    	Vector scoreTriee=new Vector();
    	Vector traceTriee=new Vector();
    	Vector pcTriee=new Vector();
    	Vector traceRandom=new Vector();
    	Vector scoreRandom=new Vector();
    	Vector pcRandom=new Vector();
    	int rand;
    	
    	Double val1,val2;
    	Double valpc1;
    	Double valtrace1;
    	
    	for(i=0;i<score.size();i++)
	    {		
		val1=(Double)score.get(i);
		valpc1=(Double)pc.get(i);
		valtrace1=(Double)trace.get(i);		   
			for(j=0;j<scoreTriee.size();j++)
			    {
				val2=(Double)scoreTriee.get(j);
				if(val1.doubleValue()>=val2.doubleValue())
				    {
					scoreTriee.add(j,val1);
					traceTriee.add(j,valtrace1);
					pcTriee.add(j,valpc1);
					break;
				    }
				
			    }
			if(j==scoreTriee.size())
			{
				scoreTriee.add(val1);
				traceTriee.add(valtrace1);
				pcTriee.add(valpc1);
			}
	    }
    	
    	while(nbIteration>0)
    	{
    		scoreRandom.clear();
    		traceRandom.clear();
    		pcRandom.clear();
    		index.clear();
    		for (i=0;i<scoreTriee.size();i++)
    		{
    			scoreRandom.add(0.0);
    			traceRandom.add(0.0);
    			pcRandom.add(0.0);
    		}
    		
    		i=0;
    		while (((Double)scoreTriee.get(i))>minScoreResidu)
    		{
    			rand=(int)(Math.random()*(double)scoreTriee.size());
    			scoreRandom.set(rand, scoreTriee.get(i));
    			traceRandom.set(rand, traceTriee.get(i));
    			pcRandom.set(rand, pcTriee.get(i));
    			index.add(new Integer(rand));
    			i++;
    		}

    		clustering.clearClusterList();
    		clustering.cluster(index,scoreRandom,pcRandom,traceRandom,minScoreCluster,surfSize,minScore);
    		clusterTable=clustering.getClusterList();
    		
    		nbClusters[clusterTable.size()]++;
    		int tailleMax=-1;
    		for(i=0;i<clusterTable.size();i++)
    	    {
    			if (((jet.cluster.data.Cluster)clusterTable.get(i)).getCoreIndex().size()>tailleMax)
    				tailleMax=((jet.cluster.data.Cluster)clusterTable.get(i)).getCoreIndex().size();
    	    }
    		if (tailleMax!=-1) tailleClusterMax[tailleMax]++;
    		nbIteration--;
        }
    	Vector statistique=new Vector(2);
    	statistique.add(nbClusters);
    	statistique.add(tailleClusterMax);
    	return statistique;
    }
    
    /** Génération aléatoire de clusters sur la proteine ref. La génération est faite nbIteration fois.
     * Les clusters sont étendus tant que la somme de leur taille couvre moins de coverage de la surface. 
     *  La distribution des tailles de clusters et du nombre de cluster est retournée. */
    
    public static Vector randomMap(jet.data.datatype.Sequence3D ref, Vector surface, float radius,double coverage,int nbIteration)
    {

	jet.cluster.Clustering clustering=new jet.cluster.Clustering(ref,radius);

	Vector index=new Vector(1,1), indexTemp=new Vector(1,1),indexSurf=new Vector(1,1),clusterTable;
	int i;
	
	int[] tailleClusterMax=new int[ref.size()+1];
	int[] nbClusters=new int[ref.size()+1];
	
	for (i=0;i<ref.size()+1;i++)
	{
		tailleClusterMax[i]=0;
		nbClusters[i]=0;
	}
	
	for (i=0;i<surface.size();i++) if (((Double)surface.get(i)).doubleValue()==1.0) indexSurf.add(i);
	
	while(nbIteration>0)
	{
		indexTemp.clear();
		index.clear();
		for (i=0;i<indexSurf.size();i++)
		{
			indexTemp.add(indexSurf.get(i));
		}
		int rand;
		while(indexTemp.size()>indexSurf.size()*(1.0-coverage))
		{
			rand=(int)(Math.random()*(double)indexTemp.size());
			index.add(indexTemp.remove(rand));
		}
		clustering.cluster(index);
		clusterTable=clustering.getClusterList();
		
		nbClusters[clusterTable.size()]++;
		int tailleMax=-1;
		for(i=0;i<clusterTable.size();i++)
	    {
			if (((jet.cluster.data.Cluster)clusterTable.get(i)).getCoreIndex().size()>tailleMax)
				tailleMax=((jet.cluster.data.Cluster)clusterTable.get(i)).getCoreIndex().size();
	    }
		if (tailleMax!=-1) tailleClusterMax[tailleMax]++;
		nbIteration--;
    }
	Vector statistique=new Vector(2);
	statistique.add(nbClusters);
	statistique.add(tailleClusterMax);
	return statistique;
    }
  
    /** Calcul de la valeur ayant pour Pvaleur seuil (ou 1-Pvaleur seuil en fonction le booleen max) 
     * sur la distribution statistique. */
    
    public static int atConfidence(double seuil, int[] statistique, boolean max)
    {
    	if ((seuil>=0.0)&&(seuil<=1.0))
    	{
	    	int somme=0, sommeSeuil=0;
	    	int i,sens;
	    	String signe;
	    	for (i=0;i<statistique.length;i++) somme=somme+statistique[i];
	    	
	    	if (max) 
	    	{
	    		i=statistique.length-1;
	    		sens=-1;
	    		signe=">";
	    	}
	    	else
	    	{
	    		i=0;
	    		sens=1;
	    		signe="<";
	    	}	
	    	sommeSeuil=sommeSeuil+statistique[i];
	    	while ((double)sommeSeuil<((double)somme*(double)seuil))
	    	{	
	    		i=i+sens;
	    		sommeSeuil=sommeSeuil+statistique[i];
	    	}
	    	return i-sens;
    	}
    	else
    	{
    		System.err.println("Le seuil de confiance doit etre entre 0 et 1");
    		return -1;
    	
    	}
    }
    
    /** Calcul de la Pvaleur (ou de 1-Pvaleur en fonction le booleen max) 
     * de valeur sur la distribution statistique. */
    
    public static double whichConfidence(int valeur, int[] statistique, boolean max)
    {
	    int somme=0, sommeSeuil=0;
	    int i,sens;
	    String signe;
	    for (i=0;i<statistique.length;i++) somme=somme+statistique[i];
	    	
	    if (max) 
	    {
	    	i=statistique.length-1;
	    	sens=-1;
	    	signe=">";
	    }
	    else
	    {
	    	i=0;
	    	sens=1;
	    	signe="<";
	    }	
	    sommeSeuil=sommeSeuil+statistique[i];
	    while (i!=valeur)
	    {	
	    	i=i+sens;
	    	sommeSeuil=sommeSeuil+statistique[i];
	    }
	    return ((double)sommeSeuil/(double)somme);
    	
    }
    
    /** Calcul de la moyenne des scores de la distribution statistique */
    
    public static double mean(int[] statistique)
    {
    	double mean=0;
    	int nb=0;
	    for(int i=0;i<statistique.length;i++)
	    {
	    	nb=nb+statistique[i];
	    	mean=mean+(double)statistique[i]*(double)i;
	    }
    	return mean/(double)nb;
    	
    }
    
    /** Elimination des résidus (index) dont le score (traceMoy) est inférieur minTraceResidu  */
    
    public static Vector filtrerScore (Vector traceMoy, Vector index, double minTraceResidu)
    {
    	Vector indexFiltrer=new Vector(1,1);
    	int i;
    	for(i=0;i<index.size();i++)
    	{		

    		if(((Double)traceMoy.get(((Integer)index.get(i)))).doubleValue()>minTraceResidu)
    		{
    			indexFiltrer.add(index.get(i));
    		}
    	}
    	return indexFiltrer;
    }
    
    /** Elimination des résidus (index) dont le score (traceMoy) est inférieur minTraceResidu ou superieur a maxTraceResidu */
 
    public static Vector filtrerScore (Vector traceMoy, Vector index, double minTraceResidu, double maxTraceResidu)
    {
    	Vector indexFiltrer=new Vector(1,1);
	Double val;
    	int i;
    	for(i=0;i<index.size();i++)
    	{	

	    val=((Double)traceMoy.get(((Integer)index.get(i)))).doubleValue();

	    if( (val>minTraceResidu) && (val<=maxTraceResidu) )
    		{
		    indexFiltrer.add(index.get(i));
    		}
    	}
    	return indexFiltrer;
    }
    


    /** Tri des résidus (index) en fonction de leur score (traceMoy) */
    
    public static Vector trierScore (Vector traceMoy, Vector index)
    {
    	Vector indexTri=new Vector();
    	int i,j;
    	Double val1,val2;
    	for(i=0;i<index.size();i++)
    	    {
    		val1=(Double)traceMoy.get(((Integer)index.get(i)).intValue());

    			for(j=0;j<indexTri.size();j++)
    			    {
    				val2=(Double)traceMoy.get(((Integer)indexTri.get(j)).intValue());
    				if(val1.doubleValue()>val2.doubleValue())
    				    {
    					indexTri.add(j,((Integer)index.get(i)).intValue());
    					break;
    				    }
    				
    			    }
    			if(j==indexTri.size()) indexTri.add(((Integer)index.get(i)).intValue());
    		    
    	    }
    	return indexTri;
    }

    /** Construction d'un vecteur de scores mixtes seed/other */
    
    public static Vector combineScore (Vector scoreMoy1, Vector scoreMoy2, Vector indexSeed)
    {
    	Vector combiScore=new Vector();
    	int i,j;
	j=0;
   
    	for(i=0;i<scoreMoy1.size();i++)
    	    {
		if ( indexSeed.contains(i) )
		    {
			combiScore.add((Double)scoreMoy2.get(i));
			j++;
		    }
		else
		    {
			combiScore.add((Double)scoreMoy1.get(i));
		    }
	    }
    	return combiScore;
    }

    
    /** initialisation à 0.0 d'un vecteur de taille size */
    
    public static Vector fillVector(int size)
    {
	int i;
	Vector v=new Vector(size);
	for(i=0;i<size;i++) v.add(new Double(0.0)); 
	return v;
    }

    /* non-redundant concatenation of the neighbours lists of all clusters*/
    public static Vector getAllNeighbours(Vector clusterTable)
    {
	Vector neighbour=new Vector();
    	Vector index=new Vector();

	for (int i=0;i<clusterTable.size();i++)
	    {
		// remove all elements from neighbour vector (so neighbour is empty)
		neighbour.clear();
		// add the list of neighbours of cluster i in neighbour vector
		neighbour.addAll(((jet.cluster.data.Cluster)clusterTable.get(i)).getNeighbourIndex());
		// retain only elements of neighbour vector that are in index (none 1st time, or neighbours common to clusters i and (i-1))
		neighbour.retainAll(index);
		// remove all elements of index vector that are in neighbour (none 1st time, or neighbours common to clusters i and (i-1))
		index.removeAll(neighbour);
		// add the list of neighbours of cluster i in index vector
		index.addAll(((jet.cluster.data.Cluster)clusterTable.get(i)).getNeighbourIndex());
	    }
	return index;
    }


    /** clusterdata is returned by the function 
     * it contains two elements, the first one is a vector with extension scores (mixed trace mtrace(j)
     * the second one is the number of cluster to which the residue belong*/
    public static Vector getClusteredScores(jet.data.datatype.Sequence3D ref, Vector clusterTable, Vector score)
    {
	int position;
	Double val1;
	jet.cluster.data.Cluster clust;
	Vector clusterData = new Vector();
	clusterData.add(fillVector(ref.size())); // cluster value
	clusterData.add(fillVector(ref.size())); // cluster number
	
	for(int i=0;i<clusterTable.size();i++)
	    {
		position=0;
		clust=(jet.cluster.data.Cluster)clusterTable.get(i);
		for(int j=0;j<clust.getCoreIndex().size();j++)
		    {
			position=((Integer)clust.getCoreIndex().get(j)).intValue();
			val1=(Double)score.get(position);
			((Vector)clusterData.get(0)).setElementAt(val1,position);
			((Vector)clusterData.get(1)).setElementAt(((Integer)(i+cluster_count)).doubleValue(),position);
		    }
		System.out.print(" "+clust.getCoreIndex().size());
		for(int k=0;k<clust.getRimIndex().size();k++)
		    {
			System.out.println("Rim exists");
			position=((Integer)clust.getRimIndex().get(k)).intValue();
			val1=(Double)score.get(position);
			((Vector)clusterData.get(0)).setElementAt(val1,position);
			((Vector)clusterData.get(1)).setElementAt(((Integer)(i+cluster_count)).doubleValue(),position);
		    }
		if(clust.getRimIndex().size()>0)System.out.print(" + "+clust.getRimIndex().size());
	    }
	return(clusterData);
    }

}

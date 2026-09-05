package jet.cluster;

import java.util.*;

/** Classe qui permet de realiser le clustering 3D des residus.*/

public class Clustering
{
    /** stocke les distances entre les résidus */
    private jet.cluster.data.DistanceMap dm;
    /** Vecteur de clusters */
    private Vector clusterList;
    /** distance maximale permettant de clusteriser deux résidus */
    private float radius;
    
    /** CONSTRUCTEURS */
    
    public Clustering(jet.data.datatype.Sequence3D sequence, float radius)
    {
	this.radius=radius;
	/* initialisation des distances entre les résidus */
	dm=new jet.cluster.data.DistanceMap(sequence,radius);
	clusterList=new Vector(1,1);
    }
    
    /** ACCESSEURS et MODIFICATEUR*/
    
    public Vector getClusterList(){return clusterList;}
    
    public void setClusterList(Vector clustList){clusterList=clustList;}
    
    public void clearClusterList(){clusterList.clear();}
    public int getClustersSize()
    {
    	int taille=0;
    	for (int i=0; i<clusterList.size(); i++)
    	{
    		for (int j=0; j<((jet.cluster.data.Cluster)clusterList.get(i)).getCoreIndex().size(); j++)
        	{
        		taille++;
        	}
    	}
    	return taille;
    }

    // return the core residues indices
    public Vector getCoreIndex(){

	Vector core=new Vector(1,1);
	int i,j,position,nb;

	for(i=0;i<clusterList.size();i++) 
	    {
		core.add(((jet.cluster.data.Cluster)clusterList.get(i)).getCoreIndex());
	    }

	return core;
    }

    public Vector getDistancestoSeed(Integer index, Vector indexSeed)
    {
	int i;
	jet.cluster.data.ProxList pl=dm.getProxList(index);;
	jet.data.datatype.Residue3D resRef,resComp;
	resRef=pl.getResidue();

	Vector dist = new Vector();
	Iterator iter=indexSeed.iterator();
	
	while(iter.hasNext())
	    {
		i=((Integer)iter.next()).intValue();
		resComp=(dm.getProxList(i)).getResidue();
		dist.add(resRef.minAtomDistance(resComp));
	    }

	return(dist);
    }


   public Vector getNeighbours(Integer index)
    {
	jet.cluster.data.ProxList pl=dm.getProxList(index);
	Vector IndexVois = new Vector();
	for(int j=0;j<pl.getLength();j++)
	    {
		if (pl.getProxNode(j).getDistance()< radius)
		    {
			IndexVois.add(pl.getProxNode(j).getRef().getId());
		    }
	    }
	return(IndexVois);
    }
		
    // get residues from index that are not already clustered			    
   public Vector getNotClustered(Vector index)
    {
	Vector core=new Vector();
	int i;
	
	for(i=0;i<clusterList.size();i++) 
	    {
		core=((jet.cluster.data.Cluster)clusterList.get(i)).getCoreIndex();
		index.removeAll(core);
	    }
	
	return(index);
    }

    
    // compute mean score over the entire ensemble of clusters
    public double getClusterMoy(Vector score)
    {
	Vector core=new Vector(1,1);
	double scoreMoy=0.0;
	jet.cluster.data.Cluster cluster;
	int i,j,position;
	int nb=0;

	for(i=0;i<clusterList.size();i++) 
	    {
		cluster=(jet.cluster.data.Cluster)clusterList.get(i);
		core=cluster.getCoreIndex();
		for (j=0;j<core.size();j++)
		    {
			position=((Integer)core.get(j)).intValue();
			scoreMoy=scoreMoy+(Double)score.get(position);
			nb++;
		    }
	    }
	
	scoreMoy=scoreMoy/(nb);
	
	return(scoreMoy);
    }

    // compute mean scores over the difference clusters
    public Vector getClusterMoys(Vector score)
    {
	Vector core=new Vector(1,1);
	double scoreMoy=0.0;
	Vector scoreMoys=new Vector();
	jet.cluster.data.Cluster cluster;
	int i,j,position;
	int nb=0;

	for(i=0;i<clusterList.size();i++) 
	    {
		scoreMoy=0.0;
		nb=0;
		cluster=(jet.cluster.data.Cluster)clusterList.get(i);
		core=cluster.getCoreIndex();
		for (j=0;j<core.size();j++)
		    {
			position=((Integer)core.get(j)).intValue();
			scoreMoy=scoreMoy+(Double)score.get(position);
			nb++;
		    }
		scoreMoy=scoreMoy/(nb);
		scoreMoys.add(scoreMoy);
	    }

	return(scoreMoys);
    }


    // compute standard deviations of the scores over the different clusters
    public Vector getClusterSDs(Vector score)
    {
	Vector core=new Vector(1,1);
	double scoreSD=0.0;
	Vector scoreSDs=new Vector();
	Vector scoreMoys=getClusterMoys(score);
	jet.cluster.data.Cluster cluster;
	int i,j,position;
	int nb=0;

	for(i=0;i<clusterList.size();i++) 
	    {
		scoreSD=0.0;
		nb=0;
		cluster=(jet.cluster.data.Cluster)clusterList.get(i);
		core=cluster.getCoreIndex();
		for (j=0;j<core.size();j++)
		    {
			position=((Integer)core.get(j)).intValue();
			scoreSD=scoreSD+ ( (Double)score.get(position) - (Double)scoreMoys.get(i) ) * ( (Double)score.get(position) - (Double)scoreMoys.get(i) );
			nb++;
		    }
		scoreSD=Math.sqrt(scoreSD/(nb-1));
		scoreSDs.add(scoreSD);
	    }

	return(scoreSDs);
    }


   public float getMeanDistancetoSeed(Integer index, Vector indexSeed)
    {
	int i;
	jet.cluster.data.ProxList pl=dm.getProxList(index);;
	jet.data.datatype.Residue3D resRef,resComp;
	resRef=pl.getResidue();
	float dist=0;
	int nb=0;
	Iterator iter=indexSeed.iterator();
	
	while(iter.hasNext())
	    {
		i=((Integer)iter.next()).intValue();
		resComp=(dm.getProxList(i)).getResidue();
		dist=dist+resRef.minAtomDistance(resComp);
		nb++;
	    }

	return(dist/nb);
    }

    public int getDensity(Integer index)
    {
	jet.cluster.data.ProxList pl=dm.getProxList(index);;
	return(pl.getLength());
    }
	
    /** METHODES */
    
    /** Clusterisation des residus dont les indices sont dans index.
     *  Les indices des residus (index) doivent etre triés en fonction du score des residus (score).
     * Si traceMinCluster=true chaque cluster est étendu si son score moyen est > à seuil. Dans ce cas  
     * Sinon les residus sont clusterisés jusqu'à un taux de couverture de la surface egal à seuil. 
     * Retourne true si les clusters ont evolué false sinon. */
    
       public boolean cluster(Vector index, Vector score, Vector pc, Vector trace, double seuil, int surfSize, boolean traceMinCluster)
    {
    	boolean evolution=false;
    	boolean evolutionTemp=false;
	Iterator iter=index.iterator();
	int i;
	if (traceMinCluster)
	    {
		/* clusterisation avec un score minimal pour chaque cluster */
		while(iter.hasNext())
		    {
			i=((Integer)iter.next()).intValue();
			/* clusterisation du residu i */
			evolution=cluster(dm.getProxList(i),score,pc,trace,seuil)||evolution;
		    }
	    }
	else
	    {
		/* clusterisation jusqu'à un taux de couverture de la surface egal à seuil */
		int nb=0;
		while((iter.hasNext())&&(((double)nb/(double)surfSize)<seuil))
		    {
			i=((Integer)iter.next()).intValue();
			evolutionTemp=cluster(dm.getProxList(i),score,pc,trace);
			evolution=evolutionTemp||evolution;
			nb++;				
		    }
		
	    }
	return evolution;
    }

    /** generation d'une couche de rim */
    public boolean clusterRim(Vector index, Vector score, Vector cv, Vector trace, int surfSize)
    {
    	boolean evolution=false;
    	boolean evolutionTemp=false;
	Iterator iter=index.iterator();
	Vector clusterMoys=new Vector(1,1);
	int i;

	// the minimal threshold for cluster score is computed based on the mean cluster score before extension
	clusterMoys=getClusterMoys(score);
	/* clusterisation avec un score minimal pour chaque cluster */
	while(iter.hasNext())
	    {
		i=((Integer)iter.next()).intValue();
		/* clusterisation du residu i */
		evolution=clusterRim(dm.getProxList(i),score,cv,trace,clusterMoys)||evolution;
	    }
	
	return evolution;
    }

    /** generation d'une couche de rim avec seuil de selection minScoreCluster et limite selon la taille de la proteine*/
    public boolean clusterRim(Vector index, Vector score, Vector cv, Vector trace, double seuil, int surfSize)
    {

    	boolean evolution=false;
    	boolean evolutionTemp=false;
	Iterator iter=index.iterator();
	Vector clusterMoys=new Vector(1,1);
	int i;

	// the minimal threshold for cluster score is computed based on the mean cluster score before extension
	clusterMoys=getClusterMoys(score);
	/* clusterisation avec un score minimal pour chaque cluster */
	int nb=0;
	while((iter.hasNext())&&(((double)nb/(double)surfSize)<seuil))
	    {
		i=((Integer)iter.next()).intValue();
		/* clusterisation du residu i */
		evolution=clusterRim(dm.getProxList(i),score,cv,trace,clusterMoys)||evolution;
		nb++;
	    }
	return evolution;
    }


    public boolean clusterLayer(Vector score, Vector pc, Vector trace, double seuil, int surfSize, int indexClust)
    {

    	boolean evolution=false;
    	boolean evolutionTemp=false;
	jet.cluster.data.ProxList pl;
	double scoreMoy=0.0;
	double traceMoy=0.0;
	double pcMoy=0.0;
	Vector core=new Vector(1,1);
	Vector neighbor=new Vector(1,1);
	int i,j,position;

	scoreMoy=0.0;
	traceMoy=0.0;
	pcMoy=0.0;
	position=0;
	
	core=((jet.cluster.data.Cluster)clusterList.get(indexClust)).getCoreIndex();
	neighbor=((jet.cluster.data.Cluster)clusterList.get(indexClust)).getNeighbourIndex();
	
	// sum score(j), trace(j) and propensity(j) for all j in the cluster core
	for (j=0;j<core.size();j++)
	    {
		position=((Integer)core.get(j)).intValue();
		scoreMoy=scoreMoy+(Double)score.get(position);
		traceMoy=traceMoy+(Double)trace.get(position);
		pcMoy=pcMoy+(Double)pc.get(position);
	    }
	
	for(i=0;i<neighbor.size();i++)
	    {
		pl=dm.getProxList(i);
		// add score(i), trace(i) and propensity(i) of current residue
		scoreMoy=scoreMoy+(Double)score.get(new Integer(pl.getId()));
		traceMoy=traceMoy+(Double)trace.get(new Integer(pl.getId()));
		pcMoy=pcMoy+(Double)pc.get(new Integer(pl.getId()));
	    }
	// compute mean velues for score, trace and propensity
	pcMoy=pcMoy/(core.size()+neighbor.size());
	traceMoy=traceMoy/(core.size()+neighbor.size());
	scoreMoy=scoreMoy/(core.size()+neighbor.size());

	/* clusterisation du residu si le score moyen du cluster est > à minTraceCluster */
	if ((scoreMoy>=seuil))
	    {
		for(i=0;i<neighbor.size();i++)
		    {
			pl=dm.getProxList(i);
			((jet.cluster.data.Cluster)clusterList.get(indexClust)).addResidue(pl);
			((jet.cluster.data.Cluster)clusterList.get(indexClust)).setScoreMoy(scoreMoy);
			((jet.cluster.data.Cluster)clusterList.get(indexClust)).setTraceMoy(traceMoy);
			((jet.cluster.data.Cluster)clusterList.get(indexClust)).setpcMoy(pcMoy);
			//	clusterList.add((jet.cluster.data.Cluster)selectedCluster.get(0));
			evolution=true;
		    }
	    }

	return evolution;
    }


    public boolean cluster(int index, Vector score, Vector pc, Vector trace, double seuil)
    {
	boolean evolution=false;
	boolean isClustered=false;
	Vector core=new Vector();
	// check whether it is already clustered
	
	int i;
	
	for(i=0;i<clusterList.size();i++) 
	    {
		core=((jet.cluster.data.Cluster)clusterList.get(i)).getCoreIndex();
		if(core.contains(index))
		    {
			isClustered=false;
		    }
	    }
	
	if( !isClustered ) 
	    {
		evolution=cluster(dm.getProxList(index),score,pc,trace,seuil);
	    }
	
	return evolution;
    }

 
    /** Clusterisation des residus dont les indices sont dans index.
     *  Les indices des residus (index) doivent etre triés en fonction du score des residus (score).
     *  Chaque residu est considere pour la clusterisation uniquement si son score est inferieur a seuilRes
     *  Chaque cluster est étendu si son score moyen est > à seuil. 
     *  Le score max de la couche est retourne */

    public Double cluster(Vector index, Vector score, Vector pc, Vector trace, double seuil, double seuilRes)
    {
    	boolean evolution=false;
	Iterator iter=index.iterator();
	Double scoreRes;
	jet.cluster.data.ProxList pl;
	int i;

	Double maxTraceResidu = 0.0;

	/* clusterisation avec un score minimal pour chaque cluster */
	while(iter.hasNext())
	    {
		i=((Integer)iter.next()).intValue();
		/* clusterisation du residu i */
		pl=dm.getProxList(i);
		scoreRes=(Double)score.get(new Integer(pl.getId()));
		if(scoreRes<=seuilRes)
		    {
			evolution=cluster(pl,score,pc,trace,seuil);
			if(evolution &&  (maxTraceResidu==0))
			    {
				maxTraceResidu=scoreRes;
			    }
		    }
	    }
    
	return maxTraceResidu;
    }

    public boolean clusterGlob(Vector index, Vector score, Vector pc, Vector trace, double seuilMin, double seuilMax)
    {
    	boolean evolution=false;
	Iterator iter=index.iterator();
	jet.cluster.data.ProxList pl;
	int i;

	/* clusterisation avec un score minimal pour chaque cluster */
	while(iter.hasNext())
	    {
		i=((Integer)iter.next()).intValue();
		/* clusterisation du residu i */
		pl=dm.getProxList(i);
		evolution=cluster(dm.getProxList(i),score,pc,trace,seuilMin,seuilMax)||evolution;

	    }
    
	return evolution;
    }


    /** Clusterisation basique des residus dont les indices sont dans index. 
     * Cette clusterisation ne tient compte seulement de la distance entre les residus.*/
    
    public void cluster(Vector index)
    {
    	int i=0,nb;
    	clearClusterList();
    	while(i<index.size())
    	{
    		nb=((Integer)index.get(i)).intValue();
    		cluster(dm.getProxList(nb));
    		i++;
    	}
    }
    
    /** Clusterisation du residu contenu dans pl (contient residu et distances aux autres residus) 
     * en fonction de son score et de sa distance aux residus deja clusterisés */
    
    private boolean cluster(jet.cluster.data.ProxList pl, Vector score, Vector pc, Vector trace, double minTraceCluster)
    {
	boolean evolution=false;
	Vector selectedCluster=new Vector(1,1);
	double scoreMoy=0.0;
	double traceMoy=0.0;
	double pcMoy=0.0;
	Vector core=new Vector(1,1);
	jet.cluster.data.Cluster cluster;
	int i,j,position,nb;

	/* Sélection du ou des clusters dont la distance avec le residu est inferieur à radius */
	for(i=0;i<clusterList.size();i++) 
	    {
		cluster=(jet.cluster.data.Cluster)clusterList.get(i);
		if(cluster.isNeighbour(pl.getResidue()))
		    {
			selectedCluster.add(clusterList.remove(i--)); 
		    }
	    }
	
	if(selectedCluster.size()>0)
	{
		scoreMoy=0.0;
		traceMoy=0.0;
		pcMoy=0.0;
		position=0;
		nb=0;
		//	Vector scores=new Vector();
		
		/** sum score(j), trace(j) and propensity(j) for all j in the clusters cores
		* only one cluster is assumed ??? in fine yes, since selected clusters should be merged*/
		for (i=0; i<selectedCluster.size();i++)
		    {
			//	scores.clear();
			core=((jet.cluster.data.Cluster)selectedCluster.get(i)).getCoreIndex();
			// sum score(j), trace(j) and propensity(j) for all j in the cluster core
			for (j=0;j<core.size();j++)
			    {
				position=((Integer)core.get(j)).intValue();
				scoreMoy=scoreMoy+(Double)score.get(position);
				//	scores.add((Double)score.get(position));
				traceMoy=traceMoy+(Double)trace.get(position);
				pcMoy=pcMoy+(Double)pc.get(position);
				nb++;
			}
		    }
		// add score(i), trace(i) and propensity(i) of current residue
		scoreMoy=scoreMoy+(Double)score.get(new Integer(pl.getId()));
		traceMoy=traceMoy+(Double)trace.get(new Integer(pl.getId()));
		pcMoy=pcMoy+(Double)pc.get(new Integer(pl.getId()));
		// compute mean velues for score, trace and propensity
		pcMoy=pcMoy/(nb+1);
		// bug detected: replace nb by nb+1
		traceMoy=traceMoy/(nb+1);
		scoreMoy=scoreMoy/(nb+1);
		
		/* clusterisation du residu si le score moyen du cluster est > à minTraceCluster */
		if ((scoreMoy>=minTraceCluster))
		{
			/* Si plusieurs clusters sélectionnés il faut les merger */
		    while(selectedCluster.size()>1)
			{
			    cluster=(jet.cluster.data.Cluster)selectedCluster.remove(0);
			    cluster.merge((jet.cluster.data.Cluster)selectedCluster.remove(0));
			    selectedCluster.add(cluster);
			    
			}
		    /* Et ajouter le residu à ces clusters mergés */
		    ((jet.cluster.data.Cluster)selectedCluster.get(0)).addResidue(pl);
		    ((jet.cluster.data.Cluster)selectedCluster.get(0)).setScoreMoy(scoreMoy);
		    ((jet.cluster.data.Cluster)selectedCluster.get(0)).setTraceMoy(traceMoy);
		    ((jet.cluster.data.Cluster)selectedCluster.get(0)).setpcMoy(pcMoy);
		    clusterList.add((jet.cluster.data.Cluster)selectedCluster.get(0));
		    evolution=true;
		}
		else
		    {
			clusterList.addAll(selectedCluster);
		    }
	}
	else
	    {
		/* si pas de cluster selectionnés création d'un cluster contenant seulement le residu */
		// provided that the score is larger than the cutoff value for clusters
		if (((Double)score.get(new Integer(pl.getId()))>=minTraceCluster))
		    {
			cluster=new jet.cluster.data.Cluster(radius);
			cluster.addResidue(pl);
			((jet.cluster.data.Cluster)cluster).setScoreMoy((Double)score.get(new Integer(pl.getId())));
			((jet.cluster.data.Cluster)cluster).setTraceMoy((Double)trace.get(new Integer(pl.getId())));
			((jet.cluster.data.Cluster)cluster).setpcMoy((Double)pc.get(new Integer(pl.getId())));
			clusterList.add(cluster);
			evolution=true;
		}
	    }
	return evolution;
    }

    /** Generation d'un rim pour le cluster avec le vecteur de seuils minScoreCluster comme condition de selection
     * les clusters ne sont plus fusionnes a ce stade */
    private boolean clusterRim(jet.cluster.data.ProxList pl, Vector score, Vector cv, Vector trace, Vector minScoreCluster)
    {
	boolean evolution=false;
	Vector selectedCluster=new Vector(1,1);
	Vector selectedClusterScore=new Vector(1,1);
	double scoreMoy=0.0;
	double traceMoy=0.0;
	double cvMoy=0.0;
	Vector core=new Vector(1,1);
	Vector rim=new Vector(1,1);
	jet.cluster.data.Cluster cluster;
	int i,j,position;

	/* Sélection du ou des clusters dont la distance avec le residu est inferieur à radius */
	j=0;
	for(i=0;i<clusterList.size();i++) 
	    {
		cluster=(jet.cluster.data.Cluster)clusterList.get(i);
		if(cluster.isNeighbour(pl.getResidue()))
		    {
			selectedCluster.add(clusterList.remove(i--));
			//HR 2013.11.14 // warning !! can create an error !! 
                        //HR 2013.11.14 double -> Double
			selectedClusterScore.add((Double)minScoreCluster.get(j));
		    }
		j++;
	    }
	if(selectedCluster.size()>0)
	    {
		scoreMoy=0.0;
		traceMoy=0.0;
		cvMoy=0.0;
		position=0;
		/** sum score(j), trace(j) and propensity(j) for all j in the clusters cores
		 * only one cluster is assumed ??? in fine yes, since selected clusters should be merged*/
		for (i=0; i<selectedCluster.size();i++)
		    {
			core=((jet.cluster.data.Cluster)selectedCluster.get(i)).getCoreIndex();
			// sum score(j), trace(j) and propensity(j) for all j in the cluster core
			for (j=0;j<core.size();j++)
			    {
				position=((Integer)core.get(j)).intValue();
				scoreMoy=scoreMoy+(Double)score.get(position);
				traceMoy=traceMoy+(Double)trace.get(position);
				cvMoy=cvMoy+(Double)cv.get(position);
			    }
			// add score(i), trace(i) and propensity(i) of current residue
			scoreMoy=scoreMoy+(Double)score.get(new Integer(pl.getId()));
			traceMoy=traceMoy+(Double)trace.get(new Integer(pl.getId()));
			cvMoy=cvMoy+(Double)cv.get(new Integer(pl.getId()));
			// compute mean velues for score, trace and propensity
			cvMoy=cvMoy/(core.size()+1);
			traceMoy=traceMoy/(core.size()+1);
			scoreMoy=scoreMoy/(core.size()+1);
     
			/* clusterisation du residu si le score moyen du cluster est > à minTraceCluster */
                        //HR 2013.11.14 double -> Double
			if ((scoreMoy>=(Double)selectedClusterScore.get(i)))
			    {
				((jet.cluster.data.Cluster)selectedCluster.get(i)).addResidue(pl);
				evolution=true;
			    }
			clusterList.add((jet.cluster.data.Cluster)selectedCluster.get(i));
		    }	
	    }
	return evolution;
    }

   /** Generation d'un rim pour le cluster
	Clusterisation du residu contenu dans pl (contient residu et distances aux autres residus) 
     * en fonction de son score et de sa distance aux residus deja clusterisés */
    
    private boolean clusterRim(jet.cluster.data.ProxList pl, Vector score, Vector cv, Vector trace, double minScoreCluster)
    {
	boolean evolution=false;
	Vector selectedCluster=new Vector(1,1);
	double scoreMoy=0.0;
	double traceMoy=0.0;
	double cvMoy=0.0;
	Vector core=new Vector(1,1);
	Vector rim=new Vector(1,1);
	jet.cluster.data.Cluster cluster;
	int i,j,position;

	/* Sélection du ou des clusters dont la distance avec le residu est inferieur à radius */
	j=0;
	for(i=0;i<clusterList.size();i++) 
	    {
		cluster=(jet.cluster.data.Cluster)clusterList.get(i);
		if(cluster.isNeighbour(pl.getResidue()))
		    {
			selectedCluster.add(clusterList.remove(i--)); 
		    }
		j++;
	    }
	
	if(selectedCluster.size()>0)
	    {
		scoreMoy=0.0;
		traceMoy=0.0;
		cvMoy=0.0;
		position=0;
		
		/** sum score(j), trace(j) and propensity(j) for all j in the clusters cores
		 * only one cluster is assumed ??? in fine yes, since selected clusters should be merged*/
		for (i=0; i<selectedCluster.size();i++)
		    {
			core=((jet.cluster.data.Cluster)selectedCluster.get(i)).getCoreIndex();
			// sum score(j), trace(j) and propensity(j) for all j in the cluster core
			for (j=0;j<core.size();j++)
			    {
				position=((Integer)core.get(j)).intValue();
				scoreMoy=scoreMoy+(Double)score.get(position);
				traceMoy=traceMoy+(Double)trace.get(position);
				cvMoy=cvMoy+(Double)cv.get(position);
			    }
			// add score(i), trace(i) and propensity(i) of current residue
			scoreMoy=scoreMoy+(Double)score.get(new Integer(pl.getId()));
			traceMoy=traceMoy+(Double)trace.get(new Integer(pl.getId()));
			cvMoy=cvMoy+(Double)cv.get(new Integer(pl.getId()));
			// compute mean velues for score, trace and propensity
			cvMoy=cvMoy/(core.size()+1);
			traceMoy=traceMoy/(core.size()+1);
			scoreMoy=scoreMoy/(core.size()+1);
     
			/* clusterisation du residu si le score moyen du cluster est > à minTraceCluster */
			if ((scoreMoy>=minScoreCluster))
			    {
				((jet.cluster.data.Cluster)selectedCluster.get(i)).addResidue(pl);
				clusterList.add((jet.cluster.data.Cluster)selectedCluster.get(i));
				evolution=true;
			    }
			else
			    {
				clusterList.addAll(selectedCluster);
			    }
		    }	
	    }
	return evolution;
    }

    /** Clusterisation du residu contenu dans pl (contient residu et distances aux autres residus) 
     * en fonction de son score et de sa distance aux residus deja clusterisés 
     * methode steepest descent */
    
    private boolean cluster(jet.cluster.data.ProxList pl, Vector score, Vector pc, Vector trace, double minTraceCluster, double maxTraceCluster)
    {
	boolean evolution=false;
	Vector selectedCluster=new Vector(1,1);
	double scoreMoy=0.0;
	double traceMoy=0.0;
	double pcMoy=0.0;
	Vector core=new Vector(1,1);
	jet.cluster.data.Cluster cluster;
	int i,j,position,nb;

	/* Sélection du ou des clusters dont la distance avec le residu est inferieur à radius */
	for(i=0;i<clusterList.size();i++) 
	    {
		cluster=(jet.cluster.data.Cluster)clusterList.get(i);
		if(cluster.isNeighbour(pl.getResidue()))
		    {
			selectedCluster.add(clusterList.remove(i--)); 
		    }
	    }
	
	if(selectedCluster.size()>0)
	    {
		scoreMoy=0.0;
		traceMoy=0.0;
		pcMoy=0.0;
		position=0;
		nb=0;
		
		/** sum score(j), trace(j) and propensity(j) for all j in the clusters cores
		 * only one cluster is assumed ??? in fine yes, since selected clusters should be merged*/
		for (i=0; i<selectedCluster.size();i++)
		    {
			core=((jet.cluster.data.Cluster)selectedCluster.get(i)).getCoreIndex();
			for (j=0;j<core.size();j++)
			    {
				position=((Integer)core.get(j)).intValue();
				scoreMoy=scoreMoy+(Double)score.get(position);
				traceMoy=traceMoy+(Double)trace.get(position);
				pcMoy=pcMoy+(Double)pc.get(position);
				nb++;
			    }
		    }
		
		scoreMoy=scoreMoy+(Double)score.get(new Integer(pl.getId()));
		traceMoy=traceMoy+(Double)trace.get(new Integer(pl.getId()));
		pcMoy=pcMoy+(Double)pc.get(new Integer(pl.getId()));
		// compute mean velues for score, trace and propensity
		pcMoy=pcMoy/(nb+1);
		traceMoy=traceMoy/(nb+1);
		scoreMoy=scoreMoy/(nb+1);
		
		/* clusterisation du residu si le score moyen du cluster est > à minTraceCluster */
		if ( (scoreMoy>=minTraceCluster) && (scoreMoy<=maxTraceCluster))
		    {
			/* Si plusieurs clusters sélectionnés il faut les merger */
			while(selectedCluster.size()>1)
			    {
				cluster=(jet.cluster.data.Cluster)selectedCluster.remove(0);
				cluster.merge((jet.cluster.data.Cluster)selectedCluster.remove(0));
				selectedCluster.add(cluster);
				
			    }
			/* Et ajouter le residu à ces clusters mergés */
			((jet.cluster.data.Cluster)selectedCluster.get(0)).addResidue(pl);
			((jet.cluster.data.Cluster)selectedCluster.get(0)).setScoreMoy(scoreMoy);
			((jet.cluster.data.Cluster)selectedCluster.get(0)).setTraceMoy(traceMoy);
			((jet.cluster.data.Cluster)selectedCluster.get(0)).setpcMoy(pcMoy);
			clusterList.add((jet.cluster.data.Cluster)selectedCluster.get(0));
			evolution=true;
		    }
		else
		    {
			clusterList.addAll(selectedCluster);
		    }
	    }
	
	
	return evolution;
    }



   /** Clusterisation du residu contenu dans pl en fonction seulement de sa distance aux residus deja clusterisés */
    
    private boolean cluster(jet.cluster.data.ProxList pl, Vector score, Vector pc, Vector trace)
    {
	boolean evolution=false;
	Vector selectedCluster=new Vector(1,1);
	double scoreMoy=0.0;
	double traceMoy=0.0;
	double pcMoy=0.0;
	Vector core=new Vector(1,1);
	
	jet.cluster.data.Cluster cluster;
	int i,j,position,nb;

	for(i=0;i<clusterList.size();i++) 
	    {
		cluster=(jet.cluster.data.Cluster)clusterList.get(i);
		if(cluster.isNeighbour(pl.getResidue()))
		    {
			selectedCluster.add(clusterList.remove(i--)); 
		    }
	    }
	
	if(selectedCluster.size()>0)
	{
		scoreMoy=0.0;
		traceMoy=0.0;
		pcMoy=0.0;
		position=0;
		nb=0;
		// sum the score(j), trace(j) and propensity(j) for all j in the clusters cores
		// its is assumed that only one cluster will match ???
		for (i=0; i<selectedCluster.size();i++)
	    {
			core=((jet.cluster.data.Cluster)selectedCluster.get(i)).getCoreIndex();
			// sum the score(j), trace(j) and propensity(j) for all j in the cluster core
			for (j=0;j<core.size();j++)
			{
				position=((Integer)core.get(j)).intValue();
				scoreMoy=scoreMoy+(Double)score.get(position);
				traceMoy=traceMoy+(Double)trace.get(position);
				pcMoy=pcMoy+(Double)pc.get(position);
				nb++;
			}
	    }
		// add the 
		scoreMoy=scoreMoy+(Double)score.get(new Integer(pl.getId()));
		traceMoy=traceMoy+(Double)trace.get(new Integer(pl.getId()));
		pcMoy=pcMoy+(Double)pc.get(new Integer(pl.getId()));
		pcMoy=pcMoy/(nb);
		traceMoy=traceMoy/(nb);
		scoreMoy=scoreMoy/(nb);
		
		while(selectedCluster.size()>1)
		{
			cluster=(jet.cluster.data.Cluster)selectedCluster.remove(0);
			cluster.merge((jet.cluster.data.Cluster)selectedCluster.remove(0));
			selectedCluster.add(cluster);
		}
		((jet.cluster.data.Cluster)selectedCluster.get(0)).addResidue(pl);
		((jet.cluster.data.Cluster)selectedCluster.get(0)).setScoreMoy(scoreMoy);
		((jet.cluster.data.Cluster)selectedCluster.get(0)).setTraceMoy(traceMoy);
		((jet.cluster.data.Cluster)selectedCluster.get(0)).setpcMoy(pcMoy);
		clusterList.add((jet.cluster.data.Cluster)selectedCluster.get(0));
		evolution=true;
	}
	else
	{
		cluster=new jet.cluster.data.Cluster(radius);
		cluster.addResidue(pl);
		((jet.cluster.data.Cluster)cluster).setScoreMoy((Double)score.get(new Integer(pl.getId())));
		((jet.cluster.data.Cluster)cluster).setTraceMoy((Double)trace.get(new Integer(pl.getId())));
		((jet.cluster.data.Cluster)cluster).setpcMoy((Double)pc.get(new Integer(pl.getId())));
		clusterList.add(cluster);
		evolution=true;
    }
	return evolution;
    }
    
  

    private void cluster(jet.cluster.data.ProxList pl)
    {
	
	Vector selectedCluster=new Vector(1,1);
	jet.cluster.data.Cluster cluster;
	int i;

	for(i=0;i<clusterList.size();i++) 
	{
		cluster=(jet.cluster.data.Cluster)clusterList.get(i);
		if(cluster.isNeighbour(pl.getResidue()))
		{
			selectedCluster.add(clusterList.remove(i--)); 
		}
	}
	if(selectedCluster.size()>0)
	{
		while(selectedCluster.size()>1)
		{
			cluster=(jet.cluster.data.Cluster)selectedCluster.remove(0);
			cluster.merge((jet.cluster.data.Cluster)selectedCluster.remove(0));
			selectedCluster.add(cluster);
		}
		((jet.cluster.data.Cluster)selectedCluster.get(0)).addResidue(pl);
		clusterList.add((jet.cluster.data.Cluster)selectedCluster.get(0));
	}
	else
	{
		cluster=new jet.cluster.data.Cluster(radius);
		cluster.addResidue(pl);
		clusterList.add(cluster);
    }
    }


    // merge clusters in clusterList and store the resulting clusters in a returned variable clusterTable
    public Vector mergeClust()
    {
	int l,k;
	Vector clusterTable=new Vector();
	jet.cluster.data.Cluster clust;
	
	// merge clusters whose residues are neighbors or that are included one in the other	
	for(k=0;k<clusterList.size();k++)
	    {
		clust=(jet.cluster.data.Cluster)clusterList.get(k);
		l=0;
		while (l<clusterTable.size())
		    {
			if (
			    clust.isEmbedded((jet.cluster.data.Cluster)clusterTable.get(l))
			    ||clust.isNeighbour((jet.cluster.data.Cluster)clusterTable.get(l))
			    )
			    {
				clust.merge((jet.cluster.data.Cluster)clusterTable.get(l));
				clusterTable.remove(l);
			    }
			else l++;
		    }
		clusterTable.add(clust);
	    }
	return(clusterTable);
    }
    
    // extend clusters in clusterList with clusters passed as argument and modify clusterTable accordingly
   public void extendClust(Vector clusterTable)
    {
	int l,k;
	jet.cluster.data.Cluster clust;
	boolean isNotFound;

	// merge clusters whose residues are neighbors or that are included one in the other	
	for(k=0;k<clusterList.size();k++)
	    {
		clust=(jet.cluster.data.Cluster)clusterList.get(k);
		isNotFound=true;
		l=0;
		while ( (l<clusterTable.size())&&(isNotFound) )
		    {
			if (
			    clust.isEmbedded((jet.cluster.data.Cluster)clusterTable.get(l))
			    ||clust.isNeighbour((jet.cluster.data.Cluster)clusterTable.get(l))
			    )
			    {
				//System.out.println("Main cluster complemented by secondary cluster");
				clust.merge((jet.cluster.data.Cluster)clusterTable.get(l));
				clusterTable.remove(l);
				clusterTable.add(clust);
				isNotFound=false;
			    }
			else l++;
		    }
	    }
    }


    /** Calcul du score moyen des residus accessibles dont les scores sont dans le vecteur trace.
     * Le score moyen d'un residu est calculé à partir des scores des résidus voisins. 
     * Deux residus sont voisins si ils clusterisent. */
    /* Voisin2 ne sert à rien ici, reliquat d'une ancienne implementation */
    
    public Vector calculScoreMoy(Vector trace, Vector axs)
    {
    	Vector traceMoy=new Vector();
    	double traceVoisins1;
    	double traceVoisins2;
    	int nbVoisins1;
    	int nbVoisins2;
    	boolean present;
    	jet.cluster.data.ProxList plVoisin1;
    	Vector plVoisin2=new Vector();
    	for (int i=0; i<trace.size();i++)
    	{
    		nbVoisins1=0;
    		nbVoisins2=0;
    		int poidVoisin1,poidVoisin2;
    		plVoisin1=dm.getProxList(i);
    		traceVoisins1=0.0;
    		traceVoisins2=0.0;
		//	if ((Double)axs.get(plVoisin1.getId())==1.0)
		if ((Double)axs.get(plVoisin1.getId())!=0.0)
    		{
	    		for(int j=0;j<plVoisin1.getLength();j++)
	    		{
	    			
	    			if (
	    					(plVoisin1.getProxNode(j).getDistance()< radius)
	    					//&&((Double)axs.get(plVoisin1.getProxNode(j).getRef().getId())==1.0)
						&&((Double)axs.get(plVoisin1.getProxNode(j).getRef().getId())!=0.0)
	    				)
	    			{
	    				
	    				traceVoisins1=traceVoisins1+(Double)trace.get(plVoisin1.getProxNode(j).getRef().getId());
	    				nbVoisins1++;
	    				
	    				for(int l=0;l<dm.getProxList(j).getLength();l++)
	    	    		{
		    				present=false;
		    				for(int k=0;k<plVoisin2.size();k++) if (dm.getProxList(j).getProxNode(l).getRef().getId()==((jet.cluster.data.ProxNode)plVoisin2.get(k)).getRef().getId()) present=true;
		    				for(int k=0;k<plVoisin1.getLength();k++) if (dm.getProxList(j).getProxNode(l).getRef().getId()==((jet.cluster.data.ProxNode)plVoisin1.getProxNode(k)).getRef().getId()) present=true;
		    				if (dm.getProxList(j).getProxNode(l).getRef().getId()==plVoisin1.getId()) present=true;
		    				if (
		    	    				(!present)
		    	    				&&(dm.getProxList(j).getProxNode(l).getDistance()<radius)
		    	    				//&&((Double)axs.get(dm.getProxList(j).getProxNode(l).getRef().getId())==1.0)
							&&((Double)axs.get(dm.getProxList(j).getProxNode(l).getRef().getId())!=0.0)
		    	    			)
		    				{
		    	    			traceVoisins2=traceVoisins2+(Double)trace.get(dm.getProxList(j).getProxNode(l).getRef().getId());
		    					nbVoisins2++;
		    					plVoisin2.add(dm.getProxList(j).getProxNode(l));
		    				}
	    	    		}
	    			}
	    		}
	    		
	    		if (nbVoisins1!=0)
	    		{
	    			traceVoisins1=traceVoisins1/nbVoisins1;
	   				if (nbVoisins2!=0)
	   				{
	   					traceVoisins2=traceVoisins2/nbVoisins2;
	   					poidVoisin2=2;
	   				}
	   				else
	   				{
	   					traceVoisins2=0;
	   					poidVoisin2=0;
	   				}
	   				poidVoisin1=3;
	   				
	   			}
	   			else
    			{
	   				traceVoisins1=0;
	    			traceVoisins2=0;
	    			poidVoisin1=0;
	    			poidVoisin2=0;
	    		}
	    		traceMoy.add((poidVoisin1*traceVoisins1+4*(Double)trace.get(plVoisin1.getId()))/(4+poidVoisin1));
	    	}
    		else traceMoy.add(0.0);
    	}
    	return traceMoy;
    	
    }
    
   

   /** Calcul du score moyen des residus accessibles dont les scores sont dans le vecteur trace.
    * Le score moyen d'un residu est calculé à partir des scores des résidus consécutifs a {-2,+2} dans la séquence*/

    public Vector calculScoreSeqMoy(Vector trace, Vector axs, int nbRes)
    {
   	Vector traceMoy=new Vector();
	Vector voisins=new Vector();

    	double traceVoisins;
	int nbVoisins;

	int poidVoisin=3;
	int poidRes=4;

	int id;

	jet.cluster.data.ProxList pl;
    
    	for (int i=0; i<trace.size();i++)
    	{ 
	    pl=dm.getProxList(i);
	    voisins.clear();
	    id=pl.getId();
	    traceVoisins=0.0;
	
	    // compute a value only if the residue is at the surface 
	    if ((Double)axs.get(id)!=0.0)
		{
		    
		    /*if(id<(nbRes-3))
			{
			    voisins.add(pl.getId()+3);
			    }*/
		    /* if(id<(nbRes-2))
			{
			    voisins.add(pl.getId()+2);
			    }*/
		    if(id<(nbRes-1))
			{
			    voisins.add(pl.getId()+1);
			}
		    /*   if(id>2)
			{
			    voisins.add(pl.getId()-3);
			    }*/
		    /*    if(id>1)
			{
			    voisins.add(pl.getId()-2);
			    }*/
		    if(id>0)
			{
			    voisins.add(pl.getId()-1);
			}
		    
		    // total number of consecutive residues
		    nbVoisins=voisins.size();
		    
		    for(int k=0; k<voisins.size();k++)
			{
			    // only accumulate with residues that are at the surface
			    if(((Double)axs.get(k)!=0.0))
				{
				    traceVoisins=traceVoisins+(Double)trace.get((Integer)voisins.get(k));
				}
			}
		    traceVoisins=traceVoisins/nbVoisins;
		    traceMoy.add((poidVoisin*traceVoisins+poidRes*(Double)trace.get(id))/(poidRes+poidVoisin));
		}
	    else traceMoy.add(0.0);
	}
    	return traceMoy;
    	
    }
    

    /** met tous les scores des residus pas a la surface a zero **/
    
    public Vector filterScore(Vector trace, Vector axs)
    {
    	Vector traceFiltered=new Vector();
	int id;

	jet.cluster.data.ProxList pl;
    
    	for (int i=0; i<trace.size();i++)
    	{ 
	    pl=dm.getProxList(i);
	    id=pl.getId();
	
	    // compute a value only if the residue is at the surface 
	    if ((Double)axs.get(id)!=0.0)
		{
		    traceFiltered.add((Double)trace.get(id));
	    	}
	    else traceFiltered.add(0.0);
    	}
    	return traceFiltered;
    	
    }


    
}



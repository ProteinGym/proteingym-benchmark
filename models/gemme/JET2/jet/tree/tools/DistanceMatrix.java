package jet.tree.tools;

import java.util.*;

/** Classe pour calculer les distances entre les sequences de l'alignement "multiAlignment". 
 * Un objet DistanceMatrix est un vecteur contenant des objets Leaf.  */

public class DistanceMatrix extends Vector
{
	/** Vecteur contenant le couple de sequences les plus éloignées */
    Vector distantCouple=null;
    /** Alignement multiple dont il faut calculer la matrice des distances */
    jet.data.datatype.MultiAlignment multiAlignment=null;
    /** Sequence de reference (dont on cherche les residus à l'interface) de l'alignement */
    jet.data.datatype.Sequence refSeq=null;
    /** Matrice de substitution utilisée pour calculer les distances entre les sequences. */
    jet.tree.tools.SubstitutionMatrix sm=null;   
    jet.data.datatype.Residue gap=new jet.data.datatype.Residue('-');   
    jet.data.datatype.Residue ext=new jet.data.datatype.Residue('.');

    /***/
    /** CONSTRUCTEURS */
    /***/
    
    public DistanceMatrix(jet.data.datatype.MultiAlignment multiAlignment, jet.tree.tools.SubstitutionMatrix sm,jet.data.datatype.Sequence ref) throws Exception
    {
	super(1,1); 
	setMultipleAlignment(multiAlignment);
	setSubstitutionMatrix(sm);
	setRefSequence(ref);
	initDistanceMatrix();
	sm=null;
	//multiAlignment=null;
    }
    
    /***/
    /** ACCESSEURS */
    /***/  
    
    public jet.tree.data.Leaf getLeaf(int index){ return (jet.tree.data.Leaf) this.get(index); }
    public Vector getDistantCouple() { return distantCouple; }
    public jet.data.datatype.Sequence getRefSequence() { return refSeq; }
    
    /***/
    /** MODIFICATEURS */
    /***/  
    public void setRefSequence(jet.data.datatype.Sequence ref){ this.refSeq=ref; }
    public void setSubstitutionMatrix(jet.tree.tools.SubstitutionMatrix sm){ this.sm=sm; }
    public void setMultipleAlignment(jet.data.datatype.MultiAlignment multiAlignment){ this.multiAlignment=multiAlignment;}
     
    public void setDistantCouple(jet.data.datatype.Sequence seq1, jet.data.datatype.Sequence seq2)
    {
	distantCouple=new Vector(2);
	distantCouple.add(seq1);
	distantCouple.add(seq2);
	//System.out.println(seq1.getSequenceName()+"\t,\t"+seq2.getSequenceName());
    }
    
    /***/
    /** METHODES */
    /***/ 
    
    /** Retourne les positions du couple de sequences le plus eloigné. */
    
    public int[] findDistantCouple()
    {
    	double max=-10000.0;
    	int[] distantCouple=new int[2];
    	for(int i=0;i<this.size();i++)
	    {
    		jet.tree.data.Leaf leaf=(jet.tree.data.Leaf)this.get(i);
    		for(int j=0;j<leaf.size();j++)
    	    {
    			if (leaf.getDistance(j)>max) {distantCouple[0]=i;distantCouple[1]=j;max=leaf.getDistance(j);}
    				
    	    }
	    }
    	return distantCouple;
    }
    
    /** Retourne la distance entre les deux sequences aux positions i et j dans 
     * l'alignement en utilisant la matrice de substitution sm */
    
    private double getScore(int i, int j)
    {
	double d=0.0;
	
	for(int k=0;k<multiAlignment.getAlignment(i).getSequenceLength();k++) 
	    {
		if((!isGapped(i,j,k))&&(!isExtension(i,j,k)))
		    d+=sm.getDistance(multiAlignment.getAlignment(i).getResidue(k),multiAlignment.getAlignment(j).getResidue(k));
	    }

	return d;
    }
  
    /** Retourne la distance au format Double entre les deux sequences au position i et j dans 
     * l'alignement en utilisant la matrice de substitution sm */
    
    private Double getDoubleScore(int i, int j) { return new Double(getScore(i, j)); }
        
    /** Indique si une le residu "k" d'une sequence "i" ou"j" est un gap ou non */
    
    public boolean isGapped(int i,int j,int k)
    {
	return ((multiAlignment.getAlignment(i).getResidue(k).equals(gap))||(multiAlignment.getAlignment(j).getResidue(k).equals(gap)));
    }

    /** Indique si une le residu "k" d'une sequence "i" ou"j" est une extension ou non */
    
    public boolean isExtension(int i,int j,int k)
    { 
    	return ((multiAlignment.getAlignment(i).getResidue(k).equals(ext))||(multiAlignment.getAlignment(j).getResidue(k).equals(ext))); 
    }
   
    /** Calcul des distances normalisées entre les sequences sous forme d'objet leaf. 
     * Formule: d_eff=-log((d(i,j)-d_min(i,j))/(d_max(i,j)-d_min(i,j))). */
    
    private void initDistanceMatrix() throws Exception
    {
    
    int i,j,k,nbDistanceInvalid=0;	
	Vector score=null, distance;
	double scoreMin, scoreMax, scoreDiff, scoreEff;
	jet.tree.data.Leaf leaf;
	//double mu,z,sd;
	//int seq1=0,seq2=0;
	double s;
	//double max=-10000.0;
	
	/* Calcul des distances entre les sequences. La matrice est symetrique
	 *  ==> pas besoin de calculer toute la matrice. */
	for(i=0;i<multiAlignment.size();i++)
	    {
		score=new Vector(1,1);
		
		for(j=i+1;j<multiAlignment.size();j++) 
		    {
			/* Calcul de la distance entre les sequence i et j*/
			s=getScore(i,j);
			/* Ajout de cette distance au vecteur score */
			score.add(new Double(s)) ;
		    }
		/* score correspond aux distances de la sequence i avec toutes 
		 * les autres sequences (une ligne de la matrice). On l'ajoute 
		 * à l'objet courant qui est donc bien un vecteur de vecteur 
		 * (matrice) */
		add(score);
	    }
	/* Calcul des scores normalisés à partir des distances calculées juste au dessus. 
	 * On aura "multiAlignment.size()" distance pour chaque sequence ==> redondance, la 
	 * matrice est toujours symetrique mais pour des facilités d'acces aux infos on 
	 * duplique les infos afin d'associer une sequence a toutes les distances qui la 
	 * concerne */
	for(i=0;i<multiAlignment.size();i++)
	    {
	
		distance=new Vector(multiAlignment.size());
		/* On retire l'element i de l'objet courant 
		 * (correspond à la ligne i de la matrice) */
		score=(Vector) remove(i);
	    k=0;
	    /* On recupere les  distances normalisées deja calculées pour la sequence i. */
		for(j=0;j<i;j++) distance.add(getLeaf(j).getDoubleDistance(i));
		/* On intercale un "0.0" qui est la distance de la sequence à elle meme */
		distance.add(new Double(0.0));
		/* On calcule les distances normalisées de la sequence i avec les autres sequences. */
		for(j=i+1;j<multiAlignment.size();j++)
		    {
			//scoreMin=getRandomScore(multiAlignment.getAlignment(i),multiAlignment.getAlignment(j));
			//scoreMin=multiAlignment.getAlignment(i).size()*sm.getExpectedValue();
		
			/* Normalisation du score non normalisé score.get(k++) */
			scoreMin=getLowerLimitScore(i,j).doubleValue(); 
			scoreMax=getUpperLimitScore(i,j);
			scoreDiff=scoreMax-scoreMin;
			
			scoreEff=(((Double)score.get(k++)).doubleValue()-scoreMin);
			
			if(scoreEff<0.000001) 
			    {
				/* distance normalisée négative */
				//System.err.println("normalized score negative: scoreDiff="+scoreDiff+" scoreMin="+scoreMin+" score="+((Double)score.get(k-1)).doubleValue());		
				//System.err.println("Unable to generate distance for the couple ( "+i+" , "+j+" ):\n"+multiAlignment.getAlignment(i).getSequenceName()+" vs "+multiAlignment.getAlignment(j).getSequenceName()+"\n\n"+multiAlignment.getAlignment(i).toFasta()+multiAlignment.getAlignment(j).toFasta());
				//System.err.println(" Unable to generate distance for the couple ( "+i+" , "+j+" ):"+multiAlignment.getAlignment(i).getSequenceName()+" vs "+multiAlignment.getAlignment(j).getSequenceName());
				//System.err.println("non gapped aligned length:"+getNonGappedAlignedLength(i,j));				
				//throw new jprotein.tree.tools.DistanceMatrixException(i,j);
				distance.add(null);
				nbDistanceInvalid++;
			    }
			else
				{
				scoreEff/=scoreDiff;			
				scoreEff=-Math.log(scoreEff);
				//System.out.println(i+" -> "+distance.size()+" : "+scoreEff);
				distance.add(new Double(scoreEff));
				/* Sauvegarde des sequences les plus distantes */
				//if(scoreEff>max) { seq1=i; seq2=j; max=scoreEff;}
				}
		    }
		/* Ajout à la bonne position "i" d'un objet Leaf contenant la sequence 
		 * ainsi que les nouvelles distances. */
		add(i,new jet.tree.data.Leaf(multiAlignment.getAlignment(i),distance,i));
	    }
	//System.out.println("decided : "+seq1+"->"+seq2); //System.out.println(seq1+" , "+seq2);
	int initialSize=this.size();
	/* Recherche de plus grand ensemble de sequences dont les distances 2 à 2 sont toutes calculables. */
	//System.err.println("debut findMaximalValidSequenceSet()");
	findMaximalValidSequenceSet();
	//System.err.println("fin findMaximalValidSequenceSet()");
	//if (initialSize!=this.size())
		//System.out.println("nbDistanceInvalid : "+nbDistanceInvalid);		
		//System.err.println("proportion : "+(((double)initialSize)*75/100)+" initialSize : "+initialSize+" finalSize : "+this.size()+" nbDistanceInvalid : "+nbDistanceInvalid);	
	//System.err.println("proportion : "+(((double)initialSize)*80/100));
	if (((double)this.size())<(((double)initialSize)*75/100))
	{
		//System.err.println("je passe dans l'exception de distantMatrix");
		System.out.println("nb Seq Eliminated : "+(initialSize-this.size()));	
		throw new Exception();
	}
	/* Initialisation du couple le plus distant */
	int[] distantCouple=findDistantCouple();
	setDistantCouple(multiAlignment.getAlignment(distantCouple[0]),multiAlignment.getAlignment(distantCouple[1]));
		
    }
    
    /** Conserve dans l'objet courant DistanceMatrix le plus grand ensemble de 
     * sequences dont distances 2 à 2 sont toutes calculables contenant la sequence de reference. */
    
    public void findMaximalValidSequenceSet()
    {
    	int nbWorthSeq;
    	while ((nbWorthSeq=worthInvalidSequence())!=-1)
    	{
    		//System.err.println("nbWorthSeq : "+nbWorthSeq);
    		multiAlignment.remove(nbWorthSeq);
    		this.remove(nbWorthSeq);
	    	for(int i=0;i<this.size();i++)
		    {
	    		((jet.tree.data.Leaf)this.get(i)).remove(nbWorthSeq);
		    }
    	}
    }
    
    /** Retourne la position de la sequence possedant le plus grand nombre de distances non calculables. */
    
    public int worthInvalidSequence()
    {
    	int max=0;
    	int nbNotValidDistance;
    	int worthSeq=-1;
    	for(int i=0;i<this.size();i++)
	    {
    		jet.tree.data.Leaf leaf=(jet.tree.data.Leaf)this.get(i);
    		nbNotValidDistance=0;
    		for(int j=0;j<leaf.size();j++)
    	    {
    			if (leaf.getDoubleDistance(j)==null) nbNotValidDistance++;
    	    }
    		//System.err.println("nbNotValidDistance : "+nbNotValidDistance);
    		if (nbNotValidDistance>max)
    		{
    			if (!getRefSequence().equals((multiAlignment.get(i))))
    			{
    				max=nbNotValidDistance;
    				worthSeq=i;
    			}
    			//else
    			//{
    			//	System.err.println("refseq: "+getRefSequence().getSequenceName());
    			//	System.err.println("seq worth: "+((jprotein.data.datatype.Sequence)multiAlignment.get(i)).getSequenceName());
    			//}
    		}
	    }
    	return worthSeq;
    }
    
    /** Calcul de la distance maximale entre deux sequences i et j: 
     * distance(i,i)*distance(j,j)/2. */

    public double getUpperLimitScore(int i, int j) { return 0.5*(getScore(i,i)+getScore(j,j)); }

    /** Calcul de la distance minimale entre deux sequences i et j:
     * (lg sans gap de l'alignement)*(valeur moyenne de la matrice de substitution sm) */
    
    public Double getLowerLimitScore(int i, int j){ return new Double(getNonGappedAlignedLength(i,j)*sm.getExpectedValue()); }

    /** Retourne le nombre de position alignées sans gap entre deux sequences "i" et "j" */
    
    public int getNonGappedAlignedLength(int i, int j)
    {
	int k,g=0;
	for(k=0;k<multiAlignment.getAlignment(i).size();k++) 
	    { 
		if((!isGapped(i,j,k))&&(!isExtension(i,j,k))) 
		    { g++;} 
	    }
	return g;
    }
    
    /** Converti en String la matrice des distances */
    
    public String toString()
    {
	int i,j;
	String s="Distance Matrix "+size()+" * "+size()+"\n\n";
	
	for(i=0;i<size();i++)
	    {
		for(j=0;j<getLeaf(i).size();j++) s+=getLeaf(i).getDistance(j)+"\t";
		s+="\n\n";
	    }
	return s;
    }
    
}
/*
      public double getRandomScore(jprotein.data.datatype.Sequence seq1,jprotein.data.datatype.Sequence seq2)
    {
	Vector v1,v2,scoreTable=new Vector(100);
	int i,j;
	double mu=0.0,std=0.0,z=1.0;
	jprotein.data.datatype.Sequence al1,al2;
	
	for(i=0;i<100;i++)
	    {
		v1=new Vector(seq1);
		v2=new Vector(seq2);

		al1=new jprotein.data.datatype.Sequence();
		al2=new jprotein.data.datatype.Sequence();

		for(j=0;j<seq1.size();j++)
		    {
			al1.add((jprotein.data.datatype.Residue)v1.remove((int)(Math.random()*v1.size())));
			al2.add((jprotein.data.datatype.Residue)v2.remove((int)(Math.random()*v2.size())));
		    }
		scoreTable.add(getDoubleScore(al1,al2));	
	    }
	mu=jprotein.tree.tools.Statistic.mean(scoreTable);
	std=jprotein.tree.tools.Statistic.standardError(scoreTable);
	return jprotein.tree.tools.Statistic.zToValue(z,mu,std);
	
	}
*/

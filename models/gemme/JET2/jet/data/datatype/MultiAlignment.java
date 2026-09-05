package jet.data.datatype;

import java.util.*;

/** Classe pour stocker les sequences alignées dans des objets MultiAlignment 
 * et pour calculer les caracteristiques de ces alignements (memes methodes que
 *  la classe BlastPairwise). <BR>
 *  Remarque: Les methodes calculant l'IC ne sont pas utilisées car l'IC est
 *   calculée sur les alignment obtenus par blast */

public class MultiAlignment extends Vector
{
    jet.data.datatype.Residue gap=new jet.data.datatype.Residue('-');
    jet.data.datatype.Residue ext =new jet.data.datatype.Residue('.');
 
    /***/
    /** CONSTRUCTEURS */
    /***/
    
    public MultiAlignment() { super(1,1); }

    public MultiAlignment(Vector sequenceList) 
    { 
	this();
	for(int i=0; i<sequenceList.size();i++)
	    {
		addAlignment((jet.data.datatype.Sequence)sequenceList.get(i));
	    }
    }

    /***/
    /** ACCESSEURS */
    /***/
    
    public jet.data.datatype.Sequence getAlignment(int index)
    { return (jet.data.datatype.Sequence) this.get(index);}
    
    public Vector getAlignments(Vector index)
    { 
    	Vector v=new Vector();
    	for (int i=0;i<index.size();i++)
    		v.add(getAlignment((Integer)index.get(i)));
    	return v;
    }
    
    /***/
    /** METHODES */
    /***/
    
    /** Ajoute la sequence "ali" à l'alignment courant en remplacant 
     * les "gaps" en debut et fin de chaine par des "ext" (".") */
    
    public void addAlignment(jet.data.datatype.Sequence ali) 
    { 
    
	int i=0;
	
	/* Remplacement des "gaps" en debut de chaine par des "ext" */
	while(i<ali.size())
	    {
		if(!ali.getResidue(i).equals(gap)) break;
		else ali.setElementAt(ext,i++);
	    }

	i=ali.size()-1;
	
	/* Remplacement des "gaps" en fin de chaine par des "ext" */
	while(i>=0)
	    {
		if(!ali.getResidue(i).equals(gap)) break; 
		else ali.setElementAt(ext,i--);
	    }	
	//System.out.println(ali.toFasta());
	
	/* Ajout de la sequence alignee à l'alignement */
	this.add(ali);
    }
  
    /** Remplace les "ext" en debut et fin de chaine par des "gap" (".") */
    
    public void changeExt() 
    { 
    	int i;
    	for(int j=0;j<this.size();j++)
	    {
    		jet.data.datatype.Sequence ali=this.getAlignment(j);
    		/* Remplacement des "ext" en debut de chaine par des "gap" */
    		i=0;
    		while(i<ali.size())
    		    {
    			if(!ali.getResidue(i).equals(ext)) break;
    			else ali.setElementAt(gap,i++);
    		    }

    		i=ali.size()-1;
    		
    		/* Remplacement des "ext" en fin de chaine par des "gap" */
    		while(i>=0)
    		    {
    			if(!ali.getResidue(i).equals(ext)) break; 
    			else ali.setElementAt(gap,i--);
    		    }	
    		//System.out.println(ali.toFasta());
	    }
    }
    
    /** Retourne un vecteur de composition. 
     * Le resultat est sous forme de vecteur de la meme longueur que la sequence ref.
     * Chaque position du vecteur correspond à une position de la sequence ref et contient 
     * un tableau dont chaque case stocke le nombre de residu d'un type. 
     * non utilisée remplacee par */
    
    public Vector getAlignmentComposition(jet.data.datatype.Sequence ref, Vector indexSeq)
    {
	Vector alignComp=new Vector(ref.getNonGappedSequenceLength());
	this.changeExt();
	if(this.size()>0)
	    {
		int i,j;
		
		int[] index;
		/* Boucle sur chaque position de la sequence ref */
		for(i=0;i<ref.size();i++)
		    {
			if(!gap.equals(ref.getResidue(i)))
			{
				/* Chaque position de alignCom contient un tableau contenant le 
				 * nombre de chacun des "24" aa à cette position de la sequence */
			    index=new int[24]; Arrays.fill(index,0);
			    /* Boucle sur chaque sequence de l'alignement courant */
			   
			    for(j=0;j<indexSeq.size();j++) 
			    {	
			    	index[getAlignment((Integer)indexSeq.get(j)).getResidue(i).getResidueIndex()]++;
			    }
			    //for(j=0;j<this.size();j++) index[getAlignment(j).getResidue(i).getResidueIndex()]++;
			    
			    alignComp.add(index);
			}
		    }
	    }
	
	return alignComp;
    }
    
    /** Retourne un vecteur de composition. 
     * Le resultat est sous forme de vecteur de la meme longueur que les sequences de l'alignement.
     * Chaque position contient un tableau dont chaque case stocke le nombre de residu d'un type. */
    
    public Vector getAlignmentComposition()
    {
    Vector alignComp=new Vector(1);
    this.changeExt();
	if(this.size()>0)
	    {
		int size=this.getAlignment(0).getSequenceLength();
		alignComp=new Vector(size);
		int i,j;
		
		int[] index;
		/* Boucle sur chaque position de la sequence ref */
		for(i=0;i<size;i++)
		    {
				/* Chaque position de alignCom contient un tableau contenant le 
				 * nombre de chacun des "25" aa à cette position de la sequence */
			    index=new int[25]; Arrays.fill(index,0);
			    /* Boucle sur chaque sequence de l'alignement courant */
			   
			    for(j=0;j<this.size();j++) 
			    {	
			    	index[getAlignment(j).getResidue(i).getResidueIndex()]++;
			    }
			    //for(j=0;j<this.size();j++) index[getAlignment(j).getResidue(i).getResidueIndex()]++;
			    
			    alignComp.add(index);
			}
		    
	    }
	
	return alignComp;
    }

    /** Ajoute les compositions de deux vecteurs "ac1" et "ac2" representant la 
     * composition de deux alignements.
     * Les deux vecteurs "ac1" et "ac2 doivent etre de meme longueur sinon 
     * l'ajout n'est pas fait 
     * non utilisée */
    
    public static Vector sumAlignmentComposition(Vector ac1, Vector ac2)
    {
	Vector ac=null;
	
	if(ac1.size()==ac2.size())
	    {
		int i,j;
		int[] index,index1,index2;

		ac=new Vector(ac1.size());

		for(i=0;i<ac1.size();i++)
		    {
			index=new int[24];
			index1=(int[])ac1.get(i); index2=(int[])ac2.get(i);
  
			for(j=0;j<24;j++) index[j]=index1[j]+index2[j];
			
			ac.add(index);
		    }
	    }

	return ac;
    }

    /** Converti en frequence le vecteur de composition (methode getAlignmentComposition) 
     * contenant le nombre et le type de residus à chaque position. 
     * non utilisée */
    
    public static Vector getCompositionFrequencies(Vector ac)
    {

	Vector acf=new Vector(ac.size());
	int i,j;
	double gapNoise=0.0;
	double total;
	int[] indexAc;
	double[] index;

	for(i=0;i<ac.size();i++)
	    {
		index=new double[24];
		indexAc=(int[])ac.get(i);
		
		total=0.0;
		
		for(j=0;j<index.length;j++) total+=(double)indexAc[j];
		total=1.0/total;
		for(j=0;j<index.length-1;j++) index[j]=((double)indexAc[j])*total;		
		acf.add(index);
	    }

	return acf;
    }

    /** Calcule "l'information content" pour chaque position d'un 
     * vecteur contenant les frequences des differents type de residu
     *  a chaque position (methode getCompositionFrequencies). 
     *  Verifier la formule de l'ic. 
     *  non utilisée */
    
    public static Vector getInformationContent(Vector acf)
    {
	int i,j;
	double index[];
	double sMax=Math.log(20.0)/Math.log(10.0),s;
	Vector sic=new Vector(acf.size());

	for(i=0;i<acf.size();i++)
	    {
		s=sMax;
		index=(double[])acf.get(i);
		for(j=0;j<20;j++) 
		    {
			if(index[j]>0.00000001)
			    {
				if(index[j]>0.999) { index[j]=0.999; }
				s+=index[j]*(Math.log(index[j])/Math.log(10.0));
			    }
		    }
		sic.add(new Double(s));
	    }

	return sic;
    }
    
    public jet.data.datatype.MultiAlignment clone()
    {
    	jet.data.datatype.MultiAlignment ma=new jet.data.datatype.MultiAlignment();
    	ma.addAll(this);
    	return ma;
    }
    

    /** Retourne l'alignment en String au format fasta */
    
    public String toString()
    {
	String str="";

	for(int i=0;i<this.size();i++)
	    {
		str+="\n"+getAlignment(i).toFasta();
	    }
	return str;
    }

}

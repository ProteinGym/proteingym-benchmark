package jet.tree.tools;

import java.util.*;
import java.io.*;

public class ET
{

    static jet.data.datatype.Residue gap=new jet.data.datatype.Residue('-');
    static jet.data.datatype.Residue ext=new jet.data.datatype.Residue('.');
    static jet.tree.tools.Tree treeTools=new jet.tree.tools.Tree();
    jet.tree.tools.SubstitutionMatrix sm=null;

    /***/
    /** CONSTRUCTEURS */
    /***/
    
    public ET(String matrixFile)
    {
	sm=new jet.tree.tools.SubstitutionMatrix(matrixFile);
    }
    
    /***/
    /** ACCESSEURS */
    /***/
    
    public jet.tree.tools.SubstitutionMatrix getSubstitutionMatrix(){return sm;}
    
    /***/
    /** METHODES */
    /***/
    
    public double[] generateIcRecord(jet.data.datatype.MultiAlignment ma, jet.data.datatype.Sequence ref) //throws Exception
    {
    	
    	double[] icRecord=new double[ref.getSequenceLength()];
    	jet.tree.tools.NJ nJ=null;
    	
    	try{
    	/* Génération de l'arbre */
    	//jprotein.data.datatype.MultiAlignment maSave=ma.clone();
    	nJ=new jet.tree.tools.NJ(ma,sm,ref);
    	
    	treeTools.getTraceIcAnalysis(nJ.getTree(),icRecord,ma,ref);
    	}
    	catch(Exception e)
	    {
    		e.printStackTrace();
    		System.exit(0);
	    }
    	
    	return icRecord;
    }
    
    public jet.tree.tools.NJ generateTree(jet.data.datatype.MultiAlignment ma, jet.data.datatype.Sequence ref) throws Exception
    {
    	jet.tree.tools.NJ nJ=null;
    	/* Génération de l'arbre */
    	nJ=new jet.tree.tools.NJ(ma,sm,ref);
	/********************reduce alphabet inserted here *****/
    	/* Initialisation des résidus conservés et backtrace sur tout l'arbre. */
    	nJ.getTree().initConservedResidue();
    	/* Affichage de l'arbre */
    	//jprotein.tree.Window treeWin=new jprotein.tree.Window(nJ.getTree());
    	//System.out.println(""+nJ.getTree());
    	return nJ;
    }
    
    /** Génère un vecteur des sequences traces des niveaux 1 à n.
     * Remarque: un residu trace au niveau n l'est aussi au niveau n+1 */
    
    public Vector generateTraceRecord(jet.tree.tools.NJ nJ,jet.data.datatype.MultiAlignment ma, jet.data.datatype.Sequence ref) throws Exception
    {
	Vector traceLevels=new Vector(1,1);
	
	jet.data.datatype.Sequence refAli=null;
	Iterator iter;
	
	/* Calcul des traces des différents niveaux */
	iter=treeTools.getTraceAnalysis(nJ.getTree()).iterator();
	/* Recuperation de la sequence de reference dans l'alignement */
	refAli=ma.getAlignment(ma.indexOf(ref));
	/* transposition de la trace à la sequence de reference */
	int nv=0;
	while(iter.hasNext()) 
	{
		nv++;
		Vector trace=(Vector)iter.next();
		jet.data.datatype.Sequence seq=getTraceSequence(refAli,trace);
	    traceLevels.add(seq);   
	}
	return traceLevels;
    }

    /** Calcule le niveau (en double) trace des residus relatif au niveau max considéré 
     * pour atteindre une couverture de "coverage"%. Formule: (Nt-Ltj)/Nt */
    
    public double[] getRelativeTraceDepth(Vector traceLevels, double coverage)
    {
	/* Creation d'un tableau des niveaux de trace de la taille de la sequence */
	double[] level=new double[((jet.data.datatype.Sequence)traceLevels.get(0)).size()];
	/* Initialisation de la couverture maximale de la sequence souhaitée */
	double maxCover=((jet.data.datatype.Sequence)traceLevels.get(0)).size()*coverage;
	jet.data.datatype.Sequence traceSeq=null;
	Vector traceDepth=new Vector(1,1);
	int i,j;
	
	for(i=0;i<traceLevels.size();i++)
	    {
		traceSeq=(jet.data.datatype.Sequence)traceLevels.get(i);
		/* couverture souhaitée atteinte, on ne regarde pas les traces de niveau inférieur */
		if(traceSeq.getNonGappedSequenceLength()>maxCover) break;
		
		for(j=0;j<traceSeq.size();j++)
		    {	
			/* Un residu trace sur tout l'arbre aura level=i */
			if(!gap.equals((jet.data.datatype.Residue)traceSeq.get(j)))
			    level[j]+=1.0;    
		    }
	    }

	if(i>0)
	    {
		/* Varie entre 0 et 1, 1 pour les residus conservés sur tout l'arbre.
		 * Correspond bien à la formule de l'article (Nt-Ltj)/Nt. 
		 * ic i = Nt = niveau max. 
		 * Remaque: on considere ici que le residus conservés sur tout 
		 * l'arbre sont de niveau 0 (en comparaison avec l'article). */
		for(j=0;j<level.length;j++) level[j]/=(double)i;
	    }
	return level;
    }

    /** Transpose la trace sous forme de vecteur d'IndexedResidu en une trace sous forme 
     * de sequence de meme longueur que la sequence "seq" mais sans les gaps. 
     * Elle y ajoute les residus conservés depuis la racine de l'arbre phylogenetique. */
    
    private jet.data.datatype.Sequence getTraceSequence(jet.data.datatype.Sequence seq, Vector trace)
    {
	int i,pos;
	char [] tab=new char[seq.getNonGappedSequenceLength()];

	jet.data.datatype.Sequence traceSeq=new jet.data.datatype.Sequence();
	/* Ajout des residus traces */
	for(i=0; i<trace.size();i++)
	    {
		pos=seq.getNonGappedPosition(((jet.data.datatype.IndexedResidue)trace.get(i)).getIndex());
		if(pos!=-1) tab[pos]=((jet.data.datatype.IndexedResidue)trace.get(i)).getResidueSymbol();
	    }
	/* Transposition sur la sequence de reference */
	for(i=0;i<tab.length;i++)
	    {
		/* Je ne comprend pas comment le 0 est arrivé la (Peut etre par defaut). */
		if(tab[i]==(char)0) traceSeq.add(new jet.data.datatype.Residue('-'));
		
		else traceSeq.add(new jet.data.datatype.Residue(tab[i]));
	    }
	return traceSeq;
    }
    
    /** Calcul des SAM (0->1) pour chaque position de la sequence de reference ref */
    
    public double[] getRelativeSAM(jet.tree.tools.NJ nJ, jet.data.datatype.MultiAlignment ma, jet.data.datatype.Sequence ref)
    {
	
    jet.data.datatype.Sequence refAli=ma.getAlignment(ma.indexOf(ref));
	int[] SAMTable=new int[refAli.getSequenceLength()];
	for(int i=0;i<SAMTable.length;i++) SAMTable[i]=0;
	
	double[] SAMTableDoubleTransposed=new double[ref.size()];
	for(int i=0;i<SAMTableDoubleTransposed.length;i++) SAMTableDoubleTransposed[i]=0;
	
	try{
	
	treeTools.getSAMAnalysis(nJ.getTree(),SAMTable);
	
	int max=-1000;
	for(int i=0;i<SAMTable.length;i++) if (SAMTable[i]>max) max=SAMTable[i];
	
	double[] SAMTableDouble=new double[SAMTable.length];
	
	for(int i=0;i<SAMTableDouble.length;i++)
	{
		SAMTableDouble[i]=((double)SAMTable[i]/(double)max)*(-1.0)+1.0;
	}
	
	//pos=seq.getNonGappedPosition(((jprotein.data.datatype.IndexedResidue)trace.get(i)).getIndex());
	//if(pos!=-1) tab[pos]=((jprotein.data.datatype.IndexedResidue)trace.get(i)).getResidueSymbol();	
	
	/* Transposition a la sequence de reference */
	
	int pos=0;
	for(int i=0;i<SAMTableDouble.length;i++)
	{
		pos=refAli.getNonGappedPosition(i);
		if(pos!=-1) SAMTableDoubleTransposed[pos]=SAMTableDouble[i];
	}
	
    }
	catch(Exception e)
    {
		e.printStackTrace();
		System.exit(0);
    }
	return SAMTableDoubleTransposed;
	
    }
    
    
    
    public double[] getOurRelativeSAM(jet.tree.tools.NJ nJ, jet.data.datatype.MultiAlignment ma, jet.data.datatype.Sequence ref)
    {
	
    double[] SAMTableDoubleTransposed=new double[ref.size()];
    	
    try{	
    jet.data.datatype.Sequence refAli=ma.getAlignment(ma.indexOf(ref));
    
    Vector SAMTables=new Vector(20);
    for(int i=0;i<20;i++)
    	SAMTables.add(new int[refAli.getSequenceLength()]);  
    
	int[] SAMTable=new int[refAli.getSequenceLength()];
	
	for(int i=0;i<refAli.getSequenceLength();i++)
	{
		SAMTable[i]=0;
		for(int j=0;j<20;j++)
			((int[])SAMTables.get(j))[i]=0;
	}
	
	for(int i=0;i<ref.size();i++) SAMTableDoubleTransposed[i]=0.0;
	
	treeTools.getSAMAnalysis(nJ.getTree(),SAMTable);
	
	for(int i=0;i<20;i++) treeTools.getSAMAnalysis(nJ.getTree(),(int[])SAMTables.get(i),i);

	double[] SAMTableDouble=new double[SAMTable.length];
	
	for(int i=0;i<SAMTable.length;i++) SAMTableDouble[i]=0.0;
	
	double rapport=0.0;
	
	for(int i=0;i<SAMTableDouble.length;i++)
	{
		for(int j=0;j<20;j++)
		{
			rapport=((double)((int[])SAMTables.get(j))[i]/(double)SAMTable[i]);
			if (rapport>0.0)
			{
				SAMTableDouble[i]=SAMTableDouble[i]-(rapport*Math.log(rapport));
			
			}
		}
		
	}
	
	double max=-1000.0;
	for(int i=0;i<SAMTableDouble.length;i++) if (SAMTableDouble[i]>max) max=SAMTableDouble[i];
	
	for(int i=0;i<SAMTableDouble.length;i++)
	{
		SAMTableDouble[i]=((double)SAMTableDouble[i]/(double)max)*(-1.0)+1.0;
	}
	
	/* Transposition a la sequence de reference */
	
	int pos=0;
	for(int i=0;i<SAMTableDouble.length;i++)
	{
		pos=refAli.getNonGappedPosition(i);
		if(pos!=-1) SAMTableDoubleTransposed[pos]=SAMTableDouble[i];
	}
	
    }
	catch(Exception e)
    {
		e.printStackTrace();
		System.exit(0);
    }
	
	return SAMTableDoubleTransposed;
	
    }
    
    public double[] getOurRelativeIC(jet.tree.tools.NJ nJ, jet.data.datatype.MultiAlignment ma, jet.data.datatype.Sequence ref)
    {
    	
    double[] ICTableDoubleTransposed=new double[ref.size()];
    for(int i=0;i<ref.size();i++) ICTableDoubleTransposed[i]=0.0;	
    
    try{	
    	
    /* Initialisation des profiles sur tout l'arbre. */
    nJ.getTree().initProfile();
    
    jet.data.datatype.Sequence refAli=ma.getAlignment(ma.indexOf(ref));
    
    Vector compositionAlignement=ma.getAlignmentComposition();
    
    Vector ICTables=new Vector(21);
    Vector countTreeTables=new Vector(21);
    Vector ICTablesBig=new Vector(21);
    Vector ICTablesBigDec=new Vector(21);
    Vector ICTablesDouble=new Vector(21);
    
    for(int i=0;i<21;i++)
    {
    	ICTables.add(new double[refAli.getSequenceLength()]); 
    	countTreeTables.add(new double[refAli.getSequenceLength()]); 
    	ICTablesBig.add(new java.math.BigInteger[refAli.getSequenceLength()]); 
    	ICTablesBigDec.add(new java.math.BigDecimal[refAli.getSequenceLength()]); 
    	ICTablesDouble.add(new double[refAli.getSequenceLength()]); 
    }
	
	for(int i=0;i<refAli.getSequenceLength();i++)
	{
		for(int j=0;j<21;j++)
		{
			((double[])ICTables.get(j))[i]=0.0;
			((double[])countTreeTables.get(j))[i]=0.0;
			((java.math.BigInteger[])ICTablesBig.get(j))[i]=new java.math.BigInteger("0");
			((java.math.BigDecimal[])ICTablesBigDec.get(j))[i]=new java.math.BigDecimal("0.0");
			((double[])ICTablesDouble.get(j))[i]=0.0;
		}
	}
	
	double rapport=0.0;
	double rapportRandom=getOurRandomIC(nJ);
	
	double beta=Math.exp(-1)/0.168472;//0.168472 est le max des rapport générés sur la base de Huang
	double puissance=-1/(Math.log(beta*rapportRandom));
	//if (puissance<0.59416099) puissance=0.59416099;// minimum pour beta=Math.exp(-1)/0.168472
	if (puissance<0.58418707) puissance=0.58418707;//0.58418707 est le minimum pour la puissance qui preserve la convexité de la fonction avec beta=2 
	if (puissance>1.0) puissance=1.0;
	double x=Math.exp(-1)/beta;
	double coef=x*Math.log(beta*x)-Math.log(beta);
	x=Math.exp(-1/puissance)/beta;
	coef=coef/(Math.pow(x, puissance)*Math.log(beta*x)-Math.log(beta));

	double numberOfSequences=(Double)nJ.getTree().getNumberOfSequencesDouble();
	java.math.BigInteger numberOfSequencesBig=(java.math.BigInteger)nJ.getTree().getNumberOfSequencesBig();
	
	double min=0.0,max;
	java.math.BigDecimal minBigDec=new java.math.BigDecimal("0.0");
	for(int i=0;i<20;i++) treeTools.getICAnalysis(nJ.getTree(),(double[])countTreeTables.get(i),(double[])ICTables.get(i),(java.math.BigInteger[])ICTablesBig.get(i),i);
	
	java.math.MathContext mc = new java.math.MathContext(10);
	for(int i=0;i<20;i++) 
	{
		
		for(int j=0;j<((double[])ICTables.get(i)).length;j++)
		{
			if (numberOfSequences==Double.POSITIVE_INFINITY)
			{
				((java.math.BigDecimal[])ICTablesBigDec.get(i))[j]=new java.math.BigDecimal(((java.math.BigInteger[])ICTablesBig.get(i))[j]).divide(new java.math.BigDecimal(numberOfSequencesBig),mc);
				((java.math.BigDecimal[])ICTablesBigDec.get(20))[j]=((java.math.BigDecimal[])ICTablesBigDec.get(20))[j].add(((java.math.BigDecimal[])ICTablesBigDec.get(i))[j]);
			}
			else
			{
				((double[])ICTables.get(i))[j]=((double[])ICTables.get(i))[j]/numberOfSequences;
				((double[])ICTables.get(20))[j]=((double[])ICTables.get(20))[j]+((double[])ICTables.get(i))[j];
			}
			((double[])countTreeTables.get(20))[j]=((double[])countTreeTables.get(20))[j]+((double[])countTreeTables.get(i))[j];
		}
	}
	
	min=1.0;
	minBigDec=new java.math.BigDecimal("1.0");
	for(int i=20;i<21;i++) 
	{
		if (numberOfSequences==Double.POSITIVE_INFINITY)
		{
			for(int j=0;j<((java.math.BigDecimal[])ICTablesBigDec.get(i)).length;j++)
			{
				if (((java.math.BigDecimal[])ICTablesBigDec.get(i))[j].compareTo(new java.math.BigDecimal("0.0"))==1)
				{
					minBigDec=((java.math.BigDecimal[])ICTablesBigDec.get(i))[j].min(minBigDec);
				}
			}
		}
		else
		{
			for(int j=0;j<((double[])ICTables.get(i)).length;j++)
			{
				if ((((double[])ICTables.get(i))[j]<min)&&(((double[])ICTables.get(i))[j]>0.0))
					min=((double[])ICTables.get(i))[j];
			}
		}
	}
	
	if (numberOfSequences==Double.POSITIVE_INFINITY) min=jet.tools.Statistic.log10(minBigDec);
	else min=Math.log10(min);
	
	for(int i=20;i<21;i++) 
	{	
		for(int j=0;j<((double[])ICTables.get(i)).length;j++)
		{
			if (numberOfSequences==Double.POSITIVE_INFINITY) 
			{
				if (((java.math.BigDecimal[])ICTablesBigDec.get(i))[j].compareTo(new java.math.BigDecimal("0.0"))==1)
				{
					((double[])ICTables.get(i))[j]=jet.tools.Statistic.log10(((java.math.BigDecimal[])ICTablesBigDec.get(i))[j])-1.1*min;
				}
			
			}
			else
			{
				if (((double[])ICTables.get(i))[j]>0.0)
				{
					((double[])ICTables.get(i))[j]=Math.log10(((double[])ICTables.get(i))[j])-1.1*min;
				}
			}
		}
	}
		
	max=Math.log10(1.0)-1.1*min;
	
	for(int i=20;i<21;i++) 
	{	
		for(int j=0;j<((double[])ICTables.get(i)).length;j++)
		{
			((double[])ICTables.get(i))[j]=((double[])ICTables.get(i))[j]/max;
		}
	}
	
	double scale=1.0;
	min=0.0;
	for(int i=20;i<21;i++) 
	{
		for(int j=0;j<((double[])ICTables.get(i)).length;j++)
		{
			if (((double[])ICTables.get(i))[j]>0.0)
			{
				
				((double[])ICTablesDouble.get(i))[j]=scale*(Math.pow(((double[])ICTables.get(i))[j],puissance)*Math.log(beta*((double[])ICTables.get(i))[j])/Math.log(2)-Math.log(beta)/Math.log(2))*coef;
				if (((double[])ICTablesDouble.get(i))[j]<min)
					min=((double[])ICTablesDouble.get(i))[j];
			}
			
		}
	}
	max=0.0;//max de la fonction utilisée
	
	max=max-min;
	for(int i=20;i<21;i++) 
	{
		for(int j=0;j<((double[])ICTablesDouble.get(i)).length;j++)
		{
			((double[])ICTablesDouble.get(i))[j]=((double[])ICTablesDouble.get(i))[j]-min;
		}
	}

	double[] ICTableDouble=new double[refAli.getSequenceLength()];
	
	for(int i=0;i<refAli.getSequenceLength();i++) ICTableDouble[i]=0.0;
	
	for(int i=0;i<ICTableDouble.length;i++)
	{
		for(int j=20;j<21;j++)
		{
			ICTableDouble[i]=ICTableDouble[i]+((double[])ICTablesDouble.get(j))[i];
			
		}
		
	}
	
	max=0.0;
	for(int i=0;i<ICTableDouble.length;i++) if (ICTableDouble[i]>max) max=ICTableDouble[i];
	
	for(int i=0;i<ICTableDouble.length;i++)
	{
		ICTableDouble[i]=(double)ICTableDouble[i]*(1/max);
	}
	
	/* Transposition a la sequence de reference */
	
	int pos=0;
	for(int i=0;i<ICTableDouble.length;i++)
	{
		pos=refAli.getNonGappedPosition(i);
		if(pos!=-1) ICTableDoubleTransposed[pos]=ICTableDouble[i];
	}
	
    }
	catch(Exception e)
    {
		e.printStackTrace();
		System.exit(0);
    }
	
	return ICTableDoubleTransposed;
	
    }

    public double getOurRandomIC(jet.tree.tools.NJ nJ)
    {
    double moyenneRapport=0.0;
    try{	
	
	double numberOfSequences=(Double)nJ.getTree().getNumberOfSequencesDouble();
	java.math.BigInteger numberOfSequencesBig=(java.math.BigInteger)nJ.getTree().getNumberOfSequencesBig();
	
	double rapport=0.0;
	java.math.BigInteger rapportBig=new java.math.BigInteger("0");
	double min=0.0,max=0.0;
	int nbAACons;
	Vector classes;
	double pb;
	
	Vector rapports=new Vector();
	for(int iter=0;iter<100;iter++)
	{
		nbAACons=(int)(Math.random()*20.0)+1;
		classes=new Vector(nbAACons);
		/* choix du nombre de classes d'aa à pourvoir */
		for(int i=0;i<nbAACons;i++){classes.add(new Vector());}
		Vector nodes=new Vector();
		treeTools.listOfNode(nJ.getTree(),nodes);
    	Vector tag = new Vector(nodes.size());
    	for(int i=0;i<nodes.size();i++){tag.add(-1);}
    	treeTools.randomSAM(classes,nJ,1.1,nodes,tag);
		int nbTagEtiquetesI;
		while(nodes.size()>0)
		{
			if (classes.size()==1)
			{
				nbTagEtiquetesI=0;
				for (int k=0;k<tag.size();k++) 
					if (((Integer)tag.get(k)).intValue()==0)
						nbTagEtiquetesI++;
				if (nbTagEtiquetesI==nodes.size())
					break;// attention si il reste des noeud etiquetes i
				pb=1.0;
			}
			else pb=0.5;
			treeTools.randomSAM(classes,nJ,pb,nodes,tag);
		}
		/* Calcul des rapports pour chaque classe d'aa */
		for(int i=0;i<nbAACons;i++)
		{
			rapport=0.0;
			rapportBig=new java.math.BigInteger("0");
			java.math.MathContext mc = new java.math.MathContext(10);
			for(int j=0;j<((Vector)classes.get(i)).size();j++)
			{
				rapport=rapport+((jet.tree.data.Leaf)((Vector)classes.get(i)).get(j)).getNumberOfSequencesDouble();
				rapportBig=rapportBig.add(((jet.tree.data.Leaf)((Vector)classes.get(i)).get(j)).getNumberOfSequencesBig());
				}
			if (numberOfSequences==Double.POSITIVE_INFINITY)
			{
				rapport=new java.math.BigDecimal(rapportBig).divide(new java.math.BigDecimal(numberOfSequencesBig),mc).doubleValue();
			}
			else
				rapport=rapport/numberOfSequences;		
			rapports.add(rapport);
			
		}
	}
	
	min=1.0;
	for(int j=0;j<rapports.size();j++)
	{
		if (((Double)rapports.get(j)<min)&&((Double)rapports.get(j)>0.0))
			min=(Double)(rapports.get(j));
	}
	min=Math.log10(min);
	for(int j=0;j<rapports.size();j++)
	{
		if ((Double)rapports.get(j)>0.0)
		{
			rapports.set(j, Math.log10((Double)rapports.get(j))-1.1*min);
		}
	}
	max=Math.log10(1.0)-1.1*min;
	
	for(int j=0;j<rapports.size();j++)
	{
		rapports.set(j, (Double)rapports.get(j)/max);
	}
	
	for(int i=0;i<rapports.size();i++){moyenneRapport=moyenneRapport+((Double)rapports.get(i)).doubleValue();}
	moyenneRapport=moyenneRapport/rapports.size();
	
    }
	catch(Exception e)
    {
		e.printStackTrace();
		System.exit(0);
    }
	
	return moyenneRapport;
	
    }
    
    public double[] getOurRelativeIC1(jet.tree.tools.NJ nJ, jet.data.datatype.MultiAlignment ma, jet.data.datatype.Sequence ref)
    {
    	
    double[] ICTableDoubleTransposed=new double[ref.size()];
    for(int i=0;i<ref.size();i++) ICTableDoubleTransposed[i]=0.0;	
    
    try{	
    	
    /* Initialisation des profiles sur tout l'arbre. */
    nJ.getTree().initProfile();
    jet.data.datatype.Sequence refAli=ma.getAlignment(ma.indexOf(ref));
   
    Vector[] ICTables=new Vector[refAli.getSequenceLength()]; 
    Vector[] ICTablesTemp=new Vector[refAli.getSequenceLength()]; 
    Vector[] ICTablesBig=new Vector[refAli.getSequenceLength()]; 
    Vector[] ICTablesBigTemp=new Vector[refAli.getSequenceLength()]; 
    Vector[] ICTablesDouble=new Vector[refAli.getSequenceLength()]; 
    
	
	for(int i=0;i<refAli.getSequenceLength();i++)
	{
		ICTables[i]=new Vector();
		ICTablesTemp[i]=new Vector();
		ICTablesBig[i]=new Vector();
		ICTablesBigTemp[i]=new Vector();
		ICTablesDouble[i]=new Vector();
	}
	
	double rapport=0.0;
	double rapportRandom=getOurRandomIC(nJ);
	double puissance=-1/(Math.log(rapportRandom));
	double x=Math.exp(-1);
	double coef=x*Math.log(x);
	x=Math.exp(-1/puissance);
	double numberOfSequences=(Double)nJ.getTree().getNumberOfSequencesDouble();
	java.math.BigInteger numberOfSequencesBig=(java.math.BigInteger)nJ.getTree().getNumberOfSequencesBig();
	
	double min=0.0,max;
	for(int i=0;i<20;i++)
	{
		
		treeTools.getICAnalysis(nJ.getTree(),(Vector[])ICTablesTemp,(Vector[])ICTablesBigTemp,i);
		
		for(int k=0;k<refAli.getSequenceLength();k++)
		{
			if (ICTablesTemp[k].size()>0)
			{
				ICTables[k].addAll(ICTablesTemp[k]);
				ICTablesBig[k].addAll(ICTablesBigTemp[k]);
			}
			else
			{
				ICTables[k].add(0.0);
				ICTablesBig[k].add(new java.math.BigInteger("0"));
			}
			ICTablesTemp[k]=new Vector();
			ICTablesBigTemp[k]=new Vector();
		}
	}
		
		java.math.MathContext mc = new java.math.MathContext(10);
		for(int j=0;j<ICTables.length;j++)
		{
			for (int k=0;k<((Vector)ICTables[j]).size();k++)
			{
				if (numberOfSequences==Double.POSITIVE_INFINITY)
					((Vector)ICTables[j]).set(k, new java.math.BigDecimal(((java.math.BigInteger)((Vector)ICTablesBig[j]).get(k))).divide(new java.math.BigDecimal(numberOfSequencesBig),mc).doubleValue());
				else
					((Vector)ICTables[j]).set(k, ((Double)((Vector)ICTables[j]).get(k)).doubleValue()/numberOfSequences);
			}
		}
		
		min=1.0;
		
		for(int j=0;j<ICTables.length;j++)
		{
			for (int k=0;k<((Vector)ICTables[j]).size();k++)
			{
				if ((((Double)ICTables[j].get(k)).doubleValue()<min)&&(((Double)ICTables[j].get(k)).doubleValue()>0.0))
					min=((Double)ICTables[j].get(k)).doubleValue();
			}
		}
		
		min=Math.log10(min);
		for(int j=0;j<ICTables.length;j++)
		{
			for (int k=0;k<((Vector)ICTables[j]).size();k++)
			{
				if (((Double)ICTables[j].get(k)).doubleValue()>0.0)
				{
					ICTables[j].set(k, Math.log10(((Double)ICTables[j].get(k)).doubleValue())-1.1*min);
				}
			}
			
		}
		
		max=Math.log10(1.0)-1.1*min;
		
		for(int j=0;j<ICTables.length;j++)
		{
			for (int k=0;k<((Vector)ICTables[j]).size();k++)
				ICTables[j].set(k, ((Double)ICTables[j].get(k)).doubleValue()/max);
		}
		
		min=0.0;
		for(int j=0;j<ICTables.length;j++)
		{
			for (int k=0;k<((Vector)ICTables[j]).size();k++)
			{
				if (((Double)ICTables[j].get(k)).doubleValue()>0.0)
				{
					ICTables[j].set(k, Math.pow(((Double)ICTables[j].get(k)).doubleValue(),puissance)*Math.log(((Double)ICTables[j].get(k)).doubleValue())/Math.log(2)*coef);
					if (((Double)ICTables[j].get(k)).doubleValue()<min)
						min=((Double)ICTables[j].get(k)).doubleValue();
				}
			}
			
		}
		max=0.0;//max de la fonction utilisée
		max=max-min;
		
		for(int j=0;j<ICTables.length;j++)
		{
			for (int k=0;k<((Vector)ICTables[j]).size();k++)
			{
				ICTables[j].set(k, ((Double)ICTables[j].get(k)).doubleValue()-min);
			}
		}

	double[] ICTableDouble=new double[refAli.getSequenceLength()];
	
	for(int i=0;i<refAli.getSequenceLength();i++) ICTableDouble[i]=0.0;
	
	for(int i=0;i<ICTableDouble.length;i++)
	{
		for (int k=0;k<((Vector)ICTables[i]).size();k++)
		{
			ICTableDouble[i]=ICTableDouble[i]+((Double)ICTables[i].get(k)).doubleValue();
		}
	}
	
	max=0.0;
	for(int i=0;i<ICTableDouble.length;i++) if (ICTableDouble[i]>max) max=ICTableDouble[i];
	
	for(int i=0;i<ICTableDouble.length;i++)
	{
		ICTableDouble[i]=(double)ICTableDouble[i]*(1/max);
	}
	
	/* Transposition a la sequence de reference */

	int pos=0;
	for(int i=0;i<ICTableDouble.length;i++)
	{
		pos=refAli.getNonGappedPosition(i);
		if(pos!=-1) ICTableDoubleTransposed[pos]=ICTableDouble[i];
	}

    }
	catch(Exception e)
    {
		e.printStackTrace();
		System.exit(0);
    }
	
	return ICTableDoubleTransposed;
	
    }
    
}

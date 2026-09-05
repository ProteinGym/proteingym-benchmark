package jet.tree.tools;
import java.util.*;

public class Tree
{
    public static final int LEFT=0;
    public static final int RIGHT=1;
    
    /***/
    /** CONSTRUCTEURS */
    /***/
    
    public Tree(){}
    
    /***/
    /** METHODES */
    /***/

    public jet.tree.data.Leaf getLeaf(jet.tree.data.SimpleNode root, jet.data.datatype.Sequence alignment)
	{
	    jet.tree.data.Leaf leaf=null;
	   
	    if(root.goLeft() instanceof jet.tree.data.SimpleNode)
	    {
		if((leaf=getLeaf((jet.tree.data.SimpleNode)root.goLeft(),alignment))!=null) { return leaf; }
	    }
	    
	    else
	    {
		
		if(alignment.equals(root.goLeft().getSequence()))
		{
		    return root.goLeft();
		}
		
		
	    }
	    
	    if(root.goRight() instanceof jet.tree.data.SimpleNode)
	    {
		if((leaf=getLeaf((jet.tree.data.SimpleNode)root.goRight(),alignment))!=null) { return leaf; }
	    }
	    
	    else
	    {
		
		if(alignment.equals(root.goRight().getSequence()))
		{
		    return root.goRight();
		}
		
	    }
	    return null;
	}

    public jet.tree.data.PathIndex getPathIndex(jet.tree.data.SimpleNode root, jet.tree.data.Leaf leaf)
	{
	    jet.tree.data.PathIndex path;
	    
	    if(root.goLeft() instanceof jet.tree.data.SimpleNode)
	    {
		if((path=getPathIndex((jet.tree.data.SimpleNode)root.goLeft(),leaf))!=null) { path.addDirection(LEFT); return path; }
	    }
	    
	    else
	    {
		if(leaf.equals(root.goLeft()))
		{
		    path=new jet.tree.data.PathIndex(); path.addDirection(LEFT);
		    return path;
		}
	    }
	    
	    if(root.goRight() instanceof jet.tree.data.SimpleNode)
	    {
		if((path=getPathIndex((jet.tree.data.SimpleNode)root.goRight(),leaf))!=null) { path.addDirection(RIGHT); return path; }
	    }
	    
	    else
	    {
		if(leaf.equals(root.goRight()))
		{
		    path=new jet.tree.data.PathIndex(); path.addDirection(RIGHT);
		    return path;
		}
	    }
	    
	    return null;
	}
    
    public double getPathDistance(jet.tree.data.SimpleNode root, jet.tree.data.PathIndex pathIndex)
	{
	    double distance=0.0;
	    
	    if(pathIndex.size()>0)
	    {
		if(((Integer)pathIndex.remove(0)).intValue()==RIGHT)
		{
		    if(root.goRight() instanceof jet.tree.data.SimpleNode)
		    {
			distance=getPathDistance((jet.tree.data.SimpleNode)root.goRight(),pathIndex);
			distance+=root.getRightVertice();
		    }
		    else { return root.getRightVertice(); }
		}
		else
		{
		    if(root.goLeft() instanceof jet.tree.data.SimpleNode)
		    {
			distance=getPathDistance((jet.tree.data.SimpleNode)root.goLeft(),pathIndex);
			distance+=root.getLeftVertice();
		    }
		    else { return root.getLeftVertice(); }
		}
		
		
	    }
	    return distance;
	}
 
    /** Initialise le fils "direction" du noeud "node" à "leaf" */
    
    public void setLeaf(jet.tree.data.SimpleNode node, int direction, jet.tree.data.Leaf leaf)
	{
	    if(direction==RIGHT) node.setRight(leaf);
	    else node.setLeft(leaf);
	}
    
    /** retourne le fils "direction" du noeud "node" à son  */
    
    public jet.tree.data.Leaf getLeaf(jet.tree.data.SimpleNode node, int direction)
	{
	    if(direction==RIGHT) return node.goRight();
	    else return node.goLeft();
	}
 
    /** Initialise à "distance" la distance entre le noeud "node" à son fils "direction" */
    
    public void setVertice(jet.tree.data.SimpleNode node, int direction, double distance)
	{
	    if(direction==RIGHT) node.setRightVertice(distance); else node.setLeftVertice(distance);
	}

    /** retourne la distance du noeud "node" à son fils "direction" */
    
    public double getVertice(jet.tree.data.SimpleNode node, int direction)
	{
	    if(direction==RIGHT) return node.getRightVertice(); else return node.getLeftVertice();
	}

    public void rootMidTree(jet.tree.data.SimpleNode root, jet.tree.data.Leaf leaf1, jet.tree.data.Leaf leaf2)
	{
	    jet.tree.data.PathIndex path, p1,p2;
	    double distance, dist1,dist2;
	    int i=0;
	    
	    p1=getPathIndex(root,leaf1); p2=getPathIndex(root,leaf2);
	    
	    while(Math.min(p1.size(),p2.size())>=2)
	    {
		if(p1.direction(0)==p2.direction(0))
		{
		    shiftRoot(root,p1.direction(0),p1.direction(1));
		    p1.remove(0); p2.remove(0);
		    
		    if(p1.direction(0)!=p2.direction(0)) { break;}
		}
		
		else { break; }
	    }
	    
	    p1=getPathIndex(root,leaf1); p2=getPathIndex(root,leaf2);
	    dist1=getPathDistance(root,p1); dist2=getPathDistance(root,p2);
	    distance=(dist1+dist2)/2;

	    if(dist2>dist1)
	    {
		distance=dist2-distance;
		moveRoot(root,getPathIndex(root,leaf2),distance);
	    }
	    else
	    {
		distance=dist1-distance;
		moveRoot(root,getPathIndex(root,leaf1),distance);
	    }
	 
	}
    
    public void moveRoot(jet.tree.data.SimpleNode root, jet.tree.data.PathIndex pathIndex, double distance)
	{
	    int direction1,direction2;
	    double verticeLength;
	    
	    if(pathIndex.size()>1)
	    {
//System.out.println("moving root.....");
		direction1=pathIndex.direction(0);
		direction2=pathIndex.direction(1);
		verticeLength=getVertice(root,direction1);
		
		if(distance<verticeLength)
		{
		    translateRoot(root,direction1,distance);
		}
		
		else
		{
		    shiftRoot(root,direction1,direction2);
		    pathIndex.remove(0);
		    moveRoot(root,pathIndex,distance-verticeLength);
		}
	    }
	}
    
    public void translateRoot(jet.tree.data.SimpleNode root, int direction, double distance)
	{
//System.out.println("translating root.....");
	    if(direction==LEFT) distance=-distance;
	    
	    root.setLeftVertice(root.getLeftVertice()+distance);
	    root.setRightVertice(root.getRightVertice()-distance);
	}
      
    public void shiftRoot(jet.tree.data.SimpleNode root, int direction1,int direction2)
	{
	    jet.tree.data.SimpleNode node= (jet.tree.data.SimpleNode) getLeaf(root, direction1);
	    jet.tree.data.Leaf oldNode,nextNode;
	    double nextDistance, oldDistance;
	    
	    //System.out.println("shifting root.....");
	    
	    nextNode=getLeaf(node,direction2);
	    nextDistance=getVertice(node,direction2);
	    
	    oldNode=getLeaf(root,opposite(direction1));
	    oldDistance=getVertice(root,LEFT)+getVertice(root,RIGHT);
	    
	    setLeaf(node,direction2,oldNode);
	    setVertice(node,direction2,oldDistance);
	    
	    setLeaf(root,direction2,nextNode);
	    setVertice(root,direction2,nextDistance);
	    
	    setLeaf(root,opposite(direction2),node);
	    setVertice(root,opposite(direction2),0.0);
	    
	}
    
    /** Retourne la direction opposée à "direction" */
    
    public int opposite(int direction) 
    { if(direction==RIGHT) return LEFT; else return RIGHT;}
    
    /** Retourne sous forme de vecteur les distances entre le noeud "root" 
     * et tous ses fils par un parcours main gauche en s'arretant si aucune 
     * sous branche de profondeur > 2 n'est observée (car les feuilles ne 
     * sont pas des SimpleNode). Verifier si c'est le but recherché. Peut etre 
     * faut il considérer un niveau de plus (si on veut tous les node) ou 2 de 
     * plus, si il faut en plus les feuilles. */
    
    public Vector getNodeDistance(jet.tree.data.SimpleNode root,double distance)
    {
	Vector dist=new Vector(1,1);
	if(root.goLeft() instanceof jet.tree.data.SimpleNode)
	    {
		//dist.add(new Double(root.getLeftVertice()+distance));
		/* erreur: les feuilles ne sont pas des SimpleNode */
		if((((jet.tree.data.SimpleNode)root.goLeft()).goLeft() instanceof jet.tree.data.SimpleNode)||(((jet.tree.data.SimpleNode)root.goLeft()).goRight() instanceof jet.tree.data.SimpleNode))
		{
		    dist.add(new Double(root.getLeftVertice()+distance));
		    dist.addAll(getNodeDistance((jet.tree.data.SimpleNode)root.goLeft(),distance+root.getLeftVertice()));
		}
	    }
	if(root.goRight() instanceof jet.tree.data.SimpleNode)
	    {
		//dist.add(new Double(root.getRightVertice()+distance));
		/* erreur: les feuilles ne sont pas des SimpleNode */
		if((((jet.tree.data.SimpleNode)root.goRight()).goLeft() instanceof jet.tree.data.SimpleNode)||(((jet.tree.data.SimpleNode)root.goRight()).goRight() instanceof jet.tree.data.SimpleNode))
		    {
			dist.add(new Double(root.getRightVertice()+distance));
			dist.addAll(getNodeDistance((jet.tree.data.SimpleNode)root.goRight(),distance+root.getRightVertice()));
		    }
	    }
	return dist;


    }
  
    /** Récupère tous les noeuds dont la distance est inférieur ou egale à une trace 
     * ainsi que leur sous arbres imediats. Autrement dit:
     * retourne sous forme de vecteur les noeuds dont la distance à la racine est 
     * inférieur à "maxDistance" par un parcours main gauche de l'arbre en s'arretant
     *  si aucune sous branche de profondeur > 2 n'est observée (car les feuilles ne 
     * sont pas des SimpleNode). Verifier si c'est le but recherché. Peut etre 
     * faut il considérer un niveau de plus (si on veut tous les node) ou 2 de 
     * plus, si il faut en plus les feuilles. */
    
    public Vector getNodePartition(jet.tree.data.SimpleNode root,double maxDistance)
    {
    
	Vector partition=new Vector(1,1);
				
	//partition.add(root);
		
	if(maxDistance-root.getRightVertice()>0.0000001)
	    {
		/* MaxDistance n'est pas depasé on relance sur les sous arbres */
		if(root.goRight() instanceof jet.tree.data.SimpleNode)
		    {	
			if((((jet.tree.data.SimpleNode)root.goRight()).goLeft() instanceof jet.tree.data.SimpleNode)||(((jet.tree.data.SimpleNode)root.goRight()).goRight() instanceof jet.tree.data.SimpleNode))
			partition.addAll(getNodePartition((jet.tree.data.SimpleNode)root.goRight(),maxDistance-root.getRightVertice()));
		    }
	    }
	else
	    {
		/* MaxDistance est depasé on recupere le sous arbre */
		if(root.goRight() instanceof jet.tree.data.SimpleNode)
		    {
			//if((((jprotein.tree.data.SimpleNode)root.goRight()).goLeft() instanceof jprotein.tree.data.SimpleNode)||(((jprotein.tree.data.SimpleNode)root.goRight()).goRight() instanceof jprotein.tree.data.SimpleNode))
			partition.add(root.goRight());
		    }
		if(maxDistance-root.getRightVertice()>-0.0000001)
			{
			/* On est sur un noeud il faut les sous arbres de ce noeud */
			if(root.goRight() instanceof jet.tree.data.SimpleNode)
			    {
				if(((jet.tree.data.SimpleNode)root.goRight()).goLeft() instanceof jet.tree.data.SimpleNode)
					partition.add(((jet.tree.data.SimpleNode)root.goRight()).goLeft());
				if(((jet.tree.data.SimpleNode)root.goRight()).goRight() instanceof jet.tree.data.SimpleNode)
					partition.add(((jet.tree.data.SimpleNode)root.goRight()).goRight());
				/* Astuce : On met deux fois le noeud sur lequel est la trace car les backtraces de ce noeud
				 * peuvent etre zappée or elles sont conservées dans les deux sous arbres */
				partition.add(root.goRight());
			    }
			}
		
	    }
	if(maxDistance-root.getLeftVertice()>0.0000001)
	    {
		/* MaxDistance n'est pas depasé on relance sur les sous arbres */
		if(root.goLeft() instanceof jet.tree.data.SimpleNode)
		    {		
			if((((jet.tree.data.SimpleNode)root.goLeft()).goLeft() instanceof jet.tree.data.SimpleNode)||(((jet.tree.data.SimpleNode)root.goLeft()).goRight() instanceof jet.tree.data.SimpleNode))
			partition.addAll(getNodePartition((jet.tree.data.SimpleNode)root.goLeft(),maxDistance-root.getLeftVertice()));
		    }
	    }
	else
	    {
		/* MaxDistance est depasé on recupere le sous arbre */
		if(root.goLeft() instanceof jet.tree.data.SimpleNode)
		    {
			//if((((jprotein.tree.data.SimpleNode)root.goLeft()).goLeft() instanceof jprotein.tree.data.SimpleNode)||(((jprotein.tree.data.SimpleNode)root.goLeft()).goRight() instanceof jprotein.tree.data.SimpleNode))
			partition.add(root.goLeft());
		    }
		if(maxDistance-root.getLeftVertice()>-0.0000001)
			{
			/* On est sur un noeud il faut en plus de ces sous arbres, recuperer ce noeud */
			if(root.goLeft() instanceof jet.tree.data.SimpleNode)
			    {
				if(((jet.tree.data.SimpleNode)root.goLeft()).goLeft() instanceof jet.tree.data.SimpleNode)
					partition.add(((jet.tree.data.SimpleNode)root.goLeft()).goLeft());
				if(((jet.tree.data.SimpleNode)root.goLeft()).goRight() instanceof jet.tree.data.SimpleNode)
					partition.add(((jet.tree.data.SimpleNode)root.goLeft()).goRight());
				/* Astuce : On met deux fois le noeud sur lequel est la trace car les backtraces de ce noeud
				 * peuvent etre zappée or elles sont conservées dans les deux sous arbres */
				partition.add(root.goLeft());
			    }
			}
	    }
	
       	return partition;
    }

    /** Retourne un vecteur des traces (trace niveau 1, ... , trace niveau n ) 
     * à partir du noeud racine de l'arbre phylogenetique */
    
    public Vector getTraceAnalysis(jet.tree.data.SimpleNode root)
    {
	Vector traceTable=new Vector(1,1);
	/* Calcul des distances de la racine aux noeuds. */
	Vector nodeDistance=getNodeDistance(root,0.00000);
	//System.out.println("distances:");
	//for(int nb=0;nb<nodeDistance.size();nb++) System.out.print(" "+nodeDistance.get(nb));
	//System.out.println("");
	double d,min;
	Vector partition=new Vector(1,1);	
	/* La partition lorsque le niveau est au noeud racine est constituée 
	 * de la racine et de ces deux noeuds fils ==> La trace est forcement 
	 * constituée des residus conservés à la racine plus les backtraces de ces 
	 * deux noeuds fils */ 
	if(root.goLeft() instanceof jet.tree.data.SimpleNode)
		partition.add(root.goLeft());
	if(root.goRight() instanceof jet.tree.data.SimpleNode)
		partition.add(root.goRight());	
	traceTable.add(getTrace(partition,root.getConservedResidue()));
	int i,j;
	while(nodeDistance.size()>0)
	    {
		j=0; min=100000.0;
		/* Recupération de la distance minimale */
		for(i=0;i<nodeDistance.size();i++)
		    {
			d=((Double)nodeDistance.get(i)).doubleValue();
			if(d < min) { min=d; j=i;}	
		    }
		/* Suppression de la valeur minimale */
		d=((Double)nodeDistance.remove(j)).doubleValue();
		/* Récupération de tous les noeuds dont la distance à la racine est egale à d, 
		 * ou imediatement superieure (pas de noeud entre) */
		//System.out.println("partition avec la trace placée à "+d);
		partition=getNodePartition(root,d);
		//for(int nb=0;nb<partition.size();nb++) System.out.print(""+((jprotein.tree.data.SimpleNode)(partition.get(nb))).toStringNode());
		/* Calcul et stockage des differentes traces */
		traceTable.add(getTrace(partition,(Vector)traceTable.lastElement()));
	    }
	
	return traceTable;
    }

    /** Calcule les residus traces d'un niveau x à partir des sequences 
     * backtraces des noeuds de niveau x. La fonction retourne un 
     * vecteur des IndexedResidue traces. */
    
    public Vector getTrace(Vector partition, Vector trace_precedente)
    {
	int i,j;
	Vector index=new Vector(1,1),trace=new Vector(1,1),v;
	
	Integer ind;
	jet.data.datatype.IndexedResidue ir;
	jet.data.datatype.Residue X=new jet.data.datatype.Residue('X');
	jet.tree.data.SimpleNode node=null;
	/* Ajout de la trace precedente */
	for(int nb=0;nb<trace_precedente.size();nb++)
		trace.add((jet.data.datatype.IndexedResidue)trace_precedente.get(nb));
	
	for(i=0;i<partition.size();i++)
	    {
		node=(jet.tree.data.SimpleNode)partition.get(i);
		/* erreur: les feuilles ne sont pas des SimpleNode */
		//if((node.goRight() instanceof jprotein.tree.data.SimpleNode)||(node.goLeft() instanceof jprotein.tree.data.SimpleNode))
		{
		
			v=node.getBackTrace();
		
			for(j=0;j<v.size();j++)
			    {
				ir=(jet.data.datatype.IndexedResidue)v.get(j);
				ind=new Integer(ir.getIndex());
				
				/*
				
				int nbTrace=0;
				for(int k=0;k<index.size();k++)
				{
					if (index.get(k).equals(ind)) nbTrace++;
				}
				if (nbTrace>=1)
				
				*/
				
				if(index.contains(ind)) 
				    {
					/* Ce residu a ete observé dans deux backtrace de ce niveau 
					 * ==> C'est donc une trace de ce niveau et on l'ajoute au 
					 * vecteur trace */
					if(!trace.contains(new jet.data.datatype.IndexedResidue(X,ir.getIndex())))
						/* Qu'en est il des residus trace de la racine ? 
						 * Reponse: Ils sont ajoutés plus tard. */
					    trace.add(new jet.data.datatype.IndexedResidue(X,ir.getIndex()));
				    }
				else index.add(ind);
			    }
		}	
	    }

	return trace;
	
    }
    
    /** Retourne un vecteur des traces (trace niveau 1, ... , trace niveau n ) 
     * à partir du noeud racine de l'arbre phylogenetique */
    
    public void getTraceIcAnalysis(jet.tree.data.SimpleNode root, double[] icTable,jet.data.datatype.MultiAlignment ma,jet.data.datatype.Sequence ref)
    {
	/* Calcul des distances de la racine aux noeuds. */
	Vector nodeDistance=getNodeDistance(root,0.00000);
	//System.out.println("distances:");
	//for(int nb=0;nb<nodeDistance.size();nb++) System.out.print(" "+nodeDistance.get(nb));
	//System.out.println("");
	double d,min;
	Vector partition=new Vector(1,1);	
	/* La partition lorsque le niveau est au noeud racine est constituée 
	 * de la racine et de ces deux noeuds fils ==> La trace est forcement 
	 * constituée des residus conservés à la racine plus les backtraces de ces 
	 * deux noeuds fils */ 
	if(root.goLeft() instanceof jet.tree.data.SimpleNode)
		partition.add(root.goLeft());
	if(root.goRight() instanceof jet.tree.data.SimpleNode)
		partition.add(root.goRight());	
	sumEntropyIc(partition,icTable,ma,ref);
	int i,j;
	while(nodeDistance.size()>0)
	    {
		j=0; min=100000.0;
		/* Recupération de la distance minimale */
		for(i=0;i<nodeDistance.size();i++)
		    {
			d=((Double)nodeDistance.get(i)).doubleValue();
			if(d < min) { min=d; j=i;}	
		    }
		/* Suppression de la valeur minimale */
		d=((Double)nodeDistance.remove(j)).doubleValue();
		/* Récupération de tous les noeuds dont la distance à la racine est egale à d, 
		 * ou imediatement superieure (pas de noeud entre) */
		//System.out.println("partition avec la trace placée à "+d);
		partition=getNodePartition(root,d);
		//for(int nb=0;nb<partition.size();nb++) System.out.print(""+((jprotein.tree.data.SimpleNode)(partition.get(nb))).toStringNode());
		/* Calcul et stockage des differentes traces */
		sumEntropyIc(partition,icTable,ma,ref);
	    }
	
    }

    /** Calcule les residus traces d'un niveau x à partir des sequences 
     * backtraces des noeuds de niveau x. La fonction retourne un 
     * vecteur des IndexedResidue traces. */
    
    public void sumEntropyIc(Vector partition, double[] icTable,jet.data.datatype.MultiAlignment ma,jet.data.datatype.Sequence ref)
    {
		int i,j;
		Vector ic_temp=new Vector(ref.size());
		Vector ic=new Vector(ref.size());
		for(i=0;i<ref.size();i++)
		{
			ic.add(0.0);
		}
		for(j=0;j<partition.size();j++)
		{
			ic_temp=ma.getAlignmentComposition(ref,((jet.tree.data.SimpleNode)partition.get(j)).getPosSeqInMultiAli());
			ic_temp=ma.getCompositionFrequencies(ic_temp);
			ic_temp=ma.getInformationContent(ic_temp);
			
			//ic_temp=jprotein.data.dataformat.parser.BlastPairwise.getAlignmentComposition(ma.getAlignments(((jprotein.tree.data.SimpleNode)partition.get(j)).getPosSeqInMultiAli()),ref);
			//ic_temp=jprotein.data.dataformat.parser.BlastPairwise.getCompositionFrequencies(ic_temp);
			//ic_temp=jprotein.data.dataformat.parser.BlastPairwise.getInformationContent(ic_temp);
			for(i=0;i<ic.size();i++)
			{
				ic.set(i, (Double)ic.get(i)+(Double)ic_temp.get(i));
			}
		}
		for(i=0;i<ic.size();i++)
		{
			ic.set(i, (Double)ic.get(i)/partition.size());
		}
		for(i=0;i<icTable.length;i++)
		{
			icTable[i]=icTable[i]+(Double)ic.get(i);
		}
		
    }
    
    /** Calcul du nombre de SAM qui conservent le residu pour chaque position de l'alignement à partir des backtraces */
    
    public void getSAMAnalysis(jet.tree.data.Leaf node,int[] SAMTable)
    {
    	Vector backTrace=node.getBackTrace();
    	for(int i=0;i<backTrace.size();i++)
    	{
    		SAMTable[((jet.data.datatype.IndexedResidue)backTrace.get(i)).getIndex()]++;
    	}    	
    	    	
    	if(node instanceof jet.tree.data.SimpleNode)
    	{
    		getSAMAnalysis(((jet.tree.data.SimpleNode)node).goLeft(),SAMTable);
    		getSAMAnalysis(((jet.tree.data.SimpleNode)node).goRight(),SAMTable);
    	}
    	
    	
    }
    
    
    /** Calcul du nombre de SAM qui conservent le residu à la valeur residueIndex pour chaque position de l'alignement à partir des backtraces */
    
    public void getSAMAnalysis(jet.tree.data.Leaf node,int[] SAMTable,int residueIndex)
    {
    	Vector backTrace=node.getBackTrace();
    	
    	for(int i=0;i<backTrace.size();i++)
    	{
    		//System.out.println("residu:"+((jprotein.data.datatype.IndexedResidue)backTrace.get(i)).getIndex()+" index:"+((jprotein.data.datatype.IndexedResidue)backTrace.get(i)).getResidueIndex()+"test index:"+residueIndex);
    		if (((jet.data.datatype.IndexedResidue)backTrace.get(i)).getResidueIndex()==residueIndex)
    		{
    		//if (((jprotein.data.datatype.IndexedResidue)backTrace.get(i)).getResidueIndex()==1)
    			//System.out.println("true");
    			SAMTable[((jet.data.datatype.IndexedResidue)backTrace.get(i)).getIndex()]++;
    		}
    	}
    	
    	if(node instanceof jet.tree.data.SimpleNode)
    	{
    		getSAMAnalysis(((jet.tree.data.SimpleNode)node).goLeft(),SAMTable,residueIndex);
    		getSAMAnalysis(((jet.tree.data.SimpleNode)node).goRight(),SAMTable,residueIndex);
    	}
    }
    
   
    public void getICAnalysis(jet.tree.data.Leaf node,double[] countTreeTable, double[] ICTable,java.math.BigInteger[] ICTableBig,int residueIndex)
    {
    	Vector backTrace=node.getBackTrace();
    	for(int i=0;i<backTrace.size();i++)
    	{
    		//System.out.println("residu:"+((jprotein.data.datatype.IndexedResidue)backTrace.get(i)).getIndex()+" index:"+((jprotein.data.datatype.IndexedResidue)backTrace.get(i)).getResidueIndex()+"test index:"+residueIndex);
    		if (((jet.data.datatype.IndexedResidue)backTrace.get(i)).getResidueIndex()==residueIndex)
    		{
    			//temp=node.getNumberOfSequences(false);
    			ICTableBig[((jet.data.datatype.IndexedResidue)backTrace.get(i)).getIndex()]=ICTableBig[((jet.data.datatype.IndexedResidue)backTrace.get(i)).getIndex()].add((java.math.BigInteger)node.getNumberOfSequencesBig());
    			ICTable[((jet.data.datatype.IndexedResidue)backTrace.get(i)).getIndex()]=ICTable[((jet.data.datatype.IndexedResidue)backTrace.get(i)).getIndex()]+((Double)node.getNumberOfSequencesDouble());
    			countTreeTable[((jet.data.datatype.IndexedResidue)backTrace.get(i)).getIndex()]=countTreeTable[((jet.data.datatype.IndexedResidue)backTrace.get(i)).getIndex()]+1.0;
    		}
    	}
    	
    	if(node instanceof jet.tree.data.SimpleNode)
    	{
    		getICAnalysis(((jet.tree.data.SimpleNode)node).goLeft(),countTreeTable,ICTable,ICTableBig,residueIndex);
    		getICAnalysis(((jet.tree.data.SimpleNode)node).goRight(),countTreeTable,ICTable,ICTableBig,residueIndex);
    	}
    }
    
    public void listOfNode(jet.tree.data.Leaf node, Vector nodes)
    {
    	nodes.add(node);
    	if(node instanceof jet.tree.data.SimpleNode)
    	{
	    	listOfNode(((jet.tree.data.SimpleNode)node).goLeft(),nodes);
	    	listOfNode(((jet.tree.data.SimpleNode)node).goRight(),nodes);
    	}
    }
    
    public Vector listOfFather(jet.tree.data.Leaf node)
    {
    	Vector nodes=new Vector();
    	while ((node=node.goFather())!=null) nodes.add(node);
    	return nodes;
    }
    
    public void randomSAM(Vector classes,jet.tree.tools.NJ nJ,double pb, Vector nodes, Vector tag)
    {
    	
    	Vector tempNodes;
    	jet.tree.data.Leaf brotherNode;
    	jet.tree.data.Leaf node;
    	int nbRand;
    	int nbTagEtiquetesI;
    	
    	/* pour chaque classe à pourvoir on choisi aleatoirement un noeud et 
    	 * on elimine les noeuds qui ne sont plus ateignables (pere et fils) */
    	for (int i=0;i<classes.size();i++)
    	{
    		//if ((!(nodes.size()>0))) break;
			
    		while (Math.random()<pb)
    		{
    			if ((!(nodes.size()>0))) break;
    			else
    			{
    				nbTagEtiquetesI=0;
    				//System.out.println("tags:");
    				for (int k=0;k<tag.size();k++) 
    				{
    					if (((Integer)tag.get(k)).intValue()==i)
    						nbTagEtiquetesI++;
    					//System.out.println(""+tag.get(k));
    				}
    				if (nbTagEtiquetesI==nodes.size())
    					break;// attention si il reste des noeud etiquetes i
    				//System.out.println("nbTag:"+nbTagEtiquetesI+", node size:"+nodes.size());
    			}
    			
	    		/* choix aléatoire d'un noeud non etiqueté par la classe i*/
    			nbRand=(int)(Math.random()*nodes.size());
    			//System.out.print("nbnoeud:"+nodes.size()+"noeud choisi:"+nbRand);
	    		while (((Integer)tag.get(nbRand)).intValue()==i)
	    		{
	    			nbRand=(int)(Math.random()*nodes.size());
	    			//System.out.print("nbnoeud:"+nodes.size()+"noeud choisi:"+nbRand);
	    		}
	    		node=(jet.tree.data.Leaf)nodes.get(nbRand);
	    		//System.out.print(""+node);
	    		/* recherche et etiquetage du noeud frere */
	    		if (node.goFather()!=null)
	    		{
	    			brotherNode=node.goFather();
	    			if (node==(((jet.tree.data.SimpleNode)brotherNode).goLeft()))
	    				brotherNode=((jet.tree.data.SimpleNode)brotherNode).goRight();
	    			else
	    				brotherNode=((jet.tree.data.SimpleNode)brotherNode).goLeft();
	    			for (int k=0;k<nodes.size();k++)
	    			{
	    				if (brotherNode==nodes.get(k))
	    				{
	    					tag.set(k, i);
	    				}
	    			}
	    		}
	    		/* recherche des noeuds fils */
	    		tempNodes=new Vector();
	    		listOfNode(node,tempNodes);
	    		/* recherche des noeuds peres */
	    		tempNodes.addAll(listOfFather(node));
	    		/* suppression de la liste des noeuds peres, fils et du noeud courant */
	    		for (int j=0;j<tempNodes.size();j++)
	    		{
	    			for (int k=0;k<nodes.size();k++)
	    			{
	    				if (tempNodes.get(j)==nodes.get(k))
	    				{
	    					nodes.remove(k);
	    					tag.remove(k);
	    				}
	    			}
	    		}		
	    		/* ajout du noeud à la classe i */
	    		((Vector)classes.get(i)).add(node);
	    		if (pb==1.1) break;
    		}
    	}
    }
    
    public void getICAnalysis(jet.tree.data.Leaf node, Vector[] ICTable,Vector[] ICTableBig,int residueIndex)
    {
    	Vector backTrace=node.getBackTrace();
    	for(int i=0;i<backTrace.size();i++)
    	{
    		//System.out.println("residu:"+((jprotein.data.datatype.IndexedResidue)backTrace.get(i)).getIndex()+" index:"+((jprotein.data.datatype.IndexedResidue)backTrace.get(i)).getResidueIndex()+"test index:"+residueIndex);
    		if (((jet.data.datatype.IndexedResidue)backTrace.get(i)).getResidueIndex()==residueIndex)
    		{
    			//temp=node.getNumberOfSequences(false);
    			ICTableBig[((jet.data.datatype.IndexedResidue)backTrace.get(i)).getIndex()].add((java.math.BigInteger)node.getNumberOfSequencesBig());
    			ICTable[((jet.data.datatype.IndexedResidue)backTrace.get(i)).getIndex()].add((Double)node.getNumberOfSequencesDouble());
    		}
    	}
    	
    	if(node instanceof jet.tree.data.SimpleNode)
    	{
    		getICAnalysis(((jet.tree.data.SimpleNode)node).goLeft(),ICTable,ICTableBig,residueIndex);
    		getICAnalysis(((jet.tree.data.SimpleNode)node).goRight(),ICTable,ICTableBig,residueIndex);
    	}
    }
    
}


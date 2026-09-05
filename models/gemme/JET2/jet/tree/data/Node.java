package jet.tree.data;

import java.util.*;
import java.io.*;

/** La classe "Node" herite des caracteristiques de la classe SimpleNode. 
 * Un objet "Node" herite aussi des caracteristiques de la classe Leaf, mais 
 * contrairement à celle-ci elle ne stocke les distance avec les sequences non 
 * reliées phylogenetiquement que temporairement jusqu'à la creation du noeud 
 * parent. */

public class Node extends jet.tree.data.SimpleNode implements Serializable
{
    
    /** variable "lambda" permettant d'attribuer un poid 
     * different aux deux noeuds fils dans le calcul des distances du noeud courant 
     * aux sequences non encore reliées phylogenetiquement à l'arbre. */
    private double lambda;
    
    /***/
    /** CONSTRUCTEURS */
    /***/
    
    public Node(jet.tree.data.Leaf left, jet.tree.data.Leaf right, double dIJ){ this(left,right,dIJ,0.5); }

    public Node(jet.tree.data.Leaf left, jet.tree.data.Leaf right, double dIJ, double lambda)
    {
	super(left,right);
	this.lambda=lambda;
	
	/* On initialise les distances du noeud courant aux noeuds fils à la moitié 
	 * de la distance entre les deux noeud fils */
	setLeftVertice(dIJ/2.0); setRightVertice(dIJ/2.0);
	/* goLeft().size()=2 quand on est en train d'agglomerer la racine car il reste la 
	 * distance entre les deux noeuds a agglomere et la distance (i,i). Dans ce cas la 
	 * distance entre la racine et chacun des deux noeuds fils est egale (ligne d'au 
	 * dessus) */
	if(goLeft().size()>2) initVertices();

	initDistances();
	
	/* Plus besoin des distances des deux fils vers les sequences non reliees 
	 * ==> elles sont retirées. */
	goLeft().unload(); goRight().unload();
	//System.out.println(getLeftVertice()+" , "+getRightVertice());
    }
    
    /** Methode evaluant la distance moyenne entre chaque sous arbre du noeud courant 
     * avec les sequences non encore introduite dans l'arbre phylogenetique. Ceci permet 
     * d'ajuster la position du noeud courant par rapport à ses deux sous arbres. */
    
    private void initVertices()
    {
    /* Moins 2.0 car dans les distance il y a la distance (i,i) et 
   	 * celle entre les deux noeud que l'on agglomere */
	double dif=((goLeft().getSum()-goRight().getSum())/((double)goLeft().size()-2.0))/2.0;
	/* diff permet de placer le noeud courant entre ses deux sous arbres. Si le sous 
	 * arbre gauche est plus proche des sequences qu'il reste a relier phylogenetiquement 
	 * que le sous arbre droit alors diff est negatif et on place le noeud courant plus 
	 * proche de son sous arbre gauche. */
	setLeftVertice(getLeftVertice()+dif); setRightVertice(getRightVertice()-dif);
    }
    
    /** Methode pour initialiser les distances du noeud courant aux sequences 
     * non encore reliées phylogenetiquement à l'arbre. Ces distances sont évaluées 
     * à partir des distances des deux fils avec ces memes sequences. La variable 
     * "Lamda" est utilisée ici pour attribuer un poid différents aux deux noeuds 
     * fils pour ce calcul. */
    
    private void initDistances()
    {
	int k; double dk; Vector nd=new Vector(1,1);
	
	for(k=0;k<goLeft().size();k++)
	    {
		/* k n'est pas encore dans l'arbre ceci permet de calculer la distance 
		 * du noeud courant au noeud k en attribuant un poid lambda à chacun 
		 * des deux sous arbres*/
		dk=lambda*goLeft().getDistance(k)+(1.0-lambda)*goRight().getDistance(k)-lambda*getLeftVertice()-(1.0-lambda)*getRightVertice();
		nd.add(new Double(dk));
	    }
	/* distance à lui meme */
	nd.add(new Double(0.0));
	/* Ajout du vecteur de distance au noeud courant */
	addAll(nd);
    }
    
}

package jet.tree.tools;
import java.util.*;

/** La classe fournit les methodes pour la construction d'un arbre phylogenetique "tree"
 * en utilisant une matrice des distances "dm" entre les sequences à reliéer phylogenetiquement.
 * L'arbre "tree" est constitué d'un noeud racine "Node". Les objets "Node" ont chacun deux fils. 
 * Les feuilles de l'arbre sont des objets "Leaf". Lorsque l'arbre est construit il ne reste 
 * que la racine de l'arbre dans la matrice dm. */


public class NJ
{
    /** Matrice des distance entre les sequences. Une fois l'arbre construit cette matrice est vide */
    private jet.tree.tools.DistanceMatrix dm;
    /** Arbres des relations phylogenetiques entre les sequences. "Tree" contient la racine de l'arbre */
    private jet.tree.data.Node tree;

    /***/
    /** CONSTRUCTEURS */
    /***/
    
    public NJ(jet.data.datatype.MultiAlignment multiAlignment, jet.tree.tools.SubstitutionMatrix sm,jet.data.datatype.Sequence ref) throws Exception
    {
	initialize(multiAlignment,sm,ref);
	//System.out.println("/* Impression de la matrice des distances */ ");
	//System.out.println(""+this.getDistanceMatrix().toString());
	/* Tant que tous les noeuds n'ont pas ete agglomérés */
	while(getDistanceMatrix().size()!=1) 
	    { 
		//System.out.println(dm.toString()); 
		getME();  
	    }
	/* L'arbre est representé par sa racine */
	this.tree=(jet.tree.data.Node)getDistanceMatrix().get(0);
	/* Calcul de la racine de l'arbre ? */
	rootTree();
    }
    
    /***/
    /** ACCESSEURS */
    /***/
    
    public jet.tree.tools.DistanceMatrix getDistanceMatrix() { return dm; }   
    public jet.tree.data.Node getTree(){return this.tree;}
    
    /** Initialise la matrice des distances entre les sequences prises 2 à 2. */
    
    protected void initialize(jet.data.datatype.MultiAlignment multiAlignment, jet.tree.tools.SubstitutionMatrix sm,jet.data.datatype.Sequence ref) throws Exception
    {
	this.dm=new jet.tree.tools.DistanceMatrix(multiAlignment, sm,ref);
    }

    /** Calcule la racine de l'arbre comme le noeud median 
     * entre les deux feuilles les plus eloignées. */
    
    public void rootTree()
    {
	jet.tree.tools.Tree treeTool=new jet.tree.tools.Tree();
	jet.tree.data.Leaf leaf1=treeTool.getLeaf(getTree(),(jet.data.datatype.Sequence)dm.getDistantCouple().get(0));
	jet.tree.data.Leaf leaf2=treeTool.getLeaf(getTree(),(jet.data.datatype.Sequence)dm.getDistantCouple().get(1));
	treeTool.rootMidTree(getTree(),leaf1,leaf2);
    }
    
    /** Calcul des deux sequences les moins distantes et 
     * agglomeration de ces deux sequences. 
     * Verifier pourquoi le deuxieme filtre */
    
    private void getME()
    {
	int i,j,memI=0,memJ=0;
	double min=10000,dist,dIJ=0.0;
	jet.tree.data.Leaf leafI, leafJ;
	
	for(i=0;i<dm.size();i++)
	    {
		leafI=dm.getLeaf(i);
		for(j=i+1;j<dm.size();j++)
		    {
			leafJ = dm.getLeaf(j);
			dist=leafI.getDistance(j);
			/* dIJ est normalisé sur les autres distances ==> 
			 * negatif si en moyenne inferieur aux autres distances.
			 * Verifier si c'est bien le NJ ou autre chose. */
			dIJ=((double)dm.size()-2.0)*dist;
			dIJ-=leafI.getSum()+leafJ.getSum();
			/* On memorise le couple le moins distant */
			if(min>dIJ) { min=dIJ; memI=i; memJ=j;}
			/* Verifier pourquoi ce deuxieme filtre */
			else if((dist<dm.getLeaf(memI).getDistance(memJ))&&(Math.abs(min-dIJ)<0.0000001)){min=dIJ; memI=i; memJ=j;}
		    }		
	    }
	
	if(memI!=memJ) aglomerate(memI,memJ);

    }
    
    /** Idem fonction aglomerate(int i, int j,double lambda) sans le baricentre lamda */
    
    protected void aglomerate(int i, int j) {aglomerate(i,j,0.5);}
    
    /** Calcule un nouveau noeud node qui est le noeud parent (consensus) des feuilles i et j. 
     * La matrice des distance est actualisée en retirant les feuilles i et j, en ajoutant le 
     * nouveau noeud ainsi que les distances qui lui sont associées dans chaque feuille de la 
     * matrice des distances "dm". Lamda doit representer le baricentre de l'agglomeration. */
    
    protected void aglomerate(int i, int j,double lambda)
    {
	int k;
	/* Nouveau noeud */
	jet.tree.data.Node node;
	jet.tree.data.Leaf leaf;
	
	double distance=dm.getLeaf(i).getDistance(j);
	/* Creation du noeud consensus a partir des deux feuilles i et j */
	node=new jet.tree.data.Node(dm.getLeaf(i), dm.getLeaf(j),distance,lambda);
	/* Retrait des distances concernant les feuilles i et j */
	node.removeDistance(i); node.removeDistance(j-1);
	/* Retrait des feuilles i et j */
	dm.remove(i); dm.remove(j-1);
	/* Ajout des distances concernant le nouveau noeud dans les autres feuilles */
	for(k=0;k<dm.size();k++)
	    {
		leaf=dm.getLeaf(k);
		leaf.removeDistance(i); leaf.removeDistance(j-1);
		leaf.addDistance(node.getDistance(k));
	    }
	/* Ajout du nouveau noeud */
	dm.add(node);

    }

    public String toString(){ return getTree().toString(); }
}

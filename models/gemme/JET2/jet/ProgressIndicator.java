package jet;

import java.util.*;

/** Classe permettant de visualiser la progression d'un processus. 
 * Si cette progression est evaluable, un pourcentage est affiché, 
 * et la progression est actualisée par un appel exterieur à la 
 * fonction setProgress(Double) sinon une barre de progression 
 * avance de gauche à droite sans actualisation extérieure. */

public class ProgressIndicator implements Runnable
{

	/***/
    /** VARIABLE D'INSTANCES */
	/***/
	
	/** Le type d'action dont on mesure la progression (Ex: Blast) */
    String action="";
    /** Etape de la progression (Ex: Connecting to server) */
    String 	status="";
    /** Processus actif ou non */
    boolean alive;
    /** Temps d'attente avant reaffichage de la progression */
    int timer;
    /** Sens de progression de la barre de progression */
    int move=1;
    /** Position de la progression entre 0 et 12 */
    int pos=0;
    /** Niveau de la progression (entre 0.0 et 1.0) */
    double progress=0.0;
    /** voir ou non le pourcentage de progression */
    boolean viewProgress=true;
    
    /***/
    /** CONSTRUCTEURS */
	/***/
    
    public ProgressIndicator(String action, int timer)
    {
	this.alive=true;
	this.action=action;
	this.timer=timer;
    }
   
    public ProgressIndicator(String action)
    {
	this(action, false);
    }

    public ProgressIndicator(String action, boolean viewProgress)
    {
	this(action, 150);
	this.viewProgress=viewProgress;
    }
    
    /***/
    /** MODIFICATEURS */
    /***/
    
    public void setAction(String action){this.action=action;}
    public void setStatus(String status){this.status=status;}
    public void setProgress(double progress)
    {
	this.progress=progress;
	if(progress>1.00001) this.progress=1.0;
	if(progress<0.00001) this.progress=0.0;
    }

    /***/  
    /** ACCESSEURS */
    /***/
    
    public double getProgress(){ return progress; }
    public String getAction(){ return action; }
    public String getStatus(){ return status; }
    public boolean progressViewable(){ return viewProgress;}
    public boolean isAlive(){ return alive;}

    /***/  
    /** METHODES */
    /***/
    
    /** Lancée lorsque la fonction start() est appelé sur l'objet courant.
     * Cette fonction imprime la progression tous les "timer" millisecondes
     *  tant que le processus est actif. */
    
    public void run()
    {
	try
	    {
		while(isAlive())
		    {
			Thread.sleep(timer);
			printProgress();
		    }
	               
	    }
	catch(Exception e){}
	System.err.print("\r"+getAction()+"\t[|||||||||||||||] done...                 \n");      
    }
    
    /** Cette fonction imprime la progression */

    public void printProgress()
    {
	String t1="    ",t2="||||||||||||";

	if(progressViewable())
	    {
		/* La progression est visualisable, on place la position 
		 * pos en fonction du pourcentage de la progression */
		pos=(int)(getProgress()*t2.length());
	    }
	else
	    {
		/* Pas de pourcentage de progression, la barre de progression 
		 * va de gauche a droite, si elle a depassé l'une des 2 bornes,
		 * il faut la faire repartir dans l'autre sens. */
		if(!(pos<t2.length())) { pos=t2.length()-2; move=-1;}
		if(pos<0) {pos=1; move=1;}
	    }
	/* Affichage des barres de progression */
	System.err.print("\r"+getAction()+"\t");
	System.err.print("["+t2.substring(0,pos)+t1+t2.substring(pos+1)+"] ");
	
	if(progressViewable())
	    {
		/* La progression est visualisable, on affiche 
		 * le pourcentage de la progression (l'etat d'
		 * avancement du processus etudié est evaluable 
		 * et est actualisé par l'appel à la fonction 
		 * setProgress())*/
		System.err.print((int)(getProgress()*100.0)+"%  "+getStatus()+"            ");
	    }
	else
	    {
		/* La progression n'est pas visualisable, on n'affiche pas de
		 *  pourcentage mais la progression avance a gauche ou a droite 
		 *  (pas de moyen de connaitre l'avancement du processus étudié)*/ 
		System.err.print(getStatus()+"            ");
		pos+=move;
	    }

    }

    /** Stoppe le processus */
    
    public void stop()
    {
	alive=false;
    }
 
}

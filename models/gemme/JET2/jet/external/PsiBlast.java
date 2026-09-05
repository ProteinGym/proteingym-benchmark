package jet.external;

import jet.JET;

public class PsiBlast extends jet.external.Command 
{
	/** Classe permettant d'executer une commande psi-blast */	
	
	public PsiBlast(String command, String database, String matrix, String queryFile, String outputFile, int maxResults, double eValue, double psithreshold, int gap_exist,int gap_ext,int maxIteration, String methods)
	{
	    command=command+" -db "+database;
	    command=command+" -show_gis ";
	    command=command+" -evalue "+eValue;
	    command=command+" -gapopen "+gap_exist;
	    command=command+" -gapextend "+gap_ext;
	    command=command+" -num_descriptions "+maxResults;
	    command=command+" -num_alignments "+maxResults;
	    command=command+" -matrix "+matrix;
	    command=command+" -query "+queryFile;
	    command=command+" -inclusion_ethresh "+psithreshold;
	    command=command+" -num_iterations "+maxIteration;
	    command=command+" -out "+outputFile;
	    command=command+" -comp_based_stats "+methods;
	    if (JET.DEBUG) System.out.println(command);
	    setCommand(command);
	    setDirectory(".");
	    sendCommand();
	}
	
	
}

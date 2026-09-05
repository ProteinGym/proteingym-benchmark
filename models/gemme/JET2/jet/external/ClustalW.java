package jet.external;


/** Classe permettant d'executer une commande clustalW */

public class ClustalW extends jet.external.Command 
{
    public ClustalW(String command, String filename)
    {
	super(command+" -infile="+filename+" /align",".");
	sendCommand();
    }

}


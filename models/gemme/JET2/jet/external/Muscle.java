package jet.external;


/** Classe permettant d'executer une commande clustalW */

public class Muscle extends jet.external.Command 
{
    public Muscle(String command, String filename)
    {
	super(command+" -in "+filename+" -clw -out "+filename.split("\\.")[0]+".aln",".");
	sendCommand();
    }

}


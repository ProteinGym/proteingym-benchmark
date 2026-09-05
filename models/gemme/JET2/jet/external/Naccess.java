package jet.external;

import jet.JET;

/** Classe permettant d'executer une commande Naccess */

public class Naccess extends jet.external.Command{

	public Naccess(String command, String filename,String dir)
    {
		super(command+" "+filename, dir);
		if (JET.DEBUG) System.out.println(command+" "+filename+"| Dir: "+dir);
		sendCommand();
	}
	
}

package jet.data.datatype;
import java.util.*;

public class PairwiseAlignment extends Vector
{
    jet.data.datatype.Sequence ref;
    
    public jet.data.datatype.Sequence getRefSequence() { return ref; }
    public void setRefSequence(jet.data.datatype.Sequence ref) {this.ref=ref;}
}


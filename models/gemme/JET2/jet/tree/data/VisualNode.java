package jet.tree.data;

import java.awt.geom.*;
import java.awt.*;
import java.awt.image.*;
import java.util.*;

public class VisualNode extends jet.tree.data.VisualLeaf
{
    private jet.tree.data.VisualLeaf left,right;
    private double balance=0.0;
    
    public VisualNode(jet.tree.data.SimpleNode node, jet.tree.data.VisualLeaf left, jet.tree.data.VisualLeaf right)
    {
	super(node); this.left=left; this.right=right;
    }
    
    public jet.tree.data.VisualLeaf goLeft() { return left; }
    public jet.tree.data.VisualLeaf goRight() { return right; }
    
    public double getLeftSpan(){ return goLeft().getLeftSpan()+getSpan()/2.0; }
    public double getRightSpan() { return goRight().getRightSpan()+getSpan()/2.0;}

    public double getSpan() { return goLeft().getRightSpan()+goRight().getLeftSpan(); }
    
    public Vector getChild()
    {
	Vector v=new Vector(goLeft().getChild());
	v.addAll( goRight().getChild());
	return v;
    }
    
    public void setVX(double x)
    {
	super.setVX(x);
	goLeft().setVX(getVX()-(getSpan()/2.0));
	goRight().setVX(getVX()+(getSpan()/2.0));
	
    }

    public void setVY(double y)
    {
	super.setVY(y);
	goLeft().setVY(getVY()+(75.0*((jet.tree.data.SimpleNode)getLeaf()).getLeftVertice())); 
	goRight().setVY(getVY()+(75.0*((jet.tree.data.SimpleNode)getLeaf()).getRightVertice()));
    }
    
public double getVMaxX() { return Math.max(goLeft().getVMaxX(),goRight().getVMaxX()); }
public double getVMinX() { return Math.min(goLeft().getVMinX(),goRight().getVMinX()); }
public double getVMaxY() { return Math.max(goLeft().getVMaxY(),goRight().getVMaxY()); }

public void updateTree()
{
super.updateTree();
goLeft().updateTree(); goRight().updateTree();
}

public void render(Graphics2D g2)
{

    Line2D.Double hor=new Line2D.Double(); hor.setLine(goLeft().getVX(), getVY(), goRight().getVX(), getVY());
    Line2D.Double v1=new Line2D.Double(); v1.setLine(goLeft().getVX(),goLeft().getVY(), goLeft().getVX(), getVY());
    Line2D.Double v2=new Line2D.Double(); v2.setLine(goRight().getVX(),goRight().getVY(), goRight().getVX(), getVY());

    g2.setColor(Color.black);
    g2.setStroke(new BasicStroke(0.01f));
    g2.draw(hor); g2.draw(v1); g2.draw(v2);

    g2.setColor(Color.magenta);
    if(isSelected()) g2.setColor(Color.blue);

    g2.fill(this);
    goLeft().render(g2); goRight().render(g2);
}
}

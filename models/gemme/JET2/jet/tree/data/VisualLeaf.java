package jet.tree.data;

import java.util.*;
import java.awt.geom.*;
import java.awt.*;
import java.awt.image.*;

public class VisualLeaf extends Rectangle2D.Double
{
private jet.tree.data.Leaf leaf;
private double x,y;
private static double w=1.0, h=1.0;
private boolean selected=false;

public VisualLeaf(jet.tree.data.Leaf leaf) { this.leaf=leaf; }

public String toString(){return leaf.toString();}

    public void setScale(double scaleX, double scaleY) 
    { 
	this.w=(scaleY*h)/scaleX; updateTree();
    }
    
    public jet.tree.data.Leaf getLeaf() { return leaf;}
    
    public Vector getChild()
    {
	Vector v=new Vector(1,1); v.add(this); return v;
    }
    
    public double getVX() { return x;}
    public double getVY() { return y;}

    public void setVX(double x){ this.x=x;}
    public void setVY(double y){ this.y=y;}
    
    public double getLeftSpan() { return w*1.5; }
    public double getRightSpan() { return w*1.5; }
    
    public double getSpan() { return getLeftSpan()+getRightSpan(); }

    public void updateTree() 
    { 
	setRect( (getVX()-(w/2.0)),(getVY()-(h/2.0)),w,h); 
    }

    public double getVMaxX() { return getVX(); }
    public double getVMinX() { return getVX(); }
    public double getVMaxY() { return getVY(); }
    
    public void select() { if(isSelected()) selected=false; else selected=true; }
    public boolean isSelected() { return selected; }
    
    public void render(Graphics2D g2)
    {
	
	g2.setColor(Color.black);
	g2.setStroke(new BasicStroke(0.01f));
	
	Font font=new Font("",Font.PLAIN,(int)1.0);
	
	g2.setFont(font.deriveFont((float)w));

	g2.translate((float)getVX(),(float)getVY()+2.0f);
	g2.rotate((Math.PI/2.0));
	g2.drawString(toString(),0.0f,0.0f);
	g2.rotate(-(Math.PI/2.0));
	g2.translate(-(float)getVX(),-((float)getVY()+2.0f));

	g2.setColor(Color.yellow);
	if(isSelected()) g2.setColor(Color.green);
	g2.fill(this);
    }
    
    public boolean equals(Object o)
    {
	if(o instanceof jet.tree.data.VisualLeaf) 
	    { 
		if(leaf.equals(((jet.tree.data.VisualLeaf)o).getLeaf())) 
		    return true; 
	    }
	return false;
    }
}

package jet.rasmol;

import java.util.*;
import javax.vecmath.*;
import javax.swing.*;
import java.util.*;
import java.awt.*;

public class SequenceAppearance
{
    private Vector colors,selected, transparent, visible;
    private jet.data.datatype.Sequence3D sequence;
    private int length;
    
    public SequenceAppearance(jet.data.datatype.Sequence3D sequence)
    {
	this.length=sequence.size();
	this.sequence=sequence;
	
	colors=new Vector(length);
	colors.ensureCapacity(length); 
	colors.setSize(length);
    
	selected=new Vector(length);
	selected.ensureCapacity(length); 
	selected.setSize(length);
	
	transparent=new Vector(length);
	transparent.ensureCapacity(length); 
	transparent.setSize(length);
	
	visible=new Vector(length);
	visible.ensureCapacity(length); 
	visible.setSize(length);
	
	
	
	for(int i=0;i<length;i++)
	    {
		setSelected(i,true);
		setVisible(i,true);
		setTransparent(i,false);
		setColor(i,new Color3f(0.0f,0.0f,0.7f));
	    }
    }
    
    public SequenceAppearance(jet.data.datatype.Sequence3D sequence, jet.rasmol.Property property)
    {
	this(sequence);
	setProperty(property);
    }
    
    public void setProperty(Property property)
    {
	jet.data.datatype.Residue3D residue;
	
	for(int i=0;i<sequence.getSequenceLength(); i++)
	    {
		residue=sequence.getResidue(i,jet.data.datatype.Sequence3D.DIRECT);
		setColor(i,property.getPropertyColor(i));
	    }
    }
    
    public void setColor(int index, Color3f color) 
    { 
	colors.set(index,color); 
    }
    
    public Color3f getColor(int index)
    {
	//if(property==null) return defaultColor;
	if(isSelected(index)) return (Color3f)colors.get(index);
	else return new Color3f(0.6f,0.6f,0.6f);
    }

    public void selectAll()
    {
	for(int i=0;i<selected.size();i++) setSelected(i,true);
    }
    
    public void unSelectAll()
    {
	for(int i=0;i<selected.size();i++) setSelected(i,false);
    }
    
   
    public boolean isSelected(int index) 
    { 
	return ((Boolean)selected.get(index)).booleanValue(); 
    }
    
    public void setSelected(int index, boolean b) 
    { 
	selected.set(index,new Boolean(b)); 
    }
    
    public void select(Vector selection)
    {
	Iterator iter;
	
	if((selection!=null)&&(selection.size()>0))
	    {	
		unSelectAll();
		iter=selection.iterator();
		while(iter.hasNext()) setSelected(((Integer)iter.next()).intValue(),true);
	    }
	
	else selectAll();
    }
   
    public boolean isVisible(int index) 
    { 
	return ((Boolean)visible.get(index)).booleanValue(); 
    }
    
    public void setVisible(int index, boolean b) 
    { 
	visible.set(index,new Boolean(b)); 
    }
    
    public boolean isTransparent(int index)
    {
	if(isSelected(index)) return false;
	else return true;
    }
    
    public void setTransparent(int index, boolean b)
    { 
	transparent.set(index,(Object)new Boolean(b)); 
    }
    
}

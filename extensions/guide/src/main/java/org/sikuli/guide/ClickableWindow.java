package org.sikuli.guide;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JWindow;
import javax.swing.Timer;

import org.sikuli.script.Debug;
import org.sikuli.script.Env;
import org.sikuli.script.Location;
import org.sikuli.script.Region;
import org.sikuli.script.TransparentWindow;



// TODO: Automatically move mouse cursor to the click target. The current implementation
// is problematic for non-rectangular clickable widgets, for instance, a round buttone. 
// Since the highlighted region is always rectangular and is larger than the area that
// is actually cliable, users may click on the edge of the region and dismiss the window
// errorneously. This needs to be fixed.
interface GlobalMouseMotionListener {
   public void globalMouseMoved(int x, int y);         
}

public class ClickableWindow extends JWindow 
implements MouseListener, SingletonInteractionTarget, GlobalMouseMotionListener {



   class GlobalMouseLocationTracker {

      Location lastLocation = null;

      ArrayList<GlobalMouseMotionListener> listeners 
      = new ArrayList<GlobalMouseMotionListener>();

      public void addListener(GlobalMouseMotionListener listener){
         listeners.add(listener);
      }

      Timer timer;
      public GlobalMouseLocationTracker(){

             timer = new Timer(10, new ActionListener(){

            @Override
            public void actionPerformed(ActionEvent arg) {

               Location newLocation = Env.getMouseLocation();
               //Debug.info("Mouse loction: " + newLocation);               


               if (lastLocation != null){

                  if (lastLocation.x != newLocation.x ||
                        lastLocation.y != newLocation.y){

                     for (GlobalMouseMotionListener listener : listeners){
                        listener.globalMouseMoved(newLocation.x,newLocation.y);
                     }
                  }
               }               
               
               lastLocation = newLocation;
            }
         });
         timer.start();
      }
      
      void start(){
         timer.start();
      }
      
      void stop(){
         Debug.info("stopping global mouse tracker");
         timer.stop();
      }
   }

   
   ArrayList<Clickable> clickables = new ArrayList<Clickable>();
   class Clickable extends JComponent {
      
         Color normalColor = new Color(1.0f,1.0f,0,0.1f);
         Color mouseOverColor = new Color(1.0f,0,0,0.1f);

         Region region;
         public Clickable(Region region){
            this.region = region;
            this.setBounds(region.getRect());
            this.setLocation(region.x,region.y);
            
            Debug.info("bounds: " + getBounds());
         }

         
         boolean mouseOver;
         public void setMouseOver(boolean mouseOver){
            if (this.mouseOver != mouseOver){
               repaint();
            }            
            this.mouseOver = mouseOver;
            
         }
         
         public void paintComponent(Graphics g){
            super.paintComponent(g);
            
            Graphics2D g2d = (Graphics2D) g;
            
            if (mouseOver){
               g2d.setColor(mouseOverColor);
            }else{
               g2d.setColor(normalColor);
            }
            
            g2d.fillRect(0,0,getWidth()-1,getHeight()-1);
            g2d.setColor(Color.white);
            g2d.drawRect(0,0,getWidth()-1,getHeight()-1);

         }
   }

   Point clickLocation;
   public Point getClickLocation() {
      return clickLocation;
   }

   SikuliGuide guide;
   GlobalMouseLocationTracker mouseTracker;
   public ClickableWindow(SikuliGuide guide){
      this.guide = guide;
      
      // this allows us to layout the components ourselves
      setLayout(null);

      setBounds(guide.getBounds());
      
      setAlwaysOnTop(true);


      mouseTracker = new GlobalMouseLocationTracker();
      mouseTracker.addListener(this);

//            JLabel label = new JLabel("<html><font size=10>This is a clickable window</font></html>");
//            //label.setLocation(20,20);
//            //label.setSize(label.getPreferredSize());
//            label.setOpaque(true);
//            label.setBackground(Color.white);
//            add(label);

      Container panel = this.getContentPane();
      panel.setBackground(null);
      setBackground(null);
      

      // This makes the JWindow transparent
      Env.getOSUtil().setWindowOpaque(this, false);
      
      // TODO: figure out how to make this JWindow non-draggable
      // 1) associate this window with a JFrame and make the JFrame not movable
//      setFocusableWindowState(false);
//      setFocusable(false);
//      setEnabled(false);      
//      getContentPane().setFocusable(false);
      getRootPane().setFocusable(false);
      
      // capture the click event
      addMouseListener(this);

      addWindowListener(new WindowAdapter(){
         public void windowClosed(WindowEvent e){
            // stop the global mouse tracker's timer thread
            mouseTracker.stop();
         }
      });
      
   }
   
   public void addClickableRegion(Region region){
      Clickable c = new  Clickable(region);
      clickables.add(c);
      add(c);
   }

   @Override
   // notifies the owner of this click target that the target has
   // been clicked
   public void mouseClicked(MouseEvent e) {
      Debug.log("clicked on " + e.getX() + "," + e.getY());
      clickLocation = e.getPoint();

      synchronized(guide){
         guide.notify();
         // ((SikuliGuide) owner).setLastClickedTarget(this);
      }
   }

   @Override
   public void mouseEntered(MouseEvent arg0) {
   }

   @Override
   public void mouseExited(MouseEvent arg0) {
   }

   @Override
   public void mousePressed(MouseEvent arg0) {
   }

   @Override
   public void mouseReleased(MouseEvent arg0) {
   }

   @Override
   public String waitUserAction() {
      
      toFront();
      setVisible(true);
      setAlwaysOnTop(true);
      
      mouseTracker.start();      
      
      synchronized(guide){
         try {
            guide.wait();
         } catch (InterruptedException e) {
            e.printStackTrace();
         }
      }
      //dispose();
      setVisible(false);
      return SikuliGuideDialog.NEXT;
   }
   
   Cursor handCursor = new Cursor(Cursor.HAND_CURSOR);
   Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);
   Cursor hourglassCursor = new Cursor(Cursor.WAIT_CURSOR);
   Cursor currentCursor = null;

   @Override
   public void globalMouseMoved(int x, int y) {
      //Debug.log("moved to " + x + "," + y);
      
      Point p = new Point(x,y);
      for (Clickable c : clickables){
         
         c.setMouseOver(c.getBounds().contains(p));
         
         
         // TODO: figure out why setCursor is not working
//         if (c.getBounds().contains(p)){
//            Debug.log("inside");
//            cursor = handCursor;
//            this.getContentPane().setCursor(hourglassCursor);
//            setCursor(hourglassCursor);
//            getOwner().setCursor(hourglassCursor);
//            c.setCursor(hourglassCursor);
//         }
         
         
      }
            


   }
   
      

   public void clear() {
      clickables.clear();
      getContentPane().removeAll();      
   }

}
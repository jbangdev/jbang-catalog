
//DEPS org.openjfx:javafx-controls:11.0.2:${os.detected.jfxname}
//DEPS org.openjfx:javafx-graphics:11.0.2:${os.detected.jfxname}

//JAVA 11+

//FILES images/jbang-icon.png

import java.io.IOException;

import javafx.animation.AnimationTimer;
import javafx.animation.ScaleTransition;
import javafx.application.Application;
import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.effect.Blend;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.ColorInput;
import javafx.scene.effect.Effect;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration; 

/**
References:
https://www.tutorialspoint.com/javafx/javafx_images.htm
https://www.tutorialspoint.com/javafx/javafx_animations.htm
*/
public class bouncinglogo extends Application {

	private Scene scene;
	private ImageView logo;
	private Image image;
	private double t = 0;
	private AnimationTimer timer;
	private boolean movingUP, movingLeft;

	int xspeed = 3;
	int yspeed = 5;

	Image loadImage(String name) throws IOException {
		return new Image(getClass().getResource(name).openStream());
	}

   @Override 
   public void start(Stage stage) throws IOException {
      //Creating an image
	   image = loadImage("jbang-icon.png");

      //Setting the image view 
      logo = new ImageView(image);
      //Setting the position of the image
      logo.setX(300);
      logo.setY(300);
      
      //setting the fit height and width of the image view 
      // imageView.setFitHeight(455); 
      // imageView.setFitWidth(500); 
	  logo.setFitHeight(200);
      logo.setFitWidth(200);
      
      //Setting the preserve ratio of the image view 
      logo.setPreserveRatio(true);

	   grow();
      
      //Creating a Group object  
      Group root = new Group(logo);
      
      //Creating a scene object 
      scene = new Scene(root, 1200, 800);  
      
      //Setting title to the Stage 
      stage.setTitle("JBang for a better java");
      
      //Adding scene to the stage 
      stage.setScene(scene);
      
      //Displaying the contents of the stage 
      stage.show();
   }
   
   private void updateImage() {

	   ColorAdjust effect = new ColorAdjust();
	   effect.setSaturation(Math.random());
	   effect.setHue(Math.random());
	   effect.setContrast(Math.random());
	   logo.setEffect(effect);
	   logo.setCache(true);
	   logo.setCacheHint(CacheHint.SPEED);
   }
   
   private void grow() {
	   //Creating scale Transition 
      ScaleTransition scaleTransition = new ScaleTransition(); 
	  
	  scaleTransition.setOnFinished(e -> {
		  updateImage();
		  move();
	  });
      
      //Setting the duration for the transition 
      scaleTransition.setDuration(Duration.millis(1500)); 
      
      //Setting the node for the transition 
      scaleTransition.setNode(logo);
      
      //Setting the dimensions for scaling 
      scaleTransition.setByY(0.5);
      scaleTransition.setByX(0.5);
      
      //Setting the cycle count for the translation 
      // scaleTransition.setCycleCount(3); 
      scaleTransition.setCycleCount(1); 
      
      //Setting auto reverse value to true 
      scaleTransition.setAutoReverse(false); 
      
      //Playing the animation 
      scaleTransition.play(); 
   }
   
   private void move() {

		  timer = new AnimationTimer() {
			public void handle(long now) {
				t += 0.02;
				
				if (t > 0.02) { // the lower the fastest
					logo.setY(logo.getY()+yspeed);
					logo.setX(logo.getX()+xspeed);
					checkhitbox();
					t = 0;
				}
			}
		};
		
		timer.start();
	   }
   
   private void checkhitbox() {
		if(logo.getX()+logo.getFitWidth() >= scene.getWidth() || logo.getX()<=0) {
			xspeed *= -1;
			updateImage();
		}

	   if(logo.getY()+logo.getFitHeight() >= scene.getHeight() || logo.getY()<=0) {
		   yspeed *= -1;
		   updateImage();
	   }
   }
   
   public static void main(String args[]) { 
      launch(args); 
   } 
}

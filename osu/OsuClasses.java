/* Written by Mayuwhim
 * July 15, 2022
 * osu! notes and data management
 */

package osu;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;

public class OsuClasses {
	//just the image method
	static BufferedImage loadImage(String filename) {
		BufferedImage img = null;			
		try {
			img = ImageIO.read(new File(filename));
		} catch (IOException e) {
			System.out.println(e.toString());
			JOptionPane.showMessageDialog(null, "An image failed to load: " + filename , "ERROR", JOptionPane.ERROR_MESSAGE);
		}
		//DEBUG
		//if (img == null) System.out.println("null");
		//else System.out.printf("w=%d, h=%d%n",img.getWidth(),img.getHeight());
		
		return img;
	}
}

/* Overarching class consisting of circles and sliders
 * Used when they need to be mixed, such as drawing in chronological order
 */
class HitObject {
	int time = 0; //time of object
	int type = -1; //0 is circle, 1 is slider, -1 is placeholder
	int ID = -1; //ID (chronological), -1 placeholder
	HitObject (int time, int type, int ID) {
		this.time = time;
		this.type = type;
		this.ID = ID;
	}
}

/* The standard hit circle
 * Tap it at its specified time
 */
class HitCircle extends Rectangle {
	int time = 0; //when circle should be hit
	int type = 0;
	int ID = 0; //circle ID
	int state = 4; //4 means not clicked, 0-3 mean clicked and mean miss, 50, 100, and 300 accuracy respectively
	int combo = 0; //visual combo (for player reading)
	int clickedTime = 0; //when circle is actually hit
	HitCircle(int x, int y, int time, int ID, int combo) {
		this.x = x; //x position
		this.y = y; //y position
		width = height = (int)(54.4-4.48*OsuReader.CS)*3; //size of circle, determined by circleSize variable
		this.time = time;
		this.ID = ID;
		this.combo = combo;
	}
}

/* Slider
 * Tap its beginning like a circle, but hold and follow its path until the end
 */
class Slider extends Rectangle {
	int time = 0; //slider start
	int type = 1;
	int ID = 0; //slider ID
	int duration = 0; //duration of slider
	int slides = 0; //repeats (1 is no repeat, 2 is repeat once, etc)
	int tempslides=0; //slides but increments down as the slider is played
	int state = 4;
	int combo = 0;
	boolean finished = false; //ensures accuracy is only calculated once per slider
	int[] touched = new int[2]; //[0] is #frames slider has been followed, [1] is #frames in total, ratio determines accuracy
	int[][] points; //every single point on the slider
	boolean direction = false; //which way it is repeating
	Slider(int[][] points, int time, int duration, int slides, int ID, int combo) {
		this.points = points;
		this.x = points[0][0]; //changes as the slider moves
		this.y = points[0][1];
		width = height = (int)(54.4-4.48*OsuReader.CS)*3;
		this.time = time;
		this.duration = duration;
		this.slides = slides;
		tempslides = slides;
		this.ID = ID;
		this.combo = combo;
	}
}

/* Spinner
 * Hold and spin with the cursor around the center of the screen
 * No x or y because it is a screen-wide feature
 */
class Spinner extends Rectangle {
	int time = 0;
	int type = 2;
	int endTime = 0; //when the spinner ends
	int state = 4;
	int dT = 0; //delta theta for current spin
	int spin = 0; //# of spins done (dT reaches +-360 and resets)
	boolean finished = false;
	Spinner(int time, int endTime) {
		this.time = time;
		this.endTime = endTime;
	}
}

//Mouse cursor
class Cursor extends Rectangle {
	Cursor (int x, int y, int size) {
		this.x = x;
		this.y = y;
		this.width = this.height = size;
	}
}

/* Timing Point
 * Determines velocity of sliders
 */
class TimingPoint {
	int time = 0; //time after which the effects of the point will act
	double beatL = 0; //slider velocity
	TimingPoint(int time, double b) {
		beatL = b;
		this.time = time;
	}
}
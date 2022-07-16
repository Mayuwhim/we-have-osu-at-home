/* Written by Mayuwhim
 * July 15, 2022
 * osu!
 * 
 * How to play:
 * 1. Select a map
 * 2. Click with mouse or tap keys on the notes to the song
 * 3. Sliders must be tapped at the start then followed
 * 4. Spinners must be held and spun around the middle
 * 
 * Tip: Accuracy is quite harsh, but listening to the song helps a lot
 * 
 * Thanks to Dean Herbert (ppy) for making the real game
 * Thanks to the osu! community for providing maps and assets
 * 
 * Notes:
 * - All time-keeping is done is milliseconds unless stated otherwise
 * - The program is really long and complicated, I tried my best to comment things
 * - The planning document may help with understanding why I'm doing what I'm doing
 * - Recommended to read OsuClasses first to get a better sense of how notes work
 * - Some maps have a "break" at the start where there are no notes, just wait a bit
 * - Enjoy!
 */

package osu;

import hsa2.GraphicsConsole;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Scanner;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class OsuReader {
	
	public static void main(String[] args) throws FileNotFoundException {
		new OsuReader();
	}
	
	//input keys, changable
	int key1 = 'Z';
	int key2 = 'X';
	
	//game variables changed in game
	String map = "";
	boolean noFail = false;
	
	//window size, buffer space around playing field
	final int WINX = 1200;
	final int WINY = 900;
	int bufferX = (WINX-960)/2;
	int bufferY = (WINY-720)/2;
	
	//setup
	static double AR, OD, CS, HP, SM; //difficulty settings
	int[]ARtime = new int[2]; //AR --> ms (appear/fade), useful
	
	double[] beatLength = new double[2]; //current beat length and slider speed multiplier
	int beatTime = -1; //for single point slider multipliers, if this matches slider time apply multiplier
	
	long startTime; //map start time in system time
	int endTime; //end time, # seconds after start
	
	//data
	int[] hits = new int[4]; //number of each accuracy (300, 100, etc)
	double acc = 100; //gross accuracy
	int combo = 0; //current combo
	int maxCombo = 0; //maximum combo this run
	int score = 0; //score
	String songName = ""; //name of current song
	
	//mechanics
	int noteCombo = 1; //VISUAL note combo (for player reading)
	
	boolean[] pressed = new boolean[5]; //S,D,Mice,Q - for keys to register only once when held
	int[] clicks = new int[4]; //number of clicks from each input
	
	boolean paused = false; //game pause
	long pauseTime = 0; //WHEN the game was last paused (for music offset)
	
	double health = 50; //HP, if 0 you die
	boolean ded = false; //death
	
	Clip[] clip = new Clip[7]; //audio
	
	GraphicsConsole gc = new GraphicsConsole(WINX,WINY);
	BufferedImage sprites = OsuClasses.loadImage("osuYM/spritesheet.png");
	
	//note lists
	static ArrayList<HitCircle> circleList = new ArrayList<HitCircle>();
	static ArrayList<Slider> sliderList = new ArrayList<Slider>();
	static ArrayList<Spinner> spinnerList = new ArrayList<Spinner>();
	
	//data lists
	static ArrayList<TimingPoint> pointList = new ArrayList<TimingPoint>();
	static ArrayList<int[]> breakList = new ArrayList<int[]>();
	static ArrayList<int[]> accList = new ArrayList<int[]>();
	
	int cursorSize = 32;
	Cursor m1 = new Cursor(-200,-200,16); //cursor
	Cursor m2 = new Cursor(-200,-200,128); //cursor for less strict slider tracking
	
	int cursorTrail = 20; //# of previous locations to draw trail, must be at least 2
	int[][] cursorPos = new int[cursorTrail][2]; //previous locations, used in slider calculation
	
	//fonts
	Font Arial18 = new Font("Arial Rounded MT Bold", Font.PLAIN, 18);
	Font Arial24 = new Font("Arial Rounded MT Bold", Font.PLAIN, 24);
	Font Arial28 = new Font("Arial Rounded MT Bold", Font.PLAIN, 28);
	Font Arial36 = new Font("Arial Rounded MT Bold", Font.PLAIN, 36);
	Font Arial48 = new Font("Arial Rounded MT Bold", Font.PLAIN, 48);
	
	OsuReader() throws FileNotFoundException {
		setup(); //first time setup (gc stuff, load audio)
		gameIntro(); //intro screen
		
		//while loop so you can go back to menu and choose different maps
		while (true) {
			boolean restart = false; //go to menu if true
			drawMenu();
			
			//import current map files
			File beatmap = new File("osuYM/"+map+"/map.osu");
			BufferedImage bg = OsuClasses.loadImage("osuYM/"+map+"/bg.jpg");
			
			//while loop so you can restart individual map
			while (true) {
				//set parameters and load notes
				Scanner sc = new Scanner(beatmap);
				setParameters(sc);
				loadSound("osuYM/"+map+"/song.wav",0);
				
				//start screen
				gc.clear();
				gc.setColor(Color.WHITE);
				gc.setFont(Arial48);
				gc.drawString("Press Q to Begin", WINX*5/14, WINY/2);
				while (!gc.isKeyDown('Q')) {
					gc.sleep(1);
				}
				
				playSound(2);
				startTime = System.currentTimeMillis()+2000;
				boolean songPlayed = false;
				
				//game loop
				while (true) {
					//start song ONCE
					if (System.currentTimeMillis() >= startTime-50 && !songPlayed) {
						clip[0].start();
						songPlayed = true;
					}
					
					//main graphics and mechanics
					if(!paused) {
						synchronized (gc) {
							gc.clear();
							drawGraphics(bg);
							checkNotes();
							checkMouse();
							drawGui();
							if (circleList.size()+sliderList.size()+spinnerList.size()==0) break; //leave loop when all notes have been played
						}
					}
					
					//pausing and failing detection and screens, you can also restart/go to menu
					checkPause(songPlayed);
					if (paused && (gc.isKeyDown('R') || gc.isKeyDown('T'))) break;
					if (checkFail()) break;
					
					gc.sleep(1);
				}
				
				//endscreen
				if (!paused && !ded) {
					playSound(5);
					drawEndscreen(bg);
				}
				clip[0].close();
				
				//detect restart/menu
				while(!gc.isKeyDown('R') && !gc.isKeyDown('T') && !paused) {
					gc.sleep(1);
				}
				clip[1].stop();
				clip[5].stop();
				playSound(2);
				if (gc.isKeyDown('T')) restart = true;
				resetGlobals(); //reset all variables
				if (restart) break; //go to menu loop
			}
		}
	}
	
	void setup() {
		gc.setTitle("osu at home");
		gc.setAntiAlias(true);
		gc.setStroke(1);
		gc.setLocationRelativeTo(null);
		gc.enableMouse();
		gc.enableMouseMotion();
		gc.enableMouseWheel();
		gc.setBackgroundColor(Color.BLACK);
		loadSound("osuYM/epicfail.wav",1); //fail sound
		loadSound("osuYM/click.wav",2); //menu sound
		loadSound("osuYM/click2.wav",3); //menu scroll sound
		loadSound("osuYM/hitsound.wav",4); //note hit sound
		loadSound("osuYM/endscreen.wav",5); //endscreen jingle
		loadSound("osuYM/intro.wav",6); //intro screen music
	}
	
	void gameIntro() {
		//setup
		BufferedImage icon = OsuClasses.loadImage("osuYM/logo.png");
		long startTime = System.currentTimeMillis();
		playSound(6);
		gc.setStroke(45);
		int ovalSize = 675;
		
		//loading up animation
		while(System.currentTimeMillis()<startTime+2500) {
			synchronized (gc) {
				int i = (int)(System.currentTimeMillis()-startTime);
				gc.clear();
				gc.setColor(new Color(255,128,128,i*255/2500));
				gc.fillOval((WINX-ovalSize)/2, (WINY-ovalSize)/2, ovalSize, ovalSize);
				gc.setColor(Color.WHITE);
				gc.drawArc((WINX-ovalSize)/2,(WINY-ovalSize)/2, ovalSize, ovalSize,90,i*360/2500);
			}
			gc.sleep(1);
		}
		
		//loop to display "beating logo" and also check for click
		gc.getMouseClick();
		int j = (int)(System.currentTimeMillis()-startTime);
		int beat = 60000/184; //184 is BPM of intro song
		int num = j/beat; //#beats since game started
		int k = 32; //variable that cycles per beat
		while (true) {
			j = (int)(System.currentTimeMillis()-startTime);
			synchronized (gc) {
				gc.clear();
				//if a beat has passed
				if (j/beat>num) {
					num = j/beat; 
					k=-32;
				}
				//graphics
				gc.setColor(new Color(255-Math.abs(k)*4,255-Math.abs(k)*4,255-Math.abs(k)*4,64));
				if (k!=32) k+=4;
				gc.drawImage(icon,(WINX-ovalSize)/2-32+Math.abs(k), (WINY-ovalSize)/2-32+Math.abs(k), ovalSize+(32-Math.abs(k))*2, ovalSize+(32-Math.abs(k))*2);
				gc.fillOval((WINX-ovalSize)/2-32+Math.abs(k), (WINY-ovalSize)/2-32+Math.abs(k), ovalSize+(32-Math.abs(k))*2, ovalSize+(32-Math.abs(k))*2);
				gc.setColor(Color.WHITE);
				gc.drawOval((WINX-ovalSize)/2-32+Math.abs(k), (WINY-ovalSize)/2-32+Math.abs(k), ovalSize+(32-Math.abs(k))*2, ovalSize+(32-Math.abs(k))*2);
				gc.setFont(Arial24);
				gc.drawString("By Mayuwhim | Special thanks to ppy and all the mappers", 5, WINY-10);
			}
			
			//mouse
			if (gc.getMouseClick()>0) {
				if (gc.getMouseX()>(WINX-ovalSize)/2 && gc.getMouseY()>(WINY-ovalSize)/2 && gc.getMouseX()<WINX-(WINX-ovalSize)/2 && gc.getMouseY()<WINY-(WINY-ovalSize)/2) {
					playSound(2);
					break;
				}
			}
			gc.sleep(1);
		}
		
		//fade out
		for (int i=5;i<=128;i+=2) {
			synchronized (gc) {
				gc.setColor(new Color(0,0,0,i));
				gc.fillRect(0, 0, WINX, WINY);
			}
			gc.sleep(1);
		}
	}
	
	void resetGlobals() {
		//reset for a fresh map
		AR = OD = CS = HP = SM = startTime = endTime = combo = maxCombo = score = 0;
		ARtime = new int[2];
		beatLength = new double[2];
		beatTime = -1;
		hits = new int[4];
		acc = 100;
		noteCombo = 1;
		health = 50;
		pressed = new boolean[5];
		clicks = new int[4];
		paused = ded = false;
		pauseTime = 0;
		songName = "";
		
		//arraylists
		for (int i = 0; i<circleList.size(); i++) {
			circleList.remove(i);
			i--;
		}
		for (int i = 0; i<sliderList.size(); i++) {
			sliderList.remove(i);
			i--;
		}
		for (int i = 0; i<spinnerList.size(); i++) {
			spinnerList.remove(i);
			i--;
		}
		for (int i = 0; i<pointList.size(); i++) {
			pointList.remove(i);
			i--;
		}
		for (int i = 0; i<breakList.size(); i++) {
			breakList.remove(i);
			i--;
		}
		for (int i = 0; i<accList.size(); i++) {
			accList.remove(i);
			i--;
		}
		
		m1 = new Cursor(-200,-200,16);
		m2 = new Cursor(-200,-200,128);
		cursorPos = new int[cursorTrail][2];
		
		gc.setFont(Arial18);
	}
	
	void drawMenu() throws FileNotFoundException {
		//setup
		if (!clip[6].isRunning()) playSound(6); //play intro song if you entered menu from map
		noFail = false;
		int pos = 0; //which map is currently selected
		File list = new File("osuYM/list.txt"); //list of maps
		ArrayList<String[]> mapsList = new ArrayList<String[]>();
		Scanner text = new Scanner(list);
		while (text.hasNextLine()) {
			String[] a = text.nextLine().split(",");
			mapsList.add(a);
		}
		boolean[] pressed = new boolean[3]; //local keys
		
		//animation loop
		while (true) {
			synchronized (gc) {
				gc.clear();
				
				//MAPS
				gc.setFont(Arial36);
				for (int i = 0 ; i<mapsList.size(); i++) {
					gc.setColor(Color.WHITE);
					gc.setStroke(1);
					gc.drawRect(100, WINY/2-50+(i-pos)*150, WINX-600, 100);
					gc.drawString(mapsList.get(i)[0],120,WINY/2+(i-pos)*150+10);
				}
				//selection box
				gc.setStroke(5);
				gc.setColor(Color.GREEN);
				gc.drawRect(100,WINY/2-50,WINX-600,100);
				
				//other UI
				gc.setColor(Color.BLACK);
				gc.fillRect(0,0,WINX,WINY/6);
				for (int i = 0; i<WINY/6; i++) {
					gc.setColor(new Color(0,0,0,255-i*255*6/WINY));
					gc.drawLine(0, i+WINY/6, WINX, i+WINY/6); //fade up top
				}
				gc.setColor(Color.WHITE);
				//noFail indicator
				gc.drawRect(WINX-100, WINY-100, 50, 50);
				if (noFail) gc.fillRect(WINX-90, WINY-90, 30, 30);
				//text
				gc.setFont(Arial48);
				gc.drawString("Song Select", 450, 100);
				gc.setFont(Arial24);
				gc.drawString("Arrow keys/scroll to navigate", 805, 700);
				gc.drawString("W to select map", 965, 760);
				gc.drawString("Toggle NoFail (F)", 865, 830);
				
				//key detection
				//UP ARROW 38 DOWN 40
				int p = gc.getMouseWheelRotation(); //mouse wheel
				//scroll up
				if ((gc.isKeyDown(38) && !pressed[0]) || p<0) {
					playSound(3);
					pressed[0] = true;
					if (pos>0) pos--;
				}
				//scroll down
				if ((gc.isKeyDown(40) && !pressed[1]) || p>0) {
					playSound(3);
					pressed[1] = true;
					if (pos<mapsList.size()-1) pos++;
				}
				//toggle nofail
				if (gc.isKeyDown('F') && !pressed[2]) {
					playSound(2);
					pressed[2] = true;
					noFail = !noFail;
				}
				//keypresses
				if (!gc.isKeyDown(38)) pressed[0] = false;
				if (!gc.isKeyDown(40)) pressed[1] = false;
				if (!gc.isKeyDown('F')) pressed[2] = false;
				
				//select map
				if (gc.isKeyDown('W')) {
					map = mapsList.get(pos)[1];
					playSound(2);
					break;
				}
			}
			gc.sleep(1);
		}
		//map has been selected
		clip[6].stop();
		gc.clear();
		gc.setFont(Arial48);
		gc.drawString("Loading...", 500, WINY/2);
	}

	void setParameters(Scanner sc) {
		//determines how far down the file currently
		boolean events = false;
		boolean timingPoints = false;
		boolean hitObjects = false;
		int[] ID = {0,0}; //circle,slider # (chronological)
		
		String text = "";
		while (sc.hasNextLine()) {
			text = sc.nextLine();
			//checks lines for important information
			if (text.contains("Title:")) {
				String[] split = text.split(":");
				songName+= split[1]+" - ";
			}
			if (text.contains("Artist:")) {
				String[] split = text.split(":");
				songName+= split[1];
			}
			if (HP ==0) HP = setDiff("HPDrainRate",text);
			if (CS ==0) CS = setDiff("CircleSize",text);
			if (OD ==0) OD = setDiff("OverallDifficulty",text);
			if (AR ==0) AR = setDiff("ApproachRate",text);
			if (SM ==0) SM = setDiff("SliderMultiplier",text);
			if (text.contains("[Events]")) events = true;
			if (text.contains("[TimingPoints]")) {
				events = false;
				timingPoints = true;
			}
			if (text.contains("[Colours]")) timingPoints = false;
			if (text.contains("[HitObjects]")) hitObjects = true;
			
			//add to data lists
			if (events) {
				if (text.startsWith("2,") || text.startsWith("Break,")) {
					String[] split = text.split(",");
					breakList.add(new int[] {Integer.parseInt(split[1]),Integer.parseInt(split[2])});
				}
			}
			
			if (timingPoints) {
				if (text.contains(",")) {
					String[] split = text.split(",");
					pointList.add(new TimingPoint(Integer.parseInt(split[0]),Double.parseDouble(split[1])));
				}
			}
			
			//add to note lists (check OsuClasses for what I'm adding)
			if (hitObjects && text.contains(",")) {
				String[] split = text.split(",");
				//check if note isn't slider
				if (!text.contains("B") && !text.contains("C") && !text.contains("L") && !text.contains("P")) {
					//check if note is spinner
					if (split.length>5 && !split[5].contains(":")) {
						spinnerList.add(new Spinner(Integer.parseInt(split[2]),Integer.parseInt(split[5])));
						noteCombo = 0;
						//endtime shifts to time of last note
						if (Integer.parseInt(split[5]) > endTime) endTime = Integer.parseInt(split[5]);
					} else addCircle(split, ID);
				} else addSlider(split, ID);
			}
		}
		
		//ARtime calculation
		if (AR<=5) {
			ARtime[0] = (int)(1200+600*(5-AR)/5);
			ARtime[1] = (int)(800+400*(5-AR)/5);
		}
		if (AR>5) {
			ARtime[0] = (int)(1200-750*(AR-5)/5);
			ARtime[1] = (int)(800-500*(AR-5)/5);
		}
		
		//stack mechanic: circles that are on top of each other are shifted a little for readability
		for (int i = 0; i<circleList.size(); i++) {
			int p = 0;
			while (true) {
				if (i+p+1>=circleList.size()) break;
				//if 2 time-adjacent circles are at the same position and not too far apart in time, add to stack
				if (circleList.get(i).x == circleList.get(i+p+1).x && circleList.get(i).y == circleList.get(i+p+1).y && circleList.get(i+p+1).time-circleList.get(i+p).time < ARtime[0]) {
					p++;
				}
				else break;
			}
			//stack
			while (p>0) {
				for (int j = 0; j<p; j++) {
					circleList.get(i+j).x-=5;
					circleList.get(i+j).y-=5;
				}
				p--;
			}
		}
	}
	
	double setDiff(String s, String text) {
		//sets difficulty settings from file
		if (text.contains(s)) {
			String[] split = text.split(":");
			return Double.parseDouble(split[1]);
		}
		return 0;
	}
	
	void addCircle(String[] split, int[] ID) {
		//visual combo
		if (Integer.parseInt(split[3]) == 1) {
			noteCombo++;
		} else {
			noteCombo = 1;
		}
		
		//add circle, endtime, increment ID
		circleList.add(new HitCircle(Integer.parseInt(split[0])*3/2,Integer.parseInt(split[1])*3/2,Integer.parseInt(split[2]),ID[0],noteCombo));
		if (Integer.parseInt(split[2]) > endTime) endTime = Integer.parseInt(split[2]);
		ID[0]++;
	}
	
	void addSlider(String[] split, int[] ID) {
		int[][] points = sliderPoints(split); //a lot of points to pinpoint where the slider is (complicated)
		int time = Integer.parseInt(split[2]); //slider start time
		
		//slider velocity calculation (data from file)
		double SV = 1;
		for (int i = 0; i<pointList.size(); i++) {
			if (pointList.size() == 0) break;
			TimingPoint a = pointList.get(i);
			//if a point's time has arrived, invoke its effects
			if (a.time <= time) {
				if (a.beatL>=0) beatLength[0] = a.beatL;
				if (a.beatL<=0) {
					beatLength[1] = a.beatL;
					if (a.time == time) SV = -100/beatLength[1];
				}
				pointList.remove(i);
				i--;
			}
		}
		
		//slider length and duration
		double length = Double.parseDouble(split[7]);
		double duration = length*beatLength[0]/(SM*100*SV);
		
		//note combo
		if (Integer.parseInt(split[3]) == 2) {
			noteCombo++;
		} else {
			noteCombo = 1;
		}
		
		//add, endtime, id
		sliderList.add(new Slider(points,time,(int) Math.round(duration),Integer.parseInt(split[6]),ID[1], noteCombo));
		if (time+duration*Integer.parseInt(split[6]) > endTime) endTime = (int)(time+duration*Integer.parseInt(split[6]));
		ID[1]++;
	}
	
	int[][] sliderPoints(String[] s) {
		//convert file data into positions
		String[] sliderStuff = s[5].split("\\|");
		int[][] points = new int[sliderStuff.length][2];
		points[0][0] = Integer.parseInt(s[0])*3/2;
		points[0][1] = Integer.parseInt(s[1])*3/2;
		for (int i = 1; i<sliderStuff.length; i++) {
			String[] point = sliderStuff[i].split(":");
			points[i][0] = Integer.parseInt(point[0])*3/2;
			points[i][1] = Integer.parseInt(point[1])*3/2;
		}
		
		//turn positions into drawable points
		int[][] positions = sliderCalc(points, (int) Math.round(Double.parseDouble(s[7])));
		
		return positions;
	}
	
	int[][] sliderCalc(int[][] a, int b) {
		//slider file data consists of bezier points, and subslider points
		//subslider points splits sliders into individually bezier'ed sections
		int[][][] sliders = new int[1000][a.length][2]; //[section][#bezier point][x or y]
		for (int i = 0; i<sliders.length; i++) {
			for (int j = 0; j<sliders[i].length; j++) {
				for (int k = 0; k<sliders[i][j].length; k++) {
					sliders[i][j][k] = -6969; //placeholder number because 0 cannot be
				}
			}
		}
		int sliderNum = 0; //subslider
		int sliderLoc = 0; //#bezier point
		//add to array
		for (int i = 0; i<a.length-1; i++) {
			sliders[sliderNum][sliderLoc] = a[i];
			sliderLoc++;
			if (a[i][0] == a[i+1][0] && a[i][1] == a[i+1][1]) {
				sliderNum++;
				sliderLoc = 0;
			}
		}
		sliders[sliderNum][sliderLoc] = a[a.length-1]; //final point
		double[] distance = new double[sliderNum+1]; //distance for each subsection
		double totalDistance = 0; //total distance
		//calculate distance with pythagorean
		for (int i = 0; i<=sliderNum; i++) {
			int[] p1 = sliders[i][0]; //first point
			int[] p2 = {0,0}; //second point, last point of subslider
			for (int j = 0; j<sliders[i].length; j++) {
				if (sliders[i][j][0] == -6969) break;
				p2 = sliders[i][j];
			}
			distance[i] = (Math.sqrt(Math.pow((p2[0]-p1[0]),2)+Math.pow((p2[1]-p1[1]),2)));
			totalDistance+=distance[i];
		}
		
		//calculate a bunch of points to be drawn
		int[][] points = new int[b][2];
		int k = 0;
		//for each subsection
		for (int i = 0; i<=sliderNum; i++) {
			int numPoints = (int)(distance[i]*b/totalDistance);
			int bezierNum = 0; //degree of bezier curve
			for (int j = 0; j<a.length; j++) {
				if (sliders[i][j][0] !=-6969) bezierNum++;
			}
			for (int j = 0 ; j < numPoints; j++) {
				int[] point = sliderCalc2(sliders[i], numPoints, bezierNum, j);
				points[k] = point;
				k++;
			}
			
		}
		
		return points;
	}
	
	int[] sliderCalc2(int[][]a, int points, int num, int cPoint) {
		int[][] b = new int[num][2];
		double c = (double)cPoint/points; //which point in section (ratio of where it should be drawn)
		int[][] d = new int[num-1][2];
		
		for (int i = 0; i<b.length; i++) {
			b[i] = a[i];
		}
		
		//bezier calculation
		for (int i = 0; i<d.length; i++) {
			d[i][0] = b[i][0] + (int)((b[i+1][0]-b[i][0])*c);
			d[i][1] = b[i][1] + (int)((b[i+1][1]-b[i][1])*c);
		}
		if (num > 2) {
			return sliderCalc2(d, points, num-1, cPoint); //recursive until bezier degree is 0 (real points)
		}
		
		return d[0];
	}

	void drawGraphics(BufferedImage bg) {
		//background
		float bgalpha = checkBreak();
		gc.drawImage(bg,0, 0, WINX, WINY,bgalpha);
		
		//Draw spinners
		for (int i = 0; i<spinnerList.size(); i++) {
			if (spinnerList.size() == 0) break;
			Spinner a = spinnerList.get(i);
			//if on time, draw
			if (a.time+startTime <= System.currentTimeMillis()+ARtime[0]) {
				//change alpha for fade
				int alpha = 0;
				if (a.state == 4) {
					alpha = (int)(192*((System.currentTimeMillis()-a.time-startTime+ARtime[0])/(double)ARtime[1]));
					if (alpha>192) alpha = 192;
				}
				if (a.state != 4) {
					alpha = 192-(int)(192*((System.currentTimeMillis()-a.endTime-startTime)/(double)650));
					if (alpha<0) alpha = 0;
				}
				gc.setColor(new Color(255,255,255,alpha/4));
				gc.fillArc((WINX-720)/2, bufferY, 720, 720, 90, a.spin*360/((a.endTime-a.time)/500)); //slider progress
				gc.setColor(new Color(255,255,255,alpha));
				gc.setStroke(5);
				gc.drawOval((WINX-720)/2, bufferY, 720, 720);
				gc.fillOval((WINX-720)/2+355, bufferY+355, 10, 10);
				
				//draw accuracy feedback after note is over
				if (a.state != 4 && a.finished) {
					gc.drawImage(sprites,WINX/2,WINY/2,WINX/2+a.width,WINY/2+a.height,0+64*a.state,0,64+64*a.state,64,null);
				}
			}
		}
		
		//Circles and sliders: must draw in order so temp arraylist
		ArrayList<HitObject> toDraw = new ArrayList<HitObject>();
		//put only everything THAT IS ON SCREEN RIGHT NOW into list
		for (int i = 0; i<sliderList.size(); i++) {
			if (sliderList.size() == 0) break;
			Slider a = sliderList.get(i);
			if (a.time+startTime <= System.currentTimeMillis()+ARtime[0]) {
				toDraw.add(new HitObject(a.time, a.type, a.ID));
			}
		}
		for (int i = 0; i<circleList.size(); i++) {
			if (circleList.size() == 0) break;
			HitCircle a = circleList.get(i);
			if (a.time+startTime <= System.currentTimeMillis()+ARtime[0]) {
				toDraw.add(new HitObject(a.time, a.type, a.ID));
			}
		}
		
		//draw everything in order
		if (toDraw.size()>0) {
			//keep drawing until all the notes are drawn
			for (int i = 0; i<toDraw.size(); i++) {
				int[] highest = {-1,-1}; //find last note, draw first (so it's on the bottom)
				for (int j = 0; j<toDraw.size(); j++) {
					if (toDraw.get(j).time > highest[0]) {
						highest[0] = toDraw.get(j).time;
						highest[1] = j;
					}
				}
				//if circle
				if (toDraw.get(highest[1]).type == 0) {
					for (int j = 0; j<circleList.size(); j++) {
						if (toDraw.get(highest[1]).ID == circleList.get(j).ID) {
							drawCircle(circleList.get(j));
							break;
						}
					}
				}
				//if slider
				if (toDraw.get(highest[1]).type == 1) {
					for (int j = 0; j<sliderList.size(); j++) {
						if (toDraw.get(highest[1]).ID == sliderList.get(j).ID) {
							drawSlider(sliderList.get(j));
							break;
						}
					}
				}
				toDraw.get(highest[1]).time=-1; //change note time to avoid interfering with later notes
			}
		}
	}
	
	float checkBreak() {
		//calculates bg alpha
		
		float alpha = 0;
		//start of map, bg visible
		if (System.currentTimeMillis() < startTime) {
			alpha = 1f;
			//fade out
			if (System.currentTimeMillis()>=startTime-500) {
				alpha = (float)((double)(startTime-System.currentTimeMillis())/500); //ratio to determine fade
			}
		}
		//show/fade during breaks
		for (int i = 0; i<breakList.size(); i++) {
			int[] a = breakList.get(i);
			if (System.currentTimeMillis() >= startTime+a[0] && System.currentTimeMillis() <= startTime+a[1]) {
				alpha = 1f;
				if (System.currentTimeMillis()>=startTime+a[1]-500) {
					alpha = (float)((double)(startTime+a[1]-System.currentTimeMillis())/500);
				}
				if (System.currentTimeMillis()<=startTime+a[0]+500) {
					alpha = (float)((double)(System.currentTimeMillis()-startTime-a[0])/500);
				}
				break;
			}
		}
		return alpha;
	}
	
	void drawCircle(HitCircle a) {
		//a.state == 4 means circle hasn't been hit yet
		int alpha = 0;
		//fade in
		if (a.state == 4) {
			alpha = (int)(192*((System.currentTimeMillis()-a.time-startTime+ARtime[0])/(double)ARtime[1]));
			if (alpha>192) alpha = 192;
		}
		//fade out effect
		if (a.state != 4) {
			if (a.clickedTime == 0) a.clickedTime = (int)(System.currentTimeMillis()-startTime);
			alpha = 192-(int)(192*((System.currentTimeMillis()-a.clickedTime-startTime)/(double)100));
			if (a.state != 0 && System.currentTimeMillis()<a.clickedTime+startTime+50) {
				a.width+= 2;
				a.height+= 2;
				a.x-=1;
				a.y-=1;
			}
			if (alpha<0) alpha = 0;
		}
		//main circle
		gc.setColor(new Color(0,128,255,alpha));
		gc.setStroke(5);
		gc.fillOval(a.x+bufferX,a.y+bufferY,a.width, a.height);
		gc.setColor(new Color(255,255,255,alpha));
		gc.setStroke(5);
		gc.drawOval(a.x+bufferX,a.y+bufferY,a.width, a.height);
		
		//visual combo
		float alpha2 = alpha/192f;
		if (a.combo<10) {
			gc.drawImage(sprites,a.x+bufferX,a.y+bufferY,a.x+bufferX+a.width,a.y+bufferY+a.height,128*a.combo,64,128+128*a.combo,192,alpha2,null);
		}
		if (a.combo>=10) {
			int ones = a.combo%10;
			int tens = (a.combo%100)/10;
			gc.drawImage(sprites,a.x+bufferX,a.y+bufferY+a.height/8,a.x+bufferX+a.width*3/4,a.y+bufferY+a.height*7/8,128*tens,64,128+128*tens,192,alpha2,null);
			gc.drawImage(sprites,a.x+bufferX+a.width*1/4,a.y+bufferY+a.height/8,a.x+bufferX+a.width,a.y+bufferY+a.height*7/8,128*ones,64,128+128*ones,192,alpha2,null);
		}
		
		//approach circle
		if (a.time+startTime >= System.currentTimeMillis()) {
			gc.setStroke(3);
			gc.setColor(new Color(0,192,255,alpha));
			int b = (int)(54.4-4.48*CS)*3;
			int approachCircle = b-(int)(b*((System.currentTimeMillis()-a.time-startTime+ARtime[0])/(double)ARtime[0]));
			gc.drawOval(a.x+bufferX-approachCircle,a.y+bufferY-approachCircle,approachCircle*2+a.width, approachCircle*2+a.height);
		}
		
		//accuracy feedback
		if (a.state != 4) {
			gc.drawImage(sprites,a.x+bufferX,a.y+bufferY,a.x+bufferX+a.width,a.y+bufferY+a.height,0+64*a.state,0,64+64*a.state,64,null);
		}
	}
	
	void drawSlider (Slider a) {
		int alpha = 0;
		//fade in
		if (a.state == 4) {
			alpha = (int)(192*((System.currentTimeMillis()-a.time-startTime+ARtime[0])/(double)ARtime[1]));
			if (alpha>192) alpha = 192;
		}
		//fade out
		if (a.state != 4) {
			alpha = 192-(int)(192*((System.currentTimeMillis()-(a.time+a.duration*a.slides+startTime))/(double)250));
			if (alpha<0) alpha = 0;
			if (alpha>192) alpha = 192;
		}
		gc.setStroke(1);
		int[] endPoint = new int[2]; //slider endpoint (useful)
		for (int j = a.points.length-1; j>=0; j--) {
			endPoint[0] = a.points[j][0];
			endPoint[1] = a.points[j][1];
			if (endPoint[0] != 0 || endPoint[1] != 0) break;
		}
		
		int curPoint = 0; //current progress in slider
		gc.setColor(new Color(255,0,0,alpha));
		/* current direction for repeat sliders is determined by a.direction
		 * remaining repeats is determined by a.tempslides
		 */
		if (!a.direction && a.tempslides>0) {
			curPoint = (int)((System.currentTimeMillis()-a.time-startTime-a.duration*(a.slides-a.tempslides))*a.points.length/a.duration);
			if (a.tempslides>1) {
				gc.fillOval(endPoint[0]-4+bufferX,endPoint[1]-4+bufferY,a.width+8, a.height+8); //bumps for repeat sliders
			}
		}
		if (a.direction && a.tempslides>0) {
			curPoint = (int)((a.points.length-1)-((System.currentTimeMillis()-a.time-startTime-a.duration*(a.slides-a.tempslides))*a.points.length/a.duration));
			if (a.tempslides>1) {
				gc.fillOval(a.points[0][0]-4+bufferX,a.points[0][1]-4+bufferY,a.width+8, a.height+8); //bumps for repeat sliders
			}
		}
		
		//main slider body (drawing many points)
		gc.setColor(new Color(0,128,255,alpha/4));
		for (int j = 0; j<a.points.length; j+=4) {
			if (a.points[j][0] == 0 && a.points[j][1] == 0) continue; //invalid points, don't draw
			gc.fillOval(a.points[j][0]+bufferX,a.points[j][1]+bufferY,a.width, a.height);
		}
		
		//"circle" outline at start of slider, and visual combo
		gc.setStroke(5);
		gc.setColor(new Color(255,255,255,alpha));
		if (System.currentTimeMillis()<a.time+startTime) {
			if (a.x!= 0 || a.y!= 0) {
				gc.drawOval(a.points[0][0]+bufferX,a.points[0][1]+bufferY,a.width,a.height);
				gc.setColor(new Color(0,128,255,alpha));
				gc.fillOval(a.points[0][0]+bufferX,a.points[0][1]+bufferY,a.width,a.height);
				gc.setColor(new Color(255,255,255,alpha));
				float alpha2 = alpha/192f;
				if (a.combo<10) {
					gc.drawImage(sprites,a.x+bufferX,a.y+bufferY,a.x+bufferX+a.width,a.y+bufferY+a.height,128*a.combo,64,128+128*a.combo,192,alpha2,null);
				}
				if (a.combo>=10) {
					int ones = a.combo%10;
					int tens = (a.combo%100)/10;
					gc.drawImage(sprites,a.x+bufferX,a.y+bufferY+a.height/8,a.x+bufferX+a.width*3/4,a.y+bufferY+a.height*7/8,128*tens,64,128+128*tens,192,alpha2,null);
					gc.drawImage(sprites,a.x+bufferX+a.width*1/4,a.y+bufferY+a.height/8,a.x+bufferX+a.width,a.y+bufferY+a.height*7/8,128*ones,64,128+128*ones,192,alpha2,null);
				}
				//for every frame slider is on screen, touched[1] is increased
				if (a.time+a.duration*a.slides+startTime>System.currentTimeMillis()) a.touched[1]++;
			}
		}
		gc.setStroke(5);
		
		if (a.time+a.duration*a.slides+startTime<=System.currentTimeMillis()) {
			curPoint = 10000; //placeholder number, stops curPoint updating when slider is done
		}
		
		//find point with current slider progress
		if (curPoint>0) {
			if (!a.direction) {
				a.x = endPoint[0];
				a.y = endPoint[1];
			}
			if (a.direction) {
				a.x = a.points[0][0];
				a.y = a.points[0][1];
			}
				
			if (curPoint<a.points.length-1) {
				a.x = a.points[curPoint][0];
				a.y = a.points[curPoint][1];
			}
			if (a.x!= 0 || a.y!= 0) gc.drawOval(a.x+bufferX, a.y+bufferY, a.width, a.height); //draw moving circle
		}
		
		//approach circle
		if (a.time+startTime >= System.currentTimeMillis()) {
			gc.setStroke(3);
			gc.setColor(new Color(0,192,255,alpha));
			int b = (int)(54.4-4.48*CS)*3;
			int approachCircle = b-(int)(b*((System.currentTimeMillis()-a.time-startTime+ARtime[0])/(double)ARtime[0]));
			gc.drawOval(a.x+bufferX-approachCircle,a.y+bufferY-approachCircle,approachCircle*2+a.width, approachCircle*2+a.height);
		}
		
		//flip direction for repeat sliders
		if (a.tempslides>1 && System.currentTimeMillis()>=a.time+a.duration*(1+a.slides-a.tempslides)+startTime) {
			a.tempslides--;
			a.direction = !a.direction;
		}
		
		//accuracy feedback
		if (a.time+a.duration*a.slides+startTime<=System.currentTimeMillis()-150 && a.finished) {
			gc.drawImage(sprites,a.x+bufferX,a.y+bufferY,a.x+bufferX+a.width,a.y+bufferY+a.height,0+64*a.state,0,64+64*a.state,64,null);
		}
		
	}
	
	void checkNotes() {
		//handles accuracy and deletion of finished notes from arraylist
		
		//spinners
		for (int i = spinnerList.size()-1; i>=0; i--) {
			if (spinnerList.size() == 0) break;
			Spinner a = spinnerList.get(i);
			//if spinner has finished
			if (a.endTime+startTime<=System.currentTimeMillis()-150 && !a.finished) {
				//find accuracy based on spinner progress
				if (a.spin<=(a.endTime-a.time)/1000) {
					a.state = 0;
					combo = -1; //miss, break combo
				}
				if (a.spin>(a.endTime-a.time)/1000) a.state = 1;
				if (a.spin>=(a.endTime-a.time)/666) a.state = 2;
				if (a.spin>=(a.endTime-a.time)/500) {
					a.state = 3;
					score+= (a.spin-(a.endTime-a.time)/500)*1000; //extra score and health if more spins than required
					health+= (a.spin-(a.endTime-a.time)/500)*10;
					if (health>50) health = 50;
				}
				hits[a.state]++; //add accuracy to array
				combo++; //increment combo
				scoreCalc(a.state); //add and calculate score
				changeHealth(a.state); //change health
				a.finished = true; //calculations for this object is done, don't do it again
			}
			//delete object after it has fully faded
			if (a.endTime+startTime<=System.currentTimeMillis()-650) {
				spinnerList.remove(i);
				i++;
			}
		}
		
		//sliders
		for (int i = sliderList.size()-1; i>=0; i--) {
			if (sliderList.size() == 0) break;
			Slider a = sliderList.get(i);
			//if slider has commenced
			if (a.time+startTime<=System.currentTimeMillis()-25-(200-10*OD)) {
				//checks for slider break (lack of click)
				if (a.state == 4) {
					a.state = 2;
					combo = 0;
					changeHealth(0);
				}
			}
			//if slider has finished
			if (a.time+a.duration*a.slides+startTime<=System.currentTimeMillis()-150 && !a.finished) {
				/* Slider accuracy:
				 * You need to follow the slider for at least 25% of all frames it is displayed for full accuracy
				 * (25% is much harder than it seems)
				 * If this isn't fulfilled, you drop the sliderend combo and lose accuracy
				 * Everything else is the same as spinners
				 */
				if (a.touched[0] < a.touched[1]/4) {
					a.state-=1;
					combo--;
				}
				if (a.touched[0] < a.touched[1]/6) {
					a.state-=1;
				}
				hits[a.state]++;
				combo++;
				scoreCalc(a.state);
				changeHealth(a.state);
				a.finished = true;
			}
			if (a.time+a.duration*a.slides+startTime<=System.currentTimeMillis()-650) {
				sliderList.remove(i);
				i++;
			}
		}
		
		//circles (accuracy calculation can be done right at the click, so it's in the mouse method)
		for (int i = circleList.size()-1; i>=0; i--) {
			if (circleList.size() == 0) break;
			HitCircle a = circleList.get(i);
			//if circle has been on screen for a while
			if (a.time+startTime<=System.currentTimeMillis()-25-(200-10*OD)) {
				//if not clicked, miss
				if (a.state == 4) {
					a.state = 0;
					hits[0]++;
					combo = 0;
					changeHealth(a.state);
				}
			}
			if (a.time+startTime<=System.currentTimeMillis()-(700-10*OD)) {
				circleList.remove(i);
				i++;
			}
		}
		
		//update max combo
		if (combo>maxCombo) maxCombo = combo;
	}
	
	void scoreCalc(int a) {
		//adds score for each note, multiplies with combo
		if (a == 3) {
			score+= 300+300*(combo-1)/10;
		}
		if (a == 2) {
			score+= 100+100*(combo-1)/10;
		}
		if (a == 1) {
			score+= 50+50*(combo-1)/10;
		}
	}
	
	void changeHealth(int a) {
		//decreases health for every note, but notes that get hit re-increase health
		if (!noFail) health-=HP;
		if (a!=4) health+=a*4;
		if (health>50) health = 50;
	}

	void checkMouse() {
		//find current mouse positions
		m1.x = gc.getMouseX()-bufferX-8;
		m1.y = gc.getMouseY()-bufferY-8;
		m2.x = gc.getMouseX()-bufferX-64;
		m2.y = gc.getMouseY()-bufferY-64;
		
		//update recent cursor positions
		for (int i = 1; i<cursorTrail; i++) {
			cursorPos[i-1][0] = cursorPos[i][0];
			cursorPos[i-1][1] = cursorPos[i][1];
		}
		cursorPos[cursorTrail-1][0] = gc.getMouseX();
		cursorPos[cursorTrail-1][1] = gc.getMouseY();
		//draw trail
		for (int i = 0; i<cursorTrail; i++) {
			float alpha = (float)((double)i/cursorTrail);
			gc.drawImage(sprites, cursorPos[i][0]-15, cursorPos[i][1]-15, cursorPos[i][0]+15, cursorPos[i][1]+15, 256, 192, 306, 242, alpha, null);
		}
		//draw cursor
		gc.drawImage(sprites, m1.x+bufferX-cursorSize*5/2+8, m1.y+bufferY-cursorSize*5/2+8, m1.x+bufferX+cursorSize*5/2+8, m1.y+bufferY+cursorSize*5/2+8, 0, 192, 256, 448, null);
		
		//note click detection
		boolean[] clicked = new boolean[4]; //if a note has been hit, the press will not interact with any other notes
		if (!gc.isKeyDown(key1)) pressed[0] = false;
		if (!gc.isKeyDown(key2)) pressed[1] = false;
		if (!gc.getMouseButton(0)) pressed[2] = false;
		if (!gc.getMouseButton(2)) pressed[3] = false;
		
		if (gc.isKeyDown(key1) && !pressed[0]) {
			pressed[0] = true;
			registerCircleClick(0, clicked); //tests for circle/slider click
			registerSliderClick(0, clicked);
			clicks[0]++;
		}
		if (gc.isKeyDown(key2) && !pressed[1]) {
			pressed[1] = true;
			registerCircleClick(1, clicked);
			registerSliderClick(1, clicked);
			clicks[1]++;
		}
		if (gc.getMouseButton(0) && !pressed[2]) {
			pressed[2] = true;
			registerCircleClick(2, clicked);
			registerSliderClick(2, clicked);
			clicks[2]++;
		}
		if (gc.getMouseButton(2) && !pressed[3]) {
			pressed[3] = true;
			registerCircleClick(3, clicked);
			registerSliderClick(3, clicked);
			clicks[3]++;
		}
		
		//holding functionalities
		if (gc.isKeyDown(key1) || gc.isKeyDown(key2) || gc.getMouseButton(0) || gc.getMouseButton(2)) {
			//if key is held and cursor satisfies slider position, increment a.touched[0]
			for (int i = 0; i<sliderList.size(); i++) {
				Slider a = sliderList.get(i);
				if (m2.intersects(a) && a.time+startTime<=System.currentTimeMillis()+(200-10*OD)) {
					a.touched[0]++;
				}
			}
			
			//Spinners (only spin when held)
			double[] angle = new double[2]; //angle for current mouse position and previous mouse position
			//calculate angle
			for (int i = 0; i<2; i++) {
				int[] dist = {cursorPos[cursorTrail-1-i][0]-WINX/2,cursorPos[cursorTrail-1-i][1]-WINY/2}; //distance from cursor to middle of screen
				//2 angles for each because Java arc functions are weird
				double angle1 = Math.asin(dist[0]/(Math.sqrt(Math.pow(dist[0], 2)+Math.pow(dist[1], 2))))*180/Math.PI;
				double angle2 = Math.acos(dist[1]/(Math.sqrt(Math.pow(dist[0], 2)+Math.pow(dist[1], 2))))*180/Math.PI;
				//calculate the real angle with these 2 angles
				if (angle1+angle2 >= 180-0.1 && angle1+angle2 <= 180+0.1) angle[i] = angle2-90;
				if (-angle1+angle2 >= 180-0.1 && -angle1+angle2 <= 180+0.1) angle[i] = -angle1+90;
				if (angle1 >= -angle2-0.1 && angle1 <= -angle2+0.1) angle[i] = 270+angle1;
				if (angle1 >= angle2-0.1 && angle1 <= angle2+0.1) angle[i] = 270+angle2;
			}
			
			double theta = angle[0]-angle[1]; //delta angle moved per frame
			//handle crossing the "x-axis" (360 degrees becomes 0)
			if (angle[0]>270 && angle[1]<90) theta-=360;
			if (angle[1]>270 && angle[0]<90) theta+=360;
			
			//calculate spins
			for (int i = 0; i<spinnerList.size(); i++) {
				Spinner a = spinnerList.get(i);
				if (a.time+startTime <= System.currentTimeMillis()+ARtime[0] && a.state == 4) {
					a.dT+=(int)theta;
					if (Math.abs(a.dT)>=360) {
						a.dT = 0;
						a.spin++;
					}
				}
				
			}
		}
	}
	
	void drawGui() {
		//map progress (timer)
		int currentTime = (int)(System.currentTimeMillis()-startTime);
		gc.setColor(Color.WHITE);
		gc.setStroke(1);
		if (currentTime<0) {
			//2 second grace period before map starts
			gc.setColor(Color.GREEN);
			gc.fillArc(WINX-160, 80, 50, 50, 90, (int)(((double)currentTime/2000)*360));
		} else {
			gc.fillArc(WINX-160, 80, 50, 50, 90, -(int)(((double)currentTime/endTime)*360));
		}
		//outline
		gc.setColor(Color.WHITE);
		gc.setStroke(2);
		gc.drawOval(WINX-160, 80, 50, 50);
		
		//gross accuracy calculation (here because it is after checkNotes and checkMouse)
		if (hits[0]+hits[1]+hits[2]+hits[3]>0) {
			acc = (double)(hits[3]*100+hits[2]*100/3+hits[1]*100/6)/(hits[0]+hits[1]+hits[2]+hits[3]);
		}
		
		//text
		gc.setFont(Arial24);
		gc.drawString(String.format("%.2f", acc), WINX-95, 115); //accuracy
		
		gc.setFont(Arial18);
		gc.drawString(songName, 20, 60); //song name
		
		char k1 = (char)key1;
		char k2 = (char)key2;
		gc.drawString(k1+","+k2+",Mouse to Tap", WINX-165, 170); //controls
		gc.drawString("Q to Pause", WINX-105, 200);
		
		gc.setStroke(3);
		gc.drawLine(WINX-225, 65, WINX-10, 65);
		gc.setFont(Arial36);
		gc.drawString(String.format("%010d", score), WINX-225, 50); //score, formatted to avoid left-alignment issues
		
		gc.setFont(Arial48);
		gc.drawString(combo+"x", 15, WINY-10); //combo
		
		//click counter
		gc.setStroke(2);
		gc.setFont(Arial18);
		for (int i = 0; i<4; i++) {
			gc.drawRect(WINX-100, 250+i*35, 75, 30);
			gc.drawString(clicks[i]+"", WINX-90, 272+i*35); //# clicks from each input
		}
		
		//HP bar
		gc.setStroke(1);
		gc.setColor(Color.WHITE);
		gc.fillRect(20, 5, WINX/3, 20);
		gc.fillOval(10, 5, 20, 20);
		gc.fillOval(WINX/3+10, 5, 20, 20);
		
		gc.setColor(Color.RED); //current health
		gc.fillRect(22, 7, (WINX/3-4)*(int)health/50, 16);
		gc.fillOval(12, 7, 16, 16);
		gc.fillOval((WINX/3-4)*(int)health/50 + 16, 7, 16, 16);
		
		//accuracy feedback bar (at bottom) (left is early, right is late)
		int[] barLength = {(int)(200-10*OD),(int)(140-8*OD),(int)(80-6*OD)};
		//colors
		gc.setColor(new Color(225,108,0));
		gc.fillRect(WINX/2-barLength[0], WINY-20, barLength[0]*2, 10);
		gc.setColor(new Color(0,225,0));
		gc.fillRect(WINX/2-barLength[1], WINY-20, barLength[1]*2, 10);
		gc.setColor(Color.CYAN.darker());
		gc.fillRect(WINX/2-barLength[2], WINY-20, barLength[2]*2, 10);
		
		//individual accuracy feedback
		for (int i = 0; i<accList.size(); i++) {
			int[] j = accList.get(i);
			int alpha = 255;
			if (accList.size()-i>5) alpha = 128;
			if (accList.size()-i>10) alpha = 64;
			if (accList.size()-i>25) alpha = 32;
			if (accList.size()-i>50) alpha = 0;
			if (j[1] == 3) gc.setColor(new Color(0,255,255,alpha));
			if (j[1] == 2) gc.setColor(new Color(0,255,0,alpha));
			if (j[1] == 1) gc.setColor(new Color(255,128,0,alpha));
			if (i == accList.size()-1) gc.setColor(Color.RED);
			gc.fillRect(WINX/2+j[0], WINY-24, 2, 18);
		}
		//arrow signifying perfect window
		gc.setColor(Color.WHITE);
		gc.drawLine(WINX/2-5, WINY-30,WINX/2, WINY-25);
		gc.drawLine(WINX/2+5, WINY-30,WINX/2, WINY-25);
	}
	
	void registerCircleClick(int a, boolean[] b) {
		//check all circles
		for (int i = 0; i<circleList.size(); i++) {
			if (b[a]) break; //if tap has already hit a circle or spinner
			HitCircle c = circleList.get(i);
			//if mouse is on circle, circle is not tapped yet, and in time window
			if (m1.intersects(c) && c.state == 4 && c.time+startTime<=System.currentTimeMillis()-25+(200-10*OD)) {
				//check for each time window (50 then 100 then 300)
				if (Math.abs(System.currentTimeMillis()-25-c.time-startTime)>(140-8*OD)) {
					c.state = 1;
					hits[1]++;
					combo++;
					scoreCalc(c.state);
					changeHealth(c.state);
					accList.add(new int[]{(int)(System.currentTimeMillis()-25-c.time-startTime), c.state}); //add accuracy to list
					b[a] = true;
					playSound(4); //hitsound
					break;
				}
				if (Math.abs(System.currentTimeMillis()-25-c.time-startTime)>(80-6*OD)) {
					c.state = 2;
					hits[2]++;
					combo++;
					scoreCalc(c.state);
					changeHealth(c.state);
					accList.add(new int[]{(int)(System.currentTimeMillis()-25-c.time-startTime), c.state});
					b[a] = true;
					playSound(4);
					break;
				}
				c.state = 3;
				hits[3]++;
				combo++;
				scoreCalc(c.state);
				changeHealth(c.state);
				accList.add(new int[]{(int)(System.currentTimeMillis()-25-c.time-startTime), c.state});
				b[a] = true;
				playSound(4);
				break;
			}
		}
	}
	
	void registerSliderClick(int a, boolean[] b) {
		//similar to circles
		for (int i = 0; i<sliderList.size(); i++) {
			if (b[a]) break;
			Slider c = sliderList.get(i);
			//sliders just need to be tapped in the time window for good starting accuracy
			if (m1.intersects(c) && c.state == 4 && c.time+startTime<=System.currentTimeMillis()-25+(200-10*OD)) {
				c.state = 3;
				combo++;
				
				//figure out accuracy nonetheless for data purposes
				int j = 1;
				if (Math.abs(System.currentTimeMillis()-25-c.time-startTime)<=(140-8*OD)) j = 2;
				if (Math.abs(System.currentTimeMillis()-25-c.time-startTime)<=(80-6*OD)) j = 3;
				accList.add(new int[]{(int)(System.currentTimeMillis()-25-c.time-startTime), j});
				
				b[a] = true;
				playSound(4);
				break;
			}
		}
	}
	
	void checkPause(boolean songPlayed) {
		//pause only works after the grace period
		
		//pause
		if (songPlayed && gc.isKeyDown('Q') && !pressed[4] && !paused) {
			pauseTime = System.currentTimeMillis(); //save current time
			clip[0].stop(); //stop music
			pressed[4] = true;
			paused = true; //disables graphics from updating
			playSound(2);
			
			//pause screen, gradient
			for (int i = 0; i<25; i++) {
				gc.setColor(new Color(0,0,0,10));
				gc.fillRect(0, 0, WINX, WINY);
				gc.setColor(new Color(0,64,255,2));
				gc.fillRect(0, 0, WINX, i*WINY/150);
				gc.fillRect(0, WINY-i*WINY/150, WINX, i*WINY/150);
				gc.sleep(1);
			}
			//text
			gc.setFont(Arial48);
			for (int i = 0; i<25; i++) {
				gc.setColor(new Color(0,0,0,10));
				gc.drawString("Game Paused", WINX*9/25+1, WINY*3/10+1);
				gc.drawString("R to Restart", WINX*9/25+1, WINY*1/2+1);
				gc.drawString("T for Menu", WINX*9/25+1, WINY*7/10+1);
				gc.setColor(new Color(255,255,255,20));
				gc.drawString("Game Paused", WINX*9/25, WINY*3/10);
				gc.drawString("R to Restart", WINX*9/25, WINY*1/2);
				gc.drawString("T for Menu", WINX*9/25, WINY*7/10);
				gc.sleep(1);
			}
		}
		
		//unpause
		if (songPlayed && gc.isKeyDown('Q') && !pressed[4] && paused) {
			pressed[4] = true;
			paused = false;
			startTime+=System.currentTimeMillis()-pauseTime; //update starttime so all notes still work
			playSound(2);
			clip[0].setMicrosecondPosition((System.currentTimeMillis()-startTime)*1000); //set clip to time at pause
			clip[0].start();
		}
		
		if (!gc.isKeyDown('Q')) pressed[4] = false;
	}
	
	boolean checkFail() {
		if (health<=0) {
			clip[0].stop();
			playSound(1); //fail sound effect
			ded = true;
			
			//fail screen, gradient
			for (int i = 0; i<25; i++) {
				gc.setColor(new Color(0,0,0,10));
				gc.fillRect(0, 0, WINX, WINY);
				gc.setColor(new Color(255,0,0,2));
				gc.fillRect(0, 0, WINX, i*WINY/150);
				gc.fillRect(0, WINY-i*WINY/150, WINX, i*WINY/150);
				gc.sleep(1);
			}
			//text
			gc.setFont(Arial48);
			for (int i = 0; i<25; i++) {
				gc.setColor(new Color(0,0,0,10));
				gc.drawString("You Failed...", WINX*9/25+1, WINY*3/10+1);
				gc.drawString("Try Again? (R)", WINX*9/25+1, WINY*1/2+1);
				gc.drawString("Back to Menu (T)", WINX*9/25+1, WINY*7/10+1);
				gc.setColor(new Color(255,255,255,20));
				gc.drawString("You Failed...", WINX*9/25, WINY*3/10);
				gc.drawString("Try Again? (R)", WINX*9/25, WINY*1/2);
				gc.drawString("Back to Menu (T)", WINX*9/25, WINY*7/10);
				gc.sleep(1);
			}
			
			return true;
		}
		return false;
	}
	
	void drawEndscreen(BufferedImage bg) {
		//local fonts
		Font Arial = new Font("Arial Rounded MT Bold", Font.PLAIN, WINY/15);
		Font ArialL = new Font("Arial Rounded MT Bold", Font.PLAIN, (WINY/5)*3);
		Font Verdana = new Font("Verdana", Font.PLAIN, WINY/20);
		
		//fade game screen out
		for (int i = 0; i<128; i+=2) {
			gc.setColor(new Color(0,0,0,i));
			gc.fillRect(0,0,WINX,WINY);
			gc.sleep(1);
		}
		
		//draw and fade endscreen in
		for (int i = 250; i>=0; i-=10) {
			synchronized(gc) {
				gc.clear();
				//end screen
				gc.drawImage(bg,0, 0, WINX, WINY,0.8f);
				gc.setColor(new Color(255,255,255,128));
				gc.setStroke(1);
				gc.fillRoundRect(WINX/10, WINY/10, WINX/2, (WINY/10)*8, WINX/20, WINX/20);
				
				//end screen text shadow
				gc.setColor(Color.BLACK);
				endText(2,Arial,Verdana);
				
				gc.drawString("300", WINX/8+2, WINY/3+WINY/30+2);
				gc.drawString("100", (WINX/5)*2-WINX/30+2, WINY/3+WINY/30+2);
				gc.drawString("50", WINX/8+2, (WINY/6)*3+WINY/20+2);
				gc.drawString("Miss", (WINX/5)*2-WINX/30+2, (WINY/6)*3+WINY/20+2);
				
				gc.setFont(Arial24);
				gc.drawString(songName, 20+2, 40+2);
				
				//end screen text
				gc.setColor(Color.WHITE);
				gc.drawString(songName, 20, 40);
				endText(0,Arial,Verdana);
				
				gc.setColor(Color.CYAN);
				gc.drawString("300", WINX/8, WINY/3+WINY/30);
				
				gc.setColor(Color.GREEN);
				gc.drawString("100", (WINX/5)*2-WINX/30, WINY/3+WINY/30);
				
				gc.setColor(Color.ORANGE);
				gc.drawString("50", WINX/8, (WINY/6)*3+WINY/20);
				
				gc.setColor(Color.RED);
				gc.drawString("Miss", (WINX/5)*2-WINX/30, (WINY/6)*3+WINY/20);
				
				getGrade(ArialL); //grade based on accuracy
				
				gc.setColor(new Color(0,0,0,192));
				gc.setFont(Arial36);
				gc.drawString("R to restart", (WINX/10)*7+2, (WINY/6)*5+2);
				gc.drawString("T for Menu", (WINX/10)*7, (WINY*11/12)+2);
				gc.setColor(Color.WHITE);
				gc.drawString("R to restart", (WINX/10)*7, (WINY/6)*5);
				gc.drawString("T for Menu", (WINX/10)*7, (WINY*11/12));
				
				//overlay for fade
				gc.setColor(new Color(0,0,0,i));
				gc.fillRect(0, 0, WINX, WINY);
			}
			gc.sleep(1);
		}
	}
	
	void endText(int x, Font Arial, Font Verdana) {
		//draw data to screen
		gc.setFont(Arial);
		gc.drawString(String.format("%010d", score), WINX/8+x, WINY/5+x);
		gc.drawString(maxCombo+"x", (WINX/5)*2+x, WINY-WINY/7+x);
		gc.drawString(String.format("%.2f", acc)+"%", WINX/8+x, WINY-WINY/7+x);
		gc.drawString(hits[3]+"x", (WINX/9)*2+x, WINY/3+WINY/30+x);
		gc.drawString(hits[2]+"x", (WINX/5)*2+WINX/15+x, WINY/3+WINY/30+x);
		gc.drawString(hits[1]+"x", (WINX/9)*2+x, (WINY/6)*3+WINY/20+x);
		gc.drawString(hits[0]+"x", (WINX/5)*2+WINX/15+x, (WINY/6)*3+WINY/20+x);
		
		gc.setFont(Verdana);
		gc.drawString("Score", WINX/2-WINX/20+x, WINY/5+x);
		gc.drawString("Max Combo", (WINX/11)*4+x, WINY-WINY/7-WINY/10+x);
		gc.drawString("Accuracy", WINX/8+x, WINY-WINY/7-WINY/10+x);
	}
	
	void getGrade(Font ArialL) {
			//calculate grade with accuracy, draw it
			gc.setFont(ArialL);
			if (acc == 100) {
				gc.setColor(Color.YELLOW);
				gc.drawString("X", (WINX/20)*13, (WINY/6)*4);
			}
			if (acc >= 95 && acc < 100) {
				gc.setColor(Color.ORANGE);
				gc.drawString("S", (WINX/20)*13, (WINY/6)*4);
			}
			if (acc >= 90 && acc < 95) {
				gc.setColor(Color.GREEN);
				gc.drawString("A", (WINX/20)*13, (WINY/6)*4);
			}
			if (acc >= 80 && acc < 90) {
				gc.setColor(Color.BLUE);
				gc.drawString("B", (WINX/20)*13, (WINY/6)*4);
			}
			if (acc >= 60 && acc < 80) {
				gc.setColor(Color.MAGENTA);
				gc.drawString("C", (WINX/20)*13, (WINY/6)*4);
			}
			if (acc <60) {
				gc.setColor(Color.RED);
				gc.drawString("D", (WINX/20)*13, (WINY/6)*4);
			}
		}
	
	void loadSound(String soundName, int i) {
		try {
			AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new File(soundName).getAbsoluteFile());
			clip[i] = AudioSystem.getClip();
			clip[i].open(audioInputStream);
		} catch(UnsupportedAudioFileException | IOException | LineUnavailableException ex) {
			System.out.println("Error with playing sound.");
			ex.printStackTrace( );
		}
	}
	
	void playSound(int i) {
		//*if currently playing, stop and restart
		clip[i].stop();
		gc.sleep(1);
		clip[i].setMicrosecondPosition(0);
		clip[i].start();
	}
}

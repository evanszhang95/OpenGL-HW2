import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import com.jogamp.opengl.*;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.gl2.GLUT;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.nativewindow.ScalableSurface;

import javax.swing.JFrame;
import javax.vecmath.Point3f;
import javax.vecmath.Vector3f;

import java.util.ArrayList;

class Hierarchical extends JFrame implements GLEventListener, KeyListener, MouseListener, MouseMotionListener, ActionListener {

    /* This defines the objModel class, which takes care
     * of loading a triangular mesh from an obj file,
     * estimating per vertex average normal,
     * and displaying the mesh.
     */
    class objModel {

        public FloatBuffer vertexBuffer;
        public IntBuffer faceBuffer;
        public FloatBuffer normalBuffer;
        public Point3f center;
        public int num_verts;		// number of vertices
        public int num_faces;		// number of triangle faces

        public void Draw() {
            vertexBuffer.rewind();
            normalBuffer.rewind();
            faceBuffer.rewind();
            gl.glEnableClientState(GL2.GL_VERTEX_ARRAY);
            gl.glEnableClientState(GL2.GL_NORMAL_ARRAY);

            gl.glVertexPointer(3, GL2.GL_FLOAT, 0, vertexBuffer);
            gl.glNormalPointer(GL2.GL_FLOAT, 0, normalBuffer);

            gl.glDrawElements(GL2.GL_TRIANGLES, num_faces * 3, GL2.GL_UNSIGNED_INT, faceBuffer);

            gl.glDisableClientState(GL2.GL_VERTEX_ARRAY);
            gl.glDisableClientState(GL2.GL_NORMAL_ARRAY);
        }

        public objModel(String filename) {
            /* load a triangular mesh model from a .obj file */
            BufferedReader in = null;
            try {
                in = new BufferedReader(new FileReader(filename));
            } catch (IOException e) {
                System.out.println("Error reading from file " + filename);
                System.exit(0);
            }

            center = new Point3f();
            float x, y, z;
            int v1, v2, v3;
            float minx, miny, minz;
            float maxx, maxy, maxz;
            float bbx, bby, bbz;
            minx = miny = minz = 10000.f;
            maxx = maxy = maxz = -10000.f;

            String line;
            String[] tokens;
            ArrayList<Point3f> input_verts = new ArrayList<Point3f>();
            ArrayList<Integer> input_faces = new ArrayList<Integer>();
            ArrayList<Vector3f> input_norms = new ArrayList<Vector3f>();
            try {
                while ((line = in.readLine()) != null) {
                    if (line.length() == 0) {
                        continue;
                    }
                    switch (line.charAt(0)) {
                        case 'v':
                            tokens = line.split("[ ]+");
                            x = Float.valueOf(tokens[1]);
                            y = Float.valueOf(tokens[2]);
                            z = Float.valueOf(tokens[3]);
                            minx = Math.min(minx, x);
                            miny = Math.min(miny, y);
                            minz = Math.min(minz, z);
                            maxx = Math.max(maxx, x);
                            maxy = Math.max(maxy, y);
                            maxz = Math.max(maxz, z);
                            input_verts.add(new Point3f(x, y, z));
                            center.add(new Point3f(x, y, z));
                            break;
                        case 'f':
                            tokens = line.split("[ ]+");
                            v1 = Integer.valueOf(tokens[1]) - 1;
                            v2 = Integer.valueOf(tokens[2]) - 1;
                            v3 = Integer.valueOf(tokens[3]) - 1;
                            input_faces.add(v1);
                            input_faces.add(v2);
                            input_faces.add(v3);
                            break;
                        default:
                            continue;
                    }
                }
                in.close();
            } catch (IOException e) {
                System.out.println("Unhandled error while reading input file.");
            }

            System.out.println("Read " + input_verts.size()
                    + " vertices and " + input_faces.size() + " faces.");

            center.scale(1.f / (float) input_verts.size());

            bbx = maxx - minx;
            bby = maxy - miny;
            bbz = maxz - minz;
            float bbmax = Math.max(bbx, Math.max(bby, bbz));

            for (Point3f p : input_verts) {

                p.x = (p.x - center.x) / bbmax;
                p.y = (p.y - center.y) / bbmax;
                p.z = (p.z - center.z) / bbmax;
            }
            center.x = center.y = center.z = 0.f;

            /* estimate per vertex average normal */
            int i;
            for (i = 0; i < input_verts.size(); i++) {
                input_norms.add(new Vector3f());
            }

            Vector3f e1 = new Vector3f();
            Vector3f e2 = new Vector3f();
            Vector3f tn = new Vector3f();
            for (i = 0; i < input_faces.size(); i += 3) {
                v1 = input_faces.get(i + 0);
                v2 = input_faces.get(i + 1);
                v3 = input_faces.get(i + 2);

                e1.sub(input_verts.get(v2), input_verts.get(v1));
                e2.sub(input_verts.get(v3), input_verts.get(v1));
                tn.cross(e1, e2);
                input_norms.get(v1).add(tn);

                e1.sub(input_verts.get(v3), input_verts.get(v2));
                e2.sub(input_verts.get(v1), input_verts.get(v2));
                tn.cross(e1, e2);
                input_norms.get(v2).add(tn);

                e1.sub(input_verts.get(v1), input_verts.get(v3));
                e2.sub(input_verts.get(v2), input_verts.get(v3));
                tn.cross(e1, e2);
                input_norms.get(v3).add(tn);
            }

            /* convert to buffers to improve display speed */
            for (i = 0; i < input_verts.size(); i++) {
                input_norms.get(i).normalize();
            }

            vertexBuffer = Buffers.newDirectFloatBuffer(input_verts.size() * 3);
            normalBuffer = Buffers.newDirectFloatBuffer(input_verts.size() * 3);
            faceBuffer = Buffers.newDirectIntBuffer(input_faces.size());

            for (i = 0; i < input_verts.size(); i++) {
                vertexBuffer.put(input_verts.get(i).x);
                vertexBuffer.put(input_verts.get(i).y);
                vertexBuffer.put(input_verts.get(i).z);
                normalBuffer.put(input_norms.get(i).x);
                normalBuffer.put(input_norms.get(i).y);
                normalBuffer.put(input_norms.get(i).z);
            }

            for (i = 0; i < input_faces.size(); i++) {
                faceBuffer.put(input_faces.get(i));
            }
            num_verts = input_verts.size();
            num_faces = input_faces.size() / 3;
        }
    }

    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ESCAPE:
            case KeyEvent.VK_Q:
                System.exit(0);
                break;
            case 'r':
            case 'R':
                initViewParameters();
                break;
            case 'w':
            case 'W':
                wireframe = !wireframe;
                break;
            case 'b':
            case 'B':
                cullface = !cullface;
                break;
            case 'f':
            case 'F':
                flatshade = !flatshade;
                break;
            case 'a':
            case 'A':
                if (animator.isAnimating()) {
                    animator.stop();
                } else {
                    animator.start();
                }
                break;
            case '+':
            case '=':
                animation_speed *= 1.2f;
                break;
            case '-':
            case '_':
                animation_speed /= 1.2;
                break;
            default:
                break;
        }
        canvas.display();
    }

    /* GL, display, model transformation, and mouse control variables */
    private final GLCanvas canvas;
    private GL2 gl;
    private final GLU glu = new GLU();
    private final GLUT glut = new GLUT();
    private FPSAnimator animator;

    private int winW = 1200, winH = 800;
    private boolean wireframe = false;
    private boolean cullface = true;
    private boolean flatshade = false;

    private float xpos = 0, ypos = 0, zpos = 0;
    private float centerx, centery, centerz;
    private float roth = 0, rotv = 0;
    private float znear, zfar;
    private int mouseX, mouseY, mouseButton;
    private float motionSpeed, rotateSpeed;
    private float animation_speed = .3f;

    /* === YOUR WORK HERE === */
    /* Define more models you need for constructing your scene */
    private objModel statue_model = new objModel("statue.obj");
    private objModel axe_model = new objModel("axe.obj");
    private objModel male_model = new objModel("male.obj");
    private objModel female_model = new objModel("female.obj");
    private objModel dragon_model = new objModel("dragon.obj");
    private objModel bird_model = new objModel("bird.obj");
    private objModel bunny_model = new objModel("bunny.obj");
    private objModel tree_conical_model = new objModel("tree_conical.obj");

    private float axe_rotateT = 0.f;
    private float sun_rotateT = 0.f;
    private float dragon_rotateT = 0.f;
    private float statue_rotateT = 0.f;
    private float bird_rotateT = 0.f;
    private float bunny_Xlimit = 0;
    private float sun_Xlimit = 0;
    private float axe_Ylimit = 0;
    private float bird_Ylimit = 0;
    private float female_Zlimit = 0;

    private boolean axe_down = true;
    private boolean bird_down = true;
    private boolean female_far = true;
    private boolean bunny_left = true;
    private boolean sun_left = true;
    /* Here you should give a conservative estimate of the scene's bounding box
     * so that the initViewParameters function can calculate proper
     * transformation parameters to display the initial scene.
     * If these are not set correctly, the objects may disappear on start.
     */
    private float xmin = -1f, ymin = -1f, zmin = -1f;
    private float xmax = 1f, ymax = 1f, zmax = 1f;

    public void display(GLAutoDrawable drawable) {
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);

        gl.glPolygonMode(GL2.GL_FRONT_AND_BACK, wireframe ? GL2.GL_LINE : GL2.GL_FILL);
        gl.glShadeModel(flatshade ? GL2.GL_FLAT : GL2.GL_SMOOTH);
        if (cullface) {
            gl.glEnable(GL2.GL_CULL_FACE);
        } else {
            gl.glDisable(GL2.GL_CULL_FACE);
        }

        gl.glLoadIdentity();

        /* this is the transformation of the entire scene */
        gl.glTranslatef(-xpos, -ypos, -zpos);
        gl.glTranslatef(centerx, centery, centerz);
        gl.glRotatef(360.f - roth, 0, 1.0f, 0);
        gl.glRotatef(rotv, 1.0f, 0, 0);
        gl.glTranslatef(-centerx, -centery, -centerz);

        /* === YOUR WORK HERE === */
        /* Below is an example of a rotating bunny
         * It rotates the bunny with bunny_rotateT degrees around the bunny's gravity center
         */
        //Male

        gl.glPushMatrix();
            //Matrix for the sun on top
            gl.glPushMatrix();
                gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SHININESS, new float[]{30.f},0);
                gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_DIFFUSE, new float[]{1f, 0.7f, 0.0f, 1f}, 0);
                gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SPECULAR, new float[]{0.7f, 0.2f, 0.0f, 1f}, 0);
                gl.glTranslatef((float) (0.1* sun_Xlimit ),1.3f, 0);
                gl.glScalef(0.3f,0.3f,0.3f);
                gl.glRotatef(sun_rotateT, 0, 1, 0);
                glut.glutSolidSphere(0.3f, 50, 50);
            gl.glPopMatrix();

            //Matrix for the statue in the middle
            gl.glPushMatrix();
                gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SHININESS, new float[]{128.f},0);
                gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_DIFFUSE, new float[]{0.2f, 0.2f, 0.2f, 1f}, 0);
                gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SPECULAR, new float[]{0.5f, 0.5f, 0.5f, 1f}, 0);
                gl.glTranslatef(0, -0.6f, 0);
                gl.glScalef(0.3f,0.3f,0.3f);
                gl.glRotatef(statue_rotateT, 0, -1, 0);
                statue_model.Draw();
            gl.glPopMatrix();

            //Matrix for the Axe in the middle
            gl.glPushMatrix();
                gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SHININESS, new float[]{128.f},0);
                gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_DIFFUSE, new float[]{1f, 1f, 0f, 1f}, 0);
                gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SPECULAR, new float[]{1f, 1f, 1f, 1f}, 0);
                gl.glTranslatef(0,(float) (0.1*axe_Ylimit),0);
                gl.glScalef(0.3f,0.3f,0.3f);
                gl.glRotatef(axe_rotateT, 0, 1, 0);
                axe_model.Draw();
            gl.glPopMatrix();

            //matrix for dragon
            gl.glPushMatrix();
                gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SHININESS, new float[]{90.f},0);
                gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_DIFFUSE, new float[]{0.5f, 0f, 0f, 1f}, 0);
                gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SPECULAR, new float[]{0.5f, 0.5f, 0.5f, 1f}, 0);
                gl.glTranslatef(-centerx, -centery, -centerz);
                gl.glScalef(1f, 1f, 1f);
                gl.glRotatef(dragon_rotateT, 0, 1, 0);
                gl.glTranslatef(-1.f, -0.5f, 0);
                dragon_model.Draw();
                //Matrix for the Forest
                for(double i=-5;i<5;i+=0.5) {
                    gl.glPushMatrix();
                        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SHININESS, new float[]{128.f}, 0);
                        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_DIFFUSE, new float[]{1f, 0.5f, 0f, 1f}, 0);
                        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SPECULAR, new float[]{0.5f, 1f, 0f, 1f}, 0);
                        gl.glTranslatef((float) i+3, 0.2f, (float) i-1);
                        tree_conical_model.Draw();
                    gl.glPopMatrix();
                }
                //Matrix for the man
                gl.glPushMatrix();
                    gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SHININESS, new float[]{100.f},0);
                    gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_DIFFUSE, new float[]{0.5f, 0.7f, 0.7f, 1f}, 0);
                    gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SPECULAR, new float[]{1.f,0.2f,0.1f,0}, 0);
                    gl.glTranslatef(2.f, 0.2f, 0);
                    gl.glScalef(0.5f, 0.5f, 0.5f);
                    gl.glRotatef(-90, 0, 1, 0);
                    male_model.Draw();
                    //Matrix for female
                    gl.glPushMatrix();
                        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SHININESS, new float[]{128.f},0);
                        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_DIFFUSE, new float[]{0.5f, 0.4f, 0.4f, 1f}, 0);
                        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SPECULAR, new float[]{1.f,0.2f,0.1f,0}, 0);
                        gl.glTranslatef(-0.5f,-0.05f, (float) (0.1* female_Zlimit ));
                        if(female_far)
                            gl.glRotatef(180.f, 0, 1, 0);
                        female_model.Draw();
                        //Matrix for bunny, this is also the 4th level of hierarchy
                        gl.glPushMatrix();
                            gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SHININESS, new float[]{128.f},0);
                            gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_DIFFUSE, new float[]{0.5f, 0.4f, 0.4f, 1f}, 0);
                            gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SPECULAR, new float[]{1.f,0.2f,0.1f,0}, 0);
                            gl.glTranslatef((float) (0.1* bunny_Xlimit ),-0.5f, 0);
                            gl.glScalef(0.2f,0.2f,0.2f);
                            if(bunny_left)
                                gl.glRotatef(-180.f, 0, 1, 0);
                            bunny_model.Draw();
                        gl.glPopMatrix();
                    gl.glPopMatrix();
                    //Matrix for the bird
                    gl.glPushMatrix();
                        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SHININESS, new float[]{128.f},0);
                        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_DIFFUSE, new float[]{0.0f, 0.7f, 0.0f, 1f}, 0);
                        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SPECULAR, new float[]{0.8f, 0.3f, 0.2f, 1f}, 0);
                        gl.glTranslatef(0.3f,(float) (0.1* bird_Ylimit ),0);
                        gl.glScalef(0.2f,0.2f,0.2f);
                        gl.glRotatef(bird_rotateT, 0, 1, 0);
                        bird_model.Draw();
                    gl.glPopMatrix();
                gl.glPopMatrix();
            gl.glPopMatrix();
        gl.glPopMatrix();

        /* increment bunny_rotateT */
        if (animator.isAnimating()) {
            dragon_rotateT += 1.0f * animation_speed;
            statue_rotateT += 1.0f * animation_speed;
            axe_rotateT += 1.0f * animation_speed;
            bird_rotateT += 1.0f * animation_speed;
            if (axe_down) {
                if (axe_Ylimit < -1) {
                    axe_Ylimit += 0.1;
                    axe_down = false;
                } else {
                    axe_Ylimit  -= 0.1;
                }
            } else {
                if (axe_Ylimit > 1) {
                    axe_Ylimit  -= 0.1;
                    axe_down = true;
                } else {
                    axe_Ylimit += 0.1;
                }
            }
            if (bird_down) {
                if (bird_Ylimit < 1) {
                    bird_Ylimit += 0.05;
                    bird_down = false;
                } else {
                    bird_Ylimit -= 0.05;
                }
            } else {
                if (bird_Ylimit > 2) {
                    bird_Ylimit  -= 0.05;
                    bird_down = true;
                } else {
                    bird_Ylimit += 0.05;
                }
            }
            if (female_far) {
                if (female_Zlimit < -20) {
                    female_Zlimit += 0.1;
                    female_far = false;
                } else {
                    female_Zlimit -= 0.1;
                }
            } else {
                if (female_Zlimit > 0) {
                    female_Zlimit -= 0.1;
                    female_far = true;
                } else {
                    female_Zlimit += 0.1;
                }
            }
            if (bunny_left) {
                if (bunny_Xlimit < -5) {
                    bunny_Xlimit += 0.1;
                    bunny_left = false;
                } else {
                    bunny_Xlimit -= 0.1;
                }
            } else {
                if (bunny_Xlimit > 5) {
                    bunny_Xlimit -= 0.1;
                    bunny_left = true;
                } else {
                    bunny_Xlimit += 0.1;
                }
            }
            if (sun_left) {
                if (sun_Xlimit < -10) {
                    sun_Xlimit += 0.01;
                    sun_left = false;
                } else {
                    sun_Xlimit -= 0.01;
                }
            } else {
                if (sun_Xlimit > 10) {
                    sun_Xlimit -= 0.01;
                    sun_left = true;
                } else {
                    sun_Xlimit += 0.01;
                }
            }
        }
    }

    public Hierarchical() {
        super("Assignment 2 -- Hierarchical Modeling");
        final GLProfile glprofile = GLProfile.getMaxFixedFunc(true);
        GLCapabilities glcapabilities = new GLCapabilities(glprofile);
        canvas = new GLCanvas(glcapabilities);
        canvas.setSurfaceScale(new float[] { ScalableSurface.IDENTITY_PIXELSCALE, ScalableSurface.IDENTITY_PIXELSCALE }); // potential fix for Retina Displays
        canvas.addGLEventListener(this);
        canvas.addKeyListener(this);
        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);
        animator = new FPSAnimator(canvas, 60);	// create a 30 fps animator
        getContentPane().add(canvas);
        setSize(winW, winH);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
        animator.start();
        canvas.requestFocus();
    }

    public static void main(String[] args) {

        new Hierarchical();
    }

    public void init(GLAutoDrawable drawable) {
        gl = drawable.getGL().getGL2();

        initViewParameters();
        gl.glClearColor(.1f, .1f, .1f, 1f);
        gl.glClearDepth(1.0f);

        // white light at the eye
        float light0_position[] = {0, 0, 1, 0};
        float light0_diffuse[] = {1, 1, 1, 1};
        float light0_specular[] = {1, 1, 1, 1};
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, light0_position, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE, light0_diffuse, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_SPECULAR, light0_specular, 0);

        //red light
        float light1_position[] = {-.1f, .1f, 0, 0};
        float light1_diffuse[] = {.6f, .05f, .05f, 1};
        float light1_specular[] = {.6f, .05f, .05f, 1};
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_POSITION, light1_position, 0);
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_DIFFUSE, light1_diffuse, 0);
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_SPECULAR, light1_specular, 0);

        //blue light
        float light2_position[] = {.1f, .1f, 0, 0};
        float light2_diffuse[] = {.05f, .05f, .6f, 1};
        float light2_specular[] = {.05f, .05f, .6f, 1};
        gl.glLightfv(GL2.GL_LIGHT2, GL2.GL_POSITION, light2_position, 0);
        gl.glLightfv(GL2.GL_LIGHT2, GL2.GL_DIFFUSE, light2_diffuse, 0);
        gl.glLightfv(GL2.GL_LIGHT2, GL2.GL_SPECULAR, light2_specular, 0);

        float lmodel_ambient[] = {1.0f, 1.0f, 1.0f, 1.0f};
        gl.glLightModelfv(GL2.GL_LIGHT_MODEL_AMBIENT, lmodel_ambient, 0);
        gl.glLightModeli(GL2.GL_LIGHT_MODEL_TWO_SIDE, 1);

        gl.glDisable(GL2.GL_COLOR_MATERIAL);
        gl.glEnable(GL2.GL_NORMALIZE);
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_LIGHT0);
        gl.glEnable(GL2.GL_LIGHT1);
        gl.glEnable(GL2.GL_LIGHT2);

        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glDepthFunc(GL2.GL_LESS);
        gl.glHint(GL2.GL_PERSPECTIVE_CORRECTION_HINT, GL2.GL_NICEST);
        gl.glCullFace(GL2.GL_BACK);
        gl.glEnable(GL2.GL_CULL_FACE);
        gl.glShadeModel(GL2.GL_SMOOTH);
    }

    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        winW = width;
        winH = height;

        gl.glViewport(0, 0, width, height);
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        glu.gluPerspective(45.f, (float) width / (float) height, znear, zfar);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
    }

    public void mousePressed(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
        mouseButton = e.getButton();
        canvas.display();
    }

    public void mouseReleased(MouseEvent e) {
        mouseButton = MouseEvent.NOBUTTON;
        canvas.display();
    }

    public void mouseDragged(MouseEvent e) {
        int x = e.getX();
        int y = e.getY();
        if (mouseButton == MouseEvent.BUTTON3) {
            zpos -= (y - mouseY) * motionSpeed;
            mouseX = x;
            mouseY = y;
            canvas.display();
        } else if (mouseButton == MouseEvent.BUTTON2) {
            xpos -= (x - mouseX) * motionSpeed;
            ypos += (y - mouseY) * motionSpeed;
            mouseX = x;
            mouseY = y;
            canvas.display();
        } else if (mouseButton == MouseEvent.BUTTON1) {
            roth -= (x - mouseX) * rotateSpeed;
            rotv += (y - mouseY) * rotateSpeed;
            mouseX = x;
            mouseY = y;
            canvas.display();
        }
    }

    /* computes optimal transformation parameters for OpenGL rendering.
     * this is based on an estimate of the scene's bounding box
     */
    void initViewParameters() {
        roth = rotv = 0;

        float ball_r = (float) Math.sqrt((xmax - xmin) * (xmax - xmin)
                + (ymax - ymin) * (ymax - ymin)
                + (zmax - zmin) * (zmax - zmin)) * 0.707f;

        centerx = (xmax + xmin) / 2.f;
        centery = (ymax + ymin) / 2.f;
        centerz = (zmax + zmin) / 2.f;
        xpos = centerx;
        ypos = centery;
        zpos = ball_r / (float) Math.sin(45.f * Math.PI / 180.f) + centerz;

        znear = 0.01f;
        zfar = 1000.f;

        motionSpeed = 0.002f * ball_r;
        rotateSpeed = 0.1f;

    }

    // these event functions are not used for this assignment
    public void dispose(GLAutoDrawable glautodrawable) {
    }

    public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
    }

    public void keyTyped(KeyEvent e) {
    }

    public void keyReleased(KeyEvent e) {
    }

    public void mouseMoved(MouseEvent e) {
    }

    public void actionPerformed(ActionEvent e) {
    }

    public void mouseClicked(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }
}

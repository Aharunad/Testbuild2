package org.openlab.wakfuprobe;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public final class MainActivity extends Activity {
    private static final int IMPORT_ZIP = 10;
    private static final int IMPORT_FOLDER = 11;
    private static final int ORANGE = Color.rgb(255,154,70);
    private static final int TEXT = Color.rgb(239,242,248);
    private static final int SECONDARY = Color.rgb(171,181,202);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder report = new StringBuilder();
    private TextView output;
    private String gpuInfo = "Not tested";
    private String gpuFrames = "Not tested";
    private boolean rendering = false;
    private GLSurfaceView surface;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13,15,25));
        getWindow().setNavigationBarColor(Color.rgb(13,15,25));
        report.append("WAKFU Android Compatibility Probe 0.1\n");
        report.append("No game client, login, or server access is bundled.\n\n");
        report.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        report.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        report.append("ABIs: ").append(String.join(", ", Build.SUPPORTED_ABIS)).append("\n");
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(memory);
        report.append("Physical RAM: ").append(memory.totalMem / (1024*1024)).append(" MiB\n");
        report.append("Free RAM at launch: ").append(memory.availMem / (1024*1024)).append(" MiB\n");
        report.append("Logical CPU cores: ").append(Runtime.getRuntime().availableProcessors()).append("\n");
        report.append("OpenGL ES reported: 0x").append(Integer.toHexString(manager.getDeviceConfigurationInfo().reqGlEsVersion)).append("\n");
        report.append("Vulkan hardware: ").append(getPackageManager().hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)).append("\n");
        report.append("\nKnown client blocker: WAKFU Linux packages include desktop-native JOGL and X11 libraries. This app does not translate or execute them.\n");
        showHome();
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setPadding(0, 0, 0, 12);
        return t;
    }

    private void button(LinearLayout parent, String label, View.OnClickListener action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setTextSize(15);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(50,44,74)));
        b.setOnClickListener(action);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, 7);
        parent.addView(b,params);
    }

    private void showHome() {
        rendering = false;
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setBackgroundColor(Color.rgb(13,15,25));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(22 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        scroller.addView(root);
        TextView overline = text("ANDROID • ARM64 • EXPERIMENT 01", 12, ORANGE);
        root.addView(overline);
        TextView title = text("WAKFU\nCompatibility Probe", 28, TEXT);
        title.setTypeface(null, 1);
        root.addView(title);
        root.addView(text("This tests the tablet's GPU and inspects your legally obtained Linux installation. It does NOT yet launch WAKFU or connect to Ankama.",14,SECONDARY));
        button(root,"1  Run OpenGL ES graphics test (12s)",v -> startGpuTest());
        button(root,"2  Inspect official WAKFU Linux folder",v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i,IMPORT_FOLDER);
        });
        button(root,"3  Inspect a small WAKFU ZIP",v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i,IMPORT_ZIP);
        });
        button(root,"Share compatibility report",v -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT,"WAKFU Android compatibility report");
            send.putExtra(Intent.EXTRA_TEXT,report.toString());
            startActivity(Intent.createChooser(send,"Share diagnostic report"));
        });
        root.addView(text("DIAGNOSTICS",12,ORANGE));
        output = text(report.toString(),13,SECONDARY);
        output.setTextIsSelectable(true);
        root.addView(output);
        setContentView(scroller);
    }

    private void append(String line) {
        report.append("\n").append(line).append("\n");
        runOnUiThread(() -> { if(output != null) output.setText(report.toString()); });
    }

    private void startGpuTest() {
        if(rendering) return;
        rendering = true;
        FrameLayout frame = new FrameLayout(this);
        surface = new GLSurfaceView(this);
        surface.setEGLContextClientVersion(2);
        GraphicsRenderer renderer = new GraphicsRenderer();
        surface.setRenderer(renderer);
        surface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        frame.addView(surface);
        TextView hud = text("Running OpenGL ES smoke test…\nThis is not WAKFU FPS.",16,TEXT);
        hud.setPadding(25,25,25,25);
        hud.setBackgroundColor(0xA0000000);
        FrameLayout.LayoutParams top = new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        frame.addView(hud,top);
        setContentView(frame);
        handler.postDelayed(() -> {
            if(!rendering) return;
            gpuInfo = renderer.info;
            gpuFrames = renderer.summary();
            append("GPU: " + gpuInfo + "\nOpenGL ES test: " + gpuFrames + "\nNOTE: This measures a trivial graphics scene, not game performance.");
            surface.onPause();
            showHome();
        },12000);
    }

    @Override protected void onActivityResult(int req,int result,Intent data) {
        super.onActivityResult(req,result,data);
        if(result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        append("Inspecting local " + (req == IMPORT_FOLDER ? "directory" : "archive") + " (nothing uploaded)...");
        new Thread(() -> {
            try {
                Scan scan = new Scan();
                if(req == IMPORT_FOLDER) {
                    String id=DocumentsContract.getTreeDocumentId(uri);
                    inspectTree(uri,id,scan,0);
                } else {
                    long size = querySize(uri);
                    if(size > 512L*1024*1024) throw new IllegalArgumentException("Archive over 512 MB. Extract the installation and choose its folder instead.");
                    inspectZip(uri,scan);
                }
                append(scan.result());
            } catch (Exception e) {
                append("Inspection failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        },"wakfu-scan").start();
    }

    private long querySize(Uri uri) {
        try(Cursor cursor = getContentResolver().query(uri,new String[]{OpenableColumns.SIZE},null,null,null)) {
            if(cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0);
        } catch(Exception ignored) {}
        return -1;
    }

    private static final class Scan {
        int files=0,folders=0,jars=0,so=0;
        boolean gameJar=false,joglJar=false,linuxJogl=false,x11=false,zaapi=false;
        String sample="";
        void add(String name) {
            files++;
            String n = name.toLowerCase(Locale.ROOT);
            if(n.endsWith(".jar")) jars++;
            if(n.endsWith(".so")) so++;
            if(n.endsWith("wakfu-client.jar")) gameJar=true;
            if(n.contains("jogl") && n.endsWith(".jar")) joglJar=true;
            if(n.contains("libjogl_desktop.so")) linuxJogl=true;
            if(n.contains("libnativewindow_x11.so")) x11=true;
            if(n.contains("zaapi/") || n.endsWith("/zaapi") || n.equals("zaapi")) zaapi=true;
            if(sample.length() < 500 && (n.contains("wakfu-client") || n.contains("jogl") || n.contains("nativewindow"))) sample += "\n  "+name;
        }
        String result() {
            StringBuilder b=new StringBuilder();
            b.append("Inspected: ").append(files).append(" files, ").append(folders).append(" folders; ")
             .append(jars).append(" JARs, ").append(so).append(" native .so files\n")
             .append("wakfu-client.jar: ").append(gameJar ? "FOUND" : "not found").append("\n")
             .append("JOGL JAR: ").append(joglJar ? "FOUND" : "not found").append("\n")
             .append("Linux JOGL: ").append(linuxJogl ? "FOUND" : "not found").append("\n")
             .append("X11 native window: ").append(x11 ? "FOUND" : "not found").append("\n")
             .append("Zaapi launcher: ").append(zaapi ? "FOUND" : "not found").append("\n");
            if(linuxJogl || x11) b.append("BLOCKER: Desktop Linux native components cannot load directly on Android ARM64.\n");
            if(!gameJar) b.append("GAME NOT VERIFIED: wakfu-client.jar absent from inspected entries.\n");
            b.append("Relevant files:").append(sample.isEmpty() ? " none recorded" : sample).append("\n");
            b.append("No game executable was launched. No files were transmitted.");
            return b.toString();
        }
    }

    private void inspectTree(Uri tree,String id,Scan scan,int depth) {
        if(depth > 16 || scan.files > 20000) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
        ArrayList<String> next = new ArrayList<>();
        try (Cursor cursor = getContentResolver().query(children,new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)) {
            if(cursor == null) return;
            while(cursor.moveToNext() && scan.files <= 20000) {
                String child = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    scan.folders++;
                    next.add(child);
                } else scan.add(name == null ? "" : name);
            }
        }
        for(String folder:next) inspectTree(tree,folder,scan,depth+1);
    }

    private void inspectZip(Uri uri,Scan scan) throws Exception {
        try(InputStream raw = getContentResolver().openInputStream(uri);
            ZipInputStream zip = new ZipInputStream(raw)) {
            if(raw == null) throw new IllegalArgumentException("Cannot read selected archive");
            ZipEntry entry;
            int n=0;
            while((entry=zip.getNextEntry()) != null) {
                if(++n > 12000) break;
                if(entry.isDirectory()) scan.folders++;
                else scan.add(entry.getName());
                zip.closeEntry();
            }
        }
    }

    @Override public void onBackPressed() {
        if(rendering) {
            rendering=false;
            if(surface != null) surface.onPause();
            showHome();
        } else super.onBackPressed();
    }

    private static final class GraphicsRenderer implements GLSurfaceView.Renderer {
        volatile String info="GPU information unavailable";
        volatile int frames=0;
        volatile long first=0, last=0;
        private int program=-1, aPosition=-1, uWave=-1;
        private FloatBuffer buffer;
        @Override public void onSurfaceCreated(GL10 gl,EGLConfig config) {
            info = GLES20.glGetString(GLES20.GL_VENDOR)+" / "+GLES20.glGetString(GLES20.GL_RENDERER)+" / "+GLES20.glGetString(GLES20.GL_VERSION);
            float[] positions={-0.7f,-0.6f,0.7f,-0.6f,-0.7f,0.6f,0.7f,0.6f};
            ByteBuffer bytes = ByteBuffer.allocateDirect(positions.length*4).order(ByteOrder.nativeOrder());
            buffer = bytes.asFloatBuffer();
            buffer.put(positions).position(0);
            int vertex=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 pos; uniform float wave; void main(){ gl_Position=vec4(pos.x,pos.y+0.08*sin(wave+pos.x*7.0),0.0,1.0); }");
            int fragment=shader(GLES20.GL_FRAGMENT_SHADER,"precision mediump float; uniform float wave; void main(){ gl_FragColor=vec4(0.65+0.20*sin(wave),0.28,0.89,1.0); }");
            program=GLES20.glCreateProgram();
            GLES20.glAttachShader(program,vertex);
            GLES20.glAttachShader(program,fragment);
            GLES20.glLinkProgram(program);
            aPosition=GLES20.glGetAttribLocation(program,"pos");
            uWave=GLES20.glGetUniformLocation(program,"wave");
            first=System.nanoTime();
        }
        static int shader(int type,String text) {
            int shader=GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader,text);
            GLES20.glCompileShader(shader);
            return shader;
        }
        @Override public void onSurfaceChanged(GL10 gl,int width,int height) {
            GLES20.glViewport(0,0,width,height);
        }
        @Override public void onDrawFrame(GL10 gl) {
            GLES20.glClearColor(0.052f,0.059f,0.108f,1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            if(program > 0 && aPosition>=0) {
                GLES20.glUseProgram(program);
                GLES20.glUniform1f(uWave,(System.nanoTime()-first)/1000000000f);
                GLES20.glEnableVertexAttribArray(aPosition);
                buffer.position(0);
                GLES20.glVertexAttribPointer(aPosition,2,GLES20.GL_FLOAT,false,0,buffer);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
                GLES20.glDisableVertexAttribArray(aPosition);
            }
            frames++;
            last=System.nanoTime();
        }
        String summary() {
            double time=(last-first)/1e9;
            if(time <= 0) return "No frames rendered";
            return String.format(Locale.US,"%d frames / %.1f s = %.1f FPS (trivial scene, may be refresh-rate limited)",frames,time,frames/time);
        }
    }
}

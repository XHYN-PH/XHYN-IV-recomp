package com.libertyrecomp;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
/** Called only in the launcher, before starting the separate game process. */
final class GpuInfo {
    private static String cached;
    static synchronized String renderer() {
        if(cached!=null) return cached;
        cached="";
        EGLDisplay display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if(display==EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(display,new int[2],0,new int[2],0)) return cached;
        EGLDisplay oldDisplay=EGL14.eglGetCurrentDisplay();
        EGLContext oldContext=EGL14.eglGetCurrentContext();
        EGLSurface oldDraw=EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW),oldRead=EGL14.eglGetCurrentSurface(EGL14.EGL_READ);
        EGLContext context=EGL14.EGL_NO_CONTEXT;EGLSurface surface=EGL14.EGL_NO_SURFACE;
        try {
            EGLConfig[] config=new EGLConfig[1];int[] count=new int[1];
            int[] attributes={EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_NONE};
            if(!EGL14.eglChooseConfig(display,attributes,0,config,0,1,count,0)||count[0]==0) return cached;
            context=EGL14.eglCreateContext(display,config[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            surface=EGL14.eglCreatePbufferSurface(display,config[0],new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);
            if(context!=EGL14.EGL_NO_CONTEXT && surface!=EGL14.EGL_NO_SURFACE && EGL14.eglMakeCurrent(display,surface,surface,context)) {
                String value=GLES20.glGetString(GLES20.GL_RENDERER);if(value!=null) cached=value;
            }
        } catch(RuntimeException ignored) { cached=""; }
        finally {
            if(oldDisplay!=EGL14.EGL_NO_DISPLAY) EGL14.eglMakeCurrent(oldDisplay,oldDraw,oldRead,oldContext);
            else EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(surface!=EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display,surface);
            if(context!=EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display,context);
            EGL14.eglTerminate(display);
        }
        return cached;
    }
    private GpuInfo() {}
}
